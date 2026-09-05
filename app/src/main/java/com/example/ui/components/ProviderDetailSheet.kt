package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.SkillEndorsementEntity
import com.example.data.db.ReportEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion
import com.example.util.ShareHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDetailSheet(
    provider: ServiceProviderEntity,
    endorsements: List<SkillEndorsementEntity>,
    districtJobStats: Map<String, Int> = emptyMap(),
    onEndorseSkill: (String) -> Unit,
    onRequestJob: (ServiceProviderEntity) -> Unit,
    onReportUser: (ReportEntity) -> Unit = {},
    activeRegion: CurrencyRegion = CurrencyRegion.currentRegion,
    isAuthenticated: Boolean = false,
    onTriggerAuth: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isReportDialogOpen by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var reportDescription by remember { mutableStateOf("") }

    if (isReportDialogOpen) {
        AlertDialog(
            onDismissRequest = { isReportDialogOpen = false },
            title = { Text("Report User") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Why are you reporting ${provider.name}?", fontSize = 12.sp, color = Slate600)
                    OutlinedTextField(
                        value = reportReason,
                        onValueChange = { reportReason = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reportDescription,
                        onValueChange = { reportDescription = it },
                        label = { Text("Description (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reportReason.isNotBlank()) {
                            onReportUser(
                                ReportEntity(
                                    id = "report_${System.currentTimeMillis()}",
                                    reportedBy = "",
                                    reportedUserId = provider.id,
                                    reportedUserName = provider.name,
                                    reason = reportReason,
                                    description = reportDescription
                                )
                            )
                            isReportDialogOpen = false
                            reportReason = ""
                            reportDescription = ""
                        }
                    },
                    enabled = reportReason.isNotBlank()
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { isReportDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    } // AlertDialog

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
            // Header Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = provider.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Slate900
                        )
                        IconButton(
                            onClick = {
                                ShareHelper.shareProviderProfile(
                                    context = context,
                                    name = provider.name,
                                    trade = provider.tagline,
                                    rating = provider.rating,
                                    endorsements = provider.endorsementsCount,
                                    district = provider.neighborhood
                                )
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = "Share Profile",
                                tint = SignalBlue,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = provider.tagline,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SignalBlue
                    )
                    Text(
                        text = "📍 ${provider.neighborhood} • ${provider.residenceYears} yrs verified resident",
                        fontSize = 12.sp,
                        color = Slate600,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${CurrencyRegion.format(provider.hourlyRate, activeRegion)}/hr",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = IndustrialEmeraldDark
                    )
                    Text(
                        text = "Labor Rate",
                        fontSize = 10.sp,
                        color = Slate500,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Trust Badges Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = IndustrialEmeraldLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialEmerald)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified",
                            tint = IndustrialEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Verified Resident",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndustrialEmeraldDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SignalBlueLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SignalBlue)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Background Checked",
                            tint = SignalBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (provider.kycStatus == "APPROVED") "National ID Verified (${provider.nationalIdNumber})" else "KYC ${provider.kycStatus}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalBlueDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFFFF3E0),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📲 ${provider.momoProvider.replace("_", " ")} Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 14.dp),
                thickness = 1.dp,
                color = Slate200
            )

            // Bio & Background
            Text(
                text = "ABOUT NEIGHBOR PROVIDER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500,
                letterSpacing = 0.5.sp
            )
            Text(
                text = provider.bio,
                fontSize = 13.sp,
                color = Slate700,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Gated Direct Contact Credentials Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isAuthenticated) IndustrialEmeraldLight else Color(0xFFFFFBEB),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isAuthenticated) IndustrialEmerald else AmberUrgent
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAuthenticated) "VERIFIED DIRECT CONTACT" else "DIRECT CONTACT & CREDENTIALS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isAuthenticated) IndustrialEmeraldDark else AmberUrgent
                        )
                        Text(
                            text = if (isAuthenticated) "📞 Phone: +256 701 234 567 • 💬 WhatsApp Verified" else "📞 Phone: +256 7** *** **8 • 🔒 Locked for Public",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    if (!isAuthenticated) {
                        Button(
                            onClick = {
                                onDismiss()
                                onTriggerAuth("Unlock ${provider.name}'s verified phone number & direct contact")
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberUrgent),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Unlock", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IndustrialEmerald,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = "ACTIVE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            // LOC-004: Public Location Privacy & District-Level Job Aggregation
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(8.dp),
                color = Slate50,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DISTRICT WORK HISTORY & COVERAGE",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Emerald600, modifier = Modifier.size(11.dp))
                            Text("Address Privacy Protected", fontSize = 9.5.sp, color = Emerald600, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (districtJobStats.isNotEmpty()) {
                        val topDistricts = districtJobStats.entries.sortedByDescending { it.value }.take(3)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            topDistricts.forEach { (district, count) ->
                                val isPrimary = district == provider.neighborhood
                                Surface(
                                    color = if (isPrimary) Blue50 else Slate100,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = district,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPrimary) SignalBlue else Slate800
                                        )
                                        Text(
                                            text = if (isPrimary) "Primary • $count jobs" else "$count jobs completed",
                                            fontSize = 10.sp,
                                            color = Slate600
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No job history yet in records",
                            fontSize = 11.sp,
                            color = Slate500,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // Endorsement Section
            Text(
                text = "COMMUNITY SKILL ENDORSEMENTS (${provider.endorsementsCount})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 16.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                provider.skillsCsv.split(",").map { it.trim() }.forEach { skill ->
                    val isEndorsedByMe = endorsements.any { it.skillName == skill }
                    val skillEndorsements = endorsements.filter { it.skillName == skill }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = skill,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = if (skillEndorsements.isNotEmpty())
                                        "Endorsed by verified neighbor"
                                    else "Local verified service skill",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }

                            Button(
                                onClick = { onEndorseSkill(skill) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isEndorsedByMe) IndustrialEmeraldLight else Slate100,
                                    contentColor = if (isEndorsedByMe) IndustrialEmeraldDark else Slate800
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = "Endorse",
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isEndorsedByMe) "Endorsed" else "+1 Endorse",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { isReportDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Report User", fontSize = 12.sp, color = Color.Red)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Primary Action Button
            Button(
                onClick = {
                    if (isAuthenticated) {
                        onRequestJob(provider)
                        onDismiss()
                    } else {
                        onDismiss()
                        onTriggerAuth("hire_${provider.id}")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isAuthenticated) SignalBlue else AmberUrgent)
            ) {
                Icon(
                    imageVector = if (isAuthenticated) Icons.Default.Send else Icons.Default.Lock,
                    contentDescription = "Request"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAuthenticated) "Request Job from ${provider.name}" else "🔒 Sign in to Hire ${provider.name}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
