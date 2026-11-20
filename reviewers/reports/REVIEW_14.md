# REVIEW 14 — DevOps, Release Engineering & Supply Chain

- **Reviewer:** R14 (PRD `RVW-014`, P0)
- **Date:** 2026-11-19
- **Commit:** `6b39a10` (baseline) — app source unchanged at `58f12ca`
- **Verdict: FAIL**
- **Gate:** G1 Reproducibility — **FAIL**
- **Environment:** Linux sandbox; Node `v22.22.3`, npm `10.9.8`; no JDK, no Android SDK. Backend dependencies were installed with `npm ci` for this review (ignored by Git); `functions/node_modules` and `functions/lib` are not tracked.
- **Instruction file:** `reviewers/DEVOPS_REVIEWER.md`

---

## 1. Scope reviewed

- Gradle/toolchain configuration and build portability.
- Release signing, minification, versioning and Play readiness.
- CI/CD presence and quality gates.
- Firebase deployment configuration (rules, indexes, functions, hosting).
- Backend build/test/deploy pipeline and dependency health.
- Repository artifact hygiene and supply-chain provenance.

Out of scope (owned elsewhere): source syntax (R01), app runtime (R02/R13), rules semantics (R07), money logic (R08).

## 2. Evidence executed

### 2.1 Android toolchain
```text
$ java -version
bash: java: command not found

$ ls -l gradlew
-rw-r--r-- 1 user user 8762 ... gradlew          ← not executable
$ git ls-files -s gradlew
100644 f5feea6d… 0  gradlew                      ← Git mode lacks +x

$ ./gradlew --version
bash: ./gradlew: Permission denied
$ bash gradlew --version
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

$ echo $ANDROID_HOME $ANDROID_SDK_ROOT $JAVA_HOME
(empty) (empty) (empty)
```

`gradle.properties` (verbatim, with line numbers):
```text
31  systemProp.org.gradle.user.home=D:/gradle-home
32  systemProp.robolectric.dependency.repo.dir=D:/robolectric-home
33  systemProp.java.io.tmpdir=D:/gradle-temp
38  org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10
42  org.gradle.java.installations.paths=D:/skills exchange/jdk11/jdk-11.0.22+7
```

`app/build.gradle.kts`:
```text
14  compileSdk = 37
18  minSdk = 24
19  targetSdk = 36
20  versionCode = 1
21  versionName = "1.0"
33  val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
35  storePassword = System.getenv("STORE_PASSWORD")
37  keyPassword = System.getenv("KEY_PASSWORD")
44  isMinifyEnabled = false
50  sourceCompatibility = JavaVersion.VERSION_11
62  languageVersion.set(JavaLanguageVersion.of(11))
68  languageVersion.set(JavaLanguageVersion.of(25))   ← test launcher
```
No `my-upload-key.jks` exists in the repository; no `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD` are present.

### 2.2 CI, lint, static analysis
```text
$ ls -a .github .circleci        → (no such directories)
$ find . -maxdepth 2 -iname 'detekt*' -o -iname 'lint*.xml' -o -iname '.editorconfig'
                                 → (none)
```
No workflow, pipeline, lint configuration, detekt/ktlint, dependency-review or branch protection evidence.

### 2.3 Backend build and deploy
```text
$ cd functions && npm ci --ignore-scripts
added … packages (audit run separately)

$ npm run build          # tsc
src/index.ts(645,13): error TS2339: Property 'method' does not exist on type 'Request'.
src/index.ts(646,11): error TS2339: Property 'status' does not exist on type 'Response<any>'.
src/index.ts(650,60): error TS2339: Property 'body' does not exist on type 'Request'.
src/index.ts(652,11): error TS2339: Property 'status' does not exist on type 'Response<any>'.
src/index.ts(660,11): error TS2339: Property 'status' does not exist on type 'Response<any>'.
src/index.ts(672,11): error TS2339: Property 'status' does not exist on type 'Response<any>'.
src/index.ts(676,9): error TS2339: Property 'status' does not exist on type 'Response<any>'.
src/index.ts(683,9): error TS2339: Property 'status' does not exist on type 'Response<any>'.

$ npx tsc; echo $?
2

$ npm test
# tests 111 / pass 111 / fail 0
```
`tsc` still emitted `lib/*.js` despite exit code 2. `firebase.json` has **no `predeploy` hook**, so `firebase deploy --only functions` would not rebuild and would ship whatever `lib/` happens to exist.

