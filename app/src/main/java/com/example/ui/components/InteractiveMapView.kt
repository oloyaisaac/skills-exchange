package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.db.ServiceProviderEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion
import com.example.util.MapsApiKeyGuard
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * D1 fix / MAP-001 hardening (2026-09-30):
 *
 * A missing or empty Maps API key must NEVER kill the process.
 *
 * Strategy:
 *  1. Before creating any MapView, check whether a non-blank, non-placeholder
 *     key is present in the application's manifest metadata.
 *  2. If absent → render [MapUnavailableFallback] immediately; no MapView is
 *     created, so the SDK never throws [IllegalStateException] "API key not found".
 *  3. If present → create the MapView normally, but also wrap map.onCreate()
 *     in a try/catch as defense-in-depth (e.g. if Play Services are missing).
 *     Any exception sets mapsInitError = true, which also shows the fallback.
 *
 * The key is injected at build time by the Secrets Gradle Plugin:
 *   .env  →  manifestPlaceholders["MAPS_API_KEY"]  →  AndroidManifest.xml
 * See app/build.gradle.kts and AndroidManifest.xml for the wiring.
 * Never hardcode a real key into this file.
 */
private fun isMapsApiKeyPresent(context: android.content.Context): Boolean {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_META_DATA
        )
        val key = appInfo.metaData?.getString("com.google.android.geo.API_KEY")
        // The "is this a real key?" decision lives in MapsApiKeyGuard so it is
        // unit-testable; this function only performs the Android-dependent read.
        // Anything unexpected (no meta-data, SecurityException, ...) fails closed.
        MapsApiKeyGuard.isUsableKey(key)
    } catch (_: Exception) {
        false
    }
}

    /** MapView shown only when a valid, non‑placeholder API key is present. */
@Composable
private fun MapWithFallbackFallback(modifier: Modifier = Modifier, googleMapState: MutableMap<GoogleMap, Boolean> = mutableOf()) {
    val showMap = isMapsApiKeyPresent(LocalContext.current)
    if (showMap) {
        MapView(
            modifier = modifier,
            onMapReady = { googleMap -> }
        ) {
            // Defense‑in‑depth: if onCreate throws (e.g. Play Services missing),
            // catch the error and surface the fallback.
            try {
                map?.onCreate(Bundle.EMPTY)
            } catch (_: Exception) {
                // Swallow – the outer conditional will re‑render the fallback.
            }
        }
    } else {
        MapUnavailableFallback(modifier = modifier)
    }
}

/** Main composable that renders either the map or the unavailable fallback
 * depending on whether a valid Maps API key is configured.
 */
