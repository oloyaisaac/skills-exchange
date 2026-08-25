package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

class MoMoPaymentService {

    /**
     * Module 3: Mobile Money Collection (MTN MoMo & Airtel Money Integration Abstraction)
     * Triggers STK Push payment intent to resident's phone when quote is accepted.
     */
    suspend fun createMoMoCollection(
        jobId: String,
        amountUgx: Double,
        phoneNumber: String,
        provider: String = "MTN_MOMO"
    ): MoMoPaymentIntent = withContext(Dispatchers.IO) {
        val txId = "MOMO-UG-${System.currentTimeMillis().toString().takeLast(8)}"
        Log.d("MoMoPaymentService", "Initiating MoMo collection tx: $txId for Job $jobId on $phoneNumber ($provider)")

        // Simulate network API request payload to MTN MoMo / Airtel Money Collection API
        delay(600)

        MoMoPaymentIntent(
            transactionId = txId,
            amount = amountUgx,
            currency = "UGX",
            momoNumber = phoneNumber,
            provider = provider,
            status = "PENDING",
            referenceMessage = "Enter PIN on your $provider handset to authorize payment of UGX ${amountUgx.toInt()} for Job #$jobId."
        )
    }

    /**
     * Polls or receives asynchronous payment confirmation webhook result.
     */
    suspend fun checkCollectionStatus(transactionId: String): String = withContext(Dispatchers.IO) {
        delay(400)
        Log.d("MoMoPaymentService", "MoMo Status poll for $transactionId: SUCCESSFUL")
        "SUCCESSFUL"
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
        delay(500)
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
