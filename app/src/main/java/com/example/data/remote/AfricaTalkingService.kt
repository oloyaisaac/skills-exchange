package com.example.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// REM-011: the API key is never stored in source control. It is supplied at build time
// (Gradle property or environment variable, see app/build.gradle.kts) and injected into
// BuildConfig. A blank key disables OTP instead of silently shipping a shared credential.
// TODO(REM-011 remainder): move send/verify behind callables so the key never reaches the APK.
const val AFRICASTALKING_USERNAME = "SkillsExchange"
const val AFRICASTALKING_BASE_URL = "https://api.africastalking.com/version1/otps"
const val AFRICASTALKING_VERIFY_URL = "https://api.africastalking.com/version1/otps/verify"

class AfricaTalkingService(
    private val apiKey: String = com.example.BuildConfig.AFRICASTALKING_API_KEY,
    private val username: String = AFRICASTALKING_USERNAME
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val JSON = MediaType.get("application/json; charset=utf-8")

    suspend fun sendOtp(phoneNumber: String, message: String = "Your Skills Exchange verification code is: "): OtpResponse {
        if (apiKey.isBlank()) {
            // Fail closed: no credential was provisioned for this build.
            return OtpResponse.error(0, "Phone verification is not configured for this build")
        }
        return try {
            CoroutineScope(Dispatchers.IO).async {
                val body = JSONObject(mapOf(
                    "username" to username,
                    "phoneNumber" to phoneNumber,
                    "message" to message,
                    "mode" to "sync"
                ))
                val request = Request.Builder()
                    .url(AFRICASTALKING_BASE_URL)
                    .post(RequestBody.create(JSON, body.toString()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Cache-Control", "no-cache")
                    .addHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("$apiKey:".toByteArray()))
                    .build()
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    OtpResponse.success(response.body?.string() ?: "")
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    when (response.code) {
                        in 400..499 -> OtpResponse.clientError(response.code, errorBody)
                        in 500..599 -> OtpResponse.serverError(response.code, errorBody)
                        else -> OtpResponse.error(response.code, errorBody)
                    }
                }
            }.await()
        } catch (e: Exception) {
            Log.d("AfricaTalkingService", "sendOtp error: ${e.message}")
            OtpResponse.exception(e)
        }
    }

    suspend fun verifyOtp(phoneNumber: String, code: String, requestId: String? = null): VerificationResponse {
        if (apiKey.isBlank()) {
            return VerificationResponse.error(0, "Phone verification is not configured for this build")
        }
        return try {
            CoroutineScope(Dispatchers.IO).async {
                val body = JSONObject(mapOf(
                    "phoneNumber" to phoneNumber,
                    "code" to code
                ).also { if (requestId != null && !requestId.isEmpty()) it.put("requestId", requestId) })
                val request = Request.Builder()
                    .url(AFRICASTALKING_VERIFY_URL)
                    .post(RequestBody.create(JSON, body.toString()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Cache-Control", "no-cache")
                    .addHeader("Authorization", "Basic " + java.util.Base64.getEncoder().encodeToString("$apiKey:".toByteArray()))
                    .build()
                val response = client.newCall(request).execute()
                if (response.code == 200) {
                    VerificationResponse.success(response.body?.string() ?: "")
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    when (response.code) {
                        in 400..499 -> VerificationResponse.clientError(response.code, errorBody)
                        in 500..599 -> VerificationResponse.serverError(response.code, errorBody)
                        else -> VerificationResponse.error(response.code, errorBody)
                    }
                }
            }.await()
        } catch (e: Exception) {
            Log.d("AfricaTalkingService", "verifyOtp error: ${e.message}")
            VerificationResponse.exception(e)
        }
    }

    private fun String.toBase64(): String {
        return java.util.Base64.getEncoder().encodeToString(this.toByteArray())
    }

    data class OtpResponse(
        val success: Boolean = false,
        val statusCode: Int = 0,
        val message: String = "",
        val requestId: String? = null,
        val phoneNumber: String? = null,
        val expiresIn: Int? = null,
        val rawResponse: String = ""
    )
    data class VerificationResponse(
        val valid: Boolean = false,
        val statusCode: Int = 0,
        val message: String = "",
        val userId: String? = null,
        val rawResponse: String = ""
    )
}
