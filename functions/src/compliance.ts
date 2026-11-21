// REM-044: consent records and data-subject access requests.
//
// Consent is recorded server-side with a policy version and a timestamp so it is demonstrable
// rather than a client-side boolean. The export deliberately omits third-party guarantor
// identifiers (NIN, phone) — a worker is entitled to their own data, not to another person's
// identity document.

export const CONSENT_VERSION = "2026-11-19";
export const REQUIRED_CONSENT_KEYS = ["terms", "privacy", "dataProcessing"] as const;
export const DSAR_SLA_DAYS = 30;
export const CONSENT_VERSION_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export type ConsentKey = typeof REQUIRED_CONSENT_KEYS[number];

export interface ConsentInput {
  version?: unknown;
  grants?: Record<string, unknown>;
}

export interface ConsentRecord {
  version: string;
  acceptedAt: number;
  grants: Record<ConsentKey, true>;
  source: "android";
}

export function buildConsentRecord(input: ConsentInput | undefined, now: number): ConsentRecord {
  const version = typeof input?.version === "string" ? input.version.trim() : "";
  if (!CONSENT_VERSION_PATTERN.test(version)) {
    throw new Error("a dated policy version is required");
  }
  const grants = input?.grants ?? {};
  for (const key of REQUIRED_CONSENT_KEYS) {
    if (grants[key] !== true) {
      throw new Error(`consent for '${key}' is required`);
    }
  }
  return {
    version,
    acceptedAt: now,
    grants: { terms: true, privacy: true, dataProcessing: true },
    source: "android",
  };
}

export interface GuarantorExportRow {
  fullName?: unknown;
  relationship?: unknown;
  district?: unknown;
  consentProvided?: unknown;
  ninVerificationStatus?: unknown;
  status?: unknown;
}

/**
 * Strip every third-party identifier from a guarantor record before it is returned to the worker.
 * The worker may see that a guarantor exists and its review state — never the NIN, phone,
 * photographs or storage paths.
 */
export function sanitiseGuarantorForExport(row: GuarantorExportRow): Record<string, unknown> {
  return {
    fullName: typeof row.fullName === "string" ? row.fullName : "",
    relationship: typeof row.relationship === "string" ? row.relationship : "",
    district: typeof row.district === "string" ? row.district : "",
    consentProvided: row.consentProvided === true,
    ninVerificationStatus: typeof row.ninVerificationStatus === "string" ? row.ninVerificationStatus : "PENDING",
    status: typeof row.status === "string" ? row.status : "",
  };
}

export interface DsarEnvelope {
  generatedAt: number;
  slaDueAt: number;
  formatVersion: 1;
  data: Record<string, unknown>;
}

export function buildDsarEnvelope(data: Record<string, unknown>, now: number): DsarEnvelope {
  return {
    generatedAt: now,
    slaDueAt: now + DSAR_SLA_DAYS * 24 * 60 * 60 * 1000,
    formatVersion: 1,
    data,
  };
}

// ---------------------------------------------------------------------------------------------
// R09-P2-02: third-party guarantor consent.
//
// The worker's own acceptance (`terms`, `privacy`, `dataProcessing`) says nothing about the two
// people whose NINs, photographs and phone numbers the worker is about to upload. Their consent is a
// separate grant, recorded separately, with the policy version and the time it was given.
// ---------------------------------------------------------------------------------------------

export const GUARANTOR_CONSENT_SCOPE = "third_party_guarantor";
export const MAX_GUARANTORS_PER_SUBMISSION = 2;

export interface GuarantorConsentRecord {
  version: string;
  recordedAt: number;
  scope: typeof GUARANTOR_CONSENT_SCOPE;
  guarantorCount: number;
  /** The worker confirmed each guarantor agreed to their data being processed. */
  confirmedByWorker: true;
  /** The worker was told this is a separate, revocable grant. */
  separateGrant: true;
  source: "android";
}

