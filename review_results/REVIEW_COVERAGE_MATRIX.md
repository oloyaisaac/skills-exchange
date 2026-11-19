# REVIEW COVERAGE MATRIX

**Programme:** Prompt 2 review execution · **Source:** HEAD `06ff649` · **Date:** 2026-11-19
**Assigned:** 15 · **Completed:** 15 · **Incomplete:** 0

Coverage is measured by evidence produced, not by files opened. A component is "Examined" only where
at least one finding or verified-working statement cites it; "Tests performed" lists only what was
actually executed. Remaining examinations are runtime checks blocked by the environment.

## 1. Reviewer coverage

| # | Reviewer | Assigned responsibilities | Components examined | Tests performed | Remaining examinations | Status | Issues |
|---|---|---|---|---|---|---|---|
| R01 | Architecture & Codebase Integrity | Buildability, layering, god classes, dead code | 68 Kotlin files, Gradle config, manifests | Structural scan; `gradlew` attempt (no JDK) | Compile, module graph, architecture tests | COMPLETE (static) | 3 |
| R02 | Android Client | Compose state, lifecycle, permissions, onboarding | Screens, components, ViewModel, MainActivity | Static state/lifecycle analysis; call-chain tracing | Build, rotation, process death, device signup | COMPLETE (static) | 6 |
| R03 | Backend & Cloud Functions | Callables, authz, webhook, deploy, FCM | 18 exports, money helpers, config | `npm ci`, `npm test` (111/111), `npm run build` (exit 2) | Emulator, provider sandbox, deploy | COMPLETE (partial execution) | 6 |
| R04 | Data & Persistence | Room schema, migrations, money, transactions | 18 entities, 27 versions, 16 migrations, DAOs | Static schema/query analysis | Upgrade paths, query plans, volume load | COMPLETE (static) | 5 |
| R05 | Integrations | AT, MoMo, Gemini, Maps, FCM, channels | All remote clients + functions money modules | Credential/endpoint tracing | Provider calls, delivery, map render | COMPLETE (static) | 5 |
| R06 | Auth & Authorization | OTP, sessions, claims, re-auth, linking | Auth paths, session manager, rules, admin UI | Call-chain tracing; claim grep | Live OTP, token exchange, claim provisioning | COMPLETE (static) | 4 |
| R07 | Application Security | Rules, secrets, storage, manifest, abuse | `firestore.rules`, `storage.rules`, manifest, backups | Rule-by-rule analysis; secret/App Check grep | Emulator exploit tests, live project probes | COMPLETE (static) | 11 |
| R08 | Payments & Escrow | Collection, release, refund, payout, ledger | Repository, payout service, callables | Call-site trace; Node money tests (111/111) | MoMo sandbox E2E, settlement, reconciliation | COMPLETE (partial execution) | 9 |
| R09 | Privacy, KYC & Compliance | PII, KYC, consent, erasure, retention, notices | Deletion path, KYC storage, notice, consent | Deletion trace; retention/processor grep | Legal review, DSR dry run, backup/restore | COMPLETE (static) | 7 |
| R10 | Safety, Trust & Fraud | SOS, PIN, reports, disputes, verification | SOS service, escrow sheet, rules, validators | Dispatch trace; rules/read analysis | Real alert delivery, drill, moderator queue | COMPLETE (static) | 7 |
| R11 | QA & Test Engineering | Tests, coverage, rules/migration/UI, CI | 41 JVM files, 1 androidTest, Node tests | Counted 356 tests; `npm test` 111/111; build exit 2 | Android suite, rules/migration/UI/E2E suites | COMPLETE (partial execution) | 6 |
| R12 | UI/UX & Accessibility | Semantics, type, localisation, formatting | Screens/components, theme, strings | Static token/size counts | TalkBack, contrast, font scale, screenshots | COMPLETE (static) | 5 |
| R13 | Performance & Reliability | Telemetry, offline, polling, size, SLOs | ViewModel, listeners, deps, assets | Dependency/asset measurement | Profiling, macrobenchmark, battery, load | COMPLETE (static) | 6 |
| R14 | DevOps & Supply Chain | CI, portability, signing, artifacts, deps | Gradle, wrapper, firebase.json, package files | `npm ci`, `npm audit`, build, `gradlew` attempt | Android release build, signing, deploy drill | COMPLETE (partial execution) | 5 |
| R15 | Documentation & Traceability | Status honesty, PRD, links, traceability | 13 root docs + PRD + reports | Counts (81 build claims, 67 links, 112 stories, 6 IDs); link check | Historical build reconstruction | COMPLETE (static) | 6 |

**Totals:** 15 reviewers, **91 individual issues** raised, 73 consolidated master issues.
No reviewer was skipped; every assignment file in `reviewers/` was read and mapped to a report.

## 2. Responsibility coverage detail

| Responsibility area | Owner | Evidence produced | Coverage |
|---|---|---|---|
| Buildability (Android) | R01 | 4 invalid files, line excerpts | Full (static) |
| Buildability (backend) | R03/R14 | Executed `tsc` exit 2, 8 errors | Full (executed) |
| CI/CD & release | R14 | No CI, `D:/` config, signing/R8 gaps | Full (static) |
| Firestore rules | R07 | Actor-by-actor rule analysis | Full (static) |
| Storage rules | R07/R09 | Metadata-spoof and KYC paths | Full (static) |
| Secrets | R07/R05 | Committed AT key, client Gemini key | Full (static) |
| Authentication | R06/R02 | OTP chain, claims, restore, re-auth | Full (static) |
| Payments lifecycle | R08/R03 | Call-site trace, money tests | Full (static + unit) |
| Safety dispatch | R10/R05 | Stub body, no FCM send | Full (static) |
| Privacy/erasure | R09 | Deletion cascade, retention, notice | Full (static) |
| Data integrity | R04 | Schema, migrations, money, dual-write | Full (static) |
| Integrations | R05 | Credentials, endpoints, claims | Full (static) |
| Accessibility/localisation | R12/R02 | Labels, sizes, strings | Full (static) |
| Performance/observability | R13 | Telemetry, offline, assets | Partial (no measurement) |
| QA evidence | R11 | Test counts, Node execution | Partial (Android unexecutable) |
| Documentation | R15 | Claims, links, PRD, IDs | Full (static) |

## 3. What "complete" means here

Every reviewer completed its assignment to the extent the environment allowed. Six reviewers
(R03, R08, R11, R14 and partially R05/R13) executed commands; the rest produced source-level evidence.
The following were **not** performed and are not credited:

| Test class | Reason | Affected reviewers |
|---|---|---|
| Android compile/unit/instrumentation | No JDK/Android SDK; module does not compile | R01, R02, R11, R12, R13 |
| Firestore/Storage emulator rules tests | No emulator/project; no tests exist | R07, R09, R10, R11 |
| Real OTP/SMS/FCM delivery | No provider credentials/devices | R03, R05, R06, R10 |
| MoMo collection/release/refund/payout | No sandbox credentials or registered callback | R03, R08 |
| Backup/restore, DSR, retention | No device/legal environment | R09 |
| TalkBack, contrast, font scale | No emulator/device | R12 |
| Profiling, load, battery | No device/backend load environment | R13 |
| Admin claim provisioning | No Firebase project/Admin credentials | R06, R10 |

## 4. Coverage verdict

- **No reviewer skipped.** All 15 assignments executed and reported.
- **No feature approved unverified.** Runtime-only behaviour is labelled `unable to verify` and never
  counted as passing.
- **Coverage is sufficient for remediation planning**, not for release approval.
