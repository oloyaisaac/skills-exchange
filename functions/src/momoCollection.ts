/**
 * PAY-001 Phase 1: trusted backend MoMo Collection foundation (sandbox only).
 *
 * This is the *pure core* of the payment trust boundary. It deliberately imports
 * NOTHING from `firebase-admin`, `firebase-functions` or `axios` so every security
 * decision here is unit-testable with `node --test` and injected fakes
 * (`functions/test/momoCollection.test.mjs`), and so no rule can be bypassed by a
 * different caller in `index.ts`. Real axios/Firestore wiring lives in `index.ts` and
 * is passed into `initiateCollection` as `CollectionDeps`.
 *
 * Trust boundary enforced here (the point of Phase 1):
 *  1. The backend is the authority. A client may only ask to start a collection; it can
 *     never declare success. MTN HTTP 202 means the request was *delivered to the payer's
 *     handset*, NOT that money moved — 202 maps to PENDING, never SUCCESSFUL.
 *  2. Amount, currency, payer phone and owner are read from the *trusted job document*,
 *     never from client values. A client amount that disagrees is rejected, not overridden.
 *  3. Escrow transitions are intentionally ABSENT. Starting a collection never flips a job
 *     to FUNDS_LOCKED_IN_ESCROW; that stays disabled until a verified confirmation path
 *     (Phase 2) exists. No dependency here can write job escrow, so it is structurally safe.
 *  4. Sandbox-only, fail-closed. `loadMomoConfig` refuses unless env is "sandbox", the base
 *     URL host is the MTN sandbox host, and all three sandbox credentials are present.
 *  5. Idempotency is checked BEFORE any provider call, so a duplicate request never double-charges.
 *  6. No fabricated references: the provider reference is a real server-minted UUID used as
 *     MTN's `X-Reference-Id`; there is no invented `PAYOUT-`/`MOMO-UG-`-style value.
 */

/** Currency used across the platform (Uganda Shilling). */
export const MOMO_CURRENCY = "UGX";

/** The ONLY host this component will ever talk to. Fail-closed guard. */
export const MOMO_SANDBOX_HOST = "sandbox.momodeveloper.mtn.com";

/** Collection status vocabulary (matches existing `momo_transactions.status`). */
export type CollectionStatus = "PENDING" | "SUCCESSFUL" | "FAILED";

/**
 * Job statuses from which a client may begin funding a collection. Derived from the
 * Firestore create rule (`jobs.status == 'REQUESTED'`) and the quote-accept flow; a job
 * already accepted is still eligible, but anything past funding is not.
 */
export const COLLECTION_ALLOWED_JOB_STATUSES: readonly string[] = [
  "REQUESTED",
  "QUOTE_PENDING",
  "QUOTE_ACCEPTED",
  "ACCEPTED",
];

/** Escrow states that mean funds are already (or were) locked — a second collection is refused. */
export const COLLECTION_FUNDED_ESCROW_STATUSES: readonly string[] = [
  "FUNDS_LOCKED_IN_ESCROW",
  "RELEASED_TO_WORKER",
];

export type InitiateErrorCode =
  | "unauthenticated"
  | "invalid_argument"
  | "job_not_found"
  | "not_job_owner"
  | "invalid_job_state"
  | "already_funded"
  | "amount_mismatch"
  | "config_error"
  | "duplicate_request"
  | "provider_error"
  | "provider_timeout";

export type InitiateError =
  | { code: "unauthenticated"; message: string }
  | { code: "invalid_argument"; message: string }
  | { code: "job_not_found"; message: string }
  | { code: "not_job_owner"; message: string }
  | { code: "invalid_job_state"; message: string }
  | { code: "already_funded"; message: string }
  | { code: "amount_mismatch"; message: string }
  | { code: "config_error"; message: string }
  | { code: "duplicate_request"; message: string; transactionId: string }
  | { code: "provider_error"; message: string }
  | { code: "provider_timeout"; message: string };

