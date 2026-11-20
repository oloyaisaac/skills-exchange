// R08-P2-05: the platform fee, ledgered and split.
//
// The resident pays `grandTotal = labour + materials + fee` into the platform's collection account in
// one movement, but nothing recorded how that total was later divided: the fee was computed in the
// Android client (an estimate, editable in a modified APK) and never written anywhere, so the books
// could not answer "how much of this collection was ours?".
//
// Every release now writes exactly one immutable `platform_fees/{FEE-<jobId>}` entry that states the
// split, and `validateFeeLedger` proves the parts add back up to the amount that was actually
// collected. The worker payout and the material advance draw from the labour and materials buckets;
// the fee is the platform's revenue and is never paid out.

import { PLATFORM_FEE_RATE } from "./pricing";

export const FEE_LEDGER_CURRENCY = "UGX";

export interface FeeQuote {
  laborTotal: number;
  materialsSubtotal: number;
  platformFee: number;
  grandTotal: number;
}

export interface FeeLedgerEntry {
  /** Deterministic: one fee entry per job, so a retried release cannot double-count revenue. */
  id: string;
  jobId: string;
  currency: string;
  laborUgx: number;
  materialsUgx: number;
  platformFeeUgx: number;
  grandTotalUgx: number;
  /** What the worker is paid out of escrow (labour only). */
  workerPayoutUgx: number;
  /** Provider collection reference that funded this escrow, when known. */
  collectionTransactionId: string | null;
  /** Payout record created for the worker release, when known. */
  payoutId: string | null;
  recordedAt: number;
}

export function feeLedgerId(jobId: string): string {
  return `FEE-${jobId}`;
}

export function buildFeeLedger(input: {
  jobId: string;
  quote: FeeQuote;
  collectionTransactionId?: string | null;
  payoutId?: string | null;
  now: number;
}): FeeLedgerEntry {
  const { laborTotal, materialsSubtotal, platformFee, grandTotal } = input.quote;
  return {
    id: feeLedgerId(input.jobId),
    jobId: input.jobId,
    currency: FEE_LEDGER_CURRENCY,
    laborUgx: laborTotal,
    materialsUgx: materialsSubtotal,
    platformFeeUgx: platformFee,
    grandTotalUgx: grandTotal,
    workerPayoutUgx: laborTotal,
    collectionTransactionId: input.collectionTransactionId ?? null,
    payoutId: input.payoutId ?? null,
    recordedAt: input.now,
  };
}

export type FeeLedgerCheck = { ok: true } | { ok: false; message: string };

/**
 * The split must be internally consistent and must not invent or lose a shilling.
 *
 * This is checked before the entry is written and again by the reconciliation report, so a
 * hand-edited document is detectable rather than authoritative.
 */
export function validateFeeLedger(entry: FeeLedgerEntry): FeeLedgerCheck {
  const parts = [entry.laborUgx, entry.materialsUgx, entry.platformFeeUgx, entry.grandTotalUgx];
  if (parts.some((value) => !Number.isInteger(value))) {
    return { ok: false, message: "Fee ledger amounts must be whole shillings." };
  }
  if (entry.laborUgx <= 0) {
    return { ok: false, message: "Fee ledger has no labour amount." };
  }
  if (entry.laborUgx + entry.materialsUgx + entry.platformFeeUgx !== entry.grandTotalUgx) {
    return {
      ok: false,
      message: `Fee ledger does not add up: ${entry.laborUgx} + ${entry.materialsUgx} + ${entry.platformFeeUgx} != ${entry.grandTotalUgx}.`,
    };
  }
  if (entry.platformFeeUgx !== Math.round(entry.laborUgx * PLATFORM_FEE_RATE)) {
    return { ok: false, message: `Platform fee is not the quoted ${PLATFORM_FEE_RATE * 100}% of labour.` };
  }
  if (entry.workerPayoutUgx !== entry.laborUgx) {
    return { ok: false, message: "The worker payout must equal the labour component." };
  }
  return { ok: true };
}

/** Platform revenue in the ledger; the fee bucket is never disbursed. */
export function platformRevenueUgx(entries: FeeLedgerEntry[]): number {
  return entries.reduce((total, entry) => total + (Number(entry.platformFeeUgx) || 0), 0);
}
