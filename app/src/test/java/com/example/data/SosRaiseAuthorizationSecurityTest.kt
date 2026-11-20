package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.JobPostEntity
import com.example.data.db.JobStatus
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.SosIncidentEntity
import com.example.data.repository.SkillsExchangeRepository
import com.example.data.repository.SkillsExchangeRepository.SosRaiseResult
import com.example.data.remote.FirebaseService
import com.example.data.remote.SmsNotificationService
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SosRaiseAuthorizationSecurityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SkillsExchangeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SkillsExchangeRepository(db, FirebaseService(), SmsNotificationService())

        runBlocking {
            db.serviceProviderDao().insertProvider(
                ServiceProviderEntity(
                    id = "prov_1",
                    name = "Elena Rostova",
                    tagline = "Master Plumber & Pipe Specialist",
                    category = "Plumbing",
                    neighborhood = "Maplewood East",
                    residenceYears = 4,
                    hourlyRate = 75.0,
                    distanceMiles = 0.8,
                    rating = 4.9,
                    completedJobsCount = 42,
                    avgResponseTime = "12 mins",
                    isVerifiedResident = true,
                    isBackgroundChecked = true,
                    bio = "Licensed master plumber.",
                    skillsCsv = "Pipe Leak Repair, Drain Clearing",
                    latitude = 37.7749,
                    longitude = -122.4194,
                    endorsementsCount = 31
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun job(
        id: String = "job_1",
        postedByUid: String? = "uid_owner",
        assignedProviderId: String? = "prov_1",
        assignedProviderUid: String? = null,
        isSosTriggered: Boolean = false
    ) = JobPostEntity(
        id = id,
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping.",
        postedByUid = postedByUid,
        postedByResident = "You (Nakawa)",
        district = "Nakawa",
        budget = 50.0,
        urgency = "Today",
        status = JobStatus.IN_PROGRESS.value,
        assignedProviderId = assignedProviderId,
        assignedProviderName = "Elena Rostova",
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0,
        isSosTriggered = isSosTriggered
    )

    private fun uid(raw: String?) = SkillsExchangeRepository.resolveAuthoritativeUid(raw)

    // ========================================================================
    // 1-5. Pure policy: isSosRaiseAuthorized (actor-explicit, no writes)
    // ========================================================================

    @Test
    fun `owner raising SOS on own job is authorized`() {
        assertTrue(
            SkillsExchangeRepository.isSosRaiseAuthorized(uid("uid_owner"), job())
        )
    }

    @Test
    fun `assigned provider raising SOS on assigned job is authorized`() {
        assertTrue(
            SkillsExchangeRepository.isSosRaiseAuthorized(
                uid("prov_1"), job(assignedProviderId = "prov_1")
            )
        )
    }

    @Test
    fun `stranger cannot raise SOS on another users job`() {
        assertFalse(
            "a stranger must not trigger another account's emergency SOS",
            SkillsExchangeRepository.isSosRaiseAuthorized(uid("uid_stranger"), job())
        )
    }

    @Test
    fun `legacy job with null owner may not be raised on by anybody`() {
        val legacy = job(postedByUid = null)
        // even the purported owner
        assertFalse(
            SkillsExchangeRepository.isSosRaiseAuthorized(uid("uid_owner"), legacy)
        )
        // even the assigned provider
        assertFalse(
            SkillsExchangeRepository.isSosRaiseAuthorized(
                uid("prov_1"), job(postedByUid = null, assignedProviderId = "prov_1")
            )
        )
    }

    @Test
    fun `synthetic and presentation-text actors are denied`() {
        val active = job()
        for (never in listOf(null, "", "   ", "system", "unknown", "unknown_worker", "user_default_me", "John Doe", "You (Nakawa)")) {
            assertFalse(
                "'$never' must never authorize raising an SOS",
                SkillsExchangeRepository.isSosRaiseAuthorized(uid(never), active)
            )
        }
    }

    // ========================================================================
    // 6-12. Fail-closed raise against the real in-memory Room database
    // ========================================================================

    @Test
    fun `unauthenticated public triggerSosAlert fails closed and sets no flag`() = runBlocking {
        db.jobPostDao().insertJob(job(isSosTriggered = false))

        val result = repository.triggerSosAlert("job_1")

        assertEquals(SosRaiseResult.NotAuthenticated, result)
        assertFalse(
            "no SOS flag may be set when there is no authenticated user",
            db.jobPostDao().getJobById("job_1")!!.isSosTriggered
        )
    }

    @Test
    fun `stranger internal raise fails closed, no flag, no incident`() = runBlocking {
        db.jobPostDao().insertJob(job())

        val result = repository.triggerSosAlertInternal("job_1", "uid_stranger")

        assertEquals(SosRaiseResult.NotAuthorized, result)
        assertFalse(db.jobPostDao().getJobById("job_1")!!.isSosTriggered)
        assertNull(db.sosIncidentDao().getSosIncidentByJobId("job_1"))
    }

    @Test
    fun `owner raising SOS succeeds and persists an ACTIVE incident`() = runBlocking {
        db.jobPostDao().insertJob(job())

        val result = repository.triggerSosAlertInternal("job_1", "uid_owner")

        assertEquals(SosRaiseResult.Success, result)
        assertTrue(db.jobPostDao().getJobById("job_1")!!.isSosTriggered)
        val incident = db.sosIncidentDao().getSosIncidentByJobId("job_1")
        assertNotNull(incident)
        assertEquals(SosIncidentEntity.STATUS_ACTIVE, incident!!.status)
        assertEquals("uid_owner", incident.postedByUid)
        assertEquals("prov_1", incident.assignedProviderId)
    }

    @Test
    fun `assigned provider raising SOS succeeds when addressed by its Firebase UID`() = runBlocking {
        db.jobPostDao().insertJob(job(assignedProviderUid = "uid_provider"))

        val result = repository.triggerSosAlertInternal("job_1", "uid_provider")

        assertEquals(SosRaiseResult.Success, result)
        assertTrue(db.jobPostDao().getJobById("job_1")!!.isSosTriggered)
    }

    @Test
    fun `catalog record id is never an authorization key on raise`() = runBlocking {
        db.jobPostDao().insertJob(job())

        assertEquals(SosRaiseResult.NotAuthorized, repository.triggerSosAlertInternal("job_1", "prov_1"))
    }

    @Test
    fun `spoofed actor uid is denied on raise`() = runBlocking {
        db.jobPostDao().insertJob(job())

        for (never in listOf(null, "", "   ", "system", "unknown", "user_default_me", "John Doe", "You (Nakawa)")) {
            assertEquals(
                SosRaiseResult.NotAuthorized,
                repository.triggerSosAlertInternal("job_1", never ?: "")
            )
        }
    }

    @Test
    fun `legacy null-owner internal raise denied to everybody`() = runBlocking {
        db.jobPostDao().insertJob(job(postedByUid = null, assignedProviderId = "prov_1"))

        assertEquals(
            SosRaiseResult.NotAuthorized,
            repository.triggerSosAlertInternal("job_1", "uid_owner")
        )
        assertEquals(
            SosRaiseResult.NotAuthorized,
            repository.triggerSosAlertInternal("job_1", "prov_1")
        )
        assertFalse(db.jobPostDao().getJobById("job_1")!!.isSosTriggered)
        assertNull(db.sosIncidentDao().getSosIncidentByJobId("job_1"))
    }

    @Test
    fun `re-raising on an already active job stays ACTIVE and is idempotent`() = runBlocking {
        db.jobPostDao().insertJob(job())

        assertEquals(SosRaiseResult.Success, repository.triggerSosAlertInternal("job_1", "uid_owner"))
        assertEquals(SosRaiseResult.Success, repository.triggerSosAlertInternal("job_1", "uid_owner"))

        val incident = db.sosIncidentDao().getSosIncidentByJobId("job_1")
        assertNotNull(incident)
        assertEquals(SosIncidentEntity.STATUS_ACTIVE, incident!!.status)
    }

    @Test
    fun `emergency SOS internal respects owner gate and records coordinates`() = runBlocking {
        db.jobPostDao().insertJob(job())

        val result = repository.triggerEmergencySosInternal("job_1", 0.3476, 32.5825, "Nakawa", "uid_owner")

        assertEquals(SosRaiseResult.Success, result)
        val incident = db.sosIncidentDao().getSosIncidentByJobId("job_1")
        assertNotNull(incident)
        assertEquals(0.3476, incident!!.latitude, 0.0001)
        assertEquals(32.5825, incident.longitude, 0.0001)
        assertEquals("Nakawa", incident.district)
    }
}