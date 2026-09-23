package com.example.util

/**
 * REG-003 / REG-004: Pure validation and authorization guards for the worker backup
 * contact and profile photo features.
 *
 * Deliberately free of Android/Firebase dependencies so every rule — including the
 * own-profile-only and authenticated-only guards — is unit-testable on the JVM.
 */
object ProfileValidators {

    /**
     * Phone convention shared with onboarding (OnboardingScreen enables submit at 9+
     * digits and caps input at 15): 9-15 digits, allowing only digits and +, space, (, ), -.
     */
    fun isValidPhone(phone: String): Boolean {
        val trimmed = phone.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.all { it.isDigit() || it in "+() -" }) return false
        val digits = trimmed.count { it.isDigit() }
        return digits in 9..15
    }

    /** Strips formatting so "+256 772 198 420" equals "+256772198420". */
    fun normalizePhoneDigits(phone: String): String = phone.filter { it.isDigit() }

    /** True only when both inputs are present and identical raw or digit-for-digit. */
    fun isSamePhone(a: String, b: String): Boolean {
        val na = a.trim()
        val nb = b.trim()
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb) return true
        return normalizePhoneDigits(na) == normalizePhoneDigits(nb)
    }

    /**
     * Returns an error message, or null when the backup contact is acceptable.
     *
     * Rules (REG-003 — no relationship semantics invented, see open question in
     * requirement_file.md §13.4):
     * - Both fields empty = intentionally unset (the field is optional) -> valid.
     * - Name without phone -> error (a name alone cannot be reached).
     * - Phone present -> must match the project's phone format.
     * - Phone must differ from the primary number (raw or digits-normalized).
     * - Name is optional when a phone is present (e.g. the worker's own second number).
     */
    fun validateBackupContact(primaryPhone: String, backupName: String, backupPhone: String): String? {
        val name = backupName.trim()
        val phone = backupPhone.trim()
        if (name.isEmpty() && phone.isEmpty()) return null
        if (phone.isEmpty()) return "Enter the backup phone number, or clear the contact name."
        if (!isValidPhone(phone)) return "Enter a valid backup phone number (9-15 digits)."
        if (isSamePhone(phone, primaryPhone)) return "Backup number must be different from your primary number."
        return null
    }

    /**
     * Authorization + validation guard for a backup-contact save attempt. The caller can
     * only reach validation when the session is authenticated AND a real (non-placeholder)
     * own profile exists — one user can never write another user's backup contact.
     */
    fun backupContactSaveBlocker(
        isAuthenticated: Boolean,
        hasOwnProfile: Boolean,
        primaryPhone: String,
        backupName: String,
        backupPhone: String
    ): String? {
        if (!isAuthenticated) return "Sign in to save your backup contact."
        if (!hasOwnProfile) return "No signed-in profile found. Sign in and try again."
        return validateBackupContact(primaryPhone, backupName, backupPhone)
    }

    /**
     * Authorization guard for a profile-photo upload attempt: authenticated session,
     * real own profile, and a pending gallery selection must all be present.
     */
    fun photoUploadBlocker(
        isAuthenticated: Boolean,
        hasOwnProfile: Boolean,
        hasPendingImage: Boolean
    ): String? {
        if (!isAuthenticated) return "Sign in to upload a profile photo."
        if (!hasOwnProfile) return "No signed-in profile found. Sign in and try again."
        if (!hasPendingImage) return "Choose a photo first."
        return null
    }
}