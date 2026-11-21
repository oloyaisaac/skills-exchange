# IMPLEMENTATION ROADMAP — Skills Exchange Remediation

**Source:** 15 executed reviews (`review_results/`), 73 consolidated findings (`MR-001`…`MR-073`), baseline `8f1d43e` / code `6b39a10`.
**Created:** 2026-11-19 · **Status:** planning only — no application code changed.

## 1. How to read this plan

Every consolidated finding has exactly one task: `REM-0NN` implements `MR-0NN`. This 1:1 mapping means no finding can be lost or silently merged. Priorities come from the master register; dependencies are technical, not cosmetic. All tasks start at `NOT_STARTED`.

| Count | Value |
|---|---|
| Tasks | 73 |
| CRITICAL | 11 |
| HIGH | 31 |
| MEDIUM | 30 |
| LOW | 1 |
| Dependency waves | 5 |
| Tasks needing external credentials to verify | 9 |

## 2. Dependency waves

Work within a wave can run in parallel once the previous wave is merged. A task may appear in a later wave only because one dependency is unresolved.

| Wave | Tasks | Focus |
|---|---|---|
| 0 | REM-001, REM-002, REM-006, REM-010, REM-011, REM-016, REM-039, REM-044, REM-049, REM-051, REM-069, REM-070, REM-072 | Unblock builds, security exposure and independent policy fixes |
| 1 | REM-003, REM-004, REM-005, REM-007, REM-008, REM-012, REM-013, REM-014, REM-015, REM-020, REM-021, REM-022, REM-029, REM-034, REM-046, REM-052, REM-054, REM-055, REM-056, REM-064, REM-067, REM-073 | Backend money/auth foundations and rules hardening |
| 2 | REM-009, REM-017, REM-023, REM-024, REM-025, REM-026, REM-030, REM-036, REM-047, REM-048, REM-050, REM-053, REM-057, REM-058, REM-059, REM-062, REM-065, REM-066, REM-068 | End-to-end money, safety and identity flows |
| 3 | REM-018, REM-027, REM-028, REM-031, REM-033, REM-035, REM-038, REM-041, REM-063 | Clients, testing and operational controls |
| 4 | REM-019, REM-032, REM-037, REM-040, REM-042, REM-043, REM-045, REM-060, REM-061, REM-071 | Documentation and final hardening |

## 3. External-verification blockers

These tasks can be implemented now but cannot be *verified* without external resources; completion evidence must wait for the named environment. They are **not** marked BLOCKED because implementation is possible.

| Task | Needs |
|---|---|
| REM-026 | MTN MoMo sandbox + registered callback |
| REM-027 | Provider refund endpoint |
| REM-028 | Payout status endpoint |
| REM-032 | Provider settlement statement |
| REM-034 | Africa's Talking SMS credits + test handset |
| REM-035 | Firebase project with FCM and an on-call device |
| REM-023 | Firebase Admin credentials |
| REM-040 | Provisioned admin + on-call rotation |
| REM-053 | Google Maps key |

## 4. Tasks by reviewer

### R01 — Architecture & Codebase Integrity

Assignment: `reviewers/ARCHITECTURE_REVIEWER.md` (deleted after verification; preserved in `verification/reports/VERIFICATION_01.md` §9) · Report: `review_results/R01_ARCHITECTURE.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-001 | CRITICAL | Android source does not compile: four structurally invalid files (MR-001) | Four production files contain syntax errors, so no build or test can run | app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt; app/src/main/java/com/example/data/remote/FirebaseService.kt; app/src/main/java/com/example/data/remote/GeminiAudioTranscriptionService.kt; app/src/main/java/com/example/ui/screens/ActiveJobScreen.kt | Repair the four files, delete or rewrite ActiveJobScreen, and compile debug | - | `:app:compileDebugKotlin`; full JVM unit suite; lint | Compile exits 0; 355 unit tests execute; no syntax or unresolved-reference errors | CI log with compile, test and lint output at the fixing commit |
| REM-003 | HIGH | God classes, no DI, no navigation, no module boundaries (MR-003) | Repository, ViewModel and escrow sheet accumulated unrelated responsibilities | SkillsExchangeRepository.kt; SkillsExchangeViewModel.kt; EscrowAndProofSheet.kt; settings.gradle.kts | Extract feature modules, introduce DI and typed navigation, reduce class size | REM-001 | Module compile; per-module unit tests; architecture/import checks | No production class over 800 lines; modules build independently; navigation typed | Module graph and class-size report in CI |
| REM-004 | MEDIUM | Dead code, duplicate manifest, unused dependencies (MR-004) | Dead screen, duplicate root manifest and unused dependencies remained after refactors | ActiveJobScreen.kt; AndroidManifest.xml (root); app/build.gradle.kts | Delete dead screen, duplicate manifest and unused deps; keep one manifest | REM-001 | Dead-code/dependency scan; assemble debug | Scan reports zero dead files and unused deps; one manifest | Scan output and APK build |

### R02 — Android Client

Assignment: `reviewers/ANDROID_CLIENT_REVIEWER.md` (deleted after verification; preserved in `verification/reports/VERIFICATION_02.md` §9) · Report: `review_results/R02_ANDROID_CLIENT.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-054 | HIGH | No `rememberSaveable`/`BackHandler`; transient state lost (MR-054) | 0 `rememberSaveable`, 0 `BackHandler`, 29 sheets | ui/screens; ui/components | Use rememberSaveable/SavedStateHandle and BackHandler for sheets/navigation | REM-001 | Rotation and process-death tests; back-navigation test | State survives recreation; back closes sheets | Instrumentation log |
| REM-073 | LOW | `CALL_PHONE` requested where `ACTION_DIAL` suffices (MR-073) | SOS dialling can use ACTION_DIAL without a dangerous permission | AndroidManifest.xml; SOS UI | Use ACTION_DIAL and remove CALL_PHONE unless direct call is required | REM-001 | Manifest review; dial intent test | Permission removed; dialler opens correctly | Manifest diff and test |

