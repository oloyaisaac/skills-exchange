package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// JOB-012: Job Address Fields
data class JobAddress(
    var streetAddress: String = "",
    var apartmentSuite: String = "",
    var landmarkDirections: String = ""
)

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
            var fromStatus = fromValue(from) ?: return false;
            var toStatus = fromValue(to) ?: return false;
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
    var name: String,
    var tagline: String,
    var category: String,
    var neighborhood: String,
    var residenceYears: Int,
    var hourlyRate: Double,
    var distanceMiles: Double,
    var rating: Double,
    var completedJobsCount: Int,
    var avgResponseTime: String,
    var isVerifiedResident: Boolean,
    var isBackgroundChecked: Boolean,
    var bio: String,
    var skillsCsv: String,
    var latitude: Double,
    var longitude: Double,
    var endorsementsCount: Int,
    // KYC & Verification Layer
    var nationalIdNumber: String = "UG-8829104-B",
    var idDocumentUrl: String = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=400&q=80",
    var selfieVerificationUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
    var kycStatus: String = "APPROVED", // "UNVERIFIED", "PENDING_REVIEW", "APPROVED", "REJECTED"
    var isActivated: Boolean = false,
    // Mobile Money Wallet Schema
    var momoNumber: String = "+256 772 198 420",
    var momoProvider: String = "MTN_MOMO", // "MTN_MOMO", "AIRTEL_MONEY"
    var walletBalance: Double = 185.0
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
    var providerId: String,
    var skillName: String,
    var endorsedByNeighborName: String,
    var neighborDistrict: String
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
    indices = [
        Index(value = ["assignedProviderId"]),
        Index(value = ["status"]),
        // C2-2 Fix #4 (F4): ownership lookups are by postedByUid, never by the display string.
        Index(value = ["postedByUid"])
    ]
)
data class JobPostEntity(
    @PrimaryKey val id: String,
    var title: String,
    var streetAddress: String = "",
    var apartmentSuite: String = "",
    var landmarkDirections: String = "",
    var photos: List<String> = emptyList(),
    var scheduledAt: Long = 0,
    var category: String,
    var description: String,
    /**
     * C2-2 Fix #4 (audit finding **F4**) — the AUTHORITATIVE, immutable owner identity of this
     * job. It is the `FirebaseAuth.currentUser.uid` of the account that created the job and is the
     * ONLY field that may be used for ownership/authorization ("is this my job?", cancel/edit/manage
     * rights). It is nullable purely for backwards compatibility with rows created before this
     * column existed: those jobs have an UNKNOWN owner and must never be attributed to anyone
     * (see MIGRATION_19_20, which deliberately stores NULL rather than guessing from
     * [postedByResident] or from the newest Room profile).
     *
     * New jobs created through [com.example.data.repository.SkillsExchangeRepository.addJobPost]
     * are guaranteed to carry a non-null, validated UID — creation fails closed otherwise.
     */
    var postedByUid: String? = null,
    /**
     * DISPLAY-ONLY presentation text, e.g. `"Robert K. (Sunnyside)"`. It is human-readable and is
     * NEVER an ownership or security key — the string is freely editable and is not an identity.
     * Do not compare it to a uid and do not gate any permission on it.
     */
    var postedByResident: String,
    var district: String,
    var budget: Double,
    var urgency: String,
    var status: String, // Requested, Accepted, In Progress, Review, Completed
    var assignedProviderId: String?,
    var assignedProviderName: String?,
    /**
     * Canonical assigned-provider identity for authorization (H7 / AUDIT-A-SOS).
     *
     * The Firebase Auth UID of the account that is assigned to this job. This is the ONLY
     * field that may be compared against [FirebaseAuth.currentUser.uid] when deciding
     * "may this account raise an emergency SOS on / stand down this job's flag". It is set by
     * the assignment flow and is stable for the lifetime of the job; it is never derived
     * from the display text, the newest Room row, or a provider-catalog record id.
     *
     * [assignedProviderId] is the row-level foreign key into [ServiceProviderEntity] and is
     * left on the job only for catalog joins (logs, feed labels, analytics). It is
     * DISPLAY-ONLY and must NEVER be compared to a uid — a catalog id and an account uid are
     * different identity classes, and comparing them is the identity-semantics bug this field
     * exists to eliminate.
     */
    var assignedProviderUid: String? = null,
    var createdTimestamp: Long,
    var estimatedHours: Double,
    var materialsCost: Double,
    var safetyFee: Double,
    // Enhanced Labor-Only & Escrow Engine Fields
    var materialsProvidedBy: String = "RESIDENT", // "RESIDENT" (Labor-Only), "PROVIDER" (Labor + Materials), "NONE_NEEDED"
    var laborHours: Double = 2.0,
    var laborRate: Double = 60.0,
    var laborTotal: Double = 120.0,
    var materialsSubtotal: Double = 0.0,
    var platformFee: Double = 12.0,
    var grandTotal: Double = 132.0,
    var materialReceiptsCsv: String = "",
    var beforePhotoUrl: String? = null,
    var afterPhotoUrl: String? = null,
    var emergencyAddonRequested: Boolean = false,
    var emergencyAddonAmount: Double = 0.0,
    var emergencyAddonReason: String = "",
    var emergencyAddonReceiptUrl: String = "",
    var emergencyAddonStatus: String = "NONE", // "NONE", "PENDING", "APPROVED", "REJECTED"
    var paymentChannel: String = "MTN_MOMO", // "MTN_MOMO", "AIRTEL_MONEY", "CARD"
    var momoPhoneNumber: String = "+256 772 198 420",
    var escrowStatus: String = "QUOTE_PENDING", // "QUOTE_PENDING", "DEPOSIT_PROMPTED", "FUNDS_LOCKED_IN_ESCROW", "WORK_IN_PROGRESS", "PROOF_SUBMITTED", "RELEASED_TO_WORKER"
    // Real-World Operational Safeguards
    var doorstepPin: String = "4829",
    var isDoorstepVerified: Boolean = false,
    var materialAdvanceRequested: Boolean = false,
    var materialAdvanceAmount: Double = 0.0,
    var materialAdvanceReceiptUrl: String = "",
    var materialAdvanceStatus: String = "NONE", // "NONE", "REQUESTED", "DISBURSED"
    var isSosTriggered: Boolean = false,
    var calloutFee: Double = 5.0,
    var jobType: String = "ONE_TIME", // "ONE_TIME", "RECURRING"
    var recurringSchedule: String = "", // "DAILY", "WEEKLY", "BI_WEEKLY", "MONTHLY", "CUSTOM"
    var recurringStartDate: String = "",
    var recurringContractProposal: String = "", // JSON or description of negotiated terms
    var recurringNegotiatedRate: Double = 0.0, // Worker's proposed rate for recurring contract
    var recurringProposalStatus: String = "NONE" // "NONE", "PENDING", "ACCEPTED", "REJECTED"
)

