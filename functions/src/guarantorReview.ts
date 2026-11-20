/**
 * ADMIN-005 (REG-002 recovery, gap G2): pure core of the admin guarantor review path.
 *
 * Everything that decides *what an administrator may see and write* for a guarantor
 * submission lives here, deliberately free of `firebase-admin` / `firebase-functions`
 * imports so the whole security contract is unit-testable with `node --test`
 * (`functions/test/guarantorReview.test.mjs`) and so no rule can be bypassed by a
 * different caller in `index.ts`.
 *
 * Threat model implemented by this file:
 *  - Only a caller holding the `admin: true` Firebase custom claim may read or decide a
 *    guarantor submission (workers, clients and anonymous callers may not).
 *  - A NIN is a third party's identifier: it is masked in every list/queue payload and
 *    masked by default in the admin detail response too; the unmasked value is released
 *    only when an authorised admin detail call explicitly asks for it.
 *  - Verification photographs live under the private, owner-only Storage prefix
 *    `users/{workerId}/guarantors/**`. A URL is minted only for a path that really sits
 *    inside that worker's own prefix, so a tampered document can never steer an admin
 *    download into someone else's folder and nothing here makes the prefix public.
 *  - Workers own the facts they submit, administrators own the decision: the write
 *    payloads built here are a fixed allowlist of admin-owned fields.
 *
 * Field names and status values are reused from the existing implementation
 * (`firestore.rules`, `GuarantorValidators.kt`, `GuarantorEntity`, `WorkerKycEntity`,
 * `SkillsExchangeRepository.remoteGuarantorStatus`) — no parallel vocabulary is invented.
 */

/** Existing status vocabulary of `guarantor_submissions/{workerId}.status`. */
export type GuarantorSubmissionStatus =
  | "PENDING_REVIEW"
  | "UNDER_REVIEW"
  | "APPROVED"
  | "REJECTED"
  | "CHANGES_REQUIRED";

/** Existing per-guarantor `ninVerificationStatus` values. */
export type NinVerificationStatus = "PENDING" | "VERIFIED" | "FAILED";

/** Decision verbs accepted by the admin API — same set as `reviewWorkerApplication`. */
export const GUARANTOR_DECISIONS: readonly string[] = ["APPROVE", "REJECT", "CHANGES_REQUIRED"];

/** Statuses that mean "an admin still has to look at this". */
export const GUARANTOR_REVIEWABLE_STATUSES: readonly string[] = ["PENDING_REVIEW", "UNDER_REVIEW"];

/** Valid administrator input for the NIN verification result. */
export const GUARANTOR_NIN_STATUSES: readonly string[] = ["PENDING", "VERIFIED", "FAILED"];

/** Firestore document ids must stay inside this shape; guards `guarantor_submissions/{id}`. */
const WORKER_ID_PATTERN = /^[A-Za-z0-9_\-:.]+$/;
const MAX_WORKER_ID_LENGTH = 128;

/** Feedback shown to the worker. */
export const MIN_GUARANTOR_FEEDBACK_LENGTH = 10;
export const MAX_GUARANTOR_FEEDBACK_LENGTH = 500;

/** Private verification-photo prefix every minted URL must sit inside. */
export const GUARANTOR_PHOTO_PREFIX_SEGMENT = "guarantors";

/** Default lifetime of a minted private-photo URL: 15 minutes, capped at 1 hour. */
export const DEFAULT_PHOTO_URL_TTL_SECONDS = 900;
export const MAX_PHOTO_URL_TTL_SECONDS = 3600;

/** The 48h review SLA promised to the worker (`reviewWorkerApplication` uses the same). */
export const REVIEW_SLA_MILLISECONDS = 48 * 60 * 60 * 1000;

/** `functions.https.CallableContext` as far as this file is concerned. */
export interface AdminCheckContext {
  readonly auth?: {
    readonly uid?: string;
    readonly token?: unknown;
  } | null;
}

