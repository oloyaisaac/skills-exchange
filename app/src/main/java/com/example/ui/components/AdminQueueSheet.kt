package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberUrgent
import com.example.ui.theme.AmberUrgentLight
import com.example.ui.theme.IndustrialEmerald
import com.example.ui.theme.IndustrialEmeraldDark
import com.example.ui.theme.RedDanger
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.util.AdminQueueDecisions

/**
 * AUDIT-A-CONSOLE (2026-10-15): Admin verification queue — FIRST operator UI surface.
 *
 * `adminVerificationQueue` + `submitAdminDecision()` had zero Composable call sites.
 * Rows arrive ONLY from the admin-gated callable; decisions leave ONLY through
 * `reviewWorkerApplication`. The backend `admin` custom claim + rules stay the real
 * authorizer; the sheet is a viewer with an explicit operator unlock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminVerificationQueueSheet(
    isOpen: Boolean,
    queue: List<Map<String, Any>>,
    isLoading: Boolean,
    isAdminUnlocked: Boolean,
    onUnlockOperator: () -> Unit = {},
    onRefresh: () -> Unit,
    onSubmitDecision: (userId: String, decision: String, feedback: String, onResult: (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var feedbackByUser by remember(isOpen) { mutableStateOf(mapOf<String, String>()) }
    var pendingUser by remember(isOpen) { mutableStateOf<String?>(null) }
    var inlineError by remember(isOpen) { mutableStateOf<String?>(null) }
    var successNote by remember(isOpen) { mutableStateOf<String?>(null) }

    LaunchedEffect(isOpen) {
        if (isOpen) {
            inlineError = null
            successNote = null
            onRefresh()
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
            AdminQueueTitle()
            AdminQueueBanner(isAdminUnlocked, onUnlockOperator)
            AdminQueueHeaderRow(
                queueSize = queue.size,
                isLoading = isLoading,
                refreshEnabled = isAdminUnlocked && pendingUser == null,
                onRefresh = {
                    inlineError = null
                    successNote = null
                    onRefresh()
                }
            )
            inlineError?.let { Text(it, fontSize = 11.5.sp, color = RedDanger) }
            successNote?.let {
                Text(it, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = IndustrialEmeraldDark)
            }
            if (queue.isEmpty() && !isLoading) {
                AdminQueueEmptyBox()
            } else {
                queue.forEach { row ->
                    val key = row.adminQueueUserId().orEmpty()
                    AdminVerificationRow(
                        row = row,
                        actionsEnabled = isAdminUnlocked && pendingUser == null,
                        feedback = feedbackByUser[key].orEmpty(),
                        onFeedbackChange = { value ->
                            feedbackByUser = feedbackByUser + (key to value)
                            inlineError = null
                            successNote = null
                        },
                        onDecide = { decision ->
                            val feedback = feedbackByUser[key].orEmpty().trim()
                            val blocker = AdminQueueDecisions.decisionBlocker(
                                isAdminUnlocked = isAdminUnlocked,
                                userId = key,
                                decision = decision,
                                feedback = feedback
                            )
                            if (blocker != null) {
                                inlineError = blocker
                            } else {
                                inlineError = null
                                pendingUser = key
                                onSubmitDecision(key, decision, feedback) { ok ->
                                    pendingUser = null
                                    if (ok) successNote = "Decision recorded for $key."
                                    else inlineError = "Backend rejected it (no admin claim or network)."
                                }
                            }
                        },
                        isPending = pendingUser == key
                    )
                }
            }
            AdminQueueCloseButton(onDismiss)
        }
    }
}

/** Title header (local, so AccountCenterSheets internals stay untouched). */
@Composable
private fun AdminQueueTitle() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(imageVector = Icons.Outlined.VerifiedUser, contentDescription = null, tint = com.example.ui.theme.SignalBlue)
        Text(text = "ADMIN • VERIFICATION QUEUE", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = Slate900)
    }
}

