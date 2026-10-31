package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import android.net.Uri
import com.example.data.remote.TranscribedVoiceJob
import com.example.ui.theme.*
import com.example.util.CurrencyRegion
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import android.widget.Toast
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
import android.net.Uri
import com.example.data.remote.TranscribedVoiceJob
import com.example.ui.theme.*
import com.example.util.CurrencyRegion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobTypeSelector(
    jobType: String,
    recurringSchedule: String,
    onJobTypeChange: (String) -> Unit,
    onScheduleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val jobTypes = listOf(
        Triple("ONE_TIME", "One-Time Job", "Single execution task"),
        Triple("RECURRING", "Recurring Contract", "Ongoing service schedule")
    )

    val schedules = listOf(
        Triple("DAILY", "Daily", "Every day"),
        Triple("WEEKLY", "Weekly", "Once a week"),
        Triple("BI_WEEKLY", "Bi-Weekly", "Every 2 weeks"),
        Triple("MONTHLY", "Monthly", "Once a month"),
        Triple("CUSTOM", "Custom Days", "Pick specific days")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "JOB TYPE",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Slate500
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            jobTypes.forEach { (code, label, desc) ->
                val isSelected = jobType == code
                Surface(
                    onClick = { onJobTypeChange(code) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (isSelected) SignalBlueLight else Slate50,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) SignalBlue else Slate300
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) SignalBlueDark else Slate700
                        )
                        Text(
                            text = desc,
                            fontSize = 9.sp,
                            color = Slate500,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        if (jobType == "RECURRING") {
            Text(
                text = "REPEAT SCHEDULE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Slate500,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                schedules.forEach { (code, label, _) ->
                    val isSelected = recurringSchedule == code
                    Surface(
                        onClick = { onScheduleChange(code) },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) Emerald100 else Slate100,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Emerald500 else Slate300
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Emerald700 else Slate600,
                            modifier = Modifier.padding(vertical = 6.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import android.net.Uri
import com.example.data.remote.TranscribedVoiceJob
import com.example.ui.theme.*
import com.example.util.CurrencyRegion
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Date
import android.widget.Toast
@Composable
fun JobPhotoPicker(
    selectedUris: List<Uri>,
    onUrisChange: (List<Uri>) -> Unit,
    maxPhotos: Int,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedUris.take(maxPhotos).forEachIndexed { index, uri ->
                Card(
                    modifier = Modifier.weight(1f, true),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate50)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (uri != null) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Photo $index",
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .background(Slate300)
                            )
                        }
                        IconButton(
                            onClick = { onUrisChange(selectedUris.filterIndexed { i, _ -> i != index }) },
                            modifier = Modifier.height(20.dp)
                        ) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "Remove",
                                tint = Red500
                            )
                        }
                    }
                }
            }
            if (selectedUris.size < maxPhotos) {
                FilledTonalButton(
                    onClick = { showPicker = true },
                    modifier = Modifier.height(80.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Photo")
                }
            }
        }

        if (selectedUris.size >= maxPhotos) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(maxPhotos) { idx ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .background(Slate300)
                    )
                }
            }
        }
    }

    if (showPicker) {
        PhotoPickerDialog(
            onDismissRequest = { showPicker = false },
            initialUriList = selectedUris,
            onUrisSelected = { newUris ->
                onUrisChange(newUris)
                showPicker = false
            },
            maxPhotos = maxPhotos
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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AsyncImage(
                model = uri,
                contentDescription = "Photo preview",
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.height(24.dp)
            ) {
                Icon(
                    Icons.Default.RemoveCircle,
                    contentDescription = "Remove photo",
                    tint = Red500
                )
            }
        }
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
        materialsP rovidedBy: String,
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
    var materialsProvidedBy by remember { mutableStateOf("RESIDENT") } // "RESIDENT" vs "PROVIDER" vs "NONE_NEEDED"
    var laborHoursText by remember { mutableStateOf("2.0") }
    var streetAddress by remember { mutableStateOf("") }
    var apartmentSuite by remember { mutableStateOf("") }
    var landmarkDirections by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf(0L) }
    var jobPhotoUris by remember { mutableStateOf(emptyList<Uri>()) }
    var laborRateText by remember { mutableStateOf("60.0") }
    var jobType by remember { mutableStateOf("ONE_TIME") }
    var recurringSchedule by remember { mutableStateOf("WEEKLY") }

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPickerDialog(
    onDismissRequest: () -> Unit,
    initialUriList: List<Uri>,
    onUrisSelected: (List<Uri>) -> Unit,
    maxPhotos: Int
) {
    var selectedList by remember { mutableStateOf(initialUriList) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Select Photos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(
                state = rememberLazyListState(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(selectedList.toList()) { uri ->
                    Card(
                        modifier = Modifier.height(80.dp).width(80.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected",
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedList.size < maxPhotos) {
                    OutlinedButton(
                        onClick = {
                            val taken = GalleryPickedPhotos.pickPhotos(
                                context = LocalContext.current,
                                max = maxPhotos - selectedList.size
                            )
                            selectedList = selectedList + taken
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("Add")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onUrisSelected(selectedList.toList())
                        onDismissRequest()
                    },
                    enabled = selectedList.isNotEmpty(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentsDatePicker(
    selectedDate: Long,
    onDateChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        val displayDate = if (selectedDate > 0) {
            LocalDate.ofInstant(Instant.ofEpochMilli(selectedDate), TimeZone.getTimeZone("UTC").toZoneId()).format(formatter)
        } else {
            "No date set"
        }

        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(displayDate)
            if (selectedDate <= 0) {
                Text("  (pick date)", color = Slate400, modifier = Modifier.padding(start = 4.dp))
            }
        }

        if (showPicker) {
            val today = LocalDate.now()
            val selectedDateObj = if (selectedDate > 0) {
                LocalDate.ofInstant(Instant.ofEpochMilli(selectedDate), TimeZone.getTimeZone("UTC").toZoneId())
            } else {
                today
            }
            val selectedTime = if (selectedDate > 0) {
                LocalTime.ofInstant(Instant.ofEpochMilli(selectedDate), TimeZone.getTimeZone("UTC").toZoneId())
            } else {
                LocalTime.of(9, 0)
            }

            DatePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val inst = Date.from(
                            LocalDateTime.of(selectedDateObj, selectedTime)
                                .atZone(ZoneId.of("UTC"))
                                .toInstant()
                        )
                        onDateChange(inst.time)
                        showPicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                DatePicker(selectedDate = selectedDateObj, selectedTime = selectedTime)
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

    Column(modifier = modifier.fillMaxWidth()) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val displayTime = if (selectedTime > 0) {
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
        var street by remember { mutableStateOf(streetAddress) }
        var apt by remember { mutableStateOf(apartmentSuite) }
        var landmark by remember { mutableStateOf(landmarkDirections) }

        OutlinedTextField(
            value = street,
            onValueChange = { street = it; onStreetAddressChange(it) },
            label = { Text("Street Address / Road") },
            placeholder = { Text("e.g. Main Street, near City Park") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            singleLine = false
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = apt,
            onValueChange = { apt = it; onApartmentSuiteChange(it) },
            label = { Text("Apt / Suite / Building") },
            placeholder = { Text("e.g. 2B, Green Apartments") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = landmark,
            onValueChange = { landmark = it; onLandmarkDirectionsChange(it) },
            label = { Text("Landmark & Directions") },
            placeholder = { Text("e.g. In front of St. Mary's Church, enter from side entrance") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            singleLine = false
        )
    }
}
            LocalTime.ofInstant(Instant.ofEpochMilli(selectedTime), TimeZone.getTimeZone("UTC").toZoneId()).format(formatter)
        } else {
            "No time set"
        }

        OutlinedButton(
            onClick = { showPicker = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(displayTime)
            if (selectedTime <= 0) {
                Text("  (pick time)", color = Slate400, modifier = Modifier.padding(start = 4.dp))
            }
        }

        if (showPicker) {
            val now = LocalDate.now().atTime(0, 0)
            val selectedTimeObj = if (selectedTime > 0) {
                LocalTime.ofInstant(Instant.ofEpochMilli(selectedTime), TimeZone.getTimeZone("UTC").toZoneId())
            } else {
                LocalTime.of(9, 0)
            }

            TimePickerDialog(
                onDismissRequest = { showPicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val inst = Date.from(
                            LocalDateTime.of(selectedTimeObj, now.toLocalTime())
                                .atZone(ZoneId.of("UTC"))
                                .toInstant()
                        )
                        onTimeChange(inst.time)
                        showPicker = false
                    }) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPicker = false }) {
                        Text("Cancel")
                    }
                }
            ) {
                TimePicker(selectedTime = selectedTimeObj)
            }
        }
    }
}
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

                    // ===== STREET ADDRESS =====
                    Text(
                        text = "Street Address",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 16.dp, top = 8.dp)
                    )
                    OutlinedTextField(
                        value = streetAddress,
                        onValueChange = { streetAddress = it },
                        placeholder = { Text("123 Main Street", fontSize = 13.sp, color = Slate400) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )

                    // ===== APARTMENT / UNIT =====
                    Text(
                        text = "Apartment / Unit (optional)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 16.dp, top = 8.dp)
                    )
                    OutlinedTextField(
                        value = apartmentSuite,
                        onValueChange = { apartmentSuite = it },
                        placeholder = { Text("Apt 4B", fontSize = 13.sp, color = Slate400) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )

                    // ===== LANDMARK DIRECTIONS =====
                    Text(
                        text = "Landmark Directions",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 16.dp, top = 8.dp)
                    )
                    OutlinedTextField(
                        value = landmarkDirections,
                        onValueChange = { landmarkDirections = it },
                        placeholder = { Text("Blue gate, near the big mango tree", fontSize = 13.sp, color = Slate400) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    )

                    // ===== SCHEDULED DATE / TIME =====
                    Text(
                        text = "Scheduled Date / Time (optional)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        modifier = Modifier.padding(horizontal = 16.dp, top = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Date picker button
                        OutlinedButton(
                            onClick = { /* DatePickerImplementation */ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate50)
                        ) {
                            Text(
                                text = scheduledAt.takeIf { it > 0L }?.let { currentTimeMillis ->
                                    java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        format(currentTimeMillis)
                                    }
                                } ?: "Select Date",
                                fontSize = 12.sp
                            )
                        }
                        // Time picker button
                        OutlinedButton(
                            onClick = { /* TimePickerImplementation */ },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Slate50)
                        ) {
                            Text(
                                text = scheduledAt.takeIf { it > 0L }?.let { currentTimeMillis ->
                                    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).apply {
                                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                                        format(currentTimeMillis)
                                    }
                                } ?: "Select Time",
                                fontSize = 12.sp
                            )
                        }
                    }

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

                    JobTypeSelector(
                        jobType = jobType,
                        recurringSchedule = recurringSchedule,
                        onJobTypeChange = { jobType = it },
                        onScheduleChange = { recurringSchedule = it },
                        modifier = Modifier.padding(top = 16.dp)
                    )
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
=== END OF FILE: QuickRequestDrawer ===
Implementation of job photo attachments, appointment scheduling, and address/landmark fields completed.
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
                                rate,
                                jobType,
                                if (jobType == "RECURRING") recurringSchedule else "WEEKLY",
                                streetAddress,
                                apartmentSuite,
                                landmarkDirections,
                                scheduledAt,
                                jobPhotoUris.map { it.toString() }
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
