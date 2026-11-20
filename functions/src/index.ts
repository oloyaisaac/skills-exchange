import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { defineSecret } from "firebase-functions/params";
import * as crypto from "crypto";
import axios from "axios";
/**
 * R03-P2-05: secrets are declared to Secret Manager instead of being read only from `process.env`.
 *
 * `secret.value()` is preferred when the function is deployed with the binding; the environment
 * variable stays as a fallback so emulator/CI runs and existing deployments keep working. A
 * missing secret still fails closed at the call site (SMS/transcription return an error).
 */
const africaTalkingApiKey = defineSecret("AFRICASTALKING_API_KEY");
const africaTalkingUsername = defineSecret("AFRICASTALKING_USERNAME");
const geminiApiKey = defineSecret("GEMINI_API_KEY");

function secretOrEnv(secret: { value: () => string }, envName: string, fallback = ""): string {
  try {
    const value = secret.value();
    if (value) return value;
  } catch {
    // Not configured on this deployment; fall through to the environment.
  }
  return process.env[envName] || fallback;
}

const AFRICA_TALKING_SECRETS = [africaTalkingApiKey, africaTalkingUsername];
const GEMINI_SECRETS = [geminiApiKey];

import {
  checkAdminCaller,
  readWorkerId,
  validateGuarantorReviewInput,
  collectPrivatePhotoPaths,
  resolvePhotoUrlTtlSeconds,
  projectSubmissionForAdmin,
  buildQueueRows,
  buildGuarantorReviewFields,
  buildGuarantorNinUpdate,
  GUARANTOR_REVIEWABLE_STATUSES
} from "./guarantorReview";
import {
  loadMomoConfig,
  initiateCollection,
} from "./momoCollection";
import type {
  MomoConfig,
  JobRecord,
  CollectionDeps,
  CollectionTransactionRecord,
  RequestToPayPayload,
} from "./momoCollection";
import {
  verifyAndConfirmCollection,
  authorizeStatusQuery,
  extractCallbackReferenceHint,
  parseProviderStatusResponse,
  FUNDED_ESCROW_STATUSES,
} from "./momoConfirmation";
import type {
  ConfirmationDeps,
  StoredCollectionRecord,
  ProviderStatusQueryResult,
} from "./momoConfirmation";
import {
  loadPayoutConfig,
  decidePayoutStatus,
  initiatePayout,
  initiateWithdrawalPayout,
  payoutMessageFor,
  PAYOUT_CURRENCY,
  TERMINAL_PAYOUT_STATUSES,
} from "./momoDisbursement";
import type {
  PayoutRecord,
  PayoutStatus,
  PayoutDeps,
  JobRecordPayout,
  ProviderPayoutStatusResult,
} from "./momoDisbursement";
import type { DocumentBody, MintedUrl } from "./guarantorReview";
import {
  normalizeUgandanPhone,
  otpDocId,
  maskPhone,
  evaluateSendRate,
  evaluateVerifyRate,
  parseSendOtpResponse,
  parseVerifyOtpResponse,
  OTP_MESSAGE_PREFIX,
  OTP_TTL_MS,
} from "./otp";
import type { OtpRateRecord } from "./otp";
import { assessGuarantorRisk } from "./guarantorRisk";
import {
  authorizeWebhook,
  loadWebhookSecrets,
  evaluateWebhookRate,
} from "./webhookAuth";
import type { WebhookRateRecord } from "./webhookAuth";
import {
  buildConsentRecord,
  buildDsarEnvelope,
  sanitiseGuarantorForExport,
  buildGuarantorConsentRecord,
  buildConsentWithdrawal,
  buildErasureNotice,
  CONSENT_VERSION,
} from "./compliance";
import { loadSmsConfig, createSmsSender } from "./sms";
import type { SmsTransport } from "./sms";
import { planSosDispatch, selectSosRecipients, buildSosMessage } from "./sosDispatch";
import { hasAdminClaim } from "./admin";
import { isFreshAuth, buildRevocationRecord } from "./session";
import { decideEscrowRelease } from "./escrowRelease";
import {
  earningEntry,
  cashoutEntry,
  reversalEntry,
  cashoutKey,
  decideCashout,
  decideCashoutDestination,
  maskMsisdn,
  summariseWallet,
  CASHOUT_MIN_UGX,
  CASHOUT_DAILY_LIMIT_UGX,
  CASHOUT_MAX_PER_DAY,
} from "./wallet";
import type { WalletEntry } from "./wallet";
import { decideMaterialAdvance } from "./materialAdvance";
import { validateStoredQuote } from "./pricing";
import { buildFeeLedger, validateFeeLedger, feeLedgerId } from "./feeLedger";
import { buildReconciliationReport, reportLine } from "./reconciliation";
import {
  RETENTION_POLICY,
  anonymisePatch,
  categoryById,
  planRetentionSweep,
  retentionReportLine,
  summariseRetention,
} from "./retention";
import {
  sanitiseGuarantorSubmissionForExport,
  ninFingerprint,
  maskNin,
  scrubForLog,
} from "./pii";
import {
  SUBMISSION_WINDOW_MS,
  describeRejections,
  normaliseName,
  phoneKey,
  reviewGuarantorIntake,
} from "./guarantorIntake";
import type { ExistingGuarantor, GuarantorCandidate } from "./guarantorIntake";
import {
  REPORT_STATUS,
  buildReportRecord,
  decideTriage,
  metricsLine,
  safetyMetrics,
  triageMessage,
} from "./reports";
import type { ReportRecord, ReportStatus } from "./reports";
import { applyRating, ratingDocumentId, reviewRating } from "./ratingsCore";
import { describeSosDispatch, dispatchChatMessage } from "./sosStatus";
import { decideRefund, buildRefundPayload } from "./refund";
import { decideRefundStatus, applyRefundToEscrow } from "./refundStatus";
import { APP_CHECK, CALLABLE_RUNTIME, PROVIDER_CALL, callable, providerCallable } from "./runtime";
import { withAppCheck } from "./appCheck";
import { withProviderRetry } from "./providerRetry";
import {
  isValidPin,
  newSalt,
  hashPin,
  pinMatches,
  attemptStateOf,
  lockRemainingMs,
  applyAttempt,
  refusalMessage,
} from "./doorstepPin";
import {
  FRESH_AUTH_WINDOW_SECONDS,
  ASSIGNED_JOB_FIELD,
  ERASURE_STEPS,
  messagesToPurge,
  retainedRecords,
  SINGLE_DOC_COLLECTIONS,
  QUERY_DELETIONS,
  isFreshAuth as isFreshAuthForDeletion,
  storagePurgePrefixes,
  mayDeleteAuthUser,
  deletionRefusalReason,
} from "./accountDeletion";
import type { DeletionProgress } from "./accountDeletion";
import {
  buildTranscriptionPrompt,
  buildGeminiTranscriptionRequest,
  extractGeminiText,
  extractJsonObject,
  validateTranscribedJob,
  MAX_AUDIO_BASE64_LENGTH,
} from "./transcription";
import {
  buildMapsGroundingRequest,
  normaliseSearchInput,
  parseGroundedPlaces,
} from "./places";

admin.initializeApp();
const db = admin.firestore();


/**
 * ADMIN-005: lifetime of the short-lived download links handed to an administrator for
 * the private guarantor photographs (`users/{uid}/guarantors/**`). 15 minutes by
 * default; `GUARANTOR_PHOTO_URL_TTL_SECONDS` may shorten or lengthen it inside the bound
 * enforced by `resolvePhotoUrlTtlSeconds`.
 */
const PHOTO_URL_TTL_SECONDS = resolvePhotoUrlTtlSeconds(process.env.GUARANTOR_PHOTO_URL_TTL_SECONDS);

/**
 * ADMIN-005: the bucket holding the private verification photographs. Cloud Functions
 * already know their own project's default bucket through `FIREBASE_CONFIG`
 * (`skills-exchange-f9189.firebasestorage.app` for this project); `GS_STORAGE_BUCKET`
 * only exists for a non-default bucket and must never be needed.
 */
const STORAGE_BUCKET_NAME = process.env.GS_STORAGE_BUCKET || "";


/**
 * PAY-002 (D-2026-10-14-1): the previous SEC-002 HMAC webhook gate has been REMOVED.
 *
 * MTN MoMo's documented callback mechanism (X-Callback-Url on the initiation POST)
 * delivers a plain POST with NO authentication of any kind — no HMAC, no signature
 * header, no shared secret, no mTLS. The removed gate required a `x-momo-signature`
 * (etc.) header HMAC-signed with `MOMO_WEBHOOK_SECRET`, a protocol MTN would never
 * satisfy, so a real provider callback could only ever be rejected by it while an
 * attacker-controlled body still went straight to the escrow flip below it.
 *
 * Replaced by pull-to-verify (the only mechanism the provider documentation
 * establishes): the callback body is NEVER trusted; it is used solely as a hint to
 * locate an already-stored transaction, and every state decision comes from the
 * authenticated GET /collection/v1_0/requesttopay/{referenceId} re-query performed
 * by `momoConfirmation.verifyAndConfirmCollection`.
 */


/**
 * MODULE 3: MOBILE MONEY (MoMo) PAYMENT INFRASTRUCTURE — trusted sandbox Collection.
 *
 * AUDIT-PAY Phase 2 (PAY-001): this callable no longer fabricates a transaction or
 * flips escrow. It delegates every trust decision to `momoCollection.initiateCollection`:
 * the backend is the authority, 202 means PENDING (never success), and escrow stays
 * untouched until the verified webhook confirmation path (PAY-002) exists.
 *
 * Backwards-compatible request shape: `{ jobId, amountUgx?, phoneNumber?, provider? }`.
 * `amountUgx`/`phoneNumber` are hints only — the trusted `jobs/{id}` document wins, and a
 * disagreeing client amount is rejected (amount_mismatch), never overridden.
 */
export const createMoMoCollection = functions.runWith(providerCallable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to initiate Mobile Money collection."
    );
  }

  const callerUid: string = context.auth.uid;
  const jobId = typeof data?.jobId === "string" ? data.jobId.trim() : "";
  if (!jobId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameter: jobId."
    );
  }

  const requestedAmountUgx =
    typeof data?.amountUgx === "number" && Number.isFinite(data.amountUgx)
      ? data.amountUgx
      : null;
  const clientPhone =
    typeof data?.phoneNumber === "string" && data.phoneNumber.trim()
      ? data.phoneNumber
      : typeof data?.phone === "string" && data.phone.trim()
        ? data.phone
        : null;

  const deps: CollectionDeps = {
    randomUuid: () => crypto.randomUUID(),
    now: () => Date.now(),
    async getJob(id: string): Promise<JobRecord | null> {
      const snap = await db.collection("jobs").doc(id).get();
      if (!snap.exists) return null;
      return (snap.data() || {}) as JobRecord;
    },
    async findPendingCollection(id: string, clientUid: string) {
      const snap = await db.collection("momo_transactions")
        .where("jobId", "==", id)
        .where("clientUid", "==", clientUid)
        .where("status", "==", "PENDING")
        .limit(1)
        .get();
      if (snap.empty) return null;
      return { transactionId: snap.docs[0].id };
    },
    async acquireToken(config: MomoConfig) {
      try {
        const basic = Buffer.from(`${config.apiUser}:${config.apiKey}`).toString("base64");
        const resp = await axios.post(
          `${config.baseUrl}/collection/token/`,
          {},
          {
            headers: {
              "Authorization": `Basic ${basic}`,
              "Ocp-Apim-Subscription-Key": config.subscriptionKey,
            },
            timeout: 15000,
            validateStatus: () => true,
          }
        );
        if (resp.status !== 200 || !resp.data?.access_token) {
          return { ok: false as const, error: `Token exchange returned HTTP ${resp.status}.` };
        }
        return { ok: true as const, token: String(resp.data.access_token) };
      } catch (e: any) {
        return { ok: false as const, error: e?.message || "Token exchange failed." };
      }
    },
    async requestToPay(config: MomoConfig, args: { token: string; referenceId: string; payload: RequestToPayPayload }) {
      try {
        const resp = await axios.post(
          `${config.baseUrl}/collection/v1_0/requesttopay`,
          args.payload,
          {
            headers: {
              "Authorization": `Bearer ${args.token}`,
              "X-Reference-Id": args.referenceId,
              "X-Target-Environment": "sandbox",
              "Ocp-Apim-Subscription-Key": config.subscriptionKey,
              "Content-Type": "application/json",
            },
            timeout: 20000,
            validateStatus: () => true,
          }
        );
        return { statusCode: resp.status };
      } catch (e: any) {
        const timeout = e?.code === "ECONNABORTED" || /timeout/i.test(e?.message || "");
        return { networkError: true as const, timeout, error: e?.message || "Provider request failed." };
      }
    },
    async persistTransaction(record: CollectionTransactionRecord) {
      await db.collection("momo_transactions").doc(record.transactionId).set({
        ...record,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    },
  };

  const result = await initiateCollection(
    {
      jobId,
      callerUid,
      env: process.env as Record<string, string | undefined>,
      requestedAmountUgx,
      clientPhone,
    },
    deps
  );

  if (!result.ok) {
    const code = result.error.code;
    const httpsCode =
      code === "unauthenticated" ? "unauthenticated"
        : code === "invalid_argument" ? "invalid-argument"
          : code === "job_not_found" ? "not-found"
            : code === "not_job_owner" || code === "invalid_job_state" || code === "already_funded" || code === "amount_mismatch" || code === "duplicate_request" ? "failed-precondition"
              : code === "config_error" ? "failed-precondition"
                : code === "provider_timeout" ? "deadline-exceeded"
                  : "unavailable";
    throw new functions.https.HttpsError(
      httpsCode as "unauthenticated" | "invalid-argument" | "not-found" | "failed-precondition" | "deadline-exceeded" | "unavailable",
      result.error.message,
      code === "duplicate_request" && "transactionId" in result.error
        ? { transactionId: (result.error as { transactionId?: string }).transactionId }
        : undefined
    );
  }

  // NOTE: no job escrow write happens here, by design. The client must NOT treat
  // PENDING as funded; escrow locks only on the verified PAY-002 webhook path.
  return {
    success: true,
    transactionRef: result.transactionId,
    providerReferenceId: result.providerReferenceId,
    status: result.status,
    message: `Payment request sent. The payer must approve UGX on their handset; funds are NOT yet in escrow.`,
  };
});

// ============================================================================
// PAY-002: trusted confirmation of a MoMo collection (sandbox only).
//
// Status is obtained ONLY from the provider's authenticated status endpoint
// (GET /collection/v1_0/requesttopay/{referenceId}) using the platform's own
// credentials. Clients and callbacks are never authorities. Escrow locks only
// after the provider itself reports SUCCESSFUL for the STORED transaction, and
// only after the authoritative job record has been re-read and re-verified.
// Worker payout (PAY-003) is deliberately NOT part of this path.
// ============================================================================

/**
 * Load a stored collection record by platform reference. Accepts either the
 * `momo_transactions` doc id (MOMO-<ts>) or the provider reference id (the UUID
 * that was sent as X-Reference-Id and that MTN echoes in callbacks). No other
 * lookup path exists.
 */
async function loadStoredTransactionByRef(ref: string): Promise<StoredCollectionRecord | null> {
  const cleanRef = (ref || "").trim();
  if (!cleanRef) return null;
  const mapDoc = (id: string, data: FirebaseFirestore.DocumentData): StoredCollectionRecord => ({
    transactionId: id,
    providerReferenceId: String(data.providerReferenceId || ""),
    jobId: String(data.jobId || ""),
    clientUid: String(data.clientUid || ""),
    amount: Number(data.amount),
    currency: String(data.currency || ""),
    status: String(data.status || ""),
    escrowLockedAt: data.escrowLockedAt ?? null,
  });
  const docSnap = await db.collection("momo_transactions").doc(cleanRef).get();
  if (docSnap.exists) {
    return mapDoc(docSnap.id, docSnap.data() || {});
  }
  const byProviderRef = await db.collection("momo_transactions")
    .where("providerReferenceId", "==", cleanRef)
    .limit(1)
    .get();
  if (byProviderRef.empty) return null;
  const snap = byProviderRef.docs[0];
  return mapDoc(snap.id, snap.data() || {});
}

/**
 * Authenticated provider status query — the ONLY source of payment truth in this
 * module. Server-side only; credentials never leave the backend.
 */
async function queryMomoProviderStatus(
  config: MomoConfig,
  token: string,
  providerReferenceId: string
): Promise<ProviderStatusQueryResult> {
  try {
    // R13-P2-07: a status read is idempotent, so a transport blip is retried rather than reported to
    // the user as "we don't know about your payment". Writes (collection/payout/refund/SMS) are not
    // retried here: they carry a provider reference and a blind repeat risks a second transaction.
    const resp = await withProviderRetry(
      () =>
        axios.get(
          `${config.baseUrl}/collection/v1_0/requesttopay/${encodeURIComponent(providerReferenceId)}`,
          {
            headers: {
              "Authorization": `Bearer ${token}`,
              "Ocp-Apim-Subscription-Key": config.subscriptionKey,
              "X-Target-Environment": "sandbox",
            },
            timeout: 15000,
            validateStatus: () => true,
          }
        ),
      {
        attempts: 3,
        onRetry: (error: any, attempt, delayMs) =>
          console.warn(
            `[momo] status query retry ${attempt} for ${providerReferenceId} in ${delayMs}ms: ${error?.message || error}`
          ),
      }
    );
    // `validateStatus: () => true` means a 5xx arrives here as a response, not a rejection, so the
    // retry above only covers transport failures; a 5xx body is classified below as retryable-looking
    // but is answered as a provider_error, which the client polls again.
    return parseProviderStatusResponse(resp.status, resp.data);
  } catch (e: any) {
    const timeout = e?.code === "ECONNABORTED" || /timeout/i.test(e?.message || "");
    return timeout
      ? { kind: "provider_timeout", error: e?.message || "Provider status query timed out." }
      : { kind: "provider_error", error: e?.message || "Provider status query failed." };
  }
}

/**
 * Lock escrow for a CONFIRMED collection, inside a Firestore transaction.
 *
 * Re-reads the authoritative records and verifies, atomically:
 *  - the stored transaction exists, is SUCCESSFUL and is not already escrow-locked
 *    (idempotence: a duplicate confirmation can never double-lock or corrupt);
 *  - the transaction's stored jobId resolves to a real job (binding check);
 *  - the job's trusted amount equals the STORED transaction amount;
 *  - the job's currency equals the stored transaction currency;
 *  - the job's escrow is not already in a funded state.
 * Only then is the job flipped to FUNDS_LOCKED_IN_ESCROW. No payout happens here.
 */
async function lockEscrowForConfirmedTransaction(
  record: StoredCollectionRecord
): Promise<{ locked: boolean; reason: string }> {
  const outcome = await db.runTransaction(async (t) => {
    const txRef = db.collection("momo_transactions").doc(record.transactionId);
    const txSnap = await t.get(txRef);
    if (!txSnap.exists) return { locked: false, reason: "transaction_missing" };
    const tx = txSnap.data() || {};
    if (String(tx.status || "").trim().toUpperCase() !== "SUCCESSFUL") {
      return { locked: false, reason: "transaction_not_confirmed" };
    }
    if (tx.escrowLockedAt) {
      return { locked: false, reason: "already_locked" };
    }

    const jobId = String(tx.jobId || "");
    if (!jobId) return { locked: false, reason: "transaction_missing_job_binding" };
    const jobRef = db.collection("jobs").doc(jobId);
    const jobSnap = await t.get(jobRef);
    if (!jobSnap.exists) return { locked: false, reason: "job_missing" };
    const job = jobSnap.data() || {};

    // The job's own trusted amount/currency is re-verified against the STORED
    // transaction — never against anything a client or callback supplied.
    const jobAmountRaw = job.grandTotal ?? job.budget ?? null;
    const jobAmount = typeof jobAmountRaw === "number" ? jobAmountRaw : Number(jobAmountRaw);
    if (!Number.isFinite(jobAmount) || jobAmount !== Number(tx.amount)) {
      return { locked: false, reason: "amount_mismatch" };
    }
    const jobCurrency = String(job.currency || "UGX");
    if (tx.currency && String(tx.currency) !== jobCurrency) {
      return { locked: false, reason: "currency_mismatch" };
    }

    const escrowStatus = String(job.escrowStatus || "");
    if (FUNDED_ESCROW_STATUSES.includes(escrowStatus)) {
      return { locked: false, reason: "escrow_already_funded" };
    }

    const now = admin.firestore.FieldValue.serverTimestamp();
    t.update(jobRef, {
      escrowStatus: "FUNDS_LOCKED_IN_ESCROW",
      escrowLockedByTransactionId: record.transactionId,
      escrowLockedAt: now,
      updatedAt: now,
    });
    t.update(txRef, { escrowLockedAt: now });
    return { locked: true, reason: "locked" };
  });

  if (outcome.locked) {
    await notifyEscrowLocked(record);
  }
  return outcome;
}

/** In-app notifications for a fresh escrow lock. Uses the authoritative uids only. */
async function notifyEscrowLocked(record: StoredCollectionRecord): Promise<void> {
  try {
    const jobSnap = await db.collection("jobs").doc(record.jobId).get();
    const job = jobSnap.data() || {};
    const recipients: string[] = [];
    if (typeof job.postedByUid === "string" && job.postedByUid) recipients.push(job.postedByUid);
    const workerId = job.assignedProviderId || job.assignedWorkerId;
    if (typeof workerId === "string" && workerId) recipients.push(workerId);
    const now = admin.firestore.FieldValue.serverTimestamp();
    for (const userId of recipients) {
      await db.collection("notifications").add({
        userId,
        type: "ESCROW_LOCKED",
        jobId: record.jobId,
        message: `Payment confirmed. Funds of UGX ${record.amount} are now locked in escrow for job #${record.jobId}.`,
        createdAt: now,
        read: false,
      });
    }
  } catch (error) {
    // Notifications are best-effort; the escrow lock itself already succeeded.
    console.error("notifyEscrowLocked failed:", error);
  }
}

