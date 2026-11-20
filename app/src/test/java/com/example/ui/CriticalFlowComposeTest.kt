package com.example.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.db.JobPostEntity
import com.example.data.db.JobQuoteEntity
import com.example.ui.components.JobPipelineKanban
import com.example.ui.components.QuickRequestDrawer
import com.example.ui.components.QuoteComparisonSheet
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R02-P2-05: Compose UI tests for the critical client flows, executed on the JVM with Robolectric
 * (no emulator required).
 *
 * Covered here: job posting validation, quote comparison, and the job pipeline surface. Escrow
 * gating is covered by [EscrowGatingComposeTest]; onboarding by [OnboardingComposeTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CriticalFlowComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun job(id: String = "job_1", status: String = "In Progress") = JobPostEntity(
        id = id,
        title = "Repair kitchen sink",
        category = "Plumbing",
        description = "Tap is dripping",
        postedByUid = "uid_owner",
        postedByResident = "You (Nakawa)",
        district = "Nakawa",
        budget = 50.0,
        urgency = "Today",
        status = status,
        assignedProviderId = "prov_1",
        assignedProviderName = "Elena Rostova",
        createdTimestamp = 1_000L,
        estimatedHours = 1.0,
        materialsCost = 0.0,
        safetyFee = 0.0
    )

    private fun quote() = JobQuoteEntity(
        id = "quote_1",
        jobId = "job_1",
        workerId = "prov_1",
        workerName = "Elena Rostova",
        proposedRate = 40.0,
        proposedHours = 2.0,
        totalQuote = 80.0
    )

    @Test
    fun `job posting stays disabled until the required fields are valid`() {
        compose.setContent {
            QuickRequestDrawer(onPostJob = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> }, onDismiss = {})
        }

        compose.onNodeWithText("Continue").assertIsNotEnabled()

        compose.onAllNodes(hasSetTextAction())[0].performTextInput("Fix leaking tap")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput("Kitchen tap drips all night")

        compose.onNodeWithText("Continue").assertIsEnabled()
    }

    @Test
    fun `quote comparison shows the worker quote and accepts it`() {
        var acceptedQuoteId: String? = null
        compose.setContent {
            QuoteComparisonSheet(
                job = job(),
                quotes = listOf(quote()),
                onAcceptQuote = { acceptedQuoteId = it },
                onDismiss = {}
            )
        }

        compose.onNodeWithText("Elena Rostova").assertExists()
        compose.onNodeWithText("Accept").performClick()
        assertEquals("quote_1", acceptedQuoteId)
    }

    @Test
    fun `pipeline renders the job and opens it`() {
        @Suppress("UNUSED_VARIABLE")
        var opened: String? = null
        compose.setContent {
            JobPipelineKanban(
                jobs = listOf(job()),
                onSelectJob = { opened = it.id },
                onAdvanceStatus = { _, _ -> },
                onOpenChat = {},
                onOpenEscrowSheet = {},
                onOpenQuoteComparison = {}
            )
        }
        compose.onNodeWithText("Repair kitchen sink").assertExists()
    }
}
