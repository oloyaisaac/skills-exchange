# Test strategy and release gates

**Owner:** QA lead · **Updated:** 2026-11-20 (R11) · **Status:** gates exist and run in CI; gaps are listed in §7

This is the test plan the reviewer asked for: what is executed, where, with which command, what it
proves, and what would have to be true before a release could be called verified. Every number here
was produced by running the command in this document; where a number cannot be measured in the
remediation sandbox it is marked **UNVERIFIED** rather than estimated.

## 1. What runs, and where

| Layer | Suite | Command | Count | Runs in | Blocking |
|---|---|---|---|---|---|
| Backend units (pure TypeScript cores) | `functions/test/*.test.mjs` | `cd functions && npm test` | **318** in 33 files | local + CI (`Backend build and tests`), 318/318 in run 44799076234 | yes |
| Backend coverage floor | same files | `npm run test:coverage` | 318 | CI, same job | yes |
| Callable handlers | `functions/test/handlers.emulator.mjs` | `npm run test:handlers` under `firebase emulators:exec --only firestore` | **19** | CI (`Backend handler tests`), 19/19 in run 44789646720 | yes |
| Firestore rules (emulator) | `rules-test/firestore.rules.test.mjs` | `npm --prefix rules-test test` under the emulator | **26** | CI (`Firestore rules tests`) | yes |
| Storage rules | `rules-test/storage.rules.check.mjs` | `node rules-test/storage.rules.check.mjs` | static | CI (same job) | yes |
| Android JVM + Robolectric + Compose | `app/src/test` (65 files) | `./gradlew :app:testDebugUnitTest` | **494** | CI (`Compile and unit tests`), 494/494 in run 44799076234 | yes |
| Android coverage | `:app:jacocoTestReport` / `:app:jacocoTestCoverageVerification` | `./gradlew :app:jacocoTestReport :app:jacocoTestCoverageVerification` | measured per run | CI, uploaded as `android-coverage` | yes (floor) |
| Accessibility semantics | `app/src/test/java/com/example/ui/AccessibilitySemanticsTest.kt` | part of the Android unit suite | **7** | CI (`Compile and unit tests`) | yes |
| Colour contrast (WCAG maths, no rendering) | `app/src/test/java/com/example/ui/theme/ContrastTest.kt` | part of the Android unit suite | **4** | CI, same job | yes |
| Offline queue drain | `app/src/test/java/com/example/data/PendingActionDrainTest.kt` | part of the Android unit suite | **5** | CI, same job, after two rounds of failures it caught | yes |
| Provider retry rule | `functions/test/providerRetry.test.mjs` | `cd functions && npm test` | **7** | CI (`Backend build and tests`) | yes |
| Release build (R8 + shrink) | `.github/workflows/android.yml` `release-build` | `./gradlew :app:assembleRelease` | not a test — a gate | CI, run 44799912835: APK 6.3 MB, mapping 679,287 lines | yes |
| Documentation and static guarantees | `scripts/check_*.mjs`, `scripts/secret_scan.mjs` | see `docs/DOCUMENT_MAP.md` | 13 guards (4 with `--selftest`) | CI (`Documentation checks`, `Secret scan`) | yes |
| Instrumented (device) tests | `app/src/androidTest` | — | 1 template | nowhere | **no** — see §7 |

The backend suite count is asserted nowhere by hand: `npm test` discovers `test/*.test.mjs`, and
`scripts/check_test_registration.mjs` fails the build if a file in `functions/test/` is run by no
script, if `test:coverage` covers a different set from `test`, or if CI stops invoking any of the
three commands. Its `--selftest` proves the guard can fail.

## 2. Release gates

A gate is a command that fails the pull request. The following are blocking today:

1. `functions`: install, `tsc` build, `npm test` (310), the same suite repeated three times with an
   identical pass count, and `npm run test:coverage` against the floors in §3.
2. `functions-handlers`: the 19 handler tests on the Firestore emulator; the step asserts
   `# pass 19` and `# fail 0`, so a suite that silently runs nothing fails instead of passing.
