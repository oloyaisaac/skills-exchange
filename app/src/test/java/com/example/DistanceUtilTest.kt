package com.example

import com.example.util.DistanceUtil
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * B9 regression tests: radius filtering must use the real distance from the
 * user's location to the provider, not the static seeded `distanceMiles`.
 */
class DistanceUtilTest {

    @Test
    fun `same coordinates have zero distance`() {
        assertEquals(
            0.0,
            DistanceUtil.haversineMiles(37.7749, -122.4194, 37.7749, -122.4194),
            0.01
        )
    }

    @Test
    fun `known city-to-city distance is approximately correct`() {
        // San Francisco -> Los Angeles is ~347 miles great-circle.
        val miles = DistanceUtil.haversineMiles(37.7749, -122.4194, 34.0522, -118.2437)
        assertEquals(347.0, miles, 5.0)
    }

    @Test
    fun `distance is symmetric`() {
        val a = DistanceUtil.haversineMiles(37.7749, -122.4194, 34.0522, -118.2437)
        val b = DistanceUtil.haversineMiles(34.0522, -118.2437, 37.7749, -122.4194)
        assertEquals(a, b, 0.0001)
    }

    @Test
    fun `effective distance uses real location when available`() {
        // Even though the seed fallback says 0.8 mi, the real distance (SF->LA)
        // must win when the user's location is known.
        val miles = DistanceUtil.effectiveDistanceMiles(
            providerLatitude = 34.0522,
            providerLongitude = -118.2437,
            userLatitude = 37.7749,
            userLongitude = -122.4194,
            fallbackMiles = 0.8
        )
        assertEquals(347.0, miles, 5.0)
    }

    @Test
    fun `effective distance falls back when user location is unknown`() {
        val miles = DistanceUtil.effectiveDistanceMiles(
            providerLatitude = 34.0522,
            providerLongitude = -118.2437,
            userLatitude = null,
            userLongitude = null,
            fallbackMiles = 0.8
        )
        assertEquals(0.8, miles, 0.0001)
    }
}
