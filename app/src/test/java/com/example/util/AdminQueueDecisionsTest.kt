package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * AUDIT-A-CONSOLE unit coverage for [AdminQueueDecisions] — the client-side guards for the
 * admin verification queue sheet.
 *
 * Pure-JVM tests (no Android framework), mirroring [ProfileValidatorsTest] /
 * [GuarantorValidatorsTest]. The callable + rules remain the real authorizer; these guards
 * only stop malformed taps from leaving the device.
 */
@RunWith(JUnit4::class)
class AdminQueueDecisionsTest {

    @Test
    fun `approve with no feedback is allowed`() {
        assertNull(
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = true,
                userId = "uid_worker_1",
                decision = "APPROVE",
                feedback = ""
            )
        )
    }

    @Test
    fun `locked console blocks every decision`() {
        assertEquals(
            "Operator console is locked. Decisions are disabled.",
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = false,
                userId = "uid_worker_1",
                decision = "APPROVE",
                feedback = ""
            )
        )
    }

    @Test
    fun `blank applicant id fails closed`() {
        assertEquals(
            "Missing applicant id. Refresh the queue and try again.",
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = true,
                userId = "   ",
                decision = "APPROVE",
                feedback = ""
            )
        )
    }

    @Test
    fun `unknown decision vocabulary is rejected`() {
        assertEquals(
            "Unknown decision. Use Approve, Changes Required, or Reject.",
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = true,
                userId = "uid_worker_1",
                decision = "MAYBE",
                feedback = ""
            )
        )
    }

    @Test
    fun `reject without feedback is blocked`() {
        assertEquals(
            "Add feedback (10+ characters) so the worker knows what to fix.",
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = true,
                userId = "uid_worker_1",
                decision = "REJECT",
                feedback = "too short"
            )
        )
    }

    @Test
    fun `changes-required without feedback is blocked`() {
        assertEquals(
            "Add feedback (10+ characters) so the worker knows what to fix.",
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = true,
                userId = "uid_worker_1",
                decision = "changes_required",
                feedback = ""
            )
        )
    }

    @Test
    fun `reject with sufficient feedback is allowed`() {
        assertNull(
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = true,
                userId = "uid_worker_1",
                decision = "REJECT",
                feedback = "National ID photo is unreadable, please re-upload both sides."
            )
        )
    }

    @Test
    fun `decision matching is case-insensitive but allow-listed`() {
        assertNull(
            AdminQueueDecisions.decisionBlocker(
                isAdminUnlocked = true,
                userId = "uid_worker_1",
                decision = "approve",
                feedback = ""
            )
        )
    }
}
