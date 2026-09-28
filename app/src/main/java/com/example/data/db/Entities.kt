package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// JOB-004: Job Status Enum with validated states
enum class JobStatus(val value: String) {
    DRAFT("DRAFT"),
    POSTED("POSTED"),
    QUOTING("QUOTING"),
    WORKER_SELECTED("WORKER_SELECTED"),
    ACCEPTED("ACCEPTED"),
    WORKER_EN_ROUTE("WORKER_EN_ROUTE"),
    ARRIVED("ARRIVED"),
    IN_PROGRESS("IN_PROGRESS"),
    COMPLETED("COMPLETED"),
    PAYMENT_RELEASED("PAYMENT_RELEASED"),
    CANCELLED("CANCELLED"),
    DISPUTED("DISPUTED"),
    EXPIRED("EXPIRED");

    companion object {
        fun fromValue(value: String): JobStatus? = entries.find { it.value == value }

        // JOB-004: Define valid state transitions
        private val validTransitions = mapOf(
            DRAFT to setOf(POSTED, CANCELLED),
            POSTED to setOf(QUOTING, CANCELLED, EXPIRED),
            QUOTING to setOf(WORKER_SELECTED, CANCELLED, EXPIRED),
            WORKER_SELECTED to setOf(ACCEPTED, CANCELLED),
            ACCEPTED to setOf(WORKER_EN_ROUTE, IN_PROGRESS, CANCELLED, DISPUTED),
            WORKER_EN_ROUTE to setOf(ARRIVED, CANCELLED, DISPUTED),
            ARRIVED to setOf(IN_PROGRESS, CANCELLED, DISPUTED),
            IN_PROGRESS to setOf(COMPLETED, CANCELLED, DISPUTED),
            COMPLETED to setOf(PAYMENT_RELEASED, DISPUTED),
            PAYMENT_RELEASED to setOf(DISPUTED),
            DISPUTED to setOf(COMPLETED, CANCELLED),
            CANCELLED to emptySet(),
            EXPIRED to emptySet()
        );

        fun canTransition(from: JobStatus, to: JobStatus): Boolean {
            return validTransitions[from]?.contains(to) == true;
        }

        fun isValidTransition(from: String, to: String): Boolean {
            val fromStatus = fromValue(from) ?: return false;
            val toStatus = fromValue(to) ?: return false;
            return canTransition(fromStatus, toStatus);
        }
    }
}

// DISP-001: Dispute Entity
enum class DisputeStatus(val value: String) {
    OPEN("OPEN"),
    UNDER_REVIEW("UNDER_REVIEW"),
    RESOLVED("RESOLVED"),
    REJECTED("REJECTED"),
    ESCALATED("ESCALATED");

    companion object {
        fun fromValue(value: String): DisputeStatus? = entries.find { it.value == value }
    }
}

enum class DisputeReason(val value: String) {
    QUALITY_ISSUE("QUALITY_ISSUE"),
    NO_SHOW("NO_SHOW"),
    INCOMPLETE_WORK("INCOMPLETE_WORK"),
    OVERCHARGING("OVERCHARGING"),
    MISREPRESENTATION("MISREPRESENTATION"),
    SAFETY_CONCERN("SAFETY_CONCERN"),
    OTHER("OTHER");

    companion object {
        fun fromValue(value: String): DisputeReason? = entries.find { it.value == value }
    }
}

// RATE-001: Rating Entity
// NOTIF-001: Notification Entity
enum class NotificationType(val value: String) {
    NEW_QUOTE("NEW_QUOTE"),
    QUOTE_ACCEPTED("QUOTE_ACCEPTED"),
    JOB_ASSIGNED("JOB_ASSIGNED"),
    WORKER_ARRIVING("WORKER_ARRIVING"),
    JOB_STATUS_CHANGE("JOB_STATUS_CHANGE"),
    NEW_MESSAGE("NEW_MESSAGE"),
    PAYMENT_STATUS("PAYMENT_STATUS"),
    PAYMENT_RECEIPT("PAYMENT_RECEIPT"),
    VERIFICATION_DECISION("VERIFICATION_DECISION"),
    TRAINING_REQUIREMENT("TRAINING_REQUIREMENT"),
    DISPUTE_UPDATE("DISPUTE_UPDATE"),
    SOS_EVENT("SOS_EVENT"),
    ACCOUNT_SECURITY("ACCOUNT_SECURITY");

    companion object {
        fun fromValue(value: String): NotificationType? = entries.find { it.value == value }
    }
}

