package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.util.Locale

data class DeviceLocationResult(
    val latitude: Double,
    val longitude: Double,
    val neighborhoodName: String,
    val accuracy: Float,
    val isRealGps: Boolean
)

object LocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(
        context: Context,
        onLocationReceived: (DeviceLocationResult) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!hasLocationPermission(context)) {
            onError("Location permission not granted.")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            onError("Location manager unavailable.")
            return
        }

        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            // Still provide a fallback neighborhood for demo if device sensors are disabled
            onLocationReceived(
                DeviceLocationResult(
                    latitude = 0.3476,
                    longitude = 32.5825,
                    neighborhoodName = "Sunnyside District",
                    accuracy = 15f,
                    isRealGps = false
                )
            )
            return
        }

        // Try getting last known location first for immediate responsiveness
        var bestLastLocation: Location? = null
        if (isGpsEnabled) {
            val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gpsLoc != null) bestLastLocation = gpsLoc
        }
        if (bestLastLocation == null && isNetworkEnabled) {
            val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (netLoc != null) bestLastLocation = netLoc
        }

        if (bestLastLocation != null) {
            val neighborhood = resolveNeighborhoodName(context, bestLastLocation.latitude, bestLastLocation.longitude)
            onLocationReceived(
                DeviceLocationResult(
                    latitude = bestLastLocation.latitude,
                    longitude = bestLastLocation.longitude,
                    neighborhoodName = neighborhood,
                    accuracy = bestLastLocation.accuracy,
                    isRealGps = true
                )
            )
            return
        }

        // Request single update if no last known location
        val provider = if (isGpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                locationManager.removeUpdates(this)
                val neighborhood = resolveNeighborhoodName(context, loc.latitude, loc.longitude)
                onLocationReceived(
                    DeviceLocationResult(
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        neighborhoodName = neighborhood,
                        accuracy = loc.accuracy,
                        isRealGps = true
                    )
                )
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (e: Exception) {
            // Fallback gracefully
            onLocationReceived(
                DeviceLocationResult(
                    latitude = 0.3476,
                    longitude = 32.5825,
                    neighborhoodName = "Sunnyside District",
                    accuracy = 10f,
                    isRealGps = false
                )
            )
        }
    }

    fun resolveNeighborhoodName(context: Context, lat: Double, lng: Double): String {
        return try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val subLocality = addr.subLocality ?: addr.locality ?: addr.subAdminArea ?: addr.featureName
                    if (!subLocality.isNullOrBlank()) {
                        return if (subLocality.contains("District", ignoreCase = true) ||
                            subLocality.contains("East", ignoreCase = true) ||
                            subLocality.contains("West", ignoreCase = true) ||
                            subLocality.contains("Heights", ignoreCase = true)) {
                            subLocality
                        } else {
                            "$subLocality District"
                        }
                    }
                }
            }
            "Sunnyside District"
        } catch (e: Exception) {
            "Sunnyside District"
        }
    }

    fun openLocationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
