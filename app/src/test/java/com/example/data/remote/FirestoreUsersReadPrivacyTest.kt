package com.example.data.remote

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * C2-1 (Test G): `users/{uid}` privacy invariants in `firestore.rules`.
 *
 * The audit found `allow read: if isAuthenticated();` on `match /users/{userId}`, which let any
 * signed-in user read every account document (phone number, backup contact — a third party's
 * number — and profile details). These tests guard the owner-or-admin shape of that rule.
 *
 * They are deliberately static assertions over the rules source: the Firestore emulator is not
 * available to the JVM unit-test task, so they cannot replace an emulator run. To verify the
 * behaviour end-to-end, run:
 *
 * ```
 * firebase emulators:exec --only firestore "node --test functions/test/*.mjs"
 * ```
 *
 * (or any emulator-backed rules test) with a collection of `users/{uid}` documents and confirm
 * that user A receives PERMISSION_DENIED when reading user B's document, while the owner and an
 * `admin`-claimed user succeed.
 */
@RunWith(JUnit4::class)
class FirestoreUsersReadPrivacyTest {

    private fun rulesFile(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val current = dir ?: throw AssertionError("firestore.rules not found from the test working directory")
            val candidate = File(current, "firestore.rules")
            if (candidate.isFile) return candidate
            dir = current.parentFile
        }
        throw AssertionError("firestore.rules not found above ${System.getProperty("user.dir")}")
    }

    /** The `match /users/{userId} { ... }` block, from its declaration to the next collection rule. */
    private fun usersBlock(): String {
        val text = rulesFile().readText()
        val start = text.indexOf("match /users/{userId}")
        assertTrue("a match /users/{userId} block must exist", start >= 0)
        val nextMatch = text.indexOf("match /providers/", start)
        val end = if (nextMatch > start) nextMatch else text.length
        return text.substring(start, end)
    }

    @Test
    fun `user documents are not readable by every authenticated user`() {
        assertFalse(
            "An authenticated user must not be able to read arbitrary users/{uid} documents",
            usersBlock().contains("allow read: if isAuthenticated();")
        )
    }

    @Test
    fun `user documents are readable by their owner`() {
        assertTrue(
            "The owning user must still be able to read their own document",
            usersBlock().contains("allow read: if isDocOwner(userId) || isAdmin();")
        )
    }

    @Test
    fun `admin read capability is preserved`() {
        val text = rulesFile().readText()
        assertTrue("admin reads must remain possible", usersBlock().contains("isAdmin()"))
        assertTrue("the admin helper must still exist", text.contains("function isAdmin()"))
        assertTrue(text.contains("request.auth.token.admin == true"))
    }

    @Test
    fun `owner-only writes and privileged field protection are unchanged`() {
        val block = usersBlock()
        assertTrue(block.contains("allow create: if isDocOwner(userId);"))
        assertTrue(block.contains("allow update: if isDocOwner(userId) && !touchesPrivilegedUserFields();"))
        assertTrue(
            "the privileged-field guard must still cover the worker authorization fields",
            rulesFile().readText().contains("'workerKycStatus', 'isWorkerActivated', 'isProviderActivated', 'admin', 'trainingCompleted'")
        )
    }
}
