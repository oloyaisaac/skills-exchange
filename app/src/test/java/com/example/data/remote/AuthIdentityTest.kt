package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R06: identity linking, session hygiene and build configuration.
 *
 * Live Firebase behaviour cannot be exercised here (no project, no device), so these tests pin the
 * properties that can be read from source: the client asks Firebase to *link* rather than silently
 * sign in, sign-out removes the push token before the session ends, and no OAuth client id is
 * compiled into the repository.
 */
class AuthIdentityTest {

    @Test
    fun `a Google identity is linked to the signed-in account, never silently swapped`() {
        val service = repoFile("app/src/main/java/com/example/data/remote/FirebaseService.kt").readText()
        assertTrue("linkWithCredential must be used", service.contains("linkWithCredential"))
        assertTrue("re-authentication must be available", service.contains("reauthenticate(credential)"))
        // Linking lives in its own function, so a collision can be reported separately.
        assertTrue(service.contains("suspend fun linkGoogleAccount("))
        // A collision must be its own outcome rather than a generic failure.
        assertTrue(service.contains("IdentityLinkResult.Collision"))
    }

    @Test
    fun `a link collision is never reported as success`() {
        val linked = IdentityLinkRules.linkMessage(IdentityLinkResult.Linked)
        val collision = IdentityLinkRules.linkMessage(IdentityLinkResult.Collision("in use"))
        assertTrue(linked.contains("linked"))
        assertFalse("a collision must not read as success", collision.contains("now linked"))
        assertTrue("a collision must explain what to do", collision.contains("different Skills Exchange account"))
        assertTrue(IdentityLinkRules.linkMessage(IdentityLinkResult.NotSignedIn).contains("Sign in first"))
        assertEquals(
            "custom message",
            IdentityLinkRules.linkMessage(IdentityLinkResult.Failed("custom message"))
        )
        assertTrue(IdentityLinkRules.reauthMessage(null).isNotBlank())
        assertTrue(IdentityLinkRules.reauthMessage(Exception("wrong code")).contains("wrong code"))
    }

    @Test
    fun `the push token is deleted before the session ends on logout`() {
        val source = repoFile("app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt").readText()
        val logout = source.substring(
            source.indexOf("fun logoutUser()"),
            source.indexOf("fun logoutUser()") + 2600
        )
        val tokenAt = logout.indexOf("deleteDevicePushToken()")
        val signOutAt = logout.indexOf("firebaseService.signOut()")
        assertTrue("logout must delete the device token", tokenAt >= 0)
        assertTrue("and do it while the session is still valid", tokenAt < signOutAt)

        val service = repoFile("app/src/main/java/com/example/data/remote/FirebaseService.kt").readText()
        assertTrue("the server token record is removed too", service.contains("clearMyPushTokens"))
        assertTrue(
            "the token is deleted locally as well",
            source.contains("SkillsExchangeMessagingService.deleteToken")
        )
    }

    @Test
    fun `no Google OAuth client id is hardcoded in source`() {
        val helper = repoFile("app/src/main/java/com/example/util/GoogleSignInHelper.kt").readText()
        assertFalse(
            "the client id must come from BuildConfig, not a literal",
            helper.contains(".apps.googleusercontent.com\"")
        )
        assertTrue(helper.contains("BuildConfig.GOOGLE_WEB_CLIENT_ID"))
        assertTrue("an unconfigured build must fail closed", helper.contains("isConfigured"))

        val gradle = repoFile("app/build.gradle.kts").readText()
        assertTrue("the field is injected at build time", gradle.contains("GOOGLE_WEB_CLIENT_ID"))
        assertTrue("and documented for local builds", repoFile(".env.example").readText().contains("GOOGLE_WEB_CLIENT_ID"))

        // No source file may carry a client id literal (the `isConfigured` suffix check is fine).
        val clientIdLiteral = Regex("\\d{6,}-[a-z0-9]+\\.apps\\.googleusercontent\\.com")
        val offenders = repoFile("app/src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> clientIdLiteral.containsMatchIn(file.readText()) }
            .map { it.name }
            .toList()
        assertTrue("Unexpected client id in: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the deletion callable is registered with a typed contract`() {
        val names = CallableContracts.ALL.map { it.name }
        assertTrue(names.contains("deleteMyAccount"))
        assertTrue(names.contains("clearMyPushTokens"))
        val payload = CallableContracts.payload("deleteMyAccount", "jobIds" to listOf("job_1"))
        assertEquals(listOf("job_1"), payload["jobIds"])
        // An empty token means "all of them" and is simply dropped from the wire payload.
        assertTrue(CallableContracts.payload("clearMyPushTokens", "token" to null).isEmpty())
    }

    private fun repoFile(relative: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return File(dir, relative)
            dir = dir.parentFile
        }
        return File(relative)
    }
}
