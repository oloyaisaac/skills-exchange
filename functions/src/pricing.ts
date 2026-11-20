// REM-029: the platform fee and grand total are a server calculation.
//
// The Android client still displays a locally computed estimate for offline use, but the stored
// quote is validated here before any money moves. A modified APK cannot lower its fee or total.

export const PLATFORM_FEE_RATE = 0.1;

export interface QuoteInput {
  laborTotal?: unknown;
  materialsSubtotal?: unknown;
  platformFee?: unknown;
  grandTotal?: unknown;
}

export interface Quote {
  laborTotal: number;
  materialsSubtotal: number;
  platformFee: number;
  grandTotal: number;
}

/** Money is whole Uganda shillings: a fractional shilling cannot be collected by the provider. */
const requireWholeShillings = (value: number, field: string): number => {
  if (!Number.isInteger(value)) {
    throw new Error(`${field} must be a whole number of UGX`);
  }
  return value;
};

export function computeQuote(laborTotal: unknown, materialsSubtotal: unknown): Quote {
  const labor = Number(laborTotal);
  const materials = Number(materialsSubtotal ?? 0);
  if (!Number.isFinite(labor) || labor <= 0) throw new Error("laborTotal must be positive");
  if (!Number.isFinite(materials) || materials < 0) throw new Error("materialsSubtotal must be >= 0");
  requireWholeShillings(labor, "laborTotal");
  requireWholeShillings(materials, "materialsSubtotal");
  // R05-P2-03: the fee is rounded ONCE, to a whole shilling, with the same half-up rule the client
  // uses (`CurrencyRegion.roundToWholeShillings`). The total is then the sum of whole components, so
  // it is always collectable and the two sides can never disagree by a shilling.
  const platformFee = Math.round(labor * PLATFORM_FEE_RATE);
  return {
    laborTotal: labor,
    materialsSubtotal: materials,
    platformFee,
    grandTotal: labor + materials + platformFee,
  };
}

export type QuoteCheck = { ok: true; quote: Quote } | { ok: false; message: string };

/**
 * Validates the quote stored on a job document. Jobs created before the quote fields existed are
 * accepted for backwards compatibility; any job that HAS the fields must agree with them exactly.
 */
export function validateStoredQuote(job: QuoteInput | undefined): QuoteCheck {
  if (!job || job.laborTotal == null) {
    return { ok: true, quote: computeQuote(1, 0) };
  }
  let expected: Quote;
  try {
    expected = computeQuote(job.laborTotal, job.materialsSubtotal);
  } catch (e: any) {
    return { ok: false, message: `Stored quote is invalid: ${e?.message || e}` };
  }
  const stored = {
    platformFee: Number(job.platformFee),
    grandTotal: Number(job.grandTotal),
  };
  if (stored.platformFee !== expected.platformFee) {
    return {
      ok: false,
      message: `Platform fee mismatch: expected ${expected.platformFee}, stored ${stored.platformFee}.`,
    };
  }
  if (stored.grandTotal !== expected.grandTotal) {
    return {
      ok: false,
      message: `Grand total mismatch: expected ${expected.grandTotal}, stored ${stored.grandTotal}.`,
    };
  }
  return { ok: true, quote: expected };
}
