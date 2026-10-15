package com.example.util

/**
 * AUDIT-A-CONSOLE: Pure validation for the admin verification queue UI.
 *
 * Free of Android/Firebase dependencies (mirrors [ProfileValidators] / [GuarantorValidators])
 * so every rule is unit-testable on the JVM.
 *
 * The sheet is a viewer only — the `reviewWorkerApplication` callable + `firestore.rules`
 * remain the real authorizer via the server-side `admin` custom claim. These guards only
 * prevent malformed taps from leaving the device.
 */
object AdminQueueDecisions {

    /** Server allow-list mirrored from `functions/src/index.ts` reviewWorkerApplication. */
    val VALID_DECISIONS = setOf("APPROVE", "REJECT", "CHANGES_REQUIRED")

    /**
     * Minimum feedback length for REJECT / CHANGES_REQUIRED, mirrored from
     * `functions/src/guarantorReview.ts` MIN_GUARANTOR_FEEDBACK_LENGTH.
     */
    const val MIN_FEEDBACK_LENGTH = 10

    /**
     * Returns an error message, or null when the decision attempt may leave the device.
     *
     * Rules:
     * - operator console must be explicitly unlocked (never derived from a client flag);
     * - userId must be a real applicant id (never blank);
     * - decision must be in [VALID_DECISIONS];
     * - REJECT / CHANGES_REQUIRED require written feedback ([MIN_FEEDBACK_LENGTH]+ chars).
     */
    fun decisionBlocker(
        isAdminUnlocked: Boolean,
        userId: String,
        decision: String,
        feedback: String
    ): String? {
        if (!isAdminUnlocked) return "Operator console is locked. Decisions are disabled."
        if (userId.isBlank()) return "Missing applicant id. Refresh the queue and try again."
        val normalized = decision.trim().uppercase()
        if (normalized !in VALID_DECISIONS) {
            return "Unknown decision. Use Approve, Changes Required, or Reject."
        }
        if ((normalized == "REJECT" || normalized == "CHANGES_REQUIRED") &&
            feedback.trim().length < MIN_FEEDBACK_LENGTH
        ) {
            return "Add feedback ($MIN_FEEDBACK_LENGTH+ characters) so the worker knows what to fix."
        }
        return null
    }
}
