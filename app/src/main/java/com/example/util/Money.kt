package com.example.util

/**
 * R04-P2-04: the one money representation.
 *
 * The ledger used to sum `Double` amounts, so a wallet balance was the result of floating-point
 * arithmetic: repeated credits and debits accumulate error (`0.1 + 0.2 != 0.3`), and two workers with
 * the same history could read different balances. Money is now stored and summed as **minor units**
 * (`Long`, 1 UGX = 100 minor units = one cent), which is exact, order-independent and auditable.
 *
 * Rules:
 *  - storage and every balance computation use [toMinorUnits] results and `Long` arithmetic;
 *  - `Double` only appears at the edges: user input (parsing an amount the worker typed) and display;
 *  - a non-finite or non-representable amount is rejected ([toMinorUnits] returns null) rather than
 *    silently becoming a balance.
 */
object Money {

    /** Minor units in one major unit (1 UGX = 100 cents). */
    const val MINOR_UNITS_PER_UNIT: Long = 100L

    /**
     * Converts a requested/displayed amount to minor units, rounding half away from zero.
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
