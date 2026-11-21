# REVIEWER REGISTER — Skills Exchange

**Programme:** ASMT-2026-11-19 · **Status:** reviews not started · **Last updated:** 2026-11-19
**Source of truth for reviewer status:** this file, updated only with linked evidence.

## 1. Roster

| ID | Professional title | PRD | Priority | Wave | Gate | Instruction file | Status |
|---|---|---|---|---|---|---|---|
| R01 | Principal Software Architect & Codebase Integrity Reviewer | RVW-001 | P0 | 0 | **Build gate** | `deleted after verification` → `verification/reports/VERIFICATION_01.md` | `submitted` — **FAIL** ([REVIEW_01](reviewers/reports/REVIEW_01.md)) |
| R14 | DevOps, Release Engineering & Supply Chain Reviewer | RVW-014 | P0 | 0 | **Reproducibility gate** | `reviewers/DEVOPS_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_14](reviewers/reports/REVIEW_14.md)) |
| R07 | Application Security Engineer | RVW-007 | P0 | 1 | Security gate | `reviewers/SECURITY_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_07](reviewers/reports/REVIEW_07.md)) |
| R10 | Safety, Trust & Fraud Prevention Reviewer | RVW-010 | P0 | 1 | Safety gate | `reviewers/SAFETY_FRAUD_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_10](reviewers/reports/REVIEW_10.md)) |
| R08 | Payments, Escrow & Financial Controls Auditor | RVW-008 | P0 | 1 | Money gate | `reviewers/PAYMENTS_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_08](reviewers/reports/REVIEW_08.md)) |
| R09 | Privacy, KYC & Regulatory Compliance Reviewer | RVW-009 | P0 | 1 | Compliance gate | `reviewers/PRIVACY_COMPLIANCE_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_09](reviewers/reports/REVIEW_09.md)) |
| R06 | Authentication & Identity Engineer | RVW-006 | P0 | 2 | Identity gate | `reviewers/AUTH_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_06](reviewers/reports/REVIEW_06.md)) |
| R03 | Backend & Cloud Functions Engineer | RVW-003 | P0 | 2 | — | `deleted after verification` → `verification/reports/VERIFICATION_03.md` | `submitted` — **FAIL** ([REVIEW_03](reviewers/reports/REVIEW_03.md)) |
| R04 | Data & Persistence Integrity Engineer | RVW-004 | P1 | 2 | — | `reviewers/DATA_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_04](reviewers/reports/REVIEW_04.md)) |
| R05 | API & External Integrations Engineer | RVW-005 | P1 | 2 | — | `reviewers/API_INTEGRATION_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_05](reviewers/reports/REVIEW_05.md)) |
| R11 | QA & Test Engineering Lead | RVW-011 | P1 | 3 | Evidence gate | `reviewers/QA_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_11](reviewers/reports/REVIEW_11.md)) |
| R13 | Performance, Scalability & Reliability Engineer | RVW-013 | P1 | 3 | — | `reviewers/PERFORMANCE_RELIABILITY_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_13](reviewers/reports/REVIEW_13.md)) |
| R02 | Senior Android Client Engineer (Kotlin/Compose) | RVW-002 | P1 | 3 | — | `deleted after verification` → `verification/reports/VERIFICATION_02.md` | `submitted` — **FAIL** ([REVIEW_02](reviewers/reports/REVIEW_02.md)) |
| R12 | UI/UX & Accessibility Specialist | RVW-012 | P1 | 3 | — | `reviewers/UI_UX_ACCESSIBILITY_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_12](reviewers/reports/REVIEW_12.md)) |
| R15 | Documentation & Requirements Traceability Auditor | RVW-015 | P1 | 4 | Synthesis gate | `reviewers/DOCUMENTATION_REVIEWER.md` | `submitted` — **FAIL** ([REVIEW_15](reviewers/reports/REVIEW_15.md)) |

**Totals:** 15 reviewers · 8 P0 · 7 P1 · **15 submitted (all FAIL)** · 0 not started · 3 open critical clusters (public data, safety dispatch, money lifecycle).

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
| F-023 phone signup has no Firebase identity | R06 | R02, R03 |
| F-024 duplicate `sosIncidents` blocks nullify guards | R07 | R10, R09 |
| F-025 forgeable notifications/transactions | R07 | R03, R08 |
| F-026 storage writes trust self-asserted metadata | R07 | R09, R10 |
| F-027 `job_quotes` readable by all authenticated users | R07 | R08, R09 |
| F-028 admin unlock is a local boolean | R06 | R07, R10 |
| F-029 deletion over-deletes cached jobs and erases complaints | R09 | R07, R10 |
| F-030 PIN disclosed to the worker | R10 | R07, R02 |
| F-031 no refund implementation exists | R08 | R03, R09 |
| F-032 fabricated transcription fallback | R05 | R09, R12 |
| F-033 backups enabled with empty rules | R07 | R09, R14 |
| F-034 escrow release never invoked from the client | R08 | R02, R03 |
| F-035 no crash/analytics telemetry | R13 | R11, R14 |
| F-036 Airtel/card channels non-functional but offered | R05 | R15, R08 |
| F-037 reports never leave the device | R10 | R07, R09 |

## 7. Blockers and log

| Date | Reviewer | Event | Evidence |
|---|---|---|---|
| 2026-11-19 | — | Programme established; 15 instruction files issued | this register |
| 2026-11-19 | R01 | Pre-review static finding: HEAD not buildable (F-001) | `ASSESSMENT_REPORT.md` §6, §11 |
| 2026-11-19 | R01 | **Submitted — FAIL.** Four production source files structurally invalid; `ActiveJobScreen` dead; 2 god classes; no DI/nav. Gradle not executable in environment (UNVERIFIED). | `reviewers/reports/REVIEW_01.md` |
| 2026-11-19 | R14 | **Submitted — FAIL.** No CI; `D:/` paths; Java 11 vs 25; unexecutable wrapper; missing keystore; R8 off; 62 MB artifacts; `npm run build` exits 2 (8 TS errors); 16 prod vulnerabilities (1 critical). | `reviewers/reports/REVIEW_14.md` |
| 2026-11-19 | R11 | Executed `npm run build` (FAIL, exit 2) and `npm test` (111/111 pass) after `npm ci`; confirms backend build/test split. | `REVIEW_14.md` §2.3 |
| 2026-11-19 | R07 | **Submitted — FAIL.** `jobs`/`messages` world-readable to unauthenticated clients; duplicate `sosIncidents`; forgeable `notifications`/`momo_transactions`; self-asserted storage metadata; empty backup rules. | `reviewers/reports/REVIEW_07.md` |
| 2026-11-19 | R10 | **Submitted — FAIL.** SOS dispatch is a log-only stub returning `SENT`; PIN shown to worker; reports stay in Room; disputes deletable by the reported user. | `reviewers/reports/REVIEW_10.md` |
| 2026-11-19 | R08 | **Submitted — FAIL.** No release caller; fabricated `DISBURSED`; no refund function; client-computed fee; device-local ledger; Airtel/card absent. | `reviewers/reports/REVIEW_08.md` |
| 2026-11-19 | R09 | **Submitted — FAIL.** Erasure over-deletes cached jobs, aborts, ignores Storage, erases complaints, no re-auth; notice false; no retention. | `reviewers/reports/REVIEW_09.md` |
| 2026-11-19 | R06 | **Submitted — FAIL.** Device-side OTP mints a local session without a Firebase credential; signup broken; admin claim unprovisionable; admin unlock cosmetic. | `reviewers/reports/REVIEW_06.md` |
| 2026-11-19 | R03 | **Submitted — FAIL.** Build exits 2; no `predeploy`; no App Check/rate limits; no FCM send; unthrottled webhook; no refund executor. | `reviewers/reports/REVIEW_03.md` |
| 2026-11-19 | R04 | **Submitted — FAIL.** `exportSchema=false`, 16 untested migrations, 33 `Double` money fields, no reconciliation. | `reviewers/reports/REVIEW_04.md` |
| 2026-11-19 | R05 | **Submitted — FAIL.** Committed AT key; fabricated transcription; log-only SMS; Airtel/Maps/card not integrated; no contract tests. | `reviewers/reports/REVIEW_05.md` |
| 2026-11-19 | R11 | **Submitted — FAIL.** 355 Android tests unexecutable; no rules/migration/UI/coverage/CI; historical build claims unsupported. | `reviewers/reports/REVIEW_11.md` |
| 2026-11-19 | R13 | **Submitted — FAIL.** No telemetry; offline replay no-op; ad-hoc coroutine scopes; 31 MB APK; no baselines. | `reviewers/reports/REVIEW_13.md` |
| 2026-11-19 | R02 | **Submitted — FAIL.** Onboarding cannot obtain a Firebase identity; 0 `rememberSaveable`/`BackHandler`; PIN rendered to both parties; strings inline. | `reviewers/reports/REVIEW_02.md` |
| 2026-11-19 | R12 | **Submitted — FAIL.** 99× `5.sp`, 59 null content descriptions, 0 `testTag`/`stringResource`, no localisation. | `reviewers/reports/REVIEW_12.md` |
| 2026-11-19 | R15 | **Submitted — FAIL.** 99 completed stories contradicted by code; 61 `file:///d:/` links; 81 build-success claims; 6 requirement IDs. | `reviewers/reports/REVIEW_15.md` |

