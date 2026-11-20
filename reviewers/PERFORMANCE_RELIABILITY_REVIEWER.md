# R13 — Performance, Scalability & Reliability Engineer

## 1. Reviewer Number
R13 (PRD story `RVW-013`, Priority P1). Depends on R01.

## 2. Professional Title
Senior Performance & Reliability Engineer (Android + Firebase), SRE practices for serverless and mobile clients.

## 3. Required Expertise
- Android startup, jank, memory, battery and network profiling (Perfetto, Macrobenchmark, Baseline Profiles).
- Compose recomposition/performance and list virtualization.
- Firestore listener/read cost modelling, query fan-out, offline cache behaviour.
- Backoff/retry, circuit breaking, queue drain and idempotent replay.
- SLI/SLO design, error budgets, observability (crash, ANR, structured logs, tracing).
- Capacity planning and cost control for mobile-money and messaging workloads.

## 4. Responsibilities
1. Establish performance baselines (cold/warm start, jank, APK size, memory).
2. Audit Firestore read/write amplification and listener lifecycle.
3. Audit offline queue drain, retry storms and duplicate side effects.
4. Audit startup work on the main thread and blocking calls in composition.
5. Audit failure handling: timeouts, retries, user messaging, graceful degradation.
6. Assess observability and define missing SLIs/SLOs and alerts.
7. Model scale and cost at expected user volumes.

## 5. Exact Features to Examine
- App startup, session restore, first render.
- Nearby provider discovery and filtering (in-memory vs query).
- Job pipeline and notification/chat listeners.
- Map rendering and fallback.
- Gemini voice transcription and maps grounding (45 s timeouts).
- Offline pending-action queue.
- Escrow/payout polling.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `MainActivity.kt`, `SkillsExchangeViewModel.restoreSession` | startup path and session gate |
| `ui/viewmodel/SkillsExchangeViewModel.kt` | flow composition, listeners, polling, uncaught launch blocks |
| `data/repository/SkillsExchangeRepository.kt` | `getRealtimeChatMessages`, jobs flows, queue, wallet balance |
| `data/remote/FirebaseService.kt` | Firestore listeners/writes, Storage uploads |
| `data/remote/GeminiAudioTranscriptionService.kt` | 45 s timeouts, large base64 payloads |
| `ui/components/InteractiveMapView.kt` | map lifecycle |
| `util/NetworkConnectivityObserver.kt` | connectivity signal |
| `app/src/main/res/drawable/img_before_leak.jpg` (896 KB), `img_after_repair.jpg` (680 KB) | asset weight |
| `_device_test/installed-base.apk` (32 MB) | APK size evidence |
| `diag-logcat-dump.txt`, `hs_err_pid*.log`, `replay_pid*.log` | historical crash/ANR evidence |
| `app/build.gradle.kts` | minify disabled, no baseline profile |
| `functions/src/*.ts` | cold start, timeouts, provider latency |

## 7. Functional Requirements
- Cold start within an agreed budget on a low-end device.
- No unbounded Firestore listener or full-collection read in a hot path.
- Offline queue drains once, in order, with a retry cap and visible state.
- Payment/SOS polling has a bounded lifetime and stops on screen exit.
- Degraded network produces actionable UI within a defined timeout.

## 8. Security Requirements
- Retries must not replay money or SOS actions without idempotency keys.
- Cached data must respect user scoping after account switch/logout.
- No sensitive payloads in performance traces or verbose logs.
- Rate limits/quotas protect callables and the webhook from abuse and cost blow-up.

## 9. Testing Requirements
- Macrobenchmark startup/jank suites and Baseline Profile generation.
- Firestore emulator load tests for listener count and read volume.
- Chaos tests: airplane mode, flaky network, token expiry, provider 5xx.
- Queue drain soak test (hours) and duplicate-prevention verification.
- Cost model with reads/writes per core journey.

## 10. Expected Evidence
- Baseline metrics table with device/emulator specification.
- Trace/screenshots or profiler exports (labelled if unavailable).
- Listener/query inventory with read-cost estimates per journey.
- Retry/backoff policy table per integration.
- SLI/SLO proposal and current observability gap list.

## 11. Passing Conditions
- PASS requires: measured baselines within budget, no listener leaks, idempotent queue drain, bounded polling, crash/ANR reporting with SLOs, and a cost model.
- FAIL now: no performance measurement, no crash/ANR telemetry, minification disabled, no baseline profile, and no evidence that Firestore listener/read volume is bounded.
