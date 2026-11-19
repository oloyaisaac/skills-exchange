# R11 — QA & Test Engineering Lead

| Field | Value |
|---|---|
| Reviewer | R11 — QA & Test Engineering (P1) |
| Assignment | `reviewers/QA_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no test or CI file changed |
| Android execution | **UNVERIFIED** — no JDK/Android SDK |
| Backend execution | **PASS** — `npm run build` exit 0; `npm test` 171/171 |
| Verdict | **FAIL** |

## 1. Test inventory

| Layer | Files | Tests | Executed here |
|---|---|---|---|
| Kotlin JVM/Robolectric (`app/src/test`) | 41 | **355** `@Test` methods | **no** (toolchain absent) |
| Instrumented (`app/src/androidTest`) | 1 (`ExampleInstrumentedTest`) | template | no |
| Cloud Functions (`functions/test/*.mjs`) | 18 | **171** | yes, 171 pass / 0 fail |
| Firebase Rules emulator | **0** | 0 | no |
| Compose UI tests | **0** (only Robolectric screenshot helpers) | — | no |
| Migration tests | 1 partial (18→19 shape) | — | no |
| Coverage (JaCoCo) | none configured | — | no |

`@Ignore`/skipped tests: 0 — nothing is hidden as skipped, but nothing measures flakiness either
(no repeated-run evidence).

## 2. Executable evidence (this environment)

```
$ cd functions && npm run build      -> exit 0
$ npm test                          -> # tests 171  # pass 171  # fail 0
$ node scripts/check_traceability.mjs -> 24 requirements, 4 untested
```

`functions/package.json` enumerates test files explicitly instead of a glob. A newly added
`*.test.mjs` is silently omitted unless the list is edited; three test files were added in this
programme and the count only rose because the list was edited each time. This is a false-confidence
risk for future work.

## 3. Critical-path coverage

| Path | Backend tests | Android tests | Rules tests |
|---|---|---|---|
| Collection / confirmation / payout core | yes (pure) | none | no |
| Escrow release/refund policy | yes (pure, new) | none | no |
| Pricing | yes | none | no |
| Doorstep PIN bypass | **none** | authorization only, not the client-trusted comparison | no |
| Account deletion across stores | none | none | no |
| SOS dispatch | SMS planning tests | authorization tests only | no |
| KYC/guarantor duplicate fraud | none | shape tests | no |
| Room migrations | n/a | 1 of 16 | n/a |
| Callables (handlers) | none | none | n/a |

R11's requirement "every critical rule in `firestore.rules` has an emulator test" is unmet: no
`@firebase/rules-unit-testing` dependency or rules test exists anywhere.

## 4. CI and release gates

`.github/workflows/ci.yml` runs:

- backend `npm ci`, `npm run build`, `npm test` on Node 20;
- documentation checkers and the pattern secret scan.

Missing gates: Android compile/unit tests (explicitly deferred — the workflow comment says the job is
intentionally absent until compilation is restored), rules emulator, coverage thresholds, dependency
audit (`npm audit` / Gradle), flake detection, instrumented/device smoke. The CI does not run on the
programme's review branches? It runs on `pull_request`, so PRs are covered.

## 5. Claim integrity

- `README.md:137` still says "111 tests"; the suite is 171.
- Five historical `build_result*.txt`/`c123_verify_*.log` artifacts remain in the repository root and
  are not evidence of the current HEAD; they are not referenced by CI.
- `docs/traceability.md` reports 24 requirement IDs with 4 lacking automated tests — consistent with
  this review, and the four map to UI/privacy paths.

## 6. Determinism and isolation

- Backend tests are pure Node (`node:test`) with injected fakes; no network, no Firebase, no clock
  dependence beyond fixed epoch values. Deterministic.
- Android suites use Robolectric with an SDK-36 config; `JobAuthorshipF4SecurityTest` uses an in-memory
  Room database and distinct phone numbers (a previous unique-index trap is documented).
- No test asserts real provider calls (by design) — meaning provider contracts are untested.
- Security tests exercise repository internals via `*Internal` seams; they would not catch a UI path
  that bypasses the seam (R02's escrow sheet is exactly such a bypass and has no test).

## 7. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R11-P2-01 | HIGH | No rules emulator tests for any collection | no rules-test dependency/tests |
| R11-P2-02 | HIGH | No Compose UI tests for onboarding, posting, quoting, escrow gating | `androidTest` has only the template |
| R11-P2-03 | HIGH | Android suite never executed in CI; 355 tests unverified | `ci.yml` comment |
| R11-P2-04 | MEDIUM | Test script is an explicit file list — new suites silently excluded | `functions/package.json` |
| R11-P2-05 | MEDIUM | No coverage measurement or thresholds | no JaCoCo config |
| R11-P2-06 | MEDIUM | No handler-level tests for callables/webhook; only pure cores | `functions/test/*` |
| R11-P2-07 | LOW | Historical build logs and the "111 tests" claim remain as stale evidence | repo root; `README.md:137` |

## 8. Verdict

**FAIL.** The backend suite is real, deterministic and green (171 tests), and the Android suite is
substantial (355 tests) but cannot be executed here or in CI. Rules, Compose UI, migration coverage
and callable-handler tests are absent, and there is no coverage gate. All Android claims are
**UNVERIFIED**.
