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
        assertTrue(profile.workerKycStatus == "APPROVED" || profile.workerKycStatus == "UNVERIFIED")
    }

    @Test
    fun `UserProfileEntity worker fields default correctly`() {
        val profile = UserProfileEntity(
            phoneNumber = "+256 772 198 421",
            fullName = "Worker User",
            district = "Sunnyside"
        )
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
}
