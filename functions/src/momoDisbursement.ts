/**
 * PAY-003: trusted backend payout of an escrow's worker (sandbox only).
 *
 * Pure core of the payout trust boundary. Imports nothing from
 * `firebase-admin`, `firebase-functions` or `axios` so every security decision
 * here is unit-testable with `node --test` and injected fakes
 * (`functions/test/momoDisbursement.test.mjs`). Real axios/Firestore wiring
 * lives in `index.ts` and is passed into `initiatePayout` as `PayoutDeps`.
 *
 * Trust boundary enforced here (the point of PAY-003):
 *  1. The backend is the authority. A client may only ask to release escrow;
 *     it can never declare the worker paid. MTN HTTP 202 means the request was
 *     *delivered to the payer's handset*, NOT that money moved — 202 maps to
 *     PAYOUT_PENDING, never PAYOUT_SUCCESSFUL.
 *  2. Amount, currency, worker phone, job binding and assigned worker are read
 *     from the *trusted job document*; the provider is never assumed to know
 *     them. A client amount that disagrees is rejected, not overridden.
 *  3. Local `pendingPayouts` is the FIRST idempotency gate (duplicate callable
 *     requests for the same key are rejected with ALREADY_PENDING before any
 *     provider call); the global `payouts` collection is the SECOND gate that
 *     stops a second transfer for a key that is already PENDING or SUCCESSFUL.
 *  4. Terminal states PAYOUT_SUCCESSFUL and PAYOUT_FAILED are immutable: a
 *     duplicate request or a contradictory provider status is ignored (no-op).
 *  5. Sandbox-only, fail-closed. `loadPayoutConfig` refuses unless env is
 *     "sandbox", the base URL host is the MTN sandbox host, and all three
 *     sandbox credentials are present.
 *  6. No fabricated references: the provider reference is a real server-minted
 *     UUID used as MTN's `X-Reference-Id`.
 */

import type { MomoConfig } from "./momoCollection";

export const PAYOUT_CURRENCY = "UGX";

export const PAYOUT_SANDBOX_HOST = "sandbox.momodeveloper.mtn.com";

export type PayoutStatus =
  | "PAYOUT_PENDING"
  | "PAYOUT_SUCCESSFUL"
  | "PAYOUT_FAILED"
  | "NEEDS_RECONCILIATION";

export const TERMINAL_PAYOUT_STATUSES: readonly string[] = [
  "PAYOUT_SUCCESSFUL",
  "PAYOUT_FAILED",
  "NEEDS_RECONCILIATION",
];

export type InitiateErrorCode =
  | "unauthenticated"
  | "invalid_argument"
  | "job_not_found"
  | "not_job_owner"
  | "invalid_job_state"
  | "job_not_escrowed"
  | "amount_mismatch"
  | "worker_mismatch"
  | "config_error"
  | "duplicate_request"
  | "already_queued"
  | "already_pending"
  | "provider_error"
  | "provider_timeout";

export type InitiateError =
  | { code: "unauthenticated"; message: string }
  | { code: "invalid_argument"; message: string }
  | { code: "job_not_found"; message: string }
  | { code: "not_job_owner"; message: string }
  | { code: "invalid_job_state"; message: string }
  | { code: "job_not_escrowed"; message: string }
  | { code: "amount_mismatch"; message: string }
  | { code: "worker_mismatch"; message: string }
  | { code: "config_error"; message: string }
  | { code: "duplicate_request"; message: string; payoutId: string }
  | { code: "already_queued"; message: string; transactionId: string }
  | { code: "already_pending"; message: string }
  | { code: "provider_error"; message: string }
  | { code: "provider_timeout"; message: string };

export type InitiateResult =
  | { ok: true; transactionId: string; payoutId: string; providerReferenceId: string; status: "PAYOUT_PENDING" }
  | { ok: false; error: InitiateError };

export interface JobRecordPayout {
  postedByUid?: string | null;
  status?: string | null;
  escrowStatus?: string | null;
  laborTotal?: number | null;
  materialsSubtotal?: number | null;
  grandTotal?: number | null;
  budget?: number | null;
  assignedProvider?: string | null;
  assignedProviderId?: string | null;
  assignedWorkerId?: string | null;
  momoPhoneNumber?: string | null;
  currency?: string | null;
}