/**
 * Reads the `admin` claim out of a *verified* ID token. The token is treated as opaque
 * data here: `firebase-functions` has already verified the signature before handing the
 * context to a callable, so this is the same trust anchor the existing `isAdmin()` helper
 * and the `isAdmin()` rule in `firestore.rules` use.
 */
function hasAdminClaim(token: unknown): boolean {
  if (typeof token !== "object" || token === null) return false;
  return (token as { readonly admin?: unknown }).admin === true;
}


/** Error codes a callable may surface; keeps `index.ts` free of hand-written strings. */
export type CallableErrorCode =
  | "unauthenticated"
  | "permission-denied"
  | "invalid-argument"
  | "not-found"
  | "failed-precondition";

export type ValidationResult<T> =
  | { readonly ok: true; readonly value: T }
  | { readonly ok: false; readonly code: CallableErrorCode; readonly message: string };

/** A Firestore document body, seen only as loose data so this file stays dependency-free. */
export type DocumentBody = Record<string, unknown>;

/** One decision, validated and ready to write. */
export interface GuarantorReviewCommand {
  readonly workerId: string;
  readonly adminUid: string;
  readonly decision: string;
  readonly status: GuarantorSubmissionStatus;
  readonly feedback: string;
  readonly ninStatus: NinVerificationStatus;
}

/** Result of the private-photo access check for one stored Storage path. */
export interface PhotoAccess {
  readonly slot: string;
  readonly storagePath: string;
  readonly available: boolean;
  readonly downloadUrl?: string;
  readonly expiresAt?: number;
  readonly error?: string;
}

/** What an admin detail screen may see about one guarantor. */
export interface AdminGuarantorView {
  readonly id: string;
  readonly ninMasked: string;
  readonly nationalIdNumber?: string;
  readonly fullName: string;
  readonly relationship: string;
  readonly district: string;
  readonly phoneNumber: string;
  readonly consentProvided: boolean;
  readonly ninVerificationStatus: string;
  readonly verifiedBy: string;
  readonly submittedAt: number;
  readonly updatedAt: number;
  readonly photos: {
    readonly portrait: PhotoAccess;
    readonly withWorker: PhotoAccess;
  };
}

/** What an admin detail screen may see about the submission as a whole. */
export interface AdminSubmissionView {
  readonly workerId: string;
  readonly exists: true;
  readonly status: string;
  readonly submittedAt: number;
  readonly reviewSlaDeadlineAt: number;
  readonly reviewFeedback: string;
  readonly reviewedAt: number;
  readonly reviewedBy: string;
  readonly ninVerificationStatus: string;
  readonly guarantorCount: number;
  readonly guarantorsAwaitingReview: boolean;
  readonly workerKycStatus: string;
  readonly groupPhoto: PhotoAccess;
  readonly guarantors: AdminGuarantorView[];
  readonly photoUrlTtlSeconds: number;
}

/** One row of `getPendingVerificationQueue`. */
export interface AdminQueueRow {
  readonly userId: string;
  readonly fullLegalName: string;
  /** Masked by construction — the queue never carries a full NIN. */
  readonly nationalIdNumberMasked: string;
  readonly status: string;
  readonly submittedAt: number;
  readonly slaDeadline: number;
  readonly idFrontStoragePath: string;
  readonly idBackStoragePath: string;
  readonly reviewFeedback: string;
  readonly kycOnFile: boolean;
  readonly guarantorStatus: string;
  readonly guarantorSubmittedAt: number;
  readonly guarantorCount: number;
  readonly ninVerificationStatus: string;
  readonly guarantorsAwaitingReview: boolean;
  readonly sortTimestamp: number;
}

// ============================ primitives ============================

function asString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function asNumber(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  return 0;
}

function asBoolean(value: unknown): boolean {
  return value === true;
}

/**
 * Masked display form of an identifier — the exact TypeScript twin of
 * `GuarantorValidators.maskIdentifier` in the Android app: keeps at most the last two
 * characters (`"*******42"`) so a listing never prints a whole NIN.
 */
