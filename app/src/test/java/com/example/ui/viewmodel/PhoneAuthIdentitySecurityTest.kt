package com.example.ui.viewmodel

import com.example.data.db.UserProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * C2-1: identity ownership tests for phone-authenticated sign-in.
 *
 * Reproduces the escalation the audit found: a newly authenticated phone user whose number
 * matched another person's stored profile used to receive that profile's
 * `workerKycStatus = APPROVED`, `isWorkerActivated` and `trainingCompleted` (plus other personal
 * data), because `loginUser` copied the matched row onto the new UID.
 *
 * [SkillsExchangeViewModel.resolveAuthenticatedProfile] is the production policy used by
 * `loginUser`; these tests drive it with exactly the hostile inputs the audit described and then
 * re-check the result through the untouched worker gate ([isWorkerAuthorized]), so a regression
 * in either layer fails the suite.
 */
@RunWith(JUnit4::class)
class PhoneAuthIdentitySecurityTest {

    private val sharedPhone = "+256 772 198 420"

    private fun approvedWorker(
        id: String,
        phone: String = sharedPhone,
        fullName: String = "Approved Worker"
    ) = UserProfileEntity(
        id = id,
        phoneNumber = phone,
        fullName = fullName,
        district = "Sunnyside",
        initialRole = "WORKER",
        currentActiveMode = "WORKER",
        workerKycStatus = "APPROVED",
        isWorkerActivated = true,
        trainingCompleted = true,
        nationalIdNumber = "CM31019880042E",
        isOnboardingCompleted = true
    )

    private fun resolve(
        authenticatedUid: String,
        existing: UserProfileEntity?,
        phone: String = sharedPhone,
        fullName: String = "Typed Name",
        district: String = "Nakawa",
        initialRole: String = "CLIENT"
    ) = SkillsExchangeViewModel.resolveAuthenticatedProfile(
        authenticatedUid = authenticatedUid,
        existing = existing,
        phone = phone,
        fullName = fullName,
        district = district,
        initialRole = initialRole
    )

    // --- Test D: a phone match must not hand over worker privileges ---

