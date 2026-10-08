package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.example.util.LocationHelper
import com.example.util.GoogleSignInHelper
import com.example.util.ProfileValidators
import com.example.ui.theme.*

/**
 * AUTH-001: Phone Authentication with OTP
 * Collects Client Full Name, Phone Number, and District Location (via GPS or manual selection)
 * without requiring National ID for clients hiring neighbors.
 * Includes Firebase Phone Auth OTP verification.
 */

enum class AuthStep {
    PHONE_ENTRY,
    OTP_VERIFICATION,
    PROFILE_SETUP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthModal(
    pendingActionReason: String? = null,
    initialDistrict: String = "Sunnyside",
    onLoginSuccess: (phoneNumber: String, fullName: String, district: String) -> Unit,
    onDismiss: () -> Unit,
    onSendOtp: ((phoneNumber: String, onResult: (Boolean, String?) -> Unit) -> Unit)? = null,
    onVerifyOtp: ((otp: String, onResult: (Boolean, String?) -> Unit) -> Unit)? = null,
    isSendingOtp: Boolean = false,
    isVerifyingOtp: Boolean = false,
    authError: String? = null,
    phoneError: String? = null,
    otpError: String? = null,
    onRetry: (() -> Unit)? = null,
    onGoogleSignIn: ((idToken: String, onComplete: (Boolean) -> Unit) -> Unit)? = null
) {
    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var selectedDistrict by remember { mutableStateOf(initialDistrict) }
    var currentStep by remember { mutableStateOf(AuthStep.PHONE_ENTRY) }
    var resendCooldown by remember { mutableStateOf(0) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            LocationHelper.getCurrentLocation(
                context = context,
                onLocationReceived = { locResult ->
                    selectedDistrict = locResult.neighborhoodName
                },
                onError = {
                    selectedDistrict = "Sunnyside"
                }
            )
        }
    }

    // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                task.addOnSuccessListener { account ->
                    // IMPORTANT (H2): Do NOT seed the legal-name field from the Google display
                    // name. The Google display name (e.g. "Isaac Vendetta 1") is a non-authoritative
                    // account label, not a verified legal identity. The user types their own legal
                    // name in PROFILE_SETUP, so the field starts empty.
                    account.idToken?.let { token ->
                        onGoogleSignIn?.invoke(token) { needsOnboarding ->
                            isGoogleSigningIn = false
                            if (needsOnboarding) {
                                currentStep = AuthStep.PROFILE_SETUP
                            }
                            // AUTH-006: needsOnboarding == false means the account was already
                            // recognised; the ViewModel closes this modal via closeAuthModal().
                            // Dismiss explicitly as well so the modal can never be left stranded
                            // if the pending action is null.
                            if (!needsOnboarding) onDismiss()
                        }
                    }
                }
                task.addOnFailureListener { exception ->
                    isGoogleSigningIn = false
                }
            } else {
                isGoogleSigningIn = false
            }
        } catch (e: Exception) {
            isGoogleSigningIn = false
        }
    }

    val triggerLocationFlow: () -> Unit = {
        if (LocationHelper.hasLocationPermission(context)) {
            LocationHelper.getCurrentLocation(
                context = context,
                onLocationReceived = { locResult ->
                    selectedDistrict = locResult.neighborhoodName
                },
                onError = {
                    selectedDistrict = "Sunnyside"
                }
            )
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val popularDistricts = listOf(
        "Sunnyside",
        "Kampala Central",
        "Nakawa",
        "Makindye",
        "Kawempe",
        "Rubaga",
        "Entebbe",
        "Mukono",
        "Wakiso",
        "Jinja"
    )

    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            kotlinx.coroutines.delay(1000)
            resendCooldown--
        }
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(SignalBlue, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Shield",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = when (currentStep) {
                            AuthStep.OTP_VERIFICATION -> "VERIFY PHONE"
                            AuthStep.PROFILE_SETUP -> "COMPLETE PROFILE"
                            else -> "CLIENT ONBOARDING"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SignalBlue
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Slate500,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Step indicator
            if (currentStep != AuthStep.PHONE_ENTRY) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AuthStep.entries.forEach { step ->
                        val isCompleted = step.ordinal < currentStep.ordinal
                        val isCurrent = step == currentStep
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        when {
                                            isCompleted -> IndustrialEmerald
                                            isCurrent -> SignalBlue
                                            else -> Slate300
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Text(
                                        text = "${step.ordinal + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Text(
                                text = when (step) {
                                    AuthStep.PHONE_ENTRY -> "Phone"
                                    AuthStep.OTP_VERIFICATION -> "Verify"
                                    AuthStep.PROFILE_SETUP -> "Profile"
                                },
                                fontSize = 9.sp,
                                color = if (isCurrent) SignalBlue else Slate500
                            )
                        }
                    }
                }
            }

            Text(
                text = when (currentStep) {
                    AuthStep.PHONE_ENTRY -> "Welcome to Skills Exchange"
                    AuthStep.OTP_VERIFICATION -> "Enter Verification Code"
                    AuthStep.PROFILE_SETUP -> "Complete Your Profile"
                },
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = Slate900,
                textAlign = TextAlign.Center
            )

            Text(
                text = when (currentStep) {
                    AuthStep.PHONE_ENTRY -> "Connect with trusted neighborhood specialists."
                    AuthStep.OTP_VERIFICATION -> "We sent a 6-digit code to $phoneNumber"
                    AuthStep.PROFILE_SETUP -> "Just a few details to get you started."
                },
                fontSize = 12.sp,
                color = Slate600,
                textAlign = TextAlign.Center
            )

            if (!pendingActionReason.isNullOrBlank() && currentStep == AuthStep.PHONE_ENTRY) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AmberUrgentLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberUrgent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock", tint = AmberUrgent, modifier = Modifier.size(16.dp))
                        Text(text = "Required for: $pendingActionReason", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberUrgent)
                    }
                }
            }

            if (authError != null) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Red.copy(alpha = 0.1f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Error, contentDescription = "Error", tint = Color.Red, modifier = Modifier.size(16.dp))
                        Text(text = authError, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                    }
                }
            }

            if (onRetry != null && (authError != null || phoneError != null || otpError != null)) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Retry", fontSize = 12.sp, color = SignalBlue)
                }
            }

            when (currentStep) {
                AuthStep.PHONE_ENTRY -> {
                    // Google Sign-In button (Prominent Primary Social Action)
                    Button(
                        onClick = {
                            isGoogleSigningIn = true
                            val signInIntent = GoogleSignInHelper.getClient(context).signInIntent
                            googleSignInLauncher.launch(signInIntent)
                        },
                        enabled = !isGoogleSigningIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Slate900,
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isGoogleSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Google",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = if (isGoogleSigningIn) "Signing In..." else "Continue with Google",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Divider separating Social and Phone Auth
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
                        Text(
                            text = "OR CONTINUE WITH PHONE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 0.5.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
                    }

                    if (phoneError != null) {
                        Text(text = phoneError, fontSize = 11.sp, color = Color.Red, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Mobile Phone Number", fontSize = 12.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Phone", tint = SignalBlue, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SignalBlue, unfocusedBorderColor = Slate300),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    Button(
                        onClick = {
                            if (phoneNumber.isNotBlank() && phoneNumber.length >= 10) {
                                onSendOtp?.invoke(phoneNumber) { success, _ ->
                                    if (success) {
                                        currentStep = AuthStep.OTP_VERIFICATION
                                        resendCooldown = 60
                                    }
                                }
                            }
                        },
                        enabled = !isSendingOtp && phoneNumber.isNotBlank() && phoneNumber.length >= 10,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                    ) {
                        if (isSendingOtp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = if (isSendingOtp) "Sending Code..." else "Send Verification Code", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                AuthStep.OTP_VERIFICATION -> {
                    if (otpError != null) {
                        Text(text = otpError, fontSize = 11.sp, color = Color.Red, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedTextField(
                        value = otpCode,
                        onValueChange = { if (it.length <= 6) otpCode = it },
                        label = { Text("6-Digit Code", fontSize = 12.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Sms, contentDescription = "OTP", tint = SignalBlue, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SignalBlue, unfocusedBorderColor = Slate300),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Button(
                        onClick = {
                            if (otpCode.length == 6) {
                                onVerifyOtp?.invoke(otpCode) { success, _ ->
                                    if (success) currentStep = AuthStep.PROFILE_SETUP
                                }
                            }
                        },
                        enabled = !isVerifyingOtp && otpCode.length == 6,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                    ) {
                        if (isVerifyingOtp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(text = if (isVerifyingOtp) "Verifying..." else "Verify Code", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            if (resendCooldown == 0) {
                                onSendOtp?.invoke(phoneNumber) { success, _ ->
                                    if (success) { resendCooldown = 60; otpCode = "" }
                                }
                            }
                        },
                        enabled = resendCooldown == 0 && !isSendingOtp
                    ) {
                        Text(
                            text = if (resendCooldown > 0) "Resend code in ${resendCooldown}s" else "Resend Code",
                            fontSize = 12.sp,
                            color = if (resendCooldown > 0) Slate500 else SignalBlue
                        )
                    }

                    TextButton(onClick = { currentStep = AuthStep.PHONE_ENTRY; otpCode = "" }) {
                        Text(text = "Change phone number", fontSize = 11.sp, color = Slate500)
                    }
                }

                AuthStep.PROFILE_SETUP -> {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Your Full Name", fontSize = 12.sp) },
                        placeholder = { Text("Enter your legal name", fontSize = 12.sp, color = Slate400) },
                        supportingText = {
                            Text(
                                text = "Use the full name on your National ID. Your Google account name is not used.",
                                fontSize = 10.sp,
                                color = Slate500
                            )
                        },
                        leadingIcon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Name", tint = SignalBlue, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedLabelColor = SignalBlue,
                            unfocusedLabelColor = Slate600,
                            cursorColor = SignalBlue,
                            focusedBorderColor = SignalBlue,
                            unfocusedBorderColor = Slate300
                        ),
                        singleLine = true
                    )

                    // C3 / A1: Mobile phone number is required for all accounts (Mobile Money
                    // escrow and contact). A new Google user bypasses PHONE_ENTRY and lands
                    // here directly, so collect and validate phone number in PROFILE_SETUP.
                    val isPhoneFormatInvalid = phoneNumber.isNotBlank() && !ProfileValidators.isValidPhone(phoneNumber)
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { newValue ->
                            if (newValue.length <= 15) phoneNumber = newValue
                        },
                        label = { Text("Mobile Phone Number", fontSize = 12.sp) },
                        placeholder = { Text("+256 7XX XXX XXX", fontSize = 12.sp, color = Slate400) },
                        supportingText = {
                            if (isPhoneFormatInvalid) {
                                Text(
                                    text = "Enter a valid phone number (9-15 digits).",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Text(
                                    text = "Required for Mobile Money escrow payments & client contact.",
                                    fontSize = 10.sp,
                                    color = Slate500
                                )
                            }
                        },
                        isError = isPhoneFormatInvalid,
                        leadingIcon = { Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = "Phone", tint = SignalBlue, modifier = Modifier.size(18.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedLabelColor = SignalBlue,
                            unfocusedLabelColor = Slate600,
                            cursorColor = SignalBlue,
                            focusedBorderColor = SignalBlue,
                            unfocusedBorderColor = Slate300
                        ),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Your Neighborhood / District", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Slate700)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.clickable { triggerLocationFlow() }
                            ) {
                                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "GPS", tint = SignalBlue, modifier = Modifier.size(14.dp))
                                Text(text = "Auto GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SignalBlue)
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            items(popularDistricts) { district ->
                                val isSelected = selectedDistrict == district
                                Surface(
                                    color = if (isSelected) SignalBlue else Slate100,
                                    contentColor = if (isSelected) Color.White else Slate700,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.clickable { selectedDistrict = district }
                                ) {
                                    Text(
                                        text = district,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { onLoginSuccess(phoneNumber, fullName, selectedDistrict) },
                        // C3 / A1: explicit completion — legal name, phone number, and district
                        // are all required. An empty or invalid phone must never be submitted as
                        // a completed registration.
                        enabled = AuthModalValidation.isProfileSetupValid(fullName, phoneNumber, selectedDistrict),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                    ) {
                        Text(text = "Complete Setup", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Trust badge footer
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Slate400, modifier = Modifier.size(12.dp))
                    Text("Zero Spam Guarantee", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Medium)
                }
                Text("•", fontSize = 10.sp, color = Slate400)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Slate400, modifier = Modifier.size(12.dp))
                    Text("100% Escrow Protection", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

object AuthModalValidation {
    /**
     * C3 / A1: Validates that a user in PROFILE_SETUP has supplied all required fields
     * before the "Complete Setup" action is enabled.
     *
     * Rules:
     * - Full legal name must be non-blank.
     * - Mobile phone number must be non-blank and satisfy [ProfileValidators.isValidPhone] (9-15 digits).
     * - District location must be non-blank.
     */
    fun isProfileSetupValid(fullName: String, phoneNumber: String, district: String): Boolean {
        val trimmedName = fullName.trim()
        val trimmedPhone = phoneNumber.trim()
        val trimmedDistrict = district.trim()
        return trimmedName.isNotEmpty() &&
            trimmedPhone.isNotEmpty() &&
            ProfileValidators.isValidPhone(trimmedPhone) &&
            trimmedDistrict.isNotEmpty()
    }
}