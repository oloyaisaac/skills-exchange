package com.example.util

/**
 * D1 fix (MAP-001) — testable half of the Maps API-key guard.
 *
 * **Why this exists as its own object.** Device defect D1 was that tapping the Map
 * View tab killed the process three times out of three with
 * `IllegalStateException: API key not found`, thrown from deep inside
 * `CreatorImpl.newMapViewDelegate` once a `MapView` was constructed without a real
 * key. The crash therefore happens *before* any app code can catch it, so the only
 * safe strategy is to refuse to build a `MapView` at all when the key is absent —
 * which is what `InteractiveMapView` does.
 *
 * The key-reading half of that check (`packageManager.getApplicationInfo(...)`) is
 * inherently Android-dependent, but the *decision* is pure string logic. Keeping the
 * decision here makes it unit-testable, so a future regression that weakens the
 * guard (e.g. someone "fixing" the fallback by removing a placeholder check) fails a
 * test instead of crashing on a user's device.
 *
 * Deliberately conservative: anything that is not clearly a real, substituted key is
 * treated as ABSENT. A false "absent" only costs the fallback card; a false
 * "present" costs the process.
 */
object MapsApiKeyGuard {

    /**
     * The literal two characters `${`, assembled at runtime so Kotlin does not read
     * it as the start of a string template. An un-substituted Gradle placeholder
     * appears in the manifest as exactly this text, so it is matched explicitly
     * rather than by scanning for a bare `$` (which a real key may legitimately
     * contain).
     */
    private const val UNSUBSTITUTED_MARK = "$" + "{"

    /**
     * Placeholder tokens that must never be mistaken for a real key.
     *
     * - `DEFAULT_MAPS_API_KEY` is the value `app/build.gradle.kts` puts in
     *   `manifestPlaceholders["MAPS_API_KEY"]`, and what `.env.example` ships, so it
     *   is what an unconfigured build actually contains.
     * - `MY_MAPS_API_KEY` mirrors the `GEMINI_API_KEY=MY_GEMINI_API_KEY` convention
     *   already used in this repo's `.env.example`.
     * - `YOUR_KEY_HERE` / `YOUR_API_KEY` are the usual copy-paste placeholders.
     */
    private val PLACEHOLDER_TOKENS = setOf(
        "DEFAULT_MAPS_API_KEY",
        "MY_MAPS_API_KEY",
        "YOUR_KEY_HERE",
        "YOUR_API_KEY",
        "CHANGEME",
        "TODO"
    )

    /** True when [key] is a real, substituted key rather than blank or a placeholder. */
    fun isUsableKey(key: String?): Boolean {
        val trimmed = key?.trim() ?: return false
        if (trimmed.isEmpty()) return false
        // An un-substituted Gradle manifest placeholder survives as a literal
        // "${MAPS_API_KEY}" (the Secrets plugin leaves it verbatim when the property
        // is missing), so treat any "${...}" residue as absent.
        if (trimmed.contains(UNSUBSTITUTED_MARK)) return false
        if (PLACEHOLDER_TOKENS.any { trimmed.equals(it, ignoreCase = true) }) return false
        return true
    }
}
