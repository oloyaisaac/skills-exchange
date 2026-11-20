package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.TranscribedVoiceJob
import com.example.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Three-step quick-request sheet.
 *
 * R01-P2-01: this file was previously spliced together — helper composables were inserted into the
 * middle of [QuickRequestDrawer]'s body, the `package`/import header appeared twice, and the
 * `onPostJob` lambda contained `materialsP rovidedBy`. It now holds one coherent declaration per
 * composable, and every value it submits is one the caller actually accepts.
 *
 * Photos are selectable and previewed locally; they are submitted as an empty list because no
 * Storage upload path exists for a quick request yet (passing fake URLs would be worse).
 */
@Composable
fun JobTypeSelector(
    jobType: String,
    recurringSchedule: String,
    onJobTypeChange: (String) -> Unit,
    onScheduleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JobTypeChip(
                label = "One-Time",
                selected = jobType == "ONE_TIME",
                onClick = { onJobTypeChange("ONE_TIME") },
                modifier = Modifier.weight(1f)
            )
            JobTypeChip(
                label = "Recurring",
                selected = jobType == "RECURRING",
                onClick = { onJobTypeChange("RECURRING") },
                modifier = Modifier.weight(1f)
            )
        }
        if (jobType == "RECURRING") {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("DAILY", "WEEKLY", "MONTHLY").forEach { schedule ->
                    JobTypeChip(
                        label = schedule.lowercase().replaceFirstChar { it.uppercase() },
                        selected = recurringSchedule == schedule,
                        onClick = { onScheduleChange(schedule) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun JobTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Emerald100 else Slate100,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Emerald500 else Slate300),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Emerald700 else Slate600,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
}

@Composable
fun PhotoPreviewCard(
    uri: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Photo preview",
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(6.dp))
            )
            IconButton(onClick = onRemove, modifier = Modifier.height(24.dp)) {
                Icon(Icons.Default.RemoveCircle, contentDescription = "Remove photo", tint = RedDanger)
            }
        }
    }
}

@Composable
fun JobPhotoPicker(
    selectedUris: List<Uri>,
    onUrisChange: (List<Uri>) -> Unit,
    maxPhotos: Int,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            selectedUris.take(maxPhotos).forEachIndexed { index, uri ->
                Box {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Photo $index",
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp))
                    )
                    IconButton(
                        onClick = { onUrisChange(selectedUris - uri) },
                        // R12-P2-04: 48dp touch target for removing a photo.
                        modifier = Modifier.size(48.dp).align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove photo", tint = RedDanger)
                    }
                }
            }
            if (selectedUris.size < maxPhotos) {
                OutlinedButton(
                    onClick = { showPicker = true },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Add photo", fontSize = 11.sp)
                }
            }
        }

        if (showPicker) {
            PhotoPickerDialog(
                onDismissRequest = { showPicker = false },
                initialUriList = selectedUris,
                onUrisSelected = onUrisChange,
                maxPhotos = maxPhotos
            )
        }
    }
}

