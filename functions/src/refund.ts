// REM-027: refund policy. Pure functions only; the provider call lives in index.ts.
//
// A refund is allowed only against a settled, successful collection for the same job, for an amount
// no larger than the settled amount, and never twice for the same transaction.

export interface SettledTransactionView {
  amount?: unknown;
  currency?: unknown;
  status?: unknown;
  providerReferenceId?: unknown;
}

export type RefundDecision =
  | { kind: "REJECT"; message: string }
  | { kind: "REFUND"; amount: number; providerReferenceId: string; currency: string };

export function decideRefund(
  transaction: SettledTransactionView | undefined,
  requestedAmount: unknown,
  alreadyRefunded = false
): RefundDecision {
  if (!transaction) {
    return { kind: "REJECT", message: "No settled payment exists for this job." };
  }
  if (alreadyRefunded) {
    return { kind: "REJECT", message: "This payment was already refunded." };
  }
  if (transaction.status !== "SUCCESS") {
    return { kind: "REJECT", message: `Payment is not settled (status: ${String(transaction.status)}).` };
  }
  const settled = Number(transaction.amount);
  const requested = Number(requestedAmount);
  if (!Number.isFinite(settled) || settled <= 0) {
    return { kind: "REJECT", message: "Settled amount is invalid." };
  }
  if (!Number.isFinite(requested) || requested <= 0) {
    return { kind: "REJECT", message: "Refund amount must be greater than zero." };
  }
  if (requested > settled) {
    return { kind: "REJECT", message: "Refund amount exceeds the settled amount." };
  }
  const providerReferenceId = String(transaction.providerReferenceId || "").trim();
  if (!providerReferenceId) {
    return { kind: "REJECT", message: "Settled payment has no provider reference." };
  }
  return {
    kind: "REFUND",
    amount: requested,
    providerReferenceId,
    currency: String(transaction.currency || "UGX"),
  };
}

export interface RefundPayload {
  amount: string;
  currency: string;
  externalId: string;
  payerMessage: string;
  payeeNote: string;
}

export function buildRefundPayload(
  amount: number,
  currency: string,
  jobId: string
): RefundPayload {
  return {
    amount: String(amount),
    currency,
    externalId: jobId,
    payerMessage: "Skills Exchange escrow refund",
    payeeNote: "Refund for a cancelled or disputed job",
  };
}
