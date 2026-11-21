package com.example.util

/**
 * AUDIT-W-QUOTE-UI: Pure validation for the worker quote proposal sheet.
 *
 * Free of Android/Firebase dependencies (mirrors [ProfileValidators] /
 * [GuarantorValidators] / [AdminQueueDecisions]) so every rule is unit-testable
 * on the JVM.
 *
 * These guards only shape the form UX — the secured
 * `SkillsExchangeRepository.submitQuote` boundary stays the real authorizer
 * (authoritative UID, finite rate/hours > 0, POSTED/QUOTING-only, self-quote
 * block, per-worker pending-duplicate block). The UI must surface backend
 * rejections even when local validation passed.
 */
object QuoteProposalValidators {

    /** Maximum notes length accepted by the sheet (display affordance only). */
    const val MAX_NOTES_LENGTH = 500

    /**
     * Parses a worker-typed amount. Returns null for blank, non-numeric, NaN,
     * or infinite input. Commas and surrounding whitespace are tolerated
     * ("15,000" -> 15000.0).
     */
    fun parsePositiveAmount(raw: String): Double? {
        val normalized = raw.trim().replace(",", "")
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return value
    }

    /** Returns an error message, or null when the proposed rate may be submitted. */
    fun validateRate(raw: String): String? {
        if (raw.isBlank()) return "Enter your proposed rate."
        val value = parsePositiveAmount(raw)
            ?: return "Enter a valid rate (numbers only, e.g. 15000)."
        if (value <= 0.0) return "Rate must be greater than zero."
        return null
    }

    /** Returns an error message, or null when the estimated hours may be submitted. */
    fun validateHours(raw: String): String? {
        if (raw.isBlank()) return "Enter your estimated hours."
        val value = parsePositiveAmount(raw)
            ?: return "Enter valid hours (numbers only, e.g. 3)."
        if (value <= 0.0) return "Hours must be greater than zero."
        return null
    }

    /** Returns an error message, or null when the notes field is acceptable. */
    fun validateNotes(notes: String): String? {
        if (notes.length > MAX_NOTES_LENGTH) {
            return "Keep notes under $MAX_NOTES_LENGTH characters."
        }
        return null
    }

    /**
     * Returns an error message, or null when the quote form may be submitted.
     * Notes are optional.
     */
    fun quoteFormBlocker(rateRaw: String, hoursRaw: String, notes: String): String? {
        validateRate(rateRaw)?.let { return it }
        validateHours(hoursRaw)?.let { return it }
        validateNotes(notes)?.let { return it }
        return null
    }

    /**
     * Safe UI-only total (rate x hours). Returns null when either side is not a
     * valid positive finite number, so the sheet never shows a misleading total.
     */
    fun safeTotal(rateRaw: String, hoursRaw: String): Double? {
        val rate = parsePositiveAmount(rateRaw) ?: return null
        val hours = parsePositiveAmount(hoursRaw) ?: return null
        if (rate <= 0.0 || hours <= 0.0) return null
        val total = rate * hours
        if (!total.isFinite()) return null
        return total
    }

    /** Formats a UGX total for display. Null input renders a placeholder. */
    fun formatUgxTotal(total: Double?): String {
        if (total == null) return "UGX —"
        val rounded = total.toLong()
        val grouped = "%,d".format(rounded)
        return "UGX $grouped"
    }

    /**
     * Maps a backend [com.example.data.repository.SkillsExchangeRepository.QuoteSubmissionResult]
     * to a worker-readable message. Every branch is covered so the sheet never
     * shows a raw exception or a blank error.
     */
    fun submissionResultMessage(resultName: String): String {
        return when (resultName) {
            "Success" -> "Quote submitted successfully."
            "NotAuthenticated" -> "Sign in to submit a quote, then try again."
            "InvalidRate" -> "Enter a valid rate greater than zero."
            "InvalidHours" -> "Enter valid hours greater than zero."
            "JobNotFound" -> "This job could not be found. It may have been removed."
            "InvalidJobState" -> "This job is no longer accepting quotes."
            "SelfQuoteForbidden" -> "You cannot quote your own job."
            "DuplicatePendingQuote" -> "You already have a pending quote for this job."
            else -> "Quote submission failed. Please try again."
        }
    }
}
