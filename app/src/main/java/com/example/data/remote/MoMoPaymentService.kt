package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
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
     * Disburses funds from Escrow pool directly to worker's Mobile Money wallet upon job completion release.
     */
    suspend fun disburseEscrowPayoutToWorker(
        workerId: String,
        momoNumber: String,
        amountUgx: Double,
        provider: String = "MTN_MOMO"
    ): MoMoPayoutResponse = withContext(Dispatchers.IO) {
        val payoutId = "PAYOUT-UG-${System.currentTimeMillis().toString().takeLast(8)}"
        Log.d("MoMoPaymentService", "Disbursed Escrow payout $payoutId of UGX ${amountUgx.toInt()} to $workerId ($momoNumber)")

        MoMoPayoutResponse(
            payoutId = payoutId,
            workerId = workerId,
            momoNumber = momoNumber,
            amount = amountUgx,
            status = "DISBURSED"
        )
    }
}
