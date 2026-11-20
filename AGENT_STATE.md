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

