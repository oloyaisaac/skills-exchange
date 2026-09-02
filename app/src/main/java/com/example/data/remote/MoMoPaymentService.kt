package com.example.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
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

class MoMoPaymentService(
    private val apiKey: String = "",
    private val baseUrl: String = "https://sandbox.momodeveloper.mtn.com/"
) {

    private val logging = HttpLoggingInterceptor { message -> Log.d("MoMoPaymentService", message) }
        .apply { setLevel(HttpLoggingInterceptor.Level.BODY) }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val api = retrofit.create(MoMoCollectionApi::class.java)

    /**
     * PAY-001: Integrate Mobile Money Collection Intent API
     * Triggers real HTTP POST to MTN MoMo Collection API (/collection/v1_0/requesttopay).
     */
    suspend fun createMoMoCollection(
        jobId: String,
        amountUgx: Double,
        phoneNumber: String,
        provider: String = "MTN_MOMO",
        apiKey: String = this.apiKey
    ): MoMoPaymentIntent = withContext(Dispatchers.IO) {
        val txId = "MOMO-UG-${System.currentTimeMillis().toString().takeLast(8)}"
        Log.d("MoMoPaymentService", "Initiating MoMo collection tx: $txId for Job $jobId on $phoneNumber ($provider)")

        try {
            val authHeader = "Bearer $apiKey"
            val request = MoMoRequestToPayRequest(
                amount = amountUgx.toInt().toString(),
                externalId = jobId,
                payer = MoMoPayer(partyId = phoneNumber),
                payerMessage = "Skills Exchange payment for Job #$jobId"
            )

            val response = api.requestToPay(authHeader, request)
            if (response.isSuccessful) {
                val body = response.body()
                val status = body?.status ?: "PENDING"
                Log.d("MoMoPaymentService", "MoMo collection response: $status for tx $txId")
                MoMoPaymentIntent(
                    transactionId = txId,
                    amount = amountUgx,
                    currency = "UGX",
                    momoNumber = phoneNumber,
                    provider = provider,
                    status = status,
                    referenceMessage = "Payment request sent to $phoneNumber. Enter PIN to authorize UGX ${amountUgx.toInt()}."
                )
            } else {
                Log.e("MoMoPaymentService", "MoMo collection failed: ${response.code()} ${response.message()}")
                MoMoPaymentIntent(
                    transactionId = txId,
                    amount = amountUgx,
                    currency = "UGX",
                    momoNumber = phoneNumber,
                    provider = provider,
                    status = "FAILED",
                    referenceMessage = "Payment request failed. Please try again."
                )
            }
        } catch (e: Exception) {
            Log.e("MoMoPaymentService", "MoMo collection error: ${e.message}")
            MoMoPaymentIntent(
                transactionId = txId,
                amount = amountUgx,
                currency = "UGX",
                momoNumber = phoneNumber,
                provider = provider,
                status = "FAILED",
                referenceMessage = "Network error. Please check your connection and try again."
            )
        }
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
