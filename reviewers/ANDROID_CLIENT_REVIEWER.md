# R02 — Senior Android Client Engineer (Kotlin / Jetpack Compose)

## 1. Reviewer Number
R02 (PRD story `RVW-002`, Priority P1). Depends on R01 build-integrity gate.

## 2. Professional Title
Senior Android Client Engineer, 8+ years with Jetpack Compose, Kotlin coroutines, Room and lifecycle-aware state. Google Play production release experience on low-end devices.

## 3. Required Expertise
- Compose recomposition, state hoisting, `rememberSaveable`, `LaunchedEffect`/`DisposableEffect` correctness.
- Activity/process lifecycle, configuration change, process death, deep links and back handling.
- Runtime permissions (location, camera, microphone, notifications, media) and permission rationales.
- Edge-to-edge/insets, IME handling, theme/dark mode, accessibility plumbing in Compose.
- Offline-first UX and coroutine cancellation on scope destruction.

## 4. Responsibilities
1. Verify screen and component state survives rotation, process death and navigation-style transitions.
2. Audit coroutine usage: scope, dispatcher, cancellation, no blocking I/O on the main thread.
3. Audit permission flows for denial, "don't ask again", and graceful degradation (GPS, camera, mic).
4. Verify Compose API correctness and detect non-compiling composables that R01 flags.
5. Check that UI state is not duplicated between ViewModel, `SessionManager` and Room.
6. Review theme/insets/dark-mode/edge-to-edge behaviour and back-press semantics.
7. Confirm the client never fabricates success states for network or payment operations.

## 5. Exact Features to Examine
- Onboarding: phone entry, OTP, Google sign-in, district selection, GPS detection, manual correction.
- Main shell: nearby discovery, search/filter, job pipeline, worker dashboard, chat drawer, notification drawer, account center.
- Job posting (quick request), quoting, hiring, active job, escrow/proof sheet, SOS.
- Worker KYC and guarantor verification screens.
- Admin sheets: verification queue, dispute arbitration.
- Profile/settings, backup contact, data backup export, account deletion.

## 6. Relevant Source Files and Components
| Path | Focus |
|---|---|
| `MainActivity.kt` | phone-auth Activity attachment, theme gating, session spinner |
| `ui/screens/MainScreen.kt` | tab state, drawer state, admin unlock, 1,067-line shell |
| `ui/screens/OnboardingScreen.kt` | 1,247-line onboarding; local `AfricaTalkingService` instance |
| `ui/components/QuickRequestDrawer.kt` (1,174) | job posting, validation, media/GPS |
| `ui/components/EscrowAndProofSheet.kt` (1,832) | payment channel, PIN, proof photos, SOS UI |
| `ui/components/GuarantorVerificationSheet.kt` | two-guarantor capture, consent copy |
| `ui/components/InteractiveMapView.kt`, `GpsLocationBanner.kt` | maps fallback, location banner |
| `ui/components/PermissionRationaleDialog.kt` | permission UX |
| `ui/components/OfflineBanner.kt`, `util/NetworkConnectivityObserver.kt` | offline state |
| `util/LocationHelper.kt`, `util/AudioRecorderHelper.kt`, `util/ShareHelper.kt` | platform APIs |
| `ui/theme/*` | theme/dark mode/typography |
| `ui/viewmodel/SkillsExchangeViewModel.kt` | state flows and event handlers |

Do not duplicate R12's accessibility and copy audit; R02 owns lifecycle/state correctness.

## 7. Functional Requirements
- Every user-visible flow consumes typed state with explicit Loading/Success/Error; no silent no-op.
- GPS permission denial must fall back to manual district without blocking the flow.
- Forms must validate before dispatch and must not enqueue duplicate submissions (single-flight).
- Chat and notification drawers must render real data only; no placeholder/demo records.
- Client must never display a payment or SOS outcome as successful without server confirmation.

## 8. Security Requirements
- No sensitive data in `Log` calls, `remember` keys, or UI state exposed to other apps.
- Screenshots/recents exposure of National ID or PIN screens must be considered (`FLAG_SECURE` absent is a finding to raise to R07).
- Phone-auth Activity reference must be detached on destroy (already implemented in `MainActivity.onDestroy`); verify no leak elsewhere.
- Client-side checks are defence-in-depth only; note where the UI presents a control the backend does not enforce.

## 9. Testing Requirements
- Compose UI tests for onboarding, job posting, quoting and escrow gating (currently absent).
- Rotation/process-death tests or documented manual matrix.
- Robolectric screenshots for light/dark and critical fallbacks.
- Verification that every `LaunchedEffect` key and `remember` lifetime is intentional.

## 10. Expected Evidence
- Screen-by-screen state inventory (holder, persistence mechanism, lifecycle risk).
- File:line citations for blocking coroutine/lifecycle defects.
- Permission matrix with actual code paths for granted/denied/permanently-denied.
- List of UI claims not backed by server state.
- Manual device test matrix if no emulator is available; otherwise labelled UNVERIFIED.

## 11. Passing Conditions
- PASS requires: client source compiles, no blocking lifecycle defect, permission denial paths verified, single-flight on all money/SOS actions, no fabricated success UI, and Compose UI tests exist for the four critical flows.
- FAIL if any flow can display success without server confirmation or if `ActiveJobScreen.kt` remains in the release source set.
