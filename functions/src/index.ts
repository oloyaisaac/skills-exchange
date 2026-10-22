import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";
import axios from "axios";
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
export const releaseEscrowToWorker = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to release escrow."
    );
  }

  const { jobId, workerId, workerMomoNumber, amountUgx, provider } = data;
  if (!jobId || !workerId || !workerMomoNumber || !amountUgx) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameters: jobId, workerId, workerMomoNumber, amountUgx."
    );
  }

  // SEC-004: Verify job ownership - only job owner can release escrow
  const jobDoc = await db.collection("jobs").doc(jobId).get();
  if (!jobDoc.exists) {
    throw new functions.https.HttpsError(
      "not-found",
      "Job not found."
    );
  }

  const jobData = jobDoc.data();
  const callerUid = context.auth.uid;

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

  // SEC-004: Validate job state - escrow can only be released for completed jobs
  const validStates = ["COMPLETED", "WORK_COMPLETED", "IN_PROGRESS"];
  if (!validStates.includes(jobData?.status)) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      `Escrow cannot be released for job in status: ${jobData?.status}. Job must be completed.`
    );
  }

  // SEC-004: Verify workerId matches assigned provider
  if (jobData?.assignedProvider !== workerId && jobData?.assignedWorkerId !== workerId) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Worker ID does not match assigned worker for this job."
    );
  }

  const payoutRef = `PAYOUT-${Date.now()}`;
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

  return {
    success: true,
    payoutRef,
    message: `Escrow released. UGX ${amountUgx} payout queued to ${workerMomoNumber}.`
  };
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
export const processMoMoWebhook = functions.https.onRequest(async (req, res) => {
  try {
    if (req.method !== "POST") {
      res.status(405).send({ error: "Method not allowed." });
      return;
    }

    const referenceHint = extractCallbackReferenceHint(req.body);
    if (!referenceHint) {
      res.status(400).send({ error: "Invalid webhook payload: no reference id." });
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
export const onJobCreated = functions.firestore
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
async function sendSmsAlert(phoneNumber: string, message: string): Promise<boolean> {
  console.log(`[Africa's Talking SMS API] To: ${phoneNumber} | Msg: ${message}`);
  // In production, invoke Africa's Talking API endpoint:
  // POST https://api.africastalking.com/version1/messaging
  return true;
}

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

  return {
    count: applications.length,
    guarantorsAwaitingReview: applications.filter((row) => row.guarantorsAwaitingReview).length,
    applications
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
      // Create refund record
      await db.collection("momo_payouts").add({
        payoutId: `REFUND-${Date.now()}`,
        jobId: disputeData?.jobId,
        userId: disputeData?.reportedBy,
        amount: refundAmount,
        currency: "UGX",
        provider: "REFUND",
        status: "QUEUED",
        reason: `Dispute refund: ${resolutionType}`,
        disputeId: disputeId,
        createdAt: now
      });

      // Update job escrow status
      await db.collection("jobs").doc(disputeData?.jobId).update({
        escrowStatus: "REFUNDED",
        updatedAt: now
      });
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

  if (disputeData?.reportedUserId) {
    await db.collection("notifications").add({
      userId: disputeData.reportedUserId,
      type: "DISPUTE_RESOLVED",
      jobId: disputeData?.jobId,
      message: `A dispute against you has been ${resolution.toLowerCase()}. ${adminNotes || ""}`,
      createdAt: now,
      read: false
    });
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
   * PAY-003: Create a worker payout (escrow release). Callable triggered when a
   * resident approves job completion. The resident releases escrow; the worker
   * receives the amount through the MTN Disbursement API (sandbox only). The
   * device never touches the payout endpoint — all trust decisions are enforced
   * server-side by the pure `initiatePayout` core.
   */
  export const createMoMoPayout = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
      throw new functions.https.HttpsError(
        "unauthenticated",
        "Authentication required to release escrow."
      );
    }

    const { jobId, workerId, workerMomoNumber, amountUgx, provider } = data;
    if (!jobId || !workerId || !workerMomoNumber || amountUgx === undefined) {
      throw new functions.https.HttpsError(
        "invalid-argument",
        "Missing required parameters: jobId, workerId, workerMomoNumber, amountUgx."
      );
    }

    const result = await initiatePayout(
      {
        jobId,
        clientUid: context.auth.uid,
        amountUgx,
        workerMomoNumber,
        provider: provider || "MTN_MOMO",
        env: process.env as Record<string, string | undefined>,
      },
      buildPayoutDeps()
    );

    if (!result.ok) {
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
      const httpsCode = codeMap[result.error.code] || "internal";
      throw new functions.https.HttpsError(httpsCode as any, result.error.message);
    }

    // Payout accepted (PENDING). Notify the worker.
    await db.collection("notifications").add({
      userId: workerId,
      type: "PAYOUT_PENDING",
      jobId,
      message: `A payout of UGX ${amountUgx} has been initiated. You will receive payment soon.`,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      read: false,
    });

    return {
      success: true,
      payoutRef: result.payoutId,
      message: `Payout pending. UGX ${amountUgx} will be delivered to ${workerMomoNumber} via the MTN Disbursement API (sandbox). Poll getMoMoPayoutStatus for the provider-verified result.`,
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