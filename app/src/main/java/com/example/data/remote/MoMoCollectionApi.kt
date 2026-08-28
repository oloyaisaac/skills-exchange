package com.example.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface MoMoCollectionApi {
    @POST("collection/v1_0/requesttopay")
    suspend fun requestToPay(
        @Header("Authorization") authorization: String,
        @Body request: MoMoRequestToPayRequest
    ): Response<MoMoRequestToPayResponse>
}

data class MoMoRequestToPayRequest(
    val amount: String,
    val currency: String = "UGX",
    val externalId: String,
    val payer: MoMoPayer,
    val payerMessage: String = "Skills Exchange Payment",
    val payeeNote: String = "Payment for services"
)

data class MoMoPayer(
    val partyIdType: String = "MSISDN",
    val partyId: String
)

data class MoMoRequestToPayResponse(
    val amount: MoMoAmount,
    val currency: String,
    val externalId: String,
    val payer: MoMoPayer,
    val status: String,
    val transactionId: String?
)

data class MoMoAmount(
    val value: String,
    val currency: String
)
