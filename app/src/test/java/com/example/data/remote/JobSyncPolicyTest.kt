package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R07-P2-03: the client never writes money state the server owns.
 *
 * The rules pin these fields; this test pins the client half, so a job mirror cannot be rejected (or,
 * worse, accepted) because the two halves disagree about who owns `escrowStatus`.
 */
class JobSyncPolicyTest {

    private val basePayload = mapOf(
        "id" to "job_1",
        "title" to "Fix the tap",
        "status" to "COMPLETED",
        "escrowStatus" to "PROOF_SUBMITTED",
        "grandTotal" to 55000,
        "budget" to 50000
    )

    @Test
    fun `terminal escrow outcomes are withheld from the client payload`() {
        for (status in JobSyncPolicy.SERVER_ONLY_ESCROW_STATUSES) {
            assertTrue(
                "escrowStatus must be withheld for $status",
                "escrowStatus" in JobSyncPolicy.withheldFields("COMPLETED", status)
            )
            val payload = JobSyncPolicy.clientPayload(
                basePayload + ("escrowStatus" to status),
                "RELEASED",
                status
            )
            assertFalse(payload.containsKey("escrowStatus"))
            assertTrue("the harmless fields are still sent", payload.containsKey("title"))
        }
    }

    @Test
    fun `a non-terminal escrow state is still mirrored`() {
        for (status in listOf("QUOTE_PENDING", "DEPOSIT_PROMPTED", "WORK_IN_PROGRESS", "PROOF_SUBMITTED")) {
            assertFalse(
                "escrowStatus must stay client-mirrored for $status",
                "escrowStatus" in JobSyncPolicy.withheldFields("ACCEPTED_FUNDED", status)
            )
            assertTrue(JobSyncPolicy.clientPayload(basePayload, "ACCEPTED_FUNDED", status).containsKey("escrowStatus"))
        }
    }

    @Test
    fun `priced amounts are withheld once the job is funded`() {
        for (status in listOf("ACCEPTED_FUNDED", "IN_PROGRESS", "COMPLETED", "RELEASED", "DISPUTED")) {
            val withheld = JobSyncPolicy.withheldFields(status, "FUNDS_LOCKED_IN_ESCROW")
            assertTrue("grandTotal withheld for $status", "grandTotal" in withheld)
            assertTrue("budget withheld for $status", "budget" in withheld)
        }
        // While the job is only a request, the owner is still pricing it.
        assertTrue(JobSyncPolicy.withheldFields("REQUESTED", "QUOTE_PENDING").isEmpty())
        assertTrue(JobSyncPolicy.withheldFields("QUOTED", "QUOTE_PENDING").isEmpty())
    }

    @Test
    fun `the job mirror merges and never carries the doorstep PIN`() {
        val service = repoFile("app/src/main/java/com/example/data/remote/FirebaseService.kt").readText()
        val sync = service.substring(
            service.indexOf("suspend fun syncJobToFirestore"),
            service.indexOf("suspend fun syncProviderToFirestore")
        )
        assertTrue("the client payload is filtered", sync.contains("JobSyncPolicy.clientPayload"))
        assertTrue("a merge keeps server-owned fields", sync.contains("SetOptions.merge()"))
        assertFalse("the PIN is never written to the job document", sync.contains("\"doorstepPin\""))
        assertFalse("the PIN hash is never written by the client", sync.contains("doorstepPinHash"))
    }

    @Test
    fun `the rules and the client agree on the server-only escrow states`() {
        val rules = repoFile("firestore.rules").readText()
        for (status in JobSyncPolicy.SERVER_ONLY_ESCROW_STATUSES - setOf("REFUND_PENDING", "REFUNDED")) {
            assertFalse(
                "the rules must not let a client write $status",
                rules.substringAfter("function escrowTransitionAllowed()")
                    .substringBefore("}")
                    .contains(status)
            )
        }
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
