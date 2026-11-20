package com.example.data.remote

/**
 * R06-P2-01: the result of asking the server to purge an account.
 *
 * Deletion is the one irreversible action in the app, so its outcome is modelled instead of being
 * collapsed into a `Boolean`. The Firebase Auth user is deleted by the client only on [Purged]; every
 * other case leaves the session intact so the person can finish (or retry) later. Previously the
 * client deleted the auth user unconditionally and then reported "partially failed", which left
 * personal records with no owner able to request their removal.
 */
sealed class AccountDeletionOutcome {

    /** Every Firestore document and every Storage object is gone; auth may now be deleted. */
    data class Purged(val storageDeleted: Int, val firestoreDocsDeleted: Int) : AccountDeletionOutcome()

    /** The sign-in is older than the five-minute window, so the server refused. */
    object ReauthRequired : AccountDeletionOutcome()

    /** The server kept the account because something could not be removed. Retriable. */
    data class Incomplete(
        val message: String,
        val storagePending: Int,
        val firestorePending: Int
    ) : AccountDeletionOutcome()

    /** The request never reached a decision (offline, unauthenticated, blocked by rules). */
    data class Failed(val message: String) : AccountDeletionOutcome()

    /** True only when the client may now delete the Firebase Auth user. */
    val mayDeleteAuthUser: Boolean get() = this is Purged
}

/**
 * R06-P2-01: maps a server error to the state the dialog must show.
 *
 * Kept pure and separate from the Firebase call so the decision table can be unit-tested without a
 * network: `failed-precondition` + `REAUTH_REQUIRED` means "verify again", `internal` +
 * `PURGE_INCOMPLETE` means "nothing was lost, try again", anything else is a plain failure.
 */
object AccountDeletionRules {

    const val REAUTH_CODE = "REAUTH_REQUIRED"
    const val INCOMPLETE_CODE = "PURGE_INCOMPLETE"

    fun outcomeOf(
        errorCode: String?,
        message: String?,
        details: Map<String, Any?> = emptyMap()
    ): AccountDeletionOutcome {
        val serverCode = (details["code"] as? String)?.trim()
        val text = message?.trim().orEmpty()
        return when {
            serverCode == REAUTH_CODE -> AccountDeletionOutcome.ReauthRequired
            serverCode == INCOMPLETE_CODE -> AccountDeletionOutcome.Incomplete(
                message = text.ifBlank { "Some of your data could not be removed, so your account was kept." },
                storagePending = (details["storagePending"] as? Number)?.toInt() ?: 0,
                firestorePending = (details["firestorePending"] as? Number)?.toInt() ?: 0
            )
            errorCode == "UNAUTHENTICATED" -> AccountDeletionOutcome.Failed(
                "Your session ended before the deletion finished. Sign in again and retry."
            )
            text.isBlank() -> AccountDeletionOutcome.Failed(
                "We could not reach the server. Check your connection and try again."
            )
            else -> AccountDeletionOutcome.Failed(text)
        }
    }

    /** The message shown under the confirm button; never claims success before the server says so. */
    fun progressLabel(step: DeletionStep): String = when (step) {
        DeletionStep.CONFIRM -> "This removes your account and every photo you uploaded."
        DeletionStep.REAUTH -> "For your safety, verify your identity again before deleting."
        DeletionStep.PURGING -> "Removing your records and photos…"
        DeletionStep.DONE -> "Your account and data were deleted."
        DeletionStep.FAILED -> "Nothing was deleted. You can try again."
    }

    /** True when the dialog must stay open (and must not report success). */
    fun keepsDialogOpen(step: DeletionStep): Boolean = step != DeletionStep.DONE
}

/** What the deletion dialog is doing right now. */
enum class DeletionStep { CONFIRM, REAUTH, PURGING, DONE, FAILED }