export function maskIdentifier(value: unknown): string {
  const trimmed = asString(value);
  if (trimmed.length === 0) return "";
  if (trimmed.length <= 2) return "*".repeat(trimmed.length);
  return "*".repeat(trimmed.length - 2) + trimmed.slice(-2);
}

/**
 * ADMIN-005: the single authorization gate for the guarantor review endpoints.
 * Same trust anchor as the existing `isAdmin()` helper and the `isAdmin()` rule in
 * `firestore.rules`: the `admin` claim of the *verified* ID token. A role supplied by
 * the client is never read, so a caller cannot choose their own authorization level.
 */
export function checkAdminCaller(
  context: AdminCheckContext | null | undefined
): ValidationResult<string> {
  const auth = context ? context.auth : null;
  if (!auth || typeof auth.uid !== "string" || auth.uid.length === 0) {
    return { ok: false, code: "unauthenticated", message: "Sign in before calling this endpoint." };
  }
  if (!hasAdminClaim(auth.token)) {
    return { ok: false, code: "permission-denied", message: "Admin access required." };
  }
  return { ok: true, value: auth.uid };
}

/** Validates the `workerId` argument before it is ever used as a document path. */
export function readWorkerId(data: DocumentBody | null | undefined): ValidationResult<string> {
  const raw = data ? data["workerId"] : undefined;
  const workerId = typeof raw === "string" ? raw.trim() : "";
  if (workerId.length === 0) {
    return { ok: false, code: "invalid-argument", message: "Missing required parameter: workerId." };
  }
  if (workerId.length > MAX_WORKER_ID_LENGTH || !WORKER_ID_PATTERN.test(workerId)) {
    return { ok: false, code: "invalid-argument", message: "workerId is not a valid user identifier." };
  }
  return { ok: true, value: workerId };
}

/** `APPROVE | REJECT | CHANGES_REQUIRED` -> the existing `guarantor_submissions` status. */
export function mapDecisionToStatus(decision: unknown): GuarantorSubmissionStatus | null {
  switch (asString(decision).toUpperCase()) {
    case "APPROVE":
      return "APPROVED";
    case "REJECT":
      return "REJECTED";
    case "CHANGES_REQUIRED":
      return "CHANGES_REQUIRED";
    default:
      return null;
  }
}

/** Absent/empty means "not verified yet"; anything outside the known set is rejected. */
export function normalizeNinStatus(value: unknown): NinVerificationStatus | null {
  const raw = asString(value);
  if (raw.length === 0) return "PENDING";
  const upper = raw.toUpperCase();
  return (GUARANTOR_NIN_STATUSES as readonly string[]).includes(upper)
    ? (upper as NinVerificationStatus)
    : null;
}

/**
 * ADMIN-005: validates an administrator review payload. The reviewer's uid is taken from
 * the verified token by the caller and never from `data`, so a review can never be
 * attributed to somebody else, and no field the worker owns (names, NINs, photos,
 * consent) can be smuggled in through this call.
 */
export function validateGuarantorReviewInput(
  data: DocumentBody | null | undefined,
  adminUid: string
): ValidationResult<GuarantorReviewCommand> {
  if (!data || typeof data !== "object") {
    return { ok: false, code: "invalid-argument", message: "A review payload is required." };
  }

  const workerIdResult = readWorkerId(data);
  if (!workerIdResult.ok) return workerIdResult;

  const status = mapDecisionToStatus(data["decision"]);
  if (!status) {
    return {
      ok: false,
      code: "invalid-argument",
      message: `decision must be one of: ${GUARANTOR_DECISIONS.join(", ")}.`
    };
  }

  const feedback = asString(data["feedback"]);
  const feedbackRequired = status === "REJECTED" || status === "CHANGES_REQUIRED";
  if (feedbackRequired && feedback.length < MIN_GUARANTOR_FEEDBACK_LENGTH) {
    return {
      ok: false,
      code: "invalid-argument",
      message: `feedback is required for ${status} and must be at least ${MIN_GUARANTOR_FEEDBACK_LENGTH} characters.`
    };
  }
  if (feedback.length > MAX_GUARANTOR_FEEDBACK_LENGTH) {
    return {
      ok: false,
      code: "invalid-argument",
      message: `feedback must be at most ${MAX_GUARANTOR_FEEDBACK_LENGTH} characters.`
    };
  }

  const ninStatus = normalizeNinStatus(data["ninStatus"]);
  if (!ninStatus) {
    return {
      ok: false,
      code: "invalid-argument",
      message: `ninStatus must be one of: ${GUARANTOR_NIN_STATUSES.join(", ")}.`
    };
  }

  return {
    ok: true,
    value: {
      workerId: workerIdResult.value,
      adminUid: asString(adminUid),
      decision: asString(data["decision"]).toUpperCase(),
      status,
      feedback,
      ninStatus
    }
  };
}

