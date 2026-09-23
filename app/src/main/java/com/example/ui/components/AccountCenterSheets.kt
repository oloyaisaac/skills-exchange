package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.example.data.db.DisputeEntity
import com.example.data.db.JobPostEntity
import com.example.data.db.UserProfileEntity
import com.example.data.db.WorkerKycEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.util.CurrencyRegion

/**
 * AUDIT FIXES (H3/H4/H7/M1): Real destinations for the "Account & Services" drawer items.
 *
 * These sheets replace the previous behaviour where the drawer items either displayed a
 * fabricated figure ("Available Escrow: UGX 240") or silently redirected to the Jobs tab.
 * Every value shown here is derived from the actual Room records passed in by MainScreen.
 */

/** Escrow statuses meaning funds are currently held by the platform and not yet paid out. */
internal val ESCROW_HELD_STATUSES = setOf(
    "FUNDS_LOCKED_IN_ESCROW",
    "WORK_IN_PROGRESS",
    "PROOF_SUBMITTED"
)

/** Sum of every job whose escrow is currently held. Returns 0.0 when nothing is held. */
fun escrowHeldTotal(jobs: List<JobPostEntity>): Double =
    jobs.filter { it.escrowStatus in ESCROW_HELD_STATUSES }.sumOf { it.grandTotal }

@Composable
private fun SheetHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun SheetFooterButton(onDismiss: () -> Unit) {
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
private fun SheetInfoBox(message: String) {
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
private fun SheetMoneyRow(title: String, subtitle: String, amount: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate50,
        border = BorderStroke(1.dp, Slate200)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                Text(subtitle, fontSize = 10.sp, color = Slate500)
            }
            Text(amount, fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = IndustrialEmerald)
        }
    }
}