```json
{
  "firestore": { "rules": "firestore.rules" },
  "storage":   { "rules": "storage.rules" },
  "functions": { "source": "functions" }
}
```
No `firestore.indexes.json`, no hosting target, no emulator configuration, single default project (`skills-exchange-f9189`), no staging project.

`functions/package.json`:
```text
engines: { "node": "18" }
dependencies: firebase-admin ^11.8.0, firebase-functions ^4.3.1, axios ^1.4.0
scripts.lint: "tslint --project tsconfig.json"     ← tslint is not installed
```
Tests import TypeScript directly (`import … from "../src/momoConfirmation.ts"`), which requires Node ≥22.6 type stripping — incompatible with the declared Node 18 runtime.

### 2.4 Dependency audit (production dependencies)
```text
$ npm audit --omit=dev
16 vulnerabilities (1 low, 4 moderate, 10 high, 1 critical)
```
Highest severity:

| Package | Severity | Advisory |
|---|---|---|
| `protobufjs` | critical | Prototype pollution (≤7.6.2) |
| `@grpc/grpc-js` | high | Malformed request can crash a server |
| `body-parser` | high | DoS via trailing bytes after compressed body |
| `follow-redirects` | high | Cross-origin credential stripping bypass |
| `node-forge` | high | RSA PKCS#1 v1.5 signature verification issue |
| `firebase-admin` | high | Inherits `@fastify/busboy` DoS |
| `google-gax`, `@google-cloud/firestore`, `markdown-it`, `protobufjs-cli` | high | Various DoS/code-injection advisories |
| `qs`, `uuid`, `teeny-request`, `@google-cloud/storage` | moderate | DoS / bounds issues |

The only offered remediation path is `firebase-admin@14.5.1` (a major upgrade). No `.npmrc`, audit gate or Renovate/Dependabot configuration exists.

### 2.5 Artifact hygiene
```text
$ git ls-files -z | xargs -0 du -h | sort -rh | head
31M  installed-check.apk
31M  _device_test/installed-base.apk
4.5M replay_pid1412.log
3.3M replay_pid53288.log
1.2M replay_pid57720.log
1.1M app/replay_pid12884.log
896K app/src/main/res/drawable/img_before_leak.jpg
680K app/src/main/res/drawable/img_after_repair.jpg
652K diag-logcat-dump.txt

$ git ls-files | wc -l        → 419
$ du -sh .                    → 82M
$ ls diag* | wc -l            → 87
```
`_device_test/ondevice_db.sqlite` (250 KB), heap-crash logs (`hs_err_pid*.log`), IDE `.idea/**`, `META-INF/**`, root `AndroidManifest.xml`, `.bat`/`.ps1` scripts and build logs are tracked. `.gitignore` (lines 44–50) excludes only `node_modules/` and `functions/lib/`; it has no patterns for `*.apk`, `*.log`, `*.sqlite`, `*.xml` dumps or `hs_err_*`.

## 3. Findings

