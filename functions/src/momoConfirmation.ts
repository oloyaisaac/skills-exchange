/**
 * PAY-002: trusted backend confirmation of a MoMo collection (sandbox only).
 *
 * This is the *pure core* of the confirmation trust boundary. Like `momoCollection.ts`
 * it imports nothing from `firebase-admin` / `firebase-functions` / `axios`, so every
 * security decision here is unit-testable with `node --test` and injected fakes
 * (`functions/test/momoConfirmation.test.mjs`), and no rule can be bypassed by a
 * different caller in `index.ts`.
 *
 * Provider contract this module implements (verified against the official MTN MoMo
 * API documentation, 2026-10-14 — NOT guessed):
 *   - Status of a RequestToPay is obtained by
 *       GET {baseUrl}/collection/v1_0/requesttopay/{referenceId}
 *     authenticated with the platform's own Bearer token + subscription key.
 *     HTTP 200 carries `status` in {PENDING, SUCCESSFUL, FAILED} plus optional
 *     `amount`, `currency`, `externalId` and `financialTransactionId`.
 *     HTTP 404 means the reference is unknown to the provider.
 *   - MTN callbacks (X-Callback-Url on the initiation POST) are delivered as a plain
 *     POST of the transaction result. MTN documents NO callback authentication — no
 *     HMAC, no signature header, no shared secret, no mTLS. A callback body is
 *     therefore ALWAYS UNTRUSTED: this module never reads a status, amount, job or
 *     user value from it. Authenticity is established exclusively by re-querying the
 *     provider status API with the platform's credentials (pull-to-verify). See
 *     DECISIONS.md D-2026-10-14-1.
 *
 * Trust rules enforced here:
 *  1. The stored `momo_transactions` record is authoritative for jobId, clientUid,
 *     amount and currency. Nothing a client or a callback supplies can override it.
 *  2. Only a provider 200 with status SUCCESSFUL — with an amount/currency consistent
 *     with the stored record — may decide that escrow may be locked.
 *  3. Provider timeouts, network errors, 5xx and malformed bodies NEVER become
 *     SUCCESSFUL. A malformed body becomes UNKNOWN (needs attention); an unreachable
 *     provider leaves the last known state (PENDING) recorded with the check error.
 *  4. Terminal states (SUCCESSFUL / FAILED / NEEDS_RECONCILIATION) are immutable from
 *     this path: replays and contradicting later reports are recorded as no-ops, so a
 *     webhook replay can never corrupt a final state.
 *  5. Escrow locking is a separate, idempotent action (`lockEscrow`) that `index.ts`
 *     implements inside a Firestore transaction; this core only *decides* whether it
 *     may be attempted, and always against the STORED record's job binding.
 */

import type { MomoConfig } from "./momoCollection";


/**
 * Confirmation state vocabulary. Explicitly includes UNKNOWN and NEEDS_RECONCILIATION
 * so that "we could not verify" and "provider and backend disagree" are first-class
 * states instead of a fabricated PENDING or SUCCESSFUL.
 */
export type ConfirmationStatus = "PENDING" | "SUCCESSFUL" | "FAILED" | "UNKNOWN" | "NEEDS_RECONCILIATION";

/** States that are final for this path. A terminal transaction is never changed again here. */
export const TERMINAL_CONFIRMATION_STATUSES: readonly string[] = [
  "SUCCESSFUL",
  "FAILED",
  "NEEDS_RECONCILIATION",
];

/** Escrow states that mean funds are already (or were) locked — locking again is refused. */
export const FUNDED_ESCROW_STATUSES: readonly string[] = [
  "FUNDS_LOCKED_IN_ESCROW",
  "RELEASED_TO_WORKER",
  "REFUNDED",
];

/** The subset of a `momo_transactions` document the confirmation path trusts. */
export interface StoredCollectionRecord {
  transactionId: string;
  providerReferenceId: string;
  jobId: string;
  clientUid: string;
  amount: number;
  currency: string;
  status: string;
  /** Set (millis or sentinel) once escrow has been locked for this transaction. */
  escrowLockedAt?: number | null;
}

/**
 * Result of the authenticated provider status query. `kind` distinguishes a genuine
 * provider answer from every failure mode so none of them can be mistaken for success.
 */
