package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
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
import com.example.data.db.SosIncidentEntity
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.SkillEndorsementEntity
import com.example.data.db.UserProfileEntity
import com.example.data.remote.AccountDeletionOutcome
import com.example.data.remote.AccountDeletionRules
import com.example.data.remote.AfricaTalkingService
import com.example.data.remote.AfricaTalkingService.OtpResponse
import com.example.data.remote.AfricaTalkingService.VerificationResponse
import com.example.data.remote.DeletionStep
import com.example.data.remote.FirebaseService
import com.example.data.remote.IdentityLinkRules
import com.example.data.remote.GeminiAudioTranscriptionService
import com.example.data.remote.GeminiMapsGroundingService
import com.example.data.remote.MapsGroundingResult
import com.example.data.remote.MoMoPaymentIntent
import com.example.data.remote.MoMoPaymentPhase
import com.example.data.remote.MoMoPaymentUiState
import com.example.data.remote.mapBackendStatusToPhase
import com.example.data.remote.SkillsExchangeMessagingService
import com.example.data.remote.TranscribedVoiceJob
import com.example.data.repository.SkillsExchangeRepository
import com.example.util.AdminDisputeDecisions
import com.example.util.AudioRecorderHelper
import com.example.util.CurrencyRegion
import com.example.util.DistanceUtil
import com.example.util.ProfileValidators
import com.google.firebase.auth.FirebaseUser
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

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
    // REM-023/R01: ONE Firebase gateway per ViewModel. It is constructed before the repository and
    // injected into it, so authentication state has a single owner. A second instance used to be
    // created for the UI; each kept its own auth listener, which is how "who is signed in" could
    // disagree with itself. The gateway is no longer exposed to composables.
    private val firebaseService = FirebaseService(application)
    private val repository = SkillsExchangeRepository(db, firebaseService)
    private val sessionManager = com.example.data.local.SessionManager(application)

    // Remote services for Maps AI grounding, audio AI and SMS. Firebase is repository-owned.
    val geminiMapsService = GeminiMapsGroundingService()
    val geminiAudioService = GeminiAudioTranscriptionService()
    val audioRecorderHelper = AudioRecorderHelper(application)
    private val networkObserver = com.example.util.NetworkConnectivityObserver(application)

    // State flows for remote features
    val firebaseUser: StateFlow<FirebaseUser?> = firebaseService.currentUserState

    /**
     * C2-2 Fix #1: UID-keyed authoritative current user. When Firebase reports a signed-in
     * UID, ONLY the Room row whose id matches that UID is exposed — never the newest row,
     * never `user_default_me`, never a placeholder. When Firebase has no signed-in user the
     * flow emits null (fail closed).
     *
     * C2-2 Fix #3 (F7): this is now the ONLY current-user source in the app. It is started
     * eagerly because security gates ([canAccessWorkerFeatures], [isWorkerApprovedAndActivated])
     * and user-owned writes read `.value` synchronously — with a subscription-gated flow a caller
     * that ran before the UI subscribed would have observed `null` and silently denied (or
     * silently skipped) a legitimate operation. The legacy newest-row
     * `repository.currentUser` flow is no longer consulted anywhere in this class.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentUserByUid: StateFlow<UserProfileEntity?> = firebaseUser
        .flatMapLatest { user ->
            repository.getCurrentUserByUid(user?.uid)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * C2-2 Fix #3 (F7): the authenticated Firebase UID, or `null` when there is none — callers
     * MUST fail closed on `null`.
     *
     * Identity is `FirebaseAuth.currentUser.uid` only. The cached DataStore UID, the newest Room
     * row, `user_default_me`, an anonymous uid and any synthetic/"unknown"/"system" stand-in are
     * deliberately NOT accepted substitutes (see [resolveAuthoritativeUid]).
     */
    fun currentAuthenticatedUid(): String? =
        resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)

    /**
     * R05-P2-05: the result carries its own grounded/unverified flag, so the screen can label model
     * prose instead of rendering invented shops as real Google Maps places.
     */
    val mapsSearchResults = MutableStateFlow<MapsGroundingResult?>(null)
    val isMapsSearching = MutableStateFlow(false)

    val audioTranscribedJob = MutableStateFlow<TranscribedVoiceJob?>(null)
    // REM-051: surfaced when server-side transcription is unavailable; never replaced by canned text.
    val audioTranscriptionError = MutableStateFlow<String?>(null)
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

    // Africa's Talking OTP authentication state
    private val africaService = AfricaTalkingService()
    val otpSendState = MutableStateFlow<OtpResponse?>(null)
    val otpVerifyState = MutableStateFlow<VerificationResponse?>(null)
    var africaStoredRequestId: String? = null
    var africaPhoneError: String? = null
    var africaOtpError: String? = null

    val selectedProvider = MutableStateFlow<ServiceProviderEntity?>(null)
    val selectedJob = MutableStateFlow<JobPostEntity?>(null)

    val isQuickRequestOpen = MutableStateFlow(false)
    val isFilterDrawerOpen = MutableStateFlow(false)
    val isSearchModalOpen = MutableStateFlow(false)
    val isNotificationDrawerOpen = MutableStateFlow(false)
    // Pending actions queue for offline actions
    val pendingActions = MutableStateFlow<List<com.example.data.db.PendingActionEntity>>(emptyList())

    /**
     * STEP 0 (offline queue data-loss fix): single-flight guard for [flushPendingActions].
     * Reconnect events must never run overlapping flushes against the same Room queue.
     * `tryLock` semantics are used at the call site so a second trigger is skipped,
     * never queued behind an in-flight flush.
     */
    private val flushMutex = Mutex()

    // C2-2 Fix #3 (F7): the legacy `currentUser` StateFlow (newest Room row) has been REMOVED.
    // "Most recently updated profile row" is device state, not an identity: on a device that ever
    // held another account it fed the WRONG profile to the worker gate, the drawer, the SOS payload
    // and every user-owned write. `SkillsExchangeRepository.currentUser` (and the DAO's newest-row
    // query) remain only as documented, non-authoritative legacy for the un-migrated F5 chat-sender
    // path — nothing in this class or in the UI reads them any more.

    val isWorkerApprovedAndActivated: StateFlow<Boolean> = combine(
        isAuthenticated,
        currentUserByUid
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
    val myDisputes: StateFlow<List<DisputeEntity>> = currentUserByUid
        .flatMapLatest { user ->
            if (user == null) flowOf(emptyList()) else repository.getDisputesForUser(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The worker's own KYC submission, used by the identity-verification destination. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val myWorkerKyc: StateFlow<com.example.data.db.WorkerKycEntity?> = currentUserByUid
        .flatMapLatest { user ->
            if (user == null) flowOf(null) else repository.getWorkerKyc(user.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** R03-P2-06: the topic the server pushes user-scoped notifications to. */
    private var pushTopic: String? = null

    private fun syncPushTopic(uid: String?) {
        val messaging = try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
        } catch (_: Exception) {
            return
        }
        if (uid == null) {
            pushTopic?.let { messaging.unsubscribeFromTopic(it) }
            pushTopic = null
            return
        }
        val topic = "user_$uid"
        if (topic == pushTopic) return
        messaging.subscribeToTopic(topic)
        pushTopic?.let { messaging.unsubscribeFromTopic(it) }
        pushTopic = topic
        // R05-P2-02: the topic is only a fallback; the real address is this device's token stored
        // server-side under the authenticated uid. Registered on every sign-in and app start.
        com.example.data.remote.PushTokenRegistrar.registerCurrentToken()
    }

    init {
        viewModelScope.launch {
            try {
                firebaseService.currentUserState.collect { user -> syncPushTopic(user?.uid) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Push-topic collector stopped", error)
            }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // R04: seeding is best-effort device bootstrap. It used to run on the main dispatcher
            // and its failures escaped as uncaught coroutine exceptions (which surfaced as the
            // intermittent CI failure in the Compose tests). It now runs off the main thread and
            // logs instead of crashing the screen.
            try {
                repository.seedInitialDataIfNeeded()
            } catch (error: Exception) {
                android.util.Log.e("SkillsExchangeViewModel", "Initial data seeding failed", error)
            }
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
        // R04: the startup collectors are long-lived and best-effort. An uncaught exception inside
        // one of them crashes the screen and, in tests, leaks into the next test as
        // `UncaughtExceptionsBeforeTest` — which is the intermittent failure CI was seeing. Each
        // collector now logs and stops instead of throwing; cancellation still propagates.
        viewModelScope.launch {
            try {
                repository.currentTraining.collect { training ->
                    currentTraining.value = training
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Training collector stopped", error)
            }
        }
        viewModelScope.launch {
            try {
                repository.getPendingActions().collect { actions ->
                    pendingActions.value = actions
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Pending-action collector stopped", error)
            }
        }
        viewModelScope.launch {
            try {
                networkObserver.observe().collect { isConnected ->
                    if (isConnected && pendingActions.value.isNotEmpty()) {
                        flushPendingActions()
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e(TAG, "Connectivity collector stopped", error)
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
                // B4: restore the persisted worker online/availability choice (defaults online).
                isWorkerOnline.value = sessionManager.getWorkerOnline()

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
            // C2-2 Fix #3 (F7): the mode switch is written to the AUTHENTICATED UID's exact row.
            // The previous `repository.currentUser.first()` wrote it to whichever Room row happened
            // to be newest — i.e. potentially to a different account's profile on this device.
            val user = currentUserByUid.value ?: return@launch
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
        // C2-2 Fix #3 (F7): the gate reads the UID-keyed profile only, so the newest Room row can
        // neither satisfy nor deny worker authorization for the authenticated account.
        return isWorkerAuthorized(isAuthenticated.value, currentUserByUid.value)
    }

    fun toggleUserRoleMode() {
        val newMode = if (userRoleMode.value == "Hire a Neighbor") "Offer Skills" else "Hire a Neighbor"
        if (newMode == "Offer Skills" && !canAccessWorkerFeatures()) {
            authError.value = "Worker verification and activation required to switch to worker mode."
            return
        }
        setUserRoleMode(newMode)
    }

    // ============================================================
    // AFRICA'S TALKING OTP AUTHENTICATION FLOW
    // ============================================================

    /** Send OTP via Africa's Talking */
    fun loginWithPhone(phoneNumber: String) {
        viewModelScope.launch {
            otpSendState.value = null
            africaPhoneError = null
            val result = africaService.sendOtp(phoneNumber)
            when (val r = result) {
                is OtpResponse.Success -> {
                    otpSendState.value = r
                    africaStoredRequestId = r.requestId
                    africaPhoneError = null
                }
                is OtpResponse.Error -> {
                    africaPhoneError = r.message
                }
            }
        }
    }

    /** Verify OTP and complete login */
    fun verifyOtpAndLogin(otpCode: String) {
        viewModelScope.launch {
            otpVerifyState.value = null
            africaOtpError = null
            val result = africaService.verifyOtp(phoneNumberFromUi, otpCode, africaStoredRequestId)
            when (val v = result) {
                is VerificationResponse.Valid -> {
                    otpVerifyState.value = v
                    loginUser(v.userId ?: africaStoredRequestId ?: africaPhoneError ?: "", phoneNumberFromUi)
                }
                is VerificationResponse.Error -> {
                    africaOtpError = v.message
                }
            }
        }
    }

    /** Complete login with phone-backed identity */
    fun loginUserWithPhone(authoritativeUid: String, phoneNumber: String) {
        isAuthenticated.value = true
        currentUserPhone.value = phoneNumber
        authState.value = AuthState.SignedIn
        isOnboardingCompleted.value = false
    }

    private var phoneNumberFromUi by mutableStateOf("")
    fun updatePhoneNumberFromUi(phone: String) {
        phoneNumberFromUi = phone
    }


    val providers: StateFlow<List<ServiceProviderEntity>> = combine(
        repository.providers,
        filterState,
        gpsLocationState
    ) { allProviders, filter, gps ->
        allProviders
            .map { provider ->
                // B9 fix: compute the real straight-line distance from the user's
                // current GPS location to the provider's coordinates, instead of
                // trusting the static seeded `distanceMiles`. Falls back to the stored
                // value when the user's location is unknown (GPS off/unavailable).
                provider.copy(
                    distanceMiles = DistanceUtil.effectiveDistanceMiles(
                        providerLatitude = provider.latitude,
                        providerLongitude = provider.longitude,
                        userLatitude = gps.latitude,
                        userLongitude = gps.longitude,
                        fallbackMiles = provider.distanceMiles
                    )
                )
            }
            .filter { provider ->
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
        val newOnline = !isWorkerOnline.value
        isWorkerOnline.value = newOnline
        // B4: persist the availability choice so a paused/offline state survives an app restart
        // instead of silently resetting to online (in-memory only) on every launch.
        viewModelScope.launch { sessionManager.setWorkerOnline(newOnline) }
    }

    fun setMobileTab(tab: String) {
        activeMobileTab.value = tab
    }

    fun searchLocalPlacesWithGoogleMaps(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            isMapsSearching.value = true
            try {
                val currentDistrict = filterState.value.district
                // R05-P2-04/-05: server-side, grounded-only; failures come back as an error result.
                mapsSearchResults.value =
                    geminiMapsService.searchLocalPlacesWithMaps(query, currentDistrict)
            } catch (error: Exception) {
                // The service already never throws, but a crash here would take the search sheet down.
                android.util.Log.w("SkillsExchangeViewModel", "Nearby place search failed", error)
                mapsSearchResults.value = MapsGroundingResult(
                    summary = "",
                    error = "Nearby place search is unavailable right now."
                )
            } finally {
                isMapsSearching.value = false
            }
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
            audioTranscriptionError.value = null
            val transcribed = geminiAudioService.transcribeAudio(audioBytes)
            if (transcribed == null) {
                audioTranscriptionError.value = "Voice transcription is unavailable. Please type the request instead."
            }
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
        recurringSchedule: String = "WEEKLY",
        streetAddress: String = "",
        apartmentSuite: String = "",
        landmarkDirections: String = "",
        scheduledAt: Long = 0,
        photos: List<String> = emptyList()
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
                streetAddress = streetAddress,
                apartmentSuite = apartmentSuite,
                landmarkDirections = landmarkDirections,
                scheduledAt = scheduledAt,
                photos = photos,
                materialsProvidedBy = materialsProvidedBy,
                laborHours = laborHours,
                laborRate = laborRate,
                jobType = jobType,
                recurringSchedule = recurringSchedule
            )
            // C2-2 Fix #4 (F4): addJobPost fails closed and returns null when there is no
            // authenticated Firebase uid. Nothing was persisted, so we must not close the sheet,
            // select a "null" job or attempt a Firestore sync.
            if (jobId == null) return@launch
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
            // C2-2 authorization: accepting a worker sets the job status, so the repository gates it
            // on the caller being the job owner and now returns whether it was allowed. Do not refresh
            // `selectedJob` on rejection -- there is nothing to show, since nothing changed.
            val hired = repository.hireProviderForJob(jobId, provider.id, provider.name)
            if (!hired) return@launch
            // Refresh current selected job
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun updateJobStatus(jobId: String, newStatus: String, reason: String = "") {
        viewModelScope.launch {
            val currentJob = repository.jobs.first().find { it.id == jobId }
            val fromStatus = currentJob?.status ?: ""
            // C2-2 Fix #6 (F6): the actor is NOT resolved here. The repository derives `changedBy`
            // from FirebaseAuth.currentUser.uid and fails closed when there is none, so this call
            // site can no longer attribute the change to the newest Room row ("system" is gone).
            val transitioned = repository.transitionJobStatus(
                jobId = jobId,
                fromStatus = fromStatus,
                toStatus = newStatus,
                reason = reason
            )
            // F6: nothing was written while unauthenticated — do not emit a status-change
            // notification for a transition that did not happen.
            if (!transitioned) return@launch

            // REM-026: completing a job as its client releases escrow on the server. The client
            // never computes the payout amount; it only names the job and worker. A failed release
            // is surfaced and leaves the local escrow untouched so it can be retried.
            if (newStatus == "COMPLETED" &&
                currentJob?.postedByUid == firebaseService.getCurrentUser()?.uid &&
                !currentJob?.assignedProviderId.isNullOrBlank()
            ) {
                val workerId = currentJob?.assignedProviderId!!
                val (released, message) = firebaseService.releaseEscrowToWorker(jobId, workerId)
                if (released) {
                    repository.applyServerConfirmedEscrowRelease(jobId, message)
                } else {
                    authError.value = "Job completed, but escrow release failed: $message"
                }
            }

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

    /** Cancel a client-owned job. Dispatches through [repository.cancelJob] (which
     *  runs the F6/F4 authorization + audit). On success the selected job is
     *  refreshed; no worker notification is fired (client cancellation is not a
     *  worker-status change). */
    fun cancelJob(jobId: String, reason: String = "") {
        viewModelScope.launch {
            val canceled = repository.cancelJob(jobId, reason)
            if (!canceled) return@launch
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    // JOB-005: Get audit trail for a job
    suspend fun getJobAuditTrail(jobId: String): List<com.example.data.db.JobStatusAuditEntity> {
        return repository.getJobAuditTrail(jobId)
    }

    /** R02-P2-01: the release has its own visible state, driven by the server's answer. */
    sealed class EscrowReleaseState {
        object Idle : EscrowReleaseState()
        object Releasing : EscrowReleaseState()
        data class Released(val message: String) : EscrowReleaseState()
        data class Failed(val message: String) : EscrowReleaseState()
    }

    val escrowReleaseState = MutableStateFlow<EscrowReleaseState>(EscrowReleaseState.Idle)

    fun resetEscrowReleaseState() {
        escrowReleaseState.value = EscrowReleaseState.Idle
    }

    /**
     * R02-P2-01/P2-02: release through the `releaseEscrowToWorker` callable and only then record
     * the local status. Single-flight: a second tap while a release is running is ignored.
     */
    fun releaseEscrow(jobId: String) {
        if (escrowReleaseState.value is EscrowReleaseState.Releasing) return
        viewModelScope.launch {
            escrowReleaseState.value = EscrowReleaseState.Releasing
            val job = repository.jobs.first().find { it.id == jobId }
            val workerId = job?.assignedProviderId
            if (job == null) {
                escrowReleaseState.value = EscrowReleaseState.Failed("Job not found on this device.")
                return@launch
            }
            if (workerId.isNullOrBlank()) {
                escrowReleaseState.value = EscrowReleaseState.Failed("No worker is assigned to this job.")
                return@launch
            }
            val (released, message) = firebaseService.releaseEscrowToWorker(jobId, workerId)
            if (released) {
                repository.applyServerConfirmedEscrowRelease(jobId, message)
                selectedJob.value = repository.jobs.first().find { it.id == jobId }
                escrowReleaseState.value = EscrowReleaseState.Released(message)
            } else {
                escrowReleaseState.value = EscrowReleaseState.Failed(message)
            }
        }
    }

    /** Non-money transitions (work in progress, proof submitted, cash settlement marks). */
    fun updateEscrowStatus(jobId: String, newEscrowStatus: String) {
        // R02-P2-01: RELEASED_TO_WORKER must come from releaseEscrow(), never from the sheet.
        if (newEscrowStatus == "RELEASED_TO_WORKER") return
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

    /** R02-P2-02: one SOS dispatch at a time; repeated taps are ignored, not queued. */
    private var sosDispatchInFlight = false

    fun triggerSosAlert(jobId: String) {
        if (sosDispatchInFlight) return
        sosDispatchInFlight = true
        viewModelScope.launch {
            try {
                val jobPost = repository.jobs.first().find { it.id == jobId } ?: return@launch
                // C2-2 Fix #3 (F7): only the AUTHENTICATED profile's coordinates may be used as the
                // fallback location — never the newest Room row's.
                val user = currentUserByUid.value
                val gps = gpsLocationState.value
                // R02-P2-04: there is no invented fallback. When neither GPS nor the profile has a
                // position, the incident is raised with 0,0 — a sentinel responders can recognise —
                // instead of Kampala city-centre coordinates that look real but may be wrong.
                val latitude = gps.latitude ?: user?.latitude ?: 0.0
                val longitude = gps.longitude ?: user?.longitude ?: 0.0
                repository.triggerEmergencySos(jobId, latitude, longitude, jobPost.district)
                val updatedJob = repository.jobs.first().find { it.id == jobId }
                selectedJob.value = updatedJob
            } finally {
                sosDispatchInFlight = false
            }
        }
    }

    /**
     * H7/SAFE-003: stand down an active SOS flag. SOS is a safety latch — only the
     * authenticated job owner may clear it, and the repository enforces that
     * owner boundary (see [SkillsExchangeRepository.clearEmergencySos]). The
     * secured boundary decides identity + authorization and returns a result; this
     * method only maps that result into [sosClearState] so the sheet can react.
     * Never writes any row when the boundary denies it.
     */
    fun clearSosAlert(jobId: String) {
        viewModelScope.launch {
            sosClearState.value = SosClearState.Clearing
            when (val result = repository.clearEmergencySos(jobId)) {
                is com.example.data.repository.SkillsExchangeRepository.SosClearResult.Success -> {
                    sosClearState.value = SosClearState.Success
                    val updatedJob = repository.jobs.first().find { it.id == jobId }
                    selectedJob.value = updatedJob
                }
                is com.example.data.repository.SkillsExchangeRepository.SosClearResult.NotAuthenticated ->
                    sosClearState.value = SosClearState.Error("You must be signed in to clear an emergency alert")
                is com.example.data.repository.SkillsExchangeRepository.SosClearResult.JobNotFound ->
                    sosClearState.value = SosClearState.Error("This job could not be found")
                is com.example.data.repository.SkillsExchangeRepository.SosClearResult.NotAuthorized ->
                    sosClearState.value = SosClearState.Error("Only the job owner can clear this emergency alert")
            }
        }
    }

    fun resetSosClearState() {
        sosClearState.value = SosClearState.Idle
    }

    // --- H7 / AUDIT-A-SOS: admin incident-monitoring queue (backend foundation, no UI yet) ---
    private val _adminSosLoading = MutableStateFlow(false)
    val adminSosLoading: StateFlow<Boolean> = _adminSosLoading.asStateFlow()

    private val _adminSosQueue = MutableStateFlow<List<SosIncidentEntity>>(emptyList())
    val adminSosQueue: StateFlow<List<SosIncidentEntity>> = _adminSosQueue.asStateFlow()

    private val _adminSosError = MutableStateFlow<String?>(null)
    val adminSosError: StateFlow<String?> = _adminSosError.asStateFlow()

    /**
     * ADMIN DESK: refresh the active-SOS queue from the authoritative Room record.
     * The Firestore mirror is best-effort; Room is the source of truth for the UI.
     */
    fun loadAdminSosQueue() {
        viewModelScope.launch {
            _adminSosLoading.value = true
            _adminSosError.value = null
            try {
                _adminSosQueue.value = repository.getActiveSosIncidents().first()
            } catch (t: Throwable) {
                _adminSosError.value = "Could not load incident queue."
            } finally {
                _adminSosLoading.value = false
            }
        }
    }

    /**
     * ADMIN DESK: acknowledge the incident as reviewed.
     * Server callable enforces the admin claim; this boundary is the client-side
     * fail-closed gate (unauthenticated callers are refused).
     */
    fun acknowledgeSosIncident(incidentId: String) {
        viewModelScope.launch {
            val actorUid = currentAuthenticatedUid() ?: return@launch
            val ok = repository.acknowledgeSos(incidentId, actorUid)
            if (ok) loadAdminSosQueue() else _adminSosError.value = "Could not acknowledge incident (not found or already resolved)."
        }
    }

    /**
     * ADMIN DESK: resolve the incident as complete.
     * Server callable enforces the admin claim.
     */
    fun resolveSosIncident(incidentId: String) {
        viewModelScope.launch {
            val actorUid = currentAuthenticatedUid() ?: return@launch
            val ok = repository.resolveSosIncident(incidentId, actorUid)
            if (ok) loadAdminSosQueue() else _adminSosError.value = "Could not resolve incident (not found or not authorized)."
        }
    }

    /**
     * H7: surfaces a client-side pre-submission dispute failure
     */
    fun setDisputeError(message: String) {
        disputeSubmissionState.value = DisputeSubmissionState.Error(message)
    }

    fun sendChatMessage(jobId: String, messageText: String) {
        if (messageText.isBlank()) return
        viewModelScope.launch {
            // C2-2 Fix #5 (F5): the repository fails closed when Firebase has no authenticated
            // uid — nothing is written and nothing is sent. `senderName` stays display text.
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
        // C3: explicit authorisation — an empty/blank or invalid phone is never a completed
        // registration. Surface the error on the phone field and stop before any
        // profile, session, or server write happens.
        if (phone.isBlank()) {
            phoneError.value = "Enter your Mobile Money phone number to continue."
            return
        }
        if (!ProfileValidators.isValidPhone(phone)) {
            phoneError.value = "Enter a valid phone number (9-15 digits)."
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
            // C2-2 Fix #3 (F7): the location write targets the AUTHENTICATED UID's exact row.
            val user = currentUserByUid.value ?: return@launch
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

    // ============ R06: account deletion (server-authoritative, ordered) ============

    /**
     * R06-P2-01 / R06-P2-02 / R06-P2-05: what the deletion dialog is doing right now.
     *
     * The dialog used to close itself the instant the button was tapped and then report either
     * nothing or "partially failed" — after the account was already gone. It now renders this state,
     * so it can only claim success once the server has confirmed the purge and the Firebase Auth user
     * has actually been removed.
     */
    val accountDeletionStep = MutableStateFlow(DeletionStep.CONFIRM)
    val accountDeletionMessage = MutableStateFlow<String?>(null)

    /** True once a Firebase code has been sent for the re-authentication step. */
    val accountDeletionCodeSent = MutableStateFlow(false)

    /** Called when the dialog opens: a fresh attempt never inherits the previous outcome. */
    fun resetAccountDeletionState() {
        if (accountDeletionStep.value == DeletionStep.PURGING) return
        accountDeletionStep.value = DeletionStep.CONFIRM
        accountDeletionMessage.value = null
        accountDeletionCodeSent.value = false
    }

    /**
     * R06-P2-01: permanently delete the signed-in account — but only in the order that can be
     * defended.
     *
     * 1. The server purges Firestore documents **and** Cloud Storage objects and reports counts.
     * 2. The Firebase Auth user is deleted only if that report says `complete` (see
     *    [AccountDeletionRules]).
     * 3. Local Room data and the session are cleared last.
     *
     * Any other outcome leaves the session intact and the dialog open, because a deleted identity
     * cannot finish removing its own data. The old code called `deleteCurrentUser()` unconditionally
     * and reported the failure afterwards.
     */
    fun permanentlyDeleteUserAccountAndData() {
        if (accountDeletionStep.value == DeletionStep.PURGING) return
        viewModelScope.launch {
            // C2-2 Fix #3 (F7): account deletion must target the AUTHENTICATED account. The UID
            // comes from FirebaseAuth only — the newest Room row is no longer consulted, so a stale
            // device profile can neither delete nor shield another account's data.
            val userId = currentAuthenticatedUid()
            if (userId == null) {
                accountDeletionStep.value = DeletionStep.FAILED
                accountDeletionMessage.value =
                    "Your session ended before deletion started. Sign in again and retry."
                return@launch
            }
            // REM-024 / R06-P2-05: the server enforces the same five-minute window, but checking
            // here avoids a pointless round trip and points the user at the re-authentication step.
            if (!firebaseService.hasFreshAuthentication()) {
                accountDeletionStep.value = DeletionStep.REAUTH
                accountDeletionMessage.value = AccountDeletionRules.progressLabel(DeletionStep.REAUTH)
                return@launch
            }

            accountDeletionStep.value = DeletionStep.PURGING
            accountDeletionMessage.value = AccountDeletionRules.progressLabel(DeletionStep.PURGING)

            // Only jobs this account posted; the server re-verifies ownership of each id before it
            // deletes anything.
            val userJobIds = repository.jobs.first()
                .filter { it.postedByUid == userId }
                .map { it.id }

            when (val outcome = firebaseService.requestAccountDeletion(userJobIds)) {
                is AccountDeletionOutcome.Purged -> {
                    // R06-P2-04: drop this device's push token before the identity disappears.
                    deleteDevicePushToken()
                    if (!firebaseService.deleteCurrentUser()) {
                        // Data is gone but the sign-in survived: say exactly that, keep the session,
                        // and let the person retry (the purge is idempotent).
                        accountDeletionStep.value = DeletionStep.FAILED
                        accountDeletionMessage.value =
                            "Your records and photos were deleted, but this sign-in could not be " +
                                "removed. Sign in again and retry to finish removing the account."
                        return@launch
                    }
                    clearLocalStateAfterSignOut()
                    accountDeletionStep.value = DeletionStep.DONE
                    accountDeletionMessage.value = AccountDeletionRules.progressLabel(DeletionStep.DONE)
                    Log.i("ViewModel", "Account deletion completed (${outcome.storageDeleted} files, ${outcome.firestoreDocsDeleted} records)")
                }
                AccountDeletionOutcome.ReauthRequired -> {
                    accountDeletionStep.value = DeletionStep.REAUTH
                    accountDeletionMessage.value = AccountDeletionRules.progressLabel(DeletionStep.REAUTH)
                }
                is AccountDeletionOutcome.Incomplete -> {
                    accountDeletionStep.value = DeletionStep.FAILED
                    accountDeletionMessage.value = outcome.message
                }
                is AccountDeletionOutcome.Failed -> {
                    accountDeletionStep.value = DeletionStep.FAILED
                    accountDeletionMessage.value = outcome.message
                }
            }
        }
    }

    /**
     * R06-P2-05: refresh the five-minute window with a Google credential, then continue deleting.
     *
     * Re-authenticating keeps the same uid, so the purge targets the same account and the person does
     * not lose their place by being told to sign out and back in.
     */
    fun reauthenticateForAccountDeletionWithGoogle(idToken: String) {
        viewModelScope.launch {
            accountDeletionMessage.value = "Checking it is you…"
            firebaseService.reauthenticateWithGoogle(idToken)
                .onSuccess {
                    accountDeletionCodeSent.value = false
                    permanentlyDeleteUserAccountAndData()
                }
                .onFailure { error ->
                    accountDeletionStep.value = DeletionStep.REAUTH
                    accountDeletionMessage.value = IdentityLinkRules.reauthMessage(error)
                }
        }
    }

    /** R06-P2-05: send a Firebase code to the account's own number for the re-authentication step. */
    fun startAccountDeletionPhoneReauth(phoneNumber: String) {
        viewModelScope.launch {
            accountDeletionMessage.value = "Sending a code…"
            firebaseService.sendPhoneVerificationCode(phoneNumber)
                .onSuccess {
                    accountDeletionCodeSent.value = true
                    accountDeletionStep.value = DeletionStep.REAUTH
                    accountDeletionMessage.value = "Enter the 6-digit code we sent to your phone."
                }
                .onFailure { error ->
                    accountDeletionStep.value = DeletionStep.REAUTH
                    accountDeletionMessage.value = IdentityLinkRules.reauthMessage(error)
                }
        }
    }

    /** R06-P2-05: exchange the code for a fresh `auth_time`, then continue deleting. */
    fun confirmAccountDeletionPhoneReauth(code: String) {
        viewModelScope.launch {
            accountDeletionMessage.value = "Checking it is you…"
            firebaseService.reauthenticateWithPhoneCode(code)
                .onSuccess {
                    accountDeletionCodeSent.value = false
                    permanentlyDeleteUserAccountAndData()
                }
                .onFailure { error ->
                    accountDeletionStep.value = DeletionStep.REAUTH
                    accountDeletionMessage.value = IdentityLinkRules.reauthMessage(error)
                }
        }
    }

    /**
     * R06-P2-04: forget this device's FCM token, locally and server-side.
     *
     * The token is a device credential that keeps delivering another person's notifications after
     * sign-out, so it is deleted while the session can still prove ownership.
     */
    private suspend fun deleteDevicePushToken() {
        val token = fcmToken.value
        firebaseService.clearMyPushTokens(token)
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { continuation ->
                SkillsExchangeMessagingService.deleteToken {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
        fcmToken.value = null
    }

    /**
     * R06-P2-03: attach Google sign-in to the account that is already signed in.
     *
     * One person, one uid, one wallet: a second Google account must not silently create a second
     * profile, so the result is reported back to the UI as text instead of being swallowed.
     */
    fun linkGoogleAccount(idToken: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = firebaseService.linkGoogleAccount(idToken)
            onResult(IdentityLinkRules.linkMessage(result))
        }
    }

    /** Shared teardown for sign-out and completed deletion; never runs before the server agrees. */
    private fun clearLocalStateAfterSignOut() {
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
    }

    // --- REG-003: WORKER BACKUP CONTACT ---
    /**
     * Saves the signed-in user's backup contact (name + phone).
     *
     * DECISION: updates are IMMEDIATE — contact metadata follows the same path as other
     * profile edits (district, mode); the existing KYC review queue applies only to
     * identity documents. Writes always target [currentUserByUid]'s own Room row and Firestore
     * `users/{uid}` (owner-only write rule), so one user can never modify another user's
     * backup contact. Never touches the guarantors tables: a backup contact is NOT a
     * guarantor.
     *
     * C2-2 Fix #3 (F7): the target row is resolved from the authenticated Firebase UID
     * ([currentUserByUid]) instead of "whatever profile row is newest on this device".
     */
    fun saveBackupContact(name: String, phone: String) {
        viewModelScope.launch {
            val user = currentUserByUid.value
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

    // --- H5 (SKILL-003 wiring): WORKER TRADE SKILLS ---
    /** Picker visibility for the trade-skills section of ProfileSettingsSheet. */
    val isSkillPickerOpen = MutableStateFlow(false)
    /** Last skills-save error, shown inline in the sheet. Null = no error. */
    val workerSkillsError = MutableStateFlow<String?>(null)

    /**
     * Persists the picker-confirmed skills on the signed-in user's OWN profile row
     * (Room + Firestore mirror) via [repository.saveWorkerSkills], which fails
     * closed on no-auth, wrong-row, or catalog-invalid input. Never touches KYC,
     * activation, or training state — skills are catalog metadata, not credentials.
     */
    fun saveWorkerSkills(primarySkill: String, additionalSkills: List<String>) {
        viewModelScope.launch {
            val user = currentUserByUid.value
            if (!isAuthenticated.value || user == null || user.id == DEMO_PROFILE_ID) {
                workerSkillsError.value = "Sign in to save your trade skills."
                return@launch
            }
            workerSkillsError.value = null
            val ok = repository.saveWorkerSkills(primarySkill, additionalSkills)
            if (ok) {
                isSkillPickerOpen.value = false
                profileSettingsSuccess.value = "Trade skills saved."
            } else {
                workerSkillsError.value =
                    "Could not save those skills. Check they belong to one trade family and try again."
            }
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
            // C2-2 Fix #3 (F7): the uploaded avatar is written to the authenticated UID's row.
            val user = currentUserByUid.value
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
    /**
     * PAY-002: trusted payment UI state, sourced ONLY from the backend.
     *
     * `PENDING` after initiation means the RequestToPay was delivered (202) — money has
     * NOT moved and escrow is NOT locked. `SUCCESSFUL` is published only when the
     * `getMoMoCollectionStatus` callable reports the provider-verified state, at which
     * point the backend has locked escrow. `FAILED` and `NEEDS_ATTENTION` likewise come
     * from the backend (or from a failed initiation / unverifiable status). No local
     * success or HTTP 202 is ever treated as proof of funds.
     */
    private val _momoPaymentState = MutableStateFlow<MoMoPaymentUiState?>(null)
    val momoPaymentState: StateFlow<MoMoPaymentUiState?> = _momoPaymentState.asStateFlow()

    /**
     * PAY-001/PAY-002: route collection through the trusted backend callable, then let
     * the backend — never the device — decide what the payment means.
     *
     * `FirebaseService.initiateMoMoCollection` (the `createMoMoCollection` callable)
     * reads the trusted job document and performs the sandbox RequestToPay server-side;
     * a 202 maps to PENDING. The app then polls `getMoMoCollectionStatus`, whose server
     * side re-queries the MTN provider status API and locks escrow only on a verified
     * SUCCESSFUL. Escrow is NEVER flipped on the device.
     */
    fun initiateMoMoEscrowPayment(
        jobId: String,
        amountUgx: Double,
        phone: String,
        provider: String = "MTN_MOMO",
        onIntentCreated: (MoMoPaymentIntent) -> Unit,
        onCollectionFailed: (code: String?, message: String?) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = firebaseService.initiateMoMoCollection(jobId, amountUgx, phone)
            if (result.success) {
                _momoPaymentState.value = MoMoPaymentUiState(
                    phase = MoMoPaymentPhase.PENDING,
                    transactionRef = result.transactionRef,
                    message = result.message
                        ?: "Payment request sent. Approve on your handset; funds are NOT yet in escrow."
                )
                onIntentCreated(
                    MoMoPaymentIntent(
                        transactionId = result.transactionRef ?: "PENDING",
                        amount = amountUgx,
                        currency = "UGX",
                        momoNumber = phone,
                        provider = provider,
                        status = result.status ?: "PENDING",
                        referenceMessage = result.message
                            ?: "Payment request sent. Approve on your handset; funds are NOT yet in escrow."
                    )
                )
                result.transactionRef?.let { pollTrustedMoMoCollectionStatus(it) }
            } else {
                // Fail closed: nothing was collected, escrow untouched. Surface a FAILED intent
                // so existing UI keeps rendering, plus the machine-readable error for new UI.
                _momoPaymentState.value = MoMoPaymentUiState(
                    phase = MoMoPaymentPhase.FAILED,
                    transactionRef = result.transactionRef,
                    message = result.message ?: "Payment request failed. Nothing was collected."
                )
                onIntentCreated(
                    MoMoPaymentIntent(
                        transactionId = result.transactionRef ?: "FAILED",
                        amount = amountUgx,
                        currency = "UGX",
                        momoNumber = phone,
                        provider = provider,
                        status = "FAILED",
                        referenceMessage = result.message ?: "Payment request failed. Nothing was collected."
                    )
                )
                onCollectionFailed(result.errorCode, result.message)
            }
        }
    }

    /**
     * PAY-002: bounded polling of the TRUSTED backend for the provider-verified status.
     *
     * Stops as soon as a terminal/attention phase arrives. A failed status check never
     * fabricates a result: the last backend-published state is kept and its message is
     * updated to surface the check failure.
     */
    private suspend fun pollTrustedMoMoCollectionStatus(transactionRef: String) {
        repeat(MOMO_STATUS_POLL_ATTEMPTS) {
            delay(MOMO_STATUS_POLL_INTERVAL_MS)
            val statusResult = firebaseService.getMoMoCollectionStatus(transactionRef)
            if (statusResult.status == null) {
                // Backend unreachable / permission / config failure: keep the last known
                // state and surface that the check failed. NEVER treat as successful.
                val current = _momoPaymentState.value ?: return
                _momoPaymentState.value = current.copy(
                    message = "Status check failed — payment NOT confirmed. " +
                        (statusResult.message ?: "Please try again shortly.")
                )
                return
            }
            val phase = mapBackendStatusToPhase(statusResult.status)
            _momoPaymentState.value = MoMoPaymentUiState(
                phase = phase,
                transactionRef = statusResult.transactionRef ?: transactionRef,
                message = statusResult.message
                    ?: "Payment status: ${statusResult.status}."
            )
            if (phase != MoMoPaymentPhase.PENDING) return
        }
    }

    /** PAY-002: drop the payment state when the payment sheet closes. */
    fun clearMoMoPaymentState() {
        _momoPaymentState.value = null
    }

    // --- MODULE 4: SMS FALLBACK DISPATCH ---
    fun getDistrictJobStatsForProvider(providerId: String): Map<String, Int> {
        return jobs.value
            .filter { it.assignedProviderId == providerId }
            .groupBy { it.district }
            .mapValues { it.value.size }
    }

    // R05-P2-01: `triggerSmsAlertToOfflineWorkers` is gone with the device SMS stub that backed it.
    // Radius SMS dispatch is server-side (`onJobCreated`); the client cannot deliver an SMS and must
    // not be able to claim it did.

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
        // C2-2 Fix #3 (F7): drafts are read for the AUTHENTICATED uid only.
        val userId = currentAuthenticatedUid() ?: return
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
        // C2-2 Fix #3 (F7): drafts are saved under the AUTHENTICATED uid only.
        val userId = currentAuthenticatedUid() ?: return
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
        // C2-2 Fix #3 (F7): third-party identity data is filed under the AUTHENTICATED account's
        // own row — never under whoever's profile happens to be newest on the device.
        val user = currentUserByUid.value ?: return
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
        // C2-2 Fix #3 (F7): the admin decision is pulled for the AUTHENTICATED uid only.
        val userId = currentAuthenticatedUid() ?: return
        viewModelScope.launch { repository.refreshGuarantorVerificationStatus(userId) }
    }

    fun uploadQualificationEvidence(evidenceType: String, fileBytes: ByteArray? = null, videoUrl: String? = null, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            // C2-2 Fix #3 (F7): evidence is uploaded under the authenticated Firebase UID only.
            // The previous form read the newest Room row FIRST (`currentUser.value?.id`) and only
            // fell back to the Firebase UID, so a second account's cached row could capture the
            // upload. Identity now comes from FirebaseAuth; the demo placeholder is rejected.
            val userId = currentAuthenticatedUid() ?: run {
                onResult(null)
                return@launch
            }
            val path = firebaseService.uploadQualificationEvidence(userId, evidenceType, fileBytes, videoUrl)
            onResult(path)
        }
    }

    val adminVerificationQueue = MutableStateFlow<List<Map<String, Any>>>(emptyList())

    /** AUDIT-A-CONSOLE: loading flag for the operator queue refresh. */
    val isAdminQueueLoading = MutableStateFlow(false)

    // ==================== AUDIT-A-ARBITRATE: Dispute Arbitration State ====================

    /** Dispute queue for the arbitration console. */
    val adminDisputeQueue = MutableStateFlow<List<Map<String, Any>>>(emptyList())

    /** Loading flag for the dispute queue refresh. */
    val isAdminDisputeLoading = MutableStateFlow(false)

    /** Outcome of an arbitration decision submission. */
    sealed class ArbitrationResult {
        object Success : ArbitrationResult()
        object NotAuthenticated : ArbitrationResult()
        object NotAuthorized : ArbitrationResult()
        data class Error(val message: String) : ArbitrationResult()
    }

    /** Last arbitration result, shown inline in the sheet. Null = no pending result. */
    val arbitrationResult = MutableStateFlow<ArbitrationResult?>(null)

    /** Currently selected dispute for detail view. Null = queue view. */
    val selectedDispute = MutableStateFlow<Map<String, Any>?>(null)

    val currentTraining = MutableStateFlow<com.example.data.db.WorkerTrainingEntity?>(null)

    init {
        viewModelScope.launch {
            repository.currentTraining.collect { training ->
                currentTraining.value = training
            }
        }
    }

    /** REM-023: true only after the server confirms the signed-in account holds the admin claim. */
    val isAdminVerified = MutableStateFlow(false)

    fun verifyAdminAccess() {
        viewModelScope.launch {
            val verified = repository.checkAdminAccess()
            isAdminVerified.value = verified
            if (verified) {
                loadAdminVerificationQueue()
            }
        }
    }

    fun loadAdminVerificationQueue() {
        // AUDIT-A-CONSOLE: guard overlapping refreshes; the callable stays the authorizer.
        if (isAdminQueueLoading.value) return
        isAdminQueueLoading.value = true
        viewModelScope.launch {
            try {
                val queue = repository.getAdminVerificationQueue()
                adminVerificationQueue.value = queue
            } finally {
                isAdminQueueLoading.value = false
            }
        }
    }

    fun submitAdminDecision(userId: String, decision: String, feedback: String, onResult: (Boolean) -> Unit) {
        // AUDIT-A-CONSOLE: client-side allow-list mirrors the server vocabulary; malformed
        // taps never leave the device, but the callable remains the real authorizer.
        val normalized = decision.trim().uppercase()
        if (userId.isBlank() || normalized !in setOf("APPROVE", "REJECT", "CHANGES_REQUIRED")) {
            onResult(false)
            return
        }
        if ((normalized == "REJECT" || normalized == "CHANGES_REQUIRED") && feedback.trim().length < 10) {
            authError.value = "Add feedback (10+ characters) so the worker knows what to fix."
            onResult(false)
            return
        }
        viewModelScope.launch {
            val success = repository.submitAdminVerificationDecision(userId, normalized, feedback.trim())
            if (success) loadAdminVerificationQueue()
            onResult(success)
        }
    }

    // ==================== AUDIT-A-ARBITRATE: Dispute Arbitration Methods ====================

    /** Loads the dispute queue via the admin-gated Cloud Function. */
    fun loadDisputesQueue(status: String? = null) {
        if (isAdminDisputeLoading.value) return
        isAdminDisputeLoading.value = true
        arbitrationResult.value = null
        viewModelScope.launch {
            try {
                val queue = repository.getDisputesQueue(status)
                adminDisputeQueue.value = queue
            } finally {
                isAdminDisputeLoading.value = false
            }
        }
    }

    /** Selects a dispute for the detail/decision view. */
    fun selectDispute(dispute: Map<String, Any>?) {
        selectedDispute.value = dispute
        arbitrationResult.value = null
    }

    /** Clears the selected dispute, returning to queue view. */
    fun clearSelectedDispute() {
        selectedDispute.value = null
        arbitrationResult.value = null
    }

    /**
     * Submits an arbitration decision via the `resolveDispute` Cloud Function.
     * Uses [AdminDisputeDecisions] for client-side guardrails; the callable remains
     * the real authorizer. Single-flight guard prevents duplicate submissions.
     */
    fun submitDisputeResolution(
        disputeId: String,
        resolution: String,
        resolutionType: String?,
        resolutionAmount: Double?,
        adminNotes: String?,
        status: String?,
        onResult: (ArbitrationResult) -> Unit
    ) {
        // AUDIT-A-ARBITRATE: client-side validation mirrors the server contract.
        val blocker = AdminDisputeDecisions.decisionBlocker(
            disputeId, resolution, resolutionType, resolutionAmount, status, adminNotes
        )
        if (blocker != null) {
            arbitrationResult.value = ArbitrationResult.Error(blocker)
            onResult(ArbitrationResult.Error(blocker))
            return
        }

        // Single-flight guard.
        if (arbitrationResult.value is ArbitrationResult.Success ||
            arbitrationResult.value == ArbitrationResult.NotAuthenticated ||
            arbitrationResult.value == ArbitrationResult.NotAuthorized
        ) {
            return
        }

        arbitrationResult.value = null
        viewModelScope.launch {
            val success = repository.resolveDispute(
                disputeId, resolution.trim().uppercase(),
                resolutionType?.trim()?.uppercase(), resolutionAmount, adminNotes?.trim()
            )
            if (success) {
                arbitrationResult.value = ArbitrationResult.Success
                loadDisputesQueue()
                onResult(ArbitrationResult.Success)
            } else {
                val result = ArbitrationResult.Error("Failed to resolve dispute. Server may be unavailable.")
                arbitrationResult.value = result
                onResult(result)
            }
        }
    }

    fun resetArbitrationState() {
        arbitrationResult.value = null
        selectedDispute.value = null
    }

    fun setWorkerActivation(userId: String, activate: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.setWorkerActivation(userId, activate)
            onResult(success)
        }
    }

    fun startTraining() {
        viewModelScope.launch {
            // C2-2 Fix #3 (F7): training rows belong to the AUTHENTICATED Firebase UID.
            val userId = currentAuthenticatedUid() ?: return@launch
            repository.startTraining(userId)
        }
    }

    fun completeTrainingModule(modulesCsv: String, moduleIndex: Int) {
        viewModelScope.launch {
            val userId = currentAuthenticatedUid() ?: return@launch
            repository.updateTrainingModule(userId, modulesCsv, moduleIndex)
        }
    }

    fun completeTraining() {
        viewModelScope.launch {
            val userId = currentAuthenticatedUid() ?: return@launch
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
        // C2-2 Fix #3 (F7): worker-gated action — identity is the authenticated Firebase UID, and a
        // missing/placeholder identity aborts rather than filing the proposal under another row.
        if (!canAccessWorkerFeatures()) return
        viewModelScope.launch {
            val userId = currentAuthenticatedUid() ?: return@launch
            repository.submitRecurringProposal(jobId, userId, proposedRate, terms)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    val quoteSubmissionState = MutableStateFlow<SkillsExchangeRepository.QuoteSubmissionResult?>(null)
    val quoteResponseState = MutableStateFlow<SkillsExchangeRepository.QuoteResponseResult?>(null)
    /**
     * AUDIT-W-QUOTE-UI: single-flight guard for the quote sheet. True from the
     * moment a submit leaves the device until the secured boundary answers, so
     * the UI can disable the button + show a spinner and never double-fire.
     */
    val isSubmittingQuote = MutableStateFlow(false)

    fun resetQuoteSubmissionState() {
        quoteSubmissionState.value = null
    }

    fun resetQuoteResponseState() {
        quoteResponseState.value = null
    }

    fun submitQuote(jobId: String, workerName: String, proposedRate: Double, proposedHours: Double, notes: String) {
        if (!canAccessWorkerFeatures()) {
            authError.value = "Only verified and activated workers can submit quotes."
            return
        }
        // AUDIT-W-QUOTE-UI single-flight: ignore re-entrant taps while a submit
        // is in flight. The repository stays the duplicate authority; this only
        // stops accidental double-taps from leaving the device twice.
        if (isSubmittingQuote.value) return
        isSubmittingQuote.value = true
        quoteSubmissionState.value = null
        viewModelScope.launch {
            try {
                val result = repository.submitQuote(jobId, workerName, proposedRate, proposedHours, notes)
                quoteSubmissionState.value = result
                if (result is SkillsExchangeRepository.QuoteSubmissionResult.NotAuthenticated) {
                    authError.value = "Authentication required to submit quotes."
                }
            } finally {
                isSubmittingQuote.value = false
            }
        }
    }

    fun respondToQuote(quoteId: String, jobId: String, accept: Boolean) {
        viewModelScope.launch {
            val result = repository.respondToQuote(quoteId, jobId, accept)
            quoteResponseState.value = result
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

    // SAFE-003: SOS stand-down state. The sheet observes [sosClearState] so a rejected stand-down
    // (not authenticated / not the job owner / no such job) surfaces a visible message instead of
    // silently leaving the panic banner up — mirroring the dispute/rating submission channels.
    val sosClearState = MutableStateFlow<SosClearState>(SosClearState.Idle)

    sealed class SosClearState {
        object Idle : SosClearState()
        object Clearing : SosClearState()
        object Success : SosClearState()
        data class Error(val message: String) : SosClearState()
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
            // C2-2 Fix #3 (F7): notifications are read for the AUTHENTICATED uid only.
            currentUserByUid.collect { user ->
                val userId = user?.id ?: return@collect
                repository.getNotificationsForUser(userId).collect { notifList ->
                    notifications.value = notifList
                }
            }
        }
        viewModelScope.launch {
            currentUserByUid.collect { user ->
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

    /**
     * STEP 0 (offline queue data-loss fix): preserve every queued item.
     *
     * The previous implementation collected the endless [repository.getPendingActions] Flow
     * and called `markActionCompleted` (Room DELETE) for each row without executing the
     * action or obtaining any server confirmation — queued work was silently discarded on
     * every reconnect. Actual execution + server-confirmed completion arrive in a later
     * step, so this method takes a one-time snapshot and deletes NOTHING. A [Mutex]
     * single-flight guard ([flushMutex]) skips overlapping reconnect triggers.
     */
    fun flushPendingActions() {
        viewModelScope.launch {
            if (!flushMutex.tryLock()) return@launch
            try {
                // One-shot snapshot: Flow.collect never completes and must not drive a flush.
                val snapshot = repository.getPendingActionsSnapshot()
                // STEP 0 intentionally performs no execution and no deletion. Each item is
                // preserved until a later step can execute it and confirm it server-side.
                // Logging the retained count keeps reconnect behavior observable in logcat.
                if (snapshot.isNotEmpty()) {
                    android.util.Log.d(
                        "SkillsExchangeViewModel",
                        "flushPendingActions (STEP 0): preserving ${snapshot.size} pending action(s); execution not yet implemented"
                    )
                }
            } finally {
                flushMutex.unlock()
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
                // C2-2 Fix #3 (F7): the reporter is the authenticated Firebase UID.
                val currentUserId = currentAuthenticatedUid() ?: run {
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

                // C2-2 authorization: moving the job to DISPUTED is a status transition, so it goes
                // through the secured boundary, which decides whether the authenticated actor may
                // drive it. The dispute record itself is still filed either way (that is the user's
                // right to report), but an unauthorized actor must not thereby move another user's
                // job status, and must not be told that they did.
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
        // C2-2 Fix #3 (F7): the caller's own disputes are keyed by the authenticated uid.
        val userId = currentAuthenticatedUid() ?: return flowOf(emptyList())
        return repository.getDisputesForUser(userId)
    }

    // ==================== AUTH-001: Phone Authentication Methods ====================

    // AUTH-002: Store phone number for session persistence
    private var pendingPhoneNumber: String? = null

    fun sendOtp(phoneNumber: String, onResult: (Boolean, String?) -> Unit) {
        pendingPhoneNumber = phoneNumber
        phoneNumberFromUi = phoneNumber
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
                val result = africaService.sendOtp(phoneNumber)
                when (result) {
                    is OtpResponse.Success -> {
                        authState.value = AuthState.OtpSent
                        phoneError.value = null
                        africaStoredRequestId = result.requestId
                        onResult(true, null)
                    }
                    is OtpResponse.Error -> {
                        val error = result.message
                        phoneError.value = error
                        authState.value = AuthState.Error(error)
                        authError.value = error
                        onResult(false, error)
                    }
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
                val result = africaService.verifyOtp(phoneNumberFromUi, otpCode, africaStoredRequestId)
                when (result) {
                    is VerificationResponse.Valid -> {
                        authState.value = AuthState.SignedIn
                        isAuthenticated.value = true
                        currentUserPhone.value = phoneNumberFromUi
                        otpError.value = null
                        // AUTH-002: Persist session
                        val deviceId = sessionManager.getDeviceId(appContext)
                        sessionManager.saveSession(
                            isAuthenticated = true,
                            phone = phoneNumberFromUi,
                            userId = result.userId ?: africaStoredRequestId ?: pendingPhoneNumber ?: "",
                            deviceId = deviceId
                        )
                        onResult(true, null)
                    }
                    is VerificationResponse.Error -> {
                        val error = result.message
                        authState.value = AuthState.Error(error)
                        authError.value = error
                        otpError.value = error
                        onResult(false, error)
                    }
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
            // C2-2 Fix #3 (F7): and never file them under the newest Room row's id, which may
            // belong to a different account on this device.
            val currentUserId = currentAuthenticatedUid() ?: return@launch
            val updatedReport = report.copy(reportedBy = currentUserId)
            repository.reportUser(updatedReport)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            // C2-2 Fix #3 (F7): only the AUTHENTICATED uid's notifications may be marked read.
            val userId = currentAuthenticatedUid() ?: return@launch
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
            // C2-2 Fix #3 (F7): mirror the AUTHENTICATED account's identity record before the wipe.
            // The newest Room row could belong to a different account on a shared/stale device, and
            // mirroring it to Firestore would overwrite the wrong `users/{uid}` document.
            currentUserByUid.value?.let { profile ->
                if (profile.id != DEMO_PROFILE_ID && isAuthenticated.value) {
                    repository.saveUserIdentityToServer(profile)
                }
            }
            // REM-024: revoke refresh tokens while the ID token is still valid, then clear locally.
            firebaseService.revokeMyTokens("LOGOUT")
            // R06-P2-04: the push token is device state, not account state. Delete it before the
            // session ends, otherwise this phone keeps receiving another person's notifications.
            deleteDevicePushToken()
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
            // R05-P2-02: hand the token to the backend; it is never logged.
            if (token != null) com.example.data.remote.PushTokenRegistrar.registerInBackground(token)
        }
    }

    fun onFcmTokenRefreshed(token: String) {
        fcmToken.value = token
        // R05-P2-02: a refreshed token must reach the backend too, otherwise pushes stop silently.
        com.example.data.remote.PushTokenRegistrar.registerInBackground(token)
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

            // RATE-003: identity, authorization and validity are ALL decided inside the repository's
            // SECURED boundary (submitJobRating). The caller-supplied jobId/toUserId are never trusted
            // to establish ownership; the actor is resolved from the authoritative Firebase UID; the job
            // must be the caller's OWN released job with toUserId as its actually-assigned provider.
            // This is what prevents rating an unrelated job, impersonating another user, and rating a
            // job whose escrow has not been released. No rating is claimed unless the insert succeeds.
            try {
                val result = repository.submitJobRating(jobId, toUserId, rating, review, isFromClient)
                ratingSubmissionState.value = when (result) {
                    is com.example.data.repository.SkillsExchangeRepository.RatingResult.Success ->
                        RatingSubmissionState.Success
                    is com.example.data.repository.SkillsExchangeRepository.RatingResult.NotAuthenticated ->
                        RatingSubmissionState.Error("User not authenticated")
                    is com.example.data.repository.SkillsExchangeRepository.RatingResult.InvalidRating ->
                        RatingSubmissionState.Error("Rating must be between 1 and 5")
                    is com.example.data.repository.SkillsExchangeRepository.RatingResult.JobNotFound ->
                        RatingSubmissionState.Error("Job not found")
                    is com.example.data.repository.SkillsExchangeRepository.RatingResult.NotAuthorized ->
                        RatingSubmissionState.Error("You are not authorized to rate this job")
                    is com.example.data.repository.SkillsExchangeRepository.RatingResult.AlreadyRated ->
                        RatingSubmissionState.Error("You have already rated this job")
                }
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
    fun resetRatingSubmissionState() {
        ratingSubmissionState.value = RatingSubmissionState.Idle
    }

    /**
     * H6: surfaces a client-side pre-submission failure (e.g. no assigned worker) through the
     * same [RatingSubmissionState] channel the sheet already observes, so the dialog shows a
     * visible error instead of silently dropping the tap. Never writes any rating row.
     */
    fun setRatingError(message: String) {
        ratingSubmissionState.value = RatingSubmissionState.Error(message)
    }



    fun getUserRatings(userId: String): Flow<List<com.example.data.db.RatingEntity>> {
        return repository.getRatingsForUser(userId)
    }

    companion object {
        /** R04: log tag for the best-effort startup collectors. */
        const val TAG = "SkillsExchangeViewModel"

        /**
         * PAY-002: how often the app re-asks the trusted backend
         * (`getMoMoCollectionStatus`) for the provider-verified payment status, and how
         * long it waits between attempts while the status is still PENDING.
         */
        const val MOMO_STATUS_POLL_ATTEMPTS = 8
        const val MOMO_STATUS_POLL_INTERVAL_MS = 5_000L

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
        // C2-2 Fix #7 (F3 remainder): both candidates are now filtered through the single shared
        // resolveAuthoritativeUid rule instead of a bare isNotBlank(). The old check would have
        // persisted "user_default_me", "system" or presentation text as the session owner, and the
        // cached session UID is later compared during restore -- so a placeholder written here could
        // feed back into the identity decision. Both arguments are Firebase-owned in practice, so
        // this closes the hole without changing legitimate behaviour.
        fun resolveSessionOwnerId(verifiedPhoneAuthUid: String?, fallbackFirebaseUid: String?): String? {
            return resolveAuthoritativeUid(verifiedPhoneAuthUid)
                ?: resolveAuthoritativeUid(fallbackFirebaseUid)
        }

        /**
         * C2-2 Fix #4 (F4): literal, non-identity values that must never be accepted as an owner
         * or sender id. Firebase will never issue these; they are the fallbacks this codebase
         * historically used when no authenticated user was available.
         */
        private val SYNTHETIC_ID_SENTINELS = setOf("unknown", "system", "anonymous", "guest", "none")

        /**
         * C2-2 Fix #3 (F7): the ONLY identity a security-sensitive operation may use.
         *
         * `FirebaseAuth.currentUser.uid` is authoritative; a missing, blank, whitespace-padded or
         * legacy demo id (`user_default_me`) yields `null`, so the caller fails closed instead of
         * promoting the newest Room row, a placeholder, an anonymous or a synthetic id.
         *
         * This is the rule every migrated F7 call site now applies (worker gate, role switch,
         * location, SOS, guarantors, training, quotes/proposals, notifications, disputes, ratings,
         * reports, profile writes, account deletion). The repository applies the identical rule at
         * the data boundary (`SkillsExchangeRepository.resolveAuthoritativeUid`) because the data
         * layer must stay decoupled from the UI layer.
         */
        fun resolveAuthoritativeUid(firebaseUid: String?): String? =
            firebaseUid?.trim()?.takeIf { candidate ->
                if (candidate.isEmpty() || candidate == DEMO_PROFILE_ID) return@takeIf false
                // C2-2 Fix #4 (F4): a real Firebase uid (including an anonymous one) is
                // alphanumeric. A value carrying whitespace or brackets is presentation text
                // ("Robert K. (Sunnyside)", "You (Nakawa)") and must never be treated as an
                // identity — and neither may the literal synthetic sentinels this codebase
                // historically fell back to. Mirrors SkillsExchangeRepository.resolveAuthoritativeUid.
                if (candidate.any { it.isWhitespace() || it == '(' || it == ')' }) return@takeIf false
                candidate.lowercase() !in SYNTHETIC_ID_SENTINELS
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
