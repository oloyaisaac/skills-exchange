package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * AUDIT-W-QUOTE-UI: pure-JVM coverage for [QuoteProposalValidators].
 *
 * Mirrors the secured backend contract (finite rate/hours > 0) for form UX only —
 * the repository boundary stays the real authorizer. Covers required/optional
 * fields, invalid numerics, safe totals, and backend-result messaging.
 */
@RunWith(JUnit4::class)
class QuoteProposalValidatorsTest {

    @Test
    fun `valid rate is accepted`() {
        assertNull(QuoteProposalValidators.validateRate("15000"))
        assertNull(QuoteProposalValidators.validateRate(" 15,000 "))
        assertNull(QuoteProposalValidators.validateRate("12.5"))
    }

    @Test
    fun `invalid rate is rejected`() {
        assertNotNull(QuoteProposalValidators.validateRate(""))
        assertNotNull(QuoteProposalValidators.validateRate("   "))
        assertNotNull(QuoteProposalValidators.validateRate("0"))
        assertNotNull(QuoteProposalValidators.validateRate("-5000"))
        assertNotNull(QuoteProposalValidators.validateRate("abc"))
        assertNotNull(QuoteProposalValidators.validateRate("NaN"))
        assertNotNull(QuoteProposalValidators.validateRate("Infinity"))
        assertNotNull(QuoteProposalValidators.validateRate("-Infinity"))
    }

    @Test
    fun `valid hours are accepted`() {
        assertNull(QuoteProposalValidators.validateHours("3"))
        assertNull(QuoteProposalValidators.validateHours("2.5"))
    }

    @Test
    fun `invalid hours are rejected`() {
        assertNotNull(QuoteProposalValidators.validateHours(""))
        assertNotNull(QuoteProposalValidators.validateHours("0"))
        assertNotNull(QuoteProposalValidators.validateHours("-2"))
        assertNotNull(QuoteProposalValidators.validateHours("two"))
        assertNotNull(QuoteProposalValidators.validateHours("NaN"))
        assertNotNull(QuoteProposalValidators.validateHours("Infinity"))
    }

    @Test
    fun `notes are optional but length-capped`() {
        assertNull(QuoteProposalValidators.validateNotes(""))
        assertNull(QuoteProposalValidators.validateNotes("I can complete the repair today."))
        assertNotNull(
            QuoteProposalValidators.validateNotes("x".repeat(QuoteProposalValidators.MAX_NOTES_LENGTH + 1)),
        )
    }

    @Test
    fun `form blocker requires rate and hours`() {
        assertNull(QuoteProposalValidators.quoteFormBlocker("15000", "3", ""))
        assertNotNull(QuoteProposalValidators.quoteFormBlocker("", "3", ""))
        assertNotNull(QuoteProposalValidators.quoteFormBlocker("15000", "", ""))
        assertNotNull(QuoteProposalValidators.quoteFormBlocker("0", "3", ""))
        assertNotNull(QuoteProposalValidators.quoteFormBlocker("15000", "-1", ""))
    }

    @Test
    fun `safe total multiplies rate by hours`() {
        assertEquals(45000.0, QuoteProposalValidators.safeTotal("15000", "3")!!, 0.001)
        assertNull(QuoteProposalValidators.safeTotal("", "3"))
        assertNull(QuoteProposalValidators.safeTotal("15000", "abc"))
        assertNull(QuoteProposalValidators.safeTotal("0", "3"))
        assertNull(QuoteProposalValidators.safeTotal("-5", "3"))
    }

    @Test
    fun `total formats as UGX with grouping`() {
        assertEquals("UGX 45,000", QuoteProposalValidators.formatUgxTotal(45000.0))
        assertEquals("UGX —", QuoteProposalValidators.formatUgxTotal(null))
    }

    @Test
    fun `every backend result maps to a readable message`() {
        assertEquals(
            "Quote submitted successfully.",
            QuoteProposalValidators.submissionResultMessage("Success"),
        )
        assertEquals(
            "You already have a pending quote for this job.",
            QuoteProposalValidators.submissionResultMessage("DuplicatePendingQuote"),
        )
        assertEquals(
            "This job is no longer accepting quotes.",
            QuoteProposalValidators.submissionResultMessage("InvalidJobState"),
        )
        assertEquals(
            "You cannot quote your own job.",
            QuoteProposalValidators.submissionResultMessage("SelfQuoteForbidden"),
        )
        assertEquals(
            "Sign in to submit a quote, then try again.",
            QuoteProposalValidators.submissionResultMessage("NotAuthenticated"),
        )
        assertEquals(
            "Quote submission failed. Please try again.",
            QuoteProposalValidators.submissionResultMessage("SomethingUnknown"),
        )
    }
}
