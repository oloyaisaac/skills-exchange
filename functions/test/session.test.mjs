/** REM-024: fresh-auth and revocation policy. */
import test from "node:test";
import assert from "node:assert/strict";

import {
  isFreshAuth,
  buildRevocationRecord,
  FRESH_AUTH_MAX_AGE_MS,
} from "../lib/session.js";

const NOW = 1_800_000_000_000;
const seconds = (ms) => ms / 1000;

test("authentication within five minutes is fresh", () => {
  assert.equal(isFreshAuth(seconds(NOW - 60_000), NOW), true);
  assert.equal(isFreshAuth(seconds(NOW), NOW), true);
  assert.equal(isFreshAuth(seconds(NOW - FRESH_AUTH_MAX_AGE_MS + 1), NOW), true);
});

test("older, future-beyond-skew or missing auth_time is not fresh", () => {
  assert.equal(isFreshAuth(seconds(NOW - FRESH_AUTH_MAX_AGE_MS - 1), NOW), false);
  assert.equal(isFreshAuth(seconds(NOW + 2 * 60_000), NOW), false);
  assert.equal(isFreshAuth(undefined, NOW), false);
  assert.equal(isFreshAuth("1800000000", NOW), false);
  assert.equal(isFreshAuth(Number.NaN, NOW), false);
});

test("revocation records require a uid and a known reason", () => {
  assert.deepEqual(buildRevocationRecord("uid_123456", "LOGOUT", 5), {
    uid: "uid_123456",
    revokedAt: 5,
    reason: "LOGOUT",
  });
  assert.throws(() => buildRevocationRecord("", "LOGOUT", 5));
  assert.throws(() => buildRevocationRecord("uid_123456", "SOMETHING", 5));
});
