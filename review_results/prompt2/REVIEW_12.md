# R12 — UI/UX & Accessibility Specialist

| Field | Value |
|---|---|
| Reviewer | R12 — UI/UX & Accessibility (P1) |
| Assignment | `reviewers/UI_UX_ACCESSIBILITY_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no UI, copy or theme changed |
| Rendering / TalkBack | **UNVERIFIED** — no Android build, emulator or device |
| Verdict | **FAIL** |

## 1. Localisation

- `app/src/main/res/values/strings.xml` contains **one** string resource.
- Every screen and component builds copy inline: `Text("…")`, notification bodies, error strings —
  several hundred literals across 26 components and the ViewModel.
- The app targets Uganda, Kenya, Tanzania and Rwanda but ships English only; no `values-sw`,
  `values-lg`, `values-rw` folders and no pseudo-locale configuration.
- `supportsRtl="true"` is declared, but with hardcoded `Arrangement.Start/End` and literal strings the
  RTL path is untested.

This alone fails R12's passing condition ("all user-facing copy externalised and translatable").

## 2. Accessibility semantics

- 171 `Icon(` call sites, 176 `contentDescription` occurrences, but **59 are `contentDescription = null`**.
  Some are decorative and correctly null; the review could not distinguish decorative from meaningful
  without rendering, so the count is an upper bound of risk, not 59 defects.
- Critical icon-only controls rely on adjacent text in several places (e.g. SOS button has both icon and
  label), which is acceptable; the dispute/PIN sheets have icon+text too.
- No `semantics { }`, `heading()`, `liveRegion`, `Role`, `stateDescription` or `testTag` usage was found;
  screen-reader structure (headings, form labels, error announcement) is therefore improvised.
- Form fields use `placeholder` text rather than persistent labels in onboarding; a screen reader hears
  the placeholder as the label and loses it once text is typed.

## 3. Touch targets and layout

- Several custom controls are 28–32 dp (`EscrowAndProofSheet.kt:591`, SOS icon at 28 dp,
  `:759` 14 dp icon inside a small button). Rows within `clickable` may extend the hit area, but the
  declared sizes are below the 46–48 dp WCAG/Android minimum and no `minimumInteractiveComponentSize`
  override is used.
- Font scale: all text uses `sp`, which scales; layouts with fixed heights and single-line labels
  (chips, PIN boxes, bottom sheets) are likely to clip at 200% — unverified without rendering.
- Small screens: no `WindowSizeClass`/adaptive layout; the shell is a fixed bottom bar + modal sheets.

## 4. State coverage

- Loading/error states exist for dispute and rating submissions (`isSubmitting*` flags) and for
  collection status.
- Escrow actions have **no** in-flight or error state (R02) — the button remains enabled and gives no
  feedback; the local status changes immediately.
- Offline state is a banner (`OfflineStatusBar`); it does not say which actions are queued.
- Empty states exist for queues/maps (fallback overlays), but admin queue emptiness was not verified.

## 5. Safety- and money-critical copy

| Copy | Location | Issue |
|---|---|---|
| "Live GPS location & job telemetry broadcasted to local emergency contacts and platform security" | `Repository.kt:2100` | asserted before any SMS/FCM result; no recipients case is invisible |
| "Your emergency alert has been sent. Help is on the way." | `Repository.kt:2133` | promises help that the platform does not guarantee |
| "Escrow Released! Funds payout dispatched…" | `Repository.kt:662` | shown on a local-only write (R02/R08); payout is not dispatched |
| "Guarantor National ID numbers are encrypted" | `GuarantorVerificationSheet.kt:166` | overstates protection (R09) |
| "Data is stored with encryption-at-rest" | `AccountComplianceDialog.kt:178` | platform-level only (R09) |

These are deception risks for low-literacy users and must be corrected before any usability sign-off.

## 6. Mode switching and information architecture

- Dual-role switching exists in the drawer/account centre and state is retained in the ViewModel.
- Client and worker views share the same shell; the "Offer Skills"/"Hire a Neighbor" labels are clear,
  but the worker dashboard's KYC gate is a separate screen without a progress indicator across steps.
- Back behaviour: sheets are modal with explicit dismiss; no accidental-exit confirmation on the
  onboarding form (input loss is covered by R02).

## 7. Theming and contrast

- Light and dark schemes exist (`Theme.kt:31–62`) with system-follow and override.
- No contrast measurements were possible without rendering; several hardcoded `Slate*` colours and
  alpha-tinted text (e.g. `Color.White.copy(alpha = 0.7f)`) on dark backgrounds are likely below 4.5:1.
  Treat contrast as **UNVERIFIED**, not passed.
- No reduced-motion handling; animations are minimal.

## 8. Testing

- No Compose UI, screenshot-baseline or accessibility test beyond three Robolectric screenshot helpers
  (`Greeting`, `MapEmptyProvidersOverlay`, `MapsApiKeyFallback`).
- No TalkBack, Accessibility Scanner, font-scale or pseudo-locale evidence exists in the repository.

## 9. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R12-P2-01 | HIGH | One string resource; all copy hardcoded English for a four-country market | `res/values/strings.xml`; inline `Text("…")` |
| R12-P2-02 | HIGH | No accessibility semantics (headings, labels, live regions); 59 null descriptions to triage | grep counts; `semantics` = 0 |
| R12-P2-03 | HIGH | Safety/money copy overstates outcomes (SOS dispatch, escrow release, encryption) | `Repository.kt:2100,2133,662`; `GuarantorVerificationSheet.kt:166` |
| R12-P2-04 | MEDIUM | Sub-48 dp interactive targets (28 dp SOS/PIN controls) | `EscrowAndProofSheet.kt:591,759` |
| R12-P2-05 | MEDIUM | Escrow actions lack loading/error/disabled states | `EscrowAndProofSheet.kt:1173,1224` |
| R12-P2-06 | MEDIUM | No contrast, font-scale, TalkBack or pseudo-locale evidence | repository absence |
| R12-P2-07 | LOW | Sensitive screens not screenshot-protected (also R07) | no `FLAG_SECURE` |

## 10. Verdict

**FAIL.** Dark mode, a consistent visual system and explicit modal dismissals are present, but the app
is not localised, has no accessibility semantics layer, has deceptive safety/money copy, and no
accessibility testing evidence. All rendering-dependent claims are **UNVERIFIED**.
