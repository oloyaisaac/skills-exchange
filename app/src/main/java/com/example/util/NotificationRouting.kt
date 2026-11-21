package com.example.util

/**
 * AUDIT FIX (M5 / B6): where tapping a notification of each type should
 * navigate. Pure mapping (no Android dependencies) so it can be unit-tested;
 * the side effects of opening the resolved target live in MainScreen, because
 * the destination sheets there are local `remember` state, not ViewModel state.
 *
 * The `type` values are the raw `NotificationEntity.type` strings written by
 * SkillsExchangeRepository (VERIFICATION_DECISION, NEW_QUOTE, ...).
 */
enum class NotificationTarget {
    IDENTITY, // IdentityVerificationSheet — KYC decision
    WALLET, // EscrowWalletSheet — payment / escrow status
    TRUST_SAFETY, // TrustSafetySheet — disputes & SOS events
    QUOTE_COMPARISON, // Quote comparison sheet for the linked job
    NONE // No navigation target — fall back to mark-read only
}

object NotificationRouting {

    fun targetFor(type: String): NotificationTarget = when (type) {
        "VERIFICATION_DECISION" -> NotificationTarget.IDENTITY
        "PAYMENT_STATUS" -> NotificationTarget.WALLET
        "DISPUTE_UPDATE", "SOS_EVENT" -> NotificationTarget.TRUST_SAFETY
        "NEW_QUOTE" -> NotificationTarget.QUOTE_COMPARISON
        else -> NotificationTarget.NONE // e.g. TRAINING_REQUIREMENT: no direct target
    }
}
