package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.db.JobPostEntity
import com.example.ui.components.EscrowAndProofSheet
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R02-P2-01/P2-02: the escrow surface must show the server's release outcome and never claim a
 * release on its own. A failed release is visible; the action is disabled while one is running.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EscrowGatingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun job() = JobPostEntity(
        id = "job_1",
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping",
        postedByUid = "uid_owner",
        postedByResident = "You (Nakawa)",
        district = "Nakawa",
        budget = 50.0,
        urgency = "Today",
        status = "In Progress",
        assignedProviderId = "prov_1",
        assignedProviderName = "Elena Rostova",
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0
    ).apply { escrowStatus = "PROOF_SUBMITTED" }

    @Test
    fun `a failed server release is surfaced to the user`() {
        compose.setContent {
            EscrowAndProofSheet(
                job = job(),
                onUpdateEscrowStatus = {},
                onUpdatePhotos = { _, _ -> },
                onRequestEmergencyAddon = { _, _ -> },
                onRespondEmergencyAddon = {},
                onUpdatePaymentChannel = { _, _ -> },
                onInitiateMobileMoneyPayment = {},
                onReleaseFunds = {},
                releaseError = "provider rejected the payout",
                onDismiss = {}
            )
        }

        compose.onNodeWithText("Release failed: provider rejected the payout").assertExists()
    }

    @Test
    fun `the release action is disabled while a release is running`() {
        var clicked = false
        compose.setContent {
            EscrowAndProofSheet(
                job = job(),
                onUpdateEscrowStatus = {},
                onUpdatePhotos = { _, _ -> },
                onRequestEmergencyAddon = { _, _ -> },
                onRespondEmergencyAddon = {},
                onUpdatePaymentChannel = { _, _ -> },
                onInitiateMobileMoneyPayment = {},
                onReleaseFunds = { clicked = true },
                isReleasingFunds = true,
                onDismiss = {}
            )
        }

        compose.onNodeWithText("Releasing with the server...").assertExists()
        compose.onNodeWithText("Releasing with the server...").performClick()
        assertTrue("a release must not start twice", !clicked)
    }
}
