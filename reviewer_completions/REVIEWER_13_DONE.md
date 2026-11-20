# REVIEWER_13 — Performance, Scalability & Reliability: DONE

I finished the work for this reviewer.

- Review: `review_results/prompt2/REVIEW_13.md` (verdict FAIL, 7 findings). Standard:
  `reviewers/PERFORMANCE_RELIABILITY_REVIEWER.md`.
- Branch: `arena/b2e695fc-skills-exchange`, based on `main` @ `94362a1`.
- The review could not deploy, install or profile anything, so its findings were re-checked against the
  code first. One was **stale** (R8 was already on), one was **worse than reported** (the queue could
  only grow), four are **fixed**, one is **accepted with a stated threshold**, and one is **partial**
  because telemetry cannot be wired in this environment.

## Findings

| ID | Severity | Outcome against this code |
|---|---|---|
| R13-P2-01 | HIGH | **Fixed.** `flushPendingActions()` snapshotted the queue, logged `"N pending"` and returned — nothing was ever executed or deleted, and the SOS mirror producer re-enqueued on every attach, so the queue could only grow. `SkillsExchangeRepository.drainPendingActionQueue()` now executes the supported action once, increments `retryCount` on failure, marks `FAILED` at `maxRetries` (3) with the payload intact, retires an unknown action type at the same cap rather than deleting it, and returns a `PendingDrainReport` (attempted / succeeded / retryScheduled / abandoned / unsupported / remaining). `pendingActionCount` is shown in the offline banner (*"N update(s) waiting to sync"*), so a user is told work is queued **and** told when it is still queued. `PendingActionDrainTest` drives all five paths in Robolectric. `REM-060` is `IN_PROGRESS`: no WorkManager, no idempotency key, no airplane-mode E2E. |
| R13-P2-02 | HIGH | **Partial — specified, not wired.** The finding is correct: there is no Crashlytics, no Performance Monitoring, no analytics and no alert anywhere. It cannot be closed here — the SDK needs a Firebase project registered by package name and signing fingerprint, and the data needs real devices. `docs/ops/RELIABILITY_AND_PERFORMANCE.md` §7 records the seven SLIs, proposed 28-day SLOs, the measurement each would need and an error-budget policy, so what remains is configuration. **No crash, ANR, startup time or latency figure is claimed anywhere in this round.** `REM-059` stays `IN_PROGRESS`. |
| R13-P2-03 | MEDIUM | **Fixed.** `streamJobsFromFirestore()` listened to the entire `jobs` collection. It had no caller — dead code the rules would have denied anyway — but it was one call away from live. It is deleted. The one live listener, a job's chat, streamed the whole history and is now bounded to the last 200 messages. `check_query_limits.mjs` now also fails on any client `addSnapshotListener` without a `limit( / limitToLast(` in the preceding 600 characters, and was proved to refuse an unbounded one. |
| R13-P2-04 | MEDIUM | **Accepted with a threshold.** Paging 3 is not adopted. Every list read is capped in the DAO (jobs 200, providers 500, quotes 200) and rendered by `LazyColumn`, which composes only visible rows, so the residual cost is one Room read of ≤200 rows and the list itself. §6 records the specific size at which the cap stops being enough and Paging becomes required (a district whose discovery scroll passes 200 jobs; a provider catalogue past ~500). That is a decision with a trigger, not silence. |
| R13-P2-05 | MEDIUM | **Fixed for timeout and memory.** All 43 callables and the two SMS triggers ran on platform defaults. `functions/src/runtime.ts` declares two presets — `CALLABLE_RUNTIME` (60 s / 256 MB: the default, written down) and `PROVIDER_CALL` (120 s / 512 MB) — and 9 callables that reach a third party plus the two SMS triggers use the second. `functions/test/appCheck.test.mjs` fails if a callable site uses neither helper. The arithmetic is stated: 3 attempts × 15 s plus ≤ 0.75 s of backoff ≈ 46 s inside 120 s. **Region is not pinned** — that is a deploy decision recorded as a residual gap. |
| R13-P2-06 | MEDIUM | **Half stale, half fixed.** R8 and resource shrinking were already enabled in R07, contrary to the review. The assets were real: 895 KB + 680 KB + 371 KB sample JPEGs, ~80 % of `res/`. They are now 68 KB, 41 KB and 16 KB (`res/` 2.0 MB → **181 KB** across 24 files, largest 68 KB). `scripts/check_asset_budget.mjs` enforces 150 KB per file and 1 MB for the tree, with an `--selftest` proving both refusals. A new `release-build` CI job generates a throwaway keystore, assembles `:app:assembleRelease`, and fails unless an APK and a non-empty R8 mapping exist, publishing both sizes. **No Baseline Profile** — it is produced on a device by Macrobenchmark. `REM-062` is `IN_PROGRESS`. |
| R13-P2-07 | LOW | **Fixed for reads.** `functions/src/providerRetry.ts` retries idempotent provider reads on transport errors, timeouts, 429 and 5xx, with exponential backoff from 250 ms capped at 2 s and full jitter, at most 3 attempts, and an `onRetry` log. Writes are **never** retried: collection, payout, refund and SMS carry a provider reference and are already guarded by `already_pending`, so a blind repeat risks a second transaction. Applied to the MoMo status query, with the per-attempt timeout lowered from 20 s to 15 s so the worst case fits the runtime. **No circuit breaker**, deliberately: it needs shared state and an operator policy that only a deployment can define. |

