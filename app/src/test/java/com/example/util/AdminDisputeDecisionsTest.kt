package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AdminDisputeDecisionsTest {

    @Test
    fun `resolved with no refund type is allowed`() {
        assertNull(
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = null,
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `rejected with no action is allowed`() {
        assertNull(
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "REJECTED",
                resolutionType = "NO_ACTION",
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `escalated is allowed`() {
        assertNull(
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "ESCALATED",
                resolutionType = null,
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `resolution matching is case-insensitive but allow-listed`() {
        assertNull(
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "resolved",
                resolutionType = null,
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `invalid resolution is rejected`() {
        assertEquals(
            "Unknown resolution. Use Resolved, Rejected, or Escalated.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "MAYBE",
                resolutionType = null,
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `invalid resolution type is rejected`() {
        assertEquals(
            "Unknown resolution type. Use Full Refund, Partial Refund, No Action, or Worker Suspended.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "INVALID_TYPE",
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `partial refund without amount is blocked`() {
        assertEquals(
            "Partial refund requires a positive amount.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_PARTIAL",
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `partial refund with zero amount is blocked`() {
        assertEquals(
            "Partial refund requires a positive amount.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_PARTIAL",
                resolutionAmount = 0.0,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `partial refund with negative amount is blocked`() {
        assertEquals(
            "Partial refund requires a positive amount.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_PARTIAL",
                resolutionAmount = -50.0,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `partial refund with NaN amount is blocked`() {
        assertEquals(
            "Partial refund requires a positive amount.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_PARTIAL",
                resolutionAmount = Double.NaN,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `partial refund with infinite amount is blocked`() {
        assertEquals(
            "Partial refund requires a positive amount.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_PARTIAL",
                resolutionAmount = Double.POSITIVE_INFINITY,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `full refund without amount is blocked`() {
        assertEquals(
            "Full refund requires a positive amount.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_FULL",
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `no action with refund amount is blocked`() {
        assertEquals(
            "Refund amount not applicable to NO_ACTION.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "REJECTED",
                resolutionType = "NO_ACTION",
                resolutionAmount = 100.0,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `worker suspended with refund amount is blocked`() {
        assertEquals(
            "Refund amount not applicable to WORKER_SUSPENDED.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "ESCALATED",
                resolutionType = "WORKER_SUSPENDED",
                resolutionAmount = 100.0,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `blank dispute id fails closed`() {
        assertEquals(
            "Missing dispute id. Refresh the queue and try again.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "   ",
                resolution = "RESOLVED",
                resolutionType = null,
                resolutionAmount = null,
                status = "OPEN",
                adminNotes = null
            )
        )
    }

    @Test
    fun `terminal resolved dispute cannot be re-resolved`() {
        assertEquals(
            "This dispute is already RESOLVED and cannot be re-resolved.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "REJECTED",
                resolutionType = null,
                resolutionAmount = null,
                status = "RESOLVED",
                adminNotes = null
            )
        )
    }

    @Test
    fun `terminal rejected dispute cannot be re-resolved`() {
        assertEquals(
            "This dispute is already REJECTED and cannot be re-resolved.",
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = null,
                resolutionAmount = null,
                status = "REJECTED",
                adminNotes = null
            )
        )
    }

    @Test
    fun `full refund with valid amount is allowed`() {
        assertNull(
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_FULL",
                resolutionAmount = 100.0,
                status = "OPEN",
                adminNotes = "Customer dissatisfied with work quality"
            )
        )
    }

    @Test
    fun `partial refund with valid amount is allowed`() {
        assertNull(
            AdminDisputeDecisions.decisionBlocker(
                disputeId = "dispute_1",
                resolution = "RESOLVED",
                resolutionType = "REFUND_PARTIAL",
                resolutionAmount = 50.0,
                status = "OPEN",
                adminNotes = "Partial credit issued"
            )
        )
    }
}