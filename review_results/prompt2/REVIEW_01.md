# R01 — Principal Software Architect & Codebase Integrity Reviewer (Prompt-2 cycle)

| Field | Value |
|---|---|
| Reviewer | R01 — Architecture & Codebase Integrity (P0, gate G0) |
| Assignment | `reviewers/ARCHITECTURE_REVIEWER.md` (read in full); file deleted after independent verification — text preserved in `verification/reports/VERIFICATION_01.md` §9 |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Strict review only — no application source, rules, build files or tests changed |
| Android build | **UNVERIFIED — no JDK/Android SDK in this environment** |
| Backend build | PASS — `npm run build` exit 0; `npm test` 171/171 |
| Verdict | **FAIL** (build gate unverified + layering/single-source-of-truth violations) |

## 1. Scope executed

Read the full R01 instruction file and the project architecture documents (`README.md`,
`AGENT_STATE.md`, `DECISIONS.md`, `docs/traceability.md`). Inspected all 67 production Kotlin files
structurally and read the largest files in depth; inspected the Cloud Functions tree, both manifests
and the Gradle build topology. Ran the executable Node suite and every documentation checker. Did not
examine R02–R15 assignments.

## 2. Buildability (gate G0)

| Check | Result | Evidence |
|---|---|---|
| `functions/npm run build` | **PASS** | `tsc` exit 0 |
| `functions/npm test` | **PASS** | 171 tests, 171 pass, 0 fail (Node 22.22.3) |
| Android `:app:compileDebugKotlin` | **UNVERIFIED** | `java`, `javac`, Gradle runtime and `ANDROID_HOME` are absent |
| Android unit tests | **UNVERIFIED** | same missing toolchain |
| Doc/route/secret checkers | **PASS** | traceability 24 IDs, links 117, queries 50, secret scan clean |

Previous gate defects are **no longer present** at this HEAD:

- `FirebaseService.kt` has no literal `\n` sequences or stray brace in code (grep, 0 hits).
- `GeminiAudioTranscriptionService.kt` line 1 is `package com.example.data.remote` (corruption gone).
- `ActiveJobScreen.kt` no longer exists in the release source set.

Because the R01 passing condition explicitly requires a clean `:app:compileDebugKotlin`, the gate
cannot be closed here. This is a resource blocker, not evidence of a defect.

## 3. Layer map and dependency direction

Intended: Compose screen → `SkillsExchangeViewModel` → `SkillsExchangeRepository` → Room / `FirebaseService`.

Observed violations:

1. **UI reaches the Firebase gateway directly.** `OnboardingScreen.kt:114` and `:133` call
   `viewModel.firebaseService.getCurrentUser()?.uid`, bypassing the repository boundary.
2. **Two `FirebaseService` instances in one ViewModel.**
   `SkillsExchangeViewModel.kt:74` constructs one for the repository and `:78` constructs a second,
   publicly exposed instance. Each instance owns its own `_currentUserState`/auth listeners, so
   "who is signed in" has more than one in-memory source.
3. **ViewModel owns infrastructure.** `:71` opens the database and `:74–80` construct Firebase,
   Gemini, MoMo and SMS services; there is no injection seam, so none of this is substitutable in a
   test without Robolectric.

## 4. Responsibility concentration (god classes)

| File | Lines | Responsibilities observed |
|---|---|---|
| `data/repository/SkillsExchangeRepository.kt` | 2,898 | auth identity resolution, job lifecycle, escrow transitions, notifications, KYC, chat, cashout |
| `ui/viewmodel/SkillsExchangeViewModel.kt` | 2,792 | auth, onboarding, jobs, payments, admin, KYC, SOS, training, export |
| `ui/components/EscrowAndProofSheet.kt` | 1,832 | escrow state machine UI, proof upload, material advance, dispute entry |
| `ui/screens/OnboardingScreen.kt` | 1,245 | registration, OTP, GPS, role selection |
| `ui/components/QuickRequestDrawer.kt` | 1,174 | catalogue, pricing, scheduling |
| `ui/screens/MainScreen.kt` | 1,068 | shell, drawer, tabs, admin sheets, dialogs |
| `data/remote/FirebaseService.kt` | 1,362 | auth, Firestore, Storage, callables, FCM |

