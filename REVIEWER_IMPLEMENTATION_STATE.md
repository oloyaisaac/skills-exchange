# REVIEWER IMPLEMENTATION STATE — Prompt 4 (strict one-reviewer-at-a-time)

**Programme:** ASMT-2026-11-19 · **Cycle:** Prompt-4 remediation
**Baseline:** `3822249` → merged PRs #12 and #13 · **Branch:** `arena/c11c487e-skills-exchange`
**Updated:** 2026-11-20

## Position

| Field | Value |
|---|---|
| Total original reviewers | **15** |
| Current reviewer | **R02 — Android Client** (not started) |
| Reviewers fully implemented and verified | **R01 (1 of 15)** |
| Reviewers awaiting implementation | R02 … R15 |
| Next reviewer number | **2** |

## R01 — COMPLETE (gate passed)

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R01-P2-01 | Demonstrate Android buildability | **IMPLEMENTED AND VERIFIED** | `Android build` workflow run 44694282419 on commit `f9c5d9c`: `:app:compileDebugKotlin` success, `:app:testDebugUnitTest` **355 tests, 0 failures** |
| R01-P2-02 | One Firebase gateway, no UI bypass | **IMPLEMENTED AND VERIFIED** | single private instance injected into the repository; UI uses `currentAuthenticatedUid()`; `check_architecture_guards.mjs` in CI |
| R01-P2-03 | Agreed decomposition roadmap for the god classes | **IMPLEMENTED AND VERIFIED** | `DECISIONS.md` D-2026-11-20-1: five behaviour-preserving slices with their seams and ordering |
| R01-P2-04 | Remove unused root manifest | **IMPLEMENTED AND VERIFIED** | root `AndroidManifest.xml` deleted; guard check passes |
| R01-P2-05 | Correct stale test count | **IMPLEMENTED AND VERIFIED** | README now states 171 backend / 355 Android tests |

### What the build gate exposed (and what was fixed to close it)

The first real compile produced **186 errors** across 10 files. Fixing them was work that R01's
assignment required, not optional polish:

- `QuickRequestDrawer.kt` was spliced together — a duplicated `package`/import header, helper
  composables inserted mid-body, and `materialsP rovidedBy` in the callback signature. Rebuilt as one
  coherent three-step sheet over the same callback contract.
- `AppDatabase` used an invalid `converters = [...]` argument; now `@TypeConverters`.
- `JobPostEntity` was missing the `apartmentSuite` column that the repository and Firestore sync
  already write; added.
- `FirebaseService` referenced a non-existent `firebaseAuth` field and called `child` on
  `FirebaseStorage`; now `auth` and `storage.reference.child`.
- `InteractiveMapView` had an unbuildable fallback composable and an out-of-scope `workerMarker`;
  replaced/hoisted. `JobPipelineKanban` had `private val` default parameters and
  `JobStatus.Accepted`; corrected, and the quote sheet navigates through `LocalContext`.
- `OnboardingScreen` declared its phone/OTP state twice and shadowed the ViewModel's errors.
- ViewModel used `.value` on a delegated state property, had a JVM setter clash, and a duplicate
  rating accessor; repository duplicated rating methods with conflicting signatures.
- Build configuration pinned a Java 11 toolchain, which Robolectric refuses for SDK 34+; now Java 17
  compile targets and a Java 21 test launcher, with the SDK pinned in `robolectric.properties`.
- `acknowledgeSos`/`resolveSosIncident` accepted an actor parameter but ignored it and re-read
  Firebase; they now validate the explicit actor (still fail-closed on blank/synthetic values).

## Verification evidence (executed)

- CI `Android build` run 44694282419: compile **success**, unit tests **355/355 pass**.
- `check_architecture_guards.mjs`, `check_readme_claims.mjs`, `check_doc_links.mjs`,
  `check_document_map.mjs`, `check_traceability.mjs`, `secret_scan.mjs`: pass.
- Backend (unchanged by R01): build exit 0, 171/171 tests on Node 22.

## Completion declarations

- `reviewer_completions/REVIEWER_01_DONE.md` — created after the gate passed.

## Blockers

None for R01. R02 has not been started.
