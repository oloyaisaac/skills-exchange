# R08 — Payments, Escrow & Financial Controls Auditor

## 1. Reviewer Number
R08 (PRD story `RVW-008`, Priority P0). Depends on R01; coordinate with R03 (implementation) and R04 (ledger).

## 2. Professional Title
Payments & Financial Controls Auditor, mobile-money escrow systems, reconciliation and fraud controls.

## 3. Required Expertise
- MTN MoMo Collections/Disbursements API, request-to-pay, async confirmation, provider reference IDs.
- Escrow lifecycle design, funding/lock/release/refund state machines and terminal-state immutability.
- Idempotency, exactly-once money movement, duplicate-callback defence.
- Double-entry/derived ledger design, reconciliation, settlement and payout batching.
- Financial crime controls: velocity limits, destination validation, chargeback/dispute handling, segregation of duties.

## 4. Responsibilities
1. Map every money flow end-to-end: client → provider → escrow → worker/payout → refund.
2. Verify no device code can declare payment success or fabricate a payout.
3. Verify escrow release/refund is server-authoritative, idempotent and authorized.
4. Audit the wallet ledger and cashout controls for negative balances, double-spend and destination tampering.
5. Verify fees, commissions, rounding and currency handling.
6. Verify reconciliation exists between provider references, Firestore transaction records and the ledger.
7. Confirm sandbox/production separation and go-live controls.

## 5. Exact Features to Examine
- Escrow funding (collection initiation, confirmation, escrow lock).
- Escrow release to worker, material advance partial disbursement.
- Worker wallet, cashout request/cancel, withdrawal ledger.
- Dispute refunds and partial refunds.
- Payment channel selection (MTN/Airtel/cash) and phone capture.
- Pricing, platform fee and emergency add-on.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `functions/src/momoCollection.ts` | config, initiation, provider reference, amount checks |
| `functions/src/momoConfirmation.ts` | pull-to-verify, escrow lock, idempotent terminal states |
| `functions/src/momoDisbursement.ts` | payout eligibility, payloads, terminal states |
| `functions/src/index.ts` | `createMoMoCollection`, `getMoMoCollectionStatus`, `processMoMoWebhook`, `releaseEscrowToWorker`, `createMoMoPayout`, `createMoMoMaterialAdvance`, `getMoMoPayoutStatus` |
| `functions/test/momo*.test.mjs` | 4 test files (111 tests total with guarantorReview) |
| `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` | escrow/status transitions, material advance, cashout |
| `app/src/main/java/com/example/data/repository/WorkerCashoutSecurityCore.kt` | ledger-derived balance, withdraw transitions, idempotency |
| `app/src/main/java/com/example/data/db/Entities.kt` | `JobPostEntity` escrow fields, `WalletLedgerEntity`, `WalletWithdrawalEntity` |
| `app/src/main/java/com/example/data/remote/MoMoPaymentService.kt` | deprecated device gateway; `disburseEscrowPayoutToWorker` returns `DISBURSED` without moving money |
| `app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt` | payment UI, PIN, release controls |
| `firestore.rules` (`momo_transactions`, `momo_payouts`) | server-owned money documents |
| `gradle.properties`, `.env.example` | provider configuration/secrets (currently no MoMo keys) |

## 7. Functional Requirements
- Funding is `PENDING` until a provider-verified success; escrow is locked exactly once.
- Release requires an eligible terminal job state and a verified recipient; no duplicate payout.
- Refund path exists for cancellation and dispute outcomes and actually moves money; marking escrow `REFUNDED` without a provider refund is a misstatement.
- Cashout amount cannot exceed ledger-derived available balance and cannot be duplicated.
- Every money movement has an immutable audit record with provider reference.
- Fees/commission are computed server-side from configuration, not client-side (`laborTotal * 0.10`) and not merely stored on the job.

## 8. Security Requirements
- Client-supplied amount, currency, phone, job binding and status are hints only; server-held values win.
- Provider credentials live only in the backend secret store.
- Webhook payloads cannot flip escrow (verified re-query only).
- Payout destination changes require re-authentication and fraud checks (velocity, allow-list, name match).
- Money documents must reject client writes; `momo_transactions` currently allows authenticated create — assess impact.
- Segregation of duties: no single client-side actor can both approve and release.

## 9. Testing Requirements
- Duplicate callback, replayed terminal callback, forged amount/reference, provider timeout and malformed response.
- Duplicate payout, ineligible job, mismatched currency and negative/zero/NaN amounts.
- Ledger property tests (sum invariants, no negative balance, reserve/release atomicity).
- Reconciliation test against synthetic provider settlement file.
- End-to-end sandbox test with real provider sandbox credentials (labelled if unavailable).

## 10. Expected Evidence
- Money-flow diagrams with trust boundaries and state transitions.
- Table: flow → authorization → idempotency key → ledger entries → failure handling → test.
- Test output for all money cases; any unexecuted case marked UNVERIFIED.
- Reconciliation procedure and sample output.
- Config matrix for sandbox vs production (base URL, keys, shortcode, currency).

## 11. Passing Conditions
- PASS requires: server-authoritative money movement only, idempotent funding/release/refund, complete ledger and reconciliation, destination-change controls, and passing money tests.
- FAIL now: payout release is not wired from the app, the device fake payout reports `DISBURSED`, the wallet ledger is device-local, dispute refunds are queued without execution while escrow is marked `REFUNDED`, and no server-side fee or reconciliation path is evidenced.
