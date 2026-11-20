// R08-P2-06: reconciliation and financial controls.
//
// Nothing compared what the provider says with what the platform recorded: a payout could stay
// PAYOUT_PENDING forever, a collection could be locked locally while the provider reported failure,
// and nobody would notice. `reconcilePayments` (scheduled) now walks the open records, asks the
// provider, applies the same transition rules the callables use, and writes an immutable report.
//
// The decision table is pure so it can be tested against synthetic settlement data.

export type PayoutStatusName =
  | "PAYOUT_PENDING"
  | "PAYOUT_SUCCESSFUL"
  | "PAYOUT_FAILED"
  | "NEEDS_RECONCILIATION";

export interface PayoutView {
  payoutId: string;
  jobId?: string | null;
  workerId?: string | null;
  amount?: number | null;
  currency?: string | null;
  status?: string | null;
  providerReferenceId?: string | null;
  createdAt?: number | null;
  lastCheckedAt?: number | null;
}

export interface ProviderView {
  kind: "ok" | "networkError" | "providerError" | "providerTimeout";
  status?: string | null;
  amount?: number | null;
  currency?: string | null;
  referenceId?: string | null;
}

export type PayoutAction =
  | { kind: "SETTLE"; status: PayoutStatusName; walletCreditUgx: number; note: string }
  | { kind: "FLAG"; status: PayoutStatusName; note: string }
  | { kind: "NOOP"; note: string };

const TERMINAL: readonly string[] = ["PAYOUT_SUCCESSFUL", "PAYOUT_FAILED"];

/**
 * Decide what one stale payout needs.
 *
 * Rules:
 *  * a terminal record is never re-opened;
 *  * an unreachable provider is a NOOP (nothing is claimed either way) — but repeated failures are
 *    visible in the report through `lastCheckedAt`;
 *  * SUCCESSFUL credits the worker wallet exactly once (the ledger key is derived from the payout id);
 *  * a provider amount/currency that disagrees with the stored record never credits: it is flagged
 *    NEEDS_RECONCILIATION, because the difference must be resolved by a human.
 */
export function decideReconciliation(payout: PayoutView, provider: ProviderView): PayoutAction {
  const stored = String(payout.status || "").trim();
  if (TERMINAL.includes(stored)) {
    return { kind: "NOOP", note: "already terminal" };
  }
  if (provider.kind !== "ok") {
    return { kind: "NOOP", note: `provider ${provider.kind}` };
  }
  const providerStatus = String(provider.status || "").trim().toUpperCase();
  if (providerStatus === "SUCCESSFUL") {
    const storedAmount = Number(payout.amount ?? 0);
    const providerAmount = Number(provider.amount ?? storedAmount);
    const currency = String(payout.currency || "UGX").trim().toUpperCase();
    const providerCurrency = String(provider.currency || currency).trim().toUpperCase();
    if (providerAmount !== storedAmount || providerCurrency !== currency) {
      return {
        kind: "FLAG",
        status: "NEEDS_RECONCILIATION",
        note: `provider reports ${providerAmount} ${providerCurrency}, stored ${storedAmount} ${currency}`,
      };
    }
    return {
      kind: "SETTLE",
      status: "PAYOUT_SUCCESSFUL",
      walletCreditUgx: storedAmount,
      note: "provider confirmed the payout",
    };
  }
  if (providerStatus === "FAILED" || providerStatus === "REJECTED") {
    return { kind: "SETTLE", status: "PAYOUT_FAILED", walletCreditUgx: 0, note: "provider reports failure" };
  }
  if (providerStatus === "PENDING") {
    return { kind: "NOOP", note: "provider still pending" };
  }
  return { kind: "FLAG", status: "NEEDS_RECONCILIATION", note: `unknown provider status '${providerStatus}'` };
}

export interface ReconciliationReport {
  generatedAt: number;
  checked: number;
  settled: number;
  flagged: number;
  unchanged: number;
  unreadable: number;
  creditedUgx: number;
  items: Array<{ payoutId: string; action: string; note: string }>;
}

/**
 * Build the report for one sweep.
 *
 * Only payouts older than `staleAfterMs` are considered, so a payout that is one minute old is not
 * chased while the provider is still processing it.
 */
export function buildReconciliationReport(input: {
  now: number;
  staleAfterMs: number;
  payouts: PayoutView[];
  providerFor: (payout: PayoutView) => ProviderView;
}): ReconciliationReport {
  const report: ReconciliationReport = {
    generatedAt: input.now,
    checked: 0,
    settled: 0,
    flagged: 0,
    unchanged: 0,
    unreadable: 0,
    creditedUgx: 0,
    items: [],
  };

  for (const payout of input.payouts) {
    const createdAt = Number(payout.createdAt ?? 0);
    if (createdAt > 0 && input.now - createdAt < input.staleAfterMs) continue;
    report.checked += 1;
    let provider: ProviderView;
    try {
      provider = input.providerFor(payout);
    } catch {
      report.unreadable += 1;
      report.items.push({ payoutId: payout.payoutId, action: "UNREADABLE", note: "provider query failed" });
      continue;
    }
    const action = decideReconciliation(payout, provider);
    if (action.kind === "SETTLE") {
      report.settled += 1;
      report.creditedUgx += action.walletCreditUgx;
    } else if (action.kind === "FLAG") {
      report.flagged += 1;
    } else {
      report.unchanged += 1;
    }
    report.items.push({ payoutId: payout.payoutId, action: action.kind, note: action.note });
  }

  return report;
}

/** A one-line summary for the operator log; never contains a phone number. */
export function reportLine(report: ReconciliationReport): string {
  return (
    `reconciliation: checked=${report.checked} settled=${report.settled} flagged=${report.flagged} ` +
    `unchanged=${report.unchanged} unreadable=${report.unreadable} creditedUgx=${report.creditedUgx}`
  );
}