### R03 — Backend & Cloud Functions

Assignment: `reviewers/BACKEND_REVIEWER.md` · Report: `review_results/R03_BACKEND.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-002 | CRITICAL | Cloud Functions build fails with 8 TypeScript errors (MR-002) | `processMoMoWebhook` uses DOM Request/Response instead of Cloud Functions types, so tsc exits 2 | functions/src/index.ts | Use `functions.https.Request/Response`, fail the build on error, and stop emitting on failure | - | `npm ci && npm run build`; `npm test` | Build exits 0; 111 tests pass; `lib/` produced only from a clean build | CI log with build and test output |
| REM-009 | MEDIUM | No predeploy, staging or rollback (MR-009) | firebase.json has no predeploy and there is no staging target | firebase.json; .firebaserc | Add predeploy build/test, staging project and rollback runbook | REM-002; REM-005 | Deploy dry run; staged deploy; rollback drill | Predeploy blocks a broken build; staging deploy succeeds; rollback documented | Deploy logs and runbook |
| REM-021 | MEDIUM | MoMo webhook is unauthenticated and unthrottled (MR-021) | The public webhook has no signature, timestamp or rate limit | functions/src/index.ts | Verify provider signature/allow-list, enforce timestamp window and throttle | REM-002; REM-011 | Forged callback rejected; replay rejected; valid callback accepted | Unsigned and stale callbacks rejected; valid callback still confirms | Test output and provider signature docs |

### R04 — Data & Persistence

Assignment: `reviewers/DATA_REVIEWER.md` · Report: `review_results/R04_DATA.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-046 | HIGH | `exportSchema=false` and no migration tests (MR-046) | `exportSchema=false` and 16 migrations have no tests | AppDatabase.kt; app/schemas/; app/src/androidTest | Enable schema export, commit JSON, add MigrationTestHelper per version | REM-001 | Migration tests from every version; schema diff check | Schema JSON committed; every migration path tested | CI migration test log |
| REM-047 | HIGH | Money stored as `Double` (MR-047) | 33 money fields use Double | Entities.kt; DAOs; repository; UI formatters | Migrate to Long minor units with a tested conversion migration | REM-046 | Migration test with monetary fixtures; arithmetic tests | No Double money fields; totals exact; migration preserves values | Migration test output |
| REM-048 | MEDIUM | Destructive fallback and dual-write without conflict rules (MR-048) | Release builds can destructively migrate; Room and Firestore have no conflict protocol | AppDatabase.kt; repository sync code | Gate destructive fallback to debug, add sync metadata and conflict resolution | REM-046 | Upgrade test; conflict-resolution tests | Release never destructive; conflicts resolved deterministically | Test output |
| REM-049 | MEDIUM | Unbounded/unindexed list reads (MR-049) | List queries use SELECT * without LIMIT and chat history is unpaginated | Daos.kt; repository; UI lists | Add paging, indices and query limits on growth paths | - | Query-plan check; load test with volume data | No unbounded list query on hot paths; p95 within budget | Query plans and load report |
| REM-050 | MEDIUM | Chat stored twice with timestamp-only ordering (MR-050) | Messages are stored twice with only timestamp for ordering | FirebaseService.kt; Daos.kt; Entities.kt | Add server sequence/ID dedup and reconciliation | REM-046 | Reconnect/ordering tests; duplicate-suppression test | No duplicates or reordering after reconnect | Test transcript |

### R05 — Integrations

