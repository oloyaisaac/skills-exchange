package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.UserProfileEntity
import com.example.ui.theme.*

/**
 * ROLE-003, KYC-006 & TRAIN-003: Worker Verification & Activation Gate Screen
 * Restricts access to bidding and the Freelancer Dashboard until National ID verification,
 * 2-guarantor checks, and 2-3 day training modules are verified by administrators.
 */
@Composable
fun WorkerVerificationStatusView(
    userProfile: UserProfileEntity?,
    onStartVerification: () -> Unit,
    onStartTraining: () -> Unit,
    onSwitchToClientMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val kycStatus = userProfile?.workerKycStatus ?: "UNVERIFIED"
    val isTrainingDone = userProfile?.trainingCompleted ?: false
    val isActivated = userProfile?.isWorkerActivated ?: false

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate50)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Header Badge
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    when (kycStatus) {
                        "APPROVED" -> Emerald100
                        "PENDING_REVIEW" -> Amber100
                        "CHANGES_REQUIRED" -> Amber100
                        "REJECTED", "SUSPENDED" -> RedDangerLight
                        else -> Slate200
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (kycStatus) {
                    "APPROVED" -> Icons.Default.CheckCircle
                    "PENDING_REVIEW" -> Icons.Default.HourglassTop
                    "CHANGES_REQUIRED" -> Icons.Default.EditNote
                    "REJECTED", "SUSPENDED" -> Icons.Default.Block
                    else -> Icons.Default.Lock
                },
                contentDescription = null,
                tint = when (kycStatus) {
                    "APPROVED" -> Emerald600
                    "PENDING_REVIEW" -> Amber700
                    "CHANGES_REQUIRED" -> Amber700
                    "REJECTED", "SUSPENDED" -> RedDanger
                    else -> Slate700
                },
                modifier = Modifier.size(36.dp)
            )
        }

        Text(
            text = when (kycStatus) {
                "APPROVED" -> "Worker Profile Verified"
                "PENDING_REVIEW" -> "Verification In Progress"
                "CHANGES_REQUIRED" -> "Changes Required on Documents"
                "REJECTED" -> "Verification Not Approved"
                "SUSPENDED" -> "Worker Account Suspended"
                else -> "Worker Verification Required"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Slate900,
            textAlign = TextAlign.Center
        )

        Text(
            text = when (kycStatus) {
                "APPROVED" -> if (!isTrainingDone) "Your identity is approved. Complete worker rule training to activate job bidding." else "Your account is activated and eligible for nearby jobs and direct quotes."
                "PENDING_REVIEW" -> "Our compliance team is verifying your National ID and contacting your 2 guarantors. Standard SLA: within 48 hours."
                "CHANGES_REQUIRED" -> "Please re-upload clearer photos of your Uganda National ID or update your guarantor contact details."
                "REJECTED" -> "Your worker application could not be approved. Contact support for assistance."
                else -> "To offer services, receive quote requests, and get hired by neighbors, please complete our mandatory 3-step verification."
            },
            fontSize = 14.sp,
            color = Slate600,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Verification Steps Checklist Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Activation Checklist",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate900
                )

                // Step 1: National ID & Identity
                VerificationStepRow(
                    stepNumber = "1",
                    title = "National ID & Selfie Liveness",
                    subtitle = if (kycStatus == "APPROVED") "Verified (Uganda National ID)" else "Submit valid government ID card",
                    isComplete = kycStatus == "APPROVED",
                    isInProgress = kycStatus == "PENDING_REVIEW"
                )

                // Step 2: 2 Distinct Guarantors
                VerificationStepRow(
                    stepNumber = "2",
                    title = "Two Verified Local Guarantors",
                    subtitle = if (kycStatus == "APPROVED") "Guarantors validated" else "Provide 2 neighborhood references with phone & ID",
                    isComplete = kycStatus == "APPROVED",
                    isInProgress = kycStatus == "PENDING_REVIEW"
                )

                // Step 3: Skill Evidence & 2-3 Day Training
                VerificationStepRow(
                    stepNumber = "3",
                    title = "Trade Safety & Rule Training",
                    subtitle = if (isTrainingDone) "Training Completed (100%)" else "Complete mandatory 2-3 day doorstep rules course",
                    isComplete = isTrainingDone,
                    isInProgress = kycStatus == "APPROVED" && !isTrainingDone
                )
            }
        }

        // Action Buttons
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (kycStatus == "UNVERIFIED" || kycStatus == "CHANGES_REQUIRED") {
                Button(
                    onClick = onStartVerification,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Icon(
                        imageVector = if (kycStatus == "CHANGES_REQUIRED") Icons.Default.EditNote else Icons.Default.Badge,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (kycStatus == "CHANGES_REQUIRED") "Resubmit KYC Documents" else "Begin Worker Verification",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if (kycStatus == "APPROVED" && !isTrainingDone) {
                Button(
                    onClick = onStartTraining,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                ) {
                    Icon(imageVector = Icons.Default.School, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Complete Safety Training", fontWeight = FontWeight.SemiBold)
                }
            }

            OutlinedButton(
                onClick = onSwitchToClientMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate700)
            ) {
                Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Switch back to 'Hire a Neighbor' Mode")
            }
        }
    }
}

@Composable
private fun VerificationStepRow(
    stepNumber: String,
    title: String,
    subtitle: String,
    isComplete: Boolean,
    isInProgress: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isComplete -> Emerald100
                        isInProgress -> Amber100
                        else -> Slate100
                    }
                )
                .border(
                    width = 1.dp,
                    color = when {
                        isComplete -> Emerald500
                        isInProgress -> Amber500
                        else -> Slate300
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Emerald600,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = stepNumber,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isInProgress) Amber800 else Slate600
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Slate900
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (isInProgress) Amber800 else Slate500
            )
        }
    }
}
