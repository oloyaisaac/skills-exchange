package com.example.data.remote

/**
 * R10: the outcomes of the trust & safety calls, as the server reported them.
 *
 * Each one distinguishes "the server accepted it" from "the server refused it" from "we never reached
 * the server". The three are not interchangeable: the app may only claim a report was filed, a rating
 * recorded or a guarantor submission accepted when the server said so.
 */
sealed interface GuarantorSubmissionOutcome {
    /** The server wrote the records. [flags] are review signals, not errors (e.g. resubmission). */
    data class Filed(val status: String, val flags: List<String>) : GuarantorSubmissionOutcome

    /** The server refused it, with a sentence written for the person — often naming the duplicate. */
    data class Rejected(val message: String) : GuarantorSubmissionOutcome
}

sealed interface ReportSubmissionOutcome {
    /** Filed. [message] is the server's acknowledgement, including the response target. */
    data class Filed(val reportId: String, val priority: String, val message: String) : ReportSubmissionOutcome

    data class Rejected(val message: String) : ReportSubmissionOutcome
}

sealed interface RatingSubmissionOutcome {
    /** Recorded by the server; [subjectAverage] is the newly derived reputation average (0 if none). */
    data class Recorded(val subjectAverage: Double) : RatingSubmissionOutcome

    data class Rejected(val message: String) : RatingSubmissionOutcome
}

/**
 * R10-P2-02: the outcome of a triage decision on the safety desk.
 *
 * Distinct from [ReportSubmissionOutcome]: this is the operator side, and the server's sentence is
 * shown to the operator instead of a generic "saved".
 */
sealed interface ReportResolutionOutcome {
    data class Recorded(val message: String) : ReportResolutionOutcome
    data class Rejected(val message: String) : ReportResolutionOutcome
}

/**
 * R10-P2-03: what the platform can actually say about an emergency alert.
 *
 * `state` mirrors `functions/src/sosStatus.ts`; [message] is composed on the server and shown
 * verbatim, and [adviseDirectCall] means the person must be told to call emergency services
 * themselves because the platform has not confirmed that anyone was reached.
 */
data class SosDispatchInfo(
    val state: String,
    val message: String,
    val chatMessage: String,
    val adviseDirectCall: Boolean
) {
    /** True while the outcome is still unknown, so the app keeps asking. */
    val isPending: Boolean
        get() = state == "PENDING" || state == "NOT_DISPATCHED"
}

/**
 * R10-P2-02: the report categories the server accepts.
 *
 * Kept in step with `REPORT_CATEGORIES` in `functions/src/reports.ts` by
 * `scripts/check_safety_categories.mjs` — a category the app offers that the server rejects is a
 * dead end for the reporter.
 */
object ReportCategories {
    const val SAFETY = "SAFETY"
    const val FRAUD = "FRAUD"
    const val HARASSMENT = "HARASSMENT"
    const val NO_SHOW = "NO_SHOW"
    const val PAYMENT_ABUSE = "PAYMENT_ABUSE"
    const val OTHER = "OTHER"

    /** Ordered for the picker: the urgent ones first. */
    val ALL = listOf(SAFETY, FRAUD, HARASSMENT, NO_SHOW, PAYMENT_ABUSE, OTHER)

    /** A free-text reason is accepted only when it names a real category; anything else is OTHER. */
    fun normalise(raw: String?): String {
        val upper = raw?.trim()?.uppercase().orEmpty()
        return if (upper in ALL) upper else OTHER
    }

    /**
     * The decisions the safety desk may record — mirrors the statuses `decideTriage` accepts in
     * `functions/src/reports.ts` (OPEN is not a decision: triage must move a report forward).
     */
    val DECISIONS = listOf("TRIAGING", "ACTIONED", "DISMISSED")

    /** ACTIONED and DISMISSED close a report and need a written outcome an auditor can read. */
    fun closesReport(decision: String): Boolean = decision == "ACTIONED" || decision == "DISMISSED"

    fun isKnownDecision(raw: String?): Boolean = raw?.trim()?.uppercase() in DECISIONS

    fun label(category: String): String = when (category) {
        SAFETY -> "Safety or threats"
        FRAUD -> "Fraud or scam"
        HARASSMENT -> "Harassment"
        NO_SHOW -> "Did not show up"
        PAYMENT_ABUSE -> "Payment abuse"
        else -> "Something else"
    }
}
