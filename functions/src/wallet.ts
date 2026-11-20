// R08-P2-02: the worker wallet has a server existence.
//
// The ledger used to live only in Room: reinstalling the app erased the payable balance, two devices
// showed two different balances, and no withdrawal or reserve had a server record. The ledger is now
// derived here from immutable entries in `wallet_entries`, every entry keyed so a retry cannot double
// count, and the balance a cashout is validated against is the server sum.
//
// Pure functions: entry shape, derived balance, withdrawal decision and velocity limits. Firestore is
// the storage; nothing here trusts a caller-supplied balance.

export type EntryKind = "EARNING" | "RESERVE" | "WITHDRAWAL" | "REVERSAL" | "ADJUSTMENT";

export interface WalletEntry {
  id: string;
  uid: string;
  jobId?: string | null;
  kind: EntryKind;
  /** Always positive; the sign comes from [kind] and [direction]. */
  amountUgx: number;
  direction: "CREDIT" | "DEBIT";
  payoutId?: string | null;
  withdrawalId?: string | null;
  description?: string | null;
  createdAt: number;
  /**
   * Whether the entry moves money that is actually held in the platform wallet.
   *
   * An escrow release pays the worker's Mobile Money directly through the provider, so crediting the
   * same shilling as a withdrawable balance would let the worker withdraw it a second time. Those
   * earnings are recorded as a statement (`withdrawable: false`): visible, auditable, not spendable.
   * Only money that stayed inside the platform is withdrawable.
   */
  withdrawable?: boolean;
}

/** A daily cashout ceiling and a per-request minimum, both in whole UGX. */
export const CASHOUT_DAILY_LIMIT_UGX = 2_000_000;
export const CASHOUT_MIN_UGX = 5_000;
export const CASHOUT_MAX_PER_DAY = 3;

export function isWholeShillings(amount: unknown): amount is number {
  return typeof amount === "number" && Number.isInteger(amount);
}

/**
 * The signed effect of one entry on the available balance.
 *
 * A RESERVE reduces what may be withdrawn until it is either finalised (WITHDRAWAL) or released
 * (REVERSAL), so the balance can never be spent twice.
 */
export function signedMinor(
  entry: Pick<WalletEntry, "kind" | "amountUgx" | "direction"> & { withdrawable?: boolean }
): number {
  if (entry.withdrawable === false) return 0;
  const magnitude = Math.abs(Number(entry.amountUgx) || 0);
  if (entry.direction === "DEBIT") return -magnitude;
  return magnitude;
}

export interface WalletSummary {
  /** Withdrawable balance: only entries that move money held by the platform. */
  availableUgx: number;
  /** Everything ever credited, including money paid out directly to Mobile Money. */
  creditsUgx: number;
  debitsUgx: number;
  entries: number;
}

/** Derived balance. Negative balances are reported as 0 and flagged, never silently spent. */
export function summariseWallet(entries: WalletEntry[]): WalletSummary {
  let credits = 0;
  let debits = 0;
  let available = 0;
  for (const entry of entries) {
    const amount = Math.abs(Number(entry.amountUgx) || 0);
    if (entry.direction === "CREDIT") credits += amount;
    else debits += amount;
    available += signedMinor(entry);
  }
  return {
    availableUgx: Math.max(0, available),
    creditsUgx: credits,
    debitsUgx: debits,
    entries: entries.length,
  };
}

/** True when the ledger is internally inconsistent (debits exceed credits). */
export function isOverdrawn(entries: WalletEntry[]): boolean {
  let balance = 0;
  for (const entry of entries) balance += signedMinor(entry);
  return balance < 0;
}

export interface CashoutContext {
  availableUgx: number;
  /** Cashouts already recorded today (any non-reversed state). */
  todayCount: number;
  /** Sum of today's cashouts, in UGX. */
  todayTotalUgx: number;
  /** True when this request is a duplicate of one already recorded under the same key. */
  duplicateKey: boolean;
}

export type CashoutDecision =
  | { ok: true; amountUgx: number; idempotencyKey: string }
  | { ok: false; code: CashoutRejectCode; message: string };

export type CashoutRejectCode =
  | "invalid_amount"
  | "below_minimum"
  | "exceeds_available"
  | "daily_limit"
  | "too_many_requests"
  | "duplicate_request"
  | "not_authenticated";

/** Deterministic idempotency key: a retried request reuses the same ledger reservation. */
export function cashoutKey(uid: string, requestId: string): string {
  return `WD-${uid}-${requestId}`;
}

/** The single decision table for a cashout request, evaluated server-side only. */
export function decideCashout(
  requestedAmountUgx: unknown,
  context: CashoutContext,
  idempotencyKey: string
): CashoutDecision {
  if (context.duplicateKey) {
    return { ok: false, code: "duplicate_request", message: "This withdrawal was already recorded." };
  }
  if (!isWholeShillings(requestedAmountUgx) || (requestedAmountUgx as number) <= 0) {
    return { ok: false, code: "invalid_amount", message: "Enter a whole amount in Uganda shillings." };
  }
  const amount = requestedAmountUgx as number;
  if (amount < CASHOUT_MIN_UGX) {
    return { ok: false, code: "below_minimum", message: `The minimum withdrawal is UGX ${CASHOUT_MIN_UGX}.` };
  }
  if (context.todayCount >= CASHOUT_MAX_PER_DAY) {
    return {
      ok: false,
      code: "too_many_requests",
      message: `You can withdraw at most ${CASHOUT_MAX_PER_DAY} times a day. Try again tomorrow.`,
    };
  }
  if (context.todayTotalUgx + amount > CASHOUT_DAILY_LIMIT_UGX) {
    return {
      ok: false,
      code: "daily_limit",
      message: `Your daily withdrawal limit is UGX ${CASHOUT_DAILY_LIMIT_UGX}.`,
    };
  }
  if (amount > context.availableUgx) {
    return {
      ok: false,
      code: "exceeds_available",
      message: `You can withdraw up to UGX ${context.availableUgx} right now.`,
    };
  }
  return { ok: true, amountUgx: amount, idempotencyKey };
}

