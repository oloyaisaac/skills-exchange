# ASSESSMENT REPORT — Skills Exchange

**Assessment ID:** ASMT-2026-11-19
**Repository:** `oloyaisaac/skills-exchange`
**Branch:** `arena/517a4993-skills-exchange` @ `6b39a10` ("installation process")
**Assessment date:** 2026-11-19
**Assessment type:** Independent, evidence-based repository review architecture (review-only; no application source code changed)
**Prepared by:** Senior Software Architect / Technical Auditor / QA Architect

> **Verdict in one line:** **NOT PRODUCTION-READY.** The committed HEAD contains compile-blocking source defects, public read access to job/chat data, a committed third-party API credential, and fabricated payment/SMS success paths. A 15-reviewer specialist program is defined to produce the full evidence trail; none of the 15 reviews has been executed yet.

---

## 1. Scope and Method

### 1.1 What was done
1. Inspected the actual source tree, build files, security rules, Cloud Functions, tests, configuration and master documents. README claims were checked against code, not trusted.
2. Counted and sampled every layer (110 Kotlin files, 15 TypeScript/functions files, 4 rule/manifest/config surfaces).
3. Ran the only suite executable in this sandbox: `functions/npm test` → **111 tests, 111 pass, 0 fail** (Node v22.22.3).
4. Performed static structural analysis of all Kotlin files (brace/paren balance and literal escape-sequence scan) because no JDK/Android SDK is available here.
5. Mapped requirements → code → tests → evidence, and recorded contradictions rather than resolving them silently.
6. Designed the review programme (complexity class, reviewer count, per-reviewer instructions, execution order, readiness gates) without modifying product source.

### 1.2 What was NOT possible
- **No Android build or Android test execution.** `java`, `javac`, `kotlinc`, Gradle runtime and the Android SDK are absent in this environment. Every statement about compilation is static-analysis evidence, not a Gradle result, and is labelled as such.
- **No Firebase deployment, emulator, device or provider sandbox call.** All runtime/deployment statements are source/config analysis.
- **No access to production data, Play Console, Firebase console or MTN/Africa's Talking accounts.**

Consequence: **unexecuted = UNVERIFIED**, never PASS. Reviewers must reproduce commands in a provisioned environment (see `REVIEW_PLAN.md` §5).

---

## 2. Phase 1 — Repository Investigation

### 2.1 Project structure
Single-module Android app plus a Firebase backend folder:

```
skills-exchange/
├── app/                      Android application (Kotlin, Compose, Room)
│   ├── src/main/java/com/example/
│   │   ├── MainActivity.kt
│   │   ├── data/db/          18 entities, DAOs, taxonomy, converters, Room v27
│   │   ├── data/local/       DataStore session
│   │   ├── data/remote/      10 Firebase/AI/MTN/SMS/messaging services
│   │   ├── data/repository/  repository + cashout security core
│   │   ├── ui/components/    26 Compose components (~13,016 lines)
│   │   ├── ui/screens/       MainScreen, OnboardingScreen, dead ActiveJobScreen
│   │   ├── ui/theme/         colour/type/theme
│   │   ├── ui/viewmodel/     single 2,749-line ViewModel
│   │   └── util/             16 helpers
│   ├── src/test/             41 JVM/Robolectric files (~7,780 lines)
│   └── src/androidTest/      1 placeholder instrumentation test
├── functions/                TypeScript Cloud Functions (4,027 lines) + 4 test files
├── firestore.rules (340)     Firestore security rules
├── storage.rules (122)       Storage security rules
├── prd.json (112 stories)    master plan
├── requirement_file.md (825) detailed requirements
├── AGENTS.md / README_AI.md  agent operating manuals
├── AGENT_STATE.md (179 KB) / TASKS.md (57 KB) / CHANGELOG.md (188 KB) / DECISIONS.md (103 KB)
├── 87 diagnostic `diag-*` files, build logs, `_device_test/` (32 MB APK + SQLite dump)
└── no `.github/`, no CI configuration
```

**Repository hygiene:** 419 tracked files; 82 MB working tree; 35 MB `.git`. Largest tracked artifacts include two ~31 MB APKs (`installed-check.apk`, `_device_test/installed-base.apk`), a 665 KB logcat dump, heap-crash logs and `_device_test/ondevice_db.sqlite`. This is a machine-state dump, not a clean source repository.

### 2.2 Languages and frameworks
| Layer | Technology | Evidence |
|---|---|---|
| Client | Kotlin 2.2.10, Jetpack Compose (BOM 2024.09.00), Material 3 | `gradle/libs.versions.toml`, imports |
| Architecture | Single-Activity MVVM + Repository; no DI, no Navigation-Compose | `MainActivity`, `MainScreen`, `settings.gradle.kts` |
| Local DB | Room 2.7.0, SQLite, schema v27, 16 migrations, `exportSchema = false` | `data/db/AppDatabase.kt` |
| Session | DataStore Preferences | `data/local/SessionManager.kt` |
| Backend | Firebase Auth, Firestore, Storage, FCM, Cloud Functions (TS) | `app/google-services.json`, `functions/` |
| Functions | TypeScript, `firebase-functions ^4.3.1`, `firebase-admin ^11.8.0`, Node 18 engine | `functions/package.json` |
| Payments | MTN MoMo sandbox collection + disbursement; Airtel only as labels | `momoCollection.ts`, `momoDisbursement.ts` |
| AI/Maps | Gemini REST, Google Maps Android SDK | `Gemini*Service.kt`, `InteractiveMapView.kt` |
| SMS | Africa's Talking OTP client; SMS alert abstraction is a logging stub | `AfricaTalkingService.kt`, `SmsNotificationService.kt` |
| Tests | JUnit4, Robolectric 4.16.1, Roborazzi 1.59.0, Compose UI test deps, Node `node:test` | build files, `functions/package.json` |
| Build | Gradle 9.5.0, AGP 9.3.2, compileSdk 37, minSdk 24, targetSdk 36 | wrapper + `app/build.gradle.kts` |

