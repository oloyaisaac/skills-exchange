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
  const prefixes = new Set<string>([`users/${uid}/`, `avatars/${uid}/`]);
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
