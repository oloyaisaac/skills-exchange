package com.example.data.remote

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * PAY-002 acceptance test 15: provider credentials must never appear in Android code
 * (and therefore never in the APK or in logcat — the app has no code path that could
 * print them).
 *
 * This is a source-level scan of `app/src/main/java` for the MTN gateway's
 * credential names, auth-header names, token endpoint and gateway host. The PAY-001
 * trust boundary removed every device-side gateway call; this test pins that state
 * and fails if anyone ever reintroduces a credential, an auth header, or a gateway
 * URL into the Android source tree. Server-side files (`functions/`) legitimately
 * contain these strings and are deliberately NOT scanned.
 */
class MoMoCredentialLeakSecurityTest {

    /**
     * Case-sensitive markers that would indicate gateway credentials, authentication
     * material, or live gateway usage inside the Android app. All are absent from the
     * app by design: configuration (env names like MTN_API_KEY) belongs exclusively to
     * the backend's `loadMomoConfig`.
     */
    private val bannedMarkers = listOf(
        "Ocp-Apim-Subscription-Key", // MTN subscription-key header name
        "requesttopay",              // gateway endpoint path (lowercase = real usage, not prose)
        "/collection/token",         // token-exchange endpoint path
        "sandbox.momodeveloper.mtn.com", // gateway host
        "MTN_API_KEY",               // credential env/config name
        "MTN_API_USER",              // credential env/config name
        "MTN_SUBSCRIPTION_KEY",      // credential env/config name
        "MOMO_WEBHOOK_SECRET"        // webhook secret name
    )

    private fun resolveAndroidSourceRoot(): File {
        // Gradle runs unit tests with the working directory set to the `app` module.
        val candidates = listOf(
            File("src/main/java"),
            File("../app/src/main/java"),
            File("app/src/main/java")
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate app/src/main/java from ${File(".").absolutePath}")
    }

    @Test
    fun `no MTN gateway credential, auth header, endpoint or host appears anywhere in Android sources`() {
        val sourceRoot = resolveAndroidSourceRoot()
        val kotlinFiles = sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("Expected Kotlin sources under $sourceRoot", kotlinFiles.isNotEmpty())

        val violations = mutableListOf<String>()
        for (file in kotlinFiles) {
            val content = file.readText()
            for (marker in bannedMarkers) {
                if (content.contains(marker)) {
                    violations += "${file.relativeTo(sourceRoot)}: contains \"$marker\""
                }
            }
        }

        assertTrue(
            "Android sources must never contain MTN gateway credentials/endpoints:\n" +
                violations.joinToString("\n"),
            violations.isEmpty()
        )
    }
}
