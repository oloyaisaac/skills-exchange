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

        fun format(amountInUsdEquivalent: Double, region: CurrencyRegion = currentRegion): String {
            // Realistic conversion multipliers
            val localAmount = when (region) {
                UGANDA -> amountInUsdEquivalent * 3700.0 // e.g. $10 -> 37,000 UGX
                KENYA -> amountInUsdEquivalent * 130.0   // e.g. $10 -> 1,300 KES
                TANZANIA -> amountInUsdEquivalent * 2600.0 // e.g. $10 -> 26,000 TZS
                RWANDA -> amountInUsdEquivalent * 1300.0  // e.g. $10 -> 13,000 RWF
                USA -> amountInUsdEquivalent
            }

            return if (region.usesDecimals) {
                "${region.symbol}${"%.2f".format(localAmount)}"
            } else {
                val formattedInt = NumberFormat.getIntegerInstance(Locale.US).format(localAmount.toLong())
                "${region.symbol} $formattedInt"
            }
        }

        fun formatCompact(amountInUsdEquivalent: Double, region: CurrencyRegion = currentRegion): String {
            val localAmount = when (region) {
                UGANDA -> amountInUsdEquivalent * 3700.0
                KENYA -> amountInUsdEquivalent * 130.0
                TANZANIA -> amountInUsdEquivalent * 2600.0
                RWANDA -> amountInUsdEquivalent * 1300.0
                USA -> amountInUsdEquivalent
            }
            return if (localAmount >= 1000) {
                "${region.symbol} ${(localAmount / 1000).toInt()}k"
            } else {
                format(amountInUsdEquivalent, region)
            }
        }
    }
}
