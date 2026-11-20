package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.db.JobPostEntity
import com.example.data.db.DisputeReason
import com.example.data.remote.MoMoPaymentPhase
import com.example.data.remote.MoMoPaymentUiState
import com.example.ui.theme.*
import com.example.util.CurrencyRegion
import com.example.util.SecureScreen

// Brand colors
val MtnYellow = Color(0xFFFFCC00)
val MtnDark = Color(0xFF1B1B1B)
val AirtelRed = Color(0xFFE21833)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscrowAndProofSheet(
    job: JobPostEntity,
    onUpdateEscrowStatus: (String) -> Unit,
    onUpdatePhotos: (beforeUrl: String?, afterUrl: String?) -> Unit,
    onRequestEmergencyAddon: (amount: Double, reason: String) -> Unit,
    onRespondEmergencyAddon: (Boolean) -> Unit,
    onUpdatePaymentChannel: (channel: String, phone: String) -> Unit,
    // PAY-002: the ONLY way money may be claimed as locked. Triggers the trusted
    // `createMoMoCollection` callable server-side; [mobileMoneyPaymentState] then
    // carries the backend's provider-verified result. The device never flips escrow.
    onInitiateMobileMoneyPayment: (phone: String) -> Unit,
    mobileMoneyPaymentState: MoMoPaymentUiState? = null,
    // R02-P2-01/P2-02: release is a server round-trip with its own visible state.
    onReleaseFunds: (() -> Unit)? = null,
    isReleasingFunds: Boolean = false,
    releaseError: String? = null,
    releaseMessage: String? = null,
    onDismiss: () -> Unit,
    onVerifyDoorstepPin: ((enteredPin: String, expectedPin: String, onResult: (Boolean) -> Unit) -> Unit)? = null,
    onRequestMaterialAdvance: ((amount: Double) -> Unit)? = null,
    onRespondMaterialAdvance: ((approve: Boolean) -> Unit)? = null,
    onTriggerSosAlert: (() -> Unit)? = null,
    onClearSosAlert: (() -> Unit)? = null,
    onSubmitDispute: ((reason: com.example.data.db.DisputeReason, description: String) -> Unit)? = null,
    isSubmittingDispute: Boolean = false,
    disputeSubmissionSuccess: Boolean = false,
    disputeSubmissionError: String? = null,
    onSubmitRating: ((rating: Int, review: String) -> Unit)? = null,
    isSubmittingRating: Boolean = false,
    ratingSubmissionSuccess: Boolean = false,
    ratingSubmissionError: String? = null,
    isClearingSos: Boolean = false,
    sosClearError: String? = null
) {
    // R02-P2-06: identity/PIN surface — block screenshots and recents preview.
    SecureScreen()
    var selectedChannel by remember { mutableStateOf(job.paymentChannel) }
    var phoneNumber by remember { mutableStateOf(job.momoPhoneNumber) }

    var activeRegion by remember { mutableStateOf(CurrencyRegion.currentRegion) }

    var showUssdPushModal by remember { mutableStateOf(false) }
    var pinCode by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }

    var doorstepPinInput by remember { mutableStateOf("") }
    var doorstepPinError by remember { mutableStateOf("") }

    var showEmergencyAddonDialog by remember { mutableStateOf(false) }
    var addonAmountText by remember { mutableStateOf("15.00") }
    var addonReasonText by remember { mutableStateOf("Emergency copper elbow fitting") }

    var showMaterialAdvanceDialog by remember { mutableStateOf(false) }

    var showDisputeDialog by remember { mutableStateOf(false) }
    var disputeReasonText by remember { mutableStateOf("") }
    var selectedDisputeReason by remember { mutableStateOf(DisputeReason.QUALITY_ISSUE) }
    var disputeError by remember { mutableStateOf("") }

    // RATE-002: Rating state (client reviews worker after escrow release)
    var showRatingDialog by remember { mutableStateOf(false) }
    var selectedStars by remember { mutableStateOf(0) }
    var reviewText by remember { mutableStateOf("") }
    var ratingError by remember { mutableStateOf("") }

    var beforePhoto by remember { mutableStateOf(job.beforePhotoUrl ?: "img_before_leak") }
    var afterPhoto by remember { mutableStateOf(job.afterPhotoUrl ?: "") }

    var paymentTrustMode by remember { mutableStateOf("FULL_ESCROW") } // "FULL_ESCROW", "MILESTONE_SPLIT", "CASH_SETTLEMENT"

    val isLaborOnly = job.materialsProvidedBy == "RESIDENT" || job.materialsProvidedBy == "NONE_NEEDED"
    val currentGrandTotal = if (job.emergencyAddonStatus == "APPROVED") {
        job.grandTotal + job.emergencyAddonAmount
    } else {
        job.grandTotal
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
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MOBILE MONEY ESCROW & PROOF OF WORK",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SignalBlue
                    )
                    Text(
                        text = job.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        maxLines = 2
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                }
            }

            // Currency Region Selector Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "REGION / CURRENCY:", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Slate500)
                CurrencyRegion.values().forEach { reg ->
                    val isSelected = activeRegion == reg
                    Surface(
                        onClick = {
                            activeRegion = reg
                            CurrencyRegion.currentRegion = reg
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) SignalBlue else Slate100,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SignalBlueDark else Slate300)
                    ) {
                        Text(
                            text = reg.symbol,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else Slate800,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Escrow State Machine Visual Progress Tracker
            EscrowStateMachineBar(currentStatus = job.escrowStatus)

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Slate200)

            // Anti-Disintermediation & Repeat Hire Loyalty Banner
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SignalBlueLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, SignalBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.VerifiedUser, contentDescription = "Security", tint = SignalBlueDark)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🛡️ Platform Safety Guarantee & Repeat Customer Discount",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalBlueDark
                        )
                        Text(
                            text = "Jobs transacted on-platform are protected by pre-authorization escrow & property insurance. Repeat hires enjoy a ~3% loyalty rate!",
                            fontSize = 10.sp,
                            color = SignalBlueDark.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Labor-Only Agreement Badge Header
            if (isLaborOnly) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEFF6FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🛠️", fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Labor-Only Agreement",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E40AF)
                            )
                            Text(
                                text = "Customer handles all parts/materials. Platform fee applies strictly to labor.",
                                fontSize = 11.sp,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                    }
                }
            }

            // SECTION 1: Itemized Cost Breakdown Ledger
            Text(
                text = "ITEMIZED COST LEDGER",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = Slate500
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .background(Slate50, RoundedCornerShape(8.dp))
                    .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Labor Line Item
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Labor (${job.laborHours} hrs @ $${"%.2f".format(job.laborRate)}/hr)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800
                        )
                        Text(text = "Verified specialist skilled rate", fontSize = 10.sp, color = Slate500)
                    }
                    Text(
                        text = "$${"%.2f".format(job.laborTotal)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                // Materials & Receipts Line Item (If Provider Sourced)
                if (!isLaborOnly) {
                    HorizontalDivider(color = Slate200)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Materials & Store Receipts",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate800
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = IndustrialEmeraldLight
                                ) {
                                    Text(
                                        text = "0% Markup",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = IndustrialEmeraldDark,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(text = "Direct pass-through store cost", fontSize = 10.sp, color = Slate500)
                        }
                        Text(
                            text = "$${"%.2f".format(job.materialsSubtotal)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    }
                }

                // Platform & Trust Fee (PAY-005: 5% platform fee + 5% worker commission)
                HorizontalDivider(color = Slate200)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Platform Fee (5%) & Commission (5%)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800
                        )
                        Text(text = "10% total deductions (5% platform fee + 5% worker commission)", fontSize = 10.sp, color = Slate500)
                    }
                    Text(
                        text = CurrencyRegion.format(job.platformFee, activeRegion),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                }

                // Emergency Add-on if requested or approved
                if (job.emergencyAddonStatus == "APPROVED" || job.emergencyAddonStatus == "PENDING") {
                    HorizontalDivider(color = Slate200)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "⚠️ Emergency Material Add-on (${job.emergencyAddonStatus})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (job.emergencyAddonStatus == "APPROVED") IndustrialEmeraldDark else AmberUrgent
                            )
                            Text(text = job.emergencyAddonReason.ifBlank { "Parts purchase" }, fontSize = 10.sp, color = Slate500)
                        }
                        Text(
                            text = "+$${"%.2f".format(job.emergencyAddonAmount)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (job.emergencyAddonStatus == "APPROVED") IndustrialEmeraldDark else AmberUrgent
                        )
                    }
                }

                HorizontalDivider(thickness = 1.5.dp, color = Slate300)

                // Total Escrow Amount
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TOTAL ESCROW REQUIRED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                            color = Slate600
                        )
                        Text(text = "Locked securely in Mobile Money Escrow", fontSize = 10.sp, color = Slate500)
                    }
                    Text(
                        text = CurrencyRegion.format(currentGrandTotal, activeRegion),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = SignalBlue
                    )
                }
            }

            // Emergency Add-on Banner for Customer Approval
            if (job.emergencyAddonStatus == "PENDING") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFFBEB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "Alert", tint = Color(0xFFD97706))
                            Text(
                                text = "Emergency Material Expense Authorization",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                        Text(
                            text = "Worker requested $${"%.2f".format(job.emergencyAddonAmount)} for '${job.emergencyAddonReason}'.",
                            fontSize = 12.sp,
                            color = Color(0xFF78350F),
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onRespondEmergencyAddon(false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                            ) {
                                Text(text = "Decline", fontSize = 12.sp, color = Slate700)
                            }
                            Button(
                                onClick = { onRespondEmergencyAddon(true) },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IndustrialEmerald)
                            ) {
                                Text(text = "Approve & Lock Funds", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Worker Action to Trigger Emergency Add-on
            if (job.emergencyAddonStatus == "NONE" && isLaborOnly) {
                OutlinedButton(
                    onClick = { showEmergencyAddonDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddBusiness,
                        contentDescription = "Emergency Addon",
                        tint = Slate700,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Request Emergency Material Add-on (Worker)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate800
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2: Ground Proof Verification ("Before" & "After" photos)
            Text(
                text = "GROUND PROOF VERIFICATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = Slate500
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Before Photo Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .border(1.dp, Slate300, RoundedCornerShape(8.dp)),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate100)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_before_leak),
                            contentDescription = "Before Photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Slate900.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = "BEFORE JOB",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // After Photo Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(130.dp)
                        .border(
                            1.dp,
                            if (afterPhoto.isNotBlank()) IndustrialEmerald else AmberUrgent,
                            RoundedCornerShape(8.dp)
                        ),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Slate100)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                afterPhoto = "img_after_repair"
                                onUpdatePhotos(beforePhoto, "img_after_repair")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (afterPhoto.isNotBlank()) {
                            Image(
                                painter = painterResource(id = R.drawable.img_after_repair),
                                contentDescription = "After Photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = IndustrialEmeraldDark.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = "AFTER COMPLETION",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Upload After Photo",
                                    tint = AmberUrgent,
                                    modifier = Modifier.size(28.dp)
                                )
                                Text(
                                    text = "Upload 'After' Photo",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate700,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    text = "Required to release escrow",
                                    fontSize = 9.sp,
                                    color = Slate500
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SECTION 2.5: OPERATIONAL SAFEGUARDS & EMERGENCY TRUST CARDS
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(imageVector = Icons.Default.VpnKey, contentDescription = "PIN", tint = SignalBlue)
                            Text(text = "Doorstep Arrival PIN Verification", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate900)
                        }
                        if (job.isDoorstepVerified) {
                            Surface(shape = RoundedCornerShape(12.dp), color = IndustrialEmeraldLight) {
                                Text(text = "VERIFIED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = IndustrialEmeraldDark, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }

                    if (job.isDoorstepVerified) {
                        Text(
                            text = "✅ 4-Digit Doorstep PIN Verified (${job.doorstepPin}). Resident & worker confirmed physically present on site.",
                            fontSize = 11.sp,
                            color = IndustrialEmeraldDark,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Resident's Doorstep Security PIN: ${job.doorstepPin}. Resident shares this code with worker on arrival to confirm job commencement.",
                                fontSize = 10.sp,
                                color = Slate600
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = doorstepPinInput,
                                    onValueChange = {
                                        if (it.length <= 4) {
                                            doorstepPinInput = it
                                            doorstepPinError = ""
                                        }
                                    },
                                    placeholder = { Text("Enter 4-Digit PIN", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                Button(
                                    onClick = {
                                        onVerifyDoorstepPin?.invoke(doorstepPinInput, job.doorstepPin) { success ->
                                            if (!success) {
                                                doorstepPinError = "Incorrect PIN code. Try again."
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                                ) {
                                    Text("Verify PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            if (doorstepPinError.isNotBlank()) {
                                Text(text = doorstepPinError, fontSize = 10.sp, color = AmberUrgent)
                            }
                        }
                    }

                    HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))

                    // Early Parts Purchase Advance Section (Up to 50% parts budget)
                    if (job.materialsProvidedBy == "PROVIDER" || job.materialsSubtotal > 0) {
                        val maxAdvance = (job.materialsSubtotal * 0.50).coerceAtLeast(10.0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "🛒 Early Parts Purchase Advance", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                                Text(
                                    text = if (job.materialAdvanceStatus == "DISBURSED") "✅ 50% Advance Disbursed ($${"%.2f".format(job.materialAdvanceAmount)}). Receipt upload verified."
                                    else if (job.materialAdvanceRequested) "⏳ 50% Advance Requested ($${"%.2f".format(job.materialAdvanceAmount)}). Receipt upload required in 60m."
                                    else "Worker can request up to 50% parts advance ($${"%.2f".format(maxAdvance)}) from escrow before labor.",
                                    fontSize = 10.sp,
                                    color = Slate600
                                )
                            }
                            if (job.materialAdvanceRequested && job.materialAdvanceStatus == "REQUESTED") {
                                Button(
                                    onClick = { onRespondMaterialAdvance?.invoke(true) },
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialEmerald)
                                ) {
                                    Text("Approve", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (!job.materialAdvanceRequested && job.materialAdvanceStatus != "DISBURSED") {
                                OutlinedButton(
                                    onClick = { onRequestMaterialAdvance?.invoke(maxAdvance) },
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SignalBlue)
                                ) {
                                    Text("Request 50%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SignalBlue)
                                }
                            }
                        }

                        HorizontalDivider(color = Slate200, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    // SOS Panic Button & Location Telemetry Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "🛡️ Safety Firewall & Emergency Panic", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                            Text(
                                text = if (job.isSosTriggered) "🚨 EMERGENCY SOS ACTIVE: Live GPS broadcasted to emergency contacts & security."
                                else "Tap panic button if safety risk occurs during physical site visit.",
                                fontSize = 9.sp,
                                color = if (job.isSosTriggered) Color.Red else Slate500
                            )
                        }
                        Button(
                            // H7: an active SOS flag can be stood down from the same contract.
                            // The flag is one-way only in Room (setSosAlert true) — without this
                            // the banner could never clear, so the Trust sheet would alarm forever.
                            // The secured repository boundary decides owner-only authorization;
                            // the button is disabled while a stand-down is in flight.
                            onClick = { if (job.isSosTriggered) onClearSosAlert?.invoke() else onTriggerSosAlert?.invoke() },
                            enabled = !isClearingSos,
                            colors = ButtonDefaults.buttonColors(containerColor = if (job.isSosTriggered) Color.Red else Slate800),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = "SOS", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = if (job.isSosTriggered) "STAND DOWN" else "SOS PANIC", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    // H7/SAFE-003: a rejected stand-down (not owner / not signed in) is surfaced
                    // here so the panic banner is never silently stuck with no explanation.
                    if (sosClearError != null) {
                        Text(
                            text = sosClearError,
                            fontSize = 10.sp,
                            color = Color.Red,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ITEMIZATION AUDIT & TAX RESERVE LEDGER
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Slate100,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.ReceiptLong, contentDescription = "Ledger", tint = Slate700, modifier = Modifier.size(16.dp))
                        Text(text = "Itemized Financial Audit Ledger & Tax Reserve", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }

                    // PAY-005: Itemized 5% worker commission, 5% app fee, 10% total deductions, and net payout
                    val workerCommission = job.laborTotal * 0.05
                    val appPlatformFee = job.laborTotal * 0.05
                    val totalPlatformDeductions = workerCommission + appPlatformFee
                    val netWorkerPayout = (job.laborTotal + job.materialsSubtotal - workerCommission).coerceAtLeast(0.0)
                    val taxReserve = job.grandTotal * 0.02

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Job Gross Escrow Amount", fontSize = 10.sp, color = Slate600)
                        Text(text = CurrencyRegion.format(job.grandTotal, activeRegion), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Worker-Side Commission (5%)", fontSize = 10.sp, color = Slate600)
                        Text(text = "-${CurrencyRegion.format(workerCommission, activeRegion)}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "App / Platform Fee (5%)", fontSize = 10.sp, color = Slate600)
                        Text(text = CurrencyRegion.format(appPlatformFee, activeRegion), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Slate800)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Total Platform Deductions (10%)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                        Text(text = CurrencyRegion.format(totalPlatformDeductions, activeRegion), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Worker Net Direct Payout", fontSize = 10.sp, color = Slate600)
                        Text(text = CurrencyRegion.format(netWorkerPayout, activeRegion), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = IndustrialEmeraldDark)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Local Tax Reserve (2% URA/KRA Withholding)", fontSize = 10.sp, color = Slate600)
                        Text(text = CurrencyRegion.format(taxReserve, activeRegion), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = SignalBlueDark)
                    }
                }
            }

            // SECTION 3: Neutral Vault & Payment Trust Mode Selector
            Text(
                text = "NEUTRAL ESCROW VAULT & PAYMENT TRUST OPTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = Slate500
            )

            // 3-Step Interactive Neutral Vault Workflow Card
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Slate100,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(imageVector = Icons.Default.Security, contentDescription = "Vault", tint = SignalBlueDark, modifier = Modifier.size(16.dp))
                        Text(text = "How Neutral Escrow Protects Your Money", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    }

                    // 3-step visual workflow pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SignalBlueLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "1. Locked in Vault", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = SignalBlueDark)
                                Text(text = "0 paid to worker", fontSize = 8.sp, color = Slate600)
                            }
                        }
                        Text(text = "➔", fontSize = 10.sp, color = Slate400, modifier = Modifier.padding(horizontal = 2.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AmberUrgentLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "2. Work Inspected", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = AmberUrgent)
                                Text(text = "Before/After proof", fontSize = 8.sp, color = Slate600)
                            }
                        }
                        Text(text = "➔", fontSize = 10.sp, color = Slate400, modifier = Modifier.padding(horizontal = 2.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = IndustrialEmeraldLight,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "3. You Approve", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = IndustrialEmeraldDark)
                                Text(text = "Instant release", fontSize = 8.sp, color = Slate600)
                            }
                        }
                    }
                }
            }

            // Payment Trust Mode Options Toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(text = "Select Payment Trust Option:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Slate700)

                val modes = listOf(
                    Triple("FULL_ESCROW", "🔒 Standard Escrow (100% Locked)", "Highest protection. Money stays in vault until you inspect."),
                    Triple("MILESTONE_SPLIT", "⚡ 2-Stage Milestone", "Lock 10% commitment deposit to start + 90% when worker arrives."),
                    Triple("CASH_SETTLEMENT", "💵 Post-Job Cash Settlement", "Pay cash directly after completion (No automated dispute guarantee).")
                )

                modes.forEach { (modeCode, modeTitle, modeDesc) ->
                    val isSelected = paymentTrustMode == modeCode
                    Surface(
                        onClick = { paymentTrustMode = modeCode },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) SignalBlueLight else Slate100,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) SignalBlue else Slate300),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { paymentTrustMode = modeCode },
                                colors = RadioButtonDefaults.colors(selectedColor = SignalBlue)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = modeTitle, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isSelected) SignalBlueDark else Slate900)
                                Text(text = modeDesc, fontSize = 9.sp, color = Slate600)
                            }
                        }
                    }
                }
            }

            // Cash Mode Warning Banner
            if (paymentTrustMode == "CASH_SETTLEMENT") {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AmberUrgentLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberUrgent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = "Warning", tint = AmberUrgent)
                        Column {
                            Text(text = "⚠️ Cash Settlement Disclaimer", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = AmberUrgent)
                            Text(
                                text = "Funds will not be held in platform vault. Escrow dispute protection & insurance guarantees do not apply to direct cash.",
                                fontSize = 10.sp,
                                color = AmberUrgent
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Payment Channel Tabs (MTN MoMo, Airtel Money, Card)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val channels = listOf(
                    Triple("MTN_MOMO", "MTN MoMo", MtnYellow),
                    Triple("AIRTEL_MONEY", "Airtel Money", AirtelRed),
                    Triple("CARD", "Visa / Card", SignalBlue)
                )

                channels.forEach { (code, name, brandColor) ->
                    val isSelected = selectedChannel == code
                    Surface(
                        onClick = {
                            selectedChannel = code
                            onUpdatePaymentChannel(code, phoneNumber)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) brandColor else Slate100,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) brandColor else Slate300
                        )
                    ) {
                        Text(
                            text = name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) (if (code == "MTN_MOMO") MtnDark else Color.White) else Slate700,
                            modifier = Modifier
                                .padding(vertical = 10.dp)
                                .wrapContentWidth(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // Mobile Money Phone Input
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = {
                    phoneNumber = it
                    onUpdatePaymentChannel(selectedChannel, it)
                },
                label = { Text("Mobile Money Phone Number (+256)", fontSize = 11.sp) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Phone", tint = Slate500)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(6.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // R02-P2-01: server release feedback, shown before the state-specific controls.
            if (releaseError != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = RedDangerLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedDanger),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "Release failed: $releaseError",
                        fontSize = 11.sp,
                        color = RedDanger,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (releaseMessage != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = IndustrialEmeraldLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialEmerald),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Text(
                        text = releaseMessage,
                        fontSize = 11.sp,
                        color = IndustrialEmeraldDark,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // ESCROW ACTIONS (State Machine Triggers)
            when (job.escrowStatus) {
                "QUOTE_PENDING", "DEPOSIT_PROMPTED" -> {
                    val actionButtonLabel = when (paymentTrustMode) {
                        "MILESTONE_SPLIT" -> "Lock Stage 1 Transport Deposit (${CurrencyRegion.format(currentGrandTotal * 0.10, activeRegion)})"
                        "CASH_SETTLEMENT" -> "Confirm Cash Settlement on Arrival"
                        else -> "Secure Funds in Neutral Vault (${CurrencyRegion.format(currentGrandTotal, activeRegion)})"
                    }

                    Button(
                        onClick = {
                            if (paymentTrustMode == "CASH_SETTLEMENT") {
                                // R02-P2-01: cash never enters escrow, so it must not be recorded as
                                // locked funds. This records the agreement and says so plainly.
                                onUpdateEscrowStatus("CASH_SETTLEMENT_ACKNOWLEDGED")
                            } else {
                                showUssdPushModal = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (paymentTrustMode == "CASH_SETTLEMENT") Slate800 else (if (selectedChannel == "MTN_MOMO") MtnYellow else (if (selectedChannel == "AIRTEL_MONEY") AirtelRed else SignalBlue)),
                            contentColor = if (paymentTrustMode == "CASH_SETTLEMENT") Color.White else (if (selectedChannel == "MTN_MOMO") MtnDark else Color.White)
                        )
                    ) {
                        Icon(
                            imageVector = if (paymentTrustMode == "CASH_SETTLEMENT") Icons.Default.Payments else Icons.Default.Lock,
                            contentDescription = "Lock Escrow"
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = actionButtonLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                "CASH_SETTLEMENT_ACKNOWLEDGED" -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slate100,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Cash settlement recorded",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate800
                            )
                            Text(
                                text = "No money is held by the platform on this job. Pay the worker in cash on arrival.",
                                fontSize = 11.sp,
                                color = Slate600
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onUpdateEscrowStatus("WORK_IN_PROGRESS") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                    ) {
                        Text(text = "Begin Labor (Start Work in Progress)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                "FUNDS_LOCKED_IN_ESCROW" -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = IndustrialEmeraldLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialEmerald),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Locked", tint = IndustrialEmeraldDark)
                                Column {
                                    Text(text = "Escrow Funds Locked!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndustrialEmeraldDark)
                                    Text(text = "$${"%.2f".format(job.grandTotal)} held safely. Worker may begin labor.", fontSize = 11.sp, color = IndustrialEmeraldDark)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { onUpdateEscrowStatus("WORK_IN_PROGRESS") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                    ) {
                        Text(text = "Begin Labor (Start Work in Progress)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                "WORK_IN_PROGRESS" -> {
                    val canSubmitProof = afterPhoto.isNotBlank()

                    Button(
                        onClick = {
                            if (canSubmitProof) {
                                onUpdateEscrowStatus("PROOF_SUBMITTED")
                            }
                        },
                        enabled = canSubmitProof,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IndustrialEmerald,
                            disabledContainerColor = Slate200
                        )
                    ) {
                        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (canSubmitProof) "Submit Proof & Request Escrow Release" else "Upload 'After' Photo First",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!canSubmitProof) {
                        Text(
                            text = "📷 Ground proof verification rule: At least 1 'After' photo is required before submitting.",
                            fontSize = 11.sp,
                            color = AmberUrgent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                "PROOF_SUBMITTED" -> {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AmberUrgentLight,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AmberUrgent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.RateReview, contentDescription = "Review", tint = AmberUrgent)
                            Text(
                                text = "Ground proof photos attached. Resident can inspect and authorize release or file a quality dispute.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberUrgent
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDisputeDialog = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(imageVector = Icons.Default.Gavel, contentDescription = "Dispute", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Raise Dispute", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onReleaseFunds?.invoke() },
                            enabled = !isReleasingFunds,
                            modifier = Modifier.weight(1.3f),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = IndustrialEmerald)
                        ) {
                            if (isReleasingFunds) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                            } else {
                                Icon(imageVector = Icons.Default.Send, contentDescription = "Release", modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isReleasingFunds) {
                                    "Releasing with the server..."
                                } else {
                                    "Authorize & Release (${CurrencyRegion.format(currentGrandTotal, activeRegion)})"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                "DISPUTE_RAISED" -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF2F2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.Gavel, contentDescription = "Dispute Active", tint = Color.Red)
                                Text(
                                    text = "⚖️ Platform Dispute & Evidence Arbitration Active",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF991B1B)
                                )
                            }
                            Text(
                                text = "Ground photos, before/after timestamps, itemized cost receipts, and in-app chat logs are under platform review.",
                                fontSize = 11.sp,
                                color = Color(0xFF7F1D1D)
                            )
                            HorizontalDivider(color = Color(0xFFFCA5A5))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status: Evidence under AI arbitration",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF991B1B)
                                )
                                Button(
                                    onClick = { onReleaseFunds?.invoke() },
                                    enabled = !isReleasingFunds,
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = IndustrialEmerald)
                                ) {
                                    Text(
                                        text = if (isReleasingFunds) "Releasing..." else "Resolve & Release Funds",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                "RELEASED_TO_WORKER" -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = IndustrialEmeraldLight,
                            border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Verified, contentDescription = "Released", tint = IndustrialEmeraldDark)
                                Column {
                                    Text(text = "✅ Escrow Released & Payout Dispatched!", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = IndustrialEmeraldDark)
                                    Text(text = "Funds sent to ${job.assignedProviderName ?: "Worker"} via Mobile Money.", fontSize = 11.sp, color = IndustrialEmeraldDark)
                                }
                            }
                        }

                        // RATE-002: Client reviews the worker once payment is released.
                        if (onSubmitRating != null && job.assignedProviderId != null && !ratingSubmissionSuccess) {
                            OutlinedButton(
                                onClick = {
                                    selectedStars = 0
                                    reviewText = ""
                                    ratingError = ""
                                    showRatingDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Star, contentDescription = "Rate", tint = AmberUrgent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Rate ${job.assignedProviderName ?: "Worker"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // MOBILE MONEY PAYMENT MODAL (PAY-002: trusted backend flow)
    // The modal no longer funds escrow locally: confirming initiates the server-side
    // collection and the modal renders the backend's provider-verified state.
    if (showUssdPushModal) {
        AlertDialog(
            onDismissRequest = { showUssdPushModal = false },
            containerColor = Slate900,
            titleContentColor = Color.White,
            textContentColor = Slate300,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MtnYellow
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "MoMo",
                            tint = MtnDark,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(20.dp)
                        )
                    }
                    Text(text = "Mobile Money USSD Prompt", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slate800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📲 USSD Push Prompt dispatched to $phoneNumber:\n'Authorize locking $${"%.2f".format(job.grandTotal)} in Skills Exchange Escrow?'",
                            fontSize = 12.sp,
                            color = Color.White,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Text(text = "Enter 4-Digit MoMo PIN to Confirm:", fontSize = 12.sp, color = Slate300)

                    OutlinedTextField(
                        value = pinCode,
                        onValueChange = {
                            if (it.length <= 4) {
                                pinCode = it
                                pinError = ""
                            }
                        },
                        placeholder = { Text("****", color = Slate500) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MtnYellow,
                            unfocusedBorderColor = Slate600
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinError.isNotBlank()) {
                        Text(text = pinError, fontSize = 11.sp, color = AmberUrgent)
                    }

                    // PAY-002: provider-verified state from the trusted backend only.
                    mobileMoneyPaymentState?.let { state ->
                        val stateLabel = when (state.phase) {
                            MoMoPaymentPhase.PENDING ->
                                "⏳ Payment pending — approve the prompt on $phoneNumber. Funds are NOT in escrow yet."
                            MoMoPaymentPhase.SUCCESSFUL ->
                                "✅ ${state.message}"
                            MoMoPaymentPhase.FAILED ->
                                "❌ ${state.message}"
                            MoMoPaymentPhase.NEEDS_ATTENTION ->
                                "⚠️ ${state.message}"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = when (state.phase) {
                                MoMoPaymentPhase.SUCCESSFUL -> Color(0xFF14532D)
                                MoMoPaymentPhase.FAILED -> Color(0xFF5B1A1A)
                                MoMoPaymentPhase.NEEDS_ATTENTION -> Color(0xFF4A3A12)
                                else -> Slate800
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stateLabel,
                                fontSize = 11.sp,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinCode.length == 4) {
                            // PAY-002: initiate the TRUSTED server-side collection. Escrow
                            // locks only after the backend confirms the provider reports
                            // SUCCESSFUL — never from this click or a local 202.
                            onInitiateMobileMoneyPayment(phoneNumber)
                        } else {
                            pinError = "Please enter a valid 4-digit PIN."
                        }
                    },
                    enabled = mobileMoneyPaymentState?.phase != MoMoPaymentPhase.PENDING,
                    colors = ButtonDefaults.buttonColors(containerColor = MtnYellow, contentColor = MtnDark),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = when (mobileMoneyPaymentState?.phase) {
                            MoMoPaymentPhase.PENDING -> "Waiting for confirmation…"
                            MoMoPaymentPhase.SUCCESSFUL -> "Paid — Escrow Locked"
                            MoMoPaymentPhase.FAILED -> "Retry Payment"
                            MoMoPaymentPhase.NEEDS_ATTENTION -> "Check Status Again"
                            null -> "Confirm PIN & Send Payment Request"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUssdPushModal = false }) {
                    Text(text = "Cancel", color = Slate400)
                }
            }
        )
    }

    // WORKER EMERGENCY ADD-ON DIALOG
    if (showEmergencyAddonDialog) {
        AlertDialog(
            onDismissRequest = { showEmergencyAddonDialog = false },
            title = { Text("Request Material Expense Add-on", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "If unexpected parts or fittings are required during site labor, request authorization from customer.",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    OutlinedTextField(
                        value = addonAmountText,
                        onValueChange = { addonAmountText = it },
                        label = { Text("Expense Amount ($)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = addonReasonText,
                        onValueChange = { addonReasonText = it },
                        label = { Text("Reason / Parts Description", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = addonAmountText.toDoubleOrNull() ?: 15.0
                        showEmergencyAddonDialog = false
                        onRequestEmergencyAddon(amount, addonReasonText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(text = "Send Request to Resident", fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmergencyAddonDialog = false }) {
                    Text(text = "Cancel", color = Slate600)
                }
            }
        )
    }

    // QUALITY DISPUTE DIALOG - DISP-002: Wired to persistence
    if (showDisputeDialog) {
        // H7: surface repository rejections (not authenticated / already filed /
        // no assigned worker) inside the dialog so a failed filing is never
        // mistaken for a recorded dispute — mirrors the H6 rating dialog.
        LaunchedEffect(disputeSubmissionError) {
            if (disputeSubmissionError != null) {
                disputeError = disputeSubmissionError
            }
        }
        // Auto-dismiss once the backend confirms a successful filing.
        LaunchedEffect(disputeSubmissionSuccess) {
            if (disputeSubmissionSuccess) {
                showDisputeDialog = false
                disputeReasonText = ""
                disputeError = ""
            }
        }
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingDispute) {
                    showDisputeDialog = false
                    disputeReasonText = ""
                    disputeError = ""
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Gavel, contentDescription = "Dispute", tint = Color.Red)
                    Text("File Escrow Quality Dispute", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Describe the defect or unfinished labor. Funds will remain securely locked in escrow while platform arbitration reviews before & after photos.",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    // DISP-002: Reason selector
                    Text("Reason", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DisputeReason.entries.forEach { reason ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDisputeReason = reason }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedDisputeReason == reason,
                                    onClick = { selectedDisputeReason = reason }
                                )
                                Text(
                                    text = reason.value.replace("_", " ").lowercase()
                                        .replaceFirstChar { it.uppercase() },
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = disputeReasonText,
                        onValueChange = {
                            disputeReasonText = it
                            disputeError = ""
                        },
                        label = { Text("Description / Details", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !isSubmittingDispute,
                        isError = disputeError.isNotEmpty(),
                        supportingText = if (disputeError.isNotEmpty()) {
                            { Text(disputeError, color = Color.Red, fontSize = 10.sp) }
                        } else null
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Slate100,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Evidence", tint = Slate600, modifier = Modifier.size(18.dp))
                            Text(text = "Counter-evidence photos from site inspection", fontSize = 10.sp, color = Slate700)
                        }
                    }

                    // DISP-002: Success message
                    if (disputeSubmissionSuccess) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Success", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                Text(text = "Dispute submitted successfully. Funds are now frozen pending review.", fontSize = 10.sp, color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // DISP-002: Validate input
                        if (disputeReasonText.isBlank()) {
                            disputeError = "Please provide a description"
                            return@Button
                        }
                        if (disputeReasonText.length < 10) {
                            disputeError = "Description must be at least 10 characters"
                            return@Button
                        }

                        // DISP-002: Submit via callback
                        onSubmitDispute?.invoke(selectedDisputeReason, disputeReasonText)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isSubmittingDispute
                ) {
                    if (isSubmittingDispute) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isSubmittingDispute) "Submitting..." else "Submit Evidence & Freeze Escrow",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDisputeDialog = false
                        disputeReasonText = ""
                        disputeError = ""
                    },
                    enabled = !isSubmittingDispute
                ) {
                    Text(text = "Cancel", color = Slate600)
                }
            }
        )
    }
    // RATE-002: Client rating dialog (1–5 stars + written review) for completed jobs.
    // The "Rate Worker" button above only sets showRatingDialog = true; this block renders it.
    if (showRatingDialog) {
        // Auto-dismiss once the backend confirms a successful submission.
        LaunchedEffect(ratingSubmissionSuccess) {
            if (ratingSubmissionSuccess) {
                showRatingDialog = false
                selectedStars = 0
                reviewText = ""
                ratingError = ""
            }
        }
        // H6: surface repository rejections (not authenticated / not authorized /
        // already rated / job not found / no assigned worker) inside the dialog so a
        // failed submission is never mistaken for a saved rating.
        LaunchedEffect(ratingSubmissionError) {
            if (ratingSubmissionError != null) {
                ratingError = ratingSubmissionError
            }
        }
        AlertDialog(
            onDismissRequest = {
                if (!isSubmittingRating) {
                    showRatingDialog = false
                    ratingError = ""
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rate",
                        tint = AmberUrgent
                    )
                    Text(
                        text = "Rate ${job.assignedProviderName ?: "Worker"}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "How was your experience? Your honest feedback keeps the community trustworthy.",
                        fontSize = 11.sp,
                        color = Slate600
                    )

                    // Star selector (1–5). At least one star is required.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "$star star",
                                tint = if (star <= selectedStars) AmberUrgent else Slate300,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(enabled = !isSubmittingRating) {
                                        selectedStars = star
                                        ratingError = ""
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = reviewText,
                        onValueChange = {
                            reviewText = it
                            ratingError = ""
                        },
                        label = { Text("Write a review (optional)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !isSubmittingRating,
                        isError = ratingError.isNotEmpty(),
                        supportingText = if (ratingError.isNotEmpty()) {
                            { Text(ratingError, color = Color.Red, fontSize = 10.sp) }
                        } else null
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // RATE-002: a star selection is mandatory (review text is optional).
                        if (selectedStars < 1) {
                            ratingError = "Please select a star rating"
                            return@Button
                        }
                        onSubmitRating?.invoke(selectedStars, reviewText)
                    },
                    shape = RoundedCornerShape(6.dp),
                    enabled = !isSubmittingRating,
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                ) {
                    if (isSubmittingRating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isSubmittingRating) "Submitting..." else "Submit Rating",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isSubmittingRating) {
                            showRatingDialog = false
                            ratingError = ""
                        }
                    },
                    enabled = !isSubmittingRating
                ) {
                    Text(text = "Cancel", color = Slate600)
                }
            }
        )
    }
}

@Composable
fun EscrowStateMachineBar(currentStatus: String) {
    val stages = listOf(
        Pair("QUOTE_PENDING", "Quote"),
        Pair("DEPOSIT_PROMPTED", "Prompted"),
        Pair("FUNDS_LOCKED_IN_ESCROW", "Locked"),
        Pair("WORK_IN_PROGRESS", "Labor"),
        Pair("PROOF_SUBMITTED", "Proof"),
        Pair("RELEASED_TO_WORKER", "Released")
    )

    val currentIdx = stages.indexOfFirst { it.first == currentStatus }.coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(Slate100, RoundedCornerShape(6.dp))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { idx, (code, label) ->
            val isActive = idx == currentIdx
            val isPassed = idx < currentIdx

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(
                            when {
                                isActive -> SignalBlue
                                isPassed -> IndustrialEmerald
                                else -> Slate300
                            },
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPassed) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }

                Text(
                    text = label,
                    fontSize = 9.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) SignalBlue else (if (isPassed) IndustrialEmeraldDark else Slate500),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
