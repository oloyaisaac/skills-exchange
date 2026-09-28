package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * REG-002 unit coverage for [GuarantorValidators] — the two-guarantor + group-photograph
 * rule set the guarantor sheet and the repository both enforce.
 *
 * Pure-JVM tests (no Android framework), mirroring [ProfileValidatorsTest].
 */
@RunWith(JUnit4::class)
class GuarantorValidatorsTest {

    private fun guarantor(
        name: String = "Amina Nakato",
        nin: String = "CM31019880042E",
        phone: String = "+256 772 198 420",
        relationship: String = "Community Leader",
        district: String = "Wakiso",
        consent: Boolean = true,
        photo: String = "users/w1/guarantors/guarantor_1.jpg",
        withWorker: String = "users/w1/guarantors/with_worker_1.jpg"
    ) = GuarantorInput(
        fullName = name,
        nationalIdNumber = nin,
        phoneNumber = phone,
        relationship = relationship,
        district = district,
        consentProvided = consent,
        guarantorPhotoPath = photo,
        workerWithGuarantorPhotoPath = withWorker
    )

    private fun second(
        name: String = "Ivan Mugisha",
        phone: String = "+256 701 555 322"
    ) = guarantor(name = name, nin = "CM44209770118F", phone = phone)

    private val groupPhoto = "users/w1/guarantors/group_1.jpg"

    private fun setOfTwo(first: GuarantorInput = guarantor(), second: GuarantorInput = second()) =
        listOf(first, second)

    // --- happy path ---

    @Test
    fun `complete set of two guarantors plus group photo is accepted`() {
        assertNull(
            GuarantorValidators.validateGuarantorSet(
                guarantors = setOfTwo(),
                workerPrimaryPhone = "+256 758 000 111",
                groupPhotoPath = groupPhoto
            )
        )
    }

    // --- count ---

    @Test
    fun `one guarantor is rejected`() {
        val blocker = GuarantorValidators.validateGuarantorSet(
            guarantors = listOf(guarantor()),
            workerPrimaryPhone = "+256 758 000 111",
            groupPhotoPath = groupPhoto
        )
        assertEquals("Provide exactly two guarantors.", blocker)
    }

    @Test
    fun `three guarantors are rejected`() {
        assertNotNull(
            GuarantorValidators.validateGuarantorSet(
                guarantors = listOf(guarantor(), second(), second(name = "Peter Okello", phone = "+256 700 111 999")),
                workerPrimaryPhone = "+256 758 000 111",
                groupPhotoPath = groupPhoto
            )
        )
    }

    // --- identity rules ---

    @Test
    fun `worker cannot stand as their own guarantor`() {
        val blocker = GuarantorValidators.validateGuarantorSet(
            guarantors = setOfTwo(first = guarantor(phone = "+256758000111")),
            workerPrimaryPhone = "+256 758 000 111",
            groupPhotoPath = groupPhoto
        )
        assertNotNull(blocker)
    }

    @Test
    fun `the two guarantors must be two different people`() {
        val blocker = GuarantorValidators.validateGuarantorSet(
            guarantors = setOfTwo(second = second(name = "amina nakato", phone = "+256 772 198 420")),
            workerPrimaryPhone = "+256 758 000 111",
            groupPhotoPath = groupPhoto
        )
        assertEquals("Guarantor 1 and Guarantor 2 must be two different people.", blocker)
    }

    @Test
    fun `same phone reused for both guarantors is rejected`() {
        assertNotNull(
            GuarantorValidators.validateGuarantorSet(
                guarantors = setOfTwo(second = second(name = "Someone Else", phone = "+256 772 198 420")),
                workerPrimaryPhone = "+256 758 000 111",
                groupPhotoPath = groupPhoto
            )
        )
    }

    // --- photographs ---

    @Test
    fun `missing guarantor photograph is rejected`() {
        assertNotNull(
            GuarantorValidators.validateGuarantorSet(
                guarantors = setOfTwo(first = guarantor(photo = "")),
                workerPrimaryPhone = "+256 758 000 111",
                groupPhotoPath = groupPhoto
            )
        )
    }

    @Test
    fun `missing worker-and-guarantor photograph is rejected`() {
        assertNotNull(
            GuarantorValidators.validateGuarantorSet(
                guarantors = setOfTwo(first = guarantor(withWorker = "")),
                workerPrimaryPhone = "+256 758 000 111",
                groupPhotoPath = groupPhoto
            )
        )
    }

    @Test
    fun `missing group photograph is rejected`() {
        assertNotNull(
            GuarantorValidators.validateGuarantorSet(
                guarantors = setOfTwo(),
                workerPrimaryPhone = "+256 758 000 111",
                groupPhotoPath = ""
            )
        )
    }

    // --- consent & identifier hygiene ---

    @Test
    fun `guarantor without recorded consent is rejected`() {
        assertNotNull(
            GuarantorValidators.validateGuarantorSet(
                guarantors = setOfTwo(first = guarantor(consent = false)),
                workerPrimaryPhone = "+256 758 000 111",
                groupPhotoPath = groupPhoto
            )
        )
    }

    @Test
    fun `too-short NIN is rejected`() {
        assertNotNull(GuarantorValidators.validateNationalIdNumber("CM31"))
    }

    @Test
    fun `well formed NIN passes the structural check`() {
        assertNull(GuarantorValidators.validateNationalIdNumber("CM31019880042E"))
    }

    @Test
    fun `name with payload characters is rejected`() {
        assertFalse(GuarantorValidators.isValidName("Amina;<script>"))
    }

    // --- authorization guards ---

    @Test
    fun `anonymous session may not file guarantors`() {
        assertEquals(
            "Sign in before submitting guarantors.",
            GuarantorValidators.submissionBlocker(
                isAuthenticated = false,
                isWorker = true,
                workerPrimaryPhone = "+256 758 000 111",
                guarantors = setOfTwo(),
                groupPhotoPath = groupPhoto
            )
        )
    }

    @Test
    fun `client account may not file guarantors`() {
        assertEquals(
            "Guarantor verification is only available for worker accounts.",
            GuarantorValidators.submissionBlocker(
                isAuthenticated = true,
                isWorker = false,
                workerPrimaryPhone = "+256 758 000 111",
                guarantors = setOfTwo(),
                groupPhotoPath = groupPhoto
            )
        )
    }

    // --- storage & logging helpers ---

    @Test
    fun `only the three known photo purposes are allowed`() {
        assertEquals(
            setOf("guarantor", "with_worker", "group"),
            GuarantorValidators.PHOTO_SLOTS
        )
    }

    @Test
    fun `guarantor id is deterministic so re-submission replaces the row`() {
        assertEquals(
            GuarantorValidators.guarantorId("w1", "0772 198 420"),
            GuarantorValidators.guarantorId("w1", "0772198420")
        )
    }

    @Test
    fun `identifiers are masked and scrubbed`() {
        val nin = "CM31019880042E"
        val masked = GuarantorValidators.maskIdentifier(nin)
        assertEquals(nin.length, masked.length)
        assertEquals("2E", masked.takeLast(2))
        assertFalse(GuarantorValidators.scrubIdentifiers("NIN CM31019880042E rejected").contains("31019880042"))
    }
}

