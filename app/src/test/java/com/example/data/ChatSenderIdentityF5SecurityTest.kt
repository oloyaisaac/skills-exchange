package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.ChatMessageEntity
import com.example.data.db.JobPostEntity
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
 * C2-2 Fix #5 (audit finding **F5**): chat sender identity.
 *
 * The defect under test: `sendChatMessage()` derived `senderId = currentUser?.id ?: "unknown"` from
 * the legacy newest-Room-row query, so a device holding another account attributed chat authorship
 * to the wrong person — and `"unknown"` is not an identity at all.
 *
 * `senderId` must come from `FirebaseAuth.currentUser.uid` only. This Robolectric context has no
 * Firebase user, so the uid-bearing assertions are exercised through the same shared resolver the
 * repository consumes, while the fail-closed and ownership assertions run against the real database.
 *
 * NOTE: `user_profiles` has a UNIQUE index on `phoneNumber`, so seeded rows need distinct numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChatSenderIdentityF5SecurityTest {

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

    // chat_messages.jobId has a FOREIGN KEY onto job_posts (ON DELETE CASCADE), so every message
    // fixture needs a real parent job row to exist first.
    private fun parentJob() = JobPostEntity(
        id = "job_1",
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping.",
        postedByUid = "uid_A",
        postedByResident = "You (Nakawa)",
        district = "Nakawa",
        budget = 50.0,
        urgency = "Today",
        status = "Requested",
        assignedProviderId = null,
        assignedProviderName = null,
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0
    )

    private fun message(
        id: String = "msg_1",
        senderId: String? = null,
        senderName: String = "You (Nakawa)",
        isSystemNotification: Boolean = false
    ) = ChatMessageEntity(
        id = id,
        jobId = "job_1",
        senderId = senderId,
        senderName = senderName,
        isFromUser = true,
        message = "hello",
        timestampText = "Just now",
        isSystemNotification = isSystemNotification
    )

    private suspend fun attemptToSend(): Boolean = repository.sendChatMessage(
        jobId = "job_1",
        senderName = "You (Nakawa)",
        isFromUser = true,
        text = "hello"
    )

    // --- TEST A: the author is the authenticated uid ---------------------------------------

    @Test
    fun `test A the author is the authenticated firebase uid not a display string`() {
        // The uid that sendChatMessage writes is produced by the single shared resolver it calls.
        assertEquals("uid_A", SkillsExchangeRepository.resolveAuthoritativeUid("uid_A"))
        // A legacy `"unknown"` sender and display text must never resolve to an identity.
        for (notAnIdentity in listOf("unknown", "system", "anonymous", "guest", "none",
                "You (Nakawa)", "Robert K.", "user_default_me", "", "   ")) {
            assertNull(
                "'$notAnIdentity' must not resolve to an authenticated identity",
                SkillsExchangeRepository.resolveAuthoritativeUid(notAnIdentity)
            )
        }
    }

    // --- TEST C / D: no Firebase user, or an invalid uid => fail closed -------------------

    @Test
    fun `test C sending fails closed with no authenticated firebase user`() = runBlocking {
        // A profile row exists on the device, which is exactly the trap the old newest-row query
        // fell into: it would have attributed the message to whoever happened to be newest.
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 501", 1_000L))

        val sent = attemptToSend()

        assertFalse("sendChatMessage must report failure without an authenticated uid", sent)
        assertTrue(
            "No message may be persisted without an authenticated author",
            db.chatMessageDao().getMessagesForJob("job_1").first().isEmpty()
        )
    }

    @Test
    fun `test D an invalid firebase uid also fails closed`() = runBlocking {
        for (invalid in listOf(null, "", "   ", "user_default_me", "unknown", "system")) {
            assertNull(
                "invalid uid '$invalid' must not resolve",
                SkillsExchangeRepository.resolveAuthoritativeUid(invalid)
            )
        }
        assertFalse(attemptToSend())
        assertTrue(
            "no \"unknown\" sender may ever be written",
            db.chatMessageDao().getMessagesForJob("job_1").first().isEmpty()
        )
    }

    // --- TEST B: the newer Room row must never become the author -------------------------

    @Test
    fun `test B the newer room row never becomes the sender`() = runBlocking {
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 501", 1_000L))
        db.userProfileDao().insertUserProfile(profile("uid_B", "+256 700 000 502", 9_000L))

        // Sanity: the legacy newest-row query answers with the OTHER account — precisely why it
        // must never be an identity source.
        assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)
        assertEquals("uid_B", repository.currentUser.first()?.id)

        // With no authenticated Firebase user the write still fails closed rather than falling
        // back to uid_B, and nothing is attributed to uid_A or uid_B.
        assertFalse(attemptToSend())
        val stored = db.chatMessageDao().getMessagesForJob("job_1").first()
        assertTrue("nothing may be persisted", stored.isEmpty())
        assertTrue(db.chatMessageDao().getMessagesAuthoredBy("uid_B").first().isEmpty())
    }

    // --- TEST E: authorship is uid-to-uid, never display text --------------------------------

    @Test
    fun `test E a message authored by uid_A is never treated as uid_B's`() = runBlocking {
        val byA = message("msg_A", senderId = "uid_A", senderName = "You (Nakawa)")
        db.jobPostDao().insertJob(parentJob())
        db.chatMessageDao().insertMessage(byA)

        // With no authenticated Firebase user the predicate fails closed: uid_B (and any other
        // account) can never claim uid_A's message.
        assertFalse(
            "a message with no authenticated viewer is nobody's message",
            repository.isMessageAuthoredByCurrentUser(byA)
        )

        // The uid-keyed query still separates the two authors correctly.
        assertEquals(
            listOf("msg_A"),
            db.chatMessageDao().getMessagesAuthoredBy("uid_A").first().map { it.id }
        )
        assertTrue(db.chatMessageDao().getMessagesAuthoredBy("uid_B").first().isEmpty())
    }

    @Test
    fun `test E2 authorship ignores senderName`() = runBlocking {
        // Two DIFFERENT accounts posting messages that carry the IDENTICAL display text
        // "You (Nakawa)". The display string cannot discriminate authorship; the uid can.
        db.jobPostDao().insertJob(parentJob())
        db.chatMessageDao().insertMessage(message("msg_A", senderId = "uid_A", senderName = "You (Nakawa)"))
        db.chatMessageDao().insertMessage(message("msg_B", senderId = "uid_B", senderName = "You (Nakawa)"))

        assertEquals(
            listOf("msg_A"),
            db.chatMessageDao().getMessagesAuthoredBy("uid_A").first().map { it.id }
        )
        assertEquals(
            listOf("msg_B"),
            db.chatMessageDao().getMessagesAuthoredBy("uid_B").first().map { it.id }
        )
        // Neither is ever "mine" while unauthenticated.
        assertFalse(repository.isMessageAuthoredByCurrentUser(message("msg_A", senderId = "uid_A")))
    }

    // --- TEST F: legacy / system messages have an unknown author --------------------------

    @Test
    fun `test F a legacy message with no senderId is never claimed`() = runBlocking {
        val legacy = message("msg_legacy", senderId = null, senderName = "Robert K. (Sunnyside)")
        db.jobPostDao().insertJob(parentJob())
        db.chatMessageDao().insertMessage(legacy)

        assertNull("a pre-F5 message keeps an unknown author", legacy.senderId)

        // The display name must never be parsed into an ownership claim.
        assertFalse(
            "a legacy message must not be claimed by the current user",
            repository.isMessageAuthoredByCurrentUser(legacy)
        )
        assertTrue(db.chatMessageDao().getMessagesAuthoredBy("Robert K. (Sunnyside)").first().isEmpty())

        // The row itself is preserved (no destructive migration).
        assertNotNull(db.chatMessageDao().getMessagesForJob("job_1").first().find { it.id == "msg_legacy" })
    }

    @Test
    fun `test F2 a system notification is never treated as the current user's message`() = runBlocking {
        val system = message("msg_system", senderId = null, senderName = "System", isSystemNotification = true)
        db.jobPostDao().insertJob(parentJob())
        db.chatMessageDao().insertMessage(system)

        assertNull("system rows have no human author", system.senderId)
        assertFalse(repository.isMessageAuthoredByCurrentUser(system))
        assertTrue(db.chatMessageDao().getMessagesAuthoredBy("uid_A").first().isEmpty())
    }
}