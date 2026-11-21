# REVIEW PLAN — Skills Exchange Independent Review Programme

**Programme ID:** ASMT-2026-11-19
**Baseline commit:** `6b39a10` on `arena/517a4993-skills-exchange`
**Mode:** review-only. No application source, rules, build scripts or tests may be changed during review.
**Owner:** Programme lead (architecture) · **Synthesis:** R15 · **Gates:** R01, R14

---

## 1. Objectives

1. Replace assertion-based status ("97/97 completed", "BUILD SUCCESSFUL") with reproducible, cited evidence.
2. Determine, per domain, whether the product can safely handle real money, identity documents and emergency events.
3. Produce a defensible production-readiness verdict and an ordered remediation backlog — without implementing remediation.
4. Leave a review system that can be re-run after every significant change.

## 2. Principles

- **Evidence over narrative.** A file:line citation, a command transcript, or a labelled `UNVERIFIED`.
- **Fail closed.** Missing evidence is never a pass.
- **One owner per domain.** No duplicated verdicts; supporting reviewers named explicitly.
- **Read-only review.** Findings go to reports, not into `app/src` or `functions/src`.
- **Preserve history.** Master documents are appended/prepended, never rewritten.
- **No production claims.** The programme cannot certify readiness on behalf of the business; it can only recommend.

## 2b. Execution status (final, 2026-11-19)

| Reviewer | State | Verdict | Report |
|---|---|---|---|
| R01 Architecture & Codebase Integrity | submitted | **FAIL** | `reviewers/reports/REVIEW_01.md` |
| R14 DevOps & Release | submitted | **FAIL** | `reviewers/reports/REVIEW_14.md` |
| R07 Application Security | submitted | **FAIL** | `reviewers/reports/REVIEW_07.md` |
| R10 Safety, Trust & Fraud | submitted | **FAIL** | `reviewers/reports/REVIEW_10.md` |
| R08 Payments & Escrow | submitted | **FAIL** | `reviewers/reports/REVIEW_08.md` |
| R09 Privacy, KYC & Compliance | submitted | **FAIL** | `reviewers/reports/REVIEW_09.md` |
| R06 Auth & Identity | submitted | **FAIL** | `reviewers/reports/REVIEW_06.md` |
| R03 Backend & Cloud Functions | submitted | **FAIL** | `reviewers/reports/REVIEW_03.md` |
| R04 Data & Persistence | submitted | **FAIL** | `reviewers/reports/REVIEW_04.md` |
| R05 API & Integrations | submitted | **FAIL** | `reviewers/reports/REVIEW_05.md` |
| R11 QA & Test Engineering | submitted | **FAIL** | `reviewers/reports/REVIEW_11.md` |
| R13 Performance & Reliability | submitted | **FAIL** | `reviewers/reports/REVIEW_13.md` |
| R02 Android Client | submitted | **FAIL** | `reviewers/reports/REVIEW_02.md` |
| R12 UI/UX & Accessibility | submitted | **FAIL** | `reviewers/reports/REVIEW_12.md` |
| R15 Documentation & Traceability | submitted | **FAIL** | `reviewers/reports/REVIEW_15.md` |

**Programme status:** 15/15 submitted; **0 PASS, 0 PASS WITH CONDITIONS, 15 FAIL, 0 BLOCKED**. Gates
G0–G8 are all closed (see `REVIEWER_REGISTER.md` §7b). No PASS verdict was admissible because G0/G1
failed before Wave 1 and were never re-run green.

**Execution mode:** static source, rules, manifest, configuration and executable Node-suite analysis
at baseline `6b39a10`. No Android build, emulator, device or provider sandbox was available; every
dynamic check is labelled `UNVERIFIED` in its report and credited as neither pass nor fail.

## 3. Execution waves

