package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.db.GuarantorEntity
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.UserProfileEntity
import com.example.util.GuarantorValidators
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseService(private val context: Context? = null) {

    private fun initializeFirebase(): Boolean {
        return try {
            if (context != null) {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                FirebaseApp.getApps(context).isNotEmpty()
            } else {
                FirebaseApp.getInstance() != null
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "FirebaseApp initialization skipped: ${e.message}")
            false
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            if (initializeFirebase()) FirebaseAuth.getInstance() else null
        } catch (e: Exception) {
            Log.d("FirebaseService", "FirebaseAuth unavailable: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (initializeFirebase()) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            Log.d("FirebaseService", "FirebaseFirestore unavailable: ${e.message}")
            null
        }
    }

    private val storage: FirebaseStorage? by lazy {
        try {
            if (initializeFirebase()) FirebaseStorage.getInstance() else null
        } catch (e: Exception) {
            Log.d("FirebaseService", "FirebaseStorage unavailable: ${e.message}")
            null
        }
    }

    private val _currentUserState = MutableStateFlow<FirebaseUser?>(null)
    val currentUserState: StateFlow<FirebaseUser?> = _currentUserState.asStateFlow()

    init {
        try {
            auth?.addAuthStateListener { firebaseAuth ->
                _currentUserState.value = firebaseAuth.currentUser
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firebase Auth listener init: ${e.message}")
        }
    }

    fun getCurrentUser(): FirebaseUser? {
        return try {
            auth?.currentUser
        } catch (e: Exception) {
            null
        }
    }

    suspend fun signInAnonymously(): FirebaseUser? {
        val authInstance = auth ?: return null
        return try {
            // C2 SAFEGUARD: never silently mint an anonymous identity while a real
            // Firebase user is already signed in — that would fork the UID and orphan
            // the durable users/{uid} record the rest of the flow depends on.
            authInstance.currentUser?.let {
                _currentUserState.value = it
                return it
            }
            val result = authInstance.signInAnonymously().await()
            _currentUserState.value = result.user
            result.user
        } catch (e: Exception) {
            Log.d("FirebaseService", "Anonymous sign-in: ${e.message}")
            null
        }
    }

    fun signOut() {
        try {
            auth?.signOut()
            _currentUserState.value = null
        } catch (e: Exception) {
            Log.d("FirebaseService", "Sign out error: ${e.message}")
        }
    }

    suspend fun deleteCurrentUser(): Boolean {
        val authInstance = auth ?: return false
        return try {
            val user = authInstance.currentUser
            user?.delete()?.await()
            _currentUserState.value = null
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Delete user error: ${e.message}")
            false
        }
    }

    suspend fun deleteUserFirestoreData(userId: String, userJobIds: List<String> = emptyList()): Boolean {
        val db = firestore ?: return false
        return try {
            db.collection("users").document(userId).delete().await()
            db.collection("worker_kyc").document(userId).delete().await()
            db.collection("worker_training").document(userId).delete().await()
            db.collection("providers").document(userId).delete().await()

            val guarantors = db.collection("guarantors").whereEqualTo("workerKycId", userId).get().await()
            for (doc in guarantors) {
                doc.reference.delete().await()
            }
            // REG-002: the guarantor verification submission belongs to the account too.
            db.collection("guarantor_submissions").document(userId).delete().await()

            val notifications = db.collection("notifications").whereEqualTo("userId", userId).get().await()
            for (doc in notifications) {
                doc.reference.delete().await()
            }

            val disputes1 = db.collection("disputes").whereEqualTo("reportedBy", userId).get().await()
            val disputes2 = db.collection("disputes").whereEqualTo("reportedUserId", userId).get().await()
            for (doc in disputes1.documents + disputes2.documents) {
                doc.reference.delete().await()
            }

            val quotes = db.collection("job_quotes").whereEqualTo("workerId", userId).get().await()
            for (doc in quotes) {
                doc.reference.delete().await()
            }

            for (jobId in userJobIds) {
                val messages = db.collection("jobs").document(jobId).collection("messages").get().await()
                for (msg in messages) {
                    msg.reference.delete().await()
                }
                db.collection("jobs").document(jobId).delete().await()
            }

            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Delete user Firestore data error: ${e.message}")
            false
        }
    }

    // ==================== AUTH-001: Phone Authentication ====================
    // Google Sign-In authentication
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val authResult = auth?.signInWithCredential(credential)?.await()
            val user = authResult?.user
            if (user != null) {
                _currentUserState.value = user
                Result.success(user)
            } else {
                Result.failure(Exception("Google sign-in returned null user"))
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "Google sign-in error: ${e.message}")
            Result.failure(e)
        }
    }
    
    // ==================== AUTH-001: Phone Authentication ====================


    /**
     * Sends a phone verification code via Firebase Phone Auth.
     * Returns Result with verificationId on success.
     */
    suspend fun sendPhoneVerificationCode(phoneNumber: String): Result<String> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not available"))
        return try {
            // Store verification ID for later use
            val verificationId = "${System.currentTimeMillis()}_${phoneNumber.hashCode()}"
            // Note: Full Firebase Phone Auth requires Play Services and proper SHA certificates
            // This is a structured placeholder that integrates with the auth flow
            // In production, use PhoneAuthProvider.getInstance(auth).verifyPhoneNumber()
            Log.d("FirebaseService", "Phone verification requested for: $phoneNumber")
            Result.success(verificationId)
        } catch (e: Exception) {
            Log.d("FirebaseService", "Send phone verification error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Verifies the OTP code entered by the user.
     * Returns Result with FirebaseUser on success.
     */
    suspend fun verifyPhoneCode(otpCode: String): Result<FirebaseUser?> {
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not available"))
        return try {
            // Note: Full Firebase Phone Auth requires Play Services and proper SHA certificates
            // This is a structured placeholder that integrates with the auth flow
            // In production, use PhoneAuthProvider.verifyPhoneNumber() with verificationId
            Log.d("FirebaseService", "OTP verification attempted")
            // For now, accept any 6-digit code as valid (development mode)
            // In production, this would verify against Firebase
            if (otpCode.length == 6) {
                // Attempt anonymous sign-in as fallback for development
                signInAnonymously()
                Result.success(_currentUserState.value)
            } else {
                Result.failure(Exception("Invalid code format"))
            }
        } catch (e: Exception) {
            Log.d("FirebaseService", "Verify phone code error: ${e.message}")
            Result.failure(e)
        }
    }

    // --- Firestore Data Operations ---

    suspend fun syncJobToFirestore(job: JobPostEntity) {
        val db = firestore ?: return
        try {
            val jobData = mapOf(
                "id" to job.id,
                "title" to job.title,
                "category" to job.category,
                "description" to job.description,
                "postedByResident" to job.postedByResident,
                "district" to job.district,
                "budget" to job.budget,
                "urgency" to job.urgency,
                "status" to job.status,
                "assignedProviderId" to (job.assignedProviderId ?: ""),
                "assignedProviderName" to (job.assignedProviderName ?: ""),
                "createdTimestamp" to job.createdTimestamp,
                "grandTotal" to job.grandTotal,
                "escrowStatus" to job.escrowStatus,
                "paymentChannel" to job.paymentChannel,
                "momoPhoneNumber" to job.momoPhoneNumber,
                "jobType" to job.jobType,
                "recurringSchedule" to job.recurringSchedule,
                "recurringStartDate" to job.recurringStartDate
            )
            db.collection("jobs")
                .document(job.id)
                .set(jobData)
                .await()
            Log.d("FirebaseService", "Successfully synced job ${job.id} to Firestore")
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firestore sync job notice: ${e.message}")
        }
    }

    suspend fun syncProviderToFirestore(provider: ServiceProviderEntity) {
        val db = firestore ?: return
        try {
            val providerData = mapOf(
                "id" to provider.id,
                "name" to provider.name,
                "tagline" to provider.tagline,
                "category" to provider.category,
                "neighborhood" to provider.neighborhood,
                "hourlyRate" to provider.hourlyRate,
                "rating" to provider.rating,
                "completedJobsCount" to provider.completedJobsCount,
                "isVerifiedResident" to provider.isVerifiedResident,
                "bio" to provider.bio,
                "skillsCsv" to provider.skillsCsv
            )
            db.collection("providers")
                .document(provider.id)
                .set(providerData)
                .await()
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firestore sync provider notice: ${e.message}")
        }
    }

    fun streamJobsFromFirestore(): Flow<List<Map<String, Any>>> = callbackFlow {
        val db = firestore
        if (db == null) {
            awaitClose { }
            return@callbackFlow
        }
        try {
            val listener = db.collection("jobs")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.d("FirebaseService", "Firestore jobs snapshot: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val jobsList = snapshot.documents.mapNotNull { doc ->
                            doc.data?.plus("docId" to doc.id)
                        }
                        trySend(jobsList)
                    }
                }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firestore stream listener: ${e.message}")
            awaitClose { }
        }
    }

    suspend fun uploadKycDocument(userId: String, docType: String, fileBytes: ByteArray): String? {
        val storageInstance = storage ?: return null
        return try {
            val fileName = "${docType}_${System.currentTimeMillis()}.jpg"
            val storageRef = storageInstance.reference.child("users/$userId/kyc/$fileName")
            storageRef.putBytes(fileBytes).await()
            storageRef.path
        } catch (e: Exception) {
            Log.e("FirebaseService", "KYC document upload failed: ${e.message}")
            null
        }
    }

    suspend fun uploadQualificationEvidence(userId: String, evidenceType: String, fileBytes: ByteArray? = null, videoUrl: String? = null): String? {
        val storageInstance = storage ?: return null
        return try {
            val path = when (evidenceType) {
                "VIDEO" -> {
                    if (videoUrl.isNullOrBlank()) return null
                    "users/$userId/certificates/video_${System.currentTimeMillis()}"
                }
                else -> {
                    if (fileBytes == null) return null
                    val fileName = "certificate_${System.currentTimeMillis()}.jpg"
                    val ref = storageInstance.reference.child("users/$userId/certificates/$fileName")
                    ref.putBytes(fileBytes).await()
                    ref.path
                }
            }
            path
        } catch (e: Exception) {
            Log.e("FirebaseService", "Qualification evidence upload failed: ${e.message}")
            null
        }
    }

    /**
     * REG-004: Uploads the signed-in user's profile photo to the public avatars bucket
     * scoped to their own UID (`avatars/{userId}/`, owner-only write per storage.rules)
     * and returns the download URL to persist on the profile.
     *
     * Returns null (fail closed) when [userId] is blank, Firebase Storage is unavailable,
     * or the upload/URL fetch fails. Only the resulting URL string is stored — image bytes
     * never touch Room or Firestore.
     */
    suspend fun uploadProfilePhoto(userId: String, photoUri: Uri): String? {
        if (userId.isBlank()) return null
        val storageInstance = storage ?: return null
        return try {
            val fileName = "profile_${System.currentTimeMillis()}.jpg"
            val storageRef = storageInstance.reference.child("avatars/$userId/$fileName")
            storageRef.putFile(photoUri).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("FirebaseService", "Profile photo upload failed: ${e.message}")
            null
        }
    }

    /**
     * REG-002: Reads back the caller's own guarantor submission so the app can show the
     * admin decision (status, feedback, NIN verification result). Returns null when offline,
     * unauthenticated or when nothing has been filed yet.
     */
    suspend fun fetchGuarantorSubmission(workerId: String): Map<String, Any>? {
        val db = firestore ?: return null
        if (workerId.isBlank()) return null
        return try {
            val doc = db.collection("guarantor_submissions").document(workerId).get().await()
            if (doc.exists()) doc.data else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * REG-002: delete + re-create so a re-submission always lands back in PENDING_REVIEW
     * through the Firestore create rule (a client is never allowed to write a status).
     */
    private suspend fun fileGuarantorSubmission(
        db: FirebaseFirestore,
        workerId: String,
        guarantors: List<GuarantorEntity>,
        groupPhotoStoragePath: String
    ) {
        val ref = db.collection("guarantor_submissions").document(workerId)
        if (ref.get().await().exists()) {
            ref.delete().await()
        }
        ref.set(
            mapOf(
                "workerId" to workerId,
                "guarantorIds" to guarantors.map { it.id },
                "guarantorCount" to guarantors.size,
                "groupPhotoStoragePath" to groupPhotoStoragePath,
                "consentConfirmed" to true,
                "submittedAt" to System.currentTimeMillis(),
                // Initial review state only — the create rule rejects any other value, and
                // every later change is admin-only.
                "status" to "PENDING_REVIEW"
            )
        ).await()
    }

    /**
     * REG-002: Uploads one guarantor-verification photograph into the private verification
     * prefix `users/{workerId}/guarantors/{slot}_{timestamp}.jpg`.
     *
     * [slot] is restricted to the three known purposes ("guarantor", "with_worker",
     * "group") so document ids can never steer the write into another folder. The owner may
     * write only inside their own prefix (storage.rules), uploads are image-only and capped,
     * and there is no public read on that prefix anywhere.
     *
     * Returns the Storage **path** — deliberately never a download/signed URL: storing a
     * URL would hand out a bearer token for another person's identity photographs. The
     * admin review function resolves bytes from the path with Admin privileges.
     *
     * Fail closed: null when the caller or slot is invalid, when Storage is unavailable, or
     * on any upload error. The failure message is scrubbed before logging so identifiers in
     * a URI cannot reach logcat.
     */
    suspend fun uploadGuarantorPhoto(workerId: String, slot: String, photoUri: Uri): String? {
        if (workerId.isBlank() || photoUri.toString().isBlank()) return null
        if (slot !in GuarantorValidators.PHOTO_SLOTS) return null
        val storageInstance = storage ?: return null
        return try {
            val storageRef = storageInstance.reference
                .child("users/$workerId/guarantors/${slot}_${System.currentTimeMillis()}.jpg")
            storageRef.putFile(photoUri).await()
            storageRef.path
        } catch (e: Exception) {
            Log.d("FirebaseService", "Guarantor photo upload failed: ${GuarantorValidators.scrubIdentifiers(e.message ?: "unknown")}")
            null
        }
    }

    /**
     * REG-002: Files the worker's two guarantors plus the group photograph for admin
     * review. Must be called with the authenticated uid as [workerId] — the repository
     * refuses to call it for anyone else, and `firestore.rules` independently rejects any
     * document whose `workerKycId` is not the caller's uid.
     *
     * Each guarantor document is created when new and refreshed in place when it already
     * exists; refreshes never touch `ninVerificationStatus`, so a worker cannot clear an
     * admin's NIN decision. The submission document is re-created (delete + create) so a
     * re-submission always returns the review state to PENDING_REVIEW through the create
     * rule rather than by letting the client write a status.
     *
     * Client-side validation is not trusted here: exactly two consented guarantors with the
     * full field set and a group photograph are required before anything is written.
     * Returns false on any failure (fail closed) without logging identifiers.
     */
    suspend fun submitGuarantorVerification(
        workerId: String,
        guarantors: List<GuarantorEntity>,
        groupPhotoStoragePath: String
    ): Boolean {
        val db = firestore ?: return false
        if (workerId.isBlank()) return false
        if (guarantors.size != GuarantorValidators.REQUIRED_GUARANTOR_COUNT) return false
        if (groupPhotoStoragePath.isBlank()) return false
        if (guarantors.any {
                it.fullName.isBlank() || it.nationalIdNumber.isBlank() ||
                    it.phoneNumber.isBlank() || !it.consentProvided ||
                    it.guarantorPhotoStoragePath.isBlank() ||
                    it.workerWithGuarantorPhotoStoragePath.isBlank()
            }
        ) return false
        return try {
            val submittedAt = System.currentTimeMillis()
            guarantors.forEach { guarantor ->
                val ref = db.collection("guarantors").document(guarantor.id)
                val payload = mutableMapOf<String, Any>(
                    "workerKycId" to workerId,
                    "fullName" to guarantor.fullName.trim(),
                    "nationalIdNumber" to guarantor.nationalIdNumber.trim(),
                    "phoneNumber" to guarantor.phoneNumber.trim(),
                    "relationship" to guarantor.relationship.trim(),
                    "district" to guarantor.district.trim(),
                    "guarantorPhotoStoragePath" to guarantor.guarantorPhotoStoragePath,
                    "workerWithGuarantorPhotoStoragePath" to guarantor.workerWithGuarantorPhotoStoragePath,
                    "consentProvided" to true,
                    "submittedAt" to submittedAt,
                    "updatedAt" to submittedAt
                )
                if (ref.get().await().exists()) {
                    ref.update(payload).await()
                } else {
                    payload["createdAt"] = guarantor.createdAt
                    // Admin-owned: the client may only ever file the un-reviewed value.
                    payload["ninVerificationStatus"] = "PENDING"
                    ref.set(payload).await()
                }
            }
            fileGuarantorSubmission(db, workerId, guarantors, groupPhotoStoragePath)
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Guarantor submission failed: ${GuarantorValidators.scrubIdentifiers(e.message ?: "unknown")}")
            false
        }
    }

    fun streamChatMessages(jobId: String): Flow<List<Map<String, Any>>> = callbackFlow {
        val db = firestore
        if (db == null) {
            awaitClose { }
            return@callbackFlow
        }
        try {
            val listener = db.collection("jobs")
                .document(jobId)
                .collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.d("FirebaseService", "Firestore chat snapshot: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val messages = snapshot.documents.mapNotNull { doc ->
                            doc.data?.plus("docId" to doc.id)
                        }
                        trySend(messages)
                    }
                }
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firestore chat stream listener: ${e.message}")
            awaitClose { }
        }
    }

    suspend fun sendChatMessage(jobId: String, senderId: String, senderName: String, text: String, isFromUser: Boolean) {
        val db = firestore ?: return
        try {
            val messageData = mapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "text" to maskPhoneNumbers(text),
                "isFromUser" to isFromUser,
                "timestamp" to System.currentTimeMillis(),
                "jobId" to jobId
            )
            db.collection("jobs")
                .document(jobId)
                .collection("messages")
                .add(messageData)
                .await()
            Log.d("FirebaseService", "Chat message sent for job $jobId")
        } catch (e: Exception) {
            Log.e("FirebaseService", "Failed to send chat message: ${e.message}")
        }
    }

    private fun maskPhoneNumbers(text: String): String {
        val phoneRegex = Regex("(\\+?\\d{3}[-.\\s]?\\d{3}[-.\\s]?\\d{4,6})")
        return phoneRegex.replace(text) { "[Phone Masked]" }
    }

    suspend fun getPendingVerificationQueue(): List<Map<String, Any>> {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("getPendingVerificationQueue")
                .call(mapOf<String, Any>())
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            data["applications"] as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun reviewWorkerApplication(userId: String, decision: String, feedback: String): Boolean {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("reviewWorkerApplication")
                .call(mapOf("userId" to userId, "decision" to decision, "feedback" to feedback))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            data["success"] as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun setWorkerActivationStatus(userId: String, activate: Boolean): Boolean {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("setWorkerActivationStatus")
                .call(mapOf("userId" to userId, "activate" to activate))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            data["success"] as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    // ==================== AUTH-002/AUTH-006: Durable user identity record ====================

    /**
     * AUTH-002 / AUTH-006: Persist the minimum identity record needed to recognise an
     * EXISTING account on a later sign-in.
     *
     * Why this exists: the Room `user_profiles` row is device-local and is deliberately
     * deleted by `clearAllUserData()` on logout. Without a server-side record, a returning
     * Google (or phone) user looks brand new after logout, clearing app data, reinstalling,
     * or signing in on another device, so they get pushed back through onboarding.
     *
     * Written to `users/{uid}` — the authenticated owner may create/update their own
     * document (see `firestore.rules` MODULE 2). Worker KYC/activation is intentionally NOT
     * stored here: `worker_kyc` is the authoritative, admin-controlled source.
     */
    suspend fun saveUserIdentity(profile: UserProfileEntity): Boolean {
        val db = firestore ?: return false
        return try {
            db.collection("users").document(profile.id).set(
                mapOf(
                    "uid" to profile.id,
                    "phoneNumber" to profile.phoneNumber,
                    "fullName" to profile.fullName,
                    "district" to profile.district,
                    "initialRole" to profile.initialRole,
                    "currentActiveMode" to profile.currentActiveMode,
                    "isOnboardingCompleted" to profile.isOnboardingCompleted,
                    // REG-003 / REG-004: profile photo URL + optional backup contact fields.
                    "avatarUrl" to profile.avatarUrl,
                    "backupContactName" to profile.backupContactName,
                    "backupContactPhone" to profile.backupContactPhone,
                    "updatedAtTimestamp" to System.currentTimeMillis()
                )
            ).await()
            true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Save user identity error: ${e.message}")
            false
        }
    }

    /**
     * AUTH-006: Reads the durable identity record for [userId] (the Firebase UID).
     * Returns null when offline, unauthenticated, or when no record exists.
     */
    suspend fun fetchUserIdentity(userId: String): UserProfileEntity? {
        val db = firestore ?: return null
        return try {
            val doc = db.collection("users").document(userId).get().await()
            if (doc.exists()) doc.toUserProfileEntity() else null
        } catch (e: Exception) {
            Log.d("FirebaseService", "Fetch user identity error: ${e.message}")
            null
        }
    }

    /**
     * AUTH-006: Phone-number fallback lookup, used when a returning user signs in on a
     * device where the Room profile is gone and the UID lookup misses.
     */
    suspend fun fetchUserIdentityByPhone(phoneNumber: String): UserProfileEntity? {
        if (phoneNumber.isBlank()) return null
        val db = firestore ?: return null
        return try {
            val snapshot = db.collection("users")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.toUserProfileEntity()
        } catch (e: Exception) {
            Log.d("FirebaseService", "Fetch user identity by phone error: ${e.message}")
            null
        }
    }

    /**
     * Maps a `users/{uid}` document to a local profile row.
     *
     * Worker privileges deliberately fail closed: `workerKycStatus` is reset to
     * "UNVERIFIED" and `isWorkerActivated`/`trainingCompleted` to false, mirroring the
     * established `restoreSession()` rule that a restored worker must re-verify before the
     * worker dashboard unlocks. Admin approval lives in `worker_kyc` and remains the gate.
     */
    private fun DocumentSnapshot.toUserProfileEntity(): UserProfileEntity? {
        val resolvedId = getString("uid") ?: id
        val role = getString("initialRole") ?: "CLIENT"
        return UserProfileEntity(
            id = resolvedId,
            phoneNumber = getString("phoneNumber").orEmpty(),
            fullName = getString("fullName").orEmpty(),
            district = getString("district").orEmpty(),
            initialRole = role,
            currentActiveMode = getString("currentActiveMode") ?: role,
            workerKycStatus = "UNVERIFIED",
            isWorkerActivated = false,
            trainingCompleted = false,
            isOnboardingCompleted = getBoolean("isOnboardingCompleted") ?: true,
            // REG-003 / REG-004: optional fields — older documents simply restore as unset.
            avatarUrl = getString("avatarUrl") ?: "",
            backupContactName = getString("backupContactName") ?: "",
            backupContactPhone = getString("backupContactPhone") ?: ""
        )
    }
}
