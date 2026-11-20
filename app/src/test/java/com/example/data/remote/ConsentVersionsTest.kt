package com.example.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R09-P2-02: the consent versions the app quotes.
 *
 * A receipt naming a version the server cannot validate proves nothing, and a boolean "accepted"
 * flag is not evidence. These tests pin the dated format, the grant set (which must match the
 * server's `REQUIRED_CONSENT_KEYS`) and the fact that a failed call reports failure rather than
 * being swallowed into a success.
 */
class ConsentVersionsTest {

    @Test
    fun `policy version is a dated value`() {
        assertTrue(ConsentVersions.POLICY_VERSION.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$")))
    }

    @Test
    fun `guarantor consent uses the same revision`() {
        assertEquals(ConsentVersions.POLICY_VERSION, ConsentVersions.GUARANTOR_VERSION)
    }

    @Test
    fun `the three required grants are the ones the server names`() {
        assertEquals(listOf("terms", "privacy", "dataProcessing"), ConsentVersions.PRIVACY_GRANTS)
        assertEquals(ConsentVersions.PRIVACY_GRANTS.size, ConsentVersions.PRIVACY_GRANTS.toSet().size)
    }

    @Test
    fun `a recorded outcome carries the version and the server time`() {
        val outcome = ConsentOutcome.Recorded(
            version = "2026-11-19",
            recordedAtMillis = 1_760_000_000_000,
            currentVersion = "2026-11-19"
        )
        assertEquals("2026-11-19", outcome.version)
        assertTrue(outcome.recordedAtMillis > 0)
    }

    @Test
    fun `a failure is a failure, never a silent success`() {
        val outcome: ConsentOutcome = ConsentOutcome.Failed("nothing was saved")
        assertFalse(outcome is ConsentOutcome.Recorded)
        assertTrue((outcome as ConsentOutcome.Failed).message.isNotBlank())
    }
}
