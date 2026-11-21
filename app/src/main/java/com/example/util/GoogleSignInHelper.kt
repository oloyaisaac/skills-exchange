package com.example.util

import android.content.Context
import com.example.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions

/**
 * R06-P2-06: Google Sign-In configuration with no project identifier in source.
 *
 * The web client id is supplied by the build (`BuildConfig.GOOGLE_WEB_CLIENT_ID`, resolved from the
 * environment, a Gradle property, or `.env` — see `app/build.gradle.kts`). When it is missing the
 * app must say so and keep the Google button disabled; it must never send a request with an empty
 * `requestIdToken`, which fails later with an error the user cannot act on.
 */
object GoogleSignInHelper {

    /** The configured web client id, or an empty string when the build did not provide one. */
    val WEB_CLIENT_ID: String = BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()

    /** True when a plausible web client id was supplied at build time. */
    val isConfigured: Boolean
        get() = WEB_CLIENT_ID.endsWith(".apps.googleusercontent.com") &&
            !WEB_CLIENT_ID.startsWith("000000000000-")

    /** Shown instead of the normal flow when Google Sign-In is not configured for this build. */
    const val NOT_CONFIGURED_MESSAGE: String =
        "Google Sign-In is not configured in this build. Set GOOGLE_WEB_CLIENT_ID and rebuild, " +
            "or continue with your phone number."

    /** Returns the client, or null when this build has no usable client id. */
    fun getClient(context: Context): GoogleSignInClient? {
        if (!isConfigured) return null
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }
}
