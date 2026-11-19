# R13 — Performance, Scalability & Reliability Engineer

| Field | Value |
|---|---|
| Reviewer | R13 — Performance & Reliability (P0, Gate G4) |
| Assignment | `reviewers/PERFORMANCE_RELIABILITY_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Telemetry, offline replay, polling/listeners, memory, APK size, retries, SLOs |
| Verdict | **FAIL** |
| Issues | 7 (2 HIGH, 5 MEDIUM) |

## Areas examined
Startup/init path, coroutine lifetimes, listener and polling behaviour, offline queue, APK/assets,
dependency weight, retry/backoff, observability and SLOs.

## Source files inspected
`SkillsExchangeViewModel.kt` (init, `flushPendingActions` 2196–2215, polling), `FirebaseService.kt`
(chat listener 861–889), `AfricaTalkingService.kt`, `AppDatabase`/DAOs, `app/build.gradle.kts`,
`res/drawable/*`, `functions/src/index.ts`.

## Features examined
Cold start, job feed, chat, MoMo status polling, offline reconnect, image loading, crash handling,
scheduled functions.

## Tests performed
- Grep for crash/analytics/perf SDKs (0) and WorkManager (0).
- Counted listeners/pollers and inspected cancellation (`awaitClose`).
- Measured APK (31 MB) and drawables: 696,302 / 380,345 / 915,654 bytes.
- **Correction:** earlier figures 372/680/896 KB were stale; current measured sizes are above.
- No profiling, macrobenchmark or device run.

## Verified working functionality
- Chat snapshot listener removes itself in `awaitClose` (`FirebaseService.kt:884`).
- Cashout transaction boundary is sound; polling has bounded attempts for collection status.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R13-01 | HIGH | No crash, ANR or performance telemetry. | Observability | No Crashlytics/Sentry/Firebase-Perf/Analytics dependency | Field visibility + alerting | Production failures invisible | Add telemetry and SLO dashboards | Crash/ANR visible in dashboard |
| R13-02 | HIGH | Offline actions are queued but never replayed. | Offline | `flushPendingActions` logs only; no WorkManager; `enqueueAction` never replayed | Durable sync | Queue grows, nothing sent | WorkManager drain with idempotency | Airplane-mode E2E |
| R13-03 | MEDIUM | Money status relies on polling; no push. | Polling | `pollTrustedMoMoCollectionStatus` loop; no FCM send | Push-driven updates | Battery/latency cost | Implement FCM and backoff/jitter | Latency/battery measurement |
| R13-04 | MEDIUM | APK and drawable assets are oversized. | Packaging | 31 MB APK; JPEGs 696/380/916 KB; R8 off | <15 MB, WebP/downscaled | Large download/install | Enable R8, compress assets | APK size budget in CI |
| R13-05 | MEDIUM | Integration work uses unmanaged scopes. | Coroutines | `CoroutineScope(Dispatchers.IO).async` per AT call | Lifecycle-scoped work | Leaks/lost errors | Inject scopes | Cancellation test |
| R13-06 | MEDIUM | No retry budget, circuit breaker or SLOs. | Reliability | Provider calls rely on OkHttp defaults; no DLQ | Bounded retries + alerts | Outages surface as generic errors | Add backoff, breaker, DLQ, SLOs | Failure-injection test |
| R13-07 | MEDIUM | No retry budget, circuit breaker or SLOs. | Reliability | OkHttp defaults only; no DLQ or SLO definitions | Bounded retries with alerting | Outages surface as generic errors | Add backoff/breaker/DLQ and SLOs | Failure-injection test and alert firing |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R13-02, R13-03, R13-04, R13-05, R13-06.
- **Not implemented:** R13-01, R13-07.
- **Partially implemented:** none.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
Telemetry, offline replay, push, retry/backoff, SLOs, memory/pagination limits.

## Security concerns
No telemetry means security incidents (SOS/payment failures) are undetected operationally.

## Performance concerns
Unbounded list reads (R04) and duplicate chat storage compound memory/IO cost; no baseline exists, so
absolute performance is unknown.

## Recommendations
1. Add crash/ANR/perf telemetry and SLOs.
2. Implement WorkManager replay.
3. Compress assets and enable R8.
4. Add retry/breaker/DLQ.

## Limitations / unable to verify
No profiling, macrobenchmark, device or load test. Performance is **unable to verify**; only static
counts and file sizes are verified.

## Cross-references
R02, R03, R04, R05, R11, MR-059/MR-060/MR-061/MR-062/MR-052/MR-049/MR-063.