### 2.3 Frontend
- Two real screens (`MainScreen` 1,067 lines, `OnboardingScreen` 1,247 lines) and 26 components; no navigation graph — visibility is state-driven from a single 2,749-line ViewModel.
- 178 inline `Text("…")` literals, **0 `stringResource` calls**, **1** entry in `strings.xml`, 59 `contentDescription = null` among 176 descriptions. `supportsRtl="true"` but no RTL/localisation testing.
- `ActiveJobScreen.kt` is a dead, non-compiling file (imports `JobEntity`, `skillsExchangeTheme`; malformed `<MapWithFallbackFallback` call).

### 2.4 Backend, database, APIs
- **18 Cloud Functions exports**: 16 callables, 1 HTTPS webhook (`processMoMoWebhook`) and 1 Firestore trigger (`onJobCreated`). Admin-gated via `checkAdminCaller` (`admin` custom claim).
- Money paths: `createMoMoCollection`, `getMoMoCollectionStatus`, `processMoMoWebhook`, `releaseEscrowToWorker`, `createMoMoPayout`, `createMoMoMaterialAdvance`, `getMoMoPayoutStatus`.
- Admin paths: verification queue/review/activation, guarantor submission/review, disputes, SOS.
- **No REST API, no web admin portal.** Requirements specify a web admin dashboard (`requirement_file.md` §15.3); implementation is in-app operator sheets behind an explicit UI unlock plus the server claim.
- Room is the primary client store; Firestore mirrors jobs/providers/disputes/SOS/messages. No formal sync protocol, no conflict resolution.
- `firebase.json` has no functions `predeploy` build hook, no `firestore.indexes.json`, no hosting.

### 2.5 Authentication and authorization
- Firebase phone OTP, Google ID-token sign-in via Credential Manager, and anonymous sign-in exist.
- Identity resolution is deliberately hardened in places: `resolveAuthoritativeUid`, `decideRestoreIdentity`, `resolveRestoredProfile`, owner/participant checks for quotes, ratings, SOS, cashout.
- **No admin claim provisioning path anywhere** (`setCustomUserClaims` absent). Admin callables are therefore unreachable until a claim is created out-of-band; no runbook exists.
- Sensitive operations (payout destination change, account deletion) have no re-authentication.

### 2.6 Security (summary — full analysis belongs to R07)
- `firestore.rules`: `jobs` → `allow read: if true`; nested `messages` → `allow read: if true`. The mirrored job document (`syncJobToFirestore`) carries street address, apartment/suite, landmark directions, MoMo phone number, payment channel/escrow state, owner UID and provider assignment — readable by anyone, including unauthenticated clients.
- `momo_transactions` → any authenticated user may `create`; updates admin-only.
- Storage: KYC/guarantor/certificate prefixes are owner-or-admin; avatars are public-read.
- **Hardcoded Africa's Talking API key** at `AfricaTalkingService.kt:15` (`atsk_…`), used in device-side Basic auth headers.
- `allowBackup="true"` with empty `backup_rules.xml`/`data_extraction_rules.xml`; debug artifacts and 32 MB APK committed.
- App Check dependency declared (`firebase-appcheck-recaptcha`) but never initialised; no callable rate limiting; `isMinifyEnabled = false`.
- `firestore.rules` has no emulator/unit tests; `momo_transactions` and `momo_payouts` are covered by rules but not by client tests.

### 2.7 Business logic and integrations
- Rich domain: 125-service taxonomy, worker KYC with 2 guarantors, 48-hour review SLA, training/activation gating, dual marketplace (discovery + quotes), one-time and recurring contracts, escrow, doorstep PIN, SOS, ratings, disputes, reports, wallet.
- Business logic is concentrated in `SkillsExchangeRepository` (2,979 lines) and `SkillsExchangeViewModel` (2,749 lines), heavily interleaved with Android/Firebase APIs.
- Integrations: Maps (placeholder key + fallback), Gemini (key via BuildConfig; missing key returns a **fabricated** sample transcript), Africa's Talking (device-side OTP with hardcoded key), SMS alerts (**logging stub returning "SENT"**), FCM (receive only — no server-side send anywhere in `functions/src`), MTN MoMo (sandbox-only, fail-closed).

### 2.8 File storage, payments, notifications
- **Storage:** Firebase Storage with private KYC/guarantor prefixes, 8–10 MB image caps, admin signed URLs (15 min default, 1 h max). Account deletion never removes Storage objects.
- **Payments:** server-authoritative collection/confirmation design with re-query validation, idempotent terminal states, sandbox-host enforcement. **Not wired end-to-end:** `releaseEscrowToWorker`/`createMoMoPayout` have no client callers; `MoMoPaymentService.disburseEscrowPayoutToWorker` returns a fabricated `DISBURSED` result without money movement; the worker wallet ledger is Room-only. Dispute refunds only write a `QUEUED` `momo_payouts` record and set `escrowStatus = "REFUNDED"` without any provider refund call or executor. The 10% platform fee is computed client-side (`laborTotal * 0.10`) and stored on the job; there is no server-side fee enforcement, settlement or reconciliation.
- **Notifications:** local Room notifications + FCM receive; no backend `send`/`sendMulticast` call, so push delivery of job/quote/payment events is not evidenced.

