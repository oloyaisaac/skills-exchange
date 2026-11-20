package com.example.util

/**
 * R05-P2-03: the job money split, computed once, in whole Uganda shillings.
 *
 * The app used to keep USD-shaped numbers in storage and multiply them by 3,700 for display, while
 * the payment functions treated the same numbers as UGX. That produced totals the provider refused
 * (fractional shillings) and screens that disagreed with the server.
 *
 * This object is the single place where the split is derived. It is pure so the arithmetic can be
 * tested directly: every component is rounded once, and the total is the sum of the rounded parts,
 * so "labour + materials + fee" can never drift from "total" by a cent.
 */
object JobMoney {

    /** Ten percent of labour, the fee the escrow screens display. */
    const val PLATFORM_FEE_RATE = 0.10

    /** Materials are excluded when the resident supplies (or nothing is needed). */
    private val LABOR_ONLY_MATERIALS = setOf("RESIDENT", "NONE_NEEDED")

    data class Split(
        val laborTotal: Double,
        val materialsSubtotal: Double,
        val platformFee: Double,
        val grandTotal: Double
    )

    fun isLaborOnly(materialsProvidedBy: String): Boolean = materialsProvidedBy in LABOR_ONLY_MATERIALS

    fun split(
        laborHours: Double,
        laborRate: Double,
        budget: Double,
        materialsProvidedBy: String
    ): Split {
        val laborTotal = CurrencyRegion.roundToWholeShillings(laborHours * laborRate)
        val materialsSubtotal = if (isLaborOnly(materialsProvidedBy)) {
            0L
        } else {
            // A budget below the labour cost must not create negative materials.
            CurrencyRegion.roundToWholeShillings((budget - laborTotal).coerceAtLeast(0.0))
        }
        val platformFee = CurrencyRegion.roundToWholeShillings(laborTotal * PLATFORM_FEE_RATE)
        val grandTotal = laborTotal + materialsSubtotal + platformFee
        return Split(
            laborTotal = laborTotal.toDouble(),
            materialsSubtotal = materialsSubtotal.toDouble(),
            platformFee = platformFee.toDouble(),
            grandTotal = grandTotal.toDouble()
        )
    }
}
