package com.example.data.remote

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
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
import com.google.firebase.functions.FirebaseFunctionsException
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

/** R09-P2-02: shown when a consent receipt could not be written — never a success claim. */
private const val CONSENT_FAILURE =
    "We could not record your agreement, so nothing was saved. Check your connection and try again."

/** R09: shown when a withdrawal could not be recorded — nothing changed, and it says so. */
private const val WITHDRAWAL_FAILURE =
    "We could not record the withdrawal. Nothing changed; please try again."

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

        /** R07-P2-04: App Check is installed once per process. */
        @Volatile
        private var appCheckInstalled = false

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

        /**
         * R06-P2-05: why a re-authentication attempt failed. A wrong code and a session that
         * Firebase no longer accepts are different problems for the user, so they read differently.
         */
        private fun reauthFailureMessage(error: Throwable): String = when (error) {
            is FirebaseAuthInvalidCredentialsException -> "That code or Google account was not accepted. Try again."
            is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again."
            is FirebaseNetworkException -> "Check your internet connection and try again."
            is FirebaseAuthUserCollisionException -> "This sign-in belongs to another account. Use the account you are signed in with."
            else -> IdentityLinkRules.reauthMessage(error)
        }

        /** R06-P2-03: why linking a Google identity failed. */
        private fun identityLinkFailureMessage(error: Throwable): String = when (error) {
            is FirebaseNetworkException -> "Check your internet connection and try again."
            is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a moment and try again."
            else -> "Google sign-in could not be linked. Please try again."
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
            val ready = if (context != null) {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    FirebaseApp.initializeApp(context)
                }
                FirebaseApp.getApps(context).isNotEmpty()
            } else {
                FirebaseApp.getInstance() != null
            }
            if (ready) installAppCheck()
            ready
        } catch (e: Exception) {
            Log.d("FirebaseService", "FirebaseApp initialization skipped: ${e.message}")
            false
        }
    }

    /**
     * R07-P2-04: attest the app instance, not just the user.
     *
     * The server now enforces App Check on every callable, so without a provider every call would be
     * rejected. Play Integrity is used in release builds; debug builds use the debug provider, whose
     * token must be registered in the Firebase console (documented in `docs/`). A failure here is
     * logged without the token value and never crashes the app — the callable will then fail closed
     * with a clear message instead.
     */
    private fun installAppCheck() {
        if (appCheckInstalled) return
        try {
            val factory = if (BuildConfig.DEBUG) {
                com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory.getInstance()
            } else {
                com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory.getInstance()
            }
            com.google.firebase.appcheck.FirebaseAppCheck.getInstance()
                .installAppCheckProviderFactory(factory)
            appCheckInstalled = true
        } catch (e: Exception) {
            Log.w("FirebaseService", "App Check setup skipped: ${e.javaClass.simpleName}")
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

    /** REM-024: true when the last sign-in is recent enough for a destructive action. */
    fun hasFreshAuthentication(maxAgeMs: Long = 5 * 60_000L): Boolean {
        val user = auth?.currentUser ?: return false
        val lastSignIn = user.metadata?.lastSignInTimestamp ?: return false
        return System.currentTimeMillis() - lastSignIn in 0..maxAgeMs
    }

    /** REM-024: revoke refresh tokens server-side so a copied session cannot outlive logout. */
    /**
     * REM-026: ask the server to release escrow. Only identifiers are sent; the amount and the
     * worker's wallet are read server-side, and a retry returns the existing payout.
     */
    suspend fun releaseEscrowToWorker(jobId: String, workerId: String): Pair<Boolean, String> {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            // R05-P2-06: validated against the typed contract before the network call.
            val result = functions.getHttpsCallable("releaseEscrowToWorker")
                .call(
                    CallableContracts.payload(
                        "releaseEscrowToWorker",
                        "jobId" to jobId,
                        "workerId" to workerId
                    )
                )
                .await()
            val data = result.data as? Map<*, *>
            Pair(true, data?.get("message")?.toString() ?: "Escrow released.")
        } catch (e: Exception) {
            Log.e("FirebaseService", "releaseEscrowToWorker failed: ${e.message}")
            Pair(false, e.message ?: "Escrow release failed.")
        }
    }

    suspend fun revokeMyTokens(reason: String = "LOGOUT"): Boolean {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            functions.getHttpsCallable("revokeMyTokens")
                .call(CallableContracts.payload("revokeMyTokens", "reason" to reason))
                .await()
            true
        } catch (e: Exception) {
            Log.e("FirebaseService", "revokeMyTokens failed: ${e.message}")
            false
        }
    }

    /**
     * R06-P2-01: deletes the Firebase Auth user. Call this ONLY after
     * [requestAccountDeletion] returned [AccountDeletionOutcome.Purged] — deleting the identity
     * first strips the only actor able to remove the remaining personal data.
     */
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

    /**
     * R07-P2-01: register the job's doorstep PIN with the server.
     *
     * Only the resident who posted the job may do this (`setDoorstepPin` re-checks ownership). The
     * plaintext PIN is hashed server-side with a fresh salt and stored in `job_secrets/{jobId}`, a
     * collection no client can read. The call is idempotent while the job is unverified, so an
     * offline creation can register later.
     */
    suspend fun registerDoorstepPin(jobId: String, pin: String): Result<Unit> {
        if (!DoorstepPinRules.isWellFormed(pin)) {
            return Result.failure(Exception("The doorstep PIN must be exactly four digits."))
        }
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            functions.getHttpsCallable("setDoorstepPin")
                .call(CallableContracts.payload("setDoorstepPin", "jobId" to jobId, "pin" to pin))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("FirebaseService", "setDoorstepPin failed: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * R07-P2-01: ask the server to verify the PIN.
     *
     * The device no longer decides: it sends only the typed code, and the server compares it with the
     * stored hash, counts the attempt and locks after five. `Verified` is the only outcome that means
     * the job may start.
     */
    suspend fun verifyDoorstepPinWithServer(jobId: String, pin: String): DoorstepPinResult {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            functions.getHttpsCallable("verifyDoorstepPin")
                .call(CallableContracts.payload("verifyDoorstepPin", "jobId" to jobId, "pin" to pin))
                .await()
            DoorstepPinResult.Verified
        } catch (e: FirebaseFunctionsException) {
            val details = (e.details as? Map<*, *>)
                ?.entries
                ?.associate { (key, value) -> key.toString() to value }
                ?: emptyMap()
            DoorstepPinRules.outcomeOf(errorCode = e.code.name, message = e.message, details = details)
        } catch (e: Exception) {
            Log.w("FirebaseService", "verifyDoorstepPin failed: ${e.javaClass.simpleName}")
            DoorstepPinResult.Failed("We could not reach the server. Check your connection and try again.")
        }
    }

    /**
     * R10-P2-04: file the guarantor submission through the server.
     *
     * The records are no longer written from this device: uniqueness (the same person cannot guarantee
     * two accounts, a worker cannot guarantee themselves) can only be checked by an actor that sees
     * every submission. Photographs are uploaded first, to this account's private prefix; the
     * callable receives their paths and writes the rows, including the consent receipt it verifies
     * against `users/{uid}/guarantor_consents/{version}`.
     *
     * Fail closed: a rejection carries the server's own sentence, and a network failure is reported as
     * a failure — never as a filed submission.
     */
    suspend fun submitGuarantorVerification(
        guarantors: List<GuarantorEntity>,
        consentVersion: String,
        groupPhotoStoragePath: String
    ): GuarantorSubmissionOutcome {
        val callable = "submitGuarantorVerification"
        if (guarantors.size != GuarantorValidators.REQUIRED_GUARANTOR_COUNT) {
            return GuarantorSubmissionOutcome.Rejected("Two guarantors are required.")
        }
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val payload = guarantors.map { guarantor ->
                mapOf(
                    "id" to guarantor.id,
                    "fullName" to guarantor.fullName.trim(),
                    "nationalIdNumber" to guarantor.nationalIdNumber.trim(),
                    "phoneNumber" to guarantor.phoneNumber.trim(),
                    "relationship" to guarantor.relationship.trim(),
                    "district" to guarantor.district.trim(),
                    "guarantorPhotoStoragePath" to guarantor.guarantorPhotoStoragePath,
                    "workerWithGuarantorPhotoStoragePath" to guarantor.workerWithGuarantorPhotoStoragePath,
                    "consentProvided" to guarantor.consentProvided
                )
            }
            val result = functions.getHttpsCallable(callable)
                .call(
                    CallableContracts.payload(
                        callable,
                        "guarantors" to payload,
                        "consentVersion" to consentVersion,
                        "groupPhotoStoragePath" to groupPhotoStoragePath
                    )
                )
                .await()
            val data = result.data as? Map<*, *>
            GuarantorSubmissionOutcome.Filed(
                status = data?.get("status") as? String ?: "PENDING_REVIEW",
                flags = (data?.get("flags") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
            )
        } catch (e: FirebaseFunctionsException) {
            val serverMessage = e.message?.takeIf { it.isNotBlank() }
            GuarantorSubmissionOutcome.Rejected(
                serverMessage ?: "We could not file your guarantors. Check the details and try again."
            )
        } catch (e: Exception) {
            Log.w("FirebaseService", "$callable failed: ${e.javaClass.simpleName}")
            GuarantorSubmissionOutcome.Rejected(
                "We could not reach the server, so nothing was submitted. Check your connection and try again."
            )
        }
    }

    /**
     * R10-P2-02: file a report on the server, where an operator can see it.
     *
     * Returns the server's own acknowledgement (priority and response target) — the confirmation the
     * UI shows, rather than a local "thanks" for something no human will ever read.
     */
    suspend fun fileReport(
        subjectUid: String,
        subjectName: String,
        jobId: String,
        category: String,
        details: String
    ): ReportSubmissionOutcome {
        val callable = "fileReport"
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable(callable)
                .call(
                    CallableContracts.payload(
                        callable,
                        "subjectUid" to subjectUid,
                        "subjectName" to subjectName,
                        "jobId" to jobId,
                        "category" to category,
                        "details" to details
                    )
                )
                .await()
            val data = result.data as? Map<*, *>
            ReportSubmissionOutcome.Filed(
                reportId = data?.get("id") as? String ?: "",
                priority = data?.get("priority") as? String ?: "",
                message = data?.get("message") as? String
                    ?: "Report received. An operator will review it."
            )
        } catch (e: FirebaseFunctionsException) {
            ReportSubmissionOutcome.Rejected(
                e.message?.takeIf { it.isNotBlank() }
                    ?: "We could not file that report. Check the details and try again."
            )
        } catch (e: Exception) {
            Log.w("FirebaseService", "$callable failed: ${e.javaClass.simpleName}")
            ReportSubmissionOutcome.Rejected(
                "We could not reach the server, so the report was not filed."
            )
        }
    }

    /**
     * R10-P2-06: record a rating on the server.
     *
     * One per job per direction, only after completion, only between the two participants — all
     * decided server-side from the stored job. The device keeps no authority over the stars.
     */
    suspend fun submitRating(jobId: String, subjectUid: String, stars: Int, review: String): RatingSubmissionOutcome {
        val callable = "submitRating"
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable(callable)
                .call(
                    CallableContracts.payload(
                        callable,
                        "jobId" to jobId,
                        "subjectUid" to subjectUid,
                        "stars" to stars,
                        "review" to review
                    )
                )
                .await()
            val data = result.data as? Map<*, *>
            RatingSubmissionOutcome.Recorded((data?.get("average") as? Number)?.toDouble() ?: 0.0)
        } catch (e: FirebaseFunctionsException) {
            RatingSubmissionOutcome.Rejected(
                e.message?.takeIf { it.isNotBlank() } ?: "That rating could not be recorded."
            )
        } catch (e: Exception) {
            Log.w("FirebaseService", "$callable failed: ${e.javaClass.simpleName}")
            RatingSubmissionOutcome.Rejected("We could not reach the server, so your rating was not recorded.")
        }
    }

    /**
     * R10-P2-03: ask the server what actually happened to an emergency alert.
     *
     * The sentence is composed server-side from the dispatch record, so the app cannot claim a
     * delivery that did not happen.
     */
    suspend fun fetchSosDispatchStatus(incidentId: String): SosDispatchInfo? {
        val callable = "getSosDispatchStatus"
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable(callable)
                .call(CallableContracts.payload(callable, "incidentId" to incidentId))
                .await()
            val data = result.data as? Map<*, *> ?: return null
            SosDispatchInfo(
                state = data["state"] as? String ?: "UNKNOWN",
                message = data["message"] as? String ?: "",
                chatMessage = data["chatMessage"] as? String ?: "",
                adviseDirectCall = data["adviseDirectCall"] == true
            )
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.NOT_FOUND) return null
            Log.w("FirebaseService", "$callable failed: ${e.javaClass.simpleName}")
            null
        } catch (e: Exception) {
            Log.w("FirebaseService", "$callable failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * R06-P2-01 / R06-P2-02: ask the server to purge this account's Firestore records **and**
     * Cloud Storage objects, in that order, before anything irreversible happens.
     *
     * The purge moved server-side because the client cannot be the judge of whether data is gone:
     * it used to delete the Firebase Auth user unconditionally after a Firestore delete that may
     * have failed, and it never touched Storage at all (KYC documents, guarantor portraits,
     * avatars and job photos stayed behind). The server re-checks the five-minute fresh-auth
     * window, verifies that every `jobId` really belongs to the caller, and reports counts.
     *
     * The Firebase Auth user is deleted by [deleteCurrentUser] ONLY when this returns
     * [AccountDeletionOutcome.Purged]; every other outcome leaves the signed-in session alive so
     * the account can still be finished or retried.
     */
    suspend fun requestAccountDeletion(jobIds: List<String> = emptyList()): AccountDeletionOutcome {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("deleteMyAccount")
                // R05-P2-06: validated against the typed contract before the network call.
                .call(CallableContracts.payload("deleteMyAccount", "jobIds" to jobIds))
                .await()
            val data = result.data as? Map<*, *>
                ?: return AccountDeletionOutcome.Failed("The server returned no deletion report. Your account was kept.")
            if (data["complete"] == true) {
                AccountDeletionOutcome.Purged(
                    storageDeleted = (data["storageDeleted"] as? Number)?.toInt() ?: 0,
                    firestoreDocsDeleted = (data["firestoreDocsDeleted"] as? Number)?.toInt() ?: 0
                )
            } else {
                AccountDeletionOutcome.Incomplete(
                    message = "Some of your data could not be removed, so your account was kept.",
                    storagePending = (data["storagePending"] as? Number)?.toInt() ?: 1,
                    firestorePending = (data["firestorePending"] as? Number)?.toInt() ?: 1
                )
            }
        } catch (e: FirebaseFunctionsException) {
            val details = (e.details as? Map<*, *>)
                ?.entries
                ?.associate { (key, value) -> key.toString() to value }
                ?: emptyMap()
            AccountDeletionRules.outcomeOf(
                errorCode = e.code.name,
                message = e.message,
                details = details
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "deleteMyAccount failed: ${e.javaClass.simpleName}")
            AccountDeletionOutcome.Failed(
                "We could not reach the server, so nothing was deleted. Check your connection and try again."
            )
        }
    }

    /**
     * R06-P2-04: remove this device's push token from the account before the session ends.
     *
     * A token left registered server-side keeps delivering another person's notifications to a
     * phone that has signed out — or, after a deletion, to an account that no longer exists.
     */
    suspend fun clearMyPushTokens(token: String? = null): Boolean {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            functions.getHttpsCallable("clearMyPushTokens")
                .call(CallableContracts.payload("clearMyPushTokens", "token" to token))
                .await()
            true
        } catch (e: Exception) {
            Log.w("FirebaseService", "clearMyPushTokens failed: ${e.javaClass.simpleName}")
            false
        }
    }

    /**
     * R06-P2-05: refresh the Firebase `auth_time` claim with a fresh Google credential.
     *
     * The five-minute window on account deletion used to have no in-app path to satisfy it: the
     * user was told to sign out and back in, which loses the context of what they were doing (and,
     * for a phone account, is not always possible). Re-authenticating keeps the same `uid` and
     * refreshes the claim the server checks.
     */
    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> {
        val user = auth?.currentUser
            ?: return Result.failure(Exception("Sign in again to continue."))
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.d("FirebaseService", "Google re-authentication failed: ${e.javaClass.simpleName}")
            Result.failure(Exception(reauthFailureMessage(e)))
        }
    }

    /**
     * R06-P2-05: refresh `auth_time` with a phone code for the account's own number.
     *
     * The pending Firebase verification (started by [sendPhoneVerificationCode]) is exchanged for a
     * credential and used to re-authenticate the *existing* user — never to sign in a new one, so a
     * failed re-authentication cannot silently move the person to another account.
     */
    suspend fun reauthenticateWithPhoneCode(otpCode: String): Result<Unit> {
        val user = auth?.currentUser
            ?: return Result.failure(Exception("Sign in again to continue."))
        val verificationId = pendingPhoneVerificationId
        val autoCredential = pendingAutoPhoneCredential
        val requestedPhone = pendingVerificationPhoneNumber
        if (verificationId.isNullOrBlank() && autoCredential == null) {
            return Result.failure(Exception("Request a new code first."))
        }
        val digits = otpCode.trim()
        if (digits.length != 6) return Result.failure(Exception("Enter the 6-digit code."))
        return try {
            val credential = autoCredential ?: PhoneAuthProvider.getCredential(verificationId!!, digits)
            user.reauthenticate(credential).await()
            clearPendingPhoneVerification()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.d("FirebaseService", "Phone re-authentication failed: ${e.javaClass.simpleName}")
            Result.failure(Exception(reauthFailureMessage(e)))
        } finally {
            if (requestedPhone != null) pendingVerificationPhoneNumber = requestedPhone
        }
    }

    /**
     * R06-P2-03: attach a Google identity to the account that is already signed in.
     *
     * Previously a Google sign-in always called `signInWithCredential`, so a person who already had
     * a phone account and then tapped "Continue with Google" either landed in a second account (a
     * second wallet, a second set of jobs) or was dropped at a generic error. Linking keeps one
     * uid and one wallet. A provider conflict is reported as [IdentityLinkResult.Collision] so the
     * UI can explain it instead of guessing.
     */
    suspend fun linkGoogleAccount(idToken: String): IdentityLinkResult {
        val user = auth?.currentUser ?: return IdentityLinkResult.NotSignedIn
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            user.linkWithCredential(credential).await()
            IdentityLinkResult.Linked
        } catch (e: FirebaseAuthUserCollisionException) {
            Log.d("FirebaseService", "Google link collision: credential already in use")
            IdentityLinkResult.Collision(e.message)
        } catch (e: Exception) {
            Log.d("FirebaseService", "Google link failed: ${e.javaClass.simpleName}")
            IdentityLinkResult.Failed(identityLinkFailureMessage(e))
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
            // R07-P2-03: withhold the fields the server owns (terminal escrow outcomes, priced
            // amounts after funding). A merge keeps the server's values instead of deleting them —
            // a plain `set()` would wipe the escrow state the Functions wrote.
            val writable = JobSyncPolicy.clientPayload(jobData, job.status, job.escrowStatus)
            db.collection("jobs")
                .document(job.id)
                .set(writable, com.google.firebase.firestore.SetOptions.merge())
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
    /**
     * R04-P2-05: returns whether the mirror actually landed. It used to return Unit and swallow the
     * failure, so "mirrored" could not be recorded and nothing could retry it.
     */
    suspend fun syncSosIncidentToFirestore(incident: SosIncidentEntity): Boolean {
        val db = firestore ?: return false
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
            return true
        } catch (e: Exception) {
            Log.d("FirebaseService", "Firestore sync sos incident notice: ${e.message}")
            return false
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
    val uid = auth?.currentUser?.uid
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
        val storageRef = storage?.reference?.child("avatars/${userId}/${fileName}") ?: return null
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
        if (auth?.currentUser == null) return null
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
            val storageRef = storage?.reference?.child("jobs/$jobId/initial_photos/$photoId") ?: return null
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
        groupPhotoStoragePath: String,
        consentRecordedAt: Long
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
                // R09-P2-02: the submission names the version and the receipt it was filed under;
                // the create rule rejects a submission without them.
                "consentVersion" to ConsentVersions.GUARANTOR_VERSION,
                "consentRecordedAt" to consentRecordedAt,
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
            // R09-P2-02: the third-party receipt comes first. Filing without a recorded grant would
            // put two other people's identity documents on the server on the worker's word alone.
            val consentRecordedAt = recordGuarantorConsent(
                ConsentVersions.GUARANTOR_VERSION,
                guarantors.size
            ) ?: return false
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
                    // R09-P2-02: the dated receipt this row was filed under, not just a boolean.
                    "consentVersion" to ConsentVersions.GUARANTOR_VERSION,
                    "consentRecordedAt" to consentRecordedAt,
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
            fileGuarantorSubmission(
                db,
                workerId,
                guarantors,
                groupPhotoStoragePath,
                consentRecordedAt
            )
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
     * R09-P2-02: record the account-level consent receipt server-side, and report what happened.
     *
     * The callable validates the policy version and all three grants; a client boolean is not
     * accepted as evidence. The result is returned rather than swallowed, because the policy sheet
     * must not close on a consent that was never written.
     */
    suspend fun recordConsent(version: String): ConsentOutcome {
        if (version.isBlank()) return ConsentOutcome.Failed("No policy version to record.")
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("recordConsent")
                .call(
                    CallableContracts.payload(
                        "recordConsent",
                        "version" to version,
                        "grants" to ConsentVersions.PRIVACY_GRANTS.associateWith { true }
                    )
                )
                .await()
            val data = result.data as? Map<*, *> ?: return ConsentOutcome.Failed(CONSENT_FAILURE)
            val recorded = data["acceptedAt"] as? Number
            ConsentOutcome.Recorded(
                version = data["version"] as? String ?: version,
                recordedAtMillis = recorded?.toLong() ?: System.currentTimeMillis(),
                currentVersion = data["currentVersion"] as? String ?: version
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "recordConsent failed: ${e.javaClass.simpleName}")
            ConsentOutcome.Failed(CONSENT_FAILURE)
        }
    }

    /**
     * R09-P2-02: record the third-party guarantor consent before the guarantor documents are filed.
     *
     * The guarantors' NINs, phones and photographs are other people's personal data. The worker's own
     * acceptance says nothing about them, so this is a separate receipt, written by the server with
     * its own version and timestamp. Filing is refused unless this succeeds.
     */
    suspend fun recordGuarantorConsent(version: String, guarantorCount: Int): Long? {
        if (version.isBlank() || guarantorCount < 1) return null
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("recordGuarantorConsent")
                .call(
                    CallableContracts.payload(
                        "recordGuarantorConsent",
                        "version" to version,
                        "guarantorCount" to guarantorCount,
                        "confirmed" to true
                    )
                )
                .await()
            val data = result.data as? Map<*, *> ?: return null
            (data["recordedAt"] as? Number)?.toLong()
        } catch (e: Exception) {
            Log.e("FirebaseService", "recordGuarantorConsent failed: ${e.javaClass.simpleName}")
            null
        }
    }

    /**
     * R09: withdraw consent. The receipt is kept and the revocation is added — a consent history that
     * can be deleted proves nothing. Requires a fresh sign-in, enforced by the callable.
     */
    suspend fun withdrawConsent(version: String, reason: String): ConsentOutcome {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions.getHttpsCallable("withdrawConsent")
                .call(
                    CallableContracts.payload(
                        "withdrawConsent",
                        "version" to version,
                        "reason" to reason
                    )
                )
                .await()
            val data = result.data as? Map<*, *> ?: return ConsentOutcome.Failed(WITHDRAWAL_FAILURE)
            ConsentOutcome.Recorded(
                version = data["version"] as? String ?: version,
                recordedAtMillis = (data["revokedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                currentVersion = version
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "withdrawConsent failed: ${e.javaClass.simpleName}")
            ConsentOutcome.Failed(WITHDRAWAL_FAILURE)
        }
    }

    /**
     * REM-044 / R09-P2-03: the data-subject access export, as the server builds it.
     *
     * The JSON is produced entirely on the server (third-party NINs masked, storage paths dropped), so
     * the client cannot add to or invent the contents of the export.
     */
    suspend fun exportMyData(): String? {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("exportMyData")
                .call(CallableContracts.payload("exportMyData"))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any?> ?: return null
            org.json.JSONObject(data).toString(2)
        } catch (e: Exception) {
            // The message can carry a project id or a URL; the class name cannot.
            Log.e("FirebaseService", "exportMyData failed: ${e.javaClass.simpleName}")
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
            val result = functions
                .getHttpsCallable("getAdminAccess")
                .call(CallableContracts.payload("getAdminAccess"))
                .await()
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
                .call(CallableContracts.payload("getPendingVerificationQueue"))
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
                .call(
                    CallableContracts.payload(
                        "reviewWorkerApplication",
                        "userId" to userId,
                        "decision" to decision,
                        "feedback" to feedback
                    )
                )
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
                .call(
                    CallableContracts.payload(
                        "setWorkerActivationStatus",
                        "userId" to userId,
                        "activate" to activate
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            data["success"] as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * R10-P2-02/P2-07: the safety-desk queue.
     *
     * Admin-gated server-side (`getReportsQueue`). Rows carry the server-decided priority and the
     * response target; the client never computes either. An empty list is returned on failure — an
     * empty queue is indistinguishable from an unreadable one, so the console says "could not load"
     * from [fetchSafetyMetrics] being null, never from this returning nothing.
     */
    suspend fun getReportsQueue(status: String? = null): List<Map<String, Any>> {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("getReportsQueue")
                .call(CallableContracts.payload("getReportsQueue", "status" to status))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            data["reports"] as? List<Map<String, Any>> ?: emptyList()
        } catch (e: Exception) {
            Log.e("FirebaseService", "getReportsQueue failed: ${e.message}")
            emptyList()
        }
    }

    /**
     * R10-P2-02: record a triage decision on a report.
     *
     * Returns the server's own sentence, or a refusal sentence when the callable declines. Never
     * returns a generic success: the console shows exactly what the server said.
     */
    suspend fun resolveReport(
        reportId: String,
        status: String,
        outcome: String,
        notes: String = ""
    ): ReportResolutionOutcome {
        // A decision the server does not know is refused here: the operator is told nothing was sent,
        // rather than being shown a success for a write that never happened.
        if (!ReportCategories.isKnownDecision(status)) {
            return ReportResolutionOutcome.Rejected(
                "Unknown decision '" + status + "' — nothing was sent."
            )
        }
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("resolveReport")
                .call(
                    CallableContracts.payload(
                        "resolveReport",
                        "reportId" to reportId,
                        "status" to status,
                        // The server's field names: `outcome` is what an auditor reads, `notes` is
                        // the operator's own commentary.
                        "outcome" to outcome,
                        "notes" to notes
                    )
                )
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: emptyMap()
            // The sentence is the server's; with no sentence nothing is claimed beyond the status.
            ReportResolutionOutcome.Recorded(
                data["message"] as? String
                    ?: ("Report is now " + ((data["status"] as? String) ?: status) + ".")
            )
        } catch (e: Exception) {
            Log.e("FirebaseService", "resolveReport failed: ${e.message}")
            ReportResolutionOutcome.Rejected(
                e.message ?: "The report could not be updated."
            )
        }
    }

    /**
     * R10-P2-07: the trust-and-safety dashboard line — open reports, breached SLAs, oldest open.
     *
     * Null means "could not read", which the console renders as unknown rather than as a healthy
     * zero. A metrics surface that lies about an empty desk is worse than one that admits it failed.
     */
    suspend fun fetchSafetyMetrics(): String? {
        return try {
            val functions = com.google.firebase.functions.FirebaseFunctions.getInstance()
            val result = functions
                .getHttpsCallable("getSafetyMetrics")
                .call(CallableContracts.payload("getSafetyMetrics"))
                .await()
            @Suppress("UNCHECKED_CAST")
            val data = result.data as? Map<String, Any> ?: return null
            data["summary"] as? String
        } catch (e: Exception) {
            Log.e("FirebaseService", "getSafetyMetrics failed: ${e.message}")
            null
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
            val result = functions
                .getHttpsCallable("getDisputesQueue")
                .call(CallableContracts.payload("getDisputesQueue", "status" to status))
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
            val result = functions
                .getHttpsCallable("resolveDispute")
                .call(
                    CallableContracts.payload(
                        "resolveDispute",
                        "disputeId" to disputeId,
                        "resolution" to resolution,
                        "resolutionType" to resolutionType,
                        "resolutionAmount" to resolutionAmount,
                        "adminNotes" to adminNotes
                    )
                )
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
            val result = functions
                .getHttpsCallable("createMoMoCollection")
                .call(
                    CallableContracts.payload(
                        "createMoMoCollection",
                        "jobId" to jobId,
                        // R05-P2-03: the provider collects whole shillings only.
                        "amountUgx" to amountUgx?.let { com.example.util.CurrencyRegion.roundToWholeShillings(it) }?.toDouble(),
                        "phoneNumber" to phoneNumber?.takeIf { it.isNotBlank() }
                    )
                )
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
                .call(CallableContracts.payload("getMoMoCollectionStatus", "transactionRef" to transactionRef))
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
