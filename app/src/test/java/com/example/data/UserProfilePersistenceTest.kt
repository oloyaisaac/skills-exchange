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
            workerKycStatus = "APPROVED",
            isWorkerActivated = true,
            trainingCompleted = true,
            isOnboardingCompleted = true
        )
        db.userProfileDao().insertUserProfile(clientProfile)

        val currentUser = db.userProfileDao().getCurrentUser().first()
        assertEquals("CLIENT", currentUser?.initialRole)
        assertEquals("CLIENT", currentUser?.currentActiveMode)
        assertEquals("Client Neighbor", currentUser?.fullName)
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
}