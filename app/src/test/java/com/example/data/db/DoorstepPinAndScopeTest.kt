package com.example.data.db

import android.content.Context
import java.io.File
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.remote.DoorstepPinRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R04-P2-03 (no business decision hidden in a schema default) and R04-P2-06 (no unscoped
 * user-facing reads).
 *
 * P2-03: the entity used to default `doorstepPin` to the literal "4829". Any job row written without
 * an explicit PIN therefore accepted a well-known code. The default is now empty.
 *
 * R07-P2-01 later removed the client-side comparison entirely: the PIN is verified by the server
 * against a salted hash, so this file asserts the format rule and that no local comparator returned.
 *
 * P2-06: the browse lists were fed by `SELECT *` with no viewer at all. The DAO now exposes an
 * approved-provider feed and a viewer-scoped job feed; both are asserted here against a real
 * (in-memory) database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DoorstepPinAndScopeTest {

    private lateinit var db: AppDatabase

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

    // ---- R04-P2-03 -------------------------------------------------------------------------

    @Test
    fun `a job created without a pin carries no default code`() {
        val job = JobPostEntity(
            id = "job_no_pin",
            title = "Fix the tap",
            category = "PLUMBING",
            description = "Kitchen tap drips",
            postedByResident = "Resident",
            district = "Kampala",
            budget = 10.0,
            urgency = "NORMAL",
            estimatedHours = 2.0,
            materialsCost = 0.0,
            safetyFee = 0.0,
            status = "POSTED",
            assignedProviderId = null,
            assignedProviderName = null,
            createdTimestamp = 1L
        )
        assertEquals("", job.doorstepPin)
    }

    @Test
    fun `pin format fails closed on blank and malformed input`() {
        assertFalse(DoorstepPinRules.isWellFormed(""))
        assertFalse(DoorstepPinRules.isWellFormed(" "))
        assertFalse(DoorstepPinRules.isWellFormed("123"))
        assertFalse(DoorstepPinRules.isWellFormed("12345"))
        assertFalse(DoorstepPinRules.isWellFormed("abcd"))
        assertFalse(DoorstepPinRules.isWellFormed(null))
        assertTrue(DoorstepPinRules.isWellFormed("1234"))
        assertTrue("surrounding whitespace is trimmed", DoorstepPinRules.isWellFormed(" 1234 "))
        assertTrue(DoorstepPinRules.isWellFormed("0000"))
    }

    @Test
    fun `the client no longer compares pins locally`() {
        // R07-P2-01: comparison happens in `verifyDoorstepPin` (Cloud Function) against a salted
        // hash. A local comparator is how the original defect survived, so its absence is asserted.
        val repo = repoFile("app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt").readText()
        assertFalse("no client-side comparator", File("app/src/main/java/com/example/util/DoorstepPinPolicy.kt").exists())
        assertFalse("no caller-supplied expected pin", repo.contains("expectedPin: String"))
        assertTrue("the typed pin goes to the server", repo.contains("verifyDoorstepPinWithServer"))
    }

    // ---- R04-P2-06 -------------------------------------------------------------------------

    private fun job(id: String, owner: String?, assignee: String?, status: String, at: Long) =
        JobPostEntity(
            id = id,
            title = "Job $id",
            category = "PLUMBING",
            description = "d",
            postedByResident = "Resident",
            district = "Kampala",
            budget = 10.0,
            urgency = "NORMAL",
            estimatedHours = 2.0,
            materialsCost = 0.0,
            safetyFee = 0.0,
            status = status,
            assignedProviderId = null,
            assignedProviderName = null,
            createdTimestamp = at,
            postedByUid = owner,
            assignedProviderUid = assignee
        )

    @Test
    fun `the job feed shows open work, the viewer's own work and work assigned to them`() = runBlocking {
        db.jobPostDao().insertJobs(
            listOf(
                job("open", owner = "resident_2", assignee = null, status = "POSTED", at = 5L),
                job("mine", owner = "worker_1", assignee = null, status = "COMPLETED", at = 4L),
                job("assigned", owner = "resident_2", assignee = "worker_1", status = "IN_PROGRESS", at = 3L),
                job("someone_else", owner = "resident_2", assignee = "worker_2", status = "COMPLETED", at = 2L),
                job("legacy", owner = null, assignee = null, status = "COMPLETED", at = 1L)
            )
        )

        val visible = db.jobPostDao().getJobsVisibleTo("worker_1").first().map { it.id }

        assertEquals(listOf("open", "mine", "assigned", "legacy"), visible)
        assertFalse("another worker's finished job must not be listed", visible.contains("someone_else"))
    }

    @Test
    fun `an unknown viewer sees only open work and legacy rows`() = runBlocking {
        db.jobPostDao().insertJobs(
            listOf(
                job("open", owner = "resident_2", assignee = null, status = "QUOTING", at = 3L),
                job("closed", owner = "resident_2", assignee = "worker_2", status = "COMPLETED", at = 2L),
                job("legacy", owner = null, assignee = null, status = "COMPLETED", at = 1L)
            )
        )

        val visible = db.jobPostDao().getJobsVisibleTo("").first().map { it.id }

        assertEquals(listOf("open", "legacy"), visible)
    }

    @Test
    fun `only KYC-approved providers reach the browse feed`() = runBlocking {
        db.serviceProviderDao().insertProviders(
            listOf(
                provider("approved", kycStatus = "APPROVED"),
                provider("pending", kycStatus = "PENDING_REVIEW"),
                provider("rejected", kycStatus = "REJECTED")
            )
        )

        val listed = db.serviceProviderDao().getApprovedProviders().first().map { it.id }

        assertEquals(listOf("approved"), listed)
    }

    private fun provider(id: String, kycStatus: String) = ServiceProviderEntity(
        id = id,
        name = "Provider $id",
        tagline = "t",
        category = "PLUMBING",
        neighborhood = "Kampala",
        residenceYears = 3,
        hourlyRate = 50.0,
        distanceMiles = 1.0,
        rating = 4.5,
        completedJobsCount = 10,
        avgResponseTime = "1h",
        isVerifiedResident = true,
        isBackgroundChecked = true,
        bio = "b",
        skillsCsv = "plumbing",
        latitude = 0.3,
        longitude = 32.6,
        endorsementsCount = 2,
        kycStatus = kycStatus
    )
}

    private fun repoFile(relative: String): java.io.File {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (java.io.File(dir, "settings.gradle.kts").exists()) return java.io.File(dir, relative)
            dir = dir.parentFile
        }
        return java.io.File(relative)
    }
