# Release engineering and supply chain

**Owner:** Engineering lead · **Reviewer round:** R14 · **Findings:** `review_results/prompt2/REVIEW_14.md`

The review's verdict was FAIL on four counts: the repository shipped artefacts, the Android build never
ran in CI, the release was unhardened, and the dependency set was neither audited nor inventoried. Two
of those were already fixed by earlier rounds; this document records what each finding looks like
against the current tree, and what is still open.

## 1. Findings against the tree at `6f659d7`

| ID | Severity | What the review said | What the tree actually did | Outcome |
|---|---|---|---|---|
| R14-P2-01 | HIGH | 64 MB of APKs, a device SQLite database and crash logs are tracked | R11 deleted the APKs and most logs, but **five files remained**: `_device_test/ondevice_db.sqlite` (8 provider rows), two `hs_err_pid*.log`, two JFR `replay_pid*.log`. `.gitignore` had **no rule** for any of them, so nothing stopped the next batch | **Fixed** — files deleted, ignore rules added, `check_repo_hygiene.mjs` gates both |
| R14-P2-02 | HIGH | No Android or release job in CI; lint never runs | Android compile, unit tests, coverage and a release build all run (R01, R13). **Lint ran nowhere** — that part was live | **Fixed** — `:app:lintDebug` runs on every PR and errors gate through `check_lint_report.mjs` |
| R14-P2-03 | HIGH | No `predeploy`, no indexes file, no aliases | `predeploy` exists (R03), `firestore.indexes.json` exists with four composite indexes, `.firebaserc` names the default project. What was missing: an emulator block and any documented deploy/rollback path | **Mostly stale** — emulator config added; the deploy and rollback procedure is now written down (absent staging project, stated) |
| R14-P2-04 | MEDIUM | Node 18 engine against Node 20 CI; SDK majors behind | `engines.node` is `22`, matching CI. The SDKs **were** behind: `firebase-admin ^11` and `firebase-functions ^4` | **Fixed** — admin 12, functions 6 (via the v1 entry point), rules-test on firebase 11; high/critical advisories went from 11 to 0 |
| R14-P2-05 | MEDIUM | Release unminified, version `1.0`/1, signing fallback implicit | R8 and shrinking were on (R07). The version was a placeholder and a missing key surfaced as an AGP `validateSigningRelease` error | **Fixed** — `version.properties` owns both values with validated formats, and a release build without a key fails immediately with a sentence that says what to do |
| R14-P2-06 | MEDIUM | No dependency audit, SBOM or licence inventory | True | **Fixed** — `npm audit --audit-level=high` gates CI, `report_dependencies.mjs` writes a CycloneDX SBOM and refuses copyleft or unlicensed production dependencies |
| R14-P2-07 | LOW | No runbooks; no rollback drill | True | **Partly fixed** — `docs/ops/DEPLOY_AND_ROLLBACK.md` documents deploy, promotion and rollback. The drill itself has never been run, and says so |

## 2. Repository hygiene (P2-01)

`scripts/check_repo_hygiene.mjs` refuses two things, because either alone is survivable and both
together are not: a tracked path that is an artefact, device capture, signing key or secret, and a
`.gitignore` that has lost the rules that keep them out.

Forbidden tracked paths: `*.apk`, `*.aab`, `*.sqlite(3)`, `hs_err_pid*.log`, `replay_pid*.log`,
`*.hprof`, `*.jks`, `*.keystore`, `.env`. The self-test proves every classification, and that a missing
ignore rule is caught.

**`app/google-services.json` stays tracked, deliberately.** It is the client configuration the Google
Services plugin reads at build time, it ships inside every APK anyway, and it contains project
identifiers, not secrets — an API key restricted to an Android package and signing fingerprint. It is
listed by name in the guard's self-test so a future round does not delete it by pattern-matching
principle. Tracked files went 452 → 447 in this round (64 → 0 artefacts).

## 3. Dependency set and advisories (P2-04, P2-06)

| Package | Before | After |
|---|---|---|
| `firebase-admin` | `^11.8.0` | `^12.7.0` |
| `firebase-functions` | `^4.3.1` | `^6.0.0`, imported as `firebase-functions/v1` |
| `rules-test` `firebase` | `^10.14.1` | `^11.10.0` |
| `rules-test` `@firebase/rules-unit-testing` | `^3.0.4` | `^4.0.1` |
| `rules-test` `@grpc/grpc-js` | transitive 1.9.x | `^1.13.6` via `overrides` |

