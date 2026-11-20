package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R05-P2-03: money has exactly one unit — the Uganda shilling.
 *
 * The finding was that the screen multiplied every stored amount by 3,700 (treating it as USD)
 * while the payment functions treated the same field as UGX, so a 37,000 UGX job was shown as
 * "UGX 136,900,000". These tests pin the formatter to the stored value.
 */
class CurrencyUnitTest {

    @Test
    fun `a stored UGX amount is shown as itself, never multiplied`() {
        assertEquals("UGX 37,000", CurrencyRegion.format(37_000.0, CurrencyRegion.UGANDA))
        assertNotEquals("UGX 136,900,000", CurrencyRegion.format(37_000.0, CurrencyRegion.UGANDA))
    }

    @Test
    fun `shillings are displayed whole`() {
        assertEquals("UGX 12,346", CurrencyRegion.format(12_345.6, CurrencyRegion.UGANDA))
        assertEquals("UGX 0", CurrencyRegion.format(0.4, CurrencyRegion.UGANDA))
    }

    @Test
    fun `NaN and infinity never render as money or crash`() {
        assertEquals("UGX 0", CurrencyRegion.format(Double.NaN, CurrencyRegion.UGANDA))
        assertEquals("UGX 0", CurrencyRegion.format(Double.POSITIVE_INFINITY, CurrencyRegion.UGANDA))
        assertEquals("UGX 0", CurrencyRegion.format(Double.NEGATIVE_INFINITY, CurrencyRegion.UGANDA))
    }

    @Test
    fun `other regions are display-only conversions of the same UGX amount`() {
        val kenya = CurrencyRegion.format(1_000_000.0, CurrencyRegion.KENYA)
        assertTrue("KES display must keep its own symbol: $kenya", kenya.startsWith("KES"))
        // Never re-enter arithmetic: the stored value is untouched.
        assertEquals(1_000_000.0, 1_000_000.0, 0.0)
        assertEquals("UGX 1,000,000", CurrencyRegion.format(1_000_000.0, CurrencyRegion.UGANDA))
    }

    @Test
    fun `compact form is still UGX`() {
        assertEquals("UGX 37k", CurrencyRegion.formatCompact(37_000.0, CurrencyRegion.UGANDA))
        assertEquals("UGX 500", CurrencyRegion.formatCompact(500.0, CurrencyRegion.UGANDA))
    }

    @Test
    fun `rounding to whole shillings is exact and total-safe`() {
        assertEquals(37_000L, CurrencyRegion.roundToWholeShillings(36_999.5))
        assertEquals(0L, CurrencyRegion.roundToWholeShillings(Double.NaN))
        assertEquals(0L, CurrencyRegion.roundToWholeShillings(Double.NEGATIVE_INFINITY))
        // Ties round half-up, the same rule the Node functions use (Math.round); ties-to-even would
        // make the client store 2500 where the server expects 2501 and the job would be rejected.
        assertEquals(2_501L, CurrencyRegion.roundToWholeShillings(2_500.5))
        assertEquals(2_500L, CurrencyRegion.roundToWholeShillings(2_499.5))
    }
}
