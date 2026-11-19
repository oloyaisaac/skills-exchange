# R10 — Safety, Trust & Fraud Prevention Reviewer

| Field | Value |
|---|---|
| Reviewer | R10 — Safety, Trust & Fraud (P0, Gate G3) |
| Assignment | `reviewers/SAFETY_FRAUD_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | SOS, doorstep PIN, reports/disputes, verification, admin arbitration, abuse controls |
| Verdict | **FAIL** — G3 not met |
| Issues | 8 (2 CRITICAL, 3 HIGH, 3 MEDIUM) |

## Areas examined
SOS raise and dispatch, alert delivery, PIN generation/verification/display, abuse reporting, dispute
lifecycle, guarantor verification, admin arbitration, ratings, throttling and audit.

## Source files inspected
`SkillsExchangeRepository.kt` (`raiseSos` ~2163, `reportUser` 2705, rating boundary ~2389),
`SmsNotificationService.kt` (dispatch 60+), `FirebaseService.kt`, `GuarantorValidators.kt`,
`EscrowAndProofSheet.kt` (PIN 641/649), `Entities.kt` (PIN 259), `firestore.rules` (SOS/reports),
`MainScreen.kt` (admin sheets), `AdminDisputeDecisions.kt`.

## Features examined
SOS button and incident record, emergency dispatch, worker presence PIN, user reporting, disputes,
guarantor submission/review, admin queue/arbitration, job ratings.

## Tests performed
- Traced SOS from UI to dispatch and read the SMS service body.
- Checked FCM send in functions (0) and rules for `sosIncidents`/`reports`.
- Read rating authorization/dedup boundary and existing tests.
- No alert was sent; no device or on-call system available.

## Verified working functionality
- `submitJobRating` enforces owner-only, released-only, assigned-provider target and duplicate prevention.
- Guarantor validation rejects duplicate phone numbers within a submission and normalises input.
- SOS incident records are authorization-controlled in rules.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R10-01 | CRITICAL | SOS dispatch is a logging stub that reports success. | SMS service | `dispatchEmergencySosAlert` (`SmsNotificationService.kt:60+`) logs, delays and returns `SENT`; no SMS call | Real delivery with receipts | Nobody is alerted; UI reports success | Implement provider send; return per-channel outcome | Real alert received + delivery receipt |
| R10-02 | CRITICAL | No server push/escalation path exists. | Functions | 0 `admin.messaging`; `raiseSos` dispatch only when a profile resolves | FCM to on-call + fallback SMS | Silent failure if profile missing, no push | Server callable writes incident and fans out | On-call device receives SOS |
| R10-03 | HIGH | PIN is disclosed to the worker and defaults to a fixed value. | Escrow sheet/data | `EscrowAndProofSheet.kt:641,649`; default `"4829"` (`Entities.kt:259`); no attempt limit | Resident-only, random, rate-limited | Worker can read/guess the PIN | Server-generated per job; blind entry; throttle | PIN attempt/limit test |
| R10-04 | HIGH | Abuse reports never leave the device. | Reporting | `reportUser` writes Room only (`repository:2705–2707`); no `reports` rules | Reports reach moderators | No moderator visibility | Persist to admin-only collection | Report → moderator queue E2E |
| R10-05 | HIGH | Account deletion erases complaints against the user. | Disputes | `FirebaseService.kt:295–299` | Evidence preserved | Complaints disappear with the account | Retain/anonymise disputes | Deletion preserves complaint |
| R10-06 | MEDIUM | No SOS throttling, dedup or admin audit trail. | Safety ops | No rate limit on SOS create; no audit log for admin decisions | Throttled, audited actions | Alert fatigue; insider actions untraceable | Add throttles and immutable audit log | Abuse + audit tests |
| R10-07 | MEDIUM | Guarantor/identity controls are weak against collusion. | Verification | Reuse across workers not detected; NIN format-only; no liveness/registry check | Cross-worker reuse detection, stronger identity proof | Collusive rings pass | Add reuse detection and stronger KYC | Synthetic collusion test |
| R10-08 | MEDIUM | Admin/SOS escalation is unreachable because no admin can hold the claim and the UI gate is client-side. | Admin queue/escalation | 0 `setCustomUserClaims`; `isAdminUnlocked = true` (`MainScreen.kt:832`) | On-call admin receives and acts on SOS | No provisionable admin; unlock is cosmetic | Provision claims via audited path; server-gate UI; add on-call routing | Admin login receives SOS end-to-end |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R10-01, R10-03, R10-05, R10-08.
- **Not implemented:** R10-02, R10-04, R10-06.
- **Partially implemented:** R10-07.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
Real emergency dispatch, on-call escalation, moderator queue, PIN hardening, audit log, abuse throttles.

## Security concerns
The SOS path is the most dangerous: it tells a user help is coming when nothing was sent. Admin
arbitration is unreachable because the claim is unprovisionable (R06).

## Performance concerns
SOS dispatch runs inline with the request; slow providers could block the UI (no timeout budget).

## Recommendations
1. Make SOS a server callable with real channels and an explicit per-channel result.
2. Generate PINs server-side, show only to the resident, rate-limit attempts.
3. Persist reports and preserve disputes.
4. Add audit logging and throttles.

## Limitations / unable to verify
No SMS provider, FCM delivery, on-call rotation, device or emergency drill. Safety claims are
**unable to verify** in operation; the stub behaviour is verified by source.

## Cross-references
R05, R06, R07, R09, MR-034/MR-035/MR-036/MR-018/MR-037/MR-038/MR-039/MR-040/MR-023.
