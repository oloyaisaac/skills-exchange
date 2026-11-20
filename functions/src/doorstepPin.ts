// R07-P2-01: the doorstep PIN is a shared secret between the resident and the worker. It used to be
// verified on the phone by comparing the typed code with a code the *caller supplied*, with a
// well-known default ("4829"), in plaintext, with no attempt limit. Anyone who could call the
// function could pass `expectedPin == enteredPin`.
//
// The rules here are pure: hashing, constant-time comparison and the attempt/lockout state machine.
// `functions/src/index.ts` applies them to `job_secrets/{jobId}` (a collection no client can read)
// and returns only counts, never the stored hash.

import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

export const PIN_LENGTH = 4;
export const MAX_ATTEMPTS = 5;
export const LOCKOUT_MS = 15 * 60 * 1000;

/** A PIN is exactly four digits. "", "4829 " and "12345" are not PINs. */
export function isValidPin(pin: unknown): boolean {
  return typeof pin === "string" && /^[0-9]{4}$/.test(pin);
}

/** Random 16-byte salt, hex encoded. */
export function newSalt(bytes: number = 16): string {
  return randomBytes(bytes).toString("hex");
}

/** Salted SHA-256 of the PIN. The plaintext PIN is never stored. */
export function hashPin(pin: string, salt: string): string {
  return createHash("sha256").update(`${salt}:${pin}`).digest("hex");
}

/**
 * Constant-time comparison of the presented PIN against the stored hash.
 *
 * A missing or malformed stored hash fails closed: "no hash on file" can never mean "any PIN".
 */
export function pinMatches(pin: unknown, salt: unknown, hash: unknown): boolean {
  if (!isValidPin(pin) || typeof salt !== "string" || salt.length < 16) return false;
  if (typeof hash !== "string" || hash.length !== 64) return false;
  const presented = Buffer.from(hashPin(pin as string, salt), "hex");
  let stored: Buffer;
  try {
    stored = Buffer.from(hash, "hex");
  } catch {
    return false;
  }
  if (stored.length !== presented.length) return false;
  return timingSafeEqual(presented, stored);
}

export interface AttemptState {
  attempts: number;
  lockedUntil: number;
}

/** Reads the attempt state off a Firestore document, tolerating absent/garbage fields. */
export function attemptStateOf(data: Record<string, unknown> | undefined | null): AttemptState {
  const attempts = Number((data || {})["attempts"]);
  const lockedUntil = Number((data || {})["lockedUntil"]);
  return {
    attempts: Number.isFinite(attempts) && attempts > 0 ? Math.floor(attempts) : 0,
    lockedUntil: Number.isFinite(lockedUntil) && lockedUntil > 0 ? Math.floor(lockedUntil) : 0,
  };
}

/** Milliseconds left on a lockout, 0 when not locked. */
export function lockRemainingMs(state: AttemptState, nowMs: number): number {
  if (state.lockedUntil <= nowMs) return 0;
  return state.lockedUntil - nowMs;
}

export interface AttemptOutcome extends AttemptState {
  locked: boolean;
  retryAfterMs: number;
  remainingAttempts: number;
}

/**
 * The single decision table for one verification attempt.
 *
 * A locked secret refuses every attempt until the window passes, and a wrong PIN burns one of
 * `MAX_ATTEMPTS`; the last wrong attempt starts a fresh lockout so guessing is bounded in time as
 * well as in count. A correct PIN clears the counter.
 */
export function applyAttempt(state: AttemptState, success: boolean, nowMs: number): AttemptOutcome {
  const lockedFor = lockRemainingMs(state, nowMs);
  if (lockedFor > 0) {
    return {
      attempts: state.attempts,
      lockedUntil: state.lockedUntil,
      locked: true,
      retryAfterMs: lockedFor,
      remainingAttempts: 0,
    };
  }
  if (success) {
    return { attempts: 0, lockedUntil: 0, locked: false, retryAfterMs: 0, remainingAttempts: MAX_ATTEMPTS };
  }
  const attempts = state.attempts + 1;
  const locked = attempts >= MAX_ATTEMPTS;
  return {
    attempts: locked ? 0 : attempts,
    lockedUntil: locked ? nowMs + LOCKOUT_MS : 0,
    locked,
    retryAfterMs: locked ? LOCKOUT_MS : 0,
    remainingAttempts: locked ? 0 : MAX_ATTEMPTS - attempts,
  };
}

/** User-facing refusal text; never includes the PIN, the hash or the salt. */
export function refusalMessage(outcome: AttemptOutcome): string {
  if (outcome.locked) {
    const minutes = Math.max(1, Math.ceil(outcome.retryAfterMs / 60000));
    return `Too many incorrect PINs. Try again in ${minutes} minute${minutes === 1 ? "" : "s"}.`;
  }
  return `Incorrect PIN. ${outcome.remainingAttempts} attempt${outcome.remainingAttempts === 1 ? "" : "s"} left.`;
}
