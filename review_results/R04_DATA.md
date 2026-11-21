# R04 — Data & Persistence Integrity Engineer

| Field | Value |
|---|---|
| Reviewer | R04 — Data / Persistence (P1) |
| Assignment | `reviewers/DATA_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Room schema, migrations, money storage, transactions, dual-write |
| Verdict | **FAIL** |
| Issues | 5 (2 HIGH, 3 MEDIUM) |

## Areas examined
18 entities, schema v27, 16 migrations, DAO queries, transaction boundaries, Room↔Firestore consistency,
message persistence.

## Source files inspected
`data/db/AppDatabase.kt`, `Entities.kt`, `Daos.kt`, `Converters.kt`; repository transaction sites
(`SkillsExchangeRepository.kt:2827,2906`); `data/remote/FirebaseService.kt` chat listener.

## Features examined
Job/quote/escrow rows, ledger and cashout reservations, chat messages, profiles, KYC, guarantors,
notifications, ratings, reports.

## Tests performed
- Enumerated entities, migrations, `exportSchema`, DAO queries and transaction usage.
- Checked test sources for `MigrationTestHelper` (none).
- No database upgrade, instrumentation or emulator run.

## Verified working functionality
- Cashout reservation is genuine: balance derived from ledger, idempotency key, in-transaction recheck
  at two repository sites.
- Foreign-key/indices exist on the main lookup paths (`jobId`, `userId`, `jobId+timestamp` for chat).

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R04-01 | HIGH | Schema is unexported and no migration is tested. | Room | `AppDatabase.kt` `version = 27`, `exportSchema = false`; 16 `MIGRATION_*`; 0 `MigrationTestHelper` tests | Exported schema + per-version migration tests | No schema history; upgrades unverified | Export schema, commit JSON, add migration tests | Migration test for each version in CI |
| R04-02 | HIGH | Currency is stored as binary floating point. | Entities | 33 `: Double` money fields; `platformFee = 12.0` | Integer minor units or decimal | Rounding drift and unreliable sums | Migrate to `Long` minor units with a tested migration | Sum/rounding tests + migration verification |
| R04-03 | MEDIUM | Destructive fallback and dual-write have no conflict protocol. | Database/repository | `fallbackToDestructiveMigration` not debug-gated; Room and Firestore written independently | Safe migrations; deterministic reconciliation | Silent wipe risk; divergence undetected | Remove release fallback; define sync/conflict rules | Upgrade test from v26; conflict tests |
| R04-04 | MEDIUM | Several list reads are unbounded or unindexed on growth paths. | DAOs | `SELECT *` list queries without `LIMIT`; message history unpaginated | Bounded queries and indices | Memory/scan growth with data volume | Add paging and indices, verify with query plans | Load test with volume dataset |
| R04-05 | MEDIUM | Chat is stored twice with only `timestamp` for ordering. | Room + Firestore | Local rows and Firestore subcollection; snapshot listener maps by timestamp only | Single source of truth or dedup key | Duplicate/lost messages on reconnect and clock skew | Add server sequence/ID dedup and reconciliation | Reconnect/ordering tests |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R04-01, R04-02, R04-03, R04-04.
- **Not implemented:** none.
- **Partially implemented:** R04-05.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
- Schema export, migration tests, reconciliation job, retention/purge, conflict resolution.

## Security concerns
Device-local ledger is authoritative for cashout availability (R08-06); a wiped or modified DB changes
what the device believes is payable.

## Performance concerns
Unbounded list reads and duplicate chat storage increase I/O and memory (R13-06).

## Recommendations
1. Export schema and add migration tests before the next schema bump.
2. Move money to integer minor units with a migration plan.
3. Remove destructive fallback from release builds.
4. Add paging and a single chat ordering/dedup key.

## Limitations / unable to verify
Upgrade paths from each historical version and query plans were not executed (**unable to verify**).

## Cross-references
R08 (money), R09 (retention), R11 (tests), R13 (reads), MR-046/MR-047/MR-048/MR-049/MR-050.
