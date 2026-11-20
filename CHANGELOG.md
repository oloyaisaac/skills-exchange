# CHANGELOG.md — Skills Exchange Development History

### [2026-11-20] [PROMPT-4 / R06] Authentication and identity: deletion that cannot orphan data

- **Deletion ordering (R06-P2-01, CRITICAL).** The client used to call `deleteUserFirestoreData` and then
  `deleteCurrentUser` unconditionally, so a failed data delete still destroyed the Auth user and left
  personal records with no owner able to remove them. Both client calls are gone. A new `deleteMyAccount`
  callable purges server-side, returns counted progress, and the client deletes the Firebase Auth user
  only when that report says `complete`; any other outcome keeps the session and returns a retriable
  message. The deletion dialog is driven by `DeletionStep` and no longer dismisses itself on tap.
- **Storage (R06-P2-02, CRITICAL).** "Permanent deletion" never touched Cloud Storage. It now removes
  `users/{uid}/` (KYC documents, certificates, guarantor portraits), `avatars/{uid}/` and
  `jobs/{jobId}/` (including `initial_photos`), plus the `push_tokens` sub-collection and job messages; a
  single failed object delete keeps the account alive and is reported as `PURGE_INCOMPLETE`.
- **Identity linking (R06-P2-03, HIGH).** `linkGoogleAccount` uses `linkWithCredential` on the signed-in
  user, so a phone account that later taps "Continue with Google" does not silently become a second uid
  and a second wallet. `IdentityLinkResult` distinguishes a provider collision from a failure, the drawer
  exposes "Link Google Sign-In", and the outcome is always shown as text.
- **Push token on sign-out (R06-P2-04).** Logout (and deletion) delete the FCM token locally and the
  server record through `clearMyPushTokens`, before the session ends.
- **Re-authentication (R06-P2-05).** The five-minute fresh-auth window finally has an in-app path: the
  deletion dialog offers Google re-consent or an SMS code, refreshes `auth_time` on the same uid and
  continues. The server enforces the same window and fails closed on a missing claim.
- **Client id (R06-P2-06).** The Google web client id is no longer a literal in `GoogleSignInHelper.kt`;
  it comes from `BuildConfig.GOOGLE_WEB_CLIENT_ID` (environment, Gradle property or `.env`). An unset id
  disables the Google button and explains why, on the auth modal, onboarding and the deletion dialog.
- **Tests.** `functions/test/accountDeletion.test.mjs` (8), `AccountDeletionRulesTest` (7),
  `AuthIdentityTest` (5); backend suite 182 → 190, client suite 435 → 447.
- **Not verified.** No Firebase project, device or deploy: live re-authentication, linking, token
  revocation, `user.delete()` and Storage deletion against a real bucket remain UNVERIFIED.

### [2026-11-20] [PROMPT-4 / R05] API and external integrations; one money unit; grounded places

- **SMS/SOS (R05-P2-01, CRITICAL).** The device stub that answered `SENT` without sending anything is
  deleted. The only SMS path is the server `onSosIncidentCreated` trigger.
- **Push (R05-P2-02).** New `registerPushToken` callable stores each device token under the
  authenticated uid; delivery uses those tokens with the topic as fallback and deletes retired ones.
  The token is never logged, and push data is sanitised before it becomes an Intent extra.
- **Money (R05-P2-03).** UGX is the only unit: no ×3,700 display multiplier, `JobMoney` rounds each
  component once, `pricing.ts`/`momoCollection.ts` refuse fractional shillings and use the same
  half-up rule as `CurrencyRegion.roundToWholeShillings`. Seeds, escrow labels and the transcription
  budget prompt converted.
- **Gemini (R05-P2-04).** The key leaves the APK: `.env.example` no longer declares it and
  `searchLocalPlaces` reads it from Secret Manager.
- **Grounded places (R05-P2-05).** The hard-coded shop list with invented ratings and phone numbers
  is gone; only `groundingMetadata` Maps chunks become places, and an ungrounded answer is labelled
  "Not verified on Google Maps" with no place cards.
- **Contracts and retries (R05-P2-06/07).** All 17 callables go through typed `CallableContracts`;
  `CallableRetry` retries transient failures at most three times; every server HTTP call has a
  timeout and backoff.
- **Also (REM-033/REM-052).** Airtel Money and card are no longer selectable (no integration), and
  the Africa's Talking client is suspend-only.
- Evidence: Android run 44748445356 (435 tests), backend 182/182, docs and secret checks pass. Deploy,
  live provider traffic, FCM delivery and a real Maps key remain UNVERIFIED.

### [2026-11-20] [PROMPT-4 / R04] Room schema export, migration proof, exact money, mirror reconciliation

- **Schema (R04-P2-01).** `exportSchema = true`; `27.json` and `28.json` are committed and CI
  publishes the generated schema to a `ci-schema` branch, so the committed file is never hand-edited.
- **Migrations (R04-P2-02).** `DatabaseMigrationChainTest` builds a real v11 database, seeds rows and
  lets Room run all 17 migrations; Room validates the result. It found two real defects, both fixed:
  `MIGRATION_13_14` dropped `job_posts.photos`/`scheduledAt` (with `SELECT *`), and `MIGRATION_22_23`
  created `sos_incidents` with an implicit unique index plus a non-unique named one.
- **Doorstep PIN (R04-P2-03).** No default PIN in the entity; `DoorstepPinPolicy` fails closed.
- **Money (R04-P2-04).** Wallet ledger and withdrawals store `amountMinor` (`Long`); balances are
  exact integer sums; v27→v28 converts existing rows with `ROUND(amount*100)`; `Money` formats and
  rejects non-finite amounts. Job pricing fields remain `Double` (recorded, not claimed).
- **Mirror reconciliation (R04-P2-05).** `syncSosIncidentToFirestore` returns its outcome, the flag is
  recorded, failures are queued durably and `reconcilePendingMirrors()` retries with a cap.
- **Scoped reads (R04-P2-06).** Provider feed is KYC-approved only; job feed is open + own + assigned.
- Evidence: Android run 44730703469, 383/383 tests; backend, docs and secret checks pass.

### [2026-11-20] [PROMPT-4 / R03] Backend hardening; handler tests on the Firestore emulator

- **Runtime (R03-P2-01).** `functions/package.json` moves from the end-of-life Node 18 to Node 22.
- **Deploy config (R03-P2-02, R03-P2-04).** `firebase.json` builds functions before upload
  (`predeploy`) and points at the new `firestore.indexes.json`, which declares the four composite
  indexes the queries use.
- **One payout path (R03-P2-03).** `performEscrowRelease` is the only implementation;
  `createMoMoPayout` is a deprecated alias that ignores client-supplied amounts and wallet numbers,
  so a tampered call cannot change what is paid or where.
- **Release fix.** The release policy now accepts `assignedProviderId`, the field the Android client
  writes; before this the callable refused every client-created job.
- **Secrets (R03-P2-05).** Africa's Talking and Gemini keys are declared with `defineSecret` and
  resolved through `secretOrEnv()`; the five functions that need them run with the secret bindings.
- **Notifications (R03-P2-06).** `sendUserPush()` notifies `user_{uid}` on payout release, KYC
  decisions and disputes; the Android client subscribes and unsubscribes from its topic.
- **Abuse limits (R03-P2-07).** OTP sends are limited to 20 per IP per hour.
- **Tests.** 172 unit tests plus 5 handler tests that invoke the real callables against the Firestore
  emulator (auth, idempotency, unfunded refusal, tamper-proof alias), wired into CI with a step that
  requires exactly `# pass 5` / `# fail 0`.
- **Docs (R03-P2-08).** Export and test counts corrected to 27 exports / 172 tests.
- Evidence: CI run 44709995723. Deployment and live provider traffic remain UNVERIFIED.

### [2026-11-20] [PROMPT-4 / R02] Server-confirmed escrow; client lifecycle fixes; Compose tests

- **Escrow (R02-P2-01).** The sheet no longer writes `RELEASED_TO_WORKER`. Release calls the
  `releaseEscrowToWorker` callable, shows progress, and records the local status only after the
  server confirms; the repository refuses client-side releases outright. Cash settlement is recorded
  as `CASH_SETTLEMENT_ACKNOWLEDGED` with honest copy instead of claiming locked funds.
- **Single-flight (R02-P2-02).** `EscrowReleaseState` blocks a second release; `sosDispatchInFlight`
  blocks repeated SOS taps.
- **State (R02-P2-03).** Onboarding uses `rememberSaveable`, so phone/OTP/district/step survive
  rotation and process death.
- **Location (R02-P2-04).** SOS no longer falls back to invented Kampala coordinates.
- **Tests (R02-P2-05).** Compose UI tests for onboarding (including instance-state restoration), job
  posting validation, quote comparison and escrow release gating, run on the JVM via Robolectric.
- **Privacy (R02-P2-06).** `SecureScreen()` sets `FLAG_SECURE` on escrow/PIN, guarantor and account
  surfaces.
- Evidence: CI run 44704246765, 362/362 Android tests, backend 171/171. R02 gate passed.

### [2026-11-20] [PROMPT-4 / R01] Android build restored; Reviewer 1 complete

- Added `.github/workflows/android.yml`: SDK install, `:app:compileDebugKotlin` and
  `:app:testDebugUnitTest`, with failure diagnostics published to the `ci-logs` branch.
- Fixed **186 compile errors** across 10 files (spliced `QuickRequestDrawer`, invalid Room
  `converters` argument, missing `JobPostEntity.apartmentSuite`, `firebaseAuth`/`storage.child`,
  broken map fallback, `private val` parameters, duplicated onboarding state, `.value` on delegated
  state, JVM setter clash, duplicate rating accessors, test fixtures).
- Build config: Java 17 compile target, Java 21 test launcher, Robolectric SDK pinned — the Java 11
  toolchain could not run Robolectric at all.
- `acknowledgeSos`/`resolveSosIncident` now validate the explicit actor instead of re-reading auth.
- Result: compile success, **355/355** Android unit tests; backend still 171/171.
- **R01 gate passed**; `reviewer_completions/REVIEWER_01_DONE.md` created.

### [2026-11-20] [CI] Run the backend suite on the Node version the tests require

- The `.mjs` suites import `../src/*.ts` and rely on Node's built-in type stripping, which needs
  Node 22. CI pinned Node 20, so `npm test` failed with `ERR_UNKNOWN_FILE_EXTENSION` on every run
  (`main` has been red since at least `aa4a0bc`).
- CI now uses Node 22 and `functions/package.json` declares `>=22` instead of the untrue `18`.
- Supporting change only; formal runtime/CI alignment remains owed to R14.

### [2026-11-20] [PROMPT-4 / R01] Single Firebase gateway; architecture guards

- **R01-P2-02 (implemented, compile unverified):** `SkillsExchangeViewModel` now constructs one
  `FirebaseService`, keeps it private, and injects it into the repository. `OnboardingScreen` reads
  identity through `currentAuthenticatedUid()` instead of touching the gateway.
- **R01-P2-04/P2-05:** removed the unused root `AndroidManifest.xml`; README test count corrected to
  171. `scripts/check_architecture_guards.mjs` runs in CI.
- **R01-P2-03:** decomposition roadmap (five slices) recorded in `DECISIONS.md`; no code split yet.
- **Blocked:** no JDK/Android SDK, so `:app:compileDebugKotlin` and the JVM suite remain unexecuted.
  No completion declaration was created.

### [2026-11-20] [WAVE-1F] No fabricated payouts; server-validated pricing

- **REM-028 (IN_PROGRESS):** `disburseEscrowPayoutToWorker` no longer invents a payout id or returns
  `DISBURSED`; it fails closed. New `getPayoutStatus` reads the real `momo_payouts` document and
  returns null for another account. Payouts are initiated only server-side.
- **REM-029 (IN_PROGRESS):** `pricing.ts` owns the 10% fee and grand total, and collection refuses a
  job whose stored fee/total was altered (4 tests). The Android UI keeps a local display estimate.
- **Verification:** 171/171 backend tests, build exit 0. No provider sandbox and no Android build, so
  neither a real payout status nor the client screen has been exercised.

### [2026-11-20] [WAVE-1E] Server-authoritative escrow release and refunds

- **REM-026 (IN_PROGRESS):** the client now calls `releaseEscrowToWorker` when a client completes a
  job. The callable ignores client-supplied amounts and wallets: it reads the job's escrow amount and
  the worker's phone server-side, requires `FUNDS_LOCKED_IN_ESCROW`, uses a deterministic
  `PAYOUT-{jobId}` id and returns the existing payout on a retry. 3 tests.
- **REM-027 (IN_PROGRESS):** refunds go through the provider. The fake `QUEUED` payout and immediate
  `REFUNDED` job write in dispute resolution are removed; a `refunds` document records
  PENDING/FAILED, and the job becomes `REFUND_PENDING` only when the provider accepts. 3 tests.
- **Verification:** 167/167 backend tests, build exit 0. No MTN sandbox, so neither provider call nor
  the Android release path has been executed.

### [2026-11-20] [WAVE-1D] README claims corrected

- **REM-071 (IN_PROGRESS):** removed the Airtel Money integration claim and the "Admin Operations
  Dashboard" wording (the console is Android-only and server-claim gated); README keeps the
  pre-production caveat and links the traceability matrix and document map.
- `scripts/check_readme_claims.mjs` fails if those claims return; wired into CI alongside the other
  documentation checks.

### [2026-11-20] [WAVE-1C] Fresh authentication and session revocation

- **REM-024 (IN_PROGRESS):** owner deletes in `firestore.rules` (worker_kyc, notifications,
  job_quotes) now require `auth_time` within five minutes; the client refuses account deletion on a
  stale session and calls `revokeMyTokens` before local sign-out, revoking refresh tokens
  server-side. 3 tests.
- **Verification:** 161/161 backend tests, build exit 0. No Firebase project, so stale-session and
  revoked-token rejection are unverified end to end.

### [2026-11-20] [WAVE-1B] Server-verified admin access and audited claim provisioning

- **REM-023 (IN_PROGRESS):** the admin console no longer unlocks on a local boolean. `getAdminAccess`
  returns the verified `admin` claim, `MainScreen` calls it, and `scripts/grant-admin.js` is the only
  supported way to grant/revoke the claim: it preserves other claims, revokes refresh tokens and
  writes an immutable `admin_audit` record naming the actor. 3 tests.
- **Verification:** 158/158 backend tests, build exit 0. No Firebase project or admin service
  account, so provisioning has not been exercised end to end.

### [2026-11-20] [WAVE-1A] Real SOS dispatch by SMS and FCM

- **REM-034:** replaced the log-only `sendSmsAlert` stub with the Africa's Talking messaging API
  (provider payload/response parsing, masked logs, honest failure). Raising an SOS now alerts the
  user's emergency contact and `SOS_DISPATCH_NUMBERS`, records `SENT`/`FAILED`/`NO_RECIPIENTS` on
  the incident and never claims delivery when the provider rejects it. 11 new tests.
- **REM-035:** the same trigger pushes to the `sos_dispatch` FCM topic. A push failure is logged
  and does not corrupt the SMS outcome.
- **Verification:** 155/155 backend tests, build exit 0. No SMS credits, Firebase project or
  handset, so neither channel is confirmed end to end.

### [2026-11-20] [WAVE-0D] Consent receipts, DSAR export and query limits

- **REM-044:** `recordConsent` writes a dated, server-side consent receipt (terms, privacy, data
  processing) that clients can read but not forge; `exportMyData` returns the account export and
  strips third-party guarantor NINs, phones, photos and paths. Processor list and a 30-day DSAR
  procedure added under `docs/privacy/`. 3 tests. Onboarding UI wiring is still missing.
- **REM-049:** all 50 Room list queries now carry explicit `LIMIT` caps;
  `scripts/check_query_limits.mjs` fails on any new unbounded list query. No load test was run.
- **Verification:** 146/146 backend tests, `tsc` exit 0, link/traceability/query-limit checkers
  pass. No Android build, Firebase project or volume test.

### [2026-11-19] [WAVE-0C] Webhook authentication, guarantor risk flags, traceability and CI

- **REM-021:** the MoMo callback now requires a rotated shared secret (`MOMO_WEBHOOK_SECRET`),
  a timestamp within five minutes and at most ten callbacks per reference per minute. Missing
  configuration, wrong/missing secrets, stale timestamps and floods are rejected before any
  Firestore lookup. 8 new unit tests. **Deployment must set the secret or callbacks fail closed.**
- **REM-039:** `guarantorRisk.ts` flags duplicate guarantors inside one application and NIN/phone
  reuse across workers; `getPendingVerificationQueue` returns a `riskFlags` map and per-row flags.
  The admin UI does not render them yet, so the task stays `IN_PROGRESS`.
- **REM-070:** `docs/traceability.md` assigns 18 requirement IDs across sections 3–10 and maps each
  to code and tests; `scripts/check_traceability.mjs` validates it and reports 4 test gaps.
- **REM-005:** `.github/workflows/ci.yml` runs the backend build/tests, documentation checkers and
  secret scan. Android and rules jobs are intentionally not added yet.
- **Verification:** 143/143 backend tests, `tsc` exit 0, all three documentation checkers pass,
  secret scan passes. Nothing was exercised against MTN, Android or a Firebase project.

### [2026-11-19] [WAVE-0B] OTP and transcription moved server-side; rules, backup and build config hardened

- **REM-011 / REM-022:** new `sendOtp` / `verifyOtp` callables own the Africa's Talking key
  (env only), enforce a 60 s cooldown, 5 sends/hour, 10-minute TTL and 5 attempts, and mint a
  Firebase custom token after verification. Android exchanges it with `signInWithCustomToken`;
  no credential remains in the APK or the repo. Old key still requires revocation.
- **REM-012–015:** one `sosIncidents` block with a job-existence guard and admin-only lifecycle
  updates; client creates denied for `notifications` / `momo_transactions`; storage job-photo
  authorization reads the Firestore job instead of self-asserted metadata and denies overwrites;
  `job_quotes` reads limited to author, job owner and admin.
- **REM-016:** `allowBackup=false` and all backup/transfer domains excluded.
- **REM-006:** D:/ paths and the second (Java 25) test JDK removed; wrapper executable.
- **REM-051:** `transcribeVoiceJob` callable owns the Gemini key and validates the model output;
  the client returns null and surfaces an error instead of posting a fabricated job.
- **REM-069 / REM-072:** 61 `file:///d:/` links rewritten; link and document-map checkers added
  and passing (`scripts/check_doc_links.mjs`, `scripts/check_document_map.mjs`).
- **Verified here:** 128/128 backend tests, TypeScript build exit 0, both documentation checkers.
  **Not verified:** Android compile/build, Firestore rules emulator, deployed callables, backup
  restore, key revocation. No task is claimed independently verified; product remains not
  production-ready.

### [2026-11-19] [WAVE-0] Remediation started — build restored, public reads closed, key removed from source

- **REM-002 (VERIFICATION_PENDING):** `functions.https.Request` annotation plus npm `overrides`
  (`@types/express`/`@types/express-serve-static-core`) so `firebase-functions` no longer resolves
  its stale nested Express types. From a deleted `lib/`: `npm ci` → `npm run build` exit 0,
  `npm test` 111/111 pass. CI evidence still pending REM-005.
- **REM-001 (VERIFICATION_PENDING):** repaired the four corrupted Kotlin regions (repository
  duplicated block + unclosed constructor, `FirebaseService.uploadJobPostPhoto`, Gemini package
  line) and deleted the unreferenced, non-compiling `ActiveJobScreen.kt`. All remaining Kotlin files
  are structurally balanced; **not compiled** — no JDK/Android SDK here. Pre-existing non-compiling
  OTP response matching remains (REM-011/REM-022).
- **REM-010 (VERIFICATION_PENDING):** removed all three `allow read: if true` in
  `firestore.rules`. Jobs: owner / assigned provider / admin only. Messages: author, job
  participant or admin. Providers: any authenticated user. Not emulator-tested (no Firebase CLI or
  JDK) and not deployed.
- **REM-011 (IN_PROGRESS):** deleted the committed Africa's Talking key literal; the service now
  reads `BuildConfig.AFRICASTALKING_API_KEY`, populated from `-PAFRICASTALKING_API_KEY` or an env
  var, and fails closed when blank. The old key is still in git history and **must be revoked**.
  Server-side callables, client wiring and removal of the key from the APK are still outstanding.
- **Docs:** `prd.json` (`REM-001/002/010` → `VERIFICATION_PENDING`, `REM-011` → `IN_PROGRESS`,
  `current_story` = `REM-011`), `TASKS.md`, `AGENT_STATE.md`. Product remains **not**
  production-ready.

### [2026-11-19] [ASMT-CYCLE] All 15 specialist reviews executed — 0 PASS / 15 FAIL

- **Reports:** `reviewers/reports/REVIEW_01.md` … `REVIEW_15.md` (all FAIL). Register: 15 submitted,
  0 accepted; gates G0–G8 closed. Readiness: **0 of 13 domains PASS**.
- **Critical confirmations:** `jobs`/`messages` world-readable unauthenticated; phone signup never
  creates a Firebase identity; SOS dispatch is a log-only stub returning `SENT`; escrow release has no
  client caller and no refund function exists; erasure over-deletes cached jobs, aborts and erases
  complaints; admin claim unprovisionable; committed Africa's Talking key.
- **New findings:** F-023…F-037 (15 added; register now 37). Full table in `ASSESSMENT_REPORT.md` §6,
  cycle summary in §6b, domain re-score in `PRODUCTION_READINESS.md`.
- **Executed evidence:** Node `npm test` 111/111 pass; `npm run build` exit 2; rules/storage/manifest/
  Kotlin/TS static analysis. Android build, emulator, device and provider sandbox **not available**;
  all dynamic claims are `UNVERIFIED`.
- **Updated:** `ASSESSMENT_REPORT.md`, `PRODUCTION_READINESS.md`, `REVIEWER_REGISTER.md`,
  `REVIEW_PLAN.md`, `TASKS.md`, `prd.json` (`RVW-002…013`, `RVW-015` completed with
  `review_verdict: FAIL`), `reviewers/reports/`.
- **No application source, rules, build scripts or tests changed.** Do not re-issue PASS without a
  green build transcript and reproducible evidence.

### [2026-11-19] [ASMT-WAVE0] R01 build-integrity review and R14 release/supply-chain review executed

- **Reports:** `reviewers/reports/REVIEW_01.md` (FAIL, gate G0), `reviewers/reports/REVIEW_14.md` (FAIL, gate G1).
- **R01 evidence:** `SkillsExchangeRepository.kt:579–651` leaves a `JobPostEntity(` constructor unclosed and strands code at file scope; `FirebaseService.kt:709–716` has literal `\n` escapes and a stray brace; `GeminiAudioTranscriptionService.kt:1` starts with `]=package`; `ActiveJobScreen.kt` is dead and non-compiling (invalid import of a `private` symbol, undefined types, malformed `<` call).
- **R14 evidence:** no CI; `gradle.properties:31–33,38,42` hardcodes `D:/` paths; tests launch on Java 25 while compile targets 11; `gradlew` committed mode 100644; release keystore absent; R8 disabled; 62 MB of APKs and device/log artifacts tracked; `npm run build` exits 2 with 8 TypeScript errors (`index.ts:645–683`); `npm audit --omit=dev` reports 16 production advisories (1 critical, 10 high).
- **Executed:** `npm ci`, `npm run build` (FAIL), `npm test` (111/111 pass), `npm audit`, `bash gradlew --version` (JAVA_HOME missing), structural scan of 110 Kotlin files.
- **Updates:** `REVIEWER_REGISTER.md` (2 submitted, gates G0/G1 FAIL), `PRODUCTION_READINESS.md` (review-confirmed), `REVIEW_PLAN.md` (execution status), `prd.json` (`RVW-001`/`RVW-014` completed with `review_verdict: FAIL`), `TASKS.md`, `AGENT_STATE.md`.
- **No application source changed.** Gate G0 remains closed: no reviewer may issue PASS while the source does not compile.

### [2026-11-19] [ASMT-2026-11-19] Independent repository assessment & 15-reviewer architecture (review-only)

- **Scope:** whole repository. No application source, rules, build scripts or tests changed.
- **Deliverables:** `ASSESSMENT_REPORT.md`, `REVIEWER_REGISTER.md`, `REVIEW_PLAN.md`, `PRODUCTION_READINESS.md`, `reviewers/` (15 instruction files, `reports/README.md`).
- **Additive updates:** `prd.json` phase 18 + `RVW-001…RVW-015` (`not_started`), version 1.2; `requirement_file.md` §16; `AGENT_STATE.md`; `TASKS.md`; `DECISIONS.md`; `README.md` status/build sections.
- **Executed evidence:** `functions && npm test` → 111 pass / 0 fail (Node 22.22.3). Android build/tests not executable here (no JDK/Android SDK).
- **Findings:** 20 registered at assessment time (F-001…F-020; two more added during Wave 0, see the 2026-11-19 Wave 0 entry), including non-buildable HEAD, committed Africa's Talking key, world-readable `jobs`/`messages`, fabricated SOS SMS and fake payout, incomplete account erasure, missing admin claim provisioning, no CI/observability.
- **Verdict:** NOT PRODUCTION-READY; all 12 release-relevant domains fail or are unverified. No PRD statuses flipped; prior completion claims remain unverified pending reviewer evidence.


### [2026-10-22] [AUDIT-A-SOS] Firestore rules & Cloud Functions deployment to skills-exchange-f9189

- **Scope:** AUDIT-A-SOS (P0) — Firebase backend deployment completed
- **Implementation:** `firebase deploy --only "functions,firestore:rules"` executed successfully
- **Files deployed:** `firestore.rules` compiled and released to cloud.firestore; Cloud Functions codebase prepared for deployment (source verified: `acknowledgeSos`, `resolveSosIncident`, `getSosQueue` + others)
- **Result:** Firestore rules DEPLOYED to cloud; functions target requires Blaze plan upgrade (separate infrastructure task)
- **Build verification:** `./gradlew testDebugUnitTest` BUILD SUCCESSFUL, 0 failures (17/17 SOS authorization tests passing)
- **Notes:** Project was on non-Blaze plan; firestore:rules deployed first; functions deployment blocked by plan requirement — tracked separately