## 7b. Gate status (post-cycle, 2026-11-19)

| Gate | Owner | Status | Evidence |
|---|---|---|---|
| G0 Build | R01 | **FAIL** | `REVIEW_01.md`: `SkillsExchangeRepository.kt:579–651`, `FirebaseService.kt:709–716`, `GeminiAudioTranscriptionService.kt:1`, `ActiveJobScreen.kt` |
| G1 Reproducibility | R14 | **FAIL** | `REVIEW_14.md`: no CI, `D:/` paths, Java 11/25 split, non-executable wrapper, `tsc` exit 2, 16 prod advisories |
| G2 Security | R07 | **FAIL** | `REVIEW_07.md`: world-readable `jobs`/`messages`, committed key, forgeable collections, self-asserted storage metadata, no App Check/rules tests |
| G3 Safety | R10 | **FAIL** | `REVIEW_10.md`: log-only SOS returning `SENT`, PIN disclosed to worker, reports stay on device, no audit trail |
| G4 Money | R08 | **FAIL** | `REVIEW_08.md`: release unwired, fabricated `DISBURSED`, no refund function, client-computed fee, local-only ledger |
| G5 Compliance | R09 | **FAIL** | `REVIEW_09.md`: aborted/over-broad erasure, complaint destruction, false privacy notice, no retention, backups enabled |
| G6 Identity | R06 | **FAIL** | `REVIEW_06.md`: phone signup never creates a Firebase credential; admin claim unprovisionable; cosmetic admin unlock |
| G7 Evidence | R11 | **FAIL** | `REVIEW_11.md`: Android suite cannot execute, no rules/migration/UI/coverage/CI |
| G8 Synthesis | R15 | **FAIL** | `REVIEW_15.md`: 99 completed stories contradicted by code, 61 host-absolute links, 81 build claims, 6 requirement IDs |

