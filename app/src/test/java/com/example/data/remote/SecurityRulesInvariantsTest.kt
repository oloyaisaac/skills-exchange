package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R07: the security invariants a reader can verify from the rule files themselves.
 *
 * The emulator suite (`rules-test/`) executes the rules; this test is the cheap guard that also runs
 * wherever the emulator does not, and it fails on the *shape* of the defect rather than on a single
 * request: a public read reappearing, a server-owned field losing its pin, or an erasure rule
 * disappearing.
 */
class SecurityRulesInvariantsTest {

    private val firestore: String by lazy { repoFile("firestore.rules").readText() }
    private val storage: String by lazy { repoFile("storage.rules").readText() }

    /**
     * The body of the rule block whose header line contains [header], found by brace balance.
     *
     * The opening brace is taken from the header line itself, so path templates like
     * `match /users/{userId} {` are not mistaken for the body.
     */
    private fun block(source: String, header: String): String {
        val start = source.indexOf(header)
        assertTrue("missing rule block: $header", start >= 0)
        val lineEnd = source.indexOf('\n', start).let { if (it < 0) source.length else it }
        val open = source.lastIndexOf('{', lineEnd)
        assertTrue("no rule body on the header line: $header", open in start..lineEnd)

        var index = open
        var depth = 0
        val builder = StringBuilder()
        while (index < source.length) {
            val char = source[index]
            if (char == '{') depth += 1
            if (char == '}') {
                depth -= 1
                if (depth == 0) {
                    builder.append(char)
                    return builder.toString()
                }
            }
            builder.append(char)
            index += 1
        }
        return builder.toString()
    }

    @Test
    fun `no Firestore collection is readable by everyone`() {
        assertEquals(
            "firestore.rules must contain no 'allow read: if true'",
            0,
            Regex("allow read\\s*:\\s*if true").findAll(firestore).count()
        )
        assertFalse(firestore.contains("allow read, write: if true"))
    }

    @Test
    fun `only the public asset prefix is world-readable in Storage`() {
        assertTrue(block(storage, "match /public/{allPaths=**}").contains("allow read: if true"))

        // Walk every match block in the file; only the public prefix may allow an anonymous read.
        val headers = storage.lines().filter { it.contains("match /") && !it.contains("match /b/{bucket}/o") }
        assertTrue("expected several storage blocks", headers.size >= 6)
        for (header in headers) {
            val body = block(storage, header.trim())
            if (body.contains("allow read: if true")) {
                assertTrue("unexpected public read in: $header", header.contains("match /public/"))
            }
        }

        // Avatars specifically: R07-P2-07 closed the public read.
        for (header in listOf("match /avatars/{userId}/{allPaths=**}", "match /users/{userId}/avatars/{allPaths=**}")) {
            val avatarBlock = block(storage, header)
            assertFalse("$header must not be public", avatarBlock.contains("allow read: if true"))
            assertTrue(avatarBlock.contains("allow read: if isAuthenticated()"))
        }
    }

    @Test
    fun `private identity prefixes stay owner-or-admin only`() {
        for (prefix in listOf("kyc", "guarantors", "certificates")) {
            val body = block(storage, "match /users/{userId}/$prefix/{allPaths=**}")
            assertTrue(body.contains("isOwner(userId) || isAdmin()"))
            assertFalse(body.contains("allow read: if true"))
        }
    }

    @Test
    fun `the doorstep PIN is server-held and job money fields are pinned`() {
        val secrets = block(firestore, "match /job_secrets/{jobId}")
        assertTrue(secrets.contains("allow read, write: if false"))

        val jobUpdate = firestore.substring(firestore.indexOf("match /jobs/{jobId}"))
        for (field in listOf("releasedAt", "payoutAmountUgx", "doorstepPinHash")) {
            assertTrue("$field must be pinned", firestore.contains(field))
        }
        assertTrue(jobUpdate.contains("serverOwnedJobFieldsTouched()"))
        assertTrue(jobUpdate.contains("escrowTransitionAllowed()"))
        assertTrue(jobUpdate.contains("jobMoneyPinned()"))
        // The terminal escrow states are server outcomes, never client choices.
        val escrowHelper = block(firestore, "function escrowTransitionAllowed()")
        assertFalse("RELEASED_TO_WORKER must never be an allowed client value",
            escrowHelper.contains("RELEASED_TO_WORKER"))
    }

    @Test
    fun `account erasure stays possible for the owner, with a fresh sign-in`() {
        val users = block(firestore, "match /users/{userId}")
        assertTrue(users.contains("allow delete: if isDocOwner(userId) && hasFreshAuth()"))
        val kyc = block(firestore, "match /worker_kyc/{userId}")
        assertTrue(kyc.contains("allow delete: if isAdmin() || (isDocOwner(userId) && hasFreshAuth())"))
        val training = block(firestore, "match /worker_training/{userId}")
        assertTrue(training.contains("allow delete:"))
        assertTrue(training.contains("hasFreshAuth()"))
    }

    @Test
    fun `privileged fields and the money ledger stay server-owned`() {
        assertTrue(firestore.contains("touchesPrivilegedUserFields()"))
        assertTrue(
            block(firestore, "match /momo_transactions/{txId}").contains("allow create: if false")
        )
        assertTrue(
            block(firestore, "match /momo_transactions/{txId}").contains("allow update: if isAdmin()")
        )
        assertTrue(block(firestore, "match /admin_audit/{auditId}").contains("allow write: if false"))
        assertTrue(block(firestore, "match /refunds/{refundId}").contains("allow write: if false"))
    }

    @Test
    fun `release hardening is switched on in the build`() {
        val gradle = repoFile("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("isMinifyEnabled = true"))
        assertTrue(gradle.contains("isShrinkResources = true"))
        assertTrue(File(repoFile("app/proguard-rules.pro").path).readText().contains("com.example.data.db"))
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertFalse("no cleartext traffic in release", manifest.contains("usesCleartextTraffic=\"true\""))
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
