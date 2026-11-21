# VERIFICATION_04 — R04: Data & Persistence Integrity Engineer

**Independent verification** of the R04 assignment against `028cf8e` (`main`), executed on
`arena/548c1374-skills-exchange`. **Date:** 2026-11-20.
**Verdict: PARTIALLY VERIFIED — not approved.** Five of six passing conditions are met and were re-tested;
**one fails, and it is the finding the round claimed closed.** The reviewer's instruction file is
**retained** (Phase 5), the sequence stops here, and R05 is not opened.

Sources read first: `reviewers/DATA_REVIEWER.md` (77 lines), `review_results/prompt2/REVIEW_04.md`
(95 lines), the one-sentence completion declaration, `REVIEWER_IMPLEMENTATION_STATE.md` §R04.

## 1. Passing conditions, one by one

| # | Condition (quoted from the assignment) | Verdict | Evidence |
|---|---|---|---|
| 1 | "schema exported" | **PASS** | `AppDatabase.kt:32` → `exportSchema = true`; `app/schemas/com.example.data.db.AppDatabase/` holds `27.json`, `28.json`, `29.json`. Versions 11–26 have no JSON because the flag was off then — retroactive export is impossible, and the chain test covers them by execution instead (see §2). |
| 2 | "every retained migration tested" | **PASS** | `DatabaseMigrationChainTest` builds a real **version-11 SQLite file** from `src/test/resources/migration/v11_baseline.sql`, seeds two rows, and opens it through production `AppDatabase` so Room runs 11 → 29 and validates the result against the entity contract. Five tests: consecutive-pair coverage (`11..29`), data preserved, migrated DB accepts rows and viewer-scoped queries, 27→28 money conversion, 28→29 PIN clear. This executes in CI (the Android suite is green). |
| 3 | "no data-loss migration" | **PASS** | The chain test's data assertions cover the whole path; the round also fixed a real defect it found — `MIGRATION_13_14` recreating `job_posts` without `photos`/`scheduledAt`. |
| 4 | "atomic and idempotent cashout verified" | **PASS** | `requestWorkerCashoutInternal` wraps the PENDING withdrawal and its RESERVE ledger entry in one `db.runInTransaction`, keyed by an idempotency key; transitions centralised in `WorkerCashoutSecurityCore`; **27** tests in `WorkerCashoutSecurityTest`, including fail-closed identity paths. |
| 5 | "no default PIN in schema" | **PASS** | `Entities.kt:266` → `var doorstepPin: String = ""`; `MIGRATION_28_29` clears the legacy `'4829'` rows; `DoorstepPinAndScopeTest` and the chain test both pin it. |
| 6 | "**a documented single representation for money/time**" | **FAIL** | See §3. The tree documents a single representation and then stores two. |

## 2. Why the chain test counts as "tested"

The assignment asked for `MigrationTestHelper`-style per-hop tests. The implementation instead executes
the entire chain from a real v11 database and lets Room validate the migrated schema, which fails the
build on a missing column, index or foreign key. That is stronger than five isolated hop fixtures for the
data-loss question, so the condition is satisfied by a different and better means. A per-hop fixture
(value-level assertions on every version) is still absent — noted, not blocking.

## 3. The failure

**`Money.kt` states one representation; the schema stores two.** `app/src/main/java/com/example/util/Money.kt`
opens with "R04-P2-04: the one money representation" and states the rule:

> storage and every balance computation use `toMinorUnits` results and `Long` arithmetic;
> `Double` only appears at the edges: user input … and display.

The exported schema for **version 29** — read from `app/schemas/com.example.data.db.AppDatabase/29.json`,
not from prose — contains:

| Table | Money columns | Affinity |
|---|---|---|
| `wallet_ledger` | `amountMinor` | **INTEGER** ✓ |
| `wallet_withdrawals` | `amountMinor` | **INTEGER** ✓ |
| `job_posts` | `budget`, `materialsCost`, `safetyFee`, `laborRate`, `laborTotal`, `materialsSubtotal`, `platformFee`, `grandTotal`, `emergencyAddonAmount`, `materialAdvanceAmount`, `calloutFee`, `recurringNegotiatedRate` | **REAL** |
| `job_quotes` | `proposedRate`, `totalQuote` | **REAL** |
| `disputes` | `resolutionAmount` | **REAL** |
| `service_providers` | `hourlyRate`, **`walletBalance`** | **REAL** |
| `user_profiles` | **`walletBalance`** | **REAL** |

**18 money columns are REAL against 2 INTEGER.** Two of them are *balances*, the exact category the
document says is stored in minor units. `user_profiles.walletBalance` even carries a default of `185.0`,
and `Daos.kt:358` writes it with arithmetic.

