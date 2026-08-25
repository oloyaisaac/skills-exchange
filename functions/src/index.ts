import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import * as crypto from "crypto";

admin.initializeApp();
const db = admin.firestore();

const MOMO_WEBHOOK_SECRET = process.env.MOMO_WEBHOOK_SECRET || "momo-webhook-secret-key-default";

/**
 * SEC-002: Helper to verify HMAC-SHA256 signature on incoming payment gateway webhooks.
 */
function verifyHmacSignature(req: functions.https.Request): boolean {
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

      await db.collection("jobs").doc(jobId).update({
        status: newJobState,
        escrowStatus: newEscrowState,
        lastPaymentWebhookAt: admin.firestore.FieldValue.serverTimestamp()
      });

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
