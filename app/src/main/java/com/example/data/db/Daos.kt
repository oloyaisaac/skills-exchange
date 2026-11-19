package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceProviderDao {
    @Query("SELECT * FROM service_providers LIMIT 500")
    fun getAllProviders(): Flow<List<ServiceProviderEntity>>

    @Query("SELECT * FROM service_providers WHERE id = :providerId LIMIT 500")
    suspend fun getProviderById(providerId: String): ServiceProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: ServiceProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<ServiceProviderEntity>)

    @Query("UPDATE service_providers SET endorsementsCount = endorsementsCount + 1 WHERE id = :providerId")
    suspend fun incrementEndorsements(providerId: String)

    @Query("DELETE FROM service_providers")
    suspend fun clearAll()
}

@Dao
interface SkillEndorsementDao {
    @Query("SELECT * FROM skill_endorsements WHERE providerId = :providerId LIMIT 100")
    fun getEndorsementsForProvider(providerId: String): Flow<List<SkillEndorsementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEndorsement(endorsement: SkillEndorsementEntity)

    @Query("DELETE FROM skill_endorsements")
    suspend fun clearAll()
}

@Dao
interface JobPostDao {
    @Query("SELECT * FROM job_posts WHERE postedByUid = :uid ORDER BY createdTimestamp DESC LIMIT 200")
    fun getJobsPostedBy(uid: String): Flow<List<JobPostEntity>>

    /**
     * C2-2 Fix #4 (F4): jobs with NO trustworthy owner (created before the `postedByUid` column
     * existed). They belong to nobody and must never surface as "my jobs" for any account.
     */
    @Query("SELECT * FROM job_posts WHERE postedByUid IS NULL ORDER BY createdTimestamp DESC LIMIT 200")
    fun getJobsWithUnknownOwner(): Flow<List<JobPostEntity>>

    @Query("SELECT * FROM job_posts ORDER BY createdTimestamp DESC LIMIT 200")
    fun getAllJobs(): Flow<List<JobPostEntity>>

    /**
     * C2-2 job-status authorization: the authorization boundary must load the REAL job row to
     * establish the actor's relationship to it (`postedByUid`, `assignedProviderId`). Callers
     * cannot supply the job, so a caller cannot assert an ownership relationship of its choosing.
     */
    @Query("SELECT * FROM job_posts WHERE id = :jobId LIMIT 1")
    suspend fun getJobById(jobId: String): JobPostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<JobPostEntity>)

    @Query("UPDATE job_posts SET status = :status WHERE id = :jobId")
    suspend fun updateJobStatus(jobId: String, status: String)

    @Query("UPDATE job_posts SET assignedProviderId = :providerId, assignedProviderName = :providerName, status = 'Accepted', escrowStatus = 'DEPOSIT_PROMPTED' WHERE id = :jobId")
    suspend fun assignProvider(jobId: String, providerId: String, providerName: String)

    @Query("UPDATE job_posts SET workerLatitude = :latitude, workerLongitude = :longitude, workerLocationUpdatedAt = :timestamp WHERE id = :jobId")
    suspend fun updateWorkerLocation(jobId: String, latitude: Double, longitude: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE job_posts SET escrowStatus = :escrowStatus WHERE id = :jobId")
    suspend fun updateEscrowStatus(jobId: String, escrowStatus: String)

    @Query("UPDATE job_posts SET beforePhotoUrl = :beforeUrl, afterPhotoUrl = :afterUrl WHERE id = :jobId")
    suspend fun updateJobPhotos(jobId: String, beforeUrl: String?, afterUrl: String?)

    @Query("UPDATE job_posts SET emergencyAddonRequested = :requested, emergencyAddonAmount = :amount, emergencyAddonReason = :reason, emergencyAddonStatus = :status WHERE id = :jobId")
    suspend fun updateEmergencyAddon(jobId: String, requested: Boolean, amount: Double, reason: String, status: String)

    @Query("UPDATE job_posts SET paymentChannel = :channel, momoPhoneNumber = :phone WHERE id = :jobId")
    suspend fun updatePaymentChannel(jobId: String, channel: String, phone: String)

    @Query("UPDATE job_posts SET isDoorstepVerified = 1, status = 'In Progress', escrowStatus = 'WORK_IN_PROGRESS' WHERE id = :jobId")
    suspend fun verifyDoorstepPin(jobId: String)

    @Query("UPDATE job_posts SET materialAdvanceRequested = :requested, materialAdvanceAmount = :amount, materialAdvanceStatus = :status, materialAdvanceReceiptUrl = :receiptUrl WHERE id = :jobId")
    suspend fun updateMaterialAdvance(jobId: String, requested: Boolean, amount: Double, status: String, receiptUrl: String)

    @Query("UPDATE job_posts SET isSosTriggered = :sosTriggered WHERE id = :jobId")
    suspend fun setSosAlert(jobId: String, sosTriggered: Boolean)

    @Query("UPDATE job_posts SET recurringContractProposal = :proposalJson, recurringNegotiatedRate = :negotiatedRate, recurringProposalStatus = :status WHERE id = :jobId")
    suspend fun updateRecurringProposal(jobId: String, proposalJson: String, negotiatedRate: Double, status: String)

    @Query("UPDATE job_posts SET recurringProposalStatus = :status WHERE id = :jobId")
    suspend fun updateRecurringProposalStatus(jobId: String, status: String)

    @Query("DELETE FROM job_posts")
    suspend fun clearAll()
}