Job/escrow transitions exist in three places (ViewModel handler, repository transition function,
Firestore rules), and the ViewModel repeats the repository's notification/escrow side effects. No
decomposition plan is recorded in `DECISIONS.md` for either god class.

## 5. Dead or duplicate artifacts

- Root `AndroidManifest.xml` is an unused AOSP template (license header only); the manifest Gradle
  consumes is `app/src/main/AndroidManifest.xml`. It remains a misleading duplicate.
- No unreferenced screen/component was found: `AdminVerificationQueueSheet`, `AppNavigationDrawer`,
  `OfflineStatusBar`, `ServiceCatalogPickerModal`, `AccountDeletionDialog` and
  `PrivacyAndTermsSheet` all have call sites (checked by declaration name, not file name).
- No orphaned Kotlin source file was confirmed; the earlier `ActiveJobScreen.kt` orphan is gone.

## 6. Build topology and source-set hygiene

- Single `:app` module; `compileSdk 37`, `minSdk 24`, `targetSdk 36` (`app/build.gradle.kts:14–19`);
  version catalogue present. No module-boundary defect observed statically.
- The test source set contains 41 files. Two of them (`JobAuthorshipF4SecurityTest.kt`,
  `PhoneAuthIdentitySecurityTest.kt`) were flagged by a naive brace scanner; raw brace counts are
  balanced and manual inspection found no obvious defect, but without `kotlinc` this remains
  **UNVERIFIED** and is not recorded as a defect.

## 7. Documentation vs source

- `README.md:137` states "111 tests on Node 22.22.3 at the 2026-11-19 assessment"; the suite at this
  HEAD reports **171**. The figure is historical but presented as current context for the test command.
- Architecture documents describe the layer model as if UI never touches Firebase; §3 shows it does.
- `AGENT_STATE.md` correctly records that Android remains uncompiled, which matches this review.

## 8. Security-architecture observations (structural only)

- Client-declared identity is not trusted for job authorship: `resolveAuthoritativeUid` is the single
  path, and rules bind `postedByUid` to `request.auth.uid` (inspected, not re-tested here).
- The admin console is now gated by `getAdminAccess` rather than a local boolean (previous cycle), so
  no UI-only authorization gate was found for admin.
- The direct UI→`firebaseService` calls in §3 are read-only identity reads, but they establish a
  pattern that bypasses repository authorization.

## 9. Features examined

Bootstrap/navigation (`MainActivity`, `MainScreen`), unified account/role switching, job lifecycle and
escrow boundaries, KYC queue orchestration, admin sheets, offline/dual-write paths — structurally only.
No dynamic execution was possible for the Android side.

## 10. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R01-P2-01 | **CRITICAL (gate)** | Android buildability cannot be established; R01 may not PASS on unexecuted evidence | no JDK/SDK; `:app:compileDebugKotlin` not run |
| R01-P2-02 | HIGH | Two `FirebaseService` instances give auth state two owners; UI reads the gateway directly | `SkillsExchangeViewModel.kt:74,78`; `OnboardingScreen.kt:114,133` |
| R01-P2-03 | HIGH | Two god classes (2,898 / 2,792 lines) with duplicated job/escrow side effects and no agreed decomposition roadmap | file sizes; `DECISIONS.md` has no plan |
| R01-P2-04 | MEDIUM | Unused root `AndroidManifest.xml` remains a duplicate artifact | root file vs `app/src/main/AndroidManifest.xml` |
| R01-P2-05 | LOW | README test count (111) contradicts the current suite (171) | `README.md:137` |

## 11. Go/no-go

**NO-GO for a PASS verdict.** Gate G0 is not closed: the Android toolchain is unavailable, so the
required compile evidence cannot be produced, and two architectural violations are confirmed by
file:line. Per the programme's own vocabulary this review is `FAIL` with the build item explicitly
`UNVERIFIED`, not "build broken".

## What must happen next

1. Provide JDK 17 + Android SDK 37 (or authorize recording the build item as permanently UNVERIFIED
   for this environment) — required before R01 can be re-run to a verdict.
2. R02 must not start until the programme lead authorizes it; per the strict sequence the next
   reviewer is R02 (Android Client).
