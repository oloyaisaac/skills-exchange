package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R04-P2-04: money is exact.
 *
 * The wallet used to sum `Double` amounts, so ten credits of 0.10 read as 0.9999999999999999 and a
 * balance could disagree with its own ledger. These tests pin the properties that make the integer
 * representation trustworthy: exact conversion, order-independent sums, no silent NaN/Infinity.
 */
class MoneyTest {

    @Test
    fun `conversion is exact for the values the app produces`() {
        assertEquals(0L, Money.toMinorUnits(0.0))
        assertEquals(1L, Money.toMinorUnits(0.01))
        assertEquals(10_000L, Money.toMinorUnits(100.0))
        assertEquals(10_050L, Money.toMinorUnits(100.5))
        assertEquals(25_025L, Money.toMinorUnits(250.25))
        assertEquals(123_456_789L, Money.toMinorUnits(1_234_567.89))
    }

    @Test
    fun `repeated credits do not drift`() {
        var total = 0L
        repeat(10) { total += Money.toMinorUnits(0.10)!! }
        assertEquals("ten credits of 0.10 must be exactly 1.00", 100L, total)
        assertEquals(1.0, Money.toMajorUnits(total), 0.0)
    }

    @Test
    fun `the sum is order independent`() {
        val amounts = listOf(0.1, 0.2, 0.3, 0.7, 12.34, 0.06)
        val forward = amounts.sumOf { Money.toMinorUnits(it)!! }
        val backward = amounts.reversed().sumOf { Money.toMinorUnits(it)!! }
        assertEquals(forward, backward)
        assertEquals(1_370L, forward) // 0.10 + 0.20 + 0.30 + 0.70 + 12.34 + 0.06
    }

    @Test
    fun `non-finite amounts are rejected rather than converted`() {
        assertNull(Money.toMinorUnits(Double.NaN))
        assertNull(Money.toMinorUnits(Double.POSITIVE_INFINITY))
        assertNull(Money.toMinorUnits(Double.NEGATIVE_INFINITY))
        assertNull(Money.toMinorUnits(1e300))
    }

    @Test
    fun `formatting is deterministic and locale independent`() {
        assertEquals("0.00", Money.format(0))
        assertEquals("0.05", Money.format(5))
        assertEquals("1.00", Money.format(100))
        assertEquals("12,345.67", Money.format(1_234_567))
        assertEquals("-12,345.67", Money.format(-1_234_567))
        assertEquals("UGX 12,345.67", Money.formatUgx(1_234_567))
    }

    @Test
    fun `major and minor units round trip`() {
        val amounts = listOf(0.01, 0.99, 1.0, 99.95, 12_345.67)
        for (amount in amounts) {
            val minor = Money.toMinorUnits(amount)!!
            assertTrue("$amount", Money.toMajorUnits(minor) == amount)
        }
    }
}