/**
 * MARKET-002: Job Quote Data Access Object
 */
@Dao
interface JobQuoteDao {
    @Query("SELECT * FROM job_quotes WHERE jobId = :jobId ORDER BY totalQuote ASC LIMIT 200")
    fun getQuotesForJob(jobId: String): Flow<List<JobQuoteEntity>>

    @Query("SELECT * FROM job_quotes WHERE workerId = :workerId ORDER BY submittedAt DESC LIMIT 200")
    fun getQuotesByWorker(workerId: String): Flow<List<JobQuoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuote(quote: JobQuoteEntity)

    @Query("UPDATE job_quotes SET status = :status, respondedAt = :respondedAt WHERE id = :quoteId")
    suspend fun updateQuoteStatus(quoteId: String, status: String, respondedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM job_quotes WHERE jobId = :jobId AND status = 'PENDING'")
    suspend fun countPendingQuotesForJob(jobId: String): Int

    @Query("SELECT COUNT(*) FROM job_quotes WHERE jobId = :jobId AND workerId = :workerId AND status = 'PENDING'")
    suspend fun countPendingQuotesForWorkerAndJob(jobId: String, workerId: String): Int

    @Query("SELECT * FROM job_quotes WHERE id = :quoteId LIMIT 200")
    suspend fun getQuoteById(quoteId: String): JobQuoteEntity?

    @Query("DELETE FROM job_quotes")
    suspend fun clearAll()
}

/**
 * OFF-001: Pending Action Queue Data Access Object
 */
@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 100")
    fun getPendingActions(): Flow<List<PendingActionEntity>>

