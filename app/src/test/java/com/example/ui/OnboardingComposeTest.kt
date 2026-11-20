package com.example.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OnboardingScreen
import com.example.ui.viewmodel.SkillsExchangeViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R02-P2-03/P2-05: the onboarding entry screen renders and its role selection advances the flow.
 * State persistence itself is provided by `rememberSaveable`; this test pins the visible contract.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OnboardingComposeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `role selection renders and continues to the account type question`() {
        val viewModel = SkillsExchangeViewModel(ApplicationProvider.getApplicationContext())
        compose.setContent { OnboardingScreen(viewModel = viewModel) }

        compose.onNodeWithText("What describes you?").assertExists()
        compose.onNodeWithText("Continue").assertIsNotEnabled()

        compose.onNodeWithText("I want to create a brand new account").performClick()

        compose.onNodeWithText("Continue").assertIsEnabled()
    }
}