@Composable
private fun AdminQueueBanner(isAdminUnlocked: Boolean, onUnlockOperator: () -> Unit) {
    AdminQueueBox(
        if (!isAdminUnlocked) "Operator access is locked. Rows come only from the admin-gated callable; decisions stay disabled until unlock."
        else "Server-enforced queue: masked IDs + private paths only. Decisions run through reviewWorkerApplication."
    )
    if (!isAdminUnlocked) {
        OutlinedButton(
            onClick = onUnlockOperator,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Unlock operator console on this device", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminQueueEmptyBox() {
    AdminQueueBox("Queue is empty — none await review, or this account lacks the admin claim.")
}

@Composable
private fun AdminQueueBox(message: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = Slate50, border = BorderStroke(1.dp, Slate200)) {
        Text(text = message, fontSize = 11.sp, color = Slate600, lineHeight = 15.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
    }
}

@Composable
private fun AdminQueueCloseButton(onDismiss: () -> Unit) {
    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Slate900)) {
        Text("Close", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AdminQueueHeaderRow(queueSize: Int, isLoading: Boolean, refreshEnabled: Boolean, onRefresh: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = if (isLoading) "Refreshing queue…" else "$queueSize application(s) pending", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Slate600)
        OutlinedButton(onClick = onRefresh, enabled = refreshEnabled && !isLoading, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
            Text("Refresh", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Lenient readers for the untyped callable payload. */
private fun Map<String, Any>.adminQueueString(vararg keys: String): String {
    for (key in keys) {
        val value = this[key] as? String
        if (!value.isNullOrBlank()) return value
    }
    return ""
}

private fun Map<String, Any>.adminQueueTime(vararg keys: String): Long {
    for (key in keys) {
        when (val value = this[key]) {
            is Number -> if (value.toLong() > 0) return value.toLong()
            is String -> value.toLongOrNull()?.let { if (it > 0) return it }
        }
    }
    return 0L
}

private fun Map<String, Any>.adminQueueUserId(): String? =
    (this["userId"] as? String)?.takeIf { it.isNotBlank() }

@Composable
private fun AdminVerificationRow(
    row: Map<String, Any>,
    actionsEnabled: Boolean,
    feedback: String,
    onFeedbackChange: (String) -> Unit,
    onDecide: (String) -> Unit,
    isPending: Boolean
) {
    val userId = row.adminQueueUserId().orEmpty()
    val name = row.adminQueueString("fullLegalName", "fullName")
    val maskedNin = row.adminQueueString("nationalIdNumberMasked")
    val status = row.adminQueueString("status").ifBlank { "PENDING_REVIEW" }
    val submittedAt = row.adminQueueTime("submittedAt", "guarantorSubmittedAt", "sortTimestamp")
    val submittedLabel = if (submittedAt > 0) {
        java.text.SimpleDateFormat("dd MMM yyyy HH:mm", java.util.Locale.US)
            .format(java.util.Date(submittedAt))
    } else "Timestamp unavailable"
    val idFront = row.adminQueueString("idFrontStoragePath")
    val idBack = row.adminQueueString("idBackStoragePath")
    val guarantorStatus = row.adminQueueString("guarantorStatus")
    val reviewFeedback = row.adminQueueString("reviewFeedback")

    Surface(shape = RoundedCornerShape(10.dp), color = Slate50, border = BorderStroke(1.dp, Slate200)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(text = name.ifBlank { if (userId.isNotBlank()) "Applicant $userId" else "Unknown applicant" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Text("UID: ${userId.ifBlank { "—" }}", fontSize = 10.sp, color = Slate500)
                }
                Surface(shape = RoundedCornerShape(12.dp), color = AmberUrgentLight, border = BorderStroke(1.dp, AmberUrgent)) {
                    Text(text = status.replace('_', ' '), fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = AmberUrgent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            AdminQueueStatusLine(label = "National ID (masked)", value = maskedNin.ifBlank { "Not on file" })
            Text("Submitted: $submittedLabel", fontSize = 10.5.sp, color = Slate600)
            if (guarantorStatus.isNotBlank()) Text("Guarantors: ${guarantorStatus.replace('_', ' ')}", fontSize = 10.5.sp, color = Slate600)
            if (idFront.isNotBlank() || idBack.isNotBlank()) {
                Text(
                    text = "ID evidence (private paths — admin session only):" +
                        (if (idFront.isNotBlank()) "\n• Front: $idFront" else "") +
                        (if (idBack.isNotBlank()) "\n• Back: $idBack" else ""),
                    fontSize = 10.sp, color = Slate500, lineHeight = 14.sp
                )
            }
            if (reviewFeedback.isNotBlank()) AdminQueueBox("Previous feedback: $reviewFeedback")
            OutlinedTextField(value = feedback, onValueChange = onFeedbackChange, label = { Text("Feedback (required to reject / request changes)") }, enabled = actionsEnabled && !isPending, minLines = 2, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminDecisionButton(label = "Approve", enabled = actionsEnabled && !isPending && userId.isNotBlank(), container = IndustrialEmerald, modifier = Modifier.weight(1f), onClick = { onDecide("APPROVE") })
                AdminDecisionButton(label = "Changes", enabled = actionsEnabled && !isPending && userId.isNotBlank(), container = AmberUrgent, modifier = Modifier.weight(1f), onClick = { onDecide("CHANGES_REQUIRED") })
                AdminDecisionButton(label = "Reject", enabled = actionsEnabled && !isPending && userId.isNotBlank(), container = RedDanger, modifier = Modifier.weight(1f), onClick = { onDecide("REJECT") })
            }
            if (isPending) Text("Submitting decision…", fontSize = 10.5.sp, color = Slate500)
        }
    }
}

@Composable
private fun AdminQueueStatusLine(label: String, value: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = Slate50, border = BorderStroke(1.dp, Slate200)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
            Text(text = value, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold, color = Slate700)
        }
    }
}

@Composable
private fun AdminDecisionButton(label: String, enabled: Boolean, container: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(6.dp), colors = ButtonDefaults.buttonColors(containerColor = container), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}