**Programme result:** 15/15 reviews submitted, **0 PASS**, **0 PASS WITH CONDITIONS**, **15 FAIL**,
**0 BLOCKED**. No report is marked `accepted` because G0/G1 remain closed and R15 returned FAIL.

Consequence: every release gate is closed. Remediation must be authorised as a separate change
programme; this register records evidence only and does not authorise source changes.

## 8. Register maintenance rules

1. Only append rows; never delete history.
2. Every status change references a report path or command transcript.
3. Verdicts expire when the reviewed commit changes; re-open affected reviewers.
4. R15 owns final reconciliation; R01 owns the build gate; no one may self-waive a gate.
5. Findings that cross domains are owned by the reviewer with the most direct evidence, with supporting reviewers named.

*Next action: provision a JDK/Android SDK environment and start R01 (see `REVIEW_PLAN.md` §3).*

## 8. Prompt-2 strict sequential re-review

A second, strictly sequential review cycle began on 2026-11-20 at `aa4a0bc`. Execution position and
blockers are tracked in `REVIEWER_EXECUTION_STATE.md`; the current-cycle report is
`review_results/prompt2/REVIEW_01.md`.

| Reviewer | Cycle status | Verdict | Report |
|---|---|---|---|
| R01 Architecture & Codebase Integrity | submitted | **FAIL** (build UNVERIFIED) | `review_results/prompt2/REVIEW_01.md` |
| R02 Android Client | submitted | **FAIL** (escrow success UI; dynamic checks UNVERIFIED) | `review_results/prompt2/REVIEW_02.md` |
| R03 Backend & Cloud Functions | submitted | **FAIL** (deploy config, handler tests) | `review_results/prompt2/REVIEW_03.md` |
| R04 Data & Persistence | submitted | **FAIL** (schema export, default PIN, migration coverage) | `review_results/prompt2/REVIEW_04.md` |
| R05 API & External Integrations | submitted | **FAIL** (SMS stub, FCM registration, currency units) | `review_results/prompt2/REVIEW_05.md` |
| R06 Authentication & Identity | submitted | **FAIL** (deletion ordering, no Storage cleanup, no linking) | `review_results/prompt2/REVIEW_06.md` |
| R07 Application Security | submitted | **FAIL** (client-trusted PIN, deletion rules, escrow fields) | `review_results/prompt2/REVIEW_07.md` |
| R08 Payments & Escrow | submitted | **FAIL** (release does not disburse, device-only ledger) | `review_results/prompt2/REVIEW_08.md` |
| R09 Privacy & Compliance | submitted | **FAIL** (consent/DSAR unreachable, erasure incomplete, no retention) | `review_results/prompt2/REVIEW_09.md` |
| R10 Safety & Fraud | submitted | **FAIL** (PIN, reports, SOS claims, no duplicate detection) | `review_results/prompt2/REVIEW_10.md` |
| R11 QA & Test Engineering | submitted | **FAIL** (no rules/Compose/handler tests, Android not in CI) | `review_results/prompt2/REVIEW_11.md` |
| R12 UI/UX & Accessibility | submitted | **FAIL** (no localisation, no semantics, deceptive safety copy) | `review_results/prompt2/REVIEW_12.md` |
| R13 Performance & Reliability | submitted | **FAIL** (inert offline queue, no observability, no paging) | `review_results/prompt2/REVIEW_13.md` |
| R14 DevOps & Supply Chain | submitted | **FAIL** (committed artifacts, no Android CI, deploy not reproducible) | `review_results/prompt2/REVIEW_14.md` |
| R15 Documentation & Traceability | submitted | **FAIL** (112 unevidenced completions, 13% traceability, stale onboarding) | `review_results/prompt2/REVIEW_15.md` |

**Prompt-2 cycle complete: 15/15 examined, 15 FAIL.**
