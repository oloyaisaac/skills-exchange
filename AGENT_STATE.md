## 2026-11-20 (Prompt-4 R12 COMPLETE) - UI/UX AND ACCESSIBILITY GATE PASSED

- R12 closed on `arena/e2ab81c4-skills-exchange`. The review could not render, so its findings were
  re-checked against the code first: two were already closed (escrow in-flight states and
  `FLAG_SECURE`, both R02) and are recorded as stale rather than re-implemented.
- Screens are navigable now. `app/src/main/java/com/example/ui/a11y/Accessibility.kt` adds
  `sectionHeading`, `emergencyLiveRegion`, `announcementLiveRegion`, `describeState` and
  `minTouchTarget`, applied to the panic control, the dispatch verdict, release feedback, onboarding
  errors, the safety desk and the offline banner. `AccessibilitySemanticsTest` reads the tree: Heading
  on the role question, assertive LiveRegion on the dispatch verdict, polite on the offline banner,
  a stateDescription on the panic button. The 58 null descriptions were triaged (decorative or beside
  a label), not counted.
- Ten controls were too small to hit: a 40 dp panic button, a 28 dp auth dismiss, and eight
  `IconButton`s at 20-36 dp because an explicit `Modifier.size` overrode Material's 48 dp. All are
  48 dp, two are asserted at that floor, and a guard refuses a sub-48 dp `IconButton`.
- Contrast is measured by `ContrastTest`, not eyeballed. It found white on `IndustrialEmerald` at
  3.77:1 (now 5.48:1) and `Slate400` at 2.56:1 while painting real sentences (now a 3.5:1 graphics
  tone, 19 text usages moved to `Slate500` at 4.76:1); a later run caught `RedDanger` on its own error
  surface at 4.41:1 (now 5.24:1). The escrow sheet is rendered at a 200 percent font scale.
- Copy: 15 critical strings externalised to `values/strings.xml` with a drafted `values-sw` locale and
  a Swahili render asserted in CI. The translations are DRAFTS with no native-speaker review. The
  other 472 inline sentences are baselined by `scripts/check_ui_copy.mjs`, which fails if the number
  rises and also refuses an unlabelled `IconButton`. Luganda, Kinyarwanda and pseudo-locales are not
  started.
- The privacy sheet no longer claims a "24/7 neighborhood arbitration protocol": the desk is
  single-operator with no on-call rota, and the copy now says what happens and what to do instead.
- Evidence: Android **489/489** (run 44795261468); eleven static guards plus the UI-copy guard and its
  self-test; 29 documents; 36 requirements. Three Android runs failed before the green one, and each
  failure is fixed in the round: a missing Compose `assert` import, RedDanger at 4.41:1, and an
  ellipsis mismatch in an existing test.
- Not verified, and said so: no TalkBack, Accessibility Scanner, switch-access or screen-curtain pass;
  no native-speaker translation review; no screenshot baseline; no dark-theme contrast; no RTL; and
  nothing rendered on a device. `REM-055` to `REM-058` stay `IN_PROGRESS`. R13 is next.

---

## 2026-11-20 (Prompt-4 R11 COMPLETE) - QA GATE PASSED

- R11 closed on `arena/f11c33ea-skills-exchange`. The review
  (`review_results/prompt2/REVIEW_11.md`) said the test programme was a claim. Four of its seven
  findings were already closed by earlier rounds and are recorded as stale, not re-implemented: the
  rules suite (26 emulator tests, R07), the Compose tests (7 in 3 files, R02) and the Android CI job
  (478 tests, R01). The stale `ci.yml` header claiming that job was "intentionally absent" is fixed.
- Suites can no longer exist and never run. `functions/package.json` `test` is the glob
  `node --test test/*.test.mjs`; `scripts/check_test_registration.mjs` fails the build when a test
  file is executed by no script, when `test:coverage` covers a different set, or when CI stops calling
  a suite. Its `--selftest` plants an unregistered suite and proves the guard refuses it.
