package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.JobPostEntity
import com.example.data.db.NeighborhoodPostEntity
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

    private val db = AppDatabase.getDatabase(application)
    private val repository = SkillsExchangeRepository(db)

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

    val mapsSearchResults = MutableStateFlow<Pair<String, List<GroundedPlaceResult>>?>(null)
    val isMapsSearching = MutableStateFlow(false)

    val audioTranscribedJob = MutableStateFlow<TranscribedVoiceJob?>(null)
    val isAudioRecording = MutableStateFlow(false)
    val isAudioTranscribing = MutableStateFlow(false)

    val filterState = MutableStateFlow(FilterState())
    val gpsLocationState = MutableStateFlow(GpsLocationState())

    val userRoleMode = MutableStateFlow("Hire a Neighbor") // "Hire a Neighbor" vs "Offer Skills"
    val activeMobileTab = MutableStateFlow("List") // "List", "Map", "Jobs", "Board"
    val themeMode = MutableStateFlow("LIGHT") // "LIGHT", "DARK", "SYSTEM"
    val isNavigationDrawerOpen = MutableStateFlow(false)
    val isWorkerOnline = MutableStateFlow(true)

    // Progressive Authentication & Gated Credential state
    val isAuthenticated = MutableStateFlow(false)
    val currentUserPhone = MutableStateFlow<String?>(null)
    val isAuthModalOpen = MutableStateFlow(false)
    val authPendingAction = MutableStateFlow<String?>(null)

    val selectedProvider = MutableStateFlow<ServiceProviderEntity?>(null)
    val selectedJob = MutableStateFlow<JobPostEntity?>(null)

    val isQuickRequestOpen = MutableStateFlow(false)
    val isFilterDrawerOpen = MutableStateFlow(false)
    val isSearchModalOpen = MutableStateFlow(false)
    val isNotificationDrawerOpen = MutableStateFlow(false)

    val currentUser: StateFlow<UserProfileEntity?> = repository.currentUser
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isWorkerApprovedAndActivated: StateFlow<Boolean> = repository.currentUser.map { user ->
        user != null && user.isWorkerActivated && user.workerKycStatus == "APPROVED"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        viewModelScope.launch {
            repository.seedInitialDataIfNeeded()
            // Sync initial mode from user profile
            repository.currentUser.first()?.let { profile ->
                userRoleMode.value = if (profile.currentActiveMode == "WORKER") "Offer Skills" else "Hire a Neighbor"
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

    fun setUserRoleMode(mode: String) {
        userRoleMode.value = mode
        viewModelScope.launch {
            val user = repository.currentUser.first() ?: return@launch
            val dbMode = if (mode == "Offer Skills") "WORKER" else "CLIENT"
            repository.switchActiveMode(user.id, dbMode)
        }
    }

    fun toggleUserRoleMode() {
        val newMode = if (userRoleMode.value == "Hire a Neighbor") "Offer Skills" else "Hire a Neighbor"
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

    val neighborhoodPosts: StateFlow<List<NeighborhoodPostEntity>> = repository.neighborhoodPosts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        isWorkerOnline.update { !it }
    }

    fun setUserRoleMode(mode: String) {
        userRoleMode.value = mode
    }

    fun toggleUserRoleMode() {
        userRoleMode.update {
            if (it == "Hire a Neighbor") "Offer Skills" else "Hire a Neighbor"
        }
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

    fun updateJobStatus(jobId: String, newStatus: String) {
        viewModelScope.launch {
            repository.updateJobStatus(jobId, newStatus)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
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
            val job = repository.jobs.first().find { it.id == jobId } ?: return@launch
            val user = repository.currentUser.first()
            val gps = gpsLocationState.value
            val latitude = gps.latitude ?: user?.latitude ?: 0.3476
            val longitude = gps.longitude ?: user?.longitude ?: 32.5825
            repository.triggerEmergencySos(jobId, latitude, longitude, job.district)
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

    fun closeAuthModal() {
        isAuthModalOpen.value = false
        authPendingAction.value = null
    }

    fun loginUser(phone: String, fullName: String = "Sarah Namukasa", district: String = "Sunnyside") {
        isAuthenticated.value = true
        currentUserPhone.value = phone
        val action = authPendingAction.value
        closeAuthModal()

        viewModelScope.launch {
            firebaseService.signInAnonymously()
            val existing = repository.currentUser.first()
            val profile = existing?.copy(
                phoneNumber = phone,
                fullName = fullName,
                district = district,
                updatedAtTimestamp = System.currentTimeMillis()
            ) ?: UserProfileEntity(
                phoneNumber = phone,
                fullName = fullName,
                district = district,
                initialRole = "CLIENT",
                currentActiveMode = "CLIENT"
            )
            repository.saveUserProfile(profile)
            setDistrict(district)
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

    fun logoutUser() {
        isAuthenticated.value = false
        currentUserPhone.value = null
        firebaseService.signOut()
    }

    fun permanentlyDeleteUserAccountAndData() {
        viewModelScope.launch {
            isAuthenticated.value = false
            currentUserPhone.value = null
            firebaseService.signOut()
            // Reset state
            selectedProvider.value = null
            selectedJob.value = null
        }
    }

    // --- MODULE 1: ESCROW STATE MACHINE TRANSITIONS ---
    fun transitionJobState(jobId: String, newStatus: String, newEscrowStatus: String) {
        viewModelScope.launch {
            val job = repository.jobs.first().find { it.id == jobId } ?: return@launch
            val updated = job.copy(
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

    fun isValidGuarantorCount(workerKycId: String): Boolean {
        return repository.validateGuarantorCount(workerKycId)
    }

    fun uploadQualificationEvidence(evidenceType: String, fileBytes: ByteArray? = null, videoUrl: String? = null, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val userId = currentUser.value?.id ?: "user_default_me"
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
            val userId = currentUser.value?.id ?: "user_default_me"
            repository.startTraining(userId)
        }
    }

    fun completeTrainingModule(modulesCsv: String, moduleIndex: Int) {
        viewModelScope.launch {
            val userId = currentUser.value?.id ?: "user_default_me"
            repository.updateTrainingModule(userId, modulesCsv, moduleIndex)
        }
    }

    fun completeTraining() {
        viewModelScope.launch {
            val userId = currentUser.value?.id ?: "user_default_me"
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
        viewModelScope.launch {
            val userId = currentUser.value?.id ?: "worker_default"
            repository.submitRecurringProposal(jobId, userId, proposedRate, terms)
            val updatedJob = repository.jobs.first().find { it.id == jobId }
            selectedJob.value = updatedJob
        }
    }

    fun submitQuote(jobId: String, workerName: String, proposedRate: Double, proposedHours: Double, notes: String) {
        viewModelScope.launch {
            val userId = currentUser.value?.id ?: "worker_default"
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

    val pendingActions = MutableStateFlow<List<com.example.data.db.PendingActionEntity>>(emptyList())

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
}
