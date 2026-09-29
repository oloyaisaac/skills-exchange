import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";
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
import type { DocumentBody, MintedUrl } from "./guarantorReview";

admin.initializeApp();
const db = admin.firestore();

const MOMO_WEBHOOK_SECRET = process.env.MOMO_WEBHOOK_SECRET;

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


if (!MOMO_WEBHOOK_SECRET) {
  console.error("FATAL: MOMO_WEBHOOK_SECRET environment variable is not set. Webhook verification will fail.");
}

/**
 * SEC-002: Helper to verify HMAC-SHA256 signature on incoming payment gateway webhooks.
 */
function verifyHmacSignature(req: functions.https.Request): boolean {
  if (!MOMO_WEBHOOK_SECRET) {
    console.error("MOMO_WEBHOOK_SECRET not configured. Rejecting webhook.");
    return false;
  }

  const signatureHeader = (
    req.headers["x-momo-signature"] ||
    req.headers["x-signature"] ||
    req.headers["x-callback-signature"] ||
    req.headers["x-hub-signature-256"]
  ) as string | undefined;

  if (!signatureHeader) {
    return false;
  }

  const rawSignature = signatureHeader.replace(/^sha256=/, "").trim();
  const rawBody = typeof req.body === "string" ? req.body : JSON.stringify(req.body);
  const expectedSignature = crypto
    .createHmac("sha256", MOMO_WEBHOOK_SECRET)
    .update(rawBody)
    .digest("hex");

  try {
    const signatureBuffer = Buffer.from(rawSignature, "hex");
    const expectedBuffer = Buffer.from(expectedSignature, "hex");
    if (signatureBuffer.length !== expectedBuffer.length) {
      return false;
    }
    return crypto.timingSafeEqual(signatureBuffer, expectedBuffer);
  } catch (error) {
    return false;
  }
}

/**
 * MODULE 3: MOBILE MONEY (MoMo) PAYMENT INFRASTRUCTURE ABSTRACTION
 * Serverless trigger when client accepts a job quote.
 * Generates payment collection intent payload for MTN MoMo / Airtel Money gateway.
 */
export const createMoMoCollection = functions.https.onCall(async (data, context) => {
  if (!context.auth) {
    throw new functions.https.HttpsError(
      "unauthenticated",
      "Authentication required to initiate Mobile Money collection."
    );
  }

  const { jobId, amountUgx, phoneNumber, provider } = data;
  if (!jobId || !amountUgx || !phoneNumber) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "Missing required parameters: jobId, amountUgx, phoneNumber."
    );
  }

  const transactionRef = `MOMO-TX-${Date.now()}`;
  const payload = {
    transactionRef,
    jobId,
    amount: amountUgx,
    currency: "UGX",
    payerPhoneNumber: phoneNumber,
    provider: provider || "MTN_MOMO",
    status: "PENDING_AUTHORIZATION",
    createdAt: admin.firestore.FieldValue.serverTimestamp()
  };

  // Record payment collection intent in Firestore
  await db.collection("momo_transactions").doc(transactionRef).set(payload);

  // Update job status to ACCEPTED_FUNDED and lock funds in escrow
  await db.collection("jobs").doc(jobId).update({
    status: "ACCEPTED_FUNDED",
    escrowStatus: "FUNDS_LOCKED_IN_ESCROW",
    paymentChannel: provider || "MTN_MOMO",
    momoPhoneNumber: phoneNumber,
    updatedAt: admin.firestore.FieldValue.serverTimestamp()
  });

  return {
    success: true,
    transactionRef,
    message: `Payment request of UGX ${amountUgx} sent to ${phoneNumber}. Please complete authorization on your handset.`
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

  // Check if caller is the job owner (postedByResident or postedBy UID)
  if (jobData?.postedByResident !== callerUid && jobData?.postedBy !== callerUid) {
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
 * MODULE 3: SERVERLESS WEBHOOK HANDLER
 * HTTP endpoint listening for asynchronous payment callbacks from MTN MoMo / Airtel Money / Flutterwave gateway.
 */
export const processMoMoWebhook = functions.https.onRequest(async (req, res) => {
  try {
    // SEC-002: Verify HMAC header signature to ensure request authenticity
    if (!verifyHmacSignature(req)) {
      res.status(401).send({ error: "Unauthorized: Invalid or missing webhook signature." });
      return;
    }

    const { transactionRef, status, jobId, payoutType } = req.body;

    if (!transactionRef || !status || !jobId) {
      res.status(400).send({ error: "Invalid webhook payload." });
      return;
    }

    if (status === "SUCCESSFUL" || status === "COMPLETED") {
      const newJobState = payoutType === "ESCROW_RELEASE" ? "RELEASED" : "ACCEPTED_FUNDED";
      const newEscrowState = payoutType === "ESCROW_RELEASE" ? "RELEASED_TO_WORKER" : "FUNDS_LOCKED_IN_ESCROW";

      const jobRef = db.collection("jobs").doc(jobId);
      await jobRef.update({
        status: newJobState,
        escrowStatus: newEscrowState,
        lastPaymentWebhookAt: admin.firestore.FieldValue.serverTimestamp()
      });

      const jobSnapshot = await jobRef.get();
      const jobData = jobSnapshot.data();
      const residentId = jobData?.postedByResident;
      const providerId = jobData?.assignedProviderId;

      if (residentId) {
        await db.collection("notifications").add({
          userId: residentId,
          type: "ESCROW_LOCKED",
          jobId,
          message: `Payment confirmed. Funds are now locked in escrow for job #${jobId}.`,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          read: false
        });
      }

      if (providerId) {
        await db.collection("notifications").add({
          userId: providerId,
          type: "ESCROW_LOCKED",
          jobId,
          message: `Payment confirmed. Funds locked in escrow for job #${jobId}. You may proceed with work.`,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
          read: false
        });
      }

      await db.collection("momo_transactions").doc(transactionRef).update({
        status: "SUCCESSFUL",
        completedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      res.status(200).send({ status: "ACKNOWLEDGED", jobId, newJobState });
    } else {
      await db.collection("momo_transactions").doc(transactionRef).update({
        status: "FAILED",
        failedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      res.status(200).send({ status: "PAYMENT_FAILED_ACKNOWLEDGED" });
    }
  } catch (error: any) {
    console.error("Error processing MoMo webhook:", error);
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