### 2.9 Testing, deployment, CI/CD, documentation
- 41 JVM test files (7,780 lines) with genuinely valuable security/authorization suites; **1 placeholder instrumented test**; 4 Node test files (111 passing). No Firestore rules tests, no Compose UI tests, no coverage reporting, no CI.
- Deployment: `firebase.json` + `.firebaserc` (project `skills-exchange-f9189`); rules reportedly deployed historically; functions blocked by the Blaze plan per `AGENT_STATE.md`/`ANALYSIS_SUMMARY.md`. No staging environment, no runbook, no rollback procedure.
- Documentation is extensive (620 KB of master docs) but drifts from code: 112/112 PRD stories marked `completed` (99/99 at assessment), README `.env` variable names do not match `loadMomoConfig` (`MTN_*`, `MOMO_ENVIRONMENT`, `MOMO_BASE_URL`), and README's manifest key instructions are superseded by the Secrets plugin.

### 2.10 Error handling and reliability
- Extensive try/catch with fail-closed returns in auth/repository layers; typed result objects for quotes, SOS, cashout.
- Gaps: no crash/ANR reporting (no Crashlytics/Analytics), no structured logging policy, no SLOs, no retry/backoff policy for Gemini/Maps/SMS, no network-state-aware retry for Firestore writes, and a large amount of swallowed exceptions (`return null`).

### 2.11 Feature inventory (implemented / partial / missing / undocumented)

**Implemented (code present; runtime unverified)**
| Area | Evidence |
|---|---|
| Unified profile + client/worker mode switch | `UserProfileEntity`, `setUserRoleMode`, role gates |
| Firebase phone OTP / Google sign-in / session restore guards | `FirebaseService`, `SkillsExchangeViewModel` companion helpers |
| 125-service taxonomy validation | `ServiceTaxonomy.kt`, `ServiceTaxonomyValidationTest` |
| Job posting, quotes, status machine with audit trail | Repository + rules state machine + `JobStatusAuditEntity` |
| Room persistence v27 with 16 migrations | `AppDatabase.kt` |
| Worker KYC + 2-guarantor submission/review | entities, sheets, `guarantorReview.ts`, callables |
| Admin verification & dispute sheets (in-app) | `AdminQueueSheet`, `AdminDisputeArbitrationSheet` |
| SOS authorization + incident store + admin callables | `Sos*SecurityTest`, `sosIncidents` rules, `getSosQueue` |
| Ratings with participant/one-per-job rules | `RatingAuthorizationSecurityTest` |
| Offline action **persistence** | `PendingActionEntity`, queue preservation tests (replay not implemented) |
| Maps fallback without key/crash | `MapsApiKeyGuard`, `MapsApiKeyFallbackTest` |
| MTN collection initiation + pull-to-verify confirmation (sandbox) | `momoCollection.ts`, `momoConfirmation.ts`, 111 Node tests |

**Partially implemented**
- Escrow release/refund: callables exist; no client wiring, no refund path.
- Worker cashout: strong local policy (`WorkerCashoutSecurityCore`) but device-local ledger only, no server settlement.
- Materials advance: TASKS records Room-only behaviour.
- Notifications: receive path only; no server push.
- Reports/moderation: Room-only, no admin surface (TASKS `DISP-004`).
- Admin operations: in-app sheets, one operator surface at a time; no web dashboard, no audit log UI.
- Account deletion: deletes Auth + some Firestore docs; `users` delete is not permitted by rules; Storage documents remain.
- Airtel Money: UI labels and enum values only; no provider integration.
- SOS dispatch: incident + authorization + admin queue exist; SMS "dispatch" is a stub.
- App Check: dependency only.
- Localisation/accessibility: English-only inline strings.

**Missing**
- CI/CD, staging/production separation, release signing, Play deployment, rollback.
- Firestore indexes file and rules test suite.
- Admin claim provisioning/bootstrap runbook.
- Refund/commission/reconciliation and provider settlement ingestion.
- Crash/ANR/perf telemetry, SLOs, structured logging.
- Data retention jobs and Storage erasure.
- Rate limiting/abuse controls on callables, webhook and OTP.
- Privacy notice/terms artifacts and DPIA.
- Baseline profiles/startup benchmarks.

**Undocumented (behaviour not described where operators would look)**
- How an `admin` claim is created/revoked.
- Required Cloud Functions environment variables (`MOMO_ENVIRONMENT`, `MOMO_BASE_URL`, `MTN_SUBSCRIPTION_KEY`, `MTN_API_USER`, `MTN_API_KEY`, `GUARANTOR_PHOTO_URL_TTL_SECONDS`, `GS_STORAGE_BUCKET`).
- That production MoMo is explicitly refused by code (sandbox-only guard).
- That SMS/SOS dispatch and escrow release are not functional.
- The device-local nature of the wallet ledger.

---

## 3. Phase 2 — Project Complexity Classification

**Classification: COMPLEX** — with **mission-critical subsystems** (payments/escrow, identity documents, emergency/SOS).

| Criterion | Evidence | Weight |
|---|---|---|
| Domain breadth | Client, worker and admin surfaces; discovery, quotes, contracts, KYC, training, escrow, wallet, disputes, SOS, ratings, chat, notifications, offline | High |
| Code volume | 27.7k lines of production Kotlin, 4.0k lines TypeScript, 7.8k lines Android tests, 340+122 rule lines | High |
| Trust boundaries | Device ↔ Firestore ↔ Cloud Functions ↔ MTN ↔ Africa's Talking ↔ Gemini/Maps; two distinct identity models; admin claims | High |
| Regulatory exposure | National ID + third-party guarantor NINs, GPS, phone, financial records across four jurisdictions | High |
| Financial criticality | Escrow, payouts, material advances, cashout ledger, disputes with monetary outcomes | **Mission-critical** |
| Safety criticality | SOS, doorstep PIN, in-person worker safety | **Mission-critical** |
| Operational maturity | No CI/CD, no observability, no staging, sandbox-only payments | Low (raises risk, not scale) |
| Scale | Hyperlocal district marketplace; modest user counts expected initially, but high per-transaction risk | Medium |

