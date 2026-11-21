/** REM-039: guarantor collusion flag tests (pure, no Firestore). */
import test from "node:test";
import assert from "node:assert/strict";

import {
  assessGuarantorRisk,
  normaliseIdentity,
  normalisePhone,
  RISK_DUPLICATE_GUARANTOR,
  RISK_REUSED_NIN,
  RISK_REUSED_PHONE,
} from "../lib/guarantorRisk.js";

test("phone normalisation compares the subscriber number", () => {
  assert.equal(normalisePhone("+256772000001"), "772000001");
  assert.equal(normalisePhone("0772 000 001"), "772000001");
  assert.equal(normalisePhone(null), "");
});

test("identity normalisation ignores case and separators", () => {
  assert.equal(normaliseIdentity("ug-123 456"), "UG123456");
  assert.equal(normaliseIdentity("  +256 772-123 456 "), "256772123456");
  assert.equal(normaliseIdentity(undefined), "");
});

test("two guarantors with the same NIN are flagged inside one application", () => {
  const result = assessGuarantorRisk([
    { workerId: "w1", nationalIdNumber: "UG-1", phoneNumber: "0772000001" },
    { workerId: "w1", nationalIdNumber: "ug1", phoneNumber: "0772000002" },
  ]);
  assert.ok(result.w1.flags.includes(RISK_DUPLICATE_GUARANTOR));
  assert.equal(result.w1.guarantorCount, 2);
});

test("a NIN reused by another worker is flagged on both sides", () => {
  const result = assessGuarantorRisk([
    { workerId: "w1", nationalIdNumber: "UG-777", phoneNumber: "0772000001" },
    { workerId: "w2", nationalIdNumber: "UG-777", phoneNumber: "0772000002" },
  ]);
  assert.ok(result.w1.flags.includes(RISK_REUSED_NIN));
  assert.ok(result.w2.flags.includes(RISK_REUSED_NIN));
  assert.equal(result.w1.sharedNinWorkers, 1);
  assert.equal(result.w2.sharedNinWorkers, 1);
});

test("a phone number reused by another worker is flagged", () => {
  const result = assessGuarantorRisk([
    { workerId: "w1", nationalIdNumber: "UG-1", phoneNumber: "+256772000001" },
    { workerId: "w2", nationalIdNumber: "UG-2", phoneNumber: "0772 000 001" },
  ]);
  assert.ok(result.w1.flags.includes(RISK_REUSED_PHONE));
  assert.equal(result.w2.sharedPhoneWorkers, 1);
});

test("independent guarantors produce no flags", () => {
  const result = assessGuarantorRisk([
    { workerId: "w1", nationalIdNumber: "UG-1", phoneNumber: "0772000001" },
    { workerId: "w1", nationalIdNumber: "UG-2", phoneNumber: "0772000002" },
    { workerId: "w2", nationalIdNumber: "UG-3", phoneNumber: "0772000003" },
  ]);
  assert.deepEqual(result.w1.flags, []);
  assert.deepEqual(result.w2.flags, []);
  assert.equal(result.w1.sharedNinWorkers, 0);
});

test("malformed records are ignored rather than crashing the queue", () => {
  const result = assessGuarantorRisk([
    { workerId: "", nationalIdNumber: "UG-1" },
    { workerId: "w1", nationalIdNumber: 42, phoneNumber: null },
    null,
  ]);
  assert.equal(result.w1.guarantorCount, 1);
  assert.deepEqual(result.w1.flags, []);
});