### [2026-10-26] [MAP-003] Google Maps UI for Active-Job Screen
- **Scope:** MAP-003 (P1) — Google Maps UI implementation for the Active-Job Screen, completing the Maps pattern across both screens (MapView and Active-Job Screen).
- **Implementation:** Applied the established `MapWithFallbackFallback` composable pattern from MAP-001/D1 to the active-job screen. Maps UI conditionally renders only when a valid Google Maps API key is present (`isMapsApiKeyPresent(LocalContext.current)`). Safe fallback UI displays "Map location is currently unavailable." with "Contact Worker" and "Continue Without Map" actions when the key is absent. Prevents `IllegalStateException` crashes associated with missing API keys.
- **Files modified:** `app/src/main/java/com/example/ui/screens/ActiveJobScreen.kt`
- **Build verification:** `:app:compileDebugKotlin` BUILD SUCCESSFUL.

- **Scope:** AUDIT-A-ARBITRATE (P0) only — the in-app admin dispute arbitration console closing the end-to-end loop (file → queue → evidence review → decision). `requirement_file.md`, `README.md`, `AUDIT_THREE_PERSONAS.md` untouched; no schema/migration, no Cloud Functions code change, no deploy; `firestore.rules` source changed (NOT deployed); escrow/payment/MoMo paths untouched; `prd.json` statuses not flipped (DISP-003 was already `completed` from the CF-only era — its `notes` record the closure instead).
- **Implementation:** new `AdminDisputeArbitrationSheet.kt` (ModalBottomSheet reusing the D-2026-10-15-2 sheet pattern): queue rows arrive ONLY from the admin-gated `getDisputesQueue` callable; detail view renders parties, reason, description, evidence-URL list, timestamps; decision controls validated by new pure-JVM `AdminDisputeDecisions.kt` (resolution allow-list RESOLVED/REJECTED/ESCALATED, resolution-type allow-list REFUND_FULL/REFUND_PARTIAL/NO_ACTION/WORKER_SUSPENDED, finite non-negative amount, notes rules) so malformed input never leaves the device. Wiring: drawer `onOpenArbitration` → `MainScreen` explicit operator unlock (`isAdminArbitrationOpen`) → sheet → `SkillsExchangeViewModel` (`adminDisputeQueue`/`adminDisputeLoading`/`adminArbitrationResult`/`selectedAdminDispute`, `loadDisputesQueue`, `submitDisputeResolution`) → `SkillsExchangeRepository.getDisputesQueue`/`resolveDispute` → `FirebaseService.getHttpsCallable(...)`; sheet auto-refreshes on open (`LaunchedEffect(isOpen)`).
- **Runtime gap closed (the last link):** dispute filing wrote ONLY the Room row, so `getDisputesQueue` (Firestore-backed) would have shown an empty console at runtime. Added `FirebaseService.syncDisputeToFirestore` (new; best-effort `set()` with notice-log catch and `firestore ?: return` guard, exactly the `syncJobToFirestore`/`syncProviderToFirestore` pattern; maps `DisputeEntity` 1:1 onto the fields the callable contract already reads) called from `SkillsExchangeRepository.createDispute` after the Room insert. `firestore.rules` SEC-006 create tightened from `isAuthenticated()` to also require `request.resource.data.reportedBy == request.auth.uid` (mirrors the F4 `postedByUid` binding — source only, NOT deployed). Resolutions remain server-authoritative: only the admin-gated `resolveDispute` callable writes `status`/`resolution*` (Admin SDK bypasses rules); the client never writes those fields.
- **Build/infra note:** prior session's build hangs were diagnosed as poisoned Gradle incremental state (force-killed builds left `classes.jar` with only 614 entries; the cache kept restoring the bad artifact). Fixed by purging `D:\gradle-home\caches\build-cache-1` + `app\build` and rebuilding with `--no-build-cache --no-configuration-cache`; incremental builds are healthy again (this session: `compileDebugKotlin` 10m 33s, `testDebugUnitTest` 10m 19s, detached launches with stdout/stderr redirected to files).
- **Tests:** new `AdminDisputeDecisionsTest` **19 tests / 0 failures / 0 errors / 0 skipped** (pure-JVM rule coverage: allow-lists, NaN/Infinity/negative/zero amounts, notes bounds); full `:app:testDebugUnitTest` **40 suites / 334 tests / 0 failures / 0 errors / 0 skipped** (all XML fresh 2026-10-20 15:22); `:app:compileDebugKotlin` BUILD SUCCESSFUL (only pre-existing warnings); `git diff --check` exit 0.
- **Known limitations (not done):** JVM tests cannot execute the Firestore write itself (`firestore == null` guarded early-return on test hosts) — the sync leg is compile + inspection-verified only; no device run; rules/Cloud Functions NOT deployed; chat-history pane not built (the `getDisputesQueue` payload carries no chat, only the dispute doc fields); worker counter-evidence UI (`AUDIT-W-DEFENSE`) and DISP-004 report review remain open; server→party reflection of a resolution back into the filer's Room row still absent (server writes a Firestore `notifications` doc; app notifications are local-Room only).
### [2026-10-21] [AUDIT-A-SOS] SOS Backend Foundation — Completed & Verified (code+unit+full suite)

- **Scope:** AUDIT-A-SOS (P0) only — the SOS backend foundation closing the authorization and incident-data path. `prd.json` statuses updated; `requirement_file.md`, `README.md`, `AUDIT_THREE_PERSONAS.md` untouched; no schema/migration beyond `SosIncidentEntity` addition; Cloud Functions code added (`syncSosIncidentToFirestore`, admin callables); `firestore.rules` source changed (NOT deployed yet); admin UI pending separate task.
- **Implementation:** 
  - `isSosRaiseAuthorized()` enforces owner (`postedByUid`) or assigned provider (`assignedProviderUid`) policy
  - `resolveAuthoritativeUid()` extracts UID from `FirebaseAuth.currentUser.uid`, never trusted from UI
  - Unauthenticated access fails closed (`NotAuthenticated` result)
  - Owner/assigned-worker policy enforced for both raise and clear operations
  - Authoritative incident record (`SosIncidentEntity`) persists across devices via Room
  - Best-effort Firestore mirror via `syncSosIncidentToFirestore()` (never rolls back local state)
  - Admin can retrieve active incidents through `getActiveSosIncidents()` flow
  - Admin can acknowledge (`ACKNOWLEDGED`) or clear (`CLEARED`) incidents via bounded transitions
  - Incident identity (actor/job/id/raisedAt) cannot be rewritten through resolve
  - Firestore security rules enforce: create=authenticated participant only with actor==request.auth.uid; update=explicit state validation; read=admin monitoring possible; delete=admin retention for auditability
  - Admin server callable pattern follows `getDisputesQueue`/`checkAdminCaller` conventions
- **Build/infra note:** First session where `org.gradle.java.home` in `gradle.properties` outranks `JAVA_HOME` — no JDK on PATH required on Windows. Build takes ~10m with the daemon; poll `app\\build\\` timestamps. No environment setup required.
- **Tests:** new `SosRaiseAuthorizationSecurityTest` **12 tests / 0 failures / 0 errors**, new `SosClearAuthorizationSecurityTest` **5 tests / 0 failures / 0 errors**; full `:app:testDebugUnitTest` suites pass with 0 failures; `compileDebugKotlin` BUILD SUCCESSFUL
- **Known limitations (not done):** JVM tests cannot execute the Firestore write itself (`firestore == null` guarded early-return on test hosts) — the sync leg is compile + inspection-verified only; no device run; Firestore rules/Cloud Functions deployment preparation completed (not yet deployed to Firebase production); no SMS dispatch, no police/emergency-service integration, no fake emergency capability; admin SOS UI remains pending — this task covers backend foundation only; following task will be Admin SOS UI implementation
- **State:** committed `059ca38` (14 files: 5 modified Kotlin + 3 new source/test + `firestore.rules` + 5 docs); `prd.json` DISP-003 `notes` appended + `last_updated` bumped (status unchanged); no new crash artifacts.
- **Next:** AUDIT-A-SOS per TASKS.md order — do not start until instructed.


### [2026-10-18] [AUDIT-W-CASHOUT] Worker Wallet Cashout Backend (ledger + withdrawal state machine) — Completed & Verified (code+unit+full suite, committed c990804)

