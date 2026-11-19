# PRODUCTION READINESS — Skills Exchange

**Assessment:** ASMT-2026-11-19 · **Code baseline:** commit `6b39a10` · **Docs HEAD:** `d448372` · **Status date:** 2026-11-19
**Verdict: NOT PRODUCTION-READY. Release is BLOCKED.**

> **Review cycle complete (2026-11-19):** all 15 specialist reviews have been executed against the
> baseline source and submitted. **15 of 15 returned FAIL** (0 PASS, 0 PASS WITH CONDITIONS,
> 0 BLOCKED). Every gate G0–G8 is closed. Reports: `reviewers/reports/REVIEW_01.md` …
> `REVIEW_15.md`; roster and verdicts: `REVIEWER_REGISTER.md`.

> This document does **not** certify the product. It records why it cannot be released today and what
> evidence would be required to change that. Statuses change only when a reviewer report supplies
> reproducible evidence at a named commit.

## 1. Readiness summary

| # | Domain | Gate | Status | Blocking reason (evidence) |
|---|---|---|---|---|
| 1 | Build & code integrity | G0 | **FAIL** | HEAD is not structurally buildable: unclosed `JobPostEntity(` at `SkillsExchangeRepository.kt:579–651`, literal `\n` at `FirebaseService.kt:709–716`, `]=package` at `GeminiAudioTranscriptionService.kt:1`, dead/broken `ActiveJobScreen.kt`. Backend `npm run build` exits 2. |
| 2 | Release engineering & supply chain | G1 | **FAIL** | No CI; `gradle.properties` hardcodes `D:/`; Java 11 compile vs Java 25 tests; R8 disabled; no release key; no `predeploy`; 16 production npm advisories. |
| 3 | Application security | G2 | **FAIL** | `jobs` **and** `messages` are world-readable to unauthenticated clients; committed Africa's Talking key; forgeable notifications/transactions; storage writes trust self-asserted metadata; no App Check; no rules tests. |
| 4 | Safety & emergency response | G3 | **FAIL** | SOS dispatch is a logging stub returning `SENT`; it only runs when a profile resolves; no server FCM/SMS; admin escalation unprovisionable; PIN shown to the worker. |
| 5 | Payments & financial controls | G4 | **FAIL** | Release callable has no client caller; device payout fabricates `DISBURSED`; **no refund function exists**; fee computed client-side; ledger device-local; sandbox-only by design. |
| 6 | Privacy, KYC & compliance | G5 | **FAIL** | Erasure cascade over-deletes cached jobs, aborts at the first rules-denied delete, ignores Storage, erases complaints against the user, and requires no re-auth; privacy notice false; backups enabled with empty rules. |
| 7 | Authentication & identity | G6 | **FAIL** | Phone signup never creates a Firebase credential, so registration is broken on a clean install; admin claim never provisioned; admin UI unlock is a local boolean; no re-auth for deletion. |
| 8 | Data & persistence | — | **FAIL** | Room v27 with 18 entities and 16 migrations, but `exportSchema=false`, no migration tests, 33 `Double` money fields, and no server/ledger reconciliation. |
| 9 | Backend & integrations | — | **FAIL** | 17 exports with sound money authorization, but the build is red, no FCM send, no App Check, unthrottled webhook, Airtel/card channels absent, transcription fabricates output. |
| 10 | QA & testing | G7 | **FAIL** | 355 Android tests cannot execute because the module does not compile; 111 Node tests pass; zero rules/migration/UI tests; no coverage; no CI. |
| 11 | Performance & reliability | — | **FAIL** | No crash/ANR/analytics telemetry; offline queue never replays; ad-hoc coroutine scopes; no startup/jank baselines. Performance itself is **UNVERIFIED**. |
| 12 | UI/UX & accessibility | — | **FAIL** | 0 `stringResource`, 178 inline strings, 59 null content descriptions, 0 `testTag`, 99 uses of `5.sp`, no localisation, no a11y testing. |
| 13 | Documentation & traceability | G8 | **FAIL** | 99 completed stories contradicted by code; 61 host-absolute `file:///d:/` links; 81 "BUILD SUCCESSFUL" claims; only 6 requirement IDs; README advertises Airtel and an admin dashboard that do not exist. |