### Wave 0 — Gates (must complete first)
| Order | Reviewer | Why first | Exit criterion |
|---|---|---|---|
| 0.1 | **R01** Architecture & Codebase Integrity | A non-compiling HEAD invalidates every dynamic claim | Gate G0 recorded: build evidence or `FAIL` with exact defects |
| 0.2 | **R14** DevOps & Release | Reproducibility determines whether other reviewers can execute anything | Gate G1: clean-clone Linux build/test transcript or `FAIL` |

Parallel within Wave 0 is allowed for static work, but **no other reviewer may issue PASS before G0 is recorded**. R07, R09 and R10 may begin static analysis immediately (no build required) and submit provisional findings.

### Wave 1 — Highest harm (P0)
| Order | Reviewer | Focus | Parallel-safe with |
|---|---|---|---|
| 1.1 | **R07** Security | Rules, secrets, exposure, hardening | R09, R10, R08 |
| 1.2 | **R10** Safety & Fraud | SOS truthfulness, PIN, abuse | R07, R09 |
| 1.3 | **R08** Payments | Money correctness and controls | R03, R04 |
| 1.4 | **R09** Privacy & Compliance | PII, consent, retention, erasure | R07, R06 |

### Wave 2 — Identity and platform (P0/P1)
| Order | Reviewer | Focus |
|---|---|---|
| 2.1 | **R06** Auth & Identity | OTP, Google, restore, admin claims |
| 2.2 | **R03** Backend & Functions | Callables, webhook, runtime, deploy |
| 2.3 | **R04** Data & Persistence | Room v27, migrations, ledger, sync |
| 2.4 | **R05** API & Integrations | Provider contracts, keys, stubs, currency |

### Wave 3 — Quality and experience (P1)
| Order | Reviewer | Focus |
|---|---|---|
| 3.1 | **R11** QA & Test Engineering | Reproducibility, coverage, claim integrity |
| 3.2 | **R13** Performance & Reliability | Baselines, listeners, retries, observability |
| 3.3 | **R02** Android Client | Compose/lifecycle/permissions (needs G0) |
| 3.4 | **R12** UI/UX & Accessibility | A11y, localisation, states, copy |

### Wave 4 — Synthesis
| Order | Reviewer | Focus |
|---|---|---|
| 4.1 | **R15** Documentation & Traceability | Reconcile all reports, traceability matrix, readiness re-score |
| 4.2 | Programme lead | Accept/reject gate outcomes; publish remediation backlog |

**Estimated effort** (not a commitment): Wave 0 2–3 days; Wave 1 5–7 days; Wave 2 4–6 days; Wave 3 4–6 days; Wave 4 2 days. Reviews may overlap within a wave; a wave's PASS verdicts wait for its dependencies.

## 4. Entry and exit criteria

### Reviewer entry
- Instruction file read in full.
- Baseline commit recorded.
- Required environment available (or limitation declared up front).
- No open change to the files under review.

### Reviewer exit (`submitted`)
1. All 11 mandated sections completed.
2. Every finding has severity, file:line evidence, and a proposed verification.
3. Executed commands and outputs attached or quoted.
4. Unexecuted checks explicitly labelled `UNVERIFIED`.
5. Verdict chosen from the register vocabulary.
6. Report stored at `reviewers/reports/REVIEW_<NN>.md`.
7. Register updated.

### Programme exit (`accepted`)
- All 15 reports submitted.
- G0–G8 recorded with evidence.
- Traceability matrix covers 100% of phases and stories.
- `PRODUCTION_READINESS.md` re-scored and signed off by the programme lead.
- Residual risks accepted in writing by the product owner (not by the review team).

## 5. Environment requirements

| Review | Required environment | If unavailable |
|---|---|---|
| R01, R02, R11, R12, R13 | JDK 17, Android SDK 36/37, Gradle 9.5, emulator or device | Static findings only; verdict cannot exceed `UNVERIFIED` |
| R03, R05, R08 | Node 22 (test runner), Firebase CLI + emulator, provider sandbox credentials | Pure-module tests only; mark integration `UNVERIFIED` |
| R07 | Firebase Emulator Suite for rules tests; secret scanner; dependency scanners | Static rules review; rules gate cannot pass |
| R14 | Clean Linux container with no `D:/` paths; CI runner | Reproducibility gate `FAIL` |
| R09, R10, R15 | Document access; stakeholder interviews; policy artifacts | Mark legal/operational conclusions `UNVERIFIED` |

