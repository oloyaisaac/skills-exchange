package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.db.DisputeEntity
import com.example.data.db.GuarantorEntity
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.SosIncidentEntity
import com.example.data.db.UserProfileEntity
import com.example.util.GuarantorValidators
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
// C2-1: FirebaseTooManyRequestsException lives in firebase-common's `com.google.firebase` package
// (verified in firebase-common-22.1.0.aar), not in `com.google.firebase.auth`.
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await

class FirebaseService(private val context: Context? = null) {

    companion object {
        /**
         * C2-1: Firebase Phone Auth needs a live Activity for the Play-Integrity / reCAPTCHA
         * fallback used by [PhoneAuthProvider.verifyPhoneNumber]. The single-activity app
         * registers itself here (MainActivity) so any FirebaseService instance can run the
         * real verification without threading an Activity through the ViewModel API.
         *
         * A WeakReference is used deliberately: this holder must never keep a destroyed
         * Activity alive. When no Activity is attached the phone flow fails closed.
         */
        private var phoneAuthActivityRef: WeakReference<Activity>? = null

        /** Firebase SMS code lifetime (Google's default is 60s). */
        private const val PHONE_VERIFICATION_TIMEOUT_SECONDS = 60L

        /** Digits of the phone number Firebase actually verified (E.164, no punctuation). */
        private fun phoneDigitsOf(phone: String?): String = phone.orEmpty().filter { it.isDigit() }

        /** Called from the host Activity so real phone verification can run. */
        fun attachPhoneAuthActivity(activity: Activity) {
            phoneAuthActivityRef = WeakReference(activity)
        }

        /** Called from the host Activity when it is destroyed. */
        fun detachPhoneAuthActivity(activity: Activity) {
            if (phoneAuthActivityRef?.get() === activity) phoneAuthActivityRef = null
        }

        private fun currentPhoneAuthActivity(): Activity? = phoneAuthActivityRef?.get()

        /**
         * C2-1: Normalises user input to the E.164 form Firebase Phone Auth requires
         * (`+256772198420`). Ugandan input is accepted as `0XXXXXXXXX`, `XXXXXXXXX`,
         * `256XXXXXXXXX` or `+256XXXXXXXXX`; a number that cannot be resolved confidently
         * returns null so the caller fails closed instead of guessing a country.
         */
        fun toE164PhoneNumber(rawPhone: String, defaultCountryCode: String = "+256"): String? {
            val trimmed = rawPhone.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.any { !it.isDigit() && it != '+' && it !in " ()-" }) return null
            val digits = trimmed.filter { it.isDigit() }
            if (digits.length < 9 || digits.length > 15) return null
            val countryDigits = defaultCountryCode.filter { it.isDigit() }
            if (countryDigits.isEmpty()) return null
            return when {
                trimmed.startsWith("+") -> "+$digits"
                digits.startsWith(countryDigits) -> "+$digits"
                digits.startsWith("0") && digits.length >= 10 -> "+$countryDigits${digits.drop(1)}"
                !digits.startsWith("0") && digits.length in 9..10 -> "+$countryDigits$digits"
                else -> null
            }
        }

        /**
         * C2-1: Pure fail-closed gate for an OTP submission.
         *
         * Returns null only when a REAL Firebase phone verification is in flight — i.e. a
         * verification id from `onCodeSent` (with a well-formed 6-digit code) or an
         * auto-retrieved Play-Services credential. In every other case it returns the error
         * to surface, which is what makes "any six-digit code" impossible: without a pending
         * Firebase verification there is nothing to exchange for a credential.
         */
        fun phoneVerificationGateFailureReason(
            verificationId: String?,
            hasAutoCredential: Boolean,
            otpCode: String
        ): String? {
            if (verificationId.isNullOrBlank() && !hasAutoCredential) {
                return "Request a verification code first."
            }
            if (verificationId.isNullOrBlank()) return null // Play Services already verified the number.
            val code = otpCode.trim()
            if (code.length != 6 || code.any { !it.isDigit() }) {
                return "Enter the 6-digit code we sent to your phone."
            }
            return null
        }

