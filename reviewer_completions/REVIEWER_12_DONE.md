# REVIEWER_12 — UI/UX & Accessibility: DONE

I finished the work for this reviewer.

- Review: `review_results/prompt2/REVIEW_12.md` (verdict FAIL, 7 findings). Standard:
  `reviewers/UI_UX_ACCESSIBILITY_REVIEWER.md`.
- Branch: `arena/e2ab81c4-skills-exchange`, based on `main` @ `733b7cc`.
- The review could not render anything ("Rendering / TalkBack: UNVERIFIED"), so its findings were
  re-checked against the code first. Two were closed by earlier rounds; three are now closed with
  executed evidence; two are partially closed and say so.

## Findings

| ID | Severity | Outcome against this code |
|---|---|---|
| R12-P2-01 | HIGH | **Partly fixed — counted, not finished.** One string resource became 15: the panic/stand-down labels, the dispatch heading, the release action and its failure, the offline banner, the safety desk title and empty state, and four onboarding landmarks, in a new `values-sw` locale. The remaining **472 inline sentences** are recorded as a baseline that may fall but not rise (`scripts/check_ui_copy.mjs`), and a Swahili render is asserted in CI. **The translations are drafts with no native-speaker review**, and Luganda/Kinyarwanda/pseudo-locales are not started. `REM-056` stays `IN_PROGRESS`. |
| R12-P2-02 | HIGH | **Fixed.** `app/src/main/java/com/example/ui/a11y/Accessibility.kt` supplies `sectionHeading()`, `emergencyLiveRegion()`, `announcementLiveRegion()`, `describeState { }` and `minTouchTarget()`, applied to the SOS panic control, the dispatch verdict, the release feedback, onboarding field errors, the safety desk and the offline banner. `AccessibilitySemanticsTest` asserts `Heading` on the role question, `LiveRegion=Assertive` on the dispatch verdict, `LiveRegion=Polite` on the offline banner, and a `stateDescription` on the panic button. The 58 null descriptions were **triaged**: every one is decorative or sits beside a text label — the three inside clickable contexts all have adjacent labels — so the count was an upper bound, as the review itself suspected. A guard now refuses an `IconButton` with no accessible name. |
| R12-P2-03 | HIGH | **Stale, with one residual fixed.** The SOS copy (R10), the escrow "payout dispatched" copy (R02) and the "encrypted" claims (R09) are already corrected in the code the review predates. The residual was in the privacy sheet: "our 24/7 neighborhood arbitration protocol freezes funds pending review" described a desk that is single-operator, unstaffed overnight and has no on-call rota (`docs/safety/SAFETY_DESK_RUNBOOK.md`). It now states what happens, how long it may take, and that nobody is on call. |
| R12-P2-04 | MEDIUM | **Fixed.** The panic control was a 40 dp Material button and the auth dismiss control a 28 dp one; **ten** `IconButton`s were constrained below the floor (24 dp filter, 24 dp GPS refresh and dismiss, 36 dp drawer and notifications, 28 dp share and notification settings, 20 dp remove-photo). All are at 48 dp now, and the two controls the tests render are asserted with `assertHeightIsAtLeast(48.dp)`. A guard refuses a sub-48 dp `IconButton`, scanning only the button's own modifier so an icon's size is not mistaken for the button's. |
| R12-P2-05 | MEDIUM | **Stale — closed by R02.** `isReleasingFunds` disables both escrow actions and renders "Releasing with the server..."; `releaseError`/`releaseMessage` are shown above them. This round added the semantics: the disabled button says why, and the outcome is announced. |
| R12-P2-06 | MEDIUM | **Partly fixed — measured, not rendered.** Contrast is no longer an opinion: `ContrastTest` computes WCAG ratios from the palette and gate them. It found two real defects — white on `IndustrialEmerald` at **3.77:1** (now 5.48:1) and `Slate400` on white at **2.56:1**, which was also being used for sentences (now a 3.5:1 graphics tone, with 19 text usages moved to `Slate500`, 4.76:1). A later run found `RedDanger` on its own error surface at **4.41:1** (now 5.24:1). Font scale is covered by a 200 % render of the escrow sheet. **Still missing:** any TalkBack pass, Accessibility Scanner run, screenshot baseline or dark-theme contrast assertions — `REM-058` stays `IN_PROGRESS`. |
| R12-P2-07 | LOW | **Stale — closed by R02.** `com/example/util/SecureScreen.kt` sets `FLAG_SECURE` on the identity, PIN, escrow and privacy surfaces and clears it on dispose. |

## Evidence

- Android job **489/489** unit tests (run 44795261468) — the 484 existing tests plus 7 semantics tests
  and 4 contrast tests. The first two runs of this round **failed and were fixed in the round**:
  a missing `androidx.compose.ui.test.assert` import (an extension function, not a member), then two
  real assertion failures the new tests caught — `RedDanger` at 4.41:1 on its own surface, and an
  externalised label whose typographic ellipsis did not match the existing test.
- `node scripts/check_ui_copy.mjs`: 15 strings across 2 locales, 472/472 inline sentences remaining
  before the baseline moves, no `Slate400` text, no unlabelled `IconButton`, no sub-48 dp `IconButton`.
  `--selftest` proves all five refusals can happen.
- The eleven pre-existing static guards still pass, and the documentation set is 29 documents and 36
  requirements.

## Not claimed

- **No human accessibility review.** Nothing here has been used with TalkBack, an Accessibility
  Scanner, a switch device or a screen curtain. The semantics tree is asserted; the experience is not.
- **No reviewed translation.** `values-sw` is a draft written in this round. The app must not be
  described as "available in Swahili".
- **472 inline sentences remain**, and the guard freezes that number rather than shrinking it.
- **Nothing was seen.** No screenshot, no device, no dark theme, no RTL. Roborazzi is configured but no
  baseline image is compared.
- `REM-055`, `REM-056`, `REM-057` and `REM-058` stay `IN_PROGRESS`: each acceptance criterion that
  requires a device, a scanner or a translator is unmet.
