package com.example

import com.example.util.NotificationRouting
import com.example.util.NotificationTarget
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AUDIT FIX (M5 / B6): regression tests for the notification-tap routing map.
 * Every notification type written by SkillsExchangeRepository must resolve to
 * the correct destination; types without a direct destination (and unknown
 * types) resolve to NONE so MainScreen keeps the old mark-read-only behaviour.
 */
class NotificationRoutingTest {

    @Test
    fun `verification decision routes to identity sheet`() {
        assertEquals(
            NotificationTarget.IDENTITY,
            NotificationRouting.targetFor("VERIFICATION_DECISION")
        )
    }

    @Test
    fun `payment status routes to escrow wallet sheet`() {
        assertEquals(
            NotificationTarget.WALLET,
            NotificationRouting.targetFor("PAYMENT_STATUS")
        )
    }

    @Test
    fun `dispute update routes to trust and safety sheet`() {
        assertEquals(
            NotificationTarget.TRUST_SAFETY,
            NotificationRouting.targetFor("DISPUTE_UPDATE")
        )
    }

    @Test
    fun `sos event routes to trust and safety sheet`() {
        assertEquals(
            NotificationTarget.TRUST_SAFETY,
            NotificationRouting.targetFor("SOS_EVENT")
        )
    }

    @Test
    fun `new quote routes to quote comparison sheet`() {
        assertEquals(
            NotificationTarget.QUOTE_COMPARISON,
            NotificationRouting.targetFor("NEW_QUOTE")
        )
    }

    @Test
    fun `training requirement has no direct navigation target`() {
        assertEquals(
            NotificationTarget.NONE,
            NotificationRouting.targetFor("TRAINING_REQUIREMENT")
        )
    }

    @Test
    fun `unknown or empty type falls back to no target`() {
        assertEquals(NotificationTarget.NONE, NotificationRouting.targetFor(""))
        assertEquals(
            NotificationTarget.NONE,
            NotificationRouting.targetFor("SOMETHING_NEW")
        )
    }
}
