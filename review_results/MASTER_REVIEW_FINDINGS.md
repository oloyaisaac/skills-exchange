# MASTER REVIEW FINDINGS — Skills Exchange

**Programme:** independent review execution (Prompt 2) · **Source:** HEAD `06ff649`, code baseline `6b39a10`
**Date:** 2026-11-19 · **Reviewers:** 15/15 completed · **Verdict:** NOT PRODUCTION-READY

This report consolidates every issue raised by the 15 individual reports in this directory. Duplicate
entries are merged into one master issue while the "Raised by" column preserves each reviewer's
perspective. Two factual corrections to the first review cycle are recorded in §4; they change counts,
not verdicts.

## 1. Summary

| Metric | Value |
|---|---|
| Reviewers assigned / completed | 15 / 15 |
| Individual issues raised (raw) | 91 |
| Consolidated master issues | 73 |
| CRITICAL | 11 |
| HIGH | 31 |
| MEDIUM | 30 |
| LOW | 1 |
| INFORMATIONAL | 0 (positives are recorded per report, not as issues) |
| Verified failing | 46 |
| Not implemented | 23 |
| Partially implemented | 4 |
| Unable to verify (runtime-only) | 0 issues are classified solely as unverifiable; 12 carry unverified runtime facets |

## 2. Consolidated findings

