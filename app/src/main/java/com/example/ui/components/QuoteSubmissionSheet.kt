package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JobPostEntity
import com.example.data.repository.SkillsExchangeRepository
import com.example.ui.theme.IndustrialEmeraldDark
import com.example.ui.theme.SignalBlue
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900
import com.example.util.QuoteProposalValidators

/**
 * AUDIT-W-QUOTE-UI: Worker quote proposal sheet.
 *
 * Flow: Worker Job Feed -> Submit Quote -> this sheet -> rate + hours + notes ->
 * secured `ViewModel.submitQuote(...)` -> success/failure feedback.
 *
 * Thin form over the ALREADY-SECURED backend: never supplies a worker UID (the
 * repository resolves the authoritative Firebase UID itself), never writes
 * Firestore directly, never decides authorization — backend rejections surface
 * via [submissionResult].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteSubmissionSheet(
    isOpen: Boolean,
    job: JobPostEntity?,
    isSubmitting: Boolean,
    submissionResult: SkillsExchangeRepository.QuoteSubmissionResult?,
    submissionError: String?,
    onSubmit: (rate: Double, hours: Double, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentJob = job
    if (!isOpen || currentJob == null) return

    var rateRaw by remember(currentJob.id, isOpen) { mutableStateOf("") }
    var hoursRaw by remember(currentJob.id, isOpen) { mutableStateOf("") }
    var notes by remember(currentJob.id, isOpen) { mutableStateOf("") }
    var attemptedSubmit by remember(currentJob.id, isOpen) { mutableStateOf(false) }
    var submittedThisOpen by remember(currentJob.id, isOpen) { mutableStateOf(false) }

    LaunchedEffect(submissionResult, isOpen) {
        if (submissionResult != null) submittedThisOpen = true
    }

    val rateError = QuoteProposalValidators.validateRate(rateRaw)
    val hoursError = QuoteProposalValidators.validateHours(hoursRaw)
    val notesError = QuoteProposalValidators.validateNotes(notes)
    val total = QuoteProposalValidators.safeTotal(rateRaw, hoursRaw)
    val formBlocked = QuoteProposalValidators.quoteFormBlocker(rateRaw, hoursRaw, notes) != null
    val locked = isSubmitting || (submittedThisOpen && submissionResult != null)

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.6f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Submit Quote", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Slate900)

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Slate100),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(currentJob.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    Text("${currentJob.district} • ${currentJob.category}", fontSize = 12.sp, color = Slate500)
                    Text(currentJob.description, fontSize = 12.sp, color = Slate600, maxLines = 2)
                }
            }

            OutlinedTextField(
                value = rateRaw,
                onValueChange = { rateRaw = it },
                label = { Text("Proposed rate (UGX per hour)") },
                placeholder = { Text("e.g. 15000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !locked,
                isError = attemptedSubmit && rateError != null,
                supportingText = {
                    if (attemptedSubmit && rateError != null) {
                        Text(rateError, color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = hoursRaw,
                onValueChange = { hoursRaw = it },
                label = { Text("Estimated hours") },
                placeholder = { Text("e.g. 3") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                enabled = !locked,
                isError = attemptedSubmit && hoursError != null,
                supportingText = {
                    if (attemptedSubmit && hoursError != null) {
                        Text(hoursError, color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= QuoteProposalValidators.MAX_NOTES_LENGTH) notes = it },
                label = { Text("Notes / terms (optional)") },
                placeholder = { Text("e.g. I can complete the repair today.") },
                enabled = !locked,
                isError = notesError != null,
                supportingText = {
                    if (notesError != null) {
                        Text(notesError, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("${notes.length}/${QuoteProposalValidators.MAX_NOTES_LENGTH}")
                    }
                },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Slate100),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Estimated total", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                    Text(
                        QuoteProposalValidators.formatUgxTotal(total),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = IndustrialEmeraldDark,
                    )
                }
            }

            if (submissionResult != null) {
                val result = submissionResult
                val isSuccess = result is SkillsExchangeRepository.QuoteSubmissionResult.Success
                Text(
                    submissionError ?: QuoteProposalValidators.submissionResultMessage(
                        result.javaClass.simpleName,
                    ),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSuccess) IndustrialEmeraldDark else MaterialTheme.colorScheme.error,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, enabled = !isSubmitting, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        attemptedSubmit = true
                        if (formBlocked || isSubmitting) return@Button
                        val rate = QuoteProposalValidators.parsePositiveAmount(rateRaw) ?: return@Button
                        val hours = QuoteProposalValidators.parsePositiveAmount(hoursRaw) ?: return@Button
                        submittedThisOpen = true
                        onSubmit(rate, hours, notes.trim())
                    },
                    enabled = !formBlocked && !locked,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Submitting…")
                    } else {
                        Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Submit Quote")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}