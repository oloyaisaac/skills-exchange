package com.example.util

/**
 * R04-P2-04 / VC-05: the money representation, stated precisely enough to be checked.
 *
 * An earlier version of this comment said "the one money representation" and then described storage as
 * minor units, while the exported schema still held eighteen REAL money columns and two REAL balances.
 * A rule that the schema contradicts is worse than no rule, so the rule is now split by **role**, and
 * `scripts/check_money_representation.mjs` fails the build when the schema stops agreeing with it.
 *
 * ## Settled money — `Long` minor units, always
 *
 * Money that is summed into a balance, reserved, withdrawn, refunded or paid out:
 * `wallet_ledger.amountMinor`, `wallet_withdrawals.amountMinor`, and anything derived from them.
 * Storage, arithmetic and comparisons are `Long`; `Double` never touches a balance. This is the only
 * representation a payout decision may use.
 *
 * ## Quoted money — `REAL`, produced only by [com.example.util.JobMoney.split]
 *
 * A price while the user is still editing it: job budgets, labour/materials/fee components, quotes,
 * a dispute's resolution figure, a provider's public hourly rate. These are stored as `REAL` because
 * they round-trip through provider payloads and Firestore as numbers, and because the rounding rule
 * ("whole shillings, each component rounded once, total = sum of rounded parts") is owned by
 * `JobMoney.split`, not by this object.
 *
 * The list is explicit: `QUOTED_MONEY` in the guard names every one of these columns with the reason,
 * and a new money-shaped `REAL` column fails the build until someone classifies it deliberately.
 *
 * ## Display mirrors — `REAL`, and provably unread
 *
 * `user_profiles.walletBalance` and `service_providers.walletBalance` are leftovers that mirror the
 * ledger. They are not authoritative: the balance a cashout checks is computed from `wallet_ledger`.
 * The guard also fails if any file outside `com.example.data` reads either one, so the "display only"
 * claim is enforced rather than asserted.
 *
 * ## Conversion points
 *
 * [toMinorUnits] is the only way into settled money, and it rejects NaN, infinity and out-of-range
 * values instead of letting them become a balance. [toMajorUnits] and [format] are display-only.
 *
 * Decision record: `DECISIONS.md` D-2026-11-20-15.
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
