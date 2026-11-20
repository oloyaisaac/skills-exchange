package com.example.data.remote

/**
 * R07-P2-03: what a client may still send when it mirrors a job into Firestore.
 *
 * The rules now pin the fields that represent money that has moved. A client that kept sending them
 * would have its *whole document* write rejected — including the harmless fields next to them — so the
 * payload is trimmed here instead of failing at the server. The client never writes the doorstep PIN
 * at all: it lives in `job_secrets/{jobId}` as a salted hash.
 *
 * Two classes of field are withheld:
 *
 *  * terminal escrow outcomes (`RELEASED_TO_WORKER`, `REFUND_*`, `CANCELLED`), which only the Admin
 *    SDK functions may write (`releaseEscrowToWorker`, `refundEscrow`, the MoMo webhook);
 *  * priced amounts once the job is past the point where they may change, mirroring `jobMoneyPinned`.
 *
 * The payload is merged (`SetOptions.merge()`), so withholding a field leaves the server's value
 * intact instead of deleting it.
 */
object JobSyncPolicy {

    /** Escrow states a client may never author. */
    val SERVER_ONLY_ESCROW_STATUSES: Set<String> = setOf(
        "RELEASED_TO_WORKER",
        "REFUND_PENDING",
        "REFUNDED",
        "CANCELLED"
    )

    /** Job statuses during which the owner may still change what the job costs. */
    private val PRICE_EDITABLE_STATUSES = setOf("REQUESTED", "QUOTED")

    private val MONEY_FIELDS = setOf("grandTotal", "budget", "platformFee", "laborTotal", "materialsSubtotal")

    /**
     * Fields withheld from a client write, given the local job state.
     *
     * Returned as a set so a test can assert the policy without building an entire job document.
     */
    fun withheldFields(status: String, escrowStatus: String): Set<String> {
        val withheld = mutableSetOf<String>()
        if (escrowStatus in SERVER_ONLY_ESCROW_STATUSES) withheld += "escrowStatus"
        if (status !in PRICE_EDITABLE_STATUSES) withheld += MONEY_FIELDS
        return withheld
    }

    /** The payload actually sent: the same map, minus whatever the server owns at this point. */
    fun clientPayload(
        fields: Map<String, Any?>,
        status: String,
        escrowStatus: String
    ): Map<String, Any?> {
        val withheld = withheldFields(status, escrowStatus)
        return fields.filterKeys { key -> key !in withheld }
    }
}
