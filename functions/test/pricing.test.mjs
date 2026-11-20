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
});

test("R05-P2-03: every quote component is a whole shilling", () => {
  const quote = computeQuote(33333, 0);
  assert.deepEqual(quote, {
    laborTotal: 33333,
    materialsSubtotal: 0,
    platformFee: 3333,
    grandTotal: 36666,
  });
  // A tie rounds half-up, exactly like `CurrencyRegion.roundToWholeShillings` on Android.
  const tie = computeQuote(25005, 0);
  assert.equal(tie.platformFee, 2501);
  assert.equal(tie.grandTotal, 27506);
  for (const value of [quote.laborTotal, quote.platformFee, quote.grandTotal]) {
    assert.equal(Number.isInteger(value), true);
  }
});

test("a fractional shilling is refused instead of being collected", () => {
  assert.throws(() => computeQuote(333.33, 0), /whole number of UGX/);
  assert.throws(() => computeQuote(50000, 0.5), /whole number of UGX/);
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