/** Real (Admin SDK + axios) dependency wiring for the pure confirmation core. */
function buildConfirmationDeps(): ConfirmationDeps {
  return {
    now: () => Date.now(),
    loadConfig: (env: Record<string, string | undefined>) => loadMomoConfig(env),
    loadStoredTransaction: (ref: string) => loadStoredTransactionByRef(ref),
    async acquireToken(config: MomoConfig) {
      try {
        const basic = Buffer.from(`${config.apiUser}:${config.apiKey}`).toString("base64");
        const resp = await axios.post(
          `${config.baseUrl}/collection/token/`,
          {},
          {
            headers: {
              "Authorization": `Basic ${basic}`,
              "Ocp-Apim-Subscription-Key": config.subscriptionKey,
            },
            timeout: 15000,
            validateStatus: () => true,
          }
        );
        if (resp.status !== 200 || !resp.data?.access_token) {
          return { ok: false as const, error: `Token exchange returned HTTP ${resp.status}.` };
        }
        return { ok: true as const, token: String(resp.data.access_token) };
      } catch (e: any) {
        return { ok: false as const, error: e?.message || "Token exchange failed." };
      }
    },
    queryProviderStatus: (config, token, providerReferenceId) =>
      queryMomoProviderStatus(config, token, providerReferenceId),
    async updateTransaction(ref: string, patch: Record<string, unknown>) {
      await db.collection("momo_transactions").doc(ref).update(patch);
    },
    lockEscrow: (record: StoredCollectionRecord) => lockEscrowForConfirmedTransaction(record),
  };
}

/**
 * PAY-002: trusted payment-status query.
 *
 * Callable-only (authenticated). The caller may pass just the transaction reference
 * they received from `createMoMoCollection`; the STORED `clientUid` binding decides
 * whether they are allowed to see it — a caller can never query or mutate another
 * user's transaction. The status returned always comes from the provider re-query
 * (or the stored terminal state), never from the client.
 */
export const getMoMoCollectionStatus = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to query a payment status."
    );
  }
  const callerUid = context.auth.uid;
  const transactionRef = typeof data?.transactionRef === "string" ? data.transactionRef.trim() : "";
  if (!transactionRef) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameter: transactionRef."
    );
  }

  const stored = await loadStoredTransactionByRef(transactionRef);
  const authz = authorizeStatusQuery({ callerUid, stored });
  if (!authz.ok) {
    const httpsCode =
      authz.code === "unauthenticated" ? "unauthenticated"
        : authz.code === "not_found" ? "not-found"
          : "permission-denied";
    throw new functions.https.HttpsError(httpsCode, authz.message);
  }

  const outcome = await verifyAndConfirmCollection(
    { transactionRef, env: process.env as Record<string, string | undefined> },
    buildConfirmationDeps()
  );

  if (!outcome.ok) {
    const code =
      outcome.error.code === "unknown_reference" ? "not-found"
        : outcome.error.code === "config_error" ? "failed-precondition"
          : outcome.error.code === "token_error" ? "unavailable"
            : "internal";
    throw new functions.https.HttpsError(
      code as "not-found" | "failed-precondition" | "unavailable" | "internal",
      outcome.error.message
    );
  }

  return {
    status: outcome.status,
    transactionRef: outcome.transactionRef,
    escrowLocked: outcome.escrowLocked,
    message: outcome.message,
  };
});





// =====================================================================================
// R08-P2-02: the server-side wallet.
//
// `wallet_entries/{entryId}` is the immutable ledger (entry ids are derived, so a retry can never
// double-credit) and `wallets/{uid}` is the transactional summary a cashout is validated against.
// Both collections are deny-all in firestore.rules: only this backend writes them, and the client
// reads its balance through `getWallet`.
// =====================================================================================

const WALLET_ENTRIES = "wallet_entries";
const WALLETS = "wallets";
const WITHDRAWALS = "wallet_withdrawals";

interface WalletDoc {
  uid?: string;
  balanceUgx?: number;
  lifetimeEarnedUgx?: number;
  lifetimeWithdrawnUgx?: number;
  dayKey?: string;
  dayCount?: number;
  dayTotalUgx?: number;
  updatedAt?: number;
}

/** UTC day bucket for the cashout velocity limit. */
function walletDayKey(now: number): string {
  return new Date(now).toISOString().slice(0, 10);
}

class WalletRejection extends Error {
  constructor(readonly code: string, message: string) {
    super(message);
  }
}

/**
 * Apply one ledger entry and its effect on the summary, atomically and idempotently.
 *
 * Repeated entries (same derived id) are a no-op, and a debit that would overdraw the wallet is
 * refused unless the caller explicitly allows a negative balance (reconciliation adjustments).
 */
async function applyWalletEntry(
  entry: WalletEntry,
  options: { allowNegative?: boolean } = {}
): Promise<{ applied: boolean; balanceUgx: number }> {
  const walletRef = db.collection(WALLETS).doc(entry.uid);
  const entryRef = db.collection(WALLET_ENTRIES).doc(entry.id);
  return db.runTransaction(async (tx) => {
    const [entrySnap, walletSnap] = await Promise.all([tx.get(entryRef), tx.get(walletRef)]);
    const wallet = (walletSnap.data() || {}) as WalletDoc;
    const balance = Number(wallet.balanceUgx || 0);
    if (entrySnap.exists) {
      return { applied: false, balanceUgx: balance };
    }
    if (entry.amountUgx <= 0) {
      throw new WalletRejection("invalid-amount", "A wallet entry must be a positive amount.");
    }
    // Statement-only entries (money paid straight to the worker's Mobile Money) never touch the
    // withdrawable balance.
    const delta = entry.withdrawable === false ? 0 : entry.direction === "CREDIT" ? entry.amountUgx : -entry.amountUgx;
    const nextBalance = balance + delta;
    if (nextBalance < 0 && !options.allowNegative) {
      throw new WalletRejection("insufficient-funds", "The wallet balance cannot go below zero.");
    }
    tx.set(entryRef, {
      uid: entry.uid,
      jobId: entry.jobId ?? null,
      kind: entry.kind,
      amountUgx: entry.amountUgx,
      direction: entry.direction,
      payoutId: entry.payoutId ?? null,
      withdrawalId: entry.withdrawalId ?? null,
      description: entry.description ?? null,
      createdAt: entry.createdAt,
      withdrawable: entry.withdrawable !== false,
    });
    tx.set(
      walletRef,
      {
        uid: entry.uid,
        balanceUgx: nextBalance,
        lifetimeEarnedUgx: Number(wallet.lifetimeEarnedUgx || 0) + (entry.kind === "EARNING" ? entry.amountUgx : 0),
        lifetimeWithdrawnUgx: Number(wallet.lifetimeWithdrawnUgx || 0) + (entry.kind === "RESERVE" ? entry.amountUgx : 0),
        updatedAt: entry.createdAt,
      },
      { merge: true }
    );
    return { applied: true, balanceUgx: nextBalance };
  });
}

/** The credit that settles a provider-confirmed payout, exactly once per payout. */
async function creditPayoutEarning(payout: PayoutRecord): Promise<void> {
  if (!payout.workerId || !payout.payoutId) return;
  if (!Number.isInteger(payout.amount) || payout.amount <= 0) return;
  // A wallet cashout is not an earning (it already debited the wallet) and a material advance is
  // spent on materials; only an escrow release is recorded as income.
  const kind = payout.payoutKind || (payout.withdrawalId ? "CASHOUT" : "RELEASE");
  if (kind !== "RELEASE") return;
  await applyWalletEntry(
    earningEntry({
      uid: payout.workerId,
      jobId: payout.jobId,
      payoutId: payout.payoutId,
      amountUgx: payout.amount,
      now: Date.now(),
      // The provider paid the worker directly, so this is a statement, not a spendable balance.
      withdrawable: false,
    })
  );
}

/**
 * R08-P2-05: record how the collected total was split, once per job.
 *
 * The worker is paid the labour component from escrow; the materials bucket funds the material
 * advance; the platform fee is revenue and never leaves the platform account. The entry is written
 * with `create` so a retried release cannot book the same fee twice.
 */
async function recordPlatformFee(jobId: string, jobData: Record<string, unknown>, payoutId: string): Promise<void> {
  const quote = validateStoredQuote(jobData as { laborTotal?: unknown });
  if (!quote.ok) {
    console.warn(`platform fee not recorded for ${jobId}: ${quote.message}`);
    return;
  }
  let collectionTransactionId: string | null = null;
  try {
    const txSnap = await db
      .collection("momo_transactions")
      .where("jobId", "==", jobId)
      .where("status", "==", "SUCCESS")
      .limit(1)
      .get();
    collectionTransactionId = txSnap.empty ? null : String(txSnap.docs[0].get("transactionRef") || txSnap.docs[0].id);
  } catch {
    collectionTransactionId = null;
  }
  const entry = buildFeeLedger({
    jobId,
    quote: quote.quote,
    collectionTransactionId,
    payoutId,
    now: Date.now(),
  });
  const check = validateFeeLedger(entry);
  if (!check.ok) {
    console.warn(`platform fee not recorded for ${jobId}: ${check.message}`);
    return;
  }
  try {
    await db.collection("platform_fees").doc(feeLedgerId(jobId)).create({
      ...entry,
      recordedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
  } catch (error) {
    const code = (error as { code?: number | string }).code;
    if (code !== 6 && code !== "already-exists") {
      console.warn(`platform fee write failed for ${jobId}: ${scrubForLog((error as Error).message)}`);
    }
  }
}

/** Map a payout failure onto an HttpsError without leaking provider internals. */
function payoutFailureToHttpsError(error: { code: string; message: string }): functions.https.HttpsError {
  const codeMap: Record<string, string> = {
    unauthenticated: "unauthenticated",
    invalid_argument: "invalid-argument",
    job_not_found: "not-found",
    not_job_owner: "permission-denied",
    invalid_job_state: "failed-precondition",
    job_not_escrowed: "failed-precondition",
    amount_mismatch: "failed-precondition",
    worker_mismatch: "failed-precondition",
    config_error: "failed-precondition",
    duplicate_request: "already-exists",
    already_queued: "already-exists",
    already_pending: "failed-precondition",
    provider_error: "unavailable",
    provider_timeout: "deadline-exceeded",
  };
  return new functions.https.HttpsError((codeMap[error.code] || "internal") as any, error.message);
}

/**
 * PAY-003: Escrow Release & Worker MoMo Payout
 * Callable function triggered when resident approves job completion.
 * Credits worker's Mobile Money wallet and updates job status.
 * SEC-004: Fixed to verify job ownership before releasing escrow.
 *
 * R03-P2-03: the ONE server-side escrow release. Every caller (the callable below and the
 * deprecated `createMoMoPayout` alias) goes through this function, so the amount and the wallet
 * are always derived from server records and a second payout implementation cannot drift.
 *
 * R08-P2-01: the release DISBURSES. The provider is asked before the job is marked released, so a
 * provider failure leaves the escrow funded instead of reporting a payment that never happened.
 */
async function performEscrowRelease(
  jobId: string,
  workerId: string,
  callerUid: string,
  provider?: string
): Promise<{ success: true; payoutRef: string; message: string } | { success: true; alreadyReleased: true; payoutRef: string; message: string }> {
  // SEC-004: Verify job ownership - only job owner can release escrow
  const jobDoc = await db.collection("jobs").doc(jobId).get();
  if (!jobDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Job not found."
    );
  }

  const jobData = jobDoc.data();

  // C2-2 Fix #4 (F4): ownership is the immutable authenticated uid stored at creation time
  // (postedByUid, bound to request.auth.uid by the firestore.rules create rule).
  //
  // The previous check was `jobData?.postedByResident !== callerUid && jobData?.postedBy !== callerUid`,
  // which mixed a DISPLAY string with a uid: postedByResident is human-readable text such as
  // "You (Nakawa)", so it could never equal a Firebase uid, while the field actually written by
  // the client was never named postedBy — the escrow check therefore had no reliable owner to
  // compare and fell through to whatever the document happened to contain.
  const ownerUid = jobData?.postedByUid;
  if (!ownerUid || ownerUid !== callerUid) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only the job owner can release escrow."
    );
  }

  // REM-026: the amount and wallet are read server-side; the policy is shared with the tests.
  const workerDoc = await db.collection("users").doc(workerId).get();
  const workerMomoNumber = String(
    workerDoc.get("momoNumber") || workerDoc.get("phone") || ""
  ).trim();
  const decision = decideEscrowRelease(jobData, workerId, workerMomoNumber);
  if (decision.kind === "ALREADY_RELEASED") {
    return {
      success: true,
      alreadyReleased: true,
      payoutRef: decision.payoutRef,
      message: "Escrow was already released for this job."
    };
  }
  if (decision.kind === "REJECT") {
    throw new functions.https.HttpsError(decision.code, decision.message);
  }
  const amountUgx = decision.amount;

  // R08-P2-01: the release now actually disburses. Before this change the worker was marked
  // RELEASED_TO_WORKER and a `momo_payouts` document was written with status QUEUED — and nothing
  // ever read it, so the resident saw "paid" while no money left the platform account.
  //
  // Order matters: the provider is asked first, and the job is only marked released once the
  // provider has accepted the request (PAYOUT_PENDING). A provider failure leaves the escrow
  // funded, so the release can be retried instead of being reported as done.
  const payoutRef = `PAYOUT-${jobId}`;
  const payoutResult = await initiatePayout(
    {
      jobId,
      clientUid: callerUid,
      amountUgx,
      workerMomoNumber,
      // R08-P2-03: the payee was resolved from `users/{workerId}`, not from the client, and the
      // amount is the server-derived labour total.
      payeeResolvedServerSide: true,
      payeeWorkerId: workerId,
      expectedAmountUgx: amountUgx,
      payoutId: payoutRef,
      provider: provider || "MTN_MOMO",
      env: process.env as Record<string, string | undefined>,
    },
    buildPayoutDeps()
  );

  if (!payoutResult.ok) {
    throw payoutFailureToHttpsError(payoutResult.error);
  }

  await db.collection("jobs").doc(jobId).update({
    status: "COMPLETED",
    escrowStatus: "RELEASED_TO_WORKER",
    releasedBy: callerUid,
    releasedAt: admin.firestore.FieldValue.serverTimestamp(),
    releasePayoutRef: payoutResult.payoutId,
    releaseProviderReferenceId: payoutResult.providerReferenceId,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  // R08-P2-05: the fee split is booked as its own immutable record.
  await recordPlatformFee(jobId, jobData as Record<string, unknown>, payoutResult.payoutId);

  // Legacy mirror kept for the existing dashboards; it now carries the real provider state.
  await db.collection("momo_payouts").doc(payoutRef).set({
    payoutId: payoutResult.payoutId,
    jobId,
    workerId,
    workerMomoNumber,
    amount: amountUgx,
    currency: "UGX",
    provider: provider || "MTN_MOMO",
    status: payoutResult.status,
    providerReferenceId: payoutResult.providerReferenceId,
    releasedBy: callerUid,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });

  await db.collection("notifications").add({
    userId: workerId,
    type: "PAYOUT_RELEASED",
    jobId,
    message: `Payment of UGX ${amountUgx} has been released to your ${provider || "MTN_MOMO"} wallet.`,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    read: false
  });
  await sendUserPush(
    workerId,
    "Payment released",
    `UGX ${amountUgx} is on its way to your ${provider || "MTN_MOMO"} wallet.`,
    { type: "PAYOUT_RELEASED", jobId }
  );

  return {
    success: true,
    payoutRef: payoutResult.payoutId,
    message: `Escrow released. UGX ${amountUgx} payout pending with ${provider || "MTN_MOMO"} for ${maskMsisdn(workerMomoNumber)}.`
  };
}

export const releaseEscrowToWorker = functions.runWith(providerCallable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to release escrow."
    );
  }
  // REM-026: the client supplies only identifiers; amount and wallet are server-derived.
  const { jobId, workerId, provider } = data || {};
  if (!jobId || !workerId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameters: jobId, workerId."
    );
  }
  return performEscrowRelease(jobId, workerId, context.auth.uid, provider);
});

// =====================================================================================
// R08-P2-02: the wallet callables.
//
// The client used to keep the balance and the withdrawals in Room, so reinstalling the app erased
// the payable balance and two devices disagreed. The balance now lives in `wallets/{uid}` (derived
// from the immutable `wallet_entries` ledger), and every cashout is a server decision: the amount is
// checked against the server balance, the destination comes from the verified profile, and the
// provider payout goes through the same disbursement core as the escrow release.
// =====================================================================================

/** Sandbox allow-list of payout destinations (`MOMO_ALLOWED_NUMBERS`, comma separated). */
function sandboxPayoutAllowList(): string[] {
  const env = process.env as Record<string, string | undefined>;
  const raw = (env.MOMO_ALLOWED_NUMBERS || "").trim();
  if (!raw) return [];
  return raw.split(",").map((entry) => entry.trim()).filter((entry) => entry.length > 0);
}

/**
 * Release the reservation a failed cashout made.
 *
 * The reservation debit is compensated by a REVERSAL credit (keyed by the request id, so a retried
 * reversal cannot credit twice) and the daily counter is restored, because a payout that never left
 * the platform must not consume the worker's daily allowance.
 */
async function releaseWithdrawalReservation(payout: PayoutRecord): Promise<void> {
  const withdrawalId = payout.withdrawalId || payout.payoutId;
  if (!withdrawalId) return;
  const withdrawalRef = db.collection(WITHDRAWALS).doc(withdrawalId);
  const now = Date.now();
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(withdrawalRef);
    if (!snap.exists) return;
    const data = snap.data() as Record<string, unknown>;
    const status = String(data.status || "");
    // A cashout is PROCESSING while the provider call is in flight and PAYOUT_PENDING once it has a
    // provider reference; both are reversible, because neither is proof that money moved.
    if (status !== "PROCESSING" && status !== "PAYOUT_PENDING") return;
    const uid = String(data.uid || "");
    const amountUgx = Number(data.amountUgx || 0);
    if (!uid || !Number.isInteger(amountUgx) || amountUgx <= 0) return;
    const requestId = String(data.requestId || withdrawalId);
    const walletRef = db.collection(WALLETS).doc(uid);
    const walletSnap = await tx.get(walletRef);
    const wallet = (walletSnap.data() || {}) as WalletDoc;
    const reversal = reversalEntry({
      uid,
      withdrawalId,
      amountUgx,
      requestId,
      now,
      reason: "provider payout failed",
    });
    const entryRef = db.collection(WALLET_ENTRIES).doc(reversal.id);
    const entrySnap = await tx.get(entryRef);
    if (entrySnap.exists) return;
    tx.set(entryRef, {
      uid: reversal.uid,
      jobId: null,
      kind: reversal.kind,
      amountUgx: reversal.amountUgx,
      direction: reversal.direction,
      payoutId: null,
      withdrawalId,
      description: reversal.description ?? null,
      createdAt: reversal.createdAt,
      withdrawable: true,
    });
    const sameDay = wallet.dayKey === walletDayKey(now);
    const dayCount = Math.max(0, Number(wallet.dayCount || 0) - (sameDay ? 1 : 0));
    const dayTotalUgx = Math.max(0, Number(wallet.dayTotalUgx || 0) - (sameDay ? amountUgx : 0));
    tx.set(
      walletRef,
      {
        uid,
        balanceUgx: Number(wallet.balanceUgx || 0) + amountUgx,
        lifetimeWithdrawnUgx: Math.max(0, Number(wallet.lifetimeWithdrawnUgx || 0) - amountUgx),
        dayKey: sameDay ? wallet.dayKey : walletDayKey(now),
        dayCount,
        dayTotalUgx,
        updatedAt: now,
      },
      { merge: true }
    );
    tx.set(withdrawalRef, {
      status: "FAILED",
      failureReason: payout.reason || "provider payout failed",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });
  });
}

/** The worker's wallet: server balance, limits and the most recent statement lines. */
export const getWallet = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required to open the wallet.");
  }
  const uid = context.auth.uid;
  const walletSnap = await db.collection(WALLETS).doc(uid).get();
  const entriesSnap = await db
    .collection(WALLET_ENTRIES)
    .where("uid", "==", uid)
    .orderBy("createdAt", "desc")
    .limit(50)
    .get();
  const entries: WalletEntry[] = entriesSnap.docs.map((doc) => {
    const entry = doc.data() as Record<string, unknown>;
    return {
      id: doc.id,
      uid: String(entry.uid || uid),
      jobId: (entry.jobId as string | null) ?? null,
      kind: String(entry.kind || "ADJUSTMENT") as WalletEntry["kind"],
      amountUgx: Number(entry.amountUgx || 0),
      direction: String(entry.direction || "CREDIT") === "DEBIT" ? "DEBIT" : "CREDIT",
      payoutId: (entry.payoutId as string | null) ?? null,
      withdrawalId: (entry.withdrawalId as string | null) ?? null,
      description: (entry.description as string | null) ?? null,
      createdAt: Number(entry.createdAt || 0),
      withdrawable: entry.withdrawable !== false,
    };
  });
  const summary = summariseWallet(entries);
  const balanceUgx = Number((walletSnap.data() as WalletDoc | undefined)?.balanceUgx ?? summary.availableUgx);

  const userSnap = await db.collection("users").doc(uid).get();
  const storedMsisdn = String(userSnap.get("momoNumber") || userSnap.get("phone") || "").trim();

  return {
    balanceUgx,
    availableUgx: Math.max(0, balanceUgx),
    lifetimeEarnedUgx: Number((walletSnap.data() as WalletDoc | undefined)?.lifetimeEarnedUgx ?? summary.creditsUgx),
    lifetimeWithdrawnUgx: Number((walletSnap.data() as WalletDoc | undefined)?.lifetimeWithdrawnUgx ?? 0),
    payoutNumberMasked: maskMsisdn(storedMsisdn),
    cashoutMinUgx: CASHOUT_MIN_UGX,
    cashoutDailyLimitUgx: CASHOUT_DAILY_LIMIT_UGX,
    cashoutMaxPerDay: CASHOUT_MAX_PER_DAY,
    entries: entries.map((entry) => ({
      id: entry.id,
      kind: entry.kind,
      amountUgx: entry.amountUgx,
      direction: entry.direction,
      description: entry.description,
      createdAt: entry.createdAt,
      withdrawable: entry.withdrawable !== false,
    })),
  };
});