- Coverage is measured and gated. Backend `test:coverage` enforces 90 lines / 80 branches / 90
  functions against a measured 96.38 / 85.94 / 96.81 on Node 22.22.3 (a `--test-coverage-lines=99`
  run fails, so the floor is enforced). Client: JaCoCo 0.8.12, `jacocoTestReport` and
  `jacocoTestCoverageVerification` (LINE 0.20, generated code excluded), both invoked by
  `android.yml`, with the LINE total printed per run and `android-coverage` uploaded.
- The emulator handler suite grew from 5 to 19 tests (fileReport, reports queue, resolveReport,
  submitRating, getSosDispatchStatus, getSafetyMetrics); the CI step asserts `# pass 19` so a suite
  that runs nothing fails. The backend unit suite is repeated three times per PR with an identical
  pass count: 310/310 three times locally.
- Stale evidence is gone: 190 tracked artefacts (74 MB), including `build.log`, `c123_verify_*.log`,
  `diag-*.txt`, `ui-*.xml`, two 32 MB APKs and stray `res/`/`META-INF/` output, described commits that
  no longer exist. README now names the command instead of stating counts (it said 171 backend / 355
  Android against 310 / 478), and `docs/qa/TEST_STRATEGY.md` records inventory, gates, floors, flake
  policy, a critical-path matrix and seven gaps by risk.
- Beyond the findings: `check_document_map.mjs` read only one directory level, so documents under
  `docs/privacy/`, `docs/safety/` and `docs/qa/` could be added unseen. It walks the tree now: 22 to
  28 documents verified.
- Evidence: backend 310/310 three times, coverage floors met, rules 26, eleven static guards plus two
  self-tests green, traceability at 32 requirements (2 without a test). CI **7/7** (runs 44789646720
  and 44789646910): handler tests 19/19 on the emulator, Android job green with the JaCoCo steps. The
  new handler gate failed its first execution, on an assertion that treated `breachingSla` as an
  envelope field; the exact-count assertion surfaced it, and the fix is in the same round. Android's
  coverage figure is in the `android-coverage` artifact, which this sandbox cannot download, so no
  number is quoted here.
- Not verified, and said so in the docs: no instrumented device test (`app/src/androidTest` is the
  template), no coverage number for `lib/index.js`, no flake evidence for the Android or rules suites,
  no provider sandbox tests, no screenshot baseline, no load testing. Nothing is deployed. `REM-065`
  and `REM-066` stay `IN_PROGRESS` for those reasons. R12 is next.

---

## 2026-11-20 (Prompt-4 R10 COMPLETE) - SAFETY GATE PASSED

- R10 closed on `arena/a19ae51b-skills-exchange`. The review (`review_results/prompt2/REVIEW_10.md`,
  written against `aa4a0bc`) said safety was a set of screens, not records. Each finding re-checked
  before any change; two had moved under later rounds and are recorded that way rather than re-fixed:
  the client-side PIN comparison (R07) and the `0,0` sentinel on the SOS raise path (R02).
- Reports are now records. `reports.ts` owns validation, the priority ladder (`SAFETY` always P0;
  `FRAUD`/`HARASSMENT` escalate at one prior open report, the rest at two) and a forward-only triage
  state machine; `fileReport` writes the record and pages `notifications/safety_desk` for a P0;
  `getReportsQueue`/`resolveReport` are admin-gated; the rules give the reporter read access and give
  clients no write. The app files through the callable and shows the server's sentence; the new
  `SafetyReportQueueSheet` is the operator console, with a metrics line that renders "unavailable"
  rather than zeros when the read fails.
- SOS copy is honest. `sosStatus.ts` describes the recorded `dispatch` (`SENT`/`FAILED`/
  `NO_RECIPIENTS`/`PENDING`/`STALE`/`UNKNOWN`), appends "call the police or an ambulance yourself now"
  to everything that is not a confirmed send, and the view model polls while pending, shows the
  sentence in the banner and writes it to the job chat.