## Evidence

- Backend, locally: `npm run build` (tsc) clean, `npm test` **318/318** across 40+ suites — the 311
  existing plus the 7 retry tests; `functions/test/appCheck.test.mjs` gained a test that both callable
  helpers carry App Check, a timeout and memory, and that the provider preset is the longer one.
- Guards, locally: all 12 existing static guards pass, plus `scripts/check_asset_budget.mjs`
  (`181 KB total, largest 68 KB`) with its selftest, and the extended `check_query_limits.mjs`
  (`53 list queries all carry LIMIT; 1 client listener(s) all bounded`). The listener rule was proved
  able to fail by running it against a fixture with an unbounded listener (exit 1).
- Android CI, run **44799076234**: `Compile and unit tests` green with **494/494** unit tests (489
  before this round, plus the 5 drain tests). The suite failed twice before it passed, and both
  failures were real: a `public property exposes its internal type argument` error that only a release
  compile surfaces, then three drain tests inserting a job whose `assignedProviderId` named no
  `service_providers` row — a real foreign key. CI compiled and caught both; this environment has no JDK
  and cannot.
- Release CI, run **44799912835**: `Release build (R8 + shrink)` green — APK **6.3 MB** (6,562,525
  bytes), R8 mapping 679,287 lines, 599.8 KB of uncompressed `res/`. Under REM-062's 15 MB criterion,
  measured rather than asserted. The job publishes to `ci-metrics` because artefacts are unreachable here.
- CI run **44799913154**: all 7 jobs green — Backend build and tests (318/318), Backend handler tests,
  Firestore rules tests, Documentation checks, Secret scan, Compile and unit tests, Release build.
- Documentation: 30 documents and 41 requirements (`REQ-PERF-01`…`05` added); 13 static guards (4 with
  `--selftest`).

## Not claimed

- **No deployment.** No runtime preset, retry rule or listener change has run in a Firebase project;
  the backend evidence is unit and handler tests.
- **No device.** No crash, ANR, startup, frame-time or battery figure exists, and no Baseline Profile —
  the SLO table is a proposal, not a measurement.
- **The release APK built in CI is not distributable**: its key is generated inside the job and thrown
  away. It proves R8 and shrinking work, nothing about release quality.
- **Paging 3 remains absent by decision**, with the threshold that would reverse it in §6.
