package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ServiceProviderEntity
import com.example.data.remote.GroundedPlaceResult
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchModal(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchResults: List<ServiceProviderEntity>,
    onSelectProvider: (ServiceProviderEntity) -> Unit,
    onSearchGoogleMaps: (String) -> Unit,
    mapsResults: Pair<String, List<GroundedPlaceResult>>?,
    isMapsSearching: Boolean,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Skill Providers, 1 = Google Maps Grounding

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEARCH & MAPS AI GROUNDING",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    color = SignalBlue
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                }
            }

            // Mode Selector Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Slate100, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    onClick = { selectedTab = 0 },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedTab == 0) Color.White else Color.Transparent,
                    shadowElevation = if (selectedTab == 0) 2.dp else 0.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = "👥 Skill Providers",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) SignalBlue else Slate600
                        )
                    }
                }

                Surface(
                    onClick = {
                        selectedTab = 1
                        if (mapsResults == null && searchQuery.isNotBlank()) {
                            onSearchGoogleMaps(searchQuery)
                        } else if (mapsResults == null) {
                            onSearchGoogleMaps("Hardware stores and plumbing material suppliers")
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = if (selectedTab == 1) Color.White else Color.Transparent,
                    shadowElevation = if (selectedTab == 1) 2.dp else 0.dp,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = "📍 Google Maps AI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selectedTab == 1) SignalBlue else Slate600)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    onSearchQueryChange(it)
                    if (selectedTab == 1 && it.length > 3) {
                        onSearchGoogleMaps(it)
                    }
                },
                placeholder = {
                    Text(
                        text = if (selectedTab == 0) "Search by skill or name (e.g. Electrician, Plumbing)..." else "Search Google Maps (e.g. 240V Breaker supplier, Copper pipes)...",
                        fontSize = 12.sp,
                        color = Slate400
                    )
                },
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Slate500) },
                trailingIcon = {
                    if (selectedTab == 1) {
                        IconButton(onClick = { onSearchGoogleMaps(searchQuery.ifBlank { "Hardware & Plumbing suppliers" }) }) {
                            Icon(imageVector = Icons.Default.Map, contentDescription = "Ground", tint = SignalBlue)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                shape = RoundedCornerShape(6.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SignalBlue,
                    unfocusedBorderColor = Slate300
                ),
                singleLine = true
            )

            if (selectedTab == 0) {
                Text(
                    text = "${searchResults.size} Matching Verified Service Providers",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate500,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxHeight(0.5f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults, key = { it.id }) { provider ->
                        Surface(
                            onClick = {
                                onSelectProvider(provider)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = Slate50,
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(text = provider.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                    Text(text = "${provider.tagline} • ${provider.neighborhood}", fontSize = 11.sp, color = Slate600)
                                }
                                Text(text = "${provider.hourlyRate.toInt()}/hr", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = IndustrialEmeraldDark)
                            }
                        }
                    }
                }
            } else {
                // Google Maps AI Grounding Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📍 Live Google Maps Grounded Places (Gemini 3.5 Flash)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SignalBlue
                    )
                    if (isMapsSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SignalBlue)
                    }
                }

                if (mapsResults != null) {
                    val (summaryText, places) = mapsResults

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE8F0FE),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SignalBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = summaryText.take(280) + if (summaryText.length > 280) "..." else "",
                            fontSize = 11.sp,
                            color = Slate800,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(0.5f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(places) { place ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Slate50,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = place.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                        Text(text = place.rating, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberUrgent)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, tint = SignalBlue, modifier = Modifier.size(12.dp))
                                        Text(text = place.address, fontSize = 11.sp, color = Slate600)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = place.description, fontSize = 11.sp, color = Slate700)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = "📞 ${place.phone}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                        Button(
                                            onClick = { /* Open Directions */ },
                                            modifier = Modifier.height(26.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("Directions & Map", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Slate50, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = { onSearchGoogleMaps(searchQuery.ifBlank { "Hardware & Plumbing suppliers in Sunnyside" }) },
                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("📍 Find Nearby Material Suppliers with Google Maps AI", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
