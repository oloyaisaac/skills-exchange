package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.JobPostEntity
import com.example.data.db.JobStatus
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.UserProfileEntity
import com.example.data.remote.FirebaseService
import com.example.data.remote.SmsNotificationService
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
 * C2-2 job-status **AUTHORIZATION** (the layer that F6 -- attribution -- did not provide).
 *
 * The defect under test: `transitionJobStatus()` checked only (a) that a Firebase user existed and
 * (b) that `JobStatus.isValidTransition()` allowed the move. It never asked whether the authenticated
 * actor had any relationship to the job, so **any signed-in user could move any other user's job**
 * along a structurally legal transition. Identity (who) and authorization (may they) were conflated:
 * resolving the actor correctly made the change look legitimate, because it was recorded honestly
 * against the wrong person.
 *
 * The policy under test is `SkillsExchangeRepository.isJobTransitionAuthorized`, reached in
 * production through `canCurrentUserTransitionJob` / `transitionJobStatus`. Worker- and admin-authorized
 * transitions fail closed on the client because neither identity exists there: `assignedProviderId` is
 * a `ServiceProviderEntity` FK ("prov_1"), not a Firebase UID, and admin is only the Firestore custom
 * claim `request.auth.token.admin`. That is deliberate -- see DECISIONS.md D-2026-10-05-2.
 *
 * This Robolectric context has no Firebase user, so uid-bearing authorization is driven through the
 * pure policy function using the shared `resolveAuthoritativeUid`, while the persistence/fail-closed
 * assertions run against the real database exactly as the production boundary sees them.
 *
 * NOTE: `user_profiles` carries a UNIQUE index on `phoneNumber`, so seeded rows need distinct numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JobStatusAuthorizationSecurityTest {

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

    private fun job(
        status: String = JobStatus.POSTED.value,
        postedByUid: String? = "uid_A",
        assignedProviderId: String? = null,
        assignedProviderName: String? = null
    ) = JobPostEntity(
        id = "job_1",
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping.",
        postedByUid = postedByUid,
        postedByResident = "You (Nakawa)",
        district = "Nakawa",
        budget = 50.0,
        urgency = "Today",
        status = status,
        assignedProviderId = assignedProviderId,
        assignedProviderName = assignedProviderName,
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0
    )

    // `job_posts.assignedProviderId` is a REAL foreign key onto `service_providers(id)`, so any test
    // that stores a job with an assignment must seed the provider row first or Room throws
    // SQLiteConstraintException. This is exactly why `assignedProviderId` is a provider FK and not a
    // Firebase uid -- the two are different namespaces and must never be compared.
    private fun provider(id: String) = ServiceProviderEntity(
        id = id,
        name = "Elena Rostova",
        tagline = "Plumbing, on time",
        category = "Plumbing",
        neighborhood = "Nakawa",
        residenceYears = 3,
        hourlyRate = 45.0,
        distanceMiles = 1.2,
        rating = 4.8,
        completedJobsCount = 27,
        avgResponseTime = "10 min",
        isVerifiedResident = true,
        isBackgroundChecked = true,
        bio = "Plumber.",
        skillsCsv = "Plumbing",
        latitude = 0.3476,
        longitude = 32.5825,
        endorsementsCount = 12
    )

    private suspend fun storedStatus(): String? = db.jobPostDao().getJobById("job_1")?.status
    private suspend fun auditCount(): Int = db.jobStatusAuditDao().getAuditTrailForJob("job_1").size

    // --- TEST A: the owner may perform an authorized client transition ---------------------

    @Test
    fun `test A the owner may perform an authorized client transition`() = runBlocking {
        db.jobPostDao().insertJob(job(status = JobStatus.POSTED.value))

        // The real production entry point, with the real actor resolution (no Firebase user here,
        // so it fails closed at the identity step -- proven separately in Test F).
        val ok = repository.transitionJobStatus("job_1", JobStatus.POSTED.value, JobStatus.QUOTING.value)

        // With no Firebase user the boundary must refuse. The owner-succeeds case is proven through
        // the pure policy function, which is the identical decision the boundary makes.
        assertFalse("no Firebase user must fail closed", ok)
        assertEquals(JobStatus.POSTED.value, storedStatus())
        assertEquals("no audit row may be written", 0, auditCount())

        val owner = SkillsExchangeRepository.resolveAuthoritativeUid("uid_A")
        assertTrue(
            "the owner must be authorized to move a POSTED job to QUOTING",
            repository.isJobTransitionAuthorized(owner, job(status = JobStatus.POSTED.value), JobStatus.QUOTING)
        )
        assertTrue(
            "the owner may select a worker",
            repository.isJobTransitionAuthorized(owner, job(status = JobStatus.QUOTING.value), JobStatus.WORKER_SELECTED)
        )
        assertTrue(
            "the owner may cancel their own live job",
            repository.isJobTransitionAuthorized(owner, job(status = JobStatus.ACCEPTED.value), JobStatus.CANCELLED)
        )
        assertTrue(
            "the owner may release escrow once the work is complete",
            repository.isJobTransitionAuthorized(owner, job(status = JobStatus.COMPLETED.value), JobStatus.PAYMENT_RELEASED)
        )
    }

    // --- TEST B: another authenticated client may not touch the job ----------------------

    @Test
    fun `test B another authenticated client cannot modify the job`() = runBlocking {
        db.jobPostDao().insertJob(job(status = JobStatus.POSTED.value, postedByUid = "uid_A"))

        val attacker = SkillsExchangeRepository.resolveAuthoritativeUid("uid_B")
        assertFalse(
            "a different authenticated user must not be authorized",
            repository.isJobTransitionAuthorized(
                attacker, job(status = JobStatus.POSTED.value, postedByUid = "uid_A"), JobStatus.QUOTING
            )
        )
        assertFalse(
            "a different authenticated user must not be able to cancel the job",
            repository.isJobTransitionAuthorized(
                attacker, job(status = JobStatus.ACCEPTED.value, postedByUid = "uid_A"), JobStatus.CANCELLED
            )
        )

        // And through the real boundary: a rejected attempt mutates nothing and audits nothing.
        val ok = repository.transitionJobStatus("job_1", JobStatus.POSTED.value, JobStatus.QUOTING.value)
        assertFalse(ok)
        assertEquals(JobStatus.POSTED.value, storedStatus())
        assertEquals(0, auditCount())
    }

    // --- TEST C: worker-authorized transitions fail closed (no worker uid exists) --------

    @Test
    fun `test C worker transitions fail closed because no worker uid exists`() {
        val owner = SkillsExchangeRepository.resolveAuthoritativeUid("uid_A")
        // assignedProviderId is a ServiceProviderEntity FK ("prov_1"), NOT a Firebase uid, so there
        // is no value that can be compared to the authenticated uid. The policy therefore refuses.
        val assigned = job(
            status = JobStatus.ACCEPTED.value,
            assignedProviderId = "prov_1",
            assignedProviderName = "Elena Rostova"
        )

        for (workerStatus in listOf(JobStatus.WORKER_EN_ROUTE, JobStatus.ARRIVED, JobStatus.IN_PROGRESS)) {
            assertFalse(
                "owner must not be able to fake worker progress ($workerStatus)",
                repository.isJobTransitionAuthorized(owner, assigned, workerStatus)
            )
        }
        assertFalse(
            "worker progress must fail closed for everyone on the client",
            repository.isJobTransitionAuthorized("prov_1", assigned, JobStatus.WORKER_EN_ROUTE)
        )
        // Even an authenticated worker whose uid happens to equal the provider FK cannot be trusted,
        // because that column is not an identity column -- documented in DECISIONS.md.
        assertFalse(
            "the provider FK is not a Firebase uid, so it must never authorize",
            repository.isJobTransitionAuthorized("prov_1", assigned, JobStatus.WORKER_EN_ROUTE)
        )
    }

    // --- TEST D: unassigned / unrelated workers cannot modify another job ----------------

    @Test
    fun `test D an unrelated worker cannot modify another users job`() = runBlocking {
        db.serviceProviderDao().insertProvider(provider("prov_1"))
        db.jobPostDao().insertJob(
            job(status = JobStatus.IN_PROGRESS.value, postedByUid = "uid_A", assignedProviderId = "prov_1")
        )

        for (stranger in listOf("uid_B", "uid_worker_9", "prov_2", null, "")) {
            val actor = SkillsExchangeRepository.resolveAuthoritativeUid(stranger)
            assertFalse(
                "'$stranger' must not be authorized to complete another user's job",
                repository.isJobTransitionAuthorized(
                    actor, job(status = JobStatus.IN_PROGRESS.value, postedByUid = "uid_A", assignedProviderId = "prov_1"),
                    JobStatus.COMPLETED
                )
            )
        }

        val ok = repository.transitionJobStatus("job_1", JobStatus.IN_PROGRESS.value, JobStatus.COMPLETED.value)
        assertFalse(ok)
        assertEquals(JobStatus.IN_PROGRESS.value, storedStatus())
        assertEquals(0, auditCount())
    }

    // --- TEST E: an authenticated user cannot exploit a STRUCTURALLY VALID transition -------

    @Test
    fun `test E authorization rejects a transition the state machine considers legal`() {
        // This is the original vulnerability, stated as a test. POSTED -> CANCELLED is explicitly
        // legal in JobStatus.validTransitions, and pre-fix transitionJobStatus would have performed
        // it for ANY signed-in user. The two checks must be independent.
        val target = JobStatus.CANCELLED
        val from = JobStatus.POSTED
        assertTrue(
            "precondition: the state machine must still consider POSTED -> CANCELLED legal",
            JobStatus.isValidTransition(from.value, target.value)
        )

        // Authorization denies it to the wrong actor, even though the transition itself is legal.
        val attacker = SkillsExchangeRepository.resolveAuthoritativeUid("uid_B")
        assertFalse(
            "a structurally legal transition must still be rejected for the wrong actor",
            repository.isJobTransitionAuthorized(attacker, job(status = from.value, postedByUid = "uid_A"), target)
        )
        // Sanity: the owner IS allowed it, so the denial is about the actor, not the transition.
        assertTrue(
            repository.isJobTransitionAuthorized(
                SkillsExchangeRepository.resolveAuthoritativeUid("uid_A"),
                job(status = from.value, postedByUid = "uid_A"),
                target
            )
        )
    }

    // --- TEST F: no Firebase user means no transition at all -----------------------------

    @Test
    fun `test F no firebase authentication yields no transition`() = runBlocking {
        db.jobPostDao().insertJob(job(status = JobStatus.POSTED.value))
        // Even for the OWNER's job: without Firebase there is no actor, so nothing happens.
        val ok = repository.transitionJobStatus("job_1", JobStatus.POSTED.value, JobStatus.CANCELLED.value)
        assertFalse("no Firebase user must never move a job", ok)
        assertEquals(JobStatus.POSTED.value, storedStatus())
        assertEquals(0, auditCount())

        // The predicate form fails closed for a null job and a null actor alike.
        assertFalse(repository.canCurrentUserTransitionJob(null, JobStatus.CANCELLED.value))
        assertNull(SkillsExchangeRepository.resolveAuthoritativeUid(null))
    }

    // --- TEST G: invalid / synthetic identities are rejected -----------------------------

    @Test
    fun `test G invalid and synthetic identities are rejected`() {
        val ownerJob = job(status = JobStatus.POSTED.value, postedByUid = "uid_A")
        for (bad in listOf(null, "", "   ", "user_default_me", "system", "unknown", "anonymous",
                "guest", "none", "John Doe", "You (Nakawa)")) {
            val actor = SkillsExchangeRepository.resolveAuthoritativeUid(bad)
            assertNull("'$bad' must not resolve to an identity", actor)
            assertFalse(
                "'$bad' must not be authorized on ANY job",
                repository.isJobTransitionAuthorized(actor, ownerJob, JobStatus.QUOTING)
            )
        }
        // The demo placeholder can never be an actor, even against a job it appears to "own".
        val demoJob = job(status = JobStatus.POSTED.value, postedByUid = "user_default_me")
        assertFalse(
            "the seeded demo profile must never be authorized",
            repository.isJobTransitionAuthorized(
                SkillsExchangeRepository.resolveAuthoritativeUid("user_default_me"), demoJob, JobStatus.QUOTING
            )
        )
        // A legacy job with a NULL owner belongs to nobody and may be driven by nobody.
        assertFalse(
            "a job with unknown provenance must not be drivable by anyone",
            repository.isJobTransitionAuthorized(
                SkillsExchangeRepository.resolveAuthoritativeUid("uid_A"),
                job(status = JobStatus.POSTED.value, postedByUid = null),
                JobStatus.QUOTING
            )
        )
    }

    // --- TEST H/I: F6 attribution preserved; the state machine is still enforced ------------

    @Test
    fun `test H attribution is preserved and the state machine is still enforced`() = runBlocking {
        db.jobPostDao().insertJob(job(status = JobStatus.ACCEPTED.value))

        // TEST I - an ILLEGAL transition is still rejected, proving authorization did not replace
        // `JobStatus.isValidTransition`. ACCEPTED -> COMPLETED is not a legal edge.
        assertFalse(
            "precondition: ACCEPTED -> COMPLETED must be structurally illegal",
            JobStatus.isValidTransition(JobStatus.ACCEPTED.value, JobStatus.COMPLETED.value)
        )
        val owner = SkillsExchangeRepository.resolveAuthoritativeUid("uid_A")
        assertFalse(
            repository.isJobTransitionAuthorized(owner, job(status = JobStatus.ACCEPTED.value), JobStatus.COMPLETED)
        )
        assertFalse(repository.transitionJobStatus("job_1", JobStatus.ACCEPTED.value, JobStatus.COMPLETED.value))
        assertEquals(JobStatus.ACCEPTED.value, storedStatus())
        assertEquals("no audit row for a rejected transition", 0, auditCount())

        // The state machine itself is untouched: the original edge set still holds.
        assertTrue(JobStatus.isValidTransition(JobStatus.POSTED.value, JobStatus.QUOTING.value))
        assertTrue(JobStatus.isValidTransition(JobStatus.IN_PROGRESS.value, JobStatus.COMPLETED.value))
        assertTrue(JobStatus.isValidTransition(JobStatus.COMPLETED.value, JobStatus.PAYMENT_RELEASED.value))
        assertFalse(JobStatus.isValidTransition(JobStatus.CANCELLED.value, JobStatus.POSTED.value))
        assertFalse(JobStatus.isValidTransition(JobStatus.EXPIRED.value, JobStatus.POSTED.value))

        // TEST H - F6 attribution is untouched: `changedBy` still comes from the shared resolver fed
        // by FirebaseAuth, never from a caller and never a sentinel/display value.
        assertEquals("uid_A", SkillsExchangeRepository.resolveAuthoritativeUid("uid_A"))
        for (never in listOf("system", "unknown", "user_default_me", "John Doe")) {
            assertFalse(
                "'$never' must never resolve as an actor",
                SkillsExchangeRepository.resolveAuthoritativeUid(never) == never
            )
        }
    }

    // --- TEST J: the newer Room profile trap (protects the earlier C2-2 work) -------------

    @Test
    fun `test J the newer room profile cannot influence authorization`() = runBlocking {
        // Firebase-authenticated uid A, Room profile A, and a NEWER Room profile B -- the exact trap
        // that broke identity resolution before F1/F3.
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", 1_000L))
        db.userProfileDao().insertUserProfile(profile("uid_B", "+256 700 000 602", 9_999L))
        db.jobPostDao().insertJob(job(status = JobStatus.POSTED.value, postedByUid = "uid_A"))

        // The newest-row query still answers with the WRONG account...
        assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)

        // ...but authorization for uid_A is decided by the job's postedByUid, not by that row.
        assertTrue(
            "uid_A must still be authorized on its own job despite the newer row",
            repository.isJobTransitionAuthorized(
                SkillsExchangeRepository.resolveAuthoritativeUid("uid_A"),
                job(status = JobStatus.POSTED.value), JobStatus.QUOTING
            )
        )
        // uid_B must not inherit uid_A's rights merely by being the newest local profile.
        assertFalse(
            "the newer room profile must not gain authorization",
            repository.isJobTransitionAuthorized(
                SkillsExchangeRepository.resolveAuthoritativeUid("uid_B"),
                job(status = JobStatus.POSTED.value, postedByUid = "uid_A"),
                JobStatus.QUOTING
            )
        )
    }
}