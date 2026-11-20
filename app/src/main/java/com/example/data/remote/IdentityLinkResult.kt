package com.example.data.remote

/**
 * R06-P2-03: the outcome of attaching a Google identity to the account that is already signed in.
 *
 * The review's failure condition was "one person can silently hold two accounts". Linking is the
 * fix; a collision means the Google account already belongs to a *different* Firebase uid, which the
 * person must resolve explicitly (sign in with Google and link the phone there, or use another
 * Google account) rather than being shuffled into a second wallet by a silent `signInWithCredential`.
 */
sealed class IdentityLinkResult {

    /** The Google provider is now attached to this uid. */
    object Linked : IdentityLinkResult()

    /** This Google account is already attached to a different uid. */
    data class Collision(val detail: String?) : IdentityLinkResult()

    /** Nobody is signed in, so there is nothing to link to. */
    object NotSignedIn : IdentityLinkResult()

    data class Failed(val message: String) : IdentityLinkResult()

    /** True only when the provider was actually attached. */
    val isLinked: Boolean get() = this is Linked
}

/**
 * R06-P2-03 / R06-P2-05: user-facing text for linking and re-authentication failures.
 *
 * Pure functions so the wording (and the rule that a collision is never reported as success) can be
 * unit-tested without Firebase.
 */
object IdentityLinkRules {

    /** Message for a failed link; a collision gets an actionable explanation, not a raw error. */
    fun linkMessage(result: IdentityLinkResult): String = when (result) {
        is IdentityLinkResult.Linked -> "Google sign-in is now linked to this account."
        is IdentityLinkResult.Collision ->
            "That Google account already belongs to a different Skills Exchange account. " +
                "Sign out and sign in with Google to use it, or choose another Google account."
        is IdentityLinkResult.NotSignedIn -> "Sign in first, then link Google sign-in."
        is IdentityLinkResult.Failed -> result.message
    }

    /** Message for a failed re-authentication attempt. */
    fun reauthMessage(error: Throwable?): String {
        val text = error?.message?.trim().orEmpty()
        return text.ifBlank { "We could not verify it was you. Please try again." }
    }
}
