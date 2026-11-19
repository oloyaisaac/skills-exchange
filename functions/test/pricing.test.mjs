/** REM-029: server-side platform fee and total. */
import test from "node:test";
import assert from "node:assert/strict";
import { computeQuote, validateStoredQuote, PLATFORM_FEE_RATE } from "../lib/pricing.js";

test("the platform fee is ten percent of labour and the total includes materials", () => {
  assert.equal(PLATFORM_FEE_RATE, 0.1);
  assert.deepEqual(computeQuote(50000, 15000), {
    laborTotal: 50000,
    materialsSubtotal: 15000,
    platformFee: 5000,
    grandTotal: 70000,
  });
  assert.equal(computeQuote(333.33, 0).grandTotal, 366.66);
});

test("a stored quote that matches the server calculation is accepted", () => {
  const quote = computeQuote(50000, 15000);
  assert.equal(validateStoredQuote(quote).ok, true);
});

test("a lowered fee or total is rejected, and legacy jobs without a quote still pass", () => {
  const fee = validateStoredQuote({
    laborTotal: 50000, materialsSubtotal: 15000, platformFee: 1, grandTotal: 70000,
  });
  assert.equal(fee.ok, false);
  assert.match(fee.message, /fee mismatch/i);

  const total = validateStoredQuote({
    laborTotal: 50000, materialsSubtotal: 15000, platformFee: 5000, grandTotal: 1000,
  });
  assert.equal(total.ok, false);
  assert.match(total.message, /total mismatch/i);

  assert.equal(validateStoredQuote({}).ok, true);
});