@Entity(tableName = "service_providers")
data class ServiceProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tagline: String,
    val category: String,
    val neighborhood: String,
    val residenceYears: Int,
    val hourlyRate: Double,
    val distanceMiles: Double,
    val rating: Double,
    val completedJobsCount: Int,
    val avgResponseTime: String,
    val isVerifiedResident: Boolean,
    val isBackgroundChecked: Boolean,
    val bio: String,
    val skillsCsv: String,
    val latitude: Double,
    val longitude: Double,
    val endorsementsCount: Int,
    // KYC & Verification Layer
    val nationalIdNumber: String = "UG-8829104-B",
    val idDocumentUrl: String = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=400&q=80",
    val selfieVerificationUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
    val kycStatus: String = "APPROVED", // "UNVERIFIED", "PENDING_REVIEW", "APPROVED", "REJECTED"
    val isActivated: Boolean = false,
    // Mobile Money Wallet Schema
    val momoNumber: String = "+256 772 198 420",
    val momoProvider: String = "MTN_MOMO", // "MTN_MOMO", "AIRTEL_MONEY"
    val walletBalance: Double = 185.0
)

@Entity(
    tableName = "skill_endorsements",
    foreignKeys = [
        ForeignKey(
            entity = ServiceProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["providerId"])]
)
data class SkillEndorsementEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val skillName: String,
    val endorsedByNeighborName: String,
    val neighborDistrict: String
)

