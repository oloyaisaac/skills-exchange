# REVIEWER IMPLEMENTATION STATE — Prompt 4 (strict one-reviewer-at-a-time)

**Programme:** ASMT-2026-11-19 · **Cycle:** Prompt-4 remediation
**Updated:** 2026-11-20 · **Branch:** `arena/55b27a05-skills-exchange`

## Position

| Field | Value |
|---|---|
| Total original reviewers | **15** |
| Current reviewer | **R04 — Data & Persistence** (next to start) |
| Reviewers fully implemented and verified | **R01, R02, R03 (3 of 15)** |
| Reviewers awaiting implementation | R04 … R15 |
| Next reviewer number | **4** |

## R01 — COMPLETE (gate passed)

Buildability proven on a runner (run 44694282419): `:app:compileDebugKotlin` success, 355/355 unit
tests after fixing 186 compile errors. Single Firebase gateway, duplicate root manifest removed,
README counts corrected, decomposition roadmap recorded (DECISIONS D-2026-11-20-1).
Declaration: `reviewer_completions/REVIEWER_01_DONE.md`.

## R02 — COMPLETE (gate passed)

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R02-P2-01 | No success UI without server confirmation | **IMPLEMENTED AND VERIFIED** | release goes through `releaseEscrowToWorker`; `applyServerConfirmedEscrowRelease` records only on success; `updateEscrowStatus` refuses client `RELEASED_TO_WORKER`; cash path now `CASH_SETTLEMENT_ACKNOWLEDGED` ("no money is held by the platform"); `EscrowGatingComposeTest` |
| R02-P2-02 | Single-flight on money/SOS actions | **IMPLEMENTED AND VERIFIED** | `EscrowReleaseState.Releasing` guard + disabled button (tested); `sosDispatchInFlight` guard |
| R02-P2-03 | Onboarding state survives rotation/process death | **IMPLEMENTED AND VERIFIED** | `rememberSaveable` for step, roles, name, phone, OTP, district, cooldown; `StateRestorationTester` test |
| R02-P2-04 | No invented SOS coordinates | **IMPLEMENTED AND VERIFIED** | fallback removed; unknown location is `0.0, 0.0` with the real district |
| R02-P2-05 | Compose UI tests for the four critical flows | **IMPLEMENTED AND VERIFIED** | onboarding, job posting, quoting and escrow tests, executed on JVM (Robolectric) in CI |
| R02-P2-06 | No `FLAG_SECURE` on identity/PIN surfaces | **IMPLEMENTED AND VERIFIED** | `SecureScreen()` applied on escrow/PIN, guarantor and account screens |
| R02-P2-07 | UI reads `viewModel.firebaseService` | **IMPLEMENTED AND VERIFIED** | removed in R01; architecture guard prevents regression |

## Verification evidence (executed)

- CI `Android build` run 44704246765: compile success, **362 tests, 0 failures** (includes the four
  new Compose flow tests), backend 171/171, documentation and secret checks pass.
- Static guards: architecture, README claims, links, document map, traceability, secret scan.

## Not claimed

- No on-device/manual matrix was run: rotation, process death and permission denial are covered by
  Robolectric restoration tests and inspection, not by a physical device. This is stated so it is not
  mistaken for device verification.
- R02's PASS conditions also require backend enforcement of the controls the UI presents; where that
  is still missing (PIN, payout) it is tracked by R08/R10, not silently accepted here.

## R03 — COMPLETE (gate passed)

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R03-P2-01 | Node 18 is end-of-life | **IMPLEMENTED AND VERIFIED** | `functions/package.json` `engines.node` is `22`; CI builds and tests on Node 22 |
| R03-P2-02 | No predeploy build step | **IMPLEMENTED AND VERIFIED** | `firebase.json` `functions.predeploy` runs `npm --prefix "$RESOURCE_DIR" run build` |
| R03-P2-03 | Two payout implementations that can disagree | **IMPLEMENTED AND VERIFIED** | one `performEscrowRelease` core; `releaseEscrowToWorker` and the deprecated `createMoMoPayout` alias both delegate; the alias ignores client `amountUgx`/`workerMomoNumber` (handler test asserts the stored amount and wallet win) |
| R03-P2-04 | No Firestore index file | **IMPLEMENTED AND VERIFIED** | `firestore.indexes.json` with the four composite indexes the queries need, referenced by `firebase.json` |
| R03-P2-05 | Provider/Gemini keys read from plain env | **IMPLEMENTED AND VERIFIED** | `defineSecret` for `AFRICASTALKING_API_KEY`, `AFRICASTALKING_USERNAME`, `GEMINI_API_KEY`; `secretOrEnv()` falls back to env; `.runWith({ secrets })` on `sendOtp`, `verifyOtp`, `transcribeVoiceJob`, `onJobCreated`, `onSosIncidentCreated` |
| R03-P2-06 | FCM used only for SOS | **IMPLEMENTED AND VERIFIED** | `sendUserPush()` sends to `user_{uid}` on payout release, KYC decision and both sides of a dispute; Android subscribes/unsubscribes the topic on sign-in/out (`syncPushTopic`) |
| R03-P2-07 | No per-IP limit on OTP sends | **IMPLEMENTED AND VERIFIED** | `otp_ip_limits` transaction, 20 sends per IP per hour (`OTP_IP_WINDOW_MS`), checked before the provider call |
| R03-P2-08 | Docs claim 18 exports / 111 tests | **IMPLEMENTED AND VERIFIED** | `reviewers/BACKEND_REVIEWER.md` now states 27 exports (24 callables, 1 webhook, 2 triggers), 16 test files / 172 assertions, Node 22, and the 5 emulator handler tests; `COVERAGE_MATRIX.md` matches |

Alongside the findings, the release path was corrected to accept `assignedProviderId` — the field the
Android client actually writes — instead of only `assignedProvider`/`assignedWorkerId`. Without it the
callable refused every client-created job.

### Verification evidence (executed)

- CI run 44709995723: `Backend build and tests` pass (172/172), `Backend handler tests (Firestore
  emulator)` pass with the step asserting exactly `# pass 5` / `# fail 0`, documentation and secret
  checks pass.
- Handler tests (`functions/test/handlers.emulator.test.mjs`) call the real callables through
  `firebase-functions-test` against the Firestore emulator: owner release writes the payout and the
  job state, a repeat release is idempotent (one payout document), a non-owner is rejected with no
  payout written, an unfunded job is refused, and `createMoMoPayout` ignores a tampered amount and
  wallet.
- `functions/test/escrowRelease.test.mjs` covers the six pure release decisions plus the client field
  name.

### Not claimed

- **No `firebase deploy` and no real-provider traffic.** There is no Firebase project, Blaze plan or
  MTN/Africa's Talking sandbox available here, so deployment, index creation, Secret Manager
  resolution and live provider callbacks are **UNVERIFIED**. The Firestore emulator proves the
  handler logic, not the deployed configuration.
- FCM delivery to a device was not observed; only the send call and topic subscription are in place.
- App Check and webhook-level rate limiting remain open and belong to later reviewers (R07).

## Completion declarations

- `reviewer_completions/REVIEWER_01_DONE.md`
- `reviewer_completions/REVIEWER_02_DONE.md`
- `reviewer_completions/REVIEWER_03_DONE.md`

## Blockers

None. R03 complete; R04 (Data & Persistence) is next.