Why not "Enterprise": no multi-team module structure, no environments, no SLOs, no compliance programme. Why not "Medium": the combination of real money, identity documents, emergency features and offline dual-write is materially riskier than a typical CRUD app. The correct posture is **complex product, mission-critical subsystems, low operational maturity** — which is exactly why the review programme is weighted toward security, payments, privacy, safety and release engineering.

---

## 4. Phase 3 — Reviewer Determination

**15 specialist reviewers** are required: 8 P0 (gating) and 7 P1. Selection is driven by distinct trust boundaries and failure modes, not by a fixed template.

Rationale for the count:
- The app has **three distinct risk domains that cannot share one reviewer**: money (R08), identity documents/regulation (R09), and physical safety (R10). Each has different evidence standards.
- **Rules security (R07)** and **authentication (R06)** are separated because one is a policy-language/attack-surface review and the other is a flow/identity-lifecycle review; merging them loses coverage of session restore and admin claims.
- **Backend implementation (R03)**, **persistence (R04)** and **API contracts (R05)** are separated because the repository contains a real serverless codebase, a 27-version Room schema, and four external providers with incompatible contracts.
- **Architecture (R01)** and **release engineering (R14)** run first as gates; without them every other PASS is unreliable.
- **QA (R11)**, **performance/reliability (R13)**, **UX/a11y (R12)** and **documentation (R15)** each own a distinct, non-overlapping evidence set.
- No reviewer is assigned a domain with no repository evidence (e.g. no dedicated "mobile iOS", "data science", "Kubernetes" or "blockchain" reviewer).

Deliberately excluded (and why): separate mobile/iOS (none), cloud infrastructure beyond Firebase (none), compliance/AML specialist distinct from privacy (no AML programme exists to review), scalability/capacity as a separate role (folded into R13), integration-testing as a separate role (folded into R11 and R03). This avoids duplication while keeping full coverage.

Coverage matrix, reviewer files and assignments: see `REVIEWER_REGISTER.md` and `reviewers/`.

---

## 5. Phases 4–6 — Reviewer Instructions, Master Documentation, Reviewer Directory

> **Execution addendum (2026-11-19):** all 15 reviewer files were issued and all 15 reviews have now been executed against baseline `6b39a10`. Every review returned **FAIL**; reports are in `reviewers/reports/`. The findings below and in §6 include the results of that cycle.

Created (see §10 for the full file list):
- 15 reviewer instruction files under `reviewers/`, each with the 11 mandated sections, exact source paths, requirements, evidence and passing conditions.
- `REVIEWER_REGISTER.md` (roster, gates, coverage matrix, statuses).
- `REVIEW_PLAN.md` (execution waves, entry/exit criteria, evidence protocol, report template, remediation handoff).
- `PRODUCTION_READINESS.md` (domain gates and blockers; explicitly **not ready**).
- `prd.json` extended with phase 18 and 15 `RVW-*` stories (`not_started`); all 97 prior stories preserved unchanged.
- `requirement_file.md` appended with a review addendum (no existing content modified).
- `AGENT_STATE.md`, `TASKS.md`, `CHANGELOG.md`, `DECISIONS.md` updated additively at the top; history preserved.
- `README.md` updated with a review-architecture section and a factual status note.

---

## 6. Findings Register (pre-review + review cycle, evidence-backed)