/**
 * True only for an object inside the *submitting worker's own* private verification
 * prefix `users/{workerId}/guarantors/...`. This is what stops a tampered document (or a
 * buggy one) from pointing an administrator's generated URL at another person's KYC
 * folder, and it refuses absolute URLs so a stored path can never be a ready-made
 * public link.
 */
export function isPrivateGuarantorPhotoPath(path: unknown, workerId: string): boolean {
  const raw = asString(path);
  if (raw.length === 0 || raw.includes("://") || raw.includes("..") || raw.startsWith("/")) return false;
  if (!workerId) return false;
  const segments = raw.split("/");
  return (
    segments.length >= 4 &&
    segments[0] === "users" &&
    segments[1] === workerId &&
    segments[2] === GUARANTOR_PHOTO_PREFIX_SEGMENT &&
    segments[3].length > 0
  );
}

/** Clamps the configured signed-URL lifetime into a sane range. */
export function resolvePhotoUrlTtlSeconds(configured: unknown): number {
  const raw = asNumber(configured);
  if (raw <= 0) return DEFAULT_PHOTO_URL_TTL_SECONDS;
  return Math.min(Math.floor(raw), MAX_PHOTO_URL_TTL_SECONDS);
}

/**
 * The admin-owned fields written to `guarantor_submissions/{workerId}` by a review:
 * decision, feedback, NIN result, reviewer identity and review timestamp. Everything the
 * worker filed stays untouched. `reviewedAt` is passed in so `index.ts` can hand
 * Firestore `FieldValue.serverTimestamp()` while the tests can assert the shape.
 */
export function buildGuarantorReviewFields<T>(
  command: GuarantorReviewCommand,
  reviewedAt: T
): Record<string, string | T> {
  return {
    status: command.status,
    reviewFeedback: command.feedback,
    ninVerificationStatus: command.ninStatus,
    reviewedBy: command.adminUid,
    reviewedAt,
    updatedAt: reviewedAt
  };
}

/**
 * The admin-owned fields written to each `guarantors/{id}` document of the reviewed
 * submission (`ninVerificationStatus` / `verifiedAt` / `verifiedBy` are exactly the keys
 * `firestore.rules` reserves for administrators).
 */
export function buildGuarantorNinUpdate<T>(
  command: GuarantorReviewCommand,
  verifiedAt: T
): Record<string, string | T> {
  return {
    ninVerificationStatus: command.ninStatus,
    verifiedAt,
    verifiedBy: command.adminUid,
    updatedAt: verifiedAt
  };
}

// ============================ private photo access ============================

/** Outcome of one minted download URL, keyed by the Storage path it was minted for. */
export interface MintedUrl {
  readonly url: string | null;
  readonly error?: string;
}

/** Own-property lookup so a stored path can never reach an inherited map entry. */
function lookupMintedUrl(map: Record<string, MintedUrl>, key: string): MintedUrl | undefined {
  return Object.prototype.hasOwnProperty.call(map, key) ? map[key] : undefined;
}

/**
 * Decides whether one stored photograph path may be shown to an administrator, and for
 * how long. A URL is only ever attached to a path inside the reviewed worker's own
 * private prefix; every other case reports *why* access was refused and hands back no
 * URL. Nothing here changes Storage rules — the prefix stays unreadable to clients.
 */
