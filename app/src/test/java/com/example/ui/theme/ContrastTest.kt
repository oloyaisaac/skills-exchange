package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R12-P2-06: contrast, measured instead of eyeballed.
 *
 * The review could not render, so it could only say the palette "likely" fails. This test computes the
 * WCAG 2.x contrast ratio from the same `Color` constants the app uses, so a palette change that
 * drops below the floor fails the build instead of failing a user.
 *
 * It is deliberately a pure JVM test: no Compose rule, no activity, no rendering. Contrast does not
 * depend on layout, and a test that needs a device is a test that runs less often.
 *
 * The pairs below are the ones the app actually paints. Each row says where it is used, so a future
 * edit knows what it is protecting.
 */
class ContrastTest {

    private fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
    }

    /** WCAG relative luminance. Compose `Color` components are already sRGB in 0..1. */
    private fun luminance(color: Color): Double =
        0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)

    private fun contrast(foreground: Color, background: Color): Double {
        val a = luminance(foreground)
        val b = luminance(background)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertContrast(
        label: String,
        foreground: Color,
        background: Color,
        minimum: Double
    ) {
        val measured = contrast(foreground, background)
        assertTrue(
            "$label measured %.2f:1, below the %.1f:1 floor".format(measured, minimum),
            measured >= minimum
        )
    }

    /** Body copy: 4.5:1 is the AA floor for text under 18pt. */
    @Test
    fun `text on its surfaces meets the AA body floor`() {
        // Primary copy on the app's white sheets and cards.
        assertContrast("Slate900 on white (headings, values)", Slate900, Color.White, 4.5)
        assertContrast("Slate800 on white (section titles)", Slate800, Color.White, 4.5)
        assertContrast("Slate700 on white (labels)", Slate700, Color.White, 4.5)
        assertContrast("Slate600 on white (secondary copy)", Slate600, Color.White, 4.5)
        assertContrast("Slate500 on white (captions)", Slate500, Color.White, 4.5)
        assertContrast("Slate600 on Slate50 (queue rows)", Slate600, Slate50, 4.5)
        assertContrast("Slate900 on Slate100 (ledger rows)", Slate900, Slate100, 4.5)
    }

    /** White on a filled button, which is how every primary action is drawn. */
    @Test
    fun `white on the filled action colours meets the AA body floor`() {
        assertContrast("white on SignalBlue (client actions)", Color.White, SignalBlue, 4.5)
        assertContrast("white on SignalBlueDark (pressed)", Color.White, SignalBlueDark, 4.5)
        // R12-P2-06: this pair was 3.77:1 before the palette moved one step darker.
        assertContrast("white on IndustrialEmerald (worker actions)", Color.White, IndustrialEmerald, 4.5)
        assertContrast("white on IndustrialEmeraldDark", Color.White, IndustrialEmeraldDark, 4.5)
        assertContrast("white on RedDanger (SOS stand-down, delete)", Color.White, RedDanger, 4.5)
    }

    /** The offline banner and the emerald confirmation banner. */
    @Test
    fun `status banners keep their text readable`() {
        assertContrast("Slate900 on AmberUrgent (offline banner)", Slate900, AmberUrgent, 4.5)
        assertContrast(
            "IndustrialEmeraldDark on IndustrialEmeraldLight (release confirmation)",
            IndustrialEmeraldDark,
            IndustrialEmeraldLight,
            4.5
        )
        assertContrast("RedDanger on RedDangerLight (release failure)", RedDanger, RedDangerLight, 4.5)
    }

    /**
     * The one place the app knowingly steps below 4.5:1 — a decorative icon tint and the disabled
     * "swipe" affordance. Recorded here so the exception is a decision in code, not an accident in a
     * palette, and still held to the 3:1 non-text floor.
     */
    @Test
    fun `icon tints stay above the non-text floor`() {
        assertContrast("Slate400 icon tint on white", Slate400, Color.White, 3.0)
        assertContrast("Slate300 divider/graphic on Slate900 (dark)", Slate300, Slate900, 3.0)
    }
}
