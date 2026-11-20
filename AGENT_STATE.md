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

