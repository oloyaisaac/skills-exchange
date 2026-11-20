package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.JobPostEntity
import com.example.data.db.JobStatusAuditEntity
import com.example.data.db.UserProfileEntity
import com.example.data.remote.FirebaseService
import com.example.data.repository.SkillsExchangeRepository
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
 * C2-2 Fix #6 (audit finding **F6**): job-status attribution.
 *
 * The defect under test: `updateJobStatus()` derived the audit actor from the legacy newest-Room-row
 * `currentUser` flow (`repository.currentUser.first()?.id ?: "system"`), so on a device that had held
 * another account the status change was attributed to the WRONG person — or to a fabricated
 * `"system"` when there was no profile at all.
 *
 * `changedBy` must come from `FirebaseAuth.currentUser.uid`, it must be resolved inside the
 * repository (never supplied by a caller), and with no authenticated uid nothing may be written.
 *
 * This Robolectric context has no Firebase user, so the uid-bearing assertions run through the same
 * shared resolver the repository consumes, while the fail-closed and audit-integrity assertions run
 * against the real database.
 *
 * NOTE: `user_profiles` has a UNIQUE index on `phoneNumber`, so seeded rows need distinct numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JobStatusAttributionF6SecurityTest {

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

    private fun profile(id: String, phoneNumber: String, updatedAtTimestamp: Long) = UserProfileEntity(
        id = id,
        phoneNumber = phoneNumber,
        fullName = "Person $id",
        district = "Nakawa",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "UNVERIFIED",
        isWorkerActivated = false,
        trainingCompleted = false,
        isOnboardingCompleted = true,
        updatedAtTimestamp = updatedAtTimestamp
    )

    private fun seedJob(status: String = "Requested") = JobPostEntity(
        id = "job_1",
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping.",
        postedByUid = "uid_A",
        postedByResident = "You (Nakawa)",
        district = "Nakawa",
        budget = 50.0,
        urgency = "Today",
        status = status,
        assignedProviderId = null,
        assignedProviderName = null,
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0
    )

    // --- TEST A: the actor is the authenticated uid, never a display name or sentinel ---------

    @Test
    fun `test A the actor is the authenticated firebase uid`() {
        // The uid transitionJobStatus writes is produced by the single shared resolver it calls.
        assertEquals("uid_A", SkillsExchangeRepository.resolveAuthoritativeUid("uid_A"))
        // Fabricated / placeholder actors must never resolve to an identity. NOTE: the contract is
        // scoped to what is decidable WITHOUT asking Firebase — literal synthetic sentinels, the
        // legacy demo id, and presentation text (whitespace or brackets, e.g. a real name or
        // "You (Nakawa)"). A bare single token such as "Nakawa" is deliberately NOT rejected:
        // Firebase UIDs are opaque alphanumeric strings, so shape alone cannot separate one from a
        // real uid, and inventing a district-name blocklist would be guesswork. F6 does not widen
        // that contract — it only guarantees the value comes from FirebaseAuth rather than a row.
        for (notAnIdentity in listOf("system", "SYSTEM", "unknown", "anonymous", "guest", "none",
                "John Doe", "Robert K. (Sunnyside)", "You (Nakawa)", "user_default_me", "", "   ",
                "  uid_A  ")) {
            val resolved = SkillsExchangeRepository.resolveAuthoritativeUid(notAnIdentity)
            if (notAnIdentity == "  uid_A  ") {
                // Padded but genuine: trimming is the one transformation that is allowed.
                assertEquals("uid_A", resolved)
            } else {
                assertNull("'$notAnIdentity' must not resolve to an authenticated identity", resolved)
            }
        }
    }

    // --- TEST B: the newer Room profile must never become the actor -----------------------

    @Test
    fun `test B the newer room profile never becomes the actor`() = runBlocking {
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", 1_000L))
        db.userProfileDao().insertUserProfile(profile("uid_B", "+256 700 000 602", 9_000L))
        db.jobPostDao().insertJob(seedJob())

        // Sanity: the legacy newest-row query answers with the OTHER account — precisely why it
        // must never be an identity source.
        assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)
        assertEquals("uid_B", repository.currentUser.first()?.id)

        // Unauthenticated, the transition fails closed even though uid_B is the newest row.
        val ok = repository.transitionJobStatus("job_1", "Requested", "In Progress")

        assertFalse("must fail closed without an authenticated uid", ok)
        assertTrue(
            "no audit entry may claim uid_B (or anyone) while unauthenticated",
            repository.getJobAuditTrail("job_1").isEmpty()
        )
        // The job status itself must not have moved either.
        assertEquals(
            "Requested",
            db.jobPostDao().getAllJobs().first().first { it.id == "job_1" }.status
        )
    }

    // --- TEST C: no Firebase authentication => no "system"/"unknown" audit record ---------

    @Test
    fun `test C no authenticated user writes no audit record`() = runBlocking {
        // No user_profiles row at all: this is where the old code produced "system".
        db.jobPostDao().insertJob(seedJob())

        val ok = repository.transitionJobStatus("job_1", "Requested", "In Progress")

        assertFalse(ok)
        val trail = repository.getJobAuditTrail("job_1")
        assertTrue("no audit entry may be written", trail.isEmpty())
        assertFalse(
            "no fabricated actor may be stored",
            trail.any { it.changedBy == "system" || it.changedBy == "unknown" }
        )
    }

    // --- TEST D: invalid / placeholder uid also fails closed ------------------------------

    @Test
    fun `test D an invalid firebase uid fails closed`() = runBlocking {
        for (invalid in listOf(null, "", "   ", "user_default_me", "system", "unknown")) {
            assertNull(
                "invalid uid '$invalid' must not resolve",
                SkillsExchangeRepository.resolveAuthoritativeUid(invalid)
            )
        }
        db.jobPostDao().insertJob(seedJob())
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", 1_000L))

        assertFalse(repository.transitionJobStatus("job_1", "Requested", "In Progress"))
        assertTrue("no fabricated actor may be written", repository.getJobAuditTrail("job_1").isEmpty())
    }

    // --- TEST E: legacy historical audit rows are immutable ----------------------------------

    @Test
    fun `test E historical legacy audit records are never rewritten`() = runBlocking {
        db.jobPostDao().insertJob(seedJob(status = "In Progress"))

        // A pre-F6 row recorded by the old code, attributed to the fabricated "system".
        val legacy = JobStatusAuditEntity(
            id = "audit_legacy",
            jobId = "job_1",
            fromStatus = "Requested",
            toStatus = "In Progress",
            changedBy = "system",
            changedAt = 1_000L,
            reason = "historical"
        )
        db.jobStatusAuditDao().insertAuditEntry(legacy)

        // uid_A is the only profile on the device, so an implementation that back-filled legacy rows
        // from the current user would rewrite "system" -> uid_A here.
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", 1_000L))

        val trail = repository.getJobAuditTrail("job_1")

        assertEquals("the historical row must survive untouched", 1, trail.size)
        assertEquals("historical data must not be rewritten to the current user", "system", trail[0].changedBy)
        assertEquals("historical", trail[0].reason)
        assertNotNull(trail.find { it.id == "audit_legacy" })
    }

    // --- TEST F: a display name never becomes the actor in a NEW record --------------------

    @Test
    fun `test F a display name is never stored as the actor`() = runBlocking {
        db.jobPostDao().insertJob(seedJob())
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", 1_000L))

        // The reason carries a human name; while unauthenticated nothing is recorded at all, so no
        // display string can be captured as `changedBy`.
        assertFalse(repository.transitionJobStatus("job_1", "Requested", "In Progress", reason = "John Doe"))
        assertTrue(repository.getJobAuditTrail("job_1").isEmpty())

        // A historical row that already holds a display name is left alone — it is data, not
        // something to correct by guessing.
        db.jobStatusAuditDao().insertAuditEntry(
            JobStatusAuditEntity(
                id = "audit_display",
                jobId = "job_1",
                fromStatus = "Requested",
                toStatus = "Accepted",
                changedBy = "John Doe",
                changedAt = 2_000L
            )
        )
        assertEquals(
            "John Doe",
            repository.getJobAuditTrail("job_1").first { it.id == "audit_display" }.changedBy
        )
    }

    // --- Transition-rule ordering: identity is checked before any state change -------------

    @Test
    fun `test G an invalid transition is still rejected while unauthenticated`() = runBlocking {
        db.jobPostDao().insertJob(seedJob())

        // Requested -> Completed is not a legal edge; the transition must be refused and must not
        // write an audit row regardless of the identity check.
        val ok = repository.transitionJobStatus("job_1", "Requested", "Completed")

        assertFalse(ok)
        assertTrue(repository.getJobAuditTrail("job_1").isEmpty())
        assertEquals("Requested", db.jobPostDao().getAllJobs().first().first { it.id == "job_1" }.status)
    }
}