/**
 * AUDIT-A-SOS: Authoritative local record of an emergency SOS incident raised on a job.
 *
 * Room is the source of truth for the incident lifecycle; Firestore (`sosIncidents`) is a
 * best-effort mirror consumed by the admin-gated `getSosQueue` callable. Status is one of
 * ACTIVE / ACKNOWLEDGED / CLEARED only.
 */
@Entity(
    tableName = "sos_incidents",
    foreignKeys = [
        ForeignKey(
            entity = JobPostEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["jobId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["postedByUid"]),
        Index(value = ["assignedProviderId"])
    ]
)
data class SosIncidentEntity(
    @PrimaryKey val id: String, // "sos_$jobId"
    var jobId: String,
    var postedByUid: String,
    var assignedProviderId: String?,
    /**
     * Canonical assigned-provider identity for cross-device authorization (H7 / AUDIT-A-SOS).
     *
     * The Firebase Auth UID of the account that is assigned to the underlying job. It is the
     * value mirrored to `sosIncidents.assignedProviderUid` and compared against
     * `request.auth.uid` in the Firestore read rule. It is never derived from the display
     * text, the newest Room row, or the provider-catalog record id.
     */
    var assignedProviderUid: String? = null,
    var status: String = "ACTIVE", // ACTIVE / ACKNOWLEDGED / CLEARED
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var district: String = "",
    var raisedAt: Long = System.currentTimeMillis(),
    var acknowledgedAt: Long = 0,
    var clearedAt: Long = 0,
    var clearedBy: String? = null,
    var firestoreMirrored: Boolean = false
) {
    companion object {
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_ACKNOWLEDGED = "ACKNOWLEDGED"
        const val STATUS_CLEARED = "CLEARED"
    }
}

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
    indices = [Index(value = ["jobId"]), Index(value = ["senderId"])]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    var jobId: String,
    /**
     * C2-2 Fix #5 (audit finding **F5**) — the AUTHENTICATED author of this message, i.e.
     * `FirebaseAuth.currentUser.uid`. This is the only field that may be used to decide "did I
     * write this?" (edit/delete/moderation/notification attribution). It must never be compared
     * against [senderName], against a phone number or against any display string.
     *
     * Nullable for two honest reasons, both meaning "NOT authored by a known account":
     *  - rows written before this column existed (MIGRATION_20_21), and
     *  - system/notification rows, which genuinely have no human author.
     * A null here must never be treated as "mine".
     */
    var senderId: String? = null,
    /** DISPLAY-ONLY name shown in the chat bubble. Never an identity. */
    var senderName: String,
    var isFromUser: Boolean,
    var message: String,
    var timestampText: String,
    var isSystemNotification: Boolean
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
    var fullLegalName: String,
    var nationalIdNumber: String,
    var dateOfBirth: String,
    var gender: String,
    var idFrontStoragePath: String,
    var idBackStoragePath: String,
    var status: String = "NOT_STARTED",
    var submittedAt: Long = 0,
    var reviewedAt: Long = 0,
    var reviewFeedback: String = "",
    // --- REG-002: group photograph + guarantor verification state ---
    /**
     * Private Storage path of the single group photograph that must contain the worker
     * together with both guarantors. Client-writable locally only; the Firestore copy is
     * written by the guarantor-submission callable and read back for review.
     */
    var groupPhotoStoragePath: String = "",
    /**
     * Guarantor step state: NOT_STARTED | DRAFT | SUBMITTED | APPROVED | CHANGES_REQUIRED
     * | REJECTED. Admin-owned values are only ever applied from the synced
     * guarantor_submissions document, never from client input.
     */
    var guarantorStatus: String = "NOT_STARTED",
    var guarantorsSubmittedAt: Long = 0,
    var guarantorReviewFeedback: String = "",
    // KYC-006: Identity Document Validation
    var identityDocumentUri: String? = null,
    var identityDocumentIssuingAuthority: String? = null,
    var identityDocumentExpiry: Long? = null,
    var identityDocumentConsentGiven: Boolean = false,
    var identityDocumentConsentWithdrawn: Boolean = false,
    // KYC-007: Address Verification
    var addressDocumentUri: String? = null,
    var addressDocumentIssuingAuthority: String? = null,
    var addressDocumentExpiry: Long? = null,
    var addressDocumentUtilityBillDate: Long? = null,
    var addressDocumentConsentGiven: Boolean = false,
    var addressDocumentConsentWithdrawn: Boolean = false,
    // KYC-008: Income Proof
    var incomeDocumentUri: String? = null,
    var incomeDocumentType: String? = null,
    var incomeDocumentDate: Long? = null,
    var incomeDocumentConsentGiven: Boolean = false,
    var incomeDocumentConsentWithdrawn: Boolean = false,
    // KYC-009: Income Source Legitimacy
    var sourceDocumentUri: String? = null,
    var sourceDocumentType: String? = null,
    var sourceDocumentAuthenticityVerified: Boolean = false,
    var sourceDocumentConsentGiven: Boolean = false,
    var sourceDocumentConsentWithdrawn: Boolean = false,
    // KYC-010: Facial Recognition
    var faceMatchResult: Float? = null,
    var faceIdPhotoUri: String? = null,
    var faceMatchThreshold: Float = 0.7f,
    var faceRecognitionConsentGiven: Boolean = false,
    var faceRecognitionConsentWithdrawn: Boolean = false
)

/**
 * KYC-003: Two-Guarantor Data Model
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
    var workerKycId: String,
    var fullName: String,
    var phoneNumber: String,
    var relationship: String,
    var district: String,
    var idFrontStoragePath: String,
    var idBackStoragePath: String,
    var createdAt: Long = System.currentTimeMillis(),
    // --- REG-002: guarantor verification requirements (KYC-003 is preserved above) ---
    /** Guarantor NIN as typed by the worker. Never written to logcat — scrub first. */
    var nationalIdNumber: String = "",
    /** Private Storage path of the guarantor's own photograph (never a signed URL). */
    var guarantorPhotoStoragePath: String = "",
    /** Private Storage path of the worker + this guarantor photograph. */
    var workerWithGuarantorPhotoStoragePath: String = "",
    /** Guarantor consent to being listed and to their data being processed. */
    var consentProvided: Boolean = false,
    /** Admin-set NIN check result. The client may only ever write "PENDING". */
    var ninVerificationStatus: String = "PENDING",
    var submittedAt: Long = 0,
    var updatedAt: Long = 0
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
    var phoneNumber: String = "+256 772 198 420",
    var fullName: String = "Sarah Namukasa",
    var district: String = "Sunnyside",
    var initialRole: String = "CLIENT", // "CLIENT" or "WORKER"
    var currentActiveMode: String = "CLIENT", // "CLIENT" or "WORKER"
    var workerKycStatus: String = "NOT_APPLIED", // "NOT_APPLIED", "UNVERIFIED", "PENDING_REVIEW", "CHANGES_REQUIRED", "APPROVED", "REJECTED", "SUSPENDED"
    var isWorkerActivated: Boolean = false,
    var trainingCompleted: Boolean = false,
    var nationalIdNumber: String = "UG-8829104-B",
    var primarySkill: String? = "Electrical",
    var additionalSkillsCsv: String = "Plumbing,Home Repair",
    // REG-004: empty = no photo uploaded yet (UI falls back to initials).
    var avatarUrl: String = "",
    // REG-003: optional worker backup contact; empty strings = not set.
    var backupContactName: String = "",
    var backupContactPhone: String = "",
    var momoNumber: String = "+256 772 198 420",
    var momoProvider: String = "MTN_MOMO",
    var walletBalance: Double = 185.0,
    var latitude: Double? = null,
    var longitude: Double? = null,
    var address: String? = null,
    var isOnboardingCompleted: Boolean = true,
    var createdAtTimestamp: Long = System.currentTimeMillis(),
    var updatedAtTimestamp: Long = System.currentTimeMillis()
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
    var jobId: String,
    var workerId: String,
    var workerName: String,
    var proposedRate: Double,
    var proposedHours: Double,
    var totalQuote: Double,
    var notes: String = "",
    var status: String = "PENDING", // "PENDING", "ACCEPTED", "REJECTED", "WITHDRAWN"
    var submittedAt: Long = System.currentTimeMillis(),
    var respondedAt: Long = 0
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
    var status: String = "NOT_STARTED", // "NOT_STARTED", "IN_PROGRESS", "COMPLETED"
    var completedModulesCsv: String = "",
    var startedAt: Long = 0,
    var completionTimestamp: Long = 0,
    var currentModuleIndex: Int = 0,
    var totalModules: Int = 3,
    var platformRulesCompleted: Boolean = false,
    var escrowGuidelinesCompleted: Boolean = false,
    var customerEtiquetteCompleted: Boolean = false,
    var safetyProtocolsCompleted: Boolean = false,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

/**
 * OFF-001: Offline Action Queue Entity
 * Records write operations performed while offline for later sync.
 */
@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey val id: String,
    var actionType: String, // "CREATE_JOB", "UPDATE_STATUS", "SEND_MESSAGE", "SUBMIT_QUOTE", etc.
    var payloadJson: String, // Serialized action data
    var createdAt: Long = System.currentTimeMillis(),
    var retryCount: Int = 0,
    var maxRetries: Int = 3,
    var status: String = "PENDING" // "PENDING", "SYNCING", "COMPLETED", "FAILED"
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
    var jobId: String,
    var reportedBy: String, // User ID who reported
    var reportedUserId: String, // User ID being reported (worker or client)
    var disputeReason: String, // DisputeReason value
    var description: String,
    var evidenceUrlsCsv: String = "", // Comma-separated evidence photo URLs
    var status: String = DisputeStatus.OPEN.value,
    var adminNotes: String = "",
    var resolutionType: String = "", // "REFUND_FULL", "REFUND_PARTIAL", "NO_ACTION", "WORKER_SUSPENDED"
    var resolutionAmount: Double = 0.0,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var resolvedAt: Long = 0,
    var resolvedBy: String = ""
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
    var jobId: String,
    var fromUserId: String, // User who gave the rating
    var toUserId: String, // User who received the rating
    var rating: Int, // 1-5 stars
    var review: String = "",
    var isFromClient: Boolean = true, // true if from client to worker, false if worker to client
    var createdAt: Long = System.currentTimeMillis()
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
    var userId: String,
    var type: String, // NotificationType value
    var title: String,
    var body: String,
    var jobId: String = "",
    var isRead: Boolean = false,
    var createdAt: Long = System.currentTimeMillis()
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
    var jobId: String,
    var fromStatus: String,
    var toStatus: String,
    /**
     * C2-2 Fix #6 (audit finding **F6**) — the AUTHENTICATED actor who made the change, i.e.
     * `FirebaseAuth.currentUser.uid`. It is written by the repository, never by a caller, so it is
     * an attribution of record rather than an assertion.
     *
     * The column is NOT NULL and that is deliberate going forward: F6 fails closed rather than
     * writing `"system"`/`"unknown"`. Historical rows written before this fix may still contain
     * those legacy values — they are immutable historical data and are never rewritten to the
     * current user.
     */
    var changedBy: String, // authenticated Firebase uid of the actor
    var changedAt: Long = System.currentTimeMillis(),
    var reason: String = ""
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
    var reportedBy: String, // User ID who submitted the report
    var reportedUserId: String, // User ID being reported
    var reportedUserName: String, // Display name of reported user
    var reason: String, // Report reason
    var description: String = "",
    var evidenceUrlsCsv: String = "",
    var status: String = "PENDING", // "PENDING", "REVIEWED", "RESOLVED", "DISMISSED"
    var adminNotes: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var resolvedAt: Long = 0,
    var resolvedBy: String = ""
)

