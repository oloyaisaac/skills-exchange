package com.example.util

/**
 * Wallet ledger and withdrawal amounts are stored as [Long] hundredths of a UGX.
 * Local cashout balance calculation reads the ledger, not the legacy profile balance columns.
 * Server-side decisions and provider payloads have their own contracts; this helper does not
 * establish an end-to-end representation or prove all settlement flows use minor units.
 *
 * Room schemas v29 and v30 still store these amounts as REAL: job_posts budget, materialsCost, safetyFee,
 * laborRate, laborTotal, materialsSubtotal, platformFee, grandTotal, emergencyAddonAmount,
 * materialAdvanceAmount, calloutFee and recurringNegotiatedRate; job_quotes proposedRate and
 * totalQuote; disputes resolutionAmount; service_providers hourlyRate and walletBalanceDisplay; and
 * user_profiles walletBalanceDisplay. They are legacy fields, not necessarily editable quotes: for
 * example totalQuote is calculated by direct multiplication, and seeded jobs use constants.
 * The two walletBalanceDisplay columns (named walletBalance before migration 29→30) default to 185.0 and a DAO setter exists; they are not known to
 * be synchronized with the ledger. They must not be used for cashout authorization.
 *
 * The schema guard is a heuristic: it checks money-shaped column names and direct Kotlin
 * member access, not SQL, reflection, calculation provenance or all possible money flows.
 * See D-2026-11-20-15 in DECISIONS.md for limitations and proposed migration work.
 */
object Money {

    /** Minor units in one major unit (1 UGX = 100 cents). */
    const val MINOR_UNITS_PER_UNIT: Long = 100L

    /**
     * Converts a requested/displayed amount to minor units, rounding to nearest, with ties to even.
     * Returns null for NaN, ±Infinity or values outside the safe range, so a corrupt or hostile
     * amount can never enter the ledger.
     */
    fun toMinorUnits(amount: Double): Long? {
        if (amount.isNaN() || amount.isInfinite()) return null
        val scaled = amount * MINOR_UNITS_PER_UNIT
        if (scaled > MAX_MINOR_UNITS.toDouble() || scaled < MIN_MINOR_UNITS.toDouble()) return null
        return kotlin.math.round(scaled).toLong()
    }

    /** Display/interop conversion back to major units. Never used for arithmetic. */
    fun toMajorUnits(minorUnits: Long): Double =
        minorUnits.toDouble() / MINOR_UNITS_PER_UNIT.toDouble()

    /** `1234567` -> `"12,345.67"`. Deterministic, locale-independent. */
    fun format(minorUnits: Long): String {
        val negative = minorUnits < 0
        val absolute = kotlin.math.abs(minorUnits)
        val whole = absolute / MINOR_UNITS_PER_UNIT
        val cents = absolute % MINOR_UNITS_PER_UNIT
        val grouped = whole.toString().reversed().chunked(3).joinToString(",").reversed()
        val sign = if (negative) "-" else ""
        return sign + grouped + "." + cents.toString().padStart(2, '0')
    }

    /** `1234567` -> `"UGX 12,345.67"`. */
    fun formatUgx(minorUnits: Long): String = "UGX " + format(minorUnits)

    /** Guards the `Long` range so an absurd amount is rejected instead of overflowing. */
    private val MAX_MINOR_UNITS: Long = Long.MAX_VALUE / 2
    private val MIN_MINOR_UNITS: Long = Long.MIN_VALUE / 2
}