/** Which stored job statuses may be paid out. The worker is paid from escrow,
 * so a paid-out job must still have *unpaid* escrow.
 */
export const PAYOUT_ALLOWED_JOB_STATUSES: readonly string[] = [
  "COMPLETED",
  "WORK_COMPLETED",
  "IN_PROGRESS",
];

export interface PayoutRecord {
  jobId: string;
  workerId: string;
  workerMomoNumber: string;
  amount: number;
  currency: string;
  provider: string;
  status: PayoutStatus;
  payoutId: string;
  clientUid: string;
  releasedBy: string;
  transactionId: string;
  providerReferenceId: string;
  createdAt?: number;
  lastCheckedAt?: number;
  reason?: string;
  /** R08: set when this payout settles a wallet withdrawal rather than an escrow release. */
  withdrawalId?: string | null;
  /** R08: RELEASE (escrow to worker), ADVANCE (materials) or CASHOUT (wallet withdrawal). */
  payoutKind?: "RELEASE" | "ADVANCE" | "CASHOUT";
  providerFinancialTransactionId?: string | null;
  /** HTTP status the provider returned when the payout request was issued. */
  providerStatusCode?: number;
}

/** Result of the authenticated provider payout status query. */
export interface ProviderPayoutStatusResult {
  kind: "ok" | "networkError" | "providerError" | "providerTimeout";
  status?: string | null;
  amount?: number | null;
  currency?: string | null;
  financialTransactionId?: string | null;
  referenceId?: string | null;
  statusCode?: number;
  error?: string | null;
}
export interface PayoutDeps {
  /**
   * Read the stored payout record (lifecycle gate + ledger).
   *
   * R08-P2-04: the gate is scoped by `payoutKind`, because a job has more than one money movement
   * (a materials advance, then the escrow release). Keying only on the job id made the advance block
   * the release it was paid out of.
   */
  findPendingPayout: (jobId: string, clientUid: string, payoutKind?: string | null) => Promise<PayoutRecord | null>;
  /** Read the authoritative job document. */
  getJob: (jobId: string) => Promise<JobRecordPayout | null>;
  /** Acquire an MTN sandbox access token (server-side only). */
  acquireToken: (config: MomoConfig) => Promise<{ ok: true; token: string } | { ok: false; error: string }>;
  /** Issue the payout RequestToPay (the release call). */
  requestPayout: (
    config: MomoConfig,
    token: string,
    referenceId: string,
    payload: Record<string, unknown>
  ) => Promise<
    | { statusCode: number; networkError?: undefined; timeout?: undefined; error?: undefined }
    | { statusCode?: undefined; networkError: true; timeout?: boolean; error?: string }
  >;
  /** Re-query MTN for the current payout status. */
  queryProviderPayoutStatus: (
    config: MomoConfig,
    token: string,
    providerReferenceId: string
  ) => Promise<ProviderPayoutStatusResult>;
  /** Persist a payout to the global `payouts` map. */
  persistPayout: (record: PayoutRecord) => Promise<void>;
  /** Update a stored payout with new fields (used to apply provider-verified
   * status). Caller owns conditional writes.
   */
  updatePayout: (ref: string, patch: Record<string, unknown>) => Promise<void>;
  /** Current epoch millis. */
  now: () => number;
  /** Server-side UUID (v4) for the provider reference. */
  randomUuid: () => string;
}



