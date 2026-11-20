package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.PendingActionEntity
import com.example.data.db.SosIncidentEntity
import com.example.data.remote.FirebaseService
import com.example.data.repository.SkillsExchangeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R13-P2-01: a queued action must either run or be dead-lettered — never quietly accumulate.
 *
 * The defect the review found: `flushPendingActions` snapshotted the queue, logged the count and
 * deleted nothing, so every queued row stayed forever and no user could see how much was pending.
 * The drain now executes the one action type the app actually produces (the SOS mirror retry), and
 * retires anything else against its own retry cap. These tests drive both paths through an injected
 * sync lambda, so the retry cap and the dead-letter are executed rather than inspected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PendingActionDrainTest {

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

    private suspend fun incident(id: String, jobId: String = "job_$id"): SosIncidentEntity {
        db.jobPostDao().insertJob(
            com.example.data.db.JobPostEntity(
                id = jobId,
                title = "Job $jobId",
                category = "PLUMBING",
                description = "d",
                postedByUid = "resident_1",
                postedByResident = "Resident",
                district = "Nakawa",
                budget = 10.0,
                urgency = "Today",
                status = "In Progress",
                // `job_posts.assignedProviderId` is a real foreign key to `service_providers`; a
                // string that names no provider row aborts the insert (CI caught exactly that).
                assignedProviderId = null,
                assignedProviderName = null,
                createdTimestamp = 1L,
                estimatedHours = 1.0,
                materialsCost = 0.0,
                safetyFee = 0.0
            )
        )
        val entity = SosIncidentEntity(
            id = id,
            jobId = jobId,
            postedByUid = "resident_1",
            assignedProviderId = "worker_1",
            latitude = 0.35,
            longitude = 32.58,
            district = "Nakawa",
            raisedAt = 1L
        )
        db.sosIncidentDao().insertSosIncident(entity)
        return entity
    }

    private suspend fun queue(id: String, type: String, payload: String, retries: Int = 0) {
        db.pendingActionDao().insertAction(
            PendingActionEntity(
                id = id,
                actionType = type,
                payloadJson = payload,
                retryCount = retries
            )
        )
    }

    /** A supported action that succeeds is executed and removed — the queue shrinks. */
    @Test
    fun `a mirrored incident is synced once and leaves the queue`() = runBlocking {
        incident("sos_1")
        queue("sync_sos_sos_1", "SYNC_SOS_INCIDENT", "sos_1")

        val report = repository.drainPendingActionQueue { true }

        assertEquals(1, report.attempted)
        assertEquals(1, report.succeeded)
        assertEquals(0, report.remaining)
        assertNull(db.pendingActionDao().getActionById("sync_sos_sos_1"))
        assertEquals(true, db.sosIncidentDao().getSosIncidentById("sos_1")?.firestoreMirrored)
    }

    /** A supported action that fails is retried, and is abandoned only at its cap — never lost earlier. */
    @Test
    fun `a failing sync is retried to the cap and then dead-lettered`() = runBlocking {
        incident("sos_2")
        queue("sync_sos_sos_2", "SYNC_SOS_INCIDENT", "sos_2")

        var report = repository.drainPendingActionQueue { false }
        assertEquals(1, report.retryScheduled)
        assertEquals(1, db.pendingActionDao().getActionById("sync_sos_sos_2")?.retryCount)

        // maxRetries defaults to 3: the third attempt exhausts it and the row stops being pending.
        report = repository.drainPendingActionQueue { false }
        assertEquals(2, db.pendingActionDao().getActionById("sync_sos_sos_2")?.retryCount)
        report = repository.drainPendingActionQueue { false }
        assertEquals(1, report.abandoned)
        assertEquals("FAILED", db.pendingActionDao().getActionById("sync_sos_sos_2")?.status)
        assertEquals(0, db.pendingActionDao().countPendingActions())
    }

    /**
     * An action type this build cannot execute is not deleted and not left to spin: it is counted,
     * retried, and finally dead-lettered with its payload intact.
     */
    @Test
    fun `an unknown action type is retired at the cap and its payload survives`() = runBlocking {
        queue("a1", "CREATE_JOB", "{\"title\":\"leaking tap\"}")

        var report = repository.drainPendingActionQueue { true }
        assertEquals(1, report.unsupported)
        assertEquals(0, report.succeeded)
        assertNotNull(db.pendingActionDao().getActionById("a1"))

        repository.drainPendingActionQueue { true }
        report = repository.drainPendingActionQueue { true }

        assertEquals(1, report.abandoned)
        val row = db.pendingActionDao().getActionById("a1")
        assertEquals("FAILED", row?.status)
        assertEquals("{\"title\":\"leaking tap\"}", row?.payloadJson)
    }

    /** Both kinds in one pass: the supported one drains, the unknown one is counted, nothing is lost. */
    @Test
    fun `one pass handles both kinds and reports what is left`() = runBlocking {
        incident("sos_3")
        queue("sync_sos_sos_3", "SYNC_SOS_INCIDENT", "sos_3")
        queue("a2", "SEND_MESSAGE", "{\"text\":\"hello\"}")

        val report = repository.drainPendingActionQueue { true }

        assertEquals(1, report.succeeded)
        assertEquals(1, report.unsupported)
        assertEquals(1, report.remaining)
        assertNull(db.pendingActionDao().getActionById("sync_sos_sos_3"))
        assertNotNull(db.pendingActionDao().getActionById("a2"))
    }

    /** An empty queue is a no-op, not a spin: nothing is attempted and nothing is left. */
    @Test
    fun `an empty queue drains to nothing`() = runBlocking {
        val report = repository.drainPendingActionQueue { true }
        assertEquals(0, report.attempted)
        assertEquals(0, report.unsupported)
        assertEquals(0, report.remaining)
        assertEquals(true, report.didNothing)
    }
}
