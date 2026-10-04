package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.UserProfileEntity
import com.example.data.remote.FirebaseService
import com.example.data.remote.SmsNotificationService
import com.example.data.repository.SkillsExchangeRepository
import com.example.ui.viewmodel.SkillsExchangeViewModel
import com.example.ui.viewmodel.SkillsExchangeViewModel.RestoreIdentity
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

    // NOTE: `user_profiles` carries a UNIQUE index on `phoneNumber` and inserts use
    // OnConflictStrategy.REPLACE. Any test that wants two profiles to coexist MUST give them
    // distinct phone numbers, otherwise the second insert deletes the first row.
    private fun authenticatedWorkerProfile(
        id: String = "2hda9VzyPbhUdM0mumJPDevWwd52",
        phoneNumber: String = "+256 700 000 001",
        fullName: String = "isaac OLOYA",
        updatedAtTimestamp: Long = 2_000L
    ) =
        UserProfileEntity(
            id = id,
            phoneNumber = phoneNumber,
            fullName = fullName,
            district = "Nakawa",
            initialRole = "WORKER",
            currentActiveMode = "WORKER",
            workerKycStatus = "UNVERIFIED",
            isWorkerActivated = false,
            trainingCompleted = false,
            isOnboardingCompleted = true,
            updatedAtTimestamp = updatedAtTimestamp
        )

    private fun legacyDemoProfile(phoneNumber: String = "+256 772 198 420") = UserProfileEntity(
        id = demoProfileId,
        phoneNumber = phoneNumber,
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
        // Same account (same id, same phone): only the descriptive data and timestamp move on.
        // Distinct phone numbers are NOT used here on purpose — `phoneNumber` is uniquely
        // indexed and inserts use REPLACE, so a changed number would be a *different* account.
        val updated = authenticatedWorkerProfile(
            fullName = "isaac OLOYA Revised",
            updatedAtTimestamp = 3_000L
        )
        db.userProfileDao().insertUserProfile(updated)

        val stored = db.userProfileDao().getUserProfile(original.id).first()
        assertNotNull(stored)
        assertEquals(original.phoneNumber, stored?.phoneNumber)
        assertEquals("isaac OLOYA Revised", stored?.fullName)
        assertEquals(original.id, db.userProfileDao().getCurrentUser().first()?.id)
    }

    // --- C2-2 Fix #1: UID-keyed current-user resolution ---

    @Test
    fun `uid-keyed lookup returns the authenticated uid even when another row is newer`() = runBlocking {
        // Room holds two real profiles; UID-B is the NEWEST row (would win the legacy
        // newest-row query). The authenticated Firebase identity is UID-A.
        // Distinct phone numbers are required: `phoneNumber` is uniquely indexed and inserts
        // use REPLACE, so a shared number would delete the sibling row instead.
        db.userProfileDao().insertUserProfile(
            authenticatedWorkerProfile(
                id = "uid_A",
                phoneNumber = "+256 700 000 101",
                updatedAtTimestamp = 1_000L
            )
        )
        db.userProfileDao().insertUserProfile(
            authenticatedWorkerProfile(
                id = "uid_B",
                phoneNumber = "+256 700 000 102",
                updatedAtTimestamp = 9_000L
            )
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
            authenticatedWorkerProfile(
                id = "uid_B",
                phoneNumber = "+256 700 000 102",
                updatedAtTimestamp = 9_000L
            )
        )

        // Fail closed: no substitution of the newest row, no placeholder, just null.
        assertNull(db.userProfileDao().getCurrentUserById("uid_A").first())
    }

    @Test
    fun `uid-keyed lookup never returns the demo placeholder`() = runBlocking {
        db.userProfileDao().insertUserProfile(legacyDemoProfile())

        // The DAO answers only exact-id matches, so addressing a real UID must not hand back the
        // demo row; production resolution additionally excludes the placeholder by id
        // (see repository.getCurrentUserByUid), so belt and suspenders hold both ways.
        val resolved = db.userProfileDao()
            .getCurrentUserById("uid_A").first()
        assertNull(resolved)

        // ...and the demo row is not reachable by "resolving whatever is newest" either.
        assertNull(db.userProfileDao().getCurrentUser().first())
    }
}

