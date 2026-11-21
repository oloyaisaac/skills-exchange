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
  messagesToPurge,
  retainedRecords,
  ERASURE_STEPS,
  ASSIGNED_JOB_FIELD,
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
    "private_docs/uid_42/",
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
    "private_docs/uid_1/",
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

// ---------------------------------------------------------------------------------------------
// R09-P2-01: erasure must also reach the account's own messages in jobs it did not post, and must
// say out loud what it keeps.
// ---------------------------------------------------------------------------------------------

test("erasure: only the account's own messages in a worked job are removed", () => {
  const docs = [
    { id: "m1", data: { senderId: "worker_1", text: "on my way" } },
    { id: "m2", data: { senderId: "resident_9", text: "thanks" } },
    { id: "m3", data: { senderId: "worker_1" } },
    { id: "m4", data: { senderName: "worker_1" } }, // legacy attribution is not provable
    { id: "m5", data: {} },
  ];
  assert.deepEqual(messagesToPurge(docs, "worker_1"), ["m1", "m3"]);
  assert.deepEqual(messagesToPurge(docs, ""), [], "no uid, nothing deleted");
  assert.deepEqual(messagesToPurge([], "worker_1"), []);
  assert.equal(ASSIGNED_JOB_FIELD, "assignedProviderUid", "the query the purge relies on");
});

test("erasure: the retained categories are named with a reason and a window", () => {
  const retained = retainedRecords();
  assert.ok(retained.length >= 3);
  const categories = retained.map((hold) => hold.category).join(" | ");
  assert.match(categories, /payment/);
  assert.match(categories, /identity-verification/);
  assert.match(categories, /consent/);
  for (const hold of retained) {
    assert.ok(hold.reason.length > 20);
    assert.ok(hold.retentionDays >= 365);
  }
});

test("erasure: the published procedure is the one the code follows", () => {
  assert.ok(ERASURE_STEPS.length >= 8);
  const steps = ERASURE_STEPS.join(" ");
  assert.match(steps, /fresh authentication/i);
  assert.match(steps, /Cloud Storage/);
  assert.match(steps, /chat/i);
  assert.match(steps, /Firebase Auth/);
  assert.equal(
    ERASURE_STEPS[ERASURE_STEPS.length - 1].includes("Auth"),
    true,
    "Auth is deleted last, and only when nothing is pending"
  );
});

test("erasure: every prefix a person can write documents to is purged", () => {
  const prefixes = storagePurgePrefixes("worker_1", ["job_1"]);
  // Identity documents live under users/{uid}/kyc|guarantors|certificates|verification.
  assert.ok(prefixes.includes("users/worker_1/"));
  assert.ok(prefixes.includes("avatars/worker_1/"));
  // Owner-writable in storage.rules, so it must not outlive the account even though the app does not
  // write there today.
  assert.ok(prefixes.includes("private_docs/worker_1/"));
  assert.ok(prefixes.includes("jobs/job_1/"));
  // Another account's prefix is never included.
  assert.equal(prefixes.some((prefix) => prefix.includes("worker_2")), false);
  // A blank job id cannot widen the purge to the whole jobs tree.
  assert.equal(storagePurgePrefixes("worker_1", ["  "]).some((p) => p === "jobs//"), false);
});