    @Query("SELECT * FROM pending_actions WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 100")
    suspend fun getPendingActionsList(): List<PendingActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: PendingActionEntity)

    @Query("UPDATE pending_actions SET status = :status, retryCount = :retryCount WHERE id = :id")
    suspend fun updateActionStatus(id: String, status: String, retryCount: Int = 0)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteAction(id: String)

    @Query("SELECT COUNT(*) FROM pending_actions WHERE status = 'PENDING'")
    suspend fun countPendingActions(): Int

    @Query("DELETE FROM pending_actions")
    suspend fun clearAll()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE jobId = :jobId ORDER BY id ASC LIMIT 500")
    fun getMessagesForJob(jobId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE senderId = :uid ORDER BY id ASC LIMIT 500")
    fun getMessagesAuthoredBy(uid: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

/**
 * KYC-003: Two-Guarantor Data Access Object
 */
@Dao
interface GuarantorDao {
    @Query("SELECT * FROM guarantors WHERE workerKycId = :workerKycId LIMIT 20")
    fun getGuarantorsForWorker(workerKycId: String): Flow<List<GuarantorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuarantor(guarantor: GuarantorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuarantors(guarantors: List<GuarantorEntity>)

    @Query("DELETE FROM guarantors WHERE workerKycId = :workerKycId")
    suspend fun deleteGuarantorsForWorker(workerKycId: String)

    @Query("SELECT COUNT(*) FROM guarantors WHERE workerKycId = :workerKycId")
    suspend fun countGuarantorsForWorker(workerKycId: String): Int

    /**
     * REG-002: non-reactive read used by the submission flow (build the payload, verify
     * the stored pair, restore the sheet for editing).
     */
    @Query("SELECT * FROM guarantors WHERE workerKycId = :workerKycId ORDER BY createdAt ASC LIMIT 20")
    suspend fun getGuarantorsForWorkerOnce(workerKycId: String): List<GuarantorEntity>

    /**
     * REG-002 / SEC-010: contact-number lookup used to detect a guarantor who is already
     * listed for another worker. Returns rows only — no caller-side write is possible.
     */
    @Query("SELECT * FROM guarantors WHERE phoneNumber = :phoneNumber LIMIT 20")
    suspend fun findGuarantorsByPhone(phoneNumber: String): List<GuarantorEntity>

    /**
     * REG-002: applies an admin decision to the locally cached rows. Admin-privileged
     * fields are never taken from user input — this is called with values read back from
     * the signed guarantor_submissions document.
     */
    @Query("UPDATE guarantors SET ninVerificationStatus = :ninStatus WHERE workerKycId = :workerKycId")
    suspend fun updateNinStatusForWorker(workerKycId: String, ninStatus: String)

    @Query("DELETE FROM guarantors")
    suspend fun clearAll()
}

/**
 * KYC-001: Worker KYC Data Access Object
 */
@Dao
interface WorkerKycDao {
    @Query("SELECT * FROM worker_kyc WHERE userId = :userId LIMIT 10")
    fun getWorkerKyc(userId: String): Flow<WorkerKycEntity?>

    @Query("SELECT * FROM worker_kyc LIMIT 1")
    fun getCurrentWorkerKyc(): Flow<WorkerKycEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkerKyc(kyc: WorkerKycEntity)

    @Query("UPDATE worker_kyc SET status = :status, submittedAt = :submittedAt WHERE userId = :userId")
    suspend fun updateKycStatus(userId: String, status: String, submittedAt: Long = System.currentTimeMillis())

    @Query("UPDATE worker_kyc SET status = :status, reviewedAt = :reviewedAt, reviewFeedback = :feedback WHERE userId = :userId")
    suspend fun updateKycReview(userId: String, status: String, reviewedAt: Long = System.currentTimeMillis(), feedback: String = "")

    /** REG-002: non-reactive read used by the guarantor submission + status refresh flow. */
    @Query("SELECT * FROM worker_kyc WHERE userId = :userId LIMIT 10")
    suspend fun getWorkerKycOnce(userId: String): WorkerKycEntity?

    /**
     * REG-002: records the guarantor step on the worker's own KYC row. Only ever called
     * with the caller's authenticated uid (the repository gates the write), and the admin
     * outcome fields are supplied from the synced submission document, not from user input.
     */
    @Query(
        "UPDATE worker_kyc SET guarantorStatus = :guarantorStatus, " +
            "groupPhotoStoragePath = :groupPhotoStoragePath, " +
            "guarantorsSubmittedAt = :submittedAt, " +
            "guarantorReviewFeedback = :feedback WHERE userId = :userId"
    )
    suspend fun updateGuarantorStep(
        userId: String,
        guarantorStatus: String,
        groupPhotoStoragePath: String,
        submittedAt: Long,
        feedback: String = ""
    )

    @Query("DELETE FROM worker_kyc")
    suspend fun clearAll()
}

/**
 * ROLE-001: User Profile & Mode Switching DAO
 */
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE id = :userId LIMIT 10")
    fun getUserProfile(userId: String): Flow<UserProfileEntity?>

    /**
     * C2-2 Fix #7 (F3 remainder): LEGACY / NON-AUTHORITATIVE. This query answers "the most recently
     * updated REAL profile row on this device", which is a cache of device state, NOT an identity.
     *
     * It has ZERO production consumers as of Fix #7: SkillsExchangeRepository.currentUser is the
     * only wrapper, and the ViewModel reads currentUserByUid instead. It is deliberately RETAINED
     * rather than deleted, because security tests use it as the trap: they insert two accounts and
     * assert this query answers with the WRONG one, proving no identity path consumes its answer.
     * Deleting it would destroy that evidence.
     *
     * It must never be used to decide who is signed in, who owns a write, or who an action is
     * attributed to. Use getCurrentUserByUid with FirebaseAuth.currentUser.uid.
     */
    @Query("SELECT * FROM user_profiles WHERE id != 'user_default_me' ORDER BY updatedAtTimestamp DESC LIMIT 1")
    fun getCurrentUser(): Flow<UserProfileEntity?>

    /**
     * C2-2 Fix #1: UID-keyed current-user lookup. When a Firebase UID is available it is
     * the authoritative identity key — this query selects ONLY the row whose id matches,
     * with no timestamp ordering and no fallback of any kind. Returns null when the UID
     * has no Room row (caller must fail closed, never substitute another row).
     */
    @Query("SELECT * FROM user_profiles WHERE id = :uid LIMIT 1")
    fun getCurrentUserById(uid: String): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(user: UserProfileEntity)

    @Query("UPDATE user_profiles SET currentActiveMode = :mode, updatedAtTimestamp = :timestamp WHERE id = :userId")
    suspend fun updateActiveMode(userId: String, mode: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profiles SET workerKycStatus = :status, updatedAtTimestamp = :timestamp WHERE id = :userId")
    suspend fun updateWorkerKycStatus(userId: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profiles SET isWorkerActivated = :activated, updatedAtTimestamp = :timestamp WHERE id = :userId")
    suspend fun updateWorkerActivation(userId: String, activated: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profiles SET trainingCompleted = :completed, updatedAtTimestamp = :timestamp WHERE id = :userId")
    suspend fun updateTrainingStatus(userId: String, completed: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE user_profiles SET walletBalance = :newBalance, updatedAtTimestamp = :timestamp WHERE id = :userId")
    suspend fun updateWalletBalance(userId: String, newBalance: Double, timestamp: Long = System.currentTimeMillis())

    /**
     * H5 (SKILL-003 wiring): persists the worker's validated trade skills on their OWN
     * profile row only. Called exclusively with the authenticated UID's row — never
     * another account's row. Skills are display/catalog metadata, not identity.
     */
    @Query("UPDATE user_profiles SET primarySkill = :primarySkill, additionalSkillsCsv = :additionalSkillsCsv, updatedAtTimestamp = :timestamp WHERE id = :userId")
    suspend fun updateWorkerSkills(userId: String, primarySkill: String, additionalSkillsCsv: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_profiles")
    suspend fun clearAll()
}

/**
 * TRAIN-001: Worker Training Progress Data Access Object
 */
@Dao
interface WorkerTrainingDao {
    @Query("SELECT * FROM worker_training WHERE userId = :userId LIMIT 10")
    fun getTrainingForUser(userId: String): Flow<WorkerTrainingEntity?>

    @Query("SELECT * FROM worker_training LIMIT 1")
    fun getCurrentUserTraining(): Flow<WorkerTrainingEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTraining(training: WorkerTrainingEntity)

    @Query("UPDATE worker_training SET status = :status, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateTrainingStatus(userId: String, status: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE worker_training SET completedModulesCsv = :modules, currentModuleIndex = :index, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun updateCompletedModules(userId: String, modules: String, index: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE worker_training SET status = 'COMPLETED', completionTimestamp = :timestamp, updatedAt = :timestamp WHERE userId = :userId")
    suspend fun completeTraining(userId: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM worker_training")
    suspend fun clearAll()
}

// DISP-001: Dispute Data Access Object
@Dao
interface DisputeDao {
    @Query("SELECT * FROM disputes ORDER BY createdAt DESC LIMIT 200")
    fun getAllDisputes(): Flow<List<DisputeEntity>>

    @Query("SELECT * FROM disputes WHERE reportedBy = :userId OR reportedUserId = :userId ORDER BY createdAt DESC LIMIT 200")
    fun getDisputesForUser(userId: String): Flow<List<DisputeEntity>>

    @Query("SELECT * FROM disputes WHERE jobId = :jobId LIMIT 200")
    suspend fun getDisputeForJob(jobId: String): DisputeEntity?

    @Query("SELECT * FROM disputes WHERE status = :status ORDER BY createdAt DESC LIMIT 200")
    suspend fun getDisputesByStatus(status: String): List<DisputeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDispute(dispute: DisputeEntity)

    @Query("UPDATE disputes SET status = :status, adminNotes = :notes, resolutionType = :resolutionType, resolutionAmount = :amount, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateDisputeStatus(id: String, status: String, notes: String = "", resolutionType: String = "", amount: Double = 0.0, resolvedAt: Long = 0, resolvedBy: String = "", timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM disputes")
    suspend fun clearAll()
}

// RATE-001: Rating Data Access Object
@Dao
interface RatingDao {
    @Query("SELECT * FROM ratings ORDER BY createdAt DESC LIMIT 200")
    fun getAllRatings(): Flow<List<RatingEntity>>

    @Query("SELECT * FROM ratings WHERE toUserId = :userId ORDER BY createdAt DESC LIMIT 200")
    fun getRatingsForUser(userId: String): Flow<List<RatingEntity>>

    @Query("SELECT * FROM ratings WHERE jobId = :jobId LIMIT 200")
    suspend fun getRatingForJob(jobId: String): RatingEntity?

    @Query("SELECT * FROM ratings WHERE fromUserId = :fromUserId AND toUserId = :toUserId AND jobId = :jobId LIMIT 200")
    suspend fun getRatingFromToJob(fromUserId: String, toUserId: String, jobId: String): RatingEntity?

    @Query("SELECT AVG(rating) FROM ratings WHERE toUserId = :userId")
    suspend fun getAverageRatingForUser(userId: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: RatingEntity)

    @Query("DELETE FROM ratings")
    suspend fun clearAll()
}

// NOTIF-001: Notification Data Access Object
@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications WHERE userId = :userId ORDER BY createdAt DESC LIMIT 200")
    fun getNotificationsForUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE userId = :userId AND isRead = 0 ORDER BY createdAt DESC LIMIT 200")
    fun getUnreadNotificationsForUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE userId = :userId AND isRead = 0")
    suspend fun getUnreadCountForUser(userId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId AND isRead = 0")
    suspend fun markAllAsRead(userId: String)

    @Query("DELETE FROM notifications WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}

// JOB-005: Job Status Audit Trail DAO
@Dao
interface JobStatusAuditDao {
    @Query("SELECT * FROM job_status_audit WHERE jobId = :jobId ORDER BY changedAt ASC LIMIT 500")
    suspend fun getAuditTrailForJob(jobId: String): List<JobStatusAuditEntity>

    @Query("SELECT * FROM job_status_audit ORDER BY changedAt DESC LIMIT 500")
    fun getAllAuditEntries(): Flow<List<JobStatusAuditEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditEntry(entry: JobStatusAuditEntity)

    @Query("DELETE FROM job_status_audit")
    suspend fun clearAll()
}

// DISP-004: User/Worker Reporting DAO
@Dao
interface ReportDao {
    @Query("SELECT * FROM reports ORDER BY createdAt DESC LIMIT 200")
    fun getAllReports(): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE reportedBy = :userId ORDER BY createdAt DESC LIMIT 200")
    fun getReportsByUser(userId: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE reportedUserId = :userId ORDER BY createdAt DESC LIMIT 200")
    fun getReportsForUser(userId: String): Flow<List<ReportEntity>>

    @Query("SELECT * FROM reports WHERE status = :status ORDER BY createdAt DESC LIMIT 200")
    suspend fun getReportsByStatus(status: String): List<ReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ReportEntity)

    @Query("UPDATE reports SET status = :status, adminNotes = :adminNotes, resolvedAt = :resolvedAt, resolvedBy = :resolvedBy, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateReportStatus(id: String, status: String, adminNotes: String = "", resolvedAt: Long = 0, resolvedBy: String = "", timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM reports")
    suspend fun clearAll()
}

// AUDIT-W-CASHOUT: Wallet Ledger DAO
/**
 * Data access for the wallet ledger. The available balance is ALWAYS computed from these rows via
 * `computeAvailableBalance` (never read from a mutable balance field), so the ledger is the sole
 * authoritative source of a worker's funds.
 */
@Dao
interface WalletLedgerDao {
    @Query("SELECT * FROM wallet_ledger WHERE workerId = :workerId ORDER BY createdAt ASC LIMIT 500")
    suspend fun getEntriesForWorker(workerId: String): List<WalletLedgerEntity>

    @Query("SELECT * FROM wallet_ledger WHERE workerId = :workerId ORDER BY createdAt DESC LIMIT 500")
    fun observeEntriesForWorker(workerId: String): Flow<List<WalletLedgerEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: WalletLedgerEntity)

    // Synchronous variant used ONLY inside `db.runInTransaction { }`. Room executes the transaction
    // block synchronously on one thread with the transaction held, so a blocking DAO call is safe
    // here and is what makes the multi-write block atomic.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertEntrySync(entry: WalletLedgerEntity)

    @Query("DELETE FROM wallet_ledger")
    suspend fun clearAll()
}

// AUDIT-W-CASHOUT: Worker Withdrawal DAO
/**
 * Data access for worker withdrawals (cashouts). There is deliberately NO public "set status to
 * anything" query: the repository drives the state machine through the narrow, typed queries below
 * so an arbitrary status write (e.g. marking a withdrawal COMPLETED from the client) is not even
 * expressible at the DAO layer.
 */
@Dao
interface WalletWithdrawalDao {
    @Query("SELECT * FROM wallet_withdrawals WHERE workerId = :workerId ORDER BY createdAt DESC LIMIT 200")
    suspend fun getWithdrawalsForWorker(workerId: String): List<WalletWithdrawalEntity>

    @Query("SELECT * FROM wallet_withdrawals WHERE workerId = :workerId ORDER BY createdAt DESC LIMIT 200")
    fun observeWithdrawalsForWorker(workerId: String): Flow<List<WalletWithdrawalEntity>>

    @Query("SELECT * FROM wallet_withdrawals WHERE id = :id LIMIT 1")
    suspend fun getWithdrawalById(id: String): WalletWithdrawalEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWithdrawal(withdrawal: WalletWithdrawalEntity)

    // Synchronous variants used ONLY inside `db.runInTransaction { }`. See WalletLedgerDao comment:
    // Room runs the transaction block synchronously on one thread with the transaction held, so
    // blocking DAO calls are safe here and keep the multi-statement block atomic.
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertWithdrawalSync(withdrawal: WalletWithdrawalEntity)

    @Query("SELECT * FROM wallet_withdrawals WHERE workerId = :workerId ORDER BY createdAt DESC LIMIT 200")
    fun getWithdrawalsForWorkerSync(workerId: String): List<WalletWithdrawalEntity>

    // Narrow, typed state transitions driven ONLY by the repository's state machine. `ABORT` on
    // conflict makes the guard part of the SQL WHERE clause atomic: a stale read cannot overwrite.
    @Query("UPDATE wallet_withdrawals SET status = :toStatus, updatedAt = :timestamp WHERE id = :id AND status = :fromStatus")
    suspend fun transitionStatus(id: String, fromStatus: String, toStatus: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("UPDATE wallet_withdrawals SET status = :toStatus, updatedAt = :timestamp WHERE id = :id AND status = :fromStatus")
    fun transitionStatusSync(id: String, fromStatus: String, toStatus: String, timestamp: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM wallet_withdrawals")
    suspend fun clearAll()
}

/**
 * AUDIT-A-SOS: Data access for the authoritative local SOS incident records.
 */
@Dao
interface SosIncidentDao {
    @Query("SELECT * FROM sos_incidents WHERE id = :incidentId LIMIT 1")
    suspend fun getSosIncidentById(incidentId: String): SosIncidentEntity?

    @Query("SELECT * FROM sos_incidents WHERE jobId = :jobId LIMIT 1")
    suspend fun getSosIncidentByJobId(jobId: String): SosIncidentEntity?

    @Query("SELECT * FROM sos_incidents WHERE status = 'ACTIVE' ORDER BY raisedAt DESC LIMIT 200")
    fun getActiveSosIncidents(): Flow<List<SosIncidentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSosIncident(incident: SosIncidentEntity)

    @Query("UPDATE sos_incidents SET status = :status, acknowledgedAt = :acknowledgedAt, clearedAt = :clearedAt, clearedBy = :clearedBy WHERE id = :id")
    suspend fun updateSosIncidentStatus(
        id: String,
        status: String,
        acknowledgedAt: Long,
        clearedAt: Long,
        clearedBy: String?
    )
}