Assignment: `reviewers/API_INTEGRATION_REVIEWER.md` · Report: `review_results/R05_INTEGRATIONS.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-033 | MEDIUM | Airtel and card offered without integration (MR-033) | Airtel and card are offered but have no integration | EscrowAndProofSheet.kt; README.md; requirements | Either integrate providers or remove them from UI, copy and docs | REM-026 | Channel selection E2E per supported provider; UI copy check | No unsupported channel selectable; docs list only working providers | Screenshot and provider config |
| REM-051 | HIGH | Transcription fabricates output when unconfigured (MR-051) | Missing Gemini key returns canned content and the key is called from the device | GeminiAudioTranscriptionService.kt; build config; functions | Return an explicit error and move the call server-side | - | Missing-key test shows error; key absent from APK | No fabricated output; key server-side | Test output and secret scan |
| REM-052 | MEDIUM | Integration calls use unmanaged coroutine scopes (MR-052) | Each AT call creates a `CoroutineScope(Dispatchers.IO).async` | AfricaTalkingService.kt; DI graph | Inject application scope and expose suspend functions | REM-001 | Cancellation test; leak check | No ad-hoc scopes; cancellation propagates | Static check and test |
| REM-053 | MEDIUM | No provider contract tests; Maps key is a placeholder (MR-053) | No contract tests; Maps uses DEFAULT_MAPS_API_KEY | app/build.gradle.kts; test suite; CI secrets | Add recorded-fixture contract tests and per-environment Maps key | REM-005 | Contract tests against sandbox; map render test | Contracts verified; map renders with injected key | CI log and screenshot |

### R06 — Authentication & Authorization

Assignment: `reviewers/AUTH_REVIEWER.md` · Report: `review_results/R06_AUTH.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-022 | CRITICAL | OTP creates a local session without a Firebase identity; signup cannot complete (MR-022) | `verifyOtp` mints a local session and `loginUser` needs a UID that was never created | SkillsExchangeViewModel.kt; FirebaseService.kt; functions/src/index.ts; OnboardingScreen.kt | Verify OTP in a callable that mints a custom token, or use Firebase phone auth; never set auth locally | REM-011; REM-002 | Clean-install signup test asserting `auth.currentUser` | User can register, sign in and restore a session with a real UID | Instrumented signup log and Firebase user record |
| REM-023 | HIGH | Admin claim unprovisionable; admin UI unlock is a local boolean (MR-023) | No `setCustomUserClaims` path exists and the UI unlock is a local boolean | functions/src/index.ts; MainScreen.kt; scripts/admin-claim.js (new); firestore.rules | Add an audited claim-provisioning script and server-backed UI gating | REM-022 | Admin/non-admin callable tests; UI gate test | Admin can load queues; non-admin denied; unlock requires server check | Provisioning log and test output |
| REM-024 | HIGH | No re-auth for destructive actions; no token revocation (MR-024) | Deletion and sensitive changes run on stale sessions; logout does not revoke tokens | SkillsExchangeViewModel.kt; FirebaseService.kt; SessionManager.kt | Require fresh auth for destructive actions; revoke refresh tokens on logout/deletion | REM-022 | Re-auth flow test; revocation test | Stale session cannot delete; revoked token rejected | Test transcript and token check |
| REM-025 | MEDIUM | Google and phone identities never link (MR-025) | The two sign-in paths create separate UIDs | FirebaseService.kt; SkillsExchangeViewModel.kt | Link credentials to one account with a tested merge policy | REM-022 | Link flow test; duplicate-identity check | One UID per person; histories merged or explicitly separated | E2E link log |

### R07 — Application Security

