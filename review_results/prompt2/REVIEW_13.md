# R13 — Performance, Scalability & Reliability Engineer

| Field | Value |
|---|---|
| Reviewer | R13 — Performance & Reliability (P1) |
| Assignment | `reviewers/PERFORMANCE_RELIABILITY_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no code or build config changed |
| Measurement | **UNVERIFIED** — no device, emulator, profiler or Firebase project |
| Verdict | **FAIL** |

## 1. Baselines and observability

- No Macrobenchmark/Baseline Profile module, no startup or jank traces, no memory measurements.
- No Crashlytics, Performance Monitoring, ANR reporting or structured logging SDK
  (grep in Gradle files = 0). Production failures are invisible except through Cloud Functions logs
  (`console.*`), which have no retention or alerting configuration in the repository.
- No SLI/SLO definitions, error budgets or alert policies.
- Release build is not minified (`isMinifyEnabled = false`), so APK size and startup pay for unused code.
  Repository contains two large sample JPEGs (≈896 KB and ≈680 KB) in `res/drawable`, and a historical
  installed APK of ~32 MB.

R13's passing condition requires measured baselines and crash/ANR telemetry; neither exists.

## 2. Offline queue is inert (reliability)

`flushPendingActions()` (`ViewModel.kt:2237–2257`) takes a snapshot and **logs it**:

```
// STEP 0 intentionally performs no execution and no deletion.
```

Actions queued while offline are therefore never executed or deleted; they accumulate forever. There
is a single-flight mutex (good) and a `retryCount` column, but no drain, no retry cap in a loop, no
backoff and no user-visible queued count. This is a direct failure of "offline queue drains once, in
order, with a retry cap".

## 3. Firestore listeners and read amplification

| Listener | Scope | Assessment |
|---|---|---|
| Jobs (`FirebaseService.streamJobsFromFirestore`, `:646`) | **entire `jobs` collection** | no `whereEqualTo`; rules require a participant-filtered query, so this listener is denied and logs an error. It currently has **no call site** (dead), but re-enabling it would either fail or force an unfiltered read |
| Chat (`:929`) | per-job subcollection with `orderBy(timestamp)` | participant-scoped; fine, needs an index for order+filter |
| Others (2) | spot-checked, scoped | no leak observed |

Room reads are capped (REM-049) at provisional limits (200/500), but there is **no Paging 3**
(`PagingSource|LazyPagingItems` = 0), so large job/chat/notification lists still load whole capped sets
into memory.

## 4. Polling and timeouts

- MoMo status polling is bounded: 8 attempts × interval, stops on non-PENDING and never fabricates
  success (`ViewModel.kt:1655–1678`) — good.
- Gemini audio payloads are capped server-side (`MAX_AUDIO_BASE64_LENGTH`) and mime type validated —
  good.
- Cloud Functions declare no `timeoutSeconds`, memory, concurrency or region; they inherit defaults
  (60 s / 256 MB / us-central1). Provider calls use 15–20 s axios timeouts, so a slow refund plus token
  exchange can approach the default limit.
- No retry/backoff or circuit breaker on any provider call; a provider outage surfaces as an error but
  causes repeated user-initiated attempts (no client-side cool-down).

## 5. Startup path

- `MainActivity` gates on a session spinner while `restoreSession()` reads DataStore and FirebaseAuth;
  no blocking disk I/O on the main thread was found in the inspected composition.
- The ViewModel constructs two `FirebaseService` instances and a Room database at creation (R01); this
  duplicates Firebase initialization work and listener registration.
- `LaunchedEffect(Unit)` requests location on first composition after permission is present; no
  debounce, but only once per composition.

## 6. Cost and scale model

No cost model exists. Observations that would drive one:

- A booking confirmation writes chat messages, notifications and job updates as separate documents;
  each listener fan-out multiplies reads.
- The `jobs` collection is unbounded and, if ever listened to without a filter, would read the whole
  marketplace.
- FCM is topic-based only for SOS; all other "notifications" are Firestore reads.
- SMS radius dispatch (`onJobCreated`) does one provider call per matching worker; no batching or cap
  beyond the query limit.

## 7. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R13-P2-01 | HIGH | Offline queue never executes; pending actions accumulate forever | `ViewModel.kt:2237–2257` |
| R13-P2-02 | HIGH | No performance baselines or crash/ANR/observability telemetry | Gradle grep = 0; no SLOs |
| R13-P2-03 | MEDIUM | Full-collection jobs listener (dead but rules-denied and unbounded by design) | `FirebaseService.kt:646–668` |
| R13-P2-04 | MEDIUM | No Paging 3; capped-but-whole lists loaded into memory | grep = 0; Room limits |
| R13-P2-05 | MEDIUM | No function timeout/memory/region config; provider calls near default 60 s | `functions/src/index.ts` |
| R13-P2-06 | MEDIUM | Release not minified; no Baseline Profile; heavy drawables in repo | `app/build.gradle.kts:45` |
| R13-P2-07 | LOW | No retry/backoff or circuit breaker on provider calls | provider helpers |

## 8. Verdict

**FAIL.** Bounded MoMo polling, server-side audio caps and rate limits are good, but the offline queue
is a no-op, there is no observability or performance baseline, release builds are unminified, and the
only jobs listener is an unbounded design that rules would deny. All performance claims are
**UNVERIFIED**.
