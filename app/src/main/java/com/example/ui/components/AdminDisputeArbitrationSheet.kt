package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberUrgent
import com.example.ui.theme.IndustrialEmerald
import com.example.ui.theme.IndustrialEmeraldDark
import com.example.ui.theme.RedDanger
import com.example.ui.theme.SignalBlue
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.util.AdminDisputeDecisions

/**
 * AUDIT-A-ARBITRATE: Admin dispute arbitration console -- operator UI surface.
 *
 * Rows arrive ONLY from the admin-gated getDisputesQueue callable; decisions
 * leave ONLY through resolveDispute. The backend admin custom claim + rules
 * stay the real authorizer; the sheet is a viewer with client-side guardrails
 * (AdminDisputeDecisions) so malformed taps never leave the device.
 *
 * All helpers are file-private with the AdminArb prefix so the public
 * SheetHeader/SheetInfoBox names in AccountCenterSheets.kt stay
 * unambiguous (same package).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDisputeArbitrationSheet(

    isOpen: Boolean,
    disputes: List<Map<String, Any>>,
    isLoading: Boolean,
    arbitrationResult: com.example.ui.viewmodel.SkillsExchangeViewModel.ArbitrationResult?,
    selectedDispute: Map<String, Any>?,
    onRefresh: () -> Unit,
    onSelectDispute: (Map<String, Any>) -> Unit,
    onClearSelection: () -> Unit,
    onSubmitResolution: (
        disputeId: String,
        resolution: String,
        resolutionType: String?,
        resolutionAmount: Double?,
        adminNotes: String?,
        status: String?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var inlineError by remember(isOpen) { mutableStateOf<String?>(null) }
    var isSubmitting by remember(isOpen) { mutableStateOf(false) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            inlineError = null
            isSubmitting = false
            onRefresh()
        }
    }

    LaunchedEffect(arbitrationResult) {
        when (val result = arbitrationResult) {
            is com.example.ui.viewmodel.SkillsExchangeViewModel.ArbitrationResult.Error -> {
                inlineError = result.message
                isSubmitting = false
            }
            is com.example.ui.viewmodel.SkillsExchangeViewModel.ArbitrationResult.Success -> {
                isSubmitting = false
            }
            else -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AdminArbHeader(Icons.Filled.Gavel, "ADMIN - DISPUTE ARBITRATION")
            AdminArbBanner()
            AdminArbHeaderRow(
                queueSize = disputes.size,
                isLoading = isLoading,
                refreshEnabled = !isSubmitting,
                onRefresh = {
                    inlineError = null
                    onRefresh()
                }
            )
            inlineError?.let { Text(it, fontSize = 11.5.sp, color = RedDanger) }
            if (arbitrationResult is com.example.ui.viewmodel.SkillsExchangeViewModel.ArbitrationResult.Success) {
                Text(
                    "Decision recorded.",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IndustrialEmeraldDark
                )
            }
            if (selectedDispute != null) {
                AdminArbDetailCard(
                    dispute = selectedDispute,
                    isSubmitting = isSubmitting,
                    onBack = {
                        inlineError = null
                        isSubmitting = false
                        onClearSelection()
                    },
                    onSubmit = { resolution, resolutionType, resolutionAmount, adminNotes, status ->
                        val disputeId = selectedDispute["id"]?.toString().orEmpty()
                        val blocker = AdminDisputeDecisions.decisionBlocker(
                            disputeId = disputeId,
                            resolution = resolution,
                            resolutionType = resolutionType,
                            resolutionAmount = resolutionAmount,
                            status = status,
                            adminNotes = adminNotes
                        )
                        if (blocker != null) {
                            inlineError = blocker
                        } else {
                            inlineError = null
                            isSubmitting = true
                            onSubmitResolution(
                                disputeId, resolution, resolutionType,
                                resolutionAmount, adminNotes, status
                            )
                        }
                    }
                )
            } else if (disputes.isEmpty() && !isLoading) {
                AdminArbBox("Queue is empty - none await review, or this account lacks the admin claim.")
            } else {
                disputes.forEach { dispute ->
                    AdminArbQueueRow(
                        dispute = dispute,
                        onClick = {
                            inlineError = null
                            onSelectDispute(dispute)
                        }
                    )
                }
            }
            AdminArbCloseButton(onDismiss)
        }
    }
}


@Composable
private fun AdminArbHeader(icon: ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = SignalBlue)
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            color = Slate900
        )
    }
}

@Composable
private fun AdminArbBanner() {
    AdminArbBox(
        "Server-enforced queue: rows come only from the admin-gated callable. " +
            "Decisions run through resolveDispute."
    )
}

@Composable
private fun AdminArbBox(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Text(
            text = message,
            fontSize = 11.sp,
            color = Slate600,
            lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        )
    }
}

@Composable
private fun AdminArbCloseButton(onDismiss: () -> Unit) {
    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Slate900)
    ) {
        Text("Close", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminArbHeaderRow(
    queueSize: Int,
    isLoading: Boolean,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isLoading) "Refreshing queue..." else "$queueSize dispute(s) pending",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = Slate600
        )
        OutlinedButton(
            onClick = onRefresh,
            enabled = refreshEnabled && !isLoading,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Refresh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Map<String, Any>.adminArbString(vararg keys: String): String {
    for (key in keys) {
        val value = this[key] as? String
        if (!value.isNullOrBlank()) return value
    }
    return ""
}

@Composable
private fun AdminArbQueueRow(dispute: Map<String, Any>, onClick: () -> Unit) {
    val disputeId = dispute.adminArbString("id").ifBlank { "Unknown" }
    val reason = dispute.adminArbString("disputeReason", "reason").ifBlank { "N/A" }
    val status = dispute.adminArbString("status").ifBlank { "UNKNOWN" }.uppercase()

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200),
        onClick = onClick
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dispute $disputeId",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (status == "OPEN") SignalBlue.copy(alpha = 0.12f) else Slate200,
                    border = BorderStroke(1.dp, if (status == "OPEN") SignalBlue else Slate500)
                ) {
                    Text(
                        text = status.replace('_', ' '),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (status == "OPEN") SignalBlue else Slate600,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(text = reason, fontSize = 11.sp, color = Slate600)
        }
    }
}

@Composable
private fun AdminArbDetailRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = key, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Slate700)
        Text(text = value, fontSize = 12.sp, color = Slate900)
    }
}


@Composable
private fun AdminArbDetailCard(
    dispute: Map<String, Any>,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onSubmit: (
        resolution: String,
        resolutionType: String?,
        resolutionAmount: Double?,
        adminNotes: String?,
        status: String?
    ) -> Unit
) {
    val disputeId = dispute.adminArbString("id").ifBlank { "-" }
    val jobId = dispute.adminArbString("jobId").ifBlank { "-" }
    val reportedBy = dispute.adminArbString("reportedBy").ifBlank { "-" }
    val reportedUserId = dispute.adminArbString("reportedUserId").ifBlank { "-" }
    val reason = dispute.adminArbString("disputeReason", "reason").ifBlank { "-" }
    val description = dispute.adminArbString("description")
    val evidence = dispute.adminArbString("evidenceUrlsCsv", "evidenceUrls")
    val status = dispute.adminArbString("status").ifBlank { "UNKNOWN" }
    val createdAt = (dispute["createdAt"] as? Number)?.toLong() ?: 0L
    val createdLabel = if (createdAt > 0) {
        java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.US)
            .format(java.util.Date(createdAt))
    } else "Timestamp unavailable"

    var selectedResolution by remember(disputeId) { mutableStateOf("RESOLVED") }
    var selectedResolutionType by remember(disputeId) { mutableStateOf<String?>(null) }
    var amountText by remember(disputeId) { mutableStateOf("") }
    var adminNotes by remember(disputeId) { mutableStateOf("") }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Back to queue", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "Dispute $disputeId", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
            AdminArbDetailRow("Job", jobId)
            AdminArbDetailRow("Reported by", reportedBy)
            AdminArbDetailRow("Reported worker", reportedUserId)
            AdminArbDetailRow("Reason", reason.replace('_', ' '))
            AdminArbDetailRow("Status", status.replace('_', ' '))
            Text("Filed: $createdLabel", fontSize = 10.5.sp, color = Slate600)
            if (description.isNotBlank()) AdminArbBox("Statement: $description")
            if (evidence.isNotBlank()) {
                Text(text = "Evidence: $evidence", fontSize = 10.sp, color = Slate500, lineHeight = 14.sp)
            }
            AdminArbResolutionSection(
                selectedResolution = selectedResolution,
                onSelectResolution = { selectedResolution = it },
                selectedResolutionType = selectedResolutionType,
                onSelectType = { selectedResolutionType = it },
                amountText = amountText,
                onAmountChange = { amountText = it },
                adminNotes = adminNotes,
                onNotesChange = { adminNotes = it },
                isSubmitting = isSubmitting,
                disputeId = disputeId,
                status = status,
                onSubmit = onSubmit
            )
        }
    }
}


@Composable
private fun AdminArbResolutionSection(
    selectedResolution: String,
    onSelectResolution: (String) -> Unit,
    selectedResolutionType: String?,
    onSelectType: (String) -> Unit,
    amountText: String,
    onAmountChange: (String) -> Unit,
    adminNotes: String,
    onNotesChange: (String) -> Unit,
    isSubmitting: Boolean,
    disputeId: String,
    status: String,
    onSubmit: (
        resolution: String,
        resolutionType: String?,
        resolutionAmount: Double?,
        adminNotes: String?,
        status: String?
    ) -> Unit
) {
    Text("Resolution", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate700)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            AdminArbResolutionChip(
                selected = selectedResolution == "RESOLVED",
                onClick = { onSelectResolution("RESOLVED") },
                label = "Resolved",
                color = IndustrialEmerald
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AdminArbResolutionChip(
                selected = selectedResolution == "REJECTED",
                onClick = { onSelectResolution("REJECTED") },
                label = "Rejected",
                color = RedDanger
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AdminArbResolutionChip(
                selected = selectedResolution == "ESCALATED",
                onClick = { onSelectResolution("ESCALATED") },
                label = "Escalated",
                color = AmberUrgent
            )
        }
    }
    Text("Resolution type", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate700)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            AdminArbResolutionTypeChip(
                selected = selectedResolutionType == "REFUND_FULL",
                onClick = { onSelectType("REFUND_FULL") },
                label = "Full Refund"
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AdminArbResolutionTypeChip(
                selected = selectedResolutionType == "REFUND_PARTIAL",
                onClick = { onSelectType("REFUND_PARTIAL") },
                label = "Partial"
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            AdminArbResolutionTypeChip(
                selected = selectedResolutionType == "NO_ACTION",
                onClick = { onSelectType("NO_ACTION") },
                label = "No Action"
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            AdminArbResolutionTypeChip(
                selected = selectedResolutionType == "WORKER_SUSPENDED",
                onClick = { onSelectType("WORKER_SUSPENDED") },
                label = "Suspend"
            )
        }
    }
    if (selectedResolutionType == "REFUND_FULL" || selectedResolutionType == "REFUND_PARTIAL") {
        OutlinedTextField(
            value = amountText,
            onValueChange = onAmountChange,
            label = { Text("Refund amount (UGX)") },
            enabled = !isSubmitting,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
    OutlinedTextField(
        value = adminNotes,
        onValueChange = onNotesChange,
        label = { Text("Admin notes (sent to both parties)") },
        enabled = !isSubmitting,
        minLines = 2,
        modifier = Modifier.fillMaxWidth()
    )
    Button(
        onClick = {
            val amount = amountText.trim().replace(",", "").toDoubleOrNull()
            onSubmit(
                selectedResolution,
                selectedResolutionType,
                amount,
                adminNotes.trim().ifBlank { null },
                status
            )
        },
        enabled = !isSubmitting && disputeId != "-",
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = IndustrialEmerald),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
    ) {
        Text(
            if (isSubmitting) "Submitting..." else "Submit $selectedResolution",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
    if (isSubmitting) Text("Submitting decision...", fontSize = 10.5.sp, color = Slate500)
}


@Composable
private fun AdminArbResolutionChip(selected: Boolean, onClick: () -> Unit, label: String, color: Color) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        border = if (selected) BorderStroke(2.dp, color) else null,
        colors = if (selected) ButtonDefaults.outlinedButtonColors(
            containerColor = color.copy(alpha = 0.15f),
            contentColor = Slate900
        ) else ButtonDefaults.outlinedButtonColors(
            containerColor = Slate50,
            contentColor = Slate600
        )
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun AdminArbResolutionTypeChip(selected: Boolean, onClick: () -> Unit, label: String) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        border = if (selected) BorderStroke(1.dp, SignalBlue) else null,
        colors = if (selected) ButtonDefaults.outlinedButtonColors(
            containerColor = SignalBlue,
            contentColor = Color.White
        ) else ButtonDefaults.outlinedButtonColors(
            containerColor = Slate50,
            contentColor = Slate600
        )
    ) {
        Text(text = label, fontSize = 11.sp)
    }
}
