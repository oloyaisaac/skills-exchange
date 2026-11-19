# R14 — DevOps, Release Engineering & Supply Chain Reviewer

| Field | Value |
|---|---|
| Reviewer | R14 — DevOps & Release Engineering (P0) |
| Assignment | `reviewers/DEVOPS_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | CI, build portability, signing, artifacts, dependency risk, deploy pipeline |
| Verdict | **FAIL** — G1 not met |
| Issues | 5 (3 HIGH, 2 MEDIUM) |

## Areas examined
CI definitions, Gradle/JDK configuration, wrapper executability, signing material, build outputs,
committed artifacts, dependency advisories, Firebase deploy configuration, staging/rollback.

## Source files inspected
`.github/` (absent), `gradle.properties`, `gradlew`, `build.gradle.kts`, `app/build.gradle.kts`,
`settings.gradle.kts`, `functions/package.json`, `functions/package-lock.json`, `firebase.json`,
`_device_test/`, root/committed APKs.

## Features examined
Debug/release build path, test execution, artifact production, dependency resolution, deploy target.

## Tests performed
- `npm ci`; `npm test` → 111/111 pass; `npm run build` → **exit 2** (8 errors).
- `npm audit --omit=dev` → **16** advisories (1 critical, 10 high, 4 moderate, 1 low).
- `bash gradlew --version` → fails (JAVA_HOME absent); wrapper not executable.
- Searched for CI definitions and `predeploy` (0).
- No release build or deploy was attempted.

## Verified working functionality
- Node dependencies install reproducibly with `npm ci`; the test suite runs and passes.
- Gradle dependency declarations are version-catalogued (`libs.versions.toml`).

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R14-01 | HIGH | No CI exists. | Repository | No `.github/` or equivalent; tests run manually | Build/test/lint on every change | Regressions merge unverified | Add CI pipeline with required checks | PR blocked on red pipeline |
| R14-02 | HIGH | Build configuration is machine-local and internally inconsistent. | Gradle | `gradle.properties:31–33,38,42` hardcode `D:/`; compile Java 11 vs test Java 25; `gradlew` mode 100644 | Portable toolchain | Builds only on one Windows box | Pin toolchain, remove absolute paths, fix wrapper | Clean Linux clone build |
| R14-03 | HIGH | Release hardening, signing and artifact hygiene are missing. | Release | `isMinifyEnabled=false`; keystore absent; 62 MB APKs + SQLite + logs committed | Signed, minified, reproducible releases | No trustworthy release artifact | Enable R8, external signing, remove artifacts | Signed AAB with provenance |
| R14-04 | MEDIUM | Dependency risk is unmanaged. | Supply chain | 16 production advisories (1 critical); Node 18 engine vs modern tooling | Clean audit + supported runtime | Known vulnerable deps in prod | Upgrade Firebase deps; pin LTS runtime | `npm audit` clean in CI |
| R14-05 | MEDIUM | Deploy pipeline has no predeploy, staging or rollback. | Firebase deploy | `firebase.json` has no `predeploy`; no environment config | Build/test gate + staging + rollback | Stale partial `lib/` can ship | Add predeploy, staging project, rollback runbook | Deploy dry run + rollback drill |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R14-02, R14-04.
- **Not implemented:** R14-01, R14-03, R14-05.
- **Partially implemented:** none.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
CI, portable toolchain, signing, staging, rollback, dependency scanning, release provenance.

## Security concerns
No secret scanning gate and a committed live key (R07) mean credential leakage can recur; vulnerable
production dependencies increase exposure.

## Performance concerns
No build-time or app-size budget; R8 disabled and artifacts large.

## Recommendations
1. Add CI that installs, builds and tests both modules.
2. Pin the toolchain and remove absolute paths.
3. Enable R8, external signing and artifact cleanup.
4. Upgrade dependencies and add secret/audit gates.
5. Add staging and rollback.

## Limitations / unable to verify
No Android build, signing key, CI service or Firebase project; release behaviour is
**unable to verify**. Node audit and build were executed.

## Cross-references
R01, R03, R07, R11, MR-005/MR-006/MR-007/MR-008/MR-009.
