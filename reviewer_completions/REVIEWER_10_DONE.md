# REVIEWER_10 — Safety, Trust & Fraud Prevention: DONE

I finished the work for this reviewer.

- Review: `review_results/prompt2/REVIEW_10.md` (verdict FAIL, 7 findings). Standard:
  `reviewers/SAFETY_FRAUD_REVIEWER.md`.
- Branch: `arena/a19ae51b-skills-exchange`, based on `main` @ `65cadfc`.
- The review was written against `aa4a0bc`; each finding was re-checked against the current code
  before anything was changed, and two were already closed by later rounds.

## Findings

| ID | Severity | Outcome against this code |
|---|---|---|
| R10-P2-01 | CRITICAL | **Partly stale, residual fixed.** The client-side PIN comparison the review quotes is gone (R07): `verifyDoorstepPin` sends only the typed PIN and the server compares it with its own hash, `attempts`, lockout and an expiry (`functions/src/doorstepPin.ts`). The `Entities.kt` default was removed in the same round. The residual — a shipped migration whose DDL gave `job_posts.doorstepPin` the literal default `'4829'` — is now closed: the DDL default is `''`, and `MIGRATION_28_29` blanks rows that carry that shared value (schema v29, applied to every install). |
| R10-P2-02 | CRITICAL | **Fixed.** Reports reach an operator. `functions/src/reports.ts` owns the record, the priority ladder, the triage state machine and the metrics; `fileReport` / `getReportsQueue` / `resolveReport` are admin- and auth-gated callables; `firestore.rules` makes `reports` read-only for the reporter and admin, write-never for clients. The app files through the callable and shows the server's own acknowledgement; the safety desk (`SafetyReportQueueSheet`) lists rows with the server-decided priority, target and breach flag and shows the server's sentence after a decision. |
| R10-P2-03 | HIGH | **Fixed.** The app never claims delivery before the server reports it. `functions/src/sosStatus.ts` composes the sentence from the recorded `dispatch`; `getSosDispatchStatus` returns it to the participants; the view model polls while it is pending and shows it in the SOS banner, which no longer says "broadcasted to local emergency contacts and platform security". Any unconfirmed state appends *"call the police or an ambulance yourself now"*, and the outcome is also written to the job chat. |
| R10-P2-04 | HIGH | **Fixed.** `functions/src/guarantorIntake.ts` fingerprints NINs under a salt, keys phones by their last nine digits and refuses `SELF_GUARANTOR`, `DUPLICATE_GUARANTOR`, `GUARANTOR_ALREADY_USED`, `WRONG_COUNT`, `NO_CONSENT`, `MISSING_EVIDENCE`, `INCOMPLETE` and `RESUBMISSION_LIMIT` (3 per 24h). Refusals write `guarantor_intake_audit` rows with codes and counts only. `firestore.rules` makes `guarantors` and `guarantor_submissions` server-only, so a client cannot bypass the checks. |
| R10-P2-05 | MEDIUM | **Fixed.** The fabricated `0.3476, 32.5825` is gone from the failure path: `requestGpsDetection` is now an honest "no fix" state (no coordinates, no district, `isUsingCurrentLocation = false`), `GpsLocationState` no longer defaults to claiming a position, `LocationHelper` reports an error instead of returning Kampala city-centre coordinates, and `updateWorkerLocation` requires a real fix (nullable, keeps the stored position otherwise). |
| R10-P2-06 | MEDIUM | **Fixed.** Ratings are server-owned: `functions/src/ratingsCore.ts` decides participation, completion and one-per-direction, `submitRating` writes `ratings/{jobId}_{raterUid}` and the `worker_stats` aggregate in one transaction, and the rules allow reading but never writing. The device stores a local copy only after the server accepts, and a refusal is surfaced as `RatingResult.NotRecorded(message)`. |
| R10-P2-07 | LOW | **Fixed (metrics and runbook; single-operator limitation stated, not solved).** `getSafetyMetrics` returns time-to-acknowledge, unanswered emergencies, failed dispatches, open/breaching/closed reports, first-response median and a per-category count, plus a one-line summary the console shows verbatim. `docs/safety/SAFETY_DESK_RUNBOOK.md` documents priorities, targets, states, escalation and the gaps. Dual control for suspensions/refunds/dismissals is **not** implemented — recorded as a known gap rather than dressed up. |

## Evidence

- CI (runs 44784548015 / 44784547851): **7/7 checks pass** — Compile and unit tests (3m37s), Backend
  build and tests, Backend handler tests, Firestore rules tests, Documentation checks, Secret scan,
  CodeRabbit.
- Backend `npm run build && npm test`: **310/310** `node --test` tests, all four new files
  registered in `functions/package.json` (guarantorIntake 12, reports 9, ratingsCore 9, sosStatus 7).
- Firestore rules: **26** emulator tests (2 replaced, 3 new): reports are reporter/admin-readable and
  client-unwritable, ratings readable by participants and unwritable, `worker_stats` read-only, and
  `guarantor_intake_audit` admin-only.
- Static guards: architecture guards, query limits, document map, doc links, traceability (30
  requirements), README claims, consent version, backup policy, secret scan and no-PII-logs all pass.
- Client: the Android job is green (478 unit tests; the four failures an earlier run reported —
  a nested-vs-top-level `ReportUiState` reference and a hand-written `job_posts` INSERT that missed a
  NOT NULL column — are fixed, the INSERT now built from the exported schema).
- Client: `SosRaiseAuthorizationSecurityTest` updated for `SosRaiseResult.Success(incidentId)`;
  `DatabaseMigrationChainTest` walks 11 → 29 and the new test proves `MIGRATION_28_29` clears the
  shared PIN while leaving a real one alone. Android compile/tests run in CI only (no JDK here).

## What is not claimed

- Nothing is deployed. No callable in this round has run against a real project.
- No live dispatch, SMS or FCM was executed — **UNVERIFIED**.
- No operator has ever triaged a report; the desk has no on-call rota, no paging provider and no
  second admin. Recorded in the runbook.
- The seeded demo provider catalogue still carries illustrative coordinates (San Francisco) for demo
  rows. They are not a device fix and never reach an SOS, but they are not real either; the
  device-side fabricated location is what this round removed.
- Chat off-platform-contact detection (review §6) had no finding ID and no code change was claimed
  for it; it remains unhandled and is not presented as done.
