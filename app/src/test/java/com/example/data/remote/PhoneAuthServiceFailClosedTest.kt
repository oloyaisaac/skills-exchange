package com.example.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * C2-1: the *public API* of phone authentication fails closed.
 *
 * Complements [PhoneAuthVerificationSecurityTest] (which covers the pure decision guards) by
 * exercising the real `FirebaseService` entry points. Robolectric is used so `android.util.Log`
 * and the Firebase bootstrapping behave like they do on a device; no Firebase project, Play
 * Services or network is required because every assertion stops BEFORE any credential exchange.
 *
 * What this locks in:
 * - `verifyPhoneCode("123456")` can no longer return success without a pending Firebase
 *   verification (the old placeholder accepted any six-digit code and then signed in
 *   anonymously);
 * - `sendPhoneVerificationCode(...)` never fabricates a verification id for unusable input.
 *
 * The parts that inherently need the real Firebase backend — Firebase rejecting a WRONG code
 * for a real verification id, and the resulting phone-backed UID — are device/emulator checks,
 * not unit tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhoneAuthServiceFailClosedTest {

    @Test
    fun `verify phone code fails closed when no verification is pending`() = runBlocking {
        val service = FirebaseService(null)
        val result = service.verifyPhoneCode("123456")
        assertTrue("verifyPhoneCode must not succeed without a pending Firebase verification", result.isFailure)
        assertEquals("Request a verification code first.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `send code fails closed on unusable phone input and never fabricates a verification id`() = runBlocking {
        val service = FirebaseService(null)
        listOf("", "   ", "12ab", "123", "not-a-phone").forEach { input ->
            val result = service.sendPhoneVerificationCode(input)
            assertTrue("Input '$input' must fail closed", result.isFailure)
            assertFalse(result.isSuccess)
        }
    }

    @Test
    fun `clearing pending verification keeps the service fail closed`() = runBlocking {
        val service = FirebaseService(null)
        service.clearPendingPhoneVerification()
        assertTrue(service.verifyPhoneCode("000000").isFailure)
    }
}