Assignment: `reviewers/SECURITY_REVIEWER.md` · Report: `review_results/R07_SECURITY.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-010 | CRITICAL | `jobs`, `messages`, `providers` are world-readable (MR-010) | `jobs`, `messages` and `providers` use `allow read: if true` | firestore.rules | Scope reads to participants/admin and gate sensitive fields | - | Rules emulator tests for owner, provider, admin and anonymous | Anonymous read denied; participant read allowed; field policy enforced | Rules test output |
| REM-011 | HIGH | Live-looking Africa's Talking key ships in the APK (MR-011) | The Africa's Talking key ships in the APK and OTP runs from the device | AfricaTalkingService.kt; SkillsExchangeViewModel.kt; functions/src/index.ts | Revoke the key, move OTP/SMS behind callables, load secrets from env/Secret Manager | - | Secret scan; callable OTP flow test; old-key rejection check | No secret in repo or APK; OTP works via callable; old key revoked | Secret-scan log and provider revocation confirmation |
| REM-012 | HIGH | Duplicate `sosIncidents` blocks nullify the stricter rule (MR-012) | Two match blocks for the same path make the weaker rule win | firestore.rules | Delete the legacy block and keep one stricter rule with job-existence check | REM-010 | Rules tests: missing job denied; legacy field not accepted | Single block; SOS for missing job denied; provider-only field rejected | Rules test output |
| REM-013 | HIGH | Notifications and MoMo transactions are client-forgeable (MR-013) | Any authenticated user can create notifications and momo_transactions | firestore.rules; functions/src/index.ts | Bind creates to server paths or deny client writes | REM-010 | Rules tests for forged payloads | Client create denied for both collections; server writes still succeed | Rules test output |
| REM-014 | HIGH | Storage photo writes trust self-asserted metadata (MR-014) | Job-photo writes authorize self-asserted custom metadata | storage.rules; FirebaseService.kt | Validate against Firestore job ownership and make uploads immutable/versioned | REM-010 | Storage emulator tests: cross-job write and overwrite denied | Only job participants can write; overwrite of existing evidence denied | Rules test output |
| REM-015 | HIGH | All quotes readable by every authenticated user (MR-015) | Every authenticated user can read all job quotes | firestore.rules | Restrict reads to job owner and the quoting worker | REM-010 | Rules tests for unrelated user and admin | Unrelated authenticated read denied; owner and author allowed | Rules test output |
| REM-016 | HIGH | Backups enabled with empty backup rules (MR-016) | Backups are enabled with empty include/exclude rules | AndroidManifest.xml; res/xml/backup_rules.xml; res/xml/data_extraction_rules.xml | Disable backup or exclude auth tokens, DataStore, DB and KYC caches | - | Backup/restore test; manifest inspection | Sensitive stores excluded; restore leaves no credentials | Backup XML diff and restore log |
| REM-017 | HIGH | No App Check enforcement or rate limits (MR-017) | App Check is declared but never initialised or enforced; callables are unthrottled | app/build.gradle.kts; FirebaseService.kt; functions/src/index.ts | Initialise App Check, set `enforceAppCheck`, add per-uid limits | REM-011; REM-022 | Unverified-client request rejected; throttle test | Unattested calls denied; limits trigger on abuse | Function logs and test output |
| REM-018 | MEDIUM | Abuse reports have no rules and never reach the backend (MR-018) | Reports are written only to Room and no rules exist for a reports collection | SkillsExchangeRepository.kt; firestore.rules; functions/src/index.ts | Add a reports collection with participant-bound create and admin-only read; build moderator queue | REM-010; REM-023 | Report-to-queue E2E; rules tests | Report visible to admin; reporter can create; others cannot read | E2E log and rules test output |
| REM-019 | MEDIUM | Zero rules tests (MR-019) | Zero rules tests exist, so regressions ship silently | app/src/test or firebase emulator test project; package.json | Add emulator-based rules suites covering every collection and actor | REM-010; REM-012; REM-013; REM-014; REM-015; REM-018 | Emulator suite in CI | Suite covers all collections; deliberate regression fails | CI log |
| REM-020 | MEDIUM | R8 disabled; no network security config or pinning (MR-020) | R8 is disabled and there is no network security policy | app/build.gradle.kts; res/xml/network_security_config.xml (new); AndroidManifest.xml | Enable R8, add network security config, consider pinning | REM-001 | Release build inspection; TLS policy test | R8 enabled; cleartext denied; policy documented | Release build report |

### R08 — Payments & Escrow

Assignment: `reviewers/PAYMENTS_REVIEWER.md` · Report: `review_results/R08_PAYMENTS.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-026 | CRITICAL | Escrow release never invoked from the client (MR-026) | `releaseEscrowToWorker` exists but has no client caller | SkillsExchangeRepository.kt; SkillsExchangeViewModel.kt; EscrowAndProofSheet.kt | Call the callable on completion and render server outcome | REM-002; REM-022 | Sandbox E2E complete → release → payout status; negative auth tests | Funds release after verified completion; failure surfaced honestly | Sandbox transaction references and logs |
| REM-027 | CRITICAL | No refund implementation exists (MR-027) | No refund function or callable exists | functions/src/index.ts; SkillsExchangeRepository.kt; firestore.rules | Implement idempotent refund callable and wire cancellation/dispute outcomes | REM-002; REM-026 | Sandbox refund E2E; idempotency and authorization tests | Cancelled/disputed job returns funds; duplicate refund blocked | Provider refund reference and ledger entry |
| REM-028 | CRITICAL | Device payout fabricates `DISBURSED` (MR-028) | `disburseEscrowPayoutToWorker` returns DISBURSED without a provider call | MoMoPaymentService.kt; SkillsExchangeViewModel.kt | Remove the stub and use the payout callable plus status polling | REM-026 | Payout flow test asserting provider reference | No synthetic success; UI reflects real provider status | Provider response captured in test |
| REM-029 | HIGH | Fee and total computed client-side (MR-029) | The 10% fee and grand total are computed on the client | SkillsExchangeRepository.kt; functions/src/index.ts | Compute fee/total server-side from stored quote and catalogue; reject mismatches | REM-002 | Tampered-client test; fee computation tests | Server rejects altered totals; fee identical across clients | Test output and stored quote diff |
| REM-030 | HIGH | Escrow status transitions are client-writable (MR-030) | Escrow status strings are written by the client and not pinned per actor | firestore.rules; functions/src/index.ts; SkillsExchangeRepository.kt | Move transitions into callables and enforce an allowed transition map | REM-029 | State-machine tests; direct-write denial tests | Only valid transitions succeed; client direct writes denied | Rules and callable test output |
| REM-031 | HIGH | Ledger is device-only with no server settlement (MR-031) | Cashout balance lives only in Room with no server settlement | functions/src/index.ts; Entities.kt; SkillsExchangeRepository.kt | Persist server ledger entries and settlement references; reconcile device cache | REM-026 | Reinstall test; balance parity test | Server balance matches device; reinstall preserves payable balance | Ledger report and parity test |
| REM-032 | MEDIUM | No reconciliation or stuck-escrow alerting (MR-032) | No reconciliation job, report or alert exists | functions/src/index.ts; ops runbook | Add daily provider reconciliation and alerts on stuck escrow | REM-031 | Reconciliation job test with fixture statement; alert firing test | Daily totals match provider; mismatch and stuck escrow alert | Reconciliation report and alert log |

### R09 — Privacy, KYC & Compliance