@Composable
fun PhotoPickerDialog(
    onDismissRequest: () -> Unit,
    initialUriList: List<Uri>,
    onUrisSelected: (List<Uri>) -> Unit,
    maxPhotos: Int
) {
    var selectedList by remember { mutableStateOf(initialUriList) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxOf(2, maxPhotos))
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedList = (selectedList + uris).take(maxPhotos)
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select photos") },
        text = {
            Column {
                if (selectedList.isEmpty()) {
                    Text("No photos selected yet.", fontSize = 12.sp, color = Slate500)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(selectedList) { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Selected photo",
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedList.isNotEmpty(),
                onClick = {
                    onUrisSelected(selectedList)
                    onDismissRequest()
                }
            ) { Text("Done") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (selectedList.size < maxPhotos) {
                    TextButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    ) { Text("Add") }
                }
                TextButton(onClick = onDismissRequest) { Text("Cancel") }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsDatePicker(
    selectedDate: Long,
    onDateChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    val displayDate = if (selectedDate > 0) {
        Instant.ofEpochMilli(selectedDate).atZone(ZoneId.systemDefault()).format(formatter)
    } else {
        "No date set"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(displayDate, fontSize = 12.sp)
            if (selectedDate <= 0) {
                Text("  (pick date)", color = Slate500, fontSize = 12.sp)
            }
        }

        if (showPicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.takeIf { it > 0 }
            )
            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val base = datePickerState.selectedDateMillis
                            if (base != null) {
                                // Keep any time that was already chosen; default to 09:00 UTC.
                                val zone = ZoneOffset.UTC
                                val previous = if (selectedDate > 0) {
                                    Instant.ofEpochMilli(selectedDate).atZone(zone).toLocalTime()
                                } else {
                                    LocalTime.of(9, 0)
                                }
                                val date = Instant.ofEpochMilli(base).atZone(zone).toLocalDate()
                                onDateChange(
                                    LocalDateTime.of(date, previous).toInstant(zone).toEpochMilli()
                                )
                            }
                            showPicker = false
                        }
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showPicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsTimePicker(
    selectedTime: Long,
    onTimeChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val displayTime = if (selectedTime > 0) {
        Instant.ofEpochMilli(selectedTime).atZone(ZoneOffset.UTC).format(formatter)
    } else {
        "No time set"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(displayTime, fontSize = 12.sp)
            if (selectedTime <= 0) {
                Text("  (pick time)", color = Slate500, fontSize = 12.sp)
            }
        }

        if (showPicker) {
            val timeState = rememberTimePickerState(
                initialHour = if (selectedTime > 0) {
                    Instant.ofEpochMilli(selectedTime).atZone(ZoneOffset.UTC).hour
                } else 9,
                initialMinute = if (selectedTime > 0) {
                    Instant.ofEpochMilli(selectedTime).atZone(ZoneOffset.UTC).minute
                } else 0,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showPicker = false },
                title = { Text("Pick time") },
                text = { TimePicker(state = timeState) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val date = if (selectedTime > 0) {
                                Instant.ofEpochMilli(selectedTime).atZone(ZoneOffset.UTC).toLocalDate()
                            } else {
                                LocalDate.now(ZoneOffset.UTC)
                            }
                            onTimeChange(
                                LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                                    .toInstant(ZoneOffset.UTC)
                                    .toEpochMilli()
                            )
                            showPicker = false
                        }
                    ) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showPicker = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
fun AddressDetailsRow(
    streetAddress: String,
    apartmentSuite: String,
    landmarkDirections: String,
    onStreetAddressChange: (String) -> Unit,
    onApartmentSuiteChange: (String) -> Unit,
    onLandmarkDirectionsChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = streetAddress,
            onValueChange = onStreetAddressChange,
            label = { Text("Street address / road") },
            placeholder = { Text("e.g. Main Street, near City Park") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            singleLine = true
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = apartmentSuite,
            onValueChange = onApartmentSuiteChange,
            label = { Text("Apartment / suite / building") },
            placeholder = { Text("e.g. 2B, Green Apartments") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            singleLine = true
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = landmarkDirections,
            onValueChange = onLandmarkDirectionsChange,
            label = { Text("Landmark & directions") },
            placeholder = { Text("e.g. blue gate, near the mango tree") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            singleLine = false
        )
    }
}

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
        laborRate: Double,
        jobType: String,
        recurringSchedule: String,
        streetAddress: String,
        apartmentSuite: String,
        landmarkDirections: String,
        scheduledAt: Long,
        photos: List<String>
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
    var materialsProvidedBy by remember { mutableStateOf("RESIDENT") }
    var laborHoursText by remember { mutableStateOf("2.0") }
    var laborRateText by remember { mutableStateOf("60.0") }
    var jobType by remember { mutableStateOf("ONE_TIME") }
    var recurringSchedule by remember { mutableStateOf("WEEKLY") }
    var streetAddress by remember { mutableStateOf("") }
    var apartmentSuite by remember { mutableStateOf("") }
    var landmarkDirections by remember { mutableStateOf("") }
    var scheduledDate by remember { mutableLongStateOf(0L) }
    var scheduledTime by remember { mutableLongStateOf(0L) }
    var photoUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    LaunchedEffect(transcribedVoiceJob) {
        val transcribed = transcribedVoiceJob ?: return@LaunchedEffect
        title = transcribed.suggestedTitle
        category = transcribed.suggestedCategory
        description = transcribed.suggestedDescription
        budgetText = transcribed.suggestedBudget.toInt().toString()
    }

    val categories = listOf("Plumbing", "Electrical", "Mechanic", "Tutoring", "Cleaning", "Carpentry", "Gardening")
    val urgencies = listOf("Today", "This Week", "Scheduled")

    val scheduledAt = when {
        scheduledDate > 0 && scheduledTime > 0 -> {
            val date = Instant.ofEpochMilli(scheduledDate).atZone(ZoneOffset.UTC).toLocalDate()
            val time = Instant.ofEpochMilli(scheduledTime).atZone(ZoneOffset.UTC).toLocalTime()
            LocalDateTime.of(date, time).toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        scheduledDate > 0 -> scheduledDate
        else -> 0L
    }

    val budgetValue = budgetText.toDoubleOrNull()
    val laborHours = laborHoursText.toDoubleOrNull() ?: 0.0
    val laborRate = laborRateText.toDoubleOrNull() ?: 0.0
    val canContinue = when (step) {
        1 -> title.isNotBlank() && description.isNotBlank() && budgetValue != null && budgetValue > 0
        2 -> true
        else -> true
    }

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
                .verticalScroll(rememberScrollState())
        ) {
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
                    listOf(1, 2, 3).forEach { index ->
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 6.dp)
                                .background(
                                    if (index <= step) SignalBlue else Slate200,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (step == 1) {
                VoiceCaptureBanner(
                    isAudioRecording = isAudioRecording,
                    isAudioTranscribing = isAudioTranscribing,
                    onStart = onStartVoiceRecord,
                    onStop = onStopVoiceRecordAndTranscribe
                )
                Spacer(Modifier.height(12.dp))

                Text("Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = item },
                            label = { Text(item, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. Fix leaking kitchen tap") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    placeholder = { Text("What needs to be done?") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Budget (UGX)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = laborHoursText,
                        onValueChange = { laborHoursText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Hours") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = laborRateText,
                    onValueChange = { laborRateText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Rate (UGX/hour)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))
                Text("Urgency", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    urgencies.forEach { option ->
                        FilterChip(
                            selected = urgency == option,
                            onClick = { urgency = option },
                            label = { Text(option, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Materials", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("RESIDENT", "PROVIDER", "NONE_NEEDED").forEach { option ->
                        FilterChip(
                            selected = materialsProvidedBy == option,
                            onClick = { materialsProvidedBy = option },
                            label = {
                                Text(
                                    option.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                                    fontSize = 11.sp
                                )
                            }
                        )
                    }
                }
            }

            if (step == 2) {
                Text("Job site", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(Modifier.height(4.dp))
                AddressDetailsRow(
                    streetAddress = streetAddress,
                    apartmentSuite = apartmentSuite,
                    landmarkDirections = landmarkDirections,
                    onStreetAddressChange = { streetAddress = it },
                    onApartmentSuiteChange = { apartmentSuite = it },
                    onLandmarkDirectionsChange = { landmarkDirections = it }
                )

                Spacer(Modifier.height(10.dp))
                Text("When (optional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppointmentsDatePicker(
                        selectedDate = scheduledDate,
                        onDateChange = { scheduledDate = it },
                        modifier = Modifier.weight(1f)
                    )
                    AppointmentsTimePicker(
                        selectedTime = scheduledTime,
                        onTimeChange = { scheduledTime = it },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text("Photos (optional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(Modifier.height(4.dp))
                JobPhotoPicker(
                    selectedUris = photoUris,
                    onUrisChange = { photoUris = it },
                    maxPhotos = 3
                )

                Spacer(Modifier.height(10.dp))
                Text("Job type", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(Modifier.height(4.dp))
                JobTypeSelector(
                    jobType = jobType,
                    recurringSchedule = recurringSchedule,
                    onJobTypeChange = { jobType = it },
                    onScheduleChange = { recurringSchedule = it }
                )
            }

            if (step == 3) {
                Text("Review", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                Spacer(Modifier.height(6.dp))
                ReviewRow("Category", category)
                ReviewRow("Title", title)
                ReviewRow("Description", description)
                ReviewRow("Budget", if (budgetValue != null) "UGX ${budgetValue.toInt()}" else "not set")
                ReviewRow("Urgency", urgency)
                ReviewRow("Materials", materialsProvidedBy)
                ReviewRow("Job type", if (jobType == "RECURRING") "Recurring ($recurringSchedule)" else "One-time")
                ReviewRow("Address", listOf(streetAddress, apartmentSuite, landmarkDirections)
                    .filter { it.isNotBlank() }.joinToString(", ").ifBlank { "not set" })
                ReviewRow("Scheduled", if (scheduledAt > 0) "yes" else "as soon as possible")
                ReviewRow("Photos", "${photoUris.size} selected (not uploaded yet)")
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Back") }
                }
                Button(
                    enabled = canContinue,
                    onClick = {
                        if (step < 3) {
                            step += 1
                        } else {
                            val budget = budgetValue ?: return@Button
                            onPostJob(
                                title,
                                category,
                                description,
                                budget,
                                urgency,
                                materialsProvidedBy,
                                laborHours,
                                laborRate,
                                jobType,
                                recurringSchedule,
                                streetAddress,
                                apartmentSuite,
                                landmarkDirections,
                                scheduledAt,
                                emptyList()
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (step < 3) "Continue" else "Post request")
                }
            }
        }
    }
}

@Composable
private fun VoiceCaptureBanner(
    isAudioRecording: Boolean,
    isAudioTranscribing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isAudioRecording) RedDangerLight else Blue50,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isAudioRecording) RedDanger else SignalBlue),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAudioRecording) "Recording voice note..." else "Speak your request",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )
                Text(
                    text = if (isAudioTranscribing) {
                        "Transcribing..."
                    } else {
                        "Voice is transcribed into the form; review it before posting."
                    },
                    fontSize = 11.sp,
                    color = Slate500
                )
            }
            IconButton(
                onClick = { if (isAudioRecording) onStop() else onStart() },
                enabled = !isAudioTranscribing
            ) {
                Icon(
                    imageVector = if (isAudioRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isAudioRecording) "Stop recording" else "Start recording",
                    tint = if (isAudioRecording) RedDanger else SignalBlue
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = Slate500)
        Text(value, fontSize = 11.sp, color = Slate900, fontWeight = FontWeight.Medium)
    }
}
