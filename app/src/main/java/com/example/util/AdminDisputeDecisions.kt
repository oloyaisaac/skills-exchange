package com.example.util

/**
 * AUDIT-A-ARBITRATE: Pure validation for the admin dispute arbitration console UI.
 *
 * Free of Android/Firebase dependencies so every rule is unit-testable on the JVM.
 *
 * The sheet is a viewer with client-side guardrails — the `resolveDispute` callable +
 * `firestore.rules` remain the real authorizer via the server-side `admin` custom claim.
 * These guards only prevent malformed taps from leaving the device.
 */
object AdminDisputeDecisions {

    /** Server allow-list mirrored from `functions/src/index.ts` resolveDispute. */
    val VALID_RESOLUTIONS = setOf("RESOLVED", "REJECTED", "ESCALATED")

    /** Resolution types mirrored from `functions/src/index.ts` resolveDispute. */
    val VALID_RESOLUTION_TYPES = setOf("REFUND_FULL", "REFUND_PARTIAL", "NO_ACTION", "WORKER_SUSPENDED")

    /** Dispute statuses that are terminal and non-actionable. */
    val TERMINAL_STATUSES = setOf("RESOLVED", "REJECTED")

    /**
     * Returns an error message, or null when the arbitration decision attempt may leave the device.
     *
     * Rules:
     * - disputeId must be a real dispute id (never blank);
     * - resolution must be in [VALID_RESOLUTIONS];
     * - resolutionType, when present, must be in [VALID_RESOLUTION_TYPES];
     * - REFUND_PARTIAL requires a positive finite amount;
     * - REFUND_FULL requires a positive finite amount;
     * - NO_ACTION and WORKER_SUSPENDED must not carry a refund amount;
     * - already-terminal disputes cannot be submitted for another resolution.
     */
    fun decisionBlocker(
        disputeId: String,
        resolution: String,
        resolutionType: String?,
        resolutionAmount: Double?,
        status: String?,
        adminNotes: String?
    ): String? {
        if (disputeId.isBlank()) return "Missing dispute id. Refresh the queue and try again."

        val normalizedResolution = resolution.trim().uppercase()
        if (normalizedResolution !in VALID_RESOLUTIONS) {
            return "Unknown resolution. Use Resolved, Rejected, or Escalated."
        }

        // Terminal disputes cannot be re-resolved.
        val normalizedStatus = status?.trim()?.uppercase()
        if (normalizedStatus != null && normalizedStatus in TERMINAL_STATUSES) {
            return "This dispute is already $normalizedStatus and cannot be re-resolved."
        }

        // Validate resolution type when provided.
        val normalizedType = resolutionType?.trim()?.uppercase()
        if (normalizedType != null && normalizedType.isNotEmpty() && normalizedType !in VALID_RESOLUTION_TYPES) {
            return "Unknown resolution type. Use Full Refund, Partial Refund, No Action, or Worker Suspended."
        }

        val amount = resolutionAmount
        val hasAmount = amount != null && amount.isFinite() && amount > 0

        when (normalizedType) {
            "REFUND_FULL" -> {
                if (!hasAmount) return "Full refund requires a positive amount."
            }
            "REFUND_PARTIAL" -> {
                if (!hasAmount) return "Partial refund requires a positive amount."
            }
            "NO_ACTION", "WORKER_SUSPENDED", null -> {
                if (amount != null && amount > 0) {
                    return "Refund amount not applicable to $normalizedType."
                }
            }
            else -> {
                // No type provided is allowed (defaults to empty on server).
            }
        }

        return null
    }
}
