package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R07-P2-01: the doorstep PIN is decided by the server.
 *
 * The defect was a comparison against a caller-supplied "expected" value, so these tests pin two
 * things that can be checked without a device: the server's answer is the only path to "verified",
 * and the UI copy never shows the PIN to the worker.
 */
class DoorstepPinContractTest {

    @Test
    fun `only an accepted callable result counts as verified`() {
        assertTrue(DoorstepPinResult.Verified.isVerified)
        assertFalse(DoorstepPinResult.NotRegistered.isVerified)
        assertFalse(DoorstepPinResult.Incorrect("wrong", 4).isVerified)
        assertFalse(DoorstepPinResult.Locked("wait", 60_000).isVerified)
        assertFalse(DoorstepPinResult.Failed("offline").isVerified)
    }

    @Test
    fun `the server error codes map to the states the dialog knows`() {
        assertTrue(
            DoorstepPinRules.outcomeOf("FAILED_PRECONDITION", "no pin", mapOf("code" to "PIN_NOT_REGISTERED"))
                is DoorstepPinResult.NotRegistered
        )
        val incorrect = DoorstepPinRules.outcomeOf(
            "PERMISSION_DENIED",
            "Incorrect PIN. 3 attempts left.",
            mapOf("code" to "PIN_INCORRECT", "remainingAttempts" to 3)
        ) as DoorstepPinResult.Incorrect
        assertEquals(3, incorrect.remainingAttempts)

        val locked = DoorstepPinRules.outcomeOf(
            "RESOURCE_EXHAUSTED",
            "Too many incorrect PINs. Try again in 15 minutes.",
            mapOf("code" to "PIN_LOCKED", "retryAfterMs" to 900_000)
        ) as DoorstepPinResult.Locked
        assertEquals(900_000L, locked.retryAfterMs)
        assertTrue(locked.message.contains("15"))
    }

    @Test
    fun `an unknown or missing error is a failure, never a success`() {
        assertTrue(DoorstepPinRules.outcomeOf(null, null, emptyMap()) is DoorstepPinResult.Failed)
        assertTrue(DoorstepPinRules.outcomeOf("INTERNAL", "boom", emptyMap()) is DoorstepPinResult.Failed)
        assertTrue(
            DoorstepPinRules.outcomeOf("UNAUTHENTICATED", "unauthenticated", emptyMap())
                is DoorstepPinResult.Failed
        )
        assertTrue(
            DoorstepPinRules.outcomeOf("PERMISSION_DENIED", null, emptyMap())
                is DoorstepPinResult.Failed
        )
    }

    @Test
    fun `a verified result has nothing to show, every other result does`() {
        assertNull(DoorstepPinRules.failureMessage(DoorstepPinResult.Verified))
        for (result in listOf(
            DoorstepPinResult.NotRegistered,
            DoorstepPinResult.Incorrect("Incorrect PIN. 2 attempts left.", 2),
            DoorstepPinResult.Locked("Too many incorrect PINs.", 1_000),
            DoorstepPinResult.Failed("offline")
        )) {
            assertTrue(DoorstepPinRules.failureMessage(result)!!.isNotBlank())
        }
    }

    @Test
    fun `the worker is told to ask for the PIN instead of being shown it`() {
        val workerText = DoorstepPinRules.workerInstruction()
        assertTrue(workerText.contains("Ask the resident"))
        assertFalse("the worker copy must not contain a code", workerText.contains("4829"))
        val residentText = DoorstepPinRules.residentInstruction("4829")
        assertTrue(residentText.contains("4829"))
    }

    @Test
    fun `the client sends only the typed PIN, and the sheet shows it only to the resident`() {
        val service = repoFile("app/src/main/java/com/example/data/remote/FirebaseService.kt").readText()
        assertTrue(service.contains("getHttpsCallable(\"verifyDoorstepPin\")"))
        assertTrue("only jobId and pin are sent", service.contains("\"jobId\" to jobId, \"pin\" to pin"))

        val sheet = repoFile("app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt").readText()
        assertTrue(sheet.contains("DoorstepPinRules.residentInstruction"))
        assertTrue(sheet.contains("DoorstepPinRules.workerInstruction"))
        assertFalse(
            "the raw stored PIN must not be interpolated into the sheet text",
            sheet.contains("\${job.doorstepPin}\"")
        )
    }

    @Test
    fun `the rules keep PIN material out of every client's reach`() {
        val rules = repoFile("firestore.rules").readText()
        val secretsBlock = rules.substringAfter("match /job_secrets/{jobId} {").substringBefore("}")
        assertTrue(secretsBlock.contains("allow read, write: if false"))
        assertTrue(rules.contains("doorstepPinHash"))
        assertTrue(rules.contains("serverOwnedJobFieldsTouched"))
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