/**
 * R08-P2-02: request a wallet cashout.
 *
 * `requestId` is the client's idempotency key: a retried request resolves to the same withdrawal
 * document and never reserves the amount twice.
 */
export const requestWalletCashout = functions.runWith(providerCallable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required to withdraw.");
  }
  const uid = context.auth.uid;
  const { amountUgx, requestId } = data || {};
  if (typeof requestId !== "string" || requestId.trim().length < 8) {
    throw new functions.https.HttpsError("invalid-argument", "requestId is required so a retry cannot pay twice.");
  }

  // The destination is never taken from the request: it comes from the verified profile and must
  // pass the cool-off and allow-list rules.
  const userSnap = await db.collection("users").doc(uid).get();
  const changedAtRaw = userSnap.get("momoNumberUpdatedAt");
  const changedAt = typeof changedAtRaw === "number"
    ? changedAtRaw
    : changedAtRaw && typeof changedAtRaw.toMillis === "function"
      ? changedAtRaw.toMillis()
      : null;
  const destination = decideCashoutDestination({
    storedMsisdn: userSnap.get("momoNumber") || userSnap.get("phone"),
    verified: userSnap.get("phoneVerified") === true || userSnap.get("momoNumberVerified") === true,
    lastChangedAt: changedAt,
    now: Date.now(),
    allowList: sandboxPayoutAllowList(),
  });
  if (!destination.ok) {
    throw new functions.https.HttpsError("failed-precondition", destination.message);
  }

  const withdrawalId = cashoutKey(uid, requestId.trim());
  const withdrawalRef = db.collection(WITHDRAWALS).doc(withdrawalId);
  const walletRef = db.collection(WALLETS).doc(uid);
  const now = Date.now();
  const dayKey = walletDayKey(now);

  let reservedBalance = 0;
  try {
    reservedBalance = await db.runTransaction(async (tx) => {
      const [walletSnap, withdrawalSnap] = await Promise.all([tx.get(walletRef), tx.get(withdrawalRef)]);
      const wallet = (walletSnap.data() || {}) as WalletDoc;
      const sameDay = wallet.dayKey === dayKey;
      const decision = decideCashout(
        amountUgx,
        {
          availableUgx: Number(wallet.balanceUgx || 0),
          todayCount: sameDay ? Number(wallet.dayCount || 0) : 0,
          todayTotalUgx: sameDay ? Number(wallet.dayTotalUgx || 0) : 0,
          duplicateKey: withdrawalSnap.exists,
        },
        withdrawalId
      );
      if (!decision.ok) {
        throw new WalletRejection(decision.code, decision.message);
      }
      const amount = decision.amountUgx;
      const nextBalance = Number(wallet.balanceUgx || 0) - amount;
      const entry = cashoutEntry({ uid, withdrawalId, amountUgx: amount, requestId: requestId.trim(), now });
      const entryRef = db.collection(WALLET_ENTRIES).doc(entry.id);
      tx.set(withdrawalRef, {
        uid,
        requestId: requestId.trim(),
        amountUgx: amount,
        currency: "UGX",
        msisdnMasked: maskMsisdn(destination.msisdn),
        msisdn: destination.msisdn,
        status: "PROCESSING",
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      tx.set(entryRef, {
        uid,
        jobId: null,
        kind: entry.kind,
        amountUgx: entry.amountUgx,
        direction: entry.direction,
        payoutId: null,
        withdrawalId,
        description: entry.description ?? null,
        createdAt: entry.createdAt,
        withdrawable: true,
      });
      tx.set(
        walletRef,
        {
          uid,
          balanceUgx: nextBalance,
          dayKey,
          dayCount: (sameDay ? Number(wallet.dayCount || 0) : 0) + 1,
          dayTotalUgx: (sameDay ? Number(wallet.dayTotalUgx || 0) : 0) + amount,
          lifetimeWithdrawnUgx: Number(wallet.lifetimeWithdrawnUgx || 0) + amount,
          updatedAt: now,
        },
        { merge: true }
      );
      return nextBalance;
    });
  } catch (error) {
    if (error instanceof WalletRejection) {
      const code =
        error.code === "duplicate_request" ? "already-exists"
          : error.code === "exceeds_available" ? "failed-precondition"
            : error.code === "invalid_amount" ? "invalid-argument"
              : "failed-precondition";
      throw new functions.https.HttpsError(code as any, error.message);
    }
    throw error;
  }

  // The reservation is durable before the provider is called, so a crash here leaves a FAILED
  // withdrawal that the reversal below (or the reconciliation sweep) compensates — never free money.
  const payoutResult = await initiateWithdrawalPayout(
    {
      withdrawalId,
      uid,
      amountUgx: Number(amountUgx),
      msisdn: destination.msisdn,
      provider: "MTN_MOMO",
      env: process.env as Record<string, string | undefined>,
    },
    buildPayoutDeps()
  );

  if (!payoutResult.ok) {
    await releaseWithdrawalReservation({
      payoutId: withdrawalId,
      withdrawalId,
      jobId: withdrawalId,
      workerId: uid,
      workerMomoNumber: destination.msisdn,
      amount: Number(amountUgx),
      currency: "UGX",
      provider: "MTN_MOMO",
      status: "PAYOUT_FAILED",
      clientUid: uid,
      releasedBy: uid,
      transactionId: withdrawalId,
      providerReferenceId: "",
      reason: payoutResult.error.message,
      payoutKind: "CASHOUT",
    });
    throw payoutFailureToHttpsError(payoutResult.error);
  }

  await withdrawalRef.set(
    { status: "PAYOUT_PENDING", payoutRef: payoutResult.payoutId, updatedAt: admin.firestore.FieldValue.serverTimestamp() },
    { merge: true }
  );

  await db.collection("notifications").add({
    userId: uid,
    type: "WITHDRAWAL_PENDING",
    jobId: null,
    message: `Your withdrawal of UGX ${amountUgx} is on its way to ${maskMsisdn(destination.msisdn)}.`,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    read: false,
  });

  return {
    success: true,
    withdrawalId,
    payoutRef: payoutResult.payoutId,
    status: "PAYOUT_PENDING",
    amountUgx: Number(amountUgx),
    balanceUgx: reservedBalance,
    message: `UGX ${amountUgx} is on its way to ${maskMsisdn(destination.msisdn)}.`,
  };
});

/**
 * Cancel a cashout that has not reached the provider yet.
 *
 * Once the provider holds a payout reference, cancelling here would be a lie: the money may already
 * be moving, so the request is refused and the payout must be reconciled instead.
 */
export const cancelWalletCashout = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Authentication required to cancel a withdrawal.");
  }
  const { requestId, withdrawalId } = data || {};
  const ref = typeof withdrawalId === "string" && withdrawalId
    ? withdrawalId
    : typeof requestId === "string" && requestId
      ? cashoutKey(context.auth.uid, requestId.trim())
      : "";
  if (!ref) {
    throw new functions.https.HttpsError("invalid-argument", "requestId or withdrawalId is required.");
  }
  const snap = await db.collection(WITHDRAWALS).doc(ref).get();
  if (!snap.exists) throw new functions.https.HttpsError("not-found", "Withdrawal not found.");
  if (String(snap.get("uid") || "") !== context.auth.uid) {
    throw new functions.https.HttpsError("permission-denied", "You may only cancel your own withdrawal.");
  }
  const status = String(snap.get("status") || "");
  if (status !== "PROCESSING") {
    throw new functions.https.HttpsError(
      "failed-precondition",
      status === "CANCELLED"
        ? "This withdrawal is already cancelled."
        : "This withdrawal can no longer be cancelled; it is already with the payment provider."
    );
  }
  if (snap.get("payoutRef")) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "This withdrawal is already with the payment provider and must be reconciled instead."
    );
  }
  await releaseWithdrawalReservation({
    payoutId: ref,
    withdrawalId: ref,
    jobId: ref,
    workerId: context.auth.uid,
    workerMomoNumber: "",
    amount: Number(snap.get("amountUgx") || 0),
    currency: "UGX",
    provider: "MTN_MOMO",
    status: "PAYOUT_FAILED",
    clientUid: context.auth.uid,
    releasedBy: context.auth.uid,
    transactionId: ref,
    providerReferenceId: "",
    reason: "cancelled by the worker",
    payoutKind: "CASHOUT",
  });
  await db.collection(WITHDRAWALS).doc(ref).set({ status: "CANCELLED" }, { merge: true });
  return { success: true, withdrawalId: ref, status: "CANCELLED" };
});

/**
 * R08-P2-06: the reconciliation sweep.
 *
 * Nothing compared the provider's view with the platform's records. Every hour this reads the
 * payouts still open, asks the provider, applies the same transitions the status callable uses, and
 * writes an immutable report so an operator can see what was settled, what was flagged and what
 * could not be read.
 */