Minimum viable environment to unlock most reviews: **JDK 17 + Android SDK + Node 22 + Firebase CLI/emulator + a clean Linux checkout**.

## 6. Evidence protocol

1. Cite `path:line` (range when helpful) for every code claim.
2. Quote command and output for every executed check; include tool versions.
3. Distinguish: **OBSERVED** (ran it), **READ** (source/doc), **INFERRED** (reasoned), **UNVERIFIED** (not executed).
4. Never cite historical logs as current evidence; re-run against the baseline commit.
5. Redact secrets, tokens and PII; reference location, not value.
6. Screenshots must include device/emulator spec and date.
7. Findings get IDs `F-<domain>-<nn>` and link to the pre-review register where applicable.

## 7. Reviewer report template

```markdown
# REVIEW <ID> — <Title>
- Reviewer: <name/role>  |  Date: YYYY-MM-DD  |  Commit: <sha>
- Verdict: PASS | PASS WITH CONDITIONS | FAIL | BLOCKED | UNVERIFIED
- Environment: <tools, versions, what was unavailable>

## 1. Scope reviewed
## 2. Evidence executed (commands, transcripts)
## 3. Findings (ID, severity, file:line, impact, reproduction)
## 4. Requirements assessment (functional/security/testing)
## 5. Coverage gaps and UNVERIFIED items
## 6. Passing conditions outcome
## 7. Recommended remediation (not implemented)
## 8. Residual risk and owner
## 9. Cross-references (other reviewers, readiness gates)
```

## 8. Change control

- **Allowed during review:** reports, register rows, findings, readiness scoring, documentation corrections that do not alter product claims.
- **Not allowed:** application source, rules, build config, test code, secret removal, data deletion. Those are remediation tasks requiring separate authorisation.
- **Emergency exception only:** a live credential or active exploit may be escalated to the owner for immediate rotation outside the review process; document the action, do not perform source surgery silently.
- Any change to the baseline commit re-opens the affected reviewers (verdicts are commit-scoped).

## 9. Synthesis and remediation handoff

R15 produces:
1. **Traceability matrix** — requirement → PRD story → source → test → evidence → verdict.
2. **Contradiction register** — document claim vs observed reality.
3. **Readiness re-score** — per gate, with the exact evidence that changed.

Programme lead then writes a **remediation backlog** ordered by:
1. S1 security/safety/money blockers (F-001, F-002, F-003, F-005, F-007).
2. Gate restorations (build, CI, signing).
3. Compliance and data-lifecycle work.
4. Quality, accessibility, performance, documentation.

Each remediation item gets a new PRD story; reviewers never mark their own remediation as passed — independent re-review is required.

## 10. Re-review triggers

- Any commit touching `firestore.rules`, `storage.rules`, auth, payments or SOS → re-open R06/R07/R08/R10.
- Any schema version bump → re-open R04.
- Any new callable or external provider → re-open R03/R05.
- Any release-signing/CI change → re-open R14.
- Monthly, or before any production launch attempt → re-run G0–G8.

## 11. Definition of done for the programme

- 15/15 reports submitted and reconciled.
- Every gate has an evidence-backed status.
- The readiness report clearly states, without euphemism, what is and is not safe.
- No reviewer has modified product source.
- All new findings are traceable to a remediation owner or an accepted residual risk.

*Start here:* R01 at `reviewers/ARCHITECTURE_REVIEWER.md` (deleted after verification; its preserved text and verdict are in `verification/reports/VERIFICATION_01.md`), then R14 at `reviewers/DEVOPS_REVIEWER.md`.
