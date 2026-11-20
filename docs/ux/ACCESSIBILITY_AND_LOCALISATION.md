# Accessibility and localisation

**Owner:** UI/UX lead · **Updated:** 2026-11-20 (R12) · **Status:** the layer exists and is tested; the copy is not finished

The reviewer's finding was that the app is usable but not navigable, and English-only for a market the
product targets in four languages. This document records what was actually changed, what is measured,
and the debt that is left — counted, not described.

## 1. What a screen-reader user gets now

The semantics live in one place, `app/src/main/java/com/example/ui/a11y/Accessibility.kt`, so the
intent is reviewable rather than scattered through 34 composables:

| Modifier | What it answers | Applied to |
|---|---|---|
| `sectionHeading()` | "can I jump to the next section?" | the onboarding step titles, the safety desk title, the dispatch verdict heading |
| `emergencyLiveRegion()` | "did something happen that I must act on?" | the platform's verdict on an SOS dispatch |
| `announcementLiveRegion()` | "did my action work?" | release failure and confirmation, onboarding field errors, an unreadable safety desk, the offline banner |
| `describeState { }` | "what is this control doing?" | the SOS panic/stand-down button, the release button while a release is running |
| `minTouchTarget()` | "can I hit it?" | the panic control, the release control |

Asserted in `app/src/test/java/com/example/ui/AccessibilitySemanticsTest.kt` (Robolectric, Compose
test rule): the offline banner carries `LiveRegion=Polite`; the role question carries `Heading`; the
dispatch verdict carries `LiveRegion=Assertive`; the panic button is at least 48 dp tall and carries a
`stateDescription`; and the whole escrow sheet still renders and stays hittable at a **200 % font
scale**, which is the setting most likely to break a fixed-height layout.

**Not verified:** TalkBack itself. Robolectric reads the semantics tree, which is what TalkBack reads,
but no test here speaks. A manual pass on a handset — with the screen curtain on — is still required
before any accessibility sign-off, and no such pass has been done.

## 2. Contrast, measured

`app/src/test/java/com/example/ui/theme/ContrastTest.kt` computes the WCAG 2.x ratio from the same
`Color` constants the app paints with, and fails the build below the floor. Two real defects fell out
of writing it:

| Pair | Before | After | Where it was used |
|---|---|---|---|
| white on `IndustrialEmerald` | **3.77:1** (fails 4.5:1) | 5.48:1 | every worker primary button, the escrow release |
| `Slate400` on white | **2.56:1** (fails even the 3:1 non-text floor) | 3.5:1 as a graphics tone | dialog placeholders, "Cancel", drawer captions, "10 min" durations |

`IndustrialEmerald` moved from `#059669` to `#047857` once, in `Color.kt`, so no call site changed.
`Slate400` became a graphics tone, and **19 text usages moved to `Slate500`** (4.76:1) — those are the
ones that were painting sentences, not icons. Three decorative glyphs (`•`, `➔`) stay on `Slate400`,
allow-listed by name in the guard.

Still **unverified:** dark-theme contrast (no dark palette pairs are asserted yet), rendered spacing,
and anything that depends on a real device's display. The review said contrast was "likely below
4.5:1"; it was, in two places, and both are fixed and now gated.

## 3. Localisation: a start, with the debt counted

Before R12 the app had **one** string resource. It now has **15**, in a new `res/values-sw/` locale,
covering the copy a user cannot afford to misunderstand: the panic and stand-down labels, the
emergency dispatch heading, the release action and its failure, the offline banner, the safety desk
title and empty state, and the four onboarding landmarks.

The strings are used through `stringResource(...)` in `OfflineStatusBar`, `SafetyDeskQueueSheet`,
`EscrowAndProofSheet` and `OnboardingScreen`, and a test renders the escrow sheet under
`@Config(qualifiers = "sw")` and asserts the Swahili label resolves — so the locale is exercised, not
just present.

**The translations are drafts.** They were written for this round and have **not** been reviewed by a
native speaker or a professional translator. `values-sw` is a foundation, not a shipped locale. Do not
describe the app as "available in Swahili" until that review happens.

**The remaining debt is 472 inline sentences** across 34 composables. `scripts/check_ui_copy.mjs`
records that number as a baseline and **fails the build if it rises** — so new copy must be a resource
even while the old copy is still inline. Lowering the baseline is the unfinished half of R12-P2-01.

Luganda (`values-lg`), Kinyarwanda and pseudo-locales are **not** started. The guard requires every
string in `values/` to exist in each locale that does exist, so a locale cannot be left half-done; it
cannot invent the missing languages.

## 4. Adding a locale

1. Create `app/src/main/res/values-<lang>/strings.xml` with **every** name from `values/strings.xml`
   (except `app_name`, which is exempt in the guard).
2. `node scripts/check_ui_copy.mjs` — it prints the count and refuses a gap.
3. Add the language to the copy matrix in the review documentation, and record who reviewed the
   translation and when. An unreviewed translation is a draft, not a locale.

## 5. Gaps, in the order they matter

1. **No human accessibility or translation review.** No TalkBack pass, no Accessibility Scanner run, no
   native-speaker review of `values-sw`.
2. **472 inline sentences** remain; the guard freezes that number but does not shrink it.
3. **No dark-theme contrast assertions**, and no rendered screenshots to compare.
4. **No RTL evidence.** `supportsRtl` is declared; nothing exercises it.
5. **No instrumented test.** Everything above runs on the JVM under Robolectric; nothing runs on a
   device. `app/src/androidTest` is still the template.
