package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.*
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.util.GoogleSignInHelper
import com.example.util.LocationHelper

enum class OnboardingStep {
    ROLE_SELECTION,
    AUTHENTICATION,
    PROFILE_SETUP
}

@Composable
fun OnboardingScreen(
    viewModel: SkillsExchangeViewModel,
    onOnboardingComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(OnboardingStep.ROLE_SELECTION) }
    var selectedRole by remember { mutableStateOf("CLIENT") } // "CLIENT" or "WORKER"

    var fullName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    var selectedDistrict by remember { mutableStateOf("Sunnyside") }
    var resendCooldown by remember { mutableStateOf(0) }
    var isGoogleSigningIn by remember { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val phoneError by viewModel.phoneError.collectAsStateWithLifecycle()
    val otpError by viewModel.otpError.collectAsStateWithLifecycle()

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
                    account.displayName?.let { name ->
                        if (name.isNotBlank()) fullName = name
                    }
                    account.idToken?.let { token ->
                        viewModel.loginWithGoogle(token, initialRole = selectedRole) { needsOnboarding ->
                            isGoogleSigningIn = false
                            if (needsOnboarding) {
                                currentStep = OnboardingStep.PROFILE_SETUP
                            } else {
                                onOnboardingComplete()
                            }
                        }
                    }
                }
                task.addOnFailureListener {
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Slate50
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar / Progress Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep != OnboardingStep.ROLE_SELECTION) {
                    IconButton(
                        onClick = {
                            if (currentStep == OnboardingStep.PROFILE_SETUP && phoneNumber.isNotBlank()) {
                                currentStep = OnboardingStep.AUTHENTICATION
                            } else {
                                currentStep = OnboardingStep.ROLE_SELECTION
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Slate700
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(36.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(SignalBlue, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Handyman,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "SKILLS EXCHANGE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Slate900
                    )
                }

                Spacer(modifier = Modifier.size(36.dp))
            }

            // Step Indicator Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                OnboardingStep.entries.forEach { step ->
                    val isActive = step == currentStep
                    val isPast = step.ordinal < currentStep.ordinal
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isActive) 24.dp else 12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    isActive -> SignalBlue
                                    isPast -> IndustrialEmerald
                                    else -> Slate200
                                }
                            )
                    )
                }
            }

            when (currentStep) {
                // ==========================================
                // STEP 1: ROLE SELECTION
                // ==========================================
                OnboardingStep.ROLE_SELECTION -> {
                    Text(
                        text = "Are you a Worker or Client?",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Text(
                        text = "Choose how you would like to get started. You can toggle between client and worker modes at any time.",
                        fontSize = 13.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Client Option Card
                    val isClientSelected = selectedRole == "CLIENT"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable { selectedRole = "CLIENT" }
                            .border(
                                width = if (isClientSelected) 2.dp else 1.dp,
                                color = if (isClientSelected) SignalBlue else Slate200,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isClientSelected) SignalBlue.copy(alpha = 0.04f) else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isClientSelected) 2.dp else 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                if (isClientSelected) SignalBlue else Slate100,
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isClientSelected) Color.White else Slate700,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "I'm a Client",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "Hire a Neighbor",
                                            fontSize = 12.sp,
                                            color = SignalBlue,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isClientSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isClientSelected) SignalBlue else Slate400,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Find and hire vetted local specialists for plumbing, electrical work, home repairs, cleaning, and more.",
                                fontSize = 12.5.sp,
                                color = Slate600,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = IndustrialEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Mobile Money Escrow protected • No National ID needed to hire",
                                    fontSize = 11.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Worker Option Card
                    val isWorkerSelected = selectedRole == "WORKER"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 28.dp)
                            .clickable { selectedRole = "WORKER" }
                            .border(
                                width = if (isWorkerSelected) 2.dp else 1.dp,
                                color = if (isWorkerSelected) IndustrialEmerald else Slate200,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isWorkerSelected) IndustrialEmerald.copy(alpha = 0.04f) else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isWorkerSelected) 2.dp else 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(
                                                if (isWorkerSelected) IndustrialEmerald else Slate100,
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Handyman,
                                            contentDescription = null,
                                            tint = if (isWorkerSelected) Color.White else Slate700,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "I'm a Worker",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "Offer Skills",
                                            fontSize = 12.sp,
                                            color = IndustrialEmerald,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (isWorkerSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isWorkerSelected) IndustrialEmerald else Slate400,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Offer your trade and craft skills to nearby neighbors, quote on job requests, and grow your local client base.",
                                fontSize = 12.5.sp,
                                color = Slate600,
                                lineHeight = 17.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = IndustrialEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "National ID verification & 2-guarantor safety review required",
                                    fontSize = 11.sp,
                                    color = Slate500,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { currentStep = OnboardingStep.AUTHENTICATION },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == "WORKER") IndustrialEmerald else SignalBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (selectedRole == "WORKER") "Continue as Worker" else "Continue as Client",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // ==========================================
                // STEP 2: AUTHENTICATION
                // ==========================================
                OnboardingStep.AUTHENTICATION -> {
                    Surface(
                        color = if (selectedRole == "WORKER") IndustrialEmerald.copy(alpha = 0.1f) else SignalBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = if (selectedRole == "WORKER") "SELECTED ROLE: WORKER" else "SELECTED ROLE: CLIENT",
                            color = if (selectedRole == "WORKER") IndustrialEmerald else SignalBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        text = "Sign in or Register",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = "Use Google or your phone number to secure your ${if (selectedRole == "WORKER") "Worker" else "Client"} account.",
                        fontSize = 13.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Google Sign-In Button
                    OutlinedButton(
                        onClick = {
                            isGoogleSigningIn = true
                            val signInIntent = GoogleSignInHelper.getSignInIntent(context)
                            googleSignInLauncher.launch(signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                        enabled = !isGoogleSigningIn
                    ) {
                        if (isGoogleSigningIn) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SignalBlue)
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Continue with Google",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate800
                                )
                            }
                        }
                    }

                    // Divider
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
                        Text(
                            text = "OR USE PHONE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate400,
                            modifier = Modifier.padding(horizontal = 12.dp),
                            letterSpacing = 1.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Slate200)
                    }

                    // Phone Number Input
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = { Text("Mobile Money Phone Number") },
                        placeholder = { Text("+256 772 198 420") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Slate500)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (phoneError != null) {
                        Text(
                            text = phoneError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }

                    // OTP Code Field if in Verifying / OTP stage
                    val isVerifying = authState is SkillsExchangeViewModel.AuthState.OtpSent || authState is SkillsExchangeViewModel.AuthState.VerifyingOtp
                    AnimatedVisibility(visible = isVerifying) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = otpCode,
                                onValueChange = { if (it.length <= 6) otpCode = it },
                                label = { Text("6-Digit Verification Code") },
                                placeholder = { Text("123456") },
                                leadingIcon = {
                                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Slate500)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                isError = otpError != null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (otpError != null) {
                                Text(
                                    text = otpError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp)
                                )
                            }
                        }
                    }

                    if (authError != null) {
                        Text(
                            text = authError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val isSendingOtp = authState is SkillsExchangeViewModel.AuthState.SendingOtp
                    val isVerifyingOtp = authState is SkillsExchangeViewModel.AuthState.VerifyingOtp

                    if (!isVerifying) {
                        Button(
                            onClick = {
                                if (phoneNumber.isNotBlank()) {
                                    viewModel.sendOtp(phoneNumber) { success, _ ->
                                        if (success) {
                                            resendCooldown = 60
                                        }
                                    }
                                }
                            },
                            enabled = phoneNumber.length >= 9 && !isSendingOtp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedRole == "WORKER") IndustrialEmerald else SignalBlue)
                        ) {
                            if (isSendingOtp) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Send Verification Code", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                if (otpCode.length == 6) {
                                    viewModel.verifyOtp(otpCode) { success, _ ->
                                        if (success) {
                                            currentStep = OnboardingStep.PROFILE_SETUP
                                        }
                                    }
                                }
                            },
                            enabled = otpCode.length == 6 && !isVerifyingOtp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (selectedRole == "WORKER") IndustrialEmerald else SignalBlue)
                        ) {
                            if (isVerifyingOtp) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text("Verify & Continue", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ==========================================
                // STEP 3: PROFILE SETUP
                // ==========================================
                OnboardingStep.PROFILE_SETUP -> {
                    Surface(
                        color = if (selectedRole == "WORKER") IndustrialEmerald.copy(alpha = 0.1f) else SignalBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = if (selectedRole == "WORKER") "INITIAL ROLE: WORKER" else "INITIAL ROLE: CLIENT",
                            color = if (selectedRole == "WORKER") IndustrialEmerald else SignalBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Text(
                        text = "Complete Profile Information",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    Text(
                        text = "Set your full name and district location to finish setup.",
                        fontSize = 13.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Legal Name") },
                        placeholder = { Text(if (selectedRole == "WORKER") "e.g. Isaac Kato" else "e.g. Sarah Namukasa") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = Slate500)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // District Location Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OPERATING DISTRICT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Slate500,
                                letterSpacing = 0.5.sp
                            )
                            TextButton(onClick = triggerLocationFlow) {
                                Icon(
                                    imageVector = Icons.Default.MyLocation,
                                    contentDescription = null,
                                    tint = SignalBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Auto-detect GPS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SignalBlue
                                )
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(popularDistricts) { district ->
                                val isSelected = selectedDistrict.equals(district, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedDistrict = district },
                                    label = { Text(district, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (selectedRole == "WORKER") IndustrialEmerald else SignalBlue,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // Worker Specific Notice
                    if (selectedRole == "WORKER") {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            colors = CardDefaults.cardColors(containerColor = IndustrialEmerald.copy(alpha = 0.08f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = IndustrialEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Worker Verification Notice",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = IndustrialEmerald
                                    )
                                    Text(
                                        text = "After creating your profile, you will complete Uganda National ID verification, submit 2 guarantors, and finish brief safety training before bidding on jobs.",
                                        fontSize = 11.5.sp,
                                        color = Slate700,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.completeOnboarding(
                                phone = phoneNumber,
                                fullName = fullName,
                                district = selectedDistrict,
                                selectedRole = selectedRole
                            )
                            onOnboardingComplete()
                        },
                        enabled = fullName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedRole == "WORKER") IndustrialEmerald else SignalBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (selectedRole == "WORKER") "Complete Worker Setup" else "Start Exploring Jobs",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
