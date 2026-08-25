package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.TranscribedVoiceJob
import com.example.ui.theme.*
import com.example.util.CurrencyRegion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickRequestDrawer(
    onPostJob: (
        title: String,
        category: String,
        description: String,
        budget: Double,
        urgency: String,
        materialsProvidedBy: String,
        laborHours: Double,
        laborRate: Double
    ) -> Unit,
    onStartVoiceRecord: () -> Unit = {},
    onStopVoiceRecordAndTranscribe: () -> Unit = {},
    transcribedVoiceJob: TranscribedVoiceJob? = null,
    isAudioRecording: Boolean = false,
    isAudioTranscribing: Boolean = false,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }

    var category by remember { mutableStateOf("Plumbing") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var urgency by remember { mutableStateOf("Today") }
    var budgetText by remember { mutableStateOf("120") }
    var materialsProvidedBy by remember { mutableStateOf("RESIDENT") } // "RESIDENT" vs "PROVIDER" vs "NONE_NEEDED"
    var laborHoursText by remember { mutableStateOf("2.0") }
    var laborRateText by remember { mutableStateOf("60.0") }

    // Auto-populate when AI Voice Transcription completes
    LaunchedEffect(transcribedVoiceJob) {
        if (transcribedVoiceJob != null) {
            title = transcribedVoiceJob.suggestedTitle
            category = transcribedVoiceJob.suggestedCategory
            description = transcribedVoiceJob.suggestedDescription
            budgetText = transcribedVoiceJob.suggestedBudget.toInt().toString()
        }
    }

    val categories = listOf("Plumbing", "Electrical", "Mechanic", "Tutoring", "Cleaning", "Carpentry", "Gardening")
    val urgencies = listOf("Today", "This Week", "Scheduled")

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
            // Header & Step Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "POST MICRO-JOB REQUEST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SignalBlue
                    )
                    Text(
                        text = "Step $step of 3",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, 3).forEach { s ->
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 6.dp)
                                .background(
                                    if (s <= step) SignalBlue else Slate200,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }

            // Voice AI Transcribe Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isAudioRecording) Color(0xFFFFEBEE) else Color(0xFFE8F0FE),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isAudioRecording) Color.Red else SignalBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = if (isAudioRecording) "🔴 Recording Voice Note..." else "🎙️ Speak Your Request (AI Transcribe)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        }
                        Text(
                            text = if (isAudioRecording) "Speak clearly into your microphone..." else "Gemini 3.5 Flash transcribes voice into job details automatically.",
                            fontSize = 10.sp,
                            color = Slate600
                        )
                    }

                    if (isAudioTranscribing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = SignalBlue)
                    } else if (isAudioRecording) {
                        Button(
                            onClick = onStopVoiceRecordAndTranscribe,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onStartVoiceRecord,
                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

            when (step) {
                1 -> {
                    // Step 1: Category & Title
                    Text(
                        text = "SELECT SERVICE CATEGORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        categories.take(4).forEach { cat ->
                            val isSelected = category == cat
                            Surface(
                                onClick = { category = cat },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) SignalBlueLight else Slate100,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) SignalBlue else Slate300
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) SignalBlueDark else Slate700,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Text(
                        text = "JOB TITLE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("e.g. Emergency sink leak repair", fontSize = 13.sp, color = Slate400) },
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
                }

                2 -> {
                    // Step 2: Description & Urgency
                    Text(
                        text = "JOB DETAILS & DESCRIPTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text("Describe what needs to be fixed or done...", fontSize = 13.sp, color = Slate400) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SignalBlue,
                            unfocusedBorderColor = Slate300
                        )
                    )

                    Text(
                        text = "JOB URGENCY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500,
                        modifier = Modifier.padding(top = 14.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        urgencies.forEach { urg ->
                            val isSelected = urgency == urg
                            Surface(
                                onClick = { urgency = urg },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) AmberUrgentLight else Slate100,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) AmberUrgent else Slate300
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = urg,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AmberUrgent else Slate700,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                3 -> {
                    // Step 3: Material Ownership & Labor Budget
                    Text(
                        text = "MATERIAL & PARTS SUPPLY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val options = listOf(
                            Triple("RESIDENT", "🛠️ Customer Provides Materials", "Labor-Only: Zero material escrow & no receipts needed"),
                            Triple("PROVIDER", "🧰 Worker Sources Materials", "Labor + Materials: Worker buys parts & uploads receipts"),
                            Triple("NONE_NEEDED", "⚡ No Materials Needed", "Services, Diagnostics, or Tutoring")
                        )

                        options.forEach { (code, label, desc) ->
                            val isSelected = materialsProvidedBy == code
                            Surface(
                                onClick = { materialsProvidedBy = code },
                                shape = RoundedCornerShape(6.dp),
                                color = if (isSelected) SignalBlueLight else Slate50,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) SignalBlue else Slate300
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { materialsProvidedBy = code },
                                        colors = RadioButtonDefaults.colors(selectedColor = SignalBlue)
                                    )
                                    Column(modifier = Modifier.padding(start = 6.dp)) {
                                        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                                        Text(text = desc, fontSize = 10.sp, color = Slate600)
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "ESTIMATED HOURS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                            OutlinedTextField(
                                value = laborHoursText,
                                onValueChange = { laborHoursText = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                singleLine = true
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "HOURLY RATE (${CurrencyRegion.currentRegion.symbol})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate500)
                            OutlinedTextField(
                                value = laborRateText,
                                onValueChange = { laborRateText = it },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                shape = RoundedCornerShape(6.dp),
                                singleLine = true
                            )
                        }
                    }

                    // Itemized Calculation Preview
                    val hrs = laborHoursText.toDoubleOrNull() ?: 2.0
                    val rate = laborRateText.toDoubleOrNull() ?: 60.0
                    val lTotal = hrs * rate
                    val isLaborOnly = materialsProvidedBy == "RESIDENT" || materialsProvidedBy == "NONE_NEEDED"
                    val pFee = lTotal * 0.10
                    val gTotal = lTotal + pFee

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .background(Slate50, RoundedCornerShape(6.dp))
                            .border(1.dp, Slate200, RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isLaborOnly) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SignalBlueLight,
                                border = androidx.compose.foundation.BorderStroke(1.dp, SignalBlue)
                            ) {
                                Text(
                                    text = "🛠️ Labor-Only Agreement — Customer provides all parts",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalBlueDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Labor ($hrs hrs @ ${CurrencyRegion.format(rate)}/hr)", fontSize = 12.sp, color = Slate600)
                            Text(text = CurrencyRegion.format(lTotal), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Platform Fee (~10% on Labor)", fontSize = 12.sp, color = Slate600)
                            Text(text = CurrencyRegion.format(pFee), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate900)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Slate300)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total Escrow Lock", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
                            Text(text = CurrencyRegion.format(gTotal), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = IndustrialEmeraldDark)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Footer Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                    ) {
                        Text(text = "Back", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    }
                }

                Button(
                    onClick = {
                        if (step < 3) {
                            step++
                        } else {
                            val hrs = laborHoursText.toDoubleOrNull() ?: 2.0
                            val rate = laborRateText.toDoubleOrNull() ?: 60.0
                            val lTotal = hrs * rate
                            val isLaborOnly = materialsProvidedBy == "RESIDENT" || materialsProvidedBy == "NONE_NEEDED"
                            val pFee = lTotal * 0.10
                            val gTotal = lTotal + pFee

                            val finalTitle = title.ifBlank { "Micro-job Request" }
                            val finalDesc = description.ifBlank { "Need local verified help with $category." }
                            onPostJob(
                                finalTitle,
                                category,
                                finalDesc,
                                gTotal,
                                urgency,
                                materialsProvidedBy,
                                hrs,
                                rate
                            )
                        }
                    },
                    modifier = Modifier.weight(1.2f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                ) {
                    Text(
                        text = if (step < 3) "Next Step" else "Broadcast Job",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (step < 3) Icons.Default.ArrowForward else Icons.Default.Check,
                        contentDescription = "Next"
                    )
                }
            }
        }
    }
}
