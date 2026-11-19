// REM-011 / REM-022: server-side OTP policy.
//
// The Africa's Talking credential lives only in the Cloud Functions environment. The Android
// client calls the `sendOtp` / `verifyOtp` callables, and a successful verification mints a
// Firebase custom token so the app ends up with a real Firebase UID instead of a local session.
//
// Everything in this module except the two callables is pure so it can be unit-tested without
// Firebase, network access or SMS credits.

export const OTP_SEND_COOLDOWN_MS = 60_000;
export const OTP_MAX_SENDS_PER_HOUR = 5;
export const OTP_TTL_MS = 10 * 60_000;
export const OTP_MAX_VERIFY_ATTEMPTS = 5;
export const OTP_MESSAGE_PREFIX = "Your Skills Exchange verification code is: ";

export interface OtpRateRecord {
  hourWindowStart?: number;
  hourCount?: number;
  lastSentAt?: number;
  attempts?: number;
  sentAt?: number;
  verified?: boolean;
  requestId?: string;
  phoneNumber?: string;
}

export interface RateDecision {
  allowed: boolean;
  reason?: "cooldown" | "hourly_limit" | "not_sent" | "expired" | "attempts_exhausted" | "already_verified";
  next: OtpRateRecord;
  remainingAttempts?: number;
}

/**
 * Normalise a Ugandan MSISDN to E.164 (`+2567XXXXXXXX`). Accepts `+256…`, `256…`, `0…` and the
 * bare nine-digit subscriber number. Rejects everything else rather than sending SMS to a
 * malformed destination.
 */
export function normalizeUgandanPhone(raw: unknown): string {
  if (typeof raw !== "string") {
    throw new Error("phone number is required");
  }
  const compact = raw.trim().replace(/[\s()-]/g, "");
  let subscriber: string;
  if (compact.startsWith("+256")) subscriber = compact.slice(4);
  else if (compact.startsWith("256")) subscriber = compact.slice(3);
  else if (compact.startsWith("0")) subscriber = compact.slice(1);
  else subscriber = compact;

  if (!/^7\d{8}$/.test(subscriber)) {
    throw new Error("invalid Ugandan phone number");
  }
  return `+256${subscriber}`;
}

/** Firestore document ids cannot contain `+`. */
export function otpDocId(phone: string): string {
  return phone.replace(/^\+/, "");
}

/** Mask a phone number before it appears in any log line. */
export function maskPhone(phone: string): string {
  if (phone.length < 6) return "***";
  return `${phone.slice(0, 4)}***${phone.slice(-2)}`;
}

export function evaluateSendRate(record: OtpRateRecord | undefined, now: number): RateDecision {
  const current = record ?? {};
  const lastSentAt = current.lastSentAt ?? 0;
  if (now - lastSentAt < OTP_SEND_COOLDOWN_MS) {
    return { allowed: false, reason: "cooldown", next: current };
  }

  const windowOpen = now - (current.hourWindowStart ?? 0) < 3_600_000;
  const count = windowOpen ? current.hourCount ?? 0 : 0;
  if (count >= OTP_MAX_SENDS_PER_HOUR) {
    return { allowed: false, reason: "hourly_limit", next: current };
  }

  return {
    allowed: true,
    next: {
      ...current,
      hourWindowStart: windowOpen ? current.hourWindowStart ?? now : now,
      hourCount: count + 1,
      lastSentAt: now,
      sentAt: now,
      attempts: 0,
      verified: false,
    },
  };
}

export function evaluateVerifyRate(record: OtpRateRecord | undefined, now: number): RateDecision {
  if (!record || typeof record.sentAt !== "number" || !record.requestId) {
    return { allowed: false, reason: "not_sent", next: record ?? {} };
  }
  if (record.verified === true) {
    return { allowed: false, reason: "already_verified", next: record };
  }
  if (now - record.sentAt > OTP_TTL_MS) {
    return { allowed: false, reason: "expired", next: record };
  }
  const attempts = record.attempts ?? 0;
  if (attempts >= OTP_MAX_VERIFY_ATTEMPTS) {
    return { allowed: false, reason: "attempts_exhausted", next: record, remainingAttempts: 0 };
  }
  return {
    allowed: true,
    next: { ...record, attempts: attempts + 1 },
    remainingAttempts: OTP_MAX_VERIFY_ATTEMPTS - attempts - 1,
  };
}

export interface OtpProviderResult {
  statusCode: number;
  requestId?: string;
  expiresIn?: number;
  valid: boolean;
  message: string;
}

/** Parse the Africa's Talking OTP send response. Tolerates both wrapped and flat shapes. */
export function parseSendOtpResponse(statusCode: number, body: unknown): OtpProviderResult {
  const root = (body ?? {}) as Record<string, unknown>;
  const data = (root.data ?? root) as Record<string, unknown>;
  const requestId =
    typeof data.requestId === "string" ? data.requestId :
    typeof root.requestId === "string" ? root.requestId : undefined;
  const expiresIn =
    typeof data.expiresIn === "number" ? data.expiresIn :
    typeof root.expiresIn === "number" ? root.expiresIn : undefined;
  const message =
    typeof data.message === "string" ? data.message :
    typeof root.message === "string" ? root.message : "";

  return {
    statusCode,
    requestId,
    expiresIn,
    valid: statusCode >= 200 && statusCode < 300 && typeof requestId === "string",
    message,
  };
}

/** Parse the Africa's Talking OTP verify response. */
export function parseVerifyOtpResponse(statusCode: number, body: unknown): OtpProviderResult {
  const root = (body ?? {}) as Record<string, unknown>;
  const data = (root.data ?? root) as Record<string, unknown>;
  const explicit =
    typeof data.valid === "boolean" ? data.valid :
    typeof root.valid === "boolean" ? root.valid : undefined;
  const message =
    typeof data.message === "string" ? data.message :
    typeof root.message === "string" ? root.message : "";

  return {
    statusCode,
    valid: explicit === true && statusCode >= 200 && statusCode < 300,
    message,
  };
}
