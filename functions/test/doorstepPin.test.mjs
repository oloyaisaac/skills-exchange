/** R07-P2-01: the doorstep PIN is hashed, compared in constant time and rate-limited. */
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import {
  PIN_LENGTH,
  MAX_ATTEMPTS,
  LOCKOUT_MS,
  isValidPin,
  newSalt,
  hashPin,
  pinMatches,
  attemptStateOf,
  lockRemainingMs,
  applyAttempt,
  refusalMessage,
} from "../lib/doorstepPin.js";

const NOW = 1_800_000_000_000;

test("a PIN is exactly four digits", () => {
  assert.equal(PIN_LENGTH, 4);
  for (const good of ["1234", "0000", "4829"]) assert.equal(isValidPin(good), true, good);
  for (const bad of ["", " 1234", "1234 ", "123", "12345", "abcd", "12a4", null, undefined, 1234]) {
    assert.equal(isValidPin(bad), false, String(bad));
  }
});

test("the stored value is a salted hash, never the PIN", () => {
  const salt = newSalt();
  assert.match(salt, /^[0-9a-f]{32}$/);
  const hash = hashPin("4829", salt);
  assert.match(hash, /^[0-9a-f]{64}$/);
  assert.equal(hash.includes("4829"), false);
  assert.equal(hashPin("4829", salt), hash, "hashing is deterministic for one salt");
  assert.notEqual(hashPin("4829", newSalt()), hash, "a different salt yields a different hash");
});

test("a wrong, blank or malformed PIN never matches", () => {
  const salt = newSalt();
  const hash = hashPin("4829", salt);
  assert.equal(pinMatches("4829", salt, hash), true);
  assert.equal(pinMatches("0000", salt, hash), false);
  assert.equal(pinMatches("", salt, hash), false);
  assert.equal(pinMatches("4829", salt, ""), false);
  assert.equal(pinMatches("4829", salt, "not-a-hash"), false);
  assert.equal(pinMatches("4829", "short", hash), false);
  assert.equal(pinMatches("4829", undefined, hash), false);
});

test("attempt state tolerates absent and garbage fields", () => {
  assert.deepEqual(attemptStateOf(undefined), { attempts: 0, lockedUntil: 0 });
  assert.deepEqual(attemptStateOf({ attempts: "3", lockedUntil: 0 }), { attempts: 3, lockedUntil: 0 });
  assert.deepEqual(attemptStateOf({ attempts: -2, lockedUntil: NaN }), { attempts: 0, lockedUntil: 0 });
  assert.equal(lockRemainingMs({ attempts: 0, lockedUntil: NOW + 1000 }, NOW), 1000);
  assert.equal(lockRemainingMs({ attempts: 0, lockedUntil: NOW - 1 }, NOW), 0);
});

test("five wrong PINs lock the job for fifteen minutes", () => {
  let state = { attempts: 0, lockedUntil: 0 };
  for (let attempt = 1; attempt < MAX_ATTEMPTS; attempt += 1) {
    const outcome = applyAttempt(state, false, NOW);
    assert.equal(outcome.locked, false);
    assert.equal(outcome.remainingAttempts, MAX_ATTEMPTS - attempt);
    state = { attempts: outcome.attempts, lockedUntil: outcome.lockedUntil };
  }
  const final = applyAttempt(state, false, NOW);
  assert.equal(final.locked, true);
  assert.equal(final.retryAfterMs, LOCKOUT_MS);
  assert.equal(final.attempts, 0, "the counter restarts after a lockout");

  // While locked, even the correct PIN is refused and the lock is not extended.
  const duringLock = applyAttempt({ attempts: 0, lockedUntil: final.lockedUntil }, true, NOW + 1000);
  assert.equal(duringLock.locked, true);
  assert.equal(duringLock.retryAfterMs, LOCKOUT_MS - 1000);

  // Once the window passes, the correct PIN is accepted and the counter clears.
  const after = applyAttempt({ attempts: 0, lockedUntil: final.lockedUntil }, true, NOW + LOCKOUT_MS + 1);
  assert.equal(after.locked, false);
  assert.equal(after.attempts, 0);
});

test("a successful attempt clears a partial counter and leaves no lock", () => {
  const outcome = applyAttempt({ attempts: 3, lockedUntil: 0 }, true, NOW);
  assert.deepEqual(
    { attempts: outcome.attempts, lockedUntil: outcome.lockedUntil, locked: outcome.locked },
    { attempts: 0, lockedUntil: 0, locked: false }
  );
  assert.equal(outcome.remainingAttempts, MAX_ATTEMPTS);
});

test("refusal text never echoes the PIN, the hash or the salt", () => {
  const incorrect = refusalMessage(applyAttempt({ attempts: 0, lockedUntil: 0 }, false, NOW));
  assert.match(incorrect, /attempt/);
  const locked = refusalMessage(applyAttempt({ attempts: MAX_ATTEMPTS - 1, lockedUntil: 0 }, false, NOW));
  assert.match(locked, /minute/);
  for (const text of [incorrect, locked]) {
    assert.equal(/[0-9a-f]{32}/.test(text), false, "no salt/hash fragment");
  }
});

test("the callables are server-held: no client-supplied expected PIN survives", () => {
  const here = dirname(fileURLToPath(import.meta.url));
  const source = readFileSync(join(here, "..", "src", "index.ts"), "utf8");
  assert.ok(source.includes("export const setDoorstepPin"));
  assert.ok(source.includes("export const verifyDoorstepPin"));
  assert.ok(source.includes('collection("job_secrets")'), "the hash lives in a server-only collection");
  assert.equal(
    source.includes("expectedPin"),
    false,
    "the server must never compare against a value supplied by the caller"
  );
});