export type ProviderStatusQueryResult =
  | {
      kind: "ok";
      statusCode: 200;
      status: "PENDING" | "SUCCESSFUL" | "FAILED";
      amount?: string | number | null;
      currency?: string | null;
      externalId?: string | null;
      financialTransactionId?: string | null;
    }
  | { kind: "not_found"; statusCode: 404 }
  | { kind: "malformed"; statusCode: number }
  | { kind: "provider_error"; statusCode?: number; error?: string }
  | { kind: "provider_timeout"; error?: string };

const PROVIDER_STATUS_VALUES = ["PENDING", "SUCCESSFUL", "FAILED"];

/**
 * Purely interpret an HTTP response from the provider status endpoint. Never throws;
 * an uninterpretable body is `malformed`, never a status guess.
 */
export function parseProviderStatusResponse(statusCode: number, body: unknown): ProviderStatusQueryResult {
  if (statusCode === 404) {
    return { kind: "not_found", statusCode: 404 };
  }
  if (statusCode !== 200) {
    return { kind: "provider_error", statusCode };
  }
  if (!body || typeof body !== "object") {
    return { kind: "malformed", statusCode };
  }
  const b = body as Record<string, unknown>;
  const rawStatus = typeof b.status === "string" ? b.status.trim().toUpperCase() : "";
  if (!PROVIDER_STATUS_VALUES.includes(rawStatus)) {
    return { kind: "malformed", statusCode };
  }
  // Optional verification fields: only surface them when well-formed; a present but
  // unparseable amount makes the whole body malformed (we refuse to verify what we
  // cannot read).
  let amount: string | number | null = null;
  if (b.amount !== undefined && b.amount !== null && b.amount !== "") {
    const n = typeof b.amount === "number" ? b.amount : Number(String(b.amount));
    if (!Number.isFinite(n)) {
      return { kind: "malformed", statusCode };
    }
    amount = n;
  }
  return {
    kind: "ok",
    statusCode: 200,
    status: rawStatus as "PENDING" | "SUCCESSFUL" | "FAILED",
    amount,
    currency: typeof b.currency === "string" && b.currency ? b.currency : null,
    externalId: typeof b.externalId === "string" && b.externalId ? b.externalId : null,
    financialTransactionId:
      typeof b.financialTransactionId === "string" && b.financialTransactionId
        ? b.financialTransactionId
        : null,
  };
}

export interface ConfirmationDecision {
  newStatus: ConfirmationStatus;
  action: "none" | "update_status" | "update_status_and_lock_escrow";
  reason: string;
}

/**
 * Decide what a provider query result means for a stored transaction.
 *
 * The caller must pass a NON-terminal stored record (terminal records short-circuit in
 * `verifyAndConfirmCollection`), but this function still defends against a terminal
 * record so a direct call can never corrupt a final state either.
 */
export function decideConfirmation(input: {
  stored: StoredCollectionRecord;
  provider: ProviderStatusQueryResult;
}): ConfirmationDecision {
  const storedStatus = (input.stored.status || "").trim().toUpperCase();

  if (TERMINAL_CONFIRMATION_STATUSES.includes(storedStatus)) {
    // Immutable final states. A repeated confirmation is a no-op; a contradicting
    // provider answer is NOT applied here — it would require manual reconciliation,
    // which is deliberately outside this automated path.
    return {
      newStatus: storedStatus as ConfirmationStatus,
      action: "none",
      reason: storedStatus === "SUCCESSFUL" ? "already_confirmed" : "terminal_state_preserved",
    };
  }

  const p = input.provider;
  if (p.kind === "provider_timeout" || p.kind === "provider_error") {
    // Unreachable provider: the last known state (PENDING) stands and the check
    // failure is recorded. Explicitly NOT a failure and NEVER a success.
    return { newStatus: "PENDING", action: "update_status", reason: "provider_unreachable" };
  }
  if (p.kind === "malformed") {
    // We received something we cannot interpret: the status is genuinely unknown.
    return { newStatus: "UNKNOWN", action: "update_status", reason: "malformed_provider_response" };
  }
  if (p.kind === "not_found") {
    // The provider does not recognise our reference — never treat as anything but unknown.
    return { newStatus: "UNKNOWN", action: "update_status", reason: "provider_unknown_reference" };
  }

  // Provider gave a definite answer (p.kind === "ok").
  if (p.status === "PENDING") {
    return { newStatus: "PENDING", action: "update_status", reason: "provider_pending" };
  }
  if (p.status === "FAILED") {
    return { newStatus: "FAILED", action: "update_status", reason: "provider_failed" };
  }

  // p.status === "SUCCESSFUL": verify against the STORED record before believing it.
  if (p.currency && p.currency !== input.stored.currency) {
    return {
      newStatus: "NEEDS_RECONCILIATION",
      action: "update_status",
      reason: "currency_mismatch",
    };
  }
  if (p.amount !== null && p.amount !== undefined && Number(p.amount) !== Number(input.stored.amount)) {
    // A manipulated or divergent amount must never fund escrow; the STORED amount
    // remains the platform's truth and the discrepancy is flagged for manual work.
    return {
      newStatus: "NEEDS_RECONCILIATION",
      action: "update_status",
      reason: "amount_mismatch",
    };
  }
  return {
    newStatus: "SUCCESSFUL",
    action: "update_status_and_lock_escrow",
    reason: "provider_confirmed",
  };
}

