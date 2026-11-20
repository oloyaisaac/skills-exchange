package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.SosIncidentEntity
import com.example.data.remote.FirebaseService
import com.example.data.remote.SmsNotificationService
import com.example.data.repository.SkillsExchangeRepository
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
 * R04-P2-05: Room and Firestore must not be able to diverge silently.
 *
 * The defect: the SOS mirror was fire-and-forget. `syncSosIncidentToFirestore` returned `Unit` and
 * swallowed every failure, nothing recorded whether the write landed, and there was no retry — so a
 * local incident could exist forever with no server counterpart and nobody would ever know.
 *
 * The fix is a durable, idempotent reconciliation pass over the existing `pending_actions` queue:
 * adopt un-mirrored incidents, attempt each once, mark success, and abandon after the retry cap so
 * the queue cannot spin. These tests drive both outcomes through the injectable sync lambda, so the
 * retry/abandon logic is executed rather than inspected.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MirrorReconciliationTest {

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

    private fun incident(id: String, jobId: String, mirrored: Boolean = false) =
        SosIncidentEntity(
            id = id,
            jobId = jobId,
            postedByUid = "user_1",
            assignedProviderId = null,
            status = SosIncidentEntity.STATUS_ACTIVE,
            latitude = 0.3,
            longitude = 32.6,
            district = "Kampala",
            raisedAt = System.currentTimeMillis(),
            firestoreMirrored = mirrored
        )

    @Test
    fun `an unmirrored incident is adopted and a successful sync marks it mirrored`() = runBlocking {
        db.sosIncidentDao().insertSosIncident(incident("sos_a", "job_a"))

        val report = repository.reconcilePendingMirrors { true }

        assertEquals(1, report.adopted)
        assertEquals(1, report.attempted)
        assertEquals(1, report.succeeded)
        assertTrue(db.sosIncidentDao().getSosIncidentById("sos_a")!!.firestoreMirrored)
        assertTrue(
            "the queue is drained on success",
            db.pendingActionDao().getPendingActionsList().isEmpty()
        )
    }

    @Test
    fun `a failed sync keeps the incident local and schedules a retry`() = runBlocking {
        db.sosIncidentDao().insertSosIncident(incident("sos_b", "job_b"))

        val report = repository.reconcilePendingMirrors { false }

        assertEquals(1, report.adopted)
        assertEquals(1, report.retryScheduled)
        assertFalse(
            "the local incident is never rolled back by a mirror failure",
            db.sosIncidentDao().getSosIncidentById("sos_b")!!.firestoreMirrored
        )
        val action = db.pendingActionDao().getPendingActionsList().single()
        assertEquals("PENDING", action.status)
        assertEquals(1, action.retryCount)
    }

    @Test
    fun `repeated failures hit the retry cap and the action is abandoned`() = runBlocking {
        db.sosIncidentDao().insertSosIncident(incident("sos_c", "job_c"))

        // maxRetries is 3 on a new pending action, so the third failed attempt abandons it.
        repository.reconcilePendingMirrors { false }
        repository.reconcilePendingMirrors { false }
        val third = repository.reconcilePendingMirrors { false }

        assertEquals(1, third.abandoned)
        val action = db.pendingActionDao().getPendingActionsList().single()
        assertEquals("FAILED", action.status)
        assertEquals(3, action.retryCount)
        assertFalse(db.sosIncidentDao().getSosIncidentById("sos_c")!!.firestoreMirrored)
    }

    @Test
    fun `a recovered network finishes an abandoned incident on the next pass`() = runBlocking {
        db.sosIncidentDao().insertSosIncident(incident("sos_d", "job_d"))
        repository.reconcilePendingMirrors { false }
        repository.reconcilePendingMirrors { false }
        repository.reconcilePendingMirrors { false }

        // The incident is still un-mirrored, so a later pass re-adopts it and a working sync wins.
        val report = repository.reconcilePendingMirrors { true }

        assertTrue(report.succeeded >= 1)
        assertTrue(db.sosIncidentDao().getSosIncidentById("sos_d")!!.firestoreMirrored)
    }

    @Test
    fun `already-mirrored incidents are left alone`() = runBlocking {
        db.sosIncidentDao().insertSosIncident(incident("sos_e", "job_e", mirrored = true))

        val report = repository.reconcilePendingMirrors { error("must not sync") }

        assertEquals(0, report.adopted)
        assertEquals(0, report.attempted)
    }
}
