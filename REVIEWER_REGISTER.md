# REVIEWER REGISTER — Skills Exchange

**Programme:** ASMT-2026-11-19 · **Status:** reviews not started · **Last updated:** 2026-11-19
**Source of truth for reviewer status:** this file, updated only with linked evidence.

## 1. Roster

| ID | Professional title | PRD | Priority | Wave | Gate | Instruction file | Status |
|---|---|---|---|---|---|---|---|
| R01 | Principal Software Architect & Codebase Integrity Reviewer | RVW-001 | P0 | 0 | **Build gate** | `reviewers/ARCHITECTURE_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_01](reviewers/reports/REVIEW_01.md)) |
| R14 | DevOps, Release Engineering & Supply Chain Reviewer | RVW-014 | P0 | 0 | **Reproducibility gate** | `reviewers/DEVOPS_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_14](reviewers/reports/REVIEW_14.md)) |
| R07 | Application Security Engineer | RVW-007 | P0 | 1 | Security gate | `reviewers/SECURITY_REVIEWER.md` | `not_started` |
| R10 | Safety, Trust & Fraud Prevention Reviewer | RVW-010 | P0 | 1 | Safety gate | `reviewers/SAFETY_FRAUD_REVIEWER.md` | `not_started` |
| R08 | Payments, Escrow & Financial Controls Auditor | RVW-008 | P0 | 1 | Money gate | `reviewers/PAYMENTS_REVIEWER.md` | `not_started` |
| R09 | Privacy, KYC & Regulatory Compliance Reviewer | RVW-009 | P0 | 1 | Compliance gate | `reviewers/PRIVACY_COMPLIANCE_REVIEWER.md` | `not_started` |
| R06 | Authentication & Identity Engineer | RVW-006 | P0 | 2 | Identity gate | `reviewers/AUTH_REVIEWER.md` | `not_started` |
| R03 | Backend & Cloud Functions Engineer | RVW-003 | P0 | 2 | — | `reviewers/BACKEND_REVIEWER.md` | `not_started` |
| R04 | Data & Persistence Integrity Engineer | RVW-004 | P1 | 2 | — | `reviewers/DATA_REVIEWER.md` | `not_started` |
| R05 | API & External Integrations Engineer | RVW-005 | P1 | 2 | — | `reviewers/API_INTEGRATION_REVIEWER.md` | `not_started` |
| R11 | QA & Test Engineering Lead | RVW-011 | P1 | 3 | Evidence gate | `reviewers/QA_REVIEWER.md` | `not_started` |
| R13 | Performance, Scalability & Reliability Engineer | RVW-013 | P1 | 3 | — | `reviewers/PERFORMANCE_RELIABILITY_REVIEWER.md` | `not_started` |
| R02 | Senior Android Client Engineer (Kotlin/Compose) | RVW-002 | P1 | 3 | — | `reviewers/ANDROID_CLIENT_REVIEWER.md` | `not_started` |
| R12 | UI/UX & Accessibility Specialist | RVW-012 | P1 | 3 | — | `reviewers/UI_UX_ACCESSIBILITY_REVIEWER.md` | `not_started` |
| R15 | Documentation & Requirements Traceability Auditor | RVW-015 | P1 | 4 | Synthesis gate | `reviewers/DOCUMENTATION_REVIEWER.md` | `not_started` |

**Totals:** 15 reviewers · 8 P0 · 7 P1 · **2 submitted (both FAIL)** · 13 not started.

## 2. Status vocabulary

| Status | Meaning |
|---|---|
| `not_started` | Instruction file exists; no evidence produced |
| `in_progress` | Reviewer has begun and recorded at least one evidence item |
| `blocked` | Cannot proceed; blocker and owner recorded below |
| `submitted` | Report complete at `reviewers/reports/REVIEW_<ID>.md`; awaiting synthesis |
| `accepted` | R15 has reconciled the report into the assessment and readiness report |
| `reopened` | New evidence invalidates a prior verdict |

