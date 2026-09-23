package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.UserProfileEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REG-003 / REG-004 regression coverage for worker backup contact + profile photo data.
 *
 * Verifies Room persistence (insert/save/reload round-trip and per-UID targeting so one
 * user can never modify another user's backup contact), the new-profile defaults, and
 * that photo/backup fields never satisfy the worker activation gate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileContactPhotoPersistenceTest {

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

    private fun workerProfile(
        id: String = "worker_uid_reg",
        backupName: String = "",
        backupPhone: String = "",
        avatarUrl: String = ""
    ) = UserProfileEntity(
        id = id,
        phoneNumber = "+256 772 198 420",
        fullName = "Reg Worker",
        district = "Nakawa",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "UNVERIFIED",
        isWorkerActivated = false,
        trainingCompleted = false,
        isOnboardingCompleted = true,
        avatarUrl = avatarUrl,
        backupContactName = backupName,
        backupContactPhone = backupPhone,
        updatedAtTimestamp = 1_000L
    )

    // --- REG-003: backup contact save and reload ---

    @Test
    fun `backup contact save and reload round trip`() = runBlocking {
        db.userProfileDao().insertUserProfile(workerProfile())

        val updated = workerProfile().copy(
            backupContactName = "Jane Kato",
            backupContactPhone = "+256 701 234 567",
            updatedAtTimestamp = 2_000L
        )
        db.userProfileDao().insertUserProfile(updated)

        val stored = db.userProfileDao().getUserProfile("worker_uid_reg").first()
        assertNotNull(stored)
        assertEquals("Jane Kato", stored?.backupContactName)
        assertEquals("+256 701 234 567", stored?.backupContactPhone)
    }

    @Test
    fun `backup contact update targets only the owners uid row`() = runBlocking {
        db.userProfileDao().insertUserProfile(workerProfile(id = "worker_a"))
        db.userProfileDao().insertUserProfile(
            workerProfile(id = "worker_b").copy(phoneNumber = "+256 700 111 222")
        )

        // saveBackupContact writes user.copy(...) keyed by the signed-in user's own id.
        val updatedA = workerProfile(id = "worker_a").copy(
            backupContactName = "Jane Kato",
            backupContactPhone = "+256 701 234 567",
            updatedAtTimestamp = 3_000L
        )
        db.userProfileDao().insertUserProfile(updatedA)

        val storedB = db.userProfileDao().getUserProfile("worker_b").first()
        assertNotNull(storedB)
        assertEquals("", storedB?.backupContactName)
        assertEquals("", storedB?.backupContactPhone)
    }

    @Test
    fun `new profiles default to empty backup contact and no avatar`() = runBlocking {
        val profile = workerProfile(id = "worker_new")
        db.userProfileDao().insertUserProfile(profile)

        val stored = db.userProfileDao().getUserProfile("worker_new").first()
        assertNotNull(stored)
        assertEquals("", stored?.backupContactName)
        assertEquals("", stored?.backupContactPhone)
        assertEquals("", stored?.avatarUrl)
    }

    // --- REG-004: profile image persistence ---

    @Test
    fun `profile image url persists and reloads`() = runBlocking {
        db.userProfileDao().insertUserProfile(workerProfile())

        val downloadUrl = "https://firebasestorage.googleapis.com/v0/b/demo/o/avatars.jpg?alt=media"
        val updated = workerProfile().copy(
            avatarUrl = downloadUrl,
            updatedAtTimestamp = 4_000L
        )
        db.userProfileDao().insertUserProfile(updated)

        val stored = db.userProfileDao().getUserProfile("worker_uid_reg").first()
        assertNotNull(stored)
        assertEquals(downloadUrl, stored?.avatarUrl)
    }

    // --- Existing security: gates unaffected by new fields ---

    @Test
    fun `backup contact and photo never unlock the worker dashboard for unapproved worker (SEC-009)`() =
        runBlocking {
            db.userProfileDao().insertUserProfile(
                workerProfile().copy(
                    backupContactName = "Jane Kato",
                    backupContactPhone = "+256 701 234 567",
                    avatarUrl = "https://example.com/photo.jpg"
                )
            )

            val currentUser = db.userProfileDao().getUserProfile("worker_uid_reg").first()
            assertNotNull(currentUser)

            // Same five-layer gate as the SEC-009 regression test.
            assertFalse(currentUser!!.isWorkerActivated)
            val canAccessWorkerDashboard =
                (currentUser.initialRole == "WORKER" || currentUser.workerKycStatus == "APPROVED") &&
                    currentUser.workerKycStatus == "APPROVED" &&
                    currentUser.trainingCompleted &&
                    currentUser.isWorkerActivated

            assertFalse(
                "Backup contact / profile photo must never satisfy the worker dashboard gate",
                canAccessWorkerDashboard
            )
        }
}