| ID | Sev | Consolidated issue | Component | Evidence | Raised by | Classification |
|---|---|---|---|---|---|---|
| MR-001 | CRITICAL | Android source does not compile: four structurally invalid files | `app/src/main/java` | `SkillsExchangeRepository.kt:579–651`; `FirebaseService.kt:709–716`; `GeminiAudioTranscriptionService.kt:1`; `ActiveJobScreen.kt:34` | R01-01 | Verified failing |
| MR-002 | CRITICAL | Cloud Functions build fails with 8 TypeScript errors | `functions/src/index.ts:645–683` | `npm run build` exit 2; `lib/` still emitted | R03-01 | Verified failing |
| MR-003 | HIGH | God classes, no DI, no navigation, no module boundaries | Repository/ViewModel/escrow sheet | 2,979 / 2,749 / 1,832 lines; single `:app` module | R01-02 | Verified failing |
| MR-004 | MEDIUM | Dead code, duplicate manifest, unused dependencies | Project root | Dead `ActiveJobScreen`; duplicate root manifest; unused Retrofit/Moshi/Navigation | R01-03 | Verified failing |
| MR-005 | HIGH | No CI pipeline of any kind | Repository | No `.github/` or equivalent | R14-01 | Not implemented |
| MR-006 | HIGH | Build config is machine-local and inconsistent | Gradle | `gradle.properties:31–33,38,42` `D:/`; Java 11 compile vs 25 tests; `gradlew` 100644 | R14-02 | Verified failing |
| MR-007 | HIGH | No release signing/hardening; 62 MB artifacts committed | Release | R8 off; keystore absent; APKs, SQLite, logs tracked | R14-03 | Not implemented |
| MR-008 | MEDIUM | 16 production advisories; Node 18 vs modern test tooling | Dependencies | `npm audit --omit=dev`: 1 critical, 10 high, 4 moderate, 1 low | R14-04 | Verified failing |
| MR-009 | MEDIUM | No predeploy, staging or rollback | Deploy | `firebase.json` has no `predeploy` | R03-02, R14-05 | Not implemented |
| MR-010 | CRITICAL | `jobs`, `messages`, `providers` are world-readable | Firestore rules | `allow read: if true` at `:228`, `:295`, `:216` | R07-01 | Verified failing |
| MR-011 | HIGH | Live-looking Africa's Talking key ships in the APK | OTP/SMS client | `AfricaTalkingService.kt:15` (redacted) | R07-11, R05-01 | Verified failing |
| MR-012 | HIGH | Duplicate `sosIncidents` blocks nullify the stricter rule | Firestore rules | Blocks at `:156` and `:318`; allow rules OR | R07-02 | Verified failing |
| MR-013 | HIGH | Notifications and MoMo transactions are client-forgeable | Firestore rules | Open creates at `:132`, `:177` | R07-03 | Verified failing |
| MR-014 | HIGH | Storage photo writes trust self-asserted metadata | `storage.rules` | `match /jobs/{jobId}/{allPaths=**}` authorizes incoming `jobOwnerId` | R07-04 | Verified failing |
| MR-015 | HIGH | All quotes readable by every authenticated user | Firestore rules | `job_quotes` read `:119` | R07-05 | Verified failing |
| MR-016 | HIGH | Backups enabled with empty backup rules | Manifest/XML | `allowBackup=true` (`:18`); sample XML, no excludes | R07-06, R09-07 | Verified failing |
| MR-017 | HIGH | No App Check enforcement or rate limits | Client/backend | Dependency declared (`build.gradle.kts:119`), never initialized/enforced | R07-07, R03-03 | Not implemented |
| MR-018 | MEDIUM | Abuse reports have no rules and never reach the backend | Rules/repository | 0 `match /reports`; `reportUser` Room-only (`:2705`) | R07-08, R10-04 | Not implemented |
| MR-019 | MEDIUM | Zero rules tests | Test suite | 0 rules tests/emulator config | R07-09, R11-03 | Not implemented |
| MR-020 | MEDIUM | R8 disabled; no network security config or pinning | Build config | `isMinifyEnabled=false` (`:44`) | R07-10 | Verified failing |
| MR-021 | MEDIUM | MoMo webhook is unauthenticated and unthrottled | `processMoMoWebhook` | Public HTTP handler, no signature/timestamp/limit | R03-05 | Verified failing |
| MR-022 | CRITICAL | OTP creates a local session without a Firebase identity; signup cannot complete | Auth | `verifyOtp` VM `2354–2405`; `loginUser` needs UID; Firebase phone auth uncalled | R06-01, R02-01 | Verified failing |
| MR-023 | HIGH | Admin claim unprovisionable; admin UI unlock is a local boolean | Claims + admin UI | 0 `setCustomUserClaims`; `MainScreen.kt:832` | R06-02 | Verified failing |
| MR-024 | HIGH | No re-auth for destructive actions; no token revocation | Auth lifecycle | Deletion without fresh auth; logout clears DataStore only | R06-03 | Not implemented |
| MR-025 | MEDIUM | Google and phone identities never link | Auth | No linking/merge path | R06-04 | Not implemented |
| MR-026 | CRITICAL | Escrow release never invoked from the client | Completion | 0 call sites of `releaseEscrowToWorker` in `app/src/main` | R08-01 | Not implemented |
| MR-027 | CRITICAL | No refund implementation exists | Cancellation/dispute | 0 refund functions/callables | R08-02 | Not implemented |
| MR-028 | CRITICAL | Device payout fabricates `DISBURSED` | `MoMoPaymentService` | Synthetic payout id, no network (`:79–96`) | R08-03 | Verified failing |
| MR-029 | HIGH | Fee and total computed client-side | Pricing | `platformFee = laborTotal * 0.10` (`repository:565/660`) | R08-04 | Verified failing |
| MR-030 | HIGH | Escrow status transitions are client-writable | Rules/repository | Status strings written by client; no per-actor transition rule | R08-05 | Verified failing |
| MR-031 | HIGH | Ledger is device-only with no server settlement | Room/cashout | Local ledger/balance; no settlement record | R08-06 | Verified failing |
| MR-032 | MEDIUM | No reconciliation or stuck-escrow alerting | Operations | No recon job/report/alert | R08-07 | Not implemented |
| MR-033 | MEDIUM | Airtel and card offered without integration | Payment UI | `EscrowAndProofSheet.kt:972`; no provider code | R05-05, R08-08 | Not implemented |
| MR-034 | CRITICAL | SOS dispatch is a log-only stub that returns `SENT` | SMS service | `SmsNotificationService.kt:60+` | R10-01 | Verified failing |
| MR-035 | CRITICAL | No server FCM/escalation; SOS silent when no profile resolves | Functions/SOS | 0 `admin.messaging`; conditional dispatch | R10-02 | Not implemented |
| MR-036 | HIGH | Doorstep PIN disclosed to worker; fixed default; no lockout | Escrow sheet/data | `EscrowAndProofSheet.kt:641,649`; `Entities.kt:259` `"4829"` | R10-03, R02-04 | Verified failing |
| MR-037 | HIGH | Account deletion removes complaints made against the user | Disputes | `FirebaseService.kt:295–299` | R09-02, R10-05 | Verified failing |
| MR-038 | MEDIUM | No SOS throttle, dedup or admin audit trail | Safety ops | No rate limit; no audit log | R10-06 | Not implemented |
| MR-039 | MEDIUM | Guarantor/identity controls weak against collusion | Verification | No cross-worker reuse detection; NIN format-only | R10-07 | Partially implemented |
| MR-040 | MEDIUM | Admin/SOS escalation unreachable in practice | Admin lifecycle | Depends on MR-023 | R10-08 | Verified failing |
| MR-041 | CRITICAL | Erasure over-deletes cached jobs, aborts, and ignores Storage | Deletion | VM `1336–1367`; `FirebaseService.kt:306–312` | R09-01 | Verified failing |
| MR-042 | HIGH | Privacy notice contains unimplemented claims | In-app notice | `AccountComplianceDialog.kt:178–193` | R09-03 | Verified failing |
| MR-043 | HIGH | No retention or purge schedule | Platform | 0 retention/purge jobs | R09-04 | Not implemented |
| MR-044 | MEDIUM | Consent, processor disclosure and DSAR workflow missing | Compliance ops | One boolean (`Entities.kt:476`); no processor list/SLA | R09-05 | Not implemented |
| MR-045 | MEDIUM | NIN over-collected and unmasked at rest | KYC | Full NIN stored; masking helper unused on storage | R09-06 | Partially implemented |
| MR-046 | HIGH | `exportSchema=false` and no migration tests | Room | v27, 16 migrations, 0 `MigrationTestHelper` | R04-01, R11-04 | Verified failing |
| MR-047 | HIGH | Money stored as `Double` | Entities | 33 `: Double` fields | R04-02, R08-09 | Verified failing |
| MR-048 | MEDIUM | Destructive fallback and dual-write without conflict rules | DB/repository | Release fallback not gated; Room/Firestore independent | R04-03 | Verified failing |
| MR-049 | MEDIUM | Unbounded/unindexed list reads | DAOs | `SELECT *` without `LIMIT`; unpaginated messages | R04-04, R13-06 | Verified failing |
| MR-050 | MEDIUM | Chat stored twice with timestamp-only ordering | Room/Firestore | Local + subcollection; no dedup key | R04-05 | Partially implemented |
| MR-051 | HIGH | Transcription fabricates output when unconfigured | Gemini service | Canned result on missing key (`:35–40`); client-side API key | R05-02 | Verified failing |
| MR-052 | MEDIUM | Integration calls use unmanaged coroutine scopes | AT client | `CoroutineScope(Dispatchers.IO).async` per call | R05-03, R13-05 | Verified failing |
| MR-053 | MEDIUM | No provider contract tests; Maps key is a placeholder | Integrations | No fixtures; `DEFAULT_MAPS_API_KEY` (`build.gradle.kts:28`) | R05-04 | Not implemented |
| MR-054 | HIGH | No `rememberSaveable`/`BackHandler`; transient state lost | Compose UI | 0 / 0; 133 `remember {` | R02-02, R12-05 | Verified failing |
| MR-055 | HIGH | 59 icon-only controls lack labels | UI semantics | 59 null of 176 `contentDescription`; 0 `semantics {}` | R12-01 | Verified failing |
| MR-056 | HIGH | Text below minimum size; no localisation | UI | 30×9sp, 86×10sp, 150×11sp; 0 `stringResource` | R12-02, R12-03, R02-05 | Verified failing |
| MR-057 | MEDIUM | Money formatting and error copy are inconsistent | UI formatting | `toInt()` truncation; raw `e.message` | R12-04 | Verified failing |
| MR-058 | MEDIUM | No UI test hooks or screenshot suite | Tests | 0 `testTag`; orphan PNG, no framework | R12-06, R11-05 | Not implemented |
| MR-059 | HIGH | No crash/ANR/analytics/perf telemetry | Observability | 0 telemetry dependencies | R13-01 | Not implemented |
| MR-060 | HIGH | Offline queue never replays | Offline | `flushPendingActions` logs only (`VM:2196–2215`); no WorkManager | R13-02, R02-03 | Verified failing |
| MR-061 | MEDIUM | Money status relies on polling; no push | Polling | `pollTrustedMoMoCollectionStatus`; 0 FCM send | R13-03 | Verified failing |
| MR-062 | MEDIUM | APK and assets oversized | Packaging | 31 MB APK; JPEGs 696/380/916 KB; R8 off | R13-04 | Verified failing |
| MR-063 | MEDIUM | No retry budget, circuit breaker or SLOs | Reliability | Default timeouts only; no DLQ | R13-07 | Not implemented |
| MR-064 | CRITICAL | Android test suite cannot execute (module does not compile) | Test suite | 355+1 tests; build failure MR-001 | R11-01 | Verified failing |
| MR-065 | HIGH | No coverage measurement or critical-path E2E | QA | 0 JaCoCo/Kover; 1 placeholder instrumentation test | R11-02 | Not implemented |
| MR-066 | MEDIUM | Device evidence is not reproducible | Evidence | `_device_test/` logs/APK; no dated commands | R11-06 | Partially implemented |
| MR-067 | HIGH | Historical "BUILD SUCCESSFUL" claims contradict source | Docs | 81 claims; 4 invalid Kotlin files; `tsc` exit 2 | R15-01 | Verified failing |
| MR-068 | HIGH | PRD marks 112/112 stories complete despite failing features | `prd.json` | All stories completed; readiness FAIL | R15-02 | Verified failing |
| MR-069 | MEDIUM | 67 host-absolute documentation links | Docs | `file:///d:/...` | R15-03 | Verified failing |
| MR-070 | MEDIUM | Only 6 requirement IDs; no trace matrix | Requirements | 6 unique IDs in 52 KB; no matrix | R15-04 | Not implemented |
| MR-071 | MEDIUM | README advertises unimplemented Airtel/admin capabilities | `README.md` | `:17`, `:104`; caveat at `:46` | R15-05 | Verified failing |
| MR-072 | MEDIUM | Overlapping sources of truth without precedence | Docs set | 7 overlapping master documents | R15-06 | Not implemented |
| MR-073 | LOW | `CALL_PHONE` requested where `ACTION_DIAL` suffices | Manifest | `AndroidManifest.xml:12` | R02-06 | Verified failing |

