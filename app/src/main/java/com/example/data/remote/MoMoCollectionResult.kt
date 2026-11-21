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

/**
 * PAY-002: result of the trusted `getMoMoCollectionStatus` callable.
 *
 * [status] is the backend's provider-verified state (PENDING / SUCCESSFUL / FAILED /
 * UNKNOWN / NEEDS_RECONCILIATION) — never a client guess. [escrowLocked] is true only
 * when THIS call caused (or previously caused) the backend to lock escrow for the
 * transaction. A non-null [errorCode] means the status could NOT be verified and the
 * caller must treat the payment as needing attention — never as received.
 */
data class MoMoCollectionStatusResult(
    val status: String?,
    val transactionRef: String?,
    val escrowLocked: Boolean,
    val message: String?,
    val errorCode: String?
)
