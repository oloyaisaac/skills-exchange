package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JobPostEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion

@Composable
fun FreelancerDashboardView(
    jobs: List<JobPostEntity>,
    isOnline: Boolean,
    onToggleOnline: () -> Unit,
    onSelectJob: (JobPostEntity) -> Unit,
    onOpenQuickRequest: () -> Unit,
    isAuthenticated: Boolean,
    onTriggerAuth: (String) -> Unit,
    modifier: Modifier = Modifier,
    isAuthorized: Boolean = false
) {
    // SEC-009 / G3 / H1 / H8: Authoritative access-control boundary.
    // Fail closed: never display worker dashboard controls, job lists, or toggle
    // buttons to unauthenticated or unapproved users, even if composed maliciously.
    if (!isAuthenticated || !isAuthorized) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Access Denied",
                        tint = RedDanger,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Worker Dashboard Access Denied",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Slate900
                    )
                    Text(
                        text = "Worker registration, approved identity verification, and administrator activation are required to access this dashboard.",
                        fontSize = 14.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 1. Worker Availability & Quick Stats Top Banner
        Surface(
            color = Slate900,
            contentColor = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Availability Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (isOnline) IndustrialEmerald else Slate500)
                        )
                        Column {
                            Text(
                                text = if (isOnline) "🟢 ONLINE & ACCEPTING JOBS" else "⚪ OFFLINE (PAUSED)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isOnline) IndustrialEmeraldLight else Slate300
                            )
                            Text(
                                text = if (isOnline) "Receiving job requests within 5 miles" else "Toggle on to receive local resident work orders",
                                fontSize = 10.sp,
                                color = Slate400
                            )
                        }
                    }

                    Switch(
                        checked = isOnline,
                        onCheckedChange = { onToggleOnline() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = IndustrialEmerald,
                            uncheckedThumbColor = Slate300,
                            uncheckedTrackColor = Slate700
                        )
                    )
                }

                // Quick Metrics Cards (Earnings, Completed Jobs, Pending Escrow)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        title = "Today's Earnings",
                        amount = CurrencyRegion.format(180.0),
                        subtitle = "+2 jobs completed",
                        icon = Icons.Default.Payments,
                        iconTint = IndustrialEmerald,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Pending Escrow",
                        amount = CurrencyRegion.format(210.0),
                        subtitle = "Locked in vault",
                        icon = Icons.Default.LockClock,
                        iconTint = SignalBlue,
                        modifier = Modifier.weight(1f)
                    )

                    MetricCard(
                        title = "Active Bids",
                        amount = "${jobs.count { it.status == "OPEN" }} Open",
                        subtitle = "Nearby requests",
                        icon = Icons.Default.Assignment,
                        iconTint = AmberUrgent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Open Micro-Jobs Feed Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💼 Open Resident Requests Near You",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "${jobs.size} available",
                    fontSize = 11.sp,
                    color = Slate500,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (jobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WorkHistory,
                        contentDescription = "No jobs",
                        tint = Slate400,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No open requests match your filter",
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    Text(
                        text = "New requests will appear as residents post in your district.",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(jobs, key = { it.id }) { job ->
                    WorkerJobFeedCard(
                        job = job,
                        onSelectJob = { onSelectJob(job) },
                        isAuthenticated = isAuthenticated,
                        onTriggerAuth = onTriggerAuth
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    amount: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Slate800,
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = title,
                    fontSize = 9.sp,
                    color = Slate400,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }

            Text(
                text = amount,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1
            )

            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = Slate400,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WorkerJobFeedCard(
    job: JobPostEntity,
    onSelectJob: () -> Unit,
    isAuthenticated: Boolean,
    onTriggerAuth: (String) -> Unit
) {
    val formattedBudget = CurrencyRegion.format(job.budget)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isAuthenticated) onSelectJob() else onTriggerAuth("Sign in to view job details & submit quote")
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: Urgency badge & Budget
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (job.urgency == "URGENT") RedDangerLight else SignalBlueLight
                    ) {
                        Text(
                            text = if (job.urgency == "URGENT") "⚡ URGENT NEED" else "📅 SCHEDULED",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (job.urgency == "URGENT") RedDanger else SignalBlue,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Slate100
                    ) {
                        Text(
                            text = job.category.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate700,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = formattedBudget,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = IndustrialEmeraldDark
                )
            }

            // Title
            Text(
                text = job.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Description
            Text(
                text = job.description,
                fontSize = 11.5.sp,
                color = Slate600,
                maxLines = 2
            )

            Divider(color = Slate200, thickness = 0.8.dp)

            // Footer: Resident info & Submit Quote CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "District",
                        tint = Slate400,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${job.district} • Resident Request",
                        fontSize = 10.5.sp,
                        color = Slate500,
                        fontWeight = FontWeight.Medium
                    )
                }

                Button(
                    onClick = {
                        if (isAuthenticated) {
                            onSelectJob()
                        } else {
                            onTriggerAuth("Sign in to submit a quote for '${job.title}'")
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isAuthenticated) Icons.Default.Send else Icons.Default.Lock,
                            contentDescription = "Submit Quote",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (isAuthenticated) "Submit Quote" else "Unlock to Bid",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
