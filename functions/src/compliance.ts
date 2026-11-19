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