3. `rules`: 26 emulator tests asserted `# fail 0`, plus the static Storage-rules check.
4. `android`: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, then
   `:app:jacocoTestCoverageVerification`.
5. `documentation`: link integrity, document map, traceability, README claims, architecture guards,
   query limits, consent version, backup policy, test registration and UI copy (each with a self-test),
   and the secret scan.

Non-blocking (run but do not gate): the `ci-schema` and `ci-logs` publication steps, the diagnostic
uploads, and `CodeRabbit`.

**Not a gate, and not claimed as one:** instrumented/device tests, screenshot diffing against a
baseline, provider sandbox calls, and any test that would need a real SMS, FCM or Mobile Money round
trip.

## 3. Coverage

**Backend** — Node's own coverage over the compiled pure modules (`lib/**/*.js`), floors enforced by
`--test-coverage-lines=90 --test-coverage-branches=80 --test-coverage-functions=90`. Measured on
2026-11-20 (Node 22.22.3): **lines 96.38 %, branches 85.94 %, functions 96.81 %**. The lowest module
is `momoDisbursement.js` at 78.45 % lines — the provider-response branches that cannot be exercised
without a sandbox account, which is why the floor is a floor and not a target.

`lib/index.js` (the 3 000-line callable entrypoint) is **not** in that number: it is only loaded by
the handler suite, which needs the emulator. It is exercised by 19 handler tests, not measured on its
own. Do not read 96 % as "the backend is 96 % covered" — read it as "the pure cores are".

**Android** — JaCoCo over the debug classes, excluding generated code (`R`, `BuildConfig`, KSP
`*_Factory`/`*_Impl`, Compose singletons, Hilt aggregated deps). `:app:jacocoTestCoverageVerification`
fails below **20 % line coverage**. The number is published on every run (artifact `android-coverage`
and the `Summarise coverage` step); the value for a given commit is in that artifact, not in this
document, because this sandbox has no JDK and cannot measure it.

The 20 % floor is deliberately far below a healthy target: it exists to catch a collapse (a suite that
stops running, a module that stops being exercised), not to certify quality. Raising it is a
follow-up, and it should be raised only against a measured number.

## 4. Determinism and flake policy

- The backend suite is pure Node with injected fakes: no network, no Firebase, no wall clock (fixed
  epochs in the fixtures). CI runs it **three times** and requires the same pass count each time, so
  an order-dependent or clock-dependent test fails the gate.
