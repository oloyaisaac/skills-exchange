package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R05-P2-03: the job split is whole shillings and internally consistent.
 *
 * `momoCollection` refuses a grand total that is not a whole number of UGX, and the screens show
 * labour + materials + fee separately. Both are only true if rounding happens once per component
 * and the total is the sum of the rounded components — never a separate rounding of an unrounded
 * sum.
 */
class JobMoneyTest {

    @Test
    fun `every component and the total are whole shillings`() {
        val split = JobMoney.split(laborHours = 1.5, laborRate = 277_500.0, budget = 455_840.0, materialsProvidedBy = "WORKER")
        for (amount in listOf(split.laborTotal, split.materialsSubtotal, split.platformFee, split.grandTotal)) {
            assertEquals("$amount must be a whole shilling", amount, Math.rint(amount), 0.0)
        }
    }

    @Test
    fun `total is the sum of the rounded components`() {
        val split = JobMoney.split(laborHours = 3.0, laborRate = 33_333.0, budget = 150_000.0, materialsProvidedBy = "WORKER")
        assertEquals(split.grandTotal, split.laborTotal + split.materialsSubtotal + split.platformFee, 0.0)
    }

    @Test
    fun `resident-supplied materials are never charged`() {
        for (supplier in listOf("RESIDENT", "NONE_NEEDED")) {
            val split = JobMoney.split(laborHours = 2.0, laborRate = 25_000.0, budget = 500_000.0, materialsProvidedBy = supplier)
            assertEquals(0.0, split.materialsSubtotal, 0.0)
        }
    }

    @Test
    fun `a budget below labour never creates negative materials`() {
        val split = JobMoney.split(laborHours = 4.0, laborRate = 25_000.0, budget = 30_000.0, materialsProvidedBy = "WORKER")
        assertEquals(0.0, split.materialsSubtotal, 0.0)
        assertTrue(split.materialsSubtotal >= 0.0)
    }

    @Test
    fun `the platform fee is ten percent of labour, rounded`() {
        val split = JobMoney.split(laborHours = 1.0, laborRate = 25_005.0, budget = 100_000.0, materialsProvidedBy = "RESIDENT")
        assertEquals(2_501.0, split.platformFee, 0.0) // 2,500.5 rounds to 2,501
        assertEquals(25_005.0, split.laborTotal, 0.0)
        assertEquals(27_506.0, split.grandTotal, 0.0)
    }

    @Test
    fun `a fractional rate is rounded before anything is collected`() {
        val split = JobMoney.split(laborHours = 1.0, laborRate = 25_000.49, budget = 100_000.0, materialsProvidedBy = "RESIDENT")
        assertEquals(25_000.0, split.laborTotal, 0.0)
        assertEquals(27_500.0, split.grandTotal, 0.0)
    }
}