export async function initiatePayout(
  input: {
    jobId: string;
    clientUid: string;
    amountUgx?: number | null;
    workerMomoNumber?: string | null;
    clientPhone?: string | null;
    provider?: string;
    env: Record<string, string | undefined>;
    /** R08: server-computed amount to validate against (defaults to the job's labour total). */
    expectedAmountUgx?: number | null;
    /** R08: the payee was resolved from the worker profile, not from the job's collection number. */
    payeeResolvedServerSide?: boolean;
    /** R08: worker uid resolved server-side from the job document. */
    payeeWorkerId?: string | null;
    /** R08: which money movement this is (defaults to the escrow release). */
    payoutKind?: "RELEASE" | "ADVANCE";
    /** R08: deterministic payout record id (`PAYOUT-<jobId>`), so a retried release cannot duplicate. */
    payoutId?: string;
  },
  deps: PayoutDeps
): Promise<InitiateResult> {
  if (!input.clientUid) {
    return { ok: false, error: { code: "unauthenticated", message: "Authentication required to release escrow." } };
  }
  if (!input.jobId) {
    return { ok: false, error: { code: "invalid_argument", message: "jobId is required." } };
  }

  // 1. Fail-closed configuration. Never proceed to a provider call without
  //    verified sandbox creds.
  const config = loadPayoutConfig(input.env);
  if ("error" in config) {
    return { ok: false, error: { code: "config_error", message: config.error } };
  }

  // 2. Idempotency BEFORE touching the provider: a duplicate callable request
  //    for the same key is rejected at the local gate first.
  const payoutKind = input.payoutKind || "RELEASE";
  const localGate = await deps.findPendingPayout(input.jobId, input.clientUid, payoutKind);
  if (localGate) {
    if (TERMINAL_PAYOUT_STATUSES.includes(localGate.status)) {
      return { ok: false, error: { code: "duplicate_request", message: "This payout already reached a terminal state.", payoutId: localGate.payoutId } };
    }
    return { ok: false, error: { code: "already_pending", message: "A payout for this job is already pending. Await its status before retrying." } };
  }

  // 3. Validate against the TRUSTED job document.
  const job = await deps.getJob(input.jobId);
  if (!job) {
    return { ok: false, error: { code: "job_not_found", message: "Job not found." } };
  }
  const validation = validatePayoutInput({
    job,
    callerUid: input.clientUid,
    requestedAmountUgx: input.amountUgx ?? null,
    workerMomoNumber: input.workerMomoNumber ?? null,
    expectedAmountUgx: input.expectedAmountUgx ?? null,
    payeeResolvedServerSide: input.payeeResolvedServerSide === true,
    payeeWorkerId: input.payeeWorkerId ?? null,
  });
  if (!validation.ok) {
    return { ok: false, error: validation.error };
  }

  return submitDisbursement(
    {
      payoutId: input.payoutId || `PAYOUT-${deps.now()}`,
      jobId: input.jobId,
      payoutKind,
      externalId: input.jobId,
      workerId: validation.workerId,
      workerMomoNumber: validation.workerMomoNumber,
      clientUid: input.clientUid,
      amountUgx: validation.amountUgx,
      provider: input.provider,
      env: input.env,
    },
    deps
  );
}

/**
 * R08-P2-04: the ONE place that talks to the disbursement provider.
 *
 * Both money paths — releasing escrow to a worker and settling a wallet cashout — go through this
 * function, so the provider contract (fail-closed config, server-minted reference, 202 => PENDING,
 * anything else => FAILED, terminal states immutable) exists exactly once. The callers differ only
 * in how they authorize the payee and in the id of the record the payout lives in.
 */
export interface DisbursementOrder {
  /** Record id in the `payouts` collection. */
  payoutId: string;
  /** Job this pays for ("" for a wallet cashout). */
  jobId: string;
  /** Value sent to the provider as `externalId` (job id or withdrawal id). */
  externalId: string;
  workerId: string;
  workerMomoNumber: string;
  /** The authenticated user the disbursement is attributed to. */
  clientUid: string;
  amountUgx: number;
  provider?: string;
  env: Record<string, string | undefined>;
  withdrawalId?: string | null;
  payoutKind?: "RELEASE" | "ADVANCE" | "CASHOUT";
}