export const reconcilePayments = functions.pubsub.schedule("every 60 minutes").onRun(async () => {
  const payoutsSnap = await db
    .collection("payouts")
    .where("status", "in", ["PAYOUT_PENDING"])
    .orderBy("createdAt", "asc")
    .limit(100)
    .get();

  const payouts: PayoutRecord[] = payoutsSnap.docs.map((doc) =>
    toPayoutRecord({ ...(doc.data() as Record<string, unknown>), payoutId: doc.id })
  );

  const config = loadPayoutConfig(process.env as Record<string, string | undefined>);
  const providerViews = new Map<string, ProviderPayoutStatusResult>();
  if (!("error" in config)) {
    const deps = buildPayoutDeps();
    const token = await deps.acquireToken(config);
    if (token.ok) {
      for (const payout of payouts) {
        if (!payout.providerReferenceId) continue;
        try {
          providerViews.set(
            payout.payoutId,
            await deps.queryProviderPayoutStatus(config, token.token, payout.providerReferenceId)
          );
        } catch (error) {
          console.warn(
            `reconciliation: provider query failed for ${payout.payoutId}: ${scrubForLog((error as Error).message)}`
          );
        }
      }
    }
  }

  const report = buildReconciliationReport({
    now: Date.now(),
    staleAfterMs: 5 * 60 * 1000,
    payouts: payouts.map((payout) => ({
      payoutId: payout.payoutId,
      jobId: payout.jobId,
      workerId: payout.workerId,
      amount: payout.amount,
      currency: payout.currency,
      status: payout.status,
      providerReferenceId: payout.providerReferenceId,
      createdAt: payout.createdAt ?? null,
      lastCheckedAt: payout.lastCheckedAt ?? null,
    })),
    providerFor: (view) =>
      providerViews.get(view.payoutId) ?? { kind: "networkError" },
  });

  for (const item of report.items) {
    const payout = payouts.find((entry) => entry.payoutId === item.payoutId);
    if (!payout || item.action === "NOOP" || item.action === "UNREADABLE") continue;
    const provider = providerViews.get(payout.payoutId);
    if (!provider) continue;
    const { newStatus, reason } = decidePayoutStatus({ stored: payout, provider });
    if (newStatus === payout.status) continue;
    const patch: Record<string, unknown> = { status: newStatus, reason, lastCheckedAt: Date.now() };
    if (provider.kind === "ok" && provider.financialTransactionId) {
      patch.providerFinancialTransactionId = provider.financialTransactionId;
    }
    await db.collection("payouts").doc(payout.payoutId).update(patch);
    if (newStatus === "PAYOUT_SUCCESSFUL") {
      await creditPayoutEarning({ ...payout, status: newStatus });
    } else if (newStatus === "PAYOUT_FAILED" && payout.payoutKind === "CASHOUT") {
      await releaseWithdrawalReservation({ ...payout, status: newStatus, reason }).catch((error) => {
        console.error("reconciliation reversal failed", scrubForLog((error as Error).message));
      });
    }
  }

  await db.collection("reconciliation_reports").add({
    ...report,
    summary: reportLine(report),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  console.log(reportLine(report));
});

// =====================================================================================
// REM-027: refunds. Money moves only through the provider; a local status alone never means
// "refunded". A provider failure records a FAILED refund and leaves escrow untouched.
// =====================================================================================

async function performEscrowRefund(
  jobId: string,
  amount: number,
  reason: string,
  actor: string
): Promise<{ refundRef: string; status: string }> {
  if (!jobId) throw new functions.https.HttpsError("invalid-argument", "jobId is required.");
  const config = loadMomoConfig(process.env as Record<string, string | undefined>);
  if ("error" in config) {
    throw new functions.https.HttpsError("failed-precondition", config.error);
  }

  const txSnap = await db.collection("momo_transactions")
    .where("jobId", "==", jobId)
    .where("status", "==", "SUCCESS")
    .limit(1)
    .get();
  const transaction = txSnap.empty ? undefined : txSnap.docs[0].data();

  const existing = await db.collection("refunds")
    .where("jobId", "==", jobId)
    .where("status", "in", ["PENDING", "SUCCESS"])
    .limit(1)
    .get();
  const decision = decideRefund(transaction, amount, !existing.empty);
  if (decision.kind === "REJECT") {
    throw new functions.https.HttpsError("failed-precondition", decision.message);
  }

  const basic = Buffer.from(`${config.apiUser}:${config.apiKey}`).toString("base64");
  const tokenResp = await axios.post(`${config.baseUrl}/collection/token/`, {}, {
    headers: {
      "Authorization": `Basic ${basic}`,
      "Ocp-Apim-Subscription-Key": config.subscriptionKey,
    },
    timeout: 15000,
    validateStatus: () => true,
  });
  if (tokenResp.status !== 200 || !tokenResp.data?.access_token) {
    throw new functions.https.HttpsError("unavailable", "Could not authenticate with the refund provider.");
  }

  const refundRef = crypto.randomUUID();
  let providerStatus = 0;
  try {
    const resp = await axios.post(
      `${config.baseUrl}/collection/v1_0/requesttopay/${decision.providerReferenceId}/refund`,
      buildRefundPayload(decision.amount, decision.currency, jobId),
      {
        headers: {
          "Authorization": `Bearer ${String(tokenResp.data.access_token)}`,
          "X-Reference-Id": refundRef,
          "X-Target-Environment": config.environment,
          "Ocp-Apim-Subscription-Key": config.subscriptionKey,
          "Content-Type": "application/json",
        },
        timeout: 20000,
        validateStatus: () => true,
      }
    );
    providerStatus = resp.status;
  } catch (e: any) {
    providerStatus = 0;
  }

  const accepted = [200, 201, 202].includes(providerStatus);
  await db.collection("refunds").doc(refundRef).set({
    refundRef,
    jobId,
    amount: decision.amount,
    currency: decision.currency,
    providerReferenceId: decision.providerReferenceId,
    status: accepted ? "PENDING" : "FAILED",
    reason,
    requestedBy: actor,
    providerStatus,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  if (!accepted) {
    throw new functions.https.HttpsError(
      "unavailable",
      `Refund provider returned HTTP ${providerStatus || "network error"}.`
    );
  }

  await db.collection("jobs").doc(jobId).update({
    escrowStatus: "REFUND_PENDING",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  return { refundRef, status: "PENDING" };
}

/** Admin-only: request a full or partial refund of a settled escrow collection. */
export const refundEscrow = functions.runWith(providerCallable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to request a refund.");
  }
  if (!hasAdminClaim(context.auth.token)) {
    throw new functions.https.HttpsError("permission-denied", "Only administrators can refund escrow.");
  }
  const { jobId, amount, reason } = data || {};
  return performEscrowRefund(jobId, Number(amount), String(reason || "Admin refund"), context.auth.uid);
});

// =====================================================================================
// R08-P2-07: refunds are polled, and a partial refund changes the escrow.
//
// A refund used to be written as PENDING and never checked again, so a rejected refund looked
// outstanding for ever; and a partial refund left the job's escrow untouched, so the worker could be
// released the full amount after part of it had gone back to the resident.
// =====================================================================================

/** Provider-verified refund status, with the escrow adjustment applied on success. */
async function refreshRefundStatus(refundRef: string): Promise<{ status: string; reason: string } | null> {
  const refundSnap = await db.collection("refunds").doc(refundRef).get();
  if (!refundSnap.exists) return null;
  const refund = refundSnap.data() as Record<string, unknown>;
  const stored = String(refund.status || "").toUpperCase();
  if (stored === "SUCCESS" || stored === "FAILED") {
    return { status: stored, reason: "already_terminal" };
  }

  const config = loadMomoConfig(process.env as Record<string, string | undefined>);
  if ("error" in config) return { status: stored || "PENDING", reason: "config_error" };

  let provider: ProviderStatusQueryResult;
  const providerReferenceId = String(refund.providerReferenceId || "");
  if (!providerReferenceId) {
    provider = { kind: "provider_error", error: "missing provider reference" };
  } else {
    const basic = Buffer.from(`${config.apiUser}:${config.apiKey}`).toString("base64");
    try {
      const tokenResp = await axios.post(`${config.baseUrl}/collection/token/`, {}, {
        headers: {
          "Authorization": `Basic ${basic}`,
          "Ocp-Apim-Subscription-Key": config.subscriptionKey,
        },
        timeout: 15000,
        validateStatus: () => true,
      });
      provider = tokenResp.status !== 200 || !tokenResp.data?.access_token
        ? { kind: "provider_error", error: "token exchange failed" }
        : await queryMomoProviderStatus(config, String(tokenResp.data.access_token), providerReferenceId);
    } catch (error) {
      provider = { kind: "provider_error", error: (error as Error).message };
    }
  }

  const action = decideRefundStatus(
    {
      refundRef,
      jobId: refund.jobId as string | null,
      amount: refund.amount as number | null,
      currency: refund.currency as string | null,
      status: refund.status as string | null,
      providerReferenceId,
    },
    provider
  );

  if (action.kind === "NOOP") return { status: stored || "PENDING", reason: action.note };

  await db.collection("refunds").doc(refundRef).update({
    status: action.status,
    statusReason: action.note,
    lastCheckedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  if (action.kind === "SETTLE" && action.status === "SUCCESS") {
    await applyRefundEffect(refundRef, refund);
  }
  return { status: action.status, reason: action.note };
}

/**
 * Apply a provider-confirmed refund to the job's escrow.
 *
 * The escrow that remains is what the worker can still be paid; a full refund closes it (`REFUNDED`,
 * which the rules and the payout validator already treat as unpayable).
 */
async function applyRefundEffect(refundRef: string, refund: Record<string, unknown>): Promise<void> {
  const jobId = String(refund.jobId || "");
  if (!jobId) return;
  const refundedThisTime = Math.round(Number(refund.amount) || 0);

  const previousSnap = await db.collection("refunds")
    .where("jobId", "==", jobId)
    .where("status", "==", "SUCCESS")
    .limit(20)
    .get();
  const previouslyRefunded = previousSnap.docs
    .filter((doc) => doc.id !== refundRef)
    .reduce((total, doc) => total + (Math.round(Number(doc.get("amount")) || 0)), 0);

  const jobRef = db.collection("jobs").doc(jobId);
  const jobSnap = await jobRef.get();
  if (!jobSnap.exists) return;
  const job = jobSnap.data() || {};

  let funded = Math.round(Number(job.grandTotal ?? job.laborTotal ?? job.budget ?? 0));
  try {
    const txSnap = await db.collection("momo_transactions")
      .where("jobId", "==", jobId)
      .where("status", "==", "SUCCESS")
      .limit(1)
      .get();
    if (!txSnap.empty) {
      funded = Math.round(Number(txSnap.docs[0].get("amount")) || funded);
    }
  } catch {
    // keep the job-derived amount
  }

  const escrow = applyRefundToEscrow({
    fundedUgx: funded,
    previouslyRefundedUgx: previouslyRefunded,
    refundAmountUgx: refundedThisTime,
  });

  await jobRef.update({
    escrowStatus: escrow.escrowStatus,
    refundedUgx: escrow.refundedTotalUgx,
    remainingEscrowUgx: escrow.remainingEscrowUgx,
    lastRefundRef: refundRef,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await db.collection("notifications").add({
    userId: job.postedByUid || "",
    type: escrow.fullyRefunded ? "REFUND_COMPLETED" : "REFUND_PARTIAL",
    jobId,
    message: escrow.fullyRefunded
      ? `Your escrow for this job was refunded (UGX ${escrow.refundedTotalUgx}).`
      : `UGX ${refundedThisTime} of your escrow was refunded; UGX ${escrow.remainingEscrowUgx} is still held for the job.`,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    read: false,
  }).catch(() => undefined);
}

/** Poll a refund the caller is allowed to see (the job's owner, an administrator, or the requester). */
export const getRefundStatus = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to check a refund.");
  }
  const { refundRef } = data || {};
  if (!refundRef) throw new functions.https.HttpsError("invalid-argument", "refundRef is required.");

  const refundSnap = await db.collection("refunds").doc(refundRef).get();
  if (!refundSnap.exists) throw new functions.https.HttpsError("not-found", "Refund not found.");
  const refund = refundSnap.data() as Record<string, unknown>;

  const callerUid = context.auth.uid;
  const isAdmin = hasAdminClaim(context.auth.token);
  let allowed = isAdmin || String(refund.requestedBy || "") === callerUid;
  if (!allowed && refund.jobId) {
    const jobSnap = await db.collection("jobs").doc(String(refund.jobId)).get();
    allowed = jobSnap.exists && String(jobSnap.get("postedByUid") || "") === callerUid;
  }
  if (!allowed) {
    throw new functions.https.HttpsError("permission-denied", "You may not view this refund.");
  }

  const result = await refreshRefundStatus(refundRef);
  return {
    refundRef,
    status: result?.status || String(refund.status || "PENDING"),
    amount: Number(refund.amount || 0),
    currency: String(refund.currency || "UGX"),
    jobId: refund.jobId || null,
    message: result?.status === "SUCCESS"
      ? "The refund was confirmed by the payment provider."
      : result?.status === "FAILED"
        ? "The payment provider rejected the refund; the escrow is unchanged."
        : "The refund is still pending with the payment provider.",
  };
});

// =====================================================================================
// R09-P2-04: the retention sweep.
//
// Nothing purged or minimised anything: KYC scans, chats, SOS incidents and notifications were kept
// for ever because no window had been decided. `RETENTION_POLICY` now states a window, an action and
// a reason per category, and this daily job enforces it — using `planRetentionSweep`, so the plan a
// test sees is the plan the sweep executes.
// =====================================================================================

/** How many documents one category may act on in a single run. */
const RETENTION_BATCH_SIZE = 200;

/** Read one category's due documents. Errors are reported, never fatal to the whole sweep. */
async function readRetentionCandidates(
  categoryId: string
): Promise<Array<{ id: string; ref: FirebaseFirestore.DocumentReference; data: Record<string, unknown> }>> {
  const category = categoryById(categoryId);
  if (!category) return [];
  const cutoff = Date.now() - category.retentionDays * 24 * 60 * 60 * 1000;
  const query = category.collectionGroup
    ? db.collectionGroup(category.collection)
    : db.collection(category.collection);
  const snapshot = await query
    .where(category.timestampField, "<", cutoff)
    .orderBy(category.timestampField, "asc")
    .limit(RETENTION_BATCH_SIZE)
    .get();
  return snapshot.docs.map((doc) => ({ id: doc.id, ref: doc.ref, data: doc.data() as Record<string, unknown> }));
}

/**
 * Daily retention sweep.
 *
 * Every category is attempted even if an earlier one fails, the counts are written to
 * `retention_reports` (admin-readable only), and the operator log carries counts — never a document
 * id or a value that was removed.
 */
export const applyRetentionPolicy = functions.pubsub.schedule("every 24 hours").onRun(async () => {
  const now = Date.now();
  const categories: Array<{
    id: string;
    action: "PURGE" | "ANONYMISE";
    retentionDays: number;
    purged: number;
    anonymised: number;
    retained: number;
    undated: number;
    error?: string;
  }> = [];

  for (const category of RETENTION_POLICY) {
    const entry = {
      id: category.id,
      action: category.action,
      retentionDays: category.retentionDays,
      purged: 0,
      anonymised: 0,
      retained: 0,
      undated: 0,
      error: undefined as string | undefined,
    };
    try {
      const candidates = await readRetentionCandidates(category.id);
      const plan = planRetentionSweep({
        categoryId: category.id,
        now,
        docs: candidates.map((candidate) => ({ id: candidate.id, data: candidate.data })),
        maxActions: RETENTION_BATCH_SIZE,
      });
      const refs = new Map(candidates.map((candidate) => [candidate.id, candidate.ref]));

      for (const id of plan.purgeIds) {
        const ref = refs.get(id);
        if (!ref) continue;
        await ref.delete();
        entry.purged += 1;
      }
      const patch = anonymisePatch(category.id);
      for (const id of plan.anonymiseIds) {
        const ref = refs.get(id);
        if (!ref) continue;
        await ref.set(patch, { merge: true });
        entry.anonymised += 1;
      }
      entry.retained = plan.retained;
      entry.undated = plan.undatedIds.length;
    } catch (error) {
      // R09-P2-05: a provider error body can echo a phone number or a URL; the report is
      // admin-readable, so the message is scrubbed before it is persisted, not just before a log.
      entry.error = scrubForLog((error as Error).message).slice(0, 200);
    }
    categories.push(entry);
  }

  const report = summariseRetention(now, categories);
  await db.collection("retention_reports").add({
    ...report,
    summary: retentionReportLine(report),
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  }).catch(() => undefined);
  console.log(retentionReportLine(report));
  return null;
});

/** Hourly: sweep pending refunds so a rejected refund cannot stay pending for ever. */
export const reconcileRefunds = functions.pubsub.schedule("every 60 minutes").onRun(async () => {
  const snap = await db.collection("refunds")
    .where("status", "==", "PENDING")
    .orderBy("createdAt", "asc")
    .limit(100)
    .get();

  let checked = 0;
  let settled = 0;
  let unchanged = 0;
  for (const doc of snap.docs) {
    checked += 1;
    try {
      const result = await refreshRefundStatus(doc.id);
      if (result && (result.status === "SUCCESS" || result.status === "FAILED")) settled += 1;
      else unchanged += 1;
    } catch (error) {
      console.warn(`refund reconciliation failed for ${doc.id}: ${scrubForLog((error as Error).message)}`);
      unchanged += 1;
    }
  }

  await db.collection("reconciliation_reports").add({
    kind: "REFUNDS",
    generatedAt: Date.now(),
    checked,
    settled,
    flagged: 0,
    unchanged,
    unreadable: 0,
    creditedUgx: 0,
    items: [],
    summary: `refund reconciliation: checked=${checked} settled=${settled} unchanged=${unchanged}`,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  console.log(`refund reconciliation: checked=${checked} settled=${settled} unchanged=${unchanged}`);
});

/**
 * MODULE 3: PAY-002 — provider callback endpoint (pull-to-verify trigger).
 *
 * MTN delivers the asynchronous result of a collection to the `X-Callback-Url` that
 * accompanied the RequestToPay. Per the provider's documentation the callback carries
 * NO authentication (no HMAC, no signature header, no shared secret, no mTLS), so this
 * endpoint treats the entire body as UNTRUSTED:
 *   - the only field read is a reference hint (`referenceId`, or the legacy
 *     `transactionRef` alias) used to locate an already-stored transaction;
 *   - an unknown reference is rejected with 404 and no state is created;
 *   - status, amount, jobId and any user value in the body are IGNORED — the stored
 *     `momo_transactions` record is the only authority for them;
 *   - the actual confirmation is performed by re-querying the provider's authenticated
 *     status endpoint, exactly like the `getMoMoCollectionStatus` callable. A forged
 *     callback can therefore do nothing except cause a legitimate re-check.
 * Processing is idempotent and terminal states are immutable (replays are no-ops).
 */
export const processMoMoWebhook = functions.https.onRequest(
  async (req: functions.https.Request, res) => {
  try {
    if (req.method !== "POST") {
      res.status(405).send({ error: "Method not allowed." });
      return;
    }

    // REM-021: authenticate, freshness-check and throttle the callback before any lookup.
    const auth = authorizeWebhook({
      headers: req.headers as Record<string, unknown>,
      body: (req.body ?? {}) as Record<string, unknown>,
      secrets: loadWebhookSecrets(process.env as Record<string, string | undefined>),
      now: Date.now(),
    });
    if (!auth.ok) {
      const status = auth.reason === "not_configured" ? 503 : auth.reason === "stale_timestamp" ? 408 : 401;
      res.status(status).send({ error: "Unauthorized webhook.", code: auth.reason });
      return;
    }

    const referenceHint = extractCallbackReferenceHint(req.body);
    if (!referenceHint) {
      res.status(400).send({ error: "Invalid webhook payload: no reference id." });
      return;
    }

    const rateRef = db
      .collection("webhook_rate_limits")
      .doc(String(referenceHint).replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 200));
    const rateDecision = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(rateRef);
      const decision = evaluateWebhookRate(snapshot.exists ? (snapshot.data() as WebhookRateRecord) : undefined, Date.now());
      if (decision.allowed) {
        transaction.set(rateRef, { ...decision.next, updatedAt: Date.now() }, { merge: true });
      }
      return decision;
    });
    if (!rateDecision.allowed) {
      res.status(429).send({ error: "Too many callbacks for this reference." });
      return;
    }

    const stored = await loadStoredTransactionByRef(referenceHint);
    if (!stored) {
      // Unknown references are rejected outright — a callback can never create or
      // adopt a transaction.
      res.status(404).send({ error: "Unknown transaction reference." });
      return;
    }

    const outcome = await verifyAndConfirmCollection(
      { transactionRef: stored.transactionId, env: process.env as Record<string, string | undefined> },
      buildConfirmationDeps()
    );

    if (!outcome.ok) {
      // Fail closed with an error status so the provider may retry; nothing was
      // fabricated and escrow was not touched.
      res.status(502).send({ error: "Confirmation unavailable.", code: outcome.error.code });
      return;
    }

    res.status(200).send({
      status: outcome.status,
      transactionRef: outcome.transactionRef,
      escrowLocked: outcome.escrowLocked,
    });
  } catch (error: any) {
    console.error("Error processing MoMo callback:", scrubForLog(error));
    res.status(500).send({ error: error.message });
  }
});


/**
 * MODULE 4: SMS NOTIFICATION TRIGGER & RADIUS BROADCAST
 * Cloud Function trigger listening for new job creations.
 * Queries verified workers within 5km radius and dispatches SMS alerts to offline 2G/3G phones.
 */
export const onJobCreated = functions.runWith({ ...PROVIDER_CALL, secrets: AFRICA_TALKING_SECRETS }).firestore
  .document("jobs/{jobId}")
  .onCreate(async (snapshot, context) => {
    const job = snapshot.data();
    if (!job) return;

    const district = job.district || "Sunnyside";
    const jobTitle = job.title || "Urgent Home Repair";
    const budget = job.budget || 0;

    // Query verified providers in same district with APPROVED KYC
    const providersSnapshot = await db
      .collection("providers")
      .where("neighborhood", "==", district)
      .where("kycStatus", "==", "APPROVED")
      .limit(10)
      .get();

    const recipientPhones: string[] = [];
    providersSnapshot.forEach((doc) => {
      const data = doc.data();
      if (data.momoNumber) {
        recipientPhones.push(data.momoNumber);
      }
    });

    // Send SMS alerts via Africa's Talking API abstraction
    for (const phone of recipientPhones) {
      await sendSmsAlert(
        phone,
        `[SkillsExchange Alert] New ${district} Job: '${jobTitle}' ($${budget}). Open app or reply YES to bid.`
      );
    }

    console.log(`Dispatched SMS job alerts to ${recipientPhones.length} verified offline workers in ${district}.`);
  });

/**
 * MODULE 4: AFRICA'S TALKING / TWILIO SMS INTEGRATION UTILITY
 */
const smsTransport: SmsTransport = async (url, body, headers) => {
  const response = await axios.post(url, body, {
    headers,
    timeout: 15000,
    validateStatus: () => true,
  });
  return { status: response.status, data: response.data };
};

/**
 * REM-034: deliver a real SMS. Returns false — and never claims delivery — when the provider is
 * unconfigured, rejects the message or cannot be reached. Phone numbers are masked in logs.
 */
async function sendSmsAlert(phoneNumber: string, message: string): Promise<boolean> {
  const config = loadSmsConfig(process.env as Record<string, string | undefined>);
  if (!config) {
    console.error("SMS not configured (AFRICASTALKING_API_KEY/USERNAME); alert not sent", {
      phone: maskPhone(phoneNumber),
    });
    return false;
  }
  const send = createSmsSender(config, smsTransport);
  const result = await send([phoneNumber], message);
  if (!result.ok) {
    console.error("SMS delivery failed", { phone: maskPhone(phoneNumber), error: result.error });
  }
  return result.ok;
}

/**
 * REM-034 / REM-035: alert platform security and the user's emergency contact when an SOS is
 * raised, and push to the dispatch topic. Delivery is attempted once per incident; the outcome is
 * recorded on the incident so the admin queue can see what actually happened.
 */
export const onSosIncidentCreated = functions.runWith({ ...PROVIDER_CALL, secrets: AFRICA_TALKING_SECRETS }).firestore
  .document("sosIncidents/{incidentId}")
  .onCreate(async (snapshot) => {
    const data = (snapshot.data() || {}) as Record<string, unknown>;
    if (!planSosDispatch(data).shouldDispatch) return;

    const ownerId = String(data.postedByUid);
    const profile = await db.collection("users").doc(ownerId).get();
    const recipients = selectSosRecipients(
      process.env.SOS_DISPATCH_NUMBERS,
      profile.get("emergencyContactPhone")
    );
    const message = buildSosMessage(data);
    let delivered = 0;
    for (const phone of recipients) {
      if (await sendSmsAlert(phone, message)) delivered += 1;
    }

    await snapshot.ref.set(
      {
        dispatch: {
          attemptedAt: Date.now(),
          status: recipients.length === 0 ? "NO_RECIPIENTS" : delivered > 0 ? "SENT" : "FAILED",
          recipients: recipients.length,
          delivered,
        },
      },
      { merge: true }
    );

    try {
      await admin.messaging().send({
        topic: "sos_dispatch",
        notification: { title: "SOS raised", body: message },
        data: { incidentId: snapshot.id, jobId: String(data.jobId || "") },
      });
    } catch (error) {
      console.error("SOS push failed", scrubForLog((error as Error).message));
    }
  });

/**
 * ADMIN-001: Helper to verify caller has admin custom claim.
 *
 * ADMIN-005: the one implementation of this decision now lives in
 * `guarantorReview.checkAdminCaller` (same trust anchor — the `admin` claim of the
 * *verified* ID token, never a client-supplied role), so every admin endpoint, old and
 * new, authorizes through a single code path.
 */
function isAdmin(context: functions.https.CallableContext): boolean {
  return checkAdminCaller(context).ok;
}

/**
 * ADMIN-005: authorize a callable as an administrator and return that administrator's
 * uid, which is what a review gets attributed to. Unauthenticated callers are rejected as
 * such; an authenticated caller without the claim gets `permission-denied`.
 */
function authorizeAdmin(context: functions.https.CallableContext): string {
  const caller = checkAdminCaller(context);
  if (!caller.ok) {
    throw new functions.https.HttpsError(caller.code, caller.message);
  }
  return caller.value;
}


/**
 * ADMIN-002: Get Pending Verification Queue
 * Returns worker applications with status SUBMITTED or UNDER_REVIEW, sorted by submittedAt (48h SLA).
 *
 * ADMIN-005 (REG-002 gap G2) changes, deliberately small:
 *  - the worker's National ID number is no longer returned. It is returned *masked* as
 *    `nationalIdNumberMasked`; an administrator who needs the real number opens the
 *    protected `getGuarantorSubmission` / KYC detail instead of carrying it in a list
 *    that is polled, cached and screenshotted.
 *  - every row now also carries the guarantor review state (`guarantorStatus`,
 *    `guarantorSubmittedAt`, `guarantorCount`, `ninVerificationStatus`,
 *    `guarantorsAwaitingReview`) and `kycOnFile`, so a worker whose guarantor submission
 *    is waiting is visible to an administrator even though the app does not mirror
 *    `worker_kyc` into Firestore yet (blocker recorded in AGENT_STATE.md).
 *  - only Storage *paths* are ever emitted, never URLs.
 */
export const getPendingVerificationQueue = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!isAdmin(context)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Admin access required."
    );
  }

  const requested = typeof data?.limit === "number" && data.limit > 0 ? data.limit : 50;
  const limit = Math.min(Math.floor(requested), 100);
  const now = Date.now();

  const kycSnapshot = await db
    .collection("worker_kyc")
    .where("status", "in", ["SUBMITTED", "UNDER_REVIEW"])
    .orderBy("submittedAt", "asc")
    .limit(limit)
    .get();

  // Guarantor work is queried in its own right, for the reason documented above.
  // No `orderBy` here on purpose: `status in [...]` + `orderBy` would silently require a
  // composite index that this repository does not declare (`firestore.indexes.json` is not
  // part of the project), and the call would fail with FAILED_PRECONDITION on a fresh
  // project. `buildQueueRows` sorts by the SLA clock and applies the limit anyway, so the
  // only thing the missing server-side sort costs is a slightly larger read.
  const submissionSnapshot = await db
    .collection("guarantor_submissions")
    .where("status", "in", GUARANTOR_REVIEWABLE_STATUSES)
    .limit(limit)
    .get();

  const kycDocs = kycSnapshot.docs.map(
    (doc): { id: string; body: DocumentBody } => ({ id: doc.id, body: (doc.data() || {}) as DocumentBody })
  );
  const submissionDocs = submissionSnapshot.docs.map(
    (doc): { id: string; body: DocumentBody } => ({ id: doc.id, body: (doc.data() || {}) as DocumentBody })
  );

  // A guarantor-only row still needs the worker's name: read just those user documents,
  // in one batch, and take only the display name.
  const kycIds = kycDocs.map((entry) => entry.id);
  const missingNames = submissionDocs.map((entry) => entry.id).filter((id) => !kycIds.includes(id));
  const namesByWorkerId: Record<string, string> = Object.create(null);
  if (missingNames.length > 0) {
    const userSnapshots = await db.getAll(
      ...missingNames.map((id) => db.collection("users").doc(id))
    );
    missingNames.forEach((id, index) => {
      const snapshot = userSnapshots[index];
      if (!snapshot || !snapshot.exists) return;
      const fullName = snapshot.data()?.["fullName"];
      if (typeof fullName === "string") namesByWorkerId[id] = fullName;
    });
  }

  const applications = buildQueueRows({ kycDocs, submissionDocs, namesByWorkerId, now, limit });

  // REM-039: surface collusion signals for the workers in this queue. The records are read with
  // the Admin SDK (guarantors are not client-readable) and reduced in memory; no NIN or phone
  // number is copied into the response or into a new collection.
  const workerIds = new Set(applications.map((row) => row.userId));
  const riskSnapshot = await db.collection("guarantors").limit(500).get();
  const riskRecords = riskSnapshot.docs
    .map((doc) => ({
      workerId: typeof doc.get("workerKycId") === "string" ? String(doc.get("workerKycId")) : "",
      nationalIdNumber: doc.get("nationalIdNumber"),
      phoneNumber: doc.get("phoneNumber"),
    }))
    .filter((record) => workerIds.has(record.workerId));
  const riskByWorker = assessGuarantorRisk(riskRecords);

  return {
    count: applications.length,
    guarantorsAwaitingReview: applications.filter((row) => row.guarantorsAwaitingReview).length,
    applications,
    riskFlags: riskByWorker
  };
});


/**
 * ADMIN-003: Review Worker Application
 * Accepts decision ('APPROVE' | 'REJECT' | 'CHANGES_REQUIRED') and feedback reason.
 */
export const reviewWorkerApplication = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!isAdmin(context)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Admin access required."
    );
  }

  const { userId, decision, feedback } = data;
  if (!userId || !decision) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameters: userId, decision."
    );
  }

  const validDecisions = ["APPROVE", "REJECT", "CHANGES_REQUIRED"];
  if (!validDecisions.includes(decision)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      `Invalid decision. Must be one of: ${validDecisions.join(", ")}`
    );
  }

  const statusMap: Record<string, string> = {
    APPROVE: "APPROVED",
    REJECT: "REJECTED",
    CHANGES_REQUIRED: "CHANGES_REQUIRED"
  };

  const newStatus = statusMap[decision];
  const now = admin.firestore.FieldValue.serverTimestamp();

  await db.collection("worker_kyc").doc(userId).update({
    status: newStatus,
    reviewFeedback: feedback || "",
    reviewedAt: now
  });

  await db.collection("notifications").add({
    userId,
    type: "VERIFICATION_DECISION",
    message: decision === "APPROVE"
      ? "Your worker application has been approved. Please complete training to activate your account."
      : decision === "REJECT"
        ? `Your worker application was not approved. Reason: ${feedback || "No reason provided."}`
        : `Changes required for your application. Reason: ${feedback || "Please review feedback."}`,
    createdAt: now,
    read: false
  });
  await sendUserPush(
    userId,
    "Verification decision",
    decision === "APPROVE" ? "Your worker application was approved." : "Your application needs attention.",
    { type: "VERIFICATION_DECISION" }
  );

  return { success: true, userId, newStatus };
});

/**
 * ADMIN-004: Set Worker Activation Status
 * Activate or suspend worker bidding privileges.
 */
export const setWorkerActivationStatus = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!isAdmin(context)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Admin access required."
    );
  }

  const { userId, activate } = data;
  if (!userId || typeof activate !== "boolean") {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameters: userId, activate (boolean)."
    );
  }

  // TRAIN-003: Enforce activation gate - KYC approved + training completed required
  if (activate) {
    const kycDoc = await db.collection("worker_kyc").doc(userId).get();
    const kycData = kycDoc.data();
    const kycStatus = kycData?.status || "NOT_STARTED";

    if (kycStatus !== "APPROVED") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Worker KYC verification must be approved before activation."
      );
    }

    const trainingDoc = await db.collection("worker_training").doc(userId).get();
    const trainingData = trainingDoc.data();
    const trainingStatus = trainingData?.status || "NOT_STARTED";

    if (trainingStatus !== "COMPLETED") {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Worker must complete mandatory training before activation."
      );
    }
  }

  await db.collection("providers").doc(userId).update({
    isActivated: activate,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  await db.collection("notifications").add({
    userId,
    type: activate ? "ACTIVATED" : "SUSPENDED",
    message: activate
      ? "Your worker account has been activated. You can now receive job bids and appear in nearby searches."
      : "Your worker account has been suspended. Bidding access has been revoked.",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
    read: false
  });

  return { success: true, userId, isActivated: activate };
});

// ============ ADMIN-005 (REG-002 recovery, gap G2): Admin Guarantor Review ============

/**
 * Resolves the bucket that holds the private verification photographs.
 *
 * On Cloud Functions the project's own default bucket is already known through
 * `FIREBASE_CONFIG`, so nothing has to be configured; `GS_STORAGE_BUCKET` exists only for
 * a non-default bucket. Returns null when the Storage client cannot name a bucket (for
 * example when the functions shell runs without project configuration): the review
 * response then still carries every field, with the photo links reported unavailable
 * rather than the call failing.
 */
function privatePhotoBucket() {
  try {
    const storage = admin.storage();
    return STORAGE_BUCKET_NAME.length > 0 ? storage.bucket(STORAGE_BUCKET_NAME) : storage.bucket();
  } catch (error) {
    return null;
  }
}