    @Test
    fun `phone match with another uid never inherits worker privileges`() {
        val victim = approvedWorker(id = "victim_worker_uid")
        val profile = resolve(
            authenticatedUid = "attacker_phone_auth_uid",
            existing = victim,
            fullName = "New Person",
            initialRole = "WORKER"
        )

        // Ownership: the authenticated UID is the id, never the matched record's id.
        assertEquals("attacker_phone_auth_uid", profile.id)
        assertNotEquals(victim.id, profile.id)

        // No privileged state is copied.
        assertEquals("UNVERIFIED", profile.workerKycStatus)
        assertFalse(profile.isWorkerActivated)
        assertFalse(profile.trainingCompleted)

        // The five-layer worker gate still denies the new identity.
        assertFalse(SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = profile))
    }

    @Test
    fun `phone match does not copy the other account's personal data`() {
        val victim = approvedWorker(id = "victim_worker_uid")
        val profile = resolve(
            authenticatedUid = "new_phone_uid",
            existing = victim,
            fullName = "New Person",
            district = "Entebbe"
        )

        assertEquals("New Person", profile.fullName)
        assertEquals("Entebbe", profile.district)
        assertNotEquals(victim.fullName, profile.fullName)
        assertNotEquals(victim.district, profile.district)
        assertNotEquals(victim.nationalIdNumber, profile.nationalIdNumber)
    }

    @Test
    fun `client registering on a worker phone number keeps the client role and is denied worker access`() {
        val victim = approvedWorker(id = "victim_worker_uid")
        val profile = resolve(
            authenticatedUid = "client_uid_new",
            existing = victim,
            fullName = "New Client",
            initialRole = "CLIENT"
        )

        assertEquals("CLIENT", profile.initialRole)
        assertEquals("CLIENT", profile.currentActiveMode)
        assertEquals("NOT_APPLIED", profile.workerKycStatus)
        assertFalse(profile.isWorkerActivated)
        assertFalse(SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = profile))
    }

    // --- Test E: a genuine same-account recovery is still recognised ---

    @Test
    fun `record owned by the authenticated uid keeps the privileges an admin granted`() {
        val own = approvedWorker(id = "worker_uid_1")
        val profile = resolve(
            authenticatedUid = "worker_uid_1",
            existing = own,
            fullName = "",
            district = ""
        )

        assertEquals("worker_uid_1", profile.id)
        assertEquals("APPROVED", profile.workerKycStatus)
        assertTrue(profile.isWorkerActivated)
        assertTrue(profile.trainingCompleted)
        assertEquals("Approved Worker", profile.fullName)
        assertEquals("Sunnyside", profile.district)
        assertTrue(SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = profile))
    }

    @Test
    fun `demo placeholder row is never adopted as a privileged account`() {
        val demo = approvedWorker(id = SkillsExchangeViewModel.DEMO_PROFILE_ID)
        val profile = resolve(
            authenticatedUid = SkillsExchangeViewModel.DEMO_PROFILE_ID,
            existing = demo,
            initialRole = "WORKER"
        )

        assertEquals("UNVERIFIED", profile.workerKycStatus)
        assertFalse(profile.isWorkerActivated)
        assertFalse(profile.trainingCompleted)
        assertFalse(SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = profile))
    }

    // --- Test F: unknown or ambiguous identity fails closed ---

    @Test
    fun `unknown identity is created fail closed in both roles`() {
        val newWorker = resolve(
            authenticatedUid = "fresh_uid",
            existing = null,
            phone = "+256 700 000 001",
            fullName = "Fresh Worker",
            initialRole = "WORKER"
        )
        assertEquals("WORKER", newWorker.initialRole)
        assertEquals("UNVERIFIED", newWorker.workerKycStatus)
        assertFalse(newWorker.isWorkerActivated)
        assertFalse(newWorker.trainingCompleted)
        assertFalse(SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = newWorker))

        val newClient = resolve(
            authenticatedUid = "fresh_uid_2",
            existing = null,
            phone = "+256 700 000 002",
            fullName = "Fresh Client",
            initialRole = "CLIENT"
        )
        assertEquals("NOT_APPLIED", newClient.workerKycStatus)
        assertFalse(SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = newClient))
    }

    // --- Test C: the authenticated Firebase UID is the profile and session identity ---

    @Test
    fun `every resolved profile is keyed to the authenticated uid`() {
        val victim = approvedWorker(id = "victim_worker_uid")
        val cases = listOf(
            "uid_a" to resolve(authenticatedUid = "uid_a", existing = null),
            "uid_b" to resolve(authenticatedUid = "uid_b", existing = victim),
            "uid_c" to resolve(authenticatedUid = "uid_c", existing = approvedWorker(id = "uid_c"))
        )
        cases.forEach { (expectedUid, profile) ->
            assertEquals("profile must be keyed to the authenticated uid", expectedUid, profile.id)
        }
    }

    @Test
    fun `session owner is the phone verified firebase uid`() {
        assertEquals(
            "phone_auth_uid",
            SkillsExchangeViewModel.resolveSessionOwnerId("phone_auth_uid", "stale_room_row_id")
        )
        assertEquals("phone_auth_uid", SkillsExchangeViewModel.resolveSessionOwnerId("phone_auth_uid", null))
        assertEquals(
            "current_firebase_uid",
            SkillsExchangeViewModel.resolveSessionOwnerId(null, "current_firebase_uid")
        )
    }

    @Test
    fun `session owner fails closed when no authenticated identity exists`() {
        assertNull(SkillsExchangeViewModel.resolveSessionOwnerId(null, null))
        assertNull(SkillsExchangeViewModel.resolveSessionOwnerId("   ", ""))
    }
}
