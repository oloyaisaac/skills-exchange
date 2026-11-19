# R11 — QA & Test Engineering Lead

| Field | Value |
|---|---|
| Reviewer | R11 — QA & Test Strategy (P1, Gate G7) |
| Assignment | `reviewers/QA_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Test inventory, executability, rules/migration/UI coverage, CI, evidence integrity |
| Verdict | **FAIL** — G7 not met |
| Issues | 6 (1 CRITICAL, 3 HIGH, 2 MEDIUM) |

## Areas examined
Test sources, runners, coverage tooling, CI wiring, critical-path coverage, reproducibility of
historical test claims.

## Source files inspected
`app/src/test/**` (41 files), `app/src/androidTest/**` (1 file), `functions/test/**`,
`app/build.gradle.kts`, root build files, `_device_test/`, `AGENT_STATE.md`, `CHANGELOG.md`, `TASKS.md`.

## Features examined
Unit/Robolectric suites, Node money tests, instrumentation smoke, screenshot artifacts, build/test
commands documented in the repo.

## Tests performed
- Counted `@Test`: **355 in `src/test` + 1 in `src/androidTest`**.
- `cd functions && npm ci && npm test` → **111 pass / 0 fail** (582 ms).
- `npm run build` → **exit 2** (8 TS errors).
- Checked coverage config (0 JaCoCo/Kover), rules tests (0), migration tests (0), CI files (0).
- Android tests could not be executed: no JDK/SDK and the module does not compile.

## Verified working functionality
- Node suite is genuinely executable and green (111 tests), including money-helper and confirmation tests.
- Several security-decision unit tests are well targeted (phone-auth refusal, rating authorization,
  cashout boundary, restore-identity fail-closed).
- Robolectric classes are configured consistently for SDK 36.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R11-01 | CRITICAL | The Android suite cannot execute because the module does not compile. | Test suite | 355 `@Test` in 41 files; structural build failure (R01-01); no JDK/SDK in environment | `./gradlew test` green | No Android test has current evidence | Fix build, then run in CI | Green test transcript at reviewed commit |
| R11-02 | HIGH | No coverage measurement or critical-path E2E. | QA | 0 JaCoCo/Kover; 1 placeholder instrumentation test; no Compose UI tests | Coverage + signup→pay→rate E2E | Regression risk unmeasured | Add coverage floor and E2E journeys | Coverage report + E2E run |
| R11-03 | HIGH | No Firestore/Storage rules tests. | Security tests | 0 rules test files/emulator config | Rule regression suite | Public-read defects shipped | Add emulator rules tests per actor | Rules suite fails on regression |
| R11-04 | HIGH | No Room migration tests. | Database tests | 16 migrations; 0 `MigrationTestHelper` | Per-version migration tests | Data-loss regressions unverified | Add migration tests | Upgrade test per version |
| R11-05 | MEDIUM | No UI automation hooks. | UI tests | 0 `testTag`; 1 orphan screenshot PNG and no framework | Stable selectors + screenshot suite | UI cannot be automated | Add `testTag`s and screenshot tests | UI suite runs in CI |
| R11-06 | MEDIUM | Device evidence is not reproducible. | Evidence | `_device_test/` logs, 31 MB APK, SQLite dump; no dated command/output | Reproducible evidence convention | Claims cannot be re-derived | Store dated logs and commands | Evidence audit |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R11-01, R11-04.
- **Not implemented:** R11-02, R11-03, R11-05.
- **Partially implemented:** R11-06.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
Executable Android suite, rules tests, migration tests, coverage, E2E, CI gate.

## Security concerns
The untested surfaces are exactly where the critical findings sit: rules, auth, SOS and money.

## Performance concerns
No performance tests or baselines (R13).

## Recommendations
1. Fix the build, then make `./gradlew test` a required CI check.
2. Add emulator rules tests and migration tests.
3. Add coverage and critical-path E2E.
4. Correct historical test claims.

## Limitations / unable to verify
Android tests, instrumentation and UI suites are **unable to verify** (no JDK/SDK, non-compiling
module). Node tests were executed; 111/111 pass.

## Cross-references
R01, R03, R07, R13, R14, MR-064/MR-065/MR-019/MR-046/MR-058/MR-066/MR-067.