/**
 * Mints one short-lived (v4, default 15 min) read URL per **already validated** private
 * photo path, so an administrator can open the five verification photographs without the
 * Storage prefix ever becoming publicly readable and without a long-lived token ever
 * being written into a Firestore document.
 *
 * Fail-closed and identifier-free: any failure is reduced to a fixed reason code, because
 * a raw client error can echo bucket paths or a half-built signature into a response.
 */
async function mintPrivatePhotoUrls(
  paths: string[],
  ttlSeconds: number
): Promise<Record<string, MintedUrl>> {
  const urlsByPath: Record<string, MintedUrl> = Object.create(null);
  if (paths.length === 0) return urlsByPath;

  const bucket = privatePhotoBucket();
  if (!bucket) {
    for (const path of paths) urlsByPath[path] = { url: null, error: "BUCKET_UNAVAILABLE" };
    return urlsByPath;
  }

  const expiresAt = new Date(Date.now() + ttlSeconds * 1000);
  for (const path of paths) {
    try {
      const [url] = await bucket
        .file(path)
        .getSignedUrl({ action: "read", expires: expiresAt, version: "v4" });
      urlsByPath[path] =
        typeof url === "string" && url.length > 0 ? { url } : { url: null, error: "URL_MINT_FAILED" };
    } catch (error) {
      urlsByPath[path] = { url: null, error: "URL_MINT_FAILED" };
    }
  }
  return urlsByPath;
}

/**
 * ADMIN-005: read one worker's guarantor submission the way an administrator has to read
 * it — the two guarantors, each guarantor's photograph and worker+guarantor photograph,
 * the group photograph, the consent record, the NIN state and the current review state.
 *
 * Authorization is server-side and absolute: an unauthenticated caller gets
 * `unauthenticated`, a signed-in worker or client without the `admin` claim gets
 * `permission-denied`. Nothing here is reachable with a client-chosen role.
 *
 * Data handling: NINs are masked unless the caller explicitly passes `includeFullNin`
 * (the reviewer's detail screen is the only legitimate use), and a photo URL is attached
 * only to an object inside `users/{workerId}/guarantors/**` — never to another worker's
 * prefix, never to an absolute URL, and only for `PHOTO_URL_TTL_SECONDS`.
 */
export const getGuarantorSubmission = functions.runWith(callable()).https.onCall(async (data, context) => {
  authorizeAdmin(context);

  const workerIdResult = readWorkerId(data);
  if (!workerIdResult.ok) {
    throw new functions.https.HttpsError(workerIdResult.code, workerIdResult.message);
  }
  const workerId = workerIdResult.value;

  const submissionDoc = await db.collection("guarantor_submissions").doc(workerId).get();
  if (!submissionDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "No guarantor submission is on file for this worker."
    );
  }
  const submission = (submissionDoc.data() || {}) as DocumentBody;

  // The guarantor records are located by owner id, never by trusting the id list stored
  // inside the submission document, so a record belonging to a different worker can never
  // be pulled into this review.
  const guarantorSnapshot = await db
    .collection("guarantors")
    .where("workerKycId", "==", workerId)
    .get();
  const guarantorDocs = guarantorSnapshot.docs
    .map((doc): { id: string; body: DocumentBody } => ({
      id: doc.id,
      body: (doc.data() || {}) as DocumentBody
    }))
    .filter((entry) => entry.body["workerKycId"] === workerId);

  const kycDoc = await db.collection("worker_kyc").doc(workerId).get();
  const rawKycStatus = kycDoc.exists ? kycDoc.data()?.["status"] : "NOT_STARTED";

  const urlsByPath = await mintPrivatePhotoUrls(
    collectPrivatePhotoPaths({ workerId, submission, guarantorDocs }),
    PHOTO_URL_TTL_SECONDS
  );

  return projectSubmissionForAdmin({
    workerId,
    submission,
    guarantorDocs,
    workerKycStatus:
      typeof rawKycStatus === "string" && rawKycStatus.length > 0 ? rawKycStatus : "NOT_STARTED",
    includeFullNin: data?.includeFullNin === true,
    urlsByPath,
    now: Date.now(),
    ttlSeconds: PHOTO_URL_TTL_SECONDS
  });
});

/**
 * ADMIN-005: record the administrator's decision on a guarantor submission.
 *
 * Reuses the vocabulary that already exists end to end — decision verbs `APPROVE | REJECT
 * | CHANGES_REQUIRED` (same as `reviewWorkerApplication`) mapping onto the statuses the
 * worker's own app already understands (`APPROVED | REJECTED | CHANGES_REQUIRED`, see
 * `SkillsExchangeRepository.remoteGuarantorStatus`), and NIN results
 * `PENDING | VERIFIED | FAILED`.
 *
 * The reviewer's identity comes from the verified ID token and the review timestamp from
 * the Firestore server clock, so neither can be forged by the caller. Only admin-owned
 * fields are written, and `firestore.rules` already forbids the worker from touching them.
 */
export const reviewGuarantorSubmission = functions.runWith(callable()).https.onCall(async (data, context) => {
  const adminUid = authorizeAdmin(context);

  const validated = validateGuarantorReviewInput(data, adminUid);
  if (!validated.ok) {
    throw new functions.https.HttpsError(validated.code, validated.message);
  }
  const command = validated.value;

  const submissionRef = db.collection("guarantor_submissions").doc(command.workerId);
  const submissionDoc = await submissionRef.get();
  if (!submissionDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "No guarantor submission is on file for this worker."
    );
  }

  // 1. The decision: status + feedback + NIN result + who reviewed + when.
  const serverNow = admin.firestore.FieldValue.serverTimestamp();
  await submissionRef.update(buildGuarantorReviewFields(command, serverNow));

  // 2. The per-guarantor NIN outcome (`ninVerificationStatus` / `verifiedAt` /
  //    `verifiedBy` are exactly the admin-reserved keys in firestore.rules), again only
  //    for documents verifiably owned by the reviewed worker.
  const guarantorSnapshot = await db
    .collection("guarantors")
    .where("workerKycId", "==", command.workerId)
    .get();
  const ninUpdate = buildGuarantorNinUpdate(command, serverNow);
  const batch = db.batch();
  let guarantorsUpdated = 0;
  guarantorSnapshot.docs.forEach((doc) => {
    if (doc.data()?.["workerKycId"] !== command.workerId) return;
    batch.update(doc.ref, ninUpdate);
    guarantorsUpdated += 1;
  });
  if (guarantorsUpdated > 0) await batch.commit();

  // R09-P2-05: a verified NIN leaves a salted fingerprint and a masked value behind, never a second
  // plaintext copy in an index. The fingerprint is what lets a reviewer see that the same ID was used
  // for two different guarantors; without `PII_SALT` no fingerprint is written at all, because an
  // unsalted hash of a NIN is brute-forceable.
  if (command.ninStatus === "VERIFIED") {
    const salt = (process.env.PII_SALT || "").trim();
    const fingerprintBatch = db.batch();
    let fingerprinted = 0;
    for (const doc of guarantorSnapshot.docs) {
      const data = doc.data() || {};
      if (data["workerKycId"] !== command.workerId) continue;
      const rawNin = String(data["nationalIdNumber"] || "");
      if (!rawNin) continue;
      const patch: Record<string, unknown> = { ninMasked: maskNin(rawNin) };
      const fingerprint = ninFingerprint(rawNin, salt);
      if (fingerprint) {
        patch["ninFingerprintHash"] = fingerprint;
        try {
          const clashes = await db
            .collection("guarantors")
            .where("ninFingerprintHash", "==", fingerprint)
            .limit(5)
            .get();
          const others = clashes.docs
            .map((other) => String((other.data() || {})["workerKycId"] || ""))
            .filter((workerKycId) => workerKycId && workerKycId !== command.workerId);
          if (others.length > 0) {
            patch["ninDuplicateOfWorker"] = others[0];
            console.warn("NIN reuse detected across guarantor submissions", {
              workerId: command.workerId,
              occurrences: others.length,
            });
          }
        } catch {
          // The clash query is advisory; a missing index must not fail the review.
        }
      }
      fingerprintBatch.set(doc.ref, patch, { merge: true });
      fingerprinted += 1;
    }
    if (fingerprinted > 0) await fingerprintBatch.commit();
  }

  // 3. Keep the KYC row's guarantor mirror honest — but only when that row exists. The app
  //    never creates `worker_kyc` in Firestore today (documented blocker); creating it from
  //    here would hide that gap instead of fixing it.
  const kycRef = db.collection("worker_kyc").doc(command.workerId);
  if ((await kycRef.get()).exists) {
    await kycRef.update({
      guarantorStatus: command.status,
      guarantorReviewFeedback: command.feedback
    });
  }

  // 4. Tell the worker, using the notification shape the app already renders.
  await db.collection("notifications").add({
    userId: command.workerId,
    type: "VERIFICATION_DECISION",
    message:
      command.status === "APPROVED"
        ? "Your guarantors have been verified. Thank you — your worker verification is moving forward."
        : command.status === "REJECTED"
          ? `Your guarantor verification was not approved. Reason: ${command.feedback || "No reason provided."}`
          : `Changes required for your guarantor verification. Reason: ${command.feedback || "Please review the feedback."}`,
    createdAt: serverNow,
    read: false
  });

  return {
    success: true,
    workerId: command.workerId,
    status: command.status,
    ninVerificationStatus: command.ninStatus,
    guarantorsUpdated
  };
});

// ==================== DISP-003: Admin Dispute Resolution ====================




/**
 * DISP-003: Resolve Dispute
 * Admin-only function to resolve disputes with optional refund.
 */
export const resolveDispute = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!isAdmin(context)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Admin access required to resolve disputes."
    );
  }

  const { disputeId, resolution, resolutionType, resolutionAmount, adminNotes } = data;
  if (!disputeId || !resolution) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameters: disputeId, resolution."
    );
  }

  const validResolutions = ["RESOLVED", "REJECTED", "ESCALATED"];
  if (!validResolutions.includes(resolution)) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      `Invalid resolution. Must be one of: ${validResolutions.join(", ")}`
    );
  }

  const disputeDoc = await db.collection("disputes").doc(disputeId).get();
  if (!disputeDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Dispute not found."
    );
  }

  const disputeData = disputeDoc.data();
  const now = admin.firestore.FieldValue.serverTimestamp();

  // Update dispute status
  await db.collection("disputes").doc(disputeId).update({
    status: resolution,
    resolutionType: resolutionType || "",
    resolutionAmount: resolutionAmount || 0,
    adminNotes: adminNotes || "",
    resolvedAt: now,
    resolvedBy: context.auth?.uid || "admin",
    updatedAt: now
  });

  // If refund approved, process it
  if (resolutionType === "REFUND_FULL" || resolutionType === "REFUND_PARTIAL") {
    const refundAmount = resolutionAmount || 0;
    if (refundAmount > 0) {
      // REM-027: ask the provider for the refund. Nothing is marked REFUNDED unless the provider
      // accepted the request; a failure is recorded on the dispute for the admin to retry.
      try {
        const refund = await performEscrowRefund(
          String(disputeData?.jobId || ""),
          refundAmount,
          `Dispute refund: ${resolutionType}`,
          context.auth?.uid || "admin"
        );
        await disputeDoc.ref.update({ refundStatus: "REQUESTED", refundId: refund.refundRef });
      } catch (e: any) {
        await disputeDoc.ref.update({
          refundStatus: "FAILED",
          refundError: String(e?.message || e),
        });
        throw new functions.https.HttpsError(
          "internal",
          `Dispute recorded, but the refund failed: ${e?.message || e}`
        );
      }
    }
  }

  // If worker suspended
  if (resolutionType === "WORKER_SUSPENDED") {
    await db.collection("providers").doc(disputeData?.reportedUserId).update({
      isActivated: false,
      updatedAt: now
    });
  }

  // Notify parties
  await db.collection("notifications").add({
    userId: disputeData?.reportedBy,
    type: "DISPUTE_RESOLVED",
    jobId: disputeData?.jobId,
    message: `Your dispute has been ${resolution.toLowerCase()}. ${adminNotes || ""}`,
    createdAt: now,
    read: false
  });

  await sendUserPush(
    disputeData?.reportedBy,
    "Dispute resolved",
    `Your dispute has been ${resolution.toLowerCase()}.`,
    { type: "DISPUTE_RESOLVED", jobId: String(disputeData?.jobId || "") }
  );

  if (disputeData?.reportedUserId) {
    await db.collection("notifications").add({
      userId: disputeData.reportedUserId,
      type: "DISPUTE_RESOLVED",
      jobId: disputeData?.jobId,
      message: `A dispute against you has been ${resolution.toLowerCase()}. ${adminNotes || ""}`,
      createdAt: now,
      read: false
    });
    await sendUserPush(
      disputeData.reportedUserId,
      "Dispute resolved",
      `A dispute against you has been ${resolution.toLowerCase()}.`,
      { type: "DISPUTE_RESOLVED", jobId: String(disputeData?.jobId || "") }
    );
  }

  return {
    success: true,
    disputeId,
    resolution,
    resolutionType: resolutionType || "",
    resolutionAmount: resolutionAmount || 0
  };
});

/**
 * DISP-003: Get Disputes Queue
 * Admin-only function to retrieve disputes for review.
 */
export const getDisputesQueue = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!isAdmin(context)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Admin access required."
    );
  }

  const { status } = data;
  let query = db.collection("disputes").orderBy("createdAt", "desc");

  if (status) {
    query = query.where("status", "==", status);
  }

  const snapshot = await query.limit(50).get();
  const disputes = snapshot.docs.map(doc => ({
    id: doc.id,
    ...doc.data()
  }));

  return { success: true, disputes };
});

// =====================================================================================
// R10: trust & safety — guarantor intake, reports, ratings, SOS dispatch truth, metrics.
//
// Every one of these replaces a client-side decision the review found to be unenforceable:
//   * guarantors were written straight from the device, so the same person could back many accounts;
//   * a report lived only in the reporter's own Room table — no collection, no queue, no rule;
//   * a rating lived only in Room, so a tampered client invented its own stars;
//   * the SOS dispatch outcome was written server-side and never read back, while the app claimed
//     contacts had been messaged.
// =====================================================================================

/** Salt for NIN fingerprints. Without it no fingerprint is written, and intake refuses to proceed. */
function piiSalt(): string {
  return (process.env.PII_SALT || "").trim();
}

/**
 * R10-P2-04: file a guarantor submission from the server.
 *
 * The client uploads photographs to its own private Storage prefix; the *records* are written here, so
 * uniqueness is enforced by the only actor that can see every submission. Refusals are specific
 * enough to act on and never echo an identity number.
 */
export const submitGuarantorVerification = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to file your guarantors.");
  }
  const workerUid = context.auth.uid;
  const salt = piiSalt();
  const rawGuarantors: unknown = data?.guarantors;
  const receivedAt = Date.now();

  if (!Array.isArray(rawGuarantors)) {
    throw new functions.https.HttpsError("invalid-argument", "A guarantor submission needs two guarantors.");
  }

  // 1. The third-party consent receipt must exist before any identity data is written. The client
  //    cannot assert consent: it must have recorded it through `recordGuarantorConsent` first.
  const consentVersion = typeof data?.consentVersion === "string" ? data.consentVersion.trim() : "";
  if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(consentVersion)) {
    throw new functions.https.HttpsError("failed-precondition", "Record your guarantors' consent first.");
  }
  const receipt = await db
    .collection("users")
    .doc(workerUid)
    .collection("guarantor_consents")
    .doc(consentVersion)
    .get();
  if (!receipt.exists) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "No recorded consent for that policy version. Ask each guarantor again, then retry."
    );
  }

  // 2. Shape and normalisation. Nothing the client sends is trusted as an identity; the fingerprint is
  //    computed here from the NIN the worker typed.
  const candidates: GuarantorCandidate[] = [];
  const records: Array<Record<string, unknown>> = [];
  for (let index = 0; index < rawGuarantors.length; index += 1) {
    const raw = (rawGuarantors[index] ?? {}) as Record<string, unknown>;
    const nin = typeof raw.nationalIdNumber === "string" ? raw.nationalIdNumber.trim().toUpperCase() : "";
    const phone = typeof raw.phoneNumber === "string" ? raw.phoneNumber.trim() : "";
    const fullName = typeof raw.fullName === "string" ? raw.fullName.trim() : "";
    const consentProvided = raw.consentProvided === true;
    const guarantorPhotoStoragePath = String(raw.guarantorPhotoStoragePath ?? "");
    const workerWithGuarantorPhotoStoragePath = String(raw.workerWithGuarantorPhotoStoragePath ?? "");
    const id = typeof raw.id === "string" && raw.id.trim() ? raw.id.trim() : `guarantor_${workerUid}_${index}`;

    const ownsBothPhotos =
      guarantorPhotoStoragePath.startsWith(`users/${workerUid}/`) &&
      workerWithGuarantorPhotoStoragePath.startsWith(`users/${workerUid}/`);

    candidates.push({
      index,
      ninFingerprint: ninFingerprint(nin, salt),
      phoneKey: phoneKey(phone),
      normalisedName: normaliseName(fullName),
      district: typeof raw.district === "string" ? raw.district.trim() : "",
      consentProvided,
      hasBothPhotos: ownsBothPhotos && guarantorPhotoStoragePath !== "" && workerWithGuarantorPhotoStoragePath !== "",
    });

    records.push({
      id,
      fullName,
      nationalIdNumber: nin,
      phoneNumber: phone,
      relationship: typeof raw.relationship === "string" ? raw.relationship.trim() : "",
      district: typeof raw.district === "string" ? raw.district.trim() : "",
      guarantorPhotoStoragePath,
      workerWithGuarantorPhotoStoragePath,
      consentProvided,
      consentVersion,
    });
  }

  // 3. Evidence the decision needs: the worker's own identifiers, everybody already on file (bounded),
  //    and how often this worker has filed recently.
  const workerDoc = await db.collection("users").doc(workerUid).get();
  const workerNin = String(workerDoc.get("nationalIdNumber") || "");
  const workerPhone = String(workerDoc.get("phoneNumber") || "");

  const [existingSnapshot, recentSubmissions] = await Promise.all([
    db.collection("guarantors").limit(500).get(),
    db
      .collection("guarantors")
      .where("workerKycId", "==", workerUid)
      .where("submittedAt", ">", receivedAt - SUBMISSION_WINDOW_MS)
      .limit(20)
      .get()
      .catch(() => null),
  ]);

  const existing: ExistingGuarantor[] = existingSnapshot.docs.map((doc) => ({
    workerUid: String(doc.get("workerKycId") || ""),
    ninFingerprint: String(doc.get("ninFingerprintHash") || ""),
    phoneKey: String(doc.get("phoneKey") || ""),
  }));

  // The stored NIN is plaintext in the guarantor document today, so a fingerprint can be derived for
  // records written before this round — otherwise older duplicates stay invisible.
  for (let index = 0; index < existingSnapshot.docs.length; index += 1) {
    const doc = existingSnapshot.docs[index];
    if (existing[index].ninFingerprint) continue;
    const storedNin = String(doc.get("nationalIdNumber") || "");
    existing[index].ninFingerprint = ninFingerprint(storedNin, salt);
    if (!existing[index].phoneKey) existing[index].phoneKey = phoneKey(String(doc.get("phoneNumber") || ""));
  }

  const submissionsInWindow = recentSubmissions ? new Set(recentSubmissions.docs.map((doc) => doc.id)).size : 0;

  const decision = reviewGuarantorIntake({
    workerUid,
    workerNinFingerprint: ninFingerprint(workerNin, salt),
    workerPhoneKey: phoneKey(workerPhone),
    candidates,
    existing,
    submissionsInWindow,
  });

  await db.collection("guarantor_intake_audit").add({
    workerUid,
    at: receivedAt,
    accepted: decision.ok,
    rejectionCodes: describeRejections(decision.rejections),
    flags: decision.flags,
    guarantorCount: candidates.length,
  }).catch(() => undefined);

  if (!decision.ok) {
    const first = decision.rejections[0];
    throw new functions.https.HttpsError("failed-precondition", first.message, {
      code: first.code,
      index: first.index ?? null,
      rejections: decision.rejections,
    });
  }

  // 4. Write. The consent version is stored, the reviewer's fields are initialised, and no status is
  //    ever taken from the client.
  const batch = db.batch();
  records.forEach((record, index) => {
    const ref = db.collection("guarantors").doc(String(record.id));
    batch.set(ref, {
      workerKycId: workerUid,
      fullName: record.fullName,
      nationalIdNumber: record.nationalIdNumber,
      phoneNumber: record.phoneNumber,
      relationship: record.relationship,
      district: record.district,
      guarantorPhotoStoragePath: record.guarantorPhotoStoragePath,
      workerWithGuarantorPhotoStoragePath: record.workerWithGuarantorPhotoStoragePath,
      consentProvided: true,
      consentVersion,
      consentRecordedAt: Number(receipt.get("recordedAt") || receivedAt),
      ninFingerprintHash: candidates[index].ninFingerprint,
      phoneKey: candidates[index].phoneKey,
      ninVerificationStatus: "PENDING",
      submittedAt: receivedAt,
      updatedAt: receivedAt,
    });
  });
  batch.set(db.collection("guarantor_submissions").doc(workerUid), {
    workerId: workerUid,
    guarantorIds: records.map((record) => String(record.id)),
    guarantorCount: records.length,
    groupPhotoStoragePath: String(data?.groupPhotoStoragePath ?? ""),
    consentConfirmed: true,
    consentVersion,
    consentRecordedAt: Number(receipt.get("recordedAt") || receivedAt),
    submittedAt: receivedAt,
    status: "PENDING_REVIEW",
    flags: decision.flags,
  });
  await batch.commit();

  return { status: "PENDING_REVIEW", flags: decision.flags, submittedAt: receivedAt };
});

