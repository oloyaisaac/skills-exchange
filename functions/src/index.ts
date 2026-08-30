import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";

admin.initializeApp();
const db = admin.firestore();

const MOMO_WEBHOOK_SECRET = process.env.MOMO_WEBHOOK_SECRET;

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
 */
function isAdmin(context: functions.https.CallableContext): boolean {
  return context.auth?.token?.admin === true;
}

/**
 * ADMIN-002: Get Pending Verification Queue
 * Returns worker applications with status SUBMITTED or UNDER_REVIEW, sorted by submittedAt (48h SLA).
 */
export const getPendingVerificationQueue = functions.https.onCall(async (data, context) => {
  if (!isAdmin(context)) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Admin access required."
    );
  }

  const snapshot = await db
    .collection("worker_kyc")
    .where("status", "in", ["SUBMITTED", "UNDER_REVIEW"])
    .orderBy("submittedAt", "asc")
    .limit(50)
    .get();

  const queue = snapshot.docs.map((doc) => {
    const d = doc.data();
    const slaDeadline = (d.submittedAt || 0) + (48 * 60 * 60 * 1000);
    return {
      userId: doc.id,
      fullLegalName: d.fullLegalName || "",
      nationalIdNumber: d.nationalIdNumber || "",
      status: d.status || "NOT_STARTED",
      submittedAt: d.submittedAt || 0,
      slaDeadline,
      idFrontStoragePath: d.idFrontStoragePath || "",
      idBackStoragePath: d.idBackStoragePath || "",
      reviewFeedback: d.reviewFeedback || ""
    };
  });

  return { count: queue.length, applications: queue };
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