Assignment: `reviewers/PRIVACY_COMPLIANCE_REVIEWER.md` · Report: `review_results/R09_PRIVACY.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-037 | HIGH | Account deletion removes complaints made against the user (MR-037) | Deletion removes disputes where the user is the reported party | FirebaseService.kt; SkillsExchangeViewModel.kt; firestore.rules | Retain disputes and anonymise only where policy allows | REM-041 | Deletion test asserts complaint survives; rules test | Complaints remain after deletion; no personal data exposed | Deletion audit log |
| REM-041 | CRITICAL | Erasure over-deletes cached jobs, aborts, and ignores Storage (MR-041) | Deletion enumerates every cached job, aborts on rules denial and never deletes Storage | SkillsExchangeViewModel.kt; FirebaseService.kt; functions/src/index.ts; storage.rules | Implement one re-authenticated server job over owned data, including Storage, with atomic reporting | REM-022; REM-024 | Per-store deletion tests; partial-failure retry test; other-user job test | All owned stores cleared; other users unaffected; failures reported | Deletion audit report and test output |
| REM-042 | HIGH | Privacy notice contains unimplemented claims (MR-042) | Notice claims encryption, coarse-only location, full export and 24/7 arbitration that are not implemented | AccountComplianceDialog.kt; README.md; privacy policy doc | Rewrite to match implemented behaviour and disclose processors/transfers | REM-041; REM-044 | Copy-to-code review; legal sign-off checklist | Every claim maps to implemented behaviour or is removed | Signed copy matrix |
| REM-043 | HIGH | No retention or purge schedule (MR-043) | No retention schedule or purge job exists | functions/src/index.ts; policy doc; Firestore rules | Define retention periods and an automated purge job with audit | REM-041 | Age-based purge test; legal-hold exclusion test | Expired data purged; holds respected; audit recorded | Purge job log |
| REM-044 | MEDIUM | Consent, processor disclosure and DSAR workflow missing (MR-044) | Consent is one boolean and there is no processor list or access-request process | Entities.kt; AccountComplianceDialog.kt; docs/privacy | Record consent version/time, publish processors, define DSAR SLA and export | - | Consent audit test; DSAR dry run | Consent demonstrable; DSAR fulfilled within SLA; processors listed | Consent records and DSAR log |
| REM-045 | MEDIUM | NIN over-collected and unmasked at rest (MR-045) | Full NIN is stored and the masking helper is not applied to storage | Entities.kt; admin UI; FirebaseService.kt | Collect only what is needed, mask at rest/in UI, restrict field access | REM-041 | Field-access audit; masking test | NIN masked in UI/logs; access limited to authorised roles | Audit output and screenshot |

### R10 — Safety, Trust & Fraud

Assignment: `reviewers/SAFETY_FRAUD_REVIEWER.md` · Report: `review_results/R10_SAFETY.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-034 | CRITICAL | SOS dispatch is a log-only stub that returns `SENT` (MR-034) | `dispatchEmergencySosAlert` logs and returns SENT without sending | SmsNotificationService.kt; SkillsExchangeRepository.kt; functions/src/index.ts | Send via a server-side provider with delivery result; never fabricate success | REM-002; REM-011 | Real delivery to test handset; failure-path test | Alert received with provider ID; failure is reported to the user | Delivery receipt and callable log |
| REM-035 | CRITICAL | No server FCM/escalation; SOS silent when no profile resolves (MR-035) | No `admin.messaging` send exists and SOS is silent when no profile resolves | functions/src/index.ts; NotificationRouting.kt | Add FCM to on-call plus fallback SMS and explicit per-channel results | REM-034; REM-023 | On-call device receives SOS; profile-missing path test | Push and fallback sent; unauthenticated raise still escalates or fails loudly | FCM delivery log and test transcript |
| REM-036 | HIGH | Doorstep PIN disclosed to worker; fixed default; no lockout (MR-036) | PIN defaults to 4829, is shown to the worker and has no attempt limit | Entities.kt; EscrowAndProofSheet.kt; SkillsExchangeRepository.kt; functions/src/index.ts | Generate per job server-side, show only to resident, verify server-side with throttling | REM-022 | PIN lifecycle tests; attempt-limit test; UI disclosure test | Worker cannot read PIN; wrong attempts locked; server verifies | Test output and screenshot |
| REM-038 | MEDIUM | No SOS throttle, dedup or admin audit trail (MR-038) | No rate limit, duplicate suppression or admin action log | firestore.rules; functions/src/index.ts; admin UI | Throttle raises, suppress duplicates, append immutable admin audit entries | REM-034; REM-023 | Abuse/dedup tests; audit-log inspection | Duplicate SOS suppressed; admin actions recorded immutably | Rules test and audit sample |
| REM-039 | MEDIUM | Guarantor/identity controls weak against collusion (MR-039) | No cross-worker reuse detection; NIN validation is format-only | GuarantorValidators.kt; functions/src/index.ts; Entities.kt | Detect reused guarantors, add stronger identity verification and liveness where feasible | - | Collusion fixture test; NIN validation tests | Reuse flagged; invalid NIN rejected; review queue shows risk flags | Test fixtures and queue output |
| REM-040 | MEDIUM | Admin/SOS escalation unreachable in practice (MR-040) | Escalation depends on a claim that cannot be provisioned | MainScreen.kt; functions/src/index.ts | Depends on claim provisioning and on-call routing | REM-023; REM-035 | Admin login receives SOS end-to-end | Named on-call receives and acknowledges SOS | E2E escalation log |

