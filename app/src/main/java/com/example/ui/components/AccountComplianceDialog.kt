package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.data.db.GuarantorEntity
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDeletionDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onConfirmDeleteAccount: () -> Unit
) {
    if (!isOpen) return

    var confirmationText by remember { mutableStateOf("") }
    val isConfirmed = confirmationText.trim().equals("DELETE", ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(RedDangerLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Delete Account",
                    tint = RedDanger,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Permanently Delete Account & Data",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "In compliance with App Store & Google Play privacy policies, account deletion will permanently purge:",
                    fontSize = 12.5.sp,
                    color = Slate600
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• Active job postings & quote bids", fontSize = 11.5.sp, color = Slate700)
                        Text("• National ID verification records & documents", fontSize = 11.5.sp, color = Slate700)
                        Text("• Mobile Money wallet links & transaction logs", fontSize = 11.5.sp, color = Slate700)
                        Text("• Local device cache and authentication tokens", fontSize = 11.5.sp, color = Slate700)
                    }
                }

                Text(
                    text = "Type 'DELETE' to confirm irreversible purge:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800
                )

                OutlinedTextField(
                    value = confirmationText,
                    onValueChange = { confirmationText = it },
                    placeholder = { Text("DELETE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmDeleteAccount()
                    onDismiss()
                },
                enabled = isConfirmed,
                colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Delete Everything", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndTermsSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Slate900.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Policy, contentDescription = null, tint = SignalBlue)
                Text(
                    text = "PRIVACY POLICY & TERMS OF SERVICE",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = Slate900
                )
            }

            HorizontalDivider(color = Slate200)

            LegalSection(
                title = "1. Data Collection & National ID Verification",
                body = "Skills Exchange collects phone numbers and optional National ID records strictly for fraud prevention, resident safety, and verification badges. Data is stored with encryption-at-rest and never sold to third parties."
            )

            LegalSection(
                title = "2. Mobile Money Escrow & Payments",
                body = "Payments held in Escrow via MTN MoMo and Airtel Money are locked until resident approval or mutual release. In case of dispute, our 24/7 neighborhood arbitration protocol freezes funds pending review."
            )

            LegalSection(
                title = "3. Hyperlocal Location Privacy",
                body = "Unverified accounts cannot view precise GPS coordinates or street numbers. Coarse district locations are shown to preserve client privacy until a quote is accepted."
            )

            LegalSection(
                title = "4. User Rights & Account Deletion",
                body = "You retain full ownership of your data. You may request full export in standard JSON format or complete account deletion anytime through the in-app settings."
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
            ) {
                Text("I Understand & Agree", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate900)
        Text(text = body, fontSize = 12.sp, color = Slate600, lineHeight = 17.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuarantorFormSheet(
    onDismiss: () -> Unit,
    onSubmit: (List<GuarantorEntity>) -> Unit,
    viewModel: SkillsExchangeViewModel
) {
    var currentStep by remember { mutableStateOf(1) }
    var g1 by remember { mutableStateOf(GuarantorEntry()) }
    var g2 by remember { mutableStateOf(GuarantorEntry()) }
    var activePhotoTarget by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        when (activePhotoTarget) {
            "g1_front" -> g1 = g1.copy(idFrontUri = uri)
            "g1_back" -> g1 = g1.copy(idBackUri = uri)
            "g2_front" -> g2 = g2.copy(idFrontUri = uri)
            "g2_back" -> g2 = g2.copy(idBackUri = uri)
        }
        activePhotoTarget = null
    }

    val current = if (currentStep == 1) g1 else g2
    val districts = listOf("Kampala Central", "Nakawa", "Makindye", "Kawempe", "Rubaga", "Wakiso", "Entebbe", "Mukono")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "GUARANTOR ${if (currentStep == 1) "1" else "2"} OF 2",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = SignalBlue
            )

            OutlinedTextField(
                value = current.fullName,
                onValueChange = { if (currentStep == 1) g1 = g1.copy(fullName = it) else g2 = g2.copy(fullName = it) },
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = SignalBlue, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = current.phoneNumber,
                onValueChange = { if (currentStep == 1) g1 = g1.copy(phoneNumber = it) else g2 = g2.copy(phoneNumber = it) },
                label = { Text("Phone Number") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SignalBlue, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            OutlinedTextField(
                value = current.relationship,
                onValueChange = { if (currentStep == 1) g1 = g1.copy(relationship = it) else g2 = g2.copy(relationship = it) },
                label = { Text("Relationship to Worker") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true
            )

            Text(text = "District", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700, modifier = Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(districts) { district ->
                    val isSelected = current.district == district
                    Surface(
                        color = if (isSelected) SignalBlue else Slate100,
                        contentColor = if (isSelected) Color.White else Slate700,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.clickable {
                            if (currentStep == 1) g1 = g1.copy(district = district) else g2 = g2.copy(district = district)
                        }
                    ) {
                        Text(
                            text = district,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhotoPickerButton(
                    label = "ID Front",
                    hasPhoto = current.idFrontUri != null,
                    onClick = {
                        activePhotoTarget = if (currentStep == 1) "g1_front" else "g2_front"
                        photoPicker.launch("image/*")
                    }
                )
                PhotoPickerButton(
                    label = "ID Back",
                    hasPhoto = current.idBackUri != null,
                    onClick = {
                        activePhotoTarget = if (currentStep == 1) "g1_back" else "g2_back"
                        photoPicker.launch("image/*")
                    }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentStep == 2) {
                    OutlinedButton(onClick = { currentStep = 1 }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                        Text("Back")
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (currentStep == 1) {
                            currentStep = 2
                        } else {
                            val workerId = viewModel.currentUser.value?.id ?: "user_default_me"
                            val entities = listOf(g1, g2).mapIndexed { index, entry ->
                                GuarantorEntity(
                                    id = "guarantor_${workerId}_${index + 1}",
                                    workerKycId = workerId,
                                    fullName = entry.fullName,
                                    phoneNumber = entry.phoneNumber,
                                    relationship = entry.relationship,
                                    district = entry.district,
                                    idFrontStoragePath = entry.idFrontUri?.toString() ?: "",
                                    idBackStoragePath = entry.idBackUri?.toString() ?: ""
                                )
                            }
                            viewModel.saveGuarantors(workerId, entities)
                            onSubmit(entities)
                        }
                    },
                    enabled = current.fullName.isNotBlank() && current.phoneNumber.isNotBlank() && current.relationship.isNotBlank() && current.district.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                ) {
                    Text(if (currentStep == 1) "Next" else "Submit Application")
                }
            }
        }
    }
}

@Composable
private fun PhotoPickerButton(label: String, hasPhoto: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (hasPhoto) IndustrialEmeraldLight else Slate50,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (hasPhoto) IndustrialEmerald else Slate300),
        modifier = Modifier.weight(1f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = CameraAlt,
                contentDescription = label,
                tint = if (hasPhoto) IndustrialEmerald else Slate500,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (hasPhoto) "$label ✓" else label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasPhoto) IndustrialEmeraldDark else Slate700
            )
        }
    }
}

private data class GuarantorEntry(
    val fullName: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    val district: String = "",
    val idFrontUri: Uri? = null,
    val idBackUri: Uri? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualificationEvidenceSheet(
    onDismiss: () -> Unit,
    onSubmit: (String, String?) -> Unit,
    viewModel: SkillsExchangeViewModel
) {
    var evidenceType by remember { mutableStateOf("CERTIFICATE") }
    var videoUrl by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        selectedImageUri = uri
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SKILL QUALIFICATION EVIDENCE",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                color = SignalBlue
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { evidenceType = "CERTIFICATE" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (evidenceType == "CERTIFICATE") SignalBlue else Slate100,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Certificate / Photo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (evidenceType == "CERTIFICATE") Color.White else Slate700,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
                Surface(
                    onClick = { evidenceType = "VIDEO" },
                    shape = RoundedCornerShape(8.dp),
                    color = if (evidenceType == "VIDEO") SignalBlue else Slate100,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Demo Video Link",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (evidenceType == "VIDEO") Color.White else Slate700,
                        modifier = Modifier.padding(vertical = 10.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (evidenceType == "CERTIFICATE") {
                Surface(
                    onClick = { photoPicker.launch("image/*") },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedImageUri != null) IndustrialEmeraldLight else Slate50,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedImageUri != null) IndustrialEmerald else Slate300),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = CameraAlt,
                            contentDescription = "Upload",
                            tint = if (selectedImageUri != null) IndustrialEmerald else Slate500,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedImageUri != null) "Certificate Selected" else "Tap to Upload Certificate / Photo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedImageUri != null) IndustrialEmeraldDark else Slate700
                        )
                    }
                }
            } else {
                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("Video Demonstration Link") },
                    placeholder = { Text("https://youtube.com/...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp), enabled = !isUploading) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        isUploading = true
                        viewModel.uploadQualificationEvidence(
                            evidenceType = evidenceType,
                            videoUrl = if (evidenceType == "VIDEO") videoUrl else null,
                            onResult = { path ->
                                isUploading = false
                                if (path != null) {
                                    onSubmit(evidenceType, path)
                                }
                            }
                        )
                    },
                    enabled = !isUploading && (evidenceType == "VIDEO" && videoUrl.isNotBlank() || evidenceType == "CERTIFICATE" && selectedImageUri != null),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                ) {
                    Text(if (isUploading) "Uploading..." else "Submit Evidence")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesRequiredView(
    feedbackReason: String,
    onUpdateDocuments: () -> Unit,
    onSwitchToClientMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = AmberUrgentLight,
            border = androidx.compose.foundation.BorderStroke(2.dp, AmberUrgent),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Changes Required",
                    tint = AmberUrgent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            text = "CHANGES REQUIRED",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = AmberUrgent,
            letterSpacing = 0.5.sp
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Slate50,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Admin Feedback:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Slate500,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = feedbackReason,
                    fontSize = 13.sp,
                    color = Slate800,
                    lineHeight = 18.sp
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFFBEB),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Need Help?",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE65100),
                    letterSpacing = 0.5.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(14.dp))
                    Text("+256 700 000 000", fontSize = 12.sp, color = Slate800, fontWeight = FontWeight.Medium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(14.dp))
                    Text("WhatsApp Support Available", fontSize = 12.sp, color = Slate800, fontWeight = FontWeight.Medium)
                }
            }
        }

        Button(
            onClick = onUpdateDocuments,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
        ) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Update Documents", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        OutlinedButton(
            onClick = onSwitchToClientMode,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("Switch to Client Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
