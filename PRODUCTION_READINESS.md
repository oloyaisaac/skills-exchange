# PRODUCTION READINESS — Skills Exchange

**Assessment:** ASMT-2026-11-19 · **Baseline:** commit `6b39a10` · **Status date:** 2026-11-19
**Verdict: NOT PRODUCTION-READY. Release is BLOCKED.**

> This document does **not** certify the product. It records why it cannot be released today and what evidence would be required to change that. No reviewer has completed a specialist review yet; all statuses below are the assessor's pre-review determination and are provisional until challenged with evidence.

## 1. Readiness summary

| # | Domain | Gate | Status | Blocking reason (evidence) |
|---|---|---|---|---|
| 1 | Build & code integrity | G0 | **FAIL** | HEAD not structurally buildable: literal `\n` at `FirebaseService.kt:709–716`, `]=package` at `GeminiAudioTranscriptionService.kt:1`, dead/broken `ActiveJobScreen.kt` |
| 2 | Release engineering & supply chain | G1 | **FAIL** | No CI; `gradle.properties` hardcodes `D:/` paths; tests on Java 25 vs Java 11 compile; R8 disabled; release key absent |
| 3 | Application security | G2 | **FAIL** | World-readable `jobs`/`messages` (address, phone, payment channel, chat); committed Africa's Talking key; PIN plaintext with default and no lockout; App Check unused; no rules tests |
| 4 | Safety & emergency response | G3 | **FAIL** | SOS SMS is a logging stub returning `SENT`; no verified dispatch channel |
| 5 | Payments & financial controls | G4 | **FAIL** | Escrow release has no client caller; device payout returns `DISBURSED` without money; dispute refunds mark escrow `REFUNDED` without a provider call; wallet ledger device-local; sandbox-only by code |
| 6 | Privacy, KYC & compliance | G5 | **FAIL** | Erasure fails at the first Firestore delete (rules deny `users` delete), leaving every other collection and all Storage documents orphaned after Auth is removed; empty backup rules; unverified encryption claims |
| 7 | Authentication & identity | G6 | **PARTIAL** | Strong identity-resolution helpers, but no admin claim provisioning, no re-auth for sensitive actions |
| 8 | Data & persistence | — | **PARTIAL** | Room v27 with 18 entities and 16 migrations, but `exportSchema = false`, thin migration tests |
| 9 | Backend & integrations | — | **PARTIAL** | 111 passing Node tests and sound pull-to-verify money core; deploy config incomplete; no FCM send |
| 10 | QA & testing | G7 | **FAIL** | Android tests unexecuted here; no rules/UI/instrumentation coverage; master docs contradict source |
| 11 | Performance & reliability | — | **UNVERIFIED** | No baselines, no crash/ANR telemetry, no SLOs, listener/read volume unmeasured |
| 12 | UI/UX & accessibility | — | **FAIL** | 0 `stringResource`, 178 inline strings, 59 null content descriptions, English-only, no a11y testing |
| 13 | Documentation & traceability | G8 | **FAIL** | 97/97 stories `completed` contradicted by F-001; README env names mismatch code; no admin runbook |

**Result: 0 of 12 release-relevant domains PASS.**

## 2. Absolute blockers (must be true before any release discussion)

1. **The committed code compiles** from a clean checkout on a standard CI image, proven by a fresh transcript — not a historical log.
2. **No secrets in the repository.** The Africa's Talking key is revoked/rotated, OTP moves server-side, and secret scanning runs in CI.
3. **No public PII.** `jobs` and `messages` are not world-readable; address, phone, payment channel and chat are not exposed to unauthenticated clients; rules are covered by emulator tests.
4. **Emergency claims are true.** Either SOS dispatches through a verified channel with delivery evidence, or the UI/README stop claiming dispatch and the gap is disclosed.
5. **Money is server-authoritative end-to-end.** Funding, release, refund, payout and cashout are idempotent, reconciled and covered by tests; no fabricated `DISBURSED`/`SENT` results.
6. **Erasure works.** Account deletion clears Auth, Firestore, Storage and Room, or reports failure without destroying the auth record.
7. **A release pipeline exists.** Signed, minified artifact built by CI, versioned, with a rollback path and a staging environment.
8. **Monitoring exists.** Crash/ANR reporting, structured logs, alerting on payment/SOS failures and defined SLOs.

