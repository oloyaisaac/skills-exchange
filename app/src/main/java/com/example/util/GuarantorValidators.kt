package com.example.util

/**
 * REG-002: One guarantor entry as entered by the worker in the guarantor step.
 *
 * Deliberately a plain data holder (no Room/Firebase types) so the whole two-guarantor
 * rule set is unit-testable on the JVM. Photo fields hold *private Storage paths* —
 * never signed URLs and never image bytes.
 */
data class GuarantorInput(
    val fullName: String = "",
    val nationalIdNumber: String = "",
    val phoneNumber: String = "",
    val relationship: String = "",
    val district: String = "",
    val consentProvided: Boolean = false,
    val guarantorPhotoPath: String = "",
    val workerWithGuarantorPhotoPath: String = ""
)

/**
 * REG-002: Pure validation and authorization guards for guarantor verification.
 *
 * Free of Android/Firebase dependencies (mirrors [ProfileValidators]) so every rule —
 * including the authenticated-only / own-submission-only guards — is unit-testable.
 *
 * Implemented from requirement_file.md §13.3:
 * - exactly two guarantors, each with full name, NIN, contact number, guarantor
 *   photograph and a worker+guarantor photograph;
 * - one group photograph containing worker + guarantor 1 + guarantor 2;
 * - the two guarantors must be two different people.
 *
 * Deliberately NOT invented (tracked as open questions in AGENT_STATE.md):
 * - No NIRA/NIN checksum or fixed-length format is assumed — [validateNationalIdNumber]
 *   is a structural sanity check only, because neither requirement_file.md nor any
 *   provided NIRA specification defines the authoritative Uganda NIN format.
 * - No relationship vocabulary is enforced; it remains free text.
 */
object GuarantorValidators {

    /** REG-002: worker registration requires exactly two guarantors. */
    const val REQUIRED_GUARANTOR_COUNT = 2

    /** REG-002: verification photographs live in a private, owner-only prefix. */
    const val PHOTO_SLOT_GUARANTOR = "guarantor"
    /** REG-002: worker together with one guarantor. */
    const val PHOTO_SLOT_WITH_WORKER = "with_worker"
    /** REG-002: worker together with both guarantors. */
    const val PHOTO_SLOT_GROUP = "group"

    /** The only photo purposes a client may upload under. */
    val PHOTO_SLOTS = setOf(PHOTO_SLOT_GUARANTOR, PHOTO_SLOT_WITH_WORKER, PHOTO_SLOT_GROUP)

    /**
     * Structural bounds for the NIN field — no official format is invented (see above).
     */
    const val MIN_NIN_LENGTH = 5
    const val MAX_NIN_LENGTH = 20

    private const val MIN_NAME_LENGTH = 3
    private const val MAX_NAME_LENGTH = 60

