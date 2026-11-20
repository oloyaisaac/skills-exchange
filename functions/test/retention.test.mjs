/**
 * R09-P2-04: retention, defined per category and enforced by a sweep.
 *
 * The review found no retention job, no TTL and no purge function: KYC documents, chats, SOS
 * incidents and notifications were kept for ever because nobody had decided otherwise. These tests
 * pin the windows, the actions and the safety rules (never purge what you cannot date, never blow
 * past the batch cap).
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  RETENTION_POLICY,
  LEGAL_HOLDS,
  categoryById,
  decideRetention,
  toMillis,
  planRetentionSweep,
  anonymisePatch,
  summariseRetention,
  retentionReportLine,
} from "../lib/retention.js";

const DAY = 24 * 60 * 60 * 1000;
const NOW = 1_800_000_000_000;

test("retention: every category states a window, an action and a reason", () => {
  assert.ok(RETENTION_POLICY.length >= 6);
  for (const category of RETENTION_POLICY) {
    assert.ok(category.retentionDays >= 1, `${category.id} has no window`);
    assert.ok(["PURGE", "ANONYMISE"].includes(category.action));
    assert.ok(category.basis.length > 20, `${category.id} has no honest reason`);
    if (category.action === "ANONYMISE") {
      assert.ok((category.blankFields ?? []).length > 0, `${category.id} anonymises nothing`);
    }
  }
  const ids = RETENTION_POLICY.map((category) => category.id);
  assert.deepEqual([...new Set(ids)], ids, "category ids are unique");
});

test("retention: legal holds are named, never swept", () => {
  const held = LEGAL_HOLDS.map((hold) => hold.id);
  assert.ok(held.includes("payments"), "financial records are held for AML/tax");
  assert.ok(held.includes("kyc"));
  assert.ok(held.includes("consent_proof"));
  for (const hold of LEGAL_HOLDS) {
    assert.ok(hold.reason.length > 20);
    assert.ok(hold.retentionDays >= 365);
  }
  const sweepIds = new Set(RETENTION_POLICY.map((category) => category.collection));
  for (const hold of LEGAL_HOLDS) {
    assert.ok(!sweepIds.has(hold.collection), `${hold.id} must not also be swept`);
  }
});

test("retention: an item inside its window is retained, outside it is due", () => {
  const category = categoryById("notifications");
  const fresh = decideRetention("notifications", NOW - 10 * DAY, NOW);
  assert.equal(fresh.action, "RETAIN");
  assert.equal(fresh.ageDays, 10);
  assert.equal(fresh.dueAt, NOW - 10 * DAY + category.retentionDays * DAY);

  const stale = decideRetention("notifications", NOW - 120 * DAY, NOW);
  assert.equal(stale.action, "PURGE");
  assert.equal(stale.ageDays, 120);

  const chat = decideRetention("chat_messages", NOW - 400 * DAY, NOW);
  assert.equal(chat.action, "ANONYMISE", "chat is minimised, not deleted, while disputes may need it");

  const sos = decideRetention("sos_incidents", NOW - 366 * DAY, NOW);
  assert.equal(sos.action, "ANONYMISE");
});

test("retention: an item that cannot be dated is retained and reported", () => {
  for (const value of [undefined, null, "", "not-a-date", 0, -5, {}, { seconds: "x" }]) {
    const decision = decideRetention("notifications", value, NOW);
    assert.equal(decision.action, "RETAIN", `${JSON.stringify(value)} must not be purged`);
    assert.equal(decision.dueAt, null);
  }
  assert.equal(decideRetention("unknown_category", NOW - 999 * DAY, NOW).action, "RETAIN");
});

test("retention: Firestore timestamps, millis and ISO strings all date an item", () => {
  const target = NOW - 100 * DAY;
  assert.equal(toMillis(target), target);
  assert.equal(toMillis({ toMillis: () => target }), target);
  assert.equal(toMillis({ seconds: Math.floor(target / 1000) }), Math.floor(target / 1000) * 1000);
  assert.equal(toMillis(new Date(target).toISOString()), target);
  assert.equal(toMillis({ toMillis: () => { throw new Error("boom"); } }), null);
});

test("retention: a sweep is bounded and plans purge and anonymise separately", () => {
  const docs = Array.from({ length: 30 }, (_, index) => ({
    id: `doc-${index}`,
    data: { createdAt: NOW - (100 + index) * DAY },
  }));
  const plan = planRetentionSweep({ categoryId: "notifications", now: NOW, docs, maxActions: 10 });
  assert.equal(plan.purgeIds.length, 10, "the cap is honoured");
  assert.equal(plan.retained, 20, "the rest is planned again next run");
  assert.equal(plan.anonymiseIds.length, 0);

  const chatDocs = [
    { id: "old", data: { timestamp: NOW - 400 * DAY } },
    { id: "new", data: { timestamp: NOW - 4 * DAY } },
    { id: "undated", data: {} },
  ];
  const chatPlan = planRetentionSweep({ categoryId: "chat_messages", now: NOW, docs: chatDocs });
  assert.deepEqual(chatPlan.anonymiseIds, ["old"]);
  assert.equal(chatPlan.retained, 1);
  assert.deepEqual(chatPlan.undatedIds, ["undated"]);
});

test("retention: anonymise blanks the personal fields and keeps a marker", () => {
  const patch = anonymisePatch("chat_messages");
  for (const field of categoryById("chat_messages").blankFields) {
    assert.equal(patch[field], "", `${field} must be blanked`);
  }
  assert.equal(patch.redactedBy, "retention_policy");
  assert.ok(typeof patch.redactedAt === "number");

  const sosPatch = anonymisePatch("sos_incidents");
  assert.equal(sosPatch.latitude, "");
  assert.equal(sosPatch.longitude, "");

  assert.deepEqual(anonymisePatch("notifications"), {}, "a PURGE category has nothing to anonymise");
  assert.deepEqual(anonymisePatch("nope"), {});
});

test("retention: the report counts actions and the log line leaks nothing", () => {
  const report = summariseRetention(NOW, [
    { id: "notifications", action: "PURGE", retentionDays: 90, purged: 12, anonymised: 0, retained: 3, undated: 1 },
    { id: "chat_messages", action: "ANONYMISE", retentionDays: 365, purged: 0, anonymised: 4, retained: 9, undated: 0 },
  ]);
  assert.equal(report.totalPurged, 12);
  assert.equal(report.totalAnonymised, 4);
  const line = retentionReportLine(report);
  assert.match(line, /purged=12/);
  assert.match(line, /anonymised=4/);
  assert.doesNotMatch(line, /doc-|senderId|nationalId/i);
});
