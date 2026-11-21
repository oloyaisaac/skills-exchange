package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * R05-P2-02: register this device's FCM token with the backend.
 *
 * Before this class existed the client logged the token and dropped it (`sendTokenToServer` only
 * wrote a log line), so the backend had no per-device address and could only broadcast to a topic.
 * Now every token is validated, then sent to the `registerPushToken` callable, which stores it under
 * the *authenticated* uid — the client never chooses whose account the device belongs to.
 *
 * The token itself is a credential: it is never written to logcat. Failures are logged by exception
 * type only, with the token redacted to its last six characters.
 */
object PushTokenRegistrar {

    /** Server callable that stores the token for `context.auth.uid`. */
    const val CALLABLE = "registerPushToken"

    private const val TAG = "PushTokenRegistrar"

    /**
     * FCM registration tokens are URL-safe base64-ish plus a `:` separator, e.g.
     * `fM1x...:APA91bH...`. Anything shorter than 20 characters is not a real token; anything
     * longer than 2048 is not one either, so it is refused before it reaches the network.
     */
    private val TOKEN_PATTERN = Regex("^[A-Za-z0-9_.:\\-]{20,2048}$")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True only for a string shaped like an FCM registration token. */
    fun isWellFormed(token: String?): Boolean = token != null && TOKEN_PATTERN.matches(token)

    /** The exact payload sent to [CALLABLE]; null when the token must not be sent at all. */
    fun payloadFor(token: String, platform: String = "android"): Map<String, Any>? =
        if (!isWellFormed(token)) null else mapOf("token" to token, "platform" to platform)

    /** Log-safe form of a token: enough to correlate builds, not enough to spoof a device. */
    fun redacted(token: String?): String = when {
        token.isNullOrEmpty() -> "<none>"
        token.length <= 8 -> "***"
        else -> "***" + token.takeLast(6)
    }

    /**
     * Sends [token] to the backend. Returns true only when the callable call completed and the
     * server acknowledged it.
     */
    suspend fun register(token: String): Boolean {
        val payload = payloadFor(token)
        if (payload == null) {
            Log.w(TAG, "Refusing to register a malformed device token")
            return false
        }
        val signedIn = try {
            FirebaseAuth.getInstance().currentUser != null
        } catch (_: Exception) {
            false
        }
        if (!signedIn) {
            // Not an error: the token is refreshed on the next sign-in via registerCurrentToken().
            Log.d(TAG, "No signed-in account; device token not registered yet")
            return false
        }
        return try {
            val result = FirebaseFunctions.getInstance()
                .getHttpsCallable(CALLABLE)
                .call(
                    // R05-P2-06: the payload is validated against the typed contract first.
                    CallableContracts.payload(
                        CALLABLE,
                        "token" to token,
                        "platform" to payload["platform"] as? String
                    )
                )
                .await()
            val acknowledged = (result.data as? Map<*, *>)?.get("success") == true
            Log.d(TAG, "Registered device token ${redacted(token)} (acknowledged=$acknowledged)")
            acknowledged
        } catch (error: Exception) {
            Log.w(TAG, "Device token registration failed (${error::class.java.simpleName})")
            false
        }
    }

    /** Fire-and-forget registration for callback-based call sites (FCM service, ViewModel). */
    fun registerInBackground(token: String) {
        scope.launch { register(token) }
    }

    /** Fetches the current token and registers it; safe to call before sign-in. */
    fun registerCurrentToken() {
        try {
            SkillsExchangeMessagingService.getToken { token ->
                if (token != null) registerInBackground(token)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not read the device token (${error::class.java.simpleName})")
        }
    }
}
