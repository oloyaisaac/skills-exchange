package com.example.ui.viewmodel

import com.example.data.db.UserProfileEntity
import com.example.ui.viewmodel.SkillsExchangeViewModel.RestoreIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * C2-2 Fix #2 (F2): session restore must never trust the cached DataStore identity.
 *
 * `restoreSession()` used to adopt whatever `SessionManager.getUserId()` held — after only an
 * expiry and a device check — and then pick the profile from the legacy newest-row query. A cached
 * UID left behind by a PREVIOUS Firebase account could therefore survive a sign-in change (or even
 * a signed-out state) as this device's identity.
 *
 * The production policy is [SkillsExchangeViewModel.decideRestoreIdentity] +
 * [SkillsExchangeViewModel.resolveRestoredProfile], exercised here exactly as the restore path
 * drives them. FirebaseAuth is the authority, the cached UID is only ever compared against it, and
 * the restored profile must be the Room row whose id equals the authenticated Firebase UID.
 *
 * Each case re-checks the outcome through the untouched worker gate
 * ([SkillsExchangeViewModel.isWorkerAuthorized]) so a regression that let another account's
 * approved-worker state ride along with the restored identity fails the suite too.
 */
@RunWith(JUnit4::class)
class RestoreSessionIdentitySecurityTest {

    private val demoProfileId = SkillsExchangeViewModel.DEMO_PROFILE_ID

    private fun approvedWorker(
        id: String,
        phoneNumber: String = "+256 700 000 301",
        updatedAtTimestamp: Long = 1_000L
    ) = UserProfileEntity(
        id = id,
        phoneNumber = phoneNumber,
        fullName = "Account $id",
        district = "Nakawa",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "APPROVED",
        isWorkerActivated = true,
        trainingCompleted = true,
        nationalIdNumber = "CM31019880042E",
        isOnboardingCompleted = true,
        updatedAtTimestamp = updatedAtTimestamp
    )

    private fun legacyDemoProfile() = UserProfileEntity(
        id = demoProfileId,
        phoneNumber = "+256 772 198 420",
        fullName = "Sarah Namukasa",
        district = "Sunnyside",
        initialRole = "CLIENT",
        currentActiveMode = "CLIENT",
        workerKycStatus = "APPROVED",
        isWorkerActivated = true,
        trainingCompleted = true,
        isOnboardingCompleted = true,
        updatedAtTimestamp = 9_000L
    )

    /** Asserts Firebase produced a usable identity, then returns it. */
    private fun authoritative(cachedUid: String?, firebaseUid: String?): RestoreIdentity.Authoritative {
        val identity = SkillsExchangeViewModel.decideRestoreIdentity(cachedUid, firebaseUid)
        assertTrue(
            "Firebase must supply the authoritative identity, got $identity",
            identity is RestoreIdentity.Authoritative
        )
        return identity as RestoreIdentity.Authoritative
    }

    // --- Test A: cached UID matches Firebase ---

    @Test
    fun `A matching cached uid restores the firebase identity and its own room profile`() {
        val identity = authoritative(cachedUid = "uid_A", firebaseUid = "uid_A")

        assertEquals("Firebase UID is the restored identity", "uid_A", identity.uid)
        assertTrue("a cache that matches Firebase may be reused", identity.cachedUidTrusted)

        val restored = SkillsExchangeViewModel.resolveRestoredProfile(
            authenticatedUid = identity.uid,
            row = approvedWorker("uid_A")
        )
        assertEquals("uid_A", restored?.id)
        assertEquals("Account uid_A", restored?.fullName)
    }

    // --- Test B: stale cached UID (different account signed in) ---

    @Test
    fun `B stale cached uid is discarded and the firebase uid becomes the identity`() {
        // The device still holds uid_A in DataStore, but Firebase is now signed in as uid_B.
        val identity = authoritative(cachedUid = "uid_A", firebaseUid = "uid_B")

        assertEquals("uid_B", identity.uid)
        assertFalse("a cached uid of another account must never be trusted", identity.cachedUidTrusted)
        assertNotEquals("uid_A", identity.uid)

        // Room holds both rows; the stale account must never be selected for uid_B…
        val staleRow = approvedWorker("uid_A")
        assertNull(
            "uid_A must never be restored as the current user",
            SkillsExchangeViewModel.resolveRestoredProfile(identity.uid, staleRow)
        )
        // …while uid_B's own row is the profile that is used.
        val restored = SkillsExchangeViewModel.resolveRestoredProfile(
            authenticatedUid = identity.uid,
            row = approvedWorker("uid_B", phoneNumber = "+256 700 000 302")
        )
        assertEquals("uid_B", restored?.id)

        // And the stale account's APPROVED/activated worker state must not ride along.
        assertFalse(
            "stale uid_A approval must not authorise uid_B",
            SkillsExchangeViewModel.isWorkerAuthorized(
                authenticated = true,
                user = SkillsExchangeViewModel.resolveRestoredProfile(identity.uid, staleRow)
            )
        )
    }