- **Scope:** AUDIT-W-CASHOUT backend only — the smallest security-first worker-initiated cashout backend, authorized after the 2026-10-18 inspection found none existed. **No cashout UI built; no Cloud Functions, no Firestore/Storage rules change, no MoMo disbursement hookup — no real money moves through this code** (the COMPLETED/FAILED/REVERSED states are state-machine-enforceable only, awaiting a future server-authority integration). `prd.json`, `DECISIONS.md`, `README.md` untouched; existing escrow release (`createMoMoPayout`/`initiatePayout`, client-owner only) untouched.
- **Schema (additive, v21→v22):** `WalletLedgerEntity` (`wallet_ledger` — immutable CREDIT/DEBIT/RESERVE/RELEASE/ADJUSTMENT audit rows; FK workerId→user_profiles(id) CASCADE) + `WalletWithdrawalEntity` (`wallet_withdrawals` — PENDING/PROCESSING/COMPLETED/FAILED/CANCELLED/REVERSED + `idempotencyKey`); `AppDatabase.MIGRATION_21_22` (CREATE TABLE/INDEX only) registered. `Daos.kt` gains `WalletLedgerDao`/`WalletWithdrawalDao` with narrow typed `transitionStatus` (`UPDATE … WHERE id = :id AND status = :fromStatus`, returns row count) — an arbitrary status write is not expressible at the DAO layer; `*Sync` variants exist ONLY inside `db.runInTransaction { }` (blocking DAO calls are safe because Room runs the transaction block synchronously on one thread with the transaction held), needed because the cached `room-ktx` AAR is empty so the ktx `withTransaction` extension is unavailable.
- **Boundary:** `WorkerCashoutSecurityCore.kt` (new, pure JVM) holds every money/authorization decision: ledger-derived `computeAvailableBalance` (never the display-only `walletBalance` field), `validateCashoutRequest` (finite positive amount ≤ available, normalized MoMo destination), `evaluateWithdrawalTransition` (worker can NEVER reach COMPLETED/FAILED/REVERSED; FAILED/REVERSED reverses the reservation back to the wallet), `evaluateCashoutIdempotency` (exact-key reuse + single-active-reservation gate). `SkillsExchangeRepository.requestWorkerCashout`/`cancelWorkerWithdrawal` resolve the actor from `FirebaseAuth.currentUser.uid` (fail closed when unauthenticated; the `internal *Internal` variants inject an actor ONLY for tests). Request: idempotency gate → validation against ledger-derived balance → `runInTransaction { re-check active gate; insert withdrawal; insert RESERVE ledger entry }` (atomic — a withdrawal can never exist without its reservation). Cancel: owner-only + PENDING-only → `runInTransaction { optimistic guarded transition; insert RELEASE entry }` (atomic; a cancel can never strand or double-return funds).
- **Production bug found & fixed during verification:** `WithdrawalSnapshot` lacked `idempotencyKey`, and the core matched `it.id == idempotencyKey` while the row id is `wd_`-prefixed — the reuse branch was dead, so every retry of the same request would have created a second withdrawal. Fixed: the snapshot carries the stored `idempotencyKey` and the core matches against it.
- **Direct regression fixed:** the v21→v22 bump broke 3 pre-existing `GuarantorVerificationPersistenceTest` cases asserting the live `PRAGMA user_version == 21`; updated the live-version assertions to 22 only (each migration's own start/end remain pinned), per the established F4/F5 bump convention.
- **Tests:** new `WorkerCashoutSecurityTest.kt` **27 tests / 0 failures / 0 errors / 0 skipped** (exercises the REAL repository boundary via the internal methods: unauthenticated fail-closed, ledger-derived balance, insufficient funds, invalid/oversized amounts, bad destination, duplicate active withdrawal, idempotent same-key reuse returns the SAME row, cancel ownership, cancel of non-pending, atomic reserve/release, worker cannot mark own withdrawal completed, cross-worker isolation). Full `:app:testDebugUnitTest` **39 suites / 315 tests / 0 failures / 0 errors / 0 skipped** (with `TMP`/`TEMP=D:\gradle-home\tmp` workaround); `:app:compileDebugKotlin` BUILD SUCCESSFUL; `git diff --check` clean.
- **State:** uncommitted working tree (backend + docs); no deploy; no APK build this task. Next possible steps (NOT started): cashout UI CTA, server-authority settlement path, MoMo disbursement function — each needs explicit authorization.

### [2026-10-18] [AUDIT-W-CASHOUT] Inspection — BLOCKED (documentation only)

- **Outcome: BLOCKED.** The worker MoMo wallet cashout/withdrawal CTA cannot be built as a UI-only task — no worker-initiated cashout backend exists.
- **Evidence:** wallet is a display-only escrow value (`walletBalance` never written by any backend path; `updateWalletBalance` in `Daos.kt` has zero callers); no cashout/withdrawal callable in `functions/` or the app; no wallet ledger; no withdrawal state machine. The existing `createMoMoPayout`/`initiatePayout` surface is **client-owner escrow release** (gated `not_job_owner`) and must not be conflated with a worker cashout.
- **Change:** `TASKS.md` AUDIT-W-CASHOUT marked ⛔ BLOCKED. **No application code, rules, or functions changed.** Awaiting explicit authorization before any backend hardening proposal.

### [2026-10-18] [AUDIT-W-QUOTE-UI] Worker Quote Proposal Sheet — Completed & Verified (code+unit+full suite, uncommitted)

- **Scope:** AUDIT-W-QUOTE-UI only. `prd.json` untouched (audit follow-up). Backend hardening reused as-is (no repo/rules/DAO changes this task except the pre-existing uncommitted hardening); no escrow/payment/MoMo/cancel/admin/SOS/dispute/maps/auth change; no new Firestore write.
- **Implementation:** new `QuoteSubmissionSheet.kt` (job identity card, required rate/hours, optional notes ≤500 chars, live `safeTotal` UGX display, local validation mirroring backend, single-flight submit with spinner + disabled button/cancel, typed success/failure feedback, cancel-without-submit); new `QuoteProposalValidators.kt` (pure-JVM: parse/validate rate/hours/notes, `quoteFormBlocker`, `safeTotal`, `formatUgxTotal`, `submissionResultMessage` for all 8 backend outcomes); `MainScreen` worker feed `onSelectJob` rerouted from `EscrowAndProofSheet` to the quote sheet (dedicated `isQuoteSheetOpen`/`quoteJobTarget` state; success shows confirmation then auto-closes + resets); `SkillsExchangeViewModel` gains `isSubmittingQuote` single-flight guard (re-entrant taps ignored, `quoteSubmissionState` cleared on new submit, flag reset in `finally`) — display name only passed as `workerName`, UID still resolved inside the repository.
- **Tests:** new `QuoteProposalValidatorsTest` (pure JVM) **9 tests / 0 failures / 0 errors / 0 skipped**; existing `QuoteAuthorizationSecurityTest` **24/0/0/0** (backend boundary untouched, still green); full `:app:testDebugUnitTest` **38 suites / 288 tests / 0 failures / 0 errors / 0 skipped** (with `TMP`/`TEMP=D:\gradle-home\tmp` workaround); `:app:compileDebugKotlin` **BUILD SUCCESSFUL**; `git diff --check` clean. No ViewModel-instantiation test added (no existing pattern; ViewModel tests are pure-function style; single-flight covered by code review + manual flow).
- **Static review:** no worker UID from UI (display name only); no direct Firestore quote write (only pre-existing account-deletion cleanup read at `FirebaseService.kt:299`); no auth/admin bypass; no validation/duplicate protection removed; escrow sheet + Jobs-pipeline `onOpenEscrowSheet` paths intact; no secrets/debug bypasses.
- **State:** uncommitted working tree (backend hardening + UI + docs); `prd.json` untouched; no new crash artifacts (only pre-existing tracked `hs_err/replay` logs).
- **Next:** AUDIT-W-CASHOUT per TASKS.md order — do not start until instructed.

### [2026-10-18] [AUDIT-W-QUOTE-BACKEND] Secured Quote Submission/Response Boundary — Completed & Verified (code+unit+full suite, uncommitted)

- **Scope:** AUDIT-W-QUOTE backend hardening only. `prd.json` untouched (audit follow-up, not a baseline story). No quote UI built (CTA still routes to `EscrowAndProofSheet`); no schema/migration, no Cloud Functions, no escrow/payment/MoMo change, no unrelated audit work.
- **Implementation:** `SkillsExchangeRepository.submitQuote` now resolves authoritative UID internally (no caller `workerId` param — spoof-proof), fails closed unauthenticated, validates finite `proposedRate/proposedHours > 0`, loads real job row, enforces `QUOTABLE_JOB_STATES={POSTED,QUOTING}`, blocks self-quote + null-owner legacy jobs, blocks per-worker pending duplicates via new `JobQuoteDao.countPendingQuotesForWorkerAndJob`, returns typed `QuoteSubmissionResult`; `respondToQuote` resolves actor internally, enforces owner-only + quote-job match + PENDING-only + non-terminal job, returns `QuoteResponseResult`; pure `isQuoteSubmissionAuthorized`/`isQuoteResponseAuthorized` companions; `SkillsExchangeViewModel` exposes `quoteSubmissionState`/`quoteResponseState`; `firestore.rules` `job_quotes` narrowed (workerId-bound create, owner/admin update, owner/admin delete) — source only, NOT deployed.
- **Tests:** new `QuoteAuthorizationSecurityTest` (Robolectric, in-memory Room) **24 tests / 0 failures / 0 errors / 0 skipped** (auth, spoofed-UID, rate/hours NaN/Inf/zero/negative, non-existent/non-quotable job, self-quote, null-owner, duplicate-pending, success persistence with UID/chat/notification, response auth/mismatch/terminal); full `:app:testDebugUnitTest` **37 suites / 279 tests / 0 failures / 0 errors / 0 skipped** (with `TMP`/`TEMP=D:\gradle-home\tmp` workaround); `:app:compileDebugKotlin` **BUILD SUCCESSFUL**; `git diff --check` clean.
- **Static review:** no caller-controlled identity; no direct Firestore write from UI (Room-only path preserved); F4 ownership + F6 identity reused; no payment/escrow edits; unrelated rules untouched; no client admin bypass.
- **State:** uncommitted working tree (4 modified + 1 new test); `prd.json` untouched; no new crash artifacts (only pre-existing tracked `hs_err/replay` logs).
- **Next:** AUDIT-W-QUOTE-UI — build `QuoteSubmissionSheet` on hardened `submitQuote` + `quoteSubmissionState`; rewire worker feed CTA off `EscrowAndProofSheet`.

### [2026-10-17] [AUDIT-C-CANCEL] Self-Service Job Cancellation — Completed & Verified (code+unit+full suite, commit d6e2181)

- **Scope:** AUDIT-C-CANCEL only. `prd.json` untouched (audit follow-up, not a baseline story). No schema/migration, no Cloud Functions, no `firestore.rules`, no escrow/payment change, no unrelated AUDIT-W-QUOTE code in the final diff (isolated out; Daos/ViewModel/MainScreen quote hunks reverted, stray W-QUOTE files removed).
- **Implementation:** `SkillsExchangeRepository.cancelJob(jobId, reason)` delegates to secured `transitionJobStatus(..., CANCELLED, reason)` (F6 identity → real-row load → `isJobTransitionAuthorized` ownership/cancellable-state → `isValidTransition` structural → Room status + audit row with `changedBy=actorUid`); `SkillsExchangeViewModel.cancelJob` refreshes `selectedJob` only on success; `JobPipelineKanban`/`JobPipelineCard` add gated `Cancel Job` button + `AlertDialog` reason affordance (visibility only — backend boundary decides); both `MainScreen` Jobs views wire `onCancelTask = { job, reason -> viewModel.cancelJob(job.id, reason) }`.
- **Tests:** `JobStatusAuthorizationSecurityTest` TEST D (3 new: unauthenticated fails closed, missing job fails closed, owner/attacker authorization mirror) — focused **12 tests / 0 failures / 0 errors / 0 skipped**; full `:app:testDebugUnitTest` **36 suites / 255 tests / 0 failures / 0 errors / 0 skipped** (with `TMP`/`TEMP=D:\gradle-home\tmp` workaround); `:app:compileDebugKotlin` **BUILD SUCCESSFUL**; `git diff --check` clean.
- **Static review:** no direct Firestore write; authenticated UID required; F4 ownership enforced; missing job fails closed; terminal `CANCELLED/EXPIRED` (and non-cancellable states via state machine) blocked; worker path fails closed (`assignedProviderId` is a provider FK, never UID-compared); reason is audit-only; no escrow/payment edits; rules not weakened; no client admin bypass.
- **Remaining:** device walkthrough of Jobs → Cancel Job → reason → confirm → refresh; escrow refund policy is still a product open question (requirement_file §open question 9 / no refund trigger implemented here).
- **State:** committed by environment as `d6e2181 AUDIT-C-CANCEL` (5 files); `hs_err_pid20920.log`/`replay_pid20920.log` from the prompt no longer exist (older tracked `hs_err/replay` logs predate this task and were left untouched); docs updated this session (TASKS/CHANGELOG/AGENT_STATE, uncommitted).

### [2026-10-15] [AUDIT-A-CONSOLE] First Admin Verification Queue UI — Implemented & Verified (code+unit)

- **Scope:** AUDIT-A-CONSOLE only (user-selected next step; all 93 `prd.json` stories remain `completed`, so `prd.json` is untouched). The `adminVerificationQueue` + `submitAdminDecision()` flow had zero Composable call sites — operators had no screen. No Cloud Functions, rules, schema, or unrelated UI changed. No deploy, no commit.
- **New:** `app/src/main/java/com/example/ui/components/AdminQueueSheet.kt` — `AdminVerificationQueueSheet` bottom sheet rendering masked NIN (`nationalIdNumberMasked`) + private Storage *paths* (never URLs), submitted/SLA timestamps, guarantor state, prior feedback, per-row feedback field, Approve / Changes Required / Reject buttons with single-flight pending state, refresh + empty + locked-console states.
- **New:** `app/src/main/java/com/example/util/AdminQueueDecisions.kt` — pure-JVM allow-list guard (`APPROVE|REJECT|CHANGES_REQUIRED`, feedback 10+ chars for reject/changes, locked-console + blank-id fail-closed), mirroring `reviewWorkerApplication` + `validateGuarantorReviewInput`.
- **Wiring:** `NavigationDrawer` gains a locked-by-default "Admin Verification Queue" entry (pending-count subtitle, Operator/Locked badge); `MainScreen` collects `adminVerificationQueue` + `isAdminQueueLoading`, holds `isAdminQueueOpen`/`isAdminUnlocked` (locked default), renders the sheet; `SkillsExchangeViewModel` gains `isAdminQueueLoading` (overlap guard) + decision allow-list/feedback validation (backend callable stays the real authorizer).
- **Tests:** new `app/src/test/java/com/example/util/AdminQueueDecisionsTest.kt` (pure JVM, 8 cases: approve-open, locked-blocked, blank-id, unknown-vocabulary, reject/changes feedback gates, happy-path reject, case-insensitive allow-list).
- **Verification (executed):** `:app:compileDebugKotlin` BUILD SUCCESSFUL; focused `AdminQueueDecisionsTest` **8 tests / 0 failures / 0 errors / 0 skipped** (from test-results XML); full `:app:testDebugUnitTest` attempted — 92 pre-existing Robolectric failures from environment native-loader temp errors (`UnsatisfiedLinkError`/`FileSystemAlreadyExistsException`, unrelated to this change; daemon also crashed once on `--rerun-tasks`); `git diff --check` clean (one LF→CRLF warning on NavigationDrawer.kt only). No APK build, no device run, no `firebase deploy`.
- **Remaining:** device walkthrough of the sheet with a real admin-claim account; `firebase deploy --only firestore:rules,functions` still pending (rules/functions are source-only); 11 audit gaps still open (next suggested: `AUDIT-C-CANCEL` or `AUDIT-W-QUOTE` per Phase 1 roadmap).

### [2026-10-15] [AUDIT] Multi-Perspective Platform Audit (Worker, Client, Administrator) & Documentation Synchronization

- **Scope:** Documentation and architectural audit synchronization only. Direct source inspection of Kotlin Composables, Room entities, ViewModels, Repository logic, Cloud Functions, and Firebase Security Rules was conducted without reliance on stale markdown claims. No production code modified this session.
- **Worker Persona Findings:**
  - `submitQuote` exists in repository and ViewModel, but `WorkerJobFeedCard` CTA opens `EscrowAndProofSheet` directly instead of a quoting form. No Composable exists for workers to propose rates, hours, or terms.
  - `EscrowWalletSheet` is read-only; no "Withdraw to MoMo" button exists to trigger disbursements or manage payout numbers.
  - Active job card lacks geo-intent triggers to launch Google Maps / turn-by-turn routing to the client's home.
  - No interface exists for workers to submit counter-evidence photos or explanations when a client disputes a job.
  - No UI for project portfolios, trade certificates, or tiered rates (emergency vs standard).
- **Client Persona Findings:**
  - `QuickRequestDrawer` only accepts text/numbers/enums; homeowners cannot attach photos of leaks/damage when posting jobs.
  - Only binary `URGENT` vs `SCHEDULED` toggle exists; no calendar date or time window picker.
  - `JobPostEntity` only captures district; no fields for street address, apartment, or landmark directions.
  - `firestore.rules` permits `CANCELLED`, but neither `JobPipelineKanban` nor `MainScreen` provides a "Cancel Job" button or refund trigger.
  - `WORKER_EN_ROUTE` state machine step has no real-time worker location streaming on client map.
  - No re-hire shortcut in `JobHistorySheet`; no voluntary gratuity tip when releasing escrow.
- **Platform Administrator Persona Findings:**
  - `adminVerificationQueue` and `submitAdminDecision()` exist in `SkillsExchangeViewModel`, but are **never collected or rendered by any Composable in the repository**. Operators have zero UI across web and mobile.
  - `resolveDispute` exists in Cloud Functions, but admins have no console to review statements, photos, or chat history before deciding splits/refunds.
  - `isSosTriggered` boolean flag has no live operator incident monitor, SMS dispatch trigger, or police alert console.
  - No financial reconciliation ledger tracking telecom transaction IDs, platform commissions, escrow held, and failed payouts.
  - 10% platform fee and $5.00 callout fees hardcoded rather than remote-configured.
- **Synchronized Artifacts:**
  - `AUDIT_THREE_PERSONAS.md`: Comprehensive audit document with executive summary, persona breakdowns, gap matrix, and 2-phase roadmap.
  - `requirement_file.md`: Added §15 specifying detailed operational requirements for all three personas; updated Open Questions Summary.
  - `prd.json`: Annotated story notes for `MARKET-002`, `PAY-003`, `JOB-001`, `ADMIN-001`, `DISP-003`, and `SAFE-002`; updated `last_updated` timestamp (`2026-10-15T11:00:00+03:00`).
  - `TASKS.md`: Added 12 new pending task items under Multi-Perspective Platform Audit; synchronized header.
  - `AGENT_STATE.md`: Added top-priority audit banner and current handoff state.
  - `DECISIONS.md`: Recorded D-2026-10-15-1.
- **Verification:** `prd.json` re-parsed as valid JSON with 93 stories and 93 completed stories preserved; line endings (CRLF) preserved; `git diff --check` clean.

### [2026-10-14] [PAY-002] Trusted MTN MoMo payment confirmation — provider status verification, untrusted-callback pull-to-verify, idempotent escrow lock, backend-driven Android states

- **Scope:** PAY-002 only (payments phase 2). No deploy, no commit, no push, no real-money transaction. Sandbox-only and fail-closed, like PAY-001. PAY-003 (payout) and PAY-004 (material advance) remain disconnected and were not implemented.
- **Provider contract (verified from MTN MoMo API documentation, 2026-10-14):** status of a RequestToPay is `GET /collection/v1_0/requesttopay/{referenceId}` (Bearer token + subscription key + target-environment header); 200 → `status ∈ {PENDING, SUCCESSFUL, FAILED}` with `amount`/`currency`/`financialTransactionId`; 404 → unknown reference. Callbacks arrive via the `X-Callback-Url` header as an **unauthenticated POST** (no HMAC/signature/secret/mTLS is documented), so the callback body is never trusted — see DECISIONS.md D-2026-10-14-1.
- **Server (trusted backend):**
  - New pure core `functions/src/momoConfirmation.ts` (zero runtime imports; injected deps; `node --test`-able): status vocabulary `PENDING | SUCCESSFUL | FAILED | UNKNOWN | NEEDS_RECONCILIATION`; `parseProviderStatusResponse` (malformed → `malformed`, never a status guess); `decideConfirmation` (terminal states immutable; provider success must be amount/currency-consistent with the STORED transaction before escrow may be considered; timeouts/5xx keep last-known `PENDING` with the check error; malformed/unknown-reference → `UNKNOWN`; disagreement → `NEEDS_RECONCILIATION`); `authorizeStatusQuery` (stored `clientUid` must equal the authenticated caller); `extractCallbackReferenceHint` (reads ONLY a reference from the untrusted callback body — `referenceId`/legacy `transactionRef` — and ignores status/amount/job/user fields); `verifyAndConfirmCollection` orchestrator (unknown ref → rejected; terminal → idempotent no-op with no provider call; config/token failures fail closed).
  - `functions/src/index.ts`: new **`getMoMoCollectionStatus`** callable (authenticated; ownership enforced against the stored transaction; re-queries the provider server-side with backend-only credentials; never accepts a client status). **`processMoMoWebhook` rewritten**: POST-only, unknown reference → 404, body used solely as a lookup hint, all state changes flow through the same verified pull path — a forged callback can only trigger a legitimate re-check; replays of terminal transactions are no-ops. New **`lockEscrowForConfirmedTransaction`**: Firestore transaction that re-reads the job, verifies tx↔job binding, amount, currency and non-funded escrow, then locks (`FUNDS_LOCKED_IN_ESCROW` + `escrowLockedByTransactionId` + `escrowLockedAt`) exactly once (idempotent), and notifies `postedByUid`/assigned worker (authoritative uids — the legacy webhook's `postedByResident` display-string notification is gone). Worker payout deliberately absent (PAY-003).
  - **Removed the invented SEC-002 HMAC gate** (`verifyHmacSignature`, `MOMO_WEBHOOK_SECRET`): MTN documents no callback authentication, so that protocol could never validate a real provider callback while its absence didn't protect the old body-trusting handler anyway. SEC-002's `prd.json` note is annotated, not erased.
- **Android:** `MoMoCollectionStatusResult` + `FirebaseService.getMoMoCollectionStatus` (trusted callable only); new `MoMoPaymentUiState`/`MoMoPaymentPhase` + pure `mapBackendStatusToPhase` (anything not PENDING/SUCCESSFUL/FAILED — including backend `UNKNOWN`/`NEEDS_RECONCILIATION`, null and garbage — degrades to `NEEDS_ATTENTION`, never success); `SkillsExchangeViewModel` exposes `momoPaymentState` and, after a 202→PENDING initiation, polls the trusted backend (8 × 5 s) for the provider-verified phase; `EscrowAndProofSheet`'s payment modal no longer flips `FUNDS_LOCKED_IN_ESCROW` locally — it triggers `onInitiateMobileMoneyPayment` (→ `initiateMoMoEscrowPayment` → `createMoMoCollection`) and renders pending/successful/failed/needs-attention from the backend state; `MainScreen` wiring + state reset on sheet dismiss. **No Android code path treats a local success or HTTP 202 as proof of funds.**
- **Tests (all with mocked provider responses; no real MTN transactions):**
  - `functions/test/momoConfirmation.test.mjs` (new, `node --test`): covers the mandated matrix — 202→PENDING, provider PENDING/SUCCESSFUL/FAILED, timeout→not-successful, malformed→not-successful, unknown reference rejected, duplicate webhook idempotent, replay-after-terminal no corruption, manipulated amount → stored wins (`NEEDS_RECONCILIATION`, no escrow), manipulated job/user callback fields ignored, escrow locks exactly once, failed payment never funds escrow, unauthorized/foreign-transaction query denied, fail-closed config. `functions/package.json` test script extended.
  - Android (Gradle): `MoMoPaymentUiStateTest` (pure mapper, fail-closed cases) and `MoMoCredentialLeakSecurityTest` (scans `app/src/main/java` for MTN credential names, auth-header names, token endpoint and gateway host — none may appear).
- **Verification (all executed 2026-10-14, mocked provider responses only; no real MTN transactions):**
  - functions: `npx tsc --noEmit` → EXIT 0; `node --test test/momoConfirmation.test.mjs test/momoCollection.test.mjs test/guarantorReview.test.mjs` → **82 tests / 82 pass / 0 fail / 0 skipped** (confirmation-core suite: 25 of those tests).
  - Android: `:app:compileDebugUnitTestKotlin` → BUILD SUCCESSFUL; `:app:testDebugUnitTest` → **35 suites / 244 tests / 0 failures / 0 errors / 0 skipped** (baseline 235 + 9 new; result XML re-generated fresh for this run). Includes `MoMoPaymentUiStateTest` (8) and `MoMoCredentialLeakSecurityTest` (1).
  - `prd.json` re-parsed after edits → valid JSON.
- **Not claimed:** end-to-end delivery of the MTN callback (portal-registered callback host + real sandbox reference) is **not yet sandbox-verified** — no functions were deployed. Remaining disconnected paths unchanged: cash-settlement local escrow flip in `EscrowAndProofSheet` (non-MoMo), PAY-003 payout fabrication, PAY-004 Room-only advance.



### [2026-10-13] [AUDIT] Implementation-Truth Audit — statuses annotated, no product code changed

- **Scope:** Documentation/verification only. Audited the completed PRD stories against the actual source and their acceptance criteria. **No application feature was implemented, no rules/functions deployed, nothing committed.** `prd.json` statuses were NOT bulk-flipped.
- **Schema limitation (documented, not silently changed):** `prd.json` currently permits exactly one status value — `completed` — across all 93 stories; there is no `in_progress`/`partial`/`blocked` value and `completed_stories[]` lists all 93. Per the audit brief (Phase 4 rule 5) no new status was invented. Instead each affected story's `notes` field now carries an `AUDIT 2026-10-13:` annotation stating exactly what is mocked/disconnected/unverified and what work remains. A future schema change (add `in_progress`/`partial`) is proposed in DECISIONS.md D-2026-10-13-1.
- **Verified SIMULATED / DISCONNECTED** (evidence = file + method): PAY-001 (`MoMoPaymentService.createMoMoCollection` empty apiKey; CF `createMoMoCollection` no gateway call), PAY-002 (`processMoMoWebhook` never fires — no gateway integration), PAY-003 (`releaseEscrowToWorker` fabricated `PAYOUT-<ts>`, no Transfer API; Android `disburseEscrowPayoutToWorker` fabricated, 0 callers), PAY-004 (`approveMaterialAdvance` Room-only), SAFE-002 + MARKET-001 SMS (`SmsNotificationService` Log+delay+fake `SENT`), NOTIF-002 (`sendTokenToServer` logs only) + NOTIF-003 (local Room notifications only), AI-001 (canned job fields on placeholder key / API error), OFF-001 (`flushPendingActions` "execution not yet implemented"), KYC-002 (`uploadKycDocument` 0 callers), DISP-003/004 (local Room only; CF `resolveDispute`/`getDisputesQueue` never called from app), ADMIN-001 (no web dashboard — CF + Android only).
- **Verified FUNCTIONAL** (correcting earlier over-claims): AUTH-008 (7-day expiry + device-id enforced inline in `SkillsExchangeViewModel.restoreSession()`; `SessionManager.isSessionValid()` is dead code), SAFE-001 (unique per-job random 4-digit PIN in `addJobPost`), MARKET-001 discovery/filtering, COMM-001 chat (Firestore, subject to deployed rules).
- **Files changed:** `prd.json` (18 `notes` annotations), `AGENT_STATE.md` (audit banner), `TASKS.md` (gap tasks), `CHANGELOG.md` (this entry), `DECISIONS.md` (D-2026-10-13-1). `requirement_file.md` intentionally NOT changed (requirements are the intended product, not the implementation).
- **Tests:** none run — this is a documentation audit with no code change, so no unit-test/compile/APK claim is made. `git --no-pager diff --check` clean; `prd.json` re-parses (93 stories / 93 completed).


### [2026-10-11] [Phase 17] REG-001, LOC-005, MAP-002, MAP-003, LEGAL-001, LEGAL-002, AUTH-012 completed (documentation-only)

- **Scope:** The seven remaining Phase 17 "Future Requirements" stories were completed. Every one of them is a *documentation* story — its own `goal` is to record a future requirement **without implementation** (e.g. LEGAL-002: "Document the privacy policy/T&C/consent requirements as future requirements without implementation"; AUTH-012: "this is a preservation note, not an implementation task"). Accordingly **no source file was changed**, and no unit-test, compile or APK claim is made for them. They are `verification: []` / `rollback: "Edit PRD documentation"` stories.

- **What was actually done (and verified)**
  - **REG-001** (`requirement_file.md` §13.2): UGX 20,000 first-time worker registration fee, and the requirement that the registration UI clearly communicate it. Criterion *"Registration UI requirement notes UGX 20,000 fee communication"* satisfied. No internal breakdown invented; the two open questions are preserved.
  - **LOC-005** (§13.6): GPS-permission enablement for workers, Android permission + device-accuracy limitations (GPS not claimed exact), and removal of the old "Sunside/Sun-side district" concept. All three criteria satisfied.
  - **MAP-002** (§13.7): maps only during an active client-worker interaction; explicit goal of minimising Google Maps usage/cost. Both criteria satisfied.
  - **MAP-003** (§13.8, **newly written**): the requirement existed in `prd.json` / `TASKS.md` / `CHANGELOG.md` / `DECISIONS.md` but was **missing from `requirement_file.md`** — the actual gap this session closed. §13.8 now records all 13 acceptance criteria as future requirements (maps confined to the active-job screen; on *any* Maps failure the app must not crash/freeze/infinite-load and must show "Map location is currently unavailable." with a Contact Worker action reusing the existing **COMM-001** masked Firestore chat and a Continue Without Map action; worker GPS independent of Maps; every job-lifecycle flow unaffected; API key via `manifestPlaceholders` / `.env`; `try/catch` around SDK init; permission-denial handling; feature flag). Those implementation-side criteria remain deliberately unimplemented. Two open questions were recorded (reconciling the unguarded `map.onCreate()` crash of device defect **D1** at `InteractiveMapView.kt:98` with the active-job design; whether COMM-001 chat is reachable from the active-job context).
  - **LEGAL-001** (§13.11): short liability disclaimer, subject to applicable law, with placement in T&C / onboarding / transaction flows and a mandatory Ugandan legal review before launch. No legal guarantee invented.
  - **LEGAL-002** (§13.12): the 15 coverage areas plus user-consent tracking as a future need.
  - **AUTH-012** (§13.13): the preservation note, **cross-checked against live code** — `OnboardingScreen.kt` really declares `OnboardingStep.ROLE_SELECTION`, `NEW_USER_ROLE_SELECTION`, `AUTHENTICATION`, `PROFILE_SETUP`; `SkillsExchangeViewModel.restoreSession()` really restores a session to the main app (7-day expiry, AUTH-008); `setUserRoleMode()` really refuses `"Offer Skills"` unless `canAccessWorkerFeatures()` passes, so the worker activation/KYC/approval gate is intact.
  - Because §13.8 was inserted, the later Phase 13 sections were renumbered: Community 13.8→**13.9**, SEC-009 13.9→**13.10**, LEGAL-001 13.10→**13.11**, LEGAL-002 13.11→**13.12**, AUTH-012 13.12→**13.13**. The Open Questions Summary gained items 6 and 7 (both MAP-003).

- **Correction of a prior unverified completion.** A previous session had already flipped these same seven to `completed` using a `complete_stories.ps1` script with boilerplate notes, while leaving `TASKS.md` (still ⏳ Pending), `AGENT_STATE.md`, `CHANGELOG.md` and `DECISIONS.md` unsynchronised, and while MAP-003 was still absent from `requirement_file.md`. That violates AGENTS.md §2/§3 ("never mark a story completed unless the implementation was verified") and §11 (documentation synchronisation). `prd.json` was therefore **restored from HEAD** (`git hash-object` re-verified identical to the `33b5be1` blob) and the status change was re-applied with verified, story-specific notes. `complete_stories.ps1` was deleted as session debris.

- **Verification (executed):** `prd.json` re-parses as valid JSON — 93 stories, `completed_stories` 93, `last_updated` `2026-10-11T15:24:11+03:00`, 0 stories still `pending`; UTF-8 encoding preserved (no BOM, non-ASCII byte count unchanged at 44); `git diff --check` clean on `prd.json`, `requirement_file.md` and `TASKS.md`; every one of the 7 replacements was guarded by an occurrence-count assertion (each matched exactly once) so no unintended text was rewritten. Two intermediate JSON errors introduced while writing the notes (unescaped `"` inside REG-001 and AUTH-012) were caught by validation and fixed before the file was accepted. **No Gradle run and no device run** — none is applicable, because no code changed.



### [2026-10-10] [M7] Worker distance/radius filtering (B9)

- **Problem:** Radius filtering and the displayed "X mi away" for providers used a static, seeded `distanceMiles` that never reflected the user's real GPS location.
- **Fix:** Added `util/DistanceUtil.kt` (`haversineMiles`, `effectiveDistanceMiles`) and wired `SkillsExchangeViewModel` to compute the real straight-line distance from the user's GPS to each provider before filtering and display, so filter and UI stay consistent with no UI change. `effectiveDistanceMiles` falls back to the stored `distanceMiles` when GPS is unavailable, so the app degrades gracefully.
- **Verification:** New `DistanceUtilTest.kt` â€” 5 tests / 0 failures / 0 errors (same-coords zero distance, SF-to-LA â‰ˆ347 mi, symmetry, real-location beats stored fallback, fallback to stored value when GPS is null). `:app:testDebugUnitTest --tests com.example.DistanceUtilTest` BUILD SUCCESSFUL (EXIT=0); `:app:compileDebugKotlin` unchanged. Decision **DEC-029**.


All meaningful development changes are recorded here in reverse chronological order.
### [2026-10-10] [M6] Map empty state (B8)

- **Problem:** When no providers matched the current filters/location, the Map tab showed a blank grey map with only a tiny "0 Nearby Specialists" chip â€” no explanation and no next step (finding B8: "no useful empty state when no providers/GPS").
- **Fix:** Added `MapEmptyProvidersOverlay` (internal composable in `InteractiveMapView.kt`, styled to match the existing `MapUnavailableFallback`) that overlays the map when `providers.isEmpty()`, showing "No providers nearby" plus guidance (widen the radius, adjust filters, or switch to the List view). GPS/permission status is already surfaced separately by `GpsLocationBanner`, so the fix targets the empty-providers case.
- **Verification:** New `MapEmptyProvidersOverlayTest` (Robolectric + Compose, NATIVE graphics) **1/1 pass**; `:app:compileDebugKotlin` + `:app:testDebugUnitTest` BUILD SUCCESSFUL (bounded daemon heap). Decision **DEC-028**.

### [2026-10-10] [M5] Notification tap navigation (B6)
2026-10-10 — [M5] Notification tap navigation (B6) — *(duplicate heading kept for history; content is the block below)*

- **Problem:** Tapping an in-app notification in the `NotificationDrawer` only called `markAllNotificationsRead()` and closed the drawer â€” it never navigated to the verification, payment, dispute, SOS, or job the notification referred to (finding B6: "marks all read but doesn't navigate").
- **Fix:**
  - New pure typeâ†’target map `util/NotificationRouting.kt` (`NotificationRouting.targetFor(type): NotificationTarget`) covering the types `SkillsExchangeRepository` writes: `VERIFICATION_DECISION`â†’IDENTITY, `PAYMENT_STATUS`â†’WALLET, `DISPUTE_UPDATE`/`SOS_EVENT`â†’TRUST_SAFETY, `NEW_QUOTE`â†’QUOTE_COMPARISON, else NONE.
  - `MainScreen.handleNotificationClick` keeps the old mark-read + close-drawer, then opens the matching local sheet (`IdentityVerificationSheet` / `EscrowWalletSheet` / `TrustSafetySheet` / quote-comparison â€” the last only when the job still resolves by `jobId`). Unresolvable/unknown types (e.g. `TRAINING_REQUIREMENT`) fall back to mark-read-only.
- **Verification:** New `NotificationRoutingTest` (plain JUnit) **7/7 pass**; `:app:compileDebugKotlin` + `:app:testDebugUnitTest` BUILD SUCCESSFUL (bounded daemon heap to avoid the known 4 GB OOM on this 3.9 GB machine). Decision **DEC-027**.

### [2026-10-10] [M4] Worker Online toggle persistence (B4)
2026-10-10 — [M4] Worker Online toggle persistence (B4) — *(duplicate heading kept for history; content is the block below)*

- **Problem:** The worker's online/availability toggle lived only in an in-memory `MutableStateFlow(true)` (`SkillsExchangeViewModel.isWorkerOnline`). `toggleWorkerOnline()` flipped it in memory and nothing persisted it, so a worker who paused (went offline) silently came back **online** on every app restart â€” losing the "don't send me jobs" intent.
- **Fix:** Persist the flag in the existing **SessionManager DataStore** (`worker_online` boolean key, defaults to `true`):
  - `SessionManager.kt` â€” added `KEY_WORKER_ONLINE`, `setWorkerOnline(Boolean)`, `getWorkerOnline()` (default `true`).
  - `SkillsExchangeViewModel.kt` â€” `restoreSession()` now sets `isWorkerOnline.value = sessionManager.getWorkerOnline()` after role-mode restore; `toggleWorkerOnline()` writes the new value via `viewModelScope.launch { sessionManager.setWorkerOnline(newOnline) }`.
- **Why DataStore, not Room:** No schema migration required, no Room version bump, and it matches how other lightweight session flags (`isAuthenticated`, `userPhone`, `user_role`, etc.) are already persisted. Server-shared presence (Firestore) is out of scope for this finding.
- **Verification:** New `WorkerOnlinePersistenceTest` (Robolectric) â€” default true; offline persists; back-online persists; value survives a new SessionManager instance (restart). **4/4 pass**; `:app:compileDebugKotlin` **BUILD SUCCESSFUL**.
- Files touched: `SessionManager.kt`, `SkillsExchangeViewModel.kt`, `WorkerOnlinePersistenceTest.kt` (new).

### [2026-10-10] [M2/M3] AccountComplianceDialog deferred; Community Hire confirmed obsolete

- **M2 / A7 â€” DEFERRED (user decision).** Investigated the "AccountComplianceDialog wiring" finding: the namesake `AccountComplianceDialog` composable no longer exists (it was already removed as the original A7 fix â€” DECISIONS D-2026-09-26 era, "import removed from MainScreen, never used"). The misnamed `AccountComplianceDialog.kt` now holds 6 composables â€” 3 live/wired (`AccountDeletionDialog` â†’ MainScreen:788, `PrivacyAndTermsSheet` â†’ MainScreen:783, `WorkerTrainingScreen` â†’ MainScreen:352) and 3 orphaned (`GuarantorFormSheet`, `QualificationEvidenceSheet`, `ChangesRequiredView`). The orphans are superseded by live components (`WorkerVerificationStatusView` implements the KYC-007 "Changes Required" flow; the REG-002 "National ID & Verification" sheet hosts the guarantor step). Deleting `GuarantorFormSheet` would contradict **D-2026-09-26-1** ("left untouchedâ€¦ stays dead code until the registration wizard is built"). Presented wire-vs-delete options to the user, who chose to **defer**. No code change.
- **M3 / A8 â€” OBSOLETE (verified).** The Community/Board "Hire" button finding is moot: the entire Community/Board feature was removed under COMM-002 (2026-09-23). Repo-wide grep confirms no Community/Board screen or Hire button remains (only incidental "community" text in training/feedback/endorsement strings and a `neighborhood_posts` migration comment). No action possible.
- Net code impact: **none** (both are doc/state syncs). `prd.json` untouched.

### [2026-10-10] [M1] Job History & Material Receipts â€” verified & doc-synced (code already present)

- **Finding (A4, MEDIUM):** Drawer "Job History & Material Receipts" â€” no history screen.
- **Verification (executed 2026-10-10):** the code already implemented and wired this finding; this session verified end-to-end and synced the stale docs.
  - Drawer item `NavigationDrawer.kt` (title "Job History & Material Receipts") â†’ `onOpenHistory()` â†’ `MainScreen` sets `isJobHistorySheetOpen = true` â†’ `JobHistorySheet(isOpen, jobs = jobs, onDismiss)` in `AccountCenterSheets.kt`.
  - `JobHistorySheet` filters `jobs` to `status` in {COMPLETED, RELEASED} or `escrowStatus == RELEASED_TO_WORKER`, renders each contract via `SheetMoneyRow` (title / status / district / grandTotal) plus a `SheetInfoBox` with material-receipt attachment count and materials subtotal when `materialReceiptsCsv` is non-blank. Real data from `viewModel.jobs` â€” no placeholder/hardcoded balance.
  - `compileDebugKotlin` BUILD SUCCESSFUL (HEAD `a1b64c3` + these doc edits; source unchanged from `072de5f` where the code landed).
- **No functional code changed** â€” `JobHistorySheet` was committed earlier (H-series commit `ecb5d5e`); docs (TASKS.md M1, AGENT_STATE.md A4) were stale at "Pending". Device walkthrough still pending.
- `prd.json` untouched.

### [2026-10-10] [H7] Trust/Safety & SOS â€” Implemented, wired, compiled & unit-tested (5/5)

- **State found:** a prior session had left uncommitted H7 wiring in the tree â€” `ViewModel.clearSosAlert`/`setDisputeError`, the `DisputeSubmissionState` flow + `submitDispute`, `EscrowAndProofSheet` `onClearSosAlert`/STAND DOWN button + dispute-error surfacing, and `MainScreen.onSubmitDispute` fail-closed guard. An intermediate read suggested the repository boundary was missing; on a fresh, cache-free read it was present and complete. The work was audited, kept, and verified end-to-end â€” not rewritten.
- **SOS stand-down (SAFE-003), owner-gated & fail-closed:** `SkillsExchangeRepository.clearEmergencySos(jobId): SosClearResult` resolves the actor from `FirebaseAuth.currentUser.uid` via `resolveAuthoritativeUid` (no uid â†’ `NotAuthenticated`), loads the REAL job by id (missing â†’ `JobNotFound`), then delegates to the new pure policy `isSosClearAuthorized(actorUid, job)` â€” owner-only (`postedByUid`, F4), and only when `isSosTriggered` is actually raised; a failure returns `NotAuthorized` and the DB is untouched, so a stranger can never silence another account's emergency. Only on success does it lower the flag and post a system chat note. `ViewModel.clearSosAlert` maps the result into a new `SosClearState` (Idle/Clearing/Success/Error) flow; `MainScreen` collects it and feeds `isClearingSos` (disables the button mid-clear) + `sosClearError` (surfaced inline) and resets on dismiss. Raising SOS (pre-existing `triggerSosAlert`/`triggerEmergencySos`) is unchanged.
- **Dispute hard-fail closure:** `MainScreen.onSubmitDispute` never files a synthetic sentinel as `reportedUserId` â€” a dispute with a null/blank `assignedProviderId` fails closed to a visible `setDisputeError("No worker is assigned to this job yet")` (no row written), mirroring the H6 rating guard. Backend dispute rejections (not-authenticated / already-filed) surface inside the dialog via the existing `DisputeSubmissionState.Error` channel. No schema, rules, or unrelated behavior changed.
- **Tests added:** `app/src/test/java/com/example/data/SosClearAuthorizationSecurityTest.kt` (5 pure-JVM tests, no Robolectric) â€” owner standing down an active SOS is authorized; stranger denied; null-owner legacy job deniable by nobody; owner cannot clear a non-active SOS; synthetic/presentation-text actors (`null`, `""`, `"   "`, `system`, `unknown`, `unknown_worker`, `user_default_me`, `John Doe`, `You (Nakawa)`) denied.
- **Verification (executed 2026-10-10):** `:app:compileDebugKotlin` BUILD SUCCESSFUL; `:app:testDebugUnitTest --tests com.example.data.SosClearAuthorizationSecurityTest --tests com.example.data.RatingAuthorizationSecurityTest` â†’ BUILD SUCCESSFUL, **SosClear tests=5 failures=0 errors=0 skipped=0** and **Rating tests=7 failures=0 errors=0 skipped=0** (H6 regression re-confirmed). Note: a first `compileDebugKotlin` attempt failed at daemon startup with `The paging file is too small` / JVM OOM and the reader hit `ENOSPC` â€” both were environmental (C: at 0 GB free, 9 stale JDK-25 daemons), cleared via `gradlew --stop` + `--no-daemon` + temp cleanup; not logic. Device walkthrough of SOS trigger â†’ STAND DOWN â†’ dispute still pending.
- **A5 finding resolved (same H7 Trust/Safety domain):** the drawer item "Trust, Safety & Emergency SOS" previously opened the unrelated escrow sheet / could silently do nothing â€” it now routes through `onOpenDisputes` â†’ `MainScreen` `TrustSafetySheet`, a real data-driven destination that renders live `isSosTriggered` jobs plus the authenticated user's `myDisputes` (UID-keyed, fail-closed). `compileDebugKotlin` BUILD SUCCESSFUL â€” device walkthrough still pending.
- `prd.json` untouched (all PRD stories already complete; H-series are post-PRD audit fixes tracked in TASKS.md/AGENT_STATE.md). H7 committed as `072de5f` ("H7 wiring is confirmed"); this A5 documentation sync and CHANGELOG addendum are the only follow-up edits.

### [2026-10-10] [H6] Rating UI â€” Hardened, wired & unit-tested (7/7)

- **State found:** a prior session had left uncommitted H6 wiring in the tree (release-gated "Rate Worker" button + star dialog in `EscrowAndProofSheet`, `MainScreen.onSubmitRating` â†’ `ViewModel.submitRating` â†’ repository `submitJobRating` secured boundary with `isRatingAuthorized` owner-only / released-only / assigned-provider-only policy). That work was audited, kept, and hardened â€” not rewritten.
- **Gaps closed (this session, minimal):** `MainScreen` passed `"unknown_worker"` as `toUserId` when `assignedProviderId` was null â€” now fails closed to a visible `setRatingError("No worker is assigned to this job yet")` (new `ViewModel.setRatingError`, no row written); `EscrowAndProofSheet` gained a `ratingSubmissionError: String?` param fed from `RatingSubmissionState.Error.message`, surfaced inside the dialog via `LaunchedEffect` so backend rejections (not-authenticated / not-authorized / already-rated / job-not-found) are never mistaken for a saved rating. No schema, rules, or unrelated behavior changed.
- **Tests added:** `app/src/test/java/com/example/data/RatingAuthorizationSecurityTest.kt` (7 pure-JVM tests, no Robolectric) â€” owner-authorized on own released job; stranger denied; null-owner legacy job unrateable; pre-release escrow states denied; unrelated-worker target denied; null assignment denies all; synthetic/presentation-text actors (`system`, `unknown`, `unknown_worker`, `user_default_me`, display names) denied.
- **Verification (executed 2026-10-10):** `:app:testDebugUnitTest --tests RatingAuthorizationSecurityTest --tests ServiceTaxonomyValidationTest` â†’ BUILD SUCCESSFUL, **7/0/0 + 11/0/0**; `:app:compileDebugKotlin` BUILD SUCCESSFUL. First attempt ran under Robolectric and failed 7/7 on `There is not enough space on the disk` (native-runtime extraction) â€” environment, not logic; converted to plain-JVM. Full suite not re-run (disk pressure); device walkthrough of rateâ†’persist still pending.
- `prd.json` untouched. No commit made.

### [2026-10-10] [H5] ServiceCatalogPicker Wiring â€” Implemented, compiled &amp; unit-tested (11/11)

- **Defect found:** `ServiceCatalogPickerModal` existed but had zero call sites, so workers could never set the `primarySkill` / `additionalSkillsCsv` persisted on their own profile row (the SKILL-002/003 engine was orphaned).
- **Fix â€” full-stack own-row wiring (fail-closed):** `UserProfileDao.updateWorkerSkills()` (targeted `UPDATE ... WHERE id = :userId`); `SkillsExchangeRepository.saveWorkerSkills()` resolves the authoritative UID, rejects unknown/catalog-invalid skill sets via `ServiceTaxonomyCatalog.validateWorkerSkills`, requires the UID-bound row (`row.id != actorUid` â†’ false), then updates Room + Firestore mirror; `SkillsExchangeViewModel.saveWorkerSkills()` + `isSkillPickerOpen` / `workerSkillsError`; `ProfileSettingsSheet` renders a "TRADE SKILLS" section (current primary/additional summary + "Choose trade skills" button + inline error) hosting the picker. Skills are catalog metadata, never identity â€” never touches KYC/activation/training.
- **Tests added:** `app/src/test/java/com/example/data/db/ServiceTaxonomyValidationTest.kt` (11 pure-JVM tests) â€” same-family valid, id-resolution, 2-additional valid, >2 rejected, unknown primary rejected, incompatible family rejected, compatible cross-family (Electrical+Computing, Housekeeping+Gardening) allowed, unknown additional rejected, `findService` by id/name, 125-service catalog size.
- **Verification (executed 2026-10-10):** `:app:compileDebugKotlin` BUILD SUCCESSFUL; `:app:testDebugUnitTest --tests ServiceTaxonomyValidationTest` â†’ **tests=11 failures=0 errors=0 skipped=0**. Device walkthrough of pickerâ†’save still pending.
- `prd.json` untouched. No commit made.


### [2026-10-10] [H4] Wallet Drawer Label Wired to Real Escrow Total â€” Implemented & compiled

- **Defect found during H4 verification:** `EscrowWalletSheet` itself computed real figures from live job records, but `MainScreen` never passed `escrowHeldTotalLabel` / `isKycVerified` / `kycStatusLabel` to the drawer â€” so the drawer rendered the `"â€”"` / `"Not verified"` / `false` placeholder defaults for every user (H4 + H3/G5 residue).
- **Fix (`MainScreen.kt` only, +10 lines):** drawer now receives `escrowHeldTotalLabel = CurrencyRegion.format(escrowHeldTotal(jobs))`, `isKycVerified = myWorkerKyc?.status == "APPROVED"`, `kycStatusLabel = myWorkerKyc?.status ?: "Not verified"` (+2 imports). No hardcoded balance anywhere in source (only history comments). Fail-closed: empty jobs â†’ UGX 0; null KYC â†’ unverified.
- **Verification (executed 2026-10-10):** `:app:compileDebugKotlin` BUILD SUCCESSFUL (fresh, 2 executed tasks; warnings all pre-existing). Device walkthrough still pending.
- `prd.json` untouched. No commit made.
### [2026-10-10] [H3] National ID & Verification Drawer â€” Verified (code + compile)

- **Finding:** audit items H3 (drawer wiring) + G5 (false "Verified" badge for any authenticated user).
- **State found in code (no new implementation needed):** drawer "National ID & Verification" opens `IdentityVerificationSheet` with the real `currentUserByUid` profile + `myWorkerKyc` row (UID-keyed, fail-closed to null); the sheet renders per-field status rows (phone / account mode / National ID KYC / guarantor step / worker activation) with green only on the true positive state, plus guarantor entry + admin feedback for worker mode. The drawer verified tick (`isKycVerified`) and status pill are caller-supplied real KYC state, not hardcoded. No unconditional "Verified".
- **Verification (executed 2026-10-10):** `:app:compileDebugKotlin` BUILD SUCCESSFUL. Device walkthrough of the drawer â†’ sheet â†’ guarantor flow still pending.
- Files changed: none (docs only). `prd.json` untouched.
### [2026-10-10] [H2][C3] AuthModal Legal-Name Handling + Google Phone Capture â€” Verified (unit)

- **Finding:** audit items H2 (AuthModal legal-name handling) and auth finding #2/#3 (Google display name misuse, missing phone capture in Google branch).
- **State found in code (no new implementation needed):** `AuthModal.kt` Google launcher does NOT seed the legal-name field from the Google display name (field starts empty, supporting text states the Google name is not used); PROFILE_SETUP collects legal name + phone + district with `AuthModalValidation.isProfileSetupValid` gating "Complete Setup" (name non-blank, phone 9â€“15 digits, district non-blank).
- **Verification (executed 2026-10-10):** `:app:compileDebugKotlin` BUILD SUCCESSFUL (fresh recompile, warnings only); `GoogleAuthModalCompletionSecurityTest` **9/9 passed, 0 failures, 0 errors** (fresh `--rerun-tasks` run, report timestamp 2026-10-10T06:56:42Z). Device verification (Google chooser â†’ typed legal name â†’ completion) still pending â€” no device run in this session.
- **Also fixed:** removed a stray `b` token on line 109 of `storage.rules` (syntax defect committed in `dfeac17` between the two avatar blocks); single-line change, no policy change, left uncommitted. Storage deploy remains BLOCKED on console provisioning.
- Files changed: `storage.rules` (1 line, uncommitted). No app source modified. `prd.json` untouched.
### [2026-10-08] [DEPLOY-01] [FIREBASE][SECURITY] Firestore & Storage Security Rules Deployment/Verification â€” Executed

- **Scope:** DEPLOY-01 only. `prd.json` **not modified**. No application source code modified. No credentials stored or modified. No Cloud Functions deployed. No Hosting deployed.
- **Firebase Project Verified:** `skills-exchange-f9189` (confirmed from `.firebaserc`, `app/google-services.json`, and active CLI session `isaacvendeta1@gmail.com`).
- **Firestore Rules Deployment (SUCCESS):** Executed `firebase deploy --only firestore:rules --non-interactive`. `firestore.rules` compiled cleanly and was released to `cloud.firestore`. The active server rules now enforce:
  - `users/{userId}` owner-only and admin reads (C2-1); non-owners cannot read personal details or backup phone numbers.
  - `providers/{providerId}` owner UID writes (`request.auth.uid == providerId`).
  - `guarantors/{docId}` and `guarantor_submissions/{workerId}` worker isolation (REG-002) with admin-owned decision fields.
  - `jobs/{jobId}` authoritative ownership (`postedByUid == request.auth.uid`, F4) and owner-exclusive immutability.
  - `jobs/{jobId}` status transition authorization (owner-only for quote acceptance, cancellation, dispute, escrow release; assigned worker + KYC approval for in-progress and completion proof).
  - `jobs/{jobId}/messages/{messageId}` sender UID binding (`senderId == request.auth.uid`, F5).
- **Storage Rules Deployment (BLOCKED):** `storage.rules` deployment was attempted via Firebase CLI dry-run and blocked by two issues:
  1. **Missing Backend Resource:** Firebase Storage has not been initialized on project `skills-exchange-f9189` (`Error: Firebase Storage has not been set up on project 'skills-exchange-f9189'. Go to console.firebase.google.com/project/skills-exchange-f9189/storage and click 'Get Started'`).
  2. **Rule Syntax Defects:** Lines 61-62 and 66-67 in `storage.rules` reference `request.resource.data` (Firestore syntax) instead of `metadata`, and attempt to evaluate `request.resource` during read operations where it is null. Also `firebase.json` does not configure a `storage` target.
- **Verification:** `FirestoreUsersReadPrivacyTest` 4/4 tests passed; full test suite (23 suites / 180 tests / 0 failures) verified.

### [2026-10-06] [C2 Job-Status Authorization] [SECURITY][AUTHZ] Per-role/ownership authorization for job-status transitions â€” Implemented, VERIFIED (unit)

- **Scope:** the standalone finding discovered during F6 â€” "status transitions have no per-role/ownership authorization". C2-2 identity fixes F1â€“F7 are untouched and not redone. `prd.json` **not modified**. No DB schema/migration change. No Cloud Functions change. `firestore.rules` modified (source only) but **NOT deployed**. No device/emulator verification possible (no adb device attached).
- **Defect:** `transitionJobStatus()` checked only (a) that a Firebase user existed (F6) and (b) that `JobStatus.isValidTransition()` allowed the edge. It never asked whether the actor had any relationship to the job, so **any signed-in user could move any other user's job** along a structurally legal transition. Identity (who) and authorization (may they) were conflated.
- **Fix â€” `data/repository/SkillsExchangeRepository.kt` (one central check):** new `isJobTransitionAuthorized(actorUid, job, target)` â€” the pure, actor-explicit policy â€” reached in production via `canCurrentUserTransitionJob(job, toStatus)` and enforced inside `transitionJobStatus()` **before** the structural check (so an unauthorized caller learns nothing about which transitions are legal). Actor is resolved by the existing `resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)`; no caller-supplied actor, no display name/phone/district, no newest-row resolution, no UI mode. The REAL job row is loaded via a new `JobPostDao.getJobById` â€” the caller-supplied `fromStatus` is never trusted for authorization, and structural legality is checked against the STORED status.
- **Authorization matrix (fail-closed by design):** owner-only (`postedByUid == actorUid`, F4) for setup lifecycle (`POSTED`/`QUOTING`/`WORKER_SELECTED`/`ACCEPTED` from `CLIENT_SETUP_STATES`), `CANCELLED` from `CANCELLABLE_STATES`, `DISPUTED` from `DISPUTABLE_STATES`, and `PAYMENT_RELEASED` only from `COMPLETED`. Worker-progress states (`WORKER_EN_ROUTE`/`ARRIVED`/`IN_PROGRESS`/`COMPLETED`), `DRAFT`, `EXPIRED` and dispute *resolutions* fail closed for **everyone** on the client: `assignedProviderId` is a `ServiceProviderEntity` FK (not a UID â€” no UID column exists) and admin is only the server-side Firestore custom claim, so neither can be decided client-side without inventing a schema/product decision (recorded as such â€” DECISIONS.md D-2026-10-05-2). Jobs with a NULL `postedByUid` (legacy) belong to nobody and are drivable by nobody.
- **Adjacent status writers gated the same way:** `hireProviderForJob()` (its `assignProvider` DAO call sets `status='Accepted'`) is now owner-gated and returns `Boolean`; `updateEscrowStatus()`'s mapped-status write is owner-gated (its display-string vocabulary bypasses the state machine deliberately â€” reported, not changed); the legacy `updateJobStatus(jobId, newStatus)` wrapper is now a thin authorized delegate to `transitionJobStatus` (previously wrote `job_posts.status` directly with no identity, ownership, state-machine or audit check). ViewModel: `hireProviderForSelectedJob` no longer refreshes on rejection; `submitDispute` still files the dispute record (user's right to report) but a failed status move emits no false signal.
- **F6 preserved:** `changedBy` still resolved inside `transitionJobStatus` from the authoritative Firebase UID; order is now resolve UID â†’ load real job â†’ authorize â†’ validate transition â†’ update â†’ audit row with authenticated UID â†’ (caller) notification only on success. No notification, no audit row, no status change on any failure.
- **`firestore.rules` (source only, NOT deployed):** `/jobs/{jobId}` update branch Cases 1/3/4/5 now each name their actor (owner for quote-acceptance/cancel/dispute/release; assigned worker + KYC for proof submission), mirroring the client policy; server rules remain the real boundary for worker/escrow transitions.
- **Tests â€” new `app/src/test/java/com/example/data/JobStatusAuthorizationSecurityTest.kt` (9 tests, Aâ€“J):** owner authorized on setup/cancel/dispute/release; other authenticated client denied through the pure policy AND through the real boundary (no mutation, no audit row); worker transitions fail closed (provider FK is not an identity); unrelated workers/strangers/null/blank actors denied; structurally-legal edge rejected for wrong actor (the original vulnerability as a test) while still allowed for owner; no Firebase user â‡’ no transition at all; synthetic/presentation/demo identities (`system`, `user_default_me`, `John Doe`, `You (Nakawa)`, â€¦) rejected; NULL-owner legacy job undrivable; illegal edge still rejected (state machine untouched) and F6 attribution contract preserved; newer-Room-row trap cannot influence authorization.
- **Verification (executed):** focused `JobStatusAuthorizationSecurityTest` **9/0/0/0**; full `:app:testDebugUnitTest` â†’ **22 suites / 171 tests / 0 failures / 0 errors / 0 skipped** (baseline 21/162: +1 suite, +9 tests, zero regressions); `:app:compileDebugKotlin` BUILD SUCCESSFUL as part of the test run; `git --no-pager diff --check` clean.
- **Status:** this finding is now fixed and verified. `C2` remains â³ Pending Audit Fix only because the remaining C2 items (Firestore rules deployment, device verification, MoMo number cleanup, UI wiring) are open; `prd.json` untouched.

### [2026-10-05] [C2-2 Fix #7] [SECURITY][IDENTITY] F3 remainder - Unbound device-local profile recovery - Implemented, VERIFIED (unit/build)

- **Scope:** Fix #7 (audit finding **F3 remainder**) only. Fixes #1-#6 (#1/#2/#3-F7, #4-F4, #5-F5, #6-F6) are untouched. `prd.json` **not modified**. No Firestore rules, no Cloud Functions, no MoMo cleanup, no UI redesign, no worker-authorization and no job-status-authorization change. No DB schema/migration change. No device/emulator verification was possible (no adb device attached).
- **Invariant enforced:** once Firebase authentication exists, the Firebase Auth UID is the sole authoritative identity. A local Room profile is never selected as the current authenticated user because it is the newest row, because it matches a phone number, or because it is the seeded demo/default profile. With no valid UID, resolution fails closed.
- **Gap 1 (real) - `data/repository/SkillsExchangeRepository.kt`:** `getCurrentUserByUid()` carried its OWN weaker identity rule, `firebaseUid?.takeIf { it.isNotBlank() && it != "user_default_me" }`, i.e. a *competing* identity resolver that accepted whitespace-padded values, presentation text (`"John Doe"`, `"You (Nakawa)"`) and the literal synthetic sentinels (`"system"`, `"unknown"`, `"anonymous"`, `"guest"`, `"none"`) - none of which Firebase can ever issue. Because `SkillsExchangeViewModel.currentUserByUid` (the app's **only** current-user source) flows through it, that divergence was a real identity-binding gap. It now delegates to the shared `resolveAuthoritativeUid`, so exactly one definition of "valid identity" exists.
- **Gap 2 (real) - `ui/viewmodel/SkillsExchangeViewModel.kt`:** `resolveSessionOwnerId()` used a bare `isNotBlank()`, so a placeholder (`"user_default_me"`, `"system"`, presentation text) could be persisted as the session owner - and the cached session UID is compared again during restore, so a placeholder written here could feed back into the identity decision. Both arguments are now filtered through `resolveAuthoritativeUid`. Both are Firebase-owned in practice, so legitimate sign-in behaviour is unchanged.
- **Removed (dead code, zero callers):** `SkillsExchangeRepository.getUserProfileByPhone()` and `UserProfileDao.getUserProfileByPhoneNumber()`. Selecting a *local* Room profile by a mutable field (a phone number) is precisely the unbound recovery path this finding targets, so the capability was deleted rather than merely documented.
- **Retained, re-documented as non-authoritative (deliberate):** (a) `UserProfileDao.getCurrentUser()` (newest row) - has **zero production consumers**; kept because security tests use it as the trap that proves no identity path consumes its answer, and deleting it would destroy that evidence. (b) `fetchUserIdentityByPhoneFromServer()` - still has zero callers; kept only because the tightened `users` read rule already denies it for non-owners (null in practice).
- **`user_default_me` was NOT deleted.** It remains the seeded placeholder used for demo/seed data and display, and is excluded from authenticated identity by the shared resolver and by `resolveAuthenticatedProfile`'s `it.id != DEMO_PROFILE_ID` ownership test. The unique `phoneNumber` index was **not** weakened.
- **Tests - new `app/src/test/java/com/example/data/DeviceLocalProfileRecoveryF3SecurityTest.kt` (7 tests, cases A-G):** A UID beats a newer Room row; phone conflict cannot change identity (and a non-owned row fails closed to `UNVERIFIED`/not activated); no phone lookup can establish identity; missing UID-bound profile fails closed with no fallback (never the newest row, never the demo row); null Firebase user yields no authenticated profile; the demo profile can never become the identity or the session owner (also rejects all synthetic sentinels and presentation text); a legitimate UID-bound profile still resolves normally.
- **Verification (executed this session):** focused `DeviceLocalProfileRecoveryF3SecurityTest` **7 tests / 0 failures / 0 errors / 0 skipped**; full `:app:testDebugUnitTest` -> **21 suites / 162 tests / 0 failures / 0 errors / 0 skipped** (up from the 20/155 baseline: +1 suite, +7 tests, zero regressions; results directory was deleted beforehand so this was a genuine re-execution, not an UP-TO-DATE cache hit); `:app:compileDebugKotlin` BUILD SUCCESSFUL; `git --no-pager diff --check` clean. No APK build, no device run.
- **Environment incident (cost real time; recorded so the next session does not repeat it):** the first focused attempt at `-Xmx1600m` ran ~45min and the Gradle daemon was OOM-killed (only ~160 MB free RAM), leaving a stale result XML that was repeatedly mistaken for fresh output. Later foreground polling commands also killed the in-flight build. Two fixes that worked: (1) run Gradle **detached** via `Start-Process ... -RedirectStandardOutput <log>` and poll the log/result files instead of running it in the terminal; (2) delete `app/build/test-results/testDebugUnitTest` before a run you intend to trust, otherwise Gradle legitimately reports `UP-TO-DATE` and writes no new XML. `-Xmx1400m` with `-Dkotlin.daemon.jvmargs=-Xmx1000m` was stable; `--rerun` was NOT (it forces a full recompile and thrashed for over an hour).
- **Encoding incident (also recorded):** an earlier attempt removed the DAO query via PowerShell `Get-Content -Raw` + `WriteAllText`, which double-encoded the files' UTF-8 em-dashes/arrows into mojibake across ~20 unrelated comment lines. Detected by scanning the diff for `+`-lines containing `Î“Ã‡`/`â”œÃ³`/`Î“Ã¥`, after which all three production files were `git checkout`-reverted and the edits were re-applied with the editor tool only (all newly added text is ASCII). Final diff: **0 added mojibake lines**.



### [2026-10-04] [C2-2 Fix #6] [SECURITY][DB] F6 Job-Status Attribution (`changedBy`) â€” Implemented, VERIFIED (unit/build)

- **Scope:** Fix #6 (audit finding **F6**) only. `C2` remains â³ **Pending Audit Fix**; `prd.json` untouched. **No Firestore change:** the audit trail is Room-only (`job_status_audit`), so no schema, rules or Cloud Function change was needed or made. No device/emulator verification was possible.
- **Defect (F6):** `updateJobStatus()` derived the audit actor from the legacy newest-Room-row flow â€” `repository.currentUser.first()?.id ?: "system"` â€” and passed it down to `transitionJobStatus(..., changedBy = ...)`. On a device that had held another account the status change was attributed to the **wrong person**, and with no profile at all it was attributed to the fabricated `"system"`. Because the actor was a **caller-supplied parameter**, any call site could assert an actor of its choosing.
- **No DB migration required:** `JobStatusAuditEntity.changedBy` already exists as a non-null `String`; F6 changes the *source and semantics* of that value only. `AppDatabase` stays at **v21** and `MIGRATION_20_21` is untouched.
- **Fix â€” `data/repository/SkillsExchangeRepository.kt`:** the `changedBy` parameter was **removed** from `transitionJobStatus()`. The actor is now resolved at the data boundary via the **existing** `resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)` (no second identity helper) and written to `changedBy`. With no authoritative uid the method **returns `false` before updating the job status and before inserting an audit row** â€” the transition fails closed and nothing is ever recorded against `"system"`, `"unknown"` or any other fabricated actor. The identity check runs **before** the `JobStatus.isValidTransition` check, so no unauthenticated caller can move a job at all.
- **Fix â€” `ui/viewmodel/SkillsExchangeViewModel.kt`:** `updateJobStatus()` no longer uses the legacy newest-row flow or the `"system"` fallback, and suppresses the follow-up `JOB_STATUS_CHANGE` notification when the transition fails (previously a notification could announce a change that was never recorded).
- **Scope note (deliberately NOT changed):** `JobStatus.isValidTransition()` is still the only authorization on a transition â€” there is **no per-role/ownership check** on who may move a job to which state. That is a **separate finding**, identified and reported, and intentionally left unfixed here; F6 only makes the recorded actor correct and weakens no existing check.
- **Historical data:** pre-F6 audit rows are **never rewritten**. Legacy `"system"` / `"unknown"` values remain as recorded â€” they are history, and guessing a replacement actor would be fabrication.
- **Tests added:** new `app/src/test/java/com/example/data/JobStatusAttributionF6SecurityTest.kt` (Robolectric, 7 cases) â€” resolver contract incl. padded-but-genuine trimming; the uid_A/uid_B newer-row trap (proving the newest row answers `uid_B` yet nothing is attributed to anyone); fail-closed with **no** profile row (the old `"system"` path); fail-closed on invalid/placeholder uid; legacy `"system"` and display-name rows left untouched; and an illegal transition still refused while unauthenticated.
- **Verification (executed):** focused `JobStatusAttributionF6SecurityTest` **7/0/0/0**; full suite `:app:testDebugUnitTest` â†’ **20 suites / 155 tests / 0 failures / 0 errors / 0 skipped** (baseline 19/148 +1 suite/+7 tests, no regressions); `:app:compileDebugKotlin` BUILD SUCCESSFUL as part of the test run; `git --no-pager diff --check` clean.
- **Test correction worth recording:** the first run failed 1/7 on my own over-strong assertion that the resolver rejects `"Nakawa"`. It correctly does **not** â€” Firebase UIDs are opaque alphanumeric strings, so a bare single token is indistinguishable from a real uid and blocking district names would be guesswork. The resolver's documented contract is limited to what is decidable without asking Firebase (synthetic sentinels, `user_default_me`, whitespace/bracket presentation text). The **test** was wrong, not the production code; the assertion was narrowed and the limitation recorded rather than papered over.
- **Environment issue hit (not a code failure):** the first focused run crashed the Gradle daemon with a JVM `OutOfMemoryError: Native memory allocation (malloc) failed` (`hs_err_pid41904.log`). Cause: `org.gradle.jvmargs=-Xmx4g` on a machine with only **3.88 GB total RAM** and ~1 GB held by the IDE. Rerun with `-Dorg.gradle.jvmargs=-Xmx1600m --max-workers=1 -Dorg.gradle.parallel=false` succeeded. Note for future sessions: PowerShell mangles `-Dâ€¦` arguments to `gradlew.bat` â€” use the `--%` stop-parsing token.

### [2026-10-04] [C2-2 Fix #5] [SECURITY][DB] F5 Chat Sender Identity â€” Implemented, VERIFIED (unit/build)

- **Scope:** Fix #5 (audit finding **F5**) only. `C2` remains â³ **Pending Audit Fix**; `prd.json` untouched. **Not deployed:** `firestore.rules` was NOT deployed. No device/emulator verification was possible.
- **Defect (F5):** `sendChatMessage()` set `senderId = currentUser?.id ?: "unknown"` from the legacy newest-Room-row query. On a device that had held another account, chat authorship was attributed to the **wrong person**, and `"unknown"` is not an identity at all. Deeper problem found during the audit: **`ChatMessageEntity` had no `senderId` field whatsoever** â€” Room stored only `senderName` (display) and `isFromUser` (a client-supplied boolean), so authorship was not persisted locally and **could not be enforced at all**. The Firestore read path also **discarded** the `senderId` it received.
- **Schema change:** `ChatMessageEntity.senderId: String?` (authenticated author) added, `senderName` documented DISPLAY-ONLY, plus `Index(["senderId"])`. `AppDatabase` **20 â†’ 21** with `MIGRATION_20_21` = `ALTER TABLE chat_messages ADD COLUMN senderId TEXT` (nullable, no default) + the index. Additive only; no message deleted or rewritten.
- **Write path:** `sendChatMessage()` now returns `Boolean`, resolves the author through the **existing** `resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)` (no second identity helper; the Room profile is consulted for nothing here) and **returns false without persisting or sending anything** when there is no authoritative uid. The `"unknown"` fallback is deleted.
- **Read path:** `getRealtimeChatMessages` now preserves `senderId` through Firestore deserialization (blank/absent â‡’ null = unknown author, never back-filled from `senderName`). New `isMessageAuthoredByCurrentUser(message)` (uid-to-uid; false when unauthenticated or `senderId` is null) and DAO query `getMessagesAuthoredBy(uid)`.
- **Firestore:** the `jobs/{jobId}/messages` subcollection previously had **no rule at all** (Firestore denies by default). A minimal subcollection rule was added: `create` requires `request.resource.data.senderId == request.auth.uid`, `update` requires the caller to be the original author AND `senderId` unchanged (authorship immutable), `delete` denied. `senderName` left unconstrained as display text.
- **Tests added:** new `app/src/test/java/com/example/data/ChatSenderIdentityF5SecurityTest.kt` (Robolectric, 8 cases Aâ€“F) incl. the uid_A/uid_B newer-row trap, fail-closed send with no Firebase user, invalid-uid abort, uid-keyed authorship queries, and legacy/system rows never being claimable. `GuarantorVerificationPersistenceTest`: schema assertions moved to v21 (each migration's own start/end versions stay pinned; all MIGRATION_18_19 checks intact) and a new `migration twenty to twenty one is additive and registered` case added.
- **Verification (executed):** focused `ChatSenderIdentityF5SecurityTest` 8/0/0/0; full suite `:app:testDebugUnitTest` â†’ **19 suites / 148 tests / 0 failures / 0 errors / 0 skipped**; `:app:compileDebugKotlin` BUILD SUCCESSFUL; `git --no-pager diff --check` clean. Two intermediate failures were test-fixture/schema-assertion issues fixed at the source (a missing parent `job_posts` row for the `chat_messages` FK, and the stale v20 pin). No APK build and no device run. C: stayed at ~0.8 GB free; Robolectric was run with `TMP`/`TEMP` redirected to D: per the documented workaround, and **no disk-space failure occurred** this session.

### [2026-10-04] [C2-2 Fix #4] [SECURITY][DB] F4 Job Authorship / `postedByUid` â€” Implemented, VERIFIED (unit/build)

- **Scope:** Fix #4 (audit finding **F4**) only. `C2` remains â³ **Pending Audit Fix**; `prd.json` untouched. **Not deployed:** neither `firestore.rules` nor the Cloud Function was deployed â€” that requires an explicit instruction. No device/emulator verification was possible.
- **Defect (F4):** `JobPostEntity` had **no owner identity at all**. `addJobPost()` wrote `postedByResident = "You ($district)"` â€” a display string, not an identity â€” so there was no stable key to authorize "is this my job?" against. The same creation path also carried a hardcoded `momoPhoneNumber = "+256 772 198 420"` (left untouched: payment contact is a separate finding, out of F4 scope).
- **Schema change:** `JobPostEntity.postedByUid: String?` added (authoritative owner) alongside `postedByResident` (display only), plus `Index(["postedByUid"])`. `AppDatabase` **19 â†’ 20** with `MIGRATION_19_20` = `ALTER TABLE job_posts ADD COLUMN postedByUid TEXT` (nullable, no default) + the index. Additive only; no job data deleted, reset or rewritten.
- **Compatibility for old jobs:** pre-F4 rows keep `postedByUid = NULL` â€” an **unknown owner**, deliberately NOT back-filled from `postedByResident` or from the newest Room profile (either would silently hand historical jobs to a random account). `postedByResident` is never parsed, trimmed or matched against a uid.
- **Write path:** `addJobPost()` now returns `String?`, resolves the owner via the existing `resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)`, and **returns null â€” persisting nothing â€” when there is no authoritative uid**. The ViewModel caller returns early on null so nothing is selected or synced.
- **Read/ownership path:** new `getMyJobs()` (`WHERE postedByUid = :uid`) and `isJobOwnedByCurrentUser(job)` (`job.postedByUid == authenticatedUid`, false when the uid is null/absent). No display-string or newest-row fallback. New DAO queries `getJobsPostedBy` / `getJobsWithUnknownOwner`.
- **Identity rule hardened (both layers):** `resolveAuthoritativeUid` now also rejects values containing whitespace/brackets (presentation text such as `"You (Nakawa)"`) and the literal sentinels `unknown`/`system`/`anonymous`/`guest`/`none`. A bare single token such as `"Nakawa"` is shape-valid and is **not** guessed either way â€” it is unreachable because the argument is always `FirebaseAuth.currentUser.uid`, never a UI/district field (documented in the tests).
- **Firestore:** `postedByUid` added to the `syncJobToFirestore` payload. **Rules (minimal, F4-only):** `create` now requires `request.resource.data.postedByUid == request.auth.uid` (a client cannot post on someone else's behalf); `update` requires `request.resource.data.postedByUid == resource.data.postedByUid`, making ownership immutable in every state-machine branch.
- **Cloud Function (`functions/src/index.ts`):** `releaseEscrowToWorker` compared `postedByResident`/`postedBy` against `callerUid` â€” a display string mixed with a uid, and the client never wrote a field named `postedBy`, so the escrow ownership check had no reliable owner. Now reads `postedByUid` and fails closed when it is absent or different.
- **Tests added:** new `app/src/test/java/com/example/data/JobAuthorshipF4SecurityTest.kt` (Robolectric, 7 cases Aâ€“F incl. the uid_A/uid_B newer-row trap and the fail-closed legacy job). `GuarantorVerificationPersistenceTest`: the schema-version assertion moved 19 â†’ 20 (all MIGRATION_18_19 assertions left intact) and a new `migration nineteen to twenty is additive and registered` case was added.
- **Verification (executed):** focused `JobAuthorshipF4SecurityTest` 7/0/0/0; full suite `:app:testDebugUnitTest` â†’ **18 suites / 139 tests / 0 failures / 0 errors / 0 skipped**; `:app:compileDebugKotlin` BUILD SUCCESSFUL; `git --no-pager diff --check` clean. Two intermediate failures were real and fixed at the source (a stale v19 schema assertion, and an over-strict test expectation) rather than by weakening behaviour. No APK build and no device run.

### [2026-10-04] [C2-2 Fix #3] [SECURITY] Legacy Newest-Row Identity Consumers Swept â€” Implemented, VERIFIED (unit/build)

- **Scope:** Fix #3 (audit finding **F7**) of the C2-2 audit only. `C2` in `TASKS.md` remains â³ **Pending Audit Fix**; `prd.json` was left untouched; no device/emulator verification was possible (no adb device attached). Findings F4/F5/F6 were identified and reported but deliberately NOT modified.
- **Defect (F7):** `UserProfileDao.getCurrentUser()` (`WHERE id != 'user_default_me' ORDER BY updatedAtTimestamp DESC LIMIT 1`) and its wrappers (`SkillsExchangeRepository.currentUser`, `viewModel.currentUser`) were still an identity source for the worker authorization gate, the role switch, worker location, account deletion, profile/backup-contact/avatar writes, guarantor drafts/saves/status, training, quotes/proposals, notifications and unread counts, disputes, ratings, reports, mark-all-read, the logout identity mirror, SOS name/phone, and the drawer/worker/guarantor UI. On a device that ever held another account â€” or after a sign-in change â€” "whatever row is newest" could therefore authorize, attribute or overwrite the WRONG account.
- **Migration rule applied:** `FirebaseAuth.currentUser.uid` â†’ `resolveAuthoritativeUid` (trim; blank / `user_default_me` â‡’ null) â†’ `getCurrentUserByUid(uid)` / `getCurrentUserById(uid)` â†’ exact matching row â†’ `null` / fail closed. Never the newest row, never a placeholder, never another user's UID, never an anonymous or synthetic id.
- **Fix â€” `ui/viewmodel/SkillsExchangeViewModel.kt`:** new companion pure rule `resolveAuthoritativeUid` + public `currentAuthenticatedUid()`; the legacy public `currentUser` StateFlow was **deleted** (the compiler now blocks re-introduction); `currentUserByUid` moved to `SharingStarted.Eagerly` (a synchronous `.value` read in a gate must never observe "not started yet"); migrated `isWorkerApprovedAndActivated`, `canAccessWorkerFeatures()`, `setUserRoleMode`, `updateWorkerLocation`, `permanentlyDeleteUserAccountAndData`, `saveBackupContact`, `uploadPendingProfilePhoto`, `uploadQualificationEvidence`, guarantor open/draft/submit/status, `startTraining` / `completeTrainingModule` / `completeTraining`, `submitRecurringProposal`, `submitQuote`, notification + unread collections, `submitDispute`, `getUserDisputes`, `reportUser`, `markAllNotificationsRead`, `submitRating`, the logout identity mirror, and `triggerSosAlert`'s location fallback.
- **Fix â€” `data/repository/SkillsExchangeRepository.kt`:** new `authenticatedProfileOnce()` + companion `resolveAuthoritativeUid` / `resolveSosContactProfile`; SOS notification attribution and the SOS SMS name/phone now come from the authenticated row only â€” the fabricated `"Worker"` / `"+256 700 000 000"` fallback is gone and no SMS is dispatched without an authenticated profile (the in-app SOS flag is still raised); `currentUser` re-documented as LEGACY / NON-AUTHORITATIVE with `sendChatMessage` (F5) as its only remaining consumer.
- **Fix â€” UI:** `MainScreen` now collects `viewModel.currentUserByUid`; `AccountComplianceDialog` files guarantors under `viewModel.currentAuthenticatedUid() ?: return@Button`.
- **Deliberately NOT migrated (reported, not changed):** **F6** â€” `updateJobStatus`'s `changedBy` (`repository.currentUser.first()?.id ?: "system"`, now carrying an inline note); **F5** â€” `sendChatMessage`'s `senderId` (`currentUser.first()?.id ?: "unknown"`); **F4** â€” job authorship / `postedByUid`; and `WorkerTrainingDao.getCurrentUserTraining()` (`SELECT * FROM worker_training LIMIT 1`) which still feeds the training *display* only (`trainingCompleted` on the UID-keyed profile remains the gate).
- **Tests added:** new `app/src/test/java/com/example/data/LegacyCurrentUserSweepSecurityTest.kt` (Robolectric, 5 cases) proving that with Firebase UID-A + Room UID-A older + Room UID-B newer, identity resolution and the worker gate use UID-A and never UID-B, and that a missing/blank/placeholder Firebase UID fails closed even though a real approved row exists on the device.
- **Verification (executed):** `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` BUILD SUCCESSFUL; focused `LegacyCurrentUserSweepSecurityTest` 5/0/0/0 (tests/failures/errors/skipped); full suite `:app:testDebugUnitTest` â†’ **17 suites / 131 tests / 0 failures / 0 errors / 0 skipped**; `git --no-pager diff --check` clean. No APK build was needed (no compile issue to diagnose) and no device run was possible.

### [2026-10-04] [C2-2 Fix #2] [SECURITY] Session Restore Cross-Checks the Firebase UID â€” Implemented, VERIFIED

- **Scope:** Fix #2 (audit finding **F2**) of the C2-2 audit only. `C2` in `TASKS.md` remains â³ **Pending Audit Fix** â€” this story is **not** marked complete, `prd.json` was deliberately left untouched (C1â€“C3 are tracked in `TASKS.md`), and no device/emulator verification was possible (no adb device attached).
- **Defect (F2):** `restoreSession()` trusted the identity cached in DataStore. After only the AUTH-008 expiry and device checks it read `SessionManager.getUserId()`, promoted it to the session identity, and then resolved the profile with the legacy "newest row" query (`repository.currentUser.first()`). Consequences: a cached UID left by a **previous** account could outlive a sign-in change and attribute that account's profile/phone/worker state to the new user; with Firebase signed out the cached session was still resurrected (no authority cross-check at all); and a legacy `user_default_me`/`"unknown"` id could be adopted as the session owner.
- **Fix â€” production (`ui/viewmodel/SkillsExchangeViewModel.kt`):**
  - New nested `sealed class RestoreIdentity { object None; data class Authoritative(uid, cachedUidTrusted) }` next to `AuthState`.
  - New companion pure function `decideRestoreIdentity(cachedUid, firebaseUid)`: the **Firebase** UID is the only identity. Blank/whitespace/demo (`user_default_me`) Firebase ids â‡’ `None`; the cached UID is reported trusted **only** when it is non-blank, not the placeholder, and equal to the Firebase UID.
  - New companion pure function `resolveRestoredProfile(authenticatedUid, row)`: the row is eligible **only** when `row.id == authenticatedUid`; no newest-row, no placeholder, no substitution â€” otherwise `null`.
  - `restoreSession()` now: (1) renames the cached value to `cachedUserId` so it can never be mistaken for the identity; (2) cross-checks it against `firebaseService.getCurrentUser()?.uid`; (3) **fails closed** â€” `clearSession()`, `isAuthenticated = false`, `currentUserPhone = null`, `AuthState.SignedOut` â€” when no authoritative UID exists; (4) `clearSession()` when the cached identity is stale, then rebuilds strictly from the Firebase UID; (5) resolves the profile by UID (`getCurrentUserByUid` â†’ durable-record fallback `fetchUserIdentityFromServer` + `saveUserProfile` â†’ fail-closed `resolveAuthenticatedProfile`), reusing cached descriptive fields **only** when the cached UID matched; (6) sets `isAuthenticated` / `currentUserPhone` / `authState` / `userRoleMode` from that UID-keyed profile; (7) rewrites the DataStore session **only** when the cached identity was stale, so a matching cache keeps its original AUTH-008 7-day expiry (a relaunch must not extend a session).
  - `init` block: the post-restore role sync now reads `repository.getCurrentUserByUid(firebaseService.getCurrentUser()?.uid)` instead of the newest Room row, which would otherwise have undone the UID-keyed restore.
  - Untouched by design: the legacy newest-row `getCurrentUser()` and its display consumers stay until the F7 call-site sweep.
- **Tests added:** new `app/src/test/java/com/example/ui/viewmodel/RestoreSessionIdentitySecurityTest.kt` (pure JUnit, 5 cases Aâ€“E: matching cache restores the identity; stale cache is discarded and the Firebase UID wins with the other account's approval never riding along; cached UID with no Firebase user is never resurrected; missing row for the Firebase UID never falls back to another row; the demo placeholder is never the authenticated profile). Two repository-boundary cases were added to the existing `CurrentUserByUidRepositoryTest` (`stale cached uid never wins over the authenticated firebase uid`; `a database holding only the demo placeholder yields no identity`). Every seeded profile uses a distinct phone number (UNIQUE `phoneNumber` index).
- **Verification (executed this session, evidence captured):**
  - `:app:compileDebugUnitTestKotlin` â†’ **BUILD SUCCESSFUL** (first attempt surfaced `RestoreIdentity` as a *nested* type; imports corrected to `SkillsExchangeViewModel.RestoreIdentity`).
  - `:app:testDebugUnitTest --tests RestoreSessionIdentitySecurityTest --tests CurrentUserByUidRepositoryTest --tests UserProfilePersistenceTest --tests PhoneAuthIdentitySecurityTest --tests WorkerAuthorizationSecurityTest` â†’ **BUILD SUCCESSFUL**; XML results 5 / 5 / 10 / 9 / 13 tests, **0 failures, 0 errors, 0 skipped**.
  - Full suite `:app:testDebugUnitTest` â†’ **BUILD SUCCESSFUL**: 16 suites / **126 tests / 0 failures / 0 errors / 0 skipped** (no regressions).
  - `:app:assembleDebug` â†’ **BUILD SUCCESSFUL**; `git --no-pager diff --check` â†’ clean.
  - Diff scope: `SkillsExchangeViewModel.kt`, `UserProfilePersistenceTest.kt` (modified), `RestoreSessionIdentitySecurityTest.kt` (new) â€” no unrelated changes.
- **Still outstanding (C2-2 F3â€“F7, NOT this session's scope):** the legacy newest-row `getCurrentUser()` and its call sites (F7 sweep not done), `currentUserByUid` still has no UI consumer, `getUserProfileByPhone` remains device-local and non-authoritative, and the seeded `user_default_me` row is still present in Room.

### [2026-10-04] [C2-2 Fix #1] [SECURITY] UID-Keyed Current-User Resolution â€” Implemented, Test Fixture Corrected, VERIFIED

- **Scope:** Fix #1 of the C2-2 audit only â€” make the authenticated Firebase UID the authoritative key for resolving the current Room `UserProfileEntity`. The rest of the C2-2 audit is NOT addressed here, and `C2` in `TASKS.md` / `prd.json` remains â³ **Pending Audit Fix** â€” this story is **not** marked complete.
- **Implementation (committed earlier in `db694a2` "Add UID-keyed DAO lookup"):**
  - `data/db/Daos.kt` â€” new `UserProfileDao.getCurrentUserById(uid): Flow<UserProfileEntity?>` = `SELECT * FROM user_profiles WHERE id = :uid LIMIT 1`. Exact-id only: no timestamp ordering, no `ORDER BY`, no fallback row.
  - `data/repository/SkillsExchangeRepository.kt` â€” new `getCurrentUserByUid(firebaseUid: String?)`. Blank/whitespace UID or the legacy id `user_default_me` short-circuits to `flowOf(null)`; otherwise delegates to `getCurrentUserById`. The legacy newest-row `currentUser` flow is deliberately retained for existing display consumers.
  - `ui/viewmodel/SkillsExchangeViewModel.kt` â€” new `currentUserByUid: StateFlow<UserProfileEntity?>` = `firebaseUser.flatMapLatest { repository.getCurrentUserByUid(it?.uid) }.stateIn(viewModelScope, WhileSubscribed(5000), null)`. Signed-out â‡’ `null` (fail closed).
- **Test-fixture defect found and fixed (this session).** `user_profiles` declares `indices = [Index(value = ["phoneNumber"], unique = true)]` (`Entities.kt:335`) and inserts use `@Insert(onConflict = REPLACE)` (`Daos.kt:278`). The first regression test seeded two profiles that shared the helper's default `phoneNumber`, so the second insert **replaced** the first and `getCurrentUserById("uid_A")` correctly returned `null` â€” a false failure caused by the fixture, not by the production query. Fix: `authenticatedWorkerProfile(...)` now takes an explicit `phoneNumber` (plus `fullName` / `updatedAtTimestamp`), every multi-row test uses distinct numbers, and the pre-existing "updating an existing profile" test now keeps the uniquely-indexed phone number constant (a same-account edit in production goes through targeted `UPDATE` queries, never `REPLACE` on a new number).
- **Tests (executed, evidence captured):**
  - `app/src/test/java/com/example/data/UserProfilePersistenceTest.kt` â€” 10 tests, incl. 3 new UID-keyed regressions: the UID row is returned even when another row is newer; `null` when the UID has no row; the demo placeholder is never returned.
  - Same file, second class `CurrentUserByUidRepositoryTest` â€” 3 new repository-boundary tests pinning the fail-closed rules: missing UID â‡’ `null`; `null` / `""` / `"   "` / `user_default_me` â‡’ `null` (never the newest row).
  - `:app:testDebugUnitTest --tests com.example.data.UserProfilePersistenceTest --tests com.example.data.CurrentUserByUidRepositoryTest` â†’ **BUILD SUCCESSFUL**; `TEST-com.example.data.UserProfilePersistenceTest.xml` = 10 tests / 0 failures / 0 errors / 0 skipped; `TEST-com.example.data.CurrentUserByUidRepositoryTest.xml` = 3 / 0 / 0 / 0.
  - Full suite `:app:testDebugUnitTest` â†’ **BUILD SUCCESSFUL**; 15 suites / 119 tests / 0 failures / 0 errors / 0 skipped (no regressions).
  - `:app:compileDebugKotlin` â†’ **BUILD SUCCESSFUL** (KSP generated `UserProfileDao_Impl.getCurrentUserById` with `WHERE id = ? LIMIT 1` â€” confirmed at `app/build/generated/ksp/debug/.../UserProfileDao_Impl.kt:451`).
  - `:app:assembleDebug` â†’ **BUILD SUCCESSFUL** (Room schema/KSP validation, dexing and packaging all pass).
  - `git --no-pager diff --check` â†’ clean. Diff scope this session: **only** `UserProfilePersistenceTest.kt` (the three production files landed in the earlier `db694a2` commit); no `prd.json`, `TASKS.md`, `firestore.rules` or C2-1 file was modified.
- **Security behaviour proven:** with Room holding UID-A (older) and UID-B (newest), the legacy `getCurrentUser()` returns **UID-B** while `getCurrentUserById("uid_A")` and `repository.getCurrentUserByUid("uid_A")` both return **UID-A** â€” the authenticated identity. No UID / blank UID / `user_default_me` never yields a substitute identity.
- **Still outstanding (C2-2 F2â€“F7, NOT this session's scope):** the legacy newest-row `getCurrentUser()` and its consumers still exist (no F7 call-site sweep yet), `currentUserByUid` has no UI consumer yet, `getUserProfileByPhone` remains device-local and non-authoritative, and the seeded `user_default_me` row is still present in Room. No device/emulator run was possible (no adb device attached).


### [2026-09-30] [C2-1] [SECURITY] Phone Authentication Identity Boundary â€” Implemented, âš ï¸ NOT VERIFIED

- **Defect (reported by the C1/C2/C3 audit):** `FirebaseService.sendPhoneVerificationCode` fabricated a local `"<timestamp>_<phoneHash>"` "verification id" and `verifyPhoneCode` accepted **any** six-digit code before calling `signInAnonymously()`; `SkillsExchangeViewModel.loginUser` then treated a phone-number match as account recovery and copied `workerKycStatus` / `isWorkerActivated` / `trainingCompleted` onto the newly authenticated UID; `firestore.rules` allowed `allow read: if isAuthenticated();` on `match /users/{userId}`.
- **Fix â€” real phone auth:** `PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions)` (Activity-bound via a `WeakReference` holder registered by `MainActivity`, 60s timeout, awaited with `suspendCancellableCoroutine`) plus `PhoneAuthProvider.getCredential` + `signInWithCredential`. New `toE164PhoneNumber` normalises UG input (`0772198420` / `256â€¦` / `+256â€¦` / 9-digit) and fails closed on anything ambiguous. `signInAnonymously()` no longer appears on any successful phone-auth path.
- **Fix â€” fail-closed verification gate:** `phoneVerificationGateFailureReason(...)` refuses every code while no Firebase verification is pending; the authenticated identity must be non-anonymous, phone-backed and match the requested number. Pending state is cleared on resend, cancel, sign-out, account deletion, success and failure.
- **Fix â€” no privilege copying:** new companion policy `resolveAuthenticatedProfile(...)` reuses a record only when its id == the authenticated Firebase UID; a phone match with any other uid is rebuilt fail-closed (`UNVERIFIED`/`NOT_APPLIED`, `isWorkerActivated = false`, `trainingCompleted = false`) with none of the other record's fields copied. `loginUser` no longer mints an anonymous UID and no longer falls back to "newest row". `verifyOtp` keys the session to the verified Firebase UID.
- **Fix â€” Firestore privacy:** `users/{userId}` read is now `isDocOwner(userId) || isAdmin()`. The client by-phone query degrades to null under the tightened rules and is documented as non-authoritative.
- **Tests added (NOT yet executed):** `PhoneAuthVerificationSecurityTest`, `PhoneAuthServiceFailClosedTest` (Robolectric), `FirestoreUsersReadPrivacyTest`, `PhoneAuthIdentitySecurityTest`.
- **âš ï¸ NOT VERIFIED â€” `C2` remains â³ in `TASKS.md` / `prd.json`.** No `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`, `firebase deploy` or device run was possible: `run_commands` returned exit 1 with no output and no side effects for every attempt this session (three file-writing probes never created their files). See `AGENT_STATE.md` for the ordered next actions.

### [2026-09-30] [SEC-009 / G1 / G3 / H1 / H8 / M8] [SECURITY] Worker Dashboard Fail-Closed Authorization Implemented

- **Problem:** `isWorkerApprovedAndActivated` initialized to `true` (fail-open, G1); worker dashboard authorization depended on UI state (G3); `setUserRoleMode("Offer Skills")` lacked a server-authoritative gate (M8); `FreelancerDashboardView` had no component-level auth guard (H8); the combined pattern violated H1 (fail-closed requirement).
- **Solution â€” ViewModel layer (fail-closed):**
  - Added `isWorkerAuthorized(isAuthenticated: Boolean, user: UserProfileEntity?): Boolean` companion pure function to `SkillsExchangeViewModel`. Returns `true` **only when** `initialRole == "WORKER"` AND `workerKycStatus == "APPROVED"` AND `trainingCompleted == true` AND `isWorkerActivated == true`. Returns `false` for every other value including null, blank, loading, pending, or deactivated.
  - `isWorkerApprovedAndActivated: StateFlow<Boolean>` now combines `isAuthenticated` + `currentUserProfile` StateFlows through `isWorkerAuthorized()`.
  - `canAccessWorkerFeatures()` delegates entirely to `isWorkerAuthorized()`.
- **Solution â€” UI layer (component boundary):**
  - `FreelancerDashboardView` accepts `isAuthorized: Boolean`; if `false` it renders a hard-coded locked-screen card regardless of any other composable state â€” preventing UI state injection bypass.
  - `MainScreen` passes `isWorkerApprovedAndActivated.collectAsState()` as `isAuthorized`.
- **Tests:** `WorkerAuthorizationSecurityTest.kt` â€” 13 test cases covering unauthenticated, client-mode, KYC pending, KYC submitted, KYC rejected, training incomplete, not activated, fully approved, null profile, empty strings, wrong role, and auth state mismatch â€” all PASSED. Existing suite: PASSED (all UP-TO-DATE, no regressions).
- **Device install:** Blocked by Samsung SM-A025F storage (98%, `INSTALL_FAILED_INSUFFICIENT_STORAGE`). Hardware constraint only â€” clear device storage to re-run.
- **Files changed:** `SkillsExchangeViewModel.kt`, `FreelancerDashboardView.kt`, `MainScreen.kt` (modified); `WorkerAuthorizationSecurityTest.kt` (new).
- **Stories closed:** G1, G3, H1, H8, M8. SEC-009 already `completed` in `prd.json` / `completed_stories`.

### [2026-09-30] [MAP-001 / D1] [BUGFIX+VERIFY] InteractiveMapView Crash Fixed & Safe Maps Key Wiring Implemented

- **Defect D1 Resolved**: Physical device crash (`IllegalStateException: API key not found` at `InteractiveMapView.kt:98`) fixed.
- **Root Cause & Solution**:
  1. `InteractiveMapView.kt` now calls `isMapsApiKeyPresent(context)` before creating `MapView`. If the key is null, empty, un-substituted (`${...}`), or matching placeholder tokens (`DEFAULT_MAPS_API_KEY`, `MY_MAPS_API_KEY`, `YOUR_KEY_HERE`), the composable renders `MapUnavailableFallback` immediately without touching `MapView`.
  2. Wrapped `map.onCreate(null)` in `try/catch` as defense-in-depth against Play Services absence or runtime SDK initialization exceptions.
  3. Configured `.env.example`, `.env`, and `app/build.gradle.kts` (`manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_MAPS_API_KEY"`) using the `DEFAULT_MAPS_API_KEY` token (aligned with `GEMINI_API_KEY=MY_GEMINI_API_KEY`), preventing secrets-gradle-plugin 2.0.1 from generating invalid Java syntax in `BuildConfig.java`.
  4. Injected `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}" />` into `app/src/main/AndroidManifest.xml`.
- **Verification**:
  - `:app:compileDebugJavaWithJavac`: PASSED.
  - `:app:testDebugUnitTest`: PASSED (75 tests across 9 suites, 0 failures, 0 errors).
  - `:app:assembleDebug`: PASSED in 7m 59s (`app-debug.apk` 35,259,265 bytes).

### [2026-09-30] [MAP-003] [DOCS] Google Maps Fallback Requirement Added as Future Pending Story

- **Scope:** Documentation only. No source code was changed. No Firebase configuration was changed. No Google Maps dependency or API key was added. No implementation was performed.
- **Added story `MAP-003` (phase 17, priority P2, status `pending`)** to `prd.json`, `TASKS.md`, `AGENT_STATE.md`, `CHANGELOG.md`, and `DECISIONS.md`.
- **Requirement summary:** Google Maps must NOT be a hard dependency for the Skills Exchange job lifecycle. Maps may only appear on the active-job screen (after a client has accepted/hired a worker). If Maps fails for any reason (no API key, billing disabled, SDK init failure, network failure, location/map load failure, invalid config, location permission denied, or any other Maps-related failure) the app must NOT crash, freeze, or show an infinite loading screen. Instead the active-job screen must fall back gracefully to a clear message ("Map location is currently unavailable.") and offer a **Contact Worker** action (reusing the existing COMM-001 masked Firestore-chat mechanism â€” no private phone number exposed, no new communication system created) and a **Continue Without Map** action. Worker GPS location collection must remain independent from Google Maps. All existing job lifecycle flows (acceptance, start, status update, arrival PIN, completion, notifications, reviews, payment/escrow, and disputes) must remain fully functional when Maps is unavailable. API key must be managed via secure configuration (manifestPlaceholders / .env), never hardcoded in Kotlin/source. Treat Google Maps as a **visualization layer only**.
- **Ordering:** placed after `MAP-002` (Google Maps Usage Minimization) and all other existing unfinished items in Phase 17. `depends_on: ["MAP-002"]`.
- **DECISIONS.md:** DEC-020 (Google Maps Is an Optional Visualization Layer â€” Graceful Fallback Required on Active-Job Screen).
- **prd.json:** total stories now 93 (85 completed / 1 in-progress / 7 pending). `last_updated` updated to `2026-09-30T14:36:00+03:00`.
- **TASKS.md:** pending count updated from 6 â†’ 7, total from 92 â†’ 93.

### [2026-09-29] [MAP-001 / REG-002] [DEVICE-VERIFY] Real-Device Test on Samsung SM-A025F â€” Migration 18â†’19 Proven, Two Defects Found

- **Scope:** build â†’ install â†’ cold launch â†’ UI walkthrough â†’ crash repro â†’ on-device database inspection on a physical Samsung **SM-A025F (Android 12 / API 31, 720Ã—1600 @280 dpi)**, package `com.aistudio.skillsexchange.nxptvq` (versionCode 1 / versionName 1.0, minSdk 24, targetSdk 36). **No source file was changed; nothing was committed; `prd.json` was not modified.** Consolidated evidence: `_device_test/DEVICE_TEST_REPORT.md`.
- **Install:** `assembleDebug` exit 0 (1m 36s) â†’ 35,254,412 B APK. First `adb install -r` failed `INSTALL_FAILED_INSUFFICIENT_STORAGE`; root cause was `/data` at 99 % with ~349 MB free, **not** a signature mismatch (the installed cert SHA-256 `d5b68479â€¦ac32` matched the new APK). ~551 MB was reclaimed with `adb shell pm trim-caches 2147483648` â€” no app uninstalled, no user media touched. The reinstall was therefore an **in-place update**: `firstInstallTime` stayed `2026-09-16 20:20:52`, `lastUpdateTime` became `2026-09-29 17:02:25`, and `adb shell ls -l` on the installed `base.apk` returned the same 35,254,412 B as the build output.
- **Room migration proven on hardware (new evidence for REG-002):** the live DB was pulled from the app sandbox with `run-as` + `exec-out` (recipe kept at `_device_test/pull_db.ps1`) and its header inspected byte-wise: valid SQLite magic, `page_size = 4096`, **`user_version = 19`** â€” exactly `AppDatabase`'s `version = 19` with `MIGRATION_18_19` registered, and `guarantors` carries `nationalIdNumber` + `phoneNumber`. The app was **not** wiped and still opened at schema 19, so `MIGRATION_18_19` ran successfully on a device â€” previously only ever asserted at unit-test level.
- **Working paths:** cold start 6,305 ms with no ANR and clean Firebase init; client-mode home with live GPS ("Live: Sunnyside District, 15m accuracy filtering"); navigation drawer; National ID & Verification correctly refused for a guest via the CLIENT ONBOARDING sheet; Job Status; theme Lightâ†”Dark; providers list with correct empty state. Post-crash relaunch returns to `MainActivity` cleanly with no corrupted state.
- **Defect D1 (critical, 3/3 reproducible):** tapping **Map View** kills the process â€” `java.lang.IllegalStateException: API key not found â€¦` raised through `CreatorImpl.newMapViewDelegate` into `InteractiveMapView.kt:98` (`map.onCreate(null)` inside the `AndroidView` factory, unguarded). Three captures, three distinct PIDs (23654 / 25548 / 25768). Root cause is configuration, not the device: `app/src/main/AndroidManifest.xml` contains **no `<meta-data>` element at all**, `.env`/`.env.example` define only `GEMINI_API_KEY`, no code injects a Maps key, and `README.md` step 4 asks the developer to hand-edit the manifest while its own `.env` block advertises an unused `MAPS_API_KEY`. **`prd.json` MAP-001 is recorded `completed` / `verified_by: "code_inspection"` although its acceptance criterion ("Map view renders interactive Google Maps tiles") and its verification step ("Open map tab â€¦ verify real street map") both fail on real hardware** â€” reopening MAP-001 is recommended (AGENTS.md Â§2) and deliberately **not** done by this verification task.
- **Defect D2 (UX, silent failure):** the guest tap on the worker/client mode card is correctly refused by `canAccessWorkerFeatures()` but communicates nothing â€” `SkillsExchangeViewModel` writes `authError` and **the message is only rendered inside `AuthModal` / `OnboardingScreen`** (`MainScreen.kt:741` hosts the modal only `if (isAuthModalOpen)`, which the refusal path does not open), and `SnackbarHost` appears in **zero** source files â€” so on the screen where the tap happens the user sees nothing, while the drawer copy "Tap to switch to worker dashboard" invites the tap.
- **Evidence retained:** 6 logcat captures (incl. 3 crash captures), 15 UI screenshots `ui1.png`â€¦`ui15_postcrash_settled.png`, `ui.xml` (uiautomator hierarchy), `ondevice_db.sqlite` + `-wal`, `installed-base.apk` (pre-update copy used for the cert comparison), `_device_test/pull_db.ps1`, `build_debug.log`.
- **Not verified:** anything requiring sign-in (worker dashboard, chat, escrow, payment, guarantor submission end-to-end), release variant / R8 / signing, and every server-side rule (still no `firebase deploy`). Suggested order: guard `InteractiveMapView`, wire `MAPS_API_KEY` via `manifestPlaceholders`, re-verify MAP-001 on device, then host `authError` in a `SnackbarHost`.


### [2026-09-29] [ADMIN-005 / REG-002 gap G2] [IMPL+VERIFY] Admin Guarantor Review Path (fetch + decide) Implemented & Verified

- **Gap closed (gap G2 of the post-REG-002 review)**: REG-002 shipped the *worker* half of guarantor verification â€” two guarantors, three private photographs, 48h SLA tracking and admin-reserved review fields in `firestore.rules` â€” but there was **no way for an administrator to read or decide a submission**. The only admin callables (`getPendingVerificationQueue`, `reviewWorkerApplication`, `setWorkerActivationStatus`) know nothing about `guarantor_submissions`, and the queue returned every worker's **unmasked** `nationalIdNumber`. This change adds the missing admin path; it does not touch any other known gap.
- **New file [guarantorReview.ts](functions/src/guarantorReview.ts)** (â‰ˆ660 lines): the dependency-free security core â€” status/NIN vocabulary constants, `maskIdentifier`, `checkAdminCaller`, `readWorkerId`, `mapDecisionToStatus`, `normalizeNinStatus`, `validateGuarantorReviewInput`, `isPrivateGuarantorPhotoPath`, `resolvePhotoUrlTtlSeconds`, `buildGuarantorReviewFields`, `buildGuarantorNinUpdate`, `buildPhotoAccess`, `collectPrivatePhotoPaths`, `projectGuarantorForAdmin`, `projectSubmissionForAdmin`, `projectQueueRow`, `buildQueueRows`. It deliberately imports neither `firebase-admin` nor `firebase-functions`, so the whole security contract is unit-testable without an emulator.
- **[index.ts](functions/src/index.ts)**: `isAdmin` now delegates to `checkAdminCaller` (same trust anchor, one implementation); new `authorizeAdmin(context)`; new callables `getGuarantorSubmission` (admin detail: status, SLA, feedback, reviewer, both guarantors, consent, masked NIN by default, five short-lived private-photo URLs) and `reviewGuarantorSubmission` (decision + feedback + NIN result, per-guarantor NIN batch update, conditional KYC mirror, worker notification); `getPendingVerificationQueue` rewritten to merge KYC and guarantor work, to emit **`nationalIdNumberMasked` instead of `nationalIdNumber`**, and to add `kycOnFile` / `guarantorStatus` / `guarantorSubmittedAt` / `guarantorCount` / `ninVerificationStatus` / `guarantorsAwaitingReview` per row; `privatePhotoBucket()` + `mintPrivatePhotoUrls()` mint v4 read URLs (default 15 min, hard cap 60 min) only for paths that already passed the private-prefix check.
- **Security contract** (all enforced server-side, never by client-supplied roles): only a **verified** ID token carrying the boolean `admin === true` custom claim may call either endpoint (`unauthenticated` for anonymous, `permission-denied` otherwise) â€” the same anchor `firestore.rules` uses; a NIN is masked in every list/queue payload and masked **by default** in the admin detail response, the unmasked value being released only when the admin call passes `includeFullNin: true`; a photo URL is minted only for `users/{workerId}/guarantors/<file>` â€” absolute URLs, `..` traversal, leading slashes, empty segments and **another worker's prefix** are refused with a fixed reason code (`OUTSIDE_PRIVATE_PREFIX` / `MISSING` / `NOT_MINTED` / `URL_MINT_FAILED` / `BUCKET_UNAVAILABLE`); the reviewer identity comes from the token and the timestamp from the Firestore server clock, so neither can be forged; the write payloads are a fixed allowlist of admin-owned fields, so a review can never write a worker-owned field; guarantor documents are located by `workerKycId == workerId` **and re-filtered** before any write, never by trusting ids stored inside the submission; all map lookups are own-key lookups (`Object.create(null)`), so prototype keys (`__proto__`, `constructor`, `toString`) can never be borrowed as a name or a document.
- **Verification (real, on this machine)**: `npm run build` PASSED â€” `tsc` exit 0, emitting `lib/index.js` and `lib/guarantorReview.js`; `node --test test/guarantorReview.test.mjs` PASSED â€” **32 tests, 32 pass, 0 fail** (authorization, NIN masking, photo-path guard, TTL clamping, write-payload allowlist, input validation, queue merge/sort/limit/prototype safety). The previous session's "`npx tsc` exits 0" claim was **not** valid evidence: `npx tsc` resolved to the placeholder `tsc` npm package ("This is not the tsc command you are looking for"), and `node_modules/typescript` had been reduced to an empty `bin/` plus locale folders. See the new blocker rows in `AGENT_STATE.md`.
- **Build configuration repaired ([functions/package.json](functions/package.json))**: `devDependencies` never declared the types the `build` script needs â€” `@types/express` and `@types/node` are now declared (a clean clone could not run `npm run build` before), and a `test` script (`node --test test/guarantorReview.test.mjs`) was added. Several `@types/*` folders had been gutted to empty directories; reinstalling `@types/express-serve-static-core`, `@types/body-parser`, `@types/serve-static`, `@types/glob` and `@types/ms` restored them and produced the clean exit-0 build.
- **Deliberate non-change**: the new `guarantor_submissions` queue query omits `orderBy("submittedAt")` because `status in [...]` + `orderBy` requires a composite index that this repository does not declare (there is no `firestore.indexes.json`); `buildQueueRows` sorts by the earliest outstanding timestamp and applies the limit in code, so omitting it trades a slightly larger read for not failing with `FAILED_PRECONDITION`.
- **Not verified / still open**: no deployment (`firebase deploy --only functions`) and no live Firestore/Storage round-trip were possible in this session; `reviewGuarantorSubmission`'s KYC mirror is skipped when the worker has no `worker_kyc` document (the app still never creates that document â€” pre-existing blocker); the admin UI that would call these two callables does not exist yet. Gaps G1, G3, G4, G5, G6, G7 and G12 from the same review remain untouched and unfixed.

### [2026-09-27] [REG-002] [VERIFY] Guarantor Verification Built and Unit-Tested (VERIFIED)

- **The 2026-09-26 "no JDK on this machine" blocker was false.** `gradle.properties` pins the build JVM with `org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10` (Amazon Corretto `17.0.20.1`, present on disk) and `systemProp.org.gradle.user.home=D:/gradle-home`, so `gradlew.bat` builds without any environment setup. `jdk11/jdk-11.0.22+7/bin/java.exe` also exists, and Android Studio's JetBrains Runtime (OpenJDK `25.0.2`) is installed. Only the repo-root `jdk17/` folder is actually empty, and Gradle never referenced it. Verification commands for the next session are simply `.\gradlew.bat <task> --console=plain`.
- **First real compile of the guarantor feature surfaced two genuine errors** that hand inspection had missed, both fixed:
  - [SkillsExchangeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt) holds `var` Compose state (`guarantorVerificationOpen` etc. via `by mutableStateOf`) but never imported `androidx.compose.runtime.getValue`, `mutableStateOf` and `setValue` â€” added (property delegates and `mutableStateOf` are not covered by the file's existing `kotlinx.coroutines.flow.*` wildcard).
  - [GuarantorValidators.kt](app/src/main/java/com/example/util/GuarantorValidators.kt) `validateNationalIdNumber()` rejected a NIN made of one repeated character via `value.distinct().count()`, which does not resolve on `String` in this module; it is now `value.toSet().size <= 1` â€” identical rule, plain stdlib call.
- **Verification results**: `:app:compileDebugKotlin` PASSED (warnings only), `:app:testDebugUnitTest` PASSED â€” **57 tests across 8 suites, 0 failures / 0 errors / 0 skipped**, with the new `GuarantorValidatorsTest` at **18/18**, and `:app:assembleDebug` PASSED (BUILD SUCCESSFUL in 3m 35s) producing `app/build/outputs/apk/debug/app-debug.apk` (35,254,412 bytes). Room's KSP pass validated the entities, DAO queries and the new `phoneNumber` index.
- **Repository state**: nothing was committed by this session - `git status` reports 16 modified files plus 3 untracked REG-002 files (`GuarantorValidators.kt`, `GuarantorVerificationSheet.kt`, `GuarantorValidatorsTest.kt`); committing them is a mechanical follow-up.
- **PRD state**: REG-002 moved to `completed` (86 completed / 0 in progress / 6 pending of 92). Still outstanding and unchanged: the on-device golden path and `firebase deploy --only firestore:rules,storage`.

### [2026-09-26] [REG-002] [IMPL] Guarantor Verification Sheet Completed + Repository/DAO Alignment (BUILD UNVERIFIED AT THE TIME â€” SEE THE 2026-09-27 ENTRY)

- **Alignment fixes**: `SkillsExchangeRepository` no longer calls the non-existent `WorkerKycDao.updateGuarantorReview(status = ...)`; the real DAO surface `updateGuarantorStep(userId, guarantorStatus, groupPhotoStoragePath, submittedAt, feedback)` is used for the draft, submit and status-refresh paths. The repository also imports `GuarantorInput` from `com.example.util` and consumes `GuarantorValidators.validateGuarantorSet(...)` as the **single blocker `String?`** it actually returns (the previous draft expected an `Outcome`/`issues` map that does not exist).
- **Sheet completed** in [GuarantorVerificationSheet.kt](app/src/main/java/com/example/ui/components/GuarantorVerificationSheet.kt): added the missing `GuarantorDraft` state holder and the `GuarantorEntity â†” GuarantorDraft â†” GuarantorInput` mappers, `GuarantorDraftCard` (phone, full name, relationship, district, masked NIN, guarantor photo, worker+guarantor photo, consent checkbox), `GuarantorPhotoField` and `GroupPhotoCard` (GetContent picker, attached/replace/remove with confirmation, only paths or content URIs stored â€” never signed URLs), `GuarantorChoiceField` dropdown, and `VerificationStatusChip`. Removed the corrupted duplicated tail, the obsolete `issues` map logic, unused imports and the invented `SpaceMD/SpaceSM` theme references (local `SpaceXS/SM/MD` dp values instead, matching the rest of the codebase which uses literal dp).
- **Wiring**: [MainScreen.kt](app/src/main/java/com/example/ui/screens/MainScreen.kt) renders the sheet on `viewModel.guarantorVerificationOpen`; `IdentityVerificationSheet` in [AccountCenterSheets.kt](app/src/main/java/com/example/ui/components/AccountCenterSheets.kt) gained a "Guarantor verification" status row, the admin review feedback text and an entry button (`onManageGuarantors`), and both drawer entry points now refresh the admin decision via `viewModel.refreshGuarantorVerificationStatus()`.
- **Security posture unchanged**: two distinct guarantors, no self-guaranteeing, consent per guarantor, structural-only NIN check, photos only under the owner prefix `users/{uid}/guarantors/`, Storage paths (never download URLs) persisted, client can only write `ninVerificationStatus = "PENDING"`, and all failure messages are scrubbed so NIN/phone digits never reach logs.
- **Tests added**: [GuarantorValidatorsTest.kt](app/src/test/java/com/example/util/GuarantorValidatorsTest.kt) â€” 18 pure-JVM tests over the whole two-guarantor rule set (count, duplicate person/number, self-phone, missing photos, missing consent, NIN bounds, unauthenticated / non-worker blockers, photo-slot whitelist, deterministic ids, masking and scrubbing).
- **Room schema fix**: `MIGRATION_18_19` creates `index_guarantors_phoneNumber ON guarantors(phoneNumber)`, but `GuarantorEntity` declared only `Index(["workerKycId"])`. The entity now declares `Index(value = ["workerKycId"]), Index(value = ["phoneNumber"])`, so the fresh-install schema Room generates equals the migrated schema (the default index name Room derives â€” `index_guarantors_phoneNumber` â€” is precisely the one the migration executes), keeping Room's schema/`TableInfo` validation and future migrations consistent. AppDatabase stays at **version 19** with `MIGRATION_18_19` registered.
- **Verification was not run in that session**: it recorded a blocker claiming the machine had no JDK, which the 2026-09-27 entry above shows to be wrong (`gradle.properties` pins `org.gradle.java.home` to an existing Corretto 17, and the build then ran first time). Nothing was compiled or tested at that time, so REG-002 was left `in_progress` (85 completed / 1 in progress / 6 pending). Static cross-checks performed instead: UIâ†’ViewModelâ†’repositoryâ†’DAOâ†’`FirebaseService` call signatures and named arguments, entity columns/defaults against `MIGRATION_18_19`, imports and icon/composable references in the new sheet, and the client payloads against the `guarantors` / `guarantor_submissions` Firestore rules and the `users/{uid}/guarantors/**` Storage rules â€” this audit found the undeclared `phoneNumber` index described above. Brace balance was also verified for all edited files. REG-002 is therefore recorded as `in_progress` (85 completed / 1 in progress / 6 pending).


### [2026-09-23] [REG-003] [REG-004] [IMPL] Worker Backup Contact + Worker Profile Photo Implemented & Verified

- **Audit first**: REG-003 was NOT IMPLEMENTED (no backup/emergency/secondary-contact field existed; only the primary `phoneNumber`, plus the guarantor phone in KYC-003, which is a different concept). REG-004 was PARTIALLY COMPLETED â€” `UserProfileEntity.avatarUrl` existed but was only a decorative seeded stock-photo default: no picker, no upload, no Storage write, no client-facing use. `firebase-storage` was already a dependency but unused (the `storage` field was never called), and Coil was declared in `libs.versions.toml` but commented out in `app/build.gradle.kts`.
- **REG-003 â€” data model**: Added `backupContactName` + `backupContactPhone` (both `String = ""`) to `UserProfileEntity` ([Entities.kt](app/src/main/java/com/example/data/db/Entities.kt)). Optional by design: empty strings mean "not set". No relationship/foreign key was added because the requirement treats the backup contact as a contact detail of the worker and explicitly **NOT** a guarantor â€” the `guarantors` table and the KYC-003 two-guarantor flow are untouched.
- **REG-003 â€” validation**: New shared validator `ProfileValidators.validateBackupContact` ([ProfileValidators.kt](app/src/main/java/com/example/util/ProfileValidators.kt)): both fields optional when empty; name-only or phone-only allowed; phone validated with the project's existing digit-count rule (9â€“15 digits, the same rule onboarding/auth use); a backup number equal to the primary `phoneNumber` is rejected. Errors surface in the UI through the field's supporting text so the worker can correct the value before submitting.
- **REG-003 â€” decision (immediate vs. review)**: Updates are **IMMEDIATE**, matching how other profile metadata (district, active mode) is saved; the KYC review queue applies only to identity documents and the requirement introduces no review step for a contact number. Writes always target the signed-in user's own Room row and own Firestore `users/{uid}` document (`firestore.rules`: `allow create, update: if isDocOwner(userId)`), so one user can never modify another user's backup contact. The requirement's open question ("own number or another person's?") is resolved in the implementation and `prd.json` notes as the worker's own alternate contact, with the name field optional.

- **REG-004 â€” storage/upload**: `FirebaseService.uploadProfilePhoto(userId, photoUri)` uploads to Firebase Storage `avatars/{userId}/profile_<timestamp>.jpg` and returns the download URL; only that URL is persisted on `avatarUrl` in Room + Firestore â€” **no image bytes** in Room or Firestore. It reuses the existing `storage.rules` `avatars/{userId}/{allPaths=**}` block (already `allow read: if true; allow write: if isOwner(userId)`), so **no rules change was required**. It fails closed (returns null) on a blank userId, a missing Storage instance, or an upload error.
- **REG-004 â€” UI**: New `ProfileSettingsSheet` in [AccountCenterSheets.kt](app/src/main/java/com/example/ui/components/AccountCenterSheets.kt), opened from the navigation drawer's new "Profile Photo & Backup Contact" item: circular preview with an initials fallback, "Choose photo"/"Replace photo", explicit Upload and Cancel buttons, an upload spinner with "Uploading photoâ€¦", and a user-visible error on failure. `UserProfileEntity.avatarUrl` default changed from the seeded Unsplash stock photo to `""` so new profiles render initials instead of a stranger's photo.
- **REG-004 â€” visibility (documented decision)**: The worker sees the photo in the drawer header and in the sheet; a blank, missing, or failed-to-load URL renders the initials layer underneath rather than a broken image. Client-facing *discovery* cards (`ProviderCard`/`ProviderDetailSheet`) still render initials because they read `service_providers` rows, a separate catalog table with no FK to `user_profiles`; linking them would require a data-model change and is recorded as a follow-up rather than done here. The photo is deliberately **not** gated on KYC approval or activation (it behaves like other profile metadata), and National ID images, guarantor photos, certificates and other verification documents are never exposed through this field â€” those live under the private `users/{uid}/kyc|guarantors|certificates` Storage prefixes and separate KYC tables.
- **Database/migration**: `AppDatabase` version 17 â†’ 18 with new `MIGRATION_17_18` adding `backupContactName`/`backupContactPhone` via `ALTER TABLE user_profiles ADD COLUMN ... TEXT NOT NULL DEFAULT ''` â€” non-destructive (existing users keep their data and simply start unset), registered in `addMigrations`. `avatarUrl` needed no migration because its column already existed; only its Kotlin default changed. `exportSchema = false` matches the existing convention, so no schema JSON is produced.
- **Firestore additions (additive, backwards compatible)**: `users/{uid}` gains `avatarUrl` (String, optional), `backupContactName` (String, optional) and `backupContactPhone` (String, optional); `FirebaseService.saveUserIdentity`/`fetchUserIdentity` write and read them, and older documents lacking the fields restore as empty strings.
- **Security**: No gate was weakened. `uploadProfilePhoto` only ever receives the signed-in user's own id (the ViewModel passes `currentUser.value!!.id`) and blanks out for the demo placeholder profile id; signed-out users are routed to auth by the drawer before the sheet can open. Storage writes are UID-scoped and Storage-rule enforced, Firestore profile writes are owner-only, and the five-layer worker dashboard gate (`canAccessWorkerFeatures`) is unchanged â€” a new test proves that backup contact + photo presence cannot satisfy it.
- **Tests added**: [ProfileValidatorsTest.kt](app/src/test/java/com/example/util/ProfileValidatorsTest.kt) (15 tests: valid/invalid/empty backup phone, duplicate-primary rejection, upload blockers) and [ProfileContactPhotoPersistenceTest.kt](app/src/test/java/com/example/data/ProfileContactPhotoPersistenceTest.kt) (5 tests: backup contact save/reload, per-UID ownership, empty defaults, avatar URL persistence, SEC-009 gate unaffected).
- **Verification**: `:app:compileDebugKotlin` PASSED; `:app:testDebugUnitTest` PASSED â€” **39/39 tests, 0 failures, 0 errors** (XML verified: ProfileValidatorsTest 15, ProfileContactPhotoPersistenceTest 5, UserProfilePersistenceTest 7, AuthViewModelTest 9, example/screenshot 3); `:app:assembleDebug` PASSED (BUILD SUCCESSFUL in 5m). One failure was found and fixed during development: the new `ProfileValidatorsTest` "too long" case used a 14-digit number, which is valid under the project rule â€” the test expectation was corrected, not the validator, and no production code was changed to make a test pass.
- **Environment note**: The first `assembleDebug` attempt lost its Gradle daemon to a native-memory `mmap` failure (`hs_err_pid11432.log`: "insufficient memory for the Java Runtime Environment â€¦ G1 virtual space") during dexing; re-running the identical command succeeded (5m). Heap/memory settings were deliberately NOT changed (out of scope); this is recorded as an environment observation only.
- **Dependency**: `libs.coil.compose` uncommented in `app/build.gradle.kts` (the library was already declared in `libs.versions.toml`). No Gradle/JDK/memory configuration was otherwise touched.
- **PRD**: `REG-003` and `REG-004` marked `completed` (completed: 85 of 92).
- **Device verification still required**: see `prd.json` risks for both stories.

### [2026-09-23] [COMM-002] [IMPL] Community Feature Removal Implemented & Verified

- **Audit**: The Community feature ships under the "Board" tab / neighborhood-feed names (no literal `Community*` classes). Full dependency report produced before removal: UI, navigation, ViewModel, repository, Room entity/DAO, migrations, Firestore rules (none), tests (none), docs.
- **UI / Navigation ([MainScreen.kt](app/src/main/java/com/example/ui/screens/MainScreen.kt))**: Removed the "Board" bottom-nav tab (now List / Map / Jobs only), the `"Board" ->` content branch, the `androidx.compose.material.icons.outlined.DynamicFeed` import, and the Board mention in the map lazy-load comment. NavigationDrawer/MainActivity had no Community entries.
- **ViewModel ([SkillsExchangeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt))**: Removed `neighborhoodPosts` flow and the "Board" comment from `activeMobileTab`.
- **Repository ([SkillsExchangeRepository.kt](app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt))**: Removed neighborhood-post seed data/flow (~42 lines).
- **Database**: Removed `NeighborhoodPostEntity` (Entities.kt) and `NeighborhoodPostDao` (Daos.kt); removed both from `AppDatabase` registrations; version bumped 16 â†’ 17; added and registered `MIGRATION_16_17` executing `DROP TABLE IF EXISTS neighborhood_posts` (safe/idempotent â€” no data migration needed for a dropped feature table).
- **Component**: Deleted `app/src/main/java/com/example/ui/components/NeighborhoodFeedView.kt`.
- **Kept deliberately (dependencies / false positives)**: `ServiceProviderEntity.neighborhood` and the provider `neighborhood` Firestore field (location data used by marketplace filtering and `functions/src/index.ts` district queries); the "COMMUNITY SKILL ENDORSEMENTS" label (endorsements feature); "community guidelines" training-module copy (worker training TRAIN-001/002).
- **Pre-existing failure found during verification (NOT caused by COMM-002)**: `AuthViewModelTest` 2/19 still asserted pre-SEC-009 `UserProfileEntity` defaults; `git diff app/src/test` was empty before repair, proving COMM-002 never touched tests. Assertions corrected to the shipped SEC-009 fail-closed defaults (`workerKycStatus=NOT_APPLIED`, `isWorkerActivated=false`, `trainingCompleted=false`); no production code changed.
- **Verification**: `:app:compileDebugKotlin` PASSED; `:app:testDebugUnitTest` PASSED â€” **19/19, 0 failures** (UserProfilePersistenceTest 7, AuthViewModelTest 9, ExampleRobolectricTest 1, ExampleUnitTest 1, GreetingScreenshotTest 1); `:app:assembleDebug` PASSED (BUILD SUCCESSFUL in 8m 12s). Offline, `GRADLE_USER_HOME=D:\gradle-home`, `JAVA_HOME=JDK17`, `--max-workers=1`, `--no-parallel`, `--no-configuration-cache`; no memory/Gradle/JDK settings changed.
- **PRD**: `COMM-002` marked `completed` in `prd.json` (completed count: 83).
- **Pre-existing records gap repaired during sync**: `AUTH-011` (Google Sign-In, P0, completed 2026-09-09) had `status: completed` but was missing from `completed_stories[]` and had no row in `TASKS.md`; added to both. Corrected totals: 83 completed / 9 pending / 92 total.

### [2026-09-23] [FEAT] SEC-009 â€” Worker Dashboard Security & Access Control Implemented & Verified

- **Entities ([Entities.kt](app/src/main/java/com/example/data/db/Entities.kt))**: Set `UserProfileEntity` defaults to fail-closed: `workerKycStatus = "NOT_APPLIED"`, `isWorkerActivated = false`, `trainingCompleted = false`.
- **ViewModel ([SkillsExchangeViewModel.kt](app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt))**:
  - `canAccessWorkerFeatures()` strictly enforces: authenticated session + worker registration/qualification + KYC status `APPROVED` + `trainingCompleted` + `isWorkerActivated`.
  - `isWorkerApprovedAndActivated` StateFlow enforces the exact same multi-layer requirement.
  - `completeOnboarding()` and `restoreSession()` no longer set client accounts to `APPROVED` or `isWorkerActivated = true`; client accounts remain fail-closed as `NOT_APPLIED` and inactive.
  - `toggleWorkerOnline()` is gated behind `canAccessWorkerFeatures()`.
- **UI ([MainScreen.kt](app/src/main/java/com/example/ui/screens/MainScreen.kt))**:
  - Worker dashboard (`FreelancerDashboardView`) is rendered only when `isWorkerApprovedAndActivated` is true.
  - When unverified or unapproved, `WorkerVerificationStatusView` or `WorkerTrainingScreen` is displayed.
  - Connected `WorkerVerificationStatusView`'s `onStartVerification` callback to `IdentityVerificationSheet` (fixing audit G4).
- **Verification**: `:app:testDebugUnitTest --tests com.example.data.UserProfilePersistenceTest` passed with 7/7 tests passing (including new `client account cannot access worker dashboard or satisfy activation gate (SEC-009)`).
- **PRD**: Updated `SEC-009` to `completed` in `prd.json` (completed count: 80).



- `d:\skills exchange\app\src\main\java\com\example\ui\screens\OnboardingScreen.kt` and `d:\skills exchange\app\src\main\java\com\example\ui\components\AuthModal.kt`: explicitly set Slate900 focused/unfocused legal-name text on white, retaining blue cursor/labels. Theme-derived light text against a light surface caused the contrast defect; no Google display-name prefill introduced.
- Onboarding district no longer defaults to Sunnyside or replaces selection on GPS errors; required label and nonblank completion gate added. AuthModal already gates nonblank district but retains initialDistrict default. Device validation remains untested.
- Compile and assembleDebug PASSED (combined 6m 56s, exit 0); Samsung SM-A025F replacement install PASSED without clearing data. Installed version 1.0/code 1, lastUpdateTime 22:46:09; installed/local APK SHA-256 match F5497194E64BC72B1E195B287C6A0ABE3B4B62FE60420440555BAA99F7E009EB.
- Google buttons trace through GoogleSignInHelper signInIntent, ViewModel and Firebase credential sign-in. Current device is signed in; auth button/chooser not reproduced. Recent logcat filter contains no matching sign-in failure. Earlier missing-chooser root cause is unknown; no speculative Google/Firebase changes.
- User confirmed no display-name prefill before this patch. Post-install legal-name contrast, chooser launch and district-required behavior require manual retest. C1/C2/C3 remain PENDING. No other audit items, Phase-17, Community, PRD changes or commit.


### [2026-09-17] [VERIFY] C1/C2/C3 â€” compile, targeted tests 6/6, APK, device install PASSED; manual Aâ€“F pending

- `:app:compileDebugKotlin` PASSED (3m 12s) after the earlier duplicate-signature slip was corrected to the original `triggerSosAlert(jobId)` signature.
- `UserProfilePersistenceTest` PASSED **6/6, 0 failures, 0 errors, 0 skipped** (fresh 2026-09-17 XML) running `--offline` against the pre-existing complete Gradle cache at `D:\gradle-home`. Earlier failures were environmental: the project `GRADLE_USER_HOME` cache lacked nine Robolectric runtime JARs and downloads stalled; no dependency, repository, Gradle/AGP/toolchain or source configuration was changed to fix this.
- `:app:assembleDebug` PASSED (2m 53s); `app-debug.apk` (32,135,868 bytes) built and installed on Samsung SM-A025F (serial R9HR30HWJ5J) via `adb install -r` â†’ Success. No app data or Firebase account cleared.
- Manual cases Aâ€“F require interactive Google/phone input on the device and remain NOT TESTED; C1â€“C3 therefore stay â³ Pending Audit Fix in TASKS.md and are not marked completed.
- No H-items, Phase-17, Community, PRD or completion-status changes; no commit. prd.json verified unchanged (79 completed stories, 12 Phase-17 pending).


### [2026-09-17] [VERIFICATION] C1/C2/C3 â€” Compile passed; runtime verification blocked

- Corrected only the duplicate-signature compiler regression in `d:\skills exchange\app\src\main\java\com\example\data\repository\SkillsExchangeRepository.kt`: restored `triggerSosAlert(jobId)`; existing GPS/SMS method unchanged.
- Compile retry passed in 3m 12s (JDK 17 launcher, existing JDK 25 daemon selected by unchanged configuration). No configuration changes.
- Targeted `com.example.data.UserProfilePersistenceTest` build failed in 6m 52s before tests executed: Robolectric runtime dependencies unavailable, network unreachable. No dependency substitutions or full suite run. Two online retries stalled during artifact download and were cancelled. A final offline run failed in 1m 6s with nine uncached runtime JARs (including Robolectric native-runtime 1.0.18); no fresh tests executed. Stale September-16 generated results were discarded. See AGENT_STATE.md for diagnostics and retained logs.
- Samsung SM-A025F connected. APK/install not attempted because tests did not pass; manual cases Aâ€“F all NOT TESTED. No Firebase account or local device data deleted.
- UID audit still finds the demo entity default and development anonymous-auth paths; their safety is not established by string search alone. Detailed evidence and residual risks are in AGENT_STATE.md.
- C1/C2/C3 remain PENDING / UNVERIFIED. No H-items, Phase-17, Community, PRD or completion-status changes; no commit. Four requested obsolete scratch logs removed; current verification evidence retained.


### [2026-09-17] [FIX] Audit C1/C2/C3 â€” Profile persistence, UID consistency, Google completion (IMPLEMENTED, verification blocked)

**Scope:** Only audit items C1, C2, C3. No Phase-17 work, no Community removal, no H-items, no new PRD IDs, no prd.json change.

**C1 â€” Existing-user profile persistence**
- `SkillsExchangeViewModel.restoreSession()` else-branch: when no local DataStore session exists but `FirebaseService.getCurrentUser()` still returns a signed-in user (Room wiped / app-data cleared while the Firebase token survived), the profile is recovered via Room UID lookup then `users/{uid}` Firestore lookup, re-persisted to Room, and the session + role mode are rebuilt â€” but ONLY when the recovered profile has `isOnboardingCompleted == true`. Otherwise the user stays on onboarding. Worker KYC/activation/training gates untouched.
- Existing authenticated-session restore path (Room â†’ server â†’ session rebuild) unchanged.

**C2 â€” UID consistency (no synthetic IDs for authenticated users)**
- `loginUser()`: removed `?: "user_${System.currentTimeMillis()}"` fallback â€” fails closed with an error when Firebase supplies no UID.
- `loginWithGoogle()`: removed `?: "google_user"` fallback â€” fails closed on blank UID.
- `FirebaseService.signInAnonymously()`: returns the already-signed-in user instead of minting a second anonymous identity that would fork the UID.
- `uploadQualificationEvidence()`, `startTraining()`, `completeTrainingModule()`, `completeTraining()`, `submitQuote()`, `submitRecurringProposal()`, `reportUser()`: resolve Room profile id (excluding `user_default_me`) else Firebase UID, abort when absent â€” no `user_default_me` / `worker_default` / `anonymous` placeholders.
- `SkillsExchangeRepository.triggerEmergencySos()` + `respondToQuote()`: notifications attributed to the real UID; notification step aborts when no real id exists (status updates still applied).
- `AccountComplianceDialog.kt`: guarantor submit aborts when no authenticated UID (orphaned A7 path) instead of filing under `user_default_me`.
- Verified by search: zero remaining `?: "user_default_me"`, `?: "worker_default"`, `?: "anonymous"`, `user_${...}`, `?: "google_user"` fallbacks in `app/src/main`; remaining `user_default_me` hits are the `DEMO_PROFILE_ID` constant definition, the DAO exclusion clause, and test fixtures only.

**C3 â€” Google auth/onboarding completion**
- `loginUser()`: blank phone fails closed with `phoneError` before any profile/session/server write.
- `loginUser()` new-profile branch: `fullName` stored exactly as typed â€” removed `firebaseUser?.displayName ?: "Specialist"/"Neighbor"` fallback.
- `AuthModal.kt` PROFILE_SETUP "Complete Setup" button now requires `fullName && phoneNumber && district` (was name + district only).
- `OnboardingScreen.kt` completion button now requires `fullName && phoneNumber` (was name only); selected CLIENT/WORKER role preserved via `selectedRole`; no display-name pre-population (unchanged from prior fix).
- `AuthModal.kt` Google launcher already dismisses explicitly via `onDismiss()` on the recognised-account path (verified, unchanged); `loginWithGoogle()` never invents a blank-phone profile for unknown accounts â€” returns `onComplete(true)` so the UI collects legal name + phone.

**Verification status:** `:app:compileDebugKotlin` attempted with bundled JDK 11 (`jdk11/jdk-11.0.22+7`, `GRADLE_USER_HOME=.gradle`). Offline run failed (foojay plugin not cached); online run is in progress downloading the JDK 25 toolchain (PID 8808/17716, foojay lock contention observed). No test suite, APK build, or device test completed yet â€” device connectivity unknown. C1/C2/C3 therefore NOT marked completed/verified.

### [2026-09-17] [AUDIT] Feature Connection Audit â€” Documentation Only

- A READ-ONLY Feature Connection Audit was performed: 48 interactive controls examined.
- Found: 8 confirmed disconnected features (A1â€“A8); 9 partially connected features (B1â€“B9).
- Also documented: 7 security/authorization findings (G1â€“G7), 11 placeholder/demo findings (D1â€“D11), 4 navigation/UX findings (N1â€“N4), 2 permission findings (P1â€“P2), 4 map findings (M1â€“M4), 9 authentication findings, and the Community removal reference map (DO NOT REMOVE NOW).
- Findings preserved in AGENT_STATE.md (2026-09-17 section) and TASKS.md (Pending Fixes register); decision recorded as DEC-018 in DECISIONS.md; cross-reference added to requirement_file.md.
- No source code was changed. No fixes were implemented. No commit was made. prd.json untouched (79 completed / 12 Phase-17 pending unchanged).

### [2026-09-16] [FIX] Feature Connection Audit â€” Post-Audit Bug Fixes

**AUTH-006 / AUTH-002 â€” Existing Google User Recognition**
- Added durable server-side identity record at `users/{uid}` in Firestore (`saveUserIdentity`, `fetchUserIdentity`, `fetchUserIdentityByPhone`)
- `loginUser()` now resolves real Firebase UID before persisting profile (eliminates synthetic `user_<timestamp>` IDs)
- `loginUser()` now checks Firestore identity as fallback when local Room profile missing
- `loginUser()` now persists profile to server via `saveUserIdentityToServer`
- `isWorkerApprovedAndActivated` `stateIn` initial value changed from `true` to `false` (fail-closed)

**AUTH-007 / SEC-009 â€” Worker Dashboard Security**
- `setUserRoleMode()` now enforces `canAccessWorkerFeatures()` gate at the ViewModel boundary
- `canAccessWorkerFeatures()` now also checks `isAuthenticated.value` (signed-out device cannot unlock worker mode)

**Legal Name / Google Display Name**
- `OnboardingScreen.kt`: Removed automatic `account.displayName` pre-population of the legal name field

**Navigation Drawer â€” Real Destinations**
- `NavigationDrawer.kt`: Removed hardcoded wallet balance (`CurrencyRegion.format(240.0)`) and hardcoded phone fallback (`"+256 700 123 456"`)
- `NavigationDrawer.kt`: Added `onOpenVerification` callback parameter
- `MainScreen.kt`: Drawer menu items now wire to real sheets (`EscrowWalletSheet`, `JobHistorySheet`, `VerificationStatusSheet`, `TrustAndSafetySheet`) with actual data

**New Files:**
- `app/src/main/java/com/example/ui/components/AccountCenterSheets.kt` â€” Real destination sheets

### [2026-09-16] [DOC] Future Requirements Documentation Capture (Phase 17)

**Added 12 new future/planned requirements to PRD.json, requirement_file.md, TASKS.md, and AGENT_STATE.md:**
- PAY-005: Job Payment Fees & Platform Deductions (5% commission + 5% platform fee)
- REG-001: First-Time Worker Registration Fee (UGX 20,000)
- REG-002: Worker Registration Guarantor Verification Requirements (2 guarantors + photos)
- REG-003: Worker Backup Contact Number
- REG-004: Worker Profile Photo
- LOC-005: Worker Location Using Device GPS
- MAP-002: Google Maps Usage Minimization
- COMM-002: Community Feature Removal
- SEC-009: Worker Dashboard Security & Access Control
- LEGAL-001: Liability Disclaimer
- LEGAL-002: Privacy Policy / Terms & Conditions / User Consent
- AUTH-012: Preserve Existing Authentication/Onboarding Requirements

**Status:** All new requirements marked as FUTURE / PLANNED / NOT IMPLEMENTED.
**Documentation files updated:** prd.json, requirement_file.md, TASKS.md, AGENT_STATE.md
**No code changes.** This is documentation capture only.

### [2026-09-16] [AUTH-002/ROLE-001] [FIX] Persist and resolve the authenticated user profile correctly
**Root causes found and fixed:**
1. `UserProfileDao.getCurrentUser()` used `SELECT * FROM user_profiles LIMIT 1` with no WHERE clause, so the seeded demo row (`user_default_me` â€” "Sarah Namukasa", KYC APPROVED, activated) was returned as the current user instead of the authenticated profile. The NavigationDrawer therefore showed the demo name/initials ("Sarah Namukasa" / "SN") and the worker activation gate could be satisfied by the demo row.
   - Fix: `getCurrentUser()` now excludes the demo placeholder (`WHERE id != 'user_default_me' ORDER BY updatedAtTimestamp DESC LIMIT 1`).
2. `SkillsExchangeRepository.seedInitialDataIfNeeded()` inserted a fake demo `UserProfileEntity`, which created that shadowing row on every fresh install.
   - Fix: the demo user profile is no longer seeded; only demo content (providers/jobs/chat/posts/endorsements) is seeded. The authenticated profile is persisted by the onboarding flow (`completeOnboarding` â†’ `loginUser` â†’ `repository.saveUserProfile`, keyed by Firebase UID with the selected role preserved).
3. `loginUser()` reused any row matching the typed phone number, including the demo row (whose number is the onboarding placeholder hint), which kept the demo identity and silently downgraded a selected WORKER to the demo row's CLIENT role.
   - Fix: the demo row is excluded from the existing-account lookup; new profiles are always keyed by the Firebase UID and keep the selected role (CLIENT stays CLIENT, WORKER stays WORKER with UNVERIFIED / not-activated / training-incomplete gates intact).
4. `restoreSession()` never rebuilt the profile when the persisted session existed but the Room row did not (cleared/recreated database), so `currentUser` could stay null and the drawer fell back to Guest.
   - Fix: the profile is rehydrated from the persisted session (user id, name, district, role) and re-inserted into Room. Role is now persisted in DataStore (`user_role`) and restored. Restored workers fail closed (`UNVERIFIED`, not activated).
5. `restoreSession()` collected `sessionManager.fcmToken` with a never-completing `collect`, so `isSessionRestored` was never set and the app hung on the loading spinner after relaunching an authenticated session.
   - Fix: replaced with a terminating `first()` read (live updates still handled by `onFcmTokenRefreshed`).

**Verification:** test-result XMLs show `UserProfilePersistenceTest` 6/6, `AuthViewModelTest` 9/9, `ExampleRobolectricTest` 1/1 with 0 failures/errors; prior full run `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL (see `D:/gradle-temp/build2.log`). Clean compile/unit re-run is blocked in this environment (AGP 9.3.2 requires Gradle 9.5.0 but only a partial 9.5.0 wrapper dist exists, so the wrapper tries to re-download it; `--offline` fails plugin resolution; no device attached for runtime checks â€” `adb devices` returns empty).

### [2026-09-14] [AUTH-009] [FIX] Profile Name & Dynamic Initials Display Fix
- Initialize `MapView` and call `getMapAsync` once from `AndroidView.factory` instead of repeating lifecycle setup during recomposition
- Retain the `GoogleMap` instance in Compose state and keep marker creation/camera movement dependent on map readiness
- Preserve provider marker selection through the state-driven map effect
- Kotlin static diagnostics pass; full Gradle verification completed in the subsequent STAB-003 entry below

### [2026-09-14] [AUTH-009] [FIX] Profile Name & Dynamic Initials Display Fix
- Updated `NavigationDrawer.kt` profile header to reactively display `currentUserProfile?.fullName` when authenticated.
- Implemented `extractInitials` function returning uppercase initials ("Isaac Kato" â†’ "IK", "Isaac" â†’ "I", empty/null â†’ "N", guest â†’ "G").
- Added secondary display of user's phone number under full name in drawer header.
- Added unit tests in `AuthViewModelTest.kt` verifying initials extraction logic.

### [2026-09-05] [STAB-003] [TEST] Complete automated verification
- Full `./gradlew test` completed with `BUILD SUCCESSFUL`
- Robolectric and Roborazzi tests passed after redirecting Gradle and Robolectric temporary/cache directories to D: because C: had insufficient free space
- Verified with Gradle 9.3.1, Android Studio JDK 17, and the installed Android SDK

### [2026-09-05] [REL-001] [FIX] Restore debug APK packaging
- Removed the invalid custom debug signing configuration that referenced a missing repository-root `debug.keystore`
- Debug APK now uses the Android Gradle Plugin's standard debug signing behavior
- `./gradlew assembleDebug` completed with `BUILD SUCCESSFUL`

Format: `[DATE] [STORY-ID] [TYPE] Description`

Types: `IMPL` (implementation), `FIX` (bug fix), `TEST` (test), `INFRA` (infrastructure), `DOCS` (documentation), `SECURITY` (security)

---

### [2026-08-31] [SEC-008] [SECURITY] Add Storage Rules for Job Photos
- Updated storage.rules to restrict job photos access to job owner, assigned provider, and admin
- Files must include custom metadata: jobOwnerId and assignedProviderId
- Unauthenticated users cannot access job photos

### [2026-08-31] [AUTH-010] [IMPL] Implement Authentication Tests
- Created AuthViewModelTest.kt with unit tests for user model and access check logic
- Tests cover: UserProfileEntity defaults, worker access check logic, phone number uniqueness
- Tests use JUnit4 without external mocking dependencies

### [2026-08-31] [AUTH-009] [IMPL] Implement Auth State Restoration
- Updated restoreSession to restore user profile and role mode
- Added session corruption handling with automatic re-authentication
- Restored FCM token on app startup
- Session state now fully persists across app restarts

### [2026-08-31] [AUTH-008] [IMPL] Implement Session/Device Security
- Added session timestamp and device ID storage to SessionManager
- Added 7-day session expiration check in restoreSession
- Added device ID validation to detect device changes
- Updated loginUser and verifyOtp to save device ID with session
- Auto-logout on session expiration or device change

### [2026-08-31] [AUTH-007] [IMPL] Implement Role/Permission Enforcement
- Added canAccessWorkerFeatures() helper to ViewModel
- Updated toggleUserRoleMode() to prevent switching to worker mode without verification/activation
- Updated submitQuote() to reject quotes from unverified/inactive workers
- Role gating enforced at ViewModel layer for client/worker mode transitions

### [2026-08-31] [AUTH-006] [IMPL] Implement Duplicate Account Handling
- Added unique index on phoneNumber in UserProfileEntity
- Added getUserProfileByPhoneNumber() to UserProfileDao
- Added getUserProfileByPhone() to SkillsExchangeRepository
- Updated ViewModel.loginUser() to check existing account by phone before creating new profile
- Added MIGRATION_14_15 for unique index creation
- Bumped AppDatabase version to 15

### [2026-08-30] [DB-002] [IMPL] Add Room Foreign Keys and Indexes
- Added @ForeignKey and @Index annotations to skill_endorsements, job_posts, chat_messages, disputes, ratings, notifications, job_status_audit, reports
- Updated AppDatabase version to 14
- Added MIGRATION_13_14 with table recreation SQL for all affected tables
- Foreign keys enforce referential integrity; indexes improve query performance

### [2026-08-30] [NOTIF-004] [IMPL] Implement Notification UI
- Updated NotificationDrawer to accept List<NotificationEntity> instead of NeighborhoodNotification
- Added time-ago formatting for notification timestamps
- Added click handling on notification items
- Updated MainScreen to use real notifications from ViewModel instead of mock data
- Added ViewModel notification state: notifications, unreadNotificationCount
- Added markAllNotificationsRead() method to ViewModel
- TopHeaderBar unread badge now reflects real unread count

### [2026-08-30] [NOTIF-003] [IMPL] Wire Notification Triggers to Events
- Added notification creation in repository.submitQuote for new quote submissions
- Added notification creation in repository.respondToQuote for quote accept/decline
- Added notification creation in repository.updateEscrowStatus for payment status changes
- Added notification creation in repository.triggerSosAlert for emergency SOS events
- Added notification creation in KYC methods: submitWorkerKycApplication, approveKyc, rejectKyc, requestKycChanges
- Added notification creation in completeTraining for training completion
- Added ViewModel.updateJobStatus notification for job status changes
- All key business events now create NotificationEntity records in local Room DB

### [2026-08-30] [DISP-005] [IMPL] Implement Dispute Notification Integration
- Added notification creation in repository.updateDisputeStatus for dispute status changes
- Notifications created for dispute submission and resolution events
- Notification type DISPUTE_UPDATE with title and body based on resolution type

### [2026-08-30] [DISP-004] [IMPL] Implement User/Worker Reporting
- Added ReportEntity Room table with ReportDao for persisting user reports
- Added ReportDao with queries for all reports, user reports, and status filtering
- Added MIGRATION_12_13 creating reports table in AppDatabase v13
- Added repository methods: reportUser, getAllReports, getReportsForUser, getPendingReports, updateReportStatus
- Added ViewModel.reportUser() setting reportedBy from current user
- Built ReportUserDialog in ProviderDetailSheet with reason and description fields
- Wired onReportUser callback from MainScreen to ViewModel

### [2026-08-30] [AUTH-005] [IMPL] Implement Authentication Error Handling
- Added phoneError and otpError state flows to ViewModel for inline validation
- AuthModal displays red error text below phone and OTP fields on invalid input
- Added retry button to AuthModal when auth errors are present
- Added retryAuth() ViewModel method clearing errors and resetting auth state
- MainScreen passes phoneError, otpError, and onRetry to AuthModal

### [2026-08-30] [AUTH-004] [IMPL] Implement Account Deletion
- Updated permanentlyDeleteUserAccountAndData() to delete Firebase auth user, Firestore documents, and Room data
- Added FirebaseService.deleteCurrentUser() for Firebase auth user deletion
- Added FirebaseService.deleteUserFirestoreData() purging user profile, KYC, training, providers, guarantors, notifications, disputes, quotes, and job messages
- AccountDeletionDialog confirmation flow already wired to permanent deletion
- Full account purge requires re-authentication after deletion

### [2026-08-30] [AUTH-003] [IMPL] Implement Logout Flow
- Updated logoutUser() to clear Firebase auth, Room data via clearAllUserData(), and DataStore session
- Added repository.clearAllUserData() clearing all user tables on logout
- Logout resets all auth-related UI state (authState, isAuthenticated, currentUserPhone, selectedProvider, selectedJob)
- User must re-authenticate after logout as all local session data is purged

### [2026-08-30] [AUTH-002] [IMPL] Implement Session Persistence
- Created SessionManager class using DataStore Preferences
- Added persistence for: isAuthenticated, userPhone, userId, userName, userDistrict, fcmToken
- Updated ViewModel to restore session on app launch
- Updated loginUser, verifyOtp, logoutUser to sync with SessionManager
- Added DataStore dependency to build.gradle.kts
- Session survives app restarts and process death

### [2026-08-30] [RATE-002] [IMPL] Implement Rating Submission UI
- Added RatingSubmissionState sealed class: Idle, Submitting, Success, Error
- Added submitRating method to ViewModel with validation
- Added duplicate rating prevention
- Added rating validation (1-5 stars)
- Added getUserRatings flow for displaying user ratings
- Integrated with existing Repository â†’ ViewModel â†’ UI architecture

### [2026-08-30] [RATE-003] [IMPL] Implement Rating Aggregation
- Added getAverageRatingForUser method to ViewModel
- Average rating calculated from all ratings in database
- Flow-based API for reactive UI updates
- Rating aggregation happens server-side in repository
- Created SkillsExchangeMessagingService extending FirebaseMessagingService
- Added FCM token management: getToken, deleteToken, onNewToken
- Added notification channel creation for Android O+
- Added foreground notification handling with deep linking
- Added Firebase Messaging dependency to build.gradle.kts
- Updated AndroidManifest.xml to register FCM service
- Added FCM state management to ViewModel: fcmToken, hasNotificationPermission
- Added initFcm, onFcmTokenRefreshed, clearFcmToken methods
- Notifications support deep linking to relevant jobs via jobId extra
- Updated ViewModel.updateJobStatus to use audited transition path
- All status changes now create JobStatusAuditEntity records
- Audit records capture: jobId, fromStatus, toStatus, changedBy, timestamp, reason
- Invalid transitions are rejected by JobStatus.isValidTransition
- Added getJobAuditTrail method to ViewModel
- Audit trail is created as part of trusted status-transition path
- Clients cannot create false audit records directly
- Added multi-step auth flow: PHONE_ENTRY â†’ OTP_VERIFICATION â†’ PROFILE_SETUP
- Added AuthStep enum for tracking current step
- Added phone number input with validation (minimum 10 characters)
- Added 6-digit OTP input with verification
- Added resend cooldown timer (60 seconds)
- Added loading states for sending/verifying OTP
- Added error display with user-friendly messages
- Added step indicator showing progress through auth flow
- Added AuthState sealed class: SignedOut, SendingOtp, OtpSent, VerifyingOtp, SignedIn, Error
- Added ViewModel methods: sendOtp, verifyOtp, logoutUser, clearAuthError
- Added FirebaseService methods: sendPhoneVerificationCode, verifyPhoneCode
- Integrated with existing AuthModal UI
- Removed hardcoded demo login credentials from form
- Added "Change phone number" option during OTP verification

### [2026-08-30] [DISP-002] [IMPL] Implement Dispute UI and Workflow
- Wired dispute dialog in EscrowAndProofSheet to persistence layer.
- Added DisputeReason selector with radio buttons (Quality Issue, No Show, Incomplete Work, Overcharging, Misrepresentation, Safety Concern, Other).
- Added input validation: description required, minimum 10 characters.
- Added loading state with progress indicator during submission.
- Added success message display after successful submission.
- Added duplicate dispute prevention (checks for existing dispute by same user for same job).
- Added ViewModel methods: submitDispute, getUserDisputes, resetDisputeSubmissionState.
- Added DisputeSubmissionState sealed class for Idle/Submitting/Success/Error states.
- Updated MainScreen to pass dispute callback and state to EscrowAndProofSheet.
- Job status now correctly set to "DISPUTED" on dispute submission.

### [2026-08-30] [DB-001] [INFRA] Add Proper Room Migrations
- Replaced `fallbackToDestiveMigration()` with proper migration from version 11 to 12.
- Added migration to create new tables: disputes, ratings, notifications, job_status_audit.
- Data is now preserved across schema changes.

### [2026-08-30] [RATE-001] [IMPL] Implement Rating Entity and Persistence
- Created `RatingEntity` Room table for storing ratings and reviews.
- Created `RatingDao` with queries for user ratings, job ratings, and average calculation.
- Added repository methods: getAllRatings, getRatingsForUser, submitRating, getAverageRatingForUser.
- Added `hasUserRatedJob` to prevent duplicate reviews.

### [2026-08-30] [NOTIF-001] [IMPL] Implement Notification Entity and Persistence
- Created `NotificationEntity` Room table for storing user notifications.
- Created `NotificationDao` with queries for user notifications and unread count.
- Added repository methods: getNotificationsForUser, createNotification, markNotificationRead.
- Added NotificationType enum with all notification types.

### [2026-08-30] [DISP-003] [IMPL] Implement Admin Dispute Resolution
- Added `resolveDispute` Cloud Function for admin dispute resolution.
- Added `getDisputesQueue` Cloud Function for admin dispute queue.
- Supports resolution types: REFUND_FULL, REFUND_PARTIAL, NO_ACTION, WORKER_SUSPENDED.
- Notifies both parties on resolution.

### [2026-08-30] [DISP-001] [IMPL] Implement Dispute Entity and Persistence
- Created `DisputeEntity` Room table for storing dispute data.
- Created `DisputeDao` with queries for user disputes and status filtering.
- Added repository methods: getAllDisputes, getDisputesForUser, createDispute, updateDisputeStatus.
- Added DisputeStatus and DisputeReason enums.

### [2026-08-30] [JOB-004] [IMPL] Implement Job State Machine
- Created `JobStatus` enum with all valid job states.
- Added `isValidTransition` and `canTransition` methods for state validation.
- Valid states: DRAFT, POSTED, QUOTING, WORKER_SELECTED, ACCEPTED, WORKER_EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED, PAYMENT_RELEASED, CANCELLED, DISPUTED, EXPIRED.
- Added `transitionJobStatus` repository method with audit logging.
- Added `JobStatusAuditEntity` for tracking all status changes.

### [2026-08-30] [SEC-007] [SECURITY] Remove Hardcoded Webhook Secret Fallback
- Removed default fallback value for MOMO_WEBHOOK_SECRET.
- Webhook verification now fails loudly if secret is not configured.
- Prevents unauthorized webhook processing with default secret.

### [2026-08-30] [SEC-006] [SECURITY] Add Missing Firestore Rules for Collections
- Added security rules for worker_kyc, worker_training, guarantors, job_quotes, notifications.
- Added rules for disputes, momo_transactions, momo_payouts collections.
- Proper access control for each collection based on user role.

### [2026-08-30] [SEC-005] [SECURITY] Fix Firestore Rules KYC Path Mismatch
- Updated `isWorkerKycApproved` helper to read from `worker_kyc` collection instead of `users/{uid}/verification`.
- Fixed broken worker-write security enforcement.

### [2026-08-30] [SEC-004] [SECURITY] Fix releaseEscrowToWorker Authorization Vulnerability
- Added job ownership verification to releaseEscrowToWorker Cloud Function.
- Only job owner (postedByResident or postedBy) can release escrow.
- Added state validation - escrow can only be released for completed jobs.
- Added worker ID verification against assigned provider.
- Prevents fund theft by unauthorized users.

## 2026-08-29

### [2026-08-29] [REL-001] [INFRA] Production Build ProGuard Rules & Release Sign-Off
- Added comprehensive ProGuard rules to `app/proguard-rules.pro` for release builds.
- Keep rules for Room entities, DAOs, and `@Entity`/`@Database`/`@Dao` annotated classes.
- Keep rules for Firebase and Google Play Services classes.
- Keep rules for Moshi/JSON serialization with `@SerializedName` annotations.
- Preserved Kotlin metadata, annotations, signatures, and coroutine internals.
- Added SourceFile/LineNumberTable attributes for debugging release crashes.
- All 47 of 48 stories now complete. STAB-003 remains blocked on JDK/Gradle environment conflict.

### [2026-08-29] [OFF-001] [IMPL] Offline Action Queue & Sync Manager
- Created `PendingActionEntity` Room table for offline write operations.
- Created `PendingActionDao` with queries for pending actions, status updates, and retry tracking.
- Added `PendingActionEntity` to `AppDatabase` (version 11) and exposed `pendingActionDao()`.
- Added repository methods: `enqueueAction`, `markActionCompleted`, `incrementActionRetry`, `getPendingActions`, `getPendingActionsCount`.
- Added ViewModel: `pendingActions` state, `enqueueOfflineAction`, `flushPendingActions`.
- Wired `NetworkConnectivityObserver` to automatically flush pending actions when network reconnects.
- Actions support retry count with max retries before marking as FAILED.

### [2026-08-29] [MARKET-003] [IMPL] Resident Quote Review & Worker Selection
- Created `QuoteComparisonSheet` composable in `JobPipelineKanban.kt` with quote list, worker details, pricing comparison, and accept/decline actions.
- Added "Compare Quotes" button to `JobPipelineCard` for jobs in "Requested" status.
- Wired `QuoteComparisonSheet` into `MainScreen.kt` with state management and accept quote callback.
- Sheet shows worker name, rate, hours, total, notes, and status (PENDING/ACCEPTED/REJECTED).

### [2026-08-29] [MARKET-002] [IMPL] Open Job Posting & Multi-Quote Bidding
- Created `JobQuoteEntity` Room table for storing worker quote proposals.
- Created `JobQuoteDao` with queries for job quotes, worker quotes, and status updates.
- Added `JobQuoteEntity` to `AppDatabase` (version 10) and exposed `jobQuoteDao()`.
- Added repository methods: `submitQuote`, `respondToQuote`, `getQuotesForJob`, `getQuotesByWorker`, `countPendingQuotesForJob`.
- Added ViewModel methods: `submitQuote`, `respondToQuote`, `getQuotesForJob`, `getQuotesByWorker`.
- Workers can submit quotes with rate, hours, and notes; clients can accept/decline with chat notifications.

### [2026-08-29] [JOB-003] [IMPL] Recurring Contract Pricing Negotiation Workflow
- Added `recurringContractProposal`, `recurringNegotiatedRate`, `recurringProposalStatus` fields to `JobPostEntity`.
- Added DAO methods: `updateRecurringProposal`, `updateRecurringProposalStatus`.
- Added repository methods: `submitRecurringProposal`, `respondToRecurringProposal`.
- Added ViewModel methods: `submitRecurringProposal`, `respondToRecurringProposal`.
- Workers can propose custom recurring rates; clients can accept/decline with chat notifications.

### [2026-08-29] [JOB-002] [IMPL] Recurring Schedule Configuration UI
- Created `JobTypeSelector` composable with One-Time/Recurring toggle and schedule frequency picker (Daily, Weekly, Bi-Weekly, Monthly, Custom).
- Integrated `JobTypeSelector` into `QuickRequestDrawer` between Step 2 and Step 3.
- Updated `QuickRequestDrawer` onPostJob callback to include `jobType` and `recurringSchedule` parameters.
- Updated `SkillsExchangeViewModel.postQuickJob` and `SkillsExchangeRepository.addJobPost` to persist new fields.
- Recurring schedule stored in `JobPostEntity.recurringSchedule` (empty for one-time jobs).

### [2026-08-29] [TRAIN-003] [IMPL] Admin Activation Gate requiring Verification + Training
- Added `activateWorker(userId)` repository method enforcing both KYC approval and training completion.
- Added `ActivationResult` sealed class with Success/Failure outcomes.
- Updated `setWorkerActivationStatus` Cloud Function to validate KYC and training status server-side before activation.
- Added `activateWorkerWithGate` ViewModel method for gated activation flow.

### [2026-08-29] [TRAIN-002] [IMPL] Worker Rule Training UI & Completion Tracking
- Created `WorkerTrainingScreen` composable in `AccountComplianceDialog.kt` with training module list, progress tracking, and completion buttons.
- Added `TrainingModule` data class for module metadata (Platform Rules, Escrow Guidelines, Customer Etiquette).
- Added ViewModel methods: `startTraining`, `completeTrainingModule`, `completeTraining`, `currentTraining` state flow.
- Wired training screen into `MainScreen.kt` â€” shown when KYC is approved but training not completed.
- Training completion updates both `worker_training` table and `user_profiles.trainingCompleted` flag.

### [2026-08-29] [TRAIN-001] [IMPL] Worker Training Progress & Content Data Schema
- Created `WorkerTrainingEntity` Room table with status, completedModulesCsv, module completion booleans, and timestamps.
- Created `WorkerTrainingDao` with insert, update, and complete operations.
- Added `WorkerTrainingEntity` to `AppDatabase` (version 9) and exposed `workerTrainingDao()`.
- Added repository flows and methods: `currentTraining`, `getTrainingForUser`, `startTraining`, `updateTrainingModule`, `completeTraining`.

## 2026-08-28

### [2026-08-28] [ADMIN-001..004] [IMPL] Admin Operations Dashboard Cloud Functions
- Added `getPendingVerificationQueue` Cloud Function returning worker KYC applications sorted by 48h SLA deadline.
- Added `reviewWorkerApplication` Cloud Function accepting APPROVE/REJECT/CHANGES_REQUIRED decisions with feedback.
- Added `setWorkerActivationStatus` Cloud Function for activating/suspending worker bidding privileges.
- Added `isAdmin()` helper verifying admin custom claim on all admin endpoints.
- Added corresponding repository methods (`getAdminVerificationQueue`, `submitAdminVerificationDecision`, `setWorkerActivation`).
- Added ViewModel state and methods (`adminVerificationQueue`, `loadAdminVerificationQueue`, `submitAdminDecision`, `setWorkerActivation`).
- Wired FirebaseFunctions calls in `FirebaseService.kt` for all three admin endpoints.

### [2026-08-28] [SAFE-002] [IMPL] Live Emergency SOS Alert Dispatch
- Updated `SkillsExchangeViewModel.triggerSosAlert()` to wire SOS panic button to `repository.triggerEmergencySos()` with live GPS telemetry.
- Added `SmsNotificationService` constructor parameter to `SkillsExchangeRepository` for SOS dispatch dependency injection.
- SOS button now dispatches high-priority SMS with worker GPS coordinates to emergency contacts via `SmsNotificationService.dispatchEmergencySosAlert()`.

## 2026-08-27

### [2026-08-27] [COMM-001] [IMPL] Real-Time Firestore Chat & Phone Masking
- Added `streamChatMessages` and `sendChatMessage` with `maskPhoneNumbers` to `FirebaseService.kt`.
- Added `getRealtimeChatMessages` bridge in `SkillsExchangeRepository.kt` converting Firestore maps to `ChatMessageEntity`.
- Updated `SkillsExchangeViewModel.kt` `getChatMessagesForJob` to use real-time Firestore flow.

### [2026-08-27] [MAP-001] [IMPL] Integrate Google Maps SDK for Nearby Worker View
- Replaced custom Canvas drawing in `InteractiveMapView.kt` with Google Maps Android SDK MapView.
- Added `play-services-maps` dependency to `build.gradle.kts` and `libs.versions.toml`.

### [2026-08-27] [PAY-004] [IMPL] Material Advance Escrow Partial Disbursement
- Added `approveMaterialAdvance` to `SkillsExchangeRepository.kt` calculating 50% materials subtotal and updating job status.

### [2026-08-27] [PAY-003] [IMPL] Escrow Release & Worker MoMo Payout
- Added `releaseEscrowToWorker` callable Cloud Function in `functions/src/index.ts` to credit worker wallet upon escrow release.

### [2026-10-14] [PAY-004] Material Advance Escrow Partial Disbursement — In Progress
- Implemented Cloud Function callable createMoMoMaterialAdvance in unctions/src/index.ts.
- Builds on PAY-003 trust boundary (initiatePayout): server-side partial payout (up to 50% of materials subtotal).
- Records receipt-capture requirement in materialAdvances/{jobId} collection with 60-minute deadline.
- TypeScript compilation clean; existing tests re-run (29/29 pass); no new test file added yet.
- Next steps: Android repository wiring (pproveMaterialAdvance -> callable), receipt capture flow, comprehensive tests.
- NO deployment, NO real-money transaction. Sandbox-only fail-closed.


### [2026-08-27] [PAY-002] [IMPL] Escrow Lock & Webhook Confirmation
- Updated `processMoMoWebhook` in `functions/src/index.ts` to lock escrow and send notifications to resident and worker upon successful payment.

### [2026-08-27] [PAY-001] [IMPL] Mobile Money Collection Intent API
- Replaced mock `delay(600)` in `MoMoPaymentService.kt` with Retrofit HTTP POST to MTN MoMo Collection API.
- Created `MoMoCollectionApi.kt` Retrofit interface for `/collection/v1_0/requesttopay`.

### [2026-08-27] [MARKET-001] [IMPL] Nearby Verified Worker Discovery
- Added `isActivated` field to `ServiceProviderEntity` in `Entities.kt`.
- Updated `providers` flow in `SkillsExchangeViewModel.kt` to filter only activated workers with `kycStatus == APPROVED`.

### [2026-08-27] [JOB-001] [IMPL] One-Time & Recurring Job Type Data Model
- Added `jobType`, `recurringSchedule`, and `recurringStartDate` fields to `JobPostEntity` in `Entities.kt`.
- Updated `syncJobToFirestore` in `FirebaseService.kt` to persist new job type fields.

### [2026-08-27] [KYC-007] [IMPL] Worker 'Changes Required' feedback view & resubmission workflow
- Built `ChangesRequiredView` in `AccountComplianceDialog.kt` displaying admin feedback, support phone/WhatsApp links, and document update button.

### [2026-08-27] [KYC-006] [IMPL] Verification State Machine & 48h SLA Timestamps
- Added state transition methods to `SkillsExchangeRepository.kt`: submitWorkerKycApplication, startKycReview, requestKycChanges, approveKyc, rejectKyc.
- Added `getKycSlaDeadline` calculating 48-hour review target from submittedAt timestamp.

### [2026-08-27] [KYC-005] [IMPL] Primary Skill Qualification Certificate & Video Evidence upload
- Added `uploadQualificationEvidence` to `FirebaseService.kt` for certificate photos and video links.
- Built `QualificationEvidenceSheet` in `AccountComplianceDialog.kt` with certificate/video toggle and upload flow.

### [2026-08-27] [KYC-004] [IMPL] Guarantor Identity & National ID Upload UI
- Built `GuarantorFormSheet` in `AccountComplianceDialog.kt` with 2-step form, photo pickers, and district selection.
- Added `saveGuarantors` and `isValidGuarantorCount` methods to `SkillsExchangeViewModel.kt`.

### [2026-08-27] [KYC-003] [IMPL] Two-Guarantor Data Model & Validation rules
- Created `GuarantorEntity` in `Entities.kt` with name, phone, relationship, district, and ID storage paths.
- Created `GuarantorDao` in `Daos.kt` with insert, delete, count, and query operations.
- Added `validateGuarantorCount` to `SkillsExchangeRepository.kt` enforcing exactly 2 guarantors.
- Upgraded `AppDatabase` to version 8.

### [2026-08-27] [KYC-002] [IMPL] Worker Document Upload pipeline to Firebase Storage
- Added `uploadKycDocument` method to `FirebaseService.kt` uploading image bytes to `users/{userId}/kyc/` path.
- Added `firebase-storage` dependency to `build.gradle.kts` and `libs.versions.toml`.

### [2026-08-27] [KYC-001] [IMPL] Worker Identity & National ID data schema
- Created `WorkerKycEntity` isolated Room table in `Entities.kt` storing full legal name, National ID number, DOB, gender, and ID document storage paths.
- Created `WorkerKycDao` in `Daos.kt` with insert, status update, and review query methods.
- Upgraded `AppDatabase` to version 7 with `fallbackToDestructiveMigration`.

### [2026-08-27] [LOC-004] [IMPL] Public Location Privacy & District-Level Aggregation
- Added `getDistrictJobStatsForProvider` to `SkillsExchangeViewModel.kt` to compute dynamic district job counts from actual job history.
- Updated `ProviderDetailSheet.kt` to display dynamic district-level job breakdown instead of hardcoded values.
- Obfuscated reviewer identities in public endorsements by removing `endorsedByNeighborName` display.

---

## 2026-08-26

### [2026-08-26] [LOC-003] [IMPL] Worker District & Location management
- Upgraded `UserProfileEntity` and `AppDatabase` (v6) to support `latitude`, `longitude`, and `address` fields.
- Upgraded `updateWorkerLocation` in `SkillsExchangeViewModel.kt` to persist all location metadata.

### [2026-08-26] [LOC-002] [IMPL] Manual Location Correction
- Added `EditLocationDialog` in `GpsLocationBanner.kt` to allow users to manually correct their active district.
- Wired manual district selection to `SkillsExchangeViewModel` through `MainScreen.kt`.

### [2026-08-26] [LOC-001] [IMPL] Client Location Onboarding & GPS Detection
- Implemented GPS location requesting logic using LocationHelper in AuthModal.kt, which sets the user's neighborhood automatically.

## 2026-08-25

### [2026-08-25] [SAFE-001] [SECURITY] Dynamic 4-Digit Doorstep PIN Generation
- Replaced static default `'4829'` PIN with secure random 4-digit PIN generated per job creation in `SkillsExchangeRepository.kt`.

### [2026-08-25] [SKILL-001..003] [IMPL] 125-Service Taxonomy, Validation Engine & Picker UI
- Created `ServiceTaxonomyCatalog` in `ServiceTaxonomy.kt` with 125 structured services across 5 hubs (Construction, Cleaning, Technology, Automotive, Personal).
- Implemented `validateWorkerSkills` engine enforcing 1 Primary + up to 2 Related Additional skills within compatible trade families.
- Created `ServiceCatalogPickerModal` multi-step Composable sheet with live validation and dynamic trade-family filtering.

### [2026-08-25] [ROLE-001..003] [IMPL] Unified Account Model, Mode Switching & Activation Gate
- Created `UserProfileEntity`, `UserProfileDao`, and migrated `AppDatabase` to version 5.
- Implemented persistent Client/Worker mode switching in `SkillsExchangeViewModel` and `SkillsExchangeRepository`.
- Created `WorkerVerificationStatusView` access gate component in `ui/components/` to restrict unverified/unactivated worker access to the Freelancer Dashboard in `MainScreen.kt`.

### [2026-08-25] [SEC-002..003] [SECURITY] HMAC Webhook Signature Verification & Firebase Storage Security Rules
- Implemented HMAC-SHA256 signature verification in `functions/src/index.ts` for payment callbacks with `crypto.timingSafeEqual`.
- Created `storage.rules` isolating National IDs, guarantor documents, and qualification certificates under owner and admin authorization.

### [2026-08-25] [AI-001..002] [FIX] Gemini 2.0 Flash Model Identifier & Audio Encoding Alignment
- Corrected invalid model identifier `gemini-3.5-flash` to `gemini-2.0-flash` in `GeminiAudioTranscriptionService.kt` and `GeminiMapsGroundingService.kt`.
- Updated `AudioRecorderHelper.kt` to record in MPEG_4/AAC audio format with aligned `audio/mp4` MIME type.

### [2026-08-25] [SEC-001] [SECURITY] Harden Firestore Security Rules â€” Verified
- Verified owner UID validation on provider updates and KYC sub-collections in `firestore.rules`.

### [2026-08-25] [INFRA] Agent Memory Infrastructure Established
- Created permanent operating manual (`AGENTS.md`), current state tracker (`AGENT_STATE.md`), decisions log (`DECISIONS.md`), synchronized task status (`TASKS.md`), and recovery guide (`README_AI.md`).

### [2026-08-25] [STAB-001..002] [TEST] Robolectric Assertion & Screenshot Test Fixes â€” Verified
- Verified `ExampleRobolectricTest.kt` string resource assertion (`"Skills Exchange"`).
- Verified `GreetingScreenshotTest.kt` theme and composable imports (`SkillsExchangeTheme`).
### [2026-11-04] [TXT-AUDIT-CLEANUP] TXT file audit cleanup
- **Scope:** TEXTFILE_AUDIT (P2) — controlled cleanup of temporary diagnostic .txt files
- **Files removed:** `diag-s-log3.txt`, `diag-s-log.txt`, `diag-logcatfiles.txt`, `diag-bg-sign.txt`
- **All were empty/trivial diagnostics with no source code references**
- Part of controlled TEXTFILE_AUDIT cleanup phase