### R11 — QA & Test Engineering

Assignment: `reviewers/QA_REVIEWER.md` · Report: `review_results/R11_QA.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-064 | CRITICAL | Android test suite cannot execute (module does not compile) (MR-064) | 355 unit tests cannot run because the module does not compile | app/src/test; app/src/androidTest; CI | Fix build then run full JVM and instrumentation suites in CI | REM-001 | Full suite run; failure detection | 355 tests execute; a deliberate regression fails | CI test report |
| REM-065 | HIGH | No coverage measurement or critical-path E2E (MR-065) | No coverage tool and no E2E for signup/pay/rate/SOS | app/build.gradle.kts; app/src/androidTest; CI | Add JaCoCo/Kover, coverage floor and E2E journeys | REM-064 | Coverage report; E2E suite | Coverage measured; critical journeys pass end-to-end | Coverage and E2E reports |
| REM-066 | MEDIUM | Device evidence is not reproducible (MR-066) | Device logs, APK and SQLite are committed without commands | docs/evidence/; TASKS.md | Adopt dated evidence logs with commands, commit and environment | REM-005 | Evidence audit | Every verification claim links to a dated log | Evidence index |

### R12 — UI/UX & Accessibility

Assignment: `reviewers/UI_UX_ACCESSIBILITY_REVIEWER.md` · Report: `review_results/R12_UI_UX_A11Y.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-055 | HIGH | 59 icon-only controls lack labels (MR-055) | 59 content descriptions are null and there are no semantics blocks | ui/components; ui/screens | Add contentDescription/semantics to every icon-only action, especially SOS | REM-001 | TalkBack traversal; accessibility scanner | All controls have labels; scanner has no critical issues | Scanner report and recording |
| REM-056 | HIGH | Text below minimum size; no localisation (MR-056) | Text runs 8–11sp and all copy is inline English | ui/theme; ui/screens; res/values/strings.xml | Raise body text to ≥12sp, externalise strings, add Luganda locale | REM-001 | Font-scale test; lint HardcodedText; locale switch | Minimum sizes met at 200%; zero hardcoded UI strings; locale works | Screenshots and lint log |
| REM-057 | MEDIUM | Money formatting and error copy are inconsistent (MR-057) | Payout truncates with toInt and raw exception messages reach users | ui/components; util formatters | Central formatters for money and mapped, actionable error copy | REM-056 | Formatter unit tests; error-copy review | Amounts grouped and exact; no raw exceptions in UI | Unit test output and screenshots |
| REM-058 | MEDIUM | No UI test hooks or screenshot suite (MR-058) | 0 testTag and one orphan screenshot | ui/screens; ui/components; app/src/test | Add testTags and a screenshot test suite for critical screens | REM-001; REM-005 | UI tests in CI; screenshot baseline | Critical controls selectable; visual diffs gated | CI log |

### R13 — Performance & Reliability