export function buildPhotoAccess(input: {
  slot: string;
  storagePath: unknown;
  workerId: string;
  urlsByPath: Record<string, MintedUrl>;
  now: number;
  ttlSeconds: number;
}): PhotoAccess {
  const raw = asString(input.storagePath);
  if (raw.length === 0) {
    return { slot: input.slot, storagePath: "", available: false, error: "MISSING" };
  }
  if (!isPrivateGuarantorPhotoPath(raw, input.workerId)) {
    return { slot: input.slot, storagePath: raw, available: false, error: "OUTSIDE_PRIVATE_PREFIX" };
  }
  const minted = lookupMintedUrl(input.urlsByPath, raw);
  const url = minted && typeof minted.url === "string" && minted.url.length > 0 ? minted.url : null;
  if (!url) {
    const reason = minted ? asString(minted.error) : "NOT_MINTED";
    return { slot: input.slot, storagePath: raw, available: false, error: reason || "URL_UNAVAILABLE" };
  }
  return {
    slot: input.slot,
    storagePath: raw,
    available: true,
    downloadUrl: url,
    expiresAt: input.now + input.ttlSeconds * 1000
  };
}

// ============================ admin detail projection ============================

function photoSlotNames(index: number): { portrait: string; withWorker: string } {
  return { portrait: `guarantor${index}_portrait`, withWorker: `guarantor${index}_with_worker` };
}

/**
 * One guarantor as an authorised administrator may see it. The NIN is masked unless the
 * detail call explicitly asked for the full value, and a photo URL is attached only when
 * the stored path really belongs to this worker's private prefix.
 */
export function projectGuarantorForAdmin(input: {
  id: string;
  body: DocumentBody;
  slot: number;
  workerId: string;
  includeFullNin: boolean;
  urlsByPath: Record<string, MintedUrl>;
  now: number;
  ttlSeconds: number;
}): AdminGuarantorView {
  const body = input.body;
  const nin = asString(body["nationalIdNumber"]);
  const slots = photoSlotNames(input.slot);
  const view: AdminGuarantorView = {
    id: input.id,
    ninMasked: maskIdentifier(nin),
    fullName: asString(body["fullName"]),
    relationship: asString(body["relationship"]),
    district: asString(body["district"]),
    phoneNumber: asString(body["phoneNumber"]),
    consentProvided: asBoolean(body["consentProvided"]),
    ninVerificationStatus: asString(body["ninVerificationStatus"]) || "PENDING",
    verifiedBy: asString(body["verifiedBy"]),
    submittedAt: asNumber(body["submittedAt"]),
    updatedAt: asNumber(body["updatedAt"]),
    photos: {
      portrait: buildPhotoAccess({
        slot: slots.portrait,
        storagePath: body["guarantorPhotoStoragePath"],
        workerId: input.workerId,
        urlsByPath: input.urlsByPath,
        now: input.now,
        ttlSeconds: input.ttlSeconds
      }),
      withWorker: buildPhotoAccess({
        slot: slots.withWorker,
        storagePath: body["workerWithGuarantorPhotoStoragePath"],
        workerId: input.workerId,
        urlsByPath: input.urlsByPath,
        now: input.now,
        ttlSeconds: input.ttlSeconds
      })
    }
  };
  if (!input.includeFullNin) return view;
  return { ...view, nationalIdNumber: nin };
}

/**
 * The private objects an administrator needs for one submission — group photograph plus
 * each guarantor's portrait and worker+guarantor photograph — after the private-prefix
 * check. `index.ts` mints a short-lived URL for each of these and nothing else, so the
 * download surface is exactly the five images named in the requirement.
 */
