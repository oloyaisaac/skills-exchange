package com.example.util

import java.text.NumberFormat
import java.util.Locale

enum class CurrencyRegion(val code: String, val label: String, val symbol: String, val locale: Locale, val usesDecimals: Boolean) {
    UGANDA("UG", "UGX (Uganda)", "UGX", Locale("en", "UG"), false),
    KENYA("KE", "KES (Kenya)", "KES", Locale("sw", "KE"), false),
    TANZANIA("TZ", "TZS (Tanzania)", "TZS", Locale("sw", "TZ"), false),
    RWANDA("RW", "RWF (Rwanda)", "RF", Locale("rw", "RW"), false),
    USA("US", "USD (United States)", "$", Locale.US, true);

    companion object {
        var currentRegion: CurrencyRegion = UGANDA

        fun fromDistrictOrCountry(query: String): CurrencyRegion {
            val q = query.lowercase()
            return when {
                q.contains("uganda") || q.contains("kampala") || q.contains("kololo") || q.contains("bugolobi") || q.contains("nakasero") || q.contains("rubaga") || q.contains("sunnyside") || q.contains("ug") -> UGANDA
                q.contains("kenya") || q.contains("nairobi") || q.contains("westlands") || q.contains("ke") -> KENYA
                q.contains("tanzania") || q.contains("dar") || q.contains("tz") -> TANZANIA
                q.contains("rwanda") || q.contains("kigali") || q.contains("rw") -> RWANDA
                q.contains("us") || q.contains("usa") || q.contains("dollar") -> USA
                else -> UGANDA
            }
        }

        /**
         * R05-P2-03: the ONE money unit in this app is the Uganda shilling. Every amount stored
         * (`job.budget`, `job.grandTotal`, `job.laborTotal`, ledger rows) is UGX, and the provider
         * payloads are UGX. This formatter used to multiply its input by 3,700 — it treated every
         * stored number as USD — so a 37,000 UGX job was displayed as "UGX 136,900,000" while the
         * server saw 37,000. There is no conversion on the way to the screen any more.
         *
         * Amounts are rounded to whole shillings: UGX is not quoted in subunits by the providers,
         * and `momoCollection` refuses a non-integer total.
         */
        fun format(amountUgx: Double, region: CurrencyRegion = currentRegion): String {
            val wholeShillings = roundToWholeShillings(amountUgx)
            if (region == UGANDA) {
                return "UGX " + NumberFormat.getIntegerInstance(Locale.US).format(wholeShillings)
            }
            // Other regions are DISPLAY ONLY and explicitly approximate: the stored unit stays UGX.
            val localAmount = wholeShillings * REGION_DISPLAY_RATES.getValue(region)
            return if (region.usesDecimals) {
                "${region.symbol}${"%.2f".format(localAmount)}"
            } else {
                "${region.symbol} ${NumberFormat.getIntegerInstance(Locale.US).format(localAmount.toLong())}"
            }
        }

        fun formatCompact(amountUgx: Double, region: CurrencyRegion = currentRegion): String {
            val wholeShillings = roundToWholeShillings(amountUgx)
            return if (wholeShillings >= 1000) {
                "${region.symbol} ${wholeShillings / 1000}k"
            } else {
                format(amountUgx, region)
            }
        }

        /** R05-P2-03: whole shillings, so nothing fractional reaches the provider payload. */
        fun roundToWholeShillings(amountUgx: Double): Long =
            if (amountUgx.isNaN() || amountUgx.isInfinite()) 0L else kotlin.math.round(amountUgx).toLong()

        /** Display-only approximations FROM the stored UGX amount. Never used for arithmetic. */
        private val REGION_DISPLAY_RATES: Map<CurrencyRegion, Double> = mapOf(
            UGANDA to 1.0,
            KENYA to 0.035,
            TANZANIA to 0.68,
            RWANDA to 0.34,
            USA to 0.00027
        )
    }
}
