package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SessionManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * B4 regression coverage for the Worker Online toggle persistence.
 *
 * The online/availability choice is stored in the SessionManager DataStore so a paused/offline
 * worker stays paused across app restarts instead of silently resetting to online (the original
 * in-memory-only behavior). The flag defaults to online (true) for a fresh install / first launch.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkerOnlinePersistenceTest {

    private lateinit var sessionManager: SessionManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sessionManager = SessionManager(context)
        // Reset the store so each test starts from the default (unset) worker_online key.
        runBlocking { sessionManager.clearSession() }
    }

    @Test
    fun `worker online defaults to true when never toggled`() = runBlocking {
        assertTrue(sessionManager.getWorkerOnline())
    }

    @Test
    fun `toggling offline persists the choice`() = runBlocking {
        sessionManager.setWorkerOnline(false)
        assertFalse(sessionManager.getWorkerOnline())
    }

    @Test
    fun `toggling back online persists the choice`() = runBlocking {
        sessionManager.setWorkerOnline(false)
        sessionManager.setWorkerOnline(true)
        assertTrue(sessionManager.getWorkerOnline())
    }

    @Test
    fun `persisted choice survives a new session manager instance (restart)`() = runBlocking {
        sessionManager.setWorkerOnline(false)

        // Simulate an app restart: a fresh SessionManager reading the same persisted store.
        val restarted = SessionManager(ApplicationProvider.getApplicationContext<Context>())
        assertFalse(restarted.getWorkerOnline())
    }
}