export type StatusQueryAuthz =
  | { ok: true }
  | { ok: false; code: "unauthenticated" | "not_found" | "permission_denied"; message: string };

/**
 * Authorize a user-facing status query against the STORED transaction binding.
 * A caller may only ever see/query a transaction whose stored `clientUid` is their own
 * authenticated uid; a caller can never successfully name another user's transaction.
 */
export function authorizeStatusQuery(input: {
  callerUid: string | null;
  stored: StoredCollectionRecord | null;
}): StatusQueryAuthz {
  if (!input.callerUid) {
    return { ok: false, code: "unauthenticated", message: "Authentication required to query a payment status." };
  }
  if (!input.stored) {
    return { ok: false, code: "not_found", message: "Transaction not found." };
  }
  if (input.stored.clientUid !== input.callerUid) {
    return {
      ok: false,
      code: "permission_denied",
      message: "You may only query the status of your own payment.",
    };
  }
  return { ok: true };
}

/**
 * Extract the ONLY value this backend will read from an untrusted callback body: a
 * reference hint used to locate an already-stored transaction. Every other field
 * (status, amount, jobId, userId, ...) is deliberately ignored — MTN callbacks carry
 * no authentication, so nothing in the body is evidence of anything.
 */
export function extractCallbackReferenceHint(body: unknown): string | null {
  if (!body || typeof body !== "object") return null;
  const b = body as Record<string, unknown>;
  // MTN's documented callback payload carries the `referenceId` that was sent as
  // X-Reference-Id (our providerReferenceId). `transactionRef` is accepted as a
  // legacy alias so a previously-registered integration keeps working.
  const ref =
    typeof b.referenceId === "string" && b.referenceId.trim()
      ? b.referenceId.trim()
      : typeof b.transactionRef === "string" && b.transactionRef.trim()
        ? b.transactionRef.trim()
        : null;
  return ref;
}

/** Human/UI-facing message for each confirmation state. */
export function confirmationMessageFor(status: ConfirmationStatus): string {
  switch (status) {
    case "PENDING":
      return "The provider has not confirmed the payment yet. Approve the prompt on the payer's handset; funds are NOT in escrow.";
    case "SUCCESSFUL":
      return "Payment confirmed by the provider. Funds are locked in escrow.";
    case "FAILED":
      return "The provider reported the payment as failed. Nothing was collected.";
    case "UNKNOWN":
      return "The provider status could not be verified. Do NOT treat this payment as received; it needs attention.";
    case "NEEDS_RECONCILIATION":
      return "The provider report disagrees with the stored transaction. No funds were locked; this payment needs manual reconciliation.";
  }
}

/** The fail-closed sandbox configuration shape produced by `loadMomoConfig`. */
export type MomoConfigLike = MomoConfig;


