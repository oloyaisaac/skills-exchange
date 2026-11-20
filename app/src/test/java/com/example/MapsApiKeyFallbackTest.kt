package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.db.ServiceProviderEntity
import com.example.ui.components.InteractiveMapView
import com.example.ui.theme.SkillsExchangeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * D1 fix (MAP-001) — the process must survive an absent/placeholder Maps API key.
 *
 * On a Samsung SM-A025F the Map View tab crashed 3/3 times with
 * `IllegalStateException: API key not found`, thrown by the Maps SDK while
 * constructing a MapView. That throw is unreachable from app code, so the guard has
 * to prevent the MapView from ever being built.
 *
 * This test renders the real composable in the same unconfigured state a debug build
 * actually ships (`manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_MAPS_API_KEY"`,
 * see `app/build.gradle.kts`) and asserts the fallback card appears. Before the fix
 * this test would fail — the composable built a MapView and the SDK threw.
 *
 * Note this complements [com.example.util.MapsApiKeyGuardTest]: that one pins the
 * key-classification rules, this one proves the composable actually consults them
 * and never reaches the crashing call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapsApiKeyFallbackTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun provider(id: String) = ServiceProviderEntity(
        id = id,
        name = "Elena Rostova",
        tagline = "Plumbing, on time",
        category = "Plumbing",
        neighborhood = "Nakawa",
        residenceYears = 3,
        hourlyRate = 45.0,
        distanceMiles = 1.2,
        rating = 4.8,
        completedJobsCount = 27,
        avgResponseTime = "10 min",
        isVerifiedResident = true,
        isBackgroundChecked = true,
        bio = "Plumber.",
        skillsCsv = "Plumbing",
        latitude = 0.3476,
        longitude = 32.5825,
        endorsementsCount = 12
    )

    /**
     * The core D1 regression: with no real key configured the composable must render
     * the fallback and must NOT construct a MapView. Reaching this assertion without
     * an exception is the proof — an unguarded MapView throws from the SDK first.
     */
    @Test
    fun interactiveMapView_withoutRealApiKey_showsFallbackAndDoesNotCrash() {
        composeTestRule.setContent {
            SkillsExchangeTheme {
                InteractiveMapView(
                    providers = listOf(provider("prov_1")),
                    selectedProvider = null,
                    onSelectProvider = {},
                    radiusMiles = 25.0
                )
            }
        }

        composeTestRule.onNodeWithText("Map view unavailable").assertIsDisplayed()
        composeTestRule
            .onNodeWithText("A Google Maps API key has not been configured.", substring = true)
            .assertIsDisplayed()
    }

    /** The fallback must appear even with an empty provider list (no compounding failure). */
    @Test
    fun interactiveMapView_withoutRealApiKey_andNoProviders_showsFallback() {
        composeTestRule.setContent {
            SkillsExchangeTheme {
                InteractiveMapView(
                    providers = emptyList(),
                    selectedProvider = null,
                    onSelectProvider = {},
                    radiusMiles = 10.0
                )
            }
        }

        composeTestRule.onNodeWithText("Map view unavailable").assertIsDisplayed()
    }

    /**
     * The fallback must tell the user how to fix it. A silent grey box would leave
     * the D1 symptom in place even though the crash is gone.
     */
    @Test
    fun fallback_explainsHowToConfigureTheKey() {
        composeTestRule.setContent {
            SkillsExchangeTheme {
                InteractiveMapView(
                    providers = emptyList(),
                    selectedProvider = null,
                    onSelectProvider = {},
                    radiusMiles = 10.0
                )
            }
        }

        composeTestRule
            .onNodeWithText("Add MAPS_API_KEY to your .env file", substring = true)
            .assertIsDisplayed()
    }
}