Severity: **S1** blocker, **S2** high, **S3** medium, **S4** low. F-001…F-022 were the assessor's pre-review findings. F-023…F-037 were added by the executed review cycle (all 15 specialist reviews returned FAIL). Findings marked *(extends F-xxx)* sharpen an earlier entry rather than restating it.

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| F-001 | S1 | **Committed HEAD is not structurally buildable.** `SkillsExchangeRepository.kt:579–651` leaves a `JobPostEntity(` constructor unclosed and strands a parameter list at file scope; `FirebaseService.kt:709–716` has literal `\n` escapes and a stray brace; `GeminiAudioTranscriptionService.kt:1` begins with `]=package`; `ActiveJobScreen.kt` has unresolved types and malformed syntax. No fresh Gradle evidence exists for this commit. | Static scan of 110 Kotlin files; `nl` excerpts in `reviewers/reports/REVIEW_01.md`; `build_result*.txt` pre-date the corruption |
| F-002 | S1 | **Third-party API credential committed in client source.** `AFRICASTALKING_API_KEY = "atsk_…"` at `AfricaTalkingService.kt:15`, used in device-side HTTP Basic headers. Key must be treated as compromised regardless of revocation status. | Source line; no secret-scanning in repo; existing leak test scans only MTN markers |
| F-003 | S1 | **World-readable job and chat data.** `firestore.rules` sets `allow read: if true` for `jobs` and nested `messages`. Mirrored job docs carry street address, suite/landmark, MoMo phone, payment channel/escrow state and ownership; every message body is public. | `firestore.rules:226–228`, `294–295`; `FirebaseService.syncJobToFirestore` (lines 458–500) |
| F-004 | S2 | **Doorstep PIN is not a security control.** Default `"4829"` in the entity and SQL schema, stored plaintext in Room (included in app backups), rendered in clear text in the UI, and verified against a client-supplied `expectedPin` with no attempt limit — a modified client can bypass the check. It is not currently mirrored to Firestore. | `Entities.kt:259`; `AppDatabase.kt:203`; `SkillsExchangeRepository.kt:871–899`; `MainScreen.kt:598`; `ViewModel.kt:840`; `EscrowAndProofSheet.kt:641–686` |
| F-005 | S1 | **Emergency SMS is fabricated.** `SmsNotificationService` only logs, delays and returns `status = "SENT"`; the repository calls it during SOS. No SOS SMS is sent via Africa's Talking or any other provider. | `SmsNotificationService.kt:29–79`; `SkillsExchangeRepository.kt:2229` |
| F-006 | S2 | **Escrow cannot complete end-to-end and can misstate refunds.** `releaseEscrowToWorker`/`createMoMoPayout` have no Android callers; `MoMoPaymentService.disburseEscrowPayoutToWorker` returns `DISBURSED` without money movement; wallet ledger is Room-only; `resolveDispute` marks escrow `REFUNDED` and queues a refund record without calling the provider or any executor. | grep of `app/src/main`; `MoMoPaymentService.kt:79–96`; `WorkerCashoutSecurityCore` |
| F-007 | S1 | **Account deletion orphans the user's data.** `users/{uid}` has no `allow delete` rule, so the first statement in `deleteUserFirestoreData` throws; the remaining Firestore collections and all Storage KYC/guarantor objects are never deleted, yet Firebase Auth deletion proceeds, leaving orphaned PII with no account owner. | `FirebaseService.kt:275–319`; `firestore.rules:192–210`; `ViewModel.kt:1336–1379` |
| F-008 | S2 | **Admin access cannot be provisioned from the repository.** No `setCustomUserClaims` or bootstrap tooling; admin surfaces rely on the claim plus an in-app unlock; no audit log of admin decisions. | grep of `functions/src` and `app/src/main`; DECISIONS D-2026-10-15-2 |
| F-009 | S2 | **No CI/CD and non-portable build.** `gradle.properties` hardcodes `D:/` Gradle/JDK/temp paths; tests launch on Java 25 while compile targets Java 11; no `.github/`; R8 disabled; release signing requires an absent keystore. | `gradle.properties`, `app/build.gradle.kts`, absence of CI |
| F-010 | S2 | **No security-rules tests and a missing composite index.** Rules are untested; `getDisputesQueue` filters by `status` and orders by `createdAt` but `firebase.json` has no `firestore.indexes.json`. | `functions/src/index.ts:1302–1325`; `firebase.json` |
| F-011 | S2 | **App Check declared but unused; no rate limiting.** `firebase-appcheck-recaptcha` dependency exists, no initialisation; no callable/webhook quota controls. | grep of `app/src/main`; callable handlers |
| F-012 | S2 | **No observability.** No Crashlytics, Analytics, structured logging, tracing, SLOs or alerting; diagnostics are committed logcat/heap dumps. | build deps; root diagnostic files |
| F-013 | S2 | **Requirements and status drift.** 112/112 PRD stories `completed` while HEAD is broken (99/99 at assessment); admin **web** dashboard required but absent; README env names (`MOMO_API_KEY`, `MOMO_API_USER`, `MOMO_API_PASS`, `WEBHOOK_SECRET`) do not match code (`MTN_SUBSCRIPTION_KEY`, `MTN_API_USER`, `MTN_API_KEY`, `MOMO_ENVIRONMENT`, `MOMO_BASE_URL`); webhook secret was removed by design. | `prd.json`; `requirement_file.md` §15.3; `README.md:48–55`; `momoCollection.ts:103–136` |
| F-014 | S2 | **Repository ships sensitive/high-volume artifacts.** Two ~31 MB APKs, a device SQLite database, 665 KB logcat dump, heap crash logs, 87 `diag-*` files; `.gitignore` does not exclude them. | repo inventory |
| F-015 | S2 | **No localisation and weak accessibility.** 0 `stringResource`, 1 string resource, 178 inline strings, 59 null content descriptions, English-only for 4 countries, RTL declared but untested. | source scan |
| F-016 | S3 | **Money representation and currency inconsistency.** Monetary values are `Double`; `CurrencyUtils` uses fixed USD multipliers; Gemini fallback suggests a USD budget in a UGX flow; no rounding/commission policy. | `Entities.kt`; `CurrencyUtils.kt`; `GeminiAudioTranscriptionService.kt` |
| F-017 | S3 | **Functions runtime/deploy configuration is incoherent.** Engine `node 18` but tests import `.ts` (requires Node ≥22.6); no `predeploy` build; `tslint` script references an uninstalled package; `firebase-functions` v4/admin v11 outdated. Additionally `npm run build` fails with 8 TypeScript errors in `index.ts` (645–683) while still emitting partial `lib/`. | `functions/package.json`, `tsconfig.json`, `firebase.json`; `npm run build` transcript in `REVIEW_14.md` |
| F-018 | S3 | **Room schema is unexported and migration coverage is thin.** `exportSchema = false` with 16 migrations; only persistence-level version assertions exist. | `AppDatabase.kt`; test inventory |
| F-019 | S3 | **Fabricated AI content on missing key.** Gemini transcription returns a canned "Emergency Pipe Repair" job instead of failing or asking the user. | `GeminiAudioTranscriptionService.kt:29–40` |
| F-020 | S4 | **Dead code/deps and duplicate configuration.** Unused `ActiveJobScreen`, deprecated/fake `MoMoPaymentService`, unused Retrofit/Moshi/Navigation deps, duplicate root `AndroidManifest.xml`, `META-INF` committed. | imports/build deps/file inventory |
| F-021 | S1 | **Backend supply chain is unsafe.** `npm audit --omit=dev` reports 16 vulnerabilities (1 critical, 10 high) in production dependencies; the only fix path is a major `firebase-admin` upgrade. | `npm audit` output in `reviewers/reports/REVIEW_14.md` §2.4 |
| F-022 | S2 | **Offline "automatic sync" is not implemented.** `flushPendingActions()` takes a snapshot, logs the count, and deletes/executes nothing; queued actions never reach the server. README/PRD claim sync on reconnect. | `SkillsExchangeViewModel.kt:2196–2215`; README features list |
| F-023 | S1 | **Phone signup never creates a Firebase identity.** `verifyOtp` accepts the device-side Africa's Talking response, sets `isAuthenticated = true` and stores a session whose `userId` can fall back to an ATT request id. No `signInWithCredential`/custom token; `signInAnonymously()` has no production caller; the Firebase phone-auth code and its tests are dead. **Consequence: `loginUser` cannot find a UID, so registration fails on a clean install.** *(extends F-002/F-008)* | `SkillsExchangeViewModel.kt:2354–2405`, `1096`, `FirebaseService.kt:345–435`; `REVIEW_06.md` |
| F-024 | S2 | **Duplicate `sosIncidents` rule blocks nullify the stronger one.** Blocks at `firestore.rules:156` and `:318`; Firestore ORs allow rules for a path, so the weaker first block wins and the second's `exists(/jobs/$(jobId))` guard is bypassed. | `firestore.rules:156–170`, `:318–330`; `REVIEW_07.md` |
| F-025 | S2 | **Notifications and MoMo transactions are client-forgeable.** `notifications` create allows any authenticated user with no field binding (`:132`); `momo_transactions` create likewise (`:177`). | `firestore.rules`; `REVIEW_07.md` |
| F-026 | S2 | **Job photo writes trust self-asserted metadata.** `storage.rules` authorizes `create, update` on incoming `jobOwnerId`, so any authenticated user can write/overwrite inside another job's folder. | `storage.rules` `match /jobs/{jobId}/{allPaths=**}`; `REVIEW_07.md` |
| F-027 | S3 | **All bids are readable by every authenticated user.** `job_quotes` `allow read: if isAuthenticated()`. | `firestore.rules:119`; `REVIEW_07.md` |
| F-028 | S2 | **Admin unlock is presentation-only.** `onUnlockOperator = { isAdminUnlocked = true }`; no credential check. Server callables are gated, but the claim is never provisioned, so administration is unreachable either way. *(extends F-008)* | `MainScreen.kt:832`; `REVIEW_06.md` |
| F-029 | S1 | **Deletion over-deletes and destroys complaint evidence.** `userJobIds` comes from every job cached on the device; job/message deletes are rules-denied for non-admins so the cascade aborts; Storage is untouched; disputes where the user is `reportedUserId` are deleted; no re-auth. *(extends F-007)* | `SkillsExchangeViewModel.kt:1336–1367`, `FirebaseService.kt:275–315`; `REVIEW_09.md` |
| F-030 | S2 | **Doorstep PIN is disclosed to the worker.** The "security" PIN is rendered in the job sheet for both parties and defaults to `4829`. *(extends F-004)* | `EscrowAndProofSheet.kt`; `Entities.kt`; `REVIEW_10.md` |
| F-031 | S1 | **No refund implementation exists.** Grep for a refund function in the repository returns zero; cancellation/dispute leaves funds locked. *(extends F-006)* | `SkillsExchangeRepository.kt`; `REVIEW_08.md` |
| F-032 | S2 | **Transcription fabricates output.** Missing/invalid Gemini key returns a canned "Emergency Pipe Repair" result indistinguishable from a real transcription; the API key is called directly from the app. *(extends F-019)* | `GeminiAudioTranscriptionService.kt:35–74`; `REVIEW_05.md` |
| F-033 | S2 | **Backups are enabled with empty rules.** `allowBackup="true"` plus sample `backup_rules.xml`/`data_extraction_rules.xml` with no includes/excludes; Firebase tokens, DataStore session and the Room DB are eligible. | `AndroidManifest.xml:18–20`; `res/xml/*`; `REVIEW_07.md` |
| F-034 | S1 | **Escrow release is never invoked.** `releaseEscrowToWorker` exists server-side and is authorized, but no client call site exists, so a completed job cannot pay the worker. *(extends F-006)* | `SkillsExchangeRepository.kt:2569` (comment only); `REVIEW_08.md` |
| F-035 | S3 | **No crash, ANR, analytics or performance telemetry.** No Crashlytics/Sentry/Firebase-Perf/Analytics dependency; production failures are invisible. *(extends F-012)* | dependency inventory; `REVIEW_13.md` |
| F-036 | S3 | **Non-functional payment channels are offered as real.** Airtel Money and Visa/Card appear as selectable channels and in copy with no integration; Maps uses a placeholder key. | `EscrowAndProofSheet.kt:972`; `build.gradle.kts:28`; `REVIEW_05.md` |
| F-037 | S2 | **Abuse reports never leave the device.** `reportUser()` writes Room only and no `reports` collection has rules. *(extends F-005 area)* | `firestore.rules`; repository `reportUser`; `REVIEW_10.md` |

