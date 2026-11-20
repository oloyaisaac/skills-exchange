package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.JobPostEntity
import com.example.data.db.JobQuoteEntity
import com.example.data.db.JobStatus
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.UserProfileEntity
import com.example.data.remote.FirebaseService
import com.example.data.repository.SkillsExchangeRepository
import com.example.data.repository.SkillsExchangeRepository.QuoteResponseResult
import com.example.data.repository.SkillsExchangeRepository.QuoteSubmissionResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * AUDIT-W-QUOTE: Comprehensive unit and security tests for worker quote submission and response.
 *
 * Covers:
 *  1. Unauthenticated submitQuote fails closed
 *  2. Caller cannot spoof worker UID (synthetic, blank, whitespace, display-name rejected)
 *  3. Rate validation (zero, negative, NaN, infinite rejected)
 *  4. Hours validation (zero, negative, NaN, infinite rejected)
 *  5. Job existence validation (non-existent job rejected)
 *  6. Job state validation (only POSTED and QUOTING accepted; terminal/in-progress rejected)
 *  7. Self-quoting prevention (worker quoting own job rejected)
 *  8. Legacy null-owner jobs protected (cannot be quoted)
 *  9. Duplicate pending quote prevention
 * 10. Legitimate quote succeeds, persists in Room with authoritative UID, emits chat and notification
 * 11. Unauthenticated respondToQuote fails closed
 * 12. Non-owner cannot accept/reject quote (unauthorized caller fails closed)
 * 13. Legitimate job owner can accept/reject quote
 * 14. Job mismatch rejected
 * 15. Non-existent quote rejected
 * 16. Already terminal quote cannot be responded to again
 * 17. Terminal job states reject quote response
 * 18. Pure companion policies (isQuoteSubmissionAuthorized, isQuoteResponseAuthorized)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class QuoteAuthorizationSecurityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SkillsExchangeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SkillsExchangeRepository(db, FirebaseService())

        // Seed provider for foreign key compatibility if needed
        runBlocking {
            db.serviceProviderDao().insertProvider(
                ServiceProviderEntity(
                    id = "uid_worker",
                    name = "Verified Worker",
                    tagline = "Master Electrician",
                    category = "Electrical",
                    neighborhood = "Nakawa",
                    residenceYears = 5,
                    hourlyRate = 25000.0,
                    distanceMiles = 1.2,
                    rating = 4.9,
                    completedJobsCount = 28,
                    avgResponseTime = "15m",
                    isVerifiedResident = true,
                    isBackgroundChecked = true,
                    bio = "Certified technician",
                    skillsCsv = "Wiring,Repair",
                    latitude = 0.33,
                    longitude = 32.61,
                    endorsementsCount = 12
                )
            )
            // `notifications.userId` is a REAL foreign key onto `user_profiles(id)`, so any test
            // that persists a quote (which emits a NEW_QUOTE notification) must seed the worker
            // profile row first or Room throws SQLITE_CONSTRAINT_FOREIGNKEY.
            // The response path notifies the quoting worker (workerId FK on job_quotes, and the
            // notification userId FK), so seed both the worker and the client/owner profiles.
            db.userProfileDao().insertUserProfile(
                UserProfileEntity(
                    id = "uid_worker",
                    phoneNumber = "+256700000001",
                    fullName = "Verified Worker",
                    district = "Nakawa",
                    initialRole = "WORKER",
                    currentActiveMode = "WORKER",
                    workerKycStatus = "APPROVED",
                    isWorkerActivated = true,
                    trainingCompleted = true,
                    isOnboardingCompleted = true,
                    updatedAtTimestamp = 1000L
                )
            )
            db.userProfileDao().insertUserProfile(
                UserProfileEntity(
                    id = "uid_client",
                    phoneNumber = "+256700000002",
                    fullName = "Client Owner",
                    district = "Nakawa",
                    initialRole = "CLIENT",
                    currentActiveMode = "CLIENT",
                    workerKycStatus = "UNVERIFIED",
                    isWorkerActivated = false,
                    trainingCompleted = false,
                    isOnboardingCompleted = true,
                    updatedAtTimestamp = 1000L
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun testJob(
        id: String = "job_1",
        status: String = JobStatus.POSTED.value,
        postedByUid: String? = "uid_client"
    ) = JobPostEntity(
        id = id,
        title = "Fix water pipe",
        category = "Plumbing",
        description = "Pipe burst in kitchen",
        postedByUid = postedByUid,
        postedByResident = "Client Resident",
        district = "Nakawa",
        budget = 50000.0,
        urgency = "Today",
        status = status,
        assignedProviderId = null,
        assignedProviderName = null,
        createdTimestamp = 1000L,
        estimatedHours = 2.0,
        materialsCost = 0.0,
        safetyFee = 0.0
    )

    private fun testQuote(
        id: String = "quote_1",
        jobId: String = "job_1",
        workerId: String = "uid_worker",
        status: String = "PENDING"
    ) = JobQuoteEntity(
        id = id,
        jobId = jobId,
        workerId = workerId,
        workerName = "Verified Worker",
        proposedRate = 25000.0,
        proposedHours = 2.0,
        totalQuote = 50000.0,
        notes = "Can fix today",
        status = status,
        submittedAt = 1000L,
        respondedAt = 0L
    )

    // =========================================================================
    // 1. AUTHENTICATION & IDENTITY (submitQuote)
    // =========================================================================

    @Test
    fun `test 1 unauthenticated submitQuote fails closed`() = runBlocking {
        db.jobPostDao().insertJob(testJob())

        // Public boundary called with no Firebase user attached
        val result = repository.submitQuote(
            jobId = "job_1",
            workerName = "Verified Worker",
            proposedRate = 25000.0,
            proposedHours = 2.0,
            notes = "Available now"
        )

        assertEquals(QuoteSubmissionResult.NotAuthenticated, result)
        assertTrue("No quote may be persisted when unauthenticated", db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    @Test
    fun `test 2 caller cannot spoof worker UID with synthetic or presentation strings`() = runBlocking {
        db.jobPostDao().insertJob(testJob())

        val spoofAttempts = listOf(
            null,
            "",
            "   ",
            "system",
            "unknown",
            "anonymous",
            "guest",
            "none",
            "user_default_me",
            "You (Nakawa)",
            "Robert K. (Sunnyside)"
        )

        for (fakeUid in spoofAttempts) {
            val result = repository.submitQuoteInternal(
                jobId = "job_1",
                workerUid = fakeUid,
                workerName = "Spoofer",
                proposedRate = 25000.0,
                proposedHours = 2.0,
                notes = "Spoofed identity"
            )
            assertEquals("Fake UID '$fakeUid' must be rejected", QuoteSubmissionResult.NotAuthenticated, result)
        }
        assertTrue("No quote may be inserted from spoofed UIDs", db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    // =========================================================================
    // 2. RATE & HOURS VALIDATION (submitQuote)
    // =========================================================================

    @Test
    fun `test 3 zero and negative rates are rejected`() = runBlocking {
        db.jobPostDao().insertJob(testJob())

        for (invalidRate in listOf(0.0, -1.0, -25000.0)) {
            val result = repository.submitQuoteInternal(
                jobId = "job_1",
                workerUid = "uid_worker",
                workerName = "Worker",
                proposedRate = invalidRate,
                proposedHours = 2.0,
                notes = "Invalid rate"
            )
            assertEquals("Rate $invalidRate must be rejected", QuoteSubmissionResult.InvalidRate, result)
        }
        assertTrue(db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    @Test
    fun `test 4 NaN and infinite rates are rejected`() = runBlocking {
        db.jobPostDao().insertJob(testJob())

        for (invalidRate in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val result = repository.submitQuoteInternal(
                jobId = "job_1",
                workerUid = "uid_worker",
                workerName = "Worker",
                proposedRate = invalidRate,
                proposedHours = 2.0,
                notes = "Non-finite rate"
            )
            assertEquals(QuoteSubmissionResult.InvalidRate, result)
        }
        assertTrue(db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    @Test
    fun `test 5 zero and negative hours are rejected`() = runBlocking {
        db.jobPostDao().insertJob(testJob())

        for (invalidHours in listOf(0.0, -0.5, -5.0)) {
            val result = repository.submitQuoteInternal(
                jobId = "job_1",
                workerUid = "uid_worker",
                workerName = "Worker",
                proposedRate = 25000.0,
                proposedHours = invalidHours,
                notes = "Invalid hours"
            )
            assertEquals("Hours $invalidHours must be rejected", QuoteSubmissionResult.InvalidHours, result)
        }
        assertTrue(db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    @Test
    fun `test 6 NaN and infinite hours are rejected`() = runBlocking {
        db.jobPostDao().insertJob(testJob())

        for (invalidHours in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val result = repository.submitQuoteInternal(
                jobId = "job_1",
                workerUid = "uid_worker",
                workerName = "Worker",
                proposedRate = 25000.0,
                proposedHours = invalidHours,
                notes = "Non-finite hours"
            )
            assertEquals(QuoteSubmissionResult.InvalidHours, result)
        }
        assertTrue(db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    // =========================================================================
    // 3. JOB EXISTENCE & STATE VALIDATION (submitQuote)
    // =========================================================================

    @Test
    fun `test 7 non-existent job is rejected`() = runBlocking {
        val result = repository.submitQuoteInternal(
            jobId = "job_non_existent",
            workerUid = "uid_worker",
            workerName = "Worker",
            proposedRate = 25000.0,
            proposedHours = 2.0,
            notes = "Non-existent"
        )
        assertEquals(QuoteSubmissionResult.JobNotFound, result)
    }

    @Test
    fun `test 8 non-quotable and terminal jobs are rejected`() = runBlocking {
        val nonQuotableStatuses = listOf(
            JobStatus.DRAFT,
            JobStatus.WORKER_SELECTED,
            JobStatus.ACCEPTED,
            JobStatus.WORKER_EN_ROUTE,
            JobStatus.ARRIVED,
            JobStatus.IN_PROGRESS,
            JobStatus.COMPLETED,
            JobStatus.PAYMENT_RELEASED,
            JobStatus.CANCELLED,
            JobStatus.DISPUTED,
            JobStatus.EXPIRED
        )

        for (status in nonQuotableStatuses) {
            db.jobPostDao().insertJob(testJob(id = "job_${status.value}", status = status.value))
            val result = repository.submitQuoteInternal(
                jobId = "job_${status.value}",
                workerUid = "uid_worker",
                workerName = "Worker",
                proposedRate = 25000.0,
                proposedHours = 2.0,
                notes = "Attempting to quote status ${status.value}"
            )
            assertEquals("Job status ${status.value} must not accept quotes", QuoteSubmissionResult.InvalidJobState, result)
        }
        assertTrue(db.jobQuoteDao().getQuotesByWorker("uid_worker").first().isEmpty())
    }

    // =========================================================================
    // 4. OWNERSHIP & SELF-QUOTING (submitQuote)
    // =========================================================================

    @Test
    fun `test 9 worker cannot quote own job`() = runBlocking {
        db.jobPostDao().insertJob(testJob(postedByUid = "uid_worker"))

        val result = repository.submitQuoteInternal(
            jobId = "job_1",
            workerUid = "uid_worker",
            workerName = "Self Quoter",
            proposedRate = 25000.0,
            proposedHours = 2.0,
            notes = "Quoting myself"
        )

        assertEquals(QuoteSubmissionResult.SelfQuoteForbidden, result)
        assertTrue(db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    @Test
    fun `test 10 legacy job with null owner cannot be quoted`() = runBlocking {
        db.jobPostDao().insertJob(testJob(postedByUid = null))

        val result = repository.submitQuoteInternal(
            jobId = "job_1",
            workerUid = "uid_worker",
            workerName = "Worker",
            proposedRate = 25000.0,
            proposedHours = 2.0,
            notes = "Quoting legacy job"
        )

        assertEquals(QuoteSubmissionResult.SelfQuoteForbidden, result)
        assertTrue(db.jobQuoteDao().getQuotesForJob("job_1").first().isEmpty())
    }

    // =========================================================================
    // 5. DUPLICATE PENDING QUOTES (submitQuote)
    // =========================================================================

    @Test
    fun `test 11 duplicate pending quote is rejected`() = runBlocking {
        db.jobPostDao().insertJob(testJob())

        val firstResult = repository.submitQuoteInternal(
            jobId = "job_1",
            workerUid = "uid_worker",
            workerName = "Worker",
            proposedRate = 25000.0,
            proposedHours = 2.0,
            notes = "First quote"
        )
        assertTrue(firstResult is QuoteSubmissionResult.Success)

        // Second submission while first is still PENDING
        val duplicateResult = repository.submitQuoteInternal(
            jobId = "job_1",
            workerUid = "uid_worker",
            workerName = "Worker",
            proposedRate = 30000.0,
            proposedHours = 1.5,
            notes = "Second quote"
        )
        assertEquals(QuoteSubmissionResult.DuplicatePendingQuote, duplicateResult)

        val quotes = db.jobQuoteDao().getQuotesForJob("job_1").first()
        assertEquals(1, quotes.size)
        assertEquals(25000.0, quotes[0].proposedRate, 0.001)
    }

    @Test
    fun `test 12 worker can submit new quote after previous quote was rejected`() = runBlocking {
        db.jobPostDao().insertJob(testJob())
        // Seed an existing rejected quote
        db.jobQuoteDao().insertQuote(testQuote(id = "quote_old", status = "REJECTED"))

        val result = repository.submitQuoteInternal(
            jobId = "job_1",
            workerUid = "uid_worker",
            workerName = "Worker",
            proposedRate = 22000.0,
            proposedHours = 2.0,
            notes = "Revised quote"
        )

        assertTrue(result is QuoteSubmissionResult.Success)
        val quotes = db.jobQuoteDao().getQuotesForJob("job_1").first()
        assertEquals(2, quotes.size)
    }

    // =========================================================================
    // 6. SUCCESSFUL SUBMISSION PERSISTENCE & SIDE EFFECTS (submitQuote)
    // =========================================================================

    @Test
    fun `test 13 legitimate quote succeeds with authoritative UID, chat message, and notification`() = runBlocking {
        db.jobPostDao().insertJob(testJob(status = JobStatus.POSTED.value))

        val result = repository.submitQuoteInternal(
            jobId = "job_1",
            workerUid = "uid_worker",
            workerName = "Verified Worker",
            proposedRate = 25000.0,
            proposedHours = 3.0,
            notes = "Can do it this afternoon"
        )

        assertTrue(result is QuoteSubmissionResult.Success)
        val quoteId = (result as QuoteSubmissionResult.Success).quoteId
        assertNotNull(quoteId)

        val quotes = db.jobQuoteDao().getQuotesForJob("job_1").first()
        assertEquals(1, quotes.size)
        val savedQuote = quotes[0]
        assertEquals(quoteId, savedQuote.id)
        assertEquals("job_1", savedQuote.jobId)
        assertEquals("uid_worker", savedQuote.workerId)
        assertEquals("Verified Worker", savedQuote.workerName)
        assertEquals(25000.0, savedQuote.proposedRate, 0.001)
        assertEquals(3.0, savedQuote.proposedHours, 0.001)
        assertEquals(75000.0, savedQuote.totalQuote, 0.001)
        assertEquals("PENDING", savedQuote.status)

        // Verify Chat message was inserted with F5 senderId = uid_worker
        val messages = db.chatMessageDao().getMessagesForJob("job_1").first()
        assertEquals(1, messages.size)
        assertEquals("uid_worker", messages[0].senderId)
        assertTrue(messages[0].message.contains("UGX 75000"))

        // Verify Notification was created
        val notifs = db.notificationDao().getNotificationsForUser("uid_worker").first()
        assertEquals(1, notifs.size)
        assertEquals("NEW_QUOTE", notifs[0].type)
    }

    @Test
    fun `test 14 QUOTING status also accepts quotes`() = runBlocking {
        db.jobPostDao().insertJob(testJob(status = JobStatus.QUOTING.value))

        val result = repository.submitQuoteInternal(
            jobId = "job_1",
            workerUid = "uid_worker",
            workerName = "Worker",
            proposedRate = 20000.0,
            proposedHours = 1.0,
            notes = "Quick fix"
        )
        assertTrue(result is QuoteSubmissionResult.Success)
    }

    // =========================================================================
    // 7. QUOTE RESPONSE AUTHORIZATION (respondToQuote)
    // =========================================================================

    @Test
    fun `test 15 unauthenticated respondToQuote fails closed`() = runBlocking {
        db.jobPostDao().insertJob(testJob())
        db.jobQuoteDao().insertQuote(testQuote())

        val result = repository.respondToQuote(
            quoteId = "quote_1",
            jobId = "job_1",
            accept = true
        )

        assertEquals(QuoteResponseResult.NotAuthenticated, result)
        assertEquals("PENDING", db.jobQuoteDao().getQuoteById("quote_1")?.status)
    }

    @Test
    fun `test 16 stranger cannot accept or reject quotes`() = runBlocking {
        db.jobPostDao().insertJob(testJob(postedByUid = "uid_client"))
        db.jobQuoteDao().insertQuote(testQuote())

        val strangerResult = repository.respondToQuoteInternal(
            quoteId = "quote_1",
            jobId = "job_1",
            actorUid = "uid_stranger",
            accept = true
        )

        assertEquals(QuoteResponseResult.NotAuthorized, strangerResult)
        assertEquals("PENDING", db.jobQuoteDao().getQuoteById("quote_1")?.status)

        // Even worker who submitted quote cannot accept their own quote
        val workerResult = repository.respondToQuoteInternal(
            quoteId = "quote_1",
            jobId = "job_1",
            actorUid = "uid_worker",
            accept = true
        )
        assertEquals(QuoteResponseResult.NotAuthorized, workerResult)
        assertEquals("PENDING", db.jobQuoteDao().getQuoteById("quote_1")?.status)
    }

    @Test
    fun `test 17 legitimate job owner can accept quote`() = runBlocking {
        db.jobPostDao().insertJob(testJob(postedByUid = "uid_client"))
        db.jobQuoteDao().insertQuote(testQuote())

        val result = repository.respondToQuoteInternal(
            quoteId = "quote_1",
            jobId = "job_1",
            actorUid = "uid_client",
            accept = true
        )

        assertEquals(QuoteResponseResult.Success, result)
        assertEquals("ACCEPTED", db.jobQuoteDao().getQuoteById("quote_1")?.status)

        // Notification delivered to worker
        val notifs = db.notificationDao().getNotificationsForUser("uid_worker").first()
        assertEquals(1, notifs.size)
        assertEquals("QUOTE_ACCEPTED", notifs[0].type)
    }

    @Test
    fun `test 18 legitimate job owner can reject quote`() = runBlocking {
        db.jobPostDao().insertJob(testJob(postedByUid = "uid_client"))
        db.jobQuoteDao().insertQuote(testQuote())

        val result = repository.respondToQuoteInternal(
            quoteId = "quote_1",
            jobId = "job_1",
            actorUid = "uid_client",
            accept = false
        )

        assertEquals(QuoteResponseResult.Success, result)
        assertEquals("REJECTED", db.jobQuoteDao().getQuoteById("quote_1")?.status)

        val notifs = db.notificationDao().getNotificationsForUser("uid_worker").first()
        assertEquals(1, notifs.size)
        assertEquals("QUOTE_REJECTED", notifs[0].type)
    }

    @Test
    fun `test 19 quote cannot be responded to on mismatched jobId`() = runBlocking {
        db.jobPostDao().insertJob(testJob(id = "job_1", postedByUid = "uid_client"))
        db.jobPostDao().insertJob(testJob(id = "job_2", postedByUid = "uid_client"))
        db.jobQuoteDao().insertQuote(testQuote(id = "quote_1", jobId = "job_1"))

        val result = repository.respondToQuoteInternal(
            quoteId = "quote_1",
            jobId = "job_2", // wrong job
            actorUid = "uid_client",
            accept = true
        )

        assertEquals(QuoteResponseResult.JobMismatch, result)
        assertEquals("PENDING", db.jobQuoteDao().getQuoteById("quote_1")?.status)
    }

    @Test
    fun `test 20 non-existent quote returns QuoteNotFound`() = runBlocking {
        db.jobPostDao().insertJob(testJob(postedByUid = "uid_client"))

        val result = repository.respondToQuoteInternal(
            quoteId = "quote_missing",
            jobId = "job_1",
            actorUid = "uid_client",
            accept = true
        )

        assertEquals(QuoteResponseResult.QuoteNotFound, result)
    }

    @Test
    fun `test 21 already terminal quote cannot be modified again`() = runBlocking {
        db.jobPostDao().insertJob(testJob(postedByUid = "uid_client"))
        db.jobQuoteDao().insertQuote(testQuote(id = "quote_1", status = "ACCEPTED"))

        val result = repository.respondToQuoteInternal(
            quoteId = "quote_1",
            jobId = "job_1",
            actorUid = "uid_client",
            accept = false
        )

        assertEquals(QuoteResponseResult.QuoteAlreadyTerminal, result)
        assertEquals("ACCEPTED", db.jobQuoteDao().getQuoteById("quote_1")?.status)
    }

    @Test
    fun `test 22 quote cannot be responded to on cancelled or completed jobs`() = runBlocking {
        for (terminalStatus in listOf(JobStatus.CANCELLED, JobStatus.COMPLETED, JobStatus.EXPIRED, JobStatus.PAYMENT_RELEASED)) {
            db.jobPostDao().insertJob(testJob(id = "job_${terminalStatus.value}", status = terminalStatus.value, postedByUid = "uid_client"))
            db.jobQuoteDao().insertQuote(testQuote(id = "quote_${terminalStatus.value}", jobId = "job_${terminalStatus.value}"))

            val result = repository.respondToQuoteInternal(
                quoteId = "quote_${terminalStatus.value}",
                jobId = "job_${terminalStatus.value}",
                actorUid = "uid_client",
                accept = true
            )

            assertEquals(QuoteResponseResult.InvalidJobState, result)
            assertEquals("PENDING", db.jobQuoteDao().getQuoteById("quote_${terminalStatus.value}")?.status)
        }
    }

    // =========================================================================
    // 8. PURE POLICY UNIT TESTS
    // =========================================================================

    @Test
    fun `test 23 pure quote submission policy correctly authorizes legitimate inputs`() {
        val quotableJob = testJob(status = JobStatus.POSTED.value, postedByUid = "uid_client")

        assertTrue(
            SkillsExchangeRepository.isQuoteSubmissionAuthorized("uid_worker", quotableJob)
        )
        // Self-quoting denied
        assertFalse(
            SkillsExchangeRepository.isQuoteSubmissionAuthorized("uid_client", quotableJob)
        )
        // Legacy job with null postedByUid denied
        assertFalse(
            SkillsExchangeRepository.isQuoteSubmissionAuthorized("uid_worker", testJob(postedByUid = null))
        )
        // Terminal job denied
        assertFalse(
            SkillsExchangeRepository.isQuoteSubmissionAuthorized("uid_worker", testJob(status = JobStatus.CANCELLED.value))
        )
        // Synthetic / unauthenticated actor denied
        assertFalse(
            SkillsExchangeRepository.isQuoteSubmissionAuthorized(null, quotableJob)
        )
        assertFalse(
            SkillsExchangeRepository.isQuoteSubmissionAuthorized("system", quotableJob)
        )
    }

    @Test
    fun `test 24 pure quote response policy correctly authorizes owner only`() {
        val job = testJob(status = JobStatus.POSTED.value, postedByUid = "uid_client")
        val quote = testQuote(jobId = "job_1", status = "PENDING")

        // Owner authorized
        assertTrue(
            SkillsExchangeRepository.isQuoteResponseAuthorized("uid_client", job, quote)
        )
        // Non-owner stranger denied
        assertFalse(
            SkillsExchangeRepository.isQuoteResponseAuthorized("uid_stranger", job, quote)
        )
        // Worker who quoted denied
        assertFalse(
            SkillsExchangeRepository.isQuoteResponseAuthorized("uid_worker", job, quote)
        )
        // Mismatched job denied
        assertFalse(
            SkillsExchangeRepository.isQuoteResponseAuthorized("uid_client", job, testQuote(jobId = "other_job"))
        )
        // Already terminal quote denied
        assertFalse(
            SkillsExchangeRepository.isQuoteResponseAuthorized("uid_client", job, testQuote(status = "ACCEPTED"))
        )
        // Cancelled job denied
        assertFalse(
            SkillsExchangeRepository.isQuoteResponseAuthorized("uid_client", testJob(status = JobStatus.CANCELLED.value), quote)
        )
    }
}
