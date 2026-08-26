package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

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
    // Mobile Money Wallet Schema
    val momoNumber: String = "+256 772 198 420",
    val momoProvider: String = "MTN_MOMO", // "MTN_MOMO", "AIRTEL_MONEY"
    val walletBalance: Double = 185.0
)

@Entity(tableName = "skill_endorsements")
data class SkillEndorsementEntity(
    @PrimaryKey val id: String,
    val providerId: String,
    val skillName: String,
    val endorsedByNeighborName: String,
    val neighborDistrict: String
)

@Entity(tableName = "job_posts")
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
    val calloutFee: Double = 5.0
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val senderName: String,
    val isFromUser: Boolean,
    val message: String,
    val timestampText: String,
    val isSystemNotification: Boolean
)

@Entity(tableName = "neighborhood_posts")
data class NeighborhoodPostEntity(
    @PrimaryKey val id: String,
    val providerName: String,
    val residentName: String,
    val jobTitle: String,
    val category: String,
    val ratingGiven: Int,
    val testimonial: String,
    val completedAgoText: String,
    val cost: Double,
    val neighborhood: String
)

/**
 * ROLE-001: Unified Account Identity Model
 * Single profile supporting both Client and Worker modes without duplicate accounts.
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String = "user_default_me",
    val phoneNumber: String = "+256 772 198 420",
    val fullName: String = "Sarah Namukasa",
    val district: String = "Sunnyside",
    val initialRole: String = "CLIENT", // "CLIENT" or "WORKER"
    val currentActiveMode: String = "CLIENT", // "CLIENT" or "WORKER"
    val workerKycStatus: String = "APPROVED", // "UNVERIFIED", "PENDING_REVIEW", "CHANGES_REQUIRED", "APPROVED", "REJECTED", "SUSPENDED"
    val isWorkerActivated: Boolean = true,
    val trainingCompleted: Boolean = true,
    val nationalIdNumber: String = "UG-8829104-B",
    val primarySkill: String? = "Electrical",
    val additionalSkillsCsv: String = "Plumbing,Home Repair",
    val avatarUrl: String = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80",
    val momoNumber: String = "+256 772 198 420",
    val momoProvider: String = "MTN_MOMO",
    val walletBalance: Double = 185.0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val updatedAtTimestamp: Long = System.currentTimeMillis()
)

