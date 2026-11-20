# REVIEW 01 — Principal Software Architect & Codebase Integrity

- **Reviewer:** R01 (PRD `RVW-001`, P0)
- **Date:** 2026-11-19
- **Commit:** `6b39a10` (baseline) — working tree reviewed at `58f12ca` (docs only; app source unchanged since baseline)
- **Verdict: FAIL**
- **Gate:** G0 Build — **FAIL**
- **Environment:** Linux sandbox; no `java`, `javac`, `kotlinc`, Gradle runtime or Android SDK. `gradlew` is committed as mode `100644`, so `./gradlew --version` returns *Permission denied*; `bash gradlew --version` returns *JAVA_HOME is not set and no 'java' command could be found*. Gradle execution is therefore **UNVERIFIED**. The verdict rests on static structural evidence that does not require a compiler.
- **Instruction file:** `reviewers/ARCHITECTURE_REVIEWER.md`

---

## 1. Scope reviewed

- `app/src/main/java/com/example/**` — 68 production Kotlin files, ~27.7k lines.
- Build topology: `app/build.gradle.kts`, `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`.
- Entry points and layering: `MainActivity`, `MainScreen`, `OnboardingScreen`, `SkillsExchangeViewModel`, `SkillsExchangeRepository`, `FirebaseService`, Room layer, 26 Compose components.
- Source-set hygiene: dead files, duplicate manifests, duplicate configuration.
- Out of scope (owned elsewhere): rule semantics (R07), money correctness (R08), test quality (R11), release pipeline (R14).

## 2. Evidence executed

### 2.1 Inventory
```text
$ find app/src -name '*.kt' | wc -l                 → 110
$ find app/src -name '*.kt' -exec cat {} + | wc -l  → 35,522
$ find app/src/main/java -name '*.kt' -exec wc -l {} + | tail -1
                                                    → 27,720 total (main)
$ find app/src/test -name '*.kt' | wc -l            → 41
```
Largest production files:

| Lines | File | Primary responsibility |
|---:|---|---|
| 2,979 | `data/repository/SkillsExchangeRepository.kt` | auth-adjacent domain rules, jobs, quotes, KYC, SOS, disputes, wallet, sync, chat |
| 2,749 | `ui/viewmodel/SkillsExchangeViewModel.kt` | all screen state, auth, payments, admin queues, GPS, FCM, theming |
| 1,832 | `ui/components/EscrowAndProofSheet.kt` | payment channel, PIN, proof photos, SOS, job completion |
| 1,247 | `ui/screens/OnboardingScreen.kt` | registration, OTP, Google, GPS, role selection |
| 1,242 | `data/remote/FirebaseService.kt` | Auth/Firestore/Storage/callable gateway |
| 1,174 | `ui/components/QuickRequestDrawer.kt` | job creation form |
| 1,067 | `ui/screens/MainScreen.kt` | shell, tabs, drawers, admin unlock |
| 826 | `data/db/Entities.kt` | 18 Room entities |
| 702 | `ui/components/JobPipelineKanban.kt` | job state UI |

### 2.2 Structural scan (strings/comments stripped, tokens matched)
Method: character scanner removes line/block comments and string/char literals, then matches `(){}` with a stack. Reproducible script recorded in the register evidence log. Results for the production source set:

```text
FirebaseService.kt                   | illegal-backslash lines=[709, 714]
SkillsExchangeRepository.kt          | unclosed=[('{', 17), ('{', 556), ('(', 579)]; extra-close=[('}', 631), ('}', 2956)]
ActiveJobScreen.kt                   | extra-close=[(')', 34), ('}', 117)]
```

### 2.3 Confirming excerpts (authoritative `nl -ba`)

**A. `SkillsExchangeRepository.kt` — constructor never closed; function body orphaned**
```text
579  val job = JobPostEntity(
...
615      recurringSchedule = if (jobType == "RECURRING") recurringSchedule else ""
616  db.jobPostDao().insertJob(job)          ← no closing ")" on the constructor
...
631  }                                        ← closes addJobPost while the call is still open
633  /**
634
635  /**
636      )                                    ← stray closing paren, file scope
637  db.jobPostDao().insertJob(job)           ← duplicate insert outside any function
638      budget: Double,                      ← parameter list floats at file scope
...
651  ): String? {
```
The compiler cannot parse this: an unclosed argument list at 579 followed by a second, orphaned declaration fragment. This is not a style issue; it is a hard syntax failure in the most business-critical class.

**B. `FirebaseService.kt` — literal `\n` outside any string/comment**
```text
709      /**\n     * AUDIT-C-PHOTO: ... */\n    }     ← comment text single-lined, then "\n" + "}" in code
711  suspend fun uploadJobPostPhoto(...): String? {
714      if (jobId.isBlank()) return null\n        // Fail closed ...\n        val uid = firebaseAuth...
```
The scanner reports illegal backslashes on lines 709 and 714 only. Line 711 also sits at column 0 with the class body apparently closed at 709, and the method body at 714–716 is a flattened copy of a doc comment and former implementation. The method is left without its real body or closing brace before line 718.

