# REVIEWER_11 — QA & Test Engineering: DONE

I finished the work for this reviewer.

- Review: `review_results/prompt2/REVIEW_11.md` (verdict FAIL, 7 findings). Standard:
  `reviewers/QA_REVIEWER.md`.
- Branch: `arena/f11c33ea-skills-exchange`, based on `main` @ `7709762`.
- The review was written against an earlier commit; each finding was re-checked against the current
  code before anything was changed. **Four of the seven were already closed** by earlier rounds and are
  recorded as stale rather than re-implemented.

## Findings

| ID | Severity | Outcome against this code |
|---|---|---|
| R11-P2-01 | CRITICAL | **Stale — closed by R07.** The review says there is no automated test of `firestore.rules`. `rules-test/firestore.rules.test.mjs` holds **26** emulator tests (reports, ratings, guarantor intake, SOS, wallet, jobs, deletion) and `.github/workflows/ci.yml` job `Firestore rules tests` runs them on every push with `# fail 0` asserted, plus a static Storage-rules check. Nothing re-implemented. |
| R11-P2-02 | CRITICAL | **Stale — closed by R02.** The review says the Compose screens have no tests. Seven Compose tests exist in three files — `OnboardingComposeTest` (2, one of them the rotation test through `StateRestorationTester`), `EscrowGatingComposeTest` (2) and `CriticalFlowComposeTest` (3: posting disabled until valid, quote comparison, pipeline) — all executed by the Android CI job. Nothing re-implemented. |
| R11-P2-03 | HIGH | **Stale — closed by R01/R09.** The review says the Android job is absent so no test can run. Job `Compile and unit tests` in `.github/workflows/android.yml` has run on every push since R01 and executes **478** unit tests; R09 watched it reject a wrong expectation, which is the "a deliberate regression fails" criterion. The stale `ci.yml` header that claimed the job was "intentionally absent" is corrected here, since a comment that says the opposite of the workflow is how this finding keeps being raised. |
| R11-P2-04 | HIGH | **Fixed.** `functions/package.json` ran a hand-listed set of test files, and `handlers.emulator.test.mjs` was matched by Node's own discovery while being excluded from `npm test` — two ways for a suite to exist and never run. `test` is now `node --test test/*.test.mjs`, so discovery is the rule. New `scripts/check_test_registration.mjs` fails the build if a file in `functions/test/` is run by no script, if `test:coverage` and `test` cover different files, or if CI stops invoking any of the three commands; `--selftest` injects an unregistered suite and proves the guard refuses it. Wired into the documentation job. |
| R11-P2-05 | HIGH | **Fixed.** Coverage was neither measured nor gated. Backend: `npm run test:coverage` with floors of **90 lines / 80 branches / 90 functions**, against a measured **96.38 / 85.94 / 96.81** on Node 22.22.3 (`lib/**/*.js`). Client: JaCoCo 0.8.12 in `app/build.gradle.kts` — `jacocoTestReport` (XML + HTML) and `jacocoTestCoverageVerification` (LINE floor 0.20), generated code excluded — and the Android workflow now runs both, summarises the LINE total per run and uploads `android-coverage`. **Not executed locally:** there is no JDK in this sandbox; the first CI run is the evidence. |
| R11-P2-06 | HIGH | **Fixed (handler layer; the rest was already three repeats deep).** `handlers.emulator.mjs` grew from 5 to **19** tests: fileReport (acknowledgement, priority, refusal of a non-participant), the reports queue (admin-only, ordered by priority), resolveReport (forward-only, attributed), submitRating (recorded once, refuses a non-participant, refuses a second rating), getSosDispatchStatus (own incident only) and getSafetyMetrics (admin-only; a failed read is unknown, never zero). The CI step asserts `# pass 19` and `# fail 0`, so a suite that runs nothing fails. The backend suite is now repeated **three times per PR** with an identical pass count required — measured 310/310 three times locally. **Now executed:** the emulator cannot run here, so the first CI run was the test of the tests — and it failed on one assertion (`breachingSla` is a property of each queue row, not of the envelope). That is fixed and the job passes **19/19** (run 44789646720). |
| R11-P2-07 | MEDIUM | **Fixed.** The repository carried evidence that could not be evidence: `build.log`, nine `c123_verify_*.log`, `diag-*.txt`, `ui-*.xml`, `build_result*.txt`, two **32 MB APKs** (`installed-check.apk`, `_device_test/installed-base.apk`) and stray `res/`/`META-INF/` build output — **190 files, 74 MB** describing commits that no longer exist. Deleted, and `README.md` now says where evidence actually comes from (the CI run for a commit) instead of stating counts (171 backend / 355 Android) that were false against 310 / 478. `docs/qa/TEST_STRATEGY.md` is new: inventory with commands and counts, release gates, coverage floors, flake policy, critical-path matrix, and seven gaps listed by risk. |

Beyond the findings: `scripts/check_document_map.mjs` claimed to verify the document map but read only
one directory level of `docs/`, so documents in `docs/privacy/`, `docs/safety/` and `docs/qa/` could
be added without appearing in the map. It now walks `docs/` recursively — the check went from 22 to
**28** documents verified, and a stray file fails it.

## Evidence

- Backend `npm run build && npm test` in `functions/`: **310/310**, run three times with an identical
  count (the flake check that is now a gate).
- Backend coverage (`node --test --experimental-test-coverage --test-coverage-lines=90
  --test-coverage-branches=80 --test-coverage-functions=90`): passes; **96.38 % lines, 85.94 %
  branches, 96.81 % functions**. A deliberate `--test-coverage-lines=99` run fails, so the floor is
  enforced rather than decorative.
- Firestore rules: **26** emulator tests (unchanged this round; they were already there).
- Static guards: doc links, document map, traceability (**32** requirements, 2 without a test),
  README claims, architecture guards, query limits, consent version, backup policy, no-PII-logs,
  secret scan and the new test-registration guard — **all pass**, plus both self-tests.
- Android: **478** unit tests and the JaCoCo tasks are executed by CI only (no JDK here) — run
  **44789646910** is `Compile and unit tests`: success, with `Unit-test coverage (JaCoCo)`,
  `Summarise coverage` and `Upload coverage report` all green. The coverage figure for a commit is in
  the `android-coverage` artifact; this sandbox cannot download GitHub artifacts, so no number is
  quoted here rather than a number being guessed.
- Handler tests: **19/19** on the Firestore emulator (run **44789646720**). Their first execution
  failed: one assertion treated `breachingSla` as an envelope field when `getReportsQueue` puts it on
  each row. **The new gate caught a wrong test the first time it ran**, which is exactly what the
  reviewer said the programme lacked.
- CI for this branch: **7/7 checks pass** (runs 44789646720 and 44789646910).

## Not claimed

- No instrumented/device test run: `app/src/androidTest` still holds the template only. `REM-065`
  therefore stays `IN_PROGRESS`.
- The Android coverage number for this commit is in the `android-coverage` artifact, not in any
  document here — this sandbox cannot measure it, and I did not invent a figure.
- `lib/index.js` has no coverage number of its own; only the 19 handler tests touch it.
- No flake evidence for the Android or rules suites (they run once per PR; three Android runs cost
  ~11 minutes each).
- Provider contracts (MTN, Africa's Talking, Gemini, Maps) remain untested against sandboxes: no
  credentials exist in this environment. `REM-066` stays `IN_PROGRESS` for the same reason.
- Nothing is deployed; no test has ever run against a real Firebase project or handset.