**Result: 0 of 13 release-relevant domains PASS.** 15/15 reviewer verdicts FAIL; gates G0–G8 closed.

## 2. Absolute blockers (must be true before any release discussion)

1. **The committed code compiles** from a clean checkout on a standard CI image, proven by a fresh transcript — not a historical log.
2. **No secrets in the repository.** The Africa's Talking key is revoked/rotated, OTP moves server-side, and secret scanning runs in CI.
3. **No public PII.** `jobs` and `messages` are not world-readable; address, phone, payment channel and chat are not exposed to unauthenticated clients; rules are covered by emulator tests.
4. **Emergency claims are true.** Either SOS dispatches through a verified channel with delivery evidence, or the UI/README stop claiming dispatch and the gap is disclosed.
5. **Money is server-authoritative end-to-end.** Funding, release, refund, payout and cashout are idempotent, reconciled and covered by tests; no fabricated `DISBURSED`/`SENT` results.
6. **Erasure works.** Account deletion clears Auth, Firestore, Storage and Room, targets only owned data, preserves complaint evidence, requires re-auth, and reports failure without destroying the auth record.
7. **Phone signup produces a real Firebase identity**, and admin claims are provisioned by an audited path.
8. **A release pipeline exists.** Signed, minified artifact built by CI, versioned, with a rollback path and a staging environment.
9. **Monitoring exists.** Crash/ANR reporting, structured logs, alerting on payment/SOS failures and defined SLOs.

None of these is currently satisfied.

## 3. Domain detail

### 3.1 Build and code integrity — FAIL
Static scan of 110 Kotlin files found four structurally invalid production files. R01
(`reviewers/reports/REVIEW_01.md`) records **FAIL** with `nl` excerpts. R14 confirms
`npm run build` exits **2** with 8 TypeScript errors while still emitting `lib/`. There is no
Gradle/JDK/Android SDK in the assessment environment, so the Android failure is asserted from source
structure, not from a build. Historical `build_result*.txt`/`c123_verify_*.log` artifacts cannot be
attributed to `6b39a10` and must not be used as evidence.

**To change status:** a clean `:app:compileDebugKotlin` transcript on the reviewed commit plus a
green `npm run build`, both pinned to the commit in CI.

### 3.2 Release engineering — FAIL
`gradle.properties` contains `D:/gradle-home`, `D:/kilo_downloads/jdk17/...`,
`D:/skills exchange/jdk11/...` and `D:/gradle-temp`. `app/build.gradle.kts` compiles for Java 11
but launches tests on Java 25. Release signing reads `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD`
with a default keystore that is not in the repository. `isMinifyEnabled = false`. There is no
`.github/` or any other CI definition. Two ~31 MB APKs and a SQLite dump are committed. `npm audit`
reports 16 production advisories (1 critical, 10 high); `firebase.json` has no `predeploy`.

**To change status:** Linux CI builds and tests from a clean clone, signs the release, enforces a
build gate, removes artefacts from Git, and reports a clean dependency/SBOM scan.

### 3.3 Application security — FAIL
`firestore.rules:228` is `allow read: if true` for `jobs`; `:295` is `allow read: if true` for
nested `messages`. This exposes street address, suite/landmark, MoMo number, payment channel, escrow
state and every chat message to **unauthenticated** clients. `notifications` create is open to any
authenticated user (`:132`) and `momo_transactions` create likewise (`:177`), so both can be forged.
`job_quotes` is readable by every authenticated user (`:119`). Storage job writes authorize on
`request.resource.metadata.jobOwnerId`, which the uploader controls, so any authenticated user can
write and overwrite inside another job's folder. The `sosIncidents` path is declared twice (`:156`,
`:318`); Firestore ORs allow rules, so the weaker first block nullifies the second's
`exists(job)` guard. `reports` has no rules at all. The doorstep PIN is stored plaintext with a
`"4829"` default, rendered to both parties, and checked against a client-supplied expected value
with no attempt limit. `allowBackup="true"` with empty sample backup rules. App Check is absent.
There are zero rules tests.

**To change status:** rules redesigned per actor and collection, rules tests in CI, secret rotated,
App Check enforced, PIN server-side, backups excluded; no open critical/high findings.

