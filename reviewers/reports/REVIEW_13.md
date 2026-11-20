# REVIEW-13 — Performance, Reliability & Observability Review

| | |
|---|---|
| **Reviewer** | R13 — Performance, Reliability & Observability Reviewer (P1) |
| **Wave** | 3 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** (reliability requirements unmet); performance **UNVERIFIED** |

## 1. Scope and evidence

Startup/init path, coroutine lifetimes, listener/polling behaviour, offline handling, APK and asset
sizes, dependency list, and telemetry configuration. No profiling, no macrobenchmark, no device run —
every performance number is absent rather than estimated.

## 2. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R13-F1 | High | No crash reporting, analytics, performance monitoring or error aggregator: 0 dependencies for Crashlytics/Sentry/Firebase-Perf/Analytics. | Production failures are invisible; no field data to prioritise the defects in this report. |
| R13-F2 | High | `flushPendingActions()` (`VM:2196–2215`) takes a snapshot and `Log.d`s it; nothing is executed or deleted. `enqueueAction`/`markActionCompleted` exist but are never replayed; no WorkManager. | The offline queue grows forever and no action ever syncs (F-022, and the README now admits it). |
| R13-F3 | High | Africa's Talking calls launch `CoroutineScope(Dispatchers.IO).async` **per call** (`AfricaTalkingService.kt`), outside the ViewModel scope. | Unstructured concurrency: leaks, uncancellable work, lost exceptions on screen exit. |
| R13-F4 | Medium | Escrow/collection flows are polled (`pollTrustedMoMoCollectionStatus`, bounded attempts); no backoff/jitter and no push alternative (no FCM send exists). | Battery/data cost and worst-case latency on the money path. |
| R13-F5 | Medium | APK: 31 MB committed `_device_test/installed-base.apk`; 3 drawable JPEGs at 372 KB / 680 KB / 896 KB; `isMinifyEnabled = false`. | Large download for the target market, no shrinking/R8, slow cold install. |
| R13-F6 | Medium | 2,749-line ViewModel initialises many flows/listeners in `init`; no baseline profile or startup macrobenchmark. | Unknown cold-start cost; likely over-eager initialisation. |
| R13-F7 | Medium | Chat uses one Firestore snapshot listener per open `jobId`; correctly removed in `awaitClose` (positive), but no pagination on message history. | Long conversations load unbounded documents. |
| R13-F8 | Medium | No retry policy, circuit breaker, or provider timeout budget on money calls beyond OkHttp defaults; no dead-letter queue in functions. | A provider outage surfaces as generic failure with no recovery telemetry. |
| R13-F9 | Low | Room queries mostly unbounded (`SELECT *` with no `LIMIT` on list flows). | Memory growth with data volume. |
| R13-F10 | Info | Cashout transaction boundary and idempotency are sound; polling has bounded attempts; listener cleanup is implemented. | Positive. |

## 3. Reliability scenarios

| Scenario | Behaviour | Verdict |
|---|---|---|
| Offline action | Queued, never replayed (R13-F2) | FAIL |
| Reconnect | Snackbar only | FAIL |
| Process death | State lost (R02-F2) | FAIL |
| Provider timeout | Generic error, no retry budget | FAIL |
| App crash | No report generated | FAIL |
| Rotation | ViewModel state survives; `remember` state lost | PARTIAL |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Crash/error visibility | FAIL |
| Offline replay | FAIL |
| Structured coroutine lifetimes | FAIL (integrations) |
| Startup/perf budget with measurement | FAIL (no data) |
| Bounded memory/network on hot paths | PARTIAL |
| Health checks/alerting | FAIL |

## 5. Passing conditions

1. Crashlytics (or equivalent) + non-PII analytics with a documented event schema.
2. Offline queue drained by WorkManager with idempotency, backoff, and an observable sync state.
3. All network work inside lifecycle scopes; no ad-hoc `CoroutineScope`.
4. Baseline profile + startup/frame macrobenchmarks with budgets in CI.
5. Asset pipeline (WebP, downscale), R8 enabled, APK < 15 MB.
6. Pagination for messages; dead-letter + alerting for scheduled functions.

## 6. Residual risk

Without telemetry the team cannot know whether the app crashes in the field, and without replay the
offline story is marketing only. Performance remains genuinely unknown.

## Recommended remediation (not implemented)

1. Add crash/ANR/perf telemetry and a non-PII event schema.
2. Drain the offline queue with WorkManager, idempotency, backoff and observable state.
3. Move all network work into lifecycle scopes.
4. Add baseline profiles, startup/frame budgets in CI, R8 and an asset pipeline.

## 7. Cross-references

F-022; R02-F4, R05-F5, R09-F7, R11-F5, R14, R03-F4.
