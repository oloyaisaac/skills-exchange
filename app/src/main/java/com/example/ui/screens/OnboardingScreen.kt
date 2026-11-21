package com.example.ui.screens

import android.util.Log
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.AfricaTalkingService.OtpResponse
import com.example.data.remote.AfricaTalkingService.VerificationResponse
import com.example.ui.theme.*
import com.example.R
import com.example.ui.a11y.announcementLiveRegion
import com.example.ui.a11y.sectionHeading
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.util.GoogleSignInHelper
import com.example.util.LocationHelper

/**
 * Device-debug log tag for the onboarding authentication hand-off.
 *
 * Documents (on-device, via `adb logcat -s OnboardingAuth`) whether the Google activity
 * result callback fires, what Google Play services returned, and which Firebase UID —
 * if any — exists at the moment the UI leaves AUTHENTICATION for PROFILE_SETUP.
 */
private const val OnboardingAuthTag = "OnboardingAuth"

enum class OnboardingStep {
    ROLE_SELECTION,
    NEW_USER_ROLE_SELECTION,
    AUTHENTICATION,
    PROFILE_SETUP
}

@Composable
fun OnboardingScreen(
    viewModel: SkillsExchangeViewModel,
    onOnboardingComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    // R02-P2-03: the onboarding form survives rotation and process death. A user who switches to
    // the SMS app to read an OTP must not come back to an empty form.
    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.ROLE_SELECTION) }
    var selectedRole by rememberSaveable { mutableStateOf("CLIENT") } // "CLIENT" or "WORKER"
    var newUserRole by rememberSaveable { mutableStateOf("") } // "CLIENT" or "WORKER" for NEW_HERE users

    var fullName by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var otpCode by rememberSaveable { mutableStateOf("") }
    var selectedDistrict by rememberSaveable { mutableStateOf("") }
    var resendCooldown by rememberSaveable { mutableStateOf(0) }

    var showOtpInput by rememberSaveable { mutableStateOf(false) }
    var isVerifying by rememberSaveable { mutableStateOf(false) }
    var requestId by rememberSaveable { mutableStateOf<String?>(null) }
    var isGoogleSigningIn by rememberSaveable { mutableStateOf(false) }
    // R06-P2-06: reason Google sign-in cannot start in this build (unset client id), if any.
    var googleConfigMessage by remember { mutableStateOf<String?>(null) }

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
                    // Keep the current selection; a GPS failure is not a district.
                }
            )
        }
    }

        // Google Sign-In launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // AUTH-002: Diagnose the Google hand-off on-device. OnboardingAuthTag is
        // intentionally surfaced via `adb logcat -s OnboardingAuth` so success,
        // cancellation, and exception paths are never silent.
        val uidBefore = viewModel.currentAuthenticatedUid()
        Log.d(OnboardingAuthTag, "googleSignInLauncher callback: resultCode=${result.resultCode}, firebaseUid_before=$uidBefore")
        when (result.resultCode) {
            android.app.Activity.RESULT_OK -> {
                try {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn
                        .getSignedInAccountFromIntent(result.data)
                    task.addOnSuccessListener { account ->
                        Log.d(OnboardingAuthTag, "Google task SUCCESS: idToken present=${!account.idToken.isNullOrBlank()}, email=${account.email}")
                        val token = account.idToken
                        if (token.isNullOrBlank()) {
                            // Fail closed: no idToken means we cannot exchange for a Firebase
                            // credential. Do NOT advance to PROFILE_SETUP.
                            isGoogleSigningIn = false
                            viewModel.reportAuthError("Google sign-in returned no token. Please try again.")
                            return@addOnSuccessListener
                        }
                        viewModel.loginWithGoogle(token, initialRole = selectedRole) { needsOnboarding ->
                            isGoogleSigningIn = false
                            val uidAfter = viewModel.currentAuthenticatedUid()
                            Log.d(OnboardingAuthTag, "AUTHENTICATION->PROFILE_SETUP: needsOnboarding=$needsOnboarding, firebaseUid_before=$uidBefore, firebaseUid_after=$uidAfter")
                            if (needsOnboarding) {
                                currentStep = OnboardingStep.PROFILE_SETUP
                            } else {
                                onOnboardingComplete()
                            }
                        }
                    }
                    task.addOnFailureListener { e ->
                        isGoogleSigningIn = false
                        Log.d(OnboardingAuthTag, "Google task FAILURE: ${e.message}")
                        viewModel.reportAuthError("Google sign-in failed. Please try again.")
                    }
                } catch (e: Exception) {
                    isGoogleSigningIn = false
                    Log.d(OnboardingAuthTag, "Google sign-in exception: ${e.message}")
                    viewModel.reportAuthError("Google sign-in error. Please try again.")
                }
            }
            else -> {
                isGoogleSigningIn = false
                Log.d(OnboardingAuthTag, "Google sign-in not completed (resultCode=${result.resultCode}). The account picker did not return a usable result.")
                // Fail closed: stay on AUTHENTICATION, never advance.
                viewModel.reportAuthError("Google sign-in did not complete. Ensure a Google account is added to the device and try again.")
            }
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
                    // Keep the current selection; a GPS failure is not a district.
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
                // STEP 1: WELCOME + EXISTING/NEW SELECTION
                // ==========================================
                OnboardingStep.ROLE_SELECTION -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Welcome to Skills Exchange",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                        Text(
                            text = "Connect with skilled workers or find opportunities to offer your skills.",
                            fontSize = 13.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        Text(
                            text = stringResource(R.string.onboarding_role_question),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate800,
                            textAlign = TextAlign.Center,
                            // R12-P2-02: TalkBack's heading navigation starts with knowing what is one.
                            modifier = Modifier
                                .padding(bottom = 16.dp)
                                .sectionHeading()
                        )

                        // Existing Client Card
                    val isClientSelected = selectedRole == "CLIENT" || selectedRole == "EXISTING_CLIENT"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .clickable { selectedRole = "EXISTING_CLIENT" }
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
                                            text = "Existing Client",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "I already have an account and want to hire workers",
                                            fontSize = 11.sp,
                                            color = Slate500,
                                            lineHeight = 14.sp
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
                                text = "Sign in to access your existing account, saved jobs, and payment history.",
                                fontSize = 12.5.sp,
                                color = Slate600,
                                lineHeight = 17.sp
                            )
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
                                            text = "Existing Worker",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Slate900
                                        )
                                        Text(
                                            text = "I already have a worker account and want to continue",
                                            fontSize = 11.sp,
                                            color = Slate500,
                                            lineHeight = 14.sp
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
                                text = "Sign in to access your worker profile, active jobs, and verification status.",
                                fontSize = 12.5.sp,
                                color = Slate600,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    // New Here Card
                    val isNewHereSelected = selectedRole == "NEW_HERE"
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp)
                            .clickable { selectedRole = "NEW_HERE" }
                            .border(
                                width = if (isNewHereSelected) 2.dp else 1.dp,
                                color = if (isNewHereSelected) SignalBlue else Slate200,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNewHereSelected) SignalBlue.copy(alpha = 0.04f) else Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (isNewHereSelected) 2.dp else 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        if (isNewHereSelected) SignalBlue else Slate100,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = null,
                                    tint = if (isNewHereSelected) Color.White else Slate700,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "New Here",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = "I want to create a brand new account",
                                    fontSize = 11.sp,
                                    color = Slate500,
                                    lineHeight = 14.sp
                                )
                            }
                            if (isNewHereSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = SignalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedRole == "NEW_HERE") {
                                currentStep = OnboardingStep.NEW_USER_ROLE_SELECTION
                            } else {
                                currentStep = OnboardingStep.AUTHENTICATION
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = selectedRole.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (selectedRole) {
                                "EXISTING_WORKER" -> IndustrialEmerald
                                else -> SignalBlue
                            }
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = 14.sp,
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
            }

            // ==========================================
            // STEP 1b: NEW USER ROLE SELECTION (Client or Worker)
            // ==========================================
            OnboardingStep.NEW_USER_ROLE_SELECTION -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "What do you want to do?",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = Slate900,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Choose whether you want to hire skilled workers or offer your own skills.",
                            fontSize = 13.sp,
                            color = Slate600,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        // Client option
                        val isNewClient = newUserRole == "CLIENT"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .clickable { newUserRole = "CLIENT" }
                                .border(
                                    width = if (isNewClient) 2.dp else 1.dp,
                                    color = if (isNewClient) SignalBlue else Slate200,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isNewClient) SignalBlue.copy(alpha = 0.04f) else Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isNewClient) 2.dp else 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (isNewClient) SignalBlue else Slate100,
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isNewClient) Color.White else Slate700,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "I want to hire workers",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "Post jobs and connect with verified skilled workers",
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        lineHeight = 14.sp
                                    )
                                }
                                if (isNewClient) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = SignalBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Worker option
                        val isNewWorker = newUserRole == "WORKER"
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp)
                                .clickable { newUserRole = "WORKER" }
                                .border(
                                    width = if (isNewWorker) 2.dp else 1.dp,
                                    color = if (isNewWorker) IndustrialEmerald else Slate200,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isNewWorker) IndustrialEmerald.copy(alpha = 0.04f) else Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = if (isNewWorker) 2.dp else 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            if (isNewWorker) IndustrialEmerald else Slate100,
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Handyman,
                                        contentDescription = null,
                                        tint = if (isNewWorker) Color.White else Slate700,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "I want to offer my skills",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "Register as a worker and get hired for jobs in your area",
                                        fontSize = 11.sp,
                                        color = Slate500,
                                        lineHeight = 14.sp
                                    )
                                }
                                if (isNewWorker) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = IndustrialEmerald,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                // Apply the chosen role and navigate to authentication
                                selectedRole = newUserRole
                                currentStep = OnboardingStep.AUTHENTICATION
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = newUserRole.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (newUserRole == "WORKER") IndustrialEmerald else SignalBlue
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Continue",
                                fontSize = 14.sp,
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
                }

                // ==========================================
                // STEP 2: AUTHENTICATION
                // ==========================================
                OnboardingStep.AUTHENTICATION -> {
                    // Normalize role for UI: EXISTING_CLIENT and NEW_HERE (client) → CLIENT styling
                    // EXISTING_WORKER and NEW_HERE (worker) → WORKER styling
                    val isWorkerRole = selectedRole == "WORKER" || selectedRole == "EXISTING_WORKER"
                    val roleBadgeText = when (selectedRole) {
                        "EXISTING_CLIENT" -> "EXISTING CLIENT"
                        "EXISTING_WORKER" -> "EXISTING WORKER"
                        "NEW_HERE" -> if (newUserRole == "WORKER") "NEW WORKER" else "NEW CLIENT"
                        "WORKER" -> "WORKER"
                        else -> "CLIENT"
                    }
                    val badgeColor = if (isWorkerRole) IndustrialEmerald else SignalBlue

                    Surface(
                        color = if (isWorkerRole) IndustrialEmerald.copy(alpha = 0.1f) else SignalBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "ACCOUNT TYPE: $roleBadgeText",
                            color = badgeColor,
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
                        text = "Use Google or your phone number to secure your ${if (isWorkerRole) "Worker" else "Client"} account.",
                        fontSize = 13.sp,
                        color = Slate600,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // Google Sign-In Button
                    OutlinedButton(
                        onClick = {
                            // R06-P2-06: no client id in this build means no Google flow at all.
                            val client = GoogleSignInHelper.getClient(context)
                            if (client == null) {
                                googleConfigMessage = GoogleSignInHelper.NOT_CONFIGURED_MESSAGE
                            } else {
                                googleConfigMessage = null
                                isGoogleSigningIn = true
                                googleSignInLauncher.launch(client.signInIntent)
                            }
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

                    if (googleConfigMessage != null) {
                        Text(
                            text = googleConfigMessage!!,
                            fontSize = 11.sp,
                            color = RedDanger,
                            modifier = Modifier.padding(top = 6.dp)
                        )
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
                            // R12-P2-06: a label users must read, so it clears the body floor.
                            color = Slate500,
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
                                .announcementLiveRegion()
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
                                        .announcementLiveRegion()
                                )
                            }
                        }
                    }

                    if (authError != null) {
                        Text(
                            text = authError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .announcementLiveRegion()
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
                            colors = ButtonDefaults.buttonColors(containerColor = if (isWorkerRole) IndustrialEmerald else SignalBlue)
                        ) {
                            if (isSendingOtp) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Text(stringResource(R.string.onboarding_send_code), fontWeight = FontWeight.Bold)
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
                                Text(stringResource(R.string.onboarding_verify_continue), fontWeight = FontWeight.Bold)
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
                        text = stringResource(R.string.onboarding_profile_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Slate900,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 6.dp)
                            .sectionHeading()
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedLabelColor = SignalBlue,
                            unfocusedLabelColor = Slate600,
                            focusedPlaceholderColor = Slate500,
                            unfocusedPlaceholderColor = Slate500,
                            cursorColor = SignalBlue
                        ),
                        placeholder = { Text(if (selectedRole == "WORKER") "Enter your legal name" else "Enter your legal name") },
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
                                text = "OPERATING DISTRICT (REQUIRED)",
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

                    // C3: phone is required for every account. The Google sign-in path
                    // bypasses the AUTHENTICATION phone-entry step, so collect it here
                    // before profile completion — this is collected input, not weakened
                    // validation, and the Complete button still requires it to be non-blank.
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = { newValue ->
                            if (newValue.length <= 15) phoneNumber = newValue
                        },
                        label = { Text("Mobile Money Phone Number") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedLabelColor = SignalBlue,
                            unfocusedLabelColor = Slate600,
                            focusedPlaceholderColor = Slate500,
                            unfocusedPlaceholderColor = Slate500,
                            cursorColor = SignalBlue
                        ),
                        placeholder = { Text("+256 7XX XXX XXX") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = Slate500)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        isError = phoneError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
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
                                .announcementLiveRegion()
                        )
                    }

                    // C3: completion is explicit — the coroutine persists the profile +
                    // session, and only then advances. The button stays disabled until
                    // a legal name AND a phone number are both present; the role the
                    // user picked on ROLE_SELECTION is preserved via selectedRole.
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
                        enabled = fullName.isNotBlank() && phoneNumber.isNotBlank() && selectedDistrict.isNotBlank(),
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