## 3. Severity distribution by domain

| Domain | CRITICAL | HIGH | MEDIUM | LOW |
|---|---|---|---|---|
| Build, architecture, DevOps | 2 | 4 | 3 | 0 |
| Security | 1 | 7 | 4 | 0 |
| Authentication | 1 | 2 | 1 | 0 |
| Payments | 3 | 3 | 2 | 0 |
| Safety | 2 | 2 | 3 | 0 |
| Privacy | 1 | 3 | 2 | 0 |
| Data | 0 | 2 | 3 | 0 |
| Integrations | 0 | 1 | 2 | 0 |
| Client/UI | 0 | 3 | 2 | 1 |
| Performance/reliability | 0 | 2 | 3 | 0 |
| QA | 1 | 1 | 1 | 0 |
| Documentation | 0 | 2 | 4 | 0 |

## 4. Corrections and disagreements recorded during execution

| # | Item | First-cycle claim | Re-verified result | Effect |
|---|---|---|---|---|
| D-01 | Function export count | R03 reported 17 | **18** `export const` in `index.ts` | Count corrected; verdict unchanged |
| D-02 | "99 × 5.sp" text | R12 reported 99 occurrences of 5.sp | **False positive** (substring of 10.5/11.5/12.5); no 5.sp sizes. Real: 30×9sp, 86×10sp, 150×11sp, 4×8sp | R12 finding restated; still HIGH |
| D-03 | Semantics modifiers | Prior cycle said 4 | **0** `semantics {}` blocks | Strengthens R12 |
| D-04 | `remember` count | Prior cycle said 221 | **133** `remember {`; `rememberSaveable` still 0 | No verdict change |
| D-05 | Oversized JPEGs | 372/680/896 KB | **380/696/916 KB** | R13 evidence updated |
| D-06 | App Check | "0 hits / absent" | Dependency **declared** (`firebase-appcheck-recaptcha`) but never initialized or enforced | R07 wording corrected; severity unchanged |
| D-07 | MoMo webhook | R03: "fails closed" (positive); R07: "unauthenticated" (negative) | Both true: integrity is protected by re-query, authentication is absent | Recorded as two perspectives, not a conflict |
| D-08 | Money core | R08: "strongest part"; R09/R10: "safety/privacy critical" | Different scopes (mechanics vs lifecycle) | No conflict |

