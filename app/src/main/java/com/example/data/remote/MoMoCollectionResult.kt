package com.example.data.remote

/**
 * PAY-001 (AUDIT-PAY Phase 2): result of the trusted `createMoMoCollection` callable.
 *
 * `success=true` means the backend delivered a RequestToPay to the payer's handset
 * (MTN HTTP 202 → PENDING) — it does NOT mean money moved and it does NOT mean escrow
 * is locked. Any failure carries an [errorCode] and means nothing was collected.
 */
data class MoMoCollectionResult(
    val success: Boolean,
    val status: String?,
    val transactionRef: String?,
    val providerReferenceId: String?,
    val message: String?,
    val errorCode: String?
)
