/**
 * REM-011 / REM-022: tests for the server-side OTP policy.
 *
 * The module under test is pure (no firebase-admin, no axios, no network), so these tests run
 * without emulators, SMS credits or provider credentials.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  normalizeUgandanPhone,
  otpDocId,
  maskPhone,
  evaluateSendRate,
  evaluateVerifyRate,
  parseSendOtpResponse,
  parseVerifyOtpResponse,
  OTP_SEND_COOLDOWN_MS,
  OTP_MAX_SENDS_PER_HOUR,
  OTP_TTL_MS,
  OTP_MAX_VERIFY_ATTEMPTS,
} from "../lib/otp.js";

const NOW = 1_800_000_000_000;

test("normalises every accepted Ugandan phone shape", () => {
  for (const input of ["+256772123456", "256772123456", "0772123456", "772123456", "+256 772 123 456"]) {
    assert.equal(normalizeUgandanPhone(input), "+256772123456", input);
  }
});

test("rejects malformed or foreign numbers", () => {
  for (const input of ["", "12345", "+254712345678", "077212345", "07721234567", "+256072123456", null, 772123456]) {
    assert.throws(() => normalizeUgandanPhone(input), undefined, String(input));
  }
});

test("document ids drop the plus and logs are masked", () => {
  assert.equal(otpDocId("+256772123456"), "256772123456");
  const masked = maskPhone("+256772123456");
  assert.ok(!masked.includes("772123"), masked);
});

test("a first send is allowed and starts the hourly window", () => {
  const decision = evaluateSendRate(undefined, NOW);
  assert.equal(decision.allowed, true);
  assert.equal(decision.next.hourCount, 1);
  assert.equal(decision.next.attempts, 0);
  assert.equal(decision.next.lastSentAt, NOW);
});

test("a second send inside the cooldown is rejected without changing counters", () => {
  const first = evaluateSendRate(undefined, NOW);
  const second = evaluateSendRate(first.next, NOW + OTP_SEND_COOLDOWN_MS - 1);
  assert.equal(second.allowed, false);
  assert.equal(second.reason, "cooldown");
  assert.equal(second.next.hourCount, 1);
});

test("the sixth send inside the hour is rejected, then the window resets", () => {
  let record;
  for (let i = 0; i < OTP_MAX_SENDS_PER_HOUR; i += 1) {
    const decision = evaluateSendRate(record, NOW + i * OTP_SEND_COOLDOWN_MS);
    assert.equal(decision.allowed, true, `send ${i + 1}`);
    record = decision.next;
  }
  const blocked = evaluateSendRate(record, NOW + OTP_MAX_SENDS_PER_HOUR * OTP_SEND_COOLDOWN_MS);
  assert.equal(blocked.allowed, false);
  assert.equal(blocked.reason, "hourly_limit");

  const nextHour = NOW + 3_600_001;
  const reset = evaluateSendRate(record, nextHour);
  assert.equal(reset.allowed, true);
  assert.equal(reset.next.hourCount, 1);
});

test("verification requires a sent, unexpired, unverified challenge", () => {
  assert.equal(evaluateVerifyRate(undefined, NOW).reason, "not_sent");
  assert.equal(
    evaluateVerifyRate({ sentAt: NOW - OTP_TTL_MS - 1, requestId: "r" }, NOW).reason,
    "expired"
  );
  assert.equal(
    evaluateVerifyRate({ sentAt: NOW, requestId: "r", verified: true }, NOW).reason,
    "already_verified"
  );
});

test("verification counts attempts and stops at the maximum", () => {
  const record = { sentAt: NOW, requestId: "r", attempts: 0 };
  const first = evaluateVerifyRate(record, NOW);
  assert.equal(first.allowed, true);
  assert.equal(first.next.attempts, 1);
  assert.equal(first.remainingAttempts, OTP_MAX_VERIFY_ATTEMPTS - 1);

  const exhausted = evaluateVerifyRate({ ...record, attempts: OTP_MAX_VERIFY_ATTEMPTS }, NOW);
  assert.equal(exhausted.allowed, false);
  assert.equal(exhausted.reason, "attempts_exhausted");
});

test("send response parsing accepts wrapped and flat provider shapes", () => {
  assert.deepEqual(parseSendOtpResponse(200, { data: { requestId: "abc", expiresIn: 300 } }), {
    statusCode: 200,
    requestId: "abc",
    expiresIn: 300,
    valid: true,
    message: "",
  });
  assert.equal(parseSendOtpResponse(200, { requestId: "flat" }).requestId, "flat");
  assert.equal(parseSendOtpResponse(500, { requestId: "abc" }).valid, false);
  assert.equal(parseSendOtpResponse(200, {}).valid, false);
});

test("verify response parsing requires an explicit valid=true", () => {
  assert.equal(parseVerifyOtpResponse(200, { data: { valid: true } }).valid, true);
  assert.equal(parseVerifyOtpResponse(200, { valid: true }).valid, true);
  assert.equal(parseVerifyOtpResponse(200, { data: { valid: "true" } }).valid, false);
  assert.equal(parseVerifyOtpResponse(400, { valid: true }).valid, false);
  assert.equal(parseVerifyOtpResponse(200, {}).valid, false);
});
