package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.ui.components.MapEmptyProvidersOverlay
import com.example.ui.theme.SkillsExchangeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * M6 / B8: the map must show a useful empty state when no providers match the
 * current filters/location, instead of a blank grey map with no explanation.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapEmptyProvidersOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyProvidersOverlay_showsTitleAndNextStep() {
        composeTestRule.setContent {
            SkillsExchangeTheme {
                MapEmptyProvidersOverlay(radiusMiles = 25.0)
            }
        }

        composeTestRule.onNodeWithText("No providers nearby").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("No specialists match within 25 mi", substring = true)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Try widening your radius", substring = true)
            .assertIsDisplayed()
    }
}
