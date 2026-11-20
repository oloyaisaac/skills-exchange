/**
 * R08-P2-03: the material advance is authorized before anything is written, and its amount comes
 * from the job rather than from the caller.
 *
 * The bug this covers: the old callable wrote a PENDING `materialAdvances/{jobId}` document before
 * checking ownership (so a stranger could burn the job's single advance slot), and it then asked the
 * payout validator for 50% of a *client-supplied* materials subtotal while that validator demanded
 * the job's labour total — so every honest advance failed and only a manipulated subtotal could pass.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { decideMaterialAdvance, advanceAmountFor, ADVANCE_FRACTION } from "../lib/materialAdvance.js";

const OWNER = "resident-uid-1";
const WORKER = "worker-uid-1";

function job(overrides = {}) {
  return {
    postedByUid: OWNER,
    status: "IN_PROGRESS",
    escrowStatus: "FUNDS_LOCKED_IN_ESCROW",
    laborTotal: 120000,
    materialsSubtotal: 40000,
    assignedProviderId: WORKER,
    ...overrides,
  };
}

test("advance: half the materials budget, in whole shillings, never more than the budget", () => {
  assert.equal(ADVANCE_FRACTION, 0.5);
  assert.equal(advanceAmountFor(40000), 20000);
  assert.equal(advanceAmountFor(40001), 20001, "rounded once, half-up");
  assert.equal(advanceAmountFor(1), 1, "never zero for a non-empty budget");
  assert.equal(advanceAmountFor(0), null);
  assert.equal(advanceAmountFor(-5), null);
  assert.equal(advanceAmountFor("40000"), null);
  assert.equal(advanceAmountFor(40000.5), null);
  assert.equal(advanceAmountFor(undefined), null);
  const amount = advanceAmountFor(40000);
  assert.ok(amount <= 40000);
});

test("advance: the resident who posted the job is the only one allowed to ask", () => {
  const stranger = decideMaterialAdvance(job(), "someone-else");
  assert.equal(stranger.kind, "REJECT");
  assert.equal(stranger.code, "permission-denied");
  assert.equal(decideMaterialAdvance(null, OWNER).code, "not-found");
  assert.equal(decideMaterialAdvance(job(), "").code, "permission-denied");
});

test("advance: escrow must be funded and a worker must be assigned", () => {
  assert.equal(decideMaterialAdvance(job({ escrowStatus: "AWAITING_PAYMENT" }), OWNER).code, "failed-precondition");
  assert.equal(decideMaterialAdvance(job({ escrowStatus: "RELEASED_TO_WORKER" }), OWNER).code, "failed-precondition");
  assert.equal(
    decideMaterialAdvance(job({ assignedProviderId: undefined, assignedProviderUid: undefined, assignedWorkerId: undefined }), OWNER).code,
    "failed-precondition"
  );
});

test("advance: a job's single advance cannot be requested twice", () => {
  const pending = decideMaterialAdvance(job(), OWNER, "PENDING");
  assert.equal(pending.kind, "REJECT");
  assert.equal(pending.code, "already-exists");
  assert.match(pending.message, /already pending/);

  const disbursed = decideMaterialAdvance(job(), OWNER, "DISBURSED");
  assert.equal(disbursed.code, "already-exists");
  assert.match(disbursed.message, /already disbursed/);

  const failedBefore = decideMaterialAdvance(job(), OWNER, "FAILED");
  assert.equal(failedBefore.kind, "ALLOW", "a failed attempt may be retried");
});

test("advance: a job with no materials budget cannot be advanced against", () => {
  assert.equal(decideMaterialAdvance(job({ materialsSubtotal: 0 }), OWNER).code, "invalid-argument");
  assert.equal(decideMaterialAdvance(job({ materialsSubtotal: undefined }), OWNER).code, "invalid-argument");
});

test("advance: the allowed amount and payee come from the job document", () => {
  const decision = decideMaterialAdvance(job({ materialsSubtotal: 50000, assignedWorkerId: undefined }), OWNER);
  assert.equal(decision.kind, "ALLOW");
  assert.equal(decision.amountUgx, 25000);
  assert.equal(decision.workerUid, WORKER);
  assert.equal(decision.materialsSubtotal, 50000);
  // The labour total is a different bucket and must not influence the advance.
  assert.notEqual(decision.amountUgx, 120000);
});