None of these is currently satisfied.

## 3. Domain detail

### 3.1 Build and code integrity — FAIL
Static scan of 110 Kotlin files found three structurally invalid files. There is no Gradle/JDK/Android SDK in the assessment environment, so the failure is asserted from source structure, not from a build. Historical `build_result*.txt`/`c123_verify_*.log` artifacts cannot be attributed to commit `6b39a10` and must not be used as evidence.

**To change status:** R01 records a clean `:app:compileDebugKotlin` transcript on this commit and the scan is clean.

### 3.2 Release engineering — FAIL
`gradle.properties` contains `D:/gradle-home`, `D:/kilo_downloads/jdk17/...`, `D:/skills exchange/jdk11/...` and `D:/gradle-temp`. `app/build.gradle.kts` compiles for Java 11 but launches tests on Java 25. Release signing reads `KEYSTORE_PATH`/`STORE_PASSWORD`/`KEY_PASSWORD` with a default keystore that is not in the repository. `isMinifyEnabled = false`. There is no `.github/` or any other CI definition. Two ~31 MB APKs and a SQLite dump are committed.

**To change status:** Linux CI builds and tests from a clean clone, signed release produced by CI, artifacts removed from Git, dependency/SBOM scan green.

### 3.3 Application security — FAIL
`firestore.rules` allows public read of `jobs` and nested `messages`. Mirrored job documents expose street address, suite/landmark, MoMo phone number, payment channel, escrow state and ownership; every chat message is public. The doorstep PIN is not mirrored to Firestore, but it is stored plaintext in Room with a `"4829"` default, shown in clear text and checked against a client-supplied expected value with no attempt limit. A real-looking Africa's Talking key is hardcoded in `AfricaTalkingService.kt:15` and used from the device. `momo_transactions` permits any authenticated create. App Check is a dependency without initialisation. There are no rules tests. `allowBackup="true"` with empty extraction rules.

**To change status:** rules redesigned and tested per actor; secrets rotated and scanning enforced; PIN server-side and rate-limited; hardening applied; no open critical/high findings.

### 3.4 Safety — FAIL
`SmsNotificationService.dispatchEmergencySosAlert` logs, delays 200 ms and returns `status = "SENT"`. The repository invokes it during SOS. There is no Africa's Talking SMS send call and no server FCM send. The admin SOS queue is server-gated and the incident record is authorization-controlled, which is good — but the user's safety expectation ("contacts notified") is not met.

**To change status:** verified dispatch (server-side SMS/voice to named contacts with delivery receipts) or explicit, honest UI that the alert is recorded but not transmitted; PIN hardened; abuse cases tested.

### 3.5 Payments — FAIL
The server money core is the strongest part of the codebase: sandbox-only fail-closed config, pull-to-verify webhook handling, terminal-state immutability, idempotent escrow lock, and 111 passing Node tests. But the product cannot move money end-to-end: no Android caller for `releaseEscrowToWorker` or `createMoMoPayout`; `MoMoPaymentService.disburseEscrowPayoutToWorker` fabricates a `DISBURSED` payout; the wallet ledger is Room-only; dispute refunds only queue a record and set `escrowStatus = "REFUNDED"` without a provider refund or executor; the 10% fee is computed client-side; no reconciliation exists; production is refused by `loadMomoConfig` by design.

**To change status:** server-authoritative release/refund/payout/cashout, ledger and provider settlement reconciliation, money tests green, production configuration path defined.

