# R09 — Privacy, KYC & Regulatory Compliance Reviewer

| Field | Value |
|---|---|
| Reviewer | R09 — Privacy, KYC & Compliance (P0) |
| Assignment | `reviewers/PRIVACY_COMPLIANCE_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no privacy code or copy changed |
| Regulator/DPIA access | **UNVERIFIED** — no filings, DPAs or console access |
| Verdict | **FAIL** |

## 1. Data inventory (implemented)

| Category | Where | Purpose | Access |
|---|---|---|---|
| Phone, UID, name, district | Firestore `users`, Room | account, contact | owner/admin |
| GPS coordinates + address/landmark | `jobs`, `users`, SOS incidents | dispatch, safety | owner/provider/admin |
| National ID photos, NIN, DOB, gender | Storage `users/*/kyc`, `worker_kyc` | KYC | owner/admin |
| Guarantor NIN/photo/phone | Storage `users/*/guarantors`, `guarantor_submissions` | verification | owner/admin |
| Chat messages | `jobs/*/messages` | coordination | participants |
| Payments (phone, amount, refs) | `momo_transactions`, `momo_payouts`, `refunds` | money | owner/worker/admin |
| Device tokens | not collected server-side (R05) | — | — |
| Consent records | `users/*/consents` (rules) | compliance | owner/admin |

## 2. Consent (not captured)

`recordConsent` exists as a callable and `FirebaseService.recordConsent(version)` wraps it, but there
is **no call site** in any screen or ViewModel (grep: only the service definition). The privacy sheet
(`AccountComplianceDialog`) displays text without recording acceptance, and guarantor consent is
text-only (`GuarantorVerificationSheet`), with no `consentVersion`/timestamp written for the guarantor.
R09's requirement — consent explicit and recorded — is unimplemented in the UI even though the storage
contract exists.

## 3. Data-subject rights

- `exportMyData` is implemented server-side (profile, jobs, disputes, notifications, payouts,
  sanitised guarantors, consent history) but has **no UI entry point**; `FirebaseService.exportMyData`
  is unused. A user cannot exercise access/portability in the app.
- Deletion: R06/R07 show Auth is deleted even when Firestore fails, Storage objects are never deleted,
  and the rules forbid `users/{uid}` deletion entirely. `docs/privacy/DSAR.md` instructs operators to
  "run the in-app deletion flow (account, jobs, messages, Storage objects…)" — the Storage part does
  not exist, so the published procedure is inaccurate.
- Correction: profile edits are owner-writable for non-privileged fields; KYC correction requires
  resubmission (implemented).

## 4. Retention

No retention job, TTL policy or purge function exists for KYC documents, chats, SOS incidents,
notifications or consents (grep in `functions/src` for retention/purge = 0). `PROCESSORS.md` and
`DSAR.md` define service levels but no storage-limitation schedule; Firestore has no TTL fields
configured. The only TTL is the 15-minute signed URL for reviewer photo access.

## 5. Privacy claims versus implementation

| Claim | Source | Reality |
|---|---|---|
| "Data is stored with encryption-at-rest" | `AccountComplianceDialog.kt:178` | true only at the Firebase platform level; no application-layer encryption for NIN, PIN or MoMo numbers |
| "Guarantor National ID numbers are encrypted" | `GuarantorVerificationSheet.kt:166` | plaintext fields in Firestore/Room; no field encryption |
| "never sold to third parties" | `AccountComplianceDialog.kt:178` | consistent with code (no analytics/ad SDK found) |
| DSAR deletion includes Storage objects | `docs/privacy/DSAR.md` | Storage deletion is not implemented |

The encryption claims are misleading to the extent they imply application-level protection.

## 6. Storage isolation and reviewer access

KYC and guarantor Storage paths are owner/admin only; reviewer access uses signed URLs with a bounded
TTL (server `PHOTO_URL_TTL_SECONDS`, default inspected as 15 minutes). Avatars are publicly readable
(`storage.rules:90–110`), which exposes a user photo to anyone holding the URL — a residual risk that
should be a documented decision.

## 7. Minimisation and third parties

- Job and SOS documents carry precise coordinates; district-level public stats are not derived from
  them, and reads are participant-scoped.
- `DataBackupHelper` exports providers/jobs including MoMo numbers to a user-chosen file; the export
  is explicit user action but contains third-party phone numbers.
- `docs/privacy/PROCESSORS.md` names Firebase, Africa's Talking, MTN, Gemini and Maps with purposes,
  categories and transfer basis. Contract/DPA status is aspirational ("required before production") —
  **UNVERIFIED**.
- No analytics, crash-reporting or advertising SDK was found in the inspected Gradle files.

## 8. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R09-P2-01 | **CRITICAL** | Erasure incomplete: Storage never deleted, `users/{uid}` delete denied, Auth removed on partial failure | `FirebaseService.kt:311–380`; `firestore.rules:207`; `ViewModel.kt:1372` |
| R09-P2-02 | HIGH | Consent capture unimplemented in UI despite callable/rules | `FirebaseService.kt:985`; no call sites |
| R09-P2-03 | HIGH | DSAR export has no user-facing entry point | `FirebaseService.kt:1008`; no call sites |
| R09-P2-04 | HIGH | No retention schedule or purge job for KYC, chat, SOS or consents | grep retention/purge = 0 |
| R09-P2-05 | MEDIUM | "Encrypted" / "encryption-at-rest" copy overstates protection for NIN and PIN | `AccountComplianceDialog.kt:178`; `GuarantorVerificationSheet.kt:166` |
| R09-P2-06 | MEDIUM | `DSAR.md` describes Storage deletion that does not exist | `docs/privacy/DSAR.md` §Deletion |
| R09-P2-07 | LOW | Avatars publicly readable | `storage.rules:90–110` |

## 9. Verdict

**FAIL.** Storage isolation and reviewer URL TTLs are sound and a processor register exists, but
consent and DSAR are unreachable from the product, erasure is incomplete across Storage/Auth/Firestore,
retention is undefined and enforced nowhere, and privacy copy overstates encryption. Regulator
registrations and processor DPAs are **UNVERIFIED**.
