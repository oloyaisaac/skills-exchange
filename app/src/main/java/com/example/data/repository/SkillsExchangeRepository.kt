@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.example.data.repository

import android.util.Log
import android.net.Uri
import com.example.data.db.*
import com.example.data.remote.DoorstepPinResult
import com.example.data.remote.DoorstepPinRules
import com.example.data.remote.FirebaseService
import com.example.data.remote.ServerCashoutResult
import com.example.data.remote.ServerRefundStatus
import com.example.data.remote.ServerWalletService
import com.example.data.remote.ServerWalletSnapshot
import com.example.util.CurrencyRegion
import com.example.util.JobMoney
import com.example.util.Money
import com.example.util.GuarantorInput
import com.example.util.GuarantorValidators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class SkillsExchangeRepository(
    private val db: AppDatabase,
    private val firebaseService: FirebaseService = FirebaseService()
) {
    /**
     * R08-P2-02: the server wallet. Constructed lazily so unit tests that never touch money do not
     * need Firebase Functions initialised.
     */
    private val serverWallet: ServerWalletService by lazy { ServerWalletService() }
    // R04-P2-06: both feeds are viewer-scoped. Providers must be KYC-approved to be listed; jobs
    // are open work plus the viewer's own or assigned jobs. Neither flow is an authorization
    // boundary — every mutation still reloads the row and checks the actor.
    val providers: Flow<List<ServiceProviderEntity>> = db.serviceProviderDao().getApprovedProviders()
    val jobs: Flow<List<JobPostEntity>> = firebaseService.currentUserState.flatMapLatest { user ->
        db.jobPostDao().getJobsVisibleTo(user?.uid.orEmpty())
    }
    /**
     * LEGACY / NON-AUTHORITATIVE (C2-2 Fix #3, audit finding **F7**): "the most recently updated
     * profile row on this device" is a cache of device state — it is NOT an identity. It must never
     * decide who is signed in, who owns a write, or who an action is attributed to. Use
     * [getCurrentUserByUid] with `FirebaseAuth.currentUser.uid` instead.
     *
     * After the F7 sweep the SOS notification/alert paths, the worker authorization gate,
     * notifications, ratings, disputes, guarantors, training, profile writes and account deletion
     * all resolve the authenticated user through the UID-keyed path.
     *
     * After F6 there are **no** remaining production consumers of this newest-row flow; it survives
     * only as a documented legacy helper. Job authorship (F4) uses `JobPostEntity.postedByUid`, chat
     * senders (F5) and job-status attribution (F6) are all resolved from
     * `FirebaseAuth.currentUser.uid`.
     */
    val currentUser: Flow<UserProfileEntity?> = db.userProfileDao().getCurrentUser()
    val currentTraining: Flow<WorkerTrainingEntity?> = db.workerTrainingDao().getCurrentUserTraining()
    val serviceTaxonomyCatalog: List<ServiceTaxonomyItem> = ServiceTaxonomyCatalog.services

    fun getServicesByCategory(category: String): List<ServiceTaxonomyItem> {
        return ServiceTaxonomyCatalog.getServicesByCategory(category)
    }

    fun validateWorkerSkills(primarySkill: String, additionalSkills: List<String>): SkillValidationResult {
        return ServiceTaxonomyCatalog.validateWorkerSkills(primarySkill, additionalSkills)
    }

    fun getUserProfile(userId: String): Flow<UserProfileEntity?> {
        return db.userProfileDao().getUserProfile(userId)
    }

    /**
     * C2-2 Fix #1: UID-keyed current-user lookup. When [firebaseUid] is available it is the
     * authoritative identity — only the Room row whose id matches is returned, never the
     * newest row, never `user_default_me`, never a placeholder. Returns a null-emitting Flow
     * when no UID is available or no row matches (caller must fail closed).
     */
    // C2-2 Fix #7 (F3 remainder): this previously carried its OWN weaker rule
    // (isNotBlank() && != "user_default_me"), i.e. a COMPETING identity resolver. It accepted
    // whitespace-padded values, presentation text such as "John Doe" / "You (Nakawa)", and the
    // literal synthetic sentinels ("system", "unknown", "anonymous", "guest", "none") -- none of
    // which Firebase can ever issue. Because SkillsExchangeViewModel.currentUserByUid (the app's
    // only current-user source) flows through here, that divergence was a real identity-binding
    // gap. It now delegates to the single shared resolveAuthoritativeUid rule, so every
    // authenticated path enforces exactly one definition of "valid identity".
    fun getCurrentUserByUid(firebaseUid: String?): Flow<UserProfileEntity?> {
        val uid = resolveAuthoritativeUid(firebaseUid)
            ?: return kotlinx.coroutines.flow.flowOf(null)
        return db.userProfileDao().getCurrentUserById(uid)
    }

    /**
     * C2-2 Fix #3 (F7): the authenticated user's OWN profile row, or `null` (fail closed).
     *
     * The identity is `FirebaseAuth.currentUser.uid` (see [resolveAuthoritativeUid]); the row must
     * match that uid exactly. The legacy newest-row [currentUser] query is deliberately NOT
     * consulted, so no security-sensitive operation can resolve "whoever was updated last".
     */
    private suspend fun authenticatedProfileOnce(): UserProfileEntity? {
        val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid) ?: return null
        return db.userProfileDao().getCurrentUserById(uid).first()
    }

    /**
     * C2-2 Fix #4 (F4): "my jobs" — the jobs owned by the AUTHENTICATED account.
     *
     * Keyed exclusively on `postedByUid` vs `FirebaseAuth.currentUser.uid`. The display string
     * `postedByResident` is never compared, and jobs with a NULL `postedByUid` (pre-F4 rows with
     * no provable owner) are excluded — they belong to nobody, so they never appear as anybody's.
     * With no authenticated uid this returns an empty list (fail closed).
     */
    fun getMyJobs(): Flow<List<JobPostEntity>> {
        val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.jobPostDao().getJobsPostedBy(uid)
    }

    /**
     * C2-2 Fix #4 (F4): ownership predicate for edit/cancel/manage/authorize decisions.
     *
     * Returns `false` when there is no authenticated uid, when [job]'s `postedByUid` is NULL
     * (unknown legacy owner) or when it names a different account. There is no display-string
     * fallback and no newest-Row fallback — both would let one account act on another's job.
     */
    fun isJobOwnedByCurrentUser(job: JobPostEntity): Boolean {
        val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid) ?: return false
        return job.postedByUid != null && job.postedByUid == uid
    }

    fun getChatMessages(jobId: String): Flow<List<ChatMessageEntity>> {
        return db.chatMessageDao().getMessagesForJob(jobId)
    }

    fun getEndorsements(providerId: String): Flow<List<SkillEndorsementEntity>> {
        return db.skillEndorsementDao().getEndorsementsForProvider(providerId)
    }

    fun getGuarantorsForWorker(workerKycId: String): Flow<List<GuarantorEntity>> {
        return db.guarantorDao().getGuarantorsForWorker(workerKycId)
    }

    suspend fun insertGuarantor(guarantor: GuarantorEntity) {
        db.guarantorDao().insertGuarantor(guarantor)
    }

    suspend fun insertGuarantors(guarantors: List<GuarantorEntity>) {
        db.guarantorDao().insertGuarantors(guarantors)
    }

    suspend fun deleteGuarantorsForWorker(workerKycId: String) {
        db.guarantorDao().deleteGuarantorsForWorker(workerKycId)
    }

    suspend fun validateGuarantorCount(workerKycId: String): Boolean {
        return db.guarantorDao().countGuarantorsForWorker(workerKycId) == 2
    }

    // ==================== REG-002: GUARANTOR VERIFICATION SUBMISSION ====================

    /** Non-reactive read used to restore a saved (unsubmitted) guarantor draft. */
    suspend fun getGuarantorsOnce(workerKycId: String): List<GuarantorEntity> =
        db.guarantorDao().getGuarantorsForWorkerOnce(workerKycId)

    /**
     * REG-002: saves and files the worker's two guarantors together with the group photograph.
     *
     * Order of operations: validate → persist locally (always, offline-first) → upload the
     * photographs that are not in Storage yet → file for admin review → record SUBMITTED +
     * submittedAt locally, but only once the remote copy was accepted. A remote failure keeps
     * the saved draft and reports a friendly, identifier-free reason so the worker can retry.
     *
     * Guarantor records are third-party personal data, so exactly two distinct guarantors
     * (never the worker's own phone number), consent from both, and a structural check on the
     * National ID number are required before anything leaves the device — see
     * [GuarantorValidators.validateGuarantorSet]. No name, phone number or NIN is ever logged.
     *
     * @param guarantors the two drafts; photo slots may still hold content URIs while offline.
     * @param groupPhotoSource content URI or Storage path of the worker + both guarantors photo.
     * @param isSignedIn whether the caller holds a real authenticated account — demo data must
     *   never file another person's identity data.
     */
    suspend fun submitGuarantorVerification(
        workerId: String,
        guarantors: List<GuarantorEntity>,
        groupPhotoSource: String,
        isSignedIn: Boolean
    ): Result<Unit> {
        if (!isSignedIn || workerId.isBlank()) {
            return Result.failure(GuarantorSubmissionException(GUARANTOR_SIGN_IN_MESSAGE))
        }
        val profile = db.userProfileDao().getUserProfile(workerId).first()
            ?: return Result.failure(GuarantorSubmissionException(GUARANTOR_ACCOUNT_MESSAGE))
        val isWorkerAccount = profile.initialRole.equals("WORKER", ignoreCase = true) ||
            profile.currentActiveMode.equals("WORKER", ignoreCase = true)
        if (!isWorkerAccount) {
            return Result.failure(GuarantorSubmissionException(GUARANTOR_WORKER_MODE_MESSAGE))
        }

        val blocker = GuarantorValidators.validateGuarantorSet(
            guarantors = guarantors.map {
                GuarantorInput(
                    fullName = it.fullName,
                    nationalIdNumber = it.nationalIdNumber,
                    phoneNumber = it.phoneNumber,
                    relationship = it.relationship,
                    district = it.district,
                    consentProvided = it.consentProvided,
                    guarantorPhotoPath = it.guarantorPhotoStoragePath,
                    workerWithGuarantorPhotoPath = it.workerWithGuarantorPhotoStoragePath
                )
            },
            workerPrimaryPhone = profile.phoneNumber,
            groupPhotoPath = groupPhotoSource
        )
        if (blocker != null) {
            return Result.failure(GuarantorSubmissionException(blocker))
        }

        val now = System.currentTimeMillis()
        ensureWorkerKycRow(workerId, profile.fullName)

        // Persist locally first, so a lost connection never costs the worker their photos.
        db.guarantorDao().deleteGuarantorsForWorker(workerId)
        db.guarantorDao().insertGuarantors(guarantors)
        // Upload the photographs that are not in private Storage yet. Failures are reported
        // without identifiers; the rows saved above survive so nothing has to be retyped.
        val uploaded = guarantors.map { draft ->
            val portraitPath = resolveVerificationPhoto(
                workerId,
                draft.guarantorPhotoStoragePath,
                GuarantorValidators.PHOTO_SLOT_GUARANTOR
            ) ?: return Result.failure(GuarantorSubmissionException(GUARANTOR_UPLOAD_MESSAGE))
            val togetherPath = resolveVerificationPhoto(
                workerId,
                draft.workerWithGuarantorPhotoStoragePath,
                GuarantorValidators.PHOTO_SLOT_WITH_WORKER
            ) ?: return Result.failure(GuarantorSubmissionException(GUARANTOR_UPLOAD_MESSAGE))
            draft.copy(
                guarantorPhotoStoragePath = portraitPath,
                workerWithGuarantorPhotoStoragePath = togetherPath
            )
        }
        val groupPhotoPath = resolveVerificationPhoto(
            workerId,
            groupPhotoSource,
            GuarantorValidators.PHOTO_SLOT_GROUP
        ) ?: return Result.failure(GuarantorSubmissionException(GUARANTOR_UPLOAD_MESSAGE))
        // Keep the real Storage paths locally, not the temporary content URIs.
        db.guarantorDao().deleteGuarantorsForWorker(workerId)
        db.guarantorDao().insertGuarantors(uploaded)

        // File for admin review, then mirror the outcome locally: SUBMITTED is only recorded
        // once the remote copy exists, so the state never claims more than admins can see.
        val filed = firebaseService.submitGuarantorVerification(workerId, uploaded, groupPhotoPath)
        db.workerKycDao().updateGuarantorStep(
            userId = workerId,
            guarantorStatus = if (filed) "SUBMITTED" else "DRAFT",
            groupPhotoStoragePath = groupPhotoPath,
            submittedAt = if (filed) now else 0L
        )
        return if (filed) {
            Result.success(Unit)
        } else {
            Result.failure(GuarantorSubmissionException(GUARANTOR_SUBMIT_MESSAGE))
        }
    }

    /**
     * REG-002: keeps the local guarantor state honest about the admin's decision by pulling
     * the worker's own submission (status, feedback, aggregate NIN result) into Room.
     * Returns a friendly failure when offline or when nothing has been filed yet.
     */
    suspend fun refreshGuarantorVerificationStatus(workerId: String): Result<Unit> {
        if (workerId.isBlank()) {
            return Result.failure(GuarantorSubmissionException(GUARANTOR_ACCOUNT_MESSAGE))
        }
        val submission = firebaseService.fetchGuarantorSubmission(workerId)
            ?: return Result.failure(GuarantorSubmissionException(GUARANTOR_STATUS_MESSAGE))
        val current = db.workerKycDao().getWorkerKycOnce(workerId)
            ?: return Result.failure(GuarantorSubmissionException(GUARANTOR_ACCOUNT_MESSAGE))
        // Review fields come from the worker's own submission document, never from user input.
        db.workerKycDao().updateGuarantorStep(
            userId = workerId,
            guarantorStatus = remoteGuarantorStatus(submission["status"] as? String),
            groupPhotoStoragePath = current.groupPhotoStoragePath,
            submittedAt = current.guarantorsSubmittedAt,
            feedback = submission["reviewFeedback"] as? String ?: ""
        )
        db.guarantorDao().updateNinStatusForWorker(
            workerKycId = workerId,
            ninStatus = when (submission["ninVerificationStatus"] as? String) {
                "VERIFIED" -> "VERIFIED"
                "FAILED" -> "FAILED"
                else -> "PENDING"
            }
        )
        return Result.success(Unit)
    }

    /**
     * REG-002: stores the guarantor drafts and the group photograph locally without filing
     * anything, so a long form can be abandoned and resumed (requirement: drafts survive
     * app restarts). Photos stay as content URIs until [submitGuarantorVerification] uploads
     * them — a draft never leaves the device.
     */
    suspend fun saveGuarantorDraft(
        workerId: String,
        guarantors: List<GuarantorEntity>,
        groupPhotoStoragePath: String
    ) {
        val fullName = db.userProfileDao().getUserProfile(workerId).first()?.fullName.orEmpty()
        ensureWorkerKycRow(workerId, fullName)
        db.guarantorDao().deleteGuarantorsForWorker(workerId)
        db.guarantorDao().insertGuarantors(guarantors)
        db.workerKycDao().updateGuarantorStep(
            userId = workerId,
            guarantorStatus = "DRAFT",
            groupPhotoStoragePath = groupPhotoStoragePath,
            submittedAt = 0L
        )
    }

    /**
     * Guarantor rows carry a foreign key on worker_kyc, the row identity verification
     * (KYC-001/002) owns. Nothing in the app writes that table today, so the known profile
     * facts are filed here rather than dropping an otherwise valid submission.
     */
    private suspend fun ensureWorkerKycRow(workerId: String, fullName: String) {
        if (db.workerKycDao().getWorkerKycOnce(workerId) != null) return
        db.workerKycDao().insertWorkerKyc(
            WorkerKycEntity(
                userId = workerId,
                fullLegalName = fullName,
                nationalIdNumber = "",
                dateOfBirth = "",
                gender = "",
                idFrontStoragePath = "",
                idBackStoragePath = ""
            )
        )
    }

    /**
     * REG-002: an already-uploaded path is reused as is (idempotent re-submission); a content
     * URI is uploaded once into the worker's private verification prefix. Null means the photo
     * is missing or the upload failed — the caller stops before filing anything incomplete.
     */
    private suspend fun resolveVerificationPhoto(
        workerId: String,
        source: String,
        slot: String
    ): String? {
        if (source.isBlank()) return null
        if (source.startsWith("users/$workerId/guarantors/")) return source
        return firebaseService.uploadGuarantorPhoto(workerId, slot, Uri.parse(source))
    }

    /** REG-002: remote review state → the locally stored guarantor status. */
    private fun remoteGuarantorStatus(raw: String?): String = when (raw) {
        "APPROVED" -> "APPROVED"
        "REJECTED" -> "REJECTED"
        "CHANGES_REQUIRED" -> "CHANGES_REQUIRED"
        "PENDING_REVIEW", "UNDER_REVIEW" -> "PENDING_REVIEW"
        else -> "SUBMITTED"
    }



    suspend fun submitWorkerKycApplication(userId: String) {
        db.workerKycDao().updateKycStatus(userId, "SUBMITTED")
        createNotification(
            NotificationEntity(
                id = "notif_${System.currentTimeMillis()}_kyc",
                userId = userId,
                type = "VERIFICATION_DECISION",
                title = "Application Submitted",
                body = "Your worker verification application has been submitted for review.",
                jobId = "",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun startKycReview(userId: String) {
        db.workerKycDao().updateKycStatus(userId, "UNDER_REVIEW")
    }

    suspend fun requestKycChanges(userId: String, feedback: String) {
        db.workerKycDao().updateKycReview(userId, "CHANGES_REQUIRED", feedback = feedback)
        createNotification(
            NotificationEntity(
                id = "notif_${System.currentTimeMillis()}_kyc",
                userId = userId,
                type = "VERIFICATION_DECISION",
                title = "Changes Required",
                body = "Your verification application requires changes: $feedback",
                jobId = "",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun approveKyc(userId: String) {
        db.workerKycDao().updateKycReview(userId, "APPROVED")
        createNotification(
            NotificationEntity(
                id = "notif_${System.currentTimeMillis()}_kyc",
                userId = userId,
                type = "VERIFICATION_DECISION",
                title = "Verification Approved",
                body = "Your worker verification has been approved! You can now proceed to training.",
                jobId = "",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun rejectKyc(userId: String, feedback: String) {
        db.workerKycDao().updateKycReview(userId, "REJECTED", feedback = feedback)
        createNotification(
            NotificationEntity(
                id = "notif_${System.currentTimeMillis()}_kyc",
                userId = userId,
                type = "VERIFICATION_DECISION",
                title = "Verification Rejected",
                body = "Your worker verification was rejected: $feedback",
                jobId = "",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun getKycSlaDeadline(submittedAt: Long): Long {
        return submittedAt + (48 * 60 * 60 * 1000)
    }

    suspend fun insertJob(job: JobPostEntity) {
        db.jobPostDao().insertJob(job)
    }

    suspend fun insertProvider(provider: ServiceProviderEntity) {
        db.serviceProviderDao().insertProvider(provider)
    }

    suspend fun saveUserProfile(profile: UserProfileEntity) {
        db.userProfileDao().insertUserProfile(profile)
    }

    /**
     * H5 (SKILL-003 wiring): persists validated trade skills on the authenticated
     * user's OWN profile row + Firestore mirror. Fails closed (returns false) when
     * there is no authoritative uid, when no UID-bound row exists, or when the skill
     * set fails `ServiceTaxonomyCatalog.validateWorkerSkills`. Skills are catalog
     * metadata, never identity — the own-row gate mirrors `saveBackupContact`.
     */
    suspend fun saveWorkerSkills(primarySkill: String, additionalSkills: List<String>): Boolean {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return false
        if (!ServiceTaxonomyCatalog.validateWorkerSkills(primarySkill, additionalSkills).isValid) return false
        val row = db.userProfileDao().getCurrentUserById(actorUid).first() ?: return false
        if (row.id != actorUid) return false
        db.userProfileDao().updateWorkerSkills(
            userId = actorUid,
            primarySkill = primarySkill,
            additionalSkillsCsv = additionalSkills.joinToString(",")
        )
        saveUserIdentityToServer(
            row.copy(
                primarySkill = primarySkill,
                additionalSkillsCsv = additionalSkills.joinToString(","),
                updatedAtTimestamp = System.currentTimeMillis()
            )
        )
        return true
    }

    /**
     * AUTH-002/AUTH-006: Mirror the identity record to Firestore (`users/{uid}`) so an
     * account can still be recognised on a later sign-in after logout, app-data clear,
     * reinstall, or when signing in on a different device.
     *
     * Kept separate from [saveUserProfile] on purpose: the device-local rebuild performed by
     * `restoreSession()` must never be pushed to the server as if it were authoritative.
     */
    suspend fun saveUserIdentityToServer(profile: UserProfileEntity): Boolean {
        return firebaseService.saveUserIdentity(profile)
    }

    /** AUTH-006: Server-side identity lookup by Firebase UID. */
    suspend fun fetchUserIdentityFromServer(userId: String): UserProfileEntity? {
        return firebaseService.fetchUserIdentity(userId)
    }

    /**
     * C2-1: server-side phone lookup. NON-AUTHORITATIVE — retained for future migration work
     * only. It must never be used as an ownership gate: the authenticated Firebase UID is the
     * only identity allowed to own a profile or inherit privileged state.
     */
    // C2-2 Fix #7 (F3 remainder): still has ZERO callers. The device-local counterpart
    // getUserProfileByPhone and its DAO query were REMOVED in this fix, because selecting a local
    // Room profile by a mutable field (a phone number) is exactly the unbound recovery path this
    // finding targets. This remote variant is retained only because the tightened `users` read rule
    // already denies it for non-owners (it returns null in practice); if it is ever revived it may
    // only feed display, never authenticated identity.
    suspend fun fetchUserIdentityByPhoneFromServer(phoneNumber: String): UserProfileEntity? {
        return firebaseService.fetchUserIdentityByPhone(phoneNumber)
    }

    suspend fun switchActiveMode(userId: String, mode: String) {
        db.userProfileDao().updateActiveMode(userId, mode)
    }

    /** KYC-001: Streams the worker's own KYC submission for the identity-verification view. */
    fun getWorkerKyc(userId: String): Flow<com.example.data.db.WorkerKycEntity?> {
        return db.workerKycDao().getWorkerKyc(userId)
    }

    suspend fun updateWorkerKycStatus(userId: String, status: String) {
        db.userProfileDao().updateWorkerKycStatus(userId, status)
    }

    suspend fun updateWorkerActivation(userId: String, activated: Boolean) {
        db.userProfileDao().updateWorkerActivation(userId, activated)
    }

    suspend fun updateTrainingStatus(userId: String, completed: Boolean) {
        db.userProfileDao().updateTrainingStatus(userId, completed)
    }

    suspend fun seedInitialDataIfNeeded() {
        @Suppress("DEPRECATION") // seeding inspects every stored row, scoped by nothing
        val existingProviders = db.serviceProviderDao().getAllProviders().first()
        if (existingProviders.isEmpty()) {
            db.serviceProviderDao().insertProviders(initialProviders)
            db.jobPostDao().insertJobs(initialJobs)
            db.chatMessageDao().insertMessages(initialChatMessages)
            initialEndorsements.forEach {
                db.skillEndorsementDao().insertEndorsement(it)
            }
            // NOTE: No demo user_profiles row is seeded. The authenticated user's profile is
            // persisted from the onboarding/auth flow (loginUser -> repository.saveUserProfile).
            // Seeding a fake identity used to shadow the real profile in getCurrentUser().
        }
    }

    /**
     * C2-2 Fix #4 (audit finding **F4**): job authorship.
     *
     * Every job is stamped with `postedByUid = FirebaseAuth.currentUser.uid` (validated by
     * [resolveAuthoritativeUid]) BEFORE it is persisted. Returns `null` — persisting nothing —
     * when there is no authoritative authenticated uid. That is a deliberate fail-closed abort:
     * a job with no provable owner can never be cancelled, edited or attributed, so creating it
     * would be worse than refusing it.
     *
     * `postedByResident` remains a pure display string; it is never an ownership key. The
     * hardcoded `momoPhoneNumber` below is left untouched — payment contact is a separate finding
     * and changing it here would alter behaviour beyond F4's scope.
     */

    suspend fun addJobPost(
        title: String,
        category: String,
        description: String,
        budget: Double,
        urgency: String,
        district: String,
        streetAddress: String = "",
        apartmentSuite: String = "",
        landmarkDirections: String = "",
        scheduledAt: Long = 0,
        photos: List<String> = emptyList(),
        materialsProvidedBy: String = "RESIDENT",
        laborHours: Double = 2.0,
        laborRate: Double = 60.0,
        jobType: String = "ONE_TIME",
        recurringSchedule: String = "WEEKLY",
        photoUris: List<android.net.Uri> = emptyList()
    ): String? {
        // F4: the ONLY acceptable owner identity. Never the newest Room row, never a display
        // name/district, never "unknown"/"system"/a synthetic or anonymous id.
        val ownerUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return null
        val newJobId = "job_${System.currentTimeMillis()}"
        // R05-P2-03: every money amount is whole Uganda shillings, the single unit the provider
        // payloads use (`momoCollection` refuses a non-integer total). `JobMoney.split` rounds each
        // component once and sums the rounded parts, so what is stored equals what is displayed.
        val money = JobMoney.split(laborHours, laborRate, budget, materialsProvidedBy)
        val laborTotal = money.laborTotal
        val materialsSubtotal = money.materialsSubtotal
        val platformFee = money.platformFee
        val grandTotal = money.grandTotal

        val randomDoorstepPin = (1000..9999).random().toString()

        // AUDIT-C-PHOTO: Upload photos first, persist download URLs in Room
        val photoUrls = mutableListOf<String>()
        for (photoUri in photoUris) {
            val url = firebaseService.uploadJobPostPhoto(newJobId, photoUri)
            if (url != null) {
                photoUrls.add(url)
            }
        }

        val job = JobPostEntity(
            id = newJobId,
            title = title,
            category = category,
            description = description,
            // F4: authoritative ownership (immutable authenticated uid).
            postedByUid = ownerUid,
            // F4: DISPLAY-ONLY text — never an ownership key.
            postedByResident = "You ($district)",
            district = district,
            streetAddress = streetAddress,
            apartmentSuite = apartmentSuite,
            landmarkDirections = landmarkDirections,
            scheduledAt = scheduledAt,
            photos = photoUrls,
            budget = grandTotal,
            urgency = urgency,
            status = "Requested",
            assignedProviderId = null,
            assignedProviderName = null,
            createdTimestamp = System.currentTimeMillis(),
            estimatedHours = laborHours,
            materialsCost = materialsSubtotal,
            safetyFee = platformFee,
            materialsProvidedBy = materialsProvidedBy,
            laborHours = laborHours,
            laborRate = laborRate,
            laborTotal = laborTotal,
            materialsSubtotal = materialsSubtotal,
            platformFee = platformFee,
            grandTotal = grandTotal,
            paymentChannel = "MTN_MOMO",
            momoPhoneNumber = "+256 772 198 420",
            escrowStatus = "QUOTE_PENDING",
            doorstepPin = randomDoorstepPin,
            jobType = jobType,
            recurringSchedule = if (jobType == "RECURRING") recurringSchedule else ""
        )
        db.jobPostDao().insertJob(job)

        // R07-P2-01: hand the PIN to the server (hashed) so verification never depends on a value the
        // caller supplies. Best-effort here: the first verification attempt registers it if the
        // device was offline at creation time.
        runCatching { registerDoorstepPin(newJobId, randomDoorstepPin) }

        // Add initial system message
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = newJobId,
                senderName = "System",
                isFromUser = false,
                message = "Job request (${if (JobMoney.isLaborOnly(materialsProvidedBy)) "Labor Only" else "Labor + Materials"}) broadcasted to verified local $category specialists in $district.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
        return newJobId
    }

    /** this legacy wrapper previously wrote `job_posts.status`     * directly with NO identity check, NO ownership check, NO state-machine check and NO audit row.
     * It is now a thin, AUTHORIZED delegate to [transitionJobStatus], which is the single secured
     * boundary. Callers that need to move a job's status go through the boundary or not at all.
     *
     * @return true only when the transition was both authorized and structurally legal.
     */
    suspend fun updateJobStatus(jobId: String, newStatus: String): Boolean {
        val job = db.jobPostDao().getJobById(jobId) ?: return false
        return transitionJobStatus(jobId, job.status, newStatus)
    }

    /** AUDIT-C-TRACKING: Update real-time worker location for a job */
    suspend fun updateWorkerLocation(
        jobId: String,
        latitude: Double,
        longitude: Double
    ) {
        db.jobPostDao().updateWorkerLocation(jobId, latitude, longitude)
    }

    /**
     * R02-P2-01: RELEASED_TO_WORKER is a server outcome, never a client choice.
     *
     * The sheet used to call this with "RELEASED_TO_WORKER" and the client wrote the status plus a
     * "payout dispatched" notification without any provider call. A release is now applied only
     * through [applyServerConfirmedEscrowRelease], after `releaseEscrowToWorker` returned success.
     * Any direct client attempt is refused before it can write.
     */
    suspend fun updateEscrowStatus(
        jobId: String,
        newEscrowStatus: String,
        serverConfirmed: Boolean = false
    ) {
        if (newEscrowStatus == "RELEASED_TO_WORKER" && !serverConfirmed) {
            Log.w("SkillsExchangeRepository", "Refused client-side release for $jobId")
            return
        }
        db.jobPostDao().updateEscrowStatus(jobId, newEscrowStatus)

        val notificationMsg = when (newEscrowStatus) {
            "DEPOSIT_PROMPTED" -> "USSD push sent to resident phone to lock funds in escrow."
            "FUNDS_LOCKED_IN_ESCROW" -> "🔒 Escrow Locked! Customer approved MoMo transfer into secure escrow."
            "WORK_IN_PROGRESS" -> "🛠️ Work in progress. Service provider is performing labor."
            "PROOF_SUBMITTED" -> "📷 Ground proof photos uploaded by worker. Awaiting resident release."
            "RELEASED_TO_WORKER" -> "Escrow released by the server. The payout is queued with the mobile money provider."
            "CASH_SETTLEMENT_ACKNOWLEDGED" -> "Cash settlement recorded. No funds are held by the platform."
            else -> "Escrow status updated to $newEscrowStatus."
        }

        val job = db.jobPostDao().getJobById(jobId)
        val assignedProviderId = job?.assignedProviderId

        if (assignedProviderId != null && (newEscrowStatus == "FUNDS_LOCKED_IN_ESCROW" || newEscrowStatus == "RELEASED_TO_WORKER")) {
            createNotification(
                NotificationEntity(
                    id = "notif_${System.currentTimeMillis()}_escrow",
                    userId = assignedProviderId,
                    type = "PAYMENT_STATUS",
                    title = when (newEscrowStatus) {
                        "FUNDS_LOCKED_IN_ESCROW" -> "Payment Secured"
                        "RELEASED_TO_WORKER" -> "Payment Released"
                        else -> "Payment Update"
                    },
                    body = notificationMsg,
                    jobId = jobId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        val mappedJobStatus = when (newEscrowStatus) {
            "FUNDS_LOCKED_IN_ESCROW", "WORK_IN_PROGRESS" -> "In Progress"
            "PROOF_SUBMITTED" -> "Review"
            "RELEASED_TO_WORKER" -> "Completed"
            else -> null
        }
        if (mappedJobStatus != null) {
            // C2-2 job-status authorization: this used to write `job_posts.status` unconditionally,
            // so ANY caller reaching this method could move any job's status as a side effect of an
            // escrow write. It is now gated on the authenticated actor being the job OWNER, the one
            // relationship the client can decide safely (see [isJobTransitionAuthorized]).
            //
            // NOTE (reported, not silently changed): these mapped values are DISPLAY strings
            // ("In Progress"/"Review"/"Completed") that are not members of the `JobStatus` enum, so
            // they deliberately do NOT go through [transitionJobStatus] -- routing them through the
            // state machine would require changing the status vocabulary, which is out of scope for
            // an authorization fix. The ownership gate is applied instead, and the vocabulary
            // mismatch is recorded in AGENT_STATE.md as an open correctness item.
            val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            val isJobOwner = actorUid != null && job != null &&
                job.postedByUid != null && job.postedByUid == actorUid
            if (isJobOwner) {
                db.jobPostDao().updateJobStatus(jobId, mappedJobStatus)
            }
        }

        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "MoMo Escrow Engine",
                isFromUser = false,
                message = notificationMsg,
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
    }

    /**
     * R02-P2-01: record a release the backend actually confirmed.
     *
     * Called only after `releaseEscrowToWorker` succeeds. The message shown to the user is the
     * server's, not a client invention, and the local row moves only now.
     */
    suspend fun applyServerConfirmedEscrowRelease(jobId: String, serverMessage: String) {
        updateEscrowStatus(jobId, "RELEASED_TO_WORKER", serverConfirmed = true)
        createNotification(
            com.example.data.db.NotificationEntity(
                id = "notif_${System.currentTimeMillis()}_escrow_server",
                userId = db.jobPostDao().getJobById(jobId)?.assignedProviderId ?: "",
                type = "PAYMENT_STATUS",
                title = "Payment Released",
                body = serverMessage,
                jobId = jobId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateJobPhotos(jobId: String, beforeUrl: String?, afterUrl: String?) {
        db.jobPostDao().updateJobPhotos(jobId, beforeUrl, afterUrl)
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "System",
                isFromUser = false,
                message = "Ground proof photos updated (Before & After verification attached).",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
    }

    suspend fun requestEmergencyAddon(jobId: String, amount: Double, reason: String) {
        db.jobPostDao().updateEmergencyAddon(
            jobId = jobId,
            requested = true,
            // R05-P2-03: whole shillings, in the one unit the app stores.
            amount = CurrencyRegion.roundToWholeShillings(amount).toDouble(),
            reason = reason,
            status = "PENDING"
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "Worker Alert",
                isFromUser = false,
                message = "⚠️ Emergency Material Add-on Request: $reason (${CurrencyRegion.format(amount)}). Customer approval required.",
                timestampText = "Just now",
                isSystemNotification = false
            )
        )
    }

    suspend fun respondToEmergencyAddon(jobId: String, approve: Boolean) {
        val newStatus = if (approve) "APPROVED" else "REJECTED"
        db.jobPostDao().updateEmergencyAddon(
            jobId = jobId,
            requested = false,
            amount = 0.0,
            reason = "",
            status = newStatus
        )
        
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "System",
                isFromUser = false,
                message = if (approve) "✅ Emergency material expense authorized by resident and added to escrow." else "❌ Emergency material expense request declined by resident.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
    }

    /**
     * R07-P2-01: verify the doorstep PIN — on the server, with the PIN the caller typed.
     *
     * The device used to compare the typed code with a second value the UI handed in
     * (`expectedPin` == the stored PIN), so the comparison proved nothing and a modified client could
     * pass it. Now only `enteredPin` is sent; `verifyDoorstepPin` (Cloud Function) compares it with a
     * salted hash in `job_secrets/{jobId}`, counts attempts and locks after five, and is the only
     * place that may start the job.
     *
     * Authorization is still enforced here as well (resident or assigned worker, authenticated uid
     * only), and the local row is updated only when the server says `Verified`. If the resident's
     * device created the job offline, the PIN may not be registered yet: the owner registers it once
     * and the verification is retried, so a flaky connection does not leave a job unstartable.
     */
    suspend fun verifyDoorstepPin(jobId: String, enteredPin: String): Pair<Boolean, String?> {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return false to "Sign in again to verify the PIN."
        val job = db.jobPostDao().getJobById(jobId) ?: return false to "That job is no longer on this device."

        val isResident = job.postedByUid != null && job.postedByUid == actorUid
        val isAssignedWorker = job.assignedProviderId == actorUid
        if (!isResident && !isAssignedWorker) {
            return false to "Only the resident or the assigned worker can verify this PIN."
        }
        if (!DoorstepPinRules.isWellFormed(enteredPin)) {
            return false to "Enter the 4-digit PIN."
        }

        var result = firebaseService.verifyDoorstepPinWithServer(jobId, enteredPin)

        // The resident holds the PIN, so the resident is who registers it when it is missing.
        if (result is DoorstepPinResult.NotRegistered && isResident &&
            DoorstepPinRules.isWellFormed(job.doorstepPin)
        ) {
            firebaseService.registerDoorstepPin(jobId, job.doorstepPin)
            result = firebaseService.verifyDoorstepPinWithServer(jobId, enteredPin)
        }

        if (result !is DoorstepPinResult.Verified) {
            return false to DoorstepPinRules.failureMessage(result)
        }

        db.jobPostDao().verifyDoorstepPin(jobId)
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "Doorstep Security",
                isFromUser = false,
                message = "🔑 Doorstep PIN verified by the server. Resident and worker confirmed present; the job has started.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
        return true to null
    }

    /**
     * R07-P2-01: give the job's doorstep PIN to the server so it can verify it later.
     *
     * Called right after the job is created. A failure is not fatal — the PIN is registered lazily on
     * the first verification attempt — but it is attempted eagerly so the common path needs no retry.
     */
    suspend fun registerDoorstepPin(jobId: String, pin: String): Boolean {
        val ownerUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid) ?: return false
        val job = db.jobPostDao().getJobById(jobId) ?: return false
        if (job.postedByUid != ownerUid) return false
        return firebaseService.registerDoorstepPin(jobId, pin).isSuccess
    }

    suspend fun requestMaterialAdvance(jobId: String, amount: Double) {
        db.jobPostDao().updateMaterialAdvance(
            jobId = jobId,
            requested = true,
            // R05-P2-03: whole shillings at the boundary, never a fractional provider amount.
            amount = CurrencyRegion.roundToWholeShillings(amount).toDouble(),
            status = "REQUESTED",
            receiptUrl = ""
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "Worker Alert",
                isFromUser = false,
                message = "🛒 Early Parts Advance Requested: Worker requested 50% parts advance (${CurrencyRegion.format(amount)}) from escrow to buy hardware. Mandatory receipt photo upload required within 60 mins.",
                timestampText = "Just now",
                isSystemNotification = false
            )
        )
    }

    suspend fun respondToMaterialAdvance(jobId: String, approve: Boolean) {
        val status = if (approve) "DISBURSED" else "REJECTED"
        db.jobPostDao().updateMaterialAdvance(
            jobId = jobId,
            requested = false,
            amount = 0.0,
            status = status,
            receiptUrl = if (approve) "img_receipt_hardware_store" else ""
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "MoMo Vault",
                isFromUser = false,
                message = if (approve) "💸 50% Material Advance Disbursed from Escrow to Worker MoMo Wallet. Hardware store receipt photo pending." else "❌ Material Advance Request declined by resident.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
    }

    suspend fun updatePaymentChannel(jobId: String, channel: String, phone: String) {
        db.jobPostDao().updatePaymentChannel(jobId, channel, phone)
    }

    /**
     * C2-2 job-status authorization: `JobPostDao.assignProvider` also sets `status = 'Accepted'`, so
     * accepting a worker is a STATUS MUTATION. It previously ran with no identity and no ownership
     * check, so any caller could accept a worker onto somebody else's job and thereby move its
     * status. Accepting a worker onto a job is the owner's decision, so this is owner-gated
     * (fail closed). Returns false when the caller is not the job owner.
     */
    suspend fun hireProviderForJob(jobId: String, providerId: String, providerName: String): Boolean {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return false
        val job = db.jobPostDao().getJobById(jobId) ?: return false
        if (job.postedByUid == null || job.postedByUid != actorUid) return false

        db.jobPostDao().assignProvider(jobId, providerId, providerName)
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = providerName,
                isFromUser = false,
                message = "Hi! Thanks for accepting my estimate. I've reviewed your request and will be over shortly.",
                timestampText = "Just now",
                isSystemNotification = false
            )
        )
        return true
    }

    /**
     * C2-2 Fix #5 (audit finding **F5**): chat sender identity.
     *
     * The author is `FirebaseAuth.currentUser.uid` (validated by the shared [resolveAuthoritativeUid]
     * helper — the Room profile is consulted for NOTHING here). Returns `false` and persists/sends
     * NOTHING when there is no authoritative uid; there is no `"unknown"` fallback and no
     * newest-row fallback.
     *
     * `senderName` remains a DISPLAY string passed through from the caller, and `isFromUser` remains
     * the bubble-alignment flag. Neither is an identity.
     */
    suspend fun sendChatMessage(
        jobId: String,
        senderName: String,
        isFromUser: Boolean,
        text: String
    ): Boolean {
        // F5: the ONLY acceptable author identity. Never the newest Room row, never a display
        // name/phone/district, never "unknown"/"system"/a synthetic or anonymous id.
        val authorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return false

        val msg = ChatMessageEntity(
            id = "msg_${System.currentTimeMillis()}",
            jobId = jobId,
            // F5: authoritative authorship (authenticated uid).
            senderId = authorUid,
            // F5: DISPLAY-ONLY text — never an identity.
            senderName = senderName,
            isFromUser = isFromUser,
            message = text,
            timestampText = "Just now",
            isSystemNotification = false
        )
        db.chatMessageDao().insertMessage(msg)

        firebaseService.sendChatMessage(jobId, authorUid, senderName, text, isFromUser)
        return true
    }

    /**
     * C2-2 Fix #5 (F5): authorship predicate for edit/delete/moderation/attribution decisions.
     *
     * Returns `false` when there is no authenticated uid, when the message's `senderId` is NULL
     * (a legacy message or a system notification with no human author) or when it names another
     * account. There is no display-name, `isFromUser`, phone or newest-row fallback.
     */
    fun isMessageAuthoredByCurrentUser(message: ChatMessageEntity): Boolean {
        val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid) ?: return false
        return message.senderId != null && message.senderId == uid
    }

    fun getFirestoreChatMessages(jobId: String): Flow<List<Map<String, Any>>> {
        return firebaseService.streamChatMessages(jobId)
    }

    fun getRealtimeChatMessages(jobId: String): Flow<List<ChatMessageEntity>> {
        return getFirestoreChatMessages(jobId).map { firestoreMessages ->
            firestoreMessages.mapNotNull { msg ->
                val id = msg["docId"] as? String ?: "msg_${msg["timestamp"] ?: System.currentTimeMillis()}"
                val senderName = msg["senderName"] as? String ?: "Unknown"
                // C2-2 Fix #5 (F5): preserve the authenticated author through deserialization.
                // Legacy documents have no `senderId` key, so this stays null = unknown author —
                // it is NEVER back-filled from senderName, and a null never means "mine".
                val senderId = msg["senderId"] as? String
                val text = msg["text"] as? String ?: ""
                val isFromUser = msg["isFromUser"] as? Boolean ?: false
                val timestamp = msg["timestamp"] as? Long ?: System.currentTimeMillis()
                ChatMessageEntity(
                    id = id,
                    jobId = jobId,
                    senderId = senderId?.takeIf { it.isNotBlank() },
                    senderName = senderName,
                    isFromUser = isFromUser,
                    message = text,
                    timestampText = formatTimestamp(timestamp),
                    isSystemNotification = false
                )
            }
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000} min ago"
            diff < 86400000 -> "${diff / 3600000} hrs ago"
            else -> "${diff / 86400000} days ago"
        }
    }

    suspend fun endorseSkill(providerId: String, skillName: String, neighborName: String, district: String) {
        val endorsement = SkillEndorsementEntity(
            id = "end_${System.currentTimeMillis()}",
            providerId = providerId,
            skillName = skillName,
            endorsedByNeighborName = neighborName,
            neighborDistrict = district
        )
        db.skillEndorsementDao().insertEndorsement(endorsement)
        db.serviceProviderDao().incrementEndorsements(providerId)
    }

    /** REM-023: server-verified admin gate; the UI must not unlock on a local boolean. */
    suspend fun checkAdminAccess(): Boolean {
        return firebaseService.checkAdminAccess()
    }

    suspend fun getAdminVerificationQueue(): List<Map<String, Any>> {
        return firebaseService.getPendingVerificationQueue()
    }

    suspend fun submitAdminVerificationDecision(userId: String, decision: String, feedback: String): Boolean {
        return firebaseService.reviewWorkerApplication(userId, decision, feedback)
    }

    suspend fun setWorkerActivation(userId: String, activate: Boolean): Boolean {
        return firebaseService.setWorkerActivationStatus(userId, activate)
    }

    // ==================== AUDIT-A-ARBITRATE: Dispute Arbitration ====================

    /**
     * Fetches the admin dispute queue via the `getDisputesQueue` Cloud Function.
     * Admin-gated server-side; returns empty list on failure.
     */
    suspend fun getDisputesQueue(status: String? = null): List<Map<String, Any>> {
        return firebaseService.getDisputesQueue(status)
    }

    /**
     * Resolves/rejects/escalates a dispute via the `resolveDispute` Cloud Function.
     * Admin-gated server-side; returns true on success.
     */
    suspend fun resolveDispute(
        disputeId: String,
        resolution: String,
        resolutionType: String?,
        resolutionAmount: Double?,
        adminNotes: String?
    ): Boolean {
        return firebaseService.resolveDispute(
            disputeId, resolution, resolutionType, resolutionAmount, adminNotes
        )
    }

    suspend fun submitRecurringProposal(jobId: String, workerId: String, proposedRate: Double, proposalTerms: String) {
        val proposal = mapOf(
            "workerId" to workerId,
            "proposedRate" to proposedRate,
            "terms" to proposalTerms,
            "submittedAt" to System.currentTimeMillis(),
            "status" to "PENDING"
        )
        db.jobPostDao().updateRecurringProposal(
            jobId = jobId,
            proposalJson = "Worker $workerId proposes UGX $proposedRate/${proposalTerms.ifBlank { "recurring schedule" }}",
            negotiatedRate = proposedRate,
            status = "PENDING"
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "Worker",
                isFromUser = false,
                message = "📋 Recurring contract proposal submitted: UGX $proposedRate/${proposalTerms.ifBlank { "recurring schedule" }}. Awaiting client acceptance.",
                timestampText = "Just now",
                isSystemNotification = false
            )
        )
    }

    suspend fun respondToRecurringProposal(jobId: String, accept: Boolean) {
        val newStatus = if (accept) "ACCEPTED" else "REJECTED"
        db.jobPostDao().updateRecurringProposalStatus(jobId, newStatus)

        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "System",
                isFromUser = false,
                message = if (accept) "✅ Recurring contract proposal accepted! Negotiated rate is now locked." else "❌ Recurring contract proposal declined by client.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
    }

    fun getQuotesForJob(jobId: String): Flow<List<JobQuoteEntity>> {
        return db.jobQuoteDao().getQuotesForJob(jobId)
    }

    fun getQuotesByWorker(workerId: String): Flow<List<JobQuoteEntity>> {
        return db.jobQuoteDao().getQuotesByWorker(workerId)
    }

    /**
     * AUDIT-W-QUOTE: Outcome of a secured quote submission.
     */
    sealed class QuoteSubmissionResult {
        data class Success(val quoteId: String) : QuoteSubmissionResult()
        object NotAuthenticated : QuoteSubmissionResult()
        object InvalidRate : QuoteSubmissionResult()
        object InvalidHours : QuoteSubmissionResult()
        object JobNotFound : QuoteSubmissionResult()
        object InvalidJobState : QuoteSubmissionResult()
        object SelfQuoteForbidden : QuoteSubmissionResult()
        object DuplicatePendingQuote : QuoteSubmissionResult()
    }

    /**
     * AUDIT-W-QUOTE: Outcome of a secured quote response (accept/reject).
     */
    sealed class QuoteResponseResult {
        object Success : QuoteResponseResult()
        object NotAuthenticated : QuoteResponseResult()
        object JobNotFound : QuoteResponseResult()
        object QuoteNotFound : QuoteResponseResult()
        object JobMismatch : QuoteResponseResult()
        object NotAuthorized : QuoteResponseResult()
        object QuoteAlreadyTerminal : QuoteResponseResult()
        object InvalidJobState : QuoteResponseResult()
    }


    /**
     * AUDIT-W-QUOTE: The single SECURED boundary for submitting a worker quote.
     * Identity, rate/hours validation, job existence, job state, ownership/self-quote,
     * and duplicate prevention are all enforced here, and the caller is trusted for none of them.
     */
    suspend fun submitQuote(
        jobId: String,
        workerName: String,
        proposedRate: Double,
        proposedHours: Double,
        notes: String
    ): QuoteSubmissionResult {
        val workerUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
        return submitQuoteInternal(jobId, workerUid, workerName, proposedRate, proposedHours, notes)
    }

    internal suspend fun submitQuoteInternal(
        jobId: String,
        workerUid: String?,
        workerName: String,
        proposedRate: Double,
        proposedHours: Double,
        notes: String
    ): QuoteSubmissionResult {
        // 1. Authoritative worker identity (F6/F7 rule). Never caller-supplied, never synthetic.
        val uid = resolveAuthoritativeUid(workerUid)
            ?: return QuoteSubmissionResult.NotAuthenticated

        // 2. Validate proposedRate (finite, positive, non-NaN).
        if (proposedRate.isNaN() || proposedRate.isInfinite() || proposedRate <= 0.0) {
            return QuoteSubmissionResult.InvalidRate
        }

        // 3. Validate proposedHours (finite, positive, non-NaN).
        if (proposedHours.isNaN() || proposedHours.isInfinite() || proposedHours <= 0.0) {
            return QuoteSubmissionResult.InvalidHours
        }

        // 4. Load authoritative job from database.
        val job = db.jobPostDao().getJobById(jobId)
            ?: return QuoteSubmissionResult.JobNotFound

        // 5. Validate job state: must be in QUOTABLE_JOB_STATES (POSTED or QUOTING).
        val jobStatus = JobStatus.fromValue(job.status)
        if (jobStatus == null || jobStatus !in QUOTABLE_JOB_STATES) {
            return QuoteSubmissionResult.InvalidJobState
        }

        // 6. Self-quoting prevention and owner verification.
        if (job.postedByUid == null || job.postedByUid == uid) {
            return QuoteSubmissionResult.SelfQuoteForbidden
        }

        // 7. Duplicate pending quote check.
        if (db.jobQuoteDao().countPendingQuotesForWorkerAndJob(jobId, uid) > 0) {
            return QuoteSubmissionResult.DuplicatePendingQuote
        }

        val quoteId = "quote_${System.currentTimeMillis()}"
        val totalQuote = proposedRate * proposedHours
        val quote = JobQuoteEntity(
            id = quoteId,
            jobId = jobId,
            workerId = uid,
            workerName = workerName,
            proposedRate = proposedRate,
            proposedHours = proposedHours,
            totalQuote = totalQuote,
            notes = notes,
            status = "PENDING"
        )
        db.jobQuoteDao().insertQuote(quote)
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderId = uid,
                senderName = workerName,
                isFromUser = false,
                message = "📋 Quote submitted: UGX ${"%.0f".format(totalQuote)} (${proposedHours}hrs @ UGX ${"%.0f".format(proposedRate)}/hr). ${if (notes.isNotBlank()) "Note: $notes" else ""}",
                timestampText = "Just now",
                isSystemNotification = false
            )
        )

        createNotification(
            NotificationEntity(
                id = "notif_${System.currentTimeMillis()}_quote",
                userId = uid,
                type = "NEW_QUOTE",
                title = "Quote Submitted",
                body = "Your quote for job $jobId has been submitted successfully.",
                jobId = jobId,
                createdAt = System.currentTimeMillis()
            )
        )

        return QuoteSubmissionResult.Success(quoteId)
    }

    suspend fun respondToQuote(quoteId: String, jobId: String, accept: Boolean): QuoteResponseResult {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
        return respondToQuoteInternal(quoteId, jobId, actorUid, accept)
    }

    internal suspend fun respondToQuoteInternal(
        quoteId: String,
        jobId: String,
        actorUid: String?,
        accept: Boolean
    ): QuoteResponseResult {
        // 1. Authoritative actor identity.
        val uid = resolveAuthoritativeUid(actorUid)
            ?: return QuoteResponseResult.NotAuthenticated

        // 2. Load authoritative job.
        val job = db.jobPostDao().getJobById(jobId)
            ?: return QuoteResponseResult.JobNotFound

        // 3. Ownership authorization: only the job owner can respond to quotes.
        if (job.postedByUid == null || job.postedByUid != uid) {
            return QuoteResponseResult.NotAuthorized
        }

        // 4. Load quote.
        val quote = db.jobQuoteDao().getQuoteById(quoteId)
            ?: return QuoteResponseResult.QuoteNotFound

        // 5. Job binding check.
        if (quote.jobId != jobId) {
            return QuoteResponseResult.JobMismatch
        }

        // 6. Terminal quote check: only PENDING quotes can be responded to.
        if (quote.status != "PENDING") {
            return QuoteResponseResult.QuoteAlreadyTerminal
        }

        // 7. Job state check: job cannot be in a terminal state.
        val jobStatus = JobStatus.fromValue(job.status)
        if (jobStatus != null && jobStatus in setOf(
                JobStatus.CANCELLED,
                JobStatus.COMPLETED,
                JobStatus.EXPIRED,
                JobStatus.PAYMENT_RELEASED
            )
        ) {
            return QuoteResponseResult.InvalidJobState
        }

        val newStatus = if (accept) "ACCEPTED" else "REJECTED"
        db.jobQuoteDao().updateQuoteStatus(quoteId, newStatus)

        val workerId = quote.workerId.takeIf { it.isNotBlank() }
        if (workerId != null) {
            db.chatMessageDao().insertMessage(
                ChatMessageEntity(
                    id = "msg_${System.currentTimeMillis()}",
                    jobId = jobId,
                    senderId = uid,
                    senderName = "System",
                    isFromUser = false,
                    message = if (accept) "✅ Quote accepted! Worker has been assigned to this job." else "❌ Quote declined.",
                    timestampText = "Just now",
                    isSystemNotification = true
                )
            )

            createNotification(
                NotificationEntity(
                    id = "notif_${System.currentTimeMillis()}_quote_$quoteId",
                    userId = workerId,
                    type = if (accept) "QUOTE_ACCEPTED" else "QUOTE_REJECTED",
                    title = if (accept) "Quote Accepted!" else "Quote Declined",
                    body = if (accept) "Your quote for job $jobId has been accepted!" else "Your quote for job $jobId has been declined.",
                    jobId = jobId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }

        return QuoteResponseResult.Success
    }

    suspend fun countPendingQuotesForJob(jobId: String): Int {
        return db.jobQuoteDao().countPendingQuotesForJob(jobId)
    }

    fun getPendingActions(): Flow<List<PendingActionEntity>> {
        return db.pendingActionDao().getPendingActions()
    }

    /**
     * STEP 0 (offline queue data-loss fix): one-time snapshot read of the pending queue.
     * Flush logic must use this instead of collecting the [getPendingActions] Flow, which
     * never completes and re-fires on every emission while a flush is already running.
     */
    suspend fun getPendingActionsSnapshot(): List<PendingActionEntity> {
        return db.pendingActionDao().getPendingActionsList()
    }

    suspend fun enqueueAction(actionType: String, payloadJson: String) {
        val action = PendingActionEntity(
            id = "action_${System.currentTimeMillis()}",
            actionType = actionType,
            payloadJson = payloadJson
        )
        db.pendingActionDao().insertAction(action)
    }

    suspend fun markActionCompleted(actionId: String) {
        db.pendingActionDao().deleteAction(actionId)
    }

    suspend fun incrementActionRetry(actionId: String) {
        val pending = db.pendingActionDao().getPendingActionsList()
        pending.find { it.id == actionId }?.let { action ->
            val newRetry = action.retryCount + 1
            if (newRetry >= action.maxRetries) {
                db.pendingActionDao().updateActionStatus(actionId, "FAILED", newRetry)
            } else {
                db.pendingActionDao().updateActionStatus(actionId, "PENDING", newRetry)
            }
        }
    }

    suspend fun getPendingActionsCount(): Int {
        return db.pendingActionDao().countPendingActions()
    }

    fun getTrainingForUser(userId: String): Flow<WorkerTrainingEntity?> {
        return db.workerTrainingDao().getTrainingForUser(userId)
    }

    fun getCurrentUserTraining(): Flow<WorkerTrainingEntity?> {
        return db.workerTrainingDao().getCurrentUserTraining()
    }

    suspend fun startTraining(userId: String) {
        val training = WorkerTrainingEntity(
            userId = userId,
            status = "IN_PROGRESS",
            startedAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        db.workerTrainingDao().insertTraining(training)
    }

    suspend fun updateTrainingModule(userId: String, modulesCsv: String, moduleIndex: Int) {
        db.workerTrainingDao().updateCompletedModules(
            userId = userId,
            modules = modulesCsv,
            index = moduleIndex,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun completeTraining(userId: String) {
        db.workerTrainingDao().completeTraining(userId)
        db.userProfileDao().updateTrainingStatus(userId, true)
        createNotification(
            NotificationEntity(
                id = "notif_${System.currentTimeMillis()}_training",
                userId = userId,
                type = "TRAINING_REQUIREMENT",
                title = "Training Completed",
                body = "You have completed the worker training. You can now be activated.",
                jobId = "",
                createdAt = System.currentTimeMillis()
            )
        )
    }

    sealed class ActivationResult {
        object Success : ActivationResult()
        data class Failure(val reason: String) : ActivationResult()
    }

    suspend fun activateWorker(userId: String): ActivationResult {
        val userProfile = db.userProfileDao().getUserProfile(userId).first()
            ?: return ActivationResult.Failure("User not found")

        if (userProfile.workerKycStatus != "APPROVED") {
            return ActivationResult.Failure("KYC verification must be approved before activation")
        }

        val training = db.workerTrainingDao().getTrainingForUser(userId).first()
        val isTrainingCompleted = training?.status == "COMPLETED" && userProfile.trainingCompleted

        if (!isTrainingCompleted) {
            return ActivationResult.Failure("Worker must complete mandatory training before activation")
        }

        db.userProfileDao().updateWorkerActivation(userId, true)
        return ActivationResult.Success
    }

    companion object {
        /**
         * C2-2 Fix #4 (F4): literal, non-identity values that must never be accepted as an owner
         * or sender id. Firebase will never issue these; they are the fallbacks this codebase
         * historically used when no authenticated user was available.
         */
        private val SYNTHETIC_ID_SENTINELS = setOf("unknown", "system", "anonymous", "guest", "none")

        /**
         * C2-2 job-status authorization: the states in which the CLIENT (job owner) is still driving
         * the setup of the job -- publishing it, collecting quotes, choosing a worker, accepting.
         * These mirror the client-facing part of `JobStatus.isValidTransition`, which continues to
         * decide whether the move is *structurally* legal; this set only decides *who* may make it.
         */
        val CLIENT_SETUP_STATES = setOf(
            JobStatus.DRAFT, JobStatus.POSTED, JobStatus.QUOTING,
            JobStatus.WORKER_SELECTED, JobStatus.ACCEPTED
        )

        /** States a job owner may cancel their own job from. Never includes CANCELLED/EXPIRED. */
        val CANCELLABLE_STATES = setOf(
            JobStatus.DRAFT, JobStatus.POSTED, JobStatus.QUOTING, JobStatus.WORKER_SELECTED,
            JobStatus.ACCEPTED, JobStatus.WORKER_EN_ROUTE, JobStatus.ARRIVED,
            JobStatus.IN_PROGRESS, JobStatus.DISPUTED
        )

        /** States on which either party may raise a dispute. */
        val DISPUTABLE_STATES = setOf(
            JobStatus.ACCEPTED, JobStatus.WORKER_EN_ROUTE, JobStatus.ARRIVED,
            JobStatus.IN_PROGRESS, JobStatus.COMPLETED, JobStatus.PAYMENT_RELEASED
        )

        /**
         * AUDIT-W-QUOTE: The states in which a job may legitimately accept quotes from workers.
         */
        val QUOTABLE_JOB_STATES = setOf(JobStatus.POSTED, JobStatus.QUOTING)

        /**
         * AUDIT-W-QUOTE: Pure quote submission authorization policy.
         * May [workerUid] submit a quote for [job]?
         *
         * Pure, actor-explicit, fail-closed policy:
         * 1. [workerUid] must be an authoritative Firebase UID (resolved via [resolveAuthoritativeUid]).
         * 2. [job] must be in a quotable state ([QUOTABLE_JOB_STATES]: POSTED or QUOTING).
         * 3. [job.postedByUid] must exist (legacy null-owner jobs belong to nobody and cannot be quoted).
         * 4. Worker cannot quote their own job ([job.postedByUid] != [workerUid]).
         */
        fun isQuoteSubmissionAuthorized(workerUid: String?, job: JobPostEntity): Boolean {
            val uid = resolveAuthoritativeUid(workerUid) ?: return false
            val status = JobStatus.fromValue(job.status) ?: return false
            if (status !in QUOTABLE_JOB_STATES) return false
            if (job.postedByUid == null || job.postedByUid == uid) return false
            return true
        }

        /**
         * AUDIT-W-QUOTE: Pure quote response authorization policy.
         * May [actorUid] accept or decline [quote] on [job]?
         *
         * Pure, actor-explicit, fail-closed policy:
         * 1. [actorUid] must be an authoritative Firebase UID.
         * 2. Only the job owner ([job.postedByUid] == [actorUid]) may accept/reject quotes.
         * 3. [quote.jobId] must match [job.id].
         * 4. [quote.status] must be "PENDING" (terminal quotes cannot be re-decided).
         * 5. [job.status] must not be terminal (CANCELLED, COMPLETED, EXPIRED).
         */
        fun isQuoteResponseAuthorized(
            actorUid: String?,
            job: JobPostEntity,
            quote: JobQuoteEntity
        ): Boolean {
            val uid = resolveAuthoritativeUid(actorUid) ?: return false
            if (job.postedByUid == null || job.postedByUid != uid) return false
            if (quote.jobId != job.id) return false
            if (quote.status != "PENDING") return false
            val jobStatus = JobStatus.fromValue(job.status) ?: return false
            if (jobStatus in setOf(JobStatus.CANCELLED, JobStatus.COMPLETED, JobStatus.EXPIRED, JobStatus.PAYMENT_RELEASED)) return false
            return true
        }

        /**
         * C2-2 Fix #3 (F7): the ONLY identity a security-sensitive operation may use.
         *
         * `FirebaseAuth.currentUser.uid` is authoritative; a missing, blank, whitespace-padded or
         * legacy demo id (`user_default_me`) yields `null` so the caller fails closed instead of
         * promoting the newest Room row, a placeholder, an anonymous or a synthetic id.
         */
        fun resolveAuthoritativeUid(firebaseUid: String?): String? =
            firebaseUid?.trim()?.takeIf { candidate ->
                if (candidate.isEmpty() || candidate == "user_default_me") return@takeIf false
                // C2-2 Fix #4 (F4): a real Firebase uid (including an anonymous one) is
                // alphanumeric. A value carrying whitespace or brackets is presentation text
                // ("Robert K. (Sunnyside)", "You (Nakawa)") and must never be treated as an
                // identity — and neither may the literal synthetic sentinels this codebase
                // historically fell back to.
                if (candidate.any { it.isWhitespace() || it == '(' || it == ')' }) return@takeIf false
                candidate.lowercase() !in SYNTHETIC_ID_SENTINELS
            }

        /**
         * C2-2 Fix #3 (F7): the ONLY profile whose contact details may be broadcast in an SOS
         * alert.
         *
         * Without an authenticated uid, without a matching row, or with a row that carries no
         * usable phone number, this returns `null` — callers must fail closed rather than send
         * another account's (or a fabricated) name and number to emergency responders.
         */
        fun resolveSosContactProfile(
            firebaseUid: String?,
            row: UserProfileEntity?
        ): UserProfileEntity? {
            val uid = resolveAuthoritativeUid(firebaseUid) ?: return null
            return row?.takeIf { it.id == uid && it.phoneNumber.isNotBlank() }
        }

        /**
         * RATE-003 rating **authorization** -- may [actorUid] rate [job], crediting [toUserId]?
         *
         * Pure, actor-explicit and fail-closed, mirroring [isJobTransitionAuthorized]. A client rates
         * the worker who actually did their job, only after escrow was released -- the same
         * `RELEASED_TO_WORKER` state under which the "Rate Worker" action appears in the UI:
         *
         *  1. The actor must own the job (`postedByUid`, the F4 ownership identity). A legacy job
         *     with a null `postedByUid` belongs to nobody and may be rated by nobody.
         *  2. The job must be in the terminal `RELEASED_TO_WORKER` escrow state (job-status rule):
         *     you may not rate an in-progress, quoted, disputed or merely funded job.
         *  3. The target ([toUserId]) must be exactly the provider actually assigned to THIS job, so
         *     a caller cannot credit or defame an unrelated worker by passing an arbitrary id.
         *
         * Any missing/blank/synthetic actor uid, missing owner, wrong escrow state, or mismatched /
         * absent provider all deny. The caller-supplied [toUserId] is never trusted to establish a
         * relationship on its own; it is only accepted when it matches the real job row.
         */
        fun isRatingAuthorized(
            actorUid: String?,
            job: JobPostEntity,
            toUserId: String
        ): Boolean {
            // F7/F4: only a real, authoritative uid is an identity; synthetic/blank/padded ids deny.
            val uid = resolveAuthoritativeUid(actorUid) ?: return false
            if (job.postedByUid == null || job.postedByUid != uid) return false

            // Job-status rule: only a released (completed-and-paid) job may be rated.
            if (job.escrowStatus != "RELEASED_TO_WORKER") return false

            // The credited party must be the provider actually assigned to THIS job.
            val provider = job.assignedProviderId ?: return false
            return provider.isNotBlank() && provider == toUserId
        }

        /**
         * SAFE-003 SOS **stand-down** authorization -- may [actorUid] clear the emergency SOS flag
         * on [job]?
         *
         * Pure, actor-explicit and fail-closed, mirroring [isRatingAuthorized]. Raising the panic
         * flag is an emergency action, but standing it down is a **status mutation on somebody's
         * job**, so it is owner-gated:
         *
         *  1. The actor must be a real, authoritative uid (F7 rule); synthetic/blank/padded ids deny.
         *  2. The actor must own the job (`postedByUid`, the F4 ownership identity). A legacy job
         *     with a null `postedByUid` belongs to nobody and may be stood down by nobody, and a
         *     stranger can never silence another account's emergency flag.
         *  3. The flag must actually be raised -- there is nothing to clear on a job that is not in
         *     an SOS state, and clearing a non-SOS row is a no-op we refuse to authorise.
         *
         * The job is always loaded by the secured boundary from its own id, so the caller can never
         * assert an ownership relationship of its choosing.
         */
        fun isSosClearAuthorized(
            actorUid: String?,
            job: JobPostEntity
        ): Boolean {
            // F7/F4: only a real, authoritative uid is an identity; synthetic/blank/padded ids deny.
            val uid = resolveAuthoritativeUid(actorUid) ?: return false
            if (job.postedByUid == null || job.postedByUid != uid) return false
            // Nothing to stand down unless the flag is actually raised.
            return job.isSosTriggered
        }

        /**
         * AUDIT-A-SOS: SOS **raise** authorization policy -- may [actorUid] raise an emergency SOS
         * on [job]?
         *
         * Raising the panic flag is an emergency action, but it mutates somebody's job and
         * broadcasts their location, so it is participant-gated and fail-closed, mirroring
         * [isSosClearAuthorized]:
         *
         *  1. The actor must be a real, authoritative uid (F7 rule); synthetic/blank/padded ids deny.
         *  2. The actor must be a participant of the job: the owner (`postedByUid`) or the assigned
         *     provider (`assignedProviderId`). A stranger can never trigger another account's SOS.
         *  3. A legacy job with a null `postedByUid` belongs to nobody and may be raised on by
         *     nobody.
         *
         * The job is always loaded by the secured boundary from its own id, so the caller can never
         * assert an ownership relationship of its choosing.
         */
        fun isSosRaiseAuthorized(
            actorUid: String?,
            job: JobPostEntity
        ): Boolean {
            // F7/F4: only a real, authoritative uid is an identity; synthetic/blank/padded ids deny.
            val uid = resolveAuthoritativeUid(actorUid) ?: return false
            // A legacy job with a null postedByUid belongs to nobody.
            if (job.postedByUid == null) return false
            // Participant gate: owner OR assigned provider.
            val isOwner = job.postedByUid == uid
            // The assigned-provider gate uses assignedProviderUid (the assigned account's
            // Firebase Auth UID). assignedProviderId is the service_providers catalog foreign
            // key (a display/join id, e.g. "prov_1") — DISPLAY-ONLY, never an authorization
            // key. Comparing an account uid against it is the identity-semantics defect this
            // gate no longer contains.
            val isAssignedProvider = job.assignedProviderUid == uid
            return isOwner || isAssignedProvider
        }

        val initialProviders = listOf(
            ServiceProviderEntity(
                id = "prov_1",
                name = "Elena Rostova",
                tagline = "Master Plumber & Pipe Specialist",
                category = "Plumbing",
                neighborhood = "Maplewood East",
                residenceYears = 4,
                hourlyRate = 277500.0,
                distanceMiles = 0.8,
                rating = 4.9,
                completedJobsCount = 42,
                avgResponseTime = "12 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "Licensed master plumber with 12+ years experience. Specializes in emergency pipe leaks, water heater installs, and drain clearing for neighborhood residents.",
                skillsCsv = "Pipe Leak Repair, Drain Clearing, Water Heater Install, Garbage Disposal Fix",
                latitude = 37.7749,
                longitude = -122.4194,
                endorsementsCount = 31
            ),
            ServiceProviderEntity(
                id = "prov_2",
                name = "Marcus Vance",
                tagline = "Certified Master Electrician",
                category = "Electrical",
                neighborhood = "Sunnyside District",
                residenceYears = 6,
                hourlyRate = 314500.0,
                distanceMiles = 1.2,
                rating = 5.0,
                completedJobsCount = 58,
                avgResponseTime = "8 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "Licensed commercial & residential electrician. Expert in EV charger line runs, smart breaker upgrades, and fixture rewiring.",
                skillsCsv = "240V EV Charging, Panel Upgrades, Recessed Lighting, Ceiling Fans",
                latitude = 37.7810,
                longitude = -122.4110,
                endorsementsCount = 45
            ),
            ServiceProviderEntity(
                id = "prov_3",
                name = "David Chen",
                tagline = "Automotive Specialist & Mobile Diagnostic",
                category = "Mechanic",
                neighborhood = "Oakridge Heights",
                residenceYears = 3,
                hourlyRate = 240500.0,
                distanceMiles = 2.1,
                rating = 4.8,
                completedJobsCount = 31,
                avgResponseTime = "15 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "ASE-certified mechanic providing driveway maintenance, brake jobs, battery installs, and OBD-II engine diagnostics.",
                skillsCsv = "Brake Replacement, Battery Swap, Oil & Filter, Engine Diagnostics",
                latitude = 37.7650,
                longitude = -122.4280,
                endorsementsCount = 22
            ),
            ServiceProviderEntity(
                id = "prov_4",
                name = "Sarah Jenkins",
                tagline = "STEM & High School Math Tutor",
                category = "Tutoring",
                neighborhood = "Sunnyside District",
                residenceYears = 5,
                hourlyRate = 185000.0,
                distanceMiles = 0.5,
                rating = 4.9,
                completedJobsCount = 64,
                avgResponseTime = "5 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "M.S. in Applied Mathematics. Tutoring middle & high school students in AP Calculus, AP Physics, Algebra II, and SAT prep.",
                skillsCsv = "AP Calculus, AP Physics, SAT Math Prep, Geometry",
                latitude = 37.7710,
                longitude = -122.4160,
                endorsementsCount = 52
            ),
            ServiceProviderEntity(
                id = "prov_5",
                name = "Devon Miller",
                tagline = "Eco-Friendly Deep Clean Specialist",
                category = "Cleaning",
                neighborhood = "Maplewood East",
                residenceYears = 2,
                hourlyRate = 166500.0,
                distanceMiles = 1.5,
                rating = 4.7,
                completedJobsCount = 29,
                avgResponseTime = "20 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "Detail-oriented deep cleaner using non-toxic pet-safe plant based products. Move-in/move-out and post-renovation cleanups.",
                skillsCsv = "Deep Cleaning, Eco Sanitation, Move-Out Prep, Carpet Washing",
                latitude = 37.7680,
                longitude = -122.4050,
                endorsementsCount = 18
            ),
            ServiceProviderEntity(
                id = "prov_6",
                name = "Amina Al-Mansoor",
                tagline = "Custom Woodwork & Finish Carpenter",
                category = "Carpentry",
                neighborhood = "Riverdale North",
                residenceYears = 7,
                hourlyRate = 333000.0,
                distanceMiles = 3.4,
                rating = 5.0,
                completedJobsCount = 46,
                avgResponseTime = "10 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "Craftsman carpenter crafting built-in shelving, hardwood floor repairs, door fitting, and custom deck repairs.",
                skillsCsv = "Custom Shelving, Trim & Crown Molding, Hardwood Repair, Door Framing",
                latitude = 37.7890,
                longitude = -122.4350,
                endorsementsCount = 38
            ),
            ServiceProviderEntity(
                id = "prov_7",
                name = "Carlos Ruiz",
                tagline = "Lawn Care & Irrigation Tech",
                category = "Gardening",
                neighborhood = "Sunnyside District",
                residenceYears = 4,
                hourlyRate = 148000.0,
                distanceMiles = 0.9,
                rating = 4.8,
                completedJobsCount = 52,
                avgResponseTime = "15 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "Local landscaping pro offering lawn aeration, sprinkler repairs, hedge trimming, and seasonal cleanup.",
                skillsCsv = "Irrigation Repair, Lawn Aeration, Hedge Trimming, Garden Prep",
                latitude = 37.7730,
                longitude = -122.4220,
                endorsementsCount = 27
            ),
            ServiceProviderEntity(
                id = "prov_8",
                name = "Maya Lin",
                tagline = "Appliance Repair Specialist",
                category = "Mechanic",
                neighborhood = "Oakridge Heights",
                residenceYears = 5,
                hourlyRate = 259000.0,
                distanceMiles = 1.8,
                rating = 4.9,
                completedJobsCount = 37,
                avgResponseTime = "18 mins",
                isVerifiedResident = true,
                isBackgroundChecked = true,
                bio = "Fixing refrigerators, washing machines, dryers, and dishwashers with genuine OEM replacement parts.",
                skillsCsv = "Washer/Dryer Fix, Refrigerator Repair, Dishwasher Service",
                latitude = 37.7610,
                longitude = -122.4200,
                endorsementsCount = 29
            )
        )

        val initialJobs = listOf(
            JobPostEntity(
                id = "job_1",
                title = "Emergency main valve leak under kitchen sink",
                category = "Plumbing",
                description = "Constant drip under shutoff valve near disposal unit. Customer provides new shutoff valve and Teflon tape on site.",
                postedByResident = "Robert K. (Sunnyside)",
                district = "Sunnyside District",
                budget = 455840.0,
                urgency = "Today",
                status = "In Progress",
                assignedProviderId = "prov_1",
                assignedProviderName = "Elena Rostova",
                createdTimestamp = System.currentTimeMillis() - 3600000,
                estimatedHours = 1.5,
                materialsCost = 0.0,
                safetyFee = 41625.0,
                materialsProvidedBy = "RESIDENT",
                laborHours = 1.5,
                laborRate = 277500.0,
                laborTotal = 416250.0,
                materialsSubtotal = 0.0,
                platformFee = 41625.0,
                grandTotal = 457875.0,
                beforePhotoUrl = "img_before_leak",
                afterPhotoUrl = "img_after_repair",
                paymentChannel = "MTN_MOMO",
                momoPhoneNumber = "+256 772 198 420",
                escrowStatus = "FUNDS_LOCKED_IN_ESCROW"
            ),
            JobPostEntity(
                id = "job_2",
                title = "EV Charger 240V Outlet Installation in Garage",
                category = "Electrical",
                description = "Install NEMA 14-50 receptacle near main electrical panel in attached garage. Worker sources 50A breaker and 6/3 Romex wire.",
                postedByResident = "Sarah M. (Maplewood)",
                district = "Maplewood East",
                budget = 1333850.0,
                urgency = "This Week",
                status = "Accepted",
                assignedProviderId = "prov_2",
                assignedProviderName = "Marcus Vance",
                createdTimestamp = System.currentTimeMillis() - 7200000,
                estimatedHours = 3.0,
                materialsCost = 277500.0,
                safetyFee = 94350.0,
                materialsProvidedBy = "PROVIDER",
                laborHours = 3.0,
                laborRate = 314500.0,
                laborTotal = 943500.0,
                materialsSubtotal = 277500.0,
                platformFee = 94350.0,
                grandTotal = 1315350.0,
                paymentChannel = "MTN_MOMO",
                momoPhoneNumber = "+256 751 883 910",
                escrowStatus = "DEPOSIT_PROMPTED"
            ),
            JobPostEntity(
                id = "job_3",
                title = "Fix squeaky hardwood floor & loose stair step",
                category = "Carpentry",
                description = "2 squeaky oak floorboards in hallway and top riser on staircase flexes under weight. Resident provides screws and glue.",
                postedByResident = "David B. (Oakridge)",
                district = "Oakridge Heights",
                budget = 732600.0,
                urgency = "Today",
                status = "Requested",
                assignedProviderId = null,
                assignedProviderName = null,
                createdTimestamp = System.currentTimeMillis() - 10800000,
                estimatedHours = 2.0,
                materialsCost = 0.0,
                safetyFee = 66600.0,
                materialsProvidedBy = "RESIDENT",
                laborHours = 2.0,
                laborRate = 333000.0,
                laborTotal = 666000.0,
                materialsSubtotal = 0.0,
                platformFee = 66600.0,
                grandTotal = 732600.0,
                paymentChannel = "MTN_MOMO",
                momoPhoneNumber = "+256 774 302 911",
                escrowStatus = "QUOTE_PENDING"
            ),
            JobPostEntity(
                id = "job_4",
                title = "Brake Pad Replacement & Rotor Polish - 2021 Civic",
                category = "Mechanic",
                description = "Squealing noise when braking on front wheels. Have replacement ceramic pads ready on site.",
                postedByResident = "Annette W. (Sunnyside)",
                district = "Sunnyside District",
                budget = 661375.0,
                urgency = "Today",
                status = "Review",
                assignedProviderId = "prov_3",
                assignedProviderName = "David Chen",
                createdTimestamp = System.currentTimeMillis() - 14400000,
                estimatedHours = 2.5,
                materialsCost = 0.0,
                safetyFee = 60125.0,
                materialsProvidedBy = "RESIDENT",
                laborHours = 2.5,
                laborRate = 240500.0,
                laborTotal = 601250.0,
                materialsSubtotal = 0.0,
                platformFee = 60125.0,
                grandTotal = 661375.0,
                beforePhotoUrl = "img_before_leak",
                afterPhotoUrl = "img_after_repair",
                paymentChannel = "MTN_MOMO",
                momoPhoneNumber = "+256 788 112 009",
                escrowStatus = "PROOF_SUBMITTED"
            ),
            JobPostEntity(
                id = "job_5",
                title = "Weekly AP Physics & Calculus Prep",
                category = "Tutoring",
                description = "10th grade student needing 2 hours/week problem solving prep for AP exams.",
                postedByResident = "Gregory S. (Riverdale)",
                district = "Riverdale North",
                budget = 407000.0,
                urgency = "Scheduled",
                status = "Completed",
                assignedProviderId = "prov_4",
                assignedProviderName = "Sarah Jenkins",
                createdTimestamp = System.currentTimeMillis() - 86400000,
                estimatedHours = 2.0,
                materialsCost = 0.0,
                safetyFee = 37000.0,
                materialsProvidedBy = "NONE_NEEDED",
                laborHours = 2.0,
                laborRate = 185000.0,
                laborTotal = 370000.0,
                materialsSubtotal = 0.0,
                platformFee = 37000.0,
                grandTotal = 407000.0,
                paymentChannel = "MTN_MOMO",
                momoPhoneNumber = "+256 779 400 112",
                escrowStatus = "RELEASED_TO_WORKER"
            ),
            JobPostEntity(
                id = "job_6",
                title = "Post-renovation deep clean for 3BR bungalow",
                category = "Cleaning",
                description = "Dust removal from baseboards, windowsills, and hardwood floors after kitchen remodeling.",
                postedByResident = "Priya N. (Maplewood)",
                district = "Maplewood East",
                budget = 732600.0,
                urgency = "This Week",
                status = "Requested",
                assignedProviderId = null,
                assignedProviderName = null,
                createdTimestamp = System.currentTimeMillis() - 28800000,
                estimatedHours = 4.0,
                materialsCost = 0.0,
                safetyFee = 66600.0,
                materialsProvidedBy = "RESIDENT",
                laborHours = 4.0,
                laborRate = 166500.0,
                laborTotal = 666000.0,
                materialsSubtotal = 0.0,
                platformFee = 66600.0,
                grandTotal = 732600.0,
                paymentChannel = "MTN_MOMO",
                momoPhoneNumber = "+256 770 551 234",
                escrowStatus = "QUOTE_PENDING"
            )
        )

        val initialChatMessages = listOf(
            ChatMessageEntity(
                id = "m1",
                jobId = "job_1",
                senderName = "System",
                isFromUser = false,
                message = "Job assigned to Elena Rostova (Verified Master Plumber).",
                timestampText = "10:00 AM",
                isSystemNotification = true
            ),
            ChatMessageEntity(
                id = "m2",
                jobId = "job_1",
                senderName = "Elena Rostova",
                isFromUser = false,
                message = "Good morning Robert! I'm 4 blocks away in Sunnyside finishing a pressure check. Can stop by in 15 mins.",
                timestampText = "10:05 AM",
                isSystemNotification = false
            ),
            ChatMessageEntity(
                id = "m3",
                jobId = "job_1",
                senderName = "Robert K.",
                isFromUser = true,
                message = "Great! I shut off the main valve so water is stopped. Side gate is open.",
                timestampText = "10:08 AM",
                isSystemNotification = false
            ),
            ChatMessageEntity(
                id = "m4",
                jobId = "job_1",
                senderName = "Elena Rostova",
                isFromUser = false,
                message = "I've arrived and parked out front.",
                timestampText = "10:20 AM",
                isSystemNotification = false
            ),

            ChatMessageEntity(
                id = "m5",
                jobId = "job_2",
                senderName = "Marcus Vance",
                isFromUser = false,
                message = "Hi Sarah! I reviewed the garage photo you sent. Main panel has two spare 50A slots. I can execute this Thursday at 2 PM.",
                timestampText = "Yesterday",
                isSystemNotification = false
            )
        )

        val initialEndorsements = listOf(
            SkillEndorsementEntity(
                id = "e1",
                providerId = "prov_1",
                skillName = "Pipe Leak Repair",
                endorsedByNeighborName = "Marcus Vance",
                neighborDistrict = "Sunnyside District"
            ),
            SkillEndorsementEntity(
                id = "e2",
                providerId = "prov_1",
                skillName = "Drain Clearing",
                endorsedByNeighborName = "Annette W.",
                neighborDistrict = "Maplewood East"
            ),
            SkillEndorsementEntity(
                id = "e3",
                providerId = "prov_2",
                skillName = "240V EV Charging",
                endorsedByNeighborName = "David Chen",
                neighborDistrict = "Oakridge Heights"
            )
        )
    }

    suspend fun approveMaterialAdvance(jobId: String, materialsSubtotal: Double): Double {
        val advanceAmount = (materialsSubtotal * 0.5).coerceAtMost(materialsSubtotal)
        db.jobPostDao().updateMaterialAdvance(jobId, true, advanceAmount, "DISBURSED", "")
        return advanceAmount
    }

    suspend fun triggerEmergencySos(jobId: String, latitude: Double, longitude: Double, district: String): SosRaiseResult {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return SosRaiseResult.NotAuthenticated
        return raiseSos(jobId, latitude, longitude, district, actorUid, sendSms = true)
    }

    /**
     * AUDIT-A-SOS: internal test seam — raises an SOS with an explicitly injected actor UID
     * instead of resolving `FirebaseAuth.currentUser.uid`. Mirrors `submitQuoteInternal`.
     */
    internal suspend fun triggerEmergencySosInternal(
        jobId: String,
        latitude: Double,
        longitude: Double,
        district: String,
        actorUid: String
    ): SosRaiseResult = raiseSos(jobId, latitude, longitude, district, actorUid, sendSms = false)

    suspend fun triggerSosAlert(jobId: String): SosRaiseResult {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return SosRaiseResult.NotAuthenticated
        val job = db.jobPostDao().getJobById(jobId) ?: return SosRaiseResult.JobNotFound
        return raiseSos(jobId, 0.0, 0.0, job.district, actorUid, sendSms = false)
    }

    /**
     * AUDIT-A-SOS: internal test seam — raises an SOS alert with an explicitly injected actor UID.
     */
    internal suspend fun triggerSosAlertInternal(jobId: String, actorUid: String): SosRaiseResult {
        val job = db.jobPostDao().getJobById(jobId) ?: return SosRaiseResult.JobNotFound
        return raiseSos(jobId, 0.0, 0.0, job.district, actorUid, sendSms = false)
    }

    /**
     * AUDIT-A-SOS: shared secured raise core. Identity (who), authorization (may they) and the
     * authoritative Room incident record are decided here; the Firestore mirror is best-effort
     * and never rolls back the local record.
     */
    private suspend fun raiseSos(
        jobId: String,
        latitude: Double,
        longitude: Double,
        district: String,
        actorUid: String,
        sendSms: Boolean
    ): SosRaiseResult {
        val job = db.jobPostDao().getJobById(jobId) ?: return SosRaiseResult.JobNotFound
        if (!isSosRaiseAuthorized(actorUid, job)) return SosRaiseResult.NotAuthorized

        db.jobPostDao().setSosAlert(jobId, true)
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "🚨 EMERGENCY SOS",
                isFromUser = false,
                message = "🚨 EMERGENCY PANIC BUTTON TRIGGERED! Live GPS location & job telemetry broadcasted to local emergency contacts and platform security.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )

        // Authoritative local incident record (Room is the source of truth).
        val incidentDistrict = district.takeIf { it.isNotBlank() } ?: job.district
        val incident = SosIncidentEntity(
            id = "sos_$jobId",
            jobId = jobId,
            postedByUid = job.postedByUid ?: "",
            assignedProviderId = job.assignedProviderId,
            assignedProviderUid = job.assignedProviderUid,
            status = SosIncidentEntity.STATUS_ACTIVE,
            latitude = latitude,
            longitude = longitude,
            district = incidentDistrict,
            raisedAt = System.currentTimeMillis()
        )
        db.sosIncidentDao().insertSosIncident(incident)
        // Best-effort mirror — a network failure must never roll back the local incident, but the
        // outcome is now recorded and the sync is queued for retry instead of being forgotten
        // (R04-P2-05: Room and Firestore used to diverge with nothing to reconcile them).
        val mirrored = firebaseService.syncSosIncidentToFirestore(incident)
        db.sosIncidentDao().setFirestoreMirrored(incident.id, mirrored)
        if (!mirrored) {
            enqueueMirrorRetry(incident.id)
        }

        if (sendSms) {
            val currentUserId = authenticatedProfileOnce()?.id
            if (currentUserId != null) {
                createNotification(
                    NotificationEntity(
                        id = "notif_${System.currentTimeMillis()}_sos",
                        userId = currentUserId,
                        type = "SOS_EVENT",
                        title = "Emergency SOS Triggered",
                        body = "Your emergency alert has been sent. Help is on the way.",
                        jobId = jobId,
                        createdAt = System.currentTimeMillis()
                    )
                )
                // R05-P2-01: this device no longer "sends" an SOS SMS at all. The stub that
                // fabricated a `SENT` status is deleted; the SMS to emergency contacts is produced
                // by the server-side `onSosIncidentCreated` trigger, which holds the Africa's
                // Talking credential and can report a real provider outcome. What this device owns
                // is the local incident above and its Firestore mirror, which is what the trigger
                // reacts to. Emergency contact details are resolved server-side from the incident's
                // authenticated owner — never from this device's newest Room row.
            }
        }

        return SosRaiseResult.Success
    }

    /**
     * SAFE-003: stand down an active emergency SOS flag. The panic flag is a safety latch the user
     * raised from the escrow sheet; clearing it is a **status mutation on somebody's job**, so it is
     * owner-gated and fail-closed (mirroring [submitJobRating]):
     *
     * 1. The actor is resolved from `FirebaseAuth.currentUser.uid` via [resolveAuthoritativeUid];
     *    it is never a caller-supplied id. No authoritative uid ⇒ [SosClearResult.NotAuthenticated]
     *    and the flag is left untouched.
     * 2. The REAL job row is loaded by id (the caller-supplied `jobId` is never trusted to establish
     *    ownership). A missing row ⇒ [SosClearResult.JobNotFound].
     * 3. [isSosClearAuthorized] decides whether that actor may clear that job's flag — owner-only,
     *    and only when it is actually raised. A failure returns [SosClearResult.NotAuthorized] and
     *    the database is untouched, so a stranger can never silence another account's emergency.
     *
     * Returns [SosClearResult.Success] only after the flag is actually lowered.
     */
    suspend fun clearEmergencySos(jobId: String): SosClearResult {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return SosClearResult.NotAuthenticated

        val job = db.jobPostDao().getJobById(jobId) ?: return SosClearResult.JobNotFound

        if (!isSosClearAuthorized(actorUid, job)) return SosClearResult.NotAuthorized

        db.jobPostDao().setSosAlert(jobId, false)
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "🚨 EMERGENCY SOS",
                isFromUser = false,
                message = "✅ Emergency SOS stood down by the job owner. Safety status is back to normal.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
        return SosClearResult.Success
    }


    // ==================== H7 / AUDIT-A-SOS: SOS Incident Methods ====================

    /** ADMIN DESK queue contents, straight from the authoritative Room record. */
    fun getActiveSosIncidents(): Flow<List<SosIncidentEntity>> =
        db.sosIncidentDao().getActiveSosIncidents()

    /**
     * ADMIN DESK: acknowledge [incidentId] as reviewed. The server callable enforces the
     * admin claim; this boundary is the client-side fail-closed gate. Returns true only
     * after the Room record is actually transitioned to ACKNOWLEDGED.
     */
    suspend fun acknowledgeSos(incidentId: String, acknowledgedBy: String): Boolean {
        // The caller (ViewModel) resolves the authenticated UID and fails closed on null; this
        // boundary validates the value it is given instead of re-reading a second identity source.
        val actorUid = resolveAuthoritativeUid(acknowledgedBy) ?: return false
        val incident = db.sosIncidentDao().getSosIncidentById(incidentId) ?: return false
        if (incident.status != SosIncidentEntity.STATUS_ACTIVE) return false
        db.sosIncidentDao().updateSosIncidentStatus(
            incidentId,
            SosIncidentEntity.STATUS_ACKNOWLEDGED,
            System.currentTimeMillis(),
            0L,
            actorUid
        )
        return true
    }

    /**
     * ADMIN DESK: resolve [incidentId] as complete. The server callable enforces the
     * admin claim; this boundary is the client-side fail-closed gate. Returns true only
     * after the Room record is actually transitioned to CLEARED.
     */
    suspend fun resolveSosIncident(incidentId: String, resolvedBy: String): Boolean {
        // Same contract as acknowledgeSos: the explicit actor is validated, never re-derived.
        val actorUid = resolveAuthoritativeUid(resolvedBy) ?: return false
        val incident = db.sosIncidentDao().getSosIncidentById(incidentId) ?: return false
        if (incident.status != SosIncidentEntity.STATUS_ACTIVE) return false
        db.sosIncidentDao().updateSosIncidentStatus(
            incidentId,
            SosIncidentEntity.STATUS_CLEARED,
            0L,
            System.currentTimeMillis(),
            actorUid
        )
        return true
    }

    // ==================== DISP-001: Dispute Methods ====================

    fun getAllDisputes(): Flow<List<DisputeEntity>> = db.disputeDao().getAllDisputes()

    fun getDisputesForUser(userId: String): Flow<List<DisputeEntity>> = db.disputeDao().getDisputesForUser(userId)

    suspend fun getDisputeForJob(jobId: String): DisputeEntity? = db.disputeDao().getDisputeForJob(jobId)

    suspend fun createDispute(dispute: DisputeEntity) {
        db.disputeDao().insertDispute(dispute)
        // AUDIT-A-ARBITRATE: mirror the filed dispute to Firestore so the admin
        // `getDisputesQueue` callable (which reads the `disputes` collection) actually
        // receives it — otherwise the arbitration console always shows an empty queue.
        // Best-effort like the job/provider syncs; Room stays the local record.
        firebaseService.syncDisputeToFirestore(dispute)
    }

    suspend fun updateDisputeStatus(disputeId: String, status: String, adminNotes: String = "", resolutionType: String = "", amount: Double = 0.0, resolvedBy: String = "") {
        db.disputeDao().updateDisputeStatus(disputeId, status, adminNotes, resolutionType, amount, System.currentTimeMillis(), resolvedBy)

        val dispute = db.disputeDao().getDisputeForJob(disputeId)
        dispute?.let {
            val title = when (status) {
                "RESOLVED" -> "Dispute Resolved"
                "REJECTED" -> "Dispute Rejected"
                "ESCALATED" -> "Dispute Escalated"
                else -> "Dispute Updated"
            }
            val body = when (status) {
                "RESOLVED" -> "Your dispute has been resolved. Resolution: $resolutionType"
                "REJECTED" -> "Your dispute has been rejected."
                "ESCALATED" -> "Your dispute has been escalated for further review."
                else -> "Dispute status updated to $status."
            }
            createNotification(
                com.example.data.db.NotificationEntity(
                    id = "notif_${System.currentTimeMillis()}_dispute_$disputeId",
                    userId = it.reportedBy,
                    type = "DISPUTE_UPDATE",
                    title = title,
                    body = body,
                    jobId = it.jobId,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    // ==================== RATE-001: Rating Methods ====================

    fun getAllRatings(): Flow<List<RatingEntity>> = db.ratingDao().getAllRatings()

    fun getRatingsForUser(userId: String): Flow<List<RatingEntity>> = db.ratingDao().getRatingsForUser(userId)

    suspend fun getRatingForJob(jobId: String): RatingEntity? = db.ratingDao().getRatingForJob(jobId)

    suspend fun hasUserRatedJob(fromUserId: String, toUserId: String, jobId: String): Boolean {
        return db.ratingDao().getRatingFromToJob(fromUserId, toUserId, jobId) != null
    }

    suspend fun getAverageRatingForUser(userId: String): Double = db.ratingDao().getAverageRatingForUser(userId) ?: 0.0

    suspend fun submitRating(rating: RatingEntity) {
        db.ratingDao().insertRating(rating)
    }

    /**
     * Outcome of a secured rating submission. Discriminated so the UI can surface a precise,
     * honest message and so tests can assert each failure path without a persisted side effect.
     */
    sealed class RatingResult {
        object Success : RatingResult()
        object NotAuthenticated : RatingResult()
        object InvalidRating : RatingResult()
        object JobNotFound : RatingResult()
        object NotAuthorized : RatingResult()
        object AlreadyRated : RatingResult()
    }

    /**
     * SAFE-003: outcome of an SOS stand-down attempt, mirroring [RatingResult]. Each distinct
     * failure lets the caller surface an honest message and lets tests assert each path without a
     * persisted side effect.
     */
    sealed class SosClearResult {
        object Success : SosClearResult()
        object NotAuthenticated : SosClearResult()
        object JobNotFound : SosClearResult()
        object NotAuthorized : SosClearResult()
    }

    /**
     * Outcome of a secured SOS raise. Discriminated so the caller can surface a precise,
     * honest message and tests can assert each failure path without a persisted side effect.
     */
    sealed class SosRaiseResult {
        object Success : SosRaiseResult()
        object NotAuthenticated : SosRaiseResult()
        object JobNotFound : SosRaiseResult()
        object NotAuthorized : SosRaiseResult()
    }

    /**
     * The single SECURED boundary for submitting a job rating. Identity (who), authorization (may
     * they) and structural validity are all decided here, in that order, and the caller is trusted
     * for none of them — mirroring [transitionJobStatus]:
     *
     * 1. The actor is resolved from `FirebaseAuth.currentUser.uid` via [resolveAuthoritativeUid]; it
     *    is NOT the caller-supplied `fromUserId`. No uid ⇒ [RatingResult.NotAuthenticated], no write.
     *    This is what prevents impersonation: the rating is always attributed to the authenticated
     *    user, never to a `fromUserId` the caller chose.
     * 2. The rating value must be 1..5.
     * 3. The REAL job row is loaded by id (the caller-supplied `jobId`/`toUserId` are never trusted
     *    to establish ownership). A missing row ⇒ [RatingResult.JobNotFound].
     * 4. [isRatingAuthorized] decides whether that actor may rate that job as that target —
     *    owner-only, released-only, and the target must be the job's assigned provider. A failure
     *    returns [RatingResult.NotAuthorized] and the database is untouched.
     * 5. Duplicate prevention: an existing `(fromUserId, toUserId, jobId)` row ⇒
     *    [RatingResult.AlreadyRated].
     *
     * Only after all five checks is the rating inserted with `fromUserId = actorUid`.
     */
    suspend fun submitJobRating(
        jobId: String,
        toUserId: String,
        rating: Int,
        review: String,
        isFromClient: Boolean
    ): RatingResult {
        // 1. Authoritative identity (F6/F7 rule). Never the newest Room row, never a display name.
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return RatingResult.NotAuthenticated

        // 2. Rating value.
        if (rating < 1 || rating > 5) return RatingResult.InvalidRating

        // 3. Load the REAL job; a caller cannot assert an ownership relationship of its choosing.
        val job = db.jobPostDao().getJobById(jobId) ?: return RatingResult.JobNotFound

        // 4. Authorization BEFORE any duplicate/persistence work, so an unauthorized caller can
        //    neither rate an unrelated job nor learn anything about existing ratings.
        if (!isRatingAuthorized(actorUid, job, toUserId)) return RatingResult.NotAuthorized

        // 5. Duplicate prevention.
        if (db.ratingDao().getRatingFromToJob(actorUid, toUserId, jobId) != null) {
            return RatingResult.AlreadyRated
        }

        db.ratingDao().insertRating(
            RatingEntity(
                id = "rating_${System.currentTimeMillis()}",
                jobId = jobId,
                // Authoritative actor, resolved above — never caller-supplied.
                fromUserId = actorUid,
                toUserId = toUserId,
                rating = rating,
                review = review,
                isFromClient = isFromClient,
                createdAt = System.currentTimeMillis()
            )
        )
        return RatingResult.Success
    }

    // ==================== NOTIF-001: Notification Methods ====================

    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>> = db.notificationDao().getNotificationsForUser(userId)

    fun getUnreadNotificationsForUser(userId: String): Flow<List<NotificationEntity>> = db.notificationDao().getUnreadNotificationsForUser(userId)

    suspend fun getUnreadNotificationCount(userId: String): Int = db.notificationDao().getUnreadCountForUser(userId)

    suspend fun createNotification(notification: NotificationEntity) {
        db.notificationDao().insertNotification(notification)
    }

    suspend fun markNotificationRead(notificationId: String) {
        db.notificationDao().markAsRead(notificationId)
    }

    suspend fun markAllNotificationsRead(userId: String) {
        db.notificationDao().markAllAsRead(userId)
    }

    // ==================== JOB-004: Job State Machine Methods ====================

    fun isValidJobStatusTransition(fromStatus: String, toStatus: String): Boolean {
        return JobStatus.isValidTransition(fromStatus, toStatus)
    }

    /**
     * C2-2 job-status **AUTHORIZATION** (the layer F6 did not have).
     *
     * F6 answers "who did this?"; this answers "was that actor allowed to do it?". The two are
     * deliberately separate and this function is the single place the second question is decided.
     *
     * It resolves the actor itself from `FirebaseAuth.currentUser.uid` via [resolveAuthoritativeUid]
     * -- it never accepts a caller-supplied actor, never reads the newest Room row, never compares a
     * display name, a phone number, a district or `"system"`/`"unknown"`, and never uses the UI mode.
     * With no valid uid the answer is `false` (fail closed).
     *
     * @param job the REAL row loaded from the database, never a caller-constructed object.
     * @param toStatus the requested target status.
     * @return true only when the authenticated actor is permitted to perform this transition.
     */
    suspend fun canCurrentUserTransitionJob(job: JobPostEntity?, toStatus: String): Boolean {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return false // fail closed: no authenticated Firebase identity
        if (job == null) return false // fail closed: the job does not exist (or was never loaded)
        // Re-resolve the job's stored status through the state machine's own vocabulary.
        val target = JobStatus.fromValue(toStatus) ?: return false // unknown target status
        return isJobTransitionAuthorized(actorUid, job, target)
    }

    /**
     * Pure, actor-explicit form of the check above, so the policy is unit-testable without Firebase
     * and so the reasoning for each status is written down in one readable place.
     *
     * ## Why this is owner-only today (and worker transitions therefore fail closed)
     *
     * `JobPostEntity.assignedProviderId` is a `ServiceProviderEntity` primary key (seeded values
     * like `"prov_1"`), **not** a Firebase UID, and `ServiceProviderEntity` carries no UID column at
     * all. There is therefore NO way to compare the authenticated uid against the assigned worker
     * without inventing a new identity column -- which would be a schema/product decision, not a
     * security fix. Admin is likewise unavailable here: the project recognizes admins solely through
     * the Firestore custom claim `request.auth.token.admin`, which is evaluated server-side by
     * `firestore.rules` and is deliberately NOT mirrored into a client-side boolean.
     *
     * So rather than guess, every worker-side and admin-side transition FAILS CLOSED here. The
     * server-side boundary (`firestore.rules`) remains the place where the worker/escrow
     * transitions are actually authorized. See `firestore.rules` `/jobs/{jobId}` update branch and
     * DECISIONS.md D-2026-10-05-2 for the follow-up product decision this requires.
     *
     * Client (job owner) transitions are the ones the client can decide safely, because
     * `postedByUid` is the authoritative owner identity established by C2-2 Fix #4 (F4):
     *  - DRAFT/POSTED/QUOTING/WORKER_SELECTED/ACCEPTED -> publishing, selecting a worker, accepting
     *  - CANCELLED (from a cancellable state) -> cancelling own job
     *  - DISPUTED (from an active state) -> raising a dispute on own job
     *  - PAYMENT_RELEASED -> the client approving release of escrow, matching `releaseEscrowToWorker`
     *
     * Worker-progress statuses (WORKER_EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED) and admin
     * resolutions (from DISPUTED) are NOT granted to the owner and NOT granted to anyone else here.
     */
    fun isJobTransitionAuthorized(
        actorUid: String?,
        job: JobPostEntity,
        target: JobStatus
    ): Boolean {
        // F4: postedByUid is the ONLY ownership identity. A NULL postedByUid means a legacy job of
        // unknown provenance, which belongs to nobody and may be driven by nobody.
        val isOwner = actorUid != null && job.postedByUid != null && job.postedByUid == actorUid
        if (!isOwner) return false

        val from = JobStatus.fromValue(job.status) ?: return false // unknown/legacy status: fail closed
        return when (target) {
            // Client-side lifecycle while the job is still being set up.
            JobStatus.POSTED, JobStatus.QUOTING, JobStatus.WORKER_SELECTED, JobStatus.ACCEPTED ->
                from in CLIENT_SETUP_STATES
            // The owner may cancel their own job, but never resurrect it.
            JobStatus.CANCELLED -> from in CANCELLABLE_STATES
            // Either party may raise a dispute on a live job.
            JobStatus.DISPUTED -> from in DISPUTABLE_STATES
            // The owner approves release of escrow for finished work.
            JobStatus.PAYMENT_RELEASED -> from == JobStatus.COMPLETED
            // Worker progress + admin dispute resolution: fail closed for everyone on the client.
            JobStatus.DRAFT, JobStatus.WORKER_EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS,
            JobStatus.COMPLETED, JobStatus.EXPIRED, JobStatus.PAYMENT_RELEASED /*handled*/,
            JobStatus.DISPUTED /*handled*/, JobStatus.CANCELLED /*handled*/,
            JobStatus.POSTED /*handled*/, JobStatus.QUOTING /*handled*/,
            JobStatus.WORKER_SELECTED /*handled*/, JobStatus.ACCEPTED /*handled*/ -> false
        }
    }

    /**
     * The single SECURED boundary for moving a job's status — identity (F6), authorization and
     * structural legality are all decided here, in that order, and the caller is trusted for none
     * of them:
     *
     * 1. The actor is resolved from `FirebaseAuth.currentUser.uid` via [resolveAuthoritativeUid]
     *    (F6 attribution). It is NOT a caller-supplied parameter, the legacy newest-Room-row
     *    `currentUser` flow is not consulted, and with no authoritative uid the method returns
     *    `false` before any write — nothing is ever recorded against `"system"`, `"unknown"` or
     *    any other fabricated actor.
     * 2. The REAL job row is loaded by id; the caller-supplied `fromStatus` is never trusted for
     *    the decision.
     * 3. [isJobTransitionAuthorized] decides whether that actor may perform that transition on
     *    that job — checked BEFORE the structural check so an unauthorized caller learns nothing
     *    about which transitions are legal.
     * 4. [JobStatus.isValidTransition] checks structural legality against the STORED status, so
     *    the audit `fromStatus` cannot disagree with the row that was actually changed.
     *
     * Only after all four checks pass are the job status and the audit row (with `changedBy =
     * actorUid`) written. Any failure returns `false` with the database untouched.
     */
    suspend fun transitionJobStatus(jobId: String, fromStatus: String, toStatus: String, reason: String = ""): Boolean {
        // F6: the ONLY acceptable actor identity. Never the newest Room row, never a display
        // name/phone/district, never "unknown"/"system"/a synthetic or anonymous id.
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return false

        // C2-2 authorization: load the REAL job. `fromStatus` is a caller-supplied parameter and is
        // therefore NOT trusted for the authorization decision -- only the stored row is. A caller
        // that lies about the current status cannot thereby gain authorization.
        val job = db.jobPostDao().getJobById(jobId) ?: return false

        // Authorization BEFORE the structural check, so an unauthorized caller learns nothing about
        // which transitions are legal and can never move the job at all.
        val target = JobStatus.fromValue(toStatus)
        if (target == null || !isJobTransitionAuthorized(actorUid, job, target)) {
            return false
        }

        // `JobStatus.isValidTransition` remains solely responsible for structural legality (STEP 9).
        // It is checked against the STORED status, not the caller-supplied `fromStatus`, so the
        // recorded audit `fromStatus` cannot disagree with the row that was actually changed.
        val storedFrom = job.status
        if (!JobStatus.isValidTransition(storedFrom, toStatus)) {
            return false
        }

        db.jobPostDao().updateJobStatus(jobId, toStatus)
        db.jobStatusAuditDao().insertAuditEntry(
            JobStatusAuditEntity(
                id = "audit_${System.currentTimeMillis()}",
                jobId = jobId,
                fromStatus = storedFrom,
                toStatus = toStatus,
                // F6: authoritative actor (authenticated uid), still resolved here, never supplied.
                changedBy = actorUid,
                reason = reason
            )
        )
        return true
    }

    /** Cancel a job owned by the authenticated client. Dispatches through the
     *  secured [transitionJobStatus] boundary (F6 identity, F4 ownership,
     *  authorization, structural legality, audit row). Returns true only when
     *  CANCELLED was written. Fail-closed: no authoritative uid, no owner, or a
     *  non-cancellable status aborts with nothing persisted. */
    suspend fun cancelJob(jobId: String, reason: String = ""): Boolean {
        val currentJob = db.jobPostDao().getJobById(jobId) ?: return false
        return transitionJobStatus(jobId, currentJob.status, JobStatus.CANCELLED.value, reason)
    }

    suspend fun getJobAuditTrail(jobId: String): List<JobStatusAuditEntity> {
        return db.jobStatusAuditDao().getAuditTrailForJob(jobId)
    }

    // ==================== AUTH-003: Logout & Session Clearing ====================

    suspend fun clearAllUserData() {
        db.userProfileDao().clearAll()
        db.workerKycDao().clearAll()
        db.guarantorDao().clearAll()
        db.workerTrainingDao().clearAll()
        db.jobPostDao().clearAll()
        db.jobQuoteDao().clearAll()
        db.chatMessageDao().clearAll()
        db.disputeDao().clearAll()
        db.ratingDao().clearAll()
        db.notificationDao().clearAll()
        db.skillEndorsementDao().clearAll()
        db.pendingActionDao().clearAll()
        db.jobStatusAuditDao().clearAll()
        db.reportDao().clearAll()
    }

    // ==================== DISP-004: User/Worker Reporting ====================

    fun getAllReports(): Flow<List<ReportEntity>> = db.reportDao().getAllReports()

    fun getReportsForUser(userId: String): Flow<List<ReportEntity>> = db.reportDao().getReportsForUser(userId)

    suspend fun reportUser(report: ReportEntity) {
        db.reportDao().insertReport(report)
    }

    suspend fun getPendingReports(): List<ReportEntity> {
        return db.reportDao().getReportsByStatus("PENDING")
    }

    /**
     * AUDIT-W-CASHOUT: Outcome of a secured worker cashout (withdrawal) request.
     *
     * Produced by the repository boundary after the pure security core has run. The caller is
     * trusted for NONE of identity, balance, amount, destination, idempotency or state.
     */
    sealed class CashoutRequestResult {
        /**
         * A withdrawal was recorded (created OR reused via the idempotency key). It is PENDING:
         * the funds are RESERVED but NOT yet disbursed. No money has moved yet — real mobile-money
         * disbursement is a separate server-authority integration step.
         */
        data class RequestRecorded(val withdrawalId: String, val status: String) : CashoutRequestResult()

        /** A prior active withdrawal already exists; its id is returned so the UI can show it. */
        data class DuplicateActiveWithdrawal(val existingWithdrawalId: String) : CashoutRequestResult()

        object NotAuthenticated : CashoutRequestResult()
        object InvalidAmount : CashoutRequestResult()
        object AmountExceedsAvailable : CashoutRequestResult()
        object DestinationNotSet : CashoutRequestResult()
        object DestinationInvalid : CashoutRequestResult()
    }

    /** AUDIT-W-CASHOUT: Outcome of a worker-initiated withdrawal cancellation. */
    sealed class WithdrawalCancelResult {
        object Success : WithdrawalCancelResult()
        object NotAuthenticated : WithdrawalCancelResult()
        object WithdrawalNotFound : WithdrawalCancelResult()
        object NotAuthorized : WithdrawalCancelResult()
        /** The withdrawal is not PENDING (already terminal or already moving) — cannot cancel. */
        object InvalidState : WithdrawalCancelResult()
    }

    // ==================== R04-P2-05: ROOM <-> FIRESTORE RECONCILIATION ====================

    /** Action type used by the durable retry queue for un-mirrored SOS incidents. */
    private val ACTION_SYNC_SOS = "SYNC_SOS_INCIDENT"

    /** What one reconciliation pass did. Returned so callers/tests can see it, never inferred. */
    internal data class MirrorReconciliationReport(
        val adopted: Int = 0,
        val attempted: Int = 0,
        val succeeded: Int = 0,
        val retryScheduled: Int = 0,
        val abandoned: Int = 0
    )

    private suspend fun enqueueMirrorRetry(incidentId: String) {
        val actionId = "sync_sos_$incidentId"
        val existing = db.pendingActionDao().getPendingActionsList()
            .any { it.id == actionId || (it.actionType == ACTION_SYNC_SOS && it.payloadJson == incidentId) }
        if (existing) return
        db.pendingActionDao().insertAction(
            PendingActionEntity(
                id = actionId,
                actionType = ACTION_SYNC_SOS,
                payloadJson = incidentId,
                status = "PENDING"
            )
        )
    }

    /**
     * R04-P2-05: reconcile the local mirror state with what actually reached Firestore.
     *
     * Two-step and idempotent:
     *  1. ADOPT — every incident the mirror has not accepted is put on the durable retry queue, so a
     *     process death between the local insert and the sync is repaired on the next pass.
     *  2. DRAIN — each queued sync is attempted once; success marks the incident mirrored and
     *     removes the action, failure increments the retry counter and abandons the action after
     *     `maxRetries` so the queue cannot spin forever.
     *
     * [sync] is injectable only so tests can drive both outcomes without a live Firestore; the
     * production default is the real gateway.
     */
    internal suspend fun reconcilePendingMirrors(
        sync: suspend (SosIncidentEntity) -> Boolean = { firebaseService.syncSosIncidentToFirestore(it) }
    ): MirrorReconciliationReport {
        // 1. ADOPT
        var adopted = 0
        for (incident in db.sosIncidentDao().getUnmirroredIncidents()) {
            val before = db.pendingActionDao().getPendingActionsList()
                .any { it.actionType == ACTION_SYNC_SOS && it.payloadJson == incident.id }
            if (!before) {
                enqueueMirrorRetry(incident.id)
                adopted++
            }
        }

        // 2. DRAIN
        var attempted = 0
        var succeeded = 0
        var retryScheduled = 0
        var abandoned = 0
        val queue = db.pendingActionDao().getPendingActionsList()
            .filter { it.actionType == ACTION_SYNC_SOS }
        for (action in queue) {
            val incident = db.sosIncidentDao().getSosIncidentById(action.payloadJson)
            attempted++
            val ok = incident != null && sync(incident)
            when {
                ok -> {
                    db.sosIncidentDao().setFirestoreMirrored(incident!!.id, true)
                    db.pendingActionDao().deleteAction(action.id)
                    succeeded++
                }
                else -> {
                    val nextRetry = action.retryCount + 1
                    if (nextRetry >= action.maxRetries) {
                        db.pendingActionDao().updateActionStatus(action.id, "FAILED", nextRetry)
                        abandoned++
                    } else {
                        db.pendingActionDao().updateActionStatus(action.id, "PENDING", nextRetry)
                        retryScheduled++
                    }
                }
            }
        }
        return MirrorReconciliationReport(adopted, attempted, succeeded, retryScheduled, abandoned)
    }

    // ==================== AUDIT-W-CASHOUT: WORKER WALLET CASHOUT ====================

    /**
     * AUDIT-W-CASHOUT: the SECURED boundary for requesting a worker wallet cashout.
     *
     * Takes only the REQUESTED amount, destination and an idempotency key. It never accepts a
     * `workerId` — the actor is resolved internally from `FirebaseAuth.currentUser.uid`, so a
     * caller cannot withdraw from anyone else's wallet. All rules live in
     * [requestWorkerCashoutInternal] and the pure WorkerCashoutSecurityCore.
     */
    suspend fun requestWorkerCashout(
        amount: Double,
        destinationMomoNumber: String,
        idempotencyKey: String
    ): CashoutRequestResult {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
        return requestWorkerCashoutInternal(amount, destinationMomoNumber, idempotencyKey, actorUid)
    }

    /**
     * Internal cashout boundary. [actorUid] is injected ONLY so tests can drive the real
     * authorization path with a chosen (or null) identity; production always resolves it from
     * `FirebaseAuth.currentUser.uid` in [requestWorkerCashout].
     */
    internal suspend fun requestWorkerCashoutInternal(
        amount: Double,
        destinationMomoNumber: String,
        idempotencyKey: String,
        actorUid: String?
    ): CashoutRequestResult {
        // 1. Authoritative identity. Fail closed when there is no valid authenticated uid.
        val uid = resolveAuthoritativeUid(actorUid)
            ?: return CashoutRequestResult.NotAuthenticated

        // 2. Idempotency key must be present and sane (it becomes the row id for a new request).
        val key = idempotencyKey.trim()
        if (key.isEmpty() || key.any { it.isWhitespace() }) {
            return CashoutRequestResult.InvalidAmount
        }

        // 3. Load THIS worker's withdrawals (never another account's) and run the idempotency gate.
        val existing = db.walletWithdrawalDao().getWithdrawalsForWorker(uid)
            .map { WithdrawalSnapshot(it.id, it.workerId, it.amountMinor, it.status, it.idempotencyKey) }
        when (val idem = evaluateCashoutIdempotency(existing, key)) {
            is CashoutIdempotencyOutcome.ReuseExisting -> {
                val reused = db.walletWithdrawalDao().getWithdrawalById(idem.withdrawalId)
                return CashoutRequestResult.RequestRecorded(
                    withdrawalId = idem.withdrawalId,
                    status = reused?.status ?: WithdrawalStatus.PENDING.value
                )
            }
            is CashoutIdempotencyOutcome.ActiveWithdrawalExists -> {
                val active = existing.first { WithdrawalStatus.fromValue(it.status) in ACTIVE_WITHDRAWAL_STATUSES }
                return CashoutRequestResult.DuplicateActiveWithdrawal(active.id)
            }
            CashoutIdempotencyOutcome.Proceed -> Unit
        }

        // 4. Authoritative available balance from the LEDGER — never a caller-supplied number and
        //    never the display-only `UserProfileEntity.walletBalance` field.
        //    R04-P2-04: every amount is an exact integer in minor units; the requested Double is
        //    converted once, here, and a non-representable amount is rejected instead of guessed.
        val requestedMinor = Money.toMinorUnits(amount)
            ?: return CashoutRequestResult.InvalidAmount
        val entries = db.walletLedgerDao().getEntriesForWorker(uid)
            .map { WalletLedgerEntrySnapshot(it.entryType, it.amountMinor, it.withdrawalId) }
        val availableMinor = computeAvailableBalanceMinor(entries)

        // 5. Validate the REQUEST (amount + destination) against the authoritative balance.
        val validation = validateCashoutRequest(requestedMinor, destinationMomoNumber, availableMinor)
        val valid = validation as? CashoutRequestValidation.Valid
            ?: return when ((validation as CashoutRequestValidation.Invalid).reason) {
                CashoutRejectReason.NOT_AUTHENTICATED -> CashoutRequestResult.NotAuthenticated
                CashoutRejectReason.INVALID_AMOUNT -> CashoutRequestResult.InvalidAmount
                CashoutRejectReason.AMOUNT_EXCEEDS_AVAILABLE -> CashoutRequestResult.AmountExceedsAvailable
                CashoutRejectReason.DESTINATION_NOT_SET -> CashoutRequestResult.DestinationNotSet
                CashoutRejectReason.DESTINATION_INVALID -> CashoutRequestResult.DestinationInvalid
            }

        // 6. Create the PENDING withdrawal and its RESERVE ledger entry ATOMICALLY, so the
        //    reservation is enforced by the same transaction that records the request. A worker
        //    can never create a withdrawal without reserving the funds against the ledger.
        val withdrawalId = if (key.startsWith("wd_")) key else "wd_$key"
        val now = System.currentTimeMillis()
        db.runInTransaction {
            // Re-check the active-withdrawal gate INSIDE the transaction to close the race where
            // two concurrent requests both passed the pre-check above.
            val concurrent = db.walletWithdrawalDao().getWithdrawalsForWorkerSync(uid)
            if (concurrent.any { WithdrawalStatus.fromValue(it.status) in ACTIVE_WITHDRAWAL_STATUSES }) {
                throw java.util.concurrent.CancellationException("active_withdrawal_exists")
            }
            db.walletWithdrawalDao().insertWithdrawalSync(
                WalletWithdrawalEntity(
                    id = withdrawalId,
                    workerId = uid,
                    amountMinor = valid.normalizedAmountMinor,
                    destinationMomoNumber = valid.normalizedDestination,
                    status = WithdrawalStatus.PENDING.value,
                    idempotencyKey = key,
                    createdAt = now,
                    updatedAt = now
                )
            )
            db.walletLedgerDao().insertEntrySync(
                WalletLedgerEntity(
                    id = "led_${withdrawalId}_reserve",
                    workerId = uid,
                    entryType = LedgerEntryType.RESERVE.value,
                    amountMinor = valid.normalizedAmountMinor,
                    withdrawalId = withdrawalId,
                    description = "Reserved for withdrawal request",
                    createdAt = now
                )
            )
        }

        return CashoutRequestResult.RequestRecorded(
            withdrawalId = withdrawalId,
            status = WithdrawalStatus.PENDING.value
        )
    }

    /**
     * AUDIT-W-CASHOUT: the SECURED boundary for CANCELLING a worker's own pending withdrawal.
     *
     * Only the withdrawal's owner may cancel, only while it is still PENDING, and the RESERVE is
     * released back to the ledger in the same transaction (funds are never destroyed). A worker
     * can never reach the terminal money-moving states through this path.
     */
    suspend fun cancelWorkerWithdrawal(withdrawalId: String): WithdrawalCancelResult {
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
        return cancelWorkerWithdrawalInternal(withdrawalId, actorUid)
    }

    internal suspend fun cancelWorkerWithdrawalInternal(
        withdrawalId: String,
        actorUid: String?
    ): WithdrawalCancelResult {
        // 1. Authoritative identity — fail closed.
        val uid = resolveAuthoritativeUid(actorUid) ?: return WithdrawalCancelResult.NotAuthenticated

        // 2. Load the withdrawal; a caller can only ever name an id, never supply the row.
        val withdrawal = db.walletWithdrawalDao().getWithdrawalById(withdrawalId)
            ?: return WithdrawalCancelResult.WithdrawalNotFound

        // 3. Ownership: another account's withdrawal is unreachable through this boundary.
        if (withdrawal.workerId != uid) {
            return WithdrawalCancelResult.NotAuthorized
        }

        // 4. State machine: only a PENDING withdrawal can be cancelled by its owner.
        val snapshot = WithdrawalSnapshot(withdrawal.id, withdrawal.workerId, withdrawal.amountMinor, withdrawal.status)
        val outcome = evaluateWithdrawalTransition(
            withdrawal = snapshot,
            from = WithdrawalStatus.PENDING,
            to = WithdrawalStatus.CANCELLED,
            actorWorkerId = uid,
            actorIsServerAuthority = false
        )
        val apply = outcome as? WithdrawalTransitionOutcome.Apply ?: return WithdrawalCancelResult.InvalidState

        // 5. Apply the transition + release the reservation ATOMICALLY.
        val now = System.currentTimeMillis()
        db.runInTransaction {
            val updated = db.walletWithdrawalDao().transitionStatusSync(
                id = withdrawalId,
                fromStatus = WithdrawalStatus.PENDING.value,
                toStatus = apply.nextStatus.value,
                timestamp = now
            )
            // Optimistic guard: if another actor moved the row first, abort (no partial write).
            if (updated == 0) throw java.util.concurrent.CancellationException("withdrawal_already_moved")
            if (apply.reversalRequired) {
                db.walletLedgerDao().insertEntrySync(
                    WalletLedgerEntity(
                        id = "led_${withdrawalId}_release",
                        workerId = uid,
                        entryType = LedgerEntryType.RELEASE.value,
                        amountMinor = withdrawal.amountMinor,
                        withdrawalId = withdrawalId,
                        description = "Reservation released (withdrawal cancelled)",
                        createdAt = now
                    )
                )
            }
        }

        return WithdrawalCancelResult.Success
    }

    // ==================== R08-P2-02: SERVER-AUTHORITATIVE WALLET ====================

    /**
     * The server's wallet: balance, limits and the recent statement.
     *
     * This is the authoritative balance. The Room ledger below is now only this device's record of
     * what it last saw — a reinstall no longer erases the payable balance because the server holds
     * it, and no client can raise its own balance.
     */
    suspend fun getServerWallet(): Result<ServerWalletSnapshot> = serverWallet.fetchWallet()

    /**
     * The server-authoritative available balance in MINOR units (1 UGX = 100), or null when the
     * server cannot be reached. Money decisions must prefer this over [getAvailableWalletBalanceMinor].
     */
    suspend fun getServerAvailableBalanceMinor(): Long? =
        serverWallet.fetchWallet().getOrNull()?.availableUgx?.let { it * 100 }

    /**
     * Request a cashout through the server.
     *
     * The amount, the velocity limits, the verified destination and the provider payout are all
     * decided server-side; [requestId] is the idempotency key so a retried call cannot pay twice.
     * The local withdrawal row is mirrored on success so the UI can show the pending request offline.
     */
    suspend fun requestServerWalletCashout(
        amountUgx: Long,
        requestId: String
    ): Result<ServerCashoutResult> {
        val outcome = serverWallet.requestCashout(amountUgx, requestId)
        outcome.getOrNull()?.let { accepted ->
            try {
                val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
                if (uid != null) {
                    val now = System.currentTimeMillis()
                    db.walletWithdrawalDao().insertWithdrawal(
                        WalletWithdrawalEntity(
                            id = accepted.withdrawalId,
                            workerId = uid,
                            amountMinor = accepted.amountUgx * 100,
                            destinationMomoNumber = "",
                            status = WithdrawalStatus.PENDING.value,
                            idempotencyKey = requestId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }
            } catch (_: Exception) {
                // The mirror is only a local convenience; the server record is authoritative.
            }
        }
        return outcome
    }

    /** Cancel a cashout the server has not yet handed to the payment provider. */
    suspend fun cancelServerWalletCashout(requestId: String): Result<Unit> =
        serverWallet.cancelCashout(requestId)

    /** R08-P2-07: provider-verified refund status for a job's refund. */
    suspend fun getServerRefundStatus(refundRef: String): Result<ServerRefundStatus> =
        serverWallet.fetchRefundStatus(refundRef)

    /**
     * AUDIT-W-CASHOUT: the authoritative AVAILABLE balance for the authenticated worker, derived
     * from the wallet ledger. This is the only balance a cashout request is validated against.
     * Returns 0.0 when there is no authenticated worker (fail closed).
     */
    suspend fun getAvailableWalletBalance(): Double {
        val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid) ?: return 0.0
        val balanceMinor = computeAvailableBalanceMinor(
            db.walletLedgerDao().getEntriesForWorker(uid)
                .map { WalletLedgerEntrySnapshot(it.entryType, it.amountMinor, it.withdrawalId) }
        )
        // R04-P2-04: the display value is derived from the exact integer balance; it is never used
        // for arithmetic, and `getAvailableWalletBalanceMinor` is the authoritative accessor.
        return Money.toMajorUnits(balanceMinor)
    }

    /** R04-P2-04: the authoritative available balance, exact, in minor units (1 UGX = 100). */
    suspend fun getAvailableWalletBalanceMinor(): Long {
        val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid) ?: return 0L
        return computeAvailableBalanceMinor(
            db.walletLedgerDao().getEntriesForWorker(uid)
                .map { WalletLedgerEntrySnapshot(it.entryType, it.amountMinor, it.withdrawalId) }
        )
    }

    /** AUDIT-W-CASHOUT: the authenticated worker's own withdrawals (never another account's). */
    fun getWorkerWithdrawals(): Flow<List<WalletWithdrawalEntity>> {
        val uid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.walletWithdrawalDao().observeWithdrawalsForWorker(uid)
    }

    suspend fun updateReportStatus(reportId: String, status: String, adminNotes: String = "", resolvedBy: String = "") {
        db.reportDao().updateReportStatus(reportId, status, adminNotes, System.currentTimeMillis(), resolvedBy)
    }
}

// ==================== REG-002: GUARANTOR VERIFICATION FAILURES ====================

/**
 * REG-002: a guarantor submission could not be saved or filed. The message is always
 * user-presentable and never contains a guarantor name, phone number or National ID
 * number — those stay in the Room row and the private Storage objects.
 */
class GuarantorSubmissionException(message: String) : Exception(message)

private const val GUARANTOR_SIGN_IN_MESSAGE =
    "Please sign in before submitting your guarantors. Their identity details may only be filed against a real account."
private const val GUARANTOR_ACCOUNT_MESSAGE =
    "We could not find your account on this device. Refresh the app and try again."
private const val GUARANTOR_WORKER_MODE_MESSAGE =
    "Guarantor verification is only available on worker accounts."
private const val GUARANTOR_UPLOAD_MESSAGE =
    "A verification photo could not be uploaded. Check your connection and try again — your answers are still saved."
private const val GUARANTOR_SUBMIT_MESSAGE =
    "We could not submit your guarantors for review. Your answers are saved, so you can try again."
private const val GUARANTOR_STATUS_MESSAGE =
    "Could not reach the verification service. Your saved guarantors are still here."