/** Dependencies injected by `index.ts` (real Admin SDK + axios). All fake-able in tests. */
export interface ConfirmationDeps {
  /** Wall-clock millis for recorded check timestamps. */
  now(): number;
  /**
   * The fail-closed sandbox configuration loader (`loadMomoConfig` in `index.ts` and
   * in the tests). Injected — not imported — so this module keeps zero runtime
   * imports and every path here stays unit-testable.
   */
  loadConfig(env: Record<string, string | undefined>): MomoConfigLike | { error: string };
  /** Load the durable transaction record by platform reference (doc id or providerReferenceId). */
  loadStoredTransaction(ref: string): Promise<StoredCollectionRecord | null>;
  /** Exchange API user credentials for a short-lived bearer token (server-side only). */
  acquireToken(config: MomoConfigLike): Promise<{ ok: true; token: string } | { ok: false; error: string }>;
  /** Authenticated provider status query for the stored provider reference id. */
  queryProviderStatus(
    config: MomoConfigLike,
    token: string,
    providerReferenceId: string
  ): Promise<ProviderStatusQueryResult>;
  /** Record the (non-escrow) outcome fields on the stored transaction. */
  updateTransaction(ref: string, patch: Record<string, unknown>): Promise<void>;
  /**
   * Lock escrow for an already-CONFIRMED transaction. MUST be idempotent and MUST
   * re-verify the job binding, amount, currency and current escrow state inside a
   * Firestore transaction (implemented in `index.ts`).
   */
  lockEscrow(record: StoredCollectionRecord): Promise<{ locked: boolean; reason: string }>;
}

export interface ConfirmInput {
  transactionRef: string;
  env: Record<string, string | undefined>;
}

export type ConfirmationOutcome =
  | {
      ok: true;
      transactionRef: string;
      status: ConfirmationStatus;
      escrowLocked: boolean;
      escrowLockReason: string;
      message: string;
    }
  | {
      ok: false;
      error: { code: "unknown_reference" | "config_error" | "token_error" | "internal_error"; message: string };
    };

/**
 * Verify and, only if the provider itself confirms success, advance a stored
 * collection. Idempotent: a terminal stored record short-circuits with its final
 * state and no provider call, no update and no escrow write. Fails closed: missing
 * sandbox config, token failures, provider errors, timeouts and malformed responses
 * all leave the record non-successful.
 */
export async function verifyAndConfirmCollection(
  input: ConfirmInput,
  deps: ConfirmationDeps
): Promise<ConfirmationOutcome> {
  const stored = await deps.loadStoredTransaction(input.transactionRef);
  if (!stored) {
    return {
      ok: false,
      error: { code: "unknown_reference", message: "Transaction not found." },
    };
  }

  const storedStatus = (stored.status || "").trim().toUpperCase();
  if (TERMINAL_CONFIRMATION_STATUSES.includes(storedStatus)) {
    // Idempotent fast-path: duplicate webhooks / repeated polls change nothing.
    return {
      ok: true,
      transactionRef: stored.transactionId,
      status: storedStatus as ConfirmationStatus,
      escrowLocked: false,
      escrowLockReason: "already_terminal",
      message: confirmationMessageFor(storedStatus as ConfirmationStatus),
    };
  }

  // Fail-closed sandbox configuration, exactly like the initiation path.
  const config = deps.loadConfig(input.env);
  if ("error" in config) {
    return { ok: false, error: { code: "config_error", message: config.error } };
  }

  const token = await deps.acquireToken(config);
  if (!token.ok) {
    return { ok: false, error: { code: "token_error", message: token.error } };
  }

  const provider = await deps.queryProviderStatus(config, token.token, stored.providerReferenceId);
  const decision = decideConfirmation({ stored, provider });

  if (decision.action !== "none") {
    const patch: Record<string, unknown> = {
      status: decision.newStatus,
      lastStatusCheckAt: deps.now(),
      lastStatusCheckReason: decision.reason,
    };
    if (provider.kind === "ok" && provider.financialTransactionId) {
      patch.providerFinancialTransactionId = provider.financialTransactionId;
    }
    if (provider.kind !== "ok") {
      patch.lastStatusCheckError = provider.kind;
    }
    await deps.updateTransaction(stored.transactionId, patch);
  }

  let escrow = { locked: false, reason: "not_attempted" };
  if (decision.action === "update_status_and_lock_escrow") {
    // The decision was made against the STORED record; lockEscrow re-verifies the
    // job binding/amount/currency/escrow state idempotently inside Firestore.
    escrow = await deps.lockEscrow(stored);
  }

  return {
    ok: true,
    transactionRef: stored.transactionId,
    status: decision.newStatus,
    escrowLocked: escrow.locked,
    escrowLockReason: escrow.reason,
    message: confirmationMessageFor(decision.newStatus),
  };
}




