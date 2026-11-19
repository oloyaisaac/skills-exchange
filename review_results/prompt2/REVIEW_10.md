# R10 — Safety, Trust & Fraud Prevention Reviewer

| Field | Value |
|---|---|
| Reviewer | R10 — Safety & Fraud (P0) |
| Assignment | `reviewers/SAFETY_FRAUD_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no safety code changed |
| Live dispatch | **UNVERIFIED** — no SMS/FCM credentials or device |
| Verdict | **FAIL** |

## 1. SOS — raise, dispatch, acknowledge, clear

| Step | Implementation | Assessment |
|---|---|---|
| Raise | `ViewModel.triggerSosAlert` → `Repository.raiseSos`: owner/provider authorization, Room incident + best-effort Firestore mirror (`Repository.kt:2085–2135`) | Authorization is sound; no in-flight guard (R02) |
| Client feedback | Immediate system chat message and notification: "Live GPS location & job telemetry broadcasted to local emergency contacts and platform security" (`Repository.kt:2098–2110`) | **Claims dispatch before any provider result** |
| Server dispatch | `onSosIncidentCreated` sends SMS to `SOS_DISPATCH_NUMBERS` + the owner's `emergencyContactPhone`, records `dispatch.status` (SENT/FAILED/NO_RECIPIENTS), then FCM to the `sos_dispatch` topic (`index.ts:948–990`) | Real dispatch mechanism, but it processes the **Firestore mirror**; if the mirror write fails the trigger never runs |
| Fallback location | `0.3476, 32.5825` (Kampala centre) when neither GPS nor profile has coordinates (`ViewModel.kt:894`) | Fabricated location in an emergency |
| Acknowledge / resolve | `acknowledgeSos` / `resolveSosIncident` are admin-claim callables with timestamps and actor uid | Auditable |
| Clear | Owner-only stand-down via repository (`clearEmergencySos`) | Authorization tested |

Gap: dispatch outcome (`NO_RECIPIENTS`, `FAILED`) is written only on the Firestore incident; the client
never reads it back and keeps showing the success copy. A user can believe help was contacted when no
SMS left the platform.

## 2. Doorstep PIN (explicit FAIL condition)

`Repository.verifyDoorstepPin(jobId, enteredPin, expectedPin)` compares against a caller-supplied
expected value (`:785–798`); the UI passes the stored PIN (`MainScreen.kt:599`). Combined with the
default `"4829"` (`Entities.kt:259`), plaintext storage and no attempt limit, the "resident & worker
confirmed physically present" claim is not a control. This is the same defect raised by R07 and it is
an explicit R10 fail condition.

## 3. Reports and disputes

- `reportUser` writes only to the Room `reports` table; there is **no Firestore `reports` collection, no
  rules entry and no admin queue callable** (grep: `reports` appears only in the DSAR export and Room
  DAO). An admin cannot see a report filed on another device; reports are lost on reinstall.
- Disputes do have a server collection, an admin queue (`getDisputesQueue`), and `resolveDispute` with
  `resolvedBy`; a refund is now requested from the provider (R08). Good.
- Admin decisions are single-actor; no dual control for high-impact refunds or worker suspension.

## 4. KYC and guarantor fraud resistance

- Exactly two guarantors are required and validated for shape (`GuarantorVerificationSheet`,
  repository persistence rules).
- **No duplicate detection** exists for NIN, phone, photo hash or device: a worker can list the same
  person (or themselves) twice with different spellings, and the same NIN can back many accounts.
  No server-side uniqueness check on `guarantor_submissions`/`worker_kyc` was found.
- Reviewer access is admin-claim gated with 15-minute signed URLs; review actions are attributed.
- No device-fingerprint or velocity control for repeated KYC submissions.

## 5. Ratings and reputation

- Ratings are Room-only; there is no Firestore `ratings` collection or rule (rules list has none), so
  ratings do not aggregate across devices and cannot be moderated server-side.
- Authorization tests exist (`RatingAuthorizationSecurityTest`) for participant/completion rules, but
  with device-local storage a modified client can fabricate its own rating rows and no server view
  contradicts it.
- No review-bombing, self-rating or collusion analytics.

## 6. Chat and off-platform contact

- Messages are participant-scoped by rules and authorship-bound (R07).
- No phone-number masking or off-platform-contact detection was found; the product claims safety in
  in-person meetings but provides no check-in/check-out beyond the PIN.

## 7. Trust & safety operations

- SOS queue and dispute queue exist; no metrics (time-to-acknowledge, incident rate), no SLA
  enforcement, and no escalation runbook in the repository.
- Admin actions carry uids for disputes/SOS/guarantor reviews; `admin_audit` covers claim changes only.

## 8. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R10-P2-01 | **CRITICAL** | Doorstep PIN verified against client-supplied expected value, default `4829`, no lockout | `Repository.kt:785–798`; `Entities.kt:259`; `MainScreen.kt:599` |
| R10-P2-02 | **CRITICAL** | Reports are Room-only with no admin surface; lost on reinstall | `Repository.reportUser`; rules have no `reports` |
| R10-P2-03 | HIGH | SOS UI claims contacts/security were notified before dispatch result; NO_RECIPIENTS/FAILED never surfaced | `Repository.kt:2098`; `index.ts:948–990` |
| R10-P2-04 | HIGH | No duplicate NIN/phone/photo detection for guarantors or KYC | no uniqueness checks found |
| R10-P2-05 | MEDIUM | SOS falls back to invented Kampala coordinates | `ViewModel.kt:894` |
| R10-P2-06 | MEDIUM | Ratings are device-local and unmoderatable server-side | no `ratings` collection/rule |
| R10-P2-07 | LOW | No T&S metrics, SLA or escalation runbook; single-admin decisions | repository absence |

## 9. Verdict

**FAIL.** The server-side SOS dispatch trigger, admin claim gating and audited acknowledge/resolve are
real improvements, but the PIN is not a control, reports never reach an operator, dispatch outcomes are
never shown, and guarantor identity duplication is undetected. No live dispatch was executed —
**UNVERIFIED**.
