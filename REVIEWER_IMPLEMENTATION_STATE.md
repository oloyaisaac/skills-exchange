# REVIEWER IMPLEMENTATION STATE — Prompt 4 (strict one-reviewer-at-a-time)

**Programme:** ASMT-2026-11-19 · **Cycle:** Prompt-4 remediation
**Baseline:** `3822249` (`arena/c27fd4d6-skills-exchange`) · **Updated:** 2026-11-20

## Position

| Field | Value |
|---|---|
| Total original reviewers | **15** |
| Current reviewer | **R01 — Principal Software Architect & Codebase Integrity Reviewer** |
| Reviewers fully implemented and verified | **0** |
| Reviewers awaiting implementation | R01 (in progress), R02–R15 |
| Next reviewer number | 2 (R02) — **not started**; blocked behind R01's gate |

## R01 — task progress

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R01-P2-01 | Android buildability must be demonstrated (`:app:compileDebugKotlin`) | **BLOCKED** | No JDK, Gradle runtime or Android SDK in this environment; command cannot run |
| R01-P2-02 | One Firebase gateway; no UI→gateway bypass | **IMPLEMENTED BUT NOT VERIFIED** | `SkillsExchangeViewModel.kt:76` single private instance injected into the repository; `OnboardingScreen` uses `currentAuthenticatedUid()`; `scripts/check_architecture_guards.mjs` passes |
| R01-P2-03 | Decompose the two god classes | **PARTIALLY IMPLEMENTED** | Roadmap recorded in `DECISIONS.md` D-2026-11-20-1 (five slices); no code split yet |
| R01-P2-04 | Remove unused root manifest | **IMPLEMENTED AND VERIFIED** | Root `AndroidManifest.xml` deleted; guard check passes |
| R01-P2-05 | Correct stale test count | **IMPLEMENTED AND VERIFIED** | `README.md` now states 171; `check_readme_claims.mjs` passes |

## Outstanding tasks (R01)

- Execute `./gradlew :app:compileDebugKotlin` and `:app:testDebugUnitTest` on a provisioned machine.
- Split the ViewModel/repository along the recorded slices (A–E), keeping the security tests green.

## Shared-component change (supporting R14, not R14 remediation)

- CI pinned Node 20 while every `.mjs` suite imports `../src/*.ts` (type stripping requires Node 22),
  so `npm test` could never pass in CI. CI now uses Node 22 and `functions/package.json` says `>=22`
  instead of the untrue `18`. Recorded for R14 to reconcile fully.

## Verification evidence

- `node scripts/check_architecture_guards.mjs` → passed (single gateway, no UI bypass, no duplicate manifest).
- `node scripts/check_readme_claims.mjs`, `check_doc_links.mjs` → passed.
- Backend unchanged; last run at `aa4a0bc` was build exit 0, 171/171 tests.
- CI `Backend build and tests`: previously red on Node 20 (`ERR_UNKNOWN_FILE_EXTENSION`); after the
  Node 22 pin the same command passes locally (171/171) and is being re-run by CI.
- Android compilation: **not executed** — no JDK/Android SDK.

## Environment probe (2026-11-20, branch `arena/5ea38882`)

| Capability | Result |
|---|---|
| `java` / `javac` preinstalled | absent |
| JDK 17 obtainable without the user | **yes** — `pip install jdk4py==17.0.9.2` provides Temurin 17.0.9 |
| `services.gradle.org`, `dl.google.com`, `repo1.maven.org`, `maven.google.com`, `api.adoptium.net` | DNS/TLS blocked (curl exit 000) |
| GitHub release **assets** (`release-assets.githubusercontent.com`) | blocked (`SSL_ERROR_SYSCALL`); GitHub release metadata via API works |
| npm registry, PyPI | reachable |
| Gradle 9.5.0 distribution | not reachable (only GitHub release asset / services.gradle.org) |
| Android SDK / AGP / Compose / Firebase AARs | not reachable (Google Maven + Maven Central blocked) |
| Firebase emulator jars | not reachable (storage.googleapis.com); needs a Firebase project anyway |
| Resources | 2 vCPU, 3 GB RAM, 20 GB free disk |

**Consequence:** even with a JDK, `:app:compileDebugKotlin` cannot resolve a single Android dependency.
R01-P2-01 and any further ViewModel refactor are blocked until either the allowlist is widened or a
Gradle dependency cache + Android SDK are supplied.

## Blockers

| Blocker | Impact | Needs |
|---|---|---|
| No JDK 17, Gradle runtime or Android SDK | R01-P2-01 cannot be satisfied; R01 cannot pass its completion gate; Android edits (VM/Onboarding) are unverified beyond static guards | Provision JDK 17 + SDK 37, or authorize closing R01 with P2-01 permanently UNVERIFIED for this environment (which would not be "implemented and verified") |

## Completion declarations

None. `reviewer_completions/` has not been created because no reviewer has passed its gate.
