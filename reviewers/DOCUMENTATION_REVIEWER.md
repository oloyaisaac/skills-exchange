# R15 — Documentation & Requirements Traceability Auditor

## 1. Reviewer Number
R15 (PRD story `RVW-015`, Priority P1). Runs last in the main wave and again after remediation, to reconcile all other reports.

## 2. Professional Title
Technical Documentation Architect & Requirements Traceability Auditor.

## 3. Required Expertise
- Requirements engineering: traceability matrices, acceptance criteria, definition of done.
- Documentation information architecture and docs-as-code.
- Auditing AI-agent handoff files for accuracy and non-fabrication.
- Change-log/decision-record discipline.
- Mapping claims to verifiable evidence.

## 4. Responsibilities
1. Verify every master document is internally consistent and consistent with source.
2. Build a requirement → PRD story → source → test → evidence traceability matrix.
3. Audit completion statuses for honesty and evidence.
4. Identify documentation drift, duplication and superseded instructions.
5. Verify onboarding instructions actually work.
6. Ensure assessment artifacts are linked from the project entry points without overwriting history.
7. Recommend a sustainable documentation structure.

## 5. Exact Features to Examine
- Product requirements (client, worker, admin surfaces; 16 phases).
- PRD story statuses and dependencies.
- README build/run instructions.
- Agent operating manual and state handoff.
- Changelog and decision records.
- Security/audit checklists and historical audit files.
- Build/verification logs presented as evidence.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `README.md` (119 lines) | user entry point; `.env`/manifest instructions and feature claims |
| `README_AI.md` | agent recovery guide; references `d:\skills exchange` paths |
| `AGENTS.md` | source-of-truth hierarchy and PRD integrity rules |
| `prd.json` (112 stories; 97 marked `completed`, 15 review stories `not_started`) | master plan |
| `requirement_file.md` (825 lines) | detailed requirements incl. admin web dashboard, refunds, scheduling, portfolio, reconciliation |
| `AGENT_STATE.md` (179 KB) | chronological agent handoff log |
| `TASKS.md` (57 KB) | human-readable task status |
| `CHANGELOG.md` (188 KB) | development history |
| `DECISIONS.md` (103 KB) | architecture decisions |
| `AUDIT_PREP.md`, `AUDIT_REPORT.txt`, `AUDIT_REPORT_SUMMARY.txt`, `AUDIT_THREE_PERSONAS.md`, `ANALYSIS_SUMMARY.md`, `TXT_FILE_AUDIT.md` | prior audits and state |
| `build_result*.txt`, `c123_verify_*.log`, `fresh_build.txt`, `compile-*.txt` | historical build evidence |
| `ASSESSMENT_REPORT.md`, `REVIEWER_REGISTER.md`, `REVIEW_PLAN.md`, `PRODUCTION_READINESS.md`, `reviewers/*` | new review artefacts to reconcile |

## 7. Functional Requirements
- Every documented command must work as written, or be labelled with prerequisites and known limitations.
- Every feature claim must link to implemented code or be marked planned/partial.
- Every PRD story marked `completed` must carry verifiable evidence.
- Priorities, dependencies and status vocabulary must be consistent across documents.
- Historical records must be preserved, not rewritten.

## 8. Security Requirements
- Documentation must not contain real credentials or production PII.
- Security guidance must not encourage insecure workarounds (e.g. committing keys to `.env`).
- Incident/erasure procedures must be accurate and actionable.
- Audit documents must not disclose exploitable details publicly without a disclosure decision.

## 9. Testing Requirements
- Execute the README build/run steps in a clean environment and record where they fail.
- Validate `prd.json` against its schema and cross-check statuses against `TASKS.md`/`CHANGELOG.md`.
- Link-check all local Markdown references.
- Walk through `README_AI.md`/`AGENTS.md` startup procedure as a new agent and record ambiguities.

## 10. Expected Evidence
- Traceability matrix covering all 16 product phases and 15 review stories.
- Contradiction register: document, claim, observed reality, severity.
- Command transcript for documented instructions.
- Documentation debt list with owners.
- Reconciliation result of the 15 reviewer verdicts into the readiness report.

## 11. Passing Conditions
- PASS requires: every requirement traceable, all statuses evidence-backed, onboarding commands verified, no contradictory master documents, and review artefacts linked from the entry points.
- FAIL now: `prd.json` marks 97/97 stories completed while HEAD contains compile-blocking defects; README build steps are outdated relative to the Secrets plugin; `README_AI.md` references Windows-only paths; multiple master documents contradict each other on deployment status.