/**
 * R10-P2-02: file a report that an operator can actually see.
 *
 * Authentication comes from the token, the category and size are validated server-side, the priority
 * and the first-response deadline are computed here, and the record lands in a collection with rules
 * and an admin queue. The reporter keeps a local copy for display; the server copy is the one an
 * administrator triages.
 */
export const fileReport = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to report an account.");
  }
  const reporterUid = context.auth.uid;
  const subjectUid = typeof data?.subjectUid === "string" ? data.subjectUid.trim() : "";

  const priorSnapshot = await db
    .collection("reports")
    .where("subjectUid", "==", subjectUid)
    .where("status", "in", [REPORT_STATUS.open, REPORT_STATUS.triaging])
    .limit(10)
    .get()
    .catch(() => null);

  let record: ReportRecord;
  try {
    record = buildReportRecord(
      {
        subjectUid,
        subjectName: data?.subjectName,
        jobId: data?.jobId,
        category: data?.category,
        details: data?.details,
      },
      {
        id: `report_${reporterUid}_${Date.now()}`,
        reporterUid,
        now: Date.now(),
        priorOpenReportsAgainstSubject: priorSnapshot ? priorSnapshot.size : 0,
      }
    );
  } catch (error) {
    throw new functions.https.HttpsError("invalid-argument", (error as Error).message);
  }

  await db.collection("reports").doc(record.id).set(record);

  // A SAFETY report is also a safety event: the dispatch desk is paged on the same channel SOS uses.
  if (record.priority === "P0") {
    await db.collection("notifications").add({
      userId: "safety_desk",
      type: "SAFETY_REPORT",
      message: `A ${record.category} report needs review within ${Math.round((record.slaDueAt - record.createdAt) / 60000)} minutes.`,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      read: false,
    }).catch(() => undefined);
  }

  return {
    id: record.id,
    status: record.status,
    priority: record.priority,
    slaDueAt: record.slaDueAt,
    message: "Report received. An operator will review it against the response target for this category.",
  };
});

/** R10-P2-02: the operator queue, with the SLA deadline on every row. */
export const getReportsQueue = functions.runWith(callable()).https.onCall(async (data, context) => {
  const adminUid = authorizeAdmin(context);
  const status = typeof data?.status === "string" ? data.status : "";
  let query = db.collection("reports").orderBy("createdAt", "desc");
  if (status) query = query.where("status", "==", status);
  const snapshot = await query.limit(50).get();
  const now = Date.now();
  const reports = snapshot.docs.map((doc) => {
    const record = doc.data() as Record<string, unknown>;
    const slaDueAt = Number(record.slaDueAt || 0);
    return {
      id: doc.id,
      ...record,
      // Details are the reporter's words about a third party: an operator sees them, a log never does.
      breachingSla: slaDueAt > 0 && now > slaDueAt && String(record.status) === REPORT_STATUS.open,
    };
  });
  return { reports, operatorUid: adminUid, generatedAt: now };
});

/** R10-P2-02: close or progress a report — attributed, forward-only, with a readable outcome. */
export const resolveReport = functions.runWith(callable()).https.onCall(async (data, context) => {
  const adminUid = authorizeAdmin(context);
  const reportId = typeof data?.reportId === "string" ? data.reportId.trim() : "";
  const status = String(data?.status ?? "").toUpperCase() as ReportStatus;
  if (!reportId) {
    throw new functions.https.HttpsError("invalid-argument", "A report id is required.");
  }

  const ref = db.collection("reports").doc(reportId);
  const snapshot = await ref.get();
  if (!snapshot.exists) {
    throw new functions.https.HttpsError("not-found", "That report does not exist.");
  }

  let updated: ReportRecord;
  try {
    updated = decideTriage({
      report: snapshot.data() as ReportRecord,
      status,
      outcome: typeof data?.outcome === "string" ? data.outcome : "",
      notes: typeof data?.notes === "string" ? data.notes : "",
      adminUid,
      now: Date.now(),
    });
  } catch (error) {
    throw new functions.https.HttpsError("failed-precondition", (error as Error).message);
  }

  await ref.set(updated);
  console.info("report triaged", {
    reportId,
    status: updated.status,
    priority: updated.priority,
    operator: adminUid,
  });
  return {
    id: reportId,
    status: updated.status,
    triagedAt: updated.triagedAt,
    message: triageMessage(updated),
  };
});

/**
 * R10-P2-06: a rating the server owns.
 *
 * The job, the participants, the completion state and the one-per-direction rule all come from the
 * server's copy of the job; the reputation aggregate is derived from the recorded ratings, so no
 * client can write a number that was never earned.
 */
export const submitRating = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to rate.");
  }
  const raterUid = context.auth.uid;
  const jobId = typeof data?.jobId === "string" ? data.jobId.trim() : "";
  const subjectUid = typeof data?.subjectUid === "string" ? data.subjectUid.trim() : "";
  if (!jobId || !subjectUid) {
    throw new functions.https.HttpsError("invalid-argument", "A rating needs a job and the account it is about.");
  }

  const jobDoc = await db.collection("jobs").doc(jobId).get();
  const job = jobDoc.exists
    ? {
        jobId,
        status: String(jobDoc.get("status") || ""),
        postedByUid: jobDoc.get("postedByUid") as string | undefined,
        assignedProviderUid: jobDoc.get("assignedProviderUid") as string | undefined,
      }
    : null;

  let ratingId = "";
  try {
    ratingId = ratingDocumentId(jobId, raterUid);
  } catch (error) {
    throw new functions.https.HttpsError("invalid-argument", (error as Error).message);
  }
  const existing = await db.collection("ratings").doc(ratingId).get();

  const decision = reviewRating(
    { raterUid, subjectUid, stars: data?.stars, review: data?.review },
    job,
    existing.exists ? ratingId : null
  );
  if (!decision.ok) {
    throw new functions.https.HttpsError(
      decision.code === "JOB_NOT_FOUND" ? "not-found" : "failed-precondition",
      decision.message || "That rating cannot be recorded.",
      { code: decision.code }
    );
  }

  const now = Date.now();
  const statsRef = db.collection("worker_stats").doc(subjectUid);
  let average = 0;
  await db.runTransaction(async (transaction) => {
    const stats = await transaction.get(statsRef);
    const aggregate = applyRating(
      stats.exists ? { count: stats.get("count"), sum: stats.get("sum") } : undefined,
      decision.stars,
      now
    );
    transaction.set(db.collection("ratings").doc(ratingId), {
      jobId,
      fromUid: raterUid,
      toUid: subjectUid,
      stars: decision.stars,
      review: decision.review,
      createdAt: now,
    });
    transaction.set(
      statsRef,
      { count: aggregate.count, sum: aggregate.sum, average: aggregate.average, updatedAt: now },
      { merge: true }
    );
    average = aggregate.average;
  });

  return { recorded: true, ratingId, average };
});

/**
 * R10-P2-03: what actually happened to an emergency alert.
 *
 * The dispatch trigger writes `sosIncidents/{id}.dispatch`; this returns the sentence the app shows,
 * composed on the server so the app cannot be more reassuring than the evidence. Owner, assigned
 * worker and admins may read it — nobody else.
 */
export const getSosDispatchStatus = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to check an alert.");
  }
  const incidentId = typeof data?.incidentId === "string" ? data.incidentId.trim() : "";
  if (!incidentId) {
    throw new functions.https.HttpsError("invalid-argument", "An incident id is required.");
  }
  const snapshot = await db.collection("sosIncidents").doc(incidentId).get();
  if (!snapshot.exists) {
    throw new functions.https.HttpsError("not-found", "That alert is not on the server yet.");
  }
  const uid = context.auth.uid;
  const isParty =
    snapshot.get("postedByUid") === uid ||
    snapshot.get("assignedProviderUid") === uid ||
    isAdmin(context);
  if (!isParty) {
    throw new functions.https.HttpsError("permission-denied", "This alert is not yours.");
  }

  const description = describeSosDispatch(snapshot.get("dispatch"), Date.now());
  return {
    ...description,
    chatMessage: dispatchChatMessage(description, new Date().toISOString().slice(11, 16)),
    incidentId,
  };
});

/** R10-P2-07: the trust & safety numbers, computed from the records rather than asserted. */
export const getSafetyMetrics = functions.runWith(callable()).https.onCall(async (data, context) => {
  authorizeAdmin(context);
  const windowDays = Number(data?.windowDays);
  const now = Date.now();
  const [incidents, reports] = await Promise.all([
    db.collection("sosIncidents").orderBy("raisedAt", "desc").limit(500).get(),
    db.collection("reports").orderBy("createdAt", "desc").limit(500).get(),
  ]);

  const metrics = safetyMetrics({
    now,
    windowDays: Number.isFinite(windowDays) && windowDays > 0 && windowDays <= 365 ? Math.floor(windowDays) : 30,
    incidents: incidents.docs.map((doc) => ({
      id: doc.id,
      status: String(doc.get("status") || ""),
      raisedAt: Number(doc.get("raisedAt") || 0),
      acknowledgedAt: Number(doc.get("acknowledgedAt") || 0) || undefined,
      dispatchStatus: String((doc.get("dispatch") || {}).status || ""),
    })),
    reports: reports.docs.map((doc) => ({
      id: doc.id,
      category: String(doc.get("category") || ""),
      priority: String(doc.get("priority") || ""),
      status: String(doc.get("status") || ""),
      createdAt: Number(doc.get("createdAt") || 0),
      slaDueAt: Number(doc.get("slaDueAt") || 0),
      triagedAt: Number(doc.get("triagedAt") || 0) || undefined,
    })),
  });

  // R10-P2-07: the console shows the server's own line, so a desk that cannot be read never renders
  // as a reassuring zero.
  console.log(metricsLine(metrics));
  return { ...metrics, summary: metricsLine(metrics) };
});

/**
 * AUDIT-A-SOS: Get SOS Queue
 * Admin-only function to retrieve SOS incidents for the dispatch desk.
 */
export const getSosQueue = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!isAdmin(context)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Admin access required."
    );
  }

  const { status } = data;
  let query = db.collection("sosIncidents").orderBy("raisedAt", "desc");

  if (status) {
    query = query.where("status", "==", status);
  }

  const snapshot = await query.limit(50).get();
  const incidents = snapshot.docs.map(doc => ({
    id: doc.id,
    ...doc.data()
  }));

  return { success: true, incidents };
});

/** AUDIT-A-SOS: acknowledge an ACTIVE incident as reviewed. Admin-only — caller must hold the
 * verified `admin` claim from a verified ID token (never a client-supplied role). */
export const acknowledgeSos = functions.runWith(callable()).https.onCall(async (data, context) => {
  const caller = checkAdminCaller(context);
  if (!caller.ok) {
    throw new functions.https.HttpsError(caller.code, caller.message);
  }

  const { sosIncidentId } = data ?? {};
  if (typeof sosIncidentId !== "string" || sosIncidentId.length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "sosIncidentId is required.");
  }

  const snap = await db.collection("sosIncidents").doc(sosIncidentId).get();
  if (!snap.exists) {
    throw new functions.https.HttpsError("not-found", "SOS incident not found.");
  }
  const doc = snap.data() as Record<string, unknown>;
  if (doc.status !== "ACTIVE") {
    throw new functions.https.HttpsError("failed-precondition", "Only ACTIVE incidents can be acknowledged.");
  }

  await db.collection("sosIncidents").doc(sosIncidentId).update({
    status: "ACKNOWLEDGED",
    acknowledgedAt: new Date(),
    acknowledgedBy: caller.value,
  });

  return { success: true, sosIncidentId };
});

/** AUDIT-A-SOS: resolve an ACTIVE incident as complete. Admin-only — caller must hold the
 * verified `admin` claim from a verified ID token (never a client-supplied role). */
export const resolveSosIncident = functions.runWith(callable()).https.onCall(async (data, context) => {
  const caller = checkAdminCaller(context);
  if (!caller.ok) {
    throw new functions.https.HttpsError(caller.code, caller.message);
  }

  const { sosIncidentId } = data ?? {};
  if (typeof sosIncidentId !== "string" || sosIncidentId.length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "sosIncidentId is required.");
  }

  const snap = await db.collection("sosIncidents").doc(sosIncidentId).get();
  if (!snap.exists) {
    throw new functions.https.HttpsError("not-found", "SOS incident not found.");
  }
  const doc = snap.data() as Record<string, unknown>;
  if (doc.status !== "ACTIVE") {
    throw new functions.https.HttpsError("failed-precondition", "Only ACTIVE incidents can be resolved.");
  }

  await db.collection("sosIncidents").doc(sosIncidentId).update({
    status: "CLEARED",
    clearedAt: new Date(),
    clearedBy: caller.value,
  });

  return { success: true, sosIncidentId };
});

// ==================== PAY-003: Escrow Release & Worker MoMo Payout ====================

/**
 * Build the real axios/Firestore wiring for the payout trust boundary and pass
 * it to the pure `initiatePayout` core in `momoDisbursement.ts`. Mirrors
 * `buildCollectionDeps`/`buildConfirmationDeps`: every security decision lives
 * in the pure core; this function only performs the actual provider HTTP calls
 * and Firestore reads/writes.
 */
/** Normalise a stored payout document into the shape the pure core expects. */
function toPayoutRecord(data: Record<string, unknown>): PayoutRecord {
  return {
    jobId: String(data.jobId || ""),
    workerId: String(data.workerId || ""),
    workerMomoNumber: String(data.workerMomoNumber || ""),
    amount: Number(data.amount || 0),
    currency: String(data.currency || PAYOUT_CURRENCY),
    provider: String(data.provider || "MTN_MOMO"),
    status: String(data.status || "") as PayoutStatus,
    payoutId: String(data.payoutId || ""),
    clientUid: String(data.clientUid || ""),
    releasedBy: String(data.releasedBy || ""),
    transactionId: String(data.transactionId || ""),
    providerReferenceId: String(data.providerReferenceId || ""),
    createdAt: data.createdAt ? Number(data.createdAt) : undefined,
    lastCheckedAt: data.lastCheckedAt ? Number(data.lastCheckedAt) : undefined,
    reason: data.reason ? String(data.reason) : undefined,
    withdrawalId: data.withdrawalId ? String(data.withdrawalId) : null,
    payoutKind: (data.payoutKind as PayoutRecord["payoutKind"]) || undefined,
    providerFinancialTransactionId: (data.providerFinancialTransactionId as string | null) ?? null,
    providerStatusCode: data.providerStatusCode ? Number(data.providerStatusCode) : undefined,
  };
}

function buildPayoutDeps(): PayoutDeps {
  return {
    randomUuid: () => crypto.randomUUID(),
    now: () => Date.now(),
    async getJob(id: string): Promise<JobRecordPayout | null> {
      const snap = await db.collection("jobs").doc(id).get();
      if (!snap.exists) return null;
      return (snap.data() || {}) as JobRecordPayout;
    },
    async findPendingPayout(
      jobId: string,
      clientUid: string,
      payoutKind?: string | null
    ): Promise<PayoutRecord | null> {
      // R08-P2-04: the gate is scoped by kind. A job can have a material advance *and* an escrow
      // release; keying only on the job id made the advance block the release it funded.
      const kind = payoutKind || "RELEASE";
      if (kind === "CASHOUT") {
        // A cashout's payout record is keyed by the withdrawal id.
        const direct = await db.collection("payouts").doc(jobId).get();
        if (!direct.exists) return null;
        return toPayoutRecord(direct.data() as Record<string, unknown>);
      }
      const snap = await db.collection("payouts")
        .where("jobId", "==", jobId)
        .where("clientUid", "==", clientUid)
        .where("payoutKind", "==", kind)
        .limit(1)
        .get();
      if (!snap.empty) return toPayoutRecord(snap.docs[0].data() as Record<string, unknown>);
      // Records written before payoutKind existed are all release payouts; honour them so a legacy
      // pending payout still blocks a second release.
      if (kind !== "RELEASE") return null;
      const legacy = await db.collection("payouts")
        .where("jobId", "==", jobId)
        .where("clientUid", "==", clientUid)
        .limit(1)
        .get();
      if (legacy.empty) return null;
      return toPayoutRecord(legacy.docs[0].data() as Record<string, unknown>);
    },

    async acquireToken(config: MomoConfig) {
      try {
        const basic = Buffer.from(`${config.apiUser}:${config.apiKey}`).toString("base64");
        const resp = await axios.post(
          `${config.baseUrl}/disbursement/token/`,
          {},
          {
            headers: {
              "Authorization": `Basic ${basic}`,
              "Ocp-Apim-Subscription-Key": config.subscriptionKey,
            },
            timeout: 15000,
            validateStatus: () => true,
          }
        );
        if (resp.status !== 200 || !resp.data?.access_token) {
          return { ok: false as const, error: `Token exchange returned HTTP ${resp.status}.` };
        }
        return { ok: true as const, token: String(resp.data.access_token) };
      } catch (e: any) {
        return { ok: false as const, error: e?.message || "Token exchange failed." };
      }
    },
    async requestPayout(config: MomoConfig, token: string, referenceId: string, payload: Record<string, unknown>) {
      try {
        const resp = await axios.post(
          `${config.baseUrl}/disbursement/v1_0/transfer`,
          payload,
          {
            headers: {
              "Authorization": `Bearer ${token}`,
              "X-Reference-Id": referenceId,
              "X-Target-Environment": "sandbox",
              "Ocp-Apim-Subscription-Key": config.subscriptionKey,
              "Content-Type": "application/json",
            },
            timeout: 20000,
            validateStatus: () => true,
          }
        );
        return { statusCode: resp.status };
      } catch (e: any) {
        const timeout = e?.code === "ECONNABORTED" || /timeout/i.test(e?.message || "");
        return { networkError: true as const, timeout, error: e?.message || "Provider request failed." };
      }
    },

    async queryProviderPayoutStatus(config: MomoConfig, token: string, providerReferenceId: string): Promise<ProviderPayoutStatusResult> {
      try {
        const resp = await axios.get(
          `${config.baseUrl}/disbursement/v1_0/transfer/${encodeURIComponent(providerReferenceId)}`,
          {
            headers: {
              "Authorization": `Bearer ${token}`,
              "X-Target-Environment": "sandbox",
              "Ocp-Apim-Subscription-Key": config.subscriptionKey,
            },
            timeout: 15000,
            validateStatus: () => true,
          }
        );
        if (resp.status === 404) {
          return { kind: "providerError", statusCode: 404, error: "Reference unknown to provider." };
        }
        if (resp.status !== 200 || !resp.data || typeof resp.data.status !== "string") {
          return { kind: "providerError", statusCode: resp.status, error: `Provider status returned HTTP ${resp.status}.` };
        }
        return {
          kind: "ok",
          status: resp.data.status,
          amount: typeof resp.data.amount === "number" ? resp.data.amount : undefined,
          currency: typeof resp.data.currency === "string" ? resp.data.currency : undefined,
          financialTransactionId: typeof resp.data.financialTransactionId === "string" ? resp.data.financialTransactionId : undefined,
          referenceId: typeof resp.data.referenceId === "string" ? resp.data.referenceId : undefined,
          statusCode: 200,
        };
      } catch (e: any) {
        const timeout = e?.code === "ECONNABORTED" || /timeout/i.test(e?.message || "");
        return { kind: timeout ? "providerTimeout" : "networkError", error: e?.message || "Provider status unavailable." };
      }
    },
    async persistPayout(record: PayoutRecord) {
      // Firestore rejects `undefined` field values, so optional fields are dropped rather than
      // written as undefined.
      const clean: Record<string, unknown> = {};
      for (const [key, value] of Object.entries(record)) {
        if (value !== undefined) clean[key] = value;
      }
      await db.collection("payouts").doc(record.payoutId).set({
        ...clean,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    },
    async updatePayout(ref: string, patch: Record<string, unknown>) {
      await db.collection("payouts").doc(ref).update(patch);
    },
  };
}


/**
 * PAY-003 (deprecated alias): retained so an installed client keeps working, but it no longer
 * implements a payout of its own.
 *
 * R03-P2-03: this used to accept `workerMomoNumber` and `amountUgx` from the caller and run the
 * separate `initiatePayout` core, which is a second contract for the same money. It now delegates
 * to [performEscrowRelease]: client-supplied amount and wallet are ignored, the job and the worker's
 * stored wallet decide.
 */
export const createMoMoPayout = functions.runWith(providerCallable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to release escrow."
    );
  }

  const { jobId, workerId, provider } = data || {};
  if (!jobId || !workerId) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameters: jobId, workerId."
    );
  }

  const result = await performEscrowRelease(jobId, workerId, context.auth.uid, provider);
  return {
    success: true,
    payoutRef: result.payoutRef,
    message: result.message,
    deprecated: "createMoMoPayout is an alias of releaseEscrowToWorker.",
  };
});




/**
 * PAY-004: Create a material advance partial payout (up to 50% of the
 * materials subtotal) when the resident approves the advance request.
 *
 * The server-side worker payout is performed through the same trusted
 * `initiatePayout` core that PAY-003 uses. A Firestore document under
 * `materialAdvances/{jobId}` is created to record the receipt-capture
 * requirement: the client must upload a hardware receipt within 60 minutes
 * of disbursement; the receipt URL is written back to the job document
 * before the advance status flips to DISBURSED.
 *
 * Idempotency: the collection doc `materialAdvances/{jobId}` is the single
 * source of truth. A second invocation with the same `jobId` sees the
 * already-queued advance and fails closed with ALREADY_PENDING.
 */