export async function submitDisbursement(
  order: DisbursementOrder,
  deps: PayoutDeps
): Promise<InitiateResult> {
  if (!order.clientUid) {
    return { ok: false, error: { code: "unauthenticated", message: "Authentication required to move money." } };
  }
  if (!Number.isInteger(order.amountUgx) || order.amountUgx <= 0) {
    return { ok: false, error: { code: "invalid_argument", message: "The payout amount must be a positive whole number of shillings." } };
  }
  if (!order.payoutId) {
    return { ok: false, error: { code: "invalid_argument", message: "A payout record id is required." } };
  }
  const msisdn = String(order.workerMomoNumber || "").trim();
  if (!/^\+?[0-9]{9,15}$/.test(msisdn)) {
    return { ok: false, error: { code: "worker_mismatch", message: "The payee's Mobile Money number is missing or malformed." } };
  }

  // 1. Fail-closed configuration. Never proceed to a provider call without
  //    verified sandbox creds.
  const config = loadPayoutConfig(order.env);
  if ("error" in config) {
    return { ok: false, error: { code: "config_error", message: config.error } };
  }

  // 2. Acquire a provider token (server-side only).
  const token = await deps.acquireToken(config);
  if (!token.ok) {
    return { ok: false, error: { code: "config_error", message: token.error } };
  }

  // 3. Build the RequestToPay with a REAL server-minted reference id.
  const providerReferenceId = deps.randomUuid();
  const payload = buildPayoutRequestPayload({
    amount: order.amountUgx,
    workerMomoNumber: msisdn,
    externalId: order.externalId,
  });

  const record = (status: PayoutStatus, extra: Partial<PayoutRecord>): PayoutRecord => ({
    jobId: order.jobId,
    workerId: order.workerId,
    workerMomoNumber: msisdn,
    amount: order.amountUgx,
    currency: PAYOUT_CURRENCY,
    provider: order.provider || "MTN_MOMO",
    status,
    payoutId: order.payoutId,
    clientUid: order.clientUid,
    releasedBy: order.clientUid,
    transactionId: order.payoutId,
    providerReferenceId,
    createdAt: deps.now(),
    withdrawalId: order.withdrawalId ?? null,
    payoutKind: order.payoutKind || "RELEASE",
    ...extra,
  });

  // 4. Issue the payout request to the MTN Disbursement API.
  const rtp = await deps.requestPayout(config, token.token, providerReferenceId, payload);

  if ("networkError" in rtp) {
    await deps.persistPayout(record("PAYOUT_FAILED", {
      reason: rtp.error ?? "provider_unreachable",
      providerReferenceId,
    }));
    return {
      ok: false,
      error: rtp.timeout
        ? { code: "provider_timeout", message: "The payout provider did not respond. Nothing was funded." }
        : { code: "provider_error", message: rtp.error ?? "Provider rejected the payout request." },
    };
  }

  // 5. 202 => accepted-and-delivered => PAYOUT_PENDING only. Non-202 => failure.
  //    Never SUCCESSFUL here: the worker money movement is decided by the
  //    provider's pull-status, not by us.
  if (rtp.statusCode !== 202) {
    await deps.persistPayout(record("PAYOUT_FAILED", {
      providerStatusCode: rtp.statusCode,
      reason: `provider returned HTTP ${rtp.statusCode}`,
    }));
    return {
      ok: false,
      error: { code: "provider_error", message: `The payout provider returned HTTP ${rtp.statusCode}. Nothing was funded.` },
    };
  }

  await deps.persistPayout(record("PAYOUT_PENDING", { providerStatusCode: 202 }));

  return {
    ok: true,
    transactionId: order.payoutId,
    payoutId: order.payoutId,
    providerReferenceId,
    status: "PAYOUT_PENDING",
  };
}

/**
 * R08-P2-02/P2-04: settle a wallet cashout through the same disbursement core.
 *
 * The money has already been reserved in the server ledger by the cashout transaction, so this
 * function only has to prove the request is well formed and hand it to `submitDisbursement`. The
 * payout record id is the withdrawal id, which makes the provider reference idempotent per request.
 */
