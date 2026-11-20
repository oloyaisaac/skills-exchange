// R09-P2-05 / REM-041: national-ID minimisation.
//
// A NIN is sensitive personal data: it is the key to a Ugandan identity. It was held in plaintext in
// Firestore `worker_kyc` and Room, copied into submissions and exports, and the UI told users it was
// "encrypted". None of that is minimisation.
//
// This module does the parts that are honest without pretending to be field encryption:
//   * a NIN is masked for anything that leaves the restricted documents (logs, support views, exports
//     of another person's data);
//   * a salted HMAC fingerprint lets the platform spot the same NIN in two submissions (fraud
//     detection) without keeping a second copy of the value in an index;
//   * log lines are scrubbed of NIN-like and phone-like patterns before they are written.
//
// It is NOT encryption of the stored value, and the privacy copy now says so.

import * as crypto from "crypto";

/**
 * A NIN as typed: two letters, a run of at least six digits, then up to ten alphanumerics — e.g.
 * `CM90012345678ABC`. The digit run is required so ordinary words ("identity") are never matched and
 * masked in a log.
 */
const NIN_PATTERN = /\b[A-Z]{2}[0-9]{6,}[A-Z0-9]{0,10}\b/gi;
/** Ugandan phone numbers in the shapes the app stores. */
const PHONE_PATTERN = /(\+?256|0)7\d{8}\b/g;

/**
 * Mask a NIN for display: keep the leading check letters and the last three characters, hide the
 * rest. Short or unrecognisable input becomes `***` rather than being echoed back.
 */
export function maskNin(raw: unknown): string {
  if (typeof raw !== "string") return "***";
  const value = raw.trim().replace(/\s+/g, "");
  if (value.length < 8) return "***";
  const head = value.slice(0, 2);
  const tail = value.slice(-3);
  return `${head}${"*".repeat(Math.max(1, value.length - 5))}${tail}`;
}

/**
 * The fingerprint of a NIN under a server-held salt.
 *
 * Two submissions of the same NIN produce the same fingerprint, so a duplicate guarantor can be
 * found without the fingerprint being reversible by anyone who does not hold the salt. Without a
 * salt the function returns an empty string and the caller must not store a fingerprint at all —
 * an unsalted hash of a NIN is brute-forceable in seconds.
 */
export function ninFingerprint(raw: unknown, salt: unknown): string {
  if (typeof raw !== "string" || typeof salt !== "string" || salt.trim().length < 16) return "";
  const normalised = raw.trim().toUpperCase().replace(/\s+/g, "");
  if (normalised.length < 6) return "";
  return crypto.createHmac("sha256", salt.trim()).update(normalised).digest("hex").slice(0, 32);
}

/** True when a string looks like a NIN, used to refuse one in an unexpected place. */
export function looksLikeNin(value: unknown): boolean {
  if (typeof value !== "string") return false;
  NIN_PATTERN.lastIndex = 0;
  const matches = value.match(NIN_PATTERN) ?? [];
  return matches.some((match) => match.length >= 8);
}

/**
 * Remove NINs and phone numbers from a log line.
 *
 * Applied to anything that carries free text a person typed (error messages, provider bodies,
 * notification payloads). The replacement keeps the shape so an operator can still tell that a
 * value was there.
 */
export function scrubForLog(value: unknown): string {
  if (value === null || value === undefined) return "";
  const text = String(value);
  return text
    .replace(PHONE_PATTERN, (match) => `${match.slice(0, 4)}***${match.slice(-2)}`)
    .replace(NIN_PATTERN, (match) => maskNin(match));
}

/**
 * Scrub every string value of a shallow record (a log context object).
 *
 * Nested objects are summarised rather than scrubbed, because a nested structure in a log line is a
 * design smell that should be fixed at the call site instead.
 */
export function scrubFields(fields: Record<string, unknown>): Record<string, string> {
  const scrubbed: Record<string, string> = {};
  for (const [key, value] of Object.entries(fields)) {
    if (value === null || value === undefined) {
      scrubbed[key] = "";
    } else if (typeof value === "object") {
      scrubbed[key] = "[object]";
    } else {
      scrubbed[key] = scrubForLog(value);
    }
  }
  return scrubbed;
}

export const PII_SCRUB_NOTE =
  "NINs and phone numbers are masked before they reach a log, a support view or another person's export.";

/**
 * R09-P2-03: a guarantor submission is not the worker's personal data alone.
 *
 * `guarantor_submissions/{uid}` holds two other people's NINs, phone numbers and private photo paths.
 * The worker may see that they submitted guarantors and the review state, but the export must not
 * hand over a second copy of someone else's identity document — so every third-party identifier is
 * masked or dropped here, on the server, before the envelope is built.
 */
export function sanitiseGuarantorSubmissionForExport(submission: unknown): Record<string, unknown> | null {
  if (!submission || typeof submission !== "object") return null;
  const record = submission as Record<string, unknown>;
  const guarantors = Array.isArray(record.guarantors) ? record.guarantors : [];

  return {
    id: typeof record.id === "string" ? record.id : "",
    status: typeof record.status === "string" ? record.status : "",
    submittedAt: toNumberOrNull(record.submittedAt),
    reviewedAt: toNumberOrNull(record.reviewedAt),
    reviewerFeedback: typeof record.reviewerFeedback === "string" ? record.reviewerFeedback : "",
    redactionNote:
      "Guarantor names, ID numbers, phone numbers and photograph paths are third-party data and are redacted from this export.",
    guarantors: guarantors.map((raw) => {
      const guarantor = (raw ?? {}) as Record<string, unknown>;
      return {
        relationship: typeof guarantor.relationship === "string" ? guarantor.relationship : "",
        district: typeof guarantor.district === "string" ? guarantor.district : "",
        consentProvided: guarantor.consentProvided === true,
        consentVersion: typeof guarantor.consentVersion === "string" ? guarantor.consentVersion : "",
        consentRecordedAt: toNumberOrNull(guarantor.consentRecordedAt),
        ninVerificationStatus:
          typeof guarantor.ninVerificationStatus === "string" ? guarantor.ninVerificationStatus : "PENDING",
        nationalIdNumber: maskNin(guarantor.nationalIdNumber),
      };
    }),
  };
}

function toNumberOrNull(value: unknown): number | null {
  const numeric = typeof value === "number" ? value : Number(value);
  return Number.isFinite(numeric) && numeric > 0 ? numeric : null;
}
