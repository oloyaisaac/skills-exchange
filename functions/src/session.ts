// REM-024: freshness of authentication for destructive actions, and session revocation.
//
// A signed-in session can live for days. Deleting an account is irreversible, so the server
// requires that the caller authenticated recently (`auth_time` in the verified ID token). The same
// policy is mirrored in Firestore rules for owner deletes; this module is its tested twin.

export const FRESH_AUTH_MAX_AGE_MS = 5 * 60_000;
export const FRESH_AUTH_CLOCK_SKEW_MS = 60_000;

/**
 * @param authTimeSeconds `context.auth.token.auth_time` — seconds since the epoch.
 * @param nowMs current time in milliseconds.
 */
export function isFreshAuth(
  authTimeSeconds: unknown,
  nowMs: number,
  maxAgeMs: number = FRESH_AUTH_MAX_AGE_MS
): boolean {
  if (typeof authTimeSeconds !== "number" || !Number.isFinite(authTimeSeconds)) return false;
  const age = nowMs - authTimeSeconds * 1000;
  return age >= -FRESH_AUTH_CLOCK_SKEW_MS && age <= maxAgeMs;
}

export interface RevocationRecord {
  uid: string;
  revokedAt: number;
  reason: "LOGOUT" | "ACCOUNT_DELETION";
}

export function buildRevocationRecord(
  uid: unknown,
  reason: unknown,
  now: number
): RevocationRecord {
  if (typeof uid !== "string" || uid.length < 6) throw new Error("uid is required");
  if (reason !== "LOGOUT" && reason !== "ACCOUNT_DELETION") {
    throw new Error("reason must be LOGOUT or ACCOUNT_DELETION");
  }
  return { uid, revokedAt: now, reason };
}
