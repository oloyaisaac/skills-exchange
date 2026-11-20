package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.UserProfileEntity
import com.example.data.remote.FirebaseService
import com.example.data.repository.SkillsExchangeRepository
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
 * C2-2 Fix #3 (audit finding **F7**): the legacy newest-row identity consumers are swept.
 *
 * The scenario under test is the exact failure the audit describes: the device holds two real
 * profiles, `uid_A` (the AUTHENTICATED account, older row) and `uid_B` (a different account, NEWER
 * row). `UserProfileDao.getCurrentUser()` — `ORDER BY updatedAtTimestamp DESC LIMIT 1` — answers
 * `uid_B`. Every security-sensitive operation must nevertheless use `uid_A`, and must fail closed
 * when Firebase has no authenticated identity at all.
 *
 * NOTE: `user_profiles` carries a UNIQUE index on `phoneNumber` and inserts use
 * `OnConflictStrategy.REPLACE`, so the two seeded rows must carry distinct phone numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LegacyCurrentUserSweepSecurityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SkillsExchangeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SkillsExchangeRepository(db, FirebaseService())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** The AUTHENTICATED account: a real worker row, but nothing is approved/activated yet. */
    private fun authenticatedProfile(
        id: String = "uid_A",
        phoneNumber: String = "+256 700 000 301",
        updatedAtTimestamp: Long = 1_000L
    ) = UserProfileEntity(
        id = id,
        phoneNumber = phoneNumber,
        fullName = "Authenticated Owner",
        district = "Nakawa",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "UNVERIFIED",
        isWorkerActivated = false,
        trainingCompleted = false,
        isOnboardingCompleted = true,
        updatedAtTimestamp = updatedAtTimestamp
    )

    /** A DIFFERENT account, fully admin-approved, and the NEWEST row on the device. */
    private fun newerFullyApprovedProfile(
        id: String = "uid_B",
        phoneNumber: String = "+256 700 000 302",
        updatedAtTimestamp: Long = 9_000L
    ) = UserProfileEntity(
        id = id,
        phoneNumber = phoneNumber,
        fullName = "Newer Other Account",
        district = "Sunnyside",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "APPROVED",
        isWorkerActivated = true,
        trainingCompleted = true,
        isOnboardingCompleted = true,
        updatedAtTimestamp = updatedAtTimestamp
    )

    private suspend fun seedAuthenticatedAndNewerForeignRows() {
        db.userProfileDao().insertUserProfile(authenticatedProfile())
        db.userProfileDao().insertUserProfile(newerFullyApprovedProfile())
    }

    @Test
    fun `security-sensitive identity resolves to the authenticated uid never the newer row`() =
        runBlocking {
            seedAuthenticatedAndNewerForeignRows()

            // Sanity: the legacy newest-row query answers with the OTHER account. This is exactly
            // why it must never be an identity source.
            assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)
            assertEquals("uid_B", repository.currentUser.first()?.id)

            // The migrated call sites resolve identity through the Firebase UID...
            val authoritativeUid = SkillsExchangeViewModel.resolveAuthoritativeUid("uid_A")
            assertEquals("uid_A", authoritativeUid)

            // ...and address the Room row by that UID (user-owned reads/writes, notifications,
            // disputes, ratings, reports, guarantors, training, quotes and proposals all do this).
            val profile = repository.getCurrentUserByUid(authoritativeUid).first()
            assertNotNull(profile)
            assertEquals("uid_A", profile?.id)
            assertEquals("Authenticated Owner", profile?.fullName)
        }

    @Test
    fun `the newest room row can never satisfy the worker gate for the authenticated account`() =
        runBlocking {
            seedAuthenticatedAndNewerForeignRows()

            // The newest row IS an approved, activated worker — so a newest-row gate would hand the
            // authenticated account privileges it never earned.
            val newestRow = db.userProfileDao().getCurrentUser().first()
            assertNotNull(newestRow)
            assertTrue(SkillsExchangeViewModel.isWorkerAuthorized(true, newestRow))

            // The UID-keyed row the gate actually reads (canAccessWorkerFeatures /
            // isWorkerApprovedAndActivated) fails closed.
            val authenticatedRow = repository
                .getCurrentUserByUid(SkillsExchangeViewModel.resolveAuthoritativeUid("uid_A"))
                .first()
            assertFalse(SkillsExchangeViewModel.isWorkerAuthorized(true, authenticatedRow))
        }

    @Test
    fun `missing firebase identity fails closed instead of using the newest room row`() =
        runBlocking {
            seedAuthenticatedAndNewerForeignRows()

            // No Firebase user, a blank/whitespace uid, or the legacy placeholder ⇒ no identity.
            assertNull(SkillsExchangeViewModel.resolveAuthoritativeUid(null))
            assertNull(SkillsExchangeViewModel.resolveAuthoritativeUid(""))
            assertNull(SkillsExchangeViewModel.resolveAuthoritativeUid("   "))
            assertNull(
                SkillsExchangeViewModel.resolveAuthoritativeUid(
                    SkillsExchangeViewModel.DEMO_PROFILE_ID
                )
            )

            // Nothing is handed out, and the gates stay closed — even though a real (approved)
            // profile row exists on the device.
            assertNull(repository.getCurrentUserByUid(null).first())
            assertNull(
                repository.getCurrentUserByUid(SkillsExchangeViewModel.DEMO_PROFILE_ID).first()
            )
            assertFalse(SkillsExchangeViewModel.isWorkerAuthorized(true, null))
            assertNotNull(db.userProfileDao().getCurrentUser().first())
        }

    @Test
    fun `firebase uid is trimmed and a placeholder is never an identity`() {
        assertEquals("uid_A", SkillsExchangeViewModel.resolveAuthoritativeUid("  uid_A  "))
        assertEquals("uid_A", SkillsExchangeRepository.resolveAuthoritativeUid("  uid_A  "))
        assertNull(SkillsExchangeRepository.resolveAuthoritativeUid(" \t "))
        assertNull(
            SkillsExchangeRepository.resolveAuthoritativeUid(
                SkillsExchangeViewModel.DEMO_PROFILE_ID
            )
        )
    }

    @Test
    fun `sos contact details come only from the authenticated row`() {
        val authenticatedRow = authenticatedProfile()
        val foreignRow = newerFullyApprovedProfile()

        // The authenticated account's own row is the only acceptable SOS payload.
        assertEquals(
            "uid_A",
            SkillsExchangeRepository.resolveSosContactProfile("uid_A", authenticatedRow)?.id
        )

        // Another account's row (the newest one on the device) is rejected...
        assertNull(SkillsExchangeRepository.resolveSosContactProfile("uid_A", foreignRow))
        // ...as are: no authenticated uid, no row at all, a row without a usable phone number,
        // and the legacy demo placeholder.
        assertNull(SkillsExchangeRepository.resolveSosContactProfile(null, authenticatedRow))
        assertNull(SkillsExchangeRepository.resolveSosContactProfile("uid_A", null))
        assertNull(
            SkillsExchangeRepository.resolveSosContactProfile(
                "uid_A",
                authenticatedRow.copy(phoneNumber = "   ")
            )
        )
        assertNull(
            SkillsExchangeRepository.resolveSosContactProfile(
                SkillsExchangeViewModel.DEMO_PROFILE_ID,
                authenticatedRow
            )
        )
    }
}
