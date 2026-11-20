package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.viewmodel.GpsLocationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopHeaderBar(
    selectedDistrict: String,
    onDistrictSelect: (String) -> Unit,
    gpsState: GpsLocationState = GpsLocationState(),
    onRequestGps: () -> Unit = {},
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenFilters: () -> Unit,
    userRoleMode: String,
    onToggleUserRole: () -> Unit,
    activeFilterCount: Int,
    unreadNotificationCount: Int,
    isAuthenticated: Boolean = false,
    onTriggerAuth: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenNavigationDrawer: () -> Unit = {},
    isWorkerOnline: Boolean = true,
    onToggleWorkerOnline: () -> Unit = {}
) {
    var isDistrictDropdownExpanded by remember { mutableStateOf(false) }
    val districts = listOf(
        "Sunnyside District",
        "Maplewood East",
        "Oakridge Heights",
        "Riverdale North",
        "Downtown Core",
        "Greenfield South"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "header_gps_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "spin"
    )

    Surface(
        color = Slate900,
        contentColor = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate800)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Left: Hamburger Menu Button
            IconButton(
                onClick = onOpenNavigationDrawer,
                // R12-P2-04: 48dp touch target.
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Drawer Navigation",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 2. Center: Location Section & District Pill
            Box {
                Surface(
                    onClick = { isDistrictDropdownExpanded = true },
                    shape = RoundedCornerShape(20.dp),
                    color = if (gpsState.isPermissionGranted) Color(0xFF0F291E) else Slate800,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (gpsState.isPermissionGranted) IndustrialEmerald else Slate700
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (gpsState.isLocating) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Locating GPS",
                                tint = SignalBlue,
                                modifier = Modifier
                                    .size(14.dp)
                                    .rotate(rotation)
                            )
                            Text(
                                text = "Locating...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalBlue
                            )
                        } else if (gpsState.isPermissionGranted) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(IndustrialEmerald)
                            )
                            Text(
                                text = if (selectedDistrict == gpsState.detectedNeighborhood) "Current Location" else selectedDistrict,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndustrialEmeraldLight
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = "District Location",
                                tint = SignalBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = selectedDistrict,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select District",
                            tint = if (gpsState.isPermissionGranted) IndustrialEmerald else Slate400,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = isDistrictDropdownExpanded,
                    onDismissRequest = { isDistrictDropdownExpanded = false },
                    modifier = Modifier
                        .background(Slate900)
                        .widthIn(min = 250.dp)
                        .border(1.dp, Slate700, RoundedCornerShape(8.dp))
                ) {
                    // Option 1: Use Current Location (GPS)
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = if (gpsState.isPermissionGranted) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                                contentDescription = "Current Location",
                                tint = if (gpsState.isPermissionGranted) IndustrialEmerald else SignalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Use Current Location",
                                        color = if (gpsState.isPermissionGranted) IndustrialEmeraldLight else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    if (gpsState.isPermissionGranted) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = IndustrialEmerald
                                        ) {
                                            Text(
                                                text = "GPS LIVE",
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (gpsState.isPermissionGranted)
                                        "Detected: ${gpsState.detectedNeighborhood} (${gpsState.accuracyMeters.toInt()}m accuracy)"
                                    else if (gpsState.isLocating)
                                        "Acquiring satellite lock..."
                                    else
                                        "Auto-detect precise neighborhood & radius",
                                    // R12-P2-06: this is a sentence, not an icon.
                                    color = Slate500,
                                    fontSize = 10.sp
                                )
                            }
                        },
                        onClick = {
                            onRequestGps()
                            isDistrictDropdownExpanded = false
                        }
                    )

                    HorizontalDivider(
                        color = Slate800,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Section Label: Manual Neighborhoods
                    Text(
                        text = "OR CHOOSE NEIGHBORHOOD",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate500,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )

                    districts.forEach { district ->
                        val isCurrentSelection = district == selectedDistrict
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCurrentSelection) Icons.Default.Check else Icons.Outlined.LocationCity,
                                    contentDescription = null,
                                    tint = if (isCurrentSelection) SignalBlue else Slate500,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = district,
                                    color = if (isCurrentSelection) SignalBlue else Color.White,
                                    fontWeight = if (isCurrentSelection) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.5.sp
                                )
                            },
                            onClick = {
                                onDistrictSelect(district)
                                isDistrictDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // 3. Right: Notification Bell Button
            Box {
                IconButton(
                    onClick = onOpenNotifications,
                    // R12-P2-04: 48dp touch target.
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (unreadNotificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(RedDanger)
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$unreadNotificationCount",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