// AUDIT-W-CASHOUT: Wallet Ledger Entity
/**
 * One immutable entry in a worker's wallet audit trail. The AVAILABLE balance is always derived
 * from these rows — never from a mutable balance field — so every movement of money is traceable
 * and a worker can never inflate their balance by editing a number.
 *
 * `entryType` is one of CREDIT / DEBIT / RESERVE / RELEASE / ADJUSTMENT (see [LedgerEntryType] in
 * `WorkerCashoutSecurityCore.kt`). A cashout writes a RESERVE entry at request time and either a
 * RELEASE entry (cancel/fail — funds handed back) or a DEBIT entry (server-authoritative
 * disbursement — money actually left) when the withdrawal settles.
 */
@Entity(
    tableName = "wallet_ledger",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["workerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workerId"]), Index(value = ["withdrawalId"])]
)
data class WalletLedgerEntity(
    @PrimaryKey val id: String,
    /** The wallet owner's authenticated Firebase UID. This is the ONLY identity key. */
    var workerId: String,
    /** CREDIT / DEBIT / RESERVE / RELEASE / ADJUSTMENT. */
    var entryType: String,
    /** Always positive; the [entryType] determines whether it raises or lowers available. */
    var amount: Double,
    /** Set when this entry is part of a withdrawal's lifecycle (reserve/release/debit). */
    var withdrawalId: String? = null,
    var description: String = "",
    var createdAt: Long = System.currentTimeMillis()
)