Assignment: `reviewers/PERFORMANCE_RELIABILITY_REVIEWER.md` · Report: `review_results/R13_PERFORMANCE.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-059 | HIGH | No crash/ANR/analytics/perf telemetry (MR-059) | No telemetry SDK is present | app/build.gradle.kts; Application class; CI config | Add Crashlytics/Sentry, non-PII analytics and perf traces with alerts | REM-001; REM-007 | Crash and ANR visible in dashboard; event schema review | Field crashes and ANRs visible within minutes; alerts configured | Dashboard link and event schema |
| REM-060 | HIGH | Offline queue never replays (MR-060) | Queued actions are logged and never executed; no WorkManager | SkillsExchangeViewModel.kt; repository; app/build.gradle.kts | Drain queue with WorkManager, idempotency keys and backoff | REM-001; REM-031 | Airplane-mode E2E; duplicate-delivery test | Queued action syncs once on reconnect; failures retry with backoff | E2E log and queue metrics |
| REM-061 | MEDIUM | Money status relies on polling; no push (MR-061) | Collection status is polled and no FCM send exists | SkillsExchangeViewModel.kt; functions/src/index.ts | Send push on status change and keep bounded polling as fallback | REM-035 | Latency and battery measurement | Status arrives via push; polling bounded | Push log and measurement |
| REM-062 | MEDIUM | APK and assets oversized (MR-062) | 31 MB APK, three large JPEGs and R8 disabled | res/drawable; app/build.gradle.kts | Enable R8, convert/downscale assets, add size budget | REM-020 | APK size check in CI | APK under 15 MB; assets under 150 KB each | Size report |
| REM-063 | MEDIUM | No retry budget, circuit breaker or SLOs (MR-063) | Provider calls rely on defaults and no SLO or alerting exists | SkillsExchangeViewModel.kt; functions/src/index.ts; ops runbook | Add bounded retry/backoff, breaker, DLQ and SLO definitions | REM-059 | Failure-injection tests; alert firing | Outage degrades gracefully; SLO alert fires | Test log and alert config |

### R14 — DevOps & Release Engineering

Assignment: `reviewers/DEVOPS_REVIEWER.md` · Report: `review_results/R14_DEVOPS.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-005 | HIGH | No CI pipeline of any kind (MR-005) | No automated build, test or security gate exists | .github/workflows/ci.yml (new); gradle files; functions/package.json | Add CI for Android build/tests, functions build/tests, rules tests and secret scan | REM-001; REM-002 | Pipeline run on PR and main; deliberate failing commit blocked | Required checks green on a clean clone; red build blocks merge | CI run URL and branch-protection settings |
| REM-006 | HIGH | Build config is machine-local and inconsistent (MR-006) | Absolute D:/ paths, Java 11/25 mismatch and non-executable wrapper tie the build to one machine | gradle.properties; gradlew; app/build.gradle.kts; build.gradle.kts | Remove absolute paths, pin one JDK, restore wrapper execute bit, use toolchains | - | Clean Linux clone build; wrapper version check | Build succeeds on Linux CI with no D:/ references | CI log on a clean runner and grep for D:/ |
| REM-007 | HIGH | No release signing/hardening; 62 MB artifacts committed (MR-007) | R8 disabled, keystore absent, and large build artefacts are committed | app/build.gradle.kts; .gitignore; repository root | Enable R8/shrinking, externalise signing, remove APK/SQLite/log artefacts | REM-001; REM-006 | Signed release build; APK inspection | Signed minified AAB/APK produced by CI; no build artefacts tracked | Signing config record (no secrets) and artefact hash |
| REM-008 | MEDIUM | 16 production advisories; Node 18 vs modern test tooling (MR-008) | 16 production advisories and a Node 18 engine against modern test tooling | functions/package.json; functions/package-lock.json; app/build.gradle.kts | Upgrade firebase-admin/functions, pin supported Node LTS, add audit gate | REM-002 | `npm audit --omit=dev`; dependency review; full test suite | Zero critical/high advisories; supported runtime; tests pass | Audit report and lockfile diff |

### R15 — Documentation & Traceability

Assignment: `reviewers/DOCUMENTATION_REVIEWER.md` · Report: `review_results/R15_DOCUMENTATION.md`

| Task | Priority | Finding | Root cause | Affected files | Required correction | Depends on | Required tests | Acceptance criteria | Completion evidence |
|---|---|---|---|---|---|---|---|---|---|
| REM-067 | HIGH | Historical "BUILD SUCCESSFUL" claims contradict source (MR-067) | 81 BUILD SUCCESSFUL entries contradict the source | AGENT_STATE.md; CHANGELOG.md | Annotate or remove unsupported claims; link to CI evidence | REM-001; REM-002 | Claim audit against CI logs | No unsupported success claim remains | Audit report |
| REM-068 | HIGH | PRD marks 112/112 stories complete despite failing features (MR-068) | 112/112 stories are completed despite failing features | prd.json; TASKS.md | Re-status stories with evidence links; keep history | REM-067 | Story-by-story audit | Every completed story has evidence or is re-statused | Audit matrix |
| REM-069 | MEDIUM | 67 host-absolute documentation links (MR-069) | 67 host-absolute file:///d:/ links | All Markdown | Convert to repo-relative links or remove | - | Link checker | Zero broken or host-absolute links | Link-check log |
| REM-070 | MEDIUM | Only 6 requirement IDs; no trace matrix (MR-070) | Only 6 requirement IDs exist and no requirement→code→test map | requirement_file.md; docs/traceability.md | Assign IDs and generate a trace matrix | - | Matrix coverage check | Every feature maps to a requirement, code and test | Matrix report |
| REM-071 | MEDIUM | README advertises unimplemented Airtel/admin capabilities (MR-071) | README advertises Airtel and an admin dashboard that do not exist | README.md | Correct feature list and status; keep caveats consistent | REM-033; REM-023 | Doc-to-code review | No advertised capability without implementation | Reviewed README diff |
| REM-072 | MEDIUM | Overlapping sources of truth without precedence (MR-072) | Seven master documents overlap without precedence | README.md; AGENTS.md; DECISIONS.md; AGENT_STATE.md; TASKS.md; prd.json; requirement_file.md | Publish one precedence rule per document type | - | Documentation map review | Each document has a stated role and owner | Docs map section |

## 5. Definition of done for the programme

1. All 11 CRITICAL and 31 HIGH tasks are closed with evidence at a named commit.
2. Every gate in `PRODUCTION_READINESS.md` is re-scored by its owner with reproducible evidence.
3. Rules, migration, Android and E2E suites run in CI and fail on regression.
4. `prd.json` and `TASKS.md` statuses match evidence, not assertion.
5. A fresh readiness review is performed; this roadmap does not itself approve release.

## 6. Validation record

