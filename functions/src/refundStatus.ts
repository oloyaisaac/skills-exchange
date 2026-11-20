// R08-P2-07: a refund has a status, and a partial refund has an effect.
//
// Two gaps: the refund record was written as PENDING and then never polled, so a refund that the
// provider rejected stayed PENDING for ever; and a partial refund flipped the job to REFUND_PENDING
// without changing anything, so the escrow still looked fully funded while part of it had gone back
// to the resident.
//
// The decision table is pure. `applyRefundToEscrow` computes the escrow that remains after a
// provider-confirmed refund, which is what the release, the advance and the ledger must work from.

export type RefundStatus = "PENDING" | "SUCCESS" | "FAILED";

export interface RefundView {
  refundRef?: string | null;
  jobId?: string | null;
  amount?: number | null;
  currency?: string | null;
  status?: string | null;
  providerReferenceId?: string | null;
}

export interface ProviderRefundView {
  /** Only `"ok"` means the provider answered; every other kind is an unreachable or failed query. */
  kind: string;
  status?: string | null;
  /** The provider may report the amount as a string, so it is coerced before comparison. */
  amount?: number | string | null;
  currency?: string | null;
}

export type RefundAction =
  | { kind: "SETTLE"; status: RefundStatus; note: string }
  | { kind: "FLAG"; status: "NEEDS_RECONCILIATION"; note: string }
  | { kind: "NOOP"; note: string };

const TERMINAL: readonly string[] = ["SUCCESS", "FAILED"];

/**
 * Decide what a pending refund needs given the provider's report.
 *
 * A terminal refund is never re-opened; an unreachable provider settles nothing; a provider amount
 * that differs from the stored amount is flagged for a human instead of being accepted.
 */
export function decideRefundStatus(refund: RefundView, provider: ProviderRefundView): RefundAction {
  const stored = String(refund.status || "").trim().toUpperCase();
  if (TERMINAL.includes(stored)) return { kind: "NOOP", note: "already terminal" };
  if (provider.kind !== "ok") return { kind: "NOOP", note: `provider ${provider.kind}` };

  const providerStatus = String(provider.status || "").trim().toUpperCase();
  if (providerStatus === "SUCCESSFUL" || providerStatus === "SUCCESS") {
    const providerAmount = provider.amount === undefined || provider.amount === null
      ? null
      : Number(provider.amount);
    if (providerAmount !== null && Number.isFinite(providerAmount) && providerAmount !== Number(refund.amount ?? 0)) {
      return {
        kind: "FLAG",
        status: "NEEDS_RECONCILIATION",
        note: `refund amount mismatch: provider ${providerAmount}, stored ${refund.amount}`,
      };
    }
    return { kind: "SETTLE", status: "SUCCESS", note: "provider confirmed the refund" };
  }
  if (providerStatus === "FAILED" || providerStatus === "REJECTED") {
    return { kind: "SETTLE", status: "FAILED", note: "provider rejected the refund" };
  }
  if (providerStatus === "PENDING") return { kind: "NOOP", note: "provider still pending" };
  return { kind: "FLAG", status: "NEEDS_RECONCILIATION", note: `unknown provider status '${providerStatus}'` };
}

export interface EscrowAfterRefund {
  /** Escrow that remains locked for the worker after this refund. */
  remainingEscrowUgx: number;
  /** Total refunded across every successful refund for the job, including this one. */
  refundedTotalUgx: number;
  /** Fully refunded, so the escrow is no longer payable. */
  fullyRefunded: boolean;
  /**
   * The escrow status the job document should carry. A partial refund leaves the *remaining* money
   * locked and still payable (`FUNDS_LOCKED_IN_ESCROW`); it must not invent a status the payout path
   * does not recognise, or the worker could never be paid what is still owed.
   */
  escrowStatus: "REFUNDED" | "FUNDS_LOCKED_IN_ESCROW";
}

/**
 * The escrow left after a refund completes.
 *
 * `fundedUgx` is the amount the resident actually paid into escrow (the stored collection amount, or
 * the job's grand total when the collection record is unavailable). A refund larger than what is
 * funded never drives the escrow below zero: the excess is a discrepancy for reconciliation, not a
 * credit the worker can claim.
 */
export function applyRefundToEscrow(input: {
  fundedUgx: unknown;
  previouslyRefundedUgx?: unknown;
  refundAmountUgx: unknown;
}): EscrowAfterRefund {
  const funded = Math.max(0, Math.round(Number(input.fundedUgx) || 0));
  const previouslyRefunded = Math.max(0, Math.round(Number(input.previouslyRefundedUgx) || 0));
  const amount = Math.max(0, Math.round(Number(input.refundAmountUgx) || 0));
  const refundedTotal = Math.min(funded, previouslyRefunded + amount);
  const remaining = Math.max(0, funded - refundedTotal);
  const fullyRefunded = remaining === 0;
  return {
    remainingEscrowUgx: remaining,
    refundedTotalUgx: refundedTotal,
    fullyRefunded,
    escrowStatus: fullyRefunded ? "REFUNDED" : "FUNDS_LOCKED_IN_ESCROW",
  };
}

/** Whether a material advance may still be paid out of a partially refunded escrow. */
export function advanceStillFunded(remainingEscrowUgx: unknown, advanceRequestedUgx: unknown): boolean {
  const remaining = Math.max(0, Math.round(Number(remainingEscrowUgx) || 0));
  const requested = Math.round(Number(advanceRequestedUgx) || 0);
  return requested > 0 && requested <= remaining;
}