// AUDIT-W-CASHOUT: Worker Withdrawal (Cashout) Entity
/**
 * A worker's request to withdraw available wallet funds to a mobile-money number.
 *
 * The lifecycle is an explicit state machine (see [WithdrawalStatus]): a worker may create a
 * PENDING request (which RESERVES the funds) and may CANCEL it while still PENDING; the terminal
 * COMPLETED / FAILED / REVERSED states are reachable ONLY from server/provider authority. A worker
 * can never mark their own withdrawal COMPLETED, and a terminal withdrawal can never be modified
 * again (no double-pay, no resurrection).
 *
 * `idempotencyKey` is the caller's retry token; a repeated press with the same key returns the
 * SAME row rather than creating a second reservation. It is scoped to this worker's rows only.
 */
@Entity(
    tableName = "wallet_withdrawals",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["workerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workerId"]), Index(value = ["idempotencyKey"])]
)
data class WalletWithdrawalEntity(
    @PrimaryKey val id: String,
    /** The wallet owner's authenticated Firebase UID — resolved internally, never trusted from UI. */
    var workerId: String,
    var amount: Double,
    /** Normalized mobile-money destination (digits only). NEVER proof of identity. */
    var destinationMomoNumber: String,
    /** PENDING / PROCESSING / COMPLETED / FAILED / CANCELLED / REVERSED. */
    var status: String,
    /** Caller-supplied retry token used to deduplicate the same intended operation. */
    var idempotencyKey: String,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
)

