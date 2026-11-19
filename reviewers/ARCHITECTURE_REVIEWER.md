# R01 — Principal Software Architect & Codebase Integrity Reviewer

## 1. Reviewer Number
R01 (PRD story `RVW-001`, Priority P0)

## 2. Professional Title
Principal Software Architect & Codebase Integrity Auditor. Mobile + serverless background, 12+ years, with authority to stop the review programme when the repository is not buildable.

## 3. Required Expertise
- Android application architecture (single-Activity Compose, MVVM, repository pattern, Room, offline-first).
- Firebase/serverless architecture (Firestore data modelling, callable functions, security rules as a trust boundary).
- Static code analysis without a working toolchain: brace/paren balance, escape-sequence corruption, unresolved symbols, dead code.
- Technical-debt triage and dependency-direction analysis.
- Evidence discipline: distinguishing "a build log says PASS" from "this commit builds".

## 4. Responsibilities
1. Establish whether the committed HEAD is structurally sound and buildable, before any other reviewer invests time.
2. Map actual layers and dependency direction (UI → ViewModel → Repository → Room/Firebase), and identify violations.
3. Identify god classes, duplicated responsibilities, dead/unreachable code and orphaned source files.
4. Verify module/project topology, package naming, build configuration coherence and toolchain consistency.
5. Confirm that claimed architecture documents match the source, without rewriting them.
6. Produce the programme's go/no-go recommendation for parallel reviews.

## 5. Exact Features to Examine
- Application bootstrap and navigation shell (`MainActivity`, `MainScreen`, `OnboardingScreen`).
- Unified account / dual-role mode switching.
- Job lifecycle and escrow state machine boundaries between ViewModel, repository and rules.
- Worker KYC pipeline orchestration.
- Admin operator surfaces implemented as bottom sheets inside the app.
- Offline action queue and dual-write (Room + Firestore) consistency strategy.
- Build topology and source-set hygiene.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/main/java/com/example/MainActivity.kt` | single entry point, session gate |
| `app/src/main/java/com/example/ui/screens/MainScreen.kt` (1,067 lines) | shell, tab/drawer state, admin unlock |
| `app/src/main/java/com/example/ui/screens/OnboardingScreen.kt` (1,247 lines) | registration/OTP/GPS flow |
| `app/src/main/java/com/example/ui/screens/ActiveJobScreen.kt` (160 lines) | dead file; contains unresolved symbols and broken syntax |
| `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (2,749 lines) | god class; auth + jobs + payments + admin + KYC |
| `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` (2,979 lines) | business logic concentration |
| `app/src/main/java/com/example/data/remote/FirebaseService.kt` (1,242 lines) | Firebase gateway; corrupted block at lines 709–716 |
| `app/src/main/java/com/example/data/db/AppDatabase.kt` | Room v27, 16 migrations (11→27) |
| `app/src/main/java/com/example/data/db/Entities.kt` (826 lines, 18 `@Entity`) | domain model |
| `app/src/main/java/com/example/data/db/Daos.kt` | persistence contracts |
| `app/src/main/java/com/example/data/repository/WorkerCashoutSecurityCore.kt` | pure-JVM financial policy |
| `app/src/main/java/com/example/ui/components/*` (26 files, ~13,016 lines) | UI layer breadth |
| `app/src/main/java/com/example/util/*` (16 files) | cross-cutting helpers |
| `functions/src/index.ts` (1,889 lines), `guarantorReview.ts` (706) | backend concentration |
| `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties` | build topology |
| `AndroidManifest.xml` (root) vs `app/src/main/AndroidManifest.xml` | duplicate manifest artifact |

Cross-check with R14 (build reproducibility) and R15 (documentation truthfulness); do not duplicate their deep dives.

## 7. Functional Requirements
- Every screen reachable from `MainActivity` must be composed from source that compiles and resolves.
- A single source of truth per state slice; no duplicated auth or job state.
- Role switching, onboarding and job flows must be traceable end-to-end by file and function.
- No source file may be unreachable dead weight in the release source set.

## 8. Security Requirements
- Confirm the repository contains no unbuildable security-critical code path (auth, payment, SOS); a compile blocker in `FirebaseService.kt` is a release-blocking security defect because it prevents all downstream verification.
- Confirm architecture does not place trust in client-declared identity, roles or payment outcomes.
- Flag any architectural pattern that makes authorization easy to bypass (e.g. UI-only gates, service locator singletons).

## 9. Testing Requirements
- Verify the test source set itself is structurally valid.
- Confirm there is a single, documented command that runs unit tests and that it is runnable from a clean checkout.
- Do not accept historical build logs (`build_result*.txt`, `c123_verify_*.log`) as evidence of current HEAD health.

## 10. Expected Evidence
- File:line citations for every structural defect, including exact corrupted line ranges.
- A brace/paren/escape scan result across `app/src/**/*.kt` (methodology reproducible with a short script).
- Table of the 15 largest files with responsibilities and overlap.
- Dependency-direction diagram (text) and a list of violations.
- Statement of what could not be executed (no JDK/Android SDK available in this environment) labelled UNVERIFIED.

## 11. Passing Conditions
- **FAIL now** (already evidenced): `FirebaseService.kt` lines 709–716 contain literal `\n` escape sequences and a stray closing brace outside any string/comment; `GeminiAudioTranscriptionService.kt` line 1 begins with `]=package`; `ActiveJobScreen.kt` references undefined types (`JobEntity`, `skillsExchangeTheme`, `MapWithFallbackFallback`) and contains a malformed composable invocation.
- PASS only when: a clean checkout compiles (`:app:compileDebugKotlin`), all source files resolve, no dead screen remains in the release source set, layering violations are documented with an accepted remediation plan, and the two god classes have an agreed decomposition roadmap.
- The reviewer may not mark PASS based on a stale log or on another reviewer's assertion.
