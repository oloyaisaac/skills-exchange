# R14 — DevOps, Release Engineering & Supply Chain Reviewer

| Field | Value |
|---|---|
| Reviewer | R14 — DevOps & Supply Chain (P0) |
| Assignment | `reviewers/DEVOPS_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no build, CI or repository hygiene change |
| Clean-clone build | **UNVERIFIED** — no JDK/Android SDK; backend build verified (exit 0, 171 tests) |
| Verdict | **FAIL** |

## 1. Toolchain coherence

| Component | Declared | Assessment |
|---|---|---|
| Gradle wrapper | 9.5.0 | wrapper committed; distribution URL standard |
| AGP / Kotlin | from version catalogue | not resolvable here; **UNVERIFIED** |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 | compileSdk 37 is ahead of stable AGP expectations; only a real build proves compatibility |
| Java | `gradle.properties` no longer hardcodes `D:/` paths | earlier machine-specific defect is fixed |
| Node (functions) | `engines.node = "18"` | Node 18 is EOL, while CI runs Node 20 — engine/CI mismatch |
| Firebase SDKs | `firebase-functions ^4.3.1`, `firebase-admin ^11.8.0` | both majors behind current; no audit/SBOM |

## 2. CI/CD

`.github/workflows/ci.yml` exists and runs on `push` to `main` and on every PR:

- backend `npm ci` → `npm run build` → `npm test` (green locally: 171/171);
- documentation checkers (links, map, traceability, README claims, query limits);
- pattern secret scan.

Missing: Android compile/unit tests (explicitly deferred), lint/detekt, rules emulator, dependency
audit (`npm audit`, Gradle), release build/signing, artifact/SBOM retention, and any deployment job
(rules/functions/indexes). There is no staging project or promotion workflow, no rollback drill, and
no documented one-command deploy.

## 3. Release configuration

| Control | State | Assessment |
|---|---|---|
| Signing | `signingConfigs.release` reads `KEYSTORE_PATH` env, else `${rootDir}/my-upload-key.jks` | no keystore committed (good); the fallback path makes a missing key a confusing build failure rather than an explicit error |
| Minify / shrink | disabled (`isMinifyEnabled = false`) | release unhardened |
| Debuggable | not enabled in release | pass |
| Versioning | versionCode 1 / versionName "1.0" | placeholder; no versioning policy |
| Manifest placeholders | `MAPS_API_KEY` default placeholder | safe default, but release would ship an invalid Maps key if unset |
| ProGuard rules | file exists | has no effect while R8 is off |

## 4. Firebase deployment

- `firebase.json` declares only `firestore.rules`, `storage.rules` and the functions source.
- **No `predeploy` build hook**, so a deploy can ship a stale `lib/` (R03).
- **No `firestore.indexes.json`**, so composite indexes are console-only and undiscoverable.
- No `.firebaserc` environment separation or aliases; no emulator configuration; no hosting.
- Deployment is therefore not reproducible from the repository.

## 5. Repository hygiene and supply chain

Tracked artifacts (from `git ls-files`):

- `_device_test/installed-base.apk` (~32 MB) and `installed-check.apk` (~32 MB);
- `_device_test/ondevice_db.sqlite` (device database, potentially containing user rows);
- `app/hs_err_pid*.log`, `app/replay_pid*.log`, `build*.log`, dozens of `diag-*` files;
- 508 tracked files in total, with the root used as a scratch directory.

`.gitignore` covers `.env`, `node_modules`, `functions/lib`, `build`, JDKs, but **not** `*.apk`,
`*.sqlite`, `*.log`, `diag-*`, `hs_err_pid*` or `replay_pid*`. The committed SQLite database and APKs
are release-integrity and privacy risks (the DB could carry real device data).

No lockfile for Gradle dependencies (normal), `functions/package-lock.json` present (good). No SBOM,
no dependency vulnerability gate, and no license inventory.

## 6. Reproducibility statement

Backend: reproducible here (`npm ci`, build, test all deterministic). Android: cannot be attempted
without a JDK/SDK; `gradle.properties` no longer contains Windows paths, but AGP 9.3.2 + compileSdk 37
+ Gradle 9.5 is an aggressive combination that must be proven on a clean Linux runner. **UNVERIFIED.**

## 7. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R14-P2-01 | HIGH | Committed 64 MB of APKs, a device SQLite DB and crash/replay logs | `git ls-files`; no ignore rules |
| R14-P2-02 | HIGH | No Android/release job in CI; compile, tests and lint never gate merges | `ci.yml` |
| R14-P2-03 | HIGH | No `predeploy`, no indexes file, no environment aliases → deploy not reproducible | `firebase.json` |
| R14-P2-04 | MEDIUM | Node 18 engine vs Node 20 CI; Firebase SDK majors behind | `functions/package.json`; `ci.yml` |
| R14-P2-05 | MEDIUM | Release unminified and versioned `1.0`/`1`; signing fallback is implicit | `app/build.gradle.kts` |
| R14-P2-06 | MEDIUM | No dependency audit, SBOM or license inventory | repo absence |
| R14-P2-07 | LOW | Build/deploy runbooks absent; no rollback drill | docs absence |

## 8. Verdict

**FAIL.** The machine-specific Gradle paths are gone and there is now a real backend/docs CI workflow,
but the repository still ships APKs and a device database, Android never builds in CI, Firebase
deployment is not reproducible from source, and the release is unhardened. Clean-clone Android
reproducibility is **UNVERIFIED**.
