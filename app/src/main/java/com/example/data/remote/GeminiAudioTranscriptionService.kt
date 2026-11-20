package com.example.data.remote

import android.util.Base64
import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

data class TranscribedVoiceJob(
    val fullTranscript: String,
    val suggestedTitle: String,
    val suggestedCategory: String,
    val suggestedBudget: Double,
    val suggestedDescription: String
)

/**
 * REM-051: voice transcription is performed by the `transcribeVoiceJob` callable.
 *
 * The Gemini credential is no longer compiled into the APK, and a missing key, network failure or
 * unparseable model response now produces `null` (surfaced as an error by the ViewModel) instead
 * of a fabricated "example" job that the user could accidentally post.
 */
class GeminiAudioTranscriptionService(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance()
) {

    suspend fun transcribeAudio(
        audioByteArray: ByteArray,
        mimeType: String = "audio/mp4"
    ): TranscribedVoiceJob? {
        if (audioByteArray.isEmpty()) return null

        return try {
            val audioBase64 = Base64.encodeToString(audioByteArray, Base64.NO_WRAP)
            // R05-P2-07: transcription is read-only, so a transient failure is retried twice.
            val result = CallableRetry.withRetry {
                functions
                    .getHttpsCallable("transcribeVoiceJob")
                    .call(
                        CallableContracts.payload(
                            "transcribeVoiceJob",
                            "audioBase64" to audioBase64,
                            "mimeType" to mimeType
                        )
                    )
                    .await()
            }

            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: return null
            val transcript = data["fullTranscript"] as? String
                ?: return null
            val category = data["suggestedCategory"] as? String
                ?: return null
            val budget = (data["suggestedBudget"] as? Number)?.toDouble()
                ?: return null

            TranscribedVoiceJob(
                fullTranscript = transcript,
                suggestedTitle = data["suggestedTitle"] as? String ?: transcript.take(45),
                suggestedCategory = category,
                suggestedBudget = budget,
                suggestedDescription = data["suggestedDescription"] as? String ?: transcript
            )
        } catch (e: Exception) {
            Log.w("GeminiAudioService", "Voice transcription unavailable: ${e.message}")
            null
        }
    }
}
