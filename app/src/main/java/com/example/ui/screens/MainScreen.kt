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
import androidx.compose.material.icons.outlined.DynamicFeed
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
import com.example.data.db.ServiceProviderEntity
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.util.LocationHelper
import com.example.util.NetworkConnectivityObserver
import com.example.util.SystemNotificationHelper

@Composable
fun MainScreen(
    viewModel: SkillsExchangeViewModel,
    modifier: Modifier = Modifier
) {
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val jobs by viewModel.jobs.collectAsStateWithLifecycle()
    val neighborhoodPosts by viewModel.neighborhoodPosts.collectAsStateWithLifecycle()

    val selectedProvider by viewModel.selectedProvider.collectAsStateWithLifecycle()
    val selectedJob by viewModel.selectedJob.collectAsStateWithLifecycle()

    val userRoleMode by viewModel.userRoleMode.collectAsStateWithLifecycle()
    val currentUserProfile by viewModel.currentUser.collectAsStateWithLifecycle()
    val isWorkerApprovedAndActivated by viewModel.isWorkerApprovedAndActivated.collectAsStateWithLifecycle()
    val activeMobileTab by viewModel.activeMobileTab.collectAsStateWithLifecycle()
    val gpsState by viewModel.gpsLocationState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isNavigationDrawerOpen by viewModel.isNavigationDrawerOpen.collectAsStateWithLifecycle()
    val isWorkerOnline by viewModel.isWorkerOnline.collectAsStateWithLifecycle()

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
    var isQuoteComparisonOpen by remember { mutableStateOf(false) }
    var quoteComparisonJob by remember { mutableStateOf<JobPostEntity?>(null) }

    var isDataBackupOpen by remember { mutableStateOf(false) }
    var isPrivacyPolicyOpen by remember { mutableStateOf(false) }
    var isAccountDeletionOpen by remember { mutableStateOf(false) }
    var isPermissionRationaleOpen by remember { mutableStateOf(false) }

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
                        Triple("Jobs", Icons.Outlined.WorkOutline, "Job Status"),
                        Triple("Board", Icons.Outlined.DynamicFeed, "Community")
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
                            onSelectJob = { job ->
                                viewModel.selectJob(job)
                                escrowJobTarget = job
                                isEscrowSheetOpen = true
                            },
                            onOpenQuickRequest = { viewModel.setQuickRequestOpen(true) },
                            isAuthenticated = isAuthenticated,
                            onTriggerAuth = { viewModel.triggerAuthFlow(it) },
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
                                onStartVerification = { viewModel.setQuickRequestOpen(true) },
                                onStartTraining = { viewModel.startTraining() },
                                onSwitchToClientMode = { viewModel.setUserRoleMode("Hire a Neighbor") },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else if (isWideScreen) {
                    // Desktop / Tablet Split View: Left list / pipeline / feed, Right map
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
                                }
                            )
                                }
                                "Board" -> {
                                    NeighborhoodFeedView(
                                        posts = neighborhoodPosts,
                                        onHireProvider = { provName ->
                                            val p = providers.find { it.name.equals(provName, ignoreCase = true) }
                                            if (p != null) viewModel.selectProvider(p)
                                        }
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

                        // Map View
                        InteractiveMapView(
                            providers = providers,
                            selectedProvider = selectedProvider,
                            onSelectProvider = { viewModel.selectProvider(it) },
                            radiusMiles = filterState.radiusMiles,
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        )
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
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        "Board" -> {
                            NeighborhoodFeedView(
                                posts = neighborhoodPosts,
                                onHireProvider = { provName ->
                                    val p = providers.find { it.name.equals(provName, ignoreCase = true) }
                                    if (p != null) viewModel.selectProvider(p)
                                },
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

    if (isAuthModalOpen) {
        val authState by viewModel.authState.collectAsStateWithLifecycle()
        val authError by viewModel.authError.collectAsStateWithLifecycle()
        AuthModal(
            pendingActionReason = authPendingAction,
            onLoginSuccess = { phone, name, district -> viewModel.loginUser(phone, name, district) },
            onDismiss = { viewModel.closeAuthModal() },
            onSendOtp = { phone, callback -> viewModel.sendOtp(phone) { success, error -> callback(success, error) } },
            onVerifyOtp = { otp, callback -> viewModel.verifyOtp(otp) { success, error -> callback(success, error) } },
            onGoogleSignIn = { token -> viewModel.loginWithGoogle(token) },
            isSendingOtp = authState is SkillsExchangeViewModel.AuthState.SendingOtp,
            isVerifyingOtp = authState is SkillsExchangeViewModel.AuthState.VerifyingOtp,
            authError = authError
        )
    }

    if (isQuickRequestOpen) {
        QuickRequestDrawer(
            onPostJob = { title, category, description, budget, urgency, materialsProvidedBy, laborHours, laborRate, jobType, recurringSchedule ->
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
                    recurringSchedule = recurringSchedule
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
            onVerifyDoorstepPin = { pin, expected, callback ->
                viewModel.verifyDoorstepPin(liveJob.id, pin, expected, callback)
            },
            onRequestMaterialAdvance = { amount ->
                viewModel.requestMaterialAdvance(liveJob.id, amount)
            },
            onRespondMaterialAdvance = { approve ->
                viewModel.respondToMaterialAdvance(liveJob.id, approve)
            },
            onTriggerSosAlert = {
                viewModel.triggerSosAlert(liveJob.id)
            },
            onSubmitDispute = { reason, description ->
                val workerId = liveJob.assignedProviderId ?: "unknown_worker"
                viewModel.submitDispute(
                    jobId = liveJob.id,
                    reportedUserId = workerId,
                    reason = reason,
                    description = description
                )
            },
            isSubmittingDispute = disputeState is SkillsExchangeViewModel.DisputeSubmissionState.Submitting,
            disputeSubmissionSuccess = disputeState is SkillsExchangeViewModel.DisputeSubmissionState.Success,
            onDismiss = {
                isEscrowSheetOpen = false
                escrowJobTarget = null
                viewModel.resetDisputeSubmissionState()
            }
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

    if (isNotificationDrawerOpen) {
        NotificationDrawer(
            notifications = notifications,
            onDismiss = { viewModel.setNotificationDrawerOpen(false) },
            onRequestPermissionDialog = { isPermissionRationaleOpen = true },
            onNotificationClick = { notification ->
                viewModel.markAllNotificationsRead()
                viewModel.setNotificationDrawerOpen(false)
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
        onLogout = { viewModel.logoutUser() },
        onOpenWallet = { viewModel.setMobileTab("Jobs") },
        onOpenHistory = { viewModel.setMobileTab("Jobs") },
        onOpenDisputes = {
            val activeJ = jobs.firstOrNull { it.status == "DISPUTED" || it.status == "IN_PROGRESS" }
            if (activeJ != null) {
                escrowJobTarget = activeJ
                isEscrowSheetOpen = true
            } else {
                viewModel.setMobileTab("Jobs")
            }
        },
        onOpenDataBackup = { isDataBackupOpen = true },
        onOpenPrivacyPolicy = { isPrivacyPolicyOpen = true },
        onOpenDeleteAccount = { isAccountDeletionOpen = true }
    )

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

    AccountDeletionDialog(
        isOpen = isAccountDeletionOpen,
        onDismiss = { isAccountDeletionOpen = false },
        onConfirmDeleteAccount = {
            viewModel.permanentlyDeleteUserAccountAndData()
        }
    )

    if (isAuthModalOpen) {
        val authState by viewModel.authState.collectAsStateWithLifecycle()
        val authError by viewModel.authError.collectAsStateWithLifecycle()
        val phoneError by viewModel.phoneError.collectAsStateWithLifecycle()
        val otpError by viewModel.otpError.collectAsStateWithLifecycle()
        AuthModal(
            pendingActionReason = authPendingAction,
            initialDistrict = filterState.district,
            onLoginSuccess = { phone, name, district ->
                viewModel.loginUser(phone, name, district)
            },
            onDismiss = { viewModel.closeAuthModal() },
            onSendOtp = { phone, callback -> viewModel.sendOtp(phone) { success, error -> callback(success, error) } },
            onVerifyOtp = { otp, callback -> viewModel.verifyOtp(otp) { success, error -> callback(success, error) } },
            onGoogleSignIn = { token -> viewModel.loginWithGoogle(token) },
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