A reviewer may only move to `submitted` with all 11 sections complete and every claim cited or labelled `UNVERIFIED`.

## 3. Verdict vocabulary

`PASS` · `PASS WITH CONDITIONS` · `FAIL` · `BLOCKED` · `UNVERIFIED`
No reviewer may issue `PASS` for an area they could not execute; unexecuted evidence is `UNVERIFIED`.

## 4. Coverage matrix

| Domain | Primary | Supporting | Explicitly out of scope for primary |
|---|---|---|---|
| Layering, buildability, dead code | R01 | R14, R11 | Deep UI behaviour (R02), rule semantics (R07) |
| Compose/lifecycle/permissions | R02 | R12, R11 | Accessibility detail (R12), auth policy (R06) |
| Cloud Functions, callables, webhook | R03 | R08, R07 | Money semantics (R08), rules policy (R07) |
| Room schema, migrations, ledger integrity | R04 | R08, R13 | Financial controls (R08), server txns (R03) |
| External API contracts & keys | R05 | R03, R07 | Identity verification logic (R06) |
| Auth, session, identity, admin claims | R06 | R07, R09 | Rules language review (R07) |
| Rules, secrets, manifest, threat model | R07 | R06, R09, R05 | Compliance obligations (R09) |
| Escrow, payouts, cashout, reconciliation | R08 | R03, R04, R10 | Rules policy (R07) |
| PII, KYC, consent, retention, erasure | R09 | R07, R06 | Fraud detection (R10) |
| SOS, doorstep PIN, ratings, disputes, abuse | R10 | R08, R09, R06 | Financial reconciliation (R08) |
| Tests, coverage, gates, claim integrity | R11 | R15, all | Domain correctness itself |
| Accessibility, copy, localisation, UX states | R12 | R02 | Lifecycle bugs (R02) |
| Performance, cost, SLOs, observability | R13 | R03, R04 | Functional correctness |
| Build, signing, CI/CD, supply chain, artifacts | R14 | R01, R11 | App architecture (R01) |
| Traceability, doc consistency, status honesty | R15 | R11, all | Re-doing specialist analysis |

No domain is unassigned; no domain has two primaries.

## 5. Gate definitions

| Gate | Owner | Entry condition | Pass condition | Blocks |
|---|---|---|---|---|
| G0 Build | R01 | Repository checked out at assessment commit | `:app:compileDebugKotlin` succeeds; no unresolved/dead source; structural scan clean | All other reviews' PASS verdicts |
| G1 Reproducibility | R14 | G0 evidence available | Clean Linux clone builds/tests; no machine-local paths; signed release config documented | R11, R13, R15 |
| G2 Security | R07 | G0 | No public PII reads; no secrets in source; rules tests green; hardening applied | Release gate |
| G3 Safety | R10 | G0 | SOS dispatch verified or claim removed; PIN secret and rate-limited | Release gate |
| G4 Money | R08 | G0, R03 draft | Server-authoritative, idempotent, reconciled money flows | Release gate |
| G5 Compliance | R09 | G0 | Lawful basis/consent, retention, complete erasure, truthful claims | Release gate |
| G6 Identity | R06 | G0 | Fail-closed restore, admin claim lifecycle, complete logout/erasure | Release gate |
| G7 Evidence | R11 | G1 | Tests reproducible and coverage measured; no unsupported "verified" claims | R15 accepted status |
| G8 Synthesis | R15 | G0–G7 submitted | Traceability matrix complete; readiness report reconciled | Programme close |

A gate failure is recorded with a finding ID and an owner; it is never waived silently.

## 6. Finding ownership

