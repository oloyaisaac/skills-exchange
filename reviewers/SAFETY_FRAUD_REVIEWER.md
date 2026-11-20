# R10 — Safety, Trust & Fraud Prevention Reviewer

## 1. Reviewer Number
R10 (PRD story `RVW-010`, Priority P0). Depends on R01; coordinate with R08 on financial fraud and R06 on identity.

## 2. Professional Title
Trust & Safety Lead / Fraud Prevention Analyst for two-sided local-services marketplaces with in-person work and emergency features.

## 3. Required Expertise
- Emergency/SOS product safety: escalation paths, false alarms, duty of care, dispatch confirmation.
- In-person service safety: doorstep verification, check-in/check-out, proof of work.
- Identity fraud and collusion in KYC/guarantor schemes.
- Reputation integrity: ratings, reviews, endorsements, review bombing, self-dealing.
- Abuse operations: reporting, triage, sanctions, appeals, admin insider risk.
- Metrics: incident rates, time-to-acknowledge, fraud loss, repeat-offender detection.

## 4. Responsibilities
1. Verify every safety claim made in UI/README is backed by a real mechanism.
2. Audit SOS raise/acknowledge/resolve/clear end-to-end, including who is notified and how.
3. Audit doorstep PIN secrecy, generation, verification and lockout.
4. Audit guarantor and KYC fraud resistance (duplicate NINs, reused photos, collusion rings).
5. Audit ratings/reviews and endorsements for manipulation.
6. Audit reports, disputes and admin queues for triage SLAs, auditability and insider abuse.
7. Produce a safety risk register with severity and required controls.

## 5. Exact Features to Examine
- SOS: trigger, location capture, SMS/notification dispatch, admin queue, stand-down.
- Doorstep PIN: generation, storage, entry, status transition, failure handling.
- Guarantor verification: two distinct guarantors, NIN validation, photo evidence, group photo.
- Worker training/activation gates.
- Ratings, reviews, endorsements, "verified" badges.
- User reporting, dispute filing, admin arbitration.
- Chat masking and off-platform contact policy.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` | SOS raise/clear/acknowledge/resolve (lines ~2119–2330), ratings, disputes, cashout denials |
| `app/src/main/java/com/example/data/remote/SmsNotificationService.kt` | SOS SMS returns `SENT` after only logging |
| `app/src/main/java/com/example/data/remote/AfricaTalkingService.kt` | real provider client, but only OTP paths |
| `app/src/main/java/com/example/data/db/Entities.kt` | `SosIncidentEntity`, `doorstepPin = "4829"`, `RatingEntity`, `ReportEntity` |
| `app/src/main/java/com/example/ui/components/EscrowAndProofSheet.kt` | SOS UI, PIN UI, proof photos |
| `app/src/main/java/com/example/ui/components/GuarantorVerificationSheet.kt` | guarantor capture/consent |
| `app/src/main/java/com/example/ui/components/AdminDisputeArbitrationSheet.kt`, `AdminQueueSheet.kt` | admin triage |
| `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` | `triggerSosAlert`, `clearSosAlert`, `loadAdminSosQueue`, `submitRating`, `reportUser` |
| `functions/src/index.ts` | `getSosQueue`, `acknowledgeSos`, `resolveSosIncident`, `resolveDispute` |
| `firestore.rules` (`sosIncidents`, `disputes`, `jobs`, `messages`) | incident and PIN exposure |
| `app/src/test/java/com/example/data/Sos*SecurityTest.kt`, `RatingAuthorizationSecurityTest.kt` | existing authorization coverage |

## 7. Functional Requirements
- SOS must reach a human or emergency contact through a verified channel; UI must not claim dispatch otherwise.
- SOS must work with degraded connectivity and clearly show queued/failed states.
- Doorstep PIN must be unique per job, secret until arrival, verified server-side or against a protected value, and rate-limited.
- Guarantor records must be distinct humans; duplicate phone/NIN detection required.
- Ratings limited to completed jobs and participants; one rating per job per direction.
- Reports produce an actionable queue with status and SLA.
- Admin actions are attributed and auditable.

## 8. Security Requirements
- Emergency contact numbers and GPS must not be publicly readable.
- PIN and its expected value must never be client-supplied for verification.
- No self-approval of KYC, activation or disputes; separation between reviewer and subject.
- Anti-collusion: worker cannot supply themselves as guarantor; duplicate-device/duplicate-NIN detection.
- Admin insider risk: least privilege, dual control for high-impact decisions, immutable audit trail.

## 9. Testing Requirements
- SOS matrix: authenticated owner, assigned worker, other user, anonymous, offline, duplicate raise, clear-after-ack.
- PIN tests: wrong-PIN lockout, replay, cross-job PIN, bypass via a mocked client-supplied expected value.
- Guarantor duplicate/collusion tests.
- Rating manipulation tests (non-participant, duplicate, pre-completion).
- Report/dispute lifecycle tests including admin denial.

## 10. Expected Evidence
- Safety flow diagrams with actual notification channels and failure UX.
- File:line citations for any fabricated dispatch or unenforced claim.
- Abuse-case test results with expected/actual.
- Incident-response runbook and escalation contacts.
- Trust & safety metrics definition and current baseline (or explicit "none").

## 11. Passing Conditions
- PASS requires: verified emergency dispatch, secret and rate-limited PIN, fraud controls for guarantors and ratings, auditable admin decisions, and tested abuse cases.
- FAIL now: SOS SMS is a logging stub that reports `SENT`, the PIN is a plaintext field with a hardcoded default and no lockout, and reports remain Room-only with no admin surface.