export async function initiateWithdrawalPayout(
  input: {
    withdrawalId: string;
    uid: string;
    amountUgx: number;
    msisdn: string;
    provider?: string;
    env: Record<string, string | undefined>;
  },
  deps: PayoutDeps
): Promise<InitiateResult> {
  if (!input.withdrawalId) {
    return { ok: false, error: { code: "invalid_argument", message: "withdrawalId is required." } };
  }
  if (!input.uid) {
    return { ok: false, error: { code: "unauthenticated", message: "Authentication required to cash out." } };
  }
  if (!Number.isInteger(input.amountUgx) || input.amountUgx <= 0) {
    return { ok: false, error: { code: "invalid_argument", message: "The cashout amount must be a positive whole number of shillings." } };
  }
  const existing = await deps.findPendingPayout(input.withdrawalId, input.uid, "CASHOUT");
  if (existing) {
    if (TERMINAL_PAYOUT_STATUSES.includes(existing.status)) {
      return { ok: false, error: { code: "duplicate_request", message: "This cashout already reached a terminal state.", payoutId: existing.payoutId } };
    }
    return { ok: false, error: { code: "already_pending", message: "A payout for this cashout is already pending. Await its status before retrying." } };
  }
  return submitDisbursement(
    {
      payoutId: input.withdrawalId,
      jobId: input.withdrawalId,
      payoutKind: "CASHOUT",
      externalId: input.withdrawalId,
      workerId: input.uid,
      workerMomoNumber: input.msisdn,
      clientUid: input.uid,
      amountUgx: input.amountUgx,
      provider: input.provider,
      env: input.env,
      withdrawalId: input.withdrawalId,
    },
    deps
  );
}

/**
 * Decide what a payout's status should be given the provider's report. Pure
 * function: the stored record is authoritative for jobId, clientUid, amount,
 * currency and worker binding; nothing a caller supplies can override it.
 *
 * - `ok` + `kind: "ok"` (provider 200) with a matching status determines the
 *   next state;
 * - a provider 200 with status SUCCESSFUL also cross-references
 *   `financialTransactionId`;
 * - a provider 200 with status FAILED means the money never moved;
 * - a provider 404 means the reference is unknown (no record, no transition);
 * - network errors/timeouts/malformed bodies never become SUCCESSFUL;
 * - the stored terminal state re-entry is a no-op (caller guards before calling).
 */
export function decidePayoutStatus({
  stored,
  provider,
}: {
  stored: PayoutRecord;
  provider: ProviderPayoutStatusResult;
}): { newStatus: PayoutStatus; reason: string } {
  const storedStatus = (stored.status || "").trim().toUpperCase();

  // Terminal re-entry is a no-op: the provider or a replay contradicted a final
  // state, and this path never mutates a final state.
  if (TERMINAL_PAYOUT_STATUSES.includes(storedStatus)) {
    return { newStatus: storedStatus as PayoutStatus, reason: "terminal_noop" };
  }

  if (provider.kind !== "ok") {
    return { newStatus: "PAYOUT_FAILED", reason: `provider_status_unavailable_${provider.kind}` };
  }

  const providerStatus = (provider.status || "").trim().toUpperCase();
  if (providerStatus === "SUCCESSFUL") {
    if (provider.amount !== undefined && provider.amount !== stored.amount) {
      return { newStatus: "NEEDS_RECONCILIATION", reason: "amount_mismatch_provider_vs_stored" };
    }
    if (provider.currency !== undefined && provider.currency !== stored.currency) {
      return { newStatus: "NEEDS_RECONCILIATION", reason: "currency_mismatch_provider_vs_stored" };
    }
    if (provider.referenceId !== undefined && provider.referenceId !== stored.providerReferenceId) {
      return { newStatus: "NEEDS_RECONCILIATION", reason: "reference_mismatch_provider_vs_stored" };
    }
    return {
      newStatus: "PAYOUT_SUCCESSFUL",
      reason: "provider_confirmed_success",
    };
  }
  if (providerStatus === "FAILED") {
    return { newStatus: "PAYOUT_FAILED", reason: "provider_confirmed_failed" };
  }
  if (providerStatus === "PENDING") {
    // Provider still working; the client polls again.
    return { newStatus: "PAYOUT_PENDING", reason: "provider_pending" };
  }
  // Unknown provider status: fail closed, no fabricated success.
  return { newStatus: "PAYOUT_FAILED", reason: `provider_unknown_status_${providerStatus}` };
}