## 5. Verified / not implemented / unverifiable

**Verified working (static or executed):** 111/111 Node tests; server-side collection amount/currency;
cashout ledger transaction and idempotency; callable ownership checks; admin callable gating; KYC
Storage owner-or-admin; fail-closed session restore; rating authorization; chat listener cleanup.

**Verified failing:** non-compiling Android source; `tsc` exit 2; public reads; duplicate SOS rules;
self-asserted Storage metadata; fake payout; PIN disclosure; deletion cascade; build claims.

**Not implemented:** CI, App Check enforcement, FCM send, refunds, release wiring, retention, DSAR,
rules/migration/coverage tests, offline replay, telemetry, admin provisioning, re-auth/revocation.

**Partially implemented:** attribution/consent records, guarantor dedup, message dual-store, device
evidence, NIN masking.

**Unable to verify (runtime only):** Android build/tests, rotation/process death, TalkBack/contrast,
real OTP/SMS/FCM delivery, MoMo sandbox and settlement, backup/restore, load/performance, admin claim
behaviour in a live project.

## 6. Recommended remediation order (not executed)

1. Restore a green build (Android + `tsc`) and add CI.
2. Close public reads, fix SOS rules, storage binding, forgeable collections and backups.
3. Make SOS truthful: real dispatch or honest failure.
4. Fix identity: OTP→Firebase credential, admin provisioning, re-auth.
5. Complete the money lifecycle: release, refund, server ledger, reconciliation.
6. Rewrite erasure; preserve complaints; correct the privacy notice.
7. Add rules/migration/E2E tests, telemetry, offline replay, accessibility and localisation.
8. Reconcile documentation and PRD status.

## 7. Programme conclusion

**73 consolidated issues (11 CRITICAL, 31 HIGH, 30 MEDIUM, 1 LOW).**
The complete review is **ready for consolidation into a remediation programme**, but the product is
**not ready for release** and no feature was approved as production-verified. Full coverage and
remaining checks: `REVIEW_COVERAGE_MATRIX.md`.
