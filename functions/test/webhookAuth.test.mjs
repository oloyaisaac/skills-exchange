/** REM-021: MoMo callback authentication, freshness and throttling policy. */
import test from "node:test";
import assert from "node:assert/strict";

import {
  authorizeWebhook,
  loadWebhookSecrets,
  parseWebhookTimestamp,
  evaluateWebhookRate,
  timingSafeEquals,
  WEBHOOK_MAX_PER_WINDOW,
  WEBHOOK_WINDOW_MS,
} from "../lib/webhookAuth.js";

const NOW = 1_800_000_000_000;
const SECRET = "0123456789abcdef0123456789abcdef";

const headers = (value) => ({ "x-momo-webhook-secret": value });

test("secret lists are trimmed, filtered and support rotation", () => {
  assert.deepEqual(loadWebhookSecrets({ MOMO_WEBHOOK_SECRET: `${SECRET}, secondsecret1234567890 ` }), [
    SECRET,
    "secondsecret1234567890",
  ]);
  assert.deepEqual(loadWebhookSecrets({ MOMO_WEBHOOK_SECRET: "short" }), []);
  assert.deepEqual(loadWebhookSecrets({}), []);
});

test("constant-time comparison rejects length and content mismatches", () => {
  assert.equal(timingSafeEquals(SECRET, SECRET), true);
  assert.equal(timingSafeEquals(SECRET, `${SECRET}x`), false);
  assert.equal(timingSafeEquals(SECRET, SECRET.replace("0", "1")), false);
});

test("missing configuration fails closed", () => {
  const result = authorizeWebhook({ headers: headers(SECRET), body: { timestamp: NOW }, secrets: [], now: NOW });
  assert.deepEqual(result, { ok: false, reason: "not_configured" });
});

test("missing or wrong secrets are rejected", () => {
  assert.deepEqual(
    authorizeWebhook({ headers: {}, body: { timestamp: NOW }, secrets: [SECRET], now: NOW }),
    { ok: false, reason: "missing_secret" }
  );
  assert.deepEqual(
    authorizeWebhook({ headers: headers("wrongwrongwrongwrong"), body: { timestamp: NOW }, secrets: [SECRET], now: NOW }),
    { ok: false, reason: "bad_secret" }
  );
});

test("a valid secret with a fresh timestamp is accepted (alternate header too)", () => {
  assert.deepEqual(
    authorizeWebhook({ headers: headers(SECRET), body: { timestamp: NOW }, secrets: [SECRET], now: NOW }),
    { ok: true }
  );
  assert.deepEqual(
    authorizeWebhook({ headers: { "x-callback-secret": SECRET }, body: { createdAt: new Date(NOW).toISOString() }, secrets: [SECRET], now: NOW }),
    { ok: true }
  );
});

test("missing, stale and future timestamps are rejected", () => {
  assert.deepEqual(
    authorizeWebhook({ headers: headers(SECRET), body: {}, secrets: [SECRET], now: NOW }),
    { ok: false, reason: "missing_timestamp" }
  );
  assert.deepEqual(
    authorizeWebhook({ headers: headers(SECRET), body: { timestamp: NOW - 6 * 60_000 }, secrets: [SECRET], now: NOW }),
    { ok: false, reason: "stale_timestamp" }
  );
  assert.deepEqual(
    authorizeWebhook({ headers: headers(SECRET), body: { timestamp: NOW + 6 * 60_000 }, secrets: [SECRET], now: NOW }),
    { ok: false, reason: "stale_timestamp" }
  );
});

test("epoch seconds and ISO timestamps parse", () => {
  assert.equal(parseWebhookTimestamp(NOW / 1000), NOW);
  assert.equal(parseWebhookTimestamp(new Date(NOW).toISOString()), NOW);
  assert.equal(parseWebhookTimestamp("nonsense"), null);
});

test("more than ten callbacks per reference per minute is throttled, then resets", () => {
  let record;
  for (let i = 0; i < WEBHOOK_MAX_PER_WINDOW; i += 1) {
    const decision = evaluateWebhookRate(record, NOW + i);
    assert.equal(decision.allowed, true, `call ${i + 1}`);
    record = decision.next;
  }
  const blocked = evaluateWebhookRate(record, NOW + 20);
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, "throttled");

  const nextWindow = evaluateWebhookRate(record, NOW + WEBHOOK_WINDOW_MS + 1);
  assert.equal(nextWindow.allowed, true);
  assert.equal(nextWindow.next.count, 1);
});