### 3.4 Safety — FAIL
`SmsNotificationService.dispatchEmergencySosAlert` logs, delays 200 ms and returns
`status = "SENT"`. `raiseSos` only calls it when an authenticated profile resolves; otherwise the
alert stays in-app and silent. There is no Africa's Talking SMS send and no server FCM send. The
admin SOS queue is server-gated and the incident record is authorization-controlled — but the admin
claim is never provisioned and the admin UI "unlock" is `isAdminUnlocked = true`
(`MainScreen.kt:832`). The user's safety expectation ("contacts notified") is not met.

**To change status:** verified dispatch with delivery receipts to named contacts, a real on-call
FCM path, an honest fallback surface, a server-generated PIN shown only to the resident, and abuse
cases tested.

### 3.5 Payments — FAIL
The server money core is the strongest part of the codebase: sandbox-only fail-closed config,
pull-to-verify webhook handling, terminal-state immutability, idempotent escrow lock, and 111
passing Node tests. But the product cannot move money end-to-end: no Android caller for
`releaseEscrowToWorker` or `createMoMoPayout`; `MoMoPaymentService.disburseEscrowPayoutToWorker`
fabricates a `DISBURSED` payout (`:79–96`); the wallet ledger is Room-only; **no refund function
exists at all**; the 10% fee is computed client-side (`repository:565/660`); no reconciliation
exists; production is refused by `loadMomoConfig` by design; Airtel and card are presented as
channels with no integration.

**To change status:** server-authoritative release/refund/payout/cashout, ledger and provider
settlement reconciliation, money tests green, production configuration path defined, non-functional
channels removed.

### 3.6 Privacy and compliance — FAIL
National ID and guarantor NIN data are collected; storage isolation and 15-minute signed URLs are
sound. But erasure is broken in five ways: it derives job IDs from **every job cached on the
device**, deletes each job and its messages (`FirebaseService.kt:306–312`) although rules deny
non-admin job deletion, aborts at the first failure, never touches Storage KYC/guarantor objects,
erases disputes where the user is `reportedUserId` (`:295–299`), and requires no fresh
authentication. The privacy notice claims encryption-at-rest, coarse-only location, a complete JSON
export and 24/7 arbitration — none of which is implemented. Chat data is public. There is no
retention schedule, DPIA, DSAR workflow or processor disclosure. Backup rules are empty while
backups are enabled.

**To change status:** erasure implemented server-side across all stores and tested; retention and
consent lifecycle defined; notice corrected; processor/transfer documentation; backups excluded.

### 3.7 Authentication and identity — FAIL
`verifyOtp` (`SkillsExchangeViewModel.kt:2354–2405`) accepts the device's own Africa's Talking
response, sets `isAuthenticated = true`, and saves a session whose `userId` can fall back to an ATT
request id. No Firebase credential is created, and `signInAnonymously()` has no production caller,
so `loginUser` fails on a clean install: phone registration is non-functional. The Firebase phone
auth code in `FirebaseService.kt:345–435` and its tests are dead. Google sign-in works but is never
linked to a phone identity. `decideRestoreIdentity` is genuinely fail-closed — but it cannot restore
a user who was never signed in to Firebase. `setCustomUserClaims` does not exist, so the admin claim
can never be provisioned; the admin UI unlock is cosmetic. Deletion has no re-authentication.

**To change status:** Firebase credential (or custom token) before `isAuthenticated`; audited admin
provisioning; re-auth for destruction; account linking; revocation on logout.

### 3.8 Data — FAIL
18 entities, schema 27, 16 migrations and a well-designed local cashout policy. `exportSchema =
false` prevents schema diffing and no `MigrationTestHelper` test exists, so upgrades are unaudited.
Money is `Double` (33 fields), and only two repository call sites use `runInTransaction`. Dual-write
between Room and Firestore has no conflict protocol and there is no reconciliation job. Messages
are stored twice with only `timestamp` for ordering.

**To change status:** exported schema + per-version migration tests, integer minor units, server-side
reconciliation, destructive fallback removed from release builds.