@Entity(
    tableName = "job_posts",
    foreignKeys = [
        ForeignKey(
            entity = ServiceProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["assignedProviderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["assignedProviderId"]), Index(value = ["status"])]
)
data class JobPostEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val description: String,
    val postedByResident: String,
    val district: String,
    val budget: Double,
    val urgency: String,
    val status: String, // Requested, Accepted, In Progress, Review, Completed
    val assignedProviderId: String?,
    val assignedProviderName: String?,
    val createdTimestamp: Long,
    val estimatedHours: Double,
    val materialsCost: Double,
    val safetyFee: Double,
    // Enhanced Labor-Only & Escrow Engine Fields
    val materialsProvidedBy: String = "RESIDENT", // "RESIDENT" (Labor-Only), "PROVIDER" (Labor + Materials), "NONE_NEEDED"
    val laborHours: Double = 2.0,
    val laborRate: Double = 60.0,
    val laborTotal: Double = 120.0,
    val materialsSubtotal: Double = 0.0,
    val platformFee: Double = 12.0,
    val grandTotal: Double = 132.0,
    val materialReceiptsCsv: String = "",
    val beforePhotoUrl: String? = null,
    val afterPhotoUrl: String? = null,
    val emergencyAddonRequested: Boolean = false,
    val emergencyAddonAmount: Double = 0.0,
    val emergencyAddonReason: String = "",
    val emergencyAddonReceiptUrl: String = "",
    val emergencyAddonStatus: String = "NONE", // "NONE", "PENDING", "APPROVED", "REJECTED"
    val paymentChannel: String = "MTN_MOMO", // "MTN_MOMO", "AIRTEL_MONEY", "CARD"
    val momoPhoneNumber: String = "+256 772 198 420",
    val escrowStatus: String = "QUOTE_PENDING", // "QUOTE_PENDING", "DEPOSIT_PROMPTED", "FUNDS_LOCKED_IN_ESCROW", "WORK_IN_PROGRESS", "PROOF_SUBMITTED", "RELEASED_TO_WORKER"
    // Real-World Operational Safeguards
    val doorstepPin: String = "4829",
    val isDoorstepVerified: Boolean = false,
    val materialAdvanceRequested: Boolean = false,
    val materialAdvanceAmount: Double = 0.0,
    val materialAdvanceReceiptUrl: String = "",
    val materialAdvanceStatus: String = "NONE", // "NONE", "REQUESTED", "DISBURSED"
    val isSosTriggered: Boolean = false,
    val calloutFee: Double = 5.0,
    val jobType: String = "ONE_TIME", // "ONE_TIME", "RECURRING"
    val recurringSchedule: String = "", // "DAILY", "WEEKLY", "BI_WEEKLY", "MONTHLY", "CUSTOM"
    val recurringStartDate: String = "",
    val recurringContractProposal: String = "", // JSON or description of negotiated terms
    val recurringNegotiatedRate: Double = 0.0, // Worker's proposed rate for recurring contract
    val recurringProposalStatus: String = "NONE" // "NONE", "PENDING", "ACCEPTED", "REJECTED"
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = JobPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val senderName: String,
    val isFromUser: Boolean,
    val message: String,
    val timestampText: String,
    val isSystemNotification: Boolean
)

/**
 * KYC-001: Worker Identity & National ID data schema
 * Isolated table storing worker KYC metadata separate from public profile data.
 */
@Entity(
    tableName = "worker_kyc",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class WorkerKycEntity(
    @PrimaryKey val userId: String,
    val fullLegalName: String,
    val nationalIdNumber: String,
    val dateOfBirth: String,
    val gender: String,
    val idFrontStoragePath: String,
    val idBackStoragePath: String,
    val status: String = "NOT_STARTED",
    val submittedAt: Long = 0,
    val reviewedAt: Long = 0,
    val reviewFeedback: String = "",
    // --- REG-002: group photograph + guarantor verification state ---
    /**
     * Private Storage path of the single group photograph that must contain the worker
     * together with both guarantors. Client-writable locally only; the Firestore copy is
     * written by the guarantor-submission callable and read back for review.
     */
    val groupPhotoStoragePath: String = "",
    /**
     * Guarantor step state: NOT_STARTED | DRAFT | SUBMITTED | APPROVED | CHANGES_REQUIRED
     * | REJECTED. Admin-owned values are only ever applied from the synced
     * guarantor_submissions document, never from client input.
     */
    val guarantorStatus: String = "NOT_STARTED",
    val guarantorsSubmittedAt: Long = 0,
    val guarantorReviewFeedback: String = ""
)

/**
 * KYC-003: Two-Guarantor Data Model
 * Each worker application must have exactly two distinct guarantors.
 */
@Entity(
    tableName = "guarantors",
    foreignKeys = [
        ForeignKey(
            entity = WorkerKycEntity::class,
            parentColumns = ["userId"],
            childColumns = ["workerKycId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workerKycId"]), Index(value = ["phoneNumber"])]
)
data class GuarantorEntity(
    @PrimaryKey val id: String,
    val workerKycId: String,
    val fullName: String,
    val phoneNumber: String,
    val relationship: String,
    val district: String,
    val idFrontStoragePath: String,
    val idBackStoragePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    // --- REG-002: guarantor verification requirements (KYC-003 is preserved above) ---
    /** Guarantor NIN as typed by the worker. Never written to logcat — scrub first. */
    val nationalIdNumber: String = "",
    /** Private Storage path of the guarantor's own photograph (never a signed URL). */
    val guarantorPhotoStoragePath: String = "",
    /** Private Storage path of the worker + this guarantor photograph. */
    val workerWithGuarantorPhotoStoragePath: String = "",
    /** Guarantor consent to being listed and to their data being processed. */
    val consentProvided: Boolean = false,
    /** Admin-set NIN check result. The client may only ever write "PENDING". */
    val ninVerificationStatus: String = "PENDING",
    val submittedAt: Long = 0,
    val updatedAt: Long = 0
)

/**
 * ROLE-001: Unified Account Identity Model
 * Single profile supporting both Client and Worker modes without duplicate accounts.
 */
@Entity(
    tableName = "user_profiles",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class UserProfileEntity(
    @PrimaryKey val id: String = "user_default_me",
    val phoneNumber: String = "+256 772 198 420",
    val fullName: String = "Sarah Namukasa",
    val district: String = "Sunnyside",
    val initialRole: String = "CLIENT", // "CLIENT" or "WORKER"
    val currentActiveMode: String = "CLIENT", // "CLIENT" or "WORKER"
    val workerKycStatus: String = "NOT_APPLIED", // "NOT_APPLIED", "UNVERIFIED", "PENDING_REVIEW", "CHANGES_REQUIRED", "APPROVED", "REJECTED", "SUSPENDED"
    val isWorkerActivated: Boolean = false,
    val trainingCompleted: Boolean = false,
    val nationalIdNumber: String = "UG-8829104-B",
    val primarySkill: String? = "Electrical",
    val additionalSkillsCsv: String = "Plumbing,Home Repair",
    // REG-004: empty = no photo uploaded yet (UI falls back to initials).
    val avatarUrl: String = "",
    // REG-003: optional worker backup contact; empty strings = not set.
    val backupContactName: String = "",
    val backupContactPhone: String = "",
    val momoNumber: String = "+256 772 198 420",
    val momoProvider: String = "MTN_MOMO",
    val walletBalance: Double = 185.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val isOnboardingCompleted: Boolean = true,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val updatedAtTimestamp: Long = System.currentTimeMillis()
)

/**
 * MARKET-002: Multi-Quote Bidding Entity
 * Stores worker quote proposals for open jobs.
 */
@Entity(
    tableName = "job_quotes",
    foreignKeys = [
        ForeignKey(
            entity = JobPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ServiceProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["workerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"]), Index(value = ["workerId"])]
)
data class JobQuoteEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val workerId: String,
    val workerName: String,
    val proposedRate: Double,
    val proposedHours: Double,
    val totalQuote: Double,
    val notes: String = "",
    val status: String = "PENDING", // "PENDING", "ACCEPTED", "REJECTED", "WITHDRAWN"
    val submittedAt: Long = System.currentTimeMillis(),
    val respondedAt: Long = 0
)

/**
 * TRAIN-001: Worker Training Progress & Content Data Schema
 * Tracks 2-3 day training module completion separately from identity verification status.
 */
@Entity(
    tableName = "worker_training",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"])]
)
data class WorkerTrainingEntity(
    @PrimaryKey val userId: String,
    val status: String = "NOT_STARTED", // "NOT_STARTED", "IN_PROGRESS", "COMPLETED"
    val completedModulesCsv: String = "",
    val startedAt: Long = 0,
    val completionTimestamp: Long = 0,
    val currentModuleIndex: Int = 0,
    val totalModules: Int = 3,
    val platformRulesCompleted: Boolean = false,
    val escrowGuidelinesCompleted: Boolean = false,
    val customerEtiquetteCompleted: Boolean = false,
    val safetyProtocolsCompleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * OFF-001: Offline Action Queue Entity
 * Records write operations performed while offline for later sync.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey val id: String,
    val actionType: String, // "CREATE_JOB", "UPDATE_STATUS", "SEND_MESSAGE", "SUBMIT_QUOTE", etc.
    val payloadJson: String, // Serialized action data
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val status: String = "PENDING" // "PENDING", "SYNCING", "COMPLETED", "FAILED"
)

// DISP-001: Dispute Entity
@Entity(
    tableName = "disputes",
    foreignKeys = [
        ForeignKey(
            entity = JobPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"]), Index(value = ["status"])]
)
data class DisputeEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val reportedBy: String, // User ID who reported
    val reportedUserId: String, // User ID being reported (worker or client)
    val disputeReason: String, // DisputeReason value
    val description: String,
    val evidenceUrlsCsv: String = "", // Comma-separated evidence photo URLs
    val status: String = DisputeStatus.OPEN.value,
    val adminNotes: String = "",
    val resolutionType: String = "", // "REFUND_FULL", "REFUND_PARTIAL", "NO_ACTION", "WORKER_SUSPENDED"
    val resolutionAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long = 0,
    val resolvedBy: String = ""
)

// RATE-001: Rating Entity
@Entity(
    tableName = "ratings",
    foreignKeys = [
        ForeignKey(
            entity = JobPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"]), Index(value = ["toUserId"])]
)
data class RatingEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val fromUserId: String, // User who gave the rating
    val toUserId: String, // User who received the rating
    val rating: Int, // 1-5 stars
    val review: String = "",
    val isFromClient: Boolean = true, // true if from client to worker, false if worker to client
    val createdAt: Long = System.currentTimeMillis()
)

// NOTIF-001: Notification Entity
@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["userId"]), Index(value = ["createdAt"])]
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String, // NotificationType value
    val title: String,
    val body: String,
    val jobId: String = "",
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// JOB-005: Job Status Audit Trail Entity
@Entity(
    tableName = "job_status_audit",
    foreignKeys = [
        ForeignKey(
            entity = JobPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["jobId"])]
)
data class JobStatusAuditEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val fromStatus: String,
    val toStatus: String,
    val changedBy: String, // User ID who made the change
    val changedAt: Long = System.currentTimeMillis(),
    val reason: String = ""
)

// DISP-004: User/Worker Reporting Entity
@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportedBy"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportedUserId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["reportedBy"]), Index(value = ["reportedUserId"]), Index(value = ["status"])]
)
data class ReportEntity(
    @PrimaryKey val id: String,
    val reportedBy: String, // User ID who submitted the report
    val reportedUserId: String, // User ID being reported
    val reportedUserName: String, // Display name of reported user
    val reason: String, // Report reason
    val description: String = "",
    val evidenceUrlsCsv: String = "",
    val status: String = "PENDING", // "PENDING", "REVIEWED", "RESOLVED", "DISMISSED"
    val adminNotes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long = 0,
    val resolvedBy: String = ""
)

