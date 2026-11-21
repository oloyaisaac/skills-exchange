/**
 * R08-P2-05: the platform fee is ledgered and the split must add up.
 *
 * Before this, the fee was computed in the Android client, never written anywhere, and the whole
 * `grandTotal` arrived in one platform account with no record of how much of it was the platform's.
 */
import test from "node:test";
import assert from "node:assert/strict";

import { buildFeeLedger, validateFeeLedger, feeLedgerId, platformRevenueUgx } from "../lib/feeLedger.js";
import { computeQuote } from "../lib/pricing.js";

const QUOTE = computeQuote(120000, 40000); // fee 12000, total 172000

test("fee ledger: one deterministic entry per job", () => {
  const entry = buildFeeLedger({ jobId: "job-1", quote: QUOTE, now: 1 });
  assert.equal(entry.id, "FEE-job-1");
  assert.equal(feeLedgerId("job-1"), "FEE-job-1");
  assert.equal(entry.currency, "UGX");
  assert.equal(entry.laborUgx, 120000);
  assert.equal(entry.materialsUgx, 40000);
  assert.equal(entry.platformFeeUgx, 12000);
  assert.equal(entry.grandTotalUgx, 172000);
  assert.equal(entry.workerPayoutUgx, 120000, "the worker is paid the labour bucket only");
  assert.equal(entry.payoutId, null);
  assert.equal(entry.collectionTransactionId, null);
});

test("fee ledger: a consistent split validates", () => {
  const entry = buildFeeLedger({ jobId: "job-1", quote: QUOTE, collectionTransactionId: "TXN-1", payoutId: "PAYOUT-1", now: 1 });
  assert.equal(validateFeeLedger(entry).ok, true);
});

test("fee ledger: a tampered total, fee or payout is rejected", () => {
  const base = buildFeeLedger({ jobId: "job-1", quote: QUOTE, now: 1 });

  const smuggled = validateFeeLedger({ ...base, grandTotalUgx: base.grandTotalUgx + 1 });
  assert.equal(smuggled.ok, false);
  assert.match(smuggled.message, /does not add up/);

  const cheapFee = validateFeeLedger({ ...base, platformFeeUgx: 1, grandTotalUgx: base.grandTotalUgx - 11999 });
  assert.equal(cheapFee.ok, false);
  assert.match(cheapFee.message, /10%/);

  const overpaidWorker = validateFeeLedger({ ...base, workerPayoutUgx: base.grandTotalUgx });
  assert.equal(overpaidWorker.ok, false);

  const fractional = validateFeeLedger({ ...base, laborUgx: 120000.5 });
  assert.equal(fractional.ok, false);

  const noLabour = validateFeeLedger({ ...buildFeeLedger({ jobId: "j", quote: computeQuote(1, 0), now: 1 }), laborUgx: 0 });
  assert.equal(noLabour.ok, false);
});

test("fee ledger: platform revenue is the sum of the fee buckets", () => {
  const entries = [
    buildFeeLedger({ jobId: "a", quote: computeQuote(100000, 0), now: 1 }),
    buildFeeLedger({ jobId: "b", quote: computeQuote(200000, 50000), now: 2 }),
  ];
  assert.equal(platformRevenueUgx(entries), 10000 + 20000);
});