/**
 * C2-2 Fix #1 at the repository boundary — the layer that actually gates identity.
 *
 * The DAO only answers exact-id queries; `SkillsExchangeRepository.getCurrentUserByUid` adds the
 * fail-closed rules: no UID, a blank UID, or the legacy demo id must yield `null` rather than
 * promoting "whatever row happens to be newest" to the authenticated identity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CurrentUserByUidRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SkillsExchangeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SkillsExchangeRepository(db, FirebaseService(), SmsNotificationService())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seedRealAndNewestRows() {
        db.userProfileDao().insertUserProfile(
            UserProfileEntity(
                id = "uid_A",
                phoneNumber = "+256 700 000 201",
                fullName = "isaac OLOYA",
                initialRole = "WORKER",
                currentActiveMode = "WORKER",
                workerKycStatus = "UNVERIFIED",
                isOnboardingCompleted = true,
                updatedAtTimestamp = 1_000L
            )
        )
        db.userProfileDao().insertUserProfile(
            UserProfileEntity(
                id = "uid_B",
                phoneNumber = "+256 700 000 202",
                fullName = "Newer Other Account",
                initialRole = "CLIENT",
                currentActiveMode = "CLIENT",
                workerKycStatus = "NOT_APPLIED",
                isOnboardingCompleted = true,
                updatedAtTimestamp = 9_000L
            )
        )
    }

    @Test
    fun `resolves the authenticated uid and ignores the newer row`() = runBlocking {
        seedRealAndNewestRows()

        val resolved = repository.getCurrentUserByUid("uid_A").first()

        assertNotNull(resolved)
        assertEquals("uid_A", resolved?.id)
        assertEquals("isaac OLOYA", resolved?.fullName)
    }

    @Test
    fun `fails closed when the uid has no matching row`() = runBlocking {
        seedRealAndNewestRows()

        assertNull(repository.getCurrentUserByUid("uid_MISSING").first())
    }

    @Test
    fun `fails closed for null blank and legacy demo uids`() = runBlocking {
        seedRealAndNewestRows()

        assertNull(repository.getCurrentUserByUid(null).first())
        assertNull(repository.getCurrentUserByUid("").first())
        assertNull(repository.getCurrentUserByUid("   ").first())
        assertNull(
            repository.getCurrentUserByUid(SkillsExchangeViewModel.DEMO_PROFILE_ID).first()
        )
    }

    /**
     * C2-2 Fix #2 (F2) at the repository boundary: when DataStore still caches the UID of a
     * previous account, only the Firebase UID may be resolved — never the stale cached one.
     */
    @Test
    fun `stale cached uid never wins over the authenticated firebase uid`() = runBlocking {
        seedRealAndNewestRows()

        val identity = SkillsExchangeViewModel.decideRestoreIdentity(
            cachedUid = "uid_A",
            firebaseUid = "uid_B"
        )
        val authenticatedUid = (identity as? RestoreIdentity.Authoritative)?.uid

        assertEquals("uid_B", authenticatedUid)
        assertFalse((identity as RestoreIdentity.Authoritative).cachedUidTrusted)
        // The stale account's row is still in Room, addressing it by the authenticated UID
        // must not return it...
        assertNotNull(repository.getCurrentUserByUid("uid_A").first())
        val resolved = repository.getCurrentUserByUid(authenticatedUid).first()
        assertEquals("uid_B", resolved?.id)
        assertEquals("Newer Other Account", resolved?.fullName)
    }

    /**
     * C2-2 Fix #2 (F2) Test E: a database holding only the seeded placeholder must never hand out
     * an authenticated identity, whatever UID the restore path asks for.
     */
    @Test
    fun `a database holding only the demo placeholder yields no identity`() = runBlocking {
        db.userProfileDao().insertUserProfile(
            UserProfileEntity(
                id = SkillsExchangeViewModel.DEMO_PROFILE_ID,
                phoneNumber = "+256 700 000 203",
                fullName = "Sarah Namukasa",
                district = "Sunnyside",
                initialRole = "CLIENT",
                currentActiveMode = "CLIENT",
                workerKycStatus = "APPROVED",
                isWorkerActivated = true,
                trainingCompleted = true,
                isOnboardingCompleted = true,
                updatedAtTimestamp = 9_000L
            )
        )

        // Neither a real UID nor the placeholder's own id may resolve to the seeded row...
        assertNull(repository.getCurrentUserByUid("uid_A").first())
        assertNull(repository.getCurrentUserByUid(SkillsExchangeViewModel.DEMO_PROFILE_ID).first())
        // ...and the legacy newest-row query refuses it too, so no restore path can adopt it.
        assertNull(db.userProfileDao().getCurrentUser().first())
    }
}