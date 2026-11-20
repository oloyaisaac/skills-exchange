package com.example.ui.components

import com.example.util.SecureScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.filled.Shield
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
import com.example.ui.viewmodel.DataExportState
import com.example.ui.viewmodel.PrivacyConsentState
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.data.remote.AccountDeletionRules
import com.example.data.remote.DeletionStep
import androidx.compose.ui.platform.LocalContext
import com.example.data.db.GuarantorEntity
import com.example.ui.theme.*

/**
 * R06-P2-01 / R06-P2-02 / R06-P2-05: the account-deletion dialog.
 *
 * It is driven by the deletion [step] rather than closing itself on tap. The old version called the
 * ViewModel and dismissed in the same frame, so a failed purge looked like success and a stale
 * session left the person with no way forward. Now the dialog:
 *
 *  * names Cloud Storage photos as part of what is purged (they were never deleted before);
 *  * stays open while the server purges, and cannot be dismissed mid-purge;
 *  * offers a real in-app re-authentication step (Google re-consent or an SMS code) instead of
 *    "sign out and sign in again";
 *  * reports success only when the purge *and* the sign-in removal both succeeded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDeletionDialog(
    isOpen: Boolean,
    step: DeletionStep = DeletionStep.CONFIRM,
    message: String? = null,
    codeSent: Boolean = false,
    accountPhone: String? = null,
    onDismiss: () -> Unit = {},
    onConfirmDeleteAccount: () -> Unit = {},
    onReauthWithGoogle: (idToken: String) -> Unit = {},
    onStartPhoneReauth: (phone: String) -> Unit = {},
    onConfirmPhoneReauth: (code: String) -> Unit = {},
    onReset: () -> Unit = {}
) {
    // R02-P2-06: account/identity surface — block screenshots and recents preview.
    SecureScreen()
    if (!isOpen) return

    var confirmationText by remember { mutableStateOf("") }
    var phoneNumber by remember(accountPhone) { mutableStateOf(accountPhone.orEmpty()) }
    var otpCode by remember { mutableStateOf("") }
    var googleUnavailable by remember { mutableStateOf<String?>(null) }
    val isConfirmed = confirmationText.trim().equals("DELETE", ignoreCase = true)
    val context = LocalContext.current

    // R06-P2-05: re-authentication with Google happens inside this dialog, so the person never has
    // to leave the deletion flow (and lose what they were doing) to refresh the 5-minute window.
    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            com.google.android.gms.auth.api.signin.GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { account ->
                    val token = account.idToken
                    if (token.isNullOrBlank()) {
                        googleUnavailable = "Google did not return a verifiable token. Use the SMS code instead."
                    } else {
                        googleUnavailable = null
                        onReauthWithGoogle(token)
                    }
                }
                .addOnFailureListener {
                    googleUnavailable = "Google verification was cancelled or failed. Use the SMS code instead."
                }
        }
    }

    val busy = step == DeletionStep.PURGING

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
                text = when (step) {
                    DeletionStep.REAUTH -> "Verify It Is You"
                    DeletionStep.DONE -> "Account Deleted"
                    else -> "Permanently Delete Account & Data"
                },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step == DeletionStep.CONFIRM) {
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
                            // R06-P2-02: Storage objects are now actually removed, and named.
                            Text("• Uploaded photos: ID documents, certificates, guarantor photos, avatars & job photos", fontSize = 11.5.sp, color = Slate700)
                            Text("• Mobile Money wallet links & transaction logs", fontSize = 11.5.sp, color = Slate700)
                            Text("• Local device cache, push token and authentication tokens", fontSize = 11.5.sp, color = Slate700)
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

                if (step == DeletionStep.REAUTH) {
                    Text(
                        text = "Deleting an account needs a sign-in from the last five minutes. " +
                            "Verify below to continue — you will stay on this screen.",
                        fontSize = 12.5.sp,
                        color = Slate600
                    )
                    Button(
                        onClick = {
                            val client = com.example.util.GoogleSignInHelper.getClient(context)
                            if (client == null) {
                                googleUnavailable = com.example.util.GoogleSignInHelper.NOT_CONFIGURED_MESSAGE
                            } else {
                                googleUnavailable = null
                                googleLauncher.launch(client.signInIntent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900, contentColor = Color.White)
                    ) {
                        Text("Verify with Google", fontWeight = FontWeight.Bold)
                    }
                    if (googleUnavailable != null) {
                        Text(text = googleUnavailable!!, fontSize = 11.sp, color = RedDanger)
                    }

                    Text("Or verify with a code sent to your phone:", fontSize = 12.sp, color = Slate800)
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        placeholder = { Text("+256 7xx xxx xxx") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                    OutlinedButton(
                        onClick = { onStartPhoneReauth(phoneNumber) },
                        enabled = phoneNumber.trim().length >= 10,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (codeSent) "Resend Code" else "Send Code")
                    }
                    if (codeSent) {
                        OutlinedTextField(
                            value = otpCode,
                            onValueChange = { otpCode = it.filter { ch -> ch.isDigit() }.take(6) },
                            placeholder = { Text("6-digit code") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = { onConfirmPhoneReauth(otpCode) },
                            enabled = otpCode.length == 6,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Verify Code", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (step == DeletionStep.PURGING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = message ?: AccountDeletionRules.progressLabel(DeletionStep.PURGING),
                            fontSize = 12.5.sp,
                            color = Slate700
                        )
                    }
                    Text(
                        text = "Keep the app open until this finishes.",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }

                if (step == DeletionStep.DONE) {
                    Text(
                        text = message ?: AccountDeletionRules.progressLabel(DeletionStep.DONE),
                        fontSize = 12.5.sp,
                        color = Slate700
                    )
                }

                if (step == DeletionStep.FAILED) {
                    Text(
                        text = message ?: AccountDeletionRules.progressLabel(DeletionStep.FAILED),
                        fontSize = 12.5.sp,
                        color = RedDanger
                    )
                    Text(
                        text = "Your account was kept. Nothing was lost.",
                        fontSize = 11.sp,
                        color = Slate500
                    )
                }
            }
        },
        confirmButton = {
            when (step) {
                DeletionStep.DONE -> Button(
                    onClick = { onReset(); onDismiss() },
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Done", fontWeight = FontWeight.Bold) }

                DeletionStep.FAILED -> Button(
                    onClick = { onReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Try Again", fontWeight = FontWeight.Bold) }

                DeletionStep.REAUTH -> TextButton(onClick = { if (!busy) onDismiss() }) { Text("Cancel") }

                DeletionStep.PURGING -> Button(
                    onClick = {},
                    enabled = false,
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Deleting…", fontWeight = FontWeight.Bold) }

                DeletionStep.CONFIRM -> Button(
                    onClick = { onConfirmDeleteAccount() },
                    enabled = isConfirmed,
                    colors = ButtonDefaults.buttonColors(containerColor = RedDanger),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Delete Everything", fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = if (step == DeletionStep.CONFIRM) {
            {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Cancel") }
            }
        } else {
            null
        }
    )
}

/**
 * R09-P2-02 / R09-P2-03 / R09-P2-05: the privacy sheet, with an honest button.
 *
 * The sheet used to end in "I Understand & Agree" calling `onDismiss` — nothing was recorded, so the
 * app's claim of consent rested on a tap that left no trace. It now records the acceptance on the
 * server (version + timestamp + the three grants) and will not report agreement until the receipt
 * exists. It also hosts the two rights the policy promises and the app never offered: a copy of your
 * data, and withdrawal of consent.
 *
 * The copy is corrected too: the account records are access-controlled, not "encryption-at-rest"
 * marketing, and the deletion section states the legal holds rather than implying everything goes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyAndTermsSheet(
    isOpen: Boolean,
    consentState: PrivacyConsentState,
    consentMessage: String?,
    exportState: DataExportState,
    exportMessage: String?,
    policyVersion: String,
    onAccept: () -> Unit,
    onWithdraw: () -> Unit,
    onExportData: () -> Unit,
    onDismiss: () -> Unit
) {
    // R02-P2-06: account/identity surface — block screenshots and recents preview.
    SecureScreen()
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
                body = "Skills Exchange collects phone numbers and the National ID records needed to " +
                    "verify workers, strictly for fraud prevention, resident safety and verification " +
                    "badges. These records live in access-controlled account documents that only you " +
                    "and authorised verification reviewers can open on the server; they are not " +
                    "end-to-end encrypted, and they are never sold. Your National ID number is never " +
                    "shown to clients and is masked wherever it is displayed."
            )

            LegalSection(
                title = "2. Mobile Money Escrow & Payments",
                body = "Payments held in Escrow via MTN MoMo are locked until resident approval or " +
                    "mutual release. In case of dispute, our 24/7 neighborhood arbitration protocol " +
                    "freezes funds pending review."
            )

            LegalSection(
                title = "3. Hyperlocal Location Privacy",
                body = "Unverified accounts cannot view precise GPS coordinates or street numbers. " +
                    "Coarse district locations are shown to preserve client privacy until a quote is " +
                    "accepted."
            )

            LegalSection(
                title = "4. Your Rights, Export & Account Deletion",
                body = "You retain ownership of your data. You can download a JSON copy of everything " +
                    "we hold about you, and you can delete your account, from this screen at any " +
                    "time. Deletion removes your records, your photos and your messages. Payment, " +
                    "identity-verification and consent records are kept for as long as the law " +
                    "requires — up to seven years — and are then deleted; when your deletion " +
                    "completes we tell you exactly which categories were kept and why."
            )

            LegalSection(
                title = "5. Consent You Are Giving Here",
                body = "Accepting below records your agreement to the Terms, this Privacy Policy and " +
                    "the processing described above, on our servers, against policy version " +
                    "$policyVersion, with the time you accepted. A copy stays readable to you. You " +
                    "can withdraw it at any time; the record of what you agreed, and when you " +
                    "withdrew, is kept so the agreement stays demonstrable."
            )

            if (consentState == PrivacyConsentState.RECORDED) {
                Text(
                    text = "Agreement recorded.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SignalBlue
                )
            }
            if (consentMessage != null) {
                Text(
                    text = consentMessage,
                    fontSize = 12.sp,
                    color = if (consentState == PrivacyConsentState.FAILED) RedDanger else Slate600
                )
            }

            Button(
                onClick = if (consentState == PrivacyConsentState.RECORDED) onDismiss else onAccept,
                enabled = consentState != PrivacyConsentState.RECORDING,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
            ) {
                Text(
                    text = when (consentState) {
                        PrivacyConsentState.RECORDED -> "Done"
                        PrivacyConsentState.RECORDING -> "Recording…"
                        PrivacyConsentState.FAILED -> "Try Again"
                        PrivacyConsentState.IDLE -> "I Understand & Agree"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(color = Slate200)

            OutlinedButton(
                onClick = onExportData,
                enabled = exportState != DataExportState.WORKING,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = when (exportState) {
                        DataExportState.WORKING -> "Preparing your export…"
                        DataExportState.DONE -> "Export again"
                        else -> "Download my data (JSON)"
                    }
                )
            }
            exportMessage?.let { message ->
                Text(
                    text = message,
                    fontSize = 12.sp,
                    color = if (exportState == DataExportState.FAILED) RedDanger else Slate600
                )
            }

            TextButton(onClick = onWithdraw, modifier = Modifier.fillMaxWidth()) {
                Text("Withdraw my consent", fontSize = 12.sp, color = Slate600)
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
                            // C2-2 Fix #3 (F7): guarantor rows are filed under the AUTHENTICATED
                            // Firebase UID and nothing else — never the newest Room row. Fail closed
                            // when no authenticated identity exists (orphaned A7 dialog path); the
                            // demo placeholder is rejected by the resolver itself.
                            val workerId = viewModel.currentAuthenticatedUid() ?: return@Button
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
private fun RowScope.PhotoPickerButton(label: String, hasPhoto: Boolean, onClick: () -> Unit) {
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
                imageVector = Icons.Outlined.CameraAlt,
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
                            imageVector = Icons.Outlined.CameraAlt,
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

data class TrainingModule(
    val id: String,
    val title: String,
    val description: String,
    val videoUrl: String,
    val durationMinutes: Int
)

@Composable
fun WorkerTrainingScreen(
    training: com.example.data.db.WorkerTrainingEntity?,
    onStartTraining: () -> Unit,
    onCompleteModule: (String, Int) -> Unit,
    onCompleteTraining: () -> Unit,
    onSwitchToClientMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modules = remember {
        listOf(
            TrainingModule("platform_rules", "Platform Rules", "Learn community guidelines, trust standards, and worker conduct expectations.", "https://training.skillsexchange.app/videos/platform_rules", 45),
            TrainingModule("escrow_guidelines", "Escrow & Payment Guidelines", "Understand escrow locking, fund release conditions, and dispute resolution.", "https://training.skillsexchange.app/videos/escrow", 30),
            TrainingModule("customer_etiquette", "Customer Etiquette & Safety", "Best practices for professional conduct, doorstep safety, and emergency protocols.", "https://training.skillsexchange.app/videos/etiquette", 25)
        )
    }

    val status = training?.status ?: "NOT_STARTED"
    val completedModules = training?.completedModulesCsv?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    val allModulesCompleted = completedModules.size >= modules.size

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = SignalBlueLight,
            border = androidx.compose.foundation.BorderStroke(2.dp, SignalBlue),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Default.Policy,
                    contentDescription = "Training",
                    tint = SignalBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Text(
            text = "WORKER TRAINING PROGRAM",
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = SignalBlue,
            letterSpacing = 0.5.sp
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Slate50,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate200),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "2-3 Day Rule Training",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate800
                )
                Text(
                    text = "Complete all modules to activate your worker account and start receiving job bids. Training covers platform rules, escrow guidelines, and customer safety.",
                    fontSize = 11.sp,
                    color = Slate600,
                    lineHeight = 16.sp
                )
            }
        }

        when (status) {
            "COMPLETED" -> {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8F5E9),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
                        Column {
                            Text("Training Completed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("Your account is eligible for admin activation.", fontSize = 11.sp, color = Color(0xFF388E3C))
                        }
                    }
                }
            }
            "IN_PROGRESS" -> {
                Text(
                    text = "Training Progress",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                )

                modules.forEachIndexed { index, module ->
                    val isCompleted = completedModules.contains(module.id)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isCompleted) Color(0xFFE8F5E9) else Slate50,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isCompleted) Color(0xFF4CAF50) else Slate200),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(
                                        if (isCompleted) Color(0xFF4CAF50) else Slate200,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                } else {
                                    Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate600)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(module.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                                Text(module.description, fontSize = 10.sp, color = Slate500, lineHeight = 14.sp)
                                Text("${module.durationMinutes} min", fontSize = 9.sp, color = Slate400, fontWeight = FontWeight.Medium)
                            }
                            if (!isCompleted) {
                                OutlinedButton(
                                    onClick = { onCompleteModule(module.id, index + 1) },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Complete", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }

                if (allModulesCompleted) {
                    Button(
                        onClick = onCompleteTraining,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("Finish Training & Activate", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            else -> {
                Button(
                    onClick = onStartTraining,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                ) {
                    Text("Start Training Program", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
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
