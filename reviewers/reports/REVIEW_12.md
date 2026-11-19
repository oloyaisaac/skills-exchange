# REVIEW-12 — UI/UX, Accessibility & Localisation Review

| | |
|---|---|
| **Reviewer** | R12 — UI/UX, Accessibility & Localisation Reviewer (P1) |
| **Wave** | 3 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** |

## 1. Scope and evidence

Static review of 100+ composables in `ui/screens/` and `ui/components/` plus the theme. Counts are from
`grep`. No emulator, TalkBack session, or screenshot render was possible — visual and interaction
claims are **UNVERIFIED**.

## 2. Measurements

| Metric | Count | Assessment |
|---|---|---|
| `stringResource` uses | **0** | No localisation, no copy governance |
| Inline `Text("…")` literals | 178 | Same |
| `strings.xml` entries | 1 (`app_name`) | — |
| `contentDescription = null` | **59** of 176 | Many are decorative, but icon-only controls are included |
| `semantics { }` modifiers | 4 | Negligible |
| `testTag` | **0** | UI automation impossible |
| Font sizes | `5.sp` ×99, `9.sp` ×30, `10.sp` ×86, `11.sp` ×150 | Body text below the 12sp accessibility floor |
| `rememberSaveable` / `BackHandler` | 0 / 0 | State and navigation loss (R02-F2) |
| `ModalBottomSheet` | 29 | No focus trapping/restore found |
| `CircularProgressIndicator` | 17 | Loading states exist but are inconsistent |
| Dark theme | Defined (`Theme.kt:31`) | Present |

## 3. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R12-F1 | High | 99 occurrences of `5.sp` and 150 of `11.sp`; the doorstep-PIN explanatory text is `10.sp` (`EscrowAndProofSheet.kt`). | Critical safety/payment instructions are unreadable for low-vision users and on cheap devices. Fails WCAG 1.4.4 / Android a11y guidance. |
| R12-F2 | High | 59 `contentDescription = null`, only 4 `semantics`, 0 `testTag`. Icon-only actions (SOS, delete, call) rely on icons without labels. | TalkBack users cannot identify key actions; the safety path is the least accessible. |
| R12-F3 | High | No string resources at all; all copy — including legal/safety text — is inline. | No Luganda/Swahili/English locale support in a Ugandan market; legal corrections require a code release; copy drifts across 178 sites. |
| R12-F4 | Medium | 29 bottom sheets with no `BackHandler` and no focus restoration. | Back gesture leaves the sheet and the screen simultaneously; keyboard focus can strand behind a sheet. |
| R12-F5 | Medium | Destructive flows are inconsistent: account deletion requires typing `DELETE` (good), but job cancel/escrow actions have no equivalent friction. | Accidental irreversible actions. |
| R12-F6 | Medium | Money rendered via string interpolation and `toInt()` truncation (e.g. payout `amountUgx.toInt()`), no `NumberFormat` grouping. | Amounts can display rounded/truncated; "UGX 1,250,000" style grouping absent. |
| R12-F7 | Medium | Error copy is developer-facing in places (raw exception `e.message` surfaced to Snackbars). | Users see stack-trace fragments; no recovery guidance. |
| R12-F8 | Low | No `testTag`s and no screenshot tests mean UI regressions cannot be caught (R11). | Quality ceiling. |
| R12-F9 | Info | Theme tokens are centralized (`Color.kt`) and dark mode is defined; 35 `isEmpty()` empty states exist. | Positive baseline. |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Minimum body text ≥ 12sp | FAIL |
| Every interactive element labelled | FAIL |
| Localisable strings | FAIL |
| Predictable back behaviour | FAIL |
| Consistent loading/empty/error states | PARTIAL |
| Money formatting | FAIL |
| Test hooks for UI automation | FAIL |

## 5. What could not be verified

Contrast ratios, touch-target sizes, TalkBack order, RTL, font scaling at 200%, and animation jank.
**UNVERIFIED** — a device pass is mandatory before any PASS.

## 6. Passing conditions

1. All text ≥ 12sp body / 11sp captions, verified in a rendered screenshot pass.
2. `contentDescription` on every icon-only control; semantics for SOS and PIN.
3. Strings externalized to `strings.xml` with at least English + Luganda; no inline copy in
   legal/safety surfaces.
4. `BackHandler` for sheets; state survives process death.
5. Consolidated `UiState` (loading/empty/error/retry) per screen.
6. `testTag` on critical controls and a screenshot test suite.

## Recommended remediation (not implemented)

1. Raise body text to at least 12sp and verify on a rendered device pass.
2. Label every icon-only control; add semantics for SOS and PIN.
3. Externalise all strings with English + Luganda resources.
4. Add `testTag`s and screenshot tests for critical controls.

## 7. Cross-references

R02-F2/F5, R11, R10-F2, R13-F5; G7.
