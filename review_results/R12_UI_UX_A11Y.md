# R12 — UI/UX & Accessibility Specialist

| Field | Value |
|---|---|
| Reviewer | R12 — UI/UX & Accessibility (P1) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Semantics, labels, typography, localisation, UX states, formatting |
| Verdict | **FAIL** |
| Issues | 5 (2 HIGH, 3 MEDIUM) |

## Areas examined
Screen structure, state feedback, accessibility labels and semantics, text sizing, contrast, localisation,
money/date formatting, error presentation, destructive-action friction.

## Source files inspected
`ui/screens/*`, `ui/components/*`, `ui/theme/Theme.kt`, `ui/theme/Color.kt`, `res/values/strings.xml`,
`res/xml/*`; spot checks of every icon-only control.

## Features examined
Onboarding, home/feed, job detail, escrow sheet, chat, SOS, profile/account, admin sheets, empty and
error states.

## Tests performed
- Token counts: `rememberSaveable` 0, `BackHandler` 0, `stringResource` 0, `testTag` 0,
  `contentDescription = null` **59**, `semantics {` **0**.
- Font-size distribution: 150 × 11.sp, 100 × 12.sp, 86 × 10.sp, 30 × 9.sp, 4 × 8.sp.
- **Correction:** the earlier "99 × 5.sp" figure was a substring artifact; there are no 5.sp sizes.
- No emulator, TalkBack, contrast measurement or font-scale test.

## Verified working functionality
- A dark colour scheme is defined (`Theme.kt:31`) and most screens have explicit empty states (35 `isEmpty()` checks).
- Destructive account deletion requires typing `DELETE`.
- Loading states exist (17 progress indicators).

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R12-01 | HIGH | Icon-only controls are unlabelled for assistive technology. | UI semantics | 59 `contentDescription = null` out of 176 `contentDescription` attributes; 0 `semantics {` blocks | Every action labelled | SOS/call/delete icons unidentifiable | Label controls; add semantics for safety actions | TalkBack traversal of all critical screens |
| R12-02 | HIGH | Body/legal text is set below recommended minimum sizes. | Typography | 30 × 9.sp, 86 × 10.sp, 150 × 11.sp, 4 × 8.sp; PIN explanation at 10.sp | ≥12sp body | Hard to read, especially safety copy | Raise to ≥12sp; verify at 200% font scale | Rendered accessibility pass |
| R12-03 | HIGH | No localisation: all copy is inline English. | Strings | 0 `stringResource`; only `app_name` in `strings.xml`; 178 `Text("` literals | Resource strings + locale(s) | English-only for a multi-language market | Externalise strings; add Luganda | Locale switch + lint clean |
| R12-04 | MEDIUM | Money and error presentation are inconsistent. | UI formatting | Payout uses `amountUgx.toInt()`; no `NumberFormat`; raw `e.message` surfaced | Grouped, rounded currency; actionable errors | Truncated amounts; developer text | Central formatters and error mapping | Formatting unit tests + screenshot review |
| R12-05 | MEDIUM | Sheet/back behaviour is unpredictable. | Navigation/state | 0 `BackHandler`; 29 `ModalBottomSheet`; 0 `rememberSaveable` | Back closes sheet, state survives | Back exits with sheet open | Add `BackHandler` and saveable state | Rotation/back instrumentation |

## Missing functionality
Localisation, labelled controls, minimum typography, consistent formatters, UI test hooks.

## Security concerns
Unreadable safety copy (R12-02) and unlabelled SOS (R12-01) reduce the effectiveness of emergency UI.

## Performance concerns
None specific; inconsistent state causes recomposition churn (R02).

## Recommendations
1. Label every icon-only control and add tests.
2. Raise text sizes and verify at 200% scaling.
3. Externalise strings and add a locale.
4. Centralise money/error formatting.

## Limitations / unable to verify
No emulator/device, TalkBack, contrast tooling or screenshot test. Visual and screen-reader claims are
**unable to verify**; static counts are verified.

## Cross-references
R02, R10, R11, MR-054/MR-055/MR-056/MR-057/MR-058.
