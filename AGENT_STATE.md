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

