package com.example.ui

import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OnboardingScreen
import com.example.ui.viewmodel.SkillsExchangeViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R02-P2-03/P2-05: onboarding renders its role entry point and survives an instance-state restore
 * (rotation / process death) instead of resetting to a blank screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun viewModel() = SkillsExchangeViewModel(ApplicationProvider.getApplicationContext())

    @Test
    fun `role selection renders all three entry choices`() {
        compose.setContent { OnboardingScreen(viewModel = viewModel()) }

        compose.onNodeWithText("What describes you?").assertExists()
        compose.onNodeWithText("Existing Client").assertExists()
        compose.onNodeWithText("Existing Worker").assertExists()
        compose.onNodeWithText("New Here").assertExists()
    }

    @Test
    fun `onboarding restores after instance state is recreated`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { OnboardingScreen(viewModel = viewModel()) }

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("What describes you?").assertExists()
        compose.onNodeWithText("Continue").assertExists()
    }
}