export function collectPrivatePhotoPaths(input: {
  workerId: string;
  submission: DocumentBody;
  guarantorDocs: Array<{ id: string; body: DocumentBody }>;
}): string[] {
  const candidates: unknown[] = [input.submission["groupPhotoStoragePath"]];
  for (const doc of input.guarantorDocs) {
    candidates.push(doc.body["guarantorPhotoStoragePath"]);
    candidates.push(doc.body["workerWithGuarantorPhotoStoragePath"]);
  }
  const seen: string[] = [];
  for (const candidate of candidates) {
    const raw = asString(candidate);
    if (!isPrivateGuarantorPhotoPath(raw, input.workerId)) continue;
    if (!seen.includes(raw)) seen.push(raw);
  }
  return seen;
}

/**
 * `getGuarantorSubmission` response body. Guarantor documents are projected in the order
 * of the submission's own `guarantorIds` list when present, so the admin sees
 * "Guarantor 1 / Guarantor 2" in the order the worker filed them.
 */
export function projectSubmissionForAdmin(input: {
  workerId: string;
  submission: DocumentBody;
  guarantorDocs: Array<{ id: string; body: DocumentBody }>;
  workerKycStatus: string;
  includeFullNin: boolean;
  urlsByPath: Record<string, MintedUrl>;
  now: number;
  ttlSeconds: number;
}): AdminSubmissionView {
  const byId: Record<string, DocumentBody> = Object.create(null);
  for (const doc of input.guarantorDocs) byId[doc.id] = doc.body;

  const filedIds: string[] = [];
  const rawIds = input.submission["guarantorIds"];
  if (Array.isArray(rawIds)) {
    for (const raw of rawIds) {
      const id = asString(raw);
      if (id.length > 0 && lookupOwn(byId, id)) filedIds.push(id);
    }
  }
  const orderedIds = filedIds.length > 0 ? filedIds : input.guarantorDocs.map((doc) => doc.id);

  const guarantors = orderedIds.map((id, index) =>
    projectGuarantorForAdmin({
      id,
      slot: index + 1,
      body: byId[id],
      workerId: input.workerId,
      includeFullNin: input.includeFullNin,
      urlsByPath: input.urlsByPath,
      now: input.now,
      ttlSeconds: input.ttlSeconds
    })
  );

  const status = asString(input.submission["status"]) || "PENDING_REVIEW";
  const submittedAt = asNumber(input.submission["submittedAt"]);
  return {
    workerId: input.workerId,
    exists: true,
    status,
    submittedAt,
    reviewSlaDeadlineAt: submittedAt > 0 ? submittedAt + REVIEW_SLA_MILLISECONDS : 0,
    reviewFeedback: asString(input.submission["reviewFeedback"]),
    reviewedAt: asNumber(input.submission["reviewedAt"]),
    reviewedBy: asString(input.submission["reviewedBy"]),
    ninVerificationStatus: asString(input.submission["ninVerificationStatus"]) || "PENDING",
    guarantorCount: guarantors.length,
    guarantorsAwaitingReview: GUARANTOR_REVIEWABLE_STATUSES.includes(status),
    workerKycStatus: input.workerKycStatus,
    groupPhoto: buildPhotoAccess({
      slot: "group_photo",
      storagePath: input.submission["groupPhotoStoragePath"],
      workerId: input.workerId,
      urlsByPath: input.urlsByPath,
      now: input.now,
      ttlSeconds: input.ttlSeconds
    }),
    guarantors,
    photoUrlTtlSeconds: input.ttlSeconds
  };
}

// ============================ admin queue projection ============================

/**
 * One `getPendingVerificationQueue` row. The worker's NIN is only ever emitted masked —
 * the queue is a list, and the reviewer can open the protected detail callable for the
 * unmasked value. Guarantor state is attached so an administrator can see submissions
 * that are waiting even when the worker's `worker_kyc` document is not on file (see the
 * `worker_kyc` synchronisation blocker recorded in AGENT_STATE.md).
 * No Storage URL is ever emitted here: only path strings, which are useless without an
 * authenticated admin session.
 */
