package com.example.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * B9 fix: real distance helpers for worker/radius filtering.
 *
 * The app previously filtered and displayed providers by a static, seeded
 * `distanceMiles` value regardless of where the user actually is. These helpers
 * compute the real great-circle (haversine) distance between two coordinates so
 * radius filtering and the displayed "X mi away" reflect the user's location.
 */
object DistanceUtil {

    private const val EARTH_RADIUS_MILES = 3958.7613

    /** Great-circle (haversine) distance in miles between two lat/lng points. */
    fun haversineMiles(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val dLat = Math.toRadians(latitude2 - latitude1)
        val dLon = Math.toRadians(longitude2 - longitude1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(latitude1)) * cos(Math.toRadians(latitude2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_MILES * c
    }

    /**
     * Distance to report for a provider: the real haversine distance when the
     * user's location is known, otherwise [fallbackMiles] (the stored/seed value)
     * so a missing GPS fix degrades gracefully instead of breaking the filter.
     */
    fun effectiveDistanceMiles(
        providerLatitude: Double,
        providerLongitude: Double,
        userLatitude: Double?,
        userLongitude: Double?,
        fallbackMiles: Double
    ): Double {
        if (userLatitude == null || userLongitude == null) return fallbackMiles
        return haversineMiles(userLatitude, userLongitude, providerLatitude, providerLongitude)
    }
}
