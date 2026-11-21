# R02 — Senior Android Client Engineer (Kotlin / Jetpack Compose)

| Field | Value |
|---|---|
| Reviewer | R02 — Android Client (P1) |
| Assignment | `reviewers/ANDROID_CLIENT_REVIEWER.md` (read in full); file deleted after independent verification — text preserved in `verification/reports/VERIFICATION_02.md` §9 |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no client source changed |
| Compile / tests | **UNVERIFIED** — no JDK, Gradle runtime or Android SDK |
| Verdict | **FAIL** |

## 1. Areas examined

Onboarding (phone, OTP, Google, district, GPS), main shell (drawer/tabs/admin sheets), job posting,
job pipeline, escrow/proof sheet, SOS, KYC/guarantor, account centre, offline banner. 67 production
Kotlin files scanned; deep reads of `MainScreen.kt`, `OnboardingScreen.kt`,
`EscrowAndProofSheet.kt`, `QuickRequestDrawer.kt`, `SkillsExchangeViewModel.kt` and
`SkillsExchangeRepository.kt`.

## 2. Compilation status

Not executable here: `java`, `javac`, Gradle and `ANDROID_HOME` are absent. No Compose compile or
instrumented run was performed. Every runtime claim below is static inspection; the UI itself is
**UNVERIFIED**. One `androidTest` file exists (`GreetingScreenshotTest.kt`); Compose UI tests for the
four critical flows do not.

## 3. Fabricated success states (release-blocking)

`EscrowAndProofSheet` writes escrow status directly through the ViewModel/repository, which only
updates Room and posts a notification. No callable, provider response or server document is consulted:

| UI action | Call | Evidence |
|---|---|---|
| "Lock Escrow" (cash-settlement branch) | `onUpdateEscrowStatus("FUNDS_LOCKED_IN_ESCROW")` | `EscrowAndProofSheet.kt:1036` → `MainScreen.kt:573` → `ViewModel.kt:822` → `Repository.kt:654` |
| "Release" / "Resolve & Release Funds" | `onUpdateEscrowStatus("RELEASED_TO_WORKER")` | `EscrowAndProofSheet.kt:1173`, `:1224` |
| Proof submitted / work in progress | same path | `EscrowAndProofSheet.kt:1086`, `:1101` |

`Repository.updateEscrowStatus` writes the local row and emits "✅ Escrow Released! Funds payout
dispatched to worker's Mobile Money wallet." (`Repository.kt:654–663`) without any payout call. The
server release path added for REM-026 is only reached from `ViewModel.updateJobStatus` when a client
marks a job COMPLETED (`ViewModel.kt:764–781`); the escrow sheet does not use it. This satisfies R02's
explicit FAIL condition: a flow can display a successful payment outcome with no server confirmation.

## 4. Lifecycle and state

| Screen | State holder | Persistence | Risk |
|---|---|---|---|
| Onboarding | 10+ `remember { mutableStateOf(...) }` (`OnboardingScreen.kt:65–73`) | none | rotation/process death discards name, phone, OTP code, district and step |
| Main shell | ViewModel flows | Room/DataStore | OK |
| MainScreen | `remember { NetworkConnectivityObserver }`, `remember(selectedProvider, jobs)` | none needed | OK |
| Dialogs/sheets | local `remember` flags | none | acceptable, closes on rotation |

No `rememberSaveable` exists in `OnboardingScreen.kt` or `MainScreen.kt`. Text entry put aside for a
rotation (e.g. while reading an OTP SMS) is lost, which is a concrete UX/state defect.

## 5. Coroutines and lifecycle

- `LaunchedEffect(Unit)` at `MainScreen.kt:145` triggers the location flow once per composition, and
  only when permission is already granted; the permission launcher is separate. No duplicate-launch
  guard beyond composition identity.
- Work is launched in `viewModelScope`; no `GlobalScope` or blocking main-thread I/O was found in the
  inspected files.
- `NetworkConnectivityObserver` is created with `remember` and observed with
  `collectAsStateWithLifecycle(initialValue = true)`; its callback registration is not obviously
  cancelled in a `DisposableEffect` (worth confirming during device testing).

