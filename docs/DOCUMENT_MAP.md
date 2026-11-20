# Document Map — role, authority and owner

REM-072: this repository has several long-lived documents that once overlapped. The rule below
makes precedence explicit. If two documents disagree, the higher row wins; the lower document must
be corrected rather than followed.

| Document | Role | Authority (highest first) | Owner | Update rule |
|---|---|---|---|---|
| `requirement_file.md` | Product requirements (what the product must do) | 1 | Product owner | Appended/changed only with a stated product decision; never silently rewritten by an implementation task |
| `prd.json` | Machine-readable story/task state (what is planned, in progress or done) | 2 | Engineering lead | Status changes must cite evidence; `last_updated` and the relevant `status_note` are updated together |
| `IMPLEMENTATION_ROADMAP.md` | Remediation plan: task → finding, dependencies, tests, acceptance, evidence | 3 | Engineering lead | Plan changes only; execution status lives in `prd.json` and `TASKS.md` |
| `review_results/MASTER_REVIEW_FINDINGS.md` | Consolidated, immutable review findings (`MR-0NN`) | 4 | Independent reviewers | Never edited to reflect a fix; fixes are evidenced in code/tests and task state |
| `reviewers/reports/*.md`, `review_results/R*.md` | Original reviewer evidence | 5 (historical record) | Independent reviewers | Never edited during implementation; a later review supersedes, it does not rewrite |
| Source code and `firestore.rules` / `storage.rules` | Actual behaviour | 6 (ground truth for what ships) | Engineering | Must be consistent with rows 1–5; discrepancies are defects, not documentation errors |
| `TASKS.md` | Human-readable task tracking | 7 | Engineering lead | Mirrors `prd.json`; update both in the same commit |
| `AGENT_STATE.md` | Chronological engineering log of agent sessions | 8 (history) | Engineering agent | Prepend a dated entry per session; never rewrite earlier entries |
| `CHANGELOG.md` | User-visible change history | 9 | Engineering | Prepend under the current date; one entry per merged change set |
| `DECISIONS.md` | Architecture decision records | 10 | Engineering lead | Append a new ADR; supersede rather than edit an accepted decision |
| `README.md` | Orientation for newcomers | 11 | Product owner | Kept short; must link to this map rather than restate requirements |
| `AGENTS.md`, `README_AI.md` | Instructions for automation | 12 | Engineering lead | Changed only when the workflow changes |
| `docs/traceability.md` | Requirement → code → test matrix | 4 (supplements findings) | Engineering lead | Updated when a requirement, implementation file or test changes |
| `docs/privacy/RETENTION.md` | Retention schedule, legal holds and the deletion sweep that enforces them | 3 | Engineering lead | Changed in the same commit as `RETENTION_POLICY` (`functions/src/retention.ts`); the table must match the constant |
| `docs/privacy/DSAR.md` | Operating procedure for access and erasure requests | 3 | Engineering lead | Must describe what the code does; corrected in the same change as `deleteMyAccount` / `exportMyData` |
| `docs/privacy/ROPA.md` | Record of processing: category, subject, purpose, basis, location, recipient, retention | 3 | Engineering lead | Updated with `RETENTION_POLICY` and `storage.rules`; gaps are listed rather than smoothed over |
| `docs/privacy/PROCESSORS.md` | Processor list, data categories, transfer basis | 3 | Engineering lead | A processor is added here before it is enabled in production |
| `docs/safety/SAFETY_DESK_RUNBOOK.md` | Operating procedure for the safety desk: priorities, response targets, triage states, dispatch outcomes, escalation | 3 | Trust & Safety lead | Every number in it is a constant in `functions/src/reports.ts` or `sosStatus.ts`; gaps are listed rather than smoothed over |
| `docs/qa/TEST_STRATEGY.md` | Test inventory, release gates, coverage floors, flake policy and the gap list | 3 | QA lead | Every number in it must come from a command in the document; gaps are listed rather than smoothed over |
| `docs/ops/RELEASE_AND_SUPPLY_CHAIN.md` | Release configuration (version, signing, R8), dependency advisories, the SBOM and licence gate, and the hygiene rules | 3 | Engineering lead | Every number comes from `npm audit`, `report_dependencies.mjs` or a CI run; unexecuted steps are named as such |
| `docs/ops/DEPLOY_AND_ROLLBACK.md` | Deploy commands, the environment gap, and the rollback procedure with its untested drill | 3 | Engineering lead | Procedures only; nothing here has been executed, and §6 says so |
| `docs/ops/RELIABILITY_AND_PERFORMANCE.md` | Offline-queue contract, function runtime presets, provider retry rule, size budget, list bounds and the SLOs that are specified but not wired | 3 | Engineering lead | Each row names its code and its guard; anything unmeasurable is listed as a gap in §8 |
| `docs/ux/ACCESSIBILITY_AND_LOCALISATION.md` | The semantics layer, the measured contrast floors, the localisation state and the counted copy debt | 3 | UI/UX lead | Every ratio comes from `ContrastTest`; unreviewed translations and unrun TalkBack passes are named as gaps |
| `review_results/`, `review_results/prompt2/` | Independent review reports and cycle matrices | 14 (snapshot; not current state) | Review lead | Append-only; never rewritten to change a verdict |
| `docs/DOCUMENT_MAP.md` | This precedence map | 0 (meta) | Engineering lead | Updated whenever a document is added, removed or changes role |
| `SECURITY.md` | Security policy and reporting | 13 | Security lead | Changed only with a security decision |
| `ASSESSMENT_REPORT.md`, `PRODUCTION_READINESS.md`, `ANALYSIS_SUMMARY.md`, `AUDIT_PREP.md`, `AUDIT_THREE_PERSONAS.md`, `REVIEW_PLAN.md`, `REVIEWER_REGISTER.md`, `REVIEWER_EXECUTION_STATE.md`, `REVIEWER_IMPLEMENTATION_STATE.md`, `TXT_FILE_AUDIT.md` | Point-in-time audits and review operations | 14 (snapshot; not current state) | Review lead | Left as evidence of the date they were produced; current status is read from `prd.json` |

## Conflict examples

- A completed story in `prd.json` contradicted by source code: the source wins; the story is
  reopened with a note (this is why the 2026-11-19 audit found 99 unsupported completions).
- A roadmap acceptance criterion that is weaker than `requirement_file.md`: the requirement wins.
- A reviewer report that names a file/line since refactored: the finding stays historically true;
  the task records the current location and the fix evidence.

## Owner of this map

Engineering lead. It is updated whenever a top-level document is added, removed or changes role.
`scripts/check_document_map.mjs` verifies that every top-level Markdown document and every `docs/`
document is listed and that each listed path exists.
