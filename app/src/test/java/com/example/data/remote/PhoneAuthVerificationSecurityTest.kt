package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * C2-1: Phone-authentication security tests (invalid code / arbitrary code / E.164 input).
 *
 * Context: `FirebaseService.sendPhoneVerificationCode` / `verifyPhoneCode` used to be a
 * placeholder that accepted ANY six-digit code and then called `signInAnonymously()`. These
 * tests lock the replacement in place:
 *
 * - a code can only be submitted when a REAL Firebase verification is pending
 *   ([FirebaseService.phoneVerificationGateFailureReason]);
 * - no code is ever interpreted locally — with a pending verification id the gate deliberately
 *   returns null so the only authority that can accept or reject the code is Firebase itself
 *   (`signInWithCredential`), which is exercised on-device (see the task's Test A/B notes);
 * - a verification id is never fabricated, and input is normalised to the E.164 form Firebase
 *   requires ([FirebaseService.toE164PhoneNumber]).
 *
 * Deliberately a plain JVM test (no Robolectric/Firebase network): the pure guards are what make
 * the escalation impossible, and they must be verifiable without an emulator or a device.
 */
@RunWith(JUnit4::class)
class PhoneAuthVerificationSecurityTest {

    // --- Test B: an arbitrary six-digit code must never authenticate anyone ---

    @Test
    fun `arbitrary six digit code is refused when no firebase verification is pending`() {
        val failure = FirebaseService.phoneVerificationGateFailureReason(
            verificationId = null,
            hasAutoCredential = false,
            otpCode = "123456"
        )
        assertNotNull("A six-digit code must not be accepted without a pending Firebase verification", failure)
        assertEquals("Request a verification code first.", failure)
    }

    @Test
    fun `no six digit combination authenticates without a pending firebase verification`() {
        val candidates = listOf("000000", "111111", "123456", "999999", "654321", "482910")
        candidates.forEach { code ->
            val failure = FirebaseService.phoneVerificationGateFailureReason(
                verificationId = null,
                hasAutoCredential = false,
                otpCode = code
            )
            assertNotNull("Code $code must be refused: Firebase never issued this verification", failure)
        }
    }

    @Test
    fun `blank verification id that is not a real credential is refused`() {
        val failure = FirebaseService.phoneVerificationGateFailureReason(
            verificationId = "   ",
            hasAutoCredential = false,
            otpCode = "123456"
        )
        assertEquals("Request a verification code first.", failure)
    }

    // --- Test A: malformed codes never reach Firebase's credential exchange ---

    @Test
    fun `code with wrong length or non digits is refused before credential exchange`() {
        listOf("12345", "1234567", "", "12345a", "abcdef").forEach { code ->
            val failure = FirebaseService.phoneVerificationGateFailureReason(
                verificationId = "firebase-issued-verification-id",
                hasAutoCredential = false,
                otpCode = code
            )
            assertNotNull("Malformed code '$code' must be refused locally", failure)
        }
    }

    @Test
    fun `well formed code with a pending firebase verification is handed to firebase only`() {
        // null means "proceed" — and the only thing that can then accept the code is Firebase's
        // signInWithCredential call inside verifyPhoneCode. Nothing is validated locally.
        assertNull(
            FirebaseService.phoneVerificationGateFailureReason(
                verificationId = "firebase-issued-verification-id",
                hasAutoCredential = false,
                otpCode = "123456"
            )
        )
    }

    @Test
    fun `play services auto verified credential allows verification without a code`() {
        assertNull(
            FirebaseService.phoneVerificationGateFailureReason(
                verificationId = null,
                hasAutoCredential = true,
                otpCode = ""
            )
        )
    }

    // --- E.164 normalisation (what Firebase Phone Auth requires) ---

    @Test
    fun `ugandan phone input is normalised to e164`() {
        assertEquals("+256772198420", FirebaseService.toE164PhoneNumber("+256 772 198 420"))
        assertEquals("+256772198420", FirebaseService.toE164PhoneNumber("0772198420"))
        assertEquals("+256772198420", FirebaseService.toE164PhoneNumber("256772198420"))
        assertEquals("+256772198420", FirebaseService.toE164PhoneNumber("772198420"))
        assertEquals("+256772198420", FirebaseService.toE164PhoneNumber("(0772) 198-420"))
    }

    @Test
    fun `internationally prefixed input is preserved`() {
        assertEquals("+14155552671", FirebaseService.toE164PhoneNumber("+1 415 555 2671"))
    }

    @Test
    fun `ambiguous or unusable phone input is rejected instead of guessed`() {
        assertNull(FirebaseService.toE164PhoneNumber(""))
        assertNull(FirebaseService.toE164PhoneNumber("   "))
        assertNull(FirebaseService.toE164PhoneNumber("12ab"))
        assertNull(FirebaseService.toE164PhoneNumber("123"))
        assertNull(FirebaseService.toE164PhoneNumber("077219842"))
        assertNull(FirebaseService.toE164PhoneNumber("0000000000000000000"))
    }
}