**C. `GeminiAudioTranscriptionService.kt` — invalid file prefix**
```text
1  ]=package com.example.data.remote
```
A leading `]=` token before the package declaration is a syntax error.

**D. `ActiveJobScreen.kt` — dead, non-compiling screen**
```text
9   import com.example.data.db.JobEntity                  ← type does not exist (no JobEntity in db/)
10  import com.example.ui.components.InteractiveMapView.MapWithFallbackFallback
                                                          ← target is `private` in InteractiveMapView.kt:74
20      skillsExchangeTheme {                             ← only `SkillsExchangeTheme` exists (Theme.kt:53)
24          .horizontalPadding(16.dp)                     ← not a Modifier extension in this repo
32          <MapWithFallbackFallback                      ← "<" is not valid Kotlin here
34          ) { showMap ->
49          color = Slate700,                             ← not imported
142         style = MaterialTheme.typography.body2        ← Material 2 API, not Material 3
149         Chip(label = Text(...), filterChipDefaults = ...)  ← signature does not exist
```
`grep -rn "ActiveJobScreen(" app/src` finds no call site: it is dead weight that still breaks the release source set. Brace/paren analysis finds an extra `)` at line 34 and an extra `}` at 117.

### 2.4 Topology observations
```text
$ grep -n "namespace\|applicationId" app/build.gradle.kts
13:  namespace = "com.example"
17:    applicationId = "com.aistudio.skillsexchange.nxptvq"

$ ls AndroidManifest.xml app/src/main/AndroidManifest.xml   → both exist
$ git ls-files | grep -c '^META-INF/'                        → 1+ (stale packaging metadata committed)

$ grep -nE "hilt|dagger|koin|navigation" app/build.gradle.kts
103:  // implementation(libs.androidx.navigation.compose)     (commented out)

$ grep -rn "AfricaTalkingService()" app/src/main/java
ui/screens/OnboardingScreen.kt:76:    val africaService = remember { AfricaTalkingService() }
ui/viewmodel/SkillsExchangeViewModel.kt:151: private val africaService = AfricaTalkingService()

$ grep -rn "SmsNotificationService()" app/src/main/java
ui/viewmodel/SkillsExchangeViewModel.kt:82: val smsNotificationService = SmsNotificationService()
data/repository/SkillsExchangeRepository.kt:16: private val smsNotificationService: SmsNotificationService = SmsNotificationService()
```

## 3. Findings

| ID | Sev | Finding | Evidence | Impact |
|---|---|---|---|---|
| A-01 | S1 | `SkillsExchangeRepository.kt` is syntactically invalid: `JobPostEntity(` at 579 is never closed; a duplicate insert and parameter list float at file scope (633–651); brace cascade to 2956. | `nl` excerpts §2.3A; scanner output §2.2 | Entire app fails to compile; job creation logic unusable and unverifiable |
| A-02 | S1 | `FirebaseService.kt` contains literal `\n` escapes outside strings/comments and a structurally broken method around 709–716. | scanner lines 709/714; `nl` excerpt §2.3B | Compile failure in the auth/Firestore/Storage gateway; blocks every client flow |
| A-03 | S1 | `GeminiAudioTranscriptionService.kt` starts with `]=package`, an invalid token before the package declaration. | `head -c 20` and line 1 | Compile failure; voice-job path unusable |
| A-04 | S2 | `ActiveJobScreen.kt` is non-compiling dead code: nonexistent `JobEntity`, private `MapWithFallbackFallback`, lowercase theme function, undefined `Slate*`, Material-2 `body2`, malformed `<` call. | §2.3D; no call sites | Release source set cannot compile; misleading "implemented" screen in tree |
| A-05 | S2 | Two god classes own most of the product: ViewModel 2,749 lines and Repository 2,979 lines, spanning auth, payments, KYC, SOS, admin, chat and UI state. | file sizes §2.1; function inventory (`submitWorkerKyc`, `initiateMoMoEscrowPayment`, `loadAdminSosQueue`, `requestWorkerCashout`, …) | Untestable units, merge conflicts, hidden coupling; every change risks unrelated regressions |
| A-06 | S3 | No dependency-injection or domain layer. Services are instantiated directly inside UI and data classes (`AfricaTalkingService()` in a composable; `SmsNotificationService()` twice), and repository/VM construct their own Firebase gateway. | §2.4 | Hidden globals, duplicate clients, impossible to substitute fakes; contributes to "tests pass but integration unverified" |
| A-07 | S3 | No navigation component; navigation is implicit state in the 2,749-line ViewModel and `MainScreen`. | `navigation-compose` commented out; `setMobileTab`, drawer booleans | Back-stack/process-death correctness cannot be reasoned about; deep links absent |
| A-08 | S3 | Package identity is incoherent: `namespace = "com.example"` vs `applicationId = "com.aistudio.skillsexchange.nxptvq"`, plus a duplicate root `AndroidManifest.xml` and committed `META-INF`. | build line 13/17; file listing | Confusing ownership, accidental debug/package mistakes, stale packaging metadata |
| A-09 | S3 | Dead/deprecated code retained in the release source set: `ActiveJobScreen`, deprecated `MoMoPaymentService` (fake payout), commented-out camera/Accompanist/Location dependencies, unused Navigation dep. | grep/build file | Increases attack surface and future confusion; hides what is actually shipped |
| A-10 | S4 | 41 JVM test files concentrate on pure policies and Room, while the only instrumented test is the template `ExampleInstrumentedTest`; no UI smoke exists. | `find`, test listing | Architecture is validated only where it is easiest to validate (handoff to R11) |

