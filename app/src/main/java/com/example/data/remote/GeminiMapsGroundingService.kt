package com.example.data.remote

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

/**
 * R05-P2-05: one nearby place.
 *
 * Only fields Google actually returned are ever filled. The previous implementation hard-coded a
 * list of invented shops complete with star ratings and phone numbers and displayed them as live
 * Google Maps results; [isGrounded] exists so the UI can never present model prose as a business.
 */
data class GroundedPlaceResult(
    val name: String,
    val address: String,
    val rating: String = "",
    val phone: String = "",
    val category: String = "",
    val description: String = "",
    val mapsUrl: String? = null,
    val isGrounded: Boolean = false
)

/**
 * Result of a nearby-supplier search.
 *
 * [isGrounded] is true only when the backend returned at least one place from Google Maps grounding
 * metadata. When it is false the caller must show [summary] as an unverified AI answer and must not
 * render any place card at all.
 */
data class MapsGroundingResult(
    val summary: String,
    val places: List<GroundedPlaceResult> = emptyList(),
    val isGrounded: Boolean = false,
    val error: String? = null
)

/**
 * R05-P2-04 / R05-P2-05 / R05-P2-07: nearby-supplier search.
 *
 * The Gemini credential is no longer in the APK: this class calls the authenticated
 * `searchLocalPlaces` callable, which holds the key server-side. There is no client-side fallback
 * content — a failure returns an error result, never fabricated shops.
 */
class GeminiMapsGroundingService {

    companion object {
        private const val TAG = "GeminiMapsService"
        const val CALLABLE = "searchLocalPlaces"
        const val MAX_QUERY_LENGTH = 200
        const val MAX_DISTRICT_LENGTH = 80

        /** User-safe message per callable failure; never leaks the underlying exception text. */
        fun messageFor(code: FirebaseFunctionsException.Code?): String = when (code) {
            FirebaseFunctionsException.Code.UNAUTHENTICATED ->
                "Sign in to search nearby places."
            FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                "Nearby place search is not configured."
            FirebaseFunctionsException.Code.UNAVAILABLE ->
                "Nearby place search is temporarily unavailable. Please try again."
            FirebaseFunctionsException.Code.INVALID_ARGUMENT ->
                "Enter a more specific search, for example \"cement and PVC pipes\"."
            else -> "Nearby place search is unavailable right now."
        }

        /** Request payload; the backend re-validates both fields. */
        fun payloadFor(query: String, district: String): Map<String, Any> = mapOf(
            "query" to query.trim(),
            "district" to district.trim().ifEmpty { "Kampala" }
        )

        /**
         * Parses the callable response. Anything that is not a grounded Maps place is dropped, so a
         * malformed or unexpected payload degrades to "nothing found" instead of a fake shop.
         */
        fun parseResponse(data: Any?): MapsGroundingResult {
            val map = data as? Map<*, *>
                ?: return MapsGroundingResult(
                    summary = "No verified places were found nearby.",
                    isGrounded = false
                )
            val summary = (map["summary"] as? String).orEmpty()
            val grounded = map["grounded"] == true
            val rawPlaces = map["places"] as? List<*>
            val places = rawPlaces.orEmpty().mapNotNull { raw ->
                val place = raw as? Map<*, *> ?: return@mapNotNull null
                val name = (place["name"] as? String)?.trim().orEmpty()
                val uri = (place["uri"] as? String)?.trim().orEmpty()
                if (name.isEmpty() || uri.isEmpty()) return@mapNotNull null
                GroundedPlaceResult(
                    name = name,
                    address = (place["address"] as? String).orEmpty(),
                    mapsUrl = uri,
                    isGrounded = true
                )
            }
            return MapsGroundingResult(
                summary = summary.ifEmpty {
                    if (places.isEmpty()) "No verified places were found nearby." else "Nearby places found on Google Maps."
                },
                places = places,
                isGrounded = grounded && places.isNotEmpty()
            )
        }
    }

    /**
     * Searches for real businesses near [district]. Never throws: every failure is returned as a
     * [MapsGroundingResult] carrying a user-safe [MapsGroundingResult.error].
     */
    suspend fun searchLocalPlacesWithMaps(query: String, district: String): MapsGroundingResult {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return MapsGroundingResult(
                summary = "",
                error = "Type at least two characters to search."
            )
        }
        if (trimmed.length > MAX_QUERY_LENGTH || district.trim().length > MAX_DISTRICT_LENGTH) {
            return MapsGroundingResult(summary = "", error = "That search is too long.")
        }
        return try {
            // R05-P2-06: validated typed payload. R05-P2-07: only the read-only search is retried.
            val result = CallableRetry.withRetry {
                FirebaseFunctions.getInstance()
                    .getHttpsCallable(CALLABLE)
                    .call(
                        CallableContracts.payload(
                            CALLABLE,
                            "query" to trimmed,
                            "district" to district.trim().ifEmpty { "Kampala" }
                        )
                    )
                    .await()
            }
            parseResponse(result.data)
        } catch (error: FirebaseFunctionsException) {
            Log.w(TAG, "Nearby place search failed (${error.code})")
            MapsGroundingResult(summary = "", error = messageFor(error.code))
        } catch (error: Exception) {
            Log.w(TAG, "Nearby place search failed (${error::class.java.simpleName})")
            MapsGroundingResult(
                summary = "",
                error = "Nearby place search is unavailable right now."
            )
        }
    }
}
