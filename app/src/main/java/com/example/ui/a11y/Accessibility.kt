package com.example.ui.a11y

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * R12-P2-02: the semantics this app was missing.
 *
 * Compose will read out every `Text` on screen, in tree order, with no notion of what is a heading,
 * what is an alert and what merely changed. That is usable with a screen reader and not navigable with
 * one: there is no way to jump between sections, and a result that appears after an action is silent
 * unless the user happens to swipe to it.
 *
 * These modifiers are deliberately semantic, not decorative. Each one answers a question TalkBack
 * asks:
 *
 * - [sectionHeading] — "can I skip to the next section?"
 * - [emergencyLiveRegion] — "did something happen that I must act on now?"
 * - [announcementLiveRegion] — "did my action succeed or fail?"
 * - [describeState] — "what is this control doing right now?" (a disabled button that never says why
 *   is a dead end for a screen-reader user)
 * - [minTouchTarget] — "can I hit it?" (48dp, the WCAG 2.2 target-size floor and Android's own
 *   minimum interactive size)
 *
 * Every one of them is applied to a composable that a test renders; see
 * `app/src/test/java/com/example/ui/AccessibilitySemanticsTest.kt`.
 */

/** Section titles. TalkBack's "headings" navigation jumps between these. */
fun Modifier.sectionHeading(): Modifier = this.semantics { heading() }

/**
 * A panic notice: an active SOS, or the platform's account of a dispatch that failed.
 *
 * Assertive, because the whole point is that a user who is not looking at the screen hears it without
 * having to navigate to it.
 */
fun Modifier.emergencyLiveRegion(): Modifier = this.semantics { liveRegion = LiveRegionMode.Assertive }

/** Errors, refusals and the server's verdict after an action: announced, but politely. */
fun Modifier.announcementLiveRegion(): Modifier = this.semantics { liveRegion = LiveRegionMode.Polite }

/**
 * The current state of a control, in the control's own vocabulary.
 *
 * `stateDescription` replaces TalkBack's default "disabled"/"on" reading, so a button that is waiting
 * on the server says so instead of going silent.
 */
fun Modifier.describeState(description: () -> String?): Modifier =
    this.semantics {
        val text = description()
        if (!text.isNullOrBlank()) stateDescription = text
    }

/**
 * The 48dp floor for anything a finger has to hit.
 *
 * Written as `heightIn` rather than a fixed size so a control keeps its own width and the fix does not
 * change a layout: only the hit area grows.
 */
fun Modifier.minTouchTarget(): Modifier = this.heightIn(min = 48.dp)
