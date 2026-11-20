/** R06-P2-01 / R06-P2-02: account deletion ordering, Storage coverage and the fresh-auth gate. */
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import {
  FRESH_AUTH_WINDOW_SECONDS,
  SINGLE_DOC_COLLECTIONS,
  QUERY_DELETIONS,
  isFreshAuth,
  storagePurgePrefixes,
  mayDeleteAuthUser,
  deletionRefusalReason,
} from "../lib/accountDeletion.js";

const NOW = 1_800_000_000_000;
const seconds = (ms) => ms / 1000;

test("deletion requires an authentication younger than five minutes", () => {
  assert.equal(FRESH_AUTH_WINDOW_SECONDS, 300);
  assert.equal(isFreshAuth(seconds(NOW), NOW), true);
  assert.equal(isFreshAuth(seconds(NOW - 4 * 60_000), NOW), true);
  // Just outside the window, in the future (clock skew), missing, or a string: refused.
  assert.equal(isFreshAuth(seconds(NOW - 5 * 60_000 - 1), NOW), false);
  assert.equal(isFreshAuth(seconds(NOW + 2 * 60_000), NOW), false);
  assert.equal(isFreshAuth(undefined, NOW), false);
  assert.equal(isFreshAuth("1800000000", NOW), false);
  assert.equal(isFreshAuth(0, NOW), false);
});

test("storage purge covers every personal prefix, including job photos", () => {
  const prefixes = storagePurgePrefixes("uid_42", ["job_a", "job_b"]);
  assert.deepEqual(prefixes, [
    "avatars/uid_42/",
    "jobs/job_a/",
    "jobs/job_b/",
    "users/uid_42/",
  ]);
  // The `users/{uid}/` prefix is what holds KYC documents, certificates and guarantor portraits.
  assert.ok(prefixes.includes("users/uid_42/"));
});

test("storage purge rejects empty ids and de-duplicates job prefixes", () => {
  assert.deepEqual(storagePurgePrefixes("", ["job_a"]), []);
  assert.deepEqual(storagePurgePrefixes("uid_1", ["job_a", "job_a", "   "]), [
    "avatars/uid_1/",
    "jobs/job_a/",
    "users/uid_1/",
  ]);
});

test("the auth user may only be removed when nothing is pending", () => {
  assert.equal(mayDeleteAuthUser({ storagePending: 0, firestorePending: 0 }), true);
  assert.equal(mayDeleteAuthUser({ storagePending: 0, firestorePending: 1 }), false);
  assert.equal(mayDeleteAuthUser({ storagePending: 1, firestorePending: 0 }), false);
  assert.equal(mayDeleteAuthUser({ storagePending: 3, firestorePending: 2 }), false);
});

test("an incomplete purge explains itself and keeps the account", () => {
  const reason = deletionRefusalReason({ storagePending: 2, firestorePending: 3 });
  assert.match(reason, /3 record\(s\)/);
  assert.match(reason, /2 file\(s\)/);
  assert.match(reason, /account was kept/);
  assert.equal(deletionRefusalReason({ storagePending: 0, firestorePending: 0 }), "");
});

test("every personal collection the client wrote is covered by the purge", () => {
  const collections = new Set([
    ...SINGLE_DOC_COLLECTIONS,
    ...QUERY_DELETIONS.map((rule) => rule.collection),
  ]);
  for (const name of [
    "users",
    "worker_kyc",
    "worker_training",
    "providers",
    "guarantor_submissions",
    "guarantors",
    "notifications",
    "disputes",
    "job_quotes",
  ]) {
    assert.ok(collections.has(name), `${name} must be purged`);
  }
});

test("the deletion callable refuses a stale session and keeps auth as the last step", () => {
  const here = dirname(fileURLToPath(import.meta.url));
  const source = readFileSync(join(here, "..", "lib", "index.js"), "utf8");
  assert.ok(source.includes("deleteMyAccount"), "deleteMyAccount callable must exist");
  assert.ok(source.includes("REAUTH_REQUIRED"), "stale sessions must be told to re-authenticate");
  assert.ok(source.includes("PURGE_INCOMPLETE"), "an incomplete purge must be reported");
  // The server must purge Storage before reporting completion, and must never delete the auth user.
  const body = source.slice(source.indexOf("deleteMyAccount"));
  assert.ok(body.includes("storagePurgePrefixes"), "Storage purge must run server-side");
  assert.equal(
    body.slice(0, body.indexOf("clearMyPushTokens")).includes("deleteUser("),
    false,
    "the deletion callable must not delete the auth user itself"
  );
});

test("sign-out clears the device push token before the session ends", () => {
  const here = dirname(fileURLToPath(import.meta.url));
  const source = readFileSync(join(here, "..", "lib", "index.js"), "utf8");
  const body = source.slice(source.indexOf("clearMyPushTokens"));
  assert.ok(body.includes("push_tokens"), "the callable must target the push token sub-collection");
  assert.ok(body.includes("context.auth.uid"), "tokens are scoped to the authenticated uid");
});
