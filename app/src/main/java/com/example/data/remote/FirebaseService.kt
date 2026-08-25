package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
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
                "momoPhoneNumber" to job.momoPhoneNumber
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
}
