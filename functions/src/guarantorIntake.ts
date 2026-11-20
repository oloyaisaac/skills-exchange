// R10-P2-04: guarantor identity duplication and collusion.
//
// Exactly two guarantors were required and shape-validated, but the *same person* could be listed
// twice with different spellings, a worker could list themselves, and one NIN could back many
// accounts. Nothing compared submissions with each other: the review found no uniqueness check
// anywhere.
//
// This module makes the decision testable and pure. The callable supplies the evidence it gathered
// (fingerprints of the NINs already on file, phone keys already used, how many times this worker has
// filed in the window) and this code decides. It never sees a raw NIN: only the salted fingerprint
// from `pii.ninFingerprint`.

export const REQUIRED_GUARANTORS = 2;
/** A worker may correct a rejected submission, but not reload the queue all day. */
export const MAX_SUBMISSIONS_PER_WINDOW = 3;
export const SUBMISSION_WINDOW_MS = 24 * 60 * 60 * 1000;

export type IntakeCode =
  | "WRONG_COUNT"
  | "SELF_GUARANTOR"
  | "DUPLICATE_GUARANTOR"
  | "GUARANTOR_ALREADY_USED"
  | "NO_CONSENT"
  | "INCOMPLETE"
  | "RESUBMISSION_LIMIT"
  | "MISSING_EVIDENCE";

export interface IntakeRejection {
  code: IntakeCode;
  message: string;
  /** Which guarantor slot (0-based) the problem belongs to, when it belongs to one. */
  index?: number;
}

/** One guarantor as the callable has already normalised it. */
export interface GuarantorCandidate {
  index: number;
  /** Salted fingerprint of the NIN (`pii.ninFingerprint`), or "" when the salt is unavailable. */
  ninFingerprint: string;
  phoneKey: string;
  normalisedName: string;
  district: string;
  consentProvided: boolean;
  hasBothPhotos: boolean;
}

/** A guarantor already on file, as returned by the server-side queries. */
export interface ExistingGuarantor {
  workerUid: string;
  ninFingerprint: string;
  phoneKey: string;
}

export interface IntakeInput {
  workerUid: string;
  /** The worker's own fingerprint/phone key, so self-guaranteeing can be caught. */
  workerNinFingerprint: string;
  workerPhoneKey: string;
  candidates: GuarantorCandidate[];
  existing: ExistingGuarantor[];
  submissionsInWindow: number;
}

export interface IntakeDecision {
  ok: boolean;
  rejections: IntakeRejection[];
  /** Non-blocking signals for the reviewer (never a rejection on their own). */
  flags: string[];
}

/** Digits only, with the Ugandan country code and trunk zero removed: `+256 772 198 420` → `772198420`. */
export function phoneKey(raw: unknown): string {
  if (typeof raw !== "string") return "";
  const digits = raw.replace(/[^0-9]/g, "");
  if (digits.length < 9) return "";
  const withoutCountry = digits.startsWith("256") ? digits.slice(3) : digits;
  const withoutTrunk = withoutCountry.startsWith("0") ? withoutCountry.slice(1) : withoutCountry;
  return withoutTrunk.slice(-9);
}

