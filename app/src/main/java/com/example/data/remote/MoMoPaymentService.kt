package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class MoMoPaymentIntent(
    val transactionId: String,
    val amount: Double,
    val currency: String = "UGX",
    val momoNumber: String,
    val provider: String, // "MTN_MOMO", "AIRTEL_MONEY"
    val status: String, // "PENDING", "SUCCESSFUL", "FAILED"
    val referenceMessage: String
)

data class MoMoPayoutResponse(
    val payoutId: String,
    val workerId: String,
    val momoNumber: String,
    val amount: Double,
    val status: String, // "QUEUED", "DISBURSED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * PAY-001 (AUDIT-PAY Phase 2): device-side MTN gateway calls are REMOVED.
 *
 * The previous implementation POSTed to the MTN sandbox RequestToPay endpoint directly
 * from the device with an empty API key and treated the attempt as funded. That path is
 * a trust-boundary violation: gateway credentials must never ship in the APK, and the
 * client must never declare payment success. All collection traffic now goes through the
 * trusted `createMoMoCollection` callable (see `FirebaseService.initiateMoMoCollection`),
 * which reads the trusted job document and performs the sandbox RequestToPay server-side.
 *
 * This class is retained only for the integer data types above and for the payout-status
 * vocabulary they carry. Any call below fails closed and must not be treated as money
 * movement.
 */
class MoMoPaymentService {

    /**
     * Removed trust-boundary violation. Previously POSTed to MTN from the device with an
     * empty key. Always throws — callers must use
     * `FirebaseService.initiateMoMoCollection` instead.
     */
    @Deprecated(
        "Trust-boundary violation: device must never call the MTN gateway. " +
            "Use FirebaseService.initiateMoMoCollection (createMoMoCollection callable)."
    )
    suspend fun createMoMoCollection(
        jobId: String,
        amountUgx: Double,
        phoneNumber: String,
        provider: String = "MTN_MOMO",
        apiKey: String = ""
    ): MoMoPaymentIntent = withContext(Dispatchers.IO) {
        Log.e(
            "MoMoPaymentService",
            "Blocked direct device gateway call for job $jobId. Route via createMoMoCollection callable."
        )
        throw UnsupportedOperationException(
            "Direct device MoMo collection is disabled (trust boundary). " +
                "Use FirebaseService.initiateMoMoCollection."
        )
    }

    /**
     * Polls or receives asynchronous payment confirmation webhook result.
     */
    suspend fun checkCollectionStatus(transactionId: String): String = withContext(Dispatchers.IO) {
        Log.d("MoMoPaymentService", "MoMo Status poll for $transactionId")
        "PENDING"
    }

    /**
     * REM-028: device-side payout creation is REMOVED. The previous body minted a synthetic
     * "PAYOUT-UG-..." id, logged a success and returned status DISBURSED without any network call —
     * a fabricated money movement. Payouts are initiated server-side (`createMoMoPayout` /
     * `releaseEscrowToWorker`) and only the provider may report that funds left escrow.
     */
    @Deprecated(
        "Device payout creation is disabled: use FirebaseService.releaseEscrowToWorker. " +
            "The provider, not the device, reports DISBURSED."
    )
    suspend fun disburseEscrowPayoutToWorker(
        workerId: String,
        momoNumber: String,
        amountUgx: Double,
        provider: String = "MTN_MOMO"
    ): MoMoPayoutResponse = withContext(Dispatchers.IO) {
        Log.e("MoMoPaymentService", "Blocked device-side payout for $workerId. Use the server callable.")
        throw UnsupportedOperationException(
            "Device-side payouts are disabled (trust boundary). Use releaseEscrowToWorker."
        )
    }

    /**
     * REM-028: read the REAL payout record the server wrote. Returns null when the payout does not
     * exist or belongs to another account (Firestore rules enforce the same ownership).
     */
    suspend fun getPayoutStatus(payoutId: String): MoMoPayoutResponse? = withContext(Dispatchers.IO) {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        try {
            val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("momo_payouts")
                .document(payoutId)
                .get()
                .await()
            if (!snapshot.exists()) return@withContext null
            val workerId = snapshot.getString("workerId") ?: return@withContext null
            if (workerId != uid) return@withContext null
            MoMoPayoutResponse(
                payoutId = payoutId,
                workerId = workerId,
                momoNumber = snapshot.getString("workerMomoNumber") ?: "",
                amount = snapshot.getDouble("amount") ?: 0.0,
                status = snapshot.getString("status") ?: "QUEUED",
                timestamp = snapshot.getLong("createdAt") ?: System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("MoMoPaymentService", "getPayoutStatus failed: ${e.message}")
            null
        }
    }
}
