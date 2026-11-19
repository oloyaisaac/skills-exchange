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
