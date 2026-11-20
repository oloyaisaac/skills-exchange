package com.example.data.remote

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * R05-P2-02: the device token reaches the backend and is never logged.
 *
 * Before the fix `sendTokenToServer` only wrote the token to logcat and the backend had no per-device
 * address at all; topic subscription was the only channel. These tests pin the token shape check,
 * the redaction used in logs, and the sanitising of push payload data that used to be copied
 * straight into an Intent extra.
 */
@RunWith(JUnit4::class)
class PushRegistrationTest {

    /** A realistic FCM registration token: 22-char instance id, ':', then the token body. */
    private val realToken =
        "fM1xQ2bTa0k:APA91bH4dO0eQ6r8jK2mN5pR7sT9uV1wX3yZ5aB7cD9eF1gH3iJ5kL7mN9oP1qR3sT5uV7wX9yZ"

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

    @Test
    fun `a real token is accepted`() {
        assertTrue(PushTokenRegistrar.isWellFormed(realToken))
    }

    @Test
    fun `short, blank and malformed tokens are refused`() {
        for (candidate in listOf(null, "", "   ", "abc", "short token with spaces", "x".repeat(4097))) {
            assertFalse("must refuse: $candidate", PushTokenRegistrar.isWellFormed(candidate))
        }
    }

    @Test
    fun `the registration payload carries the token and the platform`() {
        val payload = PushTokenRegistrar.payloadFor(realToken)
        assertEquals(realToken, payload!!["token"])
        assertEquals("android", payload["platform"])
        assertNull(PushTokenRegistrar.payloadFor("abc"))
    }

    @Test
    fun `the payload matches the typed callable contract`() {
        val payload = PushTokenRegistrar.payloadFor(realToken)!!
        val validated = CallableContracts.payload(
            PushTokenRegistrar.CALLABLE,
            "token" to payload["token"],
            "platform" to payload["platform"]
        )
        assertEquals(realToken, validated["token"])
    }

    @Test
    fun `a logged token is redacted to its last six characters`() {
        val redacted = PushTokenRegistrar.redacted(realToken)
        assertTrue(redacted.endsWith(realToken.takeLast(6)))
        assertFalse("the instance id must not appear in logs", redacted.contains("fM1xQ2bTa0k"))
        assertTrue("redacted form must stay short", redacted.length <= 9)
        assertEquals("<none>", PushTokenRegistrar.redacted(null))
        assertEquals("***", PushTokenRegistrar.redacted("abc"))
    }

    @Test
    fun `the messaging service never logs the raw token`() {
        val source = repoFile("app/src/main/java/com/example/data/remote/SkillsExchangeMessagingService.kt").readText()
        assertFalse(source.contains("Refreshed FCM token: \$token"))
        assertFalse("the old log-only uploader must be gone", source.contains("sendTokenToServer"))
        assertTrue(source.contains("PushTokenRegistrar.registerInBackground(token)"))
        assertTrue(source.contains("PushTokenRegistrar.redacted(token)"))
    }

    @Test
    fun `a job id that is not job-shaped is dropped from the Intent`() {
        assertEquals("job_1729", PushPayloadSanitiser.sanitiseJobId("job_1729"))
        assertEquals("", PushPayloadSanitiser.sanitiseJobId("job_1 extra --flags"))
        assertEquals("", PushPayloadSanitiser.sanitiseJobId("../etc/passwd"))
        assertEquals("", PushPayloadSanitiser.sanitiseJobId("x".repeat(65)))
        assertEquals("", PushPayloadSanitiser.sanitiseJobId(null))
    }

    @Test
    fun `a notification type is upper snake case or GENERAL`() {
        assertEquals("PAYMENT_STATUS", PushPayloadSanitiser.sanitiseType("payment_status"))
        assertEquals("GENERAL", PushPayloadSanitiser.sanitiseType("has spaces"))
        assertEquals("GENERAL", PushPayloadSanitiser.sanitiseType(null))
        // A long but well-shaped value is truncated, never passed through at full length.
        assertTrue(PushPayloadSanitiser.sanitiseType("A".repeat(40)).length <= 32)
    }

    @Test
    fun `display text is clipped so a hostile payload cannot flood the shade`() {
        val clipped = PushPayloadSanitiser.clip("x".repeat(600), PushPayloadSanitiser.MAX_BODY_LENGTH)
        assertEquals(PushPayloadSanitiser.MAX_BODY_LENGTH, clipped.length)
        assertTrue(clipped.endsWith("\u2026"))
        assertEquals("Skills Exchange", PushPayloadSanitiser.clip("  ", 50, "Skills Exchange"))
        assertEquals("hello", PushPayloadSanitiser.clip(" hello ", 50))
    }
}