## 6. Permissions

Location: `RequestMultiplePermissions` (`MainScreen.kt:91`) handles both granted and denied; denial
calls `viewModel.denyGpsPermission()` and the UI keeps manual district selection. Camera/microphone
requests were not found in onboarding; media capture paths use the system picker. Notification
permission is requested post-login (ViewModel state). No "don't ask again" rationale dialog is shown
after permanent denial; the flow degrades silently to manual entry. `PermissionRationaleDialog` exists
but is not referenced from the onboarding flow.

## 7. Single-flight on money/SOS actions

- Escrow release buttons are not gated by an in-flight flag; `updateEscrowStatus` is fire-and-forget,
  so repeated taps write repeatedly and emit duplicate notifications.
- SOS: `ViewModel.triggerSosAlert` (`:887`) has no in-flight guard and no confirmation; repeated taps
  insert repeated SOS chat messages and notifications. The incident row uses a fixed id
  (`sos_$jobId`), so the incident itself is replace-on-write, but the chat/notification spam remains.
- Dispute and rating submissions DO have `isSubmittingDispute` / `isSubmittingRating`
  (`EscrowAndProofSheet.kt:62,66`), so the pattern exists and was not applied to escrow/SOS.

## 8. GPS fallback accuracy

`ViewModel.triggerSosAlert` falls back to fixed Kampala coordinates `0.3476, 32.5825`
(`SkillsExchangeViewModel.kt:894–895`) when neither GPS nor a profile location exists. An SOS with
invented coordinates can send responders to the wrong place; it should fail closed or mark the
location as unknown.

## 9. UI claims not backed by server state

- "✅ Escrow Released … payout dispatched" (local write only, §3).
- "🔒 Escrow Locked" from the cash-settlement branch (no money movement).
- Escrow status vocabulary is chosen by the UI; `momoPaymentState` exists (`MainScreen.kt:569`) but is
  not what the release buttons consult.

## 10. Security-adjacent observations

- No `FLAG_SECURE` anywhere: National ID photos, guarantor NIN and doorstep/PIN surfaces can appear
  in recents and screenshots (raised to R07; R02 records the absence with `grep` = 0 hits).
- Logs inspected in `FirebaseService.kt` contain exception messages only; no phone/OTP/token values
  were found in the sampled log statements.
- UI reads `viewModel.firebaseService` directly in `OnboardingScreen.kt:114,133` (also reported by
  R01); read-only, but it bypasses the repository boundary.

## 11. Findings

| ID | Severity | Finding | Evidence | Status |
|---|---|---|---|---|
| R02-P2-01 | **CRITICAL** | Escrow release/lock can be displayed as successful with no server call | `EscrowAndProofSheet.kt:1036,1173,1224`; `Repository.kt:654–663` | confirmed statically |
| R02-P2-02 | HIGH | No in-flight guard on escrow and SOS actions; duplicate notifications/messages | `EscrowAndProofSheet.kt:1173`; `ViewModel.kt:887` | confirmed statically |
| R02-P2-03 | HIGH | Onboarding form and step use `remember`, not `rememberSaveable` | `OnboardingScreen.kt:65–73` | confirmed statically |
| R02-P2-04 | MEDIUM | SOS falls back to invented Kampala coordinates when no location exists | `ViewModel.kt:894–895` | confirmed statically |
| R02-P2-05 | MEDIUM | Compose UI tests for the four critical flows are absent | only `GreetingScreenshotTest.kt` under `androidTest` | confirmed |
| R02-P2-06 | MEDIUM | No `FLAG_SECURE` on identity/PIN screens | grep 0 hits | confirmed statically |
| R02-P2-07 | LOW | Client read of `firebaseService` bypasses repository | `OnboardingScreen.kt:114,133` | confirmed statically |

## 12. Verdict

**FAIL.** R02's passing conditions require no fabricated success UI, single-flight on money/SOS,
verified permission paths and Compose UI tests; the escrow release path fails the first outright,
and the compile gate from R01 remains unexecuted. All dynamic behaviour (rotation, process death,
permission dialogs, device UI) is **UNVERIFIED**, not failed.
