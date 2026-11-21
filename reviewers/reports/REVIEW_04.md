# REVIEW-04 — Data / Persistence Review

| | |
|---|---|
| **Reviewer** | R04 — Data / Persistence Reviewer (P1) |
| **Wave** | 2 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** |

## 1. Scope and evidence

`data/db/AppDatabase.kt`, `Entities.kt`, `Daos.kt`, all 16 migration objects, the Firestore models in
`data/remote/`, and the repository's transaction boundaries were read. Room schema export and test
coverage were checked by configuration, not assumption.

## 2. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R04-F1 | High | `AppDatabase` version **27**, 16 `Migration` objects, `exportSchema = false`; `app/schemas/` does not exist and no `MigrationTestHelper` test exists (0 of 41 test files). | Schema history is unauditable. A bad `ALTER TABLE` on a live install corrupts the ledger with no rollback path and no before/after proof. |
| R04-F2 | High | 33 money fields are `Double` (e.g. `amountUgx`, `platformFee`, `escrowBalance`) and `platformFee = 12.0` is computed client-side. | Binary floating point is used for currency; rounding drift is possible and SQL `SUM` comparisons become unreliable. |
| R04-F3 | High | The local Room ledger is authoritative for cashout availability, while Firestore holds escrow state; there is no reconciliation job or conflict rule. `runInTransaction` is used at only two sites (repository `2827`, `2906`). | Device wipe/reinstall loses the ledger that authorises payouts; Firestore and Room can disagree with no detection. |
| R04-F4 | Medium | Destructive migration behaviour must be inferred; `fallbackToDestructiveMigration` is not gated to debug and no `onDestructiveMigration` handler exists. | A missed migration silently deletes jobs, messages and ledger rows. |
| R04-F5 | Medium | 18 entities but only a handful have explicit indices; queries on `messages.jobId`, `notifications.userId`, `ratings.jobId` were checked and are indexed, room for others. | Hot-path scans as data grows; no `EXPLAIN` evidence. |
| R04-F6 | Medium | Messages are stored twice (Room + Firestore subcollection) and reconciled only by a snapshot listener; no ordering/dedup key beyond `timestamp`. | Duplicate or lost messages on reconnect; clock skew breaks ordering. |
| R04-F7 | Low | `doorstepPin = "4829"` and other defaults live in entity constructors. | Unsafe defaults reach disk whenever a caller omits the field. |
| R04-F8 | Info | `exportSchema=false` is the only reason no schema JSON drift is visible in CI. Cleaned up, this becomes the migration gate. | — |

## 3. Requirement checks

| Requirement | Result |
|---|---|
| Schema versioned and exportable | FAIL |
| Migrations tested | FAIL |
| Money stored losslessly | FAIL |
| Transactional money mutation | PARTIAL (cashout only) |
| Client/server reconciliation | FAIL |
| Deletion/retention | FAIL (see R09) |

## 4. What could not be verified

Actual on-device upgrade paths from each of the 16 historical versions, database size growth, and
query plans. All are **UNVERIFIED**.

## 5. Passing conditions

1. `exportSchema = true` with committed `app/schemas/` JSON and a `MigrationTestHelper` test per
   version, run in CI.
2. All money as `Long` minor units (or `BigDecimal` at the boundary), with a migration plan.
3. A reconciliation job compares Room ledger totals to Firestore escrow state and alerts on drift.
4. Destructive fallback removed for release builds.
5. Retention/purge policy implemented and tested.

## 6. Residual risk

A financial ledger backed by `Double`, with no tested migrations and no server reconciliation, is the
highest-likelihood data-integrity failure in the product — independent of any UI defect.

## Recommended remediation (not implemented)

1. Set `exportSchema = true`, commit `app/schemas/`, and add a migration test per version.
2. Convert money to integer minor units with a tested migration.
3. Add Room↔Firestore reconciliation with drift alerting.
4. Remove destructive fallback from release builds.

## 7. Cross-references

F-006, F-007; R08 (money), R09 (retention), R11-F2 (no migration tests), R02-F4 (offline).