`firebase-functions` v6 re-typed the package root as the v2 API, so the bare import no longer carries
`runWith`, `.https.onCall` or `.firestore.document`. Every function here is gen-1, and `src/index.ts`
now imports `firebase-functions/v1` explicitly — the deployed shape is unchanged. Moving to v2 is a
per-function migration (different option objects, a different callable wire shape) and is not attempted
here.

**Advisory state, measured with `npm audit`:**

| Tree | Before | After |
|---|---|---|
| `functions` | 1 critical, 10 high, 24 moderate, 1 low | **0 critical, 0 high**, 28 moderate |
| `rules-test` | 0 critical, 6 high, 6 moderate | **0 of any severity** |

The 28 remaining moderates are in dev-only chains (the `firebase-functions-test` → jest/babel tree) and
in `google-cloud-storage` inside admin 12; each is recorded rather than dismissed, and any that becomes
high will fail the audit gate. The gate is `npm audit --audit-level=high`, run in the `functions` and
`supply-chain` jobs.

## 4. SBOM and licence inventory (P2-06)

`scripts/report_dependencies.mjs` reads the **installed** tree (`npm ls --all --long`) and writes
`dependency-report.json`: a CycloneDX 1.5 SBOM of the production components plus a licence histogram per
package group. It is uploaded as a CI artefact on every run.

The licence gate covers shipped code only (`functions`); the rules harness is reported but not gated. A
production dependency fails the build when it is copyleft (GPL/AGPL/SSPL/CC-BY-NC/EUPL) or when its
licence is unknown. Two subtleties are implemented rather than papered over:

- **Dual licences are a choice.** `(BSD-3-Clause OR GPL-2.0)` ships under BSD, so an `OR` expression is
  refused only when *every* alternative is copyleft; an `AND` expression is refused when any part is.
  `node-forge` — a transitive dependency of `firebase-admin` — is exactly this case.
- **Missing metadata falls back to the file, visibly.** `limiter@1.1.5` records no licence; its
  `LICENSE.txt` is MIT, and the report says the licence was read from the file rather than from npm.
  The fallback refuses on GPL/AGPL/SSPL text first, so an appendix mentioning GPL cannot be laundered
  into a pass.

Measured on this tree: **234 production components** — MIT 176, Apache-2.0 31, BSD-3-Clause 12,
ISC 11, BSD-2-Clause 1, 0BSD 1 — with one licence resolved from a file.

## 5. Release configuration (P2-05)

- **Version** — `version.properties` at the repository root owns `VERSION_CODE` and `VERSION_NAME`.
  `VERSION_CODE` must be a positive integer; `VERSION_NAME` must be `MAJOR.MINOR.PATCH`. Both can be
  overridden per build by the corresponding environment variable, which is how CI stamps an artefact
  without editing source. A missing file or a malformed value is a configuration-time failure.
  Current values: code 1, name 1.0.0 — this is the first release ever cut from this repository, so
  "1" is accurate rather than stale.
- **Signing** — `KEYSTORE_PATH` wins when set, otherwise `my-upload-key.jks` in the repository root.
  The signing config is only applied when the file exists, and `gradle.taskGraph.whenReady` fails any
  build graph that wants a release variant without a key, naming the path and the variables. The CI
  release job generates a throwaway key inside the job; that APK proves R8 and shrinking work and is
  **not** distributable.
- **Hardening** — R8 minification and resource shrinking have been on since R07. Measured in run
  44799912835: APK 6.3 MB, R8 mapping 679,287 lines, 599.8 KB of uncompressed resources.
- **Not present** — no Play Console upload, no App Bundle, no staged rollout, no `versionCode`
  monotonicity check against a published track. Those need a Play account.

## 6. Static analysis (P2-02)

`./gradlew :app:lintDebug` runs after the unit tests in `android.yml`, and
`scripts/check_lint_report.mjs` reads the XML report rather than trusting the build: **Fatal and Error
issues fail the build; warnings are counted and reported.** An allowlist exists at
`scripts/lint-allowlist.json` — empty today — and every entry must carry a reason, with stale entries
failing the gate so the list cannot rot.

Detekt and ktlint are not added. Lint already covers the correctness classes that matter here (`NewApi`
against minSdk 24, missing permissions, manifest and resource errors), and a second analyser with its
own baseline is a cost this project has not earned yet.

## 7. Not claimed

- No deployment, no published artefact, no Play upload: nothing here has been released.
- The release APK built in CI is signed with a key generated and discarded inside the job.
- No SBOM has been consumed by a downstream scanner; the file is produced, not validated by a third party.
- The 28 moderate advisories are recorded, not resolved.
- No rollback drill has been run — the procedure is written, not exercised (see
  `docs/ops/DEPLOY_AND_ROLLBACK.md`).
- Lint has never been run on this machine; its first real execution is the CI job in this PR.