/** AUDIT FIX (A3/H4): MoMo & Escrow wallet — real escrow totals, no fabricated balance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscrowWalletSheet(
    isOpen: Boolean,
    jobs: List<JobPostEntity>,
    userProfile: UserProfileEntity?,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val heldJobs = jobs.filter { it.escrowStatus in ESCROW_HELD_STATUSES }
    val releasedJobs = jobs.filter { it.escrowStatus == "RELEASED_TO_WORKER" }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(Icons.Outlined.Shield, "MOMO & ESCROW WALLET")

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = SignalBlueLight,
                border = BorderStroke(1.dp, SignalBlue)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "HELD IN ESCROW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = CurrencyRegion.format(escrowHeldTotal(jobs)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Slate900
                    )
                    Text(
                        text = "${heldJobs.size} contract(s) currently funded",
                        fontSize = 11.sp,
                        color = Slate600
                    )
                }
            }

            if (heldJobs.isEmpty() && releasedJobs.isEmpty()) {
                SheetInfoBox(
                    "No escrow activity yet. Escrow begins once you accept a quote as a client, " +
                        "or once a client funds a job you are hired for as a worker."
                )
            } else {
                if (heldJobs.isNotEmpty()) {
                    SheetSectionLabel("FUNDS CURRENTLY LOCKED")
                    heldJobs.forEach { job ->
                        SheetMoneyRow(
                            title = job.title,
                            subtitle = job.escrowStatus.replace('_', ' '),
                            amount = CurrencyRegion.format(job.grandTotal)
                        )
                    }
                }
                if (releasedJobs.isNotEmpty()) {
                    SheetSectionLabel("RELEASED")
                    releasedJobs.forEach { job ->
                        SheetMoneyRow(
                            title = job.title,
                            subtitle = "Paid out • ${job.paymentChannel.replace('_', ' ')}",
                            amount = CurrencyRegion.format(job.grandTotal)
                        )
                    }
                }
            }

            userProfile?.let { profile ->
                SheetInfoBox(
                    "Settlement is processed through your registered Mobile Money number " +
                        (profile.phoneNumber.ifBlank { "on file" }) +
                        " after the client releases escrow."
                )
            }
            SheetFooterButton(onDismiss)
        }
    }
}

/** AUDIT FIX (M1): Job History & Material Receipts — real completed/released contracts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobHistorySheet(
    isOpen: Boolean,
    jobs: List<JobPostEntity>,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val historyJobs = jobs.filter {
        it.status in setOf("COMPLETED", "RELEASED") || it.escrowStatus == "RELEASED_TO_WORKER"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(Icons.Outlined.ReceiptLong, "JOB HISTORY & RECEIPTS")

            if (historyJobs.isEmpty()) {
                SheetInfoBox(
                    "No completed contracts yet. Finished and paid-out jobs will appear here " +
                        "with their material receipts and escrow release record."
                )
            } else {
                historyJobs.forEach { job ->
                    SheetMoneyRow(
                        title = job.title,
                        subtitle = "${job.status.replace('_', ' ')} • ${job.district}",
                        amount = CurrencyRegion.format(job.grandTotal)
                    )
                    if (job.materialReceiptsCsv.isNotBlank()) {
                        SheetInfoBox(
                            "Material receipts on file: " +
                                job.materialReceiptsCsv.split(',').count { it.isNotBlank() } +
                                " attachment(s) • subtotal " +
                                CurrencyRegion.format(job.materialsSubtotal)
                        )
                    }
                }
            }

            SheetFooterButton(onDismiss)
        }
    }
}

/** AUDIT FIX (H7): Trust, Safety & Emergency SOS — real disputes plus the SOS flag. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustSafetySheet(
    isOpen: Boolean,
    jobs: List<JobPostEntity>,
    disputes: List<DisputeEntity>,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val sosJobs = jobs.filter { it.isSosTriggered }
    val openDisputes = disputes.filter { it.status.uppercase() != "RESOLVED" }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(Icons.Outlined.Shield, "TRUST, SAFETY & EMERGENCY SOS")

            if (sosJobs.isEmpty()) {
                SheetInfoBox(
                    "No emergency flags are active on your contracts. The SOS trigger lives on each " +
                        "active contract's escrow sheet so it can share live job details with support."
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RedDangerLight,
                    border = BorderStroke(1.dp, RedDanger)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = RedDanger)
                            Text(
                                "EMERGENCY FLAG ACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RedDanger
                            )
                        }
                        sosJobs.forEach { job ->
                            Text(
                                text = "• ${job.title} — open this contract's escrow sheet to manage or stand down.",
                                fontSize = 11.sp,
                                color = Slate700,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            SheetSectionLabel("DISPUTES")

            if (disputes.isEmpty()) {
                SheetInfoBox("You have no disputes on record.")
            } else {
                disputes.forEach { dispute ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Slate50,
                        border = BorderStroke(1.dp, Slate200)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dispute.disputeReason.replace('_', ' '),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = dispute.status.uppercase(),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (dispute.status.uppercase() == "RESOLVED") IndustrialEmerald else AmberUrgent
                                )
                            }
                            if (dispute.description.isNotBlank()) {
                                Text(dispute.description, fontSize = 10.5.sp, color = Slate600, lineHeight = 14.sp)
                            }
                            if (dispute.resolutionType.isNotBlank()) {
                                val amountText = if (dispute.resolutionAmount > 0) {
                                    " • " + CurrencyRegion.format(dispute.resolutionAmount)
                                } else {
                                    ""
                                }
                                Text(
                                    text = "Resolution: ${dispute.resolutionType.replace('_', ' ')}" + amountText,
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                }
            }

            if (openDisputes.isNotEmpty()) {
                SheetInfoBox(
                    "${openDisputes.size} dispute(s) still under arbitration. Submit or review " +
                        "evidence from the relevant contract's escrow sheet."
                )
            }

            SheetFooterButton(onDismiss)
        }
    }
}

/** AUDIT FIX (H3): National ID & Verification — real KYC state, no unconditional "Verified". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityVerificationSheet(
    isOpen: Boolean,
    userProfile: UserProfileEntity?,
    workerKyc: WorkerKycEntity?,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val phoneOnFile = userProfile?.phoneNumber.orEmpty()
    val isPhoneLinked = phoneOnFile.isNotBlank()
    val idStatus = workerKyc?.status ?: "NOT_STARTED"
    val isWorkerMode = userProfile?.initialRole == "WORKER" ||
        userProfile?.currentActiveMode == "WORKER"

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(Icons.Outlined.VerifiedUser, "NATIONAL ID & VERIFICATION")

            VerificationStatusRow(
                label = "Phone number",
                value = if (isPhoneLinked) phoneOnFile else "Not linked",
                isPositive = isPhoneLinked
            )

            VerificationStatusRow(
                label = "Account mode",
                value = (userProfile?.currentActiveMode ?: "CLIENT").let {
                    if (it == "WORKER") "Worker" else "Client"
                },
                isPositive = true
            )

            VerificationStatusRow(
                label = "National ID (KYC)",
                value = idStatus.replace('_', ' '),
                isPositive = idStatus == "APPROVED"
            )

            VerificationStatusRow(
                label = "Worker activation",
                value = if (userProfile?.isWorkerActivated == true) "Activated" else "Inactive",
                isPositive = userProfile?.isWorkerActivated == true
            )

            when {
                !isPhoneLinked -> SheetInfoBox(
                    "Link a phone number to unlock contact details for clients and workers."
                )
                idStatus == "APPROVED" -> SheetInfoBox(
                    "Your National ID verification has been approved by an administrator."
                )
                isWorkerMode -> SheetInfoBox(
                    "National ID verification status: ${idStatus.replace('_', ' ')}. " +
                        "Submit or update your KYC documents from your worker verification screen. " +
                        "Worker tools stay locked until an administrator approves them."
                )
                else -> SheetInfoBox(
                    "National ID verification is only required when you offer skills as a worker. " +
                        "Switch to worker mode to begin verification."
                )
            }

            SheetFooterButton(onDismiss)
        }
    }
}

@Composable
private fun VerificationStatusRow(label: String, value: String, isPositive: Boolean) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isPositive) IndustrialEmeraldLight else AmberUrgentLight,
        border = BorderStroke(1.dp, if (isPositive) IndustrialEmerald else AmberUrgent)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
            Text(
                text = value,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPositive) IndustrialEmeraldDark else AmberUrgent
            )
        }
    }
}

@Composable
private fun SheetSectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Slate500,
        letterSpacing = 0.8.sp
    )
}

/**
 * REG-003 / REG-004: Profile photo + backup contact management.
 *
 * The worker reviews current saved values, corrects them, and submits explicitly.
 * Photo upload goes to Firebase Storage `avatars/{uid}/` (owner-only write) and only
 * the returned URL is stored — no image bytes in Room or Firestore. A missing or
 * failed-to-load image falls back to the initials rendered underneath the photo layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsSheet(
    isOpen: Boolean,
    userProfile: UserProfileEntity?,
    viewModel: SkillsExchangeViewModel,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val pendingPhotoUri by viewModel.pendingProfilePhotoUri.collectAsStateWithLifecycle()
    val isUploadingPhoto by viewModel.isUploadingProfilePhoto.collectAsStateWithLifecycle()
    val photoError by viewModel.profilePhotoError.collectAsStateWithLifecycle()
    val backupError by viewModel.backupContactError.collectAsStateWithLifecycle()
    val successMessage by viewModel.profileSettingsSuccess.collectAsStateWithLifecycle()

    // Prefilled so the worker can review and correct details before submitting.
    var backupName by remember(userProfile?.id, isOpen) {
        mutableStateOf(userProfile?.backupContactName ?: "")
    }
    var backupPhone by remember(userProfile?.id, isOpen) {
        mutableStateOf(userProfile?.backupContactPhone ?: "")
    }

    LaunchedEffect(isOpen) { if (isOpen) viewModel.clearProfileSettingsMessages() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        viewModel.selectPendingProfilePhoto(uri)
    }

    ModalBottomSheet(
        onDismissRequest = {
            viewModel.clearProfileSettingsMessages()
            onDismiss()
        },
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(Icons.Outlined.ManageAccounts, "PROFILE PHOTO & BACKUP CONTACT")

            // --- REG-004: Profile photo ---
            SheetSectionLabel("PROFILE PHOTO")

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(Slate100),
                    contentAlignment = Alignment.Center
                ) {
                    // Safe fallback for a missing/invalid image: the worker's initials.
                    Text(
                        text = extractInitials(userProfile?.fullName),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate600
                    )
                    // Pending selection previews over the saved URL; a failed load renders
                    // nothing so the initials underneath always remain visible.
                    val imageModel: Any? =
                        pendingPhotoUri ?: userProfile?.avatarUrl?.takeIf { it.isNotBlank() }
                    if (imageModel != null) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageModel),
                            contentDescription = "Profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { photoPicker.launch("image/*") },
                        enabled = !isUploadingPhoto,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (userProfile?.avatarUrl.isNullOrBlank()) {
                                "Choose photo"
                            } else {
                                "Replace photo"
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (pendingPhotoUri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { viewModel.uploadPendingProfilePhoto() },
                                enabled = !isUploadingPhoto,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Upload", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.clearPendingProfilePhoto() },
                                enabled = !isUploadingPhoto,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Cancel", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (isUploadingPhoto) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Uploading photo…", fontSize = 11.sp, color = Slate600)
                        }
                    }
                }
            }

            photoError?.let {
                Text(it, fontSize = 11.5.sp, color = RedDanger)
            }

            SheetInfoBox(
                "Clients see this photo when viewing your profile. National ID images, " +
                    "guarantor photos, and other verification documents are never shown here."
            )

            // --- REG-003: Backup contact ---
            SheetSectionLabel("BACKUP CONTACT")
            SheetInfoBox(
                "An alternative contact number in case your primary number is unreachable. " +
                    "Leave both fields empty to keep it unset."
            )

            OutlinedTextField(
                value = backupName,
                onValueChange = {
                    backupName = it
                    viewModel.clearProfileSettingsMessages()
                },
                label = { Text("Backup contact name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = backupPhone,
                onValueChange = {
                    if (it.length <= 15) {
                        backupPhone = it
                        viewModel.clearProfileSettingsMessages()
                    }
                },
                label = { Text("Backup contact phone number") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = backupError != null,
                supportingText = {
                    backupError?.let { message ->
                        Text(message, fontSize = 11.sp, color = RedDanger)
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { viewModel.saveBackupContact(backupName, backupPhone) },
                enabled = !isUploadingPhoto,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
            ) {
                Text("Save backup contact", fontWeight = FontWeight.Bold)
            }

            successMessage?.let {
                Text(
                    text = it,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IndustrialEmeraldDark
                )
            }

            SheetFooterButton(onDismiss = {
                viewModel.clearProfileSettingsMessages()
                onDismiss()
            })
        }
    }
}