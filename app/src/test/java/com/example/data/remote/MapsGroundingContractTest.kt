package com.example.data.remote

import com.google.firebase.functions.FirebaseFunctionsException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * R05-P2-04 / R05-P2-05: nearby places are grounded or they are not shown.
 *
 * The client used to embed the Gemini key in the APK and answered every search from a hard-coded
 * list of invented shops with invented star ratings and phone numbers. These tests pin the two
 * replacements: the key never appears in app source, and only a place the backend marked as
 * grounded is ever rendered as a place.
 */
@RunWith(JUnit4::class)
class MapsGroundingContractTest {

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(8) {
            val current = dir ?: error("$relative not found")
            val candidate = File(current, relative)
            if (candidate.exists()) return candidate
            dir = current.parentFile
        }
        throw AssertionError("$relative not found above ${System.getProperty("user.dir")}")
    }

    private fun appSources(): List<File> =
        repoFile("app/src/main/java/com/example").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private val groundedResponse = mapOf(
        "summary" to "Two suppliers near you.",
        "grounded" to true,
        "places" to listOf(
            mapOf(
                "name" to "Kampala Hardware Ltd",
                "address" to "Plot 12 Luwum Street, Kampala",
                "uri" to "https://maps.google.com/?cid=111"
            )
        )
    )

    @Test
    fun `the Gemini key is no longer referenced by any app source file`() {
        val offenders = appSources().filter { it.readText().contains("GEMINI_API_KEY") }.map { it.name }
        assertEquals("the Gemini key must stay server-side: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `the Gemini key is not injected into the build either`() {
        val env = repoFile(".env.example").readText()
        val declaresKey = env.lines().any { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") && trimmed.startsWith("GEMINI_API_KEY=")
        }
        assertFalse("GEMINI_API_KEY in .env means a BuildConfig field in the APK", declaresKey)
    }

    @Test
    fun `the client calls the authenticated callable instead of the Gemini endpoint`() {
        val source = repoFile("app/src/main/java/com/example/data/remote/GeminiMapsGroundingService.kt").readText()
        assertFalse(source.contains("generativelanguage.googleapis.com"))
        assertTrue(source.contains("searchLocalPlaces"))
        assertTrue(source.contains("CallableContracts.payload"))
    }

    @Test
    fun `the invented shop list is gone`() {
        val source = repoFile("app/src/main/java/com/example/data/remote/GeminiMapsGroundingService.kt").readText()
        assertFalse(source.contains("Central Hardware & Building Supply"))
        assertFalse(source.contains("Sunnyside Electrical & Plumbing Depot"))
        assertFalse(source.contains("Express Motor Parts & Battery Center"))
        assertFalse("no hard-coded phone numbers", source.contains("+256 700 882 110"))
        assertFalse("no invented ratings", source.contains("4.8 ★"))
    }

    @Test
    fun `a grounded response becomes real place cards`() {
        val result = GeminiMapsGroundingService.parseResponse(groundedResponse)
        assertTrue(result.isGrounded)
        assertEquals(1, result.places.size)
        val place = result.places.first()
        assertEquals("Kampala Hardware Ltd", place.name)
        assertEquals("Plot 12 Luwum Street, Kampala", place.address)
        assertEquals("https://maps.google.com/?cid=111", place.mapsUrl)
        assertTrue(place.isGrounded)
        assertEquals("", place.rating)
        assertEquals("", place.phone)
    }

    @Test
    fun `an ungrounded answer renders no place cards at all`() {
        val result = GeminiMapsGroundingService.parseResponse(
            mapOf(
                "summary" to "Try the market on Saturday.",
                "grounded" to false,
                "places" to emptyList<Map<String, Any>>()
            )
        )
        assertFalse(result.isGrounded)
        assertTrue(result.places.isEmpty())
        assertEquals("Try the market on Saturday.", result.summary)
    }

    @Test
    fun `a place without a Google Maps uri is dropped`() {
        val result = GeminiMapsGroundingService.parseResponse(
            mapOf(
                "grounded" to true,
                "places" to listOf(
                    mapOf("name" to "Ghost Shop", "address" to "Nowhere"),
                    mapOf("name" to "", "uri" to "https://maps.google.com/?cid=9")
                )
            )
        )
        assertTrue(result.places.isEmpty())
        assertFalse("grounded is only true when a place survived", result.isGrounded)
    }

    @Test
    fun `malformed payloads degrade to nothing found, never to fake data`() {
        for (payload in listOf(null, "", 42, mapOf("places" to "nope"))) {
            val result = GeminiMapsGroundingService.parseResponse(payload)
            assertTrue(result.places.isEmpty())
            assertFalse(result.isGrounded)
            assertNotNull(result.summary)
        }
    }

    @Test
    fun `callable failures produce a message, never a fallback shop`() {
        val states = listOf(
            FirebaseFunctionsException.Code.UNAUTHENTICATED,
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.FAILED_PRECONDITION,
            FirebaseFunctionsException.Code.INVALID_ARGUMENT,
            FirebaseFunctionsException.Code.INTERNAL
        )
        for (code in states) {
            val message = GeminiMapsGroundingService.messageFor(code)
            assertTrue("$code must have a message", message.isNotBlank())
            assertFalse(message.contains("Hardware"))
        }
        assertTrue(
            "an unknown failure still says something useful",
            GeminiMapsGroundingService.messageFor(null).isNotBlank()
        )
    }

    @Test
    fun `the search modal labels unverified answers`() {
        val source = repoFile("app/src/main/java/com/example/ui/components/SearchModal.kt").readText()
        assertTrue(source.contains("Not verified on Google Maps"))
        assertTrue(source.contains("Verified on Google Maps"))
    }
}
