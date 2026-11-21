package com.example.ui.viewmodel

import com.example.data.db.UserProfileEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * SEC-009 / G1 / G3 / H1 / H8: Fail-Closed Worker Dashboard Authorization Security Tests.
 *
 * Verifies that worker dashboard access fails closed under all circumstances:
 * 1. No authenticated user -> DENY
 * 2. Authenticated client -> DENY for Worker Dashboard
 * 3. Worker with no verification state -> DENY
 * 4. Worker verification pending -> DENY
 * 5. Worker verification submitted -> DENY
 * 6. Worker awaiting approval -> DENY
 * 7. Worker rejected -> DENY
 * 8. Worker approved but not activated -> DENY
 * 9. Worker approved + activated -> ALLOW
 * 10. Authorization state unavailable/error -> DENY
 */
@RunWith(JUnit4::class)
class WorkerAuthorizationSecurityTest {

    private fun approvedWorkerProfile(
        id: String = "worker_valid_uid",
        initialRole: String = "WORKER",
        workerKycStatus: String = "APPROVED",
        trainingCompleted: Boolean = true,
        isWorkerActivated: Boolean = true
    ) = UserProfileEntity(
        id = id,
        phoneNumber = "+256 701 234 567",
        fullName = "Verified Specialist",
        district = "Sunnyside",
        initialRole = initialRole,
        currentActiveMode = initialRole,
        workerKycStatus = workerKycStatus,
        trainingCompleted = trainingCompleted,
        isWorkerActivated = isWorkerActivated,
        isOnboardingCompleted = true
    )

