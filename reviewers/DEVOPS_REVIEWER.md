# R14 — DevOps, Release Engineering & Supply Chain Reviewer

## 1. Reviewer Number
R14 (PRD story `RVW-014`, Priority P0). Runs alongside R01 as the second gate; most other reviews are unreliable until builds are reproducible.

## 2. Professional Title
Principal Release Engineer / DevSecOps Architect, Android + Firebase delivery pipelines and software supply chain.

## 3. Required Expertise
- Gradle (Kotlin DSL), AGP/Kotlin/SDK compatibility, toolchains, configuration cache, reproducible builds.
- Android signing (upload/app-signing keys), Play Console release tracks, versioning and staged rollout.
- Firebase deployment (rules, indexes, functions, hosting), environments and promotion.
- CI/CD (GitHub Actions), secret management, artifact retention, SBOM and dependency pinning.
- Repository hygiene and build-artifact policy.

## 4. Responsibilities
1. Verify a clean checkout builds on a standard Linux CI image with no local machine paths.
2. Audit toolchain/version coherence (AGP, Gradle, Kotlin, JDK, compileSdk/targetSdk, Compose BOM).
3. Audit release configuration: signing, minification, versioning, debuggable flags, manifest placeholders.
4. Audit deployment configuration for Firestore rules/indexes, Storage rules and Cloud Functions.
5. Verify CI/CD exists, enforces tests/security scans, and produces signed artifacts.
6. Audit dependency freshness/CVEs, lockfiles and SBOM.
7. Audit repository hygiene: committed binaries, logs, databases and machine-specific config.

## 5. Exact Features to Examine
- Build and test pipeline for `:app` and `functions`.
- Release signing and Play upload readiness.
- Firebase resource deployment and environment separation.
- Secrets/configuration injection (`.env`, Gradle Secrets plugin, `google-services.json`).
- Committed artifacts and diagnostics.
- Versioning (`versionCode = 1`, `versionName = "1.0"`).

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `gradle.properties` | hardcoded `D:/` Gradle home, JDK home, temp and Robolectric dirs |
| `app/build.gradle.kts` | Java 11 compile, Java 25 test launcher, `isMinifyEnabled = false`, env-based release signing |
| `build.gradle.kts`, `settings.gradle.kts` | plugin topology, Foojay toolchain resolver |
| `gradle/libs.versions.toml` | AGP 9.3.2, Kotlin 2.2.10, compileSdk 37, Compose BOM 2024.09.00, Firebase BOM 34.15.0 |
| `gradle/wrapper/gradle-wrapper.properties` | Gradle 9.5.0 distribution |
| `app/proguard-rules.pro` | rules exist but R8 disabled |
| `app/google-services.json` | committed Firebase config |
| `functions/package.json`, `functions/package-lock.json` | Node 18 engine, `firebase-functions ^4.3.1`, `firebase-admin ^11.8.0`, broken `tslint` |
| `firebase.json`, `.firebaserc` | no hosting, no predeploy, no indexes file |
| `.gitignore` | does not exclude APKs, logs, XML dumps, `.sqlite` |
| `_device_test/installed-base.apk` (32 MB), `installed-check.apk` (31 MB), `_device_test/ondevice_db.sqlite` | committed artifacts |
| `build*.log`, `build*.err`, `diag-*`, `hs_err_pid*`, `replay_pid*` | 87+ diagnostic files at repository root |
| absence of `.github/` | no CI configuration |

## 7. Functional Requirements
- `./gradlew testDebugUnitTest` and `assembleRelease` succeed from a clean clone on CI.
- Release artifact is signed, minified, versioned and traceable to a commit.
- Firebase rules/indexes/functions deploy from the repository with one documented command.
- Secrets are supplied by CI secret storage, never committed.
- Builds are cached but not dependent on machine-local absolute paths.

## 8. Security Requirements
- No signing keys or keystores in the repository; key rotation procedure documented.
- No credentials in committed files; secret scanning enforced in CI.
- Dependency vulnerability scan gates merges; no known critical CVEs.
- Release builds are `debuggable=false`, minified, and not signed with debug keys.
- Artifact provenance: SBOM generated and retained.

## 9. Testing Requirements
- CI matrix: unit tests, lint/detekt, functions tests, rules emulator tests, security scans.
- Clean-cache and clean-clone build verification.
- Deployment dry-run in a staging Firebase project.
- Restore/rollback drill for rules and functions.

## 10. Expected Evidence
- CI configuration (file) and a green run URL or transcript.
- Clean-clone build transcript on Linux with no `D:/` references.
- Signing configuration evidence (no secrets revealed) and Play readiness checklist.
- Deployment runbook and staging verification.
- Dependency/SBOM report and artifact inventory.

## 11. Passing Conditions
- PASS requires: clean Linux CI build, signed/minified release, automated tests and scans, one-command deployment with staging verification, no committed artifacts, and no known critical dependency risk.
- FAIL now: `gradle.properties` hardcodes Windows paths and JDK locations, there is no CI, R8 is disabled, release signing depends on missing local files, and the repository ships 60+ MB of APKs plus logs and a SQLite database.
