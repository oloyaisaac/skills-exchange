# REVIEWER_14 — DevOps, Release Engineering & Supply Chain: DONE

I finished the work for this reviewer.

- Review: `review_results/prompt2/REVIEW_14.md` (verdict FAIL, 7 findings, written against `aa4a0bc`).
  Standard: `reviewers/DEVOPS_REVIEWER.md`.
- Branch: `arena/29125468-skills-exchange`, based on `main` @ `6f659d7`.
- Two findings were largely closed by earlier rounds and are recorded as such; five were live. Running
  lint for the first time found a defect that had nothing to do with the review's wording.

## Findings

| ID | Severity | Outcome against this code |
|---|---|---|
| R14-P2-01 | HIGH | **Fixed.** The 64 MB of APKs went in R11, but **five files were still tracked**: `_device_test/ondevice_db.sqlite` (a real Room database with 8 `service_providers` rows), two `hs_err_pid*.log` and two JFR `replay_pid*.log` — and `.gitignore` carried **no rule** for any of them, so nothing would have stopped the next batch. All five are deleted, the ignore rules are in place, and `scripts/check_repo_hygiene.mjs` fails on a forbidden tracked path **and** on a missing ignore rule, with a self-test proving both. `app/google-services.json` is deliberately kept: it is the client config the plugin reads at build time and holds no secret — the guard names it explicitly so a later round does not delete it on pattern-matching principle. |
| R14-P2-02 | HIGH | **Fixed.** Android compile, unit tests, coverage and a release build already ran (R01, R13); **lint ran nowhere**, which was the live half of the finding. `./gradlew :app:lintDebug` now runs on every PR and `scripts/check_lint_report.mjs` gates `Error`/`Fatal`, counts warnings, and fails on a stale allowlist entry. Its first real run reported **56 errors**, and 54 of them were real: the UI formats times with `java.time` (API 26+) while `minSdk` is 24, which is a `NoClassDefFoundError` on Android 7.0/7.1. Fixed with core library desugaring rather than by dropping those devices. The 55th was `CALL_PHONE` implying telephony hardware (now an optional feature); the 56th is `MissingTranslation`, allowlisted with a written reason because the partial Swahili locale is a deliberate R12 state. |
| R14-P2-03 | HIGH | **Mostly stale.** `predeploy` (R03), `firestore.indexes.json` (4 composite indexes) and `.firebaserc` all exist. What was missing: an emulator block (added to `firebase.json`) and **any written deploy or rollback path** — `docs/ops/DEPLOY_AND_ROLLBACK.md` now documents the one-command deploy, the ordering, promotion, and per-layer rollback with its caveats. **There is no staging project**, and the document says so instead of adding an alias that would fail confusingly; it lists the commands to create one. |
| R14-P2-04 | MEDIUM | **Fixed.** `engines.node` is 22, matching CI — that part was stale. The SDKs were behind: `firebase-admin ^11` and `firebase-functions ^4` are now 12 and 6. v6 re-typed the package root as the v2 API, so `src/index.ts` imports `firebase-functions/v1` explicitly; every function here is gen-1, the deployed shape is unchanged, and all 318 backend tests pass. `rules-test` moved to firebase 11 + rules-unit-testing 4 with an `@grpc/grpc-js` override. Advisories: functions **1 critical / 10 high → 0 / 0**; rules-test **6 high → 0**. |
| R14-P2-05 | MEDIUM | **Fixed.** R8 and shrinking were already on (stale). `version.properties` now owns `VERSION_CODE`/`VERSION_NAME` with validated formats and per-build environment overrides, so the version is not a literal buried in Gradle. A missing signing key used to surface as an AGP `validateSigningRelease` error after work had begun; the signing config is now applied only when the key exists and a release build graph without one fails immediately with a message naming `KEYSTORE_PATH`, `STORE_PASSWORD` and `KEY_PASSWORD`. |
| R14-P2-06 | MEDIUM | **Fixed.** `npm audit --audit-level=high` gates CI in two jobs. `scripts/report_dependencies.mjs` writes a CycloneDX 1.5 SBOM plus a licence inventory from the **installed** tree (234 production components: MIT 176, Apache-2.0 31, BSD-3-Clause 12, ISC 11, BSD-2-Clause 1, 0BSD 1) and refuses copyleft or unknown licences. Two details are implemented rather than papered over: an `OR` expression is a *choice*, so `(BSD-3-Clause OR GPL-2.0)` on `node-forge` ships as BSD; and `limiter@1.1.5`, which records no licence, resolves from its `LICENSE.txt` and the report says so. |
| R14-P2-07 | LOW | **Partly fixed.** `docs/ops/DEPLOY_AND_ROLLBACK.md` writes the deploy and rollback procedure, including what rollback cannot undo (a removed index, a payout that already left the provider) and a five-step drill. **The drill has never been run** — no project, no credentials, no Storage emulator here — and §5 says exactly that. |

## Evidence

- CI on `424ce6b`: **8 checks green** — Backend build and tests, Backend handler tests (emulator),
  Firestore rules (emulator), Supply chain, Documentation checks, Secret scan, Compile and unit tests
  (494 tests **and** the lint gate), Release build with R8.
- Lint: first run 120 issues (56 errors) → after the fixes, errors are 0 outside the single allowlisted
  rule; the summary and XML are published with any failing run.
- Dependencies: `npm audit` 0 high / 0 critical in both trees; SBOM uploaded as `dependency-report`.
- Guards: 16 static checks, 7 with `--selftest`. Documents 32, requirements 46 (`REQ-REL-01`…`05`).
- `firebase-functions` 4 → 6 is not cosmetic: the compiled tests, the handler-package and the release
  emulator run all still pass against the new major.

## Not claimed

- **No deployment of any kind.** Nothing has been deployed from this repository; `DEPLOY_AND_ROLLBACK.md`
  is a procedure, and its "Not claimed" section says so.
- **No staging project**, so promotion is a single environment. The commands to create one are written,
  not run.
- **The rollback drill has not been executed.** It is a plan, not a control.
- No Play upload, no App Bundle, no staged rollout, no `versionCode` monotonicity against a live track.
- The CI release APK is signed with a key generated and discarded inside the job.
- 28 moderate advisories remain in the backend tree, recorded rather than resolved.
- No SBOM has been consumed by a downstream scanner.
- Lint has never run on this machine; its evidence is the CI job.
