# R04 — Data & Persistence Integrity Engineer

| Field | Value |
|---|---|
| Reviewer | R04 — Data & Persistence (P1) |
| Assignment | `reviewers/DATA_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no schema, DAO, migration or test changed |
| Runtime/migration execution | **UNVERIFIED** — no JDK/Android SDK; no Robolectric run |
| Verdict | **FAIL** |

## 1. Schema inventory

`AppDatabase.kt:31` declares `version = 27`, with 16 `Migration` objects (11→27), so the claimed
migration chain exists. Entities: 18 `@Entity` classes in `Entities.kt` (826 lines). No exported JSON
schema exists: `app/schemas/` is absent and `exportSchema = false` (`AppDatabase.kt:32`).

## 2. Migration safety

| Area | Observation |
|---|---|
| Destructive fallback | none found in the builder (`fallbackToDestructiveMigration` count = 0) — good |
| Migration tests | only `GuarantorVerificationPersistenceTest.kt` asserts the 18→19 shape/version; no `MigrationTestHelper` use anywhere, so 15 of 16 migrations are untested |
| Data preservation | each migration inspected only at declaration level; full SQL review is UNVERIFIED without execution |

R04's passing condition requires every retained migration tested; it is not met.

## 3. Monetary representation

- 33 `Double` fields in `Entities.kt` (budgets, fees, ledger amounts, wallet balances).
- `WorkerCashoutSecurityCore.computeAvailableBalance` sums `Double` ledgers and compares with `Double`.
- `pricing.ts` (server) rounds fees to two decimals, but the Android ledger does not use minor units
  or `BigDecimal`, so float drift is possible across repeated credits/debits. No documented single
  money representation was found.

## 4. Business logic in schema defaults

`Entities.kt:259` defines `var doorstepPin: String = "4829"`. A default completion/verification PIN is
a business decision encoded in the schema; R04's passing condition explicitly forbids it. Any job row
created without an explicit PIN accepts `4829`.

## 5. DAO scoping and indices

- Identity-scoped DAOs exist (`getCurrentUserByUid`, `getWithdrawalsForWorker`, job queries by
  `postedByUid`), and the ViewModel no longer uses the legacy "newest row" query.
- `getAllJobs()`/`getAllProviders()` are unscoped and are used for local display; they return only
  device-local rows, so this is not cross-account exposure, but the repository exposes them without a
  viewer filter.
- No `@Index` review was completed; query plans are UNVERIFIED for lack of SQLite execution.

## 6. Wallet ledger and withdrawals

`requestWorkerCashoutInternal` (`Repository.kt:2687`) resolves the actor from FirebaseAuth, checks an
idempotency key, computes available balance from ledger entries, validates against it, and records the
PENDING withdrawal plus RESERVE entry in one transaction. Transitions are centralised in
`WorkerCashoutSecurityCore.evaluateWithdrawalTransition`. No negative-balance path was found
statically. Execution of the transaction is UNVERIFIED.

## 7. Offline queue and dual writes

- `PendingActionEntity` has `retryCount` (`Entities.kt:598`); the retry cap is enforced in repository
  code, not in the schema.
- Room↔Firestore mirroring is best-effort in several paths (e.g. SOS mirror explicitly ignores
  failure), with no reconciliation job. Divergence is possible and untested offline.

## 8. Deletion and backup

- `allowBackup="false"` with explicit `dataExtractionRules`/`fullBackupContent` now
  (`AndroidManifest.xml:18–20`), so the earlier empty-backup finding is addressed.
- `clearAllUserData` / account deletion cover the user-data tables inspected; guarantees under a
  mid-delete crash are UNVERIFIED.

## 9. Tests

41 unit test files exist, 17 under `data/`, covering authorship, cashout policy, SOS authorization,
quote/rating authorization and persistence. None executes migrations; none runs here (no toolchain).

## 10. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R04-P2-01 | HIGH | `exportSchema = false`; no schema JSON committed (explicit FAIL condition) | `AppDatabase.kt:32`; no `app/schemas` |
| R04-P2-02 | HIGH | 15 of 16 migrations have no migration test | only `GuarantorVerificationPersistenceTest.kt` |
| R04-P2-03 | HIGH | Default doorstep PIN `"4829"` in the entity | `Entities.kt:259` (explicit FAIL condition) |
| R04-P2-04 | MEDIUM | Money is `Double` end-to-end on the client, no minor-unit representation | 33 `Double` fields; `WorkerCashoutSecurityCore.kt:233` |
| R04-P2-05 | MEDIUM | Room↔Firestore divergence has no reconciliation; SOS mirror is best-effort | repository mirror calls |
| R04-P2-06 | LOW | Unscoped `getAllJobs`/`getAllProviders` exposed without viewer filter | `Daos.kt`; repository flows |

## 11. Verdict

**FAIL.** Two explicit fail conditions are present (`exportSchema = false`, default PIN), migration
coverage is one of sixteen, and monetary values are floating point. Cashout logic is materially
stronger than the baseline and no destructive fallback exists. Migration execution remains
**UNVERIFIED**.