Full analysis and validation of each finding is assigned in `REVIEWER_REGISTER.md`.

---

## 6b. Review Cycle Results

| Reviewer | Verdict | Strongest confirmed finding | Report |
|---|---|---|---|
| R01 Architecture | FAIL | Four structurally invalid production files; 2,979/2,749-line god classes | `REVIEW_01.md` |
| R02 Android Client | FAIL | Phone onboarding cannot obtain a Firebase identity; 0 `rememberSaveable`/`BackHandler` | `REVIEW_02.md` |
| R03 Backend | FAIL | `npm run build` exits 2; no App Check; no FCM send; no `predeploy` | `REVIEW_03.md` |
| R04 Data | FAIL | `exportSchema=false`, 16 untested migrations, 33 `Double` money fields | `REVIEW_04.md` |
| R05 Integrations | FAIL | Committed AT key; fabricated transcription; log-only SMS; Airtel/card absent | `REVIEW_05.md` |
| R06 Auth | FAIL | Device-side OTP mints a local session; admin claim unprovisionable | `REVIEW_06.md` |
| R07 AppSec | FAIL | `jobs` and `messages` world-readable unauthenticated; forgeable collections | `REVIEW_07.md` |
| R08 Payments | FAIL | Release never invoked; fabricated `DISBURSED`; no refund function | `REVIEW_08.md` |
| R09 Privacy/KYC | FAIL | Erasure over-deletes cached jobs, aborts, ignores Storage, erases complaints | `REVIEW_09.md` |
| R10 Safety | FAIL | SOS is a log-only stub returning `SENT`; PIN shown to worker | `REVIEW_10.md` |
| R11 QA | FAIL | 355 tests unexecutable; no rules/migration/UI/coverage/CI | `REVIEW_11.md` |
| R12 UI/UX/A11y | FAIL | 99× `5.sp`; 59 null content descriptions; no localisation | `REVIEW_12.md` |
| R13 Perf/Reliability | FAIL | No telemetry; offline replay is a no-op; ad-hoc coroutine scopes | `REVIEW_13.md` |
| R14 DevOps | FAIL | No CI; `D:/` paths; Java 11/25; R8 off; 16 advisories | `REVIEW_14.md` |
| R15 Docs | FAIL | 99 completed stories contradicted; 61 host-absolute links; 81 build claims | `REVIEW_15.md` |