/** Lowercase, collapse whitespace and punctuation: "Nakato  A." and "nakato a" are the same person. */
export function normaliseName(raw: unknown): string {
  if (typeof raw !== "string") return "";
  return raw
    .toLowerCase()
    .replace(/[^a-z ]/g, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/**
 * Decide whether a guarantor submission may be filed.
 *
 * Fail closed on every branch: a missing fingerprint is not "no duplicate", it is "cannot prove" —
 * and proof is the point of the control.
 */
export function reviewGuarantorIntake(input: IntakeInput): IntakeDecision {
  const rejections: IntakeRejection[] = [];
  const flags: string[] = [];
  const candidates = input.candidates ?? [];

  if (candidates.length !== REQUIRED_GUARANTORS) {
    rejections.push({
      code: "WRONG_COUNT",
      message: `Exactly ${REQUIRED_GUARANTORS} guarantors are required.`,
    });
    return { ok: false, rejections, flags };
  }

  if (input.submissionsInWindow >= MAX_SUBMISSIONS_PER_WINDOW) {
    rejections.push({
      code: "RESUBMISSION_LIMIT",
      message:
        "This account has already submitted guarantors several times today. Contact support rather than filing again.",
    });
  }

  for (const candidate of candidates) {
    if (!candidate.ninFingerprint) {
      rejections.push({
        code: "MISSING_EVIDENCE",
        index: candidate.index,
        message: "Identity verification is not configured on the server, so this cannot be checked. Try again later.",
      });
    }
    if (!candidate.phoneKey || !candidate.normalisedName) {
      rejections.push({
        code: "INCOMPLETE",
        index: candidate.index,
        message: "Each guarantor needs a full name and a phone number.",
      });
    }
    if (!candidate.hasBothPhotos) {
      rejections.push({
        code: "INCOMPLETE",
        index: candidate.index,
        message: "Each guarantor needs their own photograph and a photograph with you.",
      });
    }
    if (!candidate.consentProvided) {
      rejections.push({
        code: "NO_CONSENT",
        index: candidate.index,
        message: "Each guarantor must agree to their details being checked.",
      });
    }
    // A worker cannot guarantee themselves — the NIN or the phone is the same person.
    if (
      (candidate.ninFingerprint && candidate.ninFingerprint === input.workerNinFingerprint) ||
      (candidate.phoneKey && candidate.phoneKey === input.workerPhoneKey)
    ) {
      rejections.push({
        code: "SELF_GUARANTOR",
        index: candidate.index,
        message: "You cannot list yourself as your own guarantor.",
      });
    }
  }

  // Distinct humans: no two candidates may share a NIN, a phone or a name+district pair.
  const seenNin = new Map<string, number>();
  const seenPhone = new Map<string, number>();
  const seenName = new Map<string, number>();
  for (const candidate of candidates) {
    if (candidate.ninFingerprint) {
      const prior = seenNin.get(candidate.ninFingerprint);
      if (prior !== undefined) {
        rejections.push({
          code: "DUPLICATE_GUARANTOR",
          index: candidate.index,
          message: "Both guarantors must be different people — this one repeats the earlier ID number.",
        });
      } else {
        seenNin.set(candidate.ninFingerprint, candidate.index);
      }
    }
    if (candidate.phoneKey) {
      const prior = seenPhone.get(candidate.phoneKey);
      if (prior !== undefined) {
        rejections.push({
          code: "DUPLICATE_GUARANTOR",
          index: candidate.index,
          message: "Both guarantors must be different people — this one repeats the earlier phone number.",
        });
      } else {
        seenPhone.set(candidate.phoneKey, candidate.index);
      }
    }
    const nameKey = `${candidate.normalisedName}|${candidate.district.toLowerCase().trim()}`;
    if (candidate.normalisedName) {
      const prior = seenName.get(nameKey);
      if (prior !== undefined) {
        flags.push("SAME_NAME_AND_DISTRICT");
      } else {
        seenName.set(nameKey, candidate.index);
      }
    }
  }

  // Reuse across accounts: the same person may guarantee one worker, not a queue of them.
  const otherWorkers = (input.existing ?? []).filter(
    (row) => row.workerUid && row.workerUid !== input.workerUid
  );
  for (const candidate of candidates) {
    const ninTaken = candidate.ninFingerprint
      ? otherWorkers.find((row) => row.ninFingerprint && row.ninFingerprint === candidate.ninFingerprint)
      : undefined;
    const phoneTaken = candidate.phoneKey
      ? otherWorkers.find((row) => row.phoneKey && row.phoneKey === candidate.phoneKey)
      : undefined;
    if (ninTaken || phoneTaken) {
      rejections.push({
        code: "GUARANTOR_ALREADY_USED",
        index: candidate.index,
        message:
          "This guarantor is already listed on another account. A guarantor can verify one worker only.",
      });
    }
  }

  // Not a rejection: the worker is re-filing with the same people after a change request.
  const ownExisting = (input.existing ?? []).filter((row) => row.workerUid === input.workerUid);
  if (ownExisting.length > 0 && rejections.length === 0) flags.push("RESUBMISSION");

  return { ok: rejections.length === 0, rejections, flags };
}

/** A short, non-identifying description of why an intake was refused, for the audit record. */
export function describeRejections(rejections: IntakeRejection[]): string {
  const codes = [...new Set(rejections.map((rejection) => rejection.code))];
  return codes.join(",") || "NONE";
}
