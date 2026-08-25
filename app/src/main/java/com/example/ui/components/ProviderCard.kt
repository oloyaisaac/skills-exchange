package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ServiceProviderEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion

@Composable
fun ProviderCard(
    provider: ServiceProviderEntity,
    onViewProfile: (ServiceProviderEntity) -> Unit,
    onQuickRequest: (ServiceProviderEntity) -> Unit,
    activeRegion: CurrencyRegion = CurrencyRegion.currentRegion,
    isAuthenticated: Boolean = false,
    onTriggerAuth: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            // Header: Name, Verification Badge, Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = provider.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (provider.isVerifiedResident) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IndustrialEmeraldLight)
                                    .border(0.8.dp, IndustrialEmerald, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = IndustrialEmerald,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Text(
                                        text = "VERIFIED RESIDENT",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndustrialEmeraldDark
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = provider.tagline,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 1.dp)
                    )

                    Text(
                        text = "📍 ${provider.neighborhood} (${provider.residenceYears} yrs resident) • ${provider.distanceMiles} mi away",
                        fontSize = 10.sp,
                        color = Slate500,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }

                // Hourly Rate Box
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = CurrencyRegion.format(provider.hourlyRate, activeRegion),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "per hour",
                        fontSize = 9.sp,
                        color = Slate500,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outline
            )

            // Metrics Bar: Rating, Completed Jobs, Response Time, Endorsements
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberUrgentLight)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = AmberUrgent,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${provider.rating}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        Text(
                            text = "(${provider.completedJobsCount})",
                            fontSize = 9.5.sp,
                            color = Slate500
                        )
                    }
                }

                // Response time badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFFEFF6FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDBEAFE))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Response",
                            tint = SignalBlue,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = provider.avgResponseTime,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                    }
                }

                // Endorsements badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = IndustrialEmeraldLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialEmerald.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Endorsements",
                            tint = IndustrialEmerald,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "${provider.endorsementsCount} endorsed",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndustrialEmeraldDark
                        )
                    }
                }
            }

            // Skills Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                provider.skillsCsv.split(",").take(3).forEach { skill ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = skill.trim(),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { onViewProfile(provider) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(vertical = 5.dp)
                ) {
                    Text(
                        text = "Trust Profile",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = {
                        if (isAuthenticated) {
                            onQuickRequest(provider)
                        } else {
                            onTriggerAuth("hire_${provider.id}")
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isAuthenticated) SignalBlue else AmberUrgent),
                    contentPadding = PaddingValues(vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = if (isAuthenticated) Icons.Default.Send else Icons.Default.Lock,
                        contentDescription = "Request",
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAuthenticated) "Request Job" else "🔒 Hire",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
