package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceProviderDao {
    @Query("SELECT * FROM service_providers")
    fun getAllProviders(): Flow<List<ServiceProviderEntity>>

    @Query("SELECT * FROM service_providers WHERE id = :providerId")
    suspend fun getProviderById(providerId: String): ServiceProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvider(provider: ServiceProviderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviders(providers: List<ServiceProviderEntity>)

    @Query("UPDATE service_providers SET endorsementsCount = endorsementsCount + 1 WHERE id = :providerId")
    suspend fun incrementEndorsements(providerId: String)
}

@Dao
interface SkillEndorsementDao {
    @Query("SELECT * FROM skill_endorsements WHERE providerId = :providerId")
    fun getEndorsementsForProvider(providerId: String): Flow<List<SkillEndorsementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEndorsement(endorsement: SkillEndorsementEntity)
}

@Dao
interface JobPostDao {
    @Query("SELECT * FROM job_posts ORDER BY createdTimestamp DESC")
    fun getAllJobs(): Flow<List<JobPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobPostEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<JobPostEntity>)

    @Query("UPDATE job_posts SET status = :status WHERE id = :jobId")
    suspend fun updateJobStatus(jobId: String, status: String)

    @Query("UPDATE job_posts SET assignedProviderId = :providerId, assignedProviderName = :providerName, status = 'Accepted', escrowStatus = 'DEPOSIT_PROMPTED' WHERE id = :jobId")
    suspend fun assignProvider(jobId: String, providerId: String, providerName: String)

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
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE jobId = :jobId ORDER BY id ASC")
    fun getMessagesForJob(jobId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)
}

@Dao
interface NeighborhoodPostDao {
    @Query("SELECT * FROM neighborhood_posts")
    fun getAllPosts(): Flow<List<NeighborhoodPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<NeighborhoodPostEntity>)
}

/**
 * KYC-003: Two-Guarantor Data Access Object
 */
@Dao
interface GuarantorDao {
    @Query("SELECT * FROM guarantors WHERE workerKycId = :workerKycId")
    fun getGuarantorsForWorker(workerKycId: String): Flow<List<GuarantorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuarantor(guarantor: GuarantorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGuarantors(guarantors: List<GuarantorEntity>)

    @Query("DELETE FROM guarantors WHERE workerKycId = :workerKycId")
    suspend fun deleteGuarantorsForWorker(workerKycId: String)

    @Query("SELECT COUNT(*) FROM guarantors WHERE workerKycId = :workerKycId")
    suspend fun countGuarantorsForWorker(workerKycId: String): Int
}

/**
 * KYC-001: Worker KYC Data Access Object
 */
@Dao
interface WorkerKycDao {
    @Query("SELECT * FROM worker_kyc WHERE userId = :userId")
    fun getWorkerKyc(userId: String): Flow<WorkerKycEntity?>

    @Query("SELECT * FROM worker_kyc LIMIT 1")
    fun getCurrentWorkerKyc(): Flow<WorkerKycEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkerKyc(kyc: WorkerKycEntity)

    @Query("UPDATE worker_kyc SET status = :status, submittedAt = :submittedAt WHERE userId = :userId")
    suspend fun updateKycStatus(userId: String, status: String, submittedAt: Long = System.currentTimeMillis())

    @Query("UPDATE worker_kyc SET status = :status, reviewedAt = :reviewedAt, reviewFeedback = :feedback WHERE userId = :userId")
    suspend fun updateKycReview(userId: String, status: String, reviewedAt: Long = System.currentTimeMillis(), feedback: String = "")
}

/**
 * ROLE-001: User Profile & Mode Switching DAO
 */
@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE id = :userId")
    fun getUserProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles LIMIT 1")
    fun getCurrentUser(): Flow<UserProfileEntity?>

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
}

/**
 * TRAIN-001: Worker Training Progress Data Access Object
 */
@Dao
interface WorkerTrainingDao {
    @Query("SELECT * FROM worker_training WHERE userId = :userId")
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
}