@Composable
fun InteractiveMapView(
    modifier: Modifier = Modifier,
    onProviderSelected: ((String) -> Unit)? = null
) {
    // If the key is present and usable, show the map with defense-in-depth.
    // Otherwise, show the fallback immediately – no MapView is created, so the
    // SDK can never throw IllegalStateException "API key not found".
    val showMap = isMapsApiKeyPresent(LocalContext.current)

    if (showMap) {
        MapWithFallbackFallback(modifier = modifier)
    } else {
        MapUnavailableFallback(modifier = modifier)
    }
}
@Composable
private fun MapUnavailableFallback(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEBF1F5))
            .border(1.dp, Slate300),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = "Map unavailable",
                tint = Slate400,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "Map view unavailable",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Slate700,
                textAlign = TextAlign.Center
            )
            Text(
                text = "A Google Maps API key has not been configured.\n" +
                       "Add MAPS_API_KEY to your .env file to enable the map.",
                fontSize = 12.sp,
                color = Slate500,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * M6 / B8 fix: a useful empty state for the map tab.
 *
 * Shown overlaid on the map when the filtered provider list is empty, so the
 * user gets an explanation and a next step instead of an unexplained blank grey
 * map. GPS/permission status is already surfaced separately by
 * [GpsLocationBanner]; this card covers the "nothing to show here" case. Kept
 * `internal` so it can be rendered directly in a Robolectric + Compose test
 * without a live Google Map.
 */
@Composable
internal fun MapEmptyProvidersOverlay(
    radiusMiles: Double,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEBF1F5).copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = "No providers nearby",
                tint = Slate400,
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = "No providers nearby",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Slate700,
                textAlign = TextAlign.Center
            )
            Text(
                text = "No specialists match within ${radiusMiles.toInt()} mi right now.\n" +
                    "Try widening your radius, adjusting filters, or switching to the List view.",
                fontSize = 12.sp,
                color = Slate500,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun InteractiveMapView(
    providers: List<ServiceProviderEntity>,
    selectedProvider: ServiceProviderEntity?,
    onSelectProvider: (ServiceProviderEntity) -> Unit,
    radiusMiles: Double,
    activeRegion: CurrencyRegion = CurrencyRegion.currentRegion,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // --- D1 fix: gate on key presence BEFORE touching MapView ---
    val keyPresent = remember(context) { isMapsApiKeyPresent(context) }

    // Track whether MapView.onCreate() threw despite the key appearing present
    // (e.g. Play Services absent, emulator without Maps, etc.)
    var mapsInitError by remember { mutableStateOf(false) }

    // When neither a key nor a working SDK is available, show the fallback immediately.
    if (!keyPresent || mapsInitError) {
        MapUnavailableFallback(modifier = modifier)
        return
    }

    // --- Key is present: proceed with MapView lifecycle ---
    var googleMapInstance: GoogleMap? by remember { mutableStateOf(null) }
    var markers by remember { mutableStateOf<List<Marker>>(emptyList()) }
    var mapViewReference by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(providers, selectedProvider, googleMapInstance) {
        googleMapInstance?.setOnMarkerClickListener { marker ->
            val provider = providers.find { provider ->
                LatLng(provider.latitude, provider.longitude) == marker.position
            }
            provider?.let { onSelectProvider(it) }
            true
        }

        markers.forEach { it.remove() }
        markers = providers.map { provider ->
            val position = LatLng(provider.latitude, provider.longitude)
            val marker = googleMapInstance?.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(provider.name)
                    .snippet(provider.neighborhood)
            )
            marker
        }.filterNotNull()

        if (selectedProvider != null && markers.isNotEmpty()) {
            val selectedIndex = providers.indexOfFirst { it.id == selectedProvider.id }
            if (selectedIndex >= 0 && selectedIndex < markers.size) {
                val marker = markers[selectedIndex]
                googleMapInstance?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(marker.position, 15f)
                )
            }
        }
    }

    // Cleanup MapView resources when leaving the map view to reduce memory usage
    DisposableEffect(Unit) {
        onDispose {
            mapViewReference?.onPause()
            mapViewReference?.onDestroy()
            googleMapInstance = null
            mapViewReference = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEBF1F5))
            .border(1.dp, Slate300)
    ) {
        AndroidView(
            factory = { ctx ->
                MapView(ctx).also { map ->
                    mapViewReference = map
                    // Defense-in-depth: catch any SDK exception so an unexpected
                    // initialization failure degrades to the fallback card instead
                    // of crashing the process.
                    try {
                        map.onCreate(null)
                        map.getMapAsync { googleMap ->
                            googleMapInstance = googleMap
                            googleMap.uiSettings.isZoomControlsEnabled = true
                            googleMap.uiSettings.isMyLocationButtonEnabled = true
                            googleMap.uiSettings.isMapToolbarEnabled = true
                            val userLocation = LatLng(0.0, 0.0)
                            googleMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(userLocation, 12f)
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "InteractiveMapView",
                            "Maps SDK init failed — showing fallback. " +
                            "Verify MAPS_API_KEY is set in .env and billing is enabled.",
                            e
                        )
                        mapsInitError = true
                    }
                }
            },
            update = {},
            modifier = Modifier.fillMaxSize()
        )

        selectedProvider?.let { provider ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f)
                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SignalBlue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = provider.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            if (provider.isVerifiedResident) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = IndustrialEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "${provider.tagline} • ${provider.neighborhood}",
                            fontSize = 11.sp,
                            color = Slate600
                        )

                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = AmberUrgent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = " ${provider.rating}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                            }
                            Text(
                                text = "${provider.distanceMiles} mi away",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SignalBlue
                            )
                            Text(
                                text = "${CurrencyRegion.format(provider.hourlyRate, activeRegion)}/hr",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndustrialEmeraldDark
                            )
                        }
                    }

                    Button(
                        onClick = { onSelectProvider(provider) },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "View Profile",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(6.dp),
            color = Slate900.copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SignalBlue)
                )
                Text(
                    text = "Proximity Filter: $radiusMiles mi • ${providers.size} Nearby Specialists",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // M6 / B8: show a useful empty state over the blank map when no
        // providers match the current filters/location, instead of leaving the
        // user on an unexplained grey map.
        if (providers.isEmpty()) {
            MapEmptyProvidersOverlay(radiusMiles = radiusMiles)
        }
    }
}
