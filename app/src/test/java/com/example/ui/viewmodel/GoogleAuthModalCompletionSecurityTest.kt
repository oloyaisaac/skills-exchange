package com.example.ui.viewmodel

import com.example.data.db.UserProfileEntity
import com.example.ui.components.AuthModalValidation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * C3 / A1: Verification tests for Google Sign-In New User Completion & Phone Number Capture.
 *
 * Ensures that:
 * - Case A: A new Google user can enter their phone number during PROFILE_SETUP, and completion
 *           is enabled when full name, phone number, and district are validly provided.
 * - Case B: Profile setup completion remains disabled/rejected when phone number is empty, blank,
 *           or format-invalid.
 * - Case C: Profile setup completion remains disabled/rejected when any other required field
 *           (full legal name or district) is empty or blank.
 * - Case D: Existing Google user restoration behavior is unchanged (recognised account retains
 *           its profile, admin KYC status, and onboarding flag).
 * - Case E: The profile continues to use the authenticated Firebase UID and never falls back
 *           to phone-number identity or synthetic placeholders.
 */
@RunWith(JUnit4::class)
class GoogleAuthModalCompletionSecurityTest {

    private val validPhone = "+256 772 198 420"
    private val validName = "Isaac Kato"
    private val validDistrict = "Nakawa"

    // ==========================================
    // Case A: New Google user
    // ==========================================

    @Test
    fun `Case A - valid full name phone and district allow completion`() {
        // E.164-style formatted Ugandan phone
        assertTrue(AuthModalValidation.isProfileSetupValid(validName, validPhone, validDistrict))
        // Local 10-digit format
        assertTrue(AuthModalValidation.isProfileSetupValid(validName, "0772198420", validDistrict))
        // Compact international format
        assertTrue(AuthModalValidation.isProfileSetupValid(validName, "+256772198420", validDistrict))
    }

    @Test
    fun `Case A - new Google user profile resolves with authoritative Firebase UID and collected fields`() {
        val googleUid = "google_firebase_uid_new_user"
        val resolved = SkillsExchangeViewModel.resolveAuthenticatedProfile(
            authenticatedUid = googleUid,
            existing = null,
            phone = validPhone,
            fullName = validName,
            district = validDistrict,
            initialRole = "CLIENT"
        )

        assertEquals(googleUid, resolved.id)
        assertEquals(validPhone, resolved.phoneNumber)
        assertEquals(validName, resolved.fullName)
        assertEquals(validDistrict, resolved.district)
        assertEquals("CLIENT", resolved.initialRole)
        // New client profile starts fail-closed
        assertEquals("NOT_APPLIED", resolved.workerKycStatus)
        assertFalse(resolved.isWorkerActivated)
        assertFalse(resolved.trainingCompleted)
    }

    // ==========================================
    // Case B: Missing phone
    // ==========================================

    @Test
    fun `Case B - empty or blank phone number keeps completion disabled`() {
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, "", validDistrict))
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, "   ", validDistrict))
    }

    @Test
    fun `Case B - format-invalid phone number keeps completion disabled`() {
        // Less than 9 digits
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, "12345", validDistrict))
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, "+256 77", validDistrict))
        // Non-digit characters
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, "invalid_phone_number", validDistrict))
        // More than 15 digits
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, "+256 772 198 420 1234", validDistrict))
    }

    // ==========================================
    // Case C: Missing required profile field
    // ==========================================

    @Test
    fun `Case C - empty or blank legal name keeps completion disabled`() {
        assertFalse(AuthModalValidation.isProfileSetupValid("", validPhone, validDistrict))
        assertFalse(AuthModalValidation.isProfileSetupValid("   ", validPhone, validDistrict))
    }

    @Test
    fun `Case C - empty or blank district keeps completion disabled`() {
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, validPhone, ""))
        assertFalse(AuthModalValidation.isProfileSetupValid(validName, validPhone, "   "))
    }

    // ==========================================
    // Case D: Existing Google user
    // ==========================================

    @Test
    fun `Case D - existing Google user profile restoration is preserved`() {
        val googleUid = "google_firebase_uid_veteran"
        val existingApprovedProfile = UserProfileEntity(
            id = googleUid,
            phoneNumber = validPhone,
            fullName = "Veteran Worker",
            district = "Sunnyside",
            initialRole = "WORKER",
            currentActiveMode = "WORKER",
            workerKycStatus = "APPROVED",
            isWorkerActivated = true,
            trainingCompleted = true,
            isOnboardingCompleted = true
        )

        val restored = SkillsExchangeViewModel.resolveAuthenticatedProfile(
            authenticatedUid = googleUid,
            existing = existingApprovedProfile,
            phone = validPhone,
            fullName = "Veteran Worker",
            district = "Sunnyside",
            initialRole = "WORKER"
        )

        // Recognised account owned by the authenticated UID preserves its granted privileges
        assertEquals(googleUid, restored.id)
        assertEquals("APPROVED", restored.workerKycStatus)
        assertTrue(restored.isWorkerActivated)
        assertTrue(restored.trainingCompleted)
        assertEquals("Veteran Worker", restored.fullName)
    }

    @Test
    fun `Case D - matching phone number from another account cannot overwrite or elevate new Google identity`() {
        val googleUid = "google_new_attacker_uid"
        val victimProfile = UserProfileEntity(
            id = "victim_existing_uid",
            phoneNumber = validPhone,
            fullName = "Victim Name",
            district = "Entebbe",
            initialRole = "WORKER",
            currentActiveMode = "WORKER",
            workerKycStatus = "APPROVED",
            isWorkerActivated = true,
            trainingCompleted = true,
            isOnboardingCompleted = true
        )

        val resolved = SkillsExchangeViewModel.resolveAuthenticatedProfile(
            authenticatedUid = googleUid,
            existing = victimProfile,
            phone = validPhone,
            fullName = "Attacker Name",
            district = "Nakawa",
            initialRole = "WORKER"
        )

        // ID must be the authenticated Google UID, not the victim's UID
        assertEquals(googleUid, resolved.id)
        assertNotEquals(victimProfile.id, resolved.id)
        // Victim's approved status must NOT be inherited
        assertEquals("UNVERIFIED", resolved.workerKycStatus)
        assertFalse(resolved.isWorkerActivated)
        assertFalse(resolved.trainingCompleted)
    }

    // ==========================================
    // Case E: Firebase identity
    // ==========================================

    @Test
    fun `Case E - profile always uses authoritative Firebase UID and never falls back to phone identity`() {
        val googleUid = "google_authoritative_uid_789"
        val resolved = SkillsExchangeViewModel.resolveAuthenticatedProfile(
            authenticatedUid = googleUid,
            existing = null,
            phone = validPhone,
            fullName = validName,
            district = validDistrict,
            initialRole = "CLIENT"
        )

        assertEquals(googleUid, resolved.id)
        assertFalse(resolved.id.startsWith("user_"))
        assertNotEquals(validPhone, resolved.id)
    }
}
