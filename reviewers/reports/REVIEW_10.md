# REVIEW-10 — Safety, Trust & Fraud Review

| | |
|---|---|
| **Reviewer** | R10 — Safety, Trust & Fraud Reviewer (P0, Gate G3) |
| **Wave** | 1 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** — Gate G3 not met |

## 1. Scope and evidence

SOS raise/dispatch path, doorstep PIN, ratings and reviews, reports/disputes, guarantor verification,
admin arbitration, and abuse controls (rate limits, blocklists, audit).

## 2. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R10-F1 | Critical | `raiseSos` dispatches through `smsNotificationService.dispatchEmergencySosAlert` only when an authenticated **profile resolves**; otherwise it silently records an in-app flag. That service is a **log-only stub** returning `SENT` (`SmsNotificationService.kt`). No FCM send exists server-side. | The SOS button can report success while nobody is alerted. This is the highest-severity product risk in the app. |
| R10-F2 | Critical | Doorstep PIN defaults to `"4829"` (`Entities.kt`) and is displayed in plaintext to the worker as well as the resident (`EscrowAndProofSheet.kt` "Resident's Doorstep Security PIN: ${job.doorstepPin}"). | The physical-presence check is disclosed to the party it should authenticate and is guessable by default. |
| R10-F3 | High | `reportUser()` writes only to the Room `reportDao`; there are **no rules** for a `reports` collection. | Reports never reach a moderator; a blocked/abusive user is invisible to the platform. |
| R10-F4 | High | SOS/admin escalation depends on an admin custom claim that is never provisioned (R06-F3); the admin console unlock is a local boolean (R06-F4). | No one can receive or act on an SOS in the shipped configuration. |
| R10-F5 | High | Duplicate `sosIncidents` rule blocks (R07-F3) nullify the job-existence check. | SOS records can reference jobs that do not exist; incident forensics are unreliable. |
| R10-F6 | High | Deleting an account deletes disputes where the user is `reportedUserId` (R09-F5). | A bad actor can erase the complaint trail against them. |
| R10-F7 | Medium | No rate limit or cooldown on SOS creation; no duplicate-incident suppression. | Alert fatigue / abuse; no evidence of throttling in rules or functions. |
| R10-F8 | Medium | Guarantor validation rejects duplicate phone numbers within a submission (`GuarantorValidators:149–174`) but does not detect one NIN/phone guaranteeing many unrelated workers. | Collusive guarantor rings are undetected. |
| R10-F9 | Medium | `validateNationalIdNumber` is format-only; no checksum, no registry check, no liveness for photos. | Identity verification is self-asserted plus admin eyeballing. |
| R10-F10 | Medium | No audit log for admin decisions (who approved whom, when, with what evidence). | Insider abuse and appeals are untraceable. |
| R10-F11 | Info | `submitJobRating` enforces owner-only, released-only, assigned-provider target and duplicate prevention (`repository` security boundary, tests exist). `GuarantorValidators` phone normalisation is sound. | Positive — do not re-flag. |

## 3. Abuse-case review

| Abuse case | Control | Verdict |
|---|---|---|
| Fake SOS | None | FAIL |
| PIN sharing / remote "verification" | PIN shown to worker | FAIL |
| Serial bad worker, re-registration | Phone dedup only, no device/identity ban | PARTIAL |
| Report suppression | Reports stay local; deletable disputes | FAIL |
| Review bombing | One rating per (from,to,job), owner-only | PASS (static) |
| Guarantor collusion | Within-submission dedup only | PARTIAL |
| Admin insider action | No audit trail | FAIL |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Emergency alert reaches a human out-of-band | FAIL |
| Presence proof is secret from the worker | FAIL |
| Abuse reports reach moderation | FAIL |
| Dispute evidence is tamper-evident | FAIL |
| Ratings are authorized and deduplicated | PASS (static) |
| Admin actions audited | FAIL |

## 5. Passing conditions

1. SOS is a server-side callable that writes the incident, sends FCM to on-call, sends SMS via a real
   provider, and returns the channels actually used; failure is surfaced to the user.
2. PIN is server-generated per job, shown only to the resident, verified server-side, rate-limited.
3. Reports collection with participant-bound create and admin-only read; moderator queue reachable.
4. Disputes are immutable except by admin; deletion preserves evidence.
5. Audit log for all admin decisions.
6. Cross-worker guarantor reuse detection and abuse throttles.

## 6. Residual risk

A safety feature that lies about success is worse than no feature: users will press SOS and stop
seeking help. Treat R10-F1 as release-blocking on its own.

## Recommended remediation (not implemented)

1. Make SOS a server callable that writes the incident and reports which channels actually succeeded.
2. Generate the PIN per job, show it only to the resident, verify server-side, rate-limit attempts.
3. Persist reports to an admin-only `reports` collection and build the moderator queue.
4. Audit every admin decision and preserve disputes immutably.

## 7. Cross-references

F-004, F-005, F-008; R07-F3/F5/F9, R09-F5, R06-F3, R03-F4; G3.
