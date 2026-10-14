package com.example.data.remote

/**
 * PAY-001 (AUDIT-PAY Phase 2): device-side MTN gateway Retrofit interface — RETIRED.
 *
 * The device must never call the MTN gateway directly (trust boundary: gateway
 * credentials must never ship in the APK, and the client must never declare payment
 * success). All collection traffic now goes through the trusted `createMoMoCollection`
 * callable (see `FirebaseService.initiateMoMoCollection`), which performs the MTN
 * sandbox RequestToPay server-side with the backend as the authority.
 *
 * This file's types are retained only so the historical request/response vocabulary
 * stays documented for the server-side port in `functions/src/momoCollection.ts`
 * (`buildRequestToPayPayload`). Nothing in the app constructs or calls them.
 */

/** Historical device-side request shape (retired — see `buildRequestToPayPayload`). */
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