/**
 * Build the record that proves third-party consent was given.
 *
 * Fail closed: a dated policy version, a sane guarantor count and an explicit confirmation are all
 * required. A missing confirmation is refused rather than recorded as consent.
 */
export function buildGuarantorConsentRecord(
  input: { version?: unknown; guarantorCount?: unknown; confirmed?: unknown } | undefined,
  now: number
): GuarantorConsentRecord {
  const version = typeof input?.version === "string" ? input.version.trim() : "";
  if (!CONSENT_VERSION_PATTERN.test(version)) {
    throw new Error("a dated guarantor consent version is required");
  }
  if (input?.confirmed !== true) {
    throw new Error("each guarantor must have been told and must have agreed before submission");
  }
  // Only a real number is accepted: a stringly-typed count is a sign the caller is guessing.
  const count = input?.guarantorCount;
  if (typeof count !== "number" || !Number.isInteger(count) || count < 1 || count > MAX_GUARANTORS_PER_SUBMISSION) {
    throw new Error(`guarantorCount must be between 1 and ${MAX_GUARANTORS_PER_SUBMISSION}`);
  }
  return {
    version,
    recordedAt: now,
    scope: GUARANTOR_CONSENT_SCOPE,
    guarantorCount: count,
    confirmedByWorker: true,
    separateGrant: true,
    source: "android",
  };
}

/**
 * Withdrawal of consent is recorded, not erased.
 *
 * A consent receipt that can be deleted proves nothing; the record keeps the original grant and adds
 * the revocation, so the platform can show it stopped relying on the grant from a given moment.
 */
export function buildConsentWithdrawal(
  input: { version?: unknown; reason?: unknown } | undefined,
  now: number
): { version: string; revokedAt: number; reason: string } {
  const version = typeof input?.version === "string" ? input.version.trim() : "";
  if (!CONSENT_VERSION_PATTERN.test(version)) {
    throw new Error("a dated policy version is required to withdraw consent");
  }
  const reason = typeof input?.reason === "string" ? input.reason.trim().slice(0, 200) : "";
  return { version, revokedAt: now, reason: reason || "withdrawn by the account holder" };
}

// ---------------------------------------------------------------------------------------------
// R09-P2-06: the erasure notice. What was deleted, what was kept, and why — told to the person, not
// only written in a runbook.
// ---------------------------------------------------------------------------------------------

export interface RetentionHold {
  /** Human name of the category, e.g. "payment records". */
  category: string;
  /** Why it is kept, in plain language. */
  reason: string;
  retentionDays: number;
}

/**
 * Plain-language notice for a completed (or blocked) erasure.
 *
 * The review's finding was that `DSAR.md` promised something the code did not do; the mirror image
 * of that failure is telling a person "everything is gone" when the payment books survive on
 * purpose. This text states both.
 */
export function buildErasureNotice(input: {
  deletedRecords: number;
  deletedFiles: number;
  retained: RetentionHold[];
  complete: boolean;
  pendingRecords?: number;
  pendingFiles?: number;
}): string {
  const parts: string[] = [];
  if (input.complete) {
    parts.push(
      `Deleted ${input.deletedRecords} record(s) and ${input.deletedFiles} file(s) from your account.`
    );
  } else {
    const pending: string[] = [];
    if ((input.pendingRecords ?? 0) > 0) pending.push(`${input.pendingRecords} record(s)`);
    if ((input.pendingFiles ?? 0) > 0) pending.push(`${input.pendingFiles} file(s)`);
    parts.push(
      `Deletion is not finished: ${pending.join(" and ")} could not be removed, so your account was kept and nothing was lost.`
    );
  }
  if (input.retained.length > 0) {
    const listed = input.retained
      .map((hold) => `${hold.category} (${Math.round(hold.retentionDays / 365 * 10) / 10} year(s): ${hold.reason})`)
      .join("; ");
    parts.push(`Kept because the law requires it: ${listed}.`);
  }
  return parts.join(" ");
}
