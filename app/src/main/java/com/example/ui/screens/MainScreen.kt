package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.JobPostEntity
import com.example.data.db.NotificationEntity
import com.example.data.db.ServiceProviderEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.data.remote.DeletionStep
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.util.CurrencyRegion
import com.example.util.LocationHelper
import com.example.util.NetworkConnectivityObserver
import com.example.util.NotificationRouting
import com.example.util.NotificationTarget
import com.example.util.SystemNotificationHelper
import com.example.ui.components.escrowHeldTotal

@Composable
fun MainScreen(
    viewModel: SkillsExchangeViewModel,
    modifier: Modifier = Modifier
) {
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()

    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedJob by viewModel.selectedJob.collectAsStateWithLifecycle()

    val userRoleMode by viewModel.userRoleMode.collectAsStateWithLifecycle()
    // C2-2 Fix #3 (F7): the screen's own profile comes from the UID-keyed flow only. The legacy
    // newest-row `viewModel.currentUser` source has been removed from the ViewModel entirely, so
    // the drawer, worker gate, wallet, identity sheet and guarantor sheet can no longer render
    // another account's cached profile.
    val currentUserProfile by viewModel.currentUserByUid.collectAsStateWithLifecycle()
    val isWorkerApprovedAndActivated by viewModel.isWorkerApprovedAndActivated.collectAsStateWithLifecycle()
    val activeMobileTab by viewModel.activeMobileTab.collectAsStateWithLifecycle()
    val gpsState by viewModel.gpsLocationState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isNavigationDrawerOpen by viewModel.isNavigationDrawerOpen.collectAsStateWithLifecycle()
    val isWorkerOnline by viewModel.isWorkerOnline.collectAsStateWithLifecycle()

    // AUDIT FIX (H3/H7/M1): data backing the Account & Services drawer destinations.
    val myDisputes by viewModel.myDisputes.collectAsStateWithLifecycle()
    val myWorkerKyc by viewModel.myWorkerKyc.collectAsStateWithLifecycle()

    val isQuickRequestOpen by viewModel.isQuickRequestOpen.collectAsStateWithLifecycle()
    val isSearchModalOpen by viewModel.isSearchModalOpen.collectAsStateWithLifecycle()
    val isNotificationDrawerOpen by viewModel.isNotificationDrawerOpen.collectAsStateWithLifecycle()

    val mapsSearchResults by viewModel.mapsSearchResults.collectAsStateWithLifecycle()
    val isMapsSearching by viewModel.isMapsSearching.collectAsStateWithLifecycle()

    val audioTranscribedJob by viewModel.audioTranscribedJob.collectAsStateWithLifecycle()
    val isAudioRecording by viewModel.isAudioRecording.collectAsStateWithLifecycle()
    val isAudioTranscribing by viewModel.isAudioTranscribing.collectAsStateWithLifecycle()

    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()
    val currentUserPhone by viewModel.currentUserPhone.collectAsStateWithLifecycle()
    val isAuthModalOpen by viewModel.isAuthModalOpen.collectAsStateWithLifecycle()
    val authPendingAction by viewModel.authPendingAction.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val networkObserver = remember { NetworkConnectivityObserver(context) }
    val isOnline by networkObserver.observe().collectAsStateWithLifecycle(initialValue = true)

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            viewModel.startGpsLocating()
            LocationHelper.getCurrentLocation(
                context = context,
                onLocationReceived = { locResult ->
                    viewModel.onGpsLocationAcquired(
                        lat = locResult.latitude,
                        lng = locResult.longitude,
                        neighborhood = locResult.neighborhoodName,
                        accuracy = locResult.accuracy
                    )
                },
                onError = {
                    viewModel.requestGpsDetection("Sunnyside District")
                }
            )
        } else {
            viewModel.denyGpsPermission()
        }
    }

    val triggerLocationFlow: () -> Unit = {
        if (LocationHelper.hasLocationPermission(context)) {
            viewModel.startGpsLocating()
            LocationHelper.getCurrentLocation(
                context = context,
                onLocationReceived = { locResult ->
                    viewModel.onGpsLocationAcquired(
                        lat = locResult.latitude,
                        lng = locResult.longitude,
                        neighborhood = locResult.neighborhoodName,
                        accuracy = locResult.accuracy
                    )
                },
                onError = {
                    viewModel.requestGpsDetection("Sunnyside District")
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

    // Since "Use Current Location" is the default, trigger system prompt on startup if not already granted
    LaunchedEffect(Unit) {
        if (LocationHelper.hasLocationPermission(context)) {
            viewModel.startGpsLocating()
            LocationHelper.getCurrentLocation(
                context = context,
                onLocationReceived = { locResult ->
                    viewModel.onGpsLocationAcquired(
                        lat = locResult.latitude,
                        lng = locResult.longitude,
                        neighborhood = locResult.neighborhoodName,
                        accuracy = locResult.accuracy
                    )
                },
                onError = {
                    viewModel.requestGpsDetection("Sunnyside District")
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

    var isChatOpen by remember { mutableStateOf(false) }
    var isEscrowSheetOpen by remember { mutableStateOf(false) }
    var escrowJobTarget by remember { mutableStateOf<JobPostEntity?>(null) }
    // AUDIT-W-QUOTE-UI: worker quote proposal sheet state. Deliberately separate
    // from the escrow sheet: Submit Quote opens the quote form, never escrow.
    var isQuoteSheetOpen by remember { mutableStateOf(false) }
    var quoteJobTarget by remember { mutableStateOf<JobPostEntity?>(null) }
    var quoteSheetMessage by remember { mutableStateOf<String?>(null) }
    var isQuoteComparisonOpen by remember { mutableStateOf(false) }
    var quoteComparisonJob by remember { mutableStateOf<JobPostEntity?>(null) }

    var isDataBackupOpen by remember { mutableStateOf(false) }
    var isPrivacyPolicyOpen by remember { mutableStateOf(false) }
    var isAccountDeletionOpen by remember { mutableStateOf(false) }
    // R06-P2-03: linking Google sign-in to the current account (one person, one wallet).
    var googleLinkMessage by remember { mutableStateOf<String?>(null) }
    val linkGoogleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            com.google.android.gms.auth.api.signin.GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .addOnSuccessListener { account ->
                    val token = account.idToken
                    if (token.isNullOrBlank()) {
                        googleLinkMessage = "Google did not return a verifiable token. Please try again."
                    } else {
                        viewModel.linkGoogleAccount(token) { message -> googleLinkMessage = message }
                    }
                }
                .addOnFailureListener {
                    googleLinkMessage = "Google sign-in was cancelled or failed. Your account was not changed."
                }
        }
    }
    var isPermissionRationaleOpen by remember { mutableStateOf(false) }

    // AUDIT FIX (H3/H4/H7/M1): real destinations for the Account & Services drawer items.
    var isWalletSheetOpen by remember { mutableStateOf(false) }
    var isJobHistorySheetOpen by remember { mutableStateOf(false) }
    var isTrustSafetySheetOpen by remember { mutableStateOf(false) }
    var isIdentitySheetOpen by remember { mutableStateOf(false) }
    // REG-003 / REG-004: worker profile photo + backup contact sheet.
    var isProfileSettingsSheetOpen by remember { mutableStateOf(false) }
    // AUDIT-A-CONSOLE: first operator surface for the admin verification queue.
    var isAdminQueueOpen by remember { mutableStateOf(false) }
    // REM-023: no local unlock; the server decides whether this account is an operator.
    val isAdminUnlocked by viewModel.isAdminVerified.collectAsStateWithLifecycle()
    val adminQueue by viewModel.adminVerificationQueue.collectAsStateWithLifecycle()
    val isAdminQueueLoading by viewModel.isAdminQueueLoading.collectAsStateWithLifecycle()

    // AUDIT-A-ARBITRATE: admin dispute arbitration console.
    var isAdminArbitrationOpen by remember { mutableStateOf(false) }
    val adminDisputeQueue by viewModel.adminDisputeQueue.collectAsStateWithLifecycle()
    val isAdminDisputeLoading by viewModel.isAdminDisputeLoading.collectAsStateWithLifecycle()
    val arbitrationResult by viewModel.arbitrationResult.collectAsStateWithLifecycle()
    val selectedArbitrationDispute by viewModel.selectedDispute.collectAsStateWithLifecycle()

    val notifications by viewModel.notifications.collectAsStateWithLifecycle(initialValue = emptyList())
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        topBar = {
            TopHeaderBar(
                selectedDistrict = if (gpsState.isUsingCurrentLocation && gpsState.isPermissionGranted) {
                    "Current Location"
                } else if (gpsState.isUsingCurrentLocation) {
                    "Current Location"
                } else {
                    filterState.district
                },
                onDistrictSelect = { viewModel.switchToManualDistrict(it) },
                gpsState = gpsState,
                onRequestGps = triggerLocationFlow,
                onOpenSearch = { viewModel.setSearchModalOpen(true) },
                onOpenNotifications = { viewModel.setNotificationDrawerOpen(true) },
                onOpenFilters = { viewModel.setFilterDrawerOpen(true) },
                userRoleMode = userRoleMode,
                onToggleUserRole = { viewModel.toggleUserRoleMode() },
                activeFilterCount = if (filterState.category != null) 1 else 0,
                unreadNotificationCount = unreadNotificationCount,
                isAuthenticated = isAuthenticated,
                onTriggerAuth = { viewModel.triggerAuthFlow("Sign in to unlock neighbor contact") },
                onLogout = { viewModel.logoutUser() },
                onOpenNavigationDrawer = { viewModel.setNavigationDrawerOpen(true) },
                isWorkerOnline = isWorkerOnline,
                onToggleWorkerOnline = { viewModel.toggleWorkerOnline() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isAuthenticated) {
                        viewModel.setQuickRequestOpen(true)
                    } else {
                        viewModel.triggerAuthFlow("post_job")
                    }
                },
                containerColor = SignalBlue,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Post Job Request",
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        bottomBar = {
            // Mobile Segmented Bottom Navigation Control
            Surface(
                color = Slate900,
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate800)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mobileTabs = listOf(
                        Triple("List", Icons.Outlined.ListAlt, "Providers"),
                        Triple("Map", Icons.Outlined.Map, "Map View"),
                        Triple("Jobs", Icons.Outlined.WorkOutline, "Job Status")
                    )

                    mobileTabs.forEach { (tabId, icon, label) ->
                        val isSelected = activeMobileTab == tabId
                        IconButton(
                            onClick = { viewModel.setMobileTab(tabId) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) SignalBlue else Slate400,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Slate400,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Slate50)
        ) {
            // Real-Time Offline Network Indicator
            OfflineStatusBar(isOnline = isOnline)

            // Automatic GPS Geolocation Soft Banner / Pre-Prompt
            GpsLocationBanner(
                gpsState = gpsState,
                currentDistrict = if (gpsState.isUsingCurrentLocation && gpsState.isPermissionGranted) gpsState.detectedNeighborhood else filterState.district,
                onRequestGps = triggerLocationFlow,
                onDenyGps = { viewModel.denyGpsPermission() },
                onDismissBanner = { viewModel.dismissGpsBanner() },
                onManualDistrictSelect = { viewModel.switchToManualDistrict(it) }
            )

            // Filter Bar
            FilterBar(
                selectedCategory = filterState.category,
                onCategorySelect = { viewModel.setCategory(it) },
                selectedRadius = filterState.radiusMiles,
                onRadiusSelect = { viewModel.setRadius(it) },
                onlyVerified = filterState.onlyVerified,
                onToggleVerified = { viewModel.toggleOnlyVerified() },
                onOpenFilterDrawer = { viewModel.setFilterDrawerOpen(true) }
            )

            // Responsive Layout Box
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isWideScreen = maxWidth >= 600.dp

                if (userRoleMode == "Offer Skills") {
                    if (isWorkerApprovedAndActivated) {
                        // WORKER / FREELANCER MODE UI (ACTIVATED & APPROVED)
                        FreelancerDashboardView(
                            jobs = jobs,
                            isOnline = isWorkerOnline,
                            onToggleOnline = { viewModel.toggleWorkerOnline() },
                            // AUDIT-W-QUOTE-UI: Submit Quote opens the quote proposal
                            // sheet, NOT the escrow sheet. Escrow stays reachable
                            // from the Jobs pipeline (onOpenEscrowSheet below).
                            onSelectJob = { job ->
                                viewModel.selectJob(job)
                                quoteJobTarget = job
                                quoteSheetMessage = null
                                viewModel.resetQuoteSubmissionState()
                                isQuoteSheetOpen = true
                            },
                            onOpenQuickRequest = { viewModel.setQuickRequestOpen(true) },
                            isAuthenticated = isAuthenticated,
                            onTriggerAuth = { viewModel.triggerAuthFlow(it) },
                            isAuthorized = isWorkerApprovedAndActivated,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // ROLE-003: Worker Verification & Activation Gating View
                        val training by viewModel.currentTraining.collectAsStateWithLifecycle(initialValue = null)
                        val kycStatus = currentUserProfile?.workerKycStatus
                        val isTrainingDone = currentUserProfile?.trainingCompleted ?: false

                        if (kycStatus == "APPROVED" && !isTrainingDone) {
                            WorkerTrainingScreen(
                                training = training,
                                onStartTraining = { viewModel.startTraining() },
                                onCompleteModule = { modules, index -> viewModel.completeTrainingModule(modules, index) },
                                onCompleteTraining = { viewModel.completeTraining() },
                                onSwitchToClientMode = { viewModel.setUserRoleMode("Hire a Neighbor") },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            WorkerVerificationStatusView(
                                userProfile = currentUserProfile,
                                onStartVerification = {
                                    isIdentitySheetOpen = true
                                    viewModel.refreshGuarantorVerificationStatus()
                                },
                                onStartTraining = { viewModel.startTraining() },
                                onSwitchToClientMode = { viewModel.setUserRoleMode("Hire a Neighbor") },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else if (isWideScreen) {
                    // Desktop / Tablet Split View: Left list / pipeline / feed, Right map (only when doing provider discovery)
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .background(Slate50)
                        ) {
                            when (activeMobileTab) {
                                "Jobs" -> {
                            JobPipelineKanban(
                                jobs = jobs,
                                onSelectJob = { viewModel.selectJob(it) },
                                onAdvanceStatus = { jobId, newStatus -> viewModel.updateJobStatus(jobId, newStatus) },
                                onOpenChat = {
                                    viewModel.selectJob(it)
                                    isChatOpen = true
                                },
                                onOpenEscrowSheet = {
                                    escrowJobTarget = it
                                    isEscrowSheetOpen = true
                                },
                                onOpenQuoteComparison = { job ->
                                    quoteComparisonJob = job
                                    isQuoteComparisonOpen = true
                                },
                                onCancelTask = { job, reason -> viewModel.cancelJob(job.id, reason) }
                            )
                                }
                                else -> {
                                    ProviderListContent(
                                        providers = providers,
                                        onViewProfile = { viewModel.selectProvider(it) },
                                        onQuickRequest = { viewModel.selectProvider(it) },
                                        isAuthenticated = isAuthenticated,
                                        onTriggerAuth = { viewModel.triggerAuthFlow(it) }
                                    )
                                }
                            }
                        }

                        VerticalDivider(thickness = 1.dp, color = Slate300)

                        // MAP-001: Lazy Load Google Maps — Only load map for provider discovery ("List" or "Map" tab)
                        // Do not load map for "Jobs" (job pipeline) views
                        if (activeMobileTab == "List" || activeMobileTab == "Map") {
                            InteractiveMapView(
                                providers = providers,
                                selectedProvider = selectedProvider,
                                onSelectProvider = { viewModel.selectProvider(it) },
                                radiusMiles = filterState.radiusMiles,
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight()
                            )
                        } else {
                            // Placeholder for non-map tabs to maintain split layout structure
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight()
                                    .background(Slate50)
                            )
                        }
                    }
                } else {
                    // Mobile View: Displays content based on activeMobileTab
                    when (activeMobileTab) {
                        "Map" -> {
                            InteractiveMapView(
                                providers = providers,
                                selectedProvider = selectedProvider,
                                onSelectProvider = { viewModel.selectProvider(it) },
                                radiusMiles = filterState.radiusMiles,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "Jobs" -> {
                            JobPipelineKanban(
                                jobs = jobs,
                                onSelectJob = { viewModel.selectJob(it) },
                                onAdvanceStatus = { jobId, newStatus -> viewModel.updateJobStatus(jobId, newStatus) },
                                onOpenChat = {
                                    viewModel.selectJob(it)
                                    isChatOpen = true
                                },
                                onOpenEscrowSheet = {
                                    escrowJobTarget = it
                                    isEscrowSheetOpen = true
                                },
                                onCancelTask = { job, reason -> viewModel.cancelJob(job.id, reason) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            ProviderListContent(
                                providers = providers,
                                onViewProfile = { viewModel.selectProvider(it) },
                                onQuickRequest = { viewModel.selectProvider(it) },
                                isAuthenticated = isAuthenticated,
                                onTriggerAuth = { viewModel.triggerAuthFlow(it) },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Overlays & Drawers
    if (selectedProvider != null) {
        val endorsements by viewModel.getEndorsementsForProvider(selectedProvider!!.id).collectAsStateWithLifecycle(initialValue = emptyList())
        val districtJobStats = remember(selectedProvider, jobs) {
            selectedProvider?.let { viewModel.getDistrictJobStatsForProvider(it.id) } ?: emptyMap()
        }
        ProviderDetailSheet(
            provider = selectedProvider!!,
            endorsements = endorsements,
            districtJobStats = districtJobStats,
            onEndorseSkill = { skill -> viewModel.endorseSkill(selectedProvider!!.id, skill) },
            onRequestJob = { prov ->
                viewModel.setQuickRequestOpen(true)
            },
            onReportUser = { report -> viewModel.reportUser(report) },
            isAuthenticated = isAuthenticated,
                onTriggerAuth = { viewModel.triggerAuthFlow(it) },
            onDismiss = { viewModel.selectProvider(null) }
        )
    }


    if (isQuickRequestOpen) {
        QuickRequestDrawer(
            onPostJob = { title, category, description, budget, urgency, materialsProvidedBy, laborHours, laborRate, jobType, recurringSchedule, streetAddress, apartmentSuite, landmarkDirections, scheduledAt, photos ->
                viewModel.postQuickJob(
                    title = title,
                    category = category,
                    description = description,
                    budget = budget,
                    urgency = urgency,
                    materialsProvidedBy = materialsProvidedBy,
                    laborHours = laborHours,
                    laborRate = laborRate,
                    jobType = jobType,
                    recurringSchedule = recurringSchedule,
                    streetAddress = streetAddress,
                    apartmentSuite = apartmentSuite,
                    landmarkDirections = landmarkDirections,
                    scheduledAt = scheduledAt,
                    photos = photos
                )
            },
            onStartVoiceRecord = { viewModel.startVoiceRecording() },
            onStopVoiceRecordAndTranscribe = { viewModel.stopVoiceRecordingAndTranscribe() },
            transcribedVoiceJob = audioTranscribedJob,
            isAudioRecording = isAudioRecording,
            isAudioTranscribing = isAudioTranscribing,
            onDismiss = { viewModel.setQuickRequestOpen(false) }
        )
    }

    if (isEscrowSheetOpen && escrowJobTarget != null) {
        // Find latest updated job state from repository
        val liveJob = jobs.find { it.id == escrowJobTarget!!.id } ?: escrowJobTarget!!
        val disputeState by viewModel.disputeSubmissionState.collectAsStateWithLifecycle()
        val ratingState by viewModel.ratingSubmissionState.collectAsStateWithLifecycle()
        val sosClearState by viewModel.sosClearState.collectAsStateWithLifecycle()
        // PAY-002: provider-verified payment state published by the trusted backend.
        val momoPaymentState by viewModel.momoPaymentState.collectAsStateWithLifecycle()
        val escrowReleaseState by viewModel.escrowReleaseState.collectAsStateWithLifecycle()
        LaunchedEffect(isEscrowSheetOpen) {
            if (isEscrowSheetOpen) viewModel.resetEscrowReleaseState()
        }
        EscrowAndProofSheet(
            job = liveJob,
            onUpdateEscrowStatus = { newEscrowStatus ->
                viewModel.updateEscrowStatus(liveJob.id, newEscrowStatus)
            },
            onUpdatePhotos = { beforeUrl, afterUrl ->
                viewModel.updateJobPhotos(liveJob.id, beforeUrl, afterUrl)
            },
            onRequestEmergencyAddon = { amount, reason ->
                viewModel.requestEmergencyAddon(liveJob.id, amount, reason)
            },
            onRespondEmergencyAddon = { approve ->
                viewModel.respondToEmergencyAddon(liveJob.id, approve)
            },
            onUpdatePaymentChannel = { channel, phone ->
                viewModel.updatePaymentChannel(liveJob.id, channel, phone)
            },
            onInitiateMobileMoneyPayment = { phone ->
                // PAY-002: trusted backend collection; escrow locks only on the
                // backend's provider-verified SUCCESSFUL — never on this call.
                viewModel.initiateMoMoEscrowPayment(
                    jobId = liveJob.id,
                    amountUgx = liveJob.grandTotal,
                    phone = phone,
                    onIntentCreated = { /* state surfaced via momoPaymentState */ }
                )
            },
            mobileMoneyPaymentState = momoPaymentState,
            onReleaseFunds = { viewModel.releaseEscrow(liveJob.id) },
            isReleasingFunds = escrowReleaseState is SkillsExchangeViewModel.EscrowReleaseState.Releasing,
            releaseError = (escrowReleaseState as? SkillsExchangeViewModel.EscrowReleaseState.Failed)?.message,
            releaseMessage = (escrowReleaseState as? SkillsExchangeViewModel.EscrowReleaseState.Released)?.message,
            onVerifyDoorstepPin = { pin, callback ->
                viewModel.verifyDoorstepPin(liveJob.id, pin, callback)
            },
            // R07-P2-01: only the resident is shown the PIN; the worker must ask for it.
            viewerIsResident = currentUserProfile?.id == liveJob.postedByUid,
            onRequestMaterialAdvance = { amount ->
                viewModel.requestMaterialAdvance(liveJob.id, amount)
            },
            onRespondMaterialAdvance = { approve ->
                viewModel.respondToMaterialAdvance(liveJob.id, approve)
            },
            onTriggerSosAlert = {
                viewModel.triggerSosAlert(liveJob.id)
            },
            onClearSosAlert = {
                // H7/SAFE-003: STAND DOWN. The secured repository boundary decides owner-only
                // authorization and maps any rejection into sosClearState for the sheet.
                viewModel.clearSosAlert(liveJob.id)
            },
            onSubmitDispute = { reason, description ->
                // H7: never file a synthetic sentinel as the reported worker. A dispute
                // without an assigned provider has no counterparty on file — fail closed
                // to a visible error rather than writing "unknown_worker" as a
                // reportedUserId no admin queue can ever resolve.
                val workerId = liveJob.assignedProviderId
                if (workerId.isNullOrBlank()) {
                    viewModel.setDisputeError("No worker is assigned to this job yet")
                } else {
                    viewModel.submitDispute(
                        jobId = liveJob.id,
                        reportedUserId = workerId,
                        reason = reason,
                        description = description
                    )
                }
            },
            isSubmittingDispute = disputeState is SkillsExchangeViewModel.DisputeSubmissionState.Submitting,
            disputeSubmissionSuccess = disputeState is SkillsExchangeViewModel.DisputeSubmissionState.Success,
            disputeSubmissionError = (disputeState as? SkillsExchangeViewModel.DisputeSubmissionState.Error)?.message,
            onSubmitRating = { stars, review ->
                // H6: never submit a synthetic sentinel as the rated worker. The release-gated
                // "Rate Worker" button only appears when assignedProviderId != null (EscrowAndProofSheet),
                // so null here is unreachable; fail closed to a visible error rather than writing
                // "unknown_worker" as a toUserId the repository must then reject.
                val workerId = liveJob.assignedProviderId
                if (workerId.isNullOrBlank()) {
                    viewModel.setRatingError("No worker is assigned to this job yet")
                } else {
                    viewModel.submitRating(
                        jobId = liveJob.id,
                        toUserId = workerId,
                        rating = stars,
                        review = review,
                        isFromClient = true
                    )
                }
            },
            isSubmittingRating = ratingState is SkillsExchangeViewModel.RatingSubmissionState.Submitting,
            ratingSubmissionSuccess = ratingState is SkillsExchangeViewModel.RatingSubmissionState.Success,
            ratingSubmissionError = (ratingState as? SkillsExchangeViewModel.RatingSubmissionState.Error)?.message,
            isClearingSos = sosClearState is SkillsExchangeViewModel.SosClearState.Clearing,
            sosClearError = (sosClearState as? SkillsExchangeViewModel.SosClearState.Error)?.message,
            onDismiss = {
                isEscrowSheetOpen = false
                escrowJobTarget = null
                viewModel.resetDisputeSubmissionState()
                viewModel.resetRatingSubmissionState()
                viewModel.resetSosClearState()
                viewModel.clearMoMoPaymentState()
            }
        )
    }

    if (isQuoteSheetOpen && quoteJobTarget != null) {
        val liveQuoteTarget = jobs.find { it.id == quoteJobTarget!!.id } ?: quoteJobTarget!!
        val quoteResult by viewModel.quoteSubmissionState.collectAsStateWithLifecycle()
        val isSubmittingQuote by viewModel.isSubmittingQuote.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(quoteResult) {
            if (quoteResult is com.example.data.repository.SkillsExchangeRepository.QuoteSubmissionResult.Success) {
                quoteSheetMessage = "Quote submitted successfully."
                kotlinx.coroutines.delay(900)
                isQuoteSheetOpen = false
                quoteJobTarget = null
                quoteSheetMessage = null
                viewModel.resetQuoteSubmissionState()
            }
        }
        QuoteSubmissionSheet(
            isOpen = true,
            job = liveQuoteTarget,
            isSubmitting = isSubmittingQuote,
            submissionResult = quoteResult,
            submissionError = quoteSheetMessage?.takeIf {
                quoteResult is com.example.data.repository.SkillsExchangeRepository.QuoteSubmissionResult.Success
            },
            onSubmit = { rate, hours, notes ->
                quoteSheetMessage = null
                val displayName = currentUserProfile?.fullName?.trim().orEmpty()
                viewModel.submitQuote(liveQuoteTarget.id, displayName, rate, hours, notes)
            },
            onDismiss = {
                isQuoteSheetOpen = false
                quoteJobTarget = null
                quoteSheetMessage = null
                viewModel.resetQuoteSubmissionState()
            },
        )
    }

    if (isQuoteComparisonOpen && quoteComparisonJob != null) {
        val liveQuoteJob = jobs.find { it.id == quoteComparisonJob!!.id } ?: quoteComparisonJob!!
        val quotes by viewModel.getQuotesForJob(liveQuoteJob.id).collectAsStateWithLifecycle(initialValue = emptyList())
        QuoteComparisonSheet(
            job = liveQuoteJob,
            quotes = quotes,
            onAcceptQuote = { quoteId ->
                viewModel.respondToQuote(quoteId, liveQuoteJob.id, true)
            },
            onDismiss = {
                isQuoteComparisonOpen = false
                quoteComparisonJob = null
            }
        )
    }

    if (isChatOpen && selectedJob != null) {
        val chatMessages by viewModel.getChatMessagesForJob(selectedJob!!.id).collectAsStateWithLifecycle(initialValue = emptyList())
        ChatDrawer(
            job = selectedJob!!,
            messages = chatMessages,
            onSendMessage = { msg -> viewModel.sendChatMessage(selectedJob!!.id, msg) },
            onDismiss = { isChatOpen = false }
        )
    }

    if (isSearchModalOpen) {
        SearchModal(
            searchQuery = filterState.searchQuery,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            searchResults = providers,
            onSelectProvider = { viewModel.selectProvider(it) },
            onSearchGoogleMaps = { query -> viewModel.searchLocalPlacesWithGoogleMaps(query) },
            mapsResults = mapsSearchResults,
            isMapsSearching = isMapsSearching,
            onDismiss = { viewModel.setSearchModalOpen(false) }
        )
    }

    // AUDIT FIX (M5 / B6): a notification tap now navigates to the content it
    // refers to (resolved from its type + jobId) instead of only marking
    // everything read. Unresolvable targets fall back to mark-read-only.
    fun handleNotificationClick(notification: NotificationEntity) {
        viewModel.markAllNotificationsRead()
        viewModel.setNotificationDrawerOpen(false)
        val job = jobs.firstOrNull { it.id == notification.jobId }
        when (NotificationRouting.targetFor(notification.type)) {
            NotificationTarget.IDENTITY -> isIdentitySheetOpen = true
            NotificationTarget.WALLET -> isWalletSheetOpen = true
            NotificationTarget.TRUST_SAFETY -> isTrustSafetySheetOpen = true
            NotificationTarget.QUOTE_COMPARISON -> if (job != null) {
                quoteComparisonJob = job
                isQuoteComparisonOpen = true
            }
            NotificationTarget.NONE -> Unit
        }
    }

    if (isNotificationDrawerOpen) {
        NotificationDrawer(
            notifications = notifications,
            onDismiss = { viewModel.setNotificationDrawerOpen(false) },
            onRequestPermissionDialog = { isPermissionRationaleOpen = true },
            onNotificationClick = { notification ->
                handleNotificationClick(notification)
            }
        )
    }

    AppNavigationDrawer(
        isOpen = isNavigationDrawerOpen,
        onDismiss = { viewModel.setNavigationDrawerOpen(false) },
        userRoleMode = userRoleMode,
        onToggleUserRole = { viewModel.toggleUserRoleMode() },
        themeMode = themeMode,
        onSetThemeMode = { viewModel.setThemeMode(it) },
        isAuthenticated = isAuthenticated,
        currentUserPhone = currentUserPhone,
        currentDistrict = filterState.district,
        onTriggerAuth = { viewModel.triggerAuthFlow("Unlock account & verified credentials") },
        onNavigateToOnboarding = { viewModel.navigateToOnboarding() },
        onLogout = { viewModel.logoutUser() },
        currentUserProfile = currentUserProfile,
        // AUDIT FIX (H3/H4/H7/M1): each drawer item now opens its real destination sheet
        // instead of silently switching to the Jobs tab or doing nothing.
        onOpenWallet = { isWalletSheetOpen = true },
        onOpenProfileSettings = { isProfileSettingsSheetOpen = true },
        onOpenHistory = { isJobHistorySheetOpen = true },
        onOpenDisputes = { isTrustSafetySheetOpen = true },
        // AUDIT FIX (H4): feed the drawer the real escrow total instead of the "—"
        // placeholder; computed from the same live job records as EscrowWalletSheet.
        escrowHeldTotalLabel = CurrencyRegion.format(escrowHeldTotal(jobs)),
        // AUDIT FIX (H3/G5): feed the drawer the real KYC state instead of the
        // "Not verified" default, so the badge can never claim a false "Verified".
        isKycVerified = myWorkerKyc?.status == "APPROVED",
        kycStatusLabel = myWorkerKyc?.status?.replace('_', ' ') ?: "Not verified",
        onOpenLinkGoogle = {
            // R06-P2-06: no client id in this build → explain, do not launch an empty flow.
            val client = com.example.util.GoogleSignInHelper.getClient(context)
            if (client == null) {
                googleLinkMessage = com.example.util.GoogleSignInHelper.NOT_CONFIGURED_MESSAGE
            } else {
                linkGoogleLauncher.launch(client.signInIntent)
            }
        },
        onOpenIdentity = {
            isIdentitySheetOpen = true
            viewModel.refreshGuarantorVerificationStatus()
        },
        // AUDIT-A-CONSOLE: operator queue entry (locked by default; backend claim authorizes).
        isAdminUnlocked = isAdminUnlocked,
        adminPendingCount = adminQueue.size,
        onOpenAdminQueue = { isAdminQueueOpen = true },
        // AUDIT-A-ARBITRATE: dispute arbitration console entry.
        onOpenAdminArbitration = { isAdminArbitrationOpen = true },
        onOpenDataBackup = { isDataBackupOpen = true },
        onOpenPrivacyPolicy = { isPrivacyPolicyOpen = true },
        onOpenDeleteAccount = {
            viewModel.resetAccountDeletionState()
            isAccountDeletionOpen = true
        }
    )

    // AUDIT FIX (H4): MoMo & Escrow Wallet — real escrow figures from the job records.
    EscrowWalletSheet(
        isOpen = isWalletSheetOpen,
        jobs = jobs,
        userProfile = currentUserProfile,
        onDismiss = { isWalletSheetOpen = false }
    )

    // AUDIT-A-CONSOLE: first operator surface — renders the existing admin queue flow.
    // Locked by default; the backend `admin` custom claim remains the authorizer and the
    // callable returns no rows without it. Long-press-free explicit unlock lives here so a
    // field operator can enable it on a provisioned device without a client-side role flag.
    AdminVerificationQueueSheet(
        isOpen = isAdminQueueOpen,
        queue = adminQueue,
        isLoading = isAdminQueueLoading,
        isAdminUnlocked = isAdminUnlocked,
        onUnlockOperator = { viewModel.verifyAdminAccess() },
        onRefresh = { viewModel.loadAdminVerificationQueue() },
        onSubmitDecision = { userId, decision, feedback, onResult ->
            viewModel.submitAdminDecision(userId, decision, feedback, onResult)
        },
        onDismiss = { isAdminQueueOpen = false }
    )

    // AUDIT-A-ARBITRATE: admin dispute arbitration console.
    AdminDisputeArbitrationSheet(
        isOpen = isAdminArbitrationOpen,
        disputes = adminDisputeQueue,
        isLoading = isAdminDisputeLoading,
        arbitrationResult = arbitrationResult,
        selectedDispute = selectedArbitrationDispute,
        onRefresh = { viewModel.loadDisputesQueue() },
        onSelectDispute = { viewModel.selectDispute(it) },
        onClearSelection = { viewModel.clearSelectedDispute() },
        onSubmitResolution = { disputeId: String,
            resolution: String,
            resolutionType: String?,
            resolutionAmount: Double?,
            adminNotes: String?,
            status: String? ->
            viewModel.submitDisputeResolution(
                disputeId = disputeId,
                resolution = resolution,
                resolutionType = resolutionType,
                resolutionAmount = resolutionAmount,
                adminNotes = adminNotes,
                status = status,
                onResult = { result ->
                    when (result) {
                        is SkillsExchangeViewModel.ArbitrationResult.Success -> { /* success already reflected in UI state */ }
                        is SkillsExchangeViewModel.ArbitrationResult.Error -> {
                            // Error already surfaced via arbitrationResult state
                        }
                        is SkillsExchangeViewModel.ArbitrationResult.NotAuthenticated -> {
                            // Should not happen — callable handles auth server-side
                        }
                        is SkillsExchangeViewModel.ArbitrationResult.NotAuthorized -> {
                            // Should not happen — callable handles authorization server-side
                        }
                    }
                }
            )
        },
        onDismiss = { isAdminArbitrationOpen = false }
    )

    // REG-003 / REG-004: profile photo upload & backup contact management.
    ProfileSettingsSheet(
        isOpen = isProfileSettingsSheetOpen,
        userProfile = currentUserProfile,
        viewModel = viewModel,
        onDismiss = { isProfileSettingsSheetOpen = false }
    )

    // AUDIT FIX (M1): Job History & Material Receipts — completed contracts and receipts.
    JobHistorySheet(
        isOpen = isJobHistorySheetOpen,
        jobs = jobs,
        onDismiss = { isJobHistorySheetOpen = false }
    )

    // AUDIT FIX (H7): Trust, Safety & Emergency SOS — real SOS flags and dispute records.
    TrustSafetySheet(
        isOpen = isTrustSafetySheetOpen,
        jobs = jobs,
        disputes = myDisputes,
        onDismiss = { isTrustSafetySheetOpen = false }
    )

    // AUDIT FIX (H3): National ID & Verification — real KYC state, no unconditional "Verified".
    IdentityVerificationSheet(
        isOpen = isIdentitySheetOpen,
        userProfile = currentUserProfile,
        workerKyc = myWorkerKyc,
        onManageGuarantors = {
            viewModel.openGuarantorVerification(myWorkerKyc?.groupPhotoStoragePath.orEmpty())
        },
        onDismiss = { isIdentitySheetOpen = false }
    )

    // REG-002: two guarantors + group photograph, filed for admin review.
    if (viewModel.guarantorVerificationOpen) {
        GuarantorVerificationSheet(
            workerId = currentUserProfile?.id.orEmpty(),
            initialGuarantors = viewModel.guarantorDrafts,
            initialGroupPhotoSource = viewModel.guarantorGroupPhotoSource,
            isSubmitting = viewModel.guarantorSubmissionPending,
            submissionError = viewModel.guarantorSubmissionError,
            submissionMessage = viewModel.guarantorSubmissionMessage,
            onChange = viewModel::onGuarantorDraftsChanged,
            onSaveDraft = viewModel::saveGuarantorDraft,
            onSubmit = viewModel::submitGuarantorVerification,
            onDismiss = viewModel::closeGuarantorVerification
        )
    }

    // Critical Production Dialogs & Sheets
    PermissionRationaleDialog(
        isOpen = isPermissionRationaleOpen,
        onDismiss = { isPermissionRationaleOpen = false },
        onPermissionGranted = {
            // Notifications active
        }
    )

    DataBackupDialog(
        isOpen = isDataBackupOpen,
        onDismiss = { isDataBackupOpen = false },
        providers = providers,
        jobs = jobs
    )

    PrivacyAndTermsSheet(
        isOpen = isPrivacyPolicyOpen,
        onDismiss = { isPrivacyPolicyOpen = false }
    )

    // R06-P2-01: the dialog is driven by the deletion state, so it cannot report success (or close)
    // before the server has confirmed that every record and photo is gone.
    val deletionStep by viewModel.accountDeletionStep.collectAsStateWithLifecycle()
    val deletionMessage by viewModel.accountDeletionMessage.collectAsStateWithLifecycle()
    val deletionCodeSent by viewModel.accountDeletionCodeSent.collectAsStateWithLifecycle()
    val accountPhone by viewModel.currentUserPhone.collectAsStateWithLifecycle()
    AccountDeletionDialog(
        isOpen = isAccountDeletionOpen,
        step = deletionStep,
        message = deletionMessage,
        codeSent = deletionCodeSent,
        accountPhone = accountPhone,
        onDismiss = {
            if (deletionStep != DeletionStep.PURGING) {
                isAccountDeletionOpen = false
                viewModel.resetAccountDeletionState()
            }
        },
        onConfirmDeleteAccount = {
            // R06-P2-01: no more `onDismiss()` here. The dialog stays and shows what happened.
            viewModel.permanentlyDeleteUserAccountAndData()
        },
        onReauthWithGoogle = { idToken -> viewModel.reauthenticateForAccountDeletionWithGoogle(idToken) },
        onStartPhoneReauth = { phone -> viewModel.startAccountDeletionPhoneReauth(phone) },
        onConfirmPhoneReauth = { code -> viewModel.confirmAccountDeletionPhoneReauth(code) },
        onReset = { viewModel.resetAccountDeletionState() }
    )

    // R06-P2-03: the link outcome is always shown — a collision is never reported as success.
    if (googleLinkMessage != null) {
        AlertDialog(
            onDismissRequest = { googleLinkMessage = null },
            title = { Text("Google Sign-In", fontWeight = FontWeight.Bold) },
            text = { Text(googleLinkMessage!!, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { googleLinkMessage = null }) { Text("OK") }
            }
        )
    }

    if (isAuthModalOpen) {
        val authState by viewModel.authState.collectAsStateWithLifecycle()
        val authError by viewModel.authError.collectAsStateWithLifecycle()
        val phoneError by viewModel.phoneError.collectAsStateWithLifecycle()
        val otpError by viewModel.otpError.collectAsStateWithLifecycle()
        AuthModal(
            pendingActionReason = authPendingAction,
            initialDistrict = filterState.district,
            onLoginSuccess = { phone, name, district ->
                viewModel.loginUser(
                    phone = phone,
                    fullName = name,
                    district = district,
                    initialRole = if (userRoleMode == "Offer Skills") "WORKER" else "CLIENT"
                )
            },
            onDismiss = { viewModel.closeAuthModal() },
            onSendOtp = { phone, callback -> viewModel.sendOtp(phone) { success, error -> callback(success, error) } },
            onVerifyOtp = { otp, callback -> viewModel.verifyOtp(otp) { success, error -> callback(success, error) } },
            onGoogleSignIn = { token, onComplete ->
                viewModel.loginWithGoogle(
                    idToken = token,
                    initialRole = if (userRoleMode == "Offer Skills") "WORKER" else "CLIENT",
                    onComplete = onComplete
                )
            },
            isSendingOtp = authState is SkillsExchangeViewModel.AuthState.SendingOtp,
            isVerifyingOtp = authState is SkillsExchangeViewModel.AuthState.VerifyingOtp,
            authError = authError,
            phoneError = phoneError,
            otpError = otpError,
            onRetry = { viewModel.retryAuth() }
        )
    }
}

@Composable
fun ProviderListContent(
    providers: List<ServiceProviderEntity>,
    onViewProfile: (ServiceProviderEntity) -> Unit,
    onQuickRequest: (ServiceProviderEntity) -> Unit,
    isAuthenticated: Boolean = false,
    onTriggerAuth: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "NEARBY VERIFIED SPECIALISTS (${providers.size})",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = Slate500
            )
        }

        if (providers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = "No providers",
                        tint = Slate400,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "No verified providers match current filters",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate600,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(providers, key = { it.id }) { provider ->
                    ProviderCard(
                        provider = provider,
                        onViewProfile = { onViewProfile(provider) },
                        onQuickRequest = { onQuickRequest(provider) },
                        isAuthenticated = isAuthenticated,
                        onTriggerAuth = onTriggerAuth
                    )
                }
            }
        }
    }
}
