# REVIEW-08 — Payments & Escrow Review

| | |
|---|---|
| **Reviewer** | R08 — Payments & Escrow Reviewer (P0, Gate G4) |
| **Wave** | 1 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** — Gate G4 not met |

## 1. Scope and evidence

Traced every money mutation from UI to Room to Firestore to callable. Verified the hardened paths
(cashout, collection status, payout authorization) and separated them from the unwired ones. Executed
the Node payment test suite (111 tests pass). No provider sandbox calls were made.

## 2. Money lifecycle as implemented

| Stage | Implemented | Evidence |
|---|---|---|
| Quote → totals | Client-side `platformFee = laborTotal * 0.10` (`repository:565/660`); `platformFee` also hardcoded in demo data (`1898+`) | Fee is not server-authoritative |
| Collection | Callable, trusted-doc amount, `MOMO_CURRENCY = "UGX"`, sandbox | Sound (static) |
| Escrow lock | `FUNDS_LOCKED_IN_ESCROW` set client-side after collection | Writable by client |
| Release to worker | `releaseEscrowToWorker` callable exists but **the client never calls it** (only comments at `repository:2569`) | Job cannot be paid out from the app |
| Payout status | `getMoMoPayoutStatus` callable | Not surfaced in the payout flow |
| Refund | **No refund function exists** in the repository (grep `fun .*[Rr]efund` = 0) | Disputes/cancellations cannot return money |
| Worker cashout | Local ledger only; `MoMoPaymentService.disburseEscrowPayoutToWorker` fabricates `DISBURSED` (`:79–96`) | No real disbursement from cashout |

## 3. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R08-F1 | Critical | `disburseEscrowPayoutToWorker` returns a synthetic `payoutId` and `status = "DISBURSED"` with no network call. | A worker can be shown a completed payout that never happened. |
| R08-F2 | Critical | No call site of `releaseEscrowToWorker`; escrow can never leave `FUNDS_LOCKED_IN_ESCROW` from the client. | Every completed job leaves worker funds locked forever; the core value proposition fails. |
| R08-F3 | Critical | No refund implementation at all. Cancellation/dispute resolution leaves funds locked. | Residents cannot recover money; the privacy/terms copy promising refunds is false. |
| R08-F4 | High | `platformFee` and `grandTotal` computed on the client and persisted; server never recomputes. | A modified client can set a zero fee or a lower total before collection. |
| R08-F5 | High | Escrow status strings are written by the client; rules do not pin allowed status transitions per actor. | Status can be advanced without a provider confirmation. |
| R08-F6 | High | The cashout ledger is local-only (Room), derived from local rows; no server call. | Reinstall or DB manipulation changes payable balance; no settlement record. |
| R08-F7 | Medium | Refunds/escrow use `Double` throughout (R04-F2). | Rounding errors in totals and refunds. |
| R08-F8 | Medium | Card and Airtel tabs exist in the payment sheet (`EscrowAndProofSheet.kt:972`) with no processor. | Users can select non-functional channels. |
| R08-F9 | Medium | No reconciliation job, no settlement report, no daily totals, no alert on stuck escrow. | Money can be stuck or double-counted without detection. |
| R08-F10 | Info | `createMoMoPayout`, `getMoMoCollectionStatus`, `requestWorkerCashout` authorization and idempotency are genuinely sound (ledger-derived balance, in-transaction recheck). | Positive — do not re-flag. |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Server-authoritative amounts and fees | FAIL |
| Collection integrity | PASS (static) |
| Release path wired end-to-end | FAIL |
| Refund path exists | FAIL |
| Payout is real | FAIL |
| Ledger auditable/reconcilable | FAIL |
| Idempotency on retry | PASS (cashout/webhook, static + tests) |

## 5. What could not be verified

Sandbox collection against MTN, webhook delivery, and actual disbursement settlement. **UNVERIFIED**.

## 6. Passing conditions

1. Client calls `releaseEscrowToWorker` and reflects the server outcome; no local `DISBURSED`.
2. Server computes fee and total from catalogue + quote; client cannot alter them.
3. Refund callable implemented, tested, and wired to cancellation/dispute.
4. Escrow state machine enforced by callable transitions, not client writes.
5. Cashout creates a server settlement record; ledger reconciled daily with alerting.
6. Money as integer minor units.

## 7. Residual risk

Even with perfect security, the payment lifecycle is incomplete: money can go in and never come out
through the app. That is a product-stopping defect and the reason G4 fails.

## Recommended remediation (not implemented)

1. Call `releaseEscrowToWorker` from the completion path and delete the fabricated `DISBURSED` result.
2. Compute fee/total server-side and enforce escrow transitions in callables.
3. Implement and wire a refund path for cancellation and dispute outcomes.
4. Create server settlement records and a daily reconciliation report.

## 8. Cross-references

F-006, F-021; R03-F6, R04-F2/F3, R05-F4, R09-F4, R10-F4.
