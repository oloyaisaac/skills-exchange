package com.example.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * REM-011 / REM-022: phone verification now runs entirely in Cloud Functions.
 *
 * The Africa's Talking credential lives in the Functions environment, so nothing secret is
 * compiled into the APK. `sendOtp` asks the backend to send a code; `verifyOtp` asks the backend
 * to verify it and returns a Firebase custom token, which is exchanged here with
 * `signInWithCustomToken`. A successful verification therefore produces a real Firebase UID.
 */
class AfricaTalkingService(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    suspend fun sendOtp(phoneNumber: String): OtpResponse {
        return try {
            val result = functions
                .getHttpsCallable("sendOtp")
                .call(CallableContracts.payload("sendOtp", "phoneNumber" to phoneNumber))
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: emptyMap()
            OtpResponse.Success(
                requestId = data["requestId"] as? String,
                expiresIn = (data["expiresIn"] as? Number)?.toInt()
            )
        } catch (e: Exception) {
            Log.d("AfricaTalkingService", "sendOtp failed: ${e.message}")
            OtpResponse.Error(e.userMessage("Could not send the verification code"))
        }
    }

    suspend fun verifyOtp(phoneNumber: String, code: String, requestId: String? = null): VerificationResponse {
        // `requestId` is kept for call-site compatibility; the backend binds the challenge to the
        // phone number and ignores any client-supplied request id.
        return try {
            val result = functions
                .getHttpsCallable("verifyOtp")
                .call(
                    CallableContracts.payload(
                        "verifyOtp",
                        "phoneNumber" to phoneNumber,
                        "code" to code
                    )
                )
                .await()

            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: emptyMap()
            val token = data["token"] as? String
                ?: return VerificationResponse.Error("Verification did not return a session")
            val uid = data["uid"] as? String

            // REM-022: the only way this flow signs a user in is Firebase's own credential exchange.
            auth.signInWithCustomToken(token).await()
            VerificationResponse.Valid(userId = auth.currentUser?.uid ?: uid)
        } catch (e: Exception) {
            Log.d("AfricaTalkingService", "verifyOtp failed: ${e.message}")
            VerificationResponse.Error(e.userMessage("That code is not valid"))
        }
    }

    private fun Exception.userMessage(fallback: String): String {
        val message = message?.takeIf { it.isNotBlank() } ?: return fallback
        return message
    }

    sealed class OtpResponse {
        data class Success(
            val requestId: String? = null,
            val expiresIn: Int? = null
        ) : OtpResponse()

        data class Error(
            val message: String,
            val statusCode: Int = 0
        ) : OtpResponse()
    }

    sealed class VerificationResponse {
        data class Valid(
            val userId: String? = null
        ) : VerificationResponse()

        data class Error(
            val message: String,
            val statusCode: Int = 0
        ) : VerificationResponse()
    }
}