This is not a cosmetic doc mismatch. The requirement was "a documented single representation", the
implementation wrote a document asserting one, and the schema contradicts it. That is the "claim exceeding
evidence" pattern this programme exists to remove, in its subtlest form — the claim is technically a
design intent, but nothing enforces it, so the next migration that touches `grandTotal` will treat a
`Double` as authoritative.

Supporting gaps under the same failure:

- **No decision record.** `DECISIONS.md` has entries for R01 and R15 and none for the money
  representation, even though the round made a storage decision (27→28) that every future writer must
  honour.
- **No guard.** Nothing fails the build if another REAL money column is added, or if balance code starts
  summing `Double` again. Every other invariant in this programme has a script; this one has a comment.
- Time is consistently `Long` epoch millis (checked across `Entities.kt`), but only money has a written
  rule, so "money/time" is half-documented.

## 4. Findings re-test

| Finding | Status | Evidence |
|---|---|---|
| R04-P2-01 **HIGH** `exportSchema = false` | **CLOSED** | `exportSchema = true`; schemas 27/28/29 committed; the chain test reads the committed JSON. |
| R04-P2-02 **HIGH** 15 of 16 migrations untested | **CLOSED** | Whole chain 11→29 executed from a real v11 database, with data assertions. |
| R04-P2-03 **HIGH** default PIN `"4829"` | **CLOSED** | Default is `""`; `MIGRATION_28_29` clears legacy rows; two tests pin it. |
| R04-P2-04 **MEDIUM** money is `Double`, no minor-unit representation | **NOT CLOSED** | Ledger and withdrawals converted; **18 money columns remain REAL**, including two balances (§3). The `Money.kt` rule is asserted, not enforced. |
| R04-P2-05 **MEDIUM** Room↔Firestore divergence with no reconciliation | **CLOSED** | `reconcilePendingMirrors` returns a counted report; the SOS mirror now has an unaccepted-incident query (`Daos.kt:612`). |
| R04-P2-06 **LOW** unscoped `getAllJobs`/`getAllProviders` | **CLOSED** | `getJobsVisibleTo(viewerUid)` is the path; `getAllJobs()` is `@Deprecated` with a written reason, and the build prints that warning on every use. |

## 5. Failed tests

**None.** No test failed: the Android suite is green in CI (494 tests). The failure is a *condition*
verified by reading the exported schema and the document that claims otherwise — the case where "all tests
pass" and the requirement is still unmet, which is precisely why this phase does not accept a green run as
approval.

## 6. Corrections required before R04 can be re-verified

| ID | Severity | Correction | Files |
|---|---|---|---|
| **VC-05** | **blocking R04** | Make the representation true, one way or the other. **(a) Complete it:** migration 30 converts the 18 REAL money columns to minor-unit INTEGERs (STARTING with `job_posts.grandTotal`, `platformFee`, `totalQuote` and `disputes.resolutionAmount`, which feed money arithmetic), deriving display values at the edge. **(b) Or narrow it honestly:** rename the two `walletBalance` columns to display mirrors (e.g. `walletBalanceDisplay`), state in `Money.kt` exactly which fields are legacy REAL and that they are never summed or compared for authorization, and list the migration that will convert them. Either way, **add `DECISIONS.md` D-2026-11-20-xx recording the choice**. | `AppDatabase.kt`, `Entities.kt`, `Money.kt`, `DECISIONS.md`, `app/schemas/` |
| **VC-06** | HIGH | Add a guard (`scripts/check_money_representation.mjs`, with `--selftest`) that parses the newest exported schema and fails when a column whose name matches a money pattern has REAL affinity outside an explicit, reasoned allowlist — so the rule survives the next contributor. | `scripts/`, CI documentation job |
| **VC-07** | LOW | `DatabaseMigrationChainTest`'s docstring says Room runs "11 → 28" while the chain and its assertion cover 11 → 29; and `app/schemas` starts at 27 with no note saying why earlier versions cannot be exported. | `DatabaseMigrationChainTest.kt`, `app/schemas/README` or the report |

Re-verification will re-run exactly this report's checks: schema JSON inspection, the condition table, and
the new guard's selftest.

## 7. Approval

**NOT APPROVED.** Condition 6 is unmet and R04-P2-04 is open, so this reviewer stays at the front of the
queue. Per Phase 5:

- instruction file `reviewers/DATA_REVIEWER.md` — **retained, not deleted**;
- `reviewer_completions/REVIEWER_04_DONE.md` — preserved as historical evidence (it remains a single
  sentence, "I finished the work for this reviewer.");
- verification stops here; **R05 is not opened.**

What is genuinely good and should not be lost: the migration chain test, the exported schemas, the PIN
removal with its data migration, the transactional cashout, and the mirror reconciliation are all real and
executed. Four of the six findings are closed outright; the round is one honest column-type decision away
from passing.
