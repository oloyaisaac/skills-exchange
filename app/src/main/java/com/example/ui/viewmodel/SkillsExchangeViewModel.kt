package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.DisputeEntity
import com.example.data.db.DisputeReason
import com.example.data.db.DisputeStatus
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.SkillEndorsementEntity
import com.example.data.db.UserProfileEntity
import com.example.data.remote.FirebaseService
import com.example.data.remote.GeminiAudioTranscriptionService
import com.example.data.remote.GeminiMapsGroundingService
import com.example.data.remote.GroundedPlaceResult
import com.example.data.remote.MoMoPaymentIntent
import com.example.data.remote.MoMoPaymentService
import com.example.data.remote.SmsNotificationService
import com.example.data.remote.TranscribedVoiceJob
import com.example.data.repository.SkillsExchangeRepository
import com.example.util.AudioRecorderHelper
import com.example.util.CurrencyRegion
import com.example.util.ProfileValidators
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FilterState(
    val district: String = "Current Location",
    val radiusMiles: Double = 5.0,
    val category: String? = null,
    val minRate: Double = 20.0,
    val maxRate: Double = 120.0,
    val onlyVerified: Boolean = false,
    val urgency: String? = null,
    val searchQuery: String = ""
)

data class GpsLocationState(
    val isLocating: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val isBannerDismissed: Boolean = false,
    val isUsingCurrentLocation: Boolean = true,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val detectedNeighborhood: String = "Current Location",
    val accuracyMeters: Float = 12f,
    val errorMessage: String? = null
)

class SkillsExchangeViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext: Application = application
    private val db = AppDatabase.getDatabase(application)
    // FirebaseService is given the application context so the Firestore identity mirror
    // (AUTH-002/AUTH-006) and FirebaseApp initialization are always available.
    private val repository = SkillsExchangeRepository(db, FirebaseService(application))
    private val sessionManager = com.example.data.local.SessionManager(application)

    // Remote Services for Firebase, Maps AI Grounding, Audio AI, MoMo, and SMS
    val firebaseService = FirebaseService(application)
    val geminiMapsService = GeminiMapsGroundingService()
    val geminiAudioService = GeminiAudioTranscriptionService()
    val audioRecorderHelper = AudioRecorderHelper(application)
    val moMoPaymentService = MoMoPaymentService()
    val smsNotificationService = SmsNotificationService()
    private val networkObserver = com.example.util.NetworkConnectivityObserver(application)

    // State flows for remote features
    val firebaseUser: StateFlow<FirebaseUser?> = firebaseService.currentUserState

    /**
     * C2-2 Fix #1: UID-keyed authoritative current user. When Firebase reports a signed-in
     * UID, ONLY the Room row whose id matches that UID is exposed — never the newest row,
     * never `user_default_me`, never a placeholder. When Firebase has no signed-in user the
     * flow emits null (fail closed). The legacy newest-row `repository.currentUser` is
     * intentionally NOT consulted here; it remains only for pre-existing display consumers
     * until the F7 call-site sweep.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentUserByUid: StateFlow<UserProfileEntity?> = firebaseUser
        .flatMapLatest { user ->
            repository.getCurrentUserByUid(user?.uid)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val mapsSearchResults = MutableStateFlow<Pair<String, List<GroundedPlaceResult>>?>(null)
    val isMapsSearching = MutableStateFlow(false)

    val audioTranscribedJob = MutableStateFlow<TranscribedVoiceJob?>(null)
    val isAudioRecording = MutableStateFlow(false)
    val isAudioTranscribing = MutableStateFlow(false)

    // --- REG-003 / REG-004: worker backup contact & profile photo state ---
    val pendingProfilePhotoUri = MutableStateFlow<Uri?>(null)
    val isUploadingProfilePhoto = MutableStateFlow(false)
    val profilePhotoError = MutableStateFlow<String?>(null)
    val backupContactError = MutableStateFlow<String?>(null)
    val profileSettingsSuccess = MutableStateFlow<String?>(null)

    val filterState = MutableStateFlow(FilterState())
    val gpsLocationState = MutableStateFlow(GpsLocationState())

    val userRoleMode = MutableStateFlow("Hire a Neighbor") // "Hire a Neighbor" vs "Offer Skills"
    val activeMobileTab = MutableStateFlow("List") // "List", "Map", "Jobs"
    val themeMode = MutableStateFlow("LIGHT") // "LIGHT", "DARK", "SYSTEM"
    val isNavigationDrawerOpen = MutableStateFlow(false)
    val isWorkerOnline = MutableStateFlow(true)

    // Progressive Authentication & Gated Credential state
    val isAuthenticated = MutableStateFlow(false)
    val currentUserPhone = MutableStateFlow<String?>(null)
    val isAuthModalOpen = MutableStateFlow(false)
    val authPendingAction = MutableStateFlow<String?>(null)
    val isOnboardingCompleted = MutableStateFlow(false)
    val isSessionRestored = MutableStateFlow(false)

    val selectedProvider = MutableStateFlow<ServiceProviderEntity?>(null)
    val selectedJob = MutableStateFlow<JobPostEntity?>(null)

    val isQuickRequestOpen = MutableStateFlow(false)
    val isFilterDrawerOpen = MutableStateFlow(false)
    val isSearchModalOpen = MutableStateFlow(false)
    val isNotificationDrawerOpen = MutableStateFlow(false)
    // Pending actions queue for offline actions
    val pendingActions = MutableStateFlow<List<com.example.data.db.PendingActionEntity>>(emptyList())

    val currentUser: StateFlow<UserProfileEntity?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isWorkerApprovedAndActivated: StateFlow<Boolean> = combine(
        isAuthenticated,
        repository.currentUser
    ) { authenticated, user ->
        isWorkerAuthorized(authenticated, user)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false) // fail closed until real profile is emitted

    /**
     * DISP-001: The signed-in user's own disputes (as reporter or reported party).
     *
     * Backs the Trust, Safety & Emergency SOS destination, which previously had no data
     * source at all and could only open the escrow sheet of an unrelated job.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myDisputes: StateFlow<List<DisputeEntity>> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else repository.getDisputesForUser(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The worker's own KYC submission, used by the identity-verification destination. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myWorkerKyc: StateFlow<com.example.data.db.WorkerKycEntity?> = currentUser
        .flatMapLatest { user ->
            if (user == null) flowOf(null) else repository.getWorkerKyc(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
            // AUTH-002: Restore session from DataStore
            restoreSession()
            // C2-2 Fix #2 (F2): sync the initial mode from the AUTHENTICATED identity only —
            // Firebase UID → exact Room row. Re-deriving it from the newest Room row would undo
            // the UID-keyed restore performed above.
            repository.getCurrentUserByUid(firebaseService.getCurrentUser()?.uid).first()
                ?.let { profile ->
                    userRoleMode.value =
                        if (profile.currentActiveMode == "WORKER") "Offer Skills" else "Hire a Neighbor"
                }
        }
        viewModelScope.launch {
            repository.currentTraining.collect { training ->
                currentTraining.value = training
            }
        }
        viewModelScope.launch {
            repository.getPendingActions().collect { actions ->
                pendingActions.value = actions
            }
        }
        viewModelScope.launch {
            networkObserver.observe().collect { isConnected ->
                if (isConnected && pendingActions.value.isNotEmpty()) {
                    flushPendingActions()
                }
            }
        }
    }

    private suspend fun restoreSession() {
        try {
            val authenticated = sessionManager.getIsAuthenticated()
            val onboardingDone = sessionManager.getIsOnboardingCompleted()
            if (authenticated) {
                val phone = sessionManager.getUserPhone()
                val cachedUserId = sessionManager.getUserId()
                val sessionTimestamp = sessionManager.getSessionTimestamp()
                val savedDeviceId = sessionManager.getDeviceId()
                val currentDeviceId = sessionManager.getDeviceId(appContext)

                // AUTH-008: Validate session expiration (7 days)
                val sessionAge = System.currentTimeMillis() - sessionTimestamp
                if (sessionAge > 7 * 24 * 60 * 60 * 1000L) {
                    sessionManager.clearSession()
                    isAuthenticated.value = false
                    authState.value = AuthState.SignedOut
                    isOnboardingCompleted.value = onboardingDone
                    isSessionRestored.value = true
                    return
                }

                // AUTH-008: Validate device ID
                if (savedDeviceId != null && savedDeviceId != currentDeviceId) {
                    sessionManager.clearSession()
                    isAuthenticated.value = false
                    authState.value = AuthState.SignedOut
                    isOnboardingCompleted.value = onboardingDone
                    isSessionRestored.value = true
                    return
                }

                // C2-2 Fix #2 (F2): the cached DataStore identity is NEVER trusted on its own.
                // FirebaseAuth is the authority: the cached UID must MATCH the signed-in Firebase
                // UID, and when Firebase has no authenticated user the cached session is discarded
                // instead of being resurrected from stale device state.
                val identity = decideRestoreIdentity(
                    cachedUid = cachedUserId,
                    firebaseUid = firebaseService.getCurrentUser()?.uid
                )
                val authoritativeUid = (identity as? RestoreIdentity.Authoritative)?.uid
                if (authoritativeUid == null) {
                    // No trustworthy Firebase identity: fail closed to signed-out/guest. A cached
                    // session alone is never enough to resurrect an authenticated session, and no
                    // placeholder, anonymous, "unknown" or synthetic id may stand in for it.
                    sessionManager.clearSession()
                    isAuthenticated.value = false
                    currentUserPhone.value = null
                    authState.value = AuthState.SignedOut
                    isOnboardingCompleted.value = onboardingDone
                    isSessionRestored.value = true
                    return
                }
                val cachedIdentityTrusted =
                    (identity as RestoreIdentity.Authoritative).cachedUidTrusted
                if (!cachedIdentityTrusted) {
                    // Stale cached UID (missing, blank, placeholder, or a DIFFERENT account than the
                    // one Firebase now reports): invalidate it before anything can read it, then
                    // rebuild strictly from the authenticated Firebase UID below.
                    sessionManager.clearSession()
                }

                // AUTH-009: Restore user profile and role mode — via the C2-2 Fix #1 UID-keyed
                // resolution only. The exact Room row whose id equals the authenticated Firebase UID
                // is the sole candidate; the newest row, a foreign row and the demo placeholder can
                // never satisfy it, so a missing row fails closed instead of borrowing another
                // account's identity.
                val restoredProfile = resolveRestoredProfile(
                    authenticatedUid = authoritativeUid,
                    row = repository.getCurrentUserByUid(authoritativeUid).first()
                )
                    // AUTH-006: legitimate UID-keyed recovery from the durable identity record.
                    ?: resolveRestoredProfile(
                        authenticatedUid = authoritativeUid,
                        row = repository.fetchUserIdentityFromServer(authoritativeUid)
                    )?.also { repository.saveUserProfile(it) }
                    // Nothing exists for THIS uid anywhere: start the identity fail-closed, keyed by
                    // the Firebase UID. Cached descriptive fields are reused only when they belong to
                    // this same account (matching cached UID); a stale cache belongs to somebody else
                    // and is dropped rather than attributed to the new identity.
                    ?: resolveAuthenticatedProfile(
                        authenticatedUid = authoritativeUid,
                        existing = null,
                        phone = if (cachedIdentityTrusted) phone.orEmpty() else "",
                        fullName = if (cachedIdentityTrusted) sessionManager.getUserName().orEmpty() else "",
                        district = if (cachedIdentityTrusted) sessionManager.getUserDistrict().orEmpty() else "",
                        initialRole = if (cachedIdentityTrusted) {
                            sessionManager.getUserRole()
                                ?: if (userRoleMode.value == "Offer Skills") "WORKER" else "CLIENT"
                        } else "CLIENT"
                    ).also { repository.saveUserProfile(it) }

                // C2-2 Fix #2 (F2): the restore is only "signed in" with the Firebase UID as the
                // identity; the values below all come from the UID-keyed profile.
                isAuthenticated.value = true
                // The phone exposed to the progressive-auth gates comes from the UID-keyed profile,
                // never from a cached value that may belong to another account.
                currentUserPhone.value = restoredProfile.phoneNumber.takeIf { it.isNotBlank() }
                authState.value = AuthState.SignedIn
                userRoleMode.value =
                    if (restoredProfile.currentActiveMode == "WORKER") "Offer Skills" else "Hire a Neighbor"

                if (!cachedIdentityTrusted) {
                    // The cached identity was stale: rewrite it with the AUTHORITATIVE Firebase UID
                    // (and the UID-keyed profile) so the old value cannot survive a restore. When the
                    // cache already matched, the stored session is left untouched — its 7-day
                    // AUTH-008 expiry is deliberately NOT extended by a mere relaunch.
                    sessionManager.saveSession(
                        isAuthenticated = true,
                        phone = restoredProfile.phoneNumber,
                        userId = authoritativeUid,
                        name = restoredProfile.fullName,
                        district = restoredProfile.district,
                        deviceId = currentDeviceId,
                        role = restoredProfile.currentActiveMode
                    )
                }

                // Restore FCM token if available.
                // NOTE: must be a terminating read. Collecting sessionManager.fcmToken here used to
                // suspend restoreSession() forever (DataStore flows never complete), so
                // isSessionRestored was never set to true and the app hung on the loading spinner
                // after every relaunch of an authenticated session.
                val restoredFcmToken = sessionManager.fcmToken.first()
                if (restoredFcmToken != null) fcmToken.value = restoredFcmToken
                isOnboardingCompleted.value = true
                sessionManager.setOnboardingCompleted(true)
            } else {
                isOnboardingCompleted.value = onboardingDone
                // C1: no local session, but Firebase may still hold a signed-in user
                // (Room wiped / app data cleared while the Firebase token survived).
                // Recover explicitly instead of stranding the user on onboarding.
                // NOTE: a bare `return` here is safe — the surrounding finally block
                // still sets isSessionRestored = true, so MainActivity leaves the
                // loading spinner.
                val firebaseUid = firebaseService.getCurrentUser()?.uid
                if (firebaseUid.isNullOrBlank()) return
                var recovered = repository.getUserProfile(firebaseUid).first()
                    ?.takeIf { it.id != DEMO_PROFILE_ID }
                if (recovered == null) {
                    recovered = repository.fetchUserIdentityFromServer(firebaseUid)
                        ?.takeIf { it.id != DEMO_PROFILE_ID }
                    if (recovered != null) {
                        repository.saveUserProfile(recovered)
                    }
                }
                val completedProfile = recovered
                if (completedProfile?.isOnboardingCompleted == true) {
                    val deviceId = sessionManager.getDeviceId(appContext)
                    sessionManager.saveSession(
                        isAuthenticated = true,
                        phone = completedProfile.phoneNumber,
                        userId = completedProfile.id,
                        name = completedProfile.fullName,
                        district = completedProfile.district,
                        deviceId = deviceId,
                        role = completedProfile.currentActiveMode
                    )
                    sessionManager.setOnboardingCompleted(true)
                    isAuthenticated.value = true
                    currentUserPhone.value = completedProfile.phoneNumber
                    authState.value = AuthState.SignedIn
                    userRoleMode.value =
                        if (completedProfile.currentActiveMode == "WORKER") "Offer Skills" else "Hire a Neighbor"
                    isOnboardingCompleted.value = true
                }
            }
        } catch (e: Exception) {
            // Session restoration failed, user needs to re-authenticate
            sessionManager.clearSession()
            isAuthenticated.value = false
            authState.value = AuthState.SignedOut
            isOnboardingCompleted.value = false
        } finally {
            isSessionRestored.value = true
        }
    }

    /**
     * ROLE-001 / AUTH-007: Switches the active account mode.
     *
     * The worker-mode gate is enforced HERE (at the ViewModel boundary), not only inside
     * [toggleUserRoleMode]. Previously any direct caller of this public function could pass
     * "Offer Skills" and bypass the KYC/activation check entirely.
     */
    fun setUserRoleMode(mode: String) {
        if (mode == "Offer Skills" && !canAccessWorkerFeatures()) {
            authError.value = "Worker verification and activation required to switch to worker mode."
            return
        }
        userRoleMode.value = mode
        viewModelScope.launch {
            val user = repository.currentUser.first() ?: return@launch
            val dbMode = if (mode == "Offer Skills") "WORKER" else "CLIENT"
            repository.switchActiveMode(user.id, dbMode)
        }
    }

    // AUTH-007 / SEC-009 / G1 / G3 / H1 / H8: Strict multi-layer permission enforcement for worker dashboard & actions.
    // Worker dashboard and capabilities require:
    // 1. Authenticated session
    // 2. Worker account registration (initialRole == "WORKER")
    // 3. KYC verification status explicitly APPROVED by admin
    // 4. Worker training completed
    // 5. Worker activated by administrator
    fun canAccessWorkerFeatures(): Boolean {
        return isWorkerAuthorized(isAuthenticated.value, currentUser.value)
    }

    fun toggleUserRoleMode() {
        val newMode = if (userRoleMode.value == "Hire a Neighbor") "Offer Skills" else "Hire a Neighbor"
        if (newMode == "Offer Skills" && !canAccessWorkerFeatures()) {
            authError.value = "Worker verification and activation required to switch to worker mode."
            return
        }
        setUserRoleMode(newMode)
    }

    val providers: StateFlow<List<ServiceProviderEntity>> = combine(
        repository.providers,
        filterState
    ) { allProviders, filter ->
        allProviders.filter { provider ->
            val matchCategory = filter.category == null || provider.category.equals(filter.category, ignoreCase = true)
            val matchDistance = provider.distanceMiles <= filter.radiusMiles
            val matchRate = provider.hourlyRate >= filter.minRate && provider.hourlyRate <= filter.maxRate
            val matchVerified = !filter.onlyVerified || (provider.isVerifiedResident && provider.isBackgroundChecked)
            val matchActivated = provider.isActivated && provider.kycStatus == "APPROVED"
            val matchQuery = filter.searchQuery.isBlank() ||
                    provider.name.contains(filter.searchQuery, ignoreCase = true) ||
                    provider.tagline.contains(filter.searchQuery, ignoreCase = true) ||
                    provider.skillsCsv.contains(filter.searchQuery, ignoreCase = true)

            matchCategory && matchDistance && matchRate && matchVerified && matchActivated && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val jobs: StateFlow<List<JobPostEntity>> = combine(
        repository.jobs,
        filterState
    ) { allJobs, filter ->
        allJobs.filter { job ->
            val matchCategory = filter.category == null || job.category.equals(filter.category, ignoreCase = true)
            val matchQuery = filter.searchQuery.isBlank() ||
                    job.title.contains(filter.searchQuery, ignoreCase = true) ||
                    job.description.contains(filter.searchQuery, ignoreCase = true)

            matchCategory && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getChatMessagesForJob(jobId: String): Flow<List<ChatMessageEntity>> {
        return repository.getRealtimeChatMessages(jobId)
    }

    fun getEndorsementsForProvider(providerId: String): Flow<List<SkillEndorsementEntity>> {
        return repository.getEndorsements(providerId)
    }

    fun setSearchQuery(query: String) {
        filterState.update { it.copy(searchQuery = query) }
    }

    fun setCategory(category: String?) {
        filterState.update { it.copy(category = category) }
    }

    fun setRadius(radius: Double) {
        filterState.update { it.copy(radiusMiles = radius) }
    }

    fun setDistrict(district: String) {
        CurrencyRegion.currentRegion = CurrencyRegion.fromDistrictOrCountry(district)
        filterState.update { it.copy(district = district) }
    }

    fun toggleOnlyVerified() {
        filterState.update { it.copy(onlyVerified = !it.onlyVerified) }
    }

    fun setRateRange(min: Double, max: Double) {
        filterState.update { it.copy(minRate = min, maxRate = max) }
    }

    fun selectProvider(provider: ServiceProviderEntity?) {
        selectedProvider.value = provider
    }

    fun selectJob(job: JobPostEntity?) {
        selectedJob.value = job
    }

    fun setQuickRequestOpen(open: Boolean) {
        isQuickRequestOpen.value = open
    }

    fun setFilterDrawerOpen(open: Boolean) {
        isFilterDrawerOpen.value = open
    }

    fun setSearchModalOpen(open: Boolean) {
        isSearchModalOpen.value = open
    }

    fun setNotificationDrawerOpen(open: Boolean) {
        isNotificationDrawerOpen.value = open
    }

    fun setNavigationDrawerOpen(open: Boolean) {
        isNavigationDrawerOpen.value = open
    }

    fun setThemeMode(mode: String) {
        themeMode.value = mode
    }

    fun toggleWorkerOnline() {
        if (!canAccessWorkerFeatures()) {
            authError.value = "Worker verification and activation required to go online."
            return
        }
        isWorkerOnline.update { !it }
    }

    fun setMobileTab(tab: String) {
        activeMobileTab.value = tab
    }

    fun searchLocalPlacesWithGoogleMaps(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isMapsSearching.value = true
            val currentDistrict = filterState.value.district
            val result = geminiMapsService.searchLocalPlacesWithMaps(query, currentDistrict)
            mapsSearchResults.value = result
            isMapsSearching.value = false
        }
    }

    fun startVoiceRecording() {
        isAudioRecording.value = audioRecorderHelper.startRecording()
    }

    fun stopVoiceRecordingAndTranscribe() {
        if (!isAudioRecording.value) return
        isAudioRecording.value = false
        viewModelScope.launch {
            isAudioTranscribing.value = true
            val audioBytes = audioRecorderHelper.stopRecording() ?: audioRecorderHelper.generateSampleWavBytes()
            val transcribed = geminiAudioService.transcribeAudio(audioBytes)
            audioTranscribedJob.value = transcribed
            isAudioTranscribing.value = false
        }
    }

    fun postQuickJob(
        title: String,
        category: String,
        description: String,
        budget: Double,
        urgency: String,
        materialsProvidedBy: String = "RESIDENT",
        laborHours: Double = 2.0,
        laborRate: Double = 60.0,
        jobType: String = "ONE_TIME",
        recurringSchedule: String = "WEEKLY"
    ) {
        viewModelScope.launch {
            val district = filterState.value.district
            val jobId = repository.addJobPost(
                title = title,
                category = category,
                description = description,
                budget = budget,
                urgency = urgency,
                district = district,
                materialsProvidedBy = materialsProvidedBy,
                laborHours = laborHours,
                laborRate = laborRate,
                jobType = jobType,
                recurringSchedule = recurringSchedule
            )
            setQuickRequestOpen(false)
            val createdJob = repository.jobs.first().find { it.id == jobId }
            if (createdJob != null) {
                selectedJob.value = createdJob
                firebaseService.syncJobToFirestore(createdJob)
            }
        }
    }

    fun hireProviderForSelectedJob(jobId: String, provider: ServiceProviderEntity) {
        viewModelScope.launch {
            repository.hireProviderForJob(jobId, provider.id, provider.name)
            // Refresh current selected job
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun updateJobStatus(jobId: String, newStatus: String, reason: String = "") {
        viewModelScope.launch {
            val currentJob = repository.jobs.first().find { it.id == jobId }
            val fromStatus = currentJob?.status ?: ""
            val userId = currentUser.value?.id ?: "system"

            repository.transitionJobStatus(
                jobId = jobId,
                fromStatus = fromStatus,
                toStatus = newStatus,
                changedBy = userId,
                reason = reason
            )

            val assignedProviderId = currentJob?.assignedProviderId
            if (assignedProviderId != null) {
                repository.createNotification(
                    com.example.data.db.NotificationEntity(
                        id = "notif_${System.currentTimeMillis()}_job",
                        userId = assignedProviderId,
                        type = "JOB_STATUS_CHANGE",
                        title = "Job Status Updated",
                        body = "Job status changed to: $newStatus",
                        jobId = jobId,
                        createdAt = System.currentTimeMillis()
                    )
                )
            }

            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    // JOB-005: Get audit trail for a job
    suspend fun getJobAuditTrail(jobId: String): List<com.example.data.db.JobStatusAuditEntity> {
        return repository.getJobAuditTrail(jobId)
    }

    fun updateEscrowStatus(jobId: String, newEscrowStatus: String) {
        viewModelScope.launch {
            repository.updateEscrowStatus(jobId, newEscrowStatus)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun updateJobPhotos(jobId: String, beforeUrl: String?, afterUrl: String?) {
        viewModelScope.launch {
            repository.updateJobPhotos(jobId, beforeUrl, afterUrl)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun requestEmergencyAddon(jobId: String, amount: Double, reason: String) {
        viewModelScope.launch {
            repository.requestEmergencyAddon(jobId, amount, reason)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun respondToEmergencyAddon(jobId: String, approve: Boolean) {
        viewModelScope.launch {
            repository.respondToEmergencyAddon(jobId, approve)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun updatePaymentChannel(jobId: String, channel: String, phone: String) {
        viewModelScope.launch {
            repository.updatePaymentChannel(jobId, channel, phone)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun verifyDoorstepPin(jobId: String, enteredPin: String, expectedPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.verifyDoorstepPin(jobId, enteredPin, expectedPin)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
            onResult(success)
        }
    }

    fun requestMaterialAdvance(jobId: String, amount: Double) {
        viewModelScope.launch {
            repository.requestMaterialAdvance(jobId, amount)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun respondToMaterialAdvance(jobId: String, approve: Boolean) {
        viewModelScope.launch {
            repository.respondToMaterialAdvance(jobId, approve)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun triggerSosAlert(jobId: String) {
        viewModelScope.launch {
            val jobPost = repository.jobs.first().find { it.id == jobId } ?: return@launch
            val user = repository.currentUser.first()
            val gps = gpsLocationState.value
            val latitude = gps.latitude ?: user?.latitude ?: 0.3476
            val longitude = gps.longitude ?: user?.longitude ?: 32.5825
            repository.triggerEmergencySos(jobId, latitude, longitude, jobPost.district)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun sendChatMessage(jobId: String, messageText: String) {
        if (messageText.isBlank()) return
        viewModelScope.launch {
            repository.sendChatMessage(
                jobId = jobId,
                senderName = "You (${filterState.value.district})",
                isFromUser = true,
                text = messageText
            )
        }
    }

    fun endorseSkill(providerId: String, skillName: String) {
        viewModelScope.launch {
            repository.endorseSkill(
                providerId = providerId,
                skillName = skillName,
                neighborName = "Resident (${filterState.value.district})",
                district = filterState.value.district
            )
        }
    }

    fun startGpsLocating() {
        gpsLocationState.update {
            it.copy(
                isLocating = true,
                errorMessage = null,
                isUsingCurrentLocation = true
            )
        }
    }

    fun onGpsLocationAcquired(lat: Double, lng: Double, neighborhood: String, accuracy: Float) {
        gpsLocationState.update {
            it.copy(
                isLocating = false,
                isPermissionGranted = true,
                isUsingCurrentLocation = true,
                latitude = lat,
                longitude = lng,
                detectedNeighborhood = neighborhood,
                accuracyMeters = accuracy,
                errorMessage = null
            )
        }
        setDistrict(neighborhood)
    }

    fun requestGpsDetection(targetNeighborhood: String = "Sunnyside District") {
        viewModelScope.launch {
            gpsLocationState.update { it.copy(isLocating = true, errorMessage = null, isUsingCurrentLocation = true) }
            kotlinx.coroutines.delay(600) // Fast responsive transition
            gpsLocationState.update {
                it.copy(
                    isLocating = false,
                    isPermissionGranted = true,
                    isUsingCurrentLocation = true,
                    latitude = 0.3476,
                    longitude = 32.5825,
                    detectedNeighborhood = targetNeighborhood,
                    accuracyMeters = 8f,
                    errorMessage = null
                )
            }
            setDistrict(targetNeighborhood)
        }
    }

    fun dismissGpsBanner() {
        gpsLocationState.update { it.copy(isBannerDismissed = true) }
    }

    fun denyGpsPermission() {
        gpsLocationState.update {
            it.copy(
                isLocating = false,
                isPermissionGranted = false,
                isUsingCurrentLocation = false,
                errorMessage = "Location permission is needed for live GPS neighborhood matching."
            )
        }
    }

    fun switchToManualDistrict(district: String) {
        gpsLocationState.update { it.copy(isUsingCurrentLocation = false) }
        setDistrict(district)
    }

    fun triggerAuthFlow(reasonDescription: String = "Unlock direct contact & verified credentials") {
        authPendingAction.value = reasonDescription
        isAuthModalOpen.value = true
    }

    /** Navigate back to the unified onboarding screen from MainScreen. */
    fun navigateToOnboarding() {
        isOnboardingCompleted.value = false
    }

    /** Called by OnboardingScreen to mark onboarding as completed after successful auth. */
    fun setIsOnboardingCompleted(completed: Boolean) {
        isOnboardingCompleted.value = completed
    }

    fun closeAuthModal() {
        isAuthModalOpen.value = false
        authPendingAction.value = null
    }

    fun completeOnboarding(
        phone: String,
        fullName: String,
        district: String,
        selectedRole: String
    ) {
        loginUser(
            phone = phone,
            fullName = fullName,
            district = district,
            initialRole = selectedRole
        )
    }

    fun loginUser(phone: String, fullName: String = "", district: String = "", initialRole: String = "CLIENT") {
        // C3: explicit authorisation — an empty/blank phone is never a completed
        // registration. Surface the error on the phone field and stop before any
        // profile, session, or server write happens.
        if (phone.isBlank()) {
            phoneError.value = "Enter your Mobile Money phone number to continue."
            return
        }
        isAuthenticated.value = true
        currentUserPhone.value = phone
        val action = authPendingAction.value
        closeAuthModal()

        viewModelScope.launch {
            // AUTH-006 / C2: Always resolve a REAL Firebase UID before persisting a profile.
            // Previously a synthetic "user_<timestamp>" id was used whenever FirebaseAuth had
            // no current user yet, so the profile was written under an id that a later Google
            // sign-in (real Google UID) could never match — the user then looked brand new.
            // C2: no synthetic-ID fallback remains. When Firebase cannot supply a UID the
            // flow fails closed (error, no profile/session persisted) instead of forking
            // the identity under a throwaway id.
            // C2-1: the identity that owns a profile is the REAL Firebase UID of the
            // authenticated user. `signInAnonymously()` is deliberately NOT used as a
            // fallback here: a phone number alone (or no verification at all) must never mint
            // the identity that owns a profile or a session.
            val firebaseUser = firebaseService.getCurrentUser()
            val uid = firebaseUser?.uid?.takeIf { it.isNotBlank() }
            if (uid == null) {
                isAuthenticated.value = false
                authState.value = AuthState.SignedOut
                val error = "Verify your phone number to continue."
                authError.value = error
                phoneError.value = error
                return@launch
            }

            // C2-1: existing-account recognition is UID-scoped only.
            //
            // Previously this also adopted whatever Room row carried the same phone string
            // (and, failing that, the newest row on the device) and then copied it onto the
            // authenticated uid — including workerKycStatus / isWorkerActivated /
            // trainingCompleted. Knowing a phone number was therefore enough to inherit
            // another account's admin-granted worker state. A record that is not owned by
            // THIS uid is no longer evidence of anything: the user simply completes normal
            // onboarding under their verified identity.
            //
            // The two accepted sources are both keyed by the authenticated uid:
            // 1) the device-local Room row written under that uid, and
            // 2) the durable `users/{uid}` identity record (survives logout, app-data clear,
            //    reinstall and sign-in on another device).
            val existingByUid = repository.getUserProfile(uid).first()?.takeIf { it.id != DEMO_PROFILE_ID }
            val existingOnServer = if (existingByUid == null) {
                repository.fetchUserIdentityFromServer(uid)?.takeIf { it.id == uid && it.id != DEMO_PROFILE_ID }
            } else null
            val existing = existingByUid ?: existingOnServer

            // C2-1: worker privileges are never copied from a record that is not owned by this
            // authenticated uid (see resolveAuthenticatedProfile), so a phone match can never
            // hand over an approved worker's state.
            val profile = resolveAuthenticatedProfile(
                authenticatedUid = uid,
                existing = existing,
                phone = phone,
                fullName = fullName,
                district = district,
                initialRole = initialRole
            )
            repository.saveUserProfile(profile)
            // AUTH-002/AUTH-006: Mirror the identity record to Firestore so this account can
            // be recognised again after logout or on another device.
            repository.saveUserIdentityToServer(profile)
            if (district.isNotBlank()) {
                setDistrict(district)
            }
            userRoleMode.value = if (profile.currentActiveMode == "WORKER") "Offer Skills" else "Hire a Neighbor"
            isOnboardingCompleted.value = true
            sessionManager.setOnboardingCompleted(true)

            // AUTH-002: Persist session
            val deviceId = sessionManager.getDeviceId(appContext)
            sessionManager.saveSession(
                isAuthenticated = true,
                phone = profile.phoneNumber,
                userId = profile.id,
                name = profile.fullName,
                district = profile.district,
                deviceId = deviceId,
                role = profile.currentActiveMode
            )
        }

        if (action?.startsWith("hire_") == true) {
            val providerId = action.substringAfter("hire_")
            val p = providers.value.find { it.id == providerId }
            if (p != null) {
                selectedProvider.value = p
                isQuickRequestOpen.value = true
            }
        } else if (action == "post_job") {
            isQuickRequestOpen.value = true
        }
    }

    // Google Sign-In handling
    /**
     * AUTH-002 / AUTH-006: Google sign-in with durable existing-user recognition.
     *
     * Callback contract: `onComplete(true)` = this is a NEW user and the caller must continue
     * into onboarding/profile setup. `onComplete(false)` = the account was recognised (or the
     * flow already completed), so no onboarding is required.
     *
     * Previously the recognition check also required a non-blank `fullName` and `district` on
     * the local Room row. Those are optional profile fields, so any user who had completed
     * onboarding without them was pushed back through onboarding on every sign-in.
     */
    fun loginWithGoogle(idToken: String, initialRole: String = "CLIENT", onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val result = firebaseService.signInWithGoogle(idToken)
            if (result.isSuccess) {
                val user = result.getOrNull()
                // C2: the Firebase UID is the single authoritative identity. A blank UID
                // means sign-in did not actually complete — fail closed instead of writing
                // the profile under a "google_user" placeholder nobody can ever match.
                // C3: no phone is captured on the Google branch — carry the Firebase UID's
                // stored identity forward, but never invent a blank-phone profile for it.
                val uid = user?.uid
                if (uid.isNullOrBlank()) {
                    authError.value = "Google sign-in failed. Please try again."
                    onComplete(false)
                    return@launch
                }

                // 1) Local profile row (fast path, survives while the install keeps its data).
                val localProfile = repository.getUserProfile(uid).first()?.takeIf { it.id != DEMO_PROFILE_ID }
                // 2) Durable Firestore identity record (survives logout, app-data clear,
                //    reinstall and sign-in on a different device).
                val serverProfile = if (localProfile == null) {
                    repository.fetchUserIdentityFromServer(uid)?.takeIf { it.id != DEMO_PROFILE_ID }
                } else null
                val existing = localProfile ?: serverProfile

                // Onboarding is ONLY required when we have never seen this account before.
                val isNewUser = existing?.isOnboardingCompleted != true

                if (!isNewUser && existing != null) {
                    // Recognised account — reuse the stored identity and go straight to the app.
                    // C3: never overwrite a stored legal name with the Google display name —
                    // the display name is only a last-resort filler for a blank stored name.
                    val restored = existing.copy(
                        id = uid,
                        fullName = existing.fullName.ifBlank { user?.displayName.orEmpty() },
                        updatedAtTimestamp = System.currentTimeMillis()
                    )
                    if (localProfile == null) {
                        repository.saveUserProfile(restored)
                        repository.saveUserIdentityToServer(restored)
                    }
                    completeAuthForExistingUser(uid, restored, onComplete)
                    return@launch
                }

                if (existing != null) {
                    // Identity exists but onboarding was never finished: keep the identity,
                    // continue to profile setup, and let the UI collect the missing fields.
                    repository.saveUserProfile(existing.copy(id = uid))
                    onComplete(true)
                    return@launch
                }

                // Genuinely unknown account — the caller runs role-specific onboarding.
                onComplete(true)
            } else {
                authError.value = "Google sign-in failed. Please try again."
                onComplete(false)
            }
        }
    }

    /**
     * AUTH-002: Finalizes an already-recognised sign-in — restores session state, closes the
     * auth modal, and replays any pending action (hire_<id> / post_job) the user triggered.
     *
     * Kept as a private helper so the Google, phone-OTP and restore paths cannot drift apart.
     */
    private suspend fun completeAuthForExistingUser(uid: String, profile: UserProfileEntity, onComplete: (Boolean) -> Unit) {
        isAuthenticated.value = true
        authState.value = AuthState.SignedIn
        currentUserPhone.value = profile.phoneNumber
        setDistrict(profile.district)
        userRoleMode.value = if (profile.currentActiveMode == "WORKER") "Offer Skills" else "Hire a Neighbor"
        val deviceId = sessionManager.getDeviceId(appContext)
        sessionManager.saveSession(
            isAuthenticated = true,
            phone = profile.phoneNumber,
            userId = uid,
            name = profile.fullName,
            district = profile.district,
            deviceId = deviceId,
            role = profile.currentActiveMode
        )
        sessionManager.setOnboardingCompleted(true)
        isOnboardingCompleted.value = true
        val action = authPendingAction.value
        closeAuthModal()
        if (action?.startsWith("hire_") == true) {
            val providerId = action.substringAfter("hire_")
            val p = providers.value.find { it.id == providerId }
            if (p != null) {
                selectedProvider.value = p
                isQuickRequestOpen.value = true
            }
        } else if (action == "post_job") {
            isQuickRequestOpen.value = true
        }
        onComplete(false)
    }

    /**
     * LOC-003: Update worker operating district and coordinates
     */
    fun updateWorkerLocation(district: String, lat: Double = 0.3476, lng: Double = 32.5825, address: String? = null) {
        viewModelScope.launch {
            val user = repository.currentUser.first() ?: return@launch
            val updated = user.copy(
                district = district,
                latitude = lat,
                longitude = lng,
                address = address,
                updatedAtTimestamp = System.currentTimeMillis()
            )
            repository.saveUserProfile(updated)
            setDistrict(district)
        }
    }

    fun permanentlyDeleteUserAccountAndData() {
        viewModelScope.launch {
            val userId = currentUser.value?.id ?: firebaseService.getCurrentUser()?.uid ?: return@launch
            val userJobIds = repository.jobs.first().map { it.id }

            val firestoreDeleted = firebaseService.deleteUserFirestoreData(userId, userJobIds)
            val authDeleted = firebaseService.deleteCurrentUser()

            repository.clearAllUserData()
            sessionManager.clearSession()

            authState.value = AuthState.SignedOut
            isAuthenticated.value = false
            authError.value = null
            currentUserPhone.value = null
            selectedProvider.value = null
            selectedJob.value = null
            isAuthModalOpen.value = false
            authPendingAction.value = null
            currentTraining.value = null
            fcmToken.value = null
            hasNotificationPermission.value = false
            userRoleMode.value = "Hire a Neighbor"

            if (!firestoreDeleted || !authDeleted) {
                authError.value = "Account deletion partially failed. Please contact support."
            }
        }
    }

    // --- REG-003: WORKER BACKUP CONTACT ---
    /**
     * Saves the signed-in user's backup contact (name + phone).
     *
     * DECISION: updates are IMMEDIATE — contact metadata follows the same path as other
     * profile edits (district, mode); the existing KYC review queue applies only to
     * identity documents. Writes always target [currentUser]'s own Room row and Firestore
     * `users/{uid}` (owner-only write rule), so one user can never modify another user's
     * backup contact. Never touches the guarantors tables: a backup contact is NOT a
     * guarantor.
     */
    fun saveBackupContact(name: String, phone: String) {
        viewModelScope.launch {
            val user = currentUser.value
            val blocker = ProfileValidators.backupContactSaveBlocker(
                isAuthenticated = isAuthenticated.value,
                hasOwnProfile = user != null && user.id != DEMO_PROFILE_ID,
                primaryPhone = user?.phoneNumber.orEmpty(),
                backupName = name,
                backupPhone = phone
            )
            if (blocker != null) {
                backupContactError.value = blocker
                return@launch
            }
            backupContactError.value = null
            profileSettingsSuccess.value = null
            val updated = user!!.copy(
                backupContactName = name.trim(),
                backupContactPhone = phone.trim(),
                updatedAtTimestamp = System.currentTimeMillis()
            )
            repository.saveUserProfile(updated)
            repository.saveUserIdentityToServer(updated)
            profileSettingsSuccess.value = "Backup contact saved."
        }
    }

    // --- REG-004: WORKER PROFILE PHOTO ---
    fun selectPendingProfilePhoto(uri: Uri?) {
        if (uri == null) {
            clearPendingProfilePhoto()
            return
        }
        pendingProfilePhotoUri.value = uri
        profilePhotoError.value = null
        profileSettingsSuccess.value = null
    }

    fun clearPendingProfilePhoto() {
        pendingProfilePhotoUri.value = null
        profilePhotoError.value = null
    }

    fun clearProfileSettingsMessages() {
        profilePhotoError.value = null
        backupContactError.value = null
        profileSettingsSuccess.value = null
    }

    /**
     * Uploads the pending gallery selection to Firebase Storage `avatars/{uid}/`
     * (owner-only write, public read) and stores the returned download URL on the user's
     * own profile in Room + Firestore. Replaces any previous photo by overwriting the
     * stored URL. Fails closed when signed out, when the profile is the demo placeholder,
     * when no image is selected, or when Storage is unavailable/offline.
     */
    fun uploadPendingProfilePhoto() {
        viewModelScope.launch {
            val user = currentUser.value
            val uri = pendingProfilePhotoUri.value
            val blocker = ProfileValidators.photoUploadBlocker(
                isAuthenticated = isAuthenticated.value,
                hasOwnProfile = user != null && user.id != DEMO_PROFILE_ID,
                hasPendingImage = uri != null
            )
            if (blocker != null) {
                profilePhotoError.value = blocker
                return@launch
            }
            profilePhotoError.value = null
            profileSettingsSuccess.value = null
            isUploadingProfilePhoto.value = true
            val downloadUrl = firebaseService.uploadProfilePhoto(user!!.id, uri!!)
            if (downloadUrl == null) {
                isUploadingProfilePhoto.value = false
                profilePhotoError.value =
                    "Photo upload failed. Check your connection and try again."
                return@launch
            }
            val updated = user.copy(
                avatarUrl = downloadUrl,
                updatedAtTimestamp = System.currentTimeMillis()
            )
            repository.saveUserProfile(updated)
            repository.saveUserIdentityToServer(updated)
            pendingProfilePhotoUri.value = null
            isUploadingProfilePhoto.value = false
            profileSettingsSuccess.value = "Profile photo updated."
        }
    }

    // --- MODULE 1: ESCROW STATE MACHINE TRANSITIONS ---
    fun transitionJobState(jobId: String, newStatus: String, newEscrowStatus: String) {
        viewModelScope.launch {
            val jobPost = repository.jobs.first().find { it.id == jobId } ?: return@launch
            val updated = jobPost.copy(
                status = newStatus,
                escrowStatus = newEscrowStatus
            )
            repository.insertJob(updated)
            firebaseService.syncJobToFirestore(updated)
        }
    }

    // --- MODULE 2: KYC IDENTITY & GATED CAPABILITIES ---
    fun isWorkerKycApproved(provider: ServiceProviderEntity?): Boolean {
        if (provider == null) return false
        return provider.kycStatus == "APPROVED"
    }

    fun submitWorkerKycVerification(
        providerId: String,
        nationalId: String,
        idDocUrl: String,
        selfieUrl: String
    ) {
        viewModelScope.launch {
            val provider = providers.value.find { it.id == providerId } ?: return@launch
            val updated = provider.copy(
                nationalIdNumber = nationalId,
                idDocumentUrl = idDocUrl,
                selfieVerificationUrl = selfieUrl,
                kycStatus = "PENDING_REVIEW"
            )
            repository.insertProvider(updated)
            firebaseService.syncProviderToFirestore(updated)
        }
    }

    // --- MODULE 3: MOBILE MONEY (MOMO) PAYMENT ENGINE ---
    fun initiateMoMoEscrowPayment(
        jobId: String,
        amountUgx: Double,
        phone: String,
        provider: String = "MTN_MOMO",
        onIntentCreated: (MoMoPaymentIntent) -> Unit
    ) {
        viewModelScope.launch {
            val intent = moMoPaymentService.createMoMoCollection(jobId, amountUgx, phone, provider)
            transitionJobState(jobId, "ACCEPTED_FUNDED", "FUNDS_LOCKED_IN_ESCROW")
            onIntentCreated(intent)
        }
    }

    // --- MODULE 4: SMS FALLBACK DISPATCH TO OFFLINE WORKERS ---
    fun getDistrictJobStatsForProvider(providerId: String): Map<String, Int> {
        return jobs.value
            .filter { it.assignedProviderId == providerId }
            .groupBy { it.district }
            .mapValues { it.value.size }
    }

    fun triggerSmsAlertToOfflineWorkers(jobTitle: String, district: String) {
        viewModelScope.launch {
            val offlineWorkers = providers.value
                .filter { it.neighborhood.contains(district, ignoreCase = true) && it.kycStatus == "APPROVED" }
                .map { it.momoNumber }
            smsNotificationService.notifyOfflineWorkersInRadius(offlineWorkers, jobTitle, district)
        }
    }

    fun saveGuarantors(workerKycId: String, guarantors: List<com.example.data.db.GuarantorEntity>) {
        viewModelScope.launch {
            repository.deleteGuarantorsForWorker(workerKycId)
            repository.insertGuarantors(guarantors)
        }
    }

    suspend fun isValidGuarantorCount(workerKycId: String): Boolean {
        return repository.validateGuarantorCount(workerKycId)
    }

    // ── REG-002: Guarantor verification (two guarantors + group photograph) ───────────────

    /** Whether the guarantor submission sheet is open. */
    var guarantorVerificationOpen by mutableStateOf(false)
        private set

    /** Saved (0–2) guarantors, so a partially completed form resumes after an app restart. */
    var guarantorDrafts by mutableStateOf<List<com.example.data.db.GuarantorEntity>>(emptyList())
        private set

    /** Content URI or Storage path of the group photograph — empty until one is picked. */
    var guarantorGroupPhotoSource by mutableStateOf("")
        private set

    var guarantorSubmissionPending by mutableStateOf(false)
        private set

    /** Validation or network reason the last submission did not go through. */
    var guarantorSubmissionError by mutableStateOf<String?>(null)
        private set

    /** Confirmation shown by the sheet ("submitted" / "draft saved"). */
    var guarantorSubmissionMessage by mutableStateOf<String?>(null)
        private set

    /** Opens the sheet and restores drafts; [currentGroupPhotoPath] is what is already filed. */
    fun openGuarantorVerification(currentGroupPhotoPath: String) {
        val userId = currentUser.value?.id ?: return
        viewModelScope.launch {
            guarantorDrafts = repository.getGuarantorsOnce(userId)
            guarantorGroupPhotoSource = currentGroupPhotoPath
            guarantorSubmissionError = null
            guarantorSubmissionMessage = null
            guarantorVerificationOpen = true
        }
    }

    fun closeGuarantorVerification() {
        guarantorVerificationOpen = false
    }

    fun onGuarantorDraftsChanged(
        drafts: List<com.example.data.db.GuarantorEntity>,
        groupPhotoSource: String
    ) {
        guarantorDrafts = drafts
        guarantorGroupPhotoSource = groupPhotoSource
    }

    /** Saves the form as a draft — photos stay on the device until the worker submits. */
    fun saveGuarantorDraft() {
        val userId = currentUser.value?.id ?: return
        val drafts = guarantorDrafts
        val groupPhoto = guarantorGroupPhotoSource
        viewModelScope.launch {
            repository.saveGuarantorDraft(userId, drafts, groupPhoto)
            guarantorSubmissionMessage = "Draft saved on this device."
        }
    }

    /**
     * Files the two guarantors and the group photograph for admin review. The seeded demo
     * profile is not a real account, so it can never file third-party identity data; the
     * repository repeats every check, and validation errors surface inline in the sheet.
     */
    fun submitGuarantorVerification() {
        val user = currentUser.value ?: return
        if (guarantorSubmissionPending) return
        if (user.id == DEMO_PROFILE_ID) {
            guarantorSubmissionError =
                "Sign in to file your guarantors. Demo data cannot submit other people's identity details."
            return
        }
        guarantorSubmissionPending = true
        guarantorSubmissionError = null
        guarantorSubmissionMessage = null
        viewModelScope.launch {
            val result = repository.submitGuarantorVerification(
                workerId = user.id,
                guarantors = guarantorDrafts,
                groupPhotoSource = guarantorGroupPhotoSource,
                isSignedIn = true
            )
            guarantorSubmissionPending = false
            result.onSuccess {
                guarantorSubmissionMessage =
                    "Guarantors submitted for review. Decisions usually arrive within 48 hours."
            }.onFailure {
                guarantorSubmissionError =
                    it.message ?: "Could not submit your guarantors. Please try again."
            }
        }
    }

    /** Pulls the admin decision for the sheet and the status card (48h review SLA). */
    fun refreshGuarantorVerificationStatus() {
        val userId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID } ?: return
        viewModelScope.launch { repository.refreshGuarantorVerificationStatus(userId) }
    }

    fun uploadQualificationEvidence(evidenceType: String, fileBytes: ByteArray? = null, videoUrl: String? = null, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            // C2: never upload evidence under the demo placeholder id — resolve the real
            // authenticated UID (Room profile, else Firebase) and abort when absent.
            val userId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID }
                ?: firebaseService.getCurrentUser()?.uid
            if (userId.isNullOrBlank()) {
                onResult(null)
                return@launch
            }
            val path = firebaseService.uploadQualificationEvidence(userId, evidenceType, fileBytes, videoUrl)
            onResult(path)
        }
    }

    val adminVerificationQueue = MutableStateFlow<List<Map<String, Any>>>(emptyList())

    val currentTraining = MutableStateFlow<com.example.data.db.WorkerTrainingEntity?>(null)

    init {
        viewModelScope.launch {
            repository.currentTraining.collect { training ->
                currentTraining.value = training
            }
        }
    }

    fun loadAdminVerificationQueue() {
        viewModelScope.launch {
            val queue = repository.getAdminVerificationQueue()
            adminVerificationQueue.value = queue
        }
    }

    fun submitAdminDecision(userId: String, decision: String, feedback: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.submitAdminVerificationDecision(userId, decision, feedback)
            if (success) loadAdminVerificationQueue()
            onResult(success)
        }
    }

    fun setWorkerActivation(userId: String, activate: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.setWorkerActivation(userId, activate)
            onResult(success)
        }
    }

    fun startTraining() {
        viewModelScope.launch {
            // C2: training rows belong to the real authenticated UID, never the demo placeholder.
            val userId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID }
                ?: firebaseService.getCurrentUser()?.uid ?: return@launch
            repository.startTraining(userId)
        }
    }

    fun completeTrainingModule(modulesCsv: String, moduleIndex: Int) {
        viewModelScope.launch {
            val userId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID }
                ?: firebaseService.getCurrentUser()?.uid ?: return@launch
            repository.updateTrainingModule(userId, modulesCsv, moduleIndex)
        }
    }

    fun completeTraining() {
        viewModelScope.launch {
            val userId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID }
                ?: firebaseService.getCurrentUser()?.uid ?: return@launch
            repository.completeTraining(userId)
        }
    }

    fun activateWorkerWithGate(userId: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            when (val result = repository.activateWorker(userId)) {
                is SkillsExchangeRepository.ActivationResult.Success -> {
                    onResult(true, "Worker activated successfully")
                }
                is SkillsExchangeRepository.ActivationResult.Failure -> {
                    onResult(false, result.reason)
                }
            }
        }
    }

    fun submitRecurringProposal(jobId: String, proposedRate: Double, terms: String) {
        // C2: worker-gated action — a missing/placeholder identity must abort, never file
        // the proposal under "worker_default".
        if (!canAccessWorkerFeatures()) return
        viewModelScope.launch {
            val userId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID }
                ?: firebaseService.getCurrentUser()?.uid ?: return@launch
            repository.submitRecurringProposal(jobId, userId, proposedRate, terms)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun submitQuote(jobId: String, workerName: String, proposedRate: Double, proposedHours: Double, notes: String) {
        if (!canAccessWorkerFeatures()) {
            authError.value = "Only verified and activated workers can submit quotes."
            return
        }
        viewModelScope.launch {
            // C2: quotes are attributed to the real authenticated worker UID only.
            val userId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID }
                ?: firebaseService.getCurrentUser()?.uid ?: return@launch
            repository.submitQuote(jobId, userId, workerName, proposedRate, proposedHours, notes)
        }
    }

    fun respondToQuote(quoteId: String, jobId: String, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToQuote(quoteId, jobId, accept)
        }
    }

    fun getQuotesForJob(jobId: String): Flow<List<com.example.data.db.JobQuoteEntity>> {
        return repository.getQuotesForJob(jobId)
    }

    fun getQuotesByWorker(workerId: String): Flow<List<com.example.data.db.JobQuoteEntity>> {
        return repository.getQuotesByWorker(workerId)
    }

    // pendingActions moved above init block
    // DISP-002: Dispute state
    val disputes = MutableStateFlow<List<DisputeEntity>>(emptyList())
    val disputeSubmissionState = MutableStateFlow<DisputeSubmissionState>(DisputeSubmissionState.Idle)

    // AUTH-001: Authentication state
    val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
    val authError = MutableStateFlow<String?>(null)
    val phoneError = MutableStateFlow<String?>(null)
    val otpError = MutableStateFlow<String?>(null)

    // NOTIF-002: FCM state
    val fcmToken = MutableStateFlow<String?>(null)
    val hasNotificationPermission = MutableStateFlow(false)

    // NOTIF-004: Notification UI state
    val notifications = MutableStateFlow<List<com.example.data.db.NotificationEntity>>(emptyList())
    val unreadNotificationCount = MutableStateFlow(0)

    // RATE-002: Rating state
    val ratingSubmissionState = MutableStateFlow<RatingSubmissionState>(RatingSubmissionState.Idle)

    sealed class RatingSubmissionState {
        object Idle : RatingSubmissionState()
        object Submitting : RatingSubmissionState()
        object Success : RatingSubmissionState()
        data class Error(val message: String) : RatingSubmissionState()
    }

    sealed class AuthState {
        object SignedOut : AuthState()
        object SendingOtp : AuthState()
        object OtpSent : AuthState()
        object VerifyingOtp : AuthState()
        object SignedIn : AuthState()
        data class Error(val message: String) : AuthState()
    }

    /**
     * C2-2 Fix #2 (F2): the outcome of cross-checking the cached DataStore session identity
     * against the authoritative Firebase authenticated user during [restoreSession].
     *
     * [None] means no identity may be restored at all (Firebase has no authenticated user, or the
     * only thing present is a placeholder/pseudo id) — the caller must fail closed to
     * signed-out/guest rather than resurrect a session from stale device state.
     *
     * [Authoritative] carries the ONE identity a restore may use — the Firebase UID.
     * [cachedUidTrusted] is true only when the cached UID is non-blank, not the demo placeholder,
     * and equal to the Firebase UID; otherwise the cached identity was stale and must be
     * discarded before anything reads it.
     */
    sealed class RestoreIdentity {
        object None : RestoreIdentity()
        data class Authoritative(val uid: String, val cachedUidTrusted: Boolean) : RestoreIdentity()
    }

    sealed class DisputeSubmissionState {
        object Idle : DisputeSubmissionState()
        object Submitting : DisputeSubmissionState()
        object Success : DisputeSubmissionState()
        data class Error(val message: String) : DisputeSubmissionState()
    }

    init {
        viewModelScope.launch {
            repository.currentTraining.collect { training ->
                currentTraining.value = training
            }
        }
        viewModelScope.launch {
            repository.getPendingActions().collect { actions ->
                pendingActions.value = actions
            }
        }
        viewModelScope.launch {
            repository.getAllDisputes().collect { disputeList ->
                disputes.value = disputeList
            }
        }
        viewModelScope.launch {
            currentUser.collect { user ->
                val userId = user?.id ?: return@collect
                repository.getNotificationsForUser(userId).collect { notifList ->
                    notifications.value = notifList
                }
            }
        }
        viewModelScope.launch {
            currentUser.collect { user ->
                val userId = user?.id ?: return@collect
                unreadNotificationCount.value = repository.getUnreadNotificationCount(userId)
            }
        }
    }

    fun enqueueOfflineAction(actionType: String, payloadJson: String) {
        viewModelScope.launch {
            repository.enqueueAction(actionType, payloadJson)
        }
    }

    fun flushPendingActions() {
        viewModelScope.launch {
            val pending = repository.getPendingActions()
            // In a real implementation, each action would be processed based on its type
            // For now, we mark them as completed
            pending.collect { actions ->
                actions.forEach { action ->
                    repository.markActionCompleted(action.id)
                }
            }
        }
    }

    fun respondToRecurringProposal(jobId: String, accept: Boolean) {
        viewModelScope.launch {
            repository.respondToRecurringProposal(jobId, accept)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    // ==================== DISP-002: Dispute Methods ====================

    fun submitDispute(
        jobId: String,
        reportedUserId: String,
        reason: DisputeReason,
        description: String,
        evidenceUrls: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            disputeSubmissionState.value = DisputeSubmissionState.Submitting

            try {
                val currentUserId = currentUser.value?.id ?: run {
                    disputeSubmissionState.value = DisputeSubmissionState.Error("User not authenticated")
                    return@launch
                }

                // Check for duplicate dispute
                val existingDispute = repository.getDisputeForJob(jobId)
                if (existingDispute != null && existingDispute.reportedBy == currentUserId) {
                    disputeSubmissionState.value = DisputeSubmissionState.Error("A dispute has already been filed for this job")
                    return@launch
                }

                val dispute = DisputeEntity(
                    id = "dispute_${System.currentTimeMillis()}",
                    jobId = jobId,
                    reportedBy = currentUserId,
                    reportedUserId = reportedUserId,
                    disputeReason = reason.value,
                    description = description,
                    evidenceUrlsCsv = evidenceUrls.joinToString(","),
                    status = DisputeStatus.OPEN.value,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                repository.createDispute(dispute)

                // DISP-005: Notify both parties about new dispute
                val jobPost = repository.jobs.first().find { it.id == jobId }
                val otherPartyId = if (currentUserId == jobPost?.postedByResident) reportedUserId else currentUserId
                repository.createNotification(
                    com.example.data.db.NotificationEntity(
                        id = "notif_${System.currentTimeMillis()}_dispute",
                        userId = otherPartyId,
                        type = "DISPUTE_UPDATE",
                        title = "New Dispute Filed",
                        body = "A dispute has been filed for job: ${jobPost?.title ?: jobId}",
                        jobId = jobId,
                        createdAt = System.currentTimeMillis()
                    )
                )

                // Update job status to DISPUTED
                jobPost?.let {
                    repository.updateJobStatus(jobId, "DISPUTED")
                }

                disputeSubmissionState.value = DisputeSubmissionState.Success
            } catch (e: Exception) {
                disputeSubmissionState.value = DisputeSubmissionState.Error(e.message ?: "Failed to submit dispute")
            }
        }
    }

    fun resetDisputeSubmissionState() {
        disputeSubmissionState.value = DisputeSubmissionState.Idle
    }

    fun getUserDisputes(): Flow<List<DisputeEntity>> {
        val userId = currentUser.value?.id ?: return flowOf(emptyList())
        return repository.getDisputesForUser(userId)
    }

    // ==================== AUTH-001: Phone Authentication Methods ====================

    // AUTH-002: Store phone number for session persistence
    private var pendingPhoneNumber: String? = null

    fun sendOtp(phoneNumber: String, onResult: (Boolean, String?) -> Unit) {
        pendingPhoneNumber = phoneNumber
        viewModelScope.launch {
            authState.value = AuthState.SendingOtp
            authError.value = null
            phoneError.value = null

            if (phoneNumber.isBlank() || phoneNumber.length < 10) {
                val error = "Please enter a valid phone number"
                phoneError.value = error
                authState.value = AuthState.Error(error)
                onResult(false, error)
                return@launch
            }

            try {
                val result = firebaseService.sendPhoneVerificationCode(phoneNumber)
                if (result.isSuccess) {
                    authState.value = AuthState.OtpSent
                    phoneError.value = null
                    onResult(true, null)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to send OTP"
                    authState.value = AuthState.Error(error)
                    authError.value = error
                    onResult(false, error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Failed to send OTP"
                authState.value = AuthState.Error(error)
                authError.value = error
                onResult(false, error)
            }
        }
    }

    fun verifyOtp(otpCode: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            authState.value = AuthState.VerifyingOtp
            authError.value = null
            otpError.value = null

            if (otpCode.length != 6) {
                val error = "Please enter the 6-digit code"
                otpError.value = error
                authState.value = AuthState.Error(error)
                onResult(false, error)
                return@launch
            }

            try {
                val result = firebaseService.verifyPhoneCode(otpCode)
                if (result.isSuccess) {
                    // C2-1: the session owner is the Firebase UID that the SMS-code verification
                    // just produced. It is never a local Room row id and never a placeholder —
                    // verification events that produced no phone-backed identity have already
                    // failed closed inside FirebaseService.
                    val verifiedUser = result.getOrNull() ?: firebaseService.getCurrentUser()
                    val userId = resolveSessionOwnerId(verifiedUser?.uid, firebaseService.getCurrentUser()?.uid)
                    if (userId == null) {
                        isAuthenticated.value = false
                        val error = "Phone verification did not complete. Request a new code and try again."
                        authState.value = AuthState.Error(error)
                        authError.value = error
                        otpError.value = error
                        onResult(false, error)
                        return@launch
                    }
                    val phone = pendingPhoneNumber?.takeIf { it.isNotBlank() }
                        ?: verifiedUser?.phoneNumber?.takeIf { it.isNotBlank() }
                        ?: currentUserPhone.value
                    authState.value = AuthState.SignedIn
                    isAuthenticated.value = true
                    currentUserPhone.value = phone
                    otpError.value = null
                    // AUTH-002: Persist session
                    val deviceId = sessionManager.getDeviceId(appContext)
                    sessionManager.saveSession(
                        isAuthenticated = true,
                        phone = phone,
                        userId = userId,
                        deviceId = deviceId
                    )
                    onResult(true, null)
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Invalid verification code"
                    authState.value = AuthState.Error(error)
                    authError.value = error
                    otpError.value = error
                    onResult(false, error)
                }
            } catch (e: Exception) {
                val error = e.message ?: "Verification failed"
                authState.value = AuthState.Error(error)
                authError.value = error
                otpError.value = error
                onResult(false, error)
            }
        }
    }

    fun retryAuth() {
        authError.value = null
        phoneError.value = null
        otpError.value = null
        if (authState.value is AuthState.Error) {
            authState.value = AuthState.SignedOut
        }
    }

    fun reportUser(report: com.example.data.db.ReportEntity) {
        viewModelScope.launch {
            // C2: reports need a real reporter id — drop unauthenticated reports instead
            // of filing them under an "anonymous" placeholder nobody can audit.
            val currentUserId = currentUser.value?.id?.takeIf { it != DEMO_PROFILE_ID }
                ?: firebaseService.getCurrentUser()?.uid ?: return@launch
            val updatedReport = report.copy(reportedBy = currentUserId)
            repository.reportUser(updatedReport)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val userId = currentUser.value?.id ?: return@launch
            repository.markAllNotificationsRead(userId)
            unreadNotificationCount.value = 0
        }
    }

    fun logoutUser() {
        viewModelScope.launch {
            // AUTH-002/AUTH-006: Mirror the identity record to Firestore BEFORE the
            // device-local wipe. `clearAllUserData()` deletes the Room `user_profiles` row,
            // which used to leave no trace of the account anywhere — that is why a returning
            // Google/phone user looked brand new after logout. The session itself is still
            // fully cleared; only the non-sensitive identity record (uid, phone, name,
            // district, role, onboarding flag) is retained server-side so the account can be
            // recognised again. Worker KYC/activation is never mirrored (admin-controlled).
            repository.currentUser.first()?.let { profile ->
                if (profile.id != DEMO_PROFILE_ID && isAuthenticated.value) {
                    repository.saveUserIdentityToServer(profile)
                }
            }
            repository.clearAllUserData()
            firebaseService.signOut()
            sessionManager.clearSession()
            authState.value = AuthState.SignedOut
            isAuthenticated.value = false
            authError.value = null
            currentUserPhone.value = null
            selectedProvider.value = null
            selectedJob.value = null
            isAuthModalOpen.value = false
            authPendingAction.value = null
            currentTraining.value = null
            fcmToken.value = null
            hasNotificationPermission.value = false
        }
    }

                fun clearAuthError() {
        authError.value = null
        phoneError.value = null
        otpError.value = null
        if (authState.value is AuthState.Error) {
            authState.value = AuthState.SignedOut
        }
    }

    /** AUTH-002: Allows the UI (e.g. the Google sign-in activity-result callback) to
     * surface a failure message so it is never silently swallowed by a stuck spinner. */
    fun reportAuthError(message: String) {
        authError.value = message
    }

    // ==================== NOTIF-002: FCM Methods ====================

    fun initFcm() {
        com.example.data.remote.SkillsExchangeMessagingService.getToken { token ->
            fcmToken.value = token
        }
    }

    fun onFcmTokenRefreshed(token: String) {
        fcmToken.value = token
        // AUTH-002: Persist FCM token
        viewModelScope.launch {
            sessionManager.saveFcmToken(token)
        }
    }

    fun clearFcmToken() {
        com.example.data.remote.SkillsExchangeMessagingService.deleteToken()
        fcmToken.value = null
    }

    // ==================== RATE-002/RATE-003: Rating Methods ====================

    fun submitRating(jobId: String, toUserId: String, rating: Int, review: String, isFromClient: Boolean = true) {
        viewModelScope.launch {
            ratingSubmissionState.value = RatingSubmissionState.Submitting

            try {
                val fromUserId = currentUser.value?.id ?: run {
                    ratingSubmissionState.value = RatingSubmissionState.Error("User not authenticated")
                    return@launch
                }

                // Validate rating
                if (rating < 1 || rating > 5) {
                    ratingSubmissionState.value = RatingSubmissionState.Error("Rating must be between 1 and 5")
                    return@launch
                }

                // Check for duplicate rating
                if (repository.hasUserRatedJob(fromUserId, toUserId, jobId)) {
                    ratingSubmissionState.value = RatingSubmissionState.Error("You have already rated this job")
                    return@launch
                }

                val ratingEntity = com.example.data.db.RatingEntity(
                    id = "rating_${System.currentTimeMillis()}",
                    jobId = jobId,
                    fromUserId = fromUserId,
                    toUserId = toUserId,
                    rating = rating,
                    review = review,
                    isFromClient = isFromClient,
                    createdAt = System.currentTimeMillis()
                )

                repository.submitRating(ratingEntity)
                ratingSubmissionState.value = RatingSubmissionState.Success
            } catch (e: Exception) {
                ratingSubmissionState.value = RatingSubmissionState.Error(e.message ?: "Failed to submit rating")
            }
        }
    }

    fun getAverageRatingForUser(userId: String): Flow<Double> {
        return flow {
            emit(repository.getAverageRatingForUser(userId))
        }
    }

    fun getUserRatings(userId: String): Flow<List<com.example.data.db.RatingEntity>> {
        return repository.getRatingsForUser(userId)
    }

    fun resetRatingSubmissionState() {
        ratingSubmissionState.value = RatingSubmissionState.Idle
    }

    companion object {
        /**
         * Legacy/seed placeholder profile id. It is never an authenticated account and must never
         * satisfy getCurrentUser() or the onboarding "existing account" lookup.
         */
        const val DEMO_PROFILE_ID = "user_default_me"

        /**
         * Authoritative fail-closed evaluation of worker dashboard authorization (SEC-009 / G1 / G3 / H1 / H8).
         *
         * Requirements:
         * 1. Authenticated session (must be true)
         * 2. Real user profile (non-null, non-blank ID, not demo placeholder)
         * 3. Registered as a Worker (initialRole == "WORKER")
         * 4. KYC verification explicitly approved by admin (workerKycStatus == "APPROVED")
         * 5. Mandatory worker training completed (trainingCompleted == true)
         * 6. Account explicitly activated by admin (isWorkerActivated == true)
         *
         * Any missing, unknown, loading, malformed, or error state FAILS CLOSED (returns false).
         */
        fun isWorkerAuthorized(authenticated: Boolean, user: UserProfileEntity?): Boolean {
            if (!authenticated || user == null) return false
            if (user.id.isBlank() || user.id == DEMO_PROFILE_ID) return false
            if (!user.initialRole.equals("WORKER", ignoreCase = true)) return false
            if (user.workerKycStatus != "APPROVED") return false
            if (!user.trainingCompleted) return false
            if (!user.isWorkerActivated) return false
            return true
        }

        /**
         * C2-1: Builds the profile that an authenticated identity owns.
         *
         * The authenticated Firebase UID is the ONLY identity that may own a profile. A record
         * whose id differs from [authenticatedUid] — for example a device row that merely carries
         * the same phone number — is NEVER treated as this account: nothing at all is copied from
         * it, so knowing a phone number can never hand over another person's worker approval,
         * activation, training state, national ID, avatar, backup contact or wallet.
         *
         * Where a record IS owned by [authenticatedUid] (the same account returning after logout,
         * app-data clear, reinstall or on another device) its identity is kept, including state an
         * admin granted server-side — that is legitimate recovery of the same account.
         *
         * New/unowned identities fail closed exactly like worker onboarding: `UNVERIFIED` (WORKER)
         * or `NOT_APPLIED` (CLIENT), `isWorkerActivated = false`, `trainingCompleted = false`; the
         * admin-approved `worker_kyc/{uid}` record remains the gate (see [isWorkerAuthorized]).
         */
        fun resolveAuthenticatedProfile(
            authenticatedUid: String,
            existing: UserProfileEntity?,
            phone: String,
            fullName: String,
            district: String,
            initialRole: String
        ): UserProfileEntity {
            val owned = existing?.takeIf { it.id == authenticatedUid && it.id != DEMO_PROFILE_ID }
            if (owned != null) {
                // Restored identity keeps the REAL Firebase UID as its primary key.
                return owned.copy(
                    id = authenticatedUid,
                    phoneNumber = if (phone.isNotBlank()) phone else owned.phoneNumber,
                    fullName = if (fullName.isNotBlank()) fullName else owned.fullName,
                    district = if (district.isNotBlank()) district else owned.district,
                    isOnboardingCompleted = true,
                    updatedAtTimestamp = System.currentTimeMillis()
                )
            }
            // Unowned (or brand-new) identity: only what this user entered, and fail-closed
            // worker state. Deliberately no copy() of the other record's fields.
            val isWorker = initialRole == "WORKER"
            return UserProfileEntity(
                id = authenticatedUid,
                phoneNumber = phone,
                fullName = fullName,
                district = district,
                initialRole = initialRole,
                currentActiveMode = initialRole,
                workerKycStatus = if (isWorker) "UNVERIFIED" else "NOT_APPLIED",
                isWorkerActivated = false,
                trainingCompleted = false,
                isOnboardingCompleted = true
            )
        }

        /**
         * C2-1: The session owner must be the Firebase UID produced by a real phone verification
         * ([verifiedPhoneAuthUid]). The fallback is only accepted when Firebase has a signed-in
         * user at that moment; when neither exists the caller must fail closed instead of writing
         * a session without an authenticated owner.
         */
        fun resolveSessionOwnerId(verifiedPhoneAuthUid: String?, fallbackFirebaseUid: String?): String? {
            return verifiedPhoneAuthUid?.takeIf { it.isNotBlank() }
                ?: fallbackFirebaseUid?.takeIf { it.isNotBlank() }
        }

        /**
         * C2-2 Fix #2 (F2): decides which identity a session restore is allowed to use.
         *
         * The authoritative identity is ALWAYS the Firebase authenticated UID
         * (`FirebaseAuth.currentUser.uid`). The cached DataStore UID is never an identity on its
         * own: it is only ever compared against Firebase, and a cached UID that is missing, blank,
         * the demo placeholder, or a DIFFERENT account is reported as untrusted so the caller
         * discards it.
         *
         * When Firebase has no authenticated user — or Firebase reports only a
         * placeholder/pseudo id — [RestoreIdentity.None] is returned so the caller fails closed
         * instead of resurrecting an authenticated session from stale DataStore state. There is
         * deliberately no branch that promotes a cached UID, the newest Room row, "unknown", or a
         * synthetic/anonymous id to the authenticated identity.
         */
        fun decideRestoreIdentity(cachedUid: String?, firebaseUid: String?): RestoreIdentity {
            val authoritativeUid = firebaseUid
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != DEMO_PROFILE_ID }
                ?: return RestoreIdentity.None
            val cached = cachedUid?.trim()?.takeIf { it.isNotEmpty() && it != DEMO_PROFILE_ID }
            return RestoreIdentity.Authoritative(
                uid = authoritativeUid,
                cachedUidTrusted = cached == authoritativeUid
            )
        }

        /**
         * C2-2 Fix #2 (F2): the ONLY profile a restored session may adopt.
         *
         * A row is eligible solely when its id equals the authenticated Firebase UID. A row owned
         * by somebody else (`id != authenticatedUid`) — including a NEWER row, a row that merely
         * shares a phone number, or the seeded `user_default_me` placeholder — yields `null`
         * (fail closed). Callers therefore never fall back to "whatever row is newest".
         */
        fun resolveRestoredProfile(
            authenticatedUid: String?,
            row: UserProfileEntity?
        ): UserProfileEntity? {
            val uid = authenticatedUid
                ?.trim()
                ?.takeIf { it.isNotEmpty() && it != DEMO_PROFILE_ID }
                ?: return null
            return row?.takeIf { it.id == uid }
        }
    }
}
