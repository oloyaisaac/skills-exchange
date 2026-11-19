/** REM-026: escrow release policy. */
import test from "node:test";
import assert from "node:assert/strict";
import { decideEscrowRelease } from "../lib/escrowRelease.js";

const job = (overrides = {}) => ({
  status: "COMPLETED",
  escrowStatus: "FUNDS_LOCKED_IN_ESCROW",
  assignedProvider: "worker_1",
  escrowAmount: 50000,
  ...overrides,
});

test("a funded, completed job releases the server-side amount", () => {
  const decision = decideEscrowRelease(job(), "worker_1", "+256700000001");
  assert.equal(decision.kind, "RELEASE");
  assert.equal(decision.amount, 50000);
});

test("a repeated release returns the existing payout instead of paying twice", () => {
  const decision = decideEscrowRelease(
    job({ escrowStatus: "RELEASED_TO_WORKER", releasePayoutRef: "PAYOUT-job_1" }),
    "worker_1",
    "+256700000001"
  );
  assert.deepEqual(decision, { kind: "ALREADY_RELEASED", payoutRef: "PAYOUT-job_1" });
});

test("unfunded escrow, wrong worker, bad wallet and zero amount are rejected", () => {
  const unfunded = decideEscrowRelease(job({ escrowStatus: "QUOTE_PENDING" }), "worker_1", "+256700000001");
  assert.equal(unfunded.kind, "REJECT");
  assert.match(unfunded.message, /not funded/i);

  const wrongWorker = decideEscrowRelease(job(), "worker_2", "+256700000001");
  assert.equal(wrongWorker.kind, "REJECT");

  const badWallet = decideEscrowRelease(job(), "worker_1", "not-a-phone");
  assert.equal(badWallet.kind, "REJECT");
  assert.match(badWallet.message, /mobile-money/i);

  const zero = decideEscrowRelease(job({ escrowAmount: 0, budget: 0 }), "worker_1", "+256700000001");
  assert.equal(zero.kind, "REJECT");
});