    /**
     * Name convention: 3-60 characters of letters, spaces, hyphens, apostrophes and
     * periods (covers accented and hyphenated names without inventing rules).
     */
    fun isValidName(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.length < MIN_NAME_LENGTH || trimmed.length > MAX_NAME_LENGTH) return false
        return trimmed.all { it.isLetter() || it.isWhitespace() || it in "-'." }
    }

    /**
     * Structural-only NIN check: 5-20 characters, letters/digits/spaces/hyphens only,
     * and not a single repeated character. Admins verify the real number against the
     * uploaded National ID image during review.
     */
    fun validateNationalIdNumber(nin: String): String? {
        val value = nin.trim()
        if (value.isEmpty()) return "Enter the guarantor's NIN number."
        if (value.length < MIN_NIN_LENGTH || value.length > MAX_NIN_LENGTH) {
            return "NIN number must be $MIN_NIN_LENGTH-$MAX_NIN_LENGTH characters."
        }
        if (!value.all { it.isLetterOrDigit() || it == ' ' || it == '-' }) {
            return "NIN number may only contain letters, numbers, spaces and hyphens."
        }
        if (value.toSet().size <= 1) return "Enter the guarantor's real NIN number."
        return null
    }


    /**
     * Validates one guarantor entry. [workerPrimaryPhone] is the signed-in worker's own
     * number (a worker may not stand as their own guarantor) and [otherGuarantorPhone]
     * is the other guarantor's number (the two guarantors must be different people).
     *
     * The name/NIN character rules above already reject `<`, `;`, `--`, `/` and similar
     * payload characters, so free-text cannot smuggle markup or SQL fragments onward.
     */
    fun validateGuarantor(
        label: String,
        guarantor: GuarantorInput,
        workerPrimaryPhone: String,
        otherGuarantorPhone: String
    ): String? {
        if (!isValidName(guarantor.fullName)) {
            return "Enter ${label.lowercase()}'s full name (letters, spaces, hyphens or apostrophes)."
        }
        validateNationalIdNumber(guarantor.nationalIdNumber)?.let { return it }

        val phone = guarantor.phoneNumber.trim()
        if (!ProfileValidators.isValidPhone(phone)) {
            return "Enter a valid contact number for ${label.lowercase()} (9-15 digits)."
        }
        if (ProfileValidators.isSamePhone(phone, workerPrimaryPhone)) {
            return "${label.lowercase()} cannot use your own contact number."
        }
        if (ProfileValidators.isSamePhone(phone, otherGuarantorPhone)) {
            return "Guarantor 1 and Guarantor 2 must be two different people."
        }

        if (!guarantor.consentProvided) {
            return "Confirm that ${label.lowercase()} gave consent before submitting their details."
        }
        if (guarantor.guarantorPhotoPath.isBlank()) {
            return "Add a photograph of ${label.lowercase()}."
        }
        if (guarantor.workerWithGuarantorPhotoPath.isBlank()) {
            return "Add a photograph of you together with ${label.lowercase()}."
        }
        if (guarantor.relationship.trim().length > 40 || guarantor.district.trim().length > 60) {
            return "Keep the relationship and district details short."
        }
        return null
    }

    /**
     * Validates the complete REG-002 submission: exactly two distinct guarantors plus the
     * group photograph. Returns the first blocking message, or null when submittable.
     */
    fun validateGuarantorSet(
        guarantors: List<GuarantorInput>,
        workerPrimaryPhone: String,
        groupPhotoPath: String
    ): String? {
        if (guarantors.size != REQUIRED_GUARANTOR_COUNT) {
            return "Provide exactly two guarantors."
        }
        val first = guarantors[0]
        val second = guarantors[1]
        if (first.fullName.trim().isNotEmpty() &&
            first.fullName.trim().equals(second.fullName.trim(), ignoreCase = true) &&
            ProfileValidators.normalizePhoneDigits(first.phoneNumber) ==
            ProfileValidators.normalizePhoneDigits(second.phoneNumber)
        ) {
            return "Guarantor 1 and Guarantor 2 must be two different people."
        }
        validateGuarantor("Guarantor 1", first, workerPrimaryPhone, second.phoneNumber)?.let { return it }
        validateGuarantor("Guarantor 2", second, workerPrimaryPhone, first.phoneNumber)?.let { return it }
        if (groupPhotoPath.isBlank()) {
            return "Add the group photograph showing you with both guarantors."
        }
        return null
    }

    /**
     * Authorization guard for a guarantor submission attempt. Fail closed: an
     * unauthenticated session or a non-worker account can never store guarantor data,
     * and a submission is always filed under the caller's own uid.
     */
    fun submissionBlocker(
        isAuthenticated: Boolean,
        isWorker: Boolean,
        workerPrimaryPhone: String,
        guarantors: List<GuarantorInput>,
        groupPhotoPath: String
    ): String? {
        if (!isAuthenticated) return "Sign in before submitting guarantors."
        if (!isWorker) return "Guarantor verification is only available for worker accounts."
        return validateGuarantorSet(guarantors, workerPrimaryPhone, groupPhotoPath)
    }

    /**
     * Deterministic Room row id / Firestore document id for one guarantor of one worker.
     * Keyed by the digit-normalized contact number so re-submitting the same person
     * replaces the previous row instead of duplicating it.
     */
    fun guarantorId(workerId: String, phoneNumber: String): String =
        "guarantor_${workerId}_${ProfileValidators.normalizePhoneDigits(phoneNumber)}"

    /**
     * Log scrubbing: masks every run of 5+ digits (NIN numbers, contact numbers) so
     * identifiers can never reach logcat, crash reports or analytics payloads.
     */
    fun scrubIdentifiers(text: String): String =
        Regex("\\d{5,}").replace(text) { "[scrubbed]" }

    /**
     * Masked display form of an identifier for listings: keeps at most the last two
     * characters ("*******42"). Used by the admin queue payload so a full NIN is never
     * sent in a list response.
     */
    fun maskIdentifier(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.length <= 2) return "*".repeat(trimmed.length)
        return "*".repeat(trimmed.length - 2) + trimmed.takeLast(2)
    }
}
