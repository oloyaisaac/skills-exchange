# REVIEW-11 — QA & Test Strategy Review

| | |
|---|---|
| **Reviewer** | R11 — QA & Test Strategy Reviewer (P1, Gate G7) |
| **Wave** | 3 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** — Gate G7 not met |

## 1. Scope and evidence

Counted and sampled all test sources: 41 files, **355 `@Test` in `src/test` plus 1 in `src/androidTest` (356 total)**, 18 Robolectric
`@Config(sdk=[36])` classes, one orphan screenshot (`app/src/test/screenshots/greeting.png`). Ran the
Node suite: `npm test` → **111 passing**. Ran `npm run build` → **exit 2**. Checked coverage tooling,
CI, and the historical test claims in `AGENT_STATE.md`/`TASKS.md`.

## 2. Test inventory

| Layer | Count | Runs in this environment | Meaningful? |
|---|---|---|---|
| Kotlin unit/Robolectric | 355 tests / 41 files (+1 androidTest) | **No** (no JDK/SDK; project does not compile — F-001) | Mixed; security-decision tests are genuinely good |
| Node unit | 111 tests | Yes, all pass | Good on money helpers/webhook confirmation |
| Firestore rules | **0** | — | None |
| Instrumentation (`androidTest`) | 1 file | No | Smoke only |
| UI/screenshot | 1 orphan PNG, no framework | — | Not a test |
| Coverage (JaCoCo/Kover) | **absent** | — | No number exists |
| CI | **absent** | — | Nothing runs automatically |

## 3. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R11-F1 | Critical | The app module does not compile (F-001), so none of the 355 Android tests can execute; the repository still records `BUILD SUCCESSFUL` 39 times in `AGENT_STATE.md` and 42 times in `CHANGELOG.md`. | The headline test count is unexecutable. Test evidence in the repo is not trustworthy. |
| R11-F2 | High | No rules tests despite rules being the only server-side control; three rules defects (R07-F1/F3/F5) would have been caught by one test each. | Highest-risk surface is untested. |
| R11-F3 | High | No migration tests for 16 migrations / DB v27. | Data-loss regressions ship silently. |
| R11-F4 | High | No coverage configuration and no coverage number published; historical "success rate" claims have no artifact behind them. | Cannot reason about regression risk. |
| R11-F5 | High | No CI: tests are run manually, and the Node build is red, so even a green test run would not gate a merge (R14-F1). | Quality is voluntary. |
| R11-F6 | Medium | No device/instrumentation coverage of the critical journeys (phone signup, SOS, escrow, PIN). The one `androidTest` file is not those. | The defects in R02/R06/R10 are exactly in the untested paths. |
| R11-F7 | Medium | 18 Robolectric classes pinned to SDK 36 without a matrix; no API-level coverage for the min SDK. | Compatibility claims unsupported. |
| R11-F8 | Medium | `_device_test/` contains ad-hoc logs and an installed APK rather than reproducible test output. | "Device tested" claims cannot be re-derived. |
| R11-F9 | Info | Security-decision unit tests (phone-auth refusal, rating authorization, cashout boundary, restore-identity fail-closed) are well-targeted and passing where runnable. | Positive; keep these. |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Suite builds and runs reproducibly | FAIL |
| Rules tests | FAIL |
| Migration tests | FAIL |
| Coverage measured | FAIL |
| CI gate | FAIL |
| Critical-path E2E | FAIL |
| Meaningful security tests | PASS (where runnable) |

## 5. Passing conditions

1. `./gradlew test` green in CI from a clean checkout, with the build fixed first.
2. Firestore/Storage rules tests with emulator in CI; a regression test per rules finding.
3. Migration tests for every version bump; schema JSON committed.
4. Coverage reported (JaCoCo) with a floor on changed code.
5. Instrumented E2E for signup → post job → quote → collect → complete → release → rate, and SOS.
6. Historical test claims corrected in `AGENT_STATE.md`/`CHANGELOG.md` (R15).

## 6. Residual risk

The project has a respectable amount of unit testing but zero executable verification of the shipped
APK or the security rules. The gap is exactly where all critical findings live.

## Recommended remediation (not implemented)

1. Fix the build, then make `./gradlew test` a required CI check.
2. Add emulator rules tests, migration tests, and an instrumented critical-path E2E.
3. Publish coverage and enforce a changed-code floor.
4. Correct historical test/build claims in the master documents.

## 7. Cross-references

F-001, F-021; R07-F11, R04-F1, R14-F1, R02, R06, R10; G7.