        /** Maps a Firebase phone-auth failure to a message the UI can show. */
        private fun phoneAuthFailureMessage(error: Throwable): String = when (error) {
            is FirebaseAuthInvalidCredentialsException ->
                "That code is not valid. Check it and try again."
            is FirebaseAuthUserCollisionException ->
                "This phone number is already linked to another account. Sign in with that account instead."
            is FirebaseTooManyRequestsException ->
                "Too many verification attempts. Please wait a moment and try again."
            is FirebaseNetworkException ->
                "Check your internet connection and try again."
            else -> "Phone verification failed. Please try again."
        }
    }

    // ==================== C2-1: real phone verification state ====================

    /** Verification id returned by Firebase `onCodeSent`; null until a code is requested. */
    @Volatile
    private var pendingPhoneVerificationId: String? = null

    /** Credential handed back by Play Services when the number was verified without a code. */
    @Volatile
    private var pendingAutoPhoneCredential: PhoneAuthCredential? = null

    /** E.164 number the pending verification was requested for (identity cross-check). */
    @Volatile
    private var pendingVerificationPhoneNumber: String? = null

    /**
     * C2-1: Drops any in-flight verification. Must be called whenever the flow is abandoned
     * (new code requested, sign-out, or a completed/failed verification) so a stale
     * verification id can never be replayed.
     */
    fun clearPendingPhoneVerification() {
        pendingPhoneVerificationId = null
        pendingAutoPhoneCredential = null
        pendingVerificationPhoneNumber = null
    }

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

    /**
     * C2-1: Anonymous sign-in is NOT an authentication method for this app. It may only be used
     * for a throwaway guest context — never as a substitute for a successful phone verification,
     * and never to mint the identity that owns a profile/session (see `loginUser`).
     */
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
            // C2-1: an abandoned verification must never survive a sign-out.
            clearPendingPhoneVerification()
            auth?.signOut()
            _currentUserState.value = null
        } catch (e: Exception) {
            Log.d("FirebaseService", "Sign out error: ${e.message}")
        }
    }

    suspend fun deleteCurrentUser(): Boolean {
        val authInstance = auth ?: return false
        return try {
            clearPendingPhoneVerification()
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
     * C2-1: Sends a real verification code through Firebase Phone Auth
     * ([PhoneAuthProvider.verifyPhoneNumber]).
     *
     * The user's input is normalised to E.164 first, the request is bound to the attached
     * Activity (Play-Integrity / reCAPTCHA fallback), and the returned verification id is held
     * privately so the code can only ever be exchanged by this service instance. No identity
     * is created here — a Firebase UID exists only after [verifyPhoneCode] succeeds.
     *
     * Returns `Result.success(verificationId)` (blank when Play Services already verified the
     * number and no SMS code was needed) or `Result.failure` with a UI-ready message.
     */
    suspend fun sendPhoneVerificationCode(phoneNumber: String): Result<String> {
        val e164PhoneNumber = toE164PhoneNumber(phoneNumber)
            ?: return Result.failure(Exception("Enter a valid phone number, e.g. +256 772 198 420."))
        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not available"))
        val activity = currentPhoneAuthActivity()
            ?: return Result.failure(Exception("Phone verification is unavailable right now. Please try again."))

        // A new request invalidates any previous verification id.
        clearPendingPhoneVerification()

        return try {
            val verificationId = suspendCancellableCoroutine<String?> { continuation ->
                continuation.invokeOnCancellation { clearPendingPhoneVerification() }
                val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                        // Play Services verified the number itself (SMS Retriever / instant
                        // verification). Keep the credential; the user may never receive a code.
                        pendingAutoPhoneCredential = credential
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onVerificationFailed(exception: FirebaseException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }

                    override fun onCodeSent(
                        id: String,
                        forceResendingToken: PhoneAuthProvider.ForceResendingToken
                    ) {
                        pendingPhoneVerificationId = id
                        if (continuation.isActive) continuation.resume(id)
                    }
                }
                val options = PhoneAuthOptions.newBuilder(authInstance)
                    .setPhoneNumber(e164PhoneNumber)
                    .setTimeout(PHONE_VERIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)
                    .build()
                try {
                    PhoneAuthProvider.verifyPhoneNumber(options)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
            pendingVerificationPhoneNumber = e164PhoneNumber
            Result.success(verificationId.orEmpty())
        } catch (e: Exception) {
            Log.d("FirebaseService", "Send phone verification error: ${e.message}")
            clearPendingPhoneVerification()
            Result.failure(Exception(phoneAuthFailureMessage(e)))
        }
    }

    /**
     * C2-1: Exchanges the submitted code for a Firebase credential and signs in.
     *
     * The code is never interpreted here — it is handed to Firebase
     * ([PhoneAuthProvider.getCredential] + `signInWithCredential`), so only a code Firebase
     * actually issued for the pending verification can produce an authenticated UID.
     *
     * Fail closed: no pending verification, a malformed code, a rejected code, or a resulting
     * identity that is not phone-backed all return `Result.failure` and leave no authenticated
     * identity behind. `signInAnonymously()` is deliberately NOT used as a substitute for
     * successful phone verification.
     */
    suspend fun verifyPhoneCode(otpCode: String): Result<FirebaseUser?> {
        val verificationId = pendingPhoneVerificationId
        val autoCredential = pendingAutoPhoneCredential
        val requestedPhoneNumber = pendingVerificationPhoneNumber

        phoneVerificationGateFailureReason(verificationId, autoCredential != null, otpCode)
            ?.let { return Result.failure(Exception(it)) }

        val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not available"))

        return try {
            val credential = verificationId?.let { PhoneAuthProvider.getCredential(it, otpCode.trim()) }
                ?: autoCredential!!
            val authResult = authInstance.signInWithCredential(credential).await()
            val user = authResult.user
            // Only a phone-backed Firebase identity may continue: an anonymous or phone-less
            // user means verification did not establish the requested phone identity.
            val verifiedPhoneMismatch = requestedPhoneNumber != null &&
                phoneDigitsOf(user?.phoneNumber) != phoneDigitsOf(requestedPhoneNumber)
            if (user == null || user.isAnonymous || user.phoneNumber.isNullOrBlank() || verifiedPhoneMismatch) {
                clearPendingPhoneVerification()
                Result.failure(Exception("Phone verification did not complete. Request a new code and try again."))
            } else {
                clearPendingPhoneVerification()
                _currentUserState.value = user
                Result.success(user)
            }
        } catch (e: Exception) {
            // A rejected code must never leave a previously signed-in identity looking verified.
            clearPendingPhoneVerification()
            Log.d("FirebaseService", "Verify phone code error: ${e.message}")
            Result.failure(Exception(phoneAuthFailureMessage(e)))
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
                // C2-2 Fix #4 (F4): the authoritative owner uid travels with the document so the
                // server side (releaseEscrowToWorker) can authorize on it. The client-supplied value
                // is NOT trusted on its own — firestore.rules binds it to request.auth.uid on
                // create, and the Cloud Function only ever READS it.
                "postedByUid" to (job.postedByUid ?: ""),
                // DISPLAY-ONLY text; never an authorization key.
                "postedByResident" to job.postedByResident,
                "district" to job.district,
                "streetAddress" to job.streetAddress,
                "apartmentSuite" to job.apartmentSuite,
                "landmarkDirections" to job.landmarkDirections,
                "scheduledAt" to job.scheduledAt,
                "photos" to job.photos,
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

    /**
     * AUDIT-A-ARBITRATE: mirrors a locally-filed dispute into the Firestore `disputes`
     * collection that the admin `getDisputesQueue` callable reads. Without this link the
     * arbitration console would always show an empty queue because dispute filing only
     * wrote the Room row.
     *
     * Best-effort, exactly like [syncJobToFirestore] / [syncProviderToFirestore]: the Room
     * insert is the user's record of the report and is never rolled back when the network
     * write fails. `firestore.rules` SEC-006 is the authorizer on create (the document's
     * `reportedBy` must equal the caller's uid); the server-side `resolveDispute` callable
     * gated by `isAdmin()` remains the only writer of any resolution outcome.
     */
    suspend fun syncDisputeToFirestore(dispute: DisputeEntity) {
        val db = firestore ?: return
        try {
            val disputeData = mapOf(
                "id" to dispute.id,
                "jobId" to dispute.jobId,
                "reportedBy" to dispute.reportedBy,
                "reportedUserId" to dispute.reportedUserId,
                "disputeReason" to dispute.disputeReason,
                "description" to dispute.description,
                "evidenceUrlsCsv" to dispute.evidenceUrlsCsv,
                "status" to dispute.status,
                "adminNotes" to dispute.adminNotes,
                "resolutionType" to dispute.resolutionType,
                "resolutionAmount" to dispute.resolutionAmount,
                "createdAt" to dispute.createdAt,
                "updatedAt" to dispute.updatedAt,
                "resolvedAt" to dispute.resolvedAt,
                "resolvedBy" to dispute.resolvedBy
            )
            db.collection("disputes")
                .document(dispute.id)
                .set(disputeData)
                .await()
            Log.d("FirebaseService", "Successfully synced dispute ${dispute.id} to Firestore")
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firestore sync dispute notice: ${e.message}")
        }
    }

    /**
     * AUDIT-A-SOS: mirrors a locally-filed SOS incident into the Firestore `sosIncidents`
     * collection that the admin-gated `getSosQueue` callable reads. Room remains the source of
     * truth; this write is best-effort and never rolls back the local incident.
     */
    suspend fun syncSosIncidentToFirestore(incident: SosIncidentEntity) {
        val db = firestore ?: return
        try {
            val incidentData = mapOf(
                "id" to incident.id,
                "jobId" to incident.jobId,
                "postedByUid" to incident.postedByUid,
                "assignedProviderId" to incident.assignedProviderId,
                "assignedProviderUid" to incident.assignedProviderUid,
                "status" to incident.status,
                "latitude" to incident.latitude,
                "longitude" to incident.longitude,
                "district" to incident.district,
                "raisedAt" to incident.raisedAt,
                "acknowledgedAt" to incident.acknowledgedAt,
                "clearedAt" to incident.clearedAt,
                "clearedBy" to incident.clearedBy,
                "firestoreMirrored" to incident.firestoreMirrored
            )
            db.collection("sosIncidents")
                .document(incident.id)
                .set(incidentData)
                .await()
            Log.d("FirebaseService", "Successfully synced sos incident ${incident.id} to Firestore")
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firestore sync sos incident notice: ${e.message}")
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
    // Fail closed if no authentication context or userId doesn't match auth
    val uid = firebaseAuth.currentUser?.uid
    if (uid == null || uid.isBlank() || uid != userId) return null

    // Validate content type - must be an image
    val contentType = context?.contentResolver?.getType(photoUri) ?: ""
    if (!contentType.startsWith("image/")) return null

    // Read image bytes and validate size (max 10MB) using readBytes(), NOT available()
    val inputStream = context?.contentResolver?.openInputStream(photoUri)
    val bytes = inputStream?.use { it?.readBytes() } ?: return null
    inputStream?.close()
    if (bytes.isEmpty()) return null
    if (bytes.size > 10 * 1024 * 1024) return null // 10MB limit

    // Generate unique filename with timestamp
    val photoId = java.util.UUID.randomUUID().toString()
    val timestamp = System.currentTimeMillis()
    val fileName = "profile_${timestamp}_${photoId}"

    // Upload to storage/avatars/{userId}/profile_{timestamp}_{photoId}
    try {
        val storageRef = storage?.child("avatars/${userId}/${fileName}") ?: return null
        val uploadTask = storageRef.putBytes(bytes).await()
        val downloadUrl = storageRef.downloadUrl.await()
        return downloadUrl.toString()
    } catch (e: Exception) {
        // Avoid logging sensitive info - use generic error
        Log.d("FirebaseService", "uploadProfilePhoto failed: upload error")
        return null
    }
}
    /**
     * AUDIT-C-PHOTO: Uploads a job photo to Firebase Storage at
     * `jobs/{jobId}/initial_photos/{photoId}` and returns its download URL.
     *
     * Fails closed (returns null) when unauthenticated, when the job id is blank, when the
     * content type is not an image, when the file exceeds 10 MB, or when Storage is unavailable.
     */
    suspend fun uploadJobPostPhoto(jobId: String, photoUri: Uri): String? {
        if (firebaseAuth.currentUser == null) return null
        if (jobId.isBlank()) return null

        val resolver = context?.contentResolver ?: return null
        val contentType = resolver.getType(photoUri) ?: ""
        if (!contentType.startsWith("image/")) return null

        val bytes = try {
            resolver.openInputStream(photoUri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return null
        if (bytes.size > 10 * 1024 * 1024) return null

        val photoId = java.util.UUID.randomUUID().toString()
        return try {
            val storageRef = storage?.child("jobs/$jobId/initial_photos/$photoId") ?: return null
            storageRef.putBytes(bytes).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.d("FirebaseService", "uploadJobPostPhoto failed: upload error")
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

    /**
     * REM-044: record the account-level consent receipt server-side. The callable validates the
     * policy version and all three grants; a client boolean is not accepted as evidence.
     */
    suspend fun recordConsent(version: String): Boolean {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            functions.getHttpsCallable("recordConsent")
                .call(
                    mapOf(
                        "version" to version,
                        "grants" to mapOf(
                            "terms" to true,
                            "privacy" to true,
                            "dataProcessing" to true
                        )
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseService", "recordConsent failed: ${e.message}")
            false
        }
    }

    /** REM-044: download the data-subject access export as JSON. */
    suspend fun exportMyData(): String? {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("exportMyData").call(mapOf<String, Any>()).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: return null
            org.json.JSONObject(data).toString()
        } catch (e: Exception) {
            Log.e("FirebaseService", "exportMyData failed: ${e.message}")
            null
        }
    }

    /**
     * REM-023: ask the server whether the signed-in account actually holds the admin claim.
     * Never trust a local flag for this decision.
     */
    suspend fun checkAdminAccess(): Boolean {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("getAdminAccess").call(mapOf<String, Any>()).await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: return false
            data["admin"] == true
        } catch (e: Exception) {
            Log.e("FirebaseService", "checkAdminAccess failed: ${e.message}")
            false
        }
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

    /**
     * AUDIT-A-ARBITRATE: fetch the admin dispute queue.
     *
     * Invokes the `getDisputesQueue` Cloud Function, which is admin-gated via
     * `isAdmin(context)` → `checkAdminCaller(context)`. The Firebase `admin` custom
     * claim remains the authority; the client never supplies admin identity.
     */
    suspend fun getDisputesQueue(status: String? = null): List<Map<String, Any>> {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val payload = mutableMapOf<String, Any>()
            if (!status.isNullOrBlank()) payload["status"] = status
            val result = functions
                .getHttpsCallable("getDisputesQueue")
                .call(payload)
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            data["disputes"] as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            Log.e("FirebaseService", "getDisputesQueue failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * AUDIT-A-ARBITRATE: resolve/reject/escalate a dispute.
     *
     * Invokes the `resolveDispute` Cloud Function, which is admin-gated via
     * `isAdmin(context)` → `checkAdminCaller(context)`. Returns true when the server
     * reports success; false otherwise. The server remains the real authorizer.
     */
    suspend fun resolveDispute(
        disputeId: String,
        resolution: String,
        resolutionType: String?,
        resolutionAmount: Double?,
        adminNotes: String?
    ): Boolean {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val payload = mutableMapOf<String, Any>()
            payload["disputeId"] = disputeId
            payload["resolution"] = resolution
            if (resolutionType != null) payload["resolutionType"] = resolutionType
            if (resolutionAmount != null) payload["resolutionAmount"] = resolutionAmount
            if (adminNotes != null) payload["adminNotes"] = adminNotes
            val result = functions
                .getHttpsCallable("resolveDispute")
                .call(payload)
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            data["success"] as? Boolean ?: false
        } catch (e: Exception) {
            Log.e("FirebaseService", "resolveDispute failed: ${e.message}")
            false
        }
    }

    /**
     * PAY-001 (AUDIT-PAY Phase 2): request a trusted backend MoMo Collection.
     *
     * The app NEVER calls the MTN gateway directly: it invokes the `createMoMoCollection`
     * callable, which reads the trusted `jobs/{id}` document server-side, performs the MTN
     * sandbox RequestToPay, and returns PENDING — never success — without touching escrow.
     * A non-null [MoMoCollectionResult.errorCode] means nothing was collected.
     */
    suspend fun initiateMoMoCollection(
        jobId: String,
        amountUgx: Double? = null,
        phoneNumber: String? = null
    ): MoMoCollectionResult {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val payload = mutableMapOf<String, Any>("jobId" to jobId)
            if (amountUgx != null) payload["amountUgx"] = amountUgx
            if (!phoneNumber.isNullOrBlank()) payload["phoneNumber"] = phoneNumber
            val result = functions
                .getHttpsCallable("createMoMoCollection")
                .call(payload)
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            MoMoCollectionResult(
                success = data["success"] as? Boolean ?: false,
                status = data["status"] as? String,
                transactionRef = data["transactionRef"] as? String,
                providerReferenceId = data["providerReferenceId"] as? String,
                message = data["message"] as? String,
                errorCode = null
            )
        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
            Log.e("FirebaseService", "initiateMoMoCollection failed: ${e.code} ${e.message}")
            MoMoCollectionResult(
                success = false,
                status = null,
                transactionRef = null,
                providerReferenceId = null,
                message = e.message,
                errorCode = e.code.name
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "initiateMoMoCollection error: ${e.message}")
            MoMoCollectionResult(
                success = false,
                status = null,
                transactionRef = null,
                providerReferenceId = null,
                message = e.message,
                errorCode = "UNKNOWN"
            )
        }
    }

    /**
     * PAY-002: query the provider-verified status of a previously initiated collection.
     *
     * The app NEVER derives payment truth locally: this invokes the
     * `getMoMoCollectionStatus` callable, whose server side re-queries the MTN
     * provider status API with the platform's own credentials and only then reports
     * PENDING / SUCCESSFUL / FAILED / UNKNOWN / NEEDS_RECONCILIATION. The backend
     * enforces that a caller may only query transactions bound to their own uid.
     * A non-null [MoMoCollectionStatusResult.errorCode] means the status could NOT be
     * verified — treat the payment as needing attention, never as received.
     */
    suspend fun getMoMoCollectionStatus(transactionRef: String): MoMoCollectionStatusResult {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("getMoMoCollectionStatus")
                .call(mapOf("transactionRef" to transactionRef))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            MoMoCollectionStatusResult(
                status = data["status"] as? String,
                transactionRef = data["transactionRef"] as? String ?: transactionRef,
                escrowLocked = data["escrowLocked"] as? Boolean ?: false,
                message = data["message"] as? String,
                errorCode = null
            )
        } catch (e: com.google.firebase.functions.FirebaseFunctionsException) {
            // Includes permission-denied (foreign transaction), not-found (unknown
            // reference), failed-precondition (fail-closed config) and unavailable.
            Log.e("FirebaseService", "getMoMoCollectionStatus failed: ${e.code} ${e.message}")
            MoMoCollectionStatusResult(
                status = null,
                transactionRef = transactionRef,
                escrowLocked = false,
                message = e.message,
                errorCode = e.code.name
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "getMoMoCollectionStatus error: ${e.message}")
            MoMoCollectionStatusResult(
                status = null,
                transactionRef = transactionRef,
                escrowLocked = false,
                message = e.message,
                errorCode = "UNKNOWN"
            )
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
     * AUTH-006 fallback, retained but NON-AUTHORITATIVE after C2-1: this is a convenience lookup
     * for a returning user on a device where the Room profile is gone and the uid lookup missed.
     *
     * C2-1 WARNING: it must NEVER be used to decide who owns an account. A match only proves that
     * some stored record carries the same phone string — not that the caller controls that number
     * or that the record belongs to the authenticated Firebase UID. The authoritative identity is
     * always `FirebaseAuth.currentUser.uid` (see [getCurrentUser]); privileged state is only ever
     * granted to the uid that `worker_kyc/{uid}` was approved for.
     *
     * (Note: the tightened `users` read rule means this client query now returns null for
     * non-owners — deliberately. Never re-purpose it as an ownership gate.)
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
