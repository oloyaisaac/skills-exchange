# VERIFICATION_02 — R02: Senior Android Client Engineer (Kotlin / Jetpack Compose)

**Independent verification** of the R02 assignment against `cb61a31` (`main`), executed on
`arena/c5549ee2-skills-exchange`. **Date:** 2026-11-20.
**Verdict: VERIFIED COMPLETED**, with one wording correction handed back (§7) and one dynamic limit
stated rather than smoothed over (§5).

Sources read in full before touching the tree: `reviewers/ANDROID_CLIENT_REVIEWER.md` (79 lines — deleted after verification, text in §9),
`review_results/prompt2/REVIEW_02.md` (127 lines), the one-sentence completion declaration, and
`REVIEWER_IMPLEMENTATION_STATE.md` §R02.

## 1. What the assignment demands (passing conditions, quoted)

> PASS requires: client source compiles, no blocking lifecycle defect, permission denial paths verified,
> single-flight on all money/SOS actions, no fabricated success UI, and Compose UI tests exist for the
> four critical flows.
> FAIL if any flow can display success without server confirmation or if `ActiveJobScreen.kt` remains in
> the release source set.

## 2. The declaration again carries no evidence

`reviewer_completions/REVIEWER_02_DONE.md`: "I finished the work for this reviewer." No file, no test,
no run. Every claim below was re-derived from source and from runs generated from this tree.

## 3. Condition-by-condition verification

| # | Condition | Verdict | Evidence re-derived by the verifier |
|---|---|---|---|
| 1 | Client source compiles | **PASS** | Android run **44873267251**: `:app:compileDebugKotlin` success, `:app:testDebugUnitTest` success. Test source set compiles too (it is part of the same task). |
| 2 | No blocking lifecycle defect | **PASS** | `OnboardingScreen.kt` now has **13 `rememberSaveable`** declarations and **0** plain `remember {` for the form (step, role, name, phone, OTP, district, cooldown, OTP panel). `OnboardingComposeTest` proves restore with `StateRestorationTester`. No `GlobalScope` or `runBlocking` anywhere in `app/src/main`. The connectivity observer the review flagged is a `callbackFlow` whose `awaitClose` unregisters the network callback — cancellation is explicit, not assumed. |
| 3 | Permission denial paths verified | **PASS (statically; dynamically UNVERIFIED)** | `MainScreen.kt:97–117`: a denied result calls `viewModel.denyGpsPermission()` and the flow continues to manual district entry — it does not block. A rationale dialog exists and is reachable (`MainScreen.kt:213, 841, 1022`), which the review had listed as unreferenced dead UI. No device was available, so the dialog's actual appearance is UNVERIFIED. |
| 4 | Single-flight on all money/SOS actions | **PASS** | Escrow: `EscrowReleaseState.Releasing` disables both release buttons (`EscrowAndProofSheet.kt:1323, 1397`) and `EscrowGatingComposeTest` proves "the release action is disabled while a release is running". SOS: `sosDispatchInFlight` guard at `SkillsExchangeViewModel.kt:1044–1070` with `try/finally` reset. |
| 5 | No fabricated success UI | **PASS** | `SkillsExchangeRepository.updateEscrowStatus` refuses `RELEASED_TO_WORKER` unless `serverConfirmed` (`:703–713`), and the only two callers of `applyServerConfirmedEscrowRelease` are inside the callable's success branch (`SkillsExchangeViewModel.kt:884, 962`). The sheet can no longer request a release; it posts `CASH_SETTLEMENT_ACKNOWLEDGED`, `WORK_IN_PROGRESS` and `PROOF_SUBMITTED`. `SecurityRulesInvariantsTest:109` independently asserts `RELEASED_TO_WORKER` is never an allowed client value. |
| 6 | Compose UI tests exist for the four critical flows | **PASS** | `OnboardingComposeTest` (2), `CriticalFlowComposeTest` (3 — job posting validation, quote comparison/accept, pipeline open), `EscrowGatingComposeTest` (2 — failed release surfaced, action disabled while running). All execute in CI: the run publishes XML with **7 tests, 0 failures** across those three classes. |
| — | FAIL condition: `ActiveJobScreen.kt` in the release set | **not met** | File absent from the tree (deleted in R01). |

## 4. Finding-by-finding re-test