## 4. Requirements assessment

### Functional
- **FAIL.** The application cannot be built from this commit, so no screen or flow is guaranteed to function. `addJobPost` is structurally malformed, meaning the central job-creation path has no valid implementation.
- Role switching, KYC, escrow, SOS and admin flows cannot be exercised end-to-end.
- A screen documented as implemented (`ActiveJobScreen`, MAP-003) is dead and broken.

### Security
- **FAIL by dependency.** Authentication and payment authorization code cannot compile; no security property can be verified.
- Trust placement is otherwise sound in the reviewed layers (authoritative UID resolution, fail-closed returns), but architecture puts authorization logic in a 3k-line repository with no enforceable boundary.

### Testing
- **UNVERIFIED.** The test source set is structurally parseable for the sampled files; Gradle was not executable. Given A-01–A-03, `testDebugUnitTest` cannot pass on this commit regardless of test content.

## 5. Coverage gaps / UNVERIFIED

| Item | Status |
|---|---|
| Gradle `compileDebugKotlin` | **UNVERIFIED** (no JDK/SDK; wrapper not executable) |
| Whether other files have unresolved-symbol errors | **UNVERIFIED** — only structural scan performed |
| Runtime behaviour, process death, navigation back stack | **UNVERIFIED** |
| Exact count of dead functions/classes beyond `ActiveJobScreen` | **INFERRED** from grep, not exhaustive |
| Security properties | Deferred to R07/R06 |

## 6. Passing conditions outcome

| Condition | Outcome |
|---|---|
| Clean checkout compiles (`:app:compileDebugKotlin`) | **NOT MET** — not executable, and static evidence guarantees failure |
| All source files resolve | **NOT MET** — A-01…A-04 |
| No dead screen in the release source set | **NOT MET** — `ActiveJobScreen` |
| Layering violations documented with remediation plan | **PARTIAL** — documented here; plan below |
| God classes have decomposition roadmap | **PARTIAL** — roadmap below |

**Verdict: FAIL. Gate G0 is closed until a clean build transcript exists.**

## 7. Recommended remediation (not implemented)

Order matters; do not start with refactoring.

1. **Restore buildability from a known-good source.** Reconstruct `FirebaseService.kt`, `SkillsExchangeRepository.kt`, `GeminiAudioTranscriptionService.kt` from the last version that compiled (identify via history/branch) or re-apply the intended edit cleanly. Do not hand-edit around the corruption.
2. **Delete `ActiveJobScreen.kt`** or rewrite it against real types/signatures; it has no call sites, so deletion is the low-risk option.
3. **Add a build gate** (`:app:assembleDebug` + `:app:testDebugUnitTest`) before any further feature work.
4. **Decompose** the repository and ViewModel by domain (auth/session, jobs/quotes, KYC, payments/wallet, safety, admin) behind interfaces; remove direct service construction from UI.
5. **Unify identity**: one `namespace`/`applicationId` decision; remove root manifest and `META-INF`; delete unused dependencies.
6. **Introduce a navigation graph** and saveable state; then add process-death and back-stack tests.
7. Re-run this review on the repaired commit.

## 8. Residual risk and owner

- Risk: the corruption may extend to files not caught by structural scanning (type errors, unresolved references). Owner: R01, re-opened after remediation.
- Risk: the project's historical "BUILD SUCCESSFUL" evidence is not attributable to this commit; planning based on it is unsafe. Owner: R11/R15.
- Residual: no runtime verification performed in this environment.

## 9. Cross-references

- `ASSESSMENT_REPORT.md` F-001, F-019, F-020.
- `PRODUCTION_READINESS.md` §3.1, gate G0.
- R14 `reviewers/reports/REVIEW_14.md` — build is additionally non-portable and backend `tsc` fails.
- R11 — test suite cannot run on this commit; historical counts are unverified.
- R02 — UI lifecycle work is blocked until the source compiles.
