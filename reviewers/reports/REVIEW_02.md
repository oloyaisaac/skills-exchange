# REVIEW-02 — Android Client Review

| | |
|---|---|
| **Reviewer** | R02 — Android Client Reviewer (P0) |
| **Wave** | 3 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` (`app/` subtree unchanged since `6b39a10`) |
| **Scope reviewed** | 68 production Kotlin files in `app/src/main/java/com/example/`, 41 test files read only for coverage context |
| **Verdict** | **FAIL** |

## 1. Scope and evidence base

Every file in `ui/screens/`, `ui/components/`, `ui/viewmodel/`, `MainActivity.kt` and the Compose theme
was read by `sed`/`grep`. Line numbers below are exact at the reviewed commit. No Android build or
device run was possible in this environment (`java` absent, no SDK, `gradlew` not executable), so
runtime behaviour is marked **UNVERIFIED** rather than assumed.

## 2. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R02-F1 | Critical | Onboarding calls `viewModel.sendOtp` (`OnboardingScreen.kt:961`) / `verifyOtp` (`:985`), which use **Africa's Talking device-side OTP**, then `completeOnboarding` → `loginUser` (`:1214` → VM `1096`). `loginUser` requires a Firebase UID, but `verifyOtp` (VM `2354–2405`) only sets `isAuthenticated = true` and a DataStore session — it never exchanges the code for a Firebase credential, and `signInAnonymously()` has **no production caller**. | A fresh phone signup has no Firebase identity, so every repository call that needs `auth.currentUser.uid` fails after onboarding appears to succeed. The Firebase phone-auth implementation in `FirebaseService.kt:345–435` and its passing tests are **dead code** not wired to the UI. |
| R02-F2 | High | `0` uses of `rememberSaveable` against `221` `remember`; `0` `BackHandler`; no `SavedStateHandle` in the ViewModel. | Sheet state, selected tab, PIN input and chat state are lost on process death; back navigation is default-only and cannot close the 29 `ModalBottomSheet`s predictably. |
| R02-F3 | High | Doorstep PIN is rendered in a `Text` to **both** parties (`EscrowAndProofSheet.kt` "Resident's Doorstep Security PIN: ${job.doorstepPin}", also shown once verified), and the PIN is default `"4829"` (`Entities.kt`). No `FLAG_SECURE` anywhere. | The "physical presence" control is disclosed on screen to the worker it is meant to authenticate; screenshots/recording capture it. |
| R02-F4 | Medium | `Polling` + manual refresh patterns; ViewModel is 2,749 lines with 90+ `viewModelScope.launch` blocks; `flushPendingActions` is a documented no-op (`2196–2215`). | No single source of truth for screen state; reconnect shows a Snackbar but replays nothing (R13-F4). |
| R02-F5 | Medium | All UI strings are inline literals: `0` `stringResource`, 178 `Text("…")` literals, a single `app_name` string. | No localization, no central copy review, and user-facing legal/safety claims cannot be corrected in one place. |
| R02-F6 | Low | Permission handling uses `rememberLauncherForActivityResult` correctly (`AuthModal.kt:73,92`; `AccountCenterSheets.kt:577`; `MainScreen.kt:91`), but `CALL_PHONE` is requested for the SOS dial action rather than using an `ACTION_DIAL` intent (no permission needed). | Unnecessary dangerous permission increases Play review scrutiny. |
| R02-F7 | Info | Single-flight guards exist and are used (`isSubmittingQuote` `2041`, `flushMutex.tryLock()` `2198`); admin unlock state is in-memory only (`MainScreen.kt:172+`). | Positive: duplicate money-triggering taps are guarded in these paths. |

## 3. Functional requirement checks

| Requirement | Result | Evidence |
|---|---|---|
| Phone signup end-to-end | **FAIL** | R02-F1. |
| Google sign-in | PASS (static) | `loginWithGoogle` wired (VM `1215`); runtime UNVERIFIED. |
| Escrow/PIN job flow | FAIL | R02-F3, plus release is not wired (R08-F2). |
| Offline/reconnect UX | FAIL | No replay (R13-F4). |
| Permission-denied recovery | PARTIAL | Launchers exist; no rationale UI found. |
| Process-death survival | FAIL | R02-F2. |

## 4. What could not be verified

Cold start, rotation, process death, TalkBack traversal, keyboard/IME behaviour, actual permission
dialogs, and end-to-end signup on a device. All are **UNVERIFIED**; none is credited as passing.

## 5. Passing conditions

1. Onboarding obtains a real Firebase credential (phone auth or custom token) before `isAuthenticated`
   is set, proven by a Robolectric/instrumentation test that asserts `auth.currentUser != null` after
   `verifyOtp`.
2. PIN is never rendered to the worker; it is entered blind, is per-job random, and is verified
   server-side.
3. `rememberSaveable`/`SavedStateHandle` used for user-visible transient state and `BackHandler`
   closes sheets; verified by rotation/process-death tests.
4. Strings externalized and a `strings.xml` locale exists.
5. Re-run on a device with a recorded logcat and screenshots.

## 6. Residual risk

Even with static fixes, the client cannot be certified without a build. The onboarding defect alone
means no new phone user can complete registration on a clean install.

## Recommended remediation (not implemented)

1. Bind onboarding to a real Firebase credential and delete the local-only signed-in path.
2. Add `rememberSaveable`/`SavedStateHandle` and `BackHandler`; test rotation and process death.
3. Stop rendering the PIN to the worker; generate it per job and verify server-side.
4. Externalise strings and add a locale before further UI work.

## 7. Cross-references

R06-F1 (auth), R08-F2 (release), R10-F2 (PIN), R11-F1 (no device suite), R12 (a11y), F-001/F-022.
