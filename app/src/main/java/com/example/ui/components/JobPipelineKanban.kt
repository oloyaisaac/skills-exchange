package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JobPostEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion

@Composable
fun JobPipelineKanban(
    jobs: List<JobPostEntity>,
    onSelectJob: (JobPostEntity) -> Unit,
    onAdvanceStatus: (String, String) -> Unit,
    onOpenChat: (JobPostEntity) -> Unit,
    onOpenEscrowSheet: (JobPostEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val statuses = listOf("Requested", "Accepted", "In Progress", "Review", "Completed")
    var selectedTabStatus by remember { mutableStateOf("In Progress") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Slate50)
    ) {
        // Pipeline Stage Tab Header
        ScrollableTabRow(
            selectedTabIndex = statuses.indexOf(selectedTabStatus).coerceAtLeast(0),
            edgePadding = 14.dp,
            containerColor = Color.White,
            contentColor = SignalBlue,
            divider = { HorizontalDivider(thickness = 1.dp, color = Slate200) }
        ) {
            statuses.forEach { status ->
                val count = jobs.count { it.status.equals(status, ignoreCase = true) }
                Tab(
                    selected = selectedTabStatus == status,
                    onClick = { selectedTabStatus = status },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = status,
                                fontSize = 12.sp,
                                fontWeight = if (selectedTabStatus == status) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabStatus == status) SignalBlue else Slate600
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (selectedTabStatus == status) SignalBlue else Slate100
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTabStatus == status) Color.White else Slate700,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                )
            }
        }

        // Job List for Selected Status Pipeline
        val filteredJobs = jobs.filter { it.status.equals(selectedTabStatus, ignoreCase = true) }

        if (filteredJobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.WorkOutline,
                        contentDescription = "Empty",
                        tint = Slate400,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No jobs in '$selectedTabStatus' pipeline stage",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate500,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 90.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredJobs, key = { it.id }) { job ->
                    JobPipelineCard(
                        job = job,
                        onSelectJob = { onSelectJob(job) },
                        onAdvanceStatus = { nextStatus -> onAdvanceStatus(job.id, nextStatus) },
                        onOpenChat = { onOpenChat(job) },
                        onOpenEscrowSheet = { onOpenEscrowSheet(job) }
                    )
                }
            }
        }
    }
}

@Composable
fun JobPipelineCard(
    job: JobPostEntity,
    onSelectJob: () -> Unit,
    onAdvanceStatus: (String) -> Unit,
    onOpenChat: () -> Unit,
    onOpenEscrowSheet: () -> Unit
) {
    var isPricingExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Category, Labor-Only & Escrow Badges
            val isLaborOnly = job.materialsProvidedBy == "RESIDENT" || job.materialsProvidedBy == "NONE_NEEDED"

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Slate100,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                    ) {
                        Text(
                            text = job.category.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = Slate700,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isLaborOnly) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEFF6FF),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD))
                        ) {
                            Text(
                                text = "🛠️ LABOR ONLY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Escrow status badge
                val (escrowBadgeText, escrowBadgeBg, escrowBadgeFg) = when (job.escrowStatus) {
                    "FUNDS_LOCKED_IN_ESCROW" -> Triple("🔒 ESCROW LOCKED", IndustrialEmeraldLight, IndustrialEmeraldDark)
                    "PROOF_SUBMITTED" -> Triple("📷 PROOF SUBMITTED", AmberUrgentLight, AmberUrgent)
                    "RELEASED_TO_WORKER" -> Triple("✅ PAID & RELEASED", IndustrialEmeraldLight, IndustrialEmeraldDark)
                    "WORK_IN_PROGRESS" -> Triple("⚡ IN PROGRESS", SignalBlueLight, SignalBlueDark)
                    else -> Triple("⏳ QUOTE PENDING", Slate100, Slate700)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = escrowBadgeBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, escrowBadgeFg.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = escrowBadgeText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = escrowBadgeFg,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Title & Description
            Text(
                text = job.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp)
            )

            Text(
                text = job.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Assigned Provider Info & Price
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Provider",
                        tint = SignalBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = job.assignedProviderName ?: "Awaiting Provider Accept...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (job.assignedProviderName != null) MaterialTheme.colorScheme.onSurface else Slate500
                    )
                }

                Text(
                    text = CurrencyRegion.format(job.grandTotal),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = IndustrialEmeraldDark
                )
            }

            // Transparent Pricing Breakdown Trigger Toggle
            Surface(
                onClick = { isPricingExpanded = !isPricingExpanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = "Receipt",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isLaborOnly) "Labor-Only Cost Ledger" else "Transparent Cost Ledger",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (isPricingExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = Slate500,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (isPricingExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Labor (${job.laborHours} hrs)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = CurrencyRegion.format(job.laborTotal), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (!isLaborOnly) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Materials & Receipts (0% Markup)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(text = CurrencyRegion.format(job.materialsSubtotal), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Materials (Customer Provides All)", fontSize = 11.sp, color = SignalBlueDark, fontWeight = FontWeight.Bold)
                            Text(text = CurrencyRegion.format(0.0), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SignalBlueDark)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Trust & Safety Platform Fee (~10%)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = CurrencyRegion.format(job.platformFee), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Escrow Amount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = CurrencyRegion.format(job.grandTotal), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = IndustrialEmeraldDark)
                    }
                }
            }

            // Action Row: In-App Chat, MoMo Escrow Sheet, and Pipeline Status Advance
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SignalBlue),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Chat,
                        contentDescription = "Chat",
                        tint = SignalBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SignalBlue)
                }

                Button(
                    onClick = onOpenEscrowSheet,
                    modifier = Modifier.weight(1.3f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Escrow",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "MoMo Escrow", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                val nextStatus = when (job.status) {
                    "Requested" -> "Accepted"
                    "Accepted" -> "In Progress"
                    "In Progress" -> "Review"
                    "Review" -> "Completed"
                    else -> null
                }

                if (nextStatus != null) {
                    Button(
                        onClick = {
                            if (nextStatus == "In Progress" && job.escrowStatus != "FUNDS_LOCKED_IN_ESCROW" && job.escrowStatus != "WORK_IN_PROGRESS") {
                                onOpenEscrowSheet()
                            } else {
                                onAdvanceStatus(nextStatus)
                            }
                        },
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IndustrialEmerald),
                        contentPadding = PaddingValues(vertical = 6.dp)
                    ) {
                        Text(
                            text = if (nextStatus == "In Progress" && job.escrowStatus != "FUNDS_LOCKED_IN_ESCROW" && job.escrowStatus != "WORK_IN_PROGRESS") "Lock Escrow" else "Next",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
