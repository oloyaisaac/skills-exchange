/**
 * R08-P2-06: reconciliation of open payouts against the provider.
 *
 * Nothing compared the provider's answer with the platform's records, so a payout could sit in
 * PAYOUT_PENDING for ever and a collection could stay locked while the provider reported failure.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { decideReconciliation, buildReconciliationReport, reportLine } from "../lib/reconciliation.js";

const payout = (overrides = {}) => ({
  payoutId: "PAYOUT-job-1",
  jobId: "job-1",
  workerId: "worker-1",
  amount: 120000,
  currency: "UGX",
  status: "PAYOUT_PENDING",
  providerReferenceId: "uuid-1",
  createdAt: 1_000,
  ...overrides,
});

test("reconciliation: terminal records are never re-opened", () => {
  for (const status of ["PAYOUT_SUCCESSFUL", "PAYOUT_FAILED"]) {
    const action = decideReconciliation(payout({ status }), { kind: "ok", status: "FAILED" });
    assert.equal(action.kind, "NOOP");
    assert.equal(action.note, "already terminal");
  }
});

test("reconciliation: an unreachable provider claims nothing", () => {
  for (const kind of ["networkError", "providerError", "providerTimeout"]) {
    const action = decideReconciliation(payout(), { kind });
    assert.equal(action.kind, "NOOP", `${kind} must not settle or fail a payout`);
    assert.equal(action.walletCreditUgx, undefined);
  }
});

test("reconciliation: SUCCESSFUL settles with exactly the stored amount", () => {
  const action = decideReconciliation(payout(), { kind: "ok", status: "SUCCESSFUL", amount: 120000, currency: "UGX" });
  assert.equal(action.kind, "SETTLE");
  assert.equal(action.status, "PAYOUT_SUCCESSFUL");
  assert.equal(action.walletCreditUgx, 120000);
});

test("reconciliation: an amount or currency disagreement is flagged, never credited", () => {
  const amountMismatch = decideReconciliation(payout(), { kind: "ok", status: "SUCCESSFUL", amount: 200000 });
  assert.equal(amountMismatch.kind, "FLAG");
  assert.equal(amountMismatch.status, "NEEDS_RECONCILIATION");
  assert.equal(amountMismatch.walletCreditUgx, undefined);

  const currencyMismatch = decideReconciliation(payout(), { kind: "ok", status: "SUCCESSFUL", amount: 120000, currency: "USD" });
  assert.equal(currencyMismatch.kind, "FLAG");
});

test("reconciliation: FAILED settles without crediting; PENDING and unknown statuses do the right thing", () => {
  const failed = decideReconciliation(payout(), { kind: "ok", status: "FAILED" });
  assert.equal(failed.kind, "SETTLE");
  assert.equal(failed.status, "PAYOUT_FAILED");
  assert.equal(failed.walletCreditUgx, 0);

  const stillPending = decideReconciliation(payout(), { kind: "ok", status: "PENDING" });
  assert.equal(stillPending.kind, "NOOP");

  const unknown = decideReconciliation(payout(), { kind: "ok", status: "WEIRD" });
  assert.equal(unknown.kind, "FLAG");
  assert.match(unknown.note, /WEIRD/);

  const rejected = decideReconciliation(payout(), { kind: "ok", status: "REJECTED" });
  assert.equal(rejected.status, "PAYOUT_FAILED");
});

test("reconciliation report: fresh payouts are skipped and every outcome is counted", () => {
  const now = 10_000_000;
  const report = buildReconciliationReport({
    now,
    staleAfterMs: 5 * 60 * 1000,
    payouts: [
      payout({ payoutId: "PAYOUT-fresh", createdAt: now - 1000 }),
      payout({ payoutId: "PAYOUT-ok", createdAt: now - 60 * 60 * 1000 }),
      payout({ payoutId: "PAYOUT-flag", createdAt: now - 60 * 60 * 1000 }),
      payout({ payoutId: "PAYOUT-pending", createdAt: now - 60 * 60 * 1000 }),
    ],
    providerFor: (view) => {
      if (view.payoutId === "PAYOUT-fresh") throw new Error("must not be queried");
      if (view.payoutId === "PAYOUT-ok") return { kind: "ok", status: "SUCCESSFUL", amount: 120000 };
      if (view.payoutId === "PAYOUT-flag") return { kind: "ok", status: "SUCCESSFUL", amount: 1 };
      return { kind: "ok", status: "PENDING" };
    },
  });

  assert.equal(report.checked, 3);
  assert.equal(report.settled, 1);
  assert.equal(report.flagged, 1);
  assert.equal(report.unchanged, 1);
  assert.equal(report.creditedUgx, 120000);
  assert.equal(report.unreadable, 0);
  assert.ok(!report.items.some((item) => item.payoutId === "PAYOUT-fresh"));
});

test("reconciliation report: an unreadable provider is counted, not guessed", () => {
  const report = buildReconciliationReport({
    now: 10_000_000,
    staleAfterMs: 0,
    payouts: [payout()],
    providerFor: () => {
      throw new Error("boom");
    },
  });
  assert.equal(report.unreadable, 1);
  assert.equal(report.settled, 0);
  assert.equal(report.items[0].action, "UNREADABLE");
});

test("reconciliation report: the log line is a summary and never contains a phone number", () => {
  const report = buildReconciliationReport({
    now: 10_000_000,
    staleAfterMs: 0,
    payouts: [payout()],
    providerFor: () => ({ kind: "ok", status: "SUCCESSFUL", amount: 120000 }),
  });
  const line = reportLine(report);
  assert.match(line, /checked=1/);
  assert.match(line, /settled=1/);
  assert.doesNotMatch(line, /\+?\d{12}/);
});