/**
 * Build the MTN Disbursement API RequestToPay-style payload for a worker
 * payout. `externalId` ties the payout back to the job (so a provider or a
 * human can trace which job a payout belongs to), and `workerMomoNumber` is
 * the payee.
 */
export function buildPayoutRequestPayload(input: {
  amount: number;
  workerMomoNumber: string;
  externalId: string;
}): Record<string, unknown> {
  return {
    amount: input.amount,
    currency: PAYOUT_CURRENCY,
    externalId: input.externalId,
    payerMsisdn: "",
    payeeMsisdn: input.workerMomoNumber,
    reason: "worker_payout",
  };
}

/**
 * Validate a payout request against the TRUSTED job document.
 *
 * Fail-closed: the caller's amount must match the escrow amount exactly, the
 * worker's number must match the job's assigned worker, and the worker must be
 * the one the job is bound to. A client-supplied amount that disagrees is
 * rejected, never overridden.
 */
export function validatePayoutInput(input: {
  job: JobRecordPayout | null;
  callerUid: string;
  requestedAmountUgx?: number | null;
  workerMomoNumber?: string | null;
  /**
   * R08: the amount the server already computed for this payout (release of the labour total, or
   * half the materials budget for an advance). When supplied it replaces the job's labour total as
   * the expected amount, so an advance is validated against its own arithmetic instead of failing
   * every time.
   */
  expectedAmountUgx?: number | null;
  /**
   * R08: `job.momoPhoneNumber` is the *resident's* collection number. A worker payout is validated
   * against the worker's stored wallet instead, so this check is skipped only for payouts whose
   * payee was already resolved server-side from the worker profile.
   */
  payeeResolvedServerSide?: boolean;
  /**
   * R08: the worker uid resolved server-side (the client writes `assignedProviderId`, not
   * `assignedWorkerId`). When supplied it must still be one of the job's assigned workers.
   */
  payeeWorkerId?: string | null;
}): { ok: true; amountUgx: number; workerId: string; workerMomoNumber: string } |
  { ok: false; error: InitiateError } {
  if (!input.job) {
    return { ok: false, error: { code: "job_not_found", message: "Job not found." } };
  }

  const status = (input.job.status || "").trim().toUpperCase();
  const escrowStatus = (input.job.escrowStatus || "").trim().toUpperCase();
  if (!PAYOUT_ALLOWED_JOB_STATUSES.includes(status)) {
    return { ok: false, error: { code: "invalid_job_state", message: `Job is not eligible for payout (status: ${(input.job.status ?? "").trim()}).` } };
  }
  if (COLLECTION_FUNDED_ESCROW_STATUSES.includes(escrowStatus)) {
    // Funds are still in escrow (or released), the worker is not yet paid.
  } else {
    return { ok: false, error: { code: "job_not_escrowed", message: `Job escrow is already paid out (escrowStatus: ${(input.job.escrowStatus ?? "").trim()}).` } };
  }

  const amountUgx = input.requestedAmountUgx;
  if (typeof amountUgx !== "number" || amountUgx <= 0) {
    return { ok: false, error: { code: "amount_mismatch", message: "Amount must be a positive number." } };
  }
  const expectedAmount = typeof input.expectedAmountUgx === "number"
    ? input.expectedAmountUgx
    : input.job.laborTotal;
  if (typeof expectedAmount === "number" && amountUgx !== expectedAmount) {
    // The amount is server-derived; a client cannot override it.
    return { ok: false, error: { code: "amount_mismatch", message: `Amount mismatch: expected UGX ${expectedAmount}, requested UGX ${amountUgx}.` } };
  }

  // Worker must be the assigned worker of the job; the client may not pick
  // an arbitrary worker phone.
  const assignedWorkers = [
    input.job.assignedWorkerId,
    input.job.assignedProviderId,
    input.job.assignedProvider,
  ].filter((value): value is string => typeof value === "string" && value.trim().length > 0);
  const workerId = (input.payeeWorkerId && String(input.payeeWorkerId).trim()) || input.job.assignedWorkerId;
  if (!workerId) {
    return { ok: false, error: { code: "worker_mismatch", message: "Job has no assigned worker." } };
  }
  if (input.workerMomoNumber === null || input.workerMomoNumber === undefined) {
    return { ok: false, error: { code: "worker_mismatch", message: "Worker Mobile Money number is required." } };
  }
  if (assignedWorkers.length > 0 && !assignedWorkers.includes(workerId)) {
    return { ok: false, error: { code: "worker_mismatch", message: "The requested payee is not the worker assigned to this job." } };
  }
  if (
    !input.payeeResolvedServerSide &&
    typeof input.job.momoPhoneNumber === "string" &&
    input.workerMomoNumber !== input.job.momoPhoneNumber
  ) {
    return { ok: false, error: { code: "worker_mismatch", message: "Worker Mobile Money number does not match the job's assigned worker." } };
  }

  const workerMomoNumber = input.workerMomoNumber as string;
  return { ok: true, amountUgx, workerId, workerMomoNumber };
}