    // State 1: No authenticated user -> DENY
    @Test
    fun `state 1 - unauthenticated user is strictly denied worker dashboard access`() {
        val fullyApprovedWorker = approvedWorkerProfile()
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = false,
            user = fullyApprovedWorker
        )
        assertFalse("Unauthenticated user must NEVER access worker dashboard", isAuthorized)
    }

    // State 2: Authenticated client -> DENY for Worker Dashboard
    @Test
    fun `state 2 - authenticated client is strictly denied worker dashboard access`() {
        val client = UserProfileEntity(
            id = "client_uid_101",
            phoneNumber = "+256 702 345 678",
            fullName = "Resident Client",
            district = "Nakawa",
            initialRole = "CLIENT",
            currentActiveMode = "CLIENT",
            workerKycStatus = "NOT_APPLIED",
            trainingCompleted = false,
            isWorkerActivated = false,
            isOnboardingCompleted = true
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = client
        )
        assertFalse("Authenticated client must NEVER access worker dashboard", isAuthorized)
    }

    @Test
    fun `state 2b - client with forged or tampered approval fields is strictly denied`() {
        // Even if a malicious client manages to write APPROVED / activated into their local profile,
        // their non-WORKER initialRole must block access.
        val tamperedClient = UserProfileEntity(
            id = "client_tampered_102",
            phoneNumber = "+256 702 345 679",
            fullName = "Attacking Client",
            district = "Nakawa",
            initialRole = "CLIENT",
            currentActiveMode = "WORKER",
            workerKycStatus = "APPROVED",
            trainingCompleted = true,
            isWorkerActivated = true,
            isOnboardingCompleted = true
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = tamperedClient
        )
        assertFalse("Client with tampered worker fields must NEVER access worker dashboard", isAuthorized)
    }

    // State 3: Worker with no verification state -> DENY
    @Test
    fun `state 3 - worker with unverified or not applied status is denied`() {
        val unverifiedWorker = approvedWorkerProfile(workerKycStatus = "UNVERIFIED", isWorkerActivated = false)
        assertFalse(
            "Worker with UNVERIFIED status must be denied",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = unverifiedWorker)
        )

        val notAppliedWorker = approvedWorkerProfile(workerKycStatus = "NOT_APPLIED", isWorkerActivated = false)
        assertFalse(
            "Worker with NOT_APPLIED status must be denied",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = notAppliedWorker)
        )
    }

    // State 4: Worker verification pending -> DENY
    @Test
    fun `state 4 - worker verification pending is denied`() {
        val pendingWorker = approvedWorkerProfile(
            workerKycStatus = "PENDING",
            isWorkerActivated = false
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = pendingWorker
        )
        assertFalse("Worker with pending verification must be denied", isAuthorized)
    }

    // State 5: Worker verification submitted -> DENY
    @Test
    fun `state 5 - worker verification submitted is denied`() {
        val submittedWorker = approvedWorkerProfile(
            workerKycStatus = "SUBMITTED",
            isWorkerActivated = false
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = submittedWorker
        )
        assertFalse("Worker with submitted verification must be denied", isAuthorized)
    }

    // State 6: Worker awaiting approval -> DENY
    @Test
    fun `state 6 - worker awaiting admin review is denied`() {
        val reviewWorker = approvedWorkerProfile(
            workerKycStatus = "PENDING_REVIEW",
            isWorkerActivated = false
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = reviewWorker
        )
        assertFalse("Worker awaiting admin approval must be denied", isAuthorized)
    }

    // State 7: Worker rejected -> DENY
    @Test
    fun `state 7 - worker rejected is denied`() {
        val rejectedWorker = approvedWorkerProfile(
            workerKycStatus = "REJECTED",
            isWorkerActivated = false
        )
        assertFalse(
            "Rejected worker must be denied",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = rejectedWorker)
        )

        val changesRequiredWorker = approvedWorkerProfile(
            workerKycStatus = "CHANGES_REQUIRED",
            isWorkerActivated = false
        )
        assertFalse(
            "Worker with CHANGES_REQUIRED status must be denied",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = changesRequiredWorker)
        )

        val suspendedWorker = approvedWorkerProfile(
            workerKycStatus = "SUSPENDED",
            isWorkerActivated = false
        )
        assertFalse(
            "Suspended worker must be denied",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = suspendedWorker)
        )
    }

    // State 8: Worker approved but not activated -> DENY
    @Test
    fun `state 8 - worker approved but not activated is denied`() {
        val inactiveWorker = approvedWorkerProfile(
            workerKycStatus = "APPROVED",
            trainingCompleted = true,
            isWorkerActivated = false
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = inactiveWorker
        )
        assertFalse("Worker approved but not activated must be denied", isAuthorized)
    }

    @Test
    fun `state 8b - worker approved and activated but training not completed is denied`() {
        val untrainedWorker = approvedWorkerProfile(
            workerKycStatus = "APPROVED",
            trainingCompleted = false,
            isWorkerActivated = true
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = untrainedWorker
        )
        assertFalse("Worker without completed training must be denied", isAuthorized)
    }

    // State 9: Worker approved + activated -> ALLOW
    @Test
    fun `state 9 - worker registered, approved, trained, and activated is allowed`() {
        val fullyAuthorizedWorker = approvedWorkerProfile(
            id = "worker_auth_verified_999",
            initialRole = "WORKER",
            workerKycStatus = "APPROVED",
            trainingCompleted = true,
            isWorkerActivated = true
        )
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = fullyAuthorizedWorker
        )
        assertTrue("Fully registered, approved, and activated worker must be granted access", isAuthorized)
    }

    // State 10: Authorization state unavailable or error -> DENY
    @Test
    fun `state 10 - null user profile is denied`() {
        val isAuthorized = SkillsExchangeViewModel.isWorkerAuthorized(
            authenticated = true,
            user = null
        )
        assertFalse("Null user profile must fail closed", isAuthorized)
    }

    @Test
    fun `state 10b - blank id or demo placeholder profile is denied`() {
        val blankIdWorker = approvedWorkerProfile(id = "")
        assertFalse(
            "Worker profile with blank ID must fail closed",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = blankIdWorker)
        )

        val demoProfile = approvedWorkerProfile(id = SkillsExchangeViewModel.DEMO_PROFILE_ID)
        assertFalse(
            "Legacy demo profile ('user_default_me') must never satisfy worker gate",
            SkillsExchangeViewModel.isWorkerAuthorized(authenticated = true, user = demoProfile)
        )
    }
}
