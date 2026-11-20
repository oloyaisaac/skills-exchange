package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.ReportCategories
import com.example.ui.a11y.announcementLiveRegion
import com.example.ui.a11y.sectionHeading
import com.example.ui.theme.RedDanger
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate900

/**
 * R10-P2-02 / P2-07: the safety desk.
 *
 * Reports used to be rows in the reporter's own phone — no operator could see one, and nothing
 * measured whether anyone answered. The queue here arrives only from the admin-gated
 * `getReportsQueue` callable; the priority, the response target and the breach flag are all decided
 * on the server (`functions/src/reports.ts`) and shown as received. Decisions leave only through
 * `resolveReport`, and the sentence displayed afterwards is the server's own.
 *
 * Nothing here infers a healthy desk: when the metrics call fails the header says the numbers are
 * unknown rather than showing zeros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyReportQueueSheet(
    isOpen: Boolean,
    queue: List<Map<String, Any>>,
    metricsLine: String?,
    isLoading: Boolean,
    decisionMessage: String?,
    onRefresh: () -> Unit,
    onResolve: (reportId: String, status: String, outcome: String) -> Unit,
    onDismissDecisionMessage: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var noteByReport by remember(isOpen) { mutableStateOf(mapOf<String, String>()) }

    LaunchedEffect(isOpen) { if (isOpen) onRefresh() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                // R12-P2-01/P2-02: translatable, and a heading so TalkBack can jump to it.
                text = stringResource(R.string.safety_desk_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900,
                modifier = Modifier.sectionHeading()
            )
            Text(
                // The desk's own line, composed on the server. Null is "unknown", never "clear".
                text = metricsLine
                    ?: "Desk metrics unavailable — the queue below may be incomplete. Do not read this as a clear desk.",
                fontSize = 11.sp,
                fontWeight = if (metricsLine == null) FontWeight.Bold else FontWeight.Normal,
                color = if (metricsLine == null) RedDanger else Slate600,
                // R12-P2-02: an unreadable desk is the state an operator must hear about.
                modifier = Modifier.announcementLiveRegion()
            )

            if (decisionMessage != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Slate50,
                    border = BorderStroke(1.dp, Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = decisionMessage, fontSize = 11.sp, color = Slate600)
                        OutlinedButton(onClick = onDismissDecisionMessage) { Text("Dismiss", fontSize = 11.sp) }
                    }
                }
            }

            if (isLoading && queue.isEmpty()) {
                Text(text = "Loading…", fontSize = 11.sp, color = Slate500)
            } else if (queue.isEmpty()) {
                Text(
                    text = stringResource(R.string.safety_desk_empty),
                    fontSize = 11.sp,
                    color = Slate500
                )
            }

            queue.forEach { report ->
                val reportId = (report["id"] as? String).orEmpty()
                val category = (report["category"] as? String).orEmpty()
                val priority = (report["priority"] as? String).orEmpty()
                val status = (report["status"] as? String).orEmpty()
                val details = (report["details"] as? String).orEmpty()
                val subjectName = (report["subjectName"] as? String).orEmpty()
                // `breachingSla` is computed by the callable against the server clock; this screen
                // never re-derives a breach from a device clock that may be wrong.
                val breached = report["breachingSla"] as? Boolean ?: false
                val slaDueAt = (report["slaDueAt"] as? Number)?.toLong() ?: 0L
                val createdAt = (report["createdAt"] as? Number)?.toLong() ?: 0L
                val targetMinutes = if (slaDueAt > createdAt) ((slaDueAt - createdAt) / 60000L).toInt() else null
                val closed = status == "ACTIONED" || status == "DISMISSED"

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, if (breached) RedDanger else Slate200),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = priority + " · " + ReportCategories.label(category) + " · " + status,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (breached) RedDanger else Slate900
                        )
                        if (subjectName.isNotBlank()) {
                            Text(text = "About: " + subjectName, fontSize = 10.sp, color = Slate500)
                        }
                        // The response target is the server's own window for this category.
                        Text(
                            text = (targetMinutes?.let { "First response target: " + it + " min" }
                                ?: "No response target recorded") + (if (breached) " · SLA BREACHED" else ""),
                            fontSize = 10.sp,
                            color = if (breached) RedDanger else Slate500
                        )
                        if (details.isNotBlank()) {
                            Text(text = details, fontSize = 11.sp, color = Slate600)
                        }
                        if (!closed) {
                            OutlinedTextField(
                                value = noteByReport[reportId].orEmpty(),
                                onValueChange = { noteByReport = noteByReport + (reportId to it) },
                                label = {
                                    Text("Outcome (5+ characters; required to close)", fontSize = 10.sp)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                ReportCategories.DECISIONS.forEach { decision ->
                                    Button(
                                        onClick = {
                                            onResolve(reportId, decision, noteByReport[reportId].orEmpty())
                                        },
                                        // The server refuses to close a report without a readable
                                        // outcome; the button does not pretend otherwise.
                                        enabled = reportId.isNotBlank() &&
                                            (!ReportCategories.closesReport(decision) ||
                                                noteByReport[reportId].orEmpty().trim().length >= 5),
                                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = decision,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
