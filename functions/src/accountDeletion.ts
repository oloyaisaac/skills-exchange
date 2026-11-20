// R06-P2-01 / R06-P2-02: account deletion is ordered, and the Auth user is the LAST thing removed.
//
// The client used to run `deleteUserFirestoreData(...)` and `deleteCurrentUser()` back to back,
// ignoring the first result. A failed Firestore delete still deleted the Auth user, leaving personal
// documents with no owner able to remove them. It also never touched Cloud Storage, so "permanent
// deletion" left KYC, guarantor and job photos behind.
//
// The rules here are pure so `node --test` can pin them: auth is removable only when nothing is
// pending, and the list of Storage prefixes is derived, never hand-assembled at the call site.

export const FRESH_AUTH_WINDOW_SECONDS = 300;

/** Collections whose document id IS the uid. */
export const SINGLE_DOC_COLLECTIONS = [
  "users",
  "worker_kyc",
  "worker_training",
  "providers",
  "guarantor_submissions",
] as const;

/**
 * Collections deleted by a `where(field == uid)` query.
 *
 * R08: the wallet books (`wallets`, `wallet_entries`, `wallet_withdrawals`) and the money records
 * (`payouts`, `platform_fees`, `refunds`, `reconciliation_reports`) are deliberately NOT purged here.
 * They are financial records with a retention obligation, and deleting them would also destroy the
 * evidence of a balance the owner may still be owed. Resolving that tension (retain the books, erase
 * the personal identifiers) is R09's retention work, not a silent delete.
 */
export const QUERY_DELETIONS = [
  { collection: "guarantors", field: "workerKycId" },
  { collection: "notifications", field: "userId" },
  { collection: "disputes", field: "reportedBy" },
  { collection: "disputes", field: "reportedUserId" },
  { collection: "job_quotes", field: "workerId" },
] as const;

/**
 * The `auth_time` claim must be recent. A missing or nonsensical claim fails closed: without it the
 * server cannot prove the person at the device is the account owner right now.
 */
export function isFreshAuth(
  authTime: unknown,
  nowMs: number,
  windowSeconds: number = FRESH_AUTH_WINDOW_SECONDS
): boolean {
  if (typeof authTime !== "number" || !Number.isFinite(authTime) || authTime <= 0) return false;
  const ageSeconds = nowMs / 1000 - authTime;
  return ageSeconds >= 0 && ageSeconds <= windowSeconds;
}

/**
 * Every Storage prefix that can hold this account's personal images.
 *
 * `users/{uid}/` covers KYC documents, certificates and guarantor portraits; `avatars/{uid}/` covers
 * the profile picture; `jobs/{jobId}/` covers the photos attached to the jobs this account posted.
 */
export function storagePurgePrefixes(uid: string, jobIds: string[]): string[] {
  if (!uid) return [];
  // R09: every prefix whose rules let this account write personal documents. `users/{uid}/` covers
  // kyc/, guarantors/, certificates/ and verification/ (see storage.rules), which is where the
  // identity documents live; `private_docs/{uid}/` is owner-writable too and is not written by the
  // app today, but a prefix a person *can* put a document into must be purged with their account.
  const prefixes = new Set<string>([`users/${uid}/`, `avatars/${uid}/`, `private_docs/${uid}/`]);
  for (const jobId of jobIds) {
    if (typeof jobId === "string" && jobId.trim().length > 0) prefixes.add(`jobs/${jobId.trim()}/`);
  }
  return [...prefixes].sort();
}

export interface DeletionProgress {
  storageDeleted: number;
  firestoreDocsDeleted: number;
  storagePending: number;
  firestorePending: number;
}

/**
 * The single gate that decides whether the Firebase Auth user may be removed.
 *
 * Anything still pending — a Storage object, a Firestore document — means the account must survive,
 * because deleting it would strip the only actor able to finish the job.
 */
export function mayDeleteAuthUser(progress: Pick<DeletionProgress, "storagePending" | "firestorePending">): boolean {
  return progress.storagePending === 0 && progress.firestorePending === 0;
}

/** User-facing explanation for a deletion that was refused. */
export function deletionRefusalReason(progress: Pick<DeletionProgress, "storagePending" | "firestorePending">): string {
  if (mayDeleteAuthUser(progress)) return "";
  const parts: string[] = [];
  if (progress.firestorePending > 0) parts.push(`${progress.firestorePending} record(s)`);
  if (progress.storagePending > 0) parts.push(`${progress.storagePending} file(s)`);
  return `Deletion stopped: ${parts.join(" and ")} could not be removed, so your account was kept. Nothing was lost — please try again.`;
}

// ---------------------------------------------------------------------------------------------
// R09-P2-01: scoped erasure that is complete for the data this account actually authored.
//
// The R06 purge removed the messages of the jobs the account *posted*. A worker's own messages in the
// resident's job survived, because the only way it looked for chat was by job id. The purge now also
// reads the jobs the account was assigned to and removes the messages it wrote there — the account's
// words, in someone else's job, which are still that account's personal data.
// ---------------------------------------------------------------------------------------------

/** Query used to find the jobs an account worked on (single-field index, no composite needed). */
export const ASSIGNED_JOB_FIELD = "assignedProviderUid";

/** The message author field, used to keep only this account's messages. */
export const MESSAGE_AUTHOR_FIELD = "senderId";

/** A message may be attributed by uid or (legacy) by name; only a uid is provable, so only a uid is deleted. */
export function messagesToPurge(
  docs: Array<{ id: string; data: Record<string, unknown> }>,
  uid: string
): string[] {
  if (!uid) return [];
  return docs
    .filter((doc) => doc.data[MESSAGE_AUTHOR_FIELD] === uid)
    .map((doc) => doc.id);
}

/**
 * The records the platform keeps after an erasure, in plain language.
 *
 * These are the legal holds from the retention policy, phrased for the person receiving the notice.
 * Naming them is the point: a deletion that silently keeps the payment books is a dark pattern, and
 * a deletion that destroys them is a compliance failure.
 */
export function retainedRecords(): Array<{ category: string; reason: string; retentionDays: number }> {
  return [
    {
      category: "payment and escrow records",
      reason: "Anti-money-laundering and tax rules require them, and they may show money you are still owed",
      retentionDays: 2555,
    },
    {
      category: "identity-verification evidence",
      reason: "KYC rules require the verification evidence to be kept after the account closes",
      retentionDays: 1825,
    },
    {
      category: "consent receipts",
      reason: "consent must stay demonstrable for as long as it is relied on",
      retentionDays: 2190,
    },
  ];
}

/**
 * The ordered erasure checklist. The handler follows it and `docs/privacy/DSAR.md` is written from it,
 * so the published procedure cannot describe a step the code does not take.
 */
export const ERASURE_STEPS: readonly string[] = [
  "Verify fresh authentication (auth_time within the window).",
  "Verify ownership of every requested job id before touching it.",
  "Purge Cloud Storage: users/{uid}/, avatars/{uid}/ and jobs/{jobId}/ for the verified jobs.",
  "Purge Firestore personal documents (users/{uid} + sub-collections, worker_kyc, worker_training, providers, guarantor_submissions).",
  "Purge chat: every message in the account's own jobs, and every message the account wrote in jobs it worked on.",
  "Purge query-scoped records: guarantors, notifications, disputes (both sides), job quotes.",
  "Delete the verified job documents last.",
  "Write an account_deletions audit record with counts, the retained categories and the notice.",
  "Refuse to delete the Firebase Auth user while anything is pending.",
] as const;