**Programme outcome: 0 PASS / 0 PASS WITH CONDITIONS / 15 FAIL. Gates G0–G8 all closed. Readiness re-scored to 0/13 domains PASS.**

## 7. Risk Register (programme level)

| Risk | Likelihood | Impact | Mitigation / owner |
|---|---|---|---|
| A build-blocking defect masks further defects until fixed | High | High | R01/R14 gate; fix only after review evidence is recorded (remediation is out of scope for this task) |
| Committed Africa's Talking key is live and abused (SMS pumping, cost, OTP fraud) | High | High | R07 + R05; rotate immediately, move OTP server-side |
| Public `jobs`/`messages` reads expose PIN, GPS, phone, chat | High | High | R07 + R09 + R10; rules redesign + tests + deploy |
| Safety incident where SOS claims dispatch but sends nothing | Medium | Critical | R10 + R05; remove claim or implement verified dispatch before launch |
| Money loss/misstatement from unwired escrow and fake payout | Medium | Critical | R08 + R03; block release until server-authoritative end-to-end |
| Regulatory breach from incomplete erasure and third-party NIN handling | High | High | R09; DPIA, retention, erasure across all stores |
| Review fatigue / false PASS due to huge documentation volume | Medium | Medium | R11 + R15 evidence protocol; only reproducible evidence counts |
| Scope creep into implementation during review | Medium | Medium | `REVIEW_PLAN.md` change-control; no source edits in this phase |

---

## 8. Phase 7 — Answers to the Required Questions

### 8.1 Project classification
**Complex**, with mission-critical payment, identity/KYC and emergency subsystems. Not Enterprise (no environments, SLOs or multi-team structure) and not production-ready.

### 8.2 Technology stack
Kotlin 2.2.10 + Jetpack Compose (Material 3) on Android (minSdk 24, targetSdk 36, compileSdk 37), single-Activity MVVM + Repository, Room 2.7.0 (schema 27), DataStore; Firebase Auth/Firestore/Storage/FCM/Cloud Functions (TypeScript, Node 18); MTN MoMo sandbox (Airtel labels only); Gemini REST; Google Maps SDK; Africa's Talking (device-side OTP); Gradle 9.5.0/AGP 9.3.2; JUnit4 + Robolectric + Roborazzi + Node `node:test`.

### 8.3 Total number of required reviewers
**15** (8 P0, 7 P1), each with a distinct assignment. See `REVIEWER_REGISTER.md`.

### 8.4 Reviewer names
1. R01 — Principal Software Architect & Codebase Integrity Reviewer
2. R02 — Senior Android Client Engineer (Kotlin/Compose)
3. R03 — Backend & Cloud Functions Engineer
4. R04 — Data & Persistence Integrity Engineer
5. R05 — API & External Integrations Engineer
6. R06 — Authentication & Identity Engineer
7. R07 — Application Security Engineer
8. R08 — Payments, Escrow & Financial Controls Auditor
9. R09 — Privacy, KYC & Regulatory Compliance Reviewer
10. R10 — Safety, Trust & Fraud Prevention Reviewer
11. R11 — QA & Test Engineering Lead
12. R12 — UI/UX & Accessibility Specialist
13. R13 — Performance, Scalability & Reliability Engineer
14. R14 — DevOps, Release Engineering & Supply Chain Reviewer
15. R15 — Documentation & Requirements Traceability Auditor

### 8.5 What each reviewer will examine
See §4 and `REVIEWER_REGISTER.md` coverage matrix; each `reviewers/*.md` file specifies exact features, source paths, requirements, tests, evidence and passing conditions.

### 8.6 Files created or updated
See §10.

