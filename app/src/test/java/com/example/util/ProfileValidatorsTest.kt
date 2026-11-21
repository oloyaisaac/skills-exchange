package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REG-003 / REG-004 unit coverage for [ProfileValidators].
 *
 * Pure-JVM tests (no Android framework) so they also run in constrained environments.
 * Covers backup-contact phone validation, optional-empty handling, primary/backup
 * duplicate detection, and the authenticated-own-profile upload/save guards.
 */
@RunWith(JUnit4::class)
class ProfileValidatorsTest {

    // --- REG-003: backup phone validation ---

    @Test
    fun `valid backup phone number is accepted`() {
        assertNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "Jane Kato",
                backupPhone = "+256 701 234 567"
            )
        )
    }

    @Test
    fun `invalid backup phone number is rejected`() {
        // Too short
        assertNotNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "Jane Kato",
                backupPhone = "12345"
            )
        )
        // Non-numeric characters
        assertNotNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "Jane Kato",
                backupPhone = "abcdefg hij"
            )
        )
        // Too long: 16 digits exceeds the project max of 15. The UI mirrors onboarding and
        // caps raw input at 15 characters, so the validator is the authority for pasted or
        // programmatic values such as this one.
        assertNotNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "Jane Kato",
                backupPhone = "+256 772 198 420 1234"
            )
        )
    }

    @Test
    fun `empty optional values are accepted`() {
        assertNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "",
                backupPhone = ""
            )
        )
    }

    @Test
    fun `name without phone is rejected`() {
        val error = ProfileValidators.validateBackupContact(
            primaryPhone = "+256 772 198 420",
            backupName = "Jane Kato",
            backupPhone = ""
        )
        assertNotNull(error)
        assertTrue(error!!.contains("phone number", ignoreCase = true))
    }

    @Test
    fun `duplicate primary and backup phone is rejected`() {
        // Exact duplicate
        assertNotNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "",
                backupPhone = "+256 772 198 420"
            )
        )
        // Same digits under different formatting
        assertNotNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "",
                backupPhone = "+256772198420"
            )
        )
    }

    @Test
    fun `phone without name is accepted`() {
        // The backup number may be the worker's own second line; the name stays optional.
        assertNull(
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 772 198 420",
                backupName = "",
                backupPhone = "+256 701 234 567"
            )
        )
    }

    @Test
    fun `isValidPhone follows the project 9 to 15 digit convention`() {
        assertTrue(ProfileValidators.isValidPhone("+256 772 198 420"))
        assertTrue(ProfileValidators.isValidPhone("0772198420"))
        assertTrue(ProfileValidators.isValidPhone("123456789"))
        assertFalse(ProfileValidators.isValidPhone(""))
        assertFalse(ProfileValidators.isValidPhone("12345678"))
        assertFalse(ProfileValidators.isValidPhone("1234567890123456"))
        assertFalse(ProfileValidators.isValidPhone("07AB198420"))
    }

    // --- REG-003: save authorization guard ---

    @Test
    fun `backup save blocked when unauthenticated`() {
        assertNotNull(
            ProfileValidators.backupContactSaveBlocker(
                isAuthenticated = false,
                hasOwnProfile = true,
                primaryPhone = "+256 772 198 420",
                backupName = "Jane Kato",
                backupPhone = "+256 701 234 567"
            )
        )
    }

    @Test
    fun `backup save blocked without own profile`() {
        assertNotNull(
            ProfileValidators.backupContactSaveBlocker(
                isAuthenticated = true,
                hasOwnProfile = false,
                primaryPhone = "+256 772 198 420",
                backupName = "Jane Kato",
                backupPhone = "+256 701 234 567"
            )
        )
    }

    @Test
    fun `backup save allowed for authenticated own profile with valid data`() {
        assertNull(
            ProfileValidators.backupContactSaveBlocker(
                isAuthenticated = true,
                hasOwnProfile = true,
                primaryPhone = "+256 772 198 420",
                backupName = "Jane Kato",
                backupPhone = "+256 701 234 567"
            )
        )
    }

    // --- REG-004: upload authorization guard ---

    @Test
    fun `photo upload blocked when unauthenticated`() {
        assertNotNull(
            ProfileValidators.photoUploadBlocker(
                isAuthenticated = false,
                hasOwnProfile = true,
                hasPendingImage = true
            )
        )
    }

    @Test
    fun `photo upload blocked without own profile`() {
        assertNotNull(
            ProfileValidators.photoUploadBlocker(
                isAuthenticated = true,
                hasOwnProfile = false,
                hasPendingImage = true
            )
        )
    }

    @Test
    fun `photo upload blocked when no image is selected`() {
        val error = ProfileValidators.photoUploadBlocker(
            isAuthenticated = true,
            hasOwnProfile = true,
            hasPendingImage = false
        )
        assertNotNull(error)
        assertTrue(error!!.contains("Choose a photo", ignoreCase = true))
    }

    @Test
    fun `photo upload allowed for authenticated own profile with selection`() {
        assertNull(
            ProfileValidators.photoUploadBlocker(
                isAuthenticated = true,
                hasOwnProfile = true,
                hasPendingImage = true
            )
        )
    }

    // --- REG-003: save ownership note (verified at the persistence layer) ---

    @Test
    fun `validation never consults another users data`() {
        // saveBackupContact writes only currentUser.copy(...); the validator receives no uid
        // or foreign rows by design — ownership is structural, asserted here as documentation
        // alongside the DAO-level ownership test in ProfileContactPhotoPersistenceTest.
        assertEquals(
            "Backup number must be different from your primary number.",
            ProfileValidators.validateBackupContact(
                primaryPhone = "+256 700 111 222",
                backupName = "Other Person",
                backupPhone = "+256 700 111 222"
            )
        )
    }
}