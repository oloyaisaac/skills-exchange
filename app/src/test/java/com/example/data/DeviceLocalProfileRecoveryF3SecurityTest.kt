package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.UserProfileEntity
import com.example.data.remote.FirebaseService
import com.example.data.repository.SkillsExchangeRepository
import com.example.ui.viewmodel.SkillsExchangeViewModel
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
 * C2-2 Fix #7 — audit finding **F3 remainder**: eliminate unbound device-local profile recovery.
 *
 * The invariant under test:
 *
 * > Once Firebase authentication exists, the Firebase Auth UID is the authoritative identity.
 * > A local Room profile must NEVER be selected as the current authenticated user's profile
 * > because it is the newest row, matches a phone number, matches a display field, or is the
 * > seeded demo/default profile. With no valid UID the resolution fails closed.
 *
 * Two genuine gaps were found and fixed (see the production diff):
 *  1. `SkillsExchangeRepository.getCurrentUserByUid` carried its OWN weaker identity rule
 *     (`isNotBlank() && != "user_default_me"`) instead of the shared resolver, so it accepted
 *     presentation text and the `"system"`/`"unknown"` sentinels. It backs `currentUserByUid`,
 *     the app's only current-user source.
 *  2. `SkillsExchangeViewModel.resolveSessionOwnerId` used a bare `isNotBlank()`, so a placeholder
 *     could be persisted as the session owner and later compared back in during restore.
 *
 * Both now delegate to the single `resolveAuthoritativeUid` rule, so there is exactly one
 * definition of a valid identity in the codebase.
 *
 * NOTE: this Robolectric context has no Firebase user, so every "Firebase is authenticated"
 * scenario drives the pure resolvers the production code actually calls, while the database
 * assertions run against a real Room instance.
 *
 * NOTE: `user_profiles` carries a UNIQUE index on `phoneNumber`, so seeded rows need distinct
 * numbers; Test B therefore creates a *conflicting* phone situation rather than two identical ones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DeviceLocalProfileRecoveryF3SecurityTest {

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

    private fun profile(
        id: String,
        phoneNumber: String,
        fullName: String = "Person $id",
        updatedAtTimestamp: Long = 1_000L
    ) = UserProfileEntity(
        id = id,
        phoneNumber = phoneNumber,
        fullName = fullName,
        district = "Nakawa",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "UNVERIFIED",
        isWorkerActivated = false,
        trainingCompleted = false,
        isOnboardingCompleted = true,
        updatedAtTimestamp = updatedAtTimestamp
    )

    // --- TEST A: the Firebase UID wins over a NEWER Room row ------------------------------

    @Test
    fun `test A the authenticated firebase uid wins over a newer room row`() = runBlocking {
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", updatedAtTimestamp = 1_000L))
        db.userProfileDao().insertUserProfile(profile("uid_B", "+256 700 000 602", updatedAtTimestamp = 9_999L))

        // The trap: the legacy newest-row query answers with the OTHER, newer account.
        assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)

        // Authenticated as uid_A => identity must be uid_A, never the newest row.
        val uidA = SkillsExchangeRepository.resolveAuthoritativeUid("uid_A")
        assertEquals("uid_A", uidA)
        assertEquals("uid_A", repository.getCurrentUserByUid(uidA).first()?.id)

        // ...and symmetrically, authenticating as uid_B resolves uid_B (never "the newest").
        assertEquals("uid_B", repository.getCurrentUserByUid("uid_B").first()?.id)
    }

    // --- TEST B: a phone-number conflict cannot change identity --------------------------

    @Test
    fun `test B a phone conflict cannot change the authenticated identity`() = runBlocking {
        // uid_B is the newest row and carries a conflicting phone; uid_A authenticates as uid_A.
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", updatedAtTimestamp = 1_000L))
        db.userProfileDao().insertUserProfile(
            profile("uid_B", "+256 700 000 609", fullName = "Victim", updatedAtTimestamp = 9_999L)
        )

        val authenticatedUid = SkillsExchangeRepository.resolveAuthoritativeUid("uid_A")
        val resolved = repository.getCurrentUserByUid(authenticatedUid).first()

        assertNotNull(resolved)
        assertEquals("uid_A", resolved?.id)
        assertEquals("+256 700 000 601", resolved?.phoneNumber)

        // The production loginUser rule: a row is reusable ONLY when its id equals the
        // authenticated uid, so a phone collision inherits nothing.
        val profile = SkillsExchangeViewModel.resolveAuthenticatedProfile(
            authenticatedUid = authenticatedUid!!,
            existing = db.userProfileDao().getUserProfile("uid_B").first(),
            phone = "+256 700 000 609",
            fullName = "New Person",
            district = "Nakawa",
            initialRole = "WORKER"
        )
        assertEquals("uid_A", profile.id)
        assertEquals("New Person", profile.fullName)
        assertEquals("fail closed for a non-owner", "UNVERIFIED", profile.workerKycStatus)
        assertFalse(profile.isWorkerActivated)
        assertFalse(profile.trainingCompleted)
    }

    // --- TEST C: no phone lookup can establish authenticated identity ---------------------

    @Test
    fun `test C no phone lookup can establish authenticated identity`() = runBlocking {
        // A real account exists, so a phone-shaped lookup has something it COULD wrongly match.
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", updatedAtTimestamp = 1_000L))

        // The device-local phone selector is GONE: getUserProfileByPhone and the DAO query
        // getUserProfileByPhoneNumber were removed in Fix #7 (both had zero callers). Asserted by
        // reflection so a future re-introduction fails the suite instead of passing silently.
        val phoneSelectors = SkillsExchangeRepository::class.java.methods
            .filter { it.name.contains("ByPhone", ignoreCase = true) }
        phoneSelectors.forEach { method ->
            assertFalse(
                "no repository method may select a profile by phone: ${method.name}",
                method.name.startsWith("getUserProfileByPhone")
            )
        }

        // The real invariant is NOT that the resolver parses phone syntax -- it is that no code
        // path can SELECT a profile by a phone number. So prove the phone values are inert as
        // selectors: a phone-shaped value must never be treated as an identity, and critically
        // resolveAuthenticatedProfile keys strictly on the authenticated UID, so a phone can only
        // ever be stored AS DATA on the uid's own row -- never used to FIND that row.
        val phone = "+256 772 198 420"
        // A spaced phone carries whitespace, which the shared rule rejects outright.
        assertNull(SkillsExchangeRepository.resolveAuthoritativeUid(phone))
        // A compacted phone is syntactically uid-shaped, so it PASSES the format filter -- which is
        // exactly why the defence must be "look up by uid only", not "reject phone strings".
        // So assert the lookup itself: a phone value matches no row, even though uid_A exists and
        // carries a phone number.
        assertNull(
            "a phone value must never resolve a profile row",
            repository.getCurrentUserByUid("+256700000601").first()
        )
        assertNotNull("uid_A still resolves normally", repository.getCurrentUserByUid("uid_A").first())
    }

    // --- TEST D: a missing UID-bound profile fails closed -------------------------------

    @Test
    fun `test D a missing uid bound profile fails closed without any fallback`() = runBlocking {
        // The device holds only OTHER accounts plus the seeded demo row. Nothing belongs to uid_Z.
        db.userProfileDao().insertUserProfile(profile("uid_B", "+256 700 000 602", updatedAtTimestamp = 9_999L))
        db.userProfileDao().insertUserProfile(
            profile(SkillsExchangeViewModel.DEMO_PROFILE_ID, "+256 700 000 603", updatedAtTimestamp = 8_888L)
        )

        // uid_Z is authenticated but has no local row.
        assertEquals("uid_Z", SkillsExchangeRepository.resolveAuthoritativeUid("uid_Z"))
        assertNull("must fail closed: no newest-row fallback", repository.getCurrentUserByUid("uid_Z").first())
        // ...and never substitutes the demo row, even though it is the newest.
        assertNull(
            "the demo uid must not resolve a profile either",
            repository.getCurrentUserByUid(SkillsExchangeViewModel.DEMO_PROFILE_ID).first()
        )
    }

    // --- TEST E: with no Firebase authentication there is no identity --------------------

    @Test
    fun `test E no firebase authentication yields no authenticated profile`() = runBlocking {
        db.userProfileDao().insertUserProfile(profile("uid_A", "+256 700 000 601", updatedAtTimestamp = 1_000L))
        db.userProfileDao().insertUserProfile(profile("uid_B", "+256 700 000 602", updatedAtTimestamp = 9_999L))

        // A null Firebase user (the Robolectric default) must produce no identity at all — and
        // specifically NOT the newest row, which is uid_B.
        assertNull(SkillsExchangeRepository.resolveAuthoritativeUid(null))
        assertNull(repository.getCurrentUserByUid(null).first())
        // The newer row stays reachable only via the legacy non-authoritative helper, which is
        // exactly why no production path may consume it.
        assertEquals("uid_B", db.userProfileDao().getCurrentUser().first()?.id)
    }

    // --- TEST F: the demo/default profile can never become the authenticated identity ----

    @Test
    fun `test F the demo default profile can never become the authenticated identity`() = runBlocking {
        val demoId = SkillsExchangeViewModel.DEMO_PROFILE_ID
        db.userProfileDao().insertUserProfile(
            profile(demoId, "+256 772 198 420", fullName = "Sarah Namukasa", updatedAtTimestamp = 9_999L)
        )

        // The seeded placeholder is not a resolvable identity in the shared rule...
        assertNull(SkillsExchangeRepository.resolveAuthoritativeUid(demoId))
        // ...so it can never be looked up as the current user.
        assertNull(repository.getCurrentUserByUid(demoId).first())

        // Nor can it be persisted as the session owner (the second Fix #7 gap): previously the
        // bare isNotBlank() check would have accepted "user_default_me" and the sentinels.
        assertNull(SkillsExchangeViewModel.resolveSessionOwnerId(demoId, null))
        for (sentinel in listOf("system", "unknown", "anonymous", "guest", "none", "John Doe", "You (Nakawa)")) {
            assertNull(
                "'$sentinel' must not become a session owner",
                SkillsExchangeViewModel.resolveSessionOwnerId(sentinel, null)
            )
            assertNull(
                "'$sentinel' must not become a session owner via the fallback",
                SkillsExchangeViewModel.resolveSessionOwnerId(null, sentinel)
            )
        }
    }

    // --- TEST G: a legitimate UID-bound profile still resolves normally ----------------

    @Test
    fun `test G an existing legitimate profile still resolves`() = runBlocking {
        db.userProfileDao().insertUserProfile(
            profile("uid_A", "+256 700 000 601", fullName = "Real Person", updatedAtTimestamp = 1_000L)
        )

        val uid = SkillsExchangeRepository.resolveAuthoritativeUid("uid_A")
        assertEquals("uid_A", uid)
        val resolved = repository.getCurrentUserByUid(uid).first()

        assertNotNull("the genuine account must still resolve", resolved)
        assertEquals("uid_A", resolved?.id)
        assertEquals("Real Person", resolved?.fullName)
        assertEquals("+256 700 000 601", resolved?.phoneNumber)

        // The verified-uid session owner is still accepted (Fix #7 must not break sign-in).
        assertEquals("uid_A", SkillsExchangeViewModel.resolveSessionOwnerId("uid_A", null))
        assertTrue(SkillsExchangeRepository.resolveAuthoritativeUid("uid_A") != null)
    }
}