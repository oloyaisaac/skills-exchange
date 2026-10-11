package com.example.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D1 fix (MAP-001) — regression test for the Maps API-key guard.
 *
 * Device defect D1 killed the process 3/3 times when the Map View tab was tapped,
 * with `IllegalStateException: API key not found` raised from inside
 * `CreatorImpl.newMapViewDelegate`. That exception happens while the SDK constructs
 * the MapView, so it is unreachable from app code — the only safe response is to
 * refuse to build a MapView when the key is not a real one, which is what
 * `InteractiveMapView.isMapsApiKeyPresent()` does via this predicate.
 *
 * `app/build.gradle.kts` ships `manifestPlaceholders["MAPS_API_KEY"] =
 * "DEFAULT_MAPS_API_KEY"`, so an unconfigured build genuinely contains that token
 * and must still take the fallback path.
 *
 * The bias is deliberate: anything not clearly a real key is ABSENT. A wrong
 * "absent" only shows the fallback card; a wrong "present" kills the process.
 */
class MapsApiKeyGuardTest {

    // --- keys that must be accepted (real, substituted keys) ---

    @Test
    fun `typical Google Maps key is usable`() {
        assertTrue(MapsApiKeyGuard.isUsableKey("AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6"))
    }

    @Test
    fun `surrounding whitespace does not invalidate a real key`() {
        assertTrue(MapsApiKeyGuard.isUsableKey("  AIzaSyA1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6\n"))
    }

    // --- keys that must be refused: the crash cases ---

    @Test
    fun `null key is not usable`() {
        assertFalse(MapsApiKeyGuard.isUsableKey(null))
    }

    @Test
    fun `empty key is not usable`() {
        assertFalse(MapsApiKeyGuard.isUsableKey(""))
    }

    @Test
    fun `whitespace-only key is not usable`() {
        assertFalse(MapsApiKeyGuard.isUsableKey("   \t\n  "))
    }

    /**
     * The exact value an unconfigured build contains, because
     * `app/build.gradle.kts` sets `manifestPlaceholders["MAPS_API_KEY"] =
     * "DEFAULT_MAPS_API_KEY"` and `.env.example` ships the same token.
     */
    @Test
    fun `DEFAULT_MAPS_API_KEY placeholder is not usable`() {
        assertFalse(MapsApiKeyGuard.isUsableKey("DEFAULT_MAPS_API_KEY"))
    }

    @Test
    fun `other placeholder tokens are not usable`() {
        for (token in listOf("MY_MAPS_API_KEY", "YOUR_KEY_HERE", "YOUR_API_KEY", "CHANGEME", "TODO")) {
            assertFalse("$token must not be treated as a real key", MapsApiKeyGuard.isUsableKey(token))
        }
    }

    @Test
    fun `placeholder detection ignores case`() {
        assertFalse(MapsApiKeyGuard.isUsableKey("default_maps_api_key"))
        assertFalse(MapsApiKeyGuard.isUsableKey("Default_Maps_Api_Key"))
    }

    /**
     * If the Secrets Gradle Plugin cannot resolve MAPS_API_KEY it leaves the
     * placeholder verbatim, so the manifest value stays literally
     * "${MAPS_API_KEY}". That must not reach the SDK either.
     */
    @Test
    fun `un-substituted gradle placeholder is not usable`() {
        // Built from parts so Kotlin does not read "${" as a string template —
        // which is exactly the literal text an un-substituted placeholder leaves behind.
        val verbatim = "$" + "{MAPS_API_KEY}"
        assertFalse(MapsApiKeyGuard.isUsableKey(verbatim))
        assertFalse(MapsApiKeyGuard.isUsableKey("$" + "{ maps_api_key }"))
    }

    @Test
    fun `placeholder residue embedded in a value is not usable`() {
        assertFalse(MapsApiKeyGuard.isUsableKey("AIzaSy" + "$" + "{MAPS_API_KEY}"))
    }
}
