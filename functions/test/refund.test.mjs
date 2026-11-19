/** REM-027: refund policy. */
import test from "node:test";
import assert from "node:assert/strict";
import { decideRefund, buildRefundPayload } from "../lib/refund.js";

const settled = {
  amount: 50000,
  currency: "UGX",
  status: "SUCCESS",
  providerReferenceId: "ref-1",
};

test("a partial or full refund of a settled payment is allowed", () => {
  assert.deepEqual(decideRefund(settled, 20000), {
    kind: "REFUND",
    amount: 20000,
    providerReferenceId: "ref-1",
    currency: "UGX",
  });
  assert.equal(decideRefund(settled, 50000).kind, "REFUND");
});

test("unsettled, over-amount, duplicate and missing references are rejected", () => {
  for (const decision of [
    decideRefund(undefined, 1000),
    decideRefund({ ...settled, status: "PENDING" }, 1000),
    decideRefund(settled, 50001),
    decideRefund(settled, 0),
    decideRefund(settled, 1000, true),
    decideRefund({ ...settled, providerReferenceId: "" }, 1000),
  ]) {
    assert.equal(decision.kind, "REJECT");
  }
});

test("the provider payload carries a stable external id", () => {
  assert.deepEqual(buildRefundPayload(15000, "UGX", "job_1"), {
    amount: "15000",
    currency: "UGX",
    externalId: "job_1",
    payerMessage: "Skills Exchange escrow refund",
    payeeNote: "Refund for a cancelled or disputed job",
  });
});
