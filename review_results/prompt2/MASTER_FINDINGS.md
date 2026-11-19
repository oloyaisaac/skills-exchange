# MASTER FINDINGS — Prompt-2 strict sequential cycle

**Baseline:** `aa4a0bc` · **Date:** 2026-11-20 · **Reviewers:** 15/15 examined ·
**Verdict:** NOT PRODUCTION-READY (no reviewer issued PASS)

This file consolidates the 15 reports in this directory. It does not replace the first-cycle
`review_results/MASTER_REVIEW_FINDINGS.md`; history is preserved.

## 1. Critical findings

| ID | Finding | Reviewer(s) |
|---|---|---|
| P2-C1 | Escrow release/lock can be displayed as successful and write `RELEASED_TO_WORKER` with no server call or provider disbursement | R02, R07, R08 |
| P2-C2 | Doorstep PIN verified against a client-supplied expected value, default `"4829"`, plaintext, no lockout | R07, R10 |
| P2-C3 | Account deletion deletes Firebase Auth even when Firestore deletion fails, and never deletes Storage photos; rules forbid the `users/{uid}` delete it attempts | R06, R07, R09 |
| P2-C4 | `SmsNotificationService` reports `SENT` with no provider call, including SOS | R05, R10 |
| P2-C5 | Wallet ledger and withdrawals exist only in Room; no server settlement, reinstall loses balance | R04, R08 |
| P2-C6 | Reports are Room-only with no admin surface; SOS dispatch outcomes never shown to the user | R10 |

## 2. High findings

| ID | Finding | Reviewer(s) |
|---|---|---|
| P2-H1 | Two `FirebaseService` instances and direct UI→Firebase reads; 2,898/2,792-line god classes | R01 |
| P2-H2 | FCM token never registered server-side and logged in plaintext; only SOS pushes | R05, R06 |
| P2-H3 | Currency unit mismatch (USD display multiplier vs whole-UGX server) | R05 |
| P2-H4 | Material advance writes PENDING before authorization and cannot satisfy the payout validator | R08 |
| P2-H5 | Phone and Google identities never linked; duplicate accounts | R06 |
| P2-H6 | Consent and DSAR export unreachable from the UI; no retention policy enforced | R09 |
| P2-H7 | Offline queue never executes; pending actions accumulate forever | R13 |
| P2-H8 | 112 PRD stories marked completed without evidence; traceability covers 24 of 185 | R15 |
| P2-H9 | No rules emulator tests, no Compose UI tests, Android not built in CI | R11, R14 |
| P2-H10 | Repository ships 64 MB of APKs plus a device SQLite DB and crash logs | R14 |

## 3. Medium findings (selected)

- Escrow status fields not pinned by rules (R07).
- No App Check; OTP has per-phone but no IP/device throttle (R03, R07).
- Node 18 engine, no `predeploy`, no `firestore.indexes.json` (R03, R14).
- `exportSchema = false`; 15/16 migrations untested; `Double` money (R04).
- No duplicate NIN/guarantor detection; ratings device-local (R10).
- One string resource; 59 null content descriptions; no semantics; deceptive safety copy (R12).
- No performance baselines, crash/ANR telemetry, paging or minification (R13, R14).
- `AGENTS.md`/`README_AI.md` describe a Windows/D: environment that no longer exists (R15).

## 4. Improvements since the first cycle (verified statically and by tests)

- World-readable `jobs`/`messages` rules fixed; KYC/guarantor Storage private.
- Africa's Talking and MTN device credentials removed; OTP and MoMo are server-side.
- Webhook authenticates, throttles and re-queries the provider before mutation.
- Collection/payout/refund money paths validate server-side amounts; device fake payout removed.
- Admin console gated by a server-verified claim with audited provisioning.
- Logout revokes refresh tokens; fresh-auth window on destructive deletes.
- Backend suite 171/171 green; documentation checkers green; query limits enforced.

## 5. Verdict

**NOT PRODUCTION-READY.** Fifteen reviewers examined their assignments; all returned FAIL. Six critical
clusters remain (money state, PIN, erasure, SMS honesty, ledger, reports/SOS feedback). Dynamic
verification of every Android, rules, provider and deployment claim is still required in a provisioned
environment.