### 8.7 Important risks discovered
See §7 and §6 (F-001 … F-037). The reviews added five release-stopping clusters: world-readable jobs/chats (F-003/F-007), a signup that cannot produce a Firebase identity (F-023), an SOS path that reports success without sending (F-005), a money lifecycle with no release or refund (F-031/F-034), and an erasure cascade that over-deletes and aborts (F-029).

### 8.8 Recommended review execution order
**Wave 0 (gates):** R01 → R14.
**Wave 1 (highest harm):** R07 → R10 → R08 → R09.
**Wave 2 (identity and platform):** R06 → R03 → R04 → R05.
**Wave 3 (quality and experience):** R11 → R13 → R02 → R12.
**Wave 4 (synthesis):** R15, then readiness re-score.
Full sequencing, parallelism and exit criteria: `REVIEW_PLAN.md`.

---

## 9. Limitations and Non-Claims

1. No application source code was modified. The defects above are documented, not fixed.
2. No Android build or test was executed; all compile statements are static-analysis findings and must be reproduced by R01/R11 in a provisioned environment.
3. No live Firebase, MTN, Africa's Talking, Gemini or Maps call was made.
4. The assessment does **not** assert that any feature works at runtime; it asserts what the code and configuration show.
5. **This project is not production-ready and must not be described as such** until every P0 gate in `PRODUCTION_READINESS.md` is PASS with reproducible evidence.
6. Historical documents were preserved; the 97 `completed` PRD stories were not re-verified or flipped in this task.

---

## 10. Deliverables Produced

**Created**
| File | Purpose |
|---|---|
| `ASSESSMENT_REPORT.md` | This report |
| `REVIEWER_REGISTER.md` | Roster, coverage matrix, gates, status tracking |
| `REVIEW_PLAN.md` | Execution plan, evidence protocol, report template |
| `PRODUCTION_READINESS.md` | Domain gates and explicit non-readiness verdict |
| `reviewers/ARCHITECTURE_REVIEWER.md` — deleted after independent verification; text preserved in `verification/reports/VERIFICATION_01.md` §9 | R01 |
| `reviewers/ANDROID_CLIENT_REVIEWER.md` — deleted after independent verification; text preserved in `verification/reports/VERIFICATION_02.md` §9 | R02 |
| `reviewers/BACKEND_REVIEWER.md` — deleted after independent verification; text preserved in `verification/reports/VERIFICATION_03.md` §9 | R03 |
| `reviewers/DATA_REVIEWER.md` | R04 |
| `reviewers/API_INTEGRATION_REVIEWER.md` | R05 |
| `reviewers/AUTH_REVIEWER.md` | R06 |
| `reviewers/SECURITY_REVIEWER.md` | R07 |
| `reviewers/PAYMENTS_REVIEWER.md` | R08 |
| `reviewers/PRIVACY_COMPLIANCE_REVIEWER.md` | R09 |
| `reviewers/SAFETY_FRAUD_REVIEWER.md` | R10 |
| `reviewers/QA_REVIEWER.md` | R11 |
| `reviewers/UI_UX_ACCESSIBILITY_REVIEWER.md` | R12 |
| `reviewers/PERFORMANCE_RELIABILITY_REVIEWER.md` | R13 |
| `reviewers/DEVOPS_REVIEWER.md` | R14 |
| `reviewers/DOCUMENTATION_REVIEWER.md` | R15 |
| `reviewers/reports/README.md` | Evidence output convention |

**Updated additively (history preserved)**
| File | Change |
|---|---|
| `prd.json` | Phase 18 + 15 `RVW-*` stories, `not_started`; version 1.2; `current_story = RVW-001` |
| `README.md` | Review-architecture section + factual status/known-blocker note |
| `requirement_file.md` | Appended review addendum (no prior text changed) |
| `AGENT_STATE.md` | Prepended 2026-11-19 handoff section |
| `TASKS.md` | Prepended review track |
| `CHANGELOG.md` | Prepended 2026-11-19 entry |
| `DECISIONS.md` | Prepended D-2026-11-19-1 and D-2026-11-19-2 |

**Not modified:** all application source (`app/src/**`, `functions/src/**`), rules files, build scripts, tests.

---

## 11. Verification Appendix

Commands executed for this report (reproducible):

```bash
# repository inventory
git log --oneline -8; git status --short; git ls-files | wc -l
find app/src -name '*.kt' | wc -l; find app/src -name '*.kt' -exec cat {} + | wc -l
# structural scan for brace/paren/escape corruption (custom Python tokenizer, strings/comments stripped)

# executable suites in this environment
cd functions && npm ci
npm run build                   # exit 2 — 8 TS errors in src/index.ts
npm test                        # 111 tests, 111 pass, 0 fail (Node v22.22.3)
npm audit --omit=dev            # 16 vulnerabilities (1 critical, 10 high, 4 moderate, 1 low)

# static security observations
grep -n 'allow read' firestore.rules
grep -rn 'AFRICASTALKING_API_KEY' app/src/main
grep -rn 'sendToDevice\|sendMulticast\|messaging()' functions/src   # no FCM send
grep -rn 'releaseEscrowToWorker\|createMoMoPayout' app/src/main     # no callers
```

Key metrics: 110 Kotlin files / 35,522 lines (27.7k main, 7.8k test, 1 placeholder androidTest); 4,027 TypeScript lines; 18 Room entities; 16 migrations; 111 passing Node tests but `npm run build` exits 2 with 8 errors; 16 production dependency vulnerabilities (1 critical, 10 high); 0 CI workflows; 0 rules tests; 0 `stringResource`; 0 server-side FCM sends; 1 committed provider credential; 2 committed ~31 MB APKs.

---

*End of assessment. The review programme starts at `REVIEW_PLAN.md` with gate R01.*
