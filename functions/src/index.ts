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
  buildPayoutRequestPayload,
  initiatePayout,
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
import { buildConsentRecord, buildDsarEnvelope, sanitiseGuarantorForExport } from "./compliance";
import { loadSmsConfig, createSmsSender } from "./sms";
import type { SmsTransport } from "./sms";
import { planSosDispatch, selectSosRecipients, buildSosMessage } from "./sosDispatch";
import { hasAdminClaim } from "./admin";
import { isFreshAuth, buildRevocationRecord } from "./session";
import { decideEscrowRelease } from "./escrowRelease";
import { decideRefund, buildRefundPayload } from "./refund";
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
export const createMoMoCollection = functions.https.onCall(async (data, context) => {
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
    const resp = await axios.get(
      `${config.baseUrl}/collection/v1_0/requesttopay/${encodeURIComponent(providerReferenceId)}`,
      {
        headers: {
          "Authorization": `Bearer ${token}`,
          "Ocp-Apim-Subscription-Key": config.subscriptionKey,
          "X-Target-Environment": "sandbox",
        },
        timeout: 20000,
        validateStatus: () => true,
      }
    );
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
export const getMoMoCollectionStatus = functions.https.onCall(async (data, context) => {
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




/**
 * PAY-003: Escrow Release & Worker MoMo Payout
 * Callable function triggered when resident approves job completion.
 * Credits worker's Mobile Money wallet and updates job status.
 * SEC-004: Fixed to verify job ownership before releasing escrow.
 */
/**
 * R03-P2-03: the ONE server-side escrow release. Every caller (the callable below and the
 * deprecated `createMoMoPayout` alias) goes through this function, so the amount and the wallet
 * are always derived from server records and a second payout implementation cannot drift.
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

  // Deterministic id: a retried call cannot create a second payout document.
  const payoutRef = `PAYOUT-${jobId}`;
  const payoutPayload = {
    payoutId: payoutRef,
    jobId,
    workerId,
    workerMomoNumber,
    amount: amountUgx,
    currency: "UGX",
    provider: provider || "MTN_MOMO",
    status: "QUEUED",
    releasedBy: callerUid,
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  };

  await db.collection("momo_payouts").doc(payoutRef).set(payoutPayload);

  await db.collection("jobs").doc(jobId).update({
    status: "COMPLETED",
    escrowStatus: "RELEASED_TO_WORKER",
    releasedBy: callerUid,
    releasedAt: admin.firestore.FieldValue.serverTimestamp(),
    releasePayoutRef: payoutRef,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

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
    payoutRef,
    message: `Escrow released. UGX ${amountUgx} payout queued to ${workerMomoNumber}.`
  };
}

export const releaseEscrowToWorker = functions.https.onCall(async (data, context) => {
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
export const refundEscrow = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to request a refund.");
  }
  if (!hasAdminClaim(context.auth.token)) {
    throw new functions.https.HttpsError("permission-denied", "Only administrators can refund escrow.");
  }
  const { jobId, amount, reason } = data || {};
  return performEscrowRefund(jobId, Number(amount), String(reason || "Admin refund"), context.auth.uid);
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
    console.error("Error processing MoMo callback:", error);
    res.status(500).send({ error: error.message });
  }
});


/**
 * MODULE 4: SMS NOTIFICATION TRIGGER & RADIUS BROADCAST
 * Cloud Function trigger listening for new job creations.
 * Queries verified workers within 5km radius and dispatches SMS alerts to offline 2G/3G phones.
 */
export const onJobCreated = functions.runWith({ secrets: AFRICA_TALKING_SECRETS }).firestore
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
export const onSosIncidentCreated = functions.runWith({ secrets: AFRICA_TALKING_SECRETS }).firestore
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
      console.error("SOS push failed", (error as Error).message);
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
export const getPendingVerificationQueue = functions.https.onCall(async (data, context) => {
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
export const reviewWorkerApplication = functions.https.onCall(async (data, context) => {
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
export const setWorkerActivationStatus = functions.https.onCall(async (data, context) => {
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
export const getGuarantorSubmission = functions.https.onCall(async (data, context) => {
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
export const reviewGuarantorSubmission = functions.https.onCall(async (data, context) => {
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
export const resolveDispute = functions.https.onCall(async (data, context) => {
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
export const getDisputesQueue = functions.https.onCall(async (data, context) => {
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

/**
 * AUDIT-A-SOS: Get SOS Queue
 * Admin-only function to retrieve SOS incidents for the dispatch desk.
 */
export const getSosQueue = functions.https.onCall(async (data, context) => {
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
export const acknowledgeSos = functions.https.onCall(async (data, context) => {
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
export const resolveSosIncident = functions.https.onCall(async (data, context) => {
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
function buildPayoutDeps(): PayoutDeps {
  return {
    randomUuid: () => crypto.randomUUID(),
    now: () => Date.now(),
    async getJob(id: string): Promise<JobRecordPayout | null> {
      const snap = await db.collection("jobs").doc(id).get();
      if (!snap.exists) return null;
      return (snap.data() || {}) as JobRecordPayout;
    },
    async findPendingPayout(jobId: string, clientUid: string): Promise<PayoutRecord | null> {
      const snap = await db.collection("payouts")
        .where("jobId", "==", jobId)
        .where("clientUid", "==", clientUid)
        .limit(1)
        .get();
      if (snap.empty) return null;
      const data = snap.docs[0].data();
      return {
        jobId: data.jobId,
        workerId: data.workerId,
        workerMomoNumber: data.workerMomoNumber,
        amount: data.amount,
        currency: data.currency,
        provider: data.provider,
        status: data.status as PayoutStatus,
        payoutId: data.payoutId,
        clientUid: data.clientUid,
        releasedBy: data.releasedBy,
        transactionId: data.transactionId,
        providerReferenceId: data.providerReferenceId,
      };
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
      await db.collection("payouts").doc(record.payoutId).set({
        ...record,
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
export const createMoMoPayout = functions.https.onCall(async (data, context) => {
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
export const createMoMoMaterialAdvance = functions.https.onCall(
  async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Authentication required to disburse material advance."
      );
    }

    const {
      jobId,
      materialsSubtotal,
      workerId,
      workerMomoNumber,
      provider,
    } = data;

    if (
      !jobId ||
      materialsSubtotal === undefined ||
      workerId === undefined ||
      workerMomoNumber === undefined
    ) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Missing required parameters: jobId, materialsSubtotal, workerId, workerMomoNumber."
      );
    }

    // Fail closed: materialsSubtotal must be positive, never negative or zero.
    if (!(typeof materialsSubtotal === "number" && materialsSubtotal > 0)) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "materialsSubtotal must be a positive number."
      );
    }

    // Advance is capped at 50% of the materials subtotal.
    const advanceAmount = Math.min(materialsSubtotal * 0.5, materialsSubtotal);

    // Record the receipt-capture requirement in Firestore BEFORE issuing the
    // payout. The doc is the idempotency key: a second call returns ALREADY_PENDING.
    const advanceRef = db.collection("materialAdvances").doc(jobId);
    const existing = await advanceRef.get();
    if (existing.exists) {
      const existingData = existing.data();
      if (existingData?.status === "PENDING") {
        return {
          success: false,
          error: "already-pending",
          message:
            "A material advance for this job is already pending. Await its status.",
        };
      }
      // If a previous attempt exists, start a new one only if the amount changed.
      // Refuse to overwrite an already-disbursed advance.
      if (existingData?.status === "DISBURSED") {
        throw new functions.https.HttpsError(
          "failed-precondition",
          "This material advance was already disbursed and cannot be re-run."
        );
      }
    }

    await advanceRef.set({
      jobId,
      workerId,
      workerMomoNumber,
      materialsSubtotal,
      advanceAmount,
      provider: provider || "MTN_MOMO",
      status: "PENDING",
      receiptCaptureExpiresAt: admin.firestore.FieldValue.serverTimestamp(),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    // Issue the partial payout through the PAY-003 trust boundary.
    const payoutResult = await initiatePayout(
      {
        jobId,
        clientUid: context.auth.uid,
        amountUgx: Math.round(advanceAmount),
        workerMomoNumber,
        provider: provider || "MTN_MOMO",
        env: process.env as Record<string, string | undefined>,
      },
      buildPayoutDeps()
    );

    if (!payoutResult.ok) {
      await advanceRef.update({
        status: "FAILED",
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      });

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
        already_pending: "failed-precondition",
        provider_error: "unavailable",
        provider_timeout: "deadline-exceeded",
      };

      const httpsCode =
        codeMap[payoutResult.error.code] || "internal";
      throw new functions.https.HttpsError(
        httpsCode as any,
        payoutResult.error.message
      );
    }

    // Payout accepted (PENDING). Notify the worker.
    const payoutRef = payoutResult.payoutId;
    await db.collection("notifications").add({
      userId: workerId,
      type: "PAYOUT_PENDING",
      jobId,
      message: `A material advance of UGX ${advanceAmount} has been initiated. Please upload the hardware receipt within 60 minutes so the payout can be completed.`,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      read: false,
    });

    // Update the local tracking document with the payout reference.
    await advanceRef.update({
      payoutRef,
      providerReferenceId: payoutResult.providerReferenceId,
      status: "PENDING",
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return {
      success: true,
      payoutRef,
      advanceAmount: Math.round(advanceAmount),
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
export const getMoMoPayoutStatus = functions.https.onCall(async (data, context) => {
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

  const deposit = await db.collection("payouts").doc(payoutRef).get();
  if (!deposit.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Payout not found."
    );
  }
  const ledger = deposit.data() as PayoutRecord;
  const callerUid = context.auth.uid;

  // Authorize: the caller must be the stored clientUid or the job's owner.
  const jobDoc = await db.collection("jobs").doc(ledger.jobId).get();
  const ownerUid = jobDoc.exists ? (jobDoc.data() as any)?.postedByUid : undefined;
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
    console.error("Africa's Talking OTP request failed", (error as Error).message);
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
export const registerPushToken = functions.https.onCall(async (data, context) => {
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

export const sendOtp = functions.runWith({ secrets: AFRICA_TALKING_SECRETS }).https.onCall(async (data, context) => {
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

export const verifyOtp = functions.runWith({ secrets: AFRICA_TALKING_SECRETS }).https.onCall(async (data) => {
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

export const transcribeVoiceJob = functions.runWith({ secrets: GEMINI_SECRETS }).https.onCall(async (data, context) => {
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
    console.error("Gemini transcription request failed", (error as Error).message);
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
    console.error("Gemini transcription response invalid", (error as Error).message);
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
  .runWith({ secrets: GEMINI_SECRETS })
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

export const recordConsent = functions.https.onCall(async (data, context) => {
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

  return { version: record.version, acceptedAt: record.acceptedAt };
});

export const exportMyData = functions.https.onCall(async (_data, context) => {
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
    guarantorSubmission: submissions.exists ? submissions.data() : null,
    // Third-party identifiers are stripped here, not in the client.
    guarantors: guarantors.docs.map((doc) => sanitiseGuarantorForExport(doc.data() as Record<string, unknown>)),
    consents: (
      await db.collection("users").doc(uid).collection("consents").limit(50).get()
    ).docs.map((doc) => ({ version: doc.id, ...(doc.data() as Record<string, unknown>) })),
  };

  return buildDsarEnvelope(data, Date.now());
});

// =====================================================================================
// REM-023: server-verified operator access.
// =====================================================================================

export const getAdminAccess = functions.https.onCall(async (_data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in to check operator access");
  }
  // Deliberately returns only the boolean: the client needs a gate, not a claims dump.
  return { admin: hasAdminClaim(context.auth.token) };
});

// =====================================================================================
// REM-024: session revocation.
// =====================================================================================

export const revokeMyTokens = functions.https.onCall(async (data, context) => {
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