| ID | Sev | Finding | Evidence | Impact |
|---|---|---|---|---|
| D-01 | S1 | **No CI/CD at all.** No workflow, pipeline, lint, static analysis or dependency gate. | §2.2 | Every claimed verification is manual/unattributable; regressions reach the branch unchecked |
| D-02 | S1 | **Build is machine-locked.** Five absolute `D:/…` paths, including `org.gradle.java.home` and toolchain paths. | `gradle.properties:31–33,38,42` | Clean checkout/CI cannot configure; onboarding requires editing tracked config |
| D-03 | S1 | **Toolchain is internally inconsistent.** Compile/test targets Java 11, tests launch on Java 25, comments require JDK 17, no JDK in environment; wrapper is committed non-executable. | `app/build.gradle.kts:50,62,68`; `git ls-files -s gradlew`; §2.1 | Builds depend on one developer machine; Java 25 test launcher is not available on typical CI images |
| D-04 | S1 | **Release pipeline cannot produce a releasable artifact.** Env-only keystore with a default path that does not exist; blank passwords; minification disabled; `versionCode = 1`; no Play/upload config or changelog. | `app/build.gradle.kts:31–47` | No signed, shrink-wrapped, versioned release; ProGuard rules are dead weight |
| D-05 | S1 | **Backend TypeScript build fails with 8 errors** in the webhook handler; `tsc` exits 2 while emitting partial `lib/`. No `predeploy`, so deploy would ship stale/partial output. | §2.3 | Functions cannot be built reproducibly; deployment is unsafe even if Blaze is enabled |
| D-06 | S1 | **Production dependency tree has 16 known vulnerabilities (1 critical, 10 high).** Fix path requires a major `firebase-admin` upgrade. | §2.4 | Known-exploitable libraries in the payments/identity backend; no gate to catch it |
| D-07 | S2 | **Declared runtime cannot run the tests.** Engine Node 18 vs `.ts` imports requiring ≥22.6; `firebase-functions` v4 and `firebase-admin` v11 are outdated; `tslint` script references an uninstalled package. | `functions/package.json`, `tsconfig.json` | CI on the declared engine fails; lint is a no-op |
| D-08 | S2 | **Repository ships 62 MB of APKs plus device data, logs and IDE metadata**, and `.gitignore` does not prevent recurrence. | §2.5 | Slow clones, leaked device data, unprofessional release surface, secret/PII exposure risk |
| D-09 | S2 | **No environment separation or rollback plan.** Single Firebase project; no staging, indexes file, hosting target or documented rollback. | `firebase.json`, `.firebaserc`, `AGENTS.md` deployment notes | Changes go straight to the only environment; no safe promotion or recovery |
| D-10 | S3 | **No supply-chain provenance.** No SBOM, dependency locking for Gradle (no verification metadata), artifact signing, retention policy or reproducible-build flags. | build files, absence of SBOM | Cannot answer "what is in the release" or reproduce a build byte-for-byte |
| D-11 | S3 | **Version drift between toolchain layers**: AGP 9.3.2 / Gradle 9.5.0 / Kotlin 2.2.10 / compileSdk 37 / targetSdk 36 while Compose BOM is 2024.09.00 and Java target is 11. | `libs.versions.toml`, wrapper, build file | Unsupported combinations risk compiler/resource failures; no compatibility matrix documented |
| D-12 | S4 | **Historical build artifacts are treated as evidence.** Hundreds of KB of `build*.log`, `c123_verify_*.log`, `fresh_build.txt` are committed with no commit attribution. | repository root | Misleads reviewers and agents into believing the current HEAD builds |

## 4. Requirements assessment

### Functional
- A clean clone cannot build the Android app (D-02, D-03) and cannot build the functions package cleanly (D-05).
- There is no automated path from commit to signed artifact or deployed backend.
- Release artifact is neither minified nor versioned meaningfully.

### Security
- Signing secrets are expected from the environment (good) but there is no key management, rotation or CI secret policy.
- Known critical/high vulnerabilities sit in production backend dependencies with no gate.
- Nightly artifacts (APKs, SQLite, logs) are committed; the database and logs may contain PII.
- `gradlew` lacking the executable bit is itself a tamper/quality signal, not just an inconvenience.

