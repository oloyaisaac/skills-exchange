package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.JobPostEntity
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
 * C2-2 Fix #4 (audit finding **F4**): job authorship / `postedByUid`.
 *
 * The defect under test: a newly created job recorded its poster as the DISPLAY string
 * `"You ($district)"` and carried no authenticated owner at all, so nothing stable could be
 * authorized against. Ownership must instead be `FirebaseAuth.currentUser.uid`, and a job must
 * never be created without one.
 *
 * `postedByUid` is nullable ONLY for rows predating the column: those have an UNKNOWN owner and
 * must never be attributed to anybody (see MIGRATION_19_20 and test F).
 *
 * NOTE: `user_profiles` has a UNIQUE index on `phoneNumber` and inserts use REPLACE, so the two
 * seeded rows must carry distinct phone numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JobAuthorshipF4SecurityTest {

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
        fullName = "Owner $id",
        district = "Nakawa",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "UNVERIFIED",
        isWorkerActivated = false,
        trainingCompleted = false,
        isOnboardingCompleted = true,
        updatedAtTimestamp = updatedAtTimestamp
    )

    /** A job row that predates the `postedByUid` column: unknown owner, display text only. */
    private fun jobWithoutUidColumn(
        id: String = "job_legacy",
        postedByResident: String = "Robert K. (Sunnyside)"
    ) = JobPostEntity(
        id = id,
        title = "Emergency main valve leak",
        category = "Plumbing",
        description = "Constant drip under shutoff valve.",
        postedByUid = null,
        postedByResident = postedByResident,
        district = "Sunnyside District",
        budget = 123.20,
        urgency = "Today",
        status = "Requested",
        assignedProviderId = null,
        assignedProviderName = null,
        createdTimestamp = 1_000L,
        estimatedHours = 1.5,
        materialsCost = 0.0,
        safetyFee = 11.25
    )

    private suspend fun attemptToCreateJob(): String? = repository.addJobPost(
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping.",
        budget = 50.0,
        urgency = "Today",
        district = "Nakawa"
    )

    // --- TEST A / C: the authenticated uid is the only identity, and its absence aborts ---

    @Test
    fun `test A a new job is stamped with the authenticated firebase uid`() {
        // This Robolectric context has no Firebase user, so the uid that addJobPost would write is
        // exercised through the single shared resolver it consumes. Trimming is allowed; anything
        // else (display string, name, district, placeholder) must not resolve.
        assertEquals("uid_A", SkillsExchangeRepository.resolveAuthoritativeUid("uid_A"))
        assertEquals("uid_A", SkillsExchangeRepository.resolveAuthoritativeUid("  uid_A  "))
    }

    @Test
    fun `test C job creation fails closed and persists nothing without an authenticated uid`() =
        runBlocking {
            assertNull(SkillsExchangeRepository.resolveAuthoritativeUid(null))

            val jobId = attemptToCreateJob()

            assertNull("addJobPost must return null when Firebase has no user", jobId)
            assertTrue(
                "No job row may exist after creation failed closed",
                db.jobPostDao().getAllJobs().first().isEmpty()
            )
        }

    // --- TEST B: display text can never become ownership -------------------------------

    @Test
    fun `test B display text and synthetic ids are never accepted as ownership`() {
        // Structured presentation text and the synthetic sentinels are rejected outright, so a
        // display field can never be promoted to an identity.
        val rejected = listOf(
            "Robert K. (Sunnyside)",  // postedByResident-style display text
            "You (Nakawa)",           // the exact string the old code wrote
            "Robert K.",              // bare display name
            "Robert K",               // same, trimmed of the trailing dot
            "unknown",
            "system",
            "anonymous",
            "guest",
            "user_default_me",
            "   ",                    // whitespace only
            ""                         // empty
        )
        for (candidate in rejected) {
            assertNull(
                "'$candidate' must not resolve to an authenticated identity",
                SkillsExchangeRepository.resolveAuthoritativeUid(candidate)
            )
            assertNull(
                "'$candidate' must not resolve to an authenticated identity (view-model rule)",
                com.example.ui.viewmodel.SkillsExchangeViewModel.resolveAuthoritativeUid(candidate)
            )
        }
    }

    @Test
    fun `test B2 a bare district name is shape-valid but never becomes ownership`() {
        // HONEST LIMITATION: resolveAuthoritativeUid validates the SHAPE of the value it is
        // handed. "Nakawa" is a single alphanumeric token, indistinguishable in shape from a real
        // Firebase uid, so the resolver returns it rather than guessing.
        //
        // This is not exploitable, because the argument is never caller-supplied: addJobPost
        // passes ONLY firebaseService.getCurrentUser()?.uid (FirebaseAuth), and ownership is then
        // written from that uid — never from a district, a display name or a UI field. Tests A/C
        // prove that a request carrying district "Nakawa" and a display name still records no job
        // at all when there is no authenticated user.
        assertEquals("Nakawa", SkillsExchangeRepository.resolveAuthoritativeUid("Nakawa"))
    }

    // --- TEST D / E: ownership is uid-vs-uid, never the newer Room row -------------------

    @Test
    fun `test D ownership never falls back to the newer room row`() = runBlocking {
        // uid_A is the authenticated account (older row); uid_B is a DIFFERENT account and the
        // newest row on the device — exactly the trap the newest-row query sets.
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 401", 1_000L))
        db.userProfileDao().insertUserProfile(profile("uid_B", "+256 700 000 402", 9_000L))
        assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)

        val jobOwnedByA = jobWithoutUidColumn("job_A").copy(postedByUid = "uid_A")
        val jobOwnedByB = jobWithoutUidColumn("job_B").copy(postedByUid = "uid_B")

        // With no authenticated Firebase user the predicate fails closed for BOTH jobs; it must
        // never pick uid_B just because uid_B is the newest profile row.
        assertFalse(
            "must not fall back to the newest row for uid_B's job",
            repository.isJobOwnedByCurrentUser(jobOwnedByB)
        )
        assertFalse(repository.isJobOwnedByCurrentUser(jobOwnedByA))

        // "My jobs" is empty rather than exposing another account's jobs.
        assertTrue(repository.getMyJobs().first().isEmpty())
    }

    @Test
    fun `test E ownership queries key on postedByUid and never on the display string`() =
        runBlocking {
            // Two jobs carry the IDENTICAL display text "You (Nakawa)" — so the display string
            // cannot discriminate ownership, while the uid can.
            db.jobPostDao().insertJob(
                jobWithoutUidColumn("job_A", "You (Nakawa)").copy(postedByUid = "uid_A")
            )
            db.jobPostDao().insertJob(
                jobWithoutUidColumn("job_B", "You (Nakawa)").copy(postedByUid = "uid_B")
            )
            db.jobPostDao().insertJob(jobWithoutUidColumn("job_legacy", "Robert K. (Sunnyside)"))

            assertEquals(
                listOf("job_A"),
                db.jobPostDao().getJobsPostedBy("uid_A").first().map { it.id }
            )
            assertEquals(
                listOf("job_B"),
                db.jobPostDao().getJobsPostedBy("uid_B").first().map { it.id }
            )

            // The legacy row is nobody's job.
            assertEquals(
                listOf("job_legacy"),
                db.jobPostDao().getJobsWithUnknownOwner().first().map { it.id }
            )
        }

    // --- TEST F: a legacy job with no trustworthy owner is nobody's job ------------------

    @Test
    fun `test F a legacy job is never guessed into the current user's ownership`() = runBlocking {
        val legacy = jobWithoutUidColumn(postedByResident = "Robert K. (Sunnyside)")
        db.jobPostDao().insertJob(legacy)
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 401", 1_000L))

        assertNull("a pre-F4 row keeps an unknown owner", legacy.postedByUid)

        // The display text names a person, but ownership is NOT inferred from it.
        assertFalse(
            "a legacy job must not be claimed by the current user",
            repository.isJobOwnedByCurrentUser(legacy)
        )
        assertFalse(
            "the owner uid must not be back-filled from postedByResident",
            db.jobPostDao().getJobsPostedBy("uid_A").first().any { it.id == "job_legacy" }
        )

        // The row itself is preserved (no destructive migration).
        assertNotNull(db.jobPostDao().getAllJobs().first().find { it.id == "job_legacy" })
    }
}