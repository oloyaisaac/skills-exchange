# R11 — QA & Test Engineering Lead

## 1. Reviewer Number
R11 (PRD story `RVW-011`, Priority P1). Depends on R01; must run before any PASS verdict is accepted.

## 2. Professional Title
QA Architect / Test Engineering Lead, Android + serverless test strategy, release-gate design and evidence auditing.

## 3. Required Expertise
- Test pyramid for Android: pure JVM, Robolectric, Compose UI, instrumentation, screenshot (Roborazzi).
- Firebase Emulator Suite and Security Rules unit testing.
- Node test runner, TypeScript source testing and CI execution.
- Coverage measurement, flake detection, deterministic tests.
- Verification integrity: matching claims to reproducible commands and fresh artifacts.

## 4. Responsibilities
1. Inventory all test suites, counts and what each actually asserts.
2. Verify tests can be executed from a clean checkout with documented commands.
3. Assess coverage against critical paths: auth, money, KYC, SOS, rules, migrations.
4. Identify false confidence: tests that only test pure helpers while critical I/O is untested.
5. Audit historical verification claims against current HEAD.
6. Define release gates and required automation (CI, coverage thresholds, rules tests, device matrix).
7. Produce a test-gap remediation plan ordered by risk.

## 5. Exact Features to Examine
- Unit tests: auth/identity, job status authorization, quotes, ratings, SOS, cashout, persistence, taxonomy, validators.
- Screenshot tests: `GreetingScreenshotTest`, `MapEmptyProvidersOverlayTest`, `MapsApiKeyFallbackTest`.
- Instrumented tests: currently only `ExampleInstrumentedTest`.
- Cloud Functions tests: `guarantorReview`, `momoCollection`, `momoConfirmation`, `momoDisbursement`.
- Build logs and diagnostic artifacts used as evidence.
- PRD/TASKS/CHANGELOG "verified" claims.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/test/java/com/example/**` (42 files, ~7,780 lines) | all JVM/Robolectric tests |
| `app/src/test/java/com/example/data/remote/MoMoCredentialLeakSecurityTest.kt` | source-scanning security test (MTN markers only) |
| `app/src/test/java/com/example/data/*SecurityTest.kt` | authorization suites |
| `app/src/test/java/com/example/util/*Test.kt` | pure validators |
| `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt` | only instrumentation test |
| `functions/test/*.test.mjs` | 111 passing Node tests (executed 2026-11-19 on Node 22.22.3) |
| `functions/package.json` | `test` script enumerates four files |
| `app/build.gradle.kts` | test dependencies, Robolectric/Roborazzi config, Java 25 test launcher |
| `build_result*.txt`, `c123_verify_*.log`, `_device_test/DEVICE_TEST_REPORT.md` | historical evidence artifacts |
| `prd.json`, `TASKS.md`, `AGENT_STATE.md` | completion claims to audit |

## 7. Functional Requirements
- One command per layer runs the suite deterministically.
- Every critical rule in `firestore.rules` has an emulator test.
- Every money state transition has a test, including negative cases.
- Migrations from retained versions are tested.
- Test failures block release; no skipped tests without an owner and expiry.

## 8. Security Requirements
- Security tests must not be satisfiable by stubs; they must exercise the real authorization boundary.
- Secret scans must cover all providers (current leak test covers MTN markers only).
- Tests must not contain real credentials or PII.
- Test artifacts must not leak production data.

## 9. Testing Requirements
- Execute and record: `npm test` in `functions/` (expected 111 pass).
- Attempt and record Gradle unit tests; if the toolchain is unavailable, state exactly what is missing and mark all Android test claims UNVERIFIED.
- Produce a coverage report (JaCoCo) and per-module thresholds.
- Add rules emulator tests, Compose UI tests and a device smoke matrix.
- Flake analysis over repeated runs (minimum 3).

## 10. Expected Evidence
- Test inventory table: file, type, subject, dependencies, last verified date, status.
- Command transcript for every executed suite.
- Coverage report and gap list by critical path.
- Contradiction log between claimed and observed results (e.g. "BUILD SUCCESSFUL" logs versus non-compiling HEAD).
- Release-gate definition with blocking/non-blocking tests.

## 11. Passing Conditions
- PASS requires: reproducible one-command suites, green rules/money/migration tests, measured coverage with thresholds, CI-enforced gates, and no unverified completion claims in master documents.
- FAIL now: Android tests cannot be reproduced in this environment, there are no Firestore rules tests, only one placeholder instrumentation test, and master documents assert completed/verified states that the committed source contradicts.