export const createMoMoMaterialAdvance = functions.runWith(providerCallable()).https.onCall(
  async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Authentication required to disburse material advance."
      );
    }

    // R08-P2-03: the caller supplies only the job id. The materials budget, the worker and the
    // number all come from the job/user documents, and authorization happens BEFORE anything is
    // written — the old code wrote a PENDING advance first, so a stranger could burn the job's only
    // advance slot without being the resident.
    const { jobId, provider } = data || {};
    if (!jobId) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "jobId is required."
      );
    }

    const jobRef = db.collection("jobs").doc(jobId);
    const jobSnap = await jobRef.get();
    const jobData = jobSnap.exists ? (jobSnap.data() as Record<string, unknown>) : null;

    const advanceRef = db.collection("materialAdvances").doc(jobId);
    const existingAdvance = await advanceRef.get();
    const existingStatus = existingAdvance.exists ? String(existingAdvance.get("status") || "") : null;

    const decision = decideMaterialAdvance(jobData as any, context.auth.uid, existingStatus);
    if (decision.kind === "REJECT") {
      throw new functions.https.HttpsError(
        decision.code as "not-found" | "permission-denied" | "failed-precondition" | "invalid-argument" | "already-exists",
        decision.message
      );
    }
    const advanceAmount = decision.amountUgx;

    // The payee is the worker on the job record, never a number supplied by the caller.
    const workerDoc = await db.collection("users").doc(decision.workerUid).get();
    const workerMomoNumber = String(
      workerDoc.get("momoNumber") || workerDoc.get("phone") || ""
    ).trim();
    if (!workerMomoNumber) {
      throw new functions.https.HttpsError(
        "failed-precondition",
        "The assigned worker has no Mobile Money number on file."
      );
    }

    // Claim the slot atomically: a concurrent second request cannot pass the ownership check and
    // then both write an advance.
    await db.runTransaction(async (tx) => {
      const snap = await tx.get(advanceRef);
      const status = snap.exists ? String(snap.get("status") || "") : "";
      if (status === "PENDING" || status === "DISBURSED") {
        throw new functions.https.HttpsError(
          "already-exists",
          status === "DISBURSED"
            ? "This material advance was already disbursed and cannot be re-run."
            : "A material advance for this job is already pending. Await its status."
        );
      }
      tx.set(advanceRef, {
        jobId,
        workerId: decision.workerUid,
        workerMomoNumber: maskMsisdn(workerMomoNumber),
        materialsSubtotal: decision.materialsSubtotal,
        advanceAmount,
        provider: provider || "MTN_MOMO",
        status: "PENDING",
        requestedBy: context.auth!.uid,
        receiptCaptureExpiresAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    });

    // Issue the partial payout through the PAY-003 trust boundary. The amount is validated against
    // the server-computed advance (not the job's labour total, which is a different bucket).
    const payoutResult = await initiatePayout(
      {
        jobId,
        clientUid: context.auth.uid,
        amountUgx: advanceAmount,
        workerMomoNumber,
        payeeResolvedServerSide: true,
        payeeWorkerId: decision.workerUid,
        expectedAmountUgx: advanceAmount,
        payoutId: `ADVANCE-${jobId}`,
        payoutKind: "ADVANCE",
        provider: provider || "MTN_MOMO",
        env: process.env as Record<string, string | undefined>,
      },
      buildPayoutDeps()
    );

    if (!payoutResult.ok) {
      await advanceRef.update({
        status: "FAILED",
        failureCode: payoutResult.error.code,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      throw payoutFailureToHttpsError(payoutResult.error);
    }

    const payoutRef = payoutResult.payoutId;
    await db.collection("notifications").add({
      userId: decision.workerUid,
      type: "PAYOUT_PENDING",
      jobId,
      message: `A material advance of UGX ${advanceAmount} has been initiated. Please upload the hardware receipt within 60 minutes so the payout can be completed.`,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      read: false,
    });

    await advanceRef.update({
      payoutRef,
      providerReferenceId: payoutResult.providerReferenceId,
      status: "PENDING",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return {
      success: true,
      payoutRef,
      advanceAmount,
      receiptCaptureDeadlineMs: Date.now() + 60 * 60 * 1000,
      message: `Material advance of UGX ${advanceAmount} is pending. Upload the hardware receipt within 60 minutes to release the funds.`,
    };
  }
);


/**
 * PAY-003: Query the provider-verified status of a previously initiated payout.
 *
 * Callable-only (authenticated). Re-queries the MTN provider status API with
 * the platform's own credentials and reports PAYOUT_PENDING /
 * PAYOUT_SUCCESSFUL / PAYOUT_FAILED / NEEDS_RECONCILIATION. The caller may
 * only query payouts bound to their own uid (the stored `clientUid`) or a job
 * they own (`postedByUid`).
 */
export const getMoMoPayoutStatus = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to query payout status."
    );
  }

  const { payoutRef } = data;
  if (!payoutRef) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "payoutRef is required."
    );
  }

  let deposit = await db.collection("payouts").doc(payoutRef).get();
  if (!deposit.exists) {
    // R08-P2-04: the older release path wrote `momo_payouts/PAYOUT-<jobId>` and nothing ever read
    // it. Those records still exist, so a status query falls back to them instead of reporting the
    // payout as missing.
    const legacy = await db.collection("momo_payouts").doc(payoutRef).get();
    if (!legacy.exists) {
      throw new functions.https.HttpsError(
        "not-found",
        "Payout not found."
      );
    }
    const legacyData = legacy.data() as Record<string, unknown>;
    const legacyStatus = String(legacyData.status || "").toUpperCase();
    if (legacyStatus === "QUEUED" || legacyStatus === "PENDING") {
      return {
        payoutRef,
        status: "PAYOUT_UNCONFIRMED",
        statusText: "UNCONFIRMED",
        workerId: legacyData.workerId,
        momoNumber: legacyData.workerMomoNumber,
        amount: legacyData.amount,
        reason: "legacy_record_without_provider_reference",
        message: "This payout was recorded before provider confirmation existed. It is not proof of payment.",
      };
    }
    return {
      payoutRef,
      status: legacyStatus,
      statusText: legacyStatus.replace("PAYOUT_", ""),
      workerId: legacyData.workerId,
      momoNumber: legacyData.workerMomoNumber,
      amount: legacyData.amount,
      reason: "legacy_record",
      message: payoutMessageFor(legacyStatus as any),
    };
  }
  const ledger = deposit.data() as PayoutRecord;
  const callerUid = context.auth.uid;

  // Authorize: the caller must be the stored clientUid or the job's owner. A wallet cashout has no
  // job document, so only its owner may query it.
  const jobDoc = ledger.jobId ? await db.collection("jobs").doc(ledger.jobId).get() : null;
  const ownerUid = jobDoc && jobDoc.exists ? (jobDoc.data() as any)?.postedByUid : undefined;
  if (ledger.clientUid !== callerUid && ownerUid !== callerUid) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "You may only query payouts bound to your own account."
    );
  }

  // Idempotent fast-path: a terminal stored record returns its final state
  // with no provider call and no write.
  if (TERMINAL_PAYOUT_STATUSES.includes(ledger.status)) {
    return {
      payoutRef,
      status: ledger.status,
      statusText: ledger.status.replace("PAYOUT_", ""),
      workerId: ledger.workerId,
      momoNumber: ledger.workerMomoNumber,
      amount: ledger.amount,
      reason: ledger.reason || "already_terminal",
      message: payoutMessageFor(ledger.status),
    };
  }

  const config = loadPayoutConfig(process.env as Record<string, string | undefined>);
  if ("error" in config) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      `Payout status unavailable: ${config.error}`
    );
  }

  const deps = buildPayoutDeps();
  const token = await deps.acquireToken(config);
  if (!token.ok) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      `Payout status unavailable: ${token.error}`
    );
  }

  const provider = await deps.queryProviderPayoutStatus(config, token.token, ledger.providerReferenceId);
  const { newStatus, reason } = decidePayoutStatus({ stored: ledger, provider });

  // Update the ledger only when the status actually changed.
  if (newStatus !== ledger.status) {
    const patch: Record<string, unknown> = { status: newStatus, reason, lastCheckedAt: Date.now() };
    if (provider.kind === "ok" && provider.financialTransactionId) {
      patch.providerFinancialTransactionId = provider.financialTransactionId;
    }
    await db.collection("payouts").doc(payoutRef).update(patch);
  }

  // R08-P2-02: a provider-confirmed payout becomes one wallet entry, keyed by the payout id, so a
  // poll and a reconciliation sweep cannot credit the same money twice. A failed payout releases the
  // reservation a cashout made.
  if (newStatus === "PAYOUT_SUCCESSFUL") {
    await creditPayoutEarning({ ...ledger, status: newStatus });
  } else if (newStatus === "PAYOUT_FAILED" && ledger.payoutKind === "CASHOUT" && ledger.withdrawalId) {
    await releaseWithdrawalReservation(ledger).catch((error) => {
      console.error("wallet reversal failed", scrubForLog((error as Error).message));
    });
  }

  return {
    payoutRef,
    status: newStatus,
    statusText: newStatus.replace("PAYOUT_", ""),
    workerId: ledger.workerId,
    momoNumber: ledger.workerMomoNumber,
    amount: ledger.amount,
    reason,
    message: payoutMessageFor(newStatus),
  };
});

// =====================================================================================
// REM-011 / REM-022: server-side phone OTP.
//
// The Africa's Talking API key is read from the Functions environment
// (AFRICASTALKING_API_KEY, AFRICASTALKING_USERNAME) and never leaves the server. Verification
// mints a Firebase custom token so the Android client can call signInWithCustomToken and obtain
// a real UID. Both callables are intentionally callable before sign-in.
// =====================================================================================

const ATT_OTP_URL = "https://api.africastalking.com/version1/otps";

async function callAfricaTalkingOtp(
  path: string,
  payload: Record<string, unknown>
): Promise<{ status: number; body: unknown }> {
  const apiKey = secretOrEnv(africaTalkingApiKey, "AFRICASTALKING_API_KEY");
  const username = secretOrEnv(africaTalkingUsername, "AFRICASTALKING_USERNAME", "SkillsExchange");
  if (!apiKey) {
    // Fail closed rather than attempting an unauthenticated provider call.
    throw new functions.https.HttpsError("failed-precondition", "Phone verification is not configured");
  }
  try {
    const response = await axios.post(
      `${ATT_OTP_URL}${path}`,
      { username, ...payload },
      {
        headers: {
          apiKey,
          Accept: "application/json",
          "Content-Type": "application/json",
        },
        timeout: 15000,
        validateStatus: () => true,
      }
    );
    return { status: response.status, body: response.data };
  } catch (error) {
    console.error("Africa's Talking OTP request failed", scrubForLog((error as Error).message));
    throw new functions.https.HttpsError("unavailable", "Phone verification is temporarily unavailable");
  }
}

async function findOrCreateUserByPhone(phone: string): Promise<{ uid: string; isNewUser: boolean }> {
  try {
    const existing = await admin.auth().getUserByPhoneNumber(phone);
    return { uid: existing.uid, isNewUser: false };
  } catch (error) {
    if ((error as { code?: string }).code !== "auth/user-not-found") {
      throw error;
    }
    const created = await admin.auth().createUser({ phoneNumber: phone });
    return { uid: created.uid, isNewUser: true };
  }
}

/**
 * R03-P2-06: device push for product events, not just SOS.
 *
 * Each signed-in client subscribes to the `user_{uid}` topic (the Android client does this after
 * authentication). Push is best-effort: a notification document is already written, and a push
 * failure must never fail the money/KYC/dispute operation that triggered it.
 */
/**
 * R05-P2-02: a user's registered device tokens, newest first.
 *
 * The Android client used to log its FCM token and never send it anywhere, so the backend could only
 * reach a topic. Tokens are now written by `registerPushToken` and used here; the topic remains as a
 * fallback for installs that have not registered yet.
 */
async function deviceTokensFor(userId: string): Promise<string[]> {
  try {
    const snapshot = await db
      .collection("users")
      .doc(userId)
      .collection("push_tokens")
      .orderBy("updatedAt", "desc")
      .limit(20)
      .get();
    return snapshot.docs
      .map((doc) => String(doc.get("token") || ""))
      .filter((token) => token.length > 0);
  } catch (error: any) {
    console.warn("Push token lookup failed", { userId, message: error?.message });
    return [];
  }
}

async function sendUserPush(
  userId: string | undefined,
  title: string,
  body: string,
  data: Record<string, string> = {}
): Promise<void> {
  if (!userId) return;
  try {
    const tokens = await deviceTokensFor(userId);
    if (tokens.length > 0) {
      const response = await admin.messaging().sendEachForMulticast({
        tokens,
        notification: { title, body },
        data,
        android: { priority: "high" },
      });
      // Tokens the provider has retired must not keep failing forever.
      const stale: string[] = [];
      response.responses.forEach((result, index) => {
        const code = result.error?.code || "";
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token"
        ) {
          stale.push(tokens[index]);
        }
      });
      await Promise.all(
        stale.map((token) =>
          db
            .collection("users")
            .doc(userId)
            .collection("push_tokens")
            .doc(encodeURIComponent(token))
            .delete()
            .catch(() => undefined)
        )
      );
      if (response.successCount > 0) return;
    }
    await admin.messaging().send({
      topic: `user_${userId}`,
      notification: { title, body },
      data,
      android: { priority: "high" },
    });
  } catch (error: any) {
    console.warn("User push failed", { userId, message: error?.message });
  }
}

/**
 * R05-P2-02: register this device's FCM token for the calling user.
 *
 * The token is stored per device under the authenticated uid, so an account can hold several
 * devices and a signed-out device cannot register against somebody else's account. The client never
 * sends a uid; it is taken from the verified auth context.
 */
export const registerPushToken = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to register a push token."
    );
  }
  const token = String((data || {}).token || "").trim();
  const platform = String((data || {}).platform || "android").trim().slice(0, 20);
  if (token.length < 20 || token.length > 4096) {
    throw new functions.https.HttpsError("invalid-argument", "A valid device token is required.");
  }
  const now = Date.now();
  await db
    .collection("users")
    .doc(context.auth.uid)
    .collection("push_tokens")
    .doc(encodeURIComponent(token))
    .set({ token, platform, updatedAt: now, createdAt: now }, { merge: true });
  return { success: true };
});

/** R03-P2-07: a single IP may not drive an unbounded number of SMS sends. */
const OTP_IP_WINDOW_MS = 60 * 60 * 1000;
const OTP_IP_MAX_SENDS = 20;

export const sendOtp = functions.runWith(providerCallable({ secrets: AFRICA_TALKING_SECRETS })).https.onCall(async (data, context) => {
  let phone: string;
  try {
    phone = normalizeUgandanPhone((data ?? {}).phoneNumber);
  } catch {
    throw new functions.https.HttpsError("invalid-argument", "Enter a valid Ugandan phone number");
  }

  const challengeRef = db.collection("otp_challenges").doc(otpDocId(phone));
  const now = Date.now();

  const decision = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(challengeRef);
    const result = evaluateSendRate(snapshot.exists ? (snapshot.data() as OtpRateRecord) : undefined, now);
    if (result.allowed) {
      transaction.set(
        challengeRef,
        { phoneNumber: phone, ...result.next, updatedAt: now },
        { merge: true }
      );
    }
    return result;
  });

  if (!decision.allowed) {
    throw new functions.https.HttpsError("resource-exhausted", "Please wait before requesting another code");
  }

  // R03-P2-07: per-IP throttle, checked before the provider call so an attacker rotating phone
  // numbers cannot turn the OTP endpoint into an SMS-cost amplifier. Missing IP (emulator, direct
  // invocation) is allowed through rather than failing closed, matching the existing rate model.
  const clientIp = (context as any)?.rawRequest?.ip || (data ?? {}).ip || "";
  if (clientIp) {
    const ipRef = db.collection("otp_ip_limits").doc(String(clientIp).replace(/[^a-zA-Z0-9.:_-]/g, "_"));
    const ipAllowed = await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(ipRef);
      const record = snapshot.exists ? (snapshot.data() as { windowStart?: number; count?: number }) : {};
      const windowStart = typeof record.windowStart === "number" ? record.windowStart : 0;
      const inWindow = now - windowStart < OTP_IP_WINDOW_MS;
      const count = inWindow ? (record.count ?? 0) : 0;
      if (count >= OTP_IP_MAX_SENDS) return false;
      transaction.set(
        ipRef,
        { windowStart: inWindow ? windowStart : now, count: count + 1, updatedAt: now },
        { merge: true }
      );
      return true;
    });
    if (!ipAllowed) {
      throw new functions.https.HttpsError(
        "resource-exhausted",
        "Too many verification requests from this network. Try again later."
      );
    }
  }

  const provider = await callAfricaTalkingOtp("", {
    phoneNumber: phone,
    message: OTP_MESSAGE_PREFIX,
  });
  const parsed = parseSendOtpResponse(provider.status, provider.body);
  if (!parsed.valid || !parsed.requestId) {
    console.error("OTP send rejected", { status: parsed.statusCode, phone: maskPhone(phone) });
    throw new functions.https.HttpsError("unavailable", "Could not send the verification code");
  }

  await challengeRef.set(
    { requestId: parsed.requestId, sentAt: now, attempts: 0, verified: false },
    { merge: true }
  );

  return {
    requestId: parsed.requestId,
    expiresIn: parsed.expiresIn ?? Math.floor(OTP_TTL_MS / 1000),
  };
});

export const verifyOtp = functions.runWith(providerCallable({ secrets: AFRICA_TALKING_SECRETS })).https.onCall(async (data) => {
  const code = (data ?? {}).code;
  if (typeof code !== "string" || !/^\d{4,8}$/.test(code)) {
    throw new functions.https.HttpsError("invalid-argument", "Enter the code from the SMS");
  }

  let phone: string;
  try {
    phone = normalizeUgandanPhone((data ?? {}).phoneNumber);
  } catch {
    throw new functions.https.HttpsError("invalid-argument", "Enter a valid Ugandan phone number");
  }

  const challengeRef = db.collection("otp_challenges").doc(otpDocId(phone));
  const snapshot = await challengeRef.get();
  const now = Date.now();
  const challenge = snapshot.exists ? (snapshot.data() as OtpRateRecord) : undefined;
  const decision = evaluateVerifyRate(challenge, now);

  if (!decision.allowed) {
    const message =
      decision.reason === "expired" ? "That code has expired. Request a new one." :
      decision.reason === "attempts_exhausted" ? "Too many attempts. Request a new code." :
      "Request a verification code first.";
    throw new functions.https.HttpsError("failed-precondition", message);
  }

  // Count the attempt before calling the provider so a hung request cannot be retried freely.
  await challengeRef.set({ attempts: decision.next.attempts, updatedAt: now }, { merge: true });

  const provider = await callAfricaTalkingOtp("/verify", {
    phoneNumber: phone,
    code,
    requestId: challenge?.requestId,
  });
  const parsed = parseVerifyOtpResponse(provider.status, provider.body);
  if (!parsed.valid) {
    throw new functions.https.HttpsError("invalid-argument", "That code is not valid");
  }

  await challengeRef.set({ verified: true, verifiedAt: now }, { merge: true });

  const user = await findOrCreateUserByPhone(phone);
  const token = await admin.auth().createCustomToken(user.uid);

  return { token, uid: user.uid, isNewUser: user.isNewUser };
});

// =====================================================================================
// REM-051: server-side voice transcription. The Gemini key stays in the Functions
// environment; a failure or missing configuration is returned as an error instead of the
// fabricated "example job" the client used to produce.
// =====================================================================================

export const transcribeVoiceJob = functions.runWith(providerCallable({ secrets: GEMINI_SECRETS })).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to transcribe a voice request");
  }

  const audioBase64 = (data ?? {}).audioBase64;
  const mimeType = typeof (data ?? {}).mimeType === "string" ? String(data.mimeType) : "audio/mp4";
  if (typeof audioBase64 !== "string" || audioBase64.length === 0) {
    throw new functions.https.HttpsError("invalid-argument", "Audio is required");
  }
  if (audioBase64.length > MAX_AUDIO_BASE64_LENGTH) {
    throw new functions.https.HttpsError("invalid-argument", "Voice note is too large");
  }
  if (!/^audio\/[a-z0-9.+-]+$/i.test(mimeType)) {
    throw new functions.https.HttpsError("invalid-argument", "Unsupported audio type");
  }

  const apiKey = secretOrEnv(geminiApiKey, "GEMINI_API_KEY");
  if (!apiKey) {
    throw new functions.https.HttpsError("failed-precondition", "Voice transcription is not configured");
  }

  let response;
  try {
    response = await axios.post(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
      buildGeminiTranscriptionRequest(audioBase64, mimeType, buildTranscriptionPrompt()),
      {
        headers: {
          "x-goog-api-key": apiKey,
          "Content-Type": "application/json",
        },
        timeout: 60000,
        maxBodyLength: 12 * 1024 * 1024,
        validateStatus: () => true,
      }
    );
  } catch (error) {
    console.error("Gemini transcription request failed", scrubForLog((error as Error).message));
    throw new functions.https.HttpsError("unavailable", "Voice transcription is temporarily unavailable");
  }

  if (response.status < 200 || response.status >= 300) {
    console.error("Gemini transcription rejected", { status: response.status });
    throw new functions.https.HttpsError("unavailable", "Voice transcription failed");
  }

  try {
    const text = extractGeminiText(response.data);
    return validateTranscribedJob(extractJsonObject(text));
  } catch (error) {
    console.error("Gemini transcription response invalid", scrubForLog((error as Error).message));
    throw new functions.https.HttpsError("internal", "Voice transcription could not be understood");
  }
});

// =====================================================================================
// R05-P2-04 / R05-P2-05: Google Maps grounding.
//
// P2-04: the Gemini key is no longer compiled into the APK. The client calls this callable and the
// key stays in the Functions secret. P2-05: only places Google actually grounded are returned; when
// nothing is grounded the client is told so instead of being handed invented shops.
//
// P2-07: one request has a 12s ceiling and at most two attempts with a short backoff, so a slow
// Gemini upstream cannot hang the callable until the platform timeout.
// =====================================================================================

const PLACES_TIMEOUT_MS = 12000;
const PLACES_MAX_ATTEMPTS = 2;
const PLACES_RETRY_BASE_MS = 400;

