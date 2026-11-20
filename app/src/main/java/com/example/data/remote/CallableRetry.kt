package com.example.data.remote

import android.util.Log
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.delay

/**
 * R05-P2-07: bounded retry for callable calls that are safe to repeat.
 *
 * Every callable call used to be a single attempt: a transient `UNAVAILABLE` while the device moved
 * between networks surfaced to the user as a hard failure, and there was no backoff anywhere. This
 * helper retries *only* transient codes, at most [MAX_ATTEMPTS] times, with a short linear backoff.
 *
 * Callables that mutate money or state (`createMoMoCollection`, `releaseEscrowToWorker`,
 * `resolveDispute`) are deliberately NOT wrapped: the server already treats duplicates as errors,
 * and silently repeating a payment request is worse than showing the failure.
 */
object CallableRetry {

    const val MAX_ATTEMPTS = 3
    const val BASE_DELAY_MS = 250L

    /** Codes that mean "try again"; anything else is a decision, not a blip. */
    fun isRetryable(code: FirebaseFunctionsException.Code?): Boolean = when (code) {
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        FirebaseFunctionsException.Code.INTERNAL,
        FirebaseFunctionsException.Code.ABORTED -> true
        else -> false
    }

    /**
     * Runs [block] up to [MAX_ATTEMPTS] times. A retryable [FirebaseFunctionsException] is retried
     * after [BASE_DELAY_MS] × attempt; anything else is rethrown immediately.
     */
    suspend fun <T> withRetry(block: suspend (attempt: Int) -> T): T {
        var lastError: FirebaseFunctionsException? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return block(attempt)
            } catch (error: FirebaseFunctionsException) {
                lastError = error
                if (!isRetryable(error.code) || attempt == MAX_ATTEMPTS) throw error
                Log.w("CallableRetry", "Transient callable failure (${error.code}); retrying")
                delay(BASE_DELAY_MS * attempt)
            }
        }
        throw lastError ?: IllegalStateException("Callable retry exhausted without a result")
    }
}