- Guarantor identity reuse is refused server-side (salted NIN fingerprints, last-nine-digit phone
  keys, self-guarantee, duplicate, already-used, 4th submission in <24h) and audited in
  `guarantor_intake_audit` with codes and counts only. `guarantors`/`guarantor_submissions` are
  server-only in the rules; the client may not write a guarantor row at all.
- Ratings moved to the server (`ratingsCore.ts`, `ratings/{jobId}_{raterUid}`, `worker_stats`
  aggregate in one transaction). The device keeps a copy only after the server accepts, and a refusal
  reaches the sheet as the server's own sentence.
- The invented location is gone from the failure path: `requestGpsDetection` reports no fix (no
  coordinates, no district, `isUsingCurrentLocation = false`), `GpsLocationState` no longer defaults
  to claiming a position, `LocationHelper` returns an error instead of Kampala city-centre
  coordinates, and `updateWorkerLocation` keeps the stored position when there is no fix.
- The shared PIN default is closed for existing installs: schema **v29** (`MIGRATION_28_29`) blanks
  `doorstepPin` rows still holding `'4829'`; the DDL default in the creating migration is `''`. A
  migration test proves a real PIN survives.
- CI: 7/7 green (run 44784548015). CI caught two defects that reading did not: `ReportUiState` was
  referenced as a nested class when it is top-level, and the new migration test hand-wrote a partial
  `job_posts` INSERT that violated a NOT NULL column a later migration added; the INSERT is now
  generated from the exported schema.
- Evidence: backend `npm run build && npm test` **310/310** (four new files registered in
  `functions/package.json`), rules **26** tests in CI, ten static guards pass, traceability at 30
  requirements. Client compiles and tests run in CI only (no JDK here).
- Not verified, and said so in the docs: nothing deployed, no live SMS/FCM dispatch, no operator has
  ever triaged a report, the desk is single-operator with no dual control and no on-call rota
  (`docs/safety/SAFETY_DESK_RUNBOOK.md`), the seeded demo provider rows still carry illustrative
  coordinates (demo data, never a device fix), and off-platform-contact detection (review section 6,
  no finding id) remains unimplemented. R11 is next.

---

## 2026-11-20 (R09 gate audit, PR #25) - THREE OPEN CONDITIONS CLOSED

- After the R09 merge I audited the reviewer's section 11 PASSING CONDITIONS, not just the seven
  findings. Three were not actually satisfied:
  1. `private_docs/{uid}/` is owner-writable in `storage.rules` (nothing writes there today) but was
     missing from `storagePurgePrefixes`, i.e. a person could upload a document that would outlive
     their erasure. Added, and a test asserts every owner-writable prefix is purged.
  2. "No PII in logs" rested on `scrubForLog`, which had no caller. Eleven log and persisted-report
     sites now scrub provider error bodies; `scripts/check_no_pii_logs.mjs` fails the build on an
     unmasked identifier and has a `--selftest` that proves it can fail.
  3. The brief's required evidence (data inventory: category/subject/purpose/basis/location/
     recipient/retention) did not exist. `docs/privacy/ROPA.md` now lists 16 categories and states
     five gaps, including that no DPA is claimed and provider-side copies are outside the sweep.
- `scripts/check_backup_policy.mjs` pins allowBackup=false and every excluded domain, so the backup
  finding cannot silently regress. It is a static check; no on-device restore test is claimed.
- Backend 273/273 after the new prefix test; all guards pass locally.

## 2026-11-20 (Prompt-4 R09 COMPLETE) - PRIVACY GATE PASSED