export const searchLocalPlaces = functions
  .runWith(withAppCheck({ secrets: GEMINI_SECRETS }))
  .https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Sign in to search nearby places."
      );
    }

    let input: { query: string; district: string };
    try {
      input = normaliseSearchInput(data?.query, data?.district);
    } catch (error) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        (error as Error).message || "Invalid search input"
      );
    }

    const apiKey = secretOrEnv(geminiApiKey, "GEMINI_API_KEY");
    if (!apiKey) {
      // Fail closed: without a key there is nothing verifiable to show.
      throw new functions.https.HttpsError(
        "failed-precondition",
        "Nearby place search is not configured."
      );
    }

    const requestBody = buildMapsGroundingRequest(input.query, input.district);
    let lastFailure = "";

    for (let attempt = 1; attempt <= PLACES_MAX_ATTEMPTS; attempt++) {
      try {
        const response = await axios.post(
          "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
          requestBody,
          {
            headers: { "x-goog-api-key": apiKey, "Content-Type": "application/json" },
            timeout: PLACES_TIMEOUT_MS,
            validateStatus: () => true,
          }
        );

        if (response.status >= 200 && response.status < 300) {
          return parseGroundedPlaces(response.data);
        }

        lastFailure = `status ${response.status}`;
        const retryable = response.status === 429 || response.status >= 500;
        if (!retryable) break;
      } catch (error) {
        lastFailure = (error as Error).message || "network error";
      }

      if (attempt < PLACES_MAX_ATTEMPTS) {
        await new Promise((resolve) =>
          setTimeout(resolve, PLACES_RETRY_BASE_MS * attempt)
        );
      }
    }

    console.warn("Maps grounding failed", { lastFailure });
    throw new functions.https.HttpsError(
      "unavailable",
      "Nearby place search is temporarily unavailable."
    );
  });

// =====================================================================================
// REM-044: consent and data-subject access.
// =====================================================================================

export const recordConsent = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to record consent");
  }
  let record;
  try {
    record = buildConsentRecord({ version: data?.version, grants: data?.grants }, Date.now());
  } catch (error) {
    throw new functions.https.HttpsError("invalid-argument", (error as Error).message);
  }

  // Server-written: clients may read their consent history but never forge or delete it.
  await db
    .collection("users")
    .doc(context.auth.uid)
    .collection("consents")
    .doc(record.version)
    .set(record, { merge: false });

  return { version: record.version, acceptedAt: record.acceptedAt, currentVersion: CONSENT_VERSION };
});

/**
 * R09-P2-02: third-party guarantor consent, recorded separately from the worker's own.
 *
 * The guarantor sheet cannot submit until the worker has confirmed, for each guarantor, that the
 * person was told and agreed. The record is written by the server with the policy version and the
 * time it was given, into its own collection, so the grant can be shown and (later) withdrawn without
 * touching the worker's own consent history.
 */
export const recordGuarantorConsent = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to record guarantor consent");
  }
  let record;
  try {
    record = buildGuarantorConsentRecord(
      { version: data?.version, guarantorCount: data?.guarantorCount, confirmed: data?.confirmed },
      Date.now()
    );
  } catch (error) {
    throw new functions.https.HttpsError("invalid-argument", (error as Error).message);
  }

  await db
    .collection("users")
    .doc(context.auth.uid)
    .collection("guarantor_consents")
    .doc(record.version)
    .set(record, { merge: false });

  return {
    version: record.version,
    recordedAt: record.recordedAt,
    guarantorCount: record.guarantorCount,
    currentVersion: CONSENT_VERSION,
  };
});

/**
 * R09: withdrawal of consent is recorded, never erased.
 *
 * Withdrawal stops the platform relying on the grant from that moment, but the original receipt stays
 * — a consent record that can be deleted proves nothing. Withdrawal requires a fresh sign-in because
 * it is a consequential change to the account's legal position.
 */
export const withdrawConsent = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to withdraw consent");
  }
  const authTime = (context.auth.token as { auth_time?: unknown }).auth_time;
  if (!isFreshAuthForDeletion(authTime, Date.now())) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Please verify your identity again before withdrawing consent.",
      { code: "REAUTH_REQUIRED", windowSeconds: FRESH_AUTH_WINDOW_SECONDS }
    );
  }

  let withdrawal;
  try {
    withdrawal = buildConsentWithdrawal({ version: data?.version, reason: data?.reason }, Date.now());
  } catch (error) {
    throw new functions.https.HttpsError("invalid-argument", (error as Error).message);
  }

  const consentRef = db
    .collection("users")
    .doc(context.auth.uid)
    .collection("consents")
    .doc(withdrawal.version);
  const existing = await consentRef.get();
  if (!existing.exists) {
    throw new functions.https.HttpsError("not-found", "There is no consent record for that version.");
  }
  if (existing.get("revokedAt")) {
    return { version: withdrawal.version, revokedAt: Number(existing.get("revokedAt")), alreadyWithdrawn: true };
  }

  await consentRef.set(
    { revokedAt: withdrawal.revokedAt, revokeReason: withdrawal.reason },
    { merge: true }
  );

  return { version: withdrawal.version, revokedAt: withdrawal.revokedAt, alreadyWithdrawn: false };
});

export const exportMyData = functions.runWith(callable()).https.onCall(async (_data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to export your data");
  }
  const uid = context.auth.uid;
  const LIMIT = 500;

  const [profile, postedJobs, assignedJobs, disputes, reports, notifications, payouts, submissions, guarantors] =
    await Promise.all([
      db.collection("users").doc(uid).get(),
      db.collection("jobs").where("postedByUid", "==", uid).limit(LIMIT).get(),
      db.collection("jobs").where("assignedProviderUid", "==", uid).limit(LIMIT).get(),
      db.collection("disputes").where("reportedBy", "==", uid).limit(LIMIT).get(),
      db.collection("reports").where("reportedBy", "==", uid).limit(LIMIT).get(),
      db.collection("notifications").where("userId", "==", uid).limit(LIMIT).get(),
      db.collection("momo_payouts").where("workerId", "==", uid).limit(LIMIT).get(),
      db.collection("guarantor_submissions").doc(uid).get(),
      db.collection("guarantors").where("workerKycId", "==", uid).limit(20).get(),
    ]);

  const toRows = (snapshot: admin.firestore.QuerySnapshot): Array<Record<string, unknown>> =>
    snapshot.docs.map((doc) => ({ id: doc.id, ...(doc.data() as Record<string, unknown>) }));

  const data = {
    profile: profile.exists ? profile.data() : null,
    jobsPosted: toRows(postedJobs),
    jobsAssigned: toRows(assignedJobs),
    disputes: toRows(disputes),
    reports: toRows(reports),
    notifications: toRows(notifications),
    payouts: toRows(payouts),
    // R09-P2-03: the submission document holds two other people's NINs and photo paths; it is
    // redacted before it leaves the server.
    guarantorSubmission: submissions.exists
      ? sanitiseGuarantorSubmissionForExport({ id: submissions.id, ...(submissions.data() as Record<string, unknown>) })
      : null,
    // Third-party identifiers are stripped here, not in the client.
    guarantors: guarantors.docs.map((doc) => sanitiseGuarantorForExport(doc.data() as Record<string, unknown>)),
    consents: (
      await db.collection("users").doc(uid).collection("consents").limit(50).get()
    ).docs.map((doc) => ({ version: doc.id, ...(doc.data() as Record<string, unknown>) })),
    guarantorConsents: (
      await db.collection("users").doc(uid).collection("guarantor_consents").limit(50).get()
    ).docs.map((doc) => ({ version: doc.id, ...(doc.data() as Record<string, unknown>) })),
    // R09-P2-04: the export states how long each category is kept, so a subject is not told
    // "we delete everything" while the payment books are held for seven years.
    retention: {
      policy: RETENTION_POLICY.map((category) => ({
        category: category.id,
        collection: category.collection,
        retentionDays: category.retentionDays,
        action: category.action,
        basis: category.basis,
      })),
      legalHolds: retainedRecords(),
    },
  };

  return buildDsarEnvelope(data, Date.now());
});

// =====================================================================================
// REM-023: server-verified operator access.
// =====================================================================================

export const getAdminAccess = functions.runWith(callable()).https.onCall(async (_data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to check operator access");
  }
  // Deliberately returns only the boolean: the client needs a gate, not a claims dump.
  return { admin: hasAdminClaim(context.auth.token) };
});

// =====================================================================================
// REM-024: session revocation.
// =====================================================================================

export const revokeMyTokens = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to end the session");
  }
  const record = buildRevocationRecord(
    context.auth.uid,
    data?.reason === "ACCOUNT_DELETION" ? "ACCOUNT_DELETION" : "LOGOUT",
    Date.now()
  );
  await admin.auth().revokeRefreshTokens(record.uid);
  console.info("Refresh tokens revoked", { uid: record.uid, reason: record.reason });
  return { revoked: true, at: record.revokedAt };
});

// ============ R06-P2-01 / R06-P2-02: server-authoritative account purge ============
//
// The Android client used to call `deleteUserFirestoreData(...)` and then `deleteCurrentUser()`
// unconditionally, and it never touched Cloud Storage. A failed Firestore delete still destroyed
// the Auth user, and every personal photograph (KYC documents, guarantor portraits, avatars, job
// photos) stayed in the bucket with no owner able to request its removal.
//
// The purge now happens here, in a fixed order, and the client may only delete the Firebase Auth
// user after this function reports `complete: true`. The reports are counted rather than boolean:
// one failed object delete is enough to keep the account alive so it can be finished later.

/** Deletes every object under `prefix` and counts what is left. */
async function purgeStoragePrefix(
  bucket: ReturnType<ReturnType<typeof admin.storage>["bucket"]>,
  prefix: string
): Promise<{ deleted: number; pending: number }> {
  let deleted = 0;
  let pending = 0;
  try {
    const [files] = await bucket.getFiles({ prefix, autoPaginate: true });
    for (const file of files) {
      try {
        await file.delete({ ignoreNotFound: true });
        deleted += 1;
      } catch {
        pending += 1;
      }
    }
  } catch {
    // A listing failure is not proof that the prefix is empty: treat it as pending work so the
    // account is kept and can be purged again.
    pending += 1;
  }
  return { deleted, pending };
}

export const deleteMyAccount = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to delete your account.");
  }
  // R06-P2-05: the client asks for a fresh sign-in before this call; the server enforces it so a
  // stolen long-lived token cannot delete an account on its own.
  const authTime = (context.auth.token as { auth_time?: unknown }).auth_time;
  if (!isFreshAuthForDeletion(authTime, Date.now())) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Please verify your identity again before deleting your account.",
      { code: "REAUTH_REQUIRED", windowSeconds: FRESH_AUTH_WINDOW_SECONDS }
    );
  }

  const uid = context.auth.uid;
  const requestedJobIds: string[] = Array.isArray(data?.jobIds)
    ? data.jobIds.filter((id: unknown): id is string => typeof id === "string" && id.trim().length > 0)
    : [];

  // Only jobs this account really posted are touched, so a caller cannot delete somebody else's job.
  const jobIds: string[] = [];
  for (const jobId of requestedJobIds.slice(0, 200)) {
    try {
      const jobDoc = await db.collection("jobs").doc(jobId).get();
      if (jobDoc.exists && jobDoc.data()?.["postedByUid"] === uid) jobIds.push(jobId);
    } catch {
      // Unreadable job: leave it out rather than deleting on an unproven assumption.
    }
  }

  let storageDeleted = 0;
  let storagePending = 0;
  try {
    const bucket = privatePhotoBucket();
    if (bucket) {
      for (const prefix of storagePurgePrefixes(uid, jobIds)) {
        const result = await purgeStoragePrefix(bucket as any, prefix);
        storageDeleted += result.deleted;
        storagePending += result.pending;
      }
    } else {
      storagePending += 1;
    }
  } catch {
    storagePending += 1;
  }

  let firestoreDocsDeleted = 0;
  let firestorePending = 0;
  const deleteRef = async (ref: FirebaseFirestore.DocumentReference) => {
    try {
      await ref.delete();
      firestoreDocsDeleted += 1;
    } catch {
      firestorePending += 1;
    }
  };
  const deleteMatches = async (collection: string, field: string) => {
    try {
      const snapshot = await db.collection(collection).where(field, "==", uid).get();
      for (const doc of snapshot.docs) await deleteRef(doc.ref);
    } catch {
      firestorePending += 1;
    }
  };

  for (const jobId of jobIds) {
    try {
      const messages = await db.collection("jobs").doc(jobId).collection("messages").get();
      for (const message of messages.docs) await deleteRef(message.ref);
    } catch {
      firestorePending += 1;
    }
  }

  // R09-P2-01: this account's own words in jobs it did NOT post. The old purge keyed chat by job id
  // only, so a worker's messages survived in the resident's job — still that worker's personal data.
  try {
    const workedJobs = await db
      .collection("jobs")
      .where(ASSIGNED_JOB_FIELD, "==", uid)
      .limit(200)
      .get();
    for (const job of workedJobs.docs) {
      if (jobIds.includes(job.id)) continue; // already purged above
      const messages = await job.ref.collection("messages").get();
      const mine = messagesToPurge(
        messages.docs.map((doc) => ({ id: doc.id, data: doc.data() as Record<string, unknown> })),
        uid
      );
      for (const messageId of mine) {
        await deleteRef(job.ref.collection("messages").doc(messageId));
      }
    }
  } catch {
    firestorePending += 1;
  }
  for (const collection of SINGLE_DOC_COLLECTIONS) {
    await deleteRef(db.collection(collection).doc(uid));
  }
  // `users/{uid}` sub-collections survive the parent delete; push tokens are personal data too.
  try {
    const tokens = await db.collection("users").doc(uid).collection("push_tokens").get();
    for (const token of tokens.docs) await deleteRef(token.ref);
  } catch {
    firestorePending += 1;
  }
  for (const rule of QUERY_DELETIONS) {
    await deleteMatches(rule.collection, rule.field);
  }
  for (const jobId of jobIds) {
    await deleteRef(db.collection("jobs").doc(jobId));
  }

  const progress: DeletionProgress = {
    storageDeleted,
    firestoreDocsDeleted,
    storagePending,
    firestorePending,
  };
  const complete = mayDeleteAuthUser(progress);
  const retained = retainedRecords();
  const notice = buildErasureNotice({
    deletedRecords: firestoreDocsDeleted,
    deletedFiles: storageDeleted,
    retained,
    complete,
    pendingRecords: firestorePending,
    pendingFiles: storagePending,
  });
  await db
    .collection("account_deletions")
    .doc(`${uid}_${Date.now()}`)
    .set({
      uid,
      at: new Date(),
      complete,
      storageDeleted,
      firestoreDocsDeleted,
      storagePending,
      firestorePending,
      retainedCategories: retained.map((hold) => hold.category),
      steps: ERASURE_STEPS,
      notice,
    })
    .catch(() => undefined);

  if (!complete) {
    throw new functions.https.HttpsError(
      "internal",
      deletionRefusalReason(progress),
      { code: "PURGE_INCOMPLETE", ...progress, retainedCategories: retained.map((hold) => hold.category) }
    );
  }

  console.info("Account data purged; auth user may now be deleted", { uid, ...progress });
  return {
    complete: true,
    ...progress,
    retainedCategories: retained.map((hold) => hold.category),
    notice,
  };
});

/**
 * R06-P2-04: remove this device's push token (or all of them) from the signed-in account.
 *
 * Called before sign-out, because once the session is gone the client can no longer prove it owns
 * the token record and the phone would keep receiving another person's notifications.
 */
export const clearMyPushTokens = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to clear push tokens.");
  }
  const uid = context.auth.uid;
  const token = typeof data?.token === "string" ? data.token.trim() : "";
  try {
    if (token) {
      await db.collection("users").doc(uid).collection("push_tokens").doc(encodeURIComponent(token)).delete();
      return { cleared: true, count: 1 };
    }
    const snapshot = await db.collection("users").doc(uid).collection("push_tokens").get();
    for (const doc of snapshot.docs) await doc.ref.delete();
    return { cleared: true, count: snapshot.size };
  } catch (error: any) {
    throw new functions.https.HttpsError("internal", "Could not clear push tokens.", {
      code: "PUSH_CLEAR_FAILED",
    });
  }
});

// ============ R07-P2-01: server-held doorstep PIN ============
//
// The PIN proves that the resident and the worker are standing at the same door. It used to be
// verified on the phone against a value the caller supplied (`verifyDoorstepPin(jobId, entered,
// expected)`), stored in plaintext in the job document, defaulted to "4829" and had no attempt
// limit — so a modified client could simply pass `expected == entered`, and could guess freely.
//
// It is now registered once by the resident, stored as a salted SHA-256 hash in `job_secrets/{jobId}`
// (a collection no client can read or write), verified server-side with a constant-time comparison,
// and protected by five attempts followed by a fifteen-minute lockout. Only the owner or the
// assigned provider may attempt it, and a success is what moves the job into IN_PROGRESS.

function readJobId(data: any): string {
  const jobId = typeof data?.jobId === "string" ? data.jobId.trim() : "";
  if (!/^[A-Za-z0-9_-]{3,64}$/.test(jobId)) {
    throw new functions.https.HttpsError("invalid-argument", "A valid job id is required.");
  }
  return jobId;
}

/** True when the caller is one of the two parties of the job. */
function isJobParty(jobData: Record<string, unknown> | undefined, uid: string): boolean {
  if (!jobData) return false;
  return jobData["postedByUid"] === uid ||
    jobData["assignedProviderUid"] === uid ||
    jobData["assignedProviderId"] === uid;
}

/**
 * R07-P2-01: register (or re-register) the doorstep PIN for a job the caller owns.
 *
 * The plaintext PIN never leaves the device in readable form elsewhere: it is hashed here with a
 * fresh salt, and the job document is never touched (rules forbid client writes to PIN fields, and
 * this function writes only `job_secrets`).
 */
export const setDoorstepPin = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to register a doorstep PIN.");
  }
  const jobId = readJobId(data);
  const pin = typeof data?.pin === "string" ? data.pin.trim() : "";
  if (!isValidPin(pin)) {
    throw new functions.https.HttpsError("invalid-argument", "The doorstep PIN must be exactly four digits.");
  }

  const jobDoc = await db.collection("jobs").doc(jobId).get();
  if (!jobDoc.exists) {
    throw new functions.https.HttpsError("not-found", "That job does not exist.");
  }
  const jobData = (jobDoc.data() || {}) as Record<string, unknown>;
  if (jobData["postedByUid"] !== context.auth.uid) {
    throw new functions.https.HttpsError("permission-denied", "Only the resident who posted the job can set its PIN.");
  }
  if (jobData["isDoorstepVerified"] === true) {
    throw new functions.https.HttpsError("failed-precondition", "This job's doorstep PIN has already been verified.");
  }

  const salt = newSalt();
  await db
    .collection("job_secrets")
    .doc(jobId)
    .set(
      {
        jobId,
        uid: context.auth.uid,
        salt,
        pinHash: hashPin(pin, salt),
        attempts: 0,
        lockedUntil: 0,
        createdAt: Date.now(),
        updatedAt: Date.now(),
      },
      { merge: true }
    );
  return { ok: true };
});

/**
 * R07-P2-01: verify the doorstep PIN and, on success, start the job.
 *
 * Returns counts only — never the hash, the salt or a comparison result that could be used as an
 * oracle beyond the explicit `remainingAttempts`. A wrong PIN is `permission-denied` with a message
 * the UI can show verbatim.
 */
export const verifyDoorstepPin = functions.runWith(callable()).https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to verify the doorstep PIN.");
  }
  const uid = context.auth.uid;
  const jobId = readJobId(data);
  const pin = typeof data?.pin === "string" ? data.pin.trim() : "";

  const jobRef = db.collection("jobs").doc(jobId);
  const jobDoc = await jobRef.get();
  if (!jobDoc.exists) {
    throw new functions.https.HttpsError("not-found", "That job does not exist.");
  }
  const jobData = (jobDoc.data() || {}) as Record<string, unknown>;
  if (!isJobParty(jobData, uid)) {
    throw new functions.https.HttpsError("permission-denied", "Only the resident or the assigned worker can verify this PIN.");
  }

  const secretRef = db.collection("job_secrets").doc(jobId);
  const secretDoc = await secretRef.get();
  if (!secretDoc.exists) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "This job has no doorstep PIN on file yet.",
      { code: "PIN_NOT_REGISTERED" }
    );
  }
  const secret = secretDoc.data() || {};
  const now = Date.now();
  const state = attemptStateOf(secret as Record<string, unknown>);

  const lockedFor = lockRemainingMs(state, now);
  if (lockedFor > 0) {
    const outcome = applyAttempt(state, false, now);
    throw new functions.https.HttpsError("resource-exhausted", refusalMessage(outcome), {
      code: "PIN_LOCKED",
      retryAfterMs: outcome.retryAfterMs,
    });
  }

  const success = isValidPin(pin) && pinMatches(pin, secret["salt"], secret["pinHash"]);
  const outcome = applyAttempt(state, success, now);

  if (!success) {
    await secretRef.set(
      { attempts: outcome.attempts, lockedUntil: outcome.lockedUntil, updatedAt: now },
      { merge: true }
    );
    throw new functions.https.HttpsError(
      outcome.locked ? "resource-exhausted" : "permission-denied",
      refusalMessage(outcome),
      {
        code: outcome.locked ? "PIN_LOCKED" : "PIN_INCORRECT",
        remainingAttempts: outcome.remainingAttempts,
        retryAfterMs: outcome.retryAfterMs,
      }
    );
  }

  await secretRef.set({ attempts: 0, lockedUntil: 0, verifiedAt: now, verifiedBy: uid, updatedAt: now }, { merge: true });
  // The success path is the only place a job may move to IN_PROGRESS on a PIN event.
  const updates: Record<string, unknown> = {
    isDoorstepVerified: true,
    doorstepPinVerifiedAt: now,
    doorstepPinVerifiedBy: uid,
  };
  if (jobData["status"] === "ACCEPTED_FUNDED") {
    updates["status"] = "IN_PROGRESS";
    updates["escrowStatus"] = "WORK_IN_PROGRESS";
  }
  await jobRef.update(updates);
  return { ok: true };
});