### Testing
- No CI execution of the 41 JVM test files or 111 Node tests.
- Backend `npm test` passes locally (111/111) but is not wired to any gate, and it does not exercise `index.ts` — which does not compile.
- No emulator/rules tests in the pipeline.

## 5. Coverage gaps / UNVERIFIED

| Item | Status |
|---|---|
| `./gradlew assembleDebug` / `testDebugUnitTest` | **UNVERIFIED** — no JDK/SDK; wrapper not executable |
| Actual Gradle dependency vulnerabilities | **UNVERIFIED** — no resolution possible offline |
| Firebase deployment dry-run | **UNVERIFIED** — no credentials/Blaze plan |
| Play Console state, signing key custody | **UNVERIFIED** — no access |
| Whether any CI exists outside the repository (external system) | **UNVERIFIED** — no evidence in-tree; treated as absent |

## 6. Passing conditions outcome

| Condition | Outcome |
|---|---|
| Clean Linux clone builds and tests | **NOT MET** — D-02, D-03; execution unavailable |
| Signed + minified release produced by CI | **NOT MET** — D-01, D-04 |
| Automated tests and security scans gate merges | **NOT MET** — D-01, D-06 |
| One-command deploy with staging verification | **NOT MET** — D-05, D-09 |
| No committed artifacts, no known critical dependency risk | **NOT MET** — D-06, D-08 |

**Verdict: FAIL. Gate G1 is closed.**

## 7. Recommended remediation (not implemented)

1. **Make the build portable:** remove all `D:/` properties; pin JDK via CI image/toolchain; commit `gradlew` as `100755`; choose one Java version (17 recommended) for compile, tests and toolchain.
2. **Add CI** (GitHub Actions): cache Gradle, run `assembleDebug`, `testDebugUnitTest`, `npm ci && npm run build && npm test`, secret scan, dependency audit. Make it required.
3. **Fix the functions build:** resolve the `Request`/`Response` typing errors (align `firebase-functions`/`@types/express` or type the handler), add `predeploy: npm run build`, and fail deploy on `tsc` errors.
4. **Upgrade backend dependencies** (`firebase-admin` major, `firebase-functions` v5+, axios) and add `npm audit --omit=dev` as a gate; document exceptions with expiry.
5. **Release engineering:** create a keystore from CI secrets, enable R8 with the existing rules, introduce semantic `versionCode`/`versionName`, generate a signed AAB, and document Play tracks/rollback.
6. **Purge artifacts** from Git (APKs, `.sqlite`, logs, `hs_err`, `diag-*`, replay logs, IDE files); extend `.gitignore`; optionally move diagnostics to an external store.
7. **Environments and IaC:** add `firestore.indexes.json`, staging project, deploy scripts, rollback runbook, and a smoke test after deploy.
8. **Provenance:** generate an SBOM, pin Gradle dependency verification, retain artifacts with commit SHA.

## 8. Residual risk and owner

- Risk: deploying functions from a developer machine could ship a partially emitted `lib/` (tsc emits despite errors) from a commit whose source does not compile. Owner: R14/R03.
- Risk: committed APK and SQLite may contain real device/PII data. Owner: R09/R14; recommend history review and, if confirmed, history rewrite only with authorisation.
- Risk: unknown Gradle dependency CVEs until a networked build plus scanner runs. Owner: R14.
- Residual: release/signing state cannot be verified without Play/Firebase access.

## 9. Cross-references

- `ASSESSMENT_REPORT.md` F-009, F-014, F-017.
- `PRODUCTION_READINESS.md` §3.2, gate G1.
- R01 `reviewers/reports/REVIEW_01.md` — app source cannot compile; Gradle execution unavailable.
- R03 — backend handler build failure and runtime config.
- R11 — tests exist but no CI execution; Node test results reproducible locally only.
- R07/R09 — committed artifacts may contain secrets/PII.
