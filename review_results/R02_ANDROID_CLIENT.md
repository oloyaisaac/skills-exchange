# R02 — Senior Android Client Engineer (Kotlin/Compose)

| Field | Value |
|---|---|
| Reviewer | R02 — Android Client (P1) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649`; code baseline `6b39a10` |
| Areas | Compose screens, lifecycle, state, permissions, process death, onboarding |
| Verdict | **FAIL** |
| Issues | 6 (1 CRITICAL, 4 HIGH, 1 LOW) |

## Areas examined
All screens and components, state hoisting, lifecycle/process-death survival, permission handling,
onboarding and sign-in wiring, offline behaviour, sheet/back navigation, UI-level PIN handling.

## Source files inspected
`ui/screens/*` (MainScreen, OnboardingScreen, and all feature screens), `ui/components/*` (AuthModal,
EscrowAndProofSheet, AccountCenterSheets, DataBackupDialog, NavigationDrawer), `ui/viewmodel/SkillsExchangeViewModel.kt`,
`MainActivity.kt`, `ui/theme/*`.

## Features examined
Phone/Google onboarding, session restore, job posting, quoting, escrow sheet, chat, SOS, profile and
account deletion, notification handling, offline reconnect.

## Tests performed
- Static review of Compose state and lifecycle calls; counts of `remember`, `rememberSaveable`,
  `BackHandler`, `LaunchedEffect`.
- Traced `sendOtp`/`verifyOtp`/`completeOnboarding`/`loginUser` call chains.
- No build, emulator, device, rotation or process-death test (no JDK/Android SDK).

## Verified working functionality
- Permission requests use `rememberLauncherForActivityResult` (`AuthModal.kt:73,92`, `AccountCenterSheets.kt:577`, `MainScreen.kt:91`).
- Quote submission is single-flighted (`isSubmittingQuote`, VM `2041`) and reconnect flush is mutex-guarded (`flushMutex`, VM `2198`).
- Google sign-in is wired through `loginWithGoogle` (VM `1215`), subject to runtime verification.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R02-01 | CRITICAL | Phone onboarding marks the user signed in without a Firebase credential, so registration cannot complete. | Onboarding + ViewModel | `OnboardingScreen.kt:961,985` → `sendOtp` (VM `2312`) / `verifyOtp` (VM `2354–2405`) sets `isAuthenticated=true` from the ATT response; `completeOnboarding` calls `loginUser` (VM `1096`) which needs a Firebase UID; no `signInWithCredential` | Signed-in Firebase user after OTP | Local session only; `loginUser` fails on a clean install | Exchange OTP for a Firebase credential or custom token before setting auth state | Instrumented signup on a clean install asserting `auth.currentUser != null` |
| R02-02 | HIGH | User-visible transient state is lost on process death and sheets ignore back navigation. | Compose UI | 0 `rememberSaveable`, 0 `BackHandler`, 133 `remember {`, no `SavedStateHandle` | State survives recreation; back closes sheets | Selected tab/sheet/PIN input reset; back exits with sheet open | Use `rememberSaveable`/`SavedStateHandle` and `BackHandler` | Rotation and "don't keep activities" test |
| R02-03 | HIGH | Offline queue never replays; reconnect shows a message but sends nothing. | ViewModel | `flushPendingActions()` VM `2196–2215` logs a snapshot only; no WorkManager dependency | Queued actions sync on reconnect | Queue grows, nothing is sent | Drain queue via WorkManager with idempotency/backoff | Airplane-mode → reconnect E2E |
| R02-04 | HIGH | Doorstep PIN is rendered to the worker in the job sheet. | Escrow sheet | `EscrowAndProofSheet.kt:641,649` print `job.doorstepPin`; default `"4829"` (`Entities.kt:259`) | Resident-only secret, blind entry by worker | Both parties see the PIN | Generate per job server-side; show only to resident; verify server-side | UI test + server-side PIN verification |
| R02-05 | HIGH | All copy is inline; no localisation or scalable copy governance. | UI layer | 0 `stringResource`; only `app_name` in `strings.xml`; 178 `Text("` literals | String resources with locale support | English literals across UI | Externalise strings and add a locale | Lint `HardcodedText` clean; locale switch test |
| R02-06 | LOW | `CALL_PHONE` is requested although dialling can use `ACTION_DIAL` without permission. | Manifest | `AndroidManifest.xml:12` | Least-privilege permissions | Dangerous permission for SOS call | Use `ACTION_DIAL` and remove `CALL_PHONE` unless direct-call is required | Manifest review + Play policy check |

## Missing functionality
- No process-death handling, no back handling for 29 bottom sheets, no offline replay, no localisation.
- No UI test hooks (`testTag`), so automation of these flows is impossible (R12).

## Security concerns
The PIN disclosure (R02-04) defeats the presence check; the auth gap (R02-01) means identity is
client-asserted. Full analysis: R06/R07/R10.

## Performance concerns
Unbounded `remember` state and a 2,749-line ViewModel make recomposition and startup costs opaque
(R13). No measurement possible.

## Recommendations
1. Fix the OTP→Firebase binding before any UI polish.
2. Add saveable state and back handling; then an instrumented lifecycle suite.
3. Stop rendering the PIN to the worker.
4. Externalise strings; add `testTag`s for critical controls.

## Limitations / unable to verify
No build, emulator, device, TalkBack, rotation or process-death execution. All runtime statements are
static or **unable to verify**.

## Cross-references
R06 (auth), R10 (PIN), R12 (a11y), R13 (offline), R11 (tests), MR-022/MR-054/MR-060/MR-036/MR-056/MR-073.