    // --- Test C: cached UID but no Firebase authentication ---

    @Test
    fun `C cached uid without firebase authentication is never resurrected`() {
        val staleRow = approvedWorker("uid_A")

        // Signed out (or Firebase unavailable): the cached session fails closed.
        assertEquals(
            RestoreIdentity.None,
            SkillsExchangeViewModel.decideRestoreIdentity(cachedUid = "uid_A", firebaseUid = null)
        )
        // Blank / whitespace / placeholder Firebase identities are equally unusable.
        assertEquals(
            RestoreIdentity.None,
            SkillsExchangeViewModel.decideRestoreIdentity(cachedUid = "uid_A", firebaseUid = "")
        )
        assertEquals(
            RestoreIdentity.None,
            SkillsExchangeViewModel.decideRestoreIdentity(cachedUid = "uid_A", firebaseUid = "   ")
        )
        assertEquals(
            RestoreIdentity.None,
            SkillsExchangeViewModel.decideRestoreIdentity(cachedUid = "uid_A", firebaseUid = demoProfileId)
        )
        assertEquals(
            RestoreIdentity.None,
            SkillsExchangeViewModel.decideRestoreIdentity(cachedUid = null, firebaseUid = null)
        )
        // Pseudo identities must not stand in for a Firebase account either.
        assertEquals(
            RestoreIdentity.None,
            SkillsExchangeViewModel.decideRestoreIdentity(cachedUid = "unknown", firebaseUid = null)
        )

        // With no authoritative identity nothing may be resolved into a current user.
        assertNull(SkillsExchangeViewModel.resolveRestoredProfile(null, staleRow))
        assertNull(SkillsExchangeViewModel.resolveRestoredProfile("", staleRow))
        assertNull(SkillsExchangeViewModel.resolveRestoredProfile("   ", staleRow))
        assertNull(SkillsExchangeViewModel.resolveRestoredProfile(demoProfileId, staleRow))
    }

    // --- Test D: Firebase UID exists but no Room profile for it ---

    @Test
    fun `D missing profile for the firebase uid never falls back to another room row`() {
        val identity = authoritative(cachedUid = "uid_A", firebaseUid = "uid_B")

        // No row for uid_B at all: fail closed rather than adopt anything else.
        assertNull(SkillsExchangeViewModel.resolveRestoredProfile(identity.uid, null))
        // A row exists but belongs to somebody else — "the newest row in Room" is not an identity.
        assertNull(
            SkillsExchangeViewModel.resolveRestoredProfile(
                identity.uid,
                approvedWorker("uid_A", updatedAtTimestamp = 9_000L)
            )
        )
        // Only a row owned by the Firebase UID is a legitimate recovery.
        assertEquals(
            "uid_B",
            SkillsExchangeViewModel.resolveRestoredProfile(
                identity.uid,
                approvedWorker("uid_B", phoneNumber = "+256 700 000 302")
            )?.id
        )

        // The fail-closed fallback (existing C2-1 mechanism) is keyed by the Firebase UID and
        // grants nothing: no worker approval, activation or training credit is inherited.
        val recovered = SkillsExchangeViewModel.resolveAuthenticatedProfile(
            authenticatedUid = identity.uid,
            existing = approvedWorker("uid_A"),
            phone = "",
            fullName = "",
            district = "",
            initialRole = "WORKER"
        )
        assertEquals("the fallback identity stays keyed by the Firebase UID", "uid_B", recovered.id)
        assertFalse(
            "a foreign approved-worker row must not authorise the recovered identity",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = recovered)
        )
    }

    // --- Test E: seeded demo placeholder ---

    @Test
    fun `E demo placeholder is never the authenticated users profile`() {
        // Firebase has a real UID; Room holds only the seeded placeholder.
        val identity = authoritative(cachedUid = null, firebaseUid = "uid_A")
        assertNull(
            "the demo row must never satisfy the authenticated identity",
            SkillsExchangeViewModel.resolveRestoredProfile(identity.uid, legacyDemoProfile())
        )

        // A cached placeholder is not an identity…
        assertEquals(
            RestoreIdentity.None,
            SkillsExchangeViewModel.decideRestoreIdentity(cachedUid = demoProfileId, firebaseUid = null)
        )
        // …and it is not a trusted cache entry when Firebase does report a real UID.
        val overDemoCache = authoritative(cachedUid = demoProfileId, firebaseUid = "uid_A")
        assertEquals("uid_A", overDemoCache.uid)
        assertFalse(overDemoCache.cachedUidTrusted)

        // Even addressed by its own id the placeholder can never become the current identity.
        assertNull(
            SkillsExchangeViewModel.resolveRestoredProfile(demoProfileId, legacyDemoProfile())
        )
    }
}
