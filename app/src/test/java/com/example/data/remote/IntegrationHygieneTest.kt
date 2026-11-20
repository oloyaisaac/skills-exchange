package com.example.data.remote

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * R05 extras carried by the PRD (REM-033, REM-052) alongside the seven review findings.
 *
 * REM-033 — Airtel Money and card were selectable payment channels with no integration behind them:
 * choosing one wrote an escrow record claiming a provider that was never called.
 * REM-052 — the Africa's Talking client used to create `CoroutineScope(Dispatchers.IO).async(...)`
 * per call, so a cancelled screen kept the request alive. It is now suspend-only.
 *
 * Both are static assertions over source: they prove the shape of the code, not provider behaviour.
 */
@RunWith(JUnit4::class)
class IntegrationHygieneTest {

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

    private fun read(relative: String): String = repoFile(relative).readText()

    @Test
    fun `only integrated payment channels can be selected`() {
        val source = read("app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt")
        assertTrue("MTN MoMo stays selectable", source.contains("Triple(\"MTN_MOMO\", \"MTN MoMo\""))
        assertFalse("Airtel has no integration", source.contains("Triple(\"AIRTEL_MONEY\""))
        assertFalse("card has no integration", source.contains("Triple(\"CARD\""))
    }

    @Test
    fun `the seeded demo jobs do not claim an unsupported provider`() {
        val source = read("app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt")
        assertFalse(
            "seeded jobs must not claim Airtel Money payments",
            source.contains("paymentChannel = \"AIRTEL_MONEY\"")
        )
    }

    @Test
    fun `the Africa's Talking client is suspend-only`() {
        val source = read("app/src/main/java/com/example/data/remote/AfricaTalkingService.kt")
        assertFalse("no ad-hoc coroutine scopes", source.contains("CoroutineScope("))
        assertTrue("sendOtp is a suspend function", source.contains("suspend fun sendOtp("))
        assertTrue("verifyOtp is a suspend function", source.contains("suspend fun verifyOtp("))
    }

    @Test
    fun `every integration client goes through the typed callable contracts`() {
        val clients = listOf(
            "app/src/main/java/com/example/data/remote/AfricaTalkingService.kt",
            "app/src/main/java/com/example/data/remote/FirebaseService.kt",
            "app/src/main/java/com/example/data/remote/GeminiAudioTranscriptionService.kt",
            "app/src/main/java/com/example/data/remote/GeminiMapsGroundingService.kt",
            "app/src/main/java/com/example/data/remote/PushTokenRegistrar.kt"
        )
        for (client in clients) {
            val source = read(client)
            if (!source.contains("getHttpsCallable")) continue
            assertTrue("$client must validate its payload", source.contains("CallableContracts.payload"))
            assertEquals(
                "$client must not build a raw map payload",
                false,
                source.contains(".call(mapOf")
            )
        }
    }
}