export function projectQueueRow(input: {
  userId: string;
  kyc: DocumentBody | null;
  submission: DocumentBody | null;
  fullName: string;
  now: number;
}): AdminQueueRow {
  const kyc = input.kyc;
  const submission = input.submission;
  const submittedAt = asNumber(kyc ? kyc["submittedAt"] : undefined);
  const guarantorSubmittedAt = asNumber(submission ? submission["submittedAt"] : undefined);
  const status = kyc ? asString(kyc["status"]) || "NOT_STARTED" : "NOT_STARTED";
  const guarantorStatus = submission ? asString(submission["status"]) || "PENDING_REVIEW" : "";

  const sortCandidates = [submittedAt, guarantorSubmittedAt].filter((value) => value > 0);
  const sortTimestamp = sortCandidates.length > 0 ? Math.min.apply(null, sortCandidates) : 0;

  return {
    userId: input.userId,
    fullLegalName: asString(kyc ? kyc["fullLegalName"] : undefined) || asString(input.fullName),
    nationalIdNumberMasked: maskIdentifier(kyc ? kyc["nationalIdNumber"] : ""),
    status,
    submittedAt,
    slaDeadline: submittedAt > 0 ? submittedAt + REVIEW_SLA_MILLISECONDS : 0,
    idFrontStoragePath: asString(kyc ? kyc["idFrontStoragePath"] : ""),
    idBackStoragePath: asString(kyc ? kyc["idBackStoragePath"] : ""),
    reviewFeedback: asString(kyc ? kyc["reviewFeedback"] : ""),
    kycOnFile: kyc !== null,
    guarantorStatus,
    guarantorSubmittedAt,
    guarantorCount: asNumber(submission ? submission["guarantorCount"] : undefined),
    ninVerificationStatus: submission ? asString(submission["ninVerificationStatus"]) || "PENDING" : "",
    guarantorsAwaitingReview:
      submission !== null && GUARANTOR_REVIEWABLE_STATUSES.includes(guarantorStatus),
    sortTimestamp
  };
}

/**
 * Merges the KYC queue and the guarantor queue into one SLA-ordered list without
 * duplicating a worker who appears in both. Rows with no timestamp at all sort last.
 */
export function buildQueueRows(input: {
  kycDocs: Array<{ id: string; body: DocumentBody }>;
  submissionDocs: Array<{ id: string; body: DocumentBody }>;
  namesByWorkerId: Record<string, string>;
  now: number;
  limit: number;
}): AdminQueueRow[] {
  const kycById: Record<string, DocumentBody> = Object.create(null);
  const submissionById: Record<string, DocumentBody> = Object.create(null);
  const ids: string[] = [];

  const remember = (id: string): void => {
    if (!ids.includes(id)) ids.push(id);
  };

  for (const doc of input.kycDocs) {
    kycById[doc.id] = doc.body;
    remember(doc.id);
  }
  for (const doc of input.submissionDocs) {
    submissionById[doc.id] = doc.body;
    remember(doc.id);
  }

  const rows = ids.map((id) =>
    projectQueueRow({
      userId: id,
      kyc: lookupOwn(kycById, id),
      submission: lookupOwn(submissionById, id),
      fullName: lookupOwnString(input.namesByWorkerId, id),
      now: input.now
    })
  );

  rows.sort((left, right) => {
    const leftKey = left.sortTimestamp > 0 ? left.sortTimestamp : Number.MAX_SAFE_INTEGER;
    const rightKey = right.sortTimestamp > 0 ? right.sortTimestamp : Number.MAX_SAFE_INTEGER;
    if (leftKey !== rightKey) return leftKey - rightKey;
    return left.userId < right.userId ? -1 : left.userId > right.userId ? 1 : 0;
  });

  return input.limit > 0 ? rows.slice(0, input.limit) : rows;
}

function lookupOwn<T>(map: Record<string, T>, key: string): T | null {
  return Object.prototype.hasOwnProperty.call(map, key) ? map[key] : null;
}

function lookupOwnString(map: Record<string, string>, key: string): string {
  const value = lookupOwn(map, key);
  return typeof value === "string" ? value : "";
}