### 3.9 Backend and integrations — FAIL
17 exports (13 callables, 2 scheduled, 1 trigger, 1 HTTP webhook) with consistent admin gating and
amounts read from stored documents. **`npm run build` fails (8 errors, `index.ts:645–683`)** while
still emitting `lib/`; there is no `predeploy`, so a deploy ships partial output. Node 18 engine
conflicts with tests importing `.ts`; `firebase-functions` v4 / `firebase-admin` v11 are outdated;
production dependencies carry 16 advisories; no FCM send; no App Check; webhook unauthenticated and
unthrottled; Airtel/card have no provider; Gemini transcription fabricates output when its key is
missing; Maps uses a placeholder key.

**To change status:** green build gated by CI, `predeploy` hook, supported runtime, App Check and
rate limits, FCM send path, honest integration failure states.

### 3.10 Quality — FAIL
41 JVM test files (355 `@Test`) and 111 Node tests are real assets, and several security-decision
tests are well targeted. But the Android module does not compile, so none of those tests can run;
there are no Firestore rules tests, no migration tests, one placeholder instrumentation test, no
Compose UI tests, no coverage measurement and no CI. Master documents assert completed/verified
states that the committed source contradicts.

**To change status:** `./gradlew test` green in CI, rules/migration/E2E suites, coverage floor,
historical claims corrected.

### 3.11 Performance and reliability — FAIL
No startup/jank baselines, no crash or ANR telemetry, no analytics, no listener/read-cost inventory,
no retry/backoff policy, no SLOs. `flushPendingActions()` preserves and logs queued actions but
never replays them (`SkillsExchangeViewModel.kt:2196–2215`), and there is no WorkManager. Integrations
launch ad-hoc `CoroutineScope`s. APK is 31 MB with three oversized JPEGs and R8 disabled. Actual
performance is **UNVERIFIED** rather than known-good.

**To change status:** crash/perf telemetry, SLOs, replay via WorkManager, structured scopes, baseline
profiles and measured budgets in CI.

### 3.12 Accessibility and localisation — FAIL
0 `stringResource`, 1 string resource, 178 inline `Text("…")`, 59 null content descriptions, 0
`testTag`, 99 uses of `5.sp` and 150 of `11.sp`, English-only for a four-country market, no
accessibility testing. Money is interpolated and truncated with `toInt()`.

**To change status:** externalised strings with at least one additional locale, minimum text sizes,
labelled controls, TalkBack and font-scale audit, `testTag`s for automation.

### 3.13 Documentation — FAIL
620 KB of master documentation with real historical value, but status is not evidence-backed:
`prd.json` marks 99 stories complete while phone signup, release, refunds, SOS and offline replay do
not work; 61 `file:///d:/` links are dead; "BUILD SUCCESSFUL" appears 81 times across
`AGENT_STATE.md`/`CHANGELOG.md`; `requirement_file.md` carries only 6 unique requirement IDs; README
advertises Airtel APIs and an admin dashboard that do not exist; `README_AI.md` references
Windows-only paths.

**To change status:** reconciled statuses with evidence links, portable links, requirement→code→test
trace matrix, README corrected, and a single precedence rule between the master documents.

## 4. What would be required for a readiness review

| Requirement | Owner | Evidence needed |
|---|---|---|
| Build + tests reproducible in CI | R01/R14 | Green pipeline on the reviewed commit |
| Security and rules remediation | R07 | Rules tests, secret scan, exploit retest |
| Money end-to-end + reconciliation | R08 | Sandbox E2E transcript, ledger reconciliation, refund path |
| Safety dispatch verified | R10 | Provider delivery receipt + on-call runbook |
| Erasure and retention implemented | R09 | Per-store deletion evidence, retention policy |
| Identity/admin lifecycle | R06 | Working signup transcript + claim provisioning runbook |
| Observability | R13 | Crash/ANR dashboards, SLOs, alerts |
| Accessibility/localisation | R12 | a11y audit, externalised strings |
| Documentation reconciliation | R15 | Traceability matrix, contradiction register closed |

## 5. Explicit non-claims

- This document is **not** a certification, audit opinion or compliance attestation.
- No statement here should be read as "close to ready"; the gap is structural, not cosmetic.
- The product must not be distributed to real users handling real money or emergency situations in
  its current state.
- Statuses change only with reproducible evidence recorded in `REVIEWER_REGISTER.md` and a reviewer
  report.

*Re-score after every wave in `REVIEW_PLAN.md`; this is the post-cycle score. The next formal
readiness decision requires the remediation evidence above.*