export type InitiateResult =
  | { ok: true; transactionId: string; providerReferenceId: string; status: CollectionStatus }
  | { ok: false; error: InitiateError };

export interface MomoConfig {
  environment: "sandbox";
  baseUrl: string;
  subscriptionKey: string;
  apiUser: string;
  apiKey: string;
  callbackUrl: string | null;
}


/**
 * Fail-closed sandbox configuration loader.
 *
 * Refuses (returns `{ error }`) unless ALL of the following hold, so this component can
 * never accidentally use production or run uncredentialed:
 *   - `MOMO_ENVIRONMENT` is exactly "sandbox"
 *   - `MOMO_BASE_URL` parses and its host is exactly the MTN sandbox host
 *   - `MTN_SUBSCRIPTION_KEY`, `MTN_API_USER`, `MTN_API_KEY` are all non-empty
 */
export function loadMomoConfig(env: Record<string, string | undefined>): LoadConfigResult {
  const environment = (env.MOMO_ENVIRONMENT || "").trim().toLowerCase();
  if (environment !== "sandbox") {
    return {
      error:
        "MOMO_ENVIRONMENT must be exactly 'sandbox'. Production collection is not permitted by this component (fail-closed).",
    };
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
  if (host !== MOMO_SANDBOX_HOST) {
    return {
      error: `Refusing non-sandbox MoMo host '${host}'. Only '${MOMO_SANDBOX_HOST}' is permitted (fail-closed).`,
    };
  }

  const subscriptionKey = (env.MTN_SUBSCRIPTION_KEY || "").trim();
  const apiUser = (env.MTN_API_USER || "").trim();
  const apiKey = (env.MTN_API_KEY || "").trim();
  if (!subscriptionKey || !apiUser || !apiKey) {
    return {
      error:
        "MTN sandbox credentials are incomplete: MTN_SUBSCRIPTION_KEY, MTN_API_USER and MTN_API_KEY are all required.",
    };
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

/** The subset of a `jobs/{id}` document the collection path trusts. */
export interface JobRecord {
  postedByUid?: string | null;
  status?: string | null;
  escrowStatus?: string | null;
  grandTotal?: number | null;
  budget?: number | null;
  momoPhoneNumber?: string | null;
  currency?: string | null;
  /** REM-029 quote fields; validated against the server calculation when present. */
  laborTotal?: number | null;
  materialsSubtotal?: number | null;
  platformFee?: number | null;
}

export interface ValidateCollectionInput {
  job: JobRecord | null;
  callerUid: string | null;
  /** Amount the client claims to pay. Optional; when present it must equal the trusted amount. */
  requestedAmountUgx?: number | null;
  /** Client-supplied phone, used only as a last resort if the job has none. */
  clientPhone?: string | null;
}

export type ValidateCollectionResult =
  | { ok: true; amountUgx: number; payerMsisdn: string }
  | { ok: false; error: InitiateError };

/**
 * Validate a collection request against trusted data. Enforces:
 *  - authenticated caller
 *  - job exists
 *  - caller is the job's immutable owner (`postedByUid`)
 *  - job escrow is not already funded (double-fund guard)
 *  - job status is in the pre-funding allowlist
 *  - trusted amount is a positive whole number of UGX
 *  - a client-supplied amount, if any, matches the trusted amount exactly
 *  - a payer MSISDN is derivable (from the job's phone, else the client phone)
 */
export function validateCollectionInput(input: ValidateCollectionInput): ValidateCollectionResult {
  const { job, callerUid } = input;

  if (!callerUid) {
    return { ok: false, error: { code: "unauthenticated", message: "Authentication required to start a collection." } };
  }
  if (!job) {
    return { ok: false, error: { code: "job_not_found", message: "Job not found." } };
  }
  if (!job.postedByUid || job.postedByUid !== callerUid) {
    return { ok: false, error: { code: "not_job_owner", message: "Only the job owner may fund this job." } };
  }

  const escrow = (job.escrowStatus || "").trim();
  if (escrow && COLLECTION_FUNDED_ESCROW_STATUSES.includes(escrow)) {
    return {
      ok: false,
      error: { code: "already_funded", message: `Funds are already locked for this job (escrow '${escrow}').` },
    };
  }

  const status = (job.status || "").trim();
  if (!COLLECTION_ALLOWED_JOB_STATUSES.includes(status)) {
    return {
      ok: false,
      error: { code: "invalid_job_state", message: `Cannot collect payment for a job in status '${status}'.` },
    };
  }

  // REM-029: the fee/total on the job must be the server's calculation (see `pricing.ts`).
  // The arithmetic is repeated here rather than imported so this module stays dependency-free
  // for `node --test`; `pricing.test.mjs` is the reference implementation.
  if (job.laborTotal != null) {
    const labor = Number(job.laborTotal);
    const materials = Number(job.materialsSubtotal ?? 0);
    const expectedFee = Math.round(labor * 0.1 * 100) / 100;
    const expectedTotal = Math.round((labor + materials + expectedFee) * 100) / 100;
    if (!Number.isFinite(labor) || Number(job.platformFee) !== expectedFee) {
      return {
        ok: false,
        error: { code: "amount_mismatch", message: `Platform fee mismatch: expected ${expectedFee}.` },
      };
    }
    if (Number(job.grandTotal) !== expectedTotal) {
      return {
        ok: false,
        error: { code: "amount_mismatch", message: `Grand total mismatch: expected ${expectedTotal}.` },
      };
    }
  }

  const trustedAmountRaw = job.grandTotal ?? job.budget ?? null;
  if (trustedAmountRaw == null || !Number.isFinite(trustedAmountRaw) || trustedAmountRaw <= 0) {
    return {
      ok: false,
      error: { code: "invalid_argument", message: "Job has no valid positive amount to collect." },
    };
  }
  if (!Number.isInteger(trustedAmountRaw)) {
    return {
      ok: false,
      error: { code: "invalid_argument", message: "Job amount must be a whole number of UGX." },
    };
  }
  const trustedAmount = trustedAmountRaw;

  if (input.requestedAmountUgx != null && input.requestedAmountUgx !== trustedAmount) {
    return {
      ok: false,
      error: {
        code: "amount_mismatch",
        message: `Requested amount ${input.requestedAmountUgx} does not match the job amount ${trustedAmount}. The backend amount is authoritative.`,
      },
    };
  }

  const payerMsisdn = normalizeMsisdn(job.momoPhoneNumber) || normalizeMsisdn(input.clientPhone);
  if (!payerMsisdn) {
    return { ok: false, error: { code: "invalid_argument", message: "No valid payer mobile money number is available." } };
  }

  return { ok: true, amountUgx: trustedAmount, payerMsisdn };
}

/** MTN RequestToPay request body (matches the Collection API contract). */
export interface RequestToPayPayload {
  amount: string;
  currency: string;
  externalId: string;
  payer: { partyIdType: "MSISDN"; partyId: string };
  payerMessage: string;
  payeeNote: string;
}

export function buildRequestToPayPayload(input: {
  amountUgx: number;
  externalId: string;
  payerMsisdn: string;
  payerMessage?: string;
  payeeNote?: string;
}): RequestToPayPayload {
  return {
    amount: String(input.amountUgx),
    currency: MOMO_CURRENCY,
    externalId: input.externalId,
    payer: { partyIdType: "MSISDN", partyId: input.payerMsisdn },
    payerMessage: input.payerMessage || "Skills Exchange payment. Please approve on your handset.",
    payeeNote: input.payeeNote || `Skills Exchange job ${input.externalId}`,
  };
}

/**
 * HTTP 202 from RequestToPay means the provider accepted the request and pushed a prompt
 * to the payer's handset. It is explicitly NOT proof of payment. Only 202 is treated as
 * an accepted *initiation*; every other code is a failure. Success is never derived here.
 */
export function isProviderAccepted(statusCode: number): boolean {
  return statusCode === 202;
}

/** Map a provider-reported transaction status to the platform vocabulary. */
export function mapProviderTransactionStatus(providerStatus: string): CollectionStatus {
  const s = (providerStatus || "").trim().toUpperCase();
  if (s === "SUCCESSFUL" || s === "SUCCESS") return "SUCCESSFUL";
  if (s === "FAILED" || s === "REJECTED") return "FAILED";
  return "PENDING";
}

/**
 * Normalise an MSISDN to digits-only Uganda form: leading trunk `0` -> country code `256`,
 * a bare 9-digit national number -> prefixed with `256`. Returns null if not usable.
 * The payer phone is normally taken from the trusted job document, not the client.
 */
export function normalizeMsisdn(raw: string | null | undefined): string | null {
  if (!raw) return null;
  let digits = raw.replace(/[^\d]/g, "");
  if (!digits) return null;
  if (digits.startsWith("256")) {
    // already country-coded
  } else if (digits.startsWith("0")) {
    digits = "256" + digits.slice(1);
  } else if (digits.length === 9) {
    digits = "256" + digits;
  }
  // Uganda mobile MSISDN is 256 + 9 digits = 12 digits.
  if (!/^256\d{9}$/.test(digits)) return null;
  return digits;
}

export type LoadConfigResult = MomoConfig | { error: string };

/** A durable transaction record persisted to `momo_transactions`. */
export interface CollectionTransactionRecord {
  transactionId: string;
  provider: "MTN_MOMO";
  providerReferenceId: string;
  jobId: string;
  clientUid: string;
  amount: number;
  currency: string;
  status: CollectionStatus;
  createdAt: number;
  providerStatusCode?: number;
  error?: string;
}

/** Dependencies injected by `index.ts` (real axios + Admin SDK). All fake-able in tests. */
export interface CollectionDeps {
  /** Mint a fresh provider reference id (server UUID). Injected for deterministic tests. */
  randomUuid(): string;
  /** Wall-clock millis for record timestamps. */
  now(): number;
  /** Read the trusted job document (Admin SDK, bypasses client-controlled values). */
  getJob(jobId: string): Promise<JobRecord | null>;
  /** Idempotency lookup: an existing PENDING collection for this job+client. */
  findPendingCollection(jobId: string, clientUid: string): Promise<{ transactionId: string } | null>;
  /** Exchange API user credentials for a short-lived bearer token. */
  acquireToken(config: MomoConfig): Promise<{ ok: true; token: string } | { ok: false; error: string }>;
  /** Issue the RequestToPay. Returns the HTTP status on response, or a network error. */
  requestToPay(
    config: MomoConfig,
    args: { token: string; referenceId: string; payload: RequestToPayPayload }
  ): Promise<{ statusCode: number } | { networkError: true; timeout: boolean; error: string }>;
  /** Persist the durable transaction record (Admin SDK write to `momo_transactions`). */
  persistTransaction(record: CollectionTransactionRecord): Promise<void>;
}

export interface InitiateInput {
  jobId: string;
  callerUid: string | null;
  env: Record<string, string | undefined>;
  requestedAmountUgx?: number | null;
  clientPhone?: string | null;
  payerMessage?: string;
  payeeNote?: string;
}

/**
 * Orchestrate a sandbox collection initiation end to end using injected dependencies.
 *
 * Ordering is deliberate: fail-closed config first, then idempotency (before any provider
 * call), then trusted-job validation, then token, then RequestToPay. A 202 persists a
 * PENDING record and returns PENDING — it never returns SUCCESSFUL and never writes job
 * escrow. A non-202 / network error persists a FAILED record and returns the matching error.
 */
export async function initiateCollection(input: InitiateInput, deps: CollectionDeps): Promise<InitiateResult> {
  if (!input.callerUid) {
    return { ok: false, error: { code: "unauthenticated", message: "Authentication required to start a collection." } };
  }
  if (!input.jobId) {
    return { ok: false, error: { code: "invalid_argument", message: "jobId is required." } };
  }

  // 1. Fail-closed configuration. Never proceed to a provider call without verified sandbox creds.
  const config = loadMomoConfig(input.env);
  if ("error" in config) {
    return { ok: false, error: { code: "config_error", message: config.error } };
  }

  // 2. Idempotency BEFORE touching the provider: a duplicate request short-circuits.
  const existing = await deps.findPendingCollection(input.jobId, input.callerUid);
  if (existing) {
    return {
      ok: false,
      error: {
        code: "duplicate_request",
        message: "A collection for this job is already pending. Await its confirmation before retrying.",
        transactionId: existing.transactionId,
      },
    };
  }

  // 3. Validate against the TRUSTED job document.
  const job = await deps.getJob(input.jobId);
  const validation = validateCollectionInput({
    job,
    callerUid: input.callerUid,
    requestedAmountUgx: input.requestedAmountUgx ?? null,
    clientPhone: input.clientPhone ?? null,
  });
  if (!validation.ok) {
    return { ok: false, error: validation.error };
  }

  // 4. Acquire a provider token (server-side only).
  const token = await deps.acquireToken(config);
  if (!token.ok) {
    return { ok: false, error: { code: "config_error", message: token.error } };
  }

  // 5. Build the RequestToPay with a REAL server-minted reference id (no fabricated ref).
  const providerReferenceId = deps.randomUuid();
  const transactionId = `MOMO-${deps.now()}`;
  const payload = buildRequestToPayPayload({
    amountUgx: validation.amountUgx,
    externalId: input.jobId,
    payerMsisdn: validation.payerMsisdn,
    payerMessage: input.payerMessage,
    payeeNote: input.payeeNote,
  });

  // 6. Issue the request.
  const rtp = await deps.requestToPay(config, { token: token.token, referenceId: providerReferenceId, payload });

  if ("networkError" in rtp) {
    await deps.persistTransaction({
      transactionId,
      provider: "MTN_MOMO",
      providerReferenceId,
      jobId: input.jobId,
      clientUid: input.callerUid,
      amount: validation.amountUgx,
      currency: MOMO_CURRENCY,
      status: "FAILED",
      createdAt: deps.now(),
      error: rtp.error,
    });
    return {
      ok: false,
      error: rtp.timeout
        ? { code: "provider_timeout", message: "The payment provider did not respond. Nothing was collected." }
        : { code: "provider_error", message: rtp.error },
    };
  }

  // 7. 202 => accepted-and-delivered => PENDING only. Non-202 => failure. NEVER success here.
  if (!isProviderAccepted(rtp.statusCode)) {
    await deps.persistTransaction({
      transactionId,
      provider: "MTN_MOMO",
      providerReferenceId,
      jobId: input.jobId,
      clientUid: input.callerUid,
      amount: validation.amountUgx,
      currency: MOMO_CURRENCY,
      status: "FAILED",
      createdAt: deps.now(),
      providerStatusCode: rtp.statusCode,
    });
    return {
      ok: false,
      error: { code: "provider_error", message: `Payment provider returned HTTP ${rtp.statusCode}. Nothing was collected.` },
    };
  }

  await deps.persistTransaction({
    transactionId,
    provider: "MTN_MOMO",
    providerReferenceId,
    jobId: input.jobId,
    clientUid: input.callerUid,
    amount: validation.amountUgx,
    currency: MOMO_CURRENCY,
    status: "PENDING",
    createdAt: deps.now(),
    providerStatusCode: 202,
  });

  // NOTE: no job escrow update is performed here, by design. Escrow lock waits for the
  // verified confirmation path (Phase 2) and stays disabled until then.
  return { ok: true, transactionId, providerReferenceId, status: "PENDING" };
}

