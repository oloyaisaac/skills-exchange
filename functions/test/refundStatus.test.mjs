/**
 * R08-P2-07: refund status polling and the effect of a partial refund.
 *
 * The reviewer's finding: a refund was written as PENDING and never polled (so a rejected refund
 * stayed "pending" for ever), and a partial refund flipped the job to REFUND_PENDING without reducing
 * the escrow — the worker could still be released money that had gone back to the resident.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { decideRefundStatus, applyRefundToEscrow, advanceStillFunded } from "../lib/refundStatus.js";

const refund = (overrides = {}) => ({
  refundRef: "REFUND-1",
  jobId: "job-1",
  amount: 20000,
  currency: "UGX",
  status: "PENDING",
  providerReferenceId: "uuid-1",
  ...overrides,
});

test("refund: a terminal refund is never re-opened", () => {
  for (const status of ["SUCCESS", "FAILED"]) {
    const action = decideRefundStatus(refund({ status }), { kind: "ok", status: "SUCCESSFUL" });
    assert.equal(action.kind, "NOOP");
    assert.equal(action.note, "already terminal");
  }
});

test("refund: an unreachable provider leaves the refund pending", () => {
  for (const kind of ["provider_error", "provider_timeout", "networkError"]) {
    const action = decideRefundStatus(refund(), { kind });
    assert.equal(action.kind, "NOOP");
  }
});

test("refund: SUCCESSFUL settles; a different amount is flagged instead of accepted", () => {
  const settled = decideRefundStatus(refund(), { kind: "ok", status: "SUCCESSFUL", amount: 20000 });
  assert.equal(settled.kind, "SETTLE");
  assert.equal(settled.status, "SUCCESS");

  const asString = decideRefundStatus(refund(), { kind: "ok", status: "SUCCESS", amount: "20000" });
  assert.equal(asString.status, "SUCCESS", "a provider that reports the amount as a string still settles");

  const mismatch = decideRefundStatus(refund(), { kind: "ok", status: "SUCCESSFUL", amount: 25000 });
  assert.equal(mismatch.kind, "FLAG");
  assert.equal(mismatch.status, "NEEDS_RECONCILIATION");
});

test("refund: FAILED/REJECTED settles as failed, PENDING stays pending, unknown is flagged", () => {
  assert.equal(decideRefundStatus(refund(), { kind: "ok", status: "FAILED" }).status, "FAILED");
  assert.equal(decideRefundStatus(refund(), { kind: "ok", status: "REJECTED" }).status, "FAILED");
  assert.equal(decideRefundStatus(refund(), { kind: "ok", status: "PENDING" }).kind, "NOOP");
  assert.equal(decideRefundStatus(refund(), { kind: "ok", status: "???" }).kind, "FLAG");
});

test("escrow: a partial refund reduces the escrow and keeps the remainder payable", () => {
  const after = applyRefundToEscrow({ fundedUgx: 100000, previouslyRefundedUgx: 0, refundAmountUgx: 30000 });
  assert.equal(after.remainingEscrowUgx, 70000);
  assert.equal(after.refundedTotalUgx, 30000);
  assert.equal(after.fullyRefunded, false);
  assert.equal(after.escrowStatus, "FUNDS_LOCKED_IN_ESCROW", "the rest is still owed to the worker");
});

test("escrow: a second partial refund accumulates, and a full refund closes the escrow", () => {
  const second = applyRefundToEscrow({ fundedUgx: 100000, previouslyRefundedUgx: 30000, refundAmountUgx: 20000 });
  assert.equal(second.remainingEscrowUgx, 50000);
  assert.equal(second.refundedTotalUgx, 50000);

  const full = applyRefundToEscrow({ fundedUgx: 100000, previouslyRefundedUgx: 50000, refundAmountUgx: 50000 });
  assert.equal(full.remainingEscrowUgx, 0);
  assert.equal(full.fullyRefunded, true);
  assert.equal(full.escrowStatus, "REFUNDED");
});

test("escrow: a refund larger than the funding never drives the escrow negative", () => {
  const over = applyRefundToEscrow({ fundedUgx: 50000, previouslyRefundedUgx: 0, refundAmountUgx: 80000 });
  assert.equal(over.remainingEscrowUgx, 0);
  assert.equal(over.refundedTotalUgx, 50000, "the excess is a reconciliation discrepancy, not an escrow credit");
  assert.equal(over.fullyRefunded, true);

  const garbage = applyRefundToEscrow({ fundedUgx: "nonsense", refundAmountUgx: null });
  assert.equal(garbage.remainingEscrowUgx, 0);
  assert.equal(garbage.fullyRefunded, true);
});

test("advance: a partially refunded escrow can still fund an advance, but no more than what remains", () => {
  assert.equal(advanceStillFunded(70000, 20000), true);
  assert.equal(advanceStillFunded(15000, 20000), false);
  assert.equal(advanceStillFunded(0, 1), false);
  assert.equal(advanceStillFunded(70000, 0), false);
});
