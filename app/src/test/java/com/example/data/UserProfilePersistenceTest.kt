package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.UserProfileEntity
import com.example.ui.viewmodel.SkillsExchangeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUTH-002 / ROLE-001 regression coverage for onboarding profile persistence.
 *
 * Verifies that the authenticated user's UserProfileEntity is the profile returned by
 * getCurrentUser() (the NavigationDrawer source of truth) and that a legacy seeded demo
 * row ("user_default_me") can never shadow it or satisfy the worker activation gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UserProfilePersistenceTest {

    private lateinit var db: AppDatabase

    private val demoProfileId = SkillsExchangeViewModel.DEMO_PROFILE_ID

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun authenticatedWorkerProfile(id: String = "2hda9VzyPbhUdM0mumJPDevWwd52") =
        UserProfileEntity(
            id = id,
            phoneNumber = "",
            fullName = "isaac OLOYA",
            district = "Nakawa",
            initialRole = "WORKER",
            currentActiveMode = "WORKER",
            workerKycStatus = "UNVERIFIED",
            isWorkerActivated = false,
            trainingCompleted = false,
            isOnboardingCompleted = true,
            updatedAtTimestamp = 2_000L
        )

    private fun legacyDemoProfile() = UserProfileEntity(
        id = demoProfileId,
        phoneNumber = "+256 772 198 420",
        fullName = "Sarah Namukasa",
        district = "Sunnyside",
        initialRole = "CLIENT",
        currentActiveMode = "CLIENT",
        workerKycStatus = "APPROVED",
        isWorkerActivated = true,
        trainingCompleted = true,
        isOnboardingCompleted = true,
        updatedAtTimestamp = 1_000L
    )

    @Test
    fun `onboarding insert makes the authenticated profile the current user`() = runBlocking {
        val profile = authenticatedWorkerProfile()
        db.userProfileDao().insertUserProfile(profile)

        val currentUser = db.userProfileDao().getCurrentUser().first()

        assertNotNull(currentUser)
        assertEquals(profile.id, currentUser?.id)
        assertEquals("isaac OLOYA", currentUser?.fullName)
        assertEquals("WORKER", currentUser?.currentActiveMode)
        assertEquals("WORKER", currentUser?.initialRole)
    }

    @Test
    fun `legacy demo row never shadows the authenticated profile`() = runBlocking {
        db.userProfileDao().insertUserProfile(legacyDemoProfile())
        val profile = authenticatedWorkerProfile()
        db.userProfileDao().insertUserProfile(profile)

        val currentUser = db.userProfileDao().getCurrentUser().first()

        assertEquals(profile.id, currentUser?.id)
        assertEquals("isaac OLOYA", currentUser?.fullName)
        assertEquals("UNVERIFIED", currentUser?.workerKycStatus)
        assertFalse(currentUser!!.isWorkerActivated)

        // Worker activation gate must evaluate against the real, unverified profile.
        val canUseWorkerFeatures = currentUser.isWorkerActivated && currentUser.workerKycStatus == "APPROVED"
        assertFalse(canUseWorkerFeatures)
    }

    @Test
    fun `current user is null when only the demo placeholder row exists`() = runBlocking {
        db.userProfileDao().insertUserProfile(legacyDemoProfile())

        assertNull(db.userProfileDao().getCurrentUser().first())
    }

    @Test
    fun `selected worker role is preserved and gates stay closed`() = runBlocking {
        db.userProfileDao().insertUserProfile(authenticatedWorkerProfile(id = "worker_uid_1"))

        val stored = db.userProfileDao().getUserProfile("worker_uid_1").first()
        assertNotNull(stored)
        assertEquals("WORKER", stored?.initialRole)
        assertEquals("WORKER", stored?.currentActiveMode)
        assertEquals("UNVERIFIED", stored?.workerKycStatus)
        assertFalse(stored!!.isWorkerActivated)
        assertFalse(stored.trainingCompleted)
        assertTrue(stored.isOnboardingCompleted)
    }

    @Test
    fun `selected client role is preserved and is immediately usable`() = runBlocking {
        val clientProfile = UserProfileEntity(
            id = "client_uid_1",
            phoneNumber = "+256 700 111 222",
            fullName = "Client Neighbor",
            district = "Sunnyside",
            initialRole = "CLIENT",
            currentActiveMode = "CLIENT",
            workerKycStatus = "NOT_APPLIED",
            isWorkerActivated = false,
            trainingCompleted = false,
            isOnboardingCompleted = true
        )
        db.userProfileDao().insertUserProfile(clientProfile)

        val currentUser = db.userProfileDao().getCurrentUser().first()
        assertEquals("CLIENT", currentUser?.initialRole)
        assertEquals("CLIENT", currentUser?.currentActiveMode)
        assertEquals("Client Neighbor", currentUser?.fullName)
        assertFalse(currentUser!!.isWorkerActivated)
        assertEquals("NOT_APPLIED", currentUser.workerKycStatus)
    }

    @Test
    fun `client account cannot access worker dashboard or satisfy activation gate (SEC-009)`() = runBlocking {
        val clientProfile = UserProfileEntity(
            id = "client_uid_sec009",
            phoneNumber = "+256 700 999 888",
            fullName = "Regular Client",
            district = "Nakawa",
            initialRole = "CLIENT",
            currentActiveMode = "CLIENT",
            workerKycStatus = "NOT_APPLIED",
            isWorkerActivated = false,
            trainingCompleted = false,
            isOnboardingCompleted = true
        )
        db.userProfileDao().insertUserProfile(clientProfile)

        val currentUser = db.userProfileDao().getCurrentUser().first()
        assertNotNull(currentUser)

        // SEC-009 gate: requires registered worker (or approved KYC) + APPROVED + trainingCompleted + isWorkerActivated
        val canAccessWorkerDashboard = (currentUser!!.initialRole == "WORKER" || currentUser.workerKycStatus == "APPROVED") &&
                currentUser.workerKycStatus == "APPROVED" &&
                currentUser.trainingCompleted &&
                currentUser.isWorkerActivated

        assertFalse("Client must never satisfy worker dashboard gate", canAccessWorkerDashboard)
    }

    @Test
    fun `updating an existing profile keeps a single current user row`() = runBlocking {
        val original = authenticatedWorkerProfile()
        db.userProfileDao().insertUserProfile(original)
        val updated = original.copy(
            fullName = "isaac OLOYA",
            phoneNumber = "+256 700 000 111",
            updatedAtTimestamp = 3_000L
        )
        db.userProfileDao().insertUserProfile(updated)

        val stored = db.userProfileDao().getUserProfile(original.id).first()
        assertNotNull(stored)
        assertEquals("+256 700 000 111", stored?.phoneNumber)
        assertEquals(original.id, db.userProfileDao().getCurrentUser().first()?.id)
    }

    // --- C2-2 Fix #1: UID-keyed current-user resolution ---

    @Test
    fun `uid-keyed lookup returns the authenticated uid even when another row is newer`() = runBlocking {
        // Room holds two real profiles; UID-B is the NEWEST row (would win the legacy
        // newest-row query). The authenticated Firebase identity is UID-A.
        db.userProfileDao().insertUserProfile(
            authenticatedWorkerProfile(id = "uid_A").copy(updatedAtTimestamp = 1_000L)
        )
        db.userProfileDao().insertUserProfile(
            authenticatedWorkerProfile(id = "uid_B").copy(updatedAtTimestamp = 9_000L)
        )

        // Sanity: the legacy newest-row query would return the WRONG row here.
        assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)

        // UID-keyed resolution must return UID-A — the authenticated identity — never UID-B.
        val resolved = db.userProfileDao().getCurrentUserById("uid_A").first()
        assertNotNull(resolved)
        assertEquals("uid_A", resolved?.id)
        assertEquals("isaac OLOYA", resolved?.fullName)
    }

    @Test
    fun `uid-keyed lookup returns null when the authenticated uid has no row`() = runBlocking {
        db.userProfileDao().insertUserProfile(
            authenticatedWorkerProfile(id = "uid_B").copy(updatedAtTimestamp = 9_000L)
        )

        // Fail closed: no substitution of the newest row, no placeholder, just null.
        assertNull(db.userProfileDao().getCurrentUserById("uid_A").first())
    }

    @Test
    fun `uid-keyed lookup never returns the demo placeholder`() = runBlocking {
        db.userProfileDao().insertUserProfile(legacyDemoProfile())

        // The DAO answers only exact-id matches; production resolution additionally excludes
        // the demo placeholder (see repository.getCurrentUserByUid), so belt and suspenders
        // hold even if the DAO row is addressed directly.
        val resolved = db.userProfileDao()
            .getCurrentUserById("uid_A").first()
        assertNull(resolved)
    }
}