- The rules suite runs against the emulator with a fixed project id and explicit allow/deny
  expectations; denials are asserted before any document is created (a mistake that R09's CI caught).
- Android tests use Robolectric with a fixed SDK and an in-memory Room database.
- The handler gate proved itself the day it was added: its first execution failed on an assertion that
  treated `breachingSla` as an envelope field when the queue returns it per row. The exact-count
  assertion (`# pass 19`) is what made the failure visible instead of silent.
- **No repeated-run evidence exists for the Android or rules suites.** Three Android runs cost ~11
  minutes of CI each; that is a deliberate, stated gap (§7, gap 3), not an oversight.

## 5. Critical-path coverage

| Path | Unit | Handler (emulator) | Rules | Android |
|---|---|---|---|---|
| Money: collection, confirmation, payout cores | yes | yes (release/payout) | no | escrow gating (Compose) |
| Pricing and fee ledger | yes | via collection | no | unit |
| Doorstep PIN | yes (`doorstepPin.test.mjs`) | no | no | contract test (no code in the worker's copy) |
| Account deletion | yes | no | yes | unit |
| SOS raise/clear/acknowledge | yes (`sosStatus`, `sosDispatch`) | dispatch outcome (R11) | yes | raise/clear authorization |
| Reports and triage | yes (`reports`) | file, queue, resolve (R11) | yes | — |
| Ratings | yes (`ratingsCore`) | record, duplicate, stranger (R11) | yes | authorization (device side) |
| Guarantor intake / duplicates | yes (`guarantorIntake`) | no | yes | shape validation |
| KYC review | yes (`guarantorReview`, `guarantorRisk`) | no | yes | — |
| Room migrations | n/a | n/a | n/a | 11 → 29 chain + per-step tests |
| Identity / auth | yes (`session`, `otp`) | no | yes | several suites |
| Compose journeys (onboarding + rotation, posting, quoting, escrow gating) | n/a | n/a | n/a | 7 tests in 3 files |

## 6. Adding a test

- Backend: drop `functions/test/<area>.test.mjs`. `npm test` picks it up; the registration guard fails
  if you name it anything else without registering it.
- Rules: extend `rules-test/firestore.rules.test.mjs`. Assert denials on fresh document ids *before*
  creating anything, or the permissive rule answers instead of the rule under test.
- Accessibility: add the assertion to `AccessibilitySemanticsTest` (semantics, touch targets, font
  scale) or `ContrastTest` (a colour pair). A new hardcoded sentence must become a string resource —
  `scripts/check_ui_copy.mjs` fails otherwise.
- Android: add a `@RunWith(AndroidJUnit4::class)` Robolectric or plain JUnit4 test under
  `app/src/test`. Compose assertions belong in `com.example.ui` next to `CriticalFlowComposeTest`
  (which already covers posting, quoting and the pipeline) or `EscrowGatingComposeTest`.
- Handler: add to `functions/test/handlers.emulator.mjs` and bump the exact count in
  `.github/workflows/ci.yml` — the count is a gate, so bumping it is a deliberate act.

## 7. Gaps, most risky first

1. **No device or instrumented test run.** `app/src/androidTest` holds the template only. Nothing
   proves the app works on a handset: permissions, the map, FCM receipt, photo capture. **UNVERIFIED.**
2. **`lib/index.js` has no coverage number.** The 19 handler tests exercise the safety and money
   callables; the rest of the entrypoint (≈30 callables) is not executed by any automated test. The
   pure decisions behind them are, which is why this is second rather than first.
3. **No flake evidence for Android or rules suites.** The backend is run three times per PR; the other
   two layers are run once.
4. **Provider contracts are untested** (MTN, Africa's Talking, Gemini, Maps): there are no sandbox
   credentials in this environment, so the tests prove the fail-closed path, not the happy path.
5. **No screenshot baseline.** `GreetingScreenshotTest` and the Roborazzi setup exist, but no committed
   baseline is compared, so a visual regression would not be caught.
6. **The Android coverage floor (20 %) is low** and is not a quality target.
7. **No load or latency testing.** Nothing measures the p95 of a callable under concurrency.
8. **No TalkBack, Accessibility Scanner or native-speaker review.** The semantics tree is asserted and
   the palette is measured, but no human has listened to the app or read the Swahili drafts.
   `docs/ux/ACCESSIBILITY_AND_LOCALISATION.md` records the details.

## 8. Contradiction log

Claims found in the repository that the committed code did not support, and what was done:

| Claim | Where it was | What was true | Resolution |
|---|---|---|---|
| "171 tests" (backend) | `README.md` | 310 at the time of this round | README no longer states a count; it names the command |
| "355 tests" (Android) | `README.md` | 478 at R11, 489 after the R12 suites, 494 after R13 | same |
| "The Android job is intentionally absent until REM-001…" | `ci.yml` header | the Android job has existed since R01 | header rewritten |
| "The rules job arrives with REM-019" | `ci.yml` header | arrived in R07 | header rewritten |
| Build logs and APKs in the repository root used as evidence | root `*.log`, `*.apk`, `diag-*.txt`, `ui-*.xml` | described commits that no longer exist; two 32 MB APKs | removed in R11; the README says where evidence actually comes from |
| "Only one placeholder instrumented test" / "no Compose tests" | the review (`REVIEW_11.md`) | seven Compose tests exist since R02 | recorded as closed by R02, not re-implemented |

## 9. What has never been executed

Said once, plainly: no test in this repository has run against a deployed Firebase project, a real
handset, a Mobile Money sandbox, an SMS gateway or a Maps billing account. Every claim about those
paths is **UNVERIFIED**, and the suites are built so that the fail-closed branch is what they assert.
