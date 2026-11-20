package com.example.data.remote

/**
 * R05-P2-02: sanitises push payload data before it is used.
 *
 * `onMessageReceived` copied `data["jobId"]` and `data["type"]` straight into an Intent extra and
 * into the notification shade. Push payloads are sender-controlled, so a compromised or buggy
 * sender could inject an arbitrary string into an Intent, and a huge title could flood the shade.
 * The rules are pure and live here so they are unit-testable without an Android runtime.
 */
object PushPayloadSanitiser {

    const val DEFAULT_TYPE = "GENERAL"
    const val MAX_TITLE_LENGTH = 120
    const val MAX_BODY_LENGTH = 500
    const val MAX_TYPE_LENGTH = 32

    private val JOB_ID_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")
    private val TYPE_PATTERN = Regex("^[A-Z_]{1,32}$")

    /** A job id, or "" when the value is not shaped like one. Never a free-form string. */
    fun sanitiseJobId(raw: String?): String =
        raw?.trim()?.takeIf { JOB_ID_PATTERN.matches(it) } ?: ""

    /** An upper-snake-case notification type, or "GENERAL". */
    fun sanitiseType(raw: String?): String {
        val candidate = raw?.trim()?.uppercase()?.take(MAX_TYPE_LENGTH)
        return candidate?.takeIf { TYPE_PATTERN.matches(it) } ?: DEFAULT_TYPE
    }

    /** Trimmed and clipped display text with an ellipsis, or [fallback] when empty. */
    fun clip(value: String?, max: Int, fallback: String = ""): String {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallback
        return if (trimmed.length <= max) trimmed else trimmed.take(max - 1) + "\u2026"
    }
}