### 3.6 Privacy and compliance — FAIL
National ID and guarantor NIN data are collected; storage isolation and 15-minute signed URLs are sound. But erasure is broken: the client first attempts `users/{uid}.delete()`, which `firestore.rules` does not allow, so the function throws before deleting any other collection; Storage KYC/guarantor objects are never deleted; Auth is then deleted anyway, orphaning the data. Backup rules are empty while backups are enabled. The UI claims encryption that no code implements. There is no retention schedule, DPIA or recorded consent lifecycle.

**To change status:** erasure implemented across all stores and tested; retention defined; consent recorded; claims corrected; processor/transfer documentation.

### 3.7 Authentication and identity — PARTIAL
Session restore deliberately fails closed on UID mismatch; authoritative UID resolution is used for quote/rating/SOS/cashout authorization; admin callables verify the custom claim. Missing: claim provisioning/bootstrap, re-authentication for payout-destination changes and deletion, OTP abuse controls.

### 3.8 Data — PARTIAL
18 entities, schema 27, 16 migrations and a well-designed local cashout policy. `exportSchema = false` prevents schema diffing; migration coverage is thin; money is `Double`; dual-write has no conflict protocol.

### 3.9 Backend and integrations — PARTIAL
16 functions with consistent admin gating and input validation for the reviewed paths; tests are strong for pure modules but do not execute handlers. Node 18 engine conflicts with tests importing `.ts` (needs ≥22.6); no `predeploy` build; `firebase-functions` v4 and `firebase-admin` v11 are outdated; no FCM send; no composite index file for a `status + createdAt` query.

### 3.10 Quality — FAIL
42 JVM test files and 111 Node tests are real assets. But no Firestore rules tests, one placeholder instrumentation test, no Compose UI tests, no coverage measurement, no CI execution. Master documents assert completed/verified states that the committed source contradicts.

### 3.11 Performance and reliability — UNVERIFIED
No startup/jank baselines, no crash or ANR telemetry, no listener/read-cost inventory, no retry/backoff policy, no SLOs. Cannot be assessed as ready without measurement.

### 3.12 Accessibility and localisation — FAIL
0 `stringResource`, 1 string resource, 178 inline `Text("…")`, 59 null content descriptions, English-only for a four-country market, no accessibility testing.

### 3.13 Documentation — FAIL
620 KB of master documentation with real historical value, but status is not evidence-backed: `prd.json` marks 97/97 complete; README `.env` keys do not match `loadMomoConfig`; the webhook secret documented in README was deliberately removed; admin web dashboard is required but absent; `README_AI.md` references Windows-only paths.

## 4. What would be required for a readiness review

| Requirement | Owner | Evidence needed |
|---|---|---|
| Build + tests reproducible in CI | R01/R14 | Green pipeline on baseline commit |
| Security and rules remediation | R07 | Rules tests, secret scan, exploit retest |
| Money end-to-end + reconciliation | R08 | Sandbox E2E transcript, ledger reconciliation |
| Safety dispatch verified | R10 | Provider delivery receipt + runbook |
| Erasure and retention implemented | R09 | Per-store deletion evidence, retention policy |
| Identity/admin lifecycle | R06 | Claim provisioning runbook + tests |
| Observability | R13 | Crash/ANR dashboards, SLOs, alerts |
| Accessibility/localisation | R12 | a11y audit, externalised strings |
| Documentation reconciliation | R15 | Traceability matrix, contradiction register closed |

## 5. Explicit non-claims

- This document is **not** a certification, audit opinion or compliance attestation.
- No statement here should be read as "close to ready"; the gap is structural, not cosmetic.
- The product must not be distributed to real users handling real money or emergency situations in its current state.
- Statuses change only with reproducible evidence recorded in `REVIEWER_REGISTER.md` and a reviewer report.

*Re-score after every wave in `REVIEW_PLAN.md`; the next formal readiness decision occurs after R15 synthesis.*
