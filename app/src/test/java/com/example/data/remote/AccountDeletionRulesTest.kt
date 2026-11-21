package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * R06-P2-01: the deletion decision table.
 *
 * These are the rules that decide whether the Firebase Auth user may be removed. They are pure, so
 * they can be pinned without a Firebase project — which matters, because the failure they prevent
 * (deleting the identity while personal data remains) cannot be reproduced locally at all.
 */
class AccountDeletionRulesTest {

    @Test
    fun `only a completed purge allows the auth user to be deleted`() {
        assertTrue(AccountDeletionOutcome.Purged(storageDeleted = 3, firestoreDocsDeleted = 9).mayDeleteAuthUser)
        assertFalse(AccountDeletionOutcome.ReauthRequired.mayDeleteAuthUser)
        assertFalse(AccountDeletionOutcome.Incomplete("kept", 1, 0).mayDeleteAuthUser)
        assertFalse(AccountDeletionOutcome.Incomplete("kept", 0, 2).mayDeleteAuthUser)
        assertFalse(AccountDeletionOutcome.Failed("offline").mayDeleteAuthUser)
    }

    @Test
    fun `a stale session is reported as a re-authentication request, not a failure`() {
        val outcome = AccountDeletionRules.outcomeOf(
            errorCode = "FAILED_PRECONDITION",
            message = "Please verify your identity again before deleting your account.",
            details = mapOf("code" to AccountDeletionRules.REAUTH_CODE)
        )
        assertTrue(outcome is AccountDeletionOutcome.ReauthRequired)
        assertFalse(outcome.mayDeleteAuthUser)
    }

    @Test
    fun `an incomplete purge keeps the account and carries the counts`() {
        val outcome = AccountDeletionRules.outcomeOf(
            errorCode = "INTERNAL",
            message = "Deletion stopped: 4 file(s) could not be removed.",
            details = mapOf(
                "code" to AccountDeletionRules.INCOMPLETE_CODE,
                "storagePending" to 4,
                "firestorePending" to 0
            )
        )
        assertTrue(outcome is AccountDeletionOutcome.Incomplete)
        outcome as AccountDeletionOutcome.Incomplete
        assertEquals(4, outcome.storagePending)
        assertEquals(0, outcome.firestorePending)
        assertTrue("the message must explain that the account was kept", outcome.message.isNotBlank())
        assertFalse(outcome.mayDeleteAuthUser)
    }

    @Test
    fun `an expired session, an unreachable server and an unknown error are all failures`() {
        assertTrue(
            AccountDeletionRules.outcomeOf("UNAUTHENTICATED", "unauthenticated", emptyMap())
                is AccountDeletionOutcome.Failed
        )
        val offline = AccountDeletionRules.outcomeOf(null, null, emptyMap())
        assertTrue(offline is AccountDeletionOutcome.Failed)
        assertTrue((offline as AccountDeletionOutcome.Failed).message.contains("could not reach"))
        assertTrue(AccountDeletionRules.outcomeOf("INTERNAL", "boom", emptyMap()) is AccountDeletionOutcome.Failed)
    }

    @Test
    fun `the dialog copy never claims success before the server does`() {
        assertTrue(AccountDeletionRules.keepsDialogOpen(DeletionStep.CONFIRM))
        assertTrue(AccountDeletionRules.keepsDialogOpen(DeletionStep.REAUTH))
        assertTrue(AccountDeletionRules.keepsDialogOpen(DeletionStep.PURGING))
        assertTrue(AccountDeletionRules.keepsDialogOpen(DeletionStep.FAILED))
        assertFalse(AccountDeletionRules.keepsDialogOpen(DeletionStep.DONE))
        assertEquals(
            AccountDeletionRules.progressLabel(DeletionStep.FAILED),
            "Nothing was deleted. You can try again."
        )
        DeletionStep.values().forEach { step ->
            assertTrue("every step needs copy", AccountDeletionRules.progressLabel(step).isNotBlank())
        }
    }

    @Test
    fun `the client never deletes the auth user before the server purge`() {
        val source = repoFile("app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt").readText()
        val body = source.substring(
            source.indexOf("fun permanentlyDeleteUserAccountAndData()"),
            source.indexOf("fun reauthenticateForAccountDeletionWithGoogle")
        )
        val purgeAt = body.indexOf("requestAccountDeletion")
        val authAt = body.indexOf("deleteCurrentUser")
        assertTrue("the server purge must be requested", purgeAt >= 0)
        assertTrue("the auth user is deleted only after the purge is requested", purgeAt < authAt)
        // The auth deletion must sit inside the success branch, i.e. after Purged is matched.
        val purgedAt = body.indexOf("AccountDeletionOutcome.Purged")
        assertTrue("the purge result must be branched on", purgedAt in 0 until authAt)
        // And the old unconditional pair is gone.
        assertFalse(
            "deleteUserFirestoreData no longer exists on the client",
            source.contains("deleteUserFirestoreData")
        )
    }

    @Test
    fun `deletion no longer wipes local data before the server agrees`() {
        val source = repoFile("app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt").readText()
        val body = source.substring(
            source.indexOf("fun permanentlyDeleteUserAccountAndData()"),
            source.indexOf("fun reauthenticateForAccountDeletionWithGoogle")
        )
        val clearAt = body.indexOf("clearLocalStateAfterSignOut()")
        val purgedAt = body.indexOf("AccountDeletionOutcome.Purged")
        assertTrue("local state is cleared", clearAt >= 0)
        assertTrue("and only inside the successful branch", purgedAt in 0 until clearAt)
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
