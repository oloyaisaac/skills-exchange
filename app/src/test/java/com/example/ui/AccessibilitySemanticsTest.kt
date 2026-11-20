package com.example.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.JobPostEntity
import com.example.ui.components.EscrowAndProofSheet
import com.example.ui.components.OfflineStatusBar
import com.example.ui.screens.OnboardingScreen
import com.example.ui.viewmodel.SkillsExchangeViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R12-P2-02/P2-04/P2-06: the accessibility layer, asserted rather than described.
 *
 * The review counted `contentDescription` occurrences and could not render, so it could say only that
 * "no semantics were found". These tests render the real composables under Robolectric and read the
 * semantics tree the way TalkBack does: is this an alert, is this a heading, does this control say
 * what it is doing, can a finger reach it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccessibilitySemanticsTest {

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

    private fun escrow(
        sosDispatchMessage: String? = null,
        sosTriggered: Boolean = false
    ) {
        val job = job().apply { isSosTriggered = sosTriggered }
        compose.setContent {
            EscrowAndProofSheet(
                job = job,
                onUpdateEscrowStatus = {},
                onUpdatePhotos = { _, _ -> },
                onRequestEmergencyAddon = { _, _ -> },
                onRespondEmergencyAddon = {},
                onUpdatePaymentChannel = { _, _ -> },
                onInitiateMobileMoneyPayment = {},
                onReleaseFunds = {},
                sosDispatchMessage = sosDispatchMessage,
                onDismiss = {}
            )
        }
    }

    /** Losing the network is a change of state; a screen-reader user must hear it. */
    @Test
    fun `the offline banner announces itself politely`() {
        compose.setContent { OfflineStatusBar(isOnline = false) }
        compose.onNodeWithText("Offline — showing saved data. Changes queue automatically.")
            .assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    /** The onboarding question is a section, so heading navigation finds it. */
    @Test
    fun `the onboarding role question is a heading`() {
        compose.setContent {
            OnboardingScreen(viewModel = SkillsExchangeViewModel(ApplicationProvider.getApplicationContext()))
        }
        compose.onNodeWithText("What describes you?")
            .assertExists()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    /** The platform's verdict on an emergency is announced assertively, not left to be discovered. */
    @Test
    fun `the dispatch verdict is an assertive live region`() {
        escrow(sosDispatchMessage = "The emergency SMS could not be sent (2 contacts tried).", sosTriggered = true)
        compose.onNodeWithText("The emergency SMS could not be sent (2 contacts tried).")
            .assertExists()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Assertive))
    }

    /** A panic button below 48dp is a hit target that stress makes impossible. */
    @Test
    fun `the panic button is at least 48dp tall`() {
        escrow()
        compose.onNodeWithText("SOS PANIC").assertExists().assertHeightIsAtLeast(48.dp)
    }

    /** A disabled control that says nothing is a dead end; this one says what it is doing. */
    @Test
    fun `the panic button describes the alert state`() {
        escrow()
        compose.onNodeWithText("SOS PANIC")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No emergency alert is active."))
    }

    /** 200% font scale is the accessibility setting most likely to break a fixed-height layout. */
    @Test
    fun `the panic control survives a 200 percent font scale`() {
        val job = job()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = 2f)
            ) {
                EscrowAndProofSheet(
                    job = job,
                    onUpdateEscrowStatus = {},
                    onUpdatePhotos = { _, _ -> },
                    onRequestEmergencyAddon = { _, _ -> },
                    onRespondEmergencyAddon = {},
                    onUpdatePaymentChannel = { _, _ -> },
                    onInitiateMobileMoneyPayment = {},
                    onReleaseFunds = {},
                    onDismiss = {}
                )
            }
        }
        // Still rendered and still hittable: the text grew, the control did not disappear.
        compose.onNodeWithText("SOS PANIC").assertExists().assertHeightIsAtLeast(48.dp)
    }

    /** R12-P2-01: the Swahili locale actually resolves, rather than being a folder nobody reads. */
    @Test
    @Config(sdk = [34], qualifiers = "sw")
    fun `the safety copy resolves in Swahili`() {
        escrow()
        compose.onNodeWithText("MSAADA WA HARAKA").assertExists()
    }
}
