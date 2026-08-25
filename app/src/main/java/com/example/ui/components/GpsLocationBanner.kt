package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.GpsLocationState

@Composable
fun GpsLocationBanner(
    gpsState: GpsLocationState,
    currentDistrict: String,
    onRequestGps: () -> Unit,
    onDenyGps: () -> Unit,
    onDismissBanner: () -> Unit,
    onOpenDistrictSelector: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (gpsState.isBannerDismissed) return

    val infiniteTransition = rememberInfiniteTransition(label = "gps_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when {
            gpsState.isPermissionGranted -> IndustrialEmeraldLight
            gpsState.errorMessage != null -> AmberUrgentLight
            else -> Color(0xFFF0F7FF)
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = when {
                gpsState.isPermissionGranted -> IndustrialEmerald
                gpsState.errorMessage != null -> AmberUrgent
                else -> SignalBlue.copy(alpha = 0.5f)
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(
                            when {
                                gpsState.isPermissionGranted -> IndustrialEmerald
                                gpsState.errorMessage != null -> AmberUrgent
                                else -> SignalBlue
                            },
                            shape = RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (gpsState.isLocating) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Locating",
                            tint = Color.White,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(rotation)
                        )
                    } else {
                        Icon(
                            imageVector = when {
                                gpsState.isPermissionGranted -> Icons.Default.GpsFixed
                                gpsState.errorMessage != null -> Icons.Default.Warning
                                else -> Icons.Default.MyLocation
                            },
                            contentDescription = "GPS",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = when {
                            gpsState.isLocating -> "📍 GPS Locating..."
                            gpsState.isPermissionGranted -> "📍 Live: ${gpsState.detectedNeighborhood}"
                            gpsState.errorMessage != null -> "⚠️ ${gpsState.errorMessage}"
                            else -> "📍 Match specialists in $currentDistrict"
                        },
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            gpsState.isPermissionGranted -> IndustrialEmeraldDark
                            gpsState.errorMessage != null -> RedDanger
                            else -> Slate900
                        },
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            gpsState.isLocating -> "Acquiring satellite lock..."
                            gpsState.isPermissionGranted -> "${gpsState.accuracyMeters.toInt()}m accuracy filtering"
                            gpsState.errorMessage != null -> "Select district or retry"
                            else -> "Auto-match specialists near your street"
                        },
                        fontSize = 9.sp,
                        color = Slate600,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // LOC-002: Manual Location Correction Trigger
                OutlinedButton(
                    onClick = onOpenDistrictSelector,
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = "Edit",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (!gpsState.isPermissionGranted && gpsState.errorMessage == null) {
                    Button(
                        onClick = onRequestGps,
                        enabled = !gpsState.isLocating,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (gpsState.isLocating) "Locating..." else "Use GPS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (gpsState.isPermissionGranted) {
                    IconButton(
                        onClick = onRequestGps,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-detect GPS",
                            tint = IndustrialEmeraldDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = onRequestGps,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AmberUrgent),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "Retry GPS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = onDismissBanner,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = Slate500,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
