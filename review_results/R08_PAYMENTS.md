# R08 — Payments, Escrow & Financial Controls Auditor

| Field | Value |
|---|---|
| Reviewer | R08 — Payments & Escrow (P0, Gate G4) |
| Assignment | `reviewers/PAYMENTS_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Collection, escrow, release, refund, payout, cashout, ledger, reconciliation |
| Verdict | **FAIL** — G4 not met |
| Issues | 9 (3 CRITICAL, 3 HIGH, 3 MEDIUM) |

## Areas examined
Every money mutation from UI to Room to Firestore to callable; fee computation; escrow state machine;
payout and cashout paths; refund and dispute outcomes; provider settlement and reconciliation.

## Source files inspected
`SkillsExchangeRepository.kt` (fee `565/660`, escrow `2569`, cashout `2757–2906`), `MoMoPaymentService.kt`,
`FirebaseService.kt`, `Entities.kt`, `functions/src/index.ts` (`createMoMoCollection`, `releaseEscrowToWorker`,
`createMoMoPayout`, `createMoMoMaterialAdvance`, `getMoMoPayoutStatus`), `momo*.ts`, `WorkerCashoutSecurityCore.kt`.

## Features examined
Quote totals, collection initiation/status, escrow lock, completion/release, cancellation/refund,
worker cashout, material advance, payout status, dispute resolution.

## Tests performed
- Traced every call site of release/payout/refund functions in the Android source (0 for release/refund).
- Executed Node payment tests via `npm test` → 111/111 pass.
- Inspected escrow status constants and write paths.
- No MoMo sandbox, no device, no settlement files.

## Verified working functionality
- Collection is server-authoritative: amount from stored job, constant `UGX`, fail-closed config.
- `requestWorkerCashout` is genuinely hardened: ledger-derived balance, idempotency, in-transaction recheck.
- Payout/collection callables enforce `context.auth` and `postedByUid` ownership.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R08-01 | CRITICAL | Escrow release is never invoked from the client. | Completion flow | 0 call sites of `releaseEscrowToWorker` in `app/src/main`; only comments (`repository:2569`) | Completed job releases funds | Worker never paid; escrow locked forever | Wire completion to the callable and render its outcome | Sandbox E2E: complete → release → payout status |
| R08-02 | CRITICAL | No refund implementation exists. | Cancellation/dispute | 0 refund functions in repository; no refund callable | Funds return on cancellation/dispute | Money can only go in | Implement refund callable and wire it | Sandbox refund E2E + ledger entry |
| R08-03 | CRITICAL | Device payout fabricates success. | `MoMoPaymentService` | `disburseEscrowPayoutToWorker` returns `DISBURSED` with a synthetic id (`:79–96`) | Provider-confirmed disbursement | UI shows paid when nothing was sent | Delete stub; use payout callable + status polling | Provider reference in response; reconciliation |
| R08-04 | HIGH | Fee and grand total are computed on the client. | Quote/pricing | `platformFee = laborTotal * 0.10`, totals persisted (`repository:565/660`) | Server recomputes from catalogue/quote | Client can alter fee/total | Compute server-side; reject mismatches | Tampered-client test |
| R08-05 | HIGH | Escrow status transitions are client-writable. | Firestore rules/repository | Status strings written by client; rules do not pin per-actor transitions | Callable-only transitions | Escrow state can advance without provider confirmation | Enforce state machine in callables | State-transition rules tests |
| R08-06 | HIGH | Wallet ledger is device-only with no settlement record. | Room/cashout | Ledger and balance in Room; cashout has no server disbursement record | Server ledger + settlement | Reinstall/DB edit changes payable balance | Persist server ledger/settlement | Server balance matches device after reinstall |
| R08-07 | MEDIUM | No reconciliation or stuck-escrow alerting. | Operations | No reconciliation job, settlement report or alert | Daily reconciliation with alerts | Stuck/double-counted money undetected | Add reconciliation + alerting | Recon report matches provider statement |
| R08-08 | MEDIUM | Airtel and card are offered as payment channels without integration. | Payment UI | `EscrowAndProofSheet.kt:972`; no provider code | Only functional channels offered | User can select a dead channel | Integrate or remove | Channel E2E |
| R08-09 | MEDIUM | Money is `Double` end-to-end. | Entities/UI | 33 `: Double` fields; `toInt()` truncation in payout stub | Integer minor units | Rounding/truncation drift | Migrate to minor units | Arithmetic + migration tests |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R08-03, R08-04, R08-05, R08-06, R08-09.
- **Not implemented:** R08-01, R08-02, R08-07, R08-08.
- **Partially implemented:** none.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
Release wiring, refunds, real disbursement, server ledger, settlement, reconciliation, production config.

## Security concerns
Amount tampering is prevented on collection (trusted doc), but fee/total and escrow transitions are
client-controlled. Detailed rules issues: R07.

## Performance concerns
Polling collection status (R13) adds latency and battery cost on the money path.

## Recommendations
1. Wire release and delete the fabricated payout.
2. Implement refund and server settlement/reconciliation.
3. Server-authorize fee/total and escrow transitions.
4. Move money to integer minor units.

## Limitations / unable to verify
No MTN sandbox, webhook delivery or settlement statement. Everything provider-facing is
**unable to verify**; only static paths and pure unit tests were executed.

## Cross-references
R03, R04, R05, R07, R13, MR-026/MR-027/MR-028/MR-029/MR-030/MR-031/MR-032/MR-033/MR-047.
