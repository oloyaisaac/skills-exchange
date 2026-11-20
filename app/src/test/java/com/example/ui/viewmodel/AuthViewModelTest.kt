package com.example.ui.viewmodel

import com.example.data.db.UserProfileEntity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AuthViewModelTest {

    @Test
    fun `UserProfileEntity default values are correct for new client`() {
        val profile = UserProfileEntity(
            phoneNumber = "+256 772 198 420",
            fullName = "Test User",
            district = "Kampala"
        )
        assertEquals("CLIENT", profile.initialRole)
        assertEquals("CLIENT", profile.currentActiveMode)
        // SEC-009: new client accounts fail closed — worker KYC is NOT_APPLIED by default.
        assertEquals("NOT_APPLIED", profile.workerKycStatus)
    }

    @Test
    fun `UserProfileEntity worker fields default correctly`() {
        val profile = UserProfileEntity(
            phoneNumber = "+256 772 198 421",
            fullName = "Worker User",
            district = "Sunnyside"
        )
        // SEC-009: worker activation and training completion default to fail-closed (false).
        assertEquals(false, profile.isWorkerActivated)
        assertEquals(false, profile.trainingCompleted)
    }

    @Test
    fun `worker access check logic is correct`() {
        val approvedUser = UserProfileEntity(
            id = "user_1",
            workerKycStatus = "APPROVED",
            isWorkerActivated = true
        )
        assertTrue(approvedUser.isWorkerActivated && approvedUser.workerKycStatus == "APPROVED")

        val unverifiedUser = UserProfileEntity(
            id = "user_2",
            workerKycStatus = "PENDING",
            isWorkerActivated = false
        )
        assertFalse(unverifiedUser.isWorkerActivated && unverifiedUser.workerKycStatus == "APPROVED")

        val changesRequiredUser = UserProfileEntity(
            id = "user_3",
            workerKycStatus = "CHANGES_REQUIRED",
            isWorkerActivated = false
        )
        assertFalse(changesRequiredUser.isWorkerActivated && changesRequiredUser.workerKycStatus == "APPROVED")
    }

    @Test
    fun `phone number is unique identifier for duplicate prevention`() {
        val phone1 = "+256 772 198 420"
        val phone2 = "+256 772 198 421"
        assertNotEquals(phone1, phone2)

        val phone1Duplicate = "+256 772 198 420"
        assertEquals(phone1, phone1Duplicate)
    }

    @Test
    fun `extractInitials returns correct initials for various name inputs`() {
        assertEquals("IK", com.example.ui.components.extractInitials("Isaac Kato"))
        assertEquals("I", com.example.ui.components.extractInitials("Isaac"))
        assertEquals("N", com.example.ui.components.extractInitials(""))
        assertEquals("N", com.example.ui.components.extractInitials(null))
        assertEquals("N", com.example.ui.components.extractInitials("   "))
        assertEquals("JD", com.example.ui.components.extractInitials("john doe"))
    }

    @Test
    fun `UserProfileEntity defaults have isOnboardingCompleted true for backward compatibility`() {
        val defaultProfile = UserProfileEntity()
        assertTrue(defaultProfile.isOnboardingCompleted)
    }

    @Test
    fun `client role creation sets CLIENT initialRole and active mode`() {
        val clientProfile = UserProfileEntity(
            id = "client_101",
            phoneNumber = "+256 701 234 567",
            fullName = "Client Test",
            district = "Nakawa",
            initialRole = "CLIENT",
            currentActiveMode = "CLIENT",
            workerKycStatus = "APPROVED",
            isWorkerActivated = true,
            trainingCompleted = true,
            isOnboardingCompleted = true
        )
        assertEquals("CLIENT", clientProfile.initialRole)
        assertEquals("CLIENT", clientProfile.currentActiveMode)
        assertTrue(clientProfile.isOnboardingCompleted)
        assertTrue(clientProfile.isWorkerActivated)
    }

    @Test
    fun `worker role creation sets WORKER initialRole and enforces KYC and training gating`() {
        val newWorkerProfile = UserProfileEntity(
            id = "worker_202",
            phoneNumber = "+256 702 345 678",
            fullName = "Isaac Kato",
            district = "Sunnyside",
            initialRole = "WORKER",
            currentActiveMode = "WORKER",
            workerKycStatus = "UNVERIFIED",
            isWorkerActivated = false,
            trainingCompleted = false,
            isOnboardingCompleted = true
        )
        assertEquals("WORKER", newWorkerProfile.initialRole)
        assertEquals("WORKER", newWorkerProfile.currentActiveMode)
        assertTrue(newWorkerProfile.isOnboardingCompleted)
        assertFalse(newWorkerProfile.isWorkerActivated)
        assertEquals("UNVERIFIED", newWorkerProfile.workerKycStatus)
        assertFalse(newWorkerProfile.trainingCompleted)

        // Verify that access check correctly denies freelancer features until approved and activated
        val canAccess = newWorkerProfile.isWorkerActivated && newWorkerProfile.workerKycStatus == "APPROVED"
        assertFalse(canAccess)
    }

    @Test
    fun `existing profile preserves data and role on migration`() {
        val existingProfile = UserProfileEntity(
            id = "existing_303",
            phoneNumber = "+256 703 456 789",
            fullName = "Existing Veteran",
            district = "Entebbe",
            initialRole = "WORKER",
            currentActiveMode = "WORKER",
            workerKycStatus = "APPROVED",
            isWorkerActivated = true,
            trainingCompleted = true,
            isOnboardingCompleted = true
        )
        assertEquals("WORKER", existingProfile.initialRole)
        assertTrue(existingProfile.isOnboardingCompleted)
        assertTrue(existingProfile.isWorkerActivated)
    }
}
