package com.example.data.remote

/**
 * R07-P2-01: the outcome of a doorstep-PIN verification, as the server decided it.
 *
 * The PIN used to be checked on the phone by comparing the typed code with a value the caller passed
 * in, so a modified client could pass `expected == entered` and pass without knowing the PIN. The
 * check now happens in `verifyDoorstepPin`, which stores only a salted hash, compares in constant
 * time, and locks the job after five wrong attempts.
 */
sealed class DoorstepPinResult {

    /** The server matched the PIN and started the job. */
    object Verified : DoorstepPinResult()

    /** Wrong PIN; [remainingAttempts] attempts are left before a lockout. */
    data class Incorrect(val message: String, val remainingAttempts: Int) : DoorstepPinResult()

    /** Too many wrong attempts; nothing is accepted until [retryAfterMs] has passed. */
    data class Locked(val message: String, val retryAfterMs: Long) : DoorstepPinResult()

    /** The job has no PIN on file yet (the resident's device has not registered it). */
    object NotRegistered : DoorstepPinResult()

    /** The request never reached a decision (offline, unauthenticated, wrong party). */
    data class Failed(val message: String) : DoorstepPinResult()

    val isVerified: Boolean get() = this is Verified
}

/**
 * R07-P2-01: maps the callable's error to a state the UI can show, and never to "verified".
 *
 * Kept pure so the decision table is testable without Firebase: an unrecognised error is a failure,
 * never a success, and every message is safe to show (no hash, salt or PIN).
 */
object DoorstepPinRules {

    const val CODE_NOT_REGISTERED = "PIN_NOT_REGISTERED"
    const val CODE_INCORRECT = "PIN_INCORRECT"
    const val CODE_LOCKED = "PIN_LOCKED"

    fun outcomeOf(
        errorCode: String?,
        message: String?,
        details: Map<String, Any?> = emptyMap()
    ): DoorstepPinResult {
        val serverCode = (details["code"] as? String)?.trim()
        val text = message?.trim().orEmpty()
        return when {
            serverCode == CODE_NOT_REGISTERED -> DoorstepPinResult.NotRegistered
            serverCode == CODE_LOCKED -> DoorstepPinResult.Locked(
                message = text.ifBlank { "Too many incorrect PINs. Try again later." },
                retryAfterMs = (details["retryAfterMs"] as? Number)?.toLong() ?: 0L
            )
            serverCode == CODE_INCORRECT -> DoorstepPinResult.Incorrect(
                message = text.ifBlank { "Incorrect PIN. Try again." },
                remainingAttempts = (details["remainingAttempts"] as? Number)?.toInt() ?: 0
            )
            errorCode == "UNAUTHENTICATED" -> DoorstepPinResult.Failed("Sign in again to verify the PIN.")
            errorCode == "PERMISSION_DENIED" -> DoorstepPinResult.Failed(
                text.ifBlank { "Only the resident or the assigned worker can verify this PIN." }
            )
            text.isBlank() -> DoorstepPinResult.Failed("We could not reach the server. Check your connection.")
            else -> DoorstepPinResult.Failed(text)
        }
    }

    /** Text to show when a verification attempt did not succeed. */
    fun failureMessage(result: DoorstepPinResult): String? = when (result) {
        is DoorstepPinResult.Verified -> null
        is DoorstepPinResult.Incorrect -> result.message
        is DoorstepPinResult.Locked -> result.message
        is DoorstepPinResult.NotRegistered ->
            "This job has no PIN on file yet. Ask the resident to open the app once, then try again."
        is DoorstepPinResult.Failed -> result.message
    }

    /**
     * The PIN belongs to the resident. The worker is told to ask for it instead of being shown it —
     * showing it to both parties made the code a formality rather than proof of presence.
     */
    fun residentInstruction(pin: String): String =
        "Your Doorstep PIN is $pin. Share it with the worker only when they are at your door."

    fun workerInstruction(): String =
        "Ask the resident for the 4-digit Doorstep PIN when you arrive. It is not shown on your device."

    /** A PIN is exactly four digits; anything else cannot be registered or submitted. */
    fun isWellFormed(pin: String?): Boolean = (pin ?: "").trim().matches(Regex("^[0-9]{4}$"))
}