- R09 closed on `arena/e11de41e-skills-exchange` (PR #24). The review's point was that privacy was a
  document, not a control: erasure missed the worker's own chat in the resident's job, `recordConsent`
  had no caller, `exportMyData` had no entry point, nothing purged anything on a schedule, and the UI
  claimed encryption the code did not implement.
- Erasure: `deleteMyAccount` also purges the account's messages in jobs it was assigned to (matched on
  `senderId`; a name-only legacy attribution is not deleted because ownership is not provable), writes
  an `account_deletions` record with counts and retained categories, and returns a plain-language
  notice. `ERASURE_STEPS` is the published order; Auth is last and only when nothing is pending.
- Consent: the privacy sheet records a receipt and cannot claim agreement without one; third-party
  guarantor consent is a separate dated grant (`recordGuarantorConsent`), required by `firestore.rules`
  on both `guarantors` and `guarantor_submissions`; `withdrawConsent` records revocation on the receipt
  instead of deleting the proof. `scripts/check_consent_version.mjs` keeps client and server versions
  equal.
- Retention: `functions/src/retention.ts` (policy as data, legal holds, pure decision + plan), a daily
  `applyRetentionPolicy` sweep writing `retention_reports`, and undated documents retained and counted
  rather than purged. New `docs/privacy/RETENTION.md`; `docs/privacy/DSAR.md` corrected to the code.
- Data minimisation: `functions/src/pii.ts` (masking, salted fingerprint, log scrubbing, third-party
  redaction for the subject export); the reviewer's NIN verification now stores `ninMasked` and
  `ninFingerprintHash`, both admin-only in rules.
- Evidence: backend 272/272, Firestore rules 24/24, seven CI checks green (run 44766861805). CI caught
  a test that proved the wrong rule (denials asserted after creating the document) - fixed by ordering.
- Not claimed: deployment, the sweep firing, Storage purge against a real bucket, backups, and any
  regulator/DPA/provider-sandbox claim (all UNVERIFIED). REM-037 stays IN_PROGRESS by decision.

## 2026-11-20 (Prompt-4 R08 COMPLETE) - PAYMENTS GATE PASSED

- R08 closed on `arena/5eb05553-skills-exchange`. The review's verdict was that the platform kept
  "bookkeeping that looks like payment": the release marked the worker paid and wrote a QUEUED
  `momo_payouts` record that nothing read; the wallet existed only in Room; the fee was a client
  estimate; and no sweep ever compared the platform with the provider.
- The escrow release now calls the provider through `initiatePayout` BEFORE it marks the job
  released. 202 -> PAYOUT_PENDING with a server-minted reference; anything else throws and leaves the
  escrow funded. A provider failure can no longer look like a completed payment.
- One money core: `submitDisbursement` is the only code that talks to the disbursement provider. The
  release, the material advance and the wallet cashout are thin front ends; the idempotency gate is
  scoped by payoutKind, so an advance no longer blocks the release it funds, and a legacy
  `momo_payouts` record is reported as PAYOUT_UNCONFIRMED rather than assumed paid.
- The wallet left the phone: `wallet_entries` + `wallets/{uid}` hold the ledger and the balance,
  `getWallet` / `requestWalletCashout` / `cancelWalletCashout` decide everything server-side, and a
  cashout reserve is atomic with the balance. Minimum UGX 5,000, max UGX 2,000,000 and three requests
  a day, verified destination only, and a number changed inside 24 hours must be re-verified.
- Material advance: authorize first, then claim the slot in a transaction. The amount is half of
  `job.materialsSubtotal` from the job document and the payee comes from `users/{workerId}`. The old
  path could never pass its own validator (it compared half the materials with the labour total), so
  every honest advance failed; `expectedAmountUgx` fixes that.
- The fee is ledgered: one immutable `platform_fees/{FEE-<jobId>}` per release, validated so labour +
  materials + fee equals what was collected. Worker gets labour, platform keeps the fee.
- Reconciliation: hourly `reconcilePayments` and `reconcileRefunds` settle what the provider confirms,
  flag amount disagreements for a human and write immutable reports. Refunds are polled, and a
  partial refund reduces the escrow while keeping the remainder payable.