/**
 * Fail-closed sandbox configuration loader, mirroring `loadMomoConfig`.
 *
 * Refuses unless ALL hold:
 *   - `MOMO_ENVIRONMENT` is exactly "sandbox"
 *   - `MOMO_BASE_URL` parses and its host is exactly the MTN sandbox host
 *   - `MTN_SUBSCRIPTION_KEY`, `MTN_API_USER`, `MTN_API_KEY` are all non-empty
 */
export function loadPayoutConfig(env: Record<string, string | undefined>): MomoConfig | { error: string } {
  const environment = (env.MOMO_ENVIRONMENT || "").trim().toLowerCase();
  if (environment !== "sandbox") {
    return { error: "MOMO_ENVIRONMENT must be exactly 'sandbox'. Production payout is not permitted by this component (fail-closed)." };
  }

  const baseUrl = (env.MOMO_BASE_URL || "").trim();
  if (!baseUrl) {
    return { error: "MOMO_BASE_URL is required (e.g. https://sandbox.momodeveloper.mtn.com)." };
  }
  let host: string;
  try {
    host = new URL(baseUrl).host;
  } catch {
    return { error: "MOMO_BASE_URL is not a valid absolute URL." };
  }
  if (host !== PAYOUT_SANDBOX_HOST) {
    return { error: `Refusing non-sandbox payout host '${host}'. Only '${PAYOUT_SANDBOX_HOST}' is permitted (fail-closed).` };
  }

  const subscriptionKey = (env.MTN_SUBSCRIPTION_KEY || "").trim();
  const apiUser = (env.MTN_API_USER || "").trim();
  const apiKey = (env.MTN_API_KEY || "").trim();
  if (!subscriptionKey || !apiUser || !apiKey) {
    return { error: "MTN sandbox credentials are incomplete: MTN_SUBSCRIPTION_KEY, MTN_API_USER and MTN_API_KEY are all required." };
  }

  return {
    environment: "sandbox",
    baseUrl,
    subscriptionKey,
    apiUser,
    apiKey,
    callbackUrl: (env.MTN_CALLBACK_URL || "").trim() || null,
  };
}

/** Escrow states that mean funds are already (or were) locked — a second
 * payout is refused. Also includes REFUNDED, because a refunded job cannot
 * have its worker paid again.
 */
export const COLLECTION_FUNDED_ESCROW_STATUSES: readonly string[] = [
  "FUNDS_LOCKED_IN_ESCROW",
  "RELEASED_TO_WORKER",
  "REFUNDED",
];

/**
 * Generate a human-readable message from a payout status. Kept in the pure
 * core so the Android/UI vocabulary is testable without Firestore.
 */
export function payoutMessageFor(status: PayoutStatus): string {
  switch (status) {
    case "PAYOUT_PENDING":
      return "The payout is pending. The worker will receive payment shortly.";
    case "PAYOUT_SUCCESSFUL":
      return "The payout was successful. The worker has been paid.";
    case "PAYOUT_FAILED":
      return "The payout failed. No funds were transferred to the worker.";
    case "NEEDS_RECONCILIATION":
      return "There is a discrepancy between the provider and the backend. Contact support.";
    default:
      return "Unknown payout status.";
  }
}

