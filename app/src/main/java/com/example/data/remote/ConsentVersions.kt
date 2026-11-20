package com.example.data.remote

/**
 * R09-P2-02: the policy versions the app writes into consent receipts.
 *
 * These are the client's copy of the server constants in `functions/src/compliance.ts`
 * (`CONSENT_VERSION`) and are checked against that file by
 * `scripts/check_consent_version.mjs`, so the app can never quote a version the server does not
 * know — a receipt naming a version nobody can produce is worthless as proof.
 *
 * The server still validates the value it receives: nothing here is trusted.
 */
object ConsentVersions {
    /** Date-stamped policy version, matching `CONSENT_VERSION` on the server. */
    const val POLICY_VERSION = "2026-11-19"

    /** Third-party guarantor consent uses the same policy revision. */
    const val GUARANTOR_VERSION = POLICY_VERSION

    /** The grants the policy sheet records. Names match `REQUIRED_CONSENT_KEYS`. */
    val PRIVACY_GRANTS = listOf("terms", "privacy", "dataProcessing")
}

/** The outcome of a consent receipt, as the server reported it. */
sealed interface ConsentOutcome {
    /** The receipt was written; [version] is what the server recorded, [currentVersion] is today's policy. */
    data class Recorded(
        val version: String,
        val recordedAtMillis: Long,
        val currentVersion: String
    ) : ConsentOutcome

    /** Nothing was recorded. [message] is safe to show. */
    data class Failed(val message: String) : ConsentOutcome
}
