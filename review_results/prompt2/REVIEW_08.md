# R08 — Payments, Escrow & Financial Controls Auditor

| Field | Value |
|---|---|
| Reviewer | R08 — Payments & Escrow (P0) |
| Assignment | `reviewers/PAYMENTS_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no payment code changed |
| Provider sandbox | **UNVERIFIED** — no MTN credentials, no emulator |
| Verdict | **FAIL** |

## 1. Money flows

| Flow | Authorization | Idempotency | Ledger / record | Failure handling |
|---|---|---|---|---|
| Collection initiation | owner uid, server amount | pending-collection gate | `momo_transactions` | PENDING only; provider errors typed |
| Confirmation | webhook HMAC + re-query | terminal-state check | escrow lock once | mismatch → NEEDS_RECONCILIATION |
| Release (`releaseEscrowToWorker`) | owner uid, funded escrow, assigned worker | `PAYOUT-{jobId}` + alreadyReleased | `momo_payouts` QUEUED | **no disbursement call** (F1) |
| Payout (`createMoMoPayout`) | owner uid | payout record gate | `payouts` map | provider call, status query |
| Refund | admin claim / dispute | one per job, refund doc | `refunds` | provider failure → FAILED, escrow untouched |
| Material advance | owner via payout core | `materialAdvances/{jobId}` | Firestore + provider | validation conflicts (F3) |
| Worker cashout | Firebase UID, ledger balance | idempotency key | **Room only** | device-local (F2) |

## 2. Release path does not move money (critical)

`releaseEscrowToWorker` writes a QUEUED `momo_payouts` document, sets the job to
`escrowStatus = RELEASED_TO_WORKER`, and returns "payout queued" (`index.ts:640–659`). There is **no
provider call** in that function and **no trigger** on `momo_payouts` that would process the queue
(grep: only reads at `:2411`). The worker is never paid through this path, while the UI and job state
say released. This is the explicit R08 fail condition ("no server-side payout evidenced") in a new
form: the callable exists but only records an intent.

The separate `createMoMoPayout` does call `initiatePayout` (provider disbursement), so two release
paths exist with different money behaviour. The Android completion and escrow-sheet flows do not use
`createMoMoPayout`; they use the non-paying one (R02 also shows the sheet bypasses both).

## 3. Wallet ledger is device-local (critical)

`WalletLedgerEntity` / `WalletWithdrawalEntity` live in Room (`Entities.kt:773–824`); the backend has
no wallet, ledger or withdrawal collection (grep in `functions/src` = 0 wallet references). Cashout
validation is correct *relative to the local ledger*, but:

- reinstalling or clearing app data loses the payable balance;
- the same account on two devices sees two different balances;
- no server record backs a withdrawal or its reserve;
- there is no settlement or reconciliation between payout records and a worker balance.

## 4. Material advance defects

`createMoMoMaterialAdvance` (`index.ts:1906`):

1. Writes `materialAdvances/{jobId}` with `status = PENDING` **before** any ownership check; the check
   only happens inside `initiatePayout` afterwards. A non-owner can create the PENDING record, and a
   second legitimate attempt then sees "already-pending" and is blocked (griefing / denial of advance).
2. It passes `amountUgx = advanceAmount` (50% of a client-supplied `materialsSubtotal`) into
   `validatePayoutInput`, which requires the amount to equal `job.laborTotal` (R03/R08 rule). Unless
   half the materials happens to equal labour, every advance fails with `amount_mismatch`; when it
   succeeds it is because the client's `materialsSubtotal` was chosen to satisfy that equation.
3. `workerMomoNumber` is client-supplied and only checked against `job.momoPhoneNumber` when present.

## 5. Fees, rounding and currency

- The 10% fee is computed on the client (`Repository.kt:565`) and validated server-side against the
  stored quote (`pricing.ts`, REM-029). No fee ledger, split, or remittance record exists; the whole
  `grandTotal` is collected into the platform merchant account and the worker payout covers only the
  job amount, so the fee position is implicit rather than accounted.
- Currency: display treats stored amounts as USD and multiplies by 3,700 (R05), server requires whole
  UGX. Money is `Double` on the client and `number` server-side, with `round2` only in pricing.

## 6. Controls absent

- No reconciliation job/report against provider statements (R08 responsibility 6).
- No velocity limits, destination allow-list or name match on payouts/cashouts.
- Destination changes for cashout do not require re-authentication (only account deletion does).
- Segregation of duties: the same owner who funds can release; acceptable for a marketplace, but no
  admin/second approval exists for refunds above a threshold (`refundEscrow` is admin-only but
  unlimited).
- Sandbox/production separation: `loadMomoConfig`/`loadPayoutConfig` hard-refuse non-sandbox hosts
  (good), so production cannot be enabled without a code change — a deliberate fail-closed posture.

## 7. Provider-reference integrity

- Collection: provider reference stored and used for refund lookup; webhook never mutates without a
  re-query; terminal states are guarded. Good.
- Payout: `initiatePayout` stores server-minted UUID references; `releaseEscrowToWorker`'s queued record
  has **no** provider reference (it never calls the provider), which is exactly why it cannot be
  reconciled.
- Refund: `refunds/{uuid}` stores `providerReferenceId` (original) and provider HTTP status; status
  polling endpoint is not implemented (`getMoMoPayoutStatus` covers payouts only).

## 8. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R08-P2-01 | **CRITICAL** | Release marks escrow RELEASED and queues a payout without any disbursement call; no queue processor exists | `index.ts:640–659`; no `momo_payouts` trigger |
| R08-P2-02 | **CRITICAL** | Wallet ledger/withdrawals are Room-only; no server settlement, reinstall loses balance | `Entities.kt:773–824`; no wallet code in `functions/src` |
| R08-P2-03 | HIGH | Material advance writes PENDING before authorization and its amount cannot satisfy the payout validator | `index.ts:1948–1995` |
| R08-P2-04 | HIGH | Two payout implementations with different money semantics | `releaseEscrowToWorker:560`; `createMoMoPayout:1824` |
| R08-P2-05 | MEDIUM | Fee is not ledgered or split; collected implicitly | `Repository.kt:565`; `pricing.ts` |
| R08-P2-06 | MEDIUM | No reconciliation, velocity, allow-list or destination-change re-auth | repo-wide absence |
| R08-P2-07 | LOW | Refund status is not polled; partial refunds do not adjust escrow/ledger | `refund.ts`; `index.ts` refund block |

## 9. Verdict

**FAIL.** Funding is well controlled (server amount, re-query, idempotent refunds) and the device fake
payout is gone, but release does not move money, the worker ledger has no server existence, and the
material-advance path is both unauthorized-write-first and arithmetically incompatible with the payout
validator. All provider behaviour is **UNVERIFIED**.
