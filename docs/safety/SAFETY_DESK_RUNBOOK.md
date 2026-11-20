# Safety desk — triage, SLA and escalation runbook

**Owner:** Trust & Safety lead · **Status:** implemented in code, **never operated** · **Last reviewed:** 2026-11-20

This is the operating procedure for the safety desk the code now supports. It is written from the
implementation, not from an intention: every number below is a constant in
`functions/src/reports.ts` or `functions/src/sosStatus.ts`, and every queue below is a callable that
exists in `functions/src/index.ts`.

> **Read this before relying on it.** No part of this runbook has been executed in production. There is
> no on-call rota, no paging provider and no second operator. The platform records what happened; who
> answers is an organisational commitment this repository cannot make.

## 1. What reaches the desk

| Source | Record | Written by | Who can see it |
|---|---|---|---|
| Emergency alert (SOS) | `sosIncidents/{id}` + `dispatch` | `onSosIncidentCreated` (Firestore trigger) | participants, admins (`getSosQueue`) |
| Report about a person | `reports/{id}` | `fileReport` callable | reporter, admins |
| Guarantor intake refusal | `guarantor_intake_audit/{id}` | `submitGuarantorVerification` callable | admins |
| Dispute over money | `disputes/{id}` | client create (authorship bound) + `resolveDispute` | participants, admins |

A `SAFETY` report is also paged: `fileReport` writes a `notifications/safety_desk` document so a P0
report is visible on the same channel an SOS uses. That document is a hand-off signal, not a delivery
guarantee — nothing in this repository proves a human saw it.

## 2. Priority and response targets

Priority is decided on the server from the category and the number of open reports against the same
account (`reportPriority`). The first-response target follows from the category
(`FIRST_RESPONSE_HOURS`):

| Category | Priority | Target |
|---|---|---|
| `SAFETY` | P0 always | 1 hour |
| `FRAUD`, `HARASSMENT` | P1, escalates to P0 at ≥1 existing open report | 4 hours |
| `NO_SHOW`, `PAYMENT_ABUSE` | P2, escalates to P1 at ≥2 open reports | 24 hours |
| `OTHER` | P2, escalates to P1 at ≥2 open reports | 48 hours |

`slaDueAt = createdAt + target`, and `getReportsQueue` marks a row `breachingSla` when the deadline has
passed and the report is still `OPEN`. The app never computes a breach itself.

A reporter cannot hold more than `MAX_OPEN_REPORTS_PER_SUBJECT` (5) open reports against one account;
the sixth is refused with a sentence that tells them to contact support instead.

## 3. Triage states

`OPEN → TRIAGING → ACTIONED | DISMISSED`, forward only (`decideTriage`):

- `resolveReport` refuses `OPEN` as a decision and refuses to reopen a closed report. A new report is
  filed rather than an old one revived.
- `ACTIONED` and `DISMISSED` require an `outcome` of at least 5 characters — what an auditor reads.
  The check exists in the callable *and* in the client button state, so the refusal is visible before
  the tap.
- Every decision records `triagedBy`, `triagedAt` and appends `adminNotes`; the record is written by
  the server and cannot be rewritten by a client (`firestore.rules` allows no writes to `reports`).
- The console shows the sentence `triageMessage()` composes, including whether the first response was
  **within** or **past** the category target. If that sentence is missing, the console says only what
  the status is.

## 4. Emergency alerts

1. The device raises the alert; the app records `sosIncidents` and mirrors it to Firestore.
2. `onSosIncidentCreated` sends the SMS dispatch to `SOS_DISPATCH_NUMBERS` and the owner's
   `emergencyContactPhone`, then writes `dispatch.status` (`SENT` / `FAILED` / `NO_RECIPIENTS`) with
   counts, and pushes to the `sos_dispatch` topic.
3. The app asks `getSosDispatchStatus`, which returns the sentence from `describeSosDispatch()`:
   - `SENT` — names how many recipients were reached;
   - `NO_RECIPIENTS`, `FAILED`, `PENDING`, `STALE`, `UNKNOWN` — says plainly that delivery was **not**
     confirmed and appends *"call the police or an ambulance yourself now."*
4. The outcome is recorded in the job's chat as a system line so it survives the screen.

**If the mirror write fails, the trigger never runs.** The alert exists on the device and in Room; the
platform never sees it. No operator action can recover that. This is a known limitation, not a
behaviour to rely on: treat "no incident in `sosIncidents`" as *unknown*, never as *nothing happened*.

