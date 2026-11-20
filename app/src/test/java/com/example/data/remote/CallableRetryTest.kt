package com.example.data.remote

import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * R05-P2-07: retries are bounded and only happen for transient failures.
 *
 * Before this, a single `UNAVAILABLE` while a phone changed networks was shown to the user as a hard
 * failure, and no call had a backoff policy at all. These tests pin the policy: at most three
 * attempts, only for retryable codes, and never for a decision the server already made.
 *
 * The SDK's `FirebaseFunctionsException` constructors are internal, so the retry loop is exercised
 * through its explicit predicate overload with ordinary exceptions.
 */
@RunWith(JUnit4::class)
class CallableRetryTest {

    @Test
    fun `transient codes are retryable`() {
        for (code in listOf(
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
            FirebaseFunctionsException.Code.INTERNAL,
            FirebaseFunctionsException.Code.ABORTED
        )) {
            assertTrue("$code should be retried", CallableRetry.isRetryable(code))
        }
    }

    @Test
    fun `decisions are not retried`() {
        for (code in listOf(
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.ALREADY_EXISTS
        )) {
            assertFalse("$code must not be retried", CallableRetry.isRetryable(code))
        }
        assertFalse("a missing code is not retryable", CallableRetry.isRetryable(null))
    }

    @Test
    fun `only callable exceptions are retryable failures`() {
        assertFalse(
            "a plain exception is not a transient callable failure",
            CallableRetry.isRetryableFailure(IllegalStateException("boom"))
        )
    }

    @Test
    fun `a transient failure succeeds on the next attempt`() = runTest {
        var attempts = 0
        val value = CallableRetry.withRetry<String>({ true }) { attempt ->
            attempts = attempt
            if (attempt < 2) throw IllegalStateException("transient")
            "ok"
        }
        assertEquals("ok", value)
        assertEquals(2, attempts)
    }

    @Test
    fun `retries stop after three attempts`() = runTest {
        var attempts = 0
        try {
            CallableRetry.withRetry<Unit>({ true }) {
                attempts++
                throw IllegalStateException("always failing")
            }
            fail("the retry must give up")
        } catch (error: IllegalStateException) {
            assertEquals("always failing", error.message)
        }
        assertEquals(CallableRetry.MAX_ATTEMPTS, attempts)
    }

    @Test
    fun `a non-retryable failure fails immediately`() = runTest {
        var attempts = 0
        try {
            CallableRetry.withRetry<Unit>({ false }) {
                attempts++
                throw IllegalStateException("permission denied")
            }
            fail("permission denied must not be retried")
        } catch (error: IllegalStateException) {
            assertEquals("permission denied", error.message)
        }
        assertEquals(1, attempts)
    }
}
