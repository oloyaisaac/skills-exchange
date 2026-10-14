package com.example.data.remote

/**
 * PAY-002: trusted payment-state vocabulary for the Android UI.
 *
 * The app NEVER derives these phases from local request success or from an HTTP 202.
 * They are produced exclusively by [mapBackendStatusToPhase] from the status the
 * TRUSTED backend reports (the `getMoMoCollectionStatus` callable, whose server side
 * re-queries the MTN provider status API). `NEEDS_ATTENTION` covers every state the
 * backend could not verify (`UNKNOWN`), every provider/backend disagreement
 * (`NEEDS_RECONCILIATION`) and any unexpected/absent value — fail closed, never
 * "successful".
 */
enum class MoMoPaymentPhase {
    /** Request delivered to the payer's handset; NOT yet confirmed; NOT in escrow. */
    PENDING,
    /** The provider itself confirmed the collection; backend locked escrow. */
    SUCCESSFUL,
    /** The provider reported a failure; nothing was collected. */
    FAILED,
    /** Status could not be verified / needs attention. Never treat as received. */
    NEEDS_ATTENTION,
}

data class MoMoPaymentUiState(
    val phase: MoMoPaymentPhase,
    val transactionRef: String?,
    val message: String
)

/**
 * Pure mapper from a backend-reported status string to the UI phase. Anything that is
 * not explicitly PENDING, SUCCESSFUL or FAILED — including `UNKNOWN`,
 * `NEEDS_RECONCILIATION`, null and unexpected garbage — degrades to
 * [MoMoPaymentPhase.NEEDS_ATTENTION], never to success.
 */
fun mapBackendStatusToPhase(status: String?): MoMoPaymentPhase = when (status?.trim()?.uppercase()) {
    "PENDING" -> MoMoPaymentPhase.PENDING
    "SUCCESSFUL" -> MoMoPaymentPhase.SUCCESSFUL
    "FAILED" -> MoMoPaymentPhase.FAILED
    else -> MoMoPaymentPhase.NEEDS_ATTENTION
}
