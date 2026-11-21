# R15 — Documentation & Requirements Traceability Auditor

| Field | Value |
|---|---|
| Reviewer | R15 — Documentation & Traceability (P1) |
| Assignment | `reviewers/DOCUMENTATION_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no product documentation rewritten (cycle artifacts added only) |
| Verdict | **FAIL** |

## 1. Automated documentation checks

| Check | Result |
|---|---|
| Relative link check | **PASS** — 117 links, no host-absolute links |
| Document map | **PASS** — 21 documents with role/owner |
| Traceability | **PASS mechanically** — 24 requirement IDs, 4 without automated tests |
| README claims | **PASS** — no Airtel integration or web-dashboard claim; pre-production caveat present |
| Query limits | **PASS** — 50 list queries bounded |

Mechanical checks pass because they were repaired in the remediation programme (REM-069/070/071/072).

## 2. Requirements traceability coverage

- `prd.json` contains **185 stories**: 112 `completed` (inherited baseline), 11 `IN_PROGRESS`, 16
  `VERIFICATION_PENDING`, 44 `NOT_STARTED`, 2 `COMPLETED`.
- `docs/traceability.md` maps only **24** requirement IDs. Coverage is therefore ~13% of stories and is
  concentrated on the features remediated in this programme.
- `requirement_file.md` has 66 headings/sections and still states requirements that do not exist
  (administrative **web** dashboard, Airtel Money escrow, scheduling, reconciliation) — some are
  labelled as gaps in its own audit table (§10), but the requirement list itself still reads as scope.
- Status vocabulary is inconsistent between documents: `completed` (lowercase, 112) vs `COMPLETED`
  (uppercase, 2) vs `VERIFICATION_PENDING` vs TASKS' statuses. `AGENTS.md` defines a vocabulary, but
  `prd.json` predates it.

## 3. Status honesty

- The 112 `completed` stories include features this cycle found failing (escrow release, deletion,
  doorstep PIN, offline queue, reports). They were marked complete by the earlier agent and were never
  re-verified; R15's rule "every completed story must carry verifiable evidence" is not met.
- The remediation stories added in `prd.json` correctly separate implementation from
  `independent_verification`, but most still carry `VERIFICATION_PENDING`/`IN_PROGRESS` honestly.
- `AGENT_STATE.md` stops at "Wave 1f … Next: R08 REM-030/031" and has no entry for the Prompt-2 review
  cycle; an agent resuming from it would not know 15 reviewers were re-run.
- `README.md:137` still says "111 tests" while the suite is 171.

## 4. Onboarding instructions

- `README.md` gives `./gradlew testDebugUnitTest` and `npm test`; the backend command is accurate and
  was executed in this cycle. The Android command cannot run in this environment (no JDK/SDK) — that
  limitation is not stated at the command.
- `README_AI.md` still instructs agents to work from `d:\skills exchange\` with Windows-only setup
  (lines 47, 182, 197).
- `AGENTS.md:213` asserts "No environment setup is required" and names
  `org.gradle.java.home=D:/kilo_downloads/jdk17/...`; `gradle.properties` no longer contains those
  paths. A new agent following it will look for a JDK that is not configured, and the note explicitly
  tells them not to report a missing JDK — the opposite of the current environment's truth.

## 5. Precedence and duplication

- `docs/DOCUMENT_MAP.md` publishes precedence (product truth → `prd.json` → `TASKS.md`, snapshots last)
  and is linked from the README — a real improvement.
- The repository root remains a document dump: 87+ diagnostic/build artifacts, two APKs, a SQLite
  database and multiple historical audits, none listed in the map. Root clutter undermines the map.
- `review_results/` (first cycle) and `review_results/prompt2/` (this cycle) coexist; the register now
  distinguishes them, so history is preserved.

## 6. Changelog and decisions

- `CHANGELOG.md` entries for Waves 1b–1f honestly mark `IN_PROGRESS` and list unverified items.
- `DECISIONS.md` records earlier decisions (including a stale JDK-path decision) but has no entry for
  the Prompt-2 review cycle or for the decision to record Android checks as UNVERIFIED.
- No decision record explains why `exportSchema = false`, why the doorstep PIN default exists, or why
  reports/ratings are device-only.

## 7. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R15-P2-01 | HIGH | 112 stories marked completed without evidence, contradicting this cycle's findings | `prd.json`; R02/R04/R08/R09 |
| R15-P2-02 | HIGH | Traceability covers 24 of 185 stories (~13%) | `docs/traceability.md`; `prd.json` |
| R15-P2-03 | HIGH | Onboarding docs are wrong/Windows-only and explicitly discourage reporting a missing JDK | `AGENTS.md:213`; `README_AI.md:47,182,197` |
| R15-P2-04 | MEDIUM | `AGENT_STATE.md` has no Prompt-2 entry; README says 111 tests (actual 171) | `AGENT_STATE.md`; `README.md:137` |
| R15-P2-05 | MEDIUM | Status vocabulary mixed (`completed` vs `COMPLETED` vs `VERIFICATION_PENDING`) | `prd.json` counts |
| R15-P2-06 | LOW | Root diagnostic/artifact dump not covered by the document map | repo root |

## 8. Verdict

**FAIL.** The mechanical documentation gates are green and the document map/precedence rule is a real
improvement, but completion status is not evidence-backed, traceability covers a fraction of the
requirements, and the agent onboarding instructions actively misdescribe the environment. No
documentation claim about runtime behaviour is verifiable without the missing toolchain.