| Finding (from `REVIEW_02.md`) | Status | Independent evidence |
|---|---|---|
| R02-P2-01 **CRITICAL** — escrow release can display success with no server call | **CLOSED** | Repository refuses an unconfirmed `RELEASED_TO_WORKER`; the sheet no longer emits that value; release runs through `releaseEscrowToWorker` and is applied only on success, with the failure surfaced as `releaseError`. Server side confirms the ordering: the provider is asked first and the job is marked released only on acceptance (`functions/src/index.ts:950–960`). |
| R02-P2-02 **HIGH** — no in-flight guard on escrow/SOS | **CLOSED** | `EscrowReleaseState.Releasing` + disabled buttons + test; `sosDispatchInFlight` with `finally` reset. |
| R02-P2-03 **HIGH** — onboarding uses `remember`, loses state on rotation/process death | **CLOSED** | 13 `rememberSaveable`, 0 plain `remember {`; restore test present and passing. |
| R02-P2-04 **MEDIUM** — SOS invents Kampala coordinates | **CLOSED** | No `0.3476/32.5825` anywhere; fallback is `0.0` with the real district, and the GPS path reports "position is unknown" rather than inventing a district (`:1255–1268`). |
| R02-P2-05 **MEDIUM** — Compose UI tests absent | **CLOSED** | Seven tests across three classes, executing in CI. |
| R02-P2-06 **MEDIUM** — no `FLAG_SECURE` on identity/PIN surfaces | **CLOSED** | `SecureScreen()` applied in `EscrowAndProofSheet.kt:93` (PIN/escrow), `GuarantorVerificationSheet.kt:115` (NIN), `AccountComplianceDialog.kt:72,354`; the helper sets `FLAG_SECURE` and clears it on dispose. |
| R02-P2-07 **LOW** — client reads `firebaseService` directly | **CLOSED** | `grep` for `viewModel.firebaseService`/`.firebaseService.` under `ui/` returns 0; enforced by `check_architecture_guards.mjs`. |

## 5. What could not be executed (stated, not implied)

- **No device and no emulator**: rotation, process death, permission dialogs, SOS UI and the sheets are
  verified by Robolectric Compose tests and by reading the code, not on hardware. Every dynamic claim in
  this report is therefore "tested on the JVM" or "read", never "seen on a device".
- The Compose tests run as Robolectric unit tests (`app/src/test`), not as instrumented
  `androidTest`; there is one `androidTest` file (`GreetingScreenshotTest.kt`) and it is not part of
  the CI evidence chain.
- No direct unit test asserts the repository's refusal branch itself; the property is pinned from both
  ends instead (rules invariant test + Compose gating test). Recorded as a coverage nicety, not a defect.

## 6. Tests executed during this verification

| Suite | Where | Result |
|---|---|---|
| Compose critical-flow classes (`OnboardingComposeTest`, `CriticalFlowComposeTest`, `EscrowGatingComposeTest`) | CI `Android build` | 7 tests, 0 failures (from published JUnit XML) |
| Full Android unit suite | CI `Android build` | 66 suites, 494 tests, 0 failures |
| Local guard suite (architecture, PII-in-logs, links, docs, stories, hygiene, secret scan, SBOM) | this environment | PASS |
| Full CI workflow (backend 318, handlers, rules emulator, supply chain, documentation) | PR for this branch | re-run recorded in `VERIFICATION_EXECUTION_STATE.md` §2 |

## 7. Correction handed back

- **VC-03 (LOW, copy truthfulness).** `EscrowAndProofSheet.kt:1430–1431` renders "✅ Escrow Released &
  Payout Dispatched!" and "Funds sent to … via Mobile Money", while the server has at that moment
  accepted a payout request (`PAYOUT_PENDING`) and the repository's own notification says the payout is
  "queued with the mobile money provider". The screen is only reachable after server confirmation, so
  the passing condition holds; the wording asserts settlement that has not happened. Either say
  "released — payout queued with the provider", or show the provider status the server returned.

## 8. Approval

**VERIFIED COMPLETED.** Six passing conditions, all satisfied by evidence re-derived from the tree and
from runs generated from it; the review's FAIL condition (fabricated success without server
confirmation) is closed at both the repository and the rules layer. Approved on 2026-11-20.

One-sentence completion declaration preserved verbatim from
`reviewer_completions/REVIEWER_02_DONE.md`:

> I finished the work for this reviewer.

Instruction file `reviewers/ANDROID_CLIENT_REVIEWER.md`: **deleted after verification** on 2026-11-20;
the filename is recorded in `VERIFICATION_MASTER_REGISTER.md`, every inbound reference now points here,
and the assignment's full text is preserved verbatim in §9.

## 9. Preserved assignment text (verbatim)

Deleted file: `reviewers/ANDROID_CLIENT_REVIEWER.md`. Nothing below was edited.

```markdown
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
```
