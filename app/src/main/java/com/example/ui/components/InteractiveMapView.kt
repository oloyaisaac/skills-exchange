package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.db.ServiceProviderEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

@Composable
fun InteractiveMapView(
    providers: List<ServiceProviderEntity>,
    selectedProvider: ServiceProviderEntity?,
    onSelectProvider: (ServiceProviderEntity) -> Unit,
    radiusMiles: Double,
    activeRegion: CurrencyRegion = CurrencyRegion.currentRegion,
    modifier: Modifier = Modifier
) {
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
            factory = { context ->
                MapView(context).also { map ->
                    mapViewReference = map
                    map.onCreate(null)
                    map.getMapAsync { googleMap ->
                        googleMapInstance = googleMap

                        googleMap.uiSettings.isZoomControlsEnabled = true
                        googleMap.uiSettings.isMyLocationButtonEnabled = true
                        googleMap.uiSettings.isMapToolbarEnabled = true

                        val userLocation = LatLng(0.0, 0.0)
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))
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
    }
}
