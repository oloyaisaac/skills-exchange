package com.example.data.repository

import android.net.Uri
import com.example.data.db.*
import com.example.data.remote.FirebaseService
import com.example.data.remote.SmsNotificationService
import com.example.util.GuarantorInput
import com.example.util.GuarantorValidators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SkillsExchangeRepository(
    private val db: AppDatabase,
    private val firebaseService: FirebaseService = FirebaseService(),
    private val smsNotificationService: SmsNotificationService = SmsNotificationService()
) {
    val providers: Flow<List<ServiceProviderEntity>> = db.serviceProviderDao().getAllProviders()
    val jobs: Flow<List<JobPostEntity>> = db.jobPostDao().getAllJobs()
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
    fun getCurrentUserByUid(firebaseUid: String?): Flow<UserProfileEntity?> {
        val uid = firebaseUid?.takeIf { it.isNotBlank() && it != "user_default_me" }
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

    /**
     * C2-1: device-local phone lookup. NON-AUTHORITATIVE — a phone match does not establish who
     * owns an account, so this must never be used to select the record that an authenticated
     * identity inherits. Identity is always `FirebaseAuth.currentUser.uid`.
     */
    fun getUserProfileByPhone(phoneNumber: String): Flow<UserProfileEntity?> {
        return db.userProfileDao().getUserProfileByPhoneNumber(phoneNumber)
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
        materialsProvidedBy: String = "RESIDENT",
        laborHours: Double = 2.0,
        laborRate: Double = 60.0,
        jobType: String = "ONE_TIME",
        recurringSchedule: String = "WEEKLY"
    ): String? {
        // F4: the ONLY acceptable owner identity. Never the newest Room row, never a display
        // name/district, never "unknown"/"system"/a synthetic or anonymous id.
        val ownerUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return null
        val newJobId = "job_${System.currentTimeMillis()}"
        val laborTotal = laborHours * laborRate
        val isLaborOnly = materialsProvidedBy == "RESIDENT" || materialsProvidedBy == "NONE_NEEDED"
        val materialsSubtotal = if (isLaborOnly) 0.0 else (budget - laborTotal).coerceAtLeast(0.0)
        val platformFee = laborTotal * 0.10
        val grandTotal = laborTotal + materialsSubtotal + platformFee

        val randomDoorstepPin = (1000..9999).random().toString()

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
        
        // Add initial system message
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = newJobId,
                senderName = "System",
                isFromUser = false,
                message = "Job request (${if (isLaborOnly) "Labor Only" else "Labor + Materials"}) broadcasted to verified local $category specialists in $district.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
        return newJobId
    }

    suspend fun updateJobStatus(jobId: String, newStatus: String) {
        db.jobPostDao().updateJobStatus(jobId, newStatus)
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "System",
                isFromUser = false,
                message = "Job status updated to '$newStatus'.",
                timestampText = "Just now",
                isSystemNotification = true
            )
        )
    }

    suspend fun updateEscrowStatus(jobId: String, newEscrowStatus: String) {
        db.jobPostDao().updateEscrowStatus(jobId, newEscrowStatus)

        val notificationMsg = when (newEscrowStatus) {
            "DEPOSIT_PROMPTED" -> "USSD push sent to resident phone to lock funds in escrow."
            "FUNDS_LOCKED_IN_ESCROW" -> "🔒 Escrow Locked! Customer approved MoMo transfer into secure escrow."
            "WORK_IN_PROGRESS" -> "🛠️ Work in progress. Service provider is performing labor."
            "PROOF_SUBMITTED" -> "📷 Ground proof photos uploaded by worker. Awaiting resident release."
            "RELEASED_TO_WORKER" -> "✅ Escrow Released! Funds payout dispatched to worker's Mobile Money wallet."
            else -> "Escrow status updated to $newEscrowStatus."
        }

        val job = db.jobPostDao().getAllJobs().first().find { it.id == jobId }
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
            db.jobPostDao().updateJobStatus(jobId, mappedJobStatus)
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
            amount = amount,
            reason = reason,
            status = "PENDING"
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "Worker Alert",
                isFromUser = false,
                message = "⚠️ Emergency Material Add-on Request: $reason ($${"%.2f".format(amount)}). Customer approval required.",
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

    suspend fun verifyDoorstepPin(jobId: String, enteredPin: String, expectedPin: String): Boolean {
        if (enteredPin.trim() == expectedPin.trim()) {
            db.jobPostDao().verifyDoorstepPin(jobId)
            db.chatMessageDao().insertMessage(
                ChatMessageEntity(
                    id = "msg_${System.currentTimeMillis()}",
                    jobId = jobId,
                    senderName = "Doorstep Security",
                    isFromUser = false,
                    message = "🔑 4-Digit Doorstep PIN Verified! Resident & Worker confirmed physically present. Job status officially set to IN_PROGRESS.",
                    timestampText = "Just now",
                    isSystemNotification = true
                )
            )
            return true
        } else {
            return false
        }
    }

    suspend fun requestMaterialAdvance(jobId: String, amount: Double) {
        db.jobPostDao().updateMaterialAdvance(
            jobId = jobId,
            requested = true,
            amount = amount,
            status = "REQUESTED",
            receiptUrl = ""
        )
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
                senderName = "Worker Alert",
                isFromUser = false,
                message = "🛒 Early Parts Advance Requested: Worker requested 50% parts advance ($${"%.2f".format(amount)}) from escrow to buy hardware. Mandatory receipt photo upload required within 60 mins.",
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

    suspend fun triggerSosAlert(jobId: String) {
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

        // C2 / C2-2 Fix #3 (F7): the SOS notification is attributed to the AUTHENTICATED Firebase
        // UID's own row. The newest Room row is not an identity, so a device that ever held another
        // account can no longer file this alert under somebody else's profile — and when Firebase
        // has no authenticated profile the caller aborts instead of substituting a placeholder.
        // NOTE: the demo-placeholder check is inline (not via the ViewModel companion) to keep
        // the data layer decoupled from the UI layer.
        val currentUserId = authenticatedProfileOnce()?.id ?: return
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
    }

    suspend fun updatePaymentChannel(jobId: String, channel: String, phone: String) {
        db.jobPostDao().updatePaymentChannel(jobId, channel, phone)
    }

    suspend fun hireProviderForJob(jobId: String, providerId: String, providerName: String) {
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

    suspend fun getAdminVerificationQueue(): List<Map<String, Any>> {
        return firebaseService.getPendingVerificationQueue()
    }

    suspend fun submitAdminVerificationDecision(userId: String, decision: String, feedback: String): Boolean {
        return firebaseService.reviewWorkerApplication(userId, decision, feedback)
    }

    suspend fun setWorkerActivation(userId: String, activate: Boolean): Boolean {
        return firebaseService.setWorkerActivationStatus(userId, activate)
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

    suspend fun submitQuote(jobId: String, workerId: String, workerName: String, proposedRate: Double, proposedHours: Double, notes: String): String {
        val quoteId = "quote_${System.currentTimeMillis()}"
        val totalQuote = proposedRate * proposedHours
        val quote = JobQuoteEntity(
            id = quoteId,
            jobId = jobId,
            workerId = workerId,
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
                userId = workerId,
                type = "NEW_QUOTE",
                title = "Quote Submitted",
                body = "Your quote for job $jobId has been submitted successfully.",
                jobId = jobId,
                createdAt = System.currentTimeMillis()
            )
        )

        return quoteId
    }

    suspend fun respondToQuote(quoteId: String, jobId: String, accept: Boolean) {
        val newStatus = if (accept) "ACCEPTED" else "REJECTED"
        db.jobQuoteDao().updateQuoteStatus(quoteId, newStatus)

        val quote = db.jobQuoteDao().getQuoteById(quoteId)
        // C2: quote-response notifications go to the real worker id from the stored
        // quote row. Abort the notification (not the status update) when the row is
        // missing rather than notifying a "worker_default" placeholder.
        val workerId = quote?.workerId?.takeIf { it.isNotBlank() } ?: return

        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                id = "msg_${System.currentTimeMillis()}",
                jobId = jobId,
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

    suspend fun countPendingQuotesForJob(jobId: String): Int {
        return db.jobQuoteDao().countPendingQuotesForJob(jobId)
    }

    fun getPendingActions(): Flow<List<PendingActionEntity>> {
        return db.pendingActionDao().getPendingActions()
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

        val initialProviders = listOf(
            ServiceProviderEntity(
                id = "prov_1",
                name = "Elena Rostova",
                tagline = "Master Plumber & Pipe Specialist",
                category = "Plumbing",
                neighborhood = "Maplewood East",
                residenceYears = 4,
                hourlyRate = 75.0,
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
                hourlyRate = 85.0,
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
                hourlyRate = 65.0,
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
                hourlyRate = 50.0,
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
                hourlyRate = 45.0,
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
                hourlyRate = 90.0,
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
                hourlyRate = 40.0,
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
                hourlyRate = 70.0,
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
                budget = 123.20,
                urgency = "Today",
                status = "In Progress",
                assignedProviderId = "prov_1",
                assignedProviderName = "Elena Rostova",
                createdTimestamp = System.currentTimeMillis() - 3600000,
                estimatedHours = 1.5,
                materialsCost = 0.0,
                safetyFee = 11.25,
                materialsProvidedBy = "RESIDENT",
                laborHours = 1.5,
                laborRate = 75.0,
                laborTotal = 112.50,
                materialsSubtotal = 0.0,
                platformFee = 11.25,
                grandTotal = 123.75,
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
                budget = 360.50,
                urgency = "This Week",
                status = "Accepted",
                assignedProviderId = "prov_2",
                assignedProviderName = "Marcus Vance",
                createdTimestamp = System.currentTimeMillis() - 7200000,
                estimatedHours = 3.0,
                materialsCost = 75.0,
                safetyFee = 25.50,
                materialsProvidedBy = "PROVIDER",
                laborHours = 3.0,
                laborRate = 85.0,
                laborTotal = 255.00,
                materialsSubtotal = 75.00,
                platformFee = 25.50,
                grandTotal = 355.50,
                paymentChannel = "AIRTEL_MONEY",
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
                budget = 198.00,
                urgency = "Today",
                status = "Requested",
                assignedProviderId = null,
                assignedProviderName = null,
                createdTimestamp = System.currentTimeMillis() - 10800000,
                estimatedHours = 2.0,
                materialsCost = 0.0,
                safetyFee = 18.00,
                materialsProvidedBy = "RESIDENT",
                laborHours = 2.0,
                laborRate = 90.0,
                laborTotal = 180.00,
                materialsSubtotal = 0.0,
                platformFee = 18.00,
                grandTotal = 198.00,
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
                budget = 178.75,
                urgency = "Today",
                status = "Review",
                assignedProviderId = "prov_3",
                assignedProviderName = "David Chen",
                createdTimestamp = System.currentTimeMillis() - 14400000,
                estimatedHours = 2.5,
                materialsCost = 0.0,
                safetyFee = 16.25,
                materialsProvidedBy = "RESIDENT",
                laborHours = 2.5,
                laborRate = 65.0,
                laborTotal = 162.50,
                materialsSubtotal = 0.0,
                platformFee = 16.25,
                grandTotal = 178.75,
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
                budget = 110.00,
                urgency = "Scheduled",
                status = "Completed",
                assignedProviderId = "prov_4",
                assignedProviderName = "Sarah Jenkins",
                createdTimestamp = System.currentTimeMillis() - 86400000,
                estimatedHours = 2.0,
                materialsCost = 0.0,
                safetyFee = 10.00,
                materialsProvidedBy = "NONE_NEEDED",
                laborHours = 2.0,
                laborRate = 50.0,
                laborTotal = 100.00,
                materialsSubtotal = 0.0,
                platformFee = 10.00,
                grandTotal = 110.00,
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
                budget = 198.00,
                urgency = "This Week",
                status = "Requested",
                assignedProviderId = null,
                assignedProviderName = null,
                createdTimestamp = System.currentTimeMillis() - 28800000,
                estimatedHours = 4.0,
                materialsCost = 0.0,
                safetyFee = 18.00,
                materialsProvidedBy = "RESIDENT",
                laborHours = 4.0,
                laborRate = 45.0,
                laborTotal = 180.00,
                materialsSubtotal = 0.0,
                platformFee = 18.00,
                grandTotal = 198.00,
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

    suspend fun triggerEmergencySos(jobId: String, latitude: Double, longitude: Double, district: String) {
        db.jobPostDao().setSosAlert(jobId, true)

        // C2-2 Fix #3 (F7): the details broadcast to emergency contacts must belong to the
        // AUTHENTICATED account only — never the newest Room row, never a fabricated placeholder.
        // With no authenticated profile the SMS is not dispatched at all (the in-app SOS flag set
        // above is still raised): sending a stranger's number, or a made-up "+256 700 000 000", to
        // responders is worse than sending none.
        val profile = resolveSosContactProfile(
            firebaseUid = firebaseService.getCurrentUser()?.uid,
            row = authenticatedProfileOnce()
        ) ?: return

        smsNotificationService.dispatchEmergencySosAlert(
            workerPhone = profile.phoneNumber,
            workerName = profile.fullName.ifBlank { "Authenticated user" },
            latitude = latitude,
            longitude = longitude,
            district = district
        )
    }

    // ==================== DISP-001: Dispute Methods ====================

    fun getAllDisputes(): Flow<List<DisputeEntity>> = db.disputeDao().getAllDisputes()

    fun getDisputesForUser(userId: String): Flow<List<DisputeEntity>> = db.disputeDao().getDisputesForUser(userId)

    suspend fun getDisputeForJob(jobId: String): DisputeEntity? = db.disputeDao().getDisputeForJob(jobId)

    suspend fun createDispute(dispute: DisputeEntity) {
        db.disputeDao().insertDispute(dispute)
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
     * C2-2 Fix #6 (audit finding **F6**): job-status attribution.
     *
     * `changedBy` is resolved HERE, at the data boundary, from `FirebaseAuth.currentUser.uid`
     * (validated by [resolveAuthoritativeUid]) — it is no longer a caller-supplied parameter, so no
     * caller can assert an actor of its choosing, and the legacy newest-Room-row `currentUser`
     * flow is not consulted at all.
     *
     * With no authoritative uid the whole transition FAILS CLOSED: the job status is not updated
     * and no audit entry is written. A status change is never recorded against `"system"`,
     * `"unknown"` or any other fabricated actor.
     *
     * NOTE (scope): the *transition* rules themselves (`JobStatus.isValidTransition`) and the
     * absence of a per-role authorization check on who may move a job to which state are NOT
     * addressed here — that is a separate finding. This change only makes the recorded actor
     * correct, and it does not weaken any existing check.
     */
    suspend fun transitionJobStatus(jobId: String, fromStatus: String, toStatus: String, reason: String = ""): Boolean {
        // F6: the ONLY acceptable actor identity. Never the newest Room row, never a display
        // name/phone/district, never "unknown"/"system"/a synthetic or anonymous id.
        val actorUid = resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)
            ?: return false
        if (!JobStatus.isValidTransition(fromStatus, toStatus)) {
            return false
        }
        db.jobPostDao().updateJobStatus(jobId, toStatus)
        db.jobStatusAuditDao().insertAuditEntry(
            JobStatusAuditEntity(
                id = "audit_${System.currentTimeMillis()}",
                jobId = jobId,
                fromStatus = fromStatus,
                toStatus = toStatus,
                // F6: authoritative actor (authenticated uid).
                changedBy = actorUid,
                reason = reason
            )
        )
        return true
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

