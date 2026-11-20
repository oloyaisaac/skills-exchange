# Reliability, Scale and Performance — what is bounded, what is measured, what is not

**Owner:** Engineering lead · **Reviewer round:** R13 · **Findings:** `review_results/prompt2/REVIEW_13.md`

This document exists because review 13 asked the right kind of question — *what happens when the
network, the provider or the device misbehaves?* — and because the honest answer is a mix of "fixed",
"already fixed before R13" and "not measurable in this environment". Every claim below is tied to a
file, a test or a CI job. Where there is no measurement, it says so instead of implying one.

## 1. Findings against the tree at `94362a1`

| ID | Severity | What the review said | What the tree actually did | Outcome |
|---|---|---|---|---|
| P2-01 | HIGH | Queued offline actions are never flushed; the retry is a no-op | `flushPendingActions()` logged `"flushPendingActions: N pending"` and returned. The only producer, the SOS mirror retry, re-enqueued on every attach, so the queue could only grow | **Fixed** — `drainPendingActionQueue()` executes, retries to a cap and dead-letters |
| P2-02 | HIGH | No crash reporting, performance telemetry or SLOs | Nothing anywhere: no Crashlytics, no Performance, no APM, no SLO document, no alert policy | **Partial** — SLOs are specified and the crash-reporting dependency gap is written down; nothing is wired, because there is no Firebase project and no device in this environment (§7) |
| P2-03 | MEDIUM | An unfiltered listener streams every job to the device | `streamJobsFromFirestore()` listened to the whole `jobs` collection. It had **no caller** — dead code that the rules would have denied anyway — but it was one call away from being live. The one live listener (a job's chat) was unbounded | **Fixed** — the jobs listener is deleted; chat reads the last 200 messages, and `check_query_limits.mjs` fails on any client listener without a bound |
| P2-04 | MEDIUM | No Paging 3, so lists load whole result sets | Every list read is capped in the DAO (jobs 200, providers 500, quotes 200) and rendered by `LazyColumn`, which composes lazily | **Accepted with a threshold** — §6 records the size at which the cap stops being enough and Paging becomes required |
| P2-05 | MEDIUM | Cloud Functions declare no timeout, memory or region | All 43 callables and the two SMS triggers ran on platform defaults; the MoMo status call alone allowed 20 s, and a release pays out, writes a ledger line and marks the job in one invocation | **Fixed (timeout/memory)** — `functions/src/runtime.ts` declares two presets, applied to every callable and both SMS triggers; the region stays a deploy decision (§3) |
| P2-06 | MEDIUM | Release build not minified, no Baseline Profile, huge drawables | R8 and resource shrinking were already on (R07). Two committed sample JPEGs were 896 KB and 680 KB, plus a 371 KB icon — 80 % of `res/` | **Mostly fixed** — assets re-encoded to 68 / 41 / 16 KB (`res/` 2.0 MB → 181 KB), a budget guard added, and CI now builds the release variant. Baseline Profiles remain absent (§5) |
| P2-07 | LOW | Provider calls have no retry, backoff or circuit breaker | True: one shot per call, and a socket blip was reported to the user as "we don't know about your payment" | **Fixed for reads** — bounded jittered backoff; writes are deliberately not retried (§4) |

## 2. The offline action queue (P2-01)

The queue lives in Room (`pending_actions`) and is drained by
`SkillsExchangeRepository.drainPendingActionQueue()`. The contract, asserted by
`app/src/test/java/com/example/data/PendingActionDrainTest.kt`:

| Situation | Behaviour |
|---|---|
| Supported type, action succeeds | Row deleted, mirror flag set (`firestoreMirrored = true` for SOS) |
| Supported type, action throws | `retryCount` incremented, row stays `PENDING` |
| `retryCount` reaches `maxRetries` (3) | Row marked `FAILED`, payload left intact — **never deleted** |
| Unknown type | Counted `unsupported`, retried, dead-lettered at the cap with the payload still readable |
| Empty queue | `PendingDrainReport.didNothing`; no write, no notification |

`PendingDrainReport` (attempted, succeeded, retryScheduled, abandoned, unsupported, remaining) is
exposed as `lastDrainReport`, and the count as `pendingActionCount`, which the offline banner shows:
*"N update(s) waiting to sync"*. A user is told work is queued and told when it is still queued — the
banner appears while offline **or** while anything is pending.

Producer set today: only the SOS mirror retry enqueues. That is a small surface, and it is why the
queue is drained on attach rather than with a background worker — `WorkManager` would add a scheduler
for a queue that is normally empty. If a second producer is added, a worker becomes the right answer;
that is recorded as an open thread rather than assumed.

## 3. Function runtime (P2-05)

`functions/src/runtime.ts` defines two presets and nothing else:

| Preset | Timeout | Memory | Used by |
|---|---|---|---|
| `CALLABLE_RUNTIME` | 60 s | 256 MB | Callables that only touch Firestore — the platform default, written down |
| `PROVIDER_CALL` | 120 s | 512 MB | The nine callables that reach a third party: MoMo collection and payout, escrow release, material advance, wallet cashout, refund, OTP send, OTP verify and transcription — plus the two SMS triggers (`onJobCreated`, `onSosIncidentCreated`) |

33 callables use `callable()`, 9 use `providerCallable()`, and one callable that carries a secrets list
across several lines uses `withAppCheck({ secrets })` directly — the test accepts all three, and
requires the runtime to be present either way.

The point is not the numbers; it is that the choice is now a line in a diff that a reviewer can
challenge, instead of a default nobody looked at. `functions/test/appCheck.test.mjs` fails if a callable
site does not use one of the two helpers, and asserts both helpers carry `enforceAppCheck`,
`timeoutSeconds` and `memory`.

**Budget arithmetic.** `PROVIDER_CALL` is 120 s. A MoMo status read allows 15 s per attempt and at most
3 attempts, with ≤ 0.75 s of backoff between them: worst case ≈ 46 s, inside the timeout with room for
the token exchange and the writes that follow. `CALLABLE_RUNTIME` is the platform default, so no
handler that previously worked can time out sooner.

**Region is not set.** Region is a deploy decision: moving a function's region moves its Firestore
reads and its callable endpoint, and the client would need the same value. It is left to the deploy
runbook rather than guessed here, and this is a residual gap, not a fixed finding.

## 4. Provider retry policy (P2-07)

`functions/src/providerRetry.ts`:

- **Retried:** transport failures (`ECONNRESET`, `ECONNABORTED`, `ETIMEDOUT`, `EAI_AGAIN`,
  `ENOTFOUND`, socket hang-up), timeouts, HTTP 429 and 5xx.
- **Not retried:** any 4xx except 429 — a malformed request or wrong credentials stay wrong.
- **Not retried at all:** writes — collection, payout, refund, SMS. Those carry a provider reference and
  are already guarded by `already_pending`; repeating one risks a second transaction. The retry helper
  is only handed reads.
- **Shape:** at most 3 attempts, exponential backoff from 250 ms capped at 2 s, full jitter, and an
  `onRetry` hook so a retry is a log line rather than a silent delay.

Applied today to the MoMo status query (`queryMomoProviderStatus`). `functions/test/providerRetry.test.mjs`
covers the classification table, the attempt cap, that a non-retryable error fails on the first attempt
with no wait, that every retry is reported, and that backoff never returns zero or exceeds the ceiling.

**Circuit breaker: not implemented, deliberately.** A breaker needs shared state across instances
(Firestore or Memorystore) and a recovery policy an operator can act on; with no deployment, any
threshold would be invented. What exists instead is the bound that matters most for a mobile client: a
single provider call can no longer hang for the whole function timeout.

## 5. Client size and cold start (P2-06)

Measured on this tree, not estimated:

| Resource | Before | After |
|---|---|---|
| `img_before_leak.jpg` | 895 KB (1200×896) | **68 KB** (720×537, q72, progressive) |
| `img_after_repair.jpg` | 680 KB (1200×896) | **41 KB** (720×537) |
| `img_app_icon_1785778320921.jpg` | 371 KB (1024×1024) | **16 KB** (512×512) |
| `app/src/main/res/` total | ≈ 2.0 MB | **181 KB** across 24 files, largest 68 KB |

`scripts/check_asset_budget.mjs` enforces 150 KB per file and 1 MB for the tree, with a selftest that
proves both refusals fire. It runs in the `documentation` CI job.

R8 and resource shrinking were already enabled in R07; what was missing was any evidence that a release
build works. The new `release-build` job in `.github/workflows/android.yml` generates a throwaway
keystore, assembles `:app:assembleRelease`, and fails unless an APK and a non-empty R8 mapping file are
produced. It publishes the APK size and the uncompressed `res/` payload so the shrink is measured, not
asserted.

**Baseline Profiles: not added.** A Baseline Profile is generated on a device by the Macrobenchmark
library and consumed as a checked-in artefact; producing one requires an emulator or phone with the app
installed, which this environment does not have. Shipping a hand-written profile would be a guess
dressed as a measurement.

## 6. Scale: what is bounded, and where the bounds stop working

| Read | Bound | Why it is enough today | When it stops being enough |
|---|---|---|---|
| Jobs feed | `LIMIT 200`, newest first | Discovery shows recent work in the user's trades; a job older than the last 200 is reachable by id, not by scroll | A district with sustained volume where users scroll past 200 — then Paging 3 with a `PagingSource` over Room |
| Providers | `LIMIT 500` | The catalogue is district-scoped and nowhere near 500 approved providers | Catalogue past ~500 per district |
| Quotes per job | `LIMIT 200` | A job with 200 quotes is a moderation problem, not a rendering one | Never, realistically |
| Chat messages | `limitToLast(200)` | A live tail for a job in progress | If history browsing is required — then a paged reader, not a bigger tail |
| Firestore `jobs` listener | **Removed** | Nothing used it; a client cannot read the whole collection under the rules | If a live feed is ever wanted, it must be a query with `whereEqualTo(district)` and a limit |

Paging 3 (P2-04) is therefore **not** implemented, and that is a decision rather than an oversight:
`LazyColumn` already composes only visible rows, so the remaining cost of the caps is one Room read of
≤200 rows and the list itself. Introducing `PagingSource`, `RemoteMediator` and paging UI state would
add three moving parts and a new failure mode to every list for no measured gain at current volumes.
The row above is the trigger to revisit it.

## 7. Telemetry and SLOs (P2-02) — specified, not wired

Nothing in this repository reports a crash, an ANR or a frame time. There is no Crashlytics, no
Performance Monitoring, no analytics and no alerting. This is a **gap**, and it is the one finding in
R13 that is not closed.

Why it cannot be closed here: the SDK needs a Firebase project with Crashlytics enabled and an app
registered by package name and signing fingerprint; the data only exists once real devices run a real
build. Neither is available in this environment, and adding the dependency without the project would
produce a build that compiles and reports nothing — the exact "claim exceeding evidence" this programme
exists to avoid.

What is specified, so the work is a configuration task rather than a design task:

| SLI | Definition | Proposed SLO (28 days) | Where it would be measured |
|---|---|---|---|
| Cold start | Time to first frame on a mid-range Android device | p90 ≤ 2.5 s | Macrobenchmark `StartupTimingMetric` + Performance trace |
| Crash-free sessions | Sessions without a fatal exception | ≥ 99.5 % | Crashlytics (fatal + ANR) |
| ANR rate | ANRs per session | ≤ 0.5 % | Crashlytics ANR reporting |
| Callable latency | p95 duration of `PROVIDER_CALL` handlers | ≤ 8 s | Cloud Monitoring on Cloud Functions metrics |
| Callable error rate | 5xx + unhandled errors per callable | ≤ 1 % | Cloud Monitoring / Cloud Logging |
| Escrow reconciliation drift | Settled jobs whose ledger disagrees with the provider | 0 | `reconcilePayments` report (§8 of the roadmap) |
| Offline queue residue | Pending actions older than 24 h on a device that has been online | 0 | `pendingActionCount` / `lastDrainReport` once telemetry exists |

**Error budget policy (proposed):** a breach of the crash-free-session or callable-error SLO freezes
feature work on the affected surface until the cause is identified; a breach of the escrow-drift SLI
stops releases outright. Both are policy, not code, and neither is enforced by a machine today.

**What a machine does check today:** the drain contract (§2), the runtime presets and App Check
coverage (§3), the retry rule and its bound (§4), the asset budget and the release build (§5), and the
listener/query bounds (§6). Those are the reliability invariants that exist without any telemetry.

## 8. Not claimed

- No crash, ANR, startup or latency figure in this document is measured; the SLO table is a proposal.
- No deployment: none of the runtime presets, retry logic or listener changes has run in a Firebase
  project. Backend behaviour is verified by unit and handler tests only.
- The release APK built in CI is signed with a throwaway key generated inside the job. It proves R8 and
  shrinking work; it is not a distributable artefact.
- Baseline Profiles, a circuit breaker and regional pinning are absent, each with the reason above.
- Paging 3 is absent by decision, with the threshold that would reverse it.