- Evidence: backend 250/250 (48 new tests across wallet, material advance, fee ledger, reconciliation,
  payout core and refund status), rules 17 -> 20 emulator tests, callables 30 -> 34 all App Check
  wrapped, seven static guards pass. Client suite not run here (no JDK); CI runs it.
- Not verified: no MTN sandbox call was ever made, no deploy, the sweeps have never run against real
  data, and a release still pays Mobile Money directly - so its wallet line is a statement
  (`withdrawable: false`), not a spendable balance. Crediting it would let the same shilling be
  withdrawn twice; what funds a withdrawable balance is the open product decision.

## 2026-11-20 (Prompt-4 R07 COMPLETE) - APPLICATION SECURITY GATE PASSED

- R07 gate passed on `arena/503811fb-skills-exchange` (PR #22). Android run 44758119411 green
  (7/7 checks), backend 202/202 on run 44758119327, and a new CI job - "Firestore rules tests
  (emulator)" - green in 32s. Client suite: the readable XML from the preceding run of the same test
  set totals 466 cases; its two failures were an error in a new policy test, fixed before the green
  run, so 466 tests, 0 failures.
- Both critical findings closed. The doorstep PIN is server-held: salted SHA-256 in
  `job_secrets/{jobId}` (no client, admin included, may read it), constant-time comparison, five
  attempts then a fifteen-minute lockout, and the success path is the only way a job starts. The
  local comparator, the `4829` default and the worker's view of the PIN are gone. Account erasure
  regained owner-delete rules with a fresh sign-in for users, worker_kyc, worker_training and
  providers.
- Escrow can no longer be forged at the rules layer: release timestamps, payout fields and PIN
  material are pinned, terminal escrow outcomes and priced amounts after funding are Admin-SDK-only,
  and the client mirrors jobs with a merge that withholds exactly those fields.
- App Check is enforced on all 28 callables and installed in the client; avatars now need a session;
  the release build has R8 and resource shrinking.
- CI again found what reading did not: a new policy test assumed escrow and money withholding were
  independent.
- Not verified: no deploy (production rules unchanged), App Check has never minted a token, no
  release APK was built, Storage rules are only structurally checked, no on-device bypass attempts.
  R08 is next.

---

## 2026-11-20 (Prompt-4 R06 COMPLETE) - AUTHENTICATION & IDENTITY GATE PASSED

- R06 gate passed on `arena/2029ee23-skills-exchange` (PR #21). Android run 44752473651 green (6/6
  checks), backend 190/190 on run 44752473672. The readable per-test XML from the preceding run of the
  same test set totals 447 cases; its single failure was a bug in one of the new static scans, fixed
  before the green run, so the suite is 447 tests, 0 failures.
- All six findings closed. Deletion is now server-authoritative: `deleteMyAccount` purges Firestore and
  Cloud Storage (`users/{uid}/`, `avatars/{uid}/`, `jobs/{jobId}/`) with counted reports, refuses a
  missing or stale `auth_time`, and the client deletes the Firebase Auth user only on a complete purge.
  The dialog is state-driven, names the photos it removes, and offers in-app re-authentication (Google
  re-consent or SMS code). Google sign-in links to the existing account instead of creating a second
  uid; a collision is shown as text. Logout and deletion delete the FCM token locally and server-side.
  The Google web client id comes from build config, and an unset id disables the button with an
  explanation instead of launching an empty flow.
- CI again found what reading did not: a non-suspend teardown calling suspend functions, and a static
  scan that matched the helper's own suffix guard.
- Not verified: no Firebase project, device or `firebase deploy`, so live re-authentication, linking,
  revocation, `user.delete()` and Storage deletion against a real bucket remain UNVERIFIED; REM-025
  stays IN_PROGRESS because its required E2E link log cannot be produced here. R07 is next.

---

