package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.PendingActionEntity
import com.example.data.remote.FirebaseService
import com.example.data.remote.SmsNotificationService
import com.example.data.repository.SkillsExchangeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * STEP 0 (offline queue data-loss fix): the flush path must preserve queued work.
 *
 * Regression for the confirmed defect in `SkillsExchangeViewModel.flushPendingActions()`,
 * which collected the endless pending-actions Flow and deleted every row via
 * `markActionCompleted()` without executing anything or obtaining server confirmation.
 *
 * Step 0 replaces that with a one-time snapshot read that deletes NOTHING. These tests
 * pin the repository side of that contract:
 *  1. Snapshot reads return the queued rows AND leave them in the table.
 *  2. Repeated / concurrent snapshot reads (overlapping reconnect triggers) never
 *     delete or duplicate rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PendingActionPreservationStep0Test {

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

    private fun action(id: String, type: String = "SEND_MESSAGE") = PendingActionEntity(
        id = id,
        actionType = type,
        payloadJson = "{}",
        createdAt = System.currentTimeMillis(),
        retryCount = 0,
        maxRetries = 3,
        status = "PENDING"
    )

    @Test
    fun `snapshot read preserves every queued item`() = runBlocking {
        db.pendingActionDao().insertAction(action("action_step0_1", "PUBLISH_JOB"))
        db.pendingActionDao().insertAction(action("action_step0_2"))

        val snapshot = repository.getPendingActionsSnapshot()

        assertEquals(
            "snapshot must return both queued items",
            setOf("action_step0_1", "action_step0_2"),
            snapshot.map { it.id }.toSet()
        )
        assertEquals(
            "STEP 0 must not delete, complete, or otherwise discard any queue item",
            2,
            repository.getPendingActionsCount()
        )
    }

    @Test
    fun `overlapping snapshot reads never delete or duplicate items`() = runBlocking {
        db.pendingActionDao().insertAction(action("action_step0_3", "PUBLISH_JOB"))
        db.pendingActionDao().insertAction(action("action_step0_4"))

        // Simulate overlapping reconnect triggers racing the same queue.
        val snapshots = (1..10).map {
            async { repository.getPendingActionsSnapshot() }
        }.awaitAll()

        snapshots.forEach { snapshot ->
            assertEquals(
                "every overlapping read must see both items",
                setOf("action_step0_3", "action_step0_4"),
                snapshot.map { it.id }.toSet()
            )
        }
        assertEquals(
            "concurrent snapshot reads must preserve the queue exactly",
            2,
            repository.getPendingActionsCount()
        )
        assertTrue(
            "no FAILED projection may leak into the pending snapshot",
            snapshots.flatten().none { it.status == "FAILED" }
        )
    }
}
