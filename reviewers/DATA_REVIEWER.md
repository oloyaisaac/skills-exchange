# R04 — Data & Persistence Integrity Engineer

## 1. Reviewer Number
R04 (PRD story `RVW-004`, Priority P1). Depends on R01 build gate.

## 2. Professional Title
Senior Data / Persistence Engineer, Android Room + SQLite and Firestore document modelling, financial ledger and migration specialist.

## 3. Required Expertise
- Room schema design, `@Entity`/`@Dao`/`@TypeConverter`, indices, foreign keys, `exportSchema`, migration testing.
- SQLite migration correctness across 17 versions (11 → 27) and destructive-change risk.
- Offline-first dual-write patterns, conflict resolution, tombstones, idempotent replay.
- Ledger invariants (double-entry or derived-balance), monetary representation and rounding.
- Firestore denormalisation, document-size limits, query/field consistency and backfill strategy.

## 4. Responsibilities
1. Validate Room v27 schema, all 18 entities and 16 migrations (11→27) for data-loss risk.
2. Verify DAO queries enforce authorization-relevant predicates (owner/worker scoping) and use indices.
3. Audit Room↔Firestore mirroring for divergence, ordering and failure handling.
4. Audit the wallet ledger and withdrawal state machine for balance invariants and replay safety.
5. Verify `pending_actions` offline queue semantics (retry caps, ordering, duplication).
6. Check monetary fields and date/time fields for a single, documented representation.

## 5. Exact Features to Examine
- User profile, provider profile, jobs, quotes, chat, notifications.
- Worker KYC, guarantor records and guarantor submission.
- Disputes, ratings, reports, SOS incidents, job-status audit trail.
- Wallet ledger and wallet withdrawals (cashout).
- Pending-action offline queue and data backup export.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/main/java/com/example/data/db/AppDatabase.kt` | `version = 27`, `exportSchema = false`, 16 `Migration` objects (11→27) |
| `app/src/main/java/com/example/data/db/Entities.kt` | 18 entities; `doorstepPin = "4829"` default at line 259; `Double` money fields |
| `app/src/main/java/com/example/data/db/Daos.kt` | all queries; ownership scoping |
| `app/src/main/java/com/example/data/db/TypeConverters.kt` | type serialisation |
| `app/src/main/java/com/example/data/db/ServiceTaxonomy.kt` | 125-service catalog |
| `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` | transactions, cashout, disputes, status audit |
| `app/src/main/java/com/example/data/repository/WorkerCashoutSecurityCore.kt` | pure-JVM ledger and transition rules |
| `app/src/main/java/com/example/data/local/SessionManager.kt` | DataStore session persistence |
| `app/src/test/java/com/example/data/GuarantorVerificationPersistenceTest.kt` | existing migration/version assertion |
| `app/src/test/java/com/example/data/WorkerCashoutSecurityTest.kt` | ledger/withdrawal invariants |
| `app/src/test/java/com/example/data/PendingActionPreservationStep0Test.kt` | queue preservation |

Cross-check `firestore.rules` with R07 for field-level write constraints; R04 owns data shape and integrity.

## 7. Functional Requirements
- Migrations must preserve user data for every supported installed version.
- A cashout must atomically reserve funds exactly once and never produce a negative available balance.
- Job status changes must write an audit row with actor and timestamp.
- Offline actions must survive restart and not duplicate server effects.
- Entity defaults must not encode business decisions (e.g. a default doorstep PIN).

## 8. Security Requirements
- Sensitive identifiers (NIN, MoMo numbers, pins, tokens) must not be persisted unencrypted without a documented decision.
- DAO methods must not expose another user's rows through unscoped queries used by UI flows.
- Room data is protected only by device sandboxing; `allowBackup="true"` with empty backup rules is a privacy finding (hand to R09).
- Deletion paths (`clearAllUserData`, `permanentlyDeleteUserAccountAndData`) must remove every table that contains user data.

## 9. Testing Requirements
- Migration tests from every retained version (at minimum 21→27) with representative data.
- Export schemas and diff them per version; `exportSchema = false` is a FAIL condition.
- Transaction/rollback tests for cashout and escrow transitions.
- Concurrency tests for duplicate withdrawals and parallel job updates.
- Firestore/Room divergence tests with simulated offline windows.

## 10. Expected Evidence
- Entity/relationship diagram and full schema inventory.
- Migration-by-migration table: change, data risk, test coverage.
- Ledger invariant proof or failing counter-example.
- Query plan/index list for hot DAO methods.
- Explicit list of untested migrations labelled UNVERIFIED.

## 11. Passing Conditions
- PASS requires: schema exported, every retained migration tested, no data-loss migration, atomic and idempotent cashout verified, no default PIN in schema, and a documented single representation for money/time.
- FAIL if `exportSchema = false` remains without an accepted compensating control, or if any unguarded destructive migration exists.