/**
 * The ledger entry a successful release produces.
 *
 * Keyed by payout so a retry, a status poll and a reconciliation sweep all converge on one entry
 * (`wallet_entries/EARN-<payoutId>`): crediting twice would create money.
 */
export function earningEntry(input: {
  uid: string;
  jobId?: string | null;
  payoutId: string;
  amountUgx: number;
  now: number;
  /** A direct provider payout is a statement, not a spendable balance. */
  withdrawable?: boolean;
}): WalletEntry {
  return {
    id: `EARN-${input.payoutId}`,
    uid: input.uid,
    jobId: input.jobId ?? null,
    kind: "EARNING",
    amountUgx: Math.max(0, Math.round(input.amountUgx)),
    direction: "CREDIT",
    payoutId: input.payoutId,
    description: "Escrow released to your wallet",
    createdAt: input.now,
    withdrawable: input.withdrawable === true,
  };
}

/**
 * The single debit a recorded cashout produces.
 *
 * The reservation *is* the debit: while the provider payout is in flight the money is unavailable,
 * and a failed payout adds a compensating credit ([reversal]). A second "withdrawal" entry would
 * cancel the debit and hand the worker the same money twice.
 */
export function cashoutEntry(input: {
  uid: string;
  withdrawalId: string;
  amountUgx: number;
  requestId: string;
  now: number;
}): WalletEntry {
  return {
    id: `WD-${input.requestId}`,
    uid: input.uid,
    jobId: null,
    kind: "RESERVE",
    amountUgx: Math.round(input.amountUgx),
    direction: "DEBIT",
    withdrawalId: input.withdrawalId,
    payoutId: null,
    description: "Withdrawal requested",
    createdAt: input.now,
    withdrawable: true,
  };
}

/** The compensating entry when a provider payout fails: the reservation is released. */
export function reversalEntry(input: {
  uid: string;
  withdrawalId: string;
  amountUgx: number;
  requestId: string;
  now: number;
  reason: string;
}): WalletEntry {
  return {
    id: `REVERSAL-${input.requestId}`,
    uid: input.uid,
    jobId: null,
    kind: "REVERSAL",
    amountUgx: Math.round(input.amountUgx),
    direction: "CREDIT",
    withdrawalId: input.withdrawalId,
    payoutId: null,
    description: `Withdrawal reversed: ${input.reason}`,
    createdAt: input.now,
    withdrawable: true,
  };
}

// ---------------------------------------------------------------------------------------------
// R08-P2-06: destination controls. The reviewer asked for a velocity limit, an allow-list and a
// re-authentication step when the payout destination changes. The velocity limit is above
// (`decideCashout`); the destination rules are here.
// ---------------------------------------------------------------------------------------------

/** A number that changed less than a day ago must be re-confirmed before it receives money. */
export const DESTINATION_COOLOFF_MS = 24 * 60 * 60 * 1000;

/** Ugandan Mobile Money formats: +2567XXXXXXXX or 07XXXXXXXX (also 2567XXXXXXXX). */
export function normaliseMsisdn(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const digits = raw.replace(/[\s()-]/g, "");
  if (/^\+?2567\d{8}$/.test(digits)) return `+2567${digits.slice(-8)}`;
  if (/^07\d{8}$/.test(digits)) return `+256${digits.slice(1)}`;
  return null;
}

/** Never log or return a full payout number. */
export function maskMsisdn(raw: unknown): string {
  const normalised = normaliseMsisdn(raw);
  if (!normalised) return "unknown";
  return `${normalised.slice(0, 6)}***${normalised.slice(-3)}`;
}

export type DestinationRejectCode =
  | "no_destination"
  | "unverified_destination"
  | "destination_recently_changed"
  | "destination_not_allowed";

export type DestinationDecision =
  | { ok: true; msisdn: string }
  | { ok: false; code: DestinationRejectCode; message: string };

/**
 * Decide whether the stored payout destination may receive money.
 *
 * The caller never supplies the number: it comes from the user profile, must be verified, must not
 * have changed inside the cool-off window (that is the re-authentication requirement — the user has
 * to verify the new number again before it can be paid), and in sandbox must be on the allow-list.
 */
export function decideCashoutDestination(input: {
  storedMsisdn?: unknown;
  verified: boolean;
  lastChangedAt?: number | null;
  now: number;
  allowList?: string[];
}): DestinationDecision {
  const msisdn = normaliseMsisdn(input.storedMsisdn);
  if (!msisdn) {
    return { ok: false, code: "no_destination", message: "Add a Mobile Money number to your profile before withdrawing." };
  }
  if (!input.verified) {
    return { ok: false, code: "unverified_destination", message: "Verify your phone number before withdrawing." };
  }
  const changedAt = Number(input.lastChangedAt ?? 0);
  if (changedAt > 0 && input.now - changedAt < DESTINATION_COOLOFF_MS) {
    return {
      ok: false,
      code: "destination_recently_changed",
      message: "You changed your payout number recently. Verify it again before withdrawing to it.",
    };
  }
  const allowList = (input.allowList || [])
    .map((entry) => normaliseMsisdn(entry))
    .filter((entry): entry is string => entry !== null);
  if (allowList.length > 0 && !allowList.includes(msisdn)) {
    return {
      ok: false,
      code: "destination_not_allowed",
      message: "This number is not on the payout allow-list for the current environment.",
    };
  }
  return { ok: true, msisdn };
}