| Finding (from `ASSESSMENT_REPORT.md`) | Primary reviewer | Confirms/refutes required |
|---|---|---|
| F-001 non-buildable HEAD | R01 | R11, R14 |
| F-002 committed Africa's Talking key | R07 | R05, R14 |
| F-003 public `jobs`/`messages` reads | R07 | R09, R10 |
| F-004 doorstep PIN weaknesses | R10 | R07, R06, R04 |
| F-005 fabricated SOS SMS | R10 | R05, R07 |
| F-006 escrow/payout not wired | R08 | R03, R02 |
| F-007 incomplete account deletion | R09 | R07, R06, R04 |
| F-008 no admin claim provisioning | R06 | R03, R07 |
| F-009 no CI/non-portable build | R14 | R01, R11 |
| F-010 rules tests/index gaps | R07 | R03, R11 |
| F-011 App Check/rate limits | R07 | R03, R13 |
| F-012 no observability | R13 | R11, R14 |
| F-013 requirements/status drift | R15 | R09, R11 |
| F-014 artifact hygiene | R14 | R09, R07 |
| F-015 localisation/accessibility | R12 | R02, R11 |
| F-016 money representation/currency | R08 | R04, R05 |
| F-017 functions runtime/deploy config | R03 | R14 |
| F-018 unexported Room schema | R04 | R11 |
| F-019 fabricated AI fallback | R05 | R02, R12 |
| F-020 dead code/duplicate config | R01 | R02, R14 |
| F-021 backend supply chain (16 advisories) | R14 | R03 |
| F-022 offline queue never replays | R13 | R02, R11 |

## 7. Blockers and log

| Date | Reviewer | Event | Evidence |
|---|---|---|---|
| 2026-11-19 | — | Programme established; 15 instruction files issued | this register |
| 2026-11-19 | R01 | Pre-review static finding: HEAD not buildable (F-001) | `ASSESSMENT_REPORT.md` §6, §11 |
| 2026-11-19 | R01 | **Submitted — FAIL.** Four production source files structurally invalid; `ActiveJobScreen` dead; 2 god classes; no DI/nav. Gradle not executable in environment (UNVERIFIED). | `reviewers/reports/REVIEW_01.md` |
| 2026-11-19 | R14 | **Submitted — FAIL.** No CI; `D:/` paths; Java 11 vs 25; unexecutable wrapper; missing keystore; R8 off; 62 MB artifacts; `npm run build` exits 2 (8 TS errors); 16 prod vulnerabilities (1 critical). | `reviewers/reports/REVIEW_14.md` |
| 2026-11-19 | R11 | Executed `npm run build` (FAIL, exit 2) and `npm test` (111/111 pass) after `npm ci`; confirms backend build/test split. | `REVIEW_14.md` §2.3 |

## 7b. Gate status (Wave 0 outcome, 2026-11-19)

| Gate | Owner | Status | Evidence |
|---|---|---|---|
| G0 Build | R01 | **FAIL** | `REVIEW_01.md`: `SkillsExchangeRepository.kt:579–651`, `FirebaseService.kt:709–716`, `GeminiAudioTranscriptionService.kt:1`, `ActiveJobScreen.kt` |
| G1 Reproducibility | R14 | **FAIL** | `REVIEW_14.md`: no CI, `D:/` paths, Java 11/25 split, non-executable wrapper, `tsc` exit 2, 16 prod advisories |

Consequence: **no reviewer may issue a PASS verdict** while G0/G1 are failed. Wave 1 may proceed only as static/provisional analysis (R07, R09, R10 are safe to start); dynamic claims remain `UNVERIFIED`.

## 8. Register maintenance rules

1. Only append rows; never delete history.
2. Every status change references a report path or command transcript.
3. Verdicts expire when the reviewed commit changes; re-open affected reviewers.
4. R15 owns final reconciliation; R01 owns the build gate; no one may self-waive a gate.
5. Findings that cross domains are owned by the reviewer with the most direct evidence, with supporting reviewers named.

*Next action: provision a JDK/Android SDK environment and start R01 (see `REVIEW_PLAN.md` §3).*
