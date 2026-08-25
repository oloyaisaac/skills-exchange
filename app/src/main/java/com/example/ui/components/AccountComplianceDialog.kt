package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