Operators acknowledge with `acknowledgeSos` and close with `resolveSosIncident`; both record the actor
uid and the timestamp. `getSosQueue` lists the open ones.

## 5. Guarantor fraud signals

`submitGuarantorVerification` computes fingerprints server-side and refuses or flags before writing:

| Code | Meaning |
|---|---|
| `WRONG_COUNT` | not exactly two guarantors |
| `SELF_GUARANTOR` | the worker is one of their own guarantors |
| `DUPLICATE_GUARANTOR` | the same person twice in one submission |
| `GUARANTOR_ALREADY_USED` | the NIN or phone already backs another submission |
| `NO_CONSENT` / `MISSING_EVIDENCE` / `INCOMPLETE` | a grant, a photograph or a field is missing |
| `RESUBMISSION_LIMIT` | more than 3 submissions in 24 hours |

Flags (`SAME_NAME_AND_DISTRICT`, `RESUBMISSION`) are review signals, not refusals. Each refusal writes
a `guarantor_intake_audit` row with the codes and counts — never the name or the NIN — so a pattern
across accounts can be read later without keeping a second copy of the identity.

## 6. Metrics

`getSafetyMetrics` returns, for a window (default 30 days, maximum 365):

| Field | Reading |
|---|---|
| `sosRaised` | emergencies raised in the window |
| `sosAcknowledged`, `medianAckSeconds` | how fast an operator answered, over acknowledged incidents only |
| `sosUnacknowledged`, `oldestUnacknowledgedSeconds` | emergencies nobody answered — **the number that matters most** |
| `sosDispatchFailed` | SMS that never reached anyone (FAILED or NO_RECIPIENTS) |
| `reportsOpen`, `reportsBreachingSla`, `reportsClosed` | report backlog against the targets above |
| `medianReportFirstResponseSeconds` | time to first response, over closed reports only |
| `byCategory` | where reports come from |

The callable also returns a one-line `summary` (the same string it logs) and the console displays it
verbatim. When the call fails the console says the numbers are **unavailable** and that the queue may
be incomplete — it never shows zeros it did not receive.

## 7. Escalation

1. **P0 (`SAFETY`, or an escalated repeat):** acknowledge the alert in `getSosQueue` if it is an SOS;
   otherwise triage the report, contact the reporter through the app, and record the outcome. If the
   dispatch state is anything other than `SENT`, tell the person to call emergency services directly —
   the app already says so; repeating it is not redundant.
2. **A report about a worker with open reports already:** the priority has already escalated; check
   `guarantor_intake_audit` and `worker_stats` for the same fingerprint pattern before deciding.
3. **Money involved (fraud, payment abuse):** the report is a safety record, not a refund. A refund or
   reversal goes through `resolveDispute` / the refund path, which has its own evidence trail.

## 8. Known gaps — read before promising a service level

- **Single operator.** Every decision is attributed to one admin uid. There is no dual control for a
  suspension, a refund or a dismissal. Nothing in the code prevents one admin from closing every
  report; the only safeguard is the audit trail.
- **No on-call, no paging provider.** `notifications/safety_desk` and the `sos_dispatch` topic are
  Firestore/FCM surfaces; whether a human is woken by them is an operational fact, not a code fact.
- **No operator UI for SOS acknowledgement in the app.** `acknowledgeSos` and `resolveSosIncident`
  exist as callables and are exercised by tests; the safety-desk sheet in this repository covers
  reports only.
- **`reports` reads are limited to 50 rows per call** and `getSafetyMetrics` to 500 documents per
  collection. Beyond that the numbers are a recent sample, not a census.
- **No decisions have been executed against a real project.** The callables have never run outside
  tests.

## 9. Where the code lives

| Concern | File |
|---|---|
| Report shape, priority ladder, triage state machine, metrics, operator sentence | `functions/src/reports.ts` |
| Guarantor intake review (duplicates, velocity, consent) | `functions/src/guarantorIntake.ts` |
| Dispatch description and chat line | `functions/src/sosStatus.ts` |
| Rating and reputation aggregate | `functions/src/ratingsCore.ts` |
| Callables | `functions/src/index.ts` |
| Rules (reports, ratings, worker_stats, intake audit, server-only guarantors) | `firestore.rules` |
| Operator console | `app/src/main/java/com/example/ui/components/SafetyReportQueueSheet.kt` |
| Reporter-facing outcome | `app/src/main/java/com/example/data/remote/SafetyOutcomes.kt` |
