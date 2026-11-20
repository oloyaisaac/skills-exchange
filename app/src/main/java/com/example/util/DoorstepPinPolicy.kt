package com.example.util

/**
 * R04-P2-03: the doorstep PIN rule, extracted so it can be tested without Firebase.
 *
 * The defect this replaces was a *schema* default: `doorstepPin = "4829"`. A row written without an
 * explicit PIN therefore carried a well-known code, and the comparison in the repository accepted
 * whatever was stored. The rule now has three parts:
 *
 * 1. a PIN is exactly four digits — `""`, `"4829 "` or `"12345"` are not PINs;
 * 2. an unknown PIN (blank/absent) never matches anything, including another blank;
 * 3. matching is exact after trimming, never a prefix or case-insensitive comparison.
 */
object DoorstepPinPolicy {

    private val PIN = Regex("^[0-9]{4}$")

    /** True when [pin] is a well-formed four-digit code. */
    fun isValid(pin: String?): Boolean = PIN.matches((pin ?: "").trim())

    /**
     * True only when both sides are valid four-digit codes and equal. An absent PIN on either side
     * fails closed: "no PIN configured" must never mean "any PIN works".
     */
    fun matches(enteredPin: String?, expectedPin: String?): Boolean {
        val entered = (enteredPin ?: "").trim()
        val expected = (expectedPin ?: "").trim()
        if (!PIN.matches(entered) || !PIN.matches(expected)) return false
        return entered == expected
    }
}
