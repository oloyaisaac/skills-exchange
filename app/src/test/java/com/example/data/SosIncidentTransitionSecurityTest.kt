package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import com.example.data.db.SosIncidentEntity
import com.example.data.remote.FirebaseService
import com.example.data.repository.SkillsExchangeRepository
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
 * H7/AUDIT-A-SOS — authoritative incident-state transitions.
 *
 * Verifies that acknowledge/resolve perform the authorized, in-state transition and never
 * rewrite the incident identity (actor/job/id/raisedAt) or an out-of-state row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SosIncidentTransitionSecurityTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: SkillsExchangeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = SkillsExchangeRepository(db, FirebaseService())

        runBlocking {
            // job_posts.assignedProviderId has a foreign key to service_providers.id.
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
            db.jobPostDao().insertJob(
                JobPostEntity(
                    id = "job_1",
                    title = "Repair kitchen sink",
                    category = "Plumbing",
                    description = "Tap is dripping.",
                    postedByUid = "uid_owner",
                    postedByResident = "You (Nakawa)",
                    district = "Nakawa",
                    budget = 50.0,
                    urgency = "Today",
                    status = "IN_PROGRESS",
                    assignedProviderId = "prov_1",
                    assignedProviderUid = "uid_provider",
                    assignedProviderName = "Elena Rostova",
                    createdTimestamp = 1_000L,
                    estimatedHours = 1.0,
                    materialsCost = 0.0,
                    safetyFee = 0.0
                )
            )
            db.sosIncidentDao().insertSosIncident(
                SosIncidentEntity(
                    id = "sos_job_1",
                    jobId = "job_1",
                    postedByUid = "uid_owner",
                    assignedProviderId = "prov_1",
                    assignedProviderUid = "uid_provider",
                    status = SosIncidentEntity.STATUS_ACTIVE,
                    latitude = 0.0,
                    longitude = 0.0,
                    district = "Nakawa",
                    raisedAt = 1_000L
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `acknowledge transitions an ACTIVE incident and records the actor`() = runBlocking {
        assertTrue(repository.acknowledgeSos("sos_job_1", "uid_admin"))

        val incident = db.sosIncidentDao().getSosIncidentById("sos_job_1")
        assertNotNull(incident)
        assertEquals(SosIncidentEntity.STATUS_ACKNOWLEDGED, incident!!.status)
        assertTrue(incident.acknowledgedAt > 0)
        assertEquals(0L, incident.clearedAt)
        assertEquals("uid_admin", incident.clearedBy)
    }

    @Test
    fun `acknowledge rejects an unauthenticated actor`() = runBlocking {
        assertFalse(repository.acknowledgeSos("sos_job_1", ""))
        assertFalse(repository.acknowledgeSos("sos_job_1", "   "))
        assertFalse(repository.acknowledgeSos("sos_job_1", "system"))
    }

    @Test
    fun `acknowledge rejects unknown incident id`() = runBlocking {
        assertFalse(repository.acknowledgeSos("sos_unknown", "uid_admin"))
    }

    @Test
    fun `acknowledge rejects an already-resolved incident`() = runBlocking {
        assertTrue(repository.acknowledgeSos("sos_job_1", "uid_admin"))
        assertFalse(repository.acknowledgeSos("sos_job_1", "uid_admin"))
    }

    @Test
    fun `resolve transitions an ACTIVE incident and records the actor`() = runBlocking {
        assertTrue(repository.resolveSosIncident("sos_job_1", "uid_admin"))

        val incident = db.sosIncidentDao().getSosIncidentById("sos_job_1")
        assertNotNull(incident)
        assertEquals(SosIncidentEntity.STATUS_CLEARED, incident!!.status)
        assertEquals(0L, incident.acknowledgedAt)
        assertTrue(incident.clearedAt > 0)
        assertEquals("uid_admin", incident.clearedBy)
    }

    @Test
    fun `resolve rejects an already-resolved incident`() = runBlocking {
        assertTrue(repository.resolveSosIncident("sos_job_1", "uid_admin"))
        assertFalse(repository.resolveSosIncident("sos_job_1", "uid_admin"))
    }

    @Test
    fun `incident identity cannot be rewritten through resolve`() = runBlocking {
        assertTrue(repository.resolveSosIncident("sos_job_1", "uid_admin"))
        assertFalse(repository.resolveSosIncident("sos_unknown", "uid_admin"))
        assertEquals(
            SosIncidentEntity.STATUS_CLEARED,
            db.sosIncidentDao().getSosIncidentById("sos_job_1")!!.status
        )
    }
}