| Check | Method | Result |
|---|---|---|
| Every finding is evidence-backed | Each `MR` in the master register cites `path:line` or an executed command | 73/73 |
| Original reviewer preserved | `Raised by` in master; reviewer column here | 73/73 |
| Duplicates consolidated without loss | 94 raw issues → 73 master issues; each local ID mapped | 94/94 mapped |
| Different perspectives preserved | Notes record R03/R07 webhook and R08/R09 money disagreements | 8 corrections/perspectives recorded |
| Every finding assigned | 1:1 `REM` ↔ `MR` | 73/73 |
| Dependencies valid | All dependency IDs exist; DFS cycle check | 0 missing, 0 cycles |
| Priority consistent with severity | Task priority compared to master severity | 73/73 match |
| No task marked complete | Status scan | 73 `NOT_STARTED` |

## 7. Priority queue and recommended order

Execute by dependency wave; inside a wave, CRITICAL first, then HIGH, MEDIUM, LOW. The waves in §2 are the recommended order; this list is the priority view.

| Priority | Tasks (dependency wave in brackets) |
|---|---|
| CRITICAL | REM-001 [0], REM-002 [0], REM-010 [0], REM-022 [1], REM-034 [1], REM-064 [1], REM-026 [2], REM-027 [3], REM-028 [3], REM-035 [3], REM-041 [3] |
| HIGH | REM-006 [0], REM-011 [0], REM-016 [0], REM-051 [0], REM-003 [1], REM-005 [1], REM-007 [1], REM-012 [1], REM-013 [1], REM-014 [1], REM-015 [1], REM-029 [1], REM-046 [1], REM-054 [1], REM-055 [1], REM-056 [1], REM-067 [1], REM-017 [2], REM-023 [2], REM-024 [2], REM-030 [2], REM-036 [2], REM-047 [2], REM-059 [2], REM-065 [2], REM-068 [2], REM-031 [3], REM-037 [4], REM-042 [4], REM-043 [4], REM-060 [4] |
| MEDIUM | REM-004, REM-008, REM-009, REM-018, REM-019, REM-020, REM-021, REM-025, REM-032, REM-033, REM-038, REM-039, REM-040, REM-044, REM-045, REM-048, REM-049, REM-050, REM-052, REM-053, REM-057, REM-058, REM-061, REM-062, REM-063, REM-066, REM-069, REM-070, REM-071, REM-072 |
| LOW | REM-073 |

**First merge candidates (wave 0, CRITICAL/HIGH):** REM-001, REM-002, REM-010, REM-011. They unblock compilation, backend build, the largest data exposure and credential abuse respectively.

## 8. Change control

- This plan was produced without modifying application source, rules, build files or tests.

- A task moves `NOT_STARTED → IN_PROGRESS → VERIFICATION_PENDING → COMPLETED` only with the evidence named in its row. `BLOCKED` requires a recorded external dependency.

- Acceptance criteria may not be weakened to fit an implementation; a scope change requires a new decision record in `DECISIONS.md`.

- Any change to a finding requires a re-review of the owning report, not an edit to this plan alone.


## 9. Findings raised by multiple reviewers

The plan assigns each finding one primary owner so work is not duplicated, but the other reviewers’
perspectives remain attached to the finding. These tasks were raised independently by more than
one reviewer; the owning task must satisfy all listed perspectives.

| Task | Finding | Primary owner | Also raised by | Reviewer-local IDs |
|---|---|---|---|---|
| REM-009 | MR-009 | R03 | R14 | R03-02, R14-05 |
| REM-011 | MR-011 | R07 | R05 | R07-11, R05-01 |
| REM-016 | MR-016 | R07 | R09 | R07-06, R09-07 |
| REM-017 | MR-017 | R07 | R03 | R07-07, R03-03 |
| REM-018 | MR-018 | R07 | R10 | R07-08, R10-04 |
| REM-019 | MR-019 | R07 | R11 | R07-09, R11-03 |
| REM-022 | MR-022 | R06 | R02 | R06-01, R02-01 |
| REM-032 | MR-032 | R08 | R03 | R08-07, R03-06 |
| REM-033 | MR-033 | R05 | R08 | R05-05, R08-08 |
| REM-035 | MR-035 | R10 | R03 | R10-02, R03-04 |
| REM-036 | MR-036 | R10 | R02 | R10-03, R02-04 |
| REM-037 | MR-037 | R09 | R10 | R09-02, R10-05 |
| REM-046 | MR-046 | R04 | R11 | R04-01, R11-04 |
| REM-047 | MR-047 | R04 | R08 | R04-02, R08-09 |
| REM-049 | MR-049 | R04 | R13 | R04-04, R13-06 |
| REM-052 | MR-052 | R05 | R13 | R05-03, R13-05 |
| REM-054 | MR-054 | R02 | R12 | R02-02, R12-05 |
| REM-056 | MR-056 | R12 | R02 | R12-02, R12-03, R02-05 |
| REM-058 | MR-058 | R12 | R11 | R12-06, R11-05 |
| REM-060 | MR-060 | R13 | R02 | R13-02, R02-03 |

**Shared findings:** 20 of 73. All other findings have a single reporter.

Where reviewers reached different conclusions, the master register records them in §4 (corrections and disagreements); this table does not average them away. For example, R03 treats the webhook as integrity-safe because it re-queries the provider, while R07 treats it as unauthenticated; both are true and both requirements are in REM-021.
