# REVIEW-09 — Privacy, KYC & Compliance Review

| | |
|---|---|
| **Reviewer** | R09 — Privacy, KYC & Compliance Reviewer (P0, Gate G5) |
| **Wave** | 1 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** — Gate G5 not met |

## 1. Scope and evidence

In-app privacy/consent copy, KYC and guarantor data models, storage rules for identity documents,
`DataBackupHelper`, the account-deletion cascade, retention/purge logic, and third-party data flows
(Africa's Talking, Google Sign-In, Gemini, MTN MoMo).

## 2. Data inventory and protection

| Data | Where stored | Protection | Assessment |
|---|---|---|---|
| Phone, name, district | Room + Firestore `users/{uid}` + DataStore | Owner-write rules; readable by admin | Acceptable (admin claim unprovisionable) |
| National ID (NIN) | `worker_kyc/{uid}` doc + Storage `kyc/` | Owner-or-admin storage rule | PARTIAL — no encryption-at-rest beyond provider default; no field-level encryption |
| Guarantor name/phone/NIN/photo | `guarantors`, `guarantor_submissions`, Storage `guarantor/` | Owner-or-admin | PARTIAL — guarantor is a third party whose consent is a boolean |
| Job photos/receipts | Storage `jobs/{jobId}/**` | Self-asserted metadata (R07-F4) | FAIL |
| Chat messages | Firestore `jobs/{jobId}/messages` | **`allow read: if true`** | FAIL |
| Momo number | Room, exported in JSON backup | Plaintext, shareable | FAIL |
| Ledger/money | Room + Firestore `momo_transactions` | Client-creatable | FAIL |

## 3. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R09-F1 | Critical | Chat messages and job posts are world-readable (R07-F1). | Unbounded disclosure of names, phone numbers, addresses, GPS and conversations. No consent can cover this. |
| R09-F2 | Critical | `permanentlyDeleteUserAccountAndData` (`VM:1336`) computes `userJobIds` from **every job in the local Room cache** (`repository.jobs.first()`), then `deleteUserFirestoreData` deletes each job document and its messages (`FirebaseService.kt:306–312`). Rules allow job deletion only for admins, so the first foreign job throws and the whole try-block aborts (`return false`), leaving most data behind — after the local DB has already been cleared. | Erasure is both over-broad (targets other users' jobs) and non-atomic (partial failure reported only as a banner). It cannot satisfy a deletion request and risks destroying other people's records. |
| R09-F3 | High | Deletion requires no fresh authentication; `deleteCurrentUser()` will fail with `requires-recent-login` after a long session, yet local wipe and sign-out still occur. | User believes the account is gone; the Firebase account and remaining cloud data persist, now unreachable. |
| R09-F4 | High | Deletion does not touch Storage (`kyc/`, `guarantor/`, avatars), `sosIncidents`, `momo_transactions`, ratings, `reports`, or audit logs. | Identity documents and evidence photos survive account deletion indefinitely. |
| R09-F5 | High | Deletion also removes `disputes` where the user is `reportedBy` **or** `reportedUserId` (`:295–299`). | A reported user can erase the complaint against them by deleting their own account. Evidence destruction. |
| R09-F6 | High | Privacy copy claims "encryption-at-rest", "coarse district only", "full export in JSON", "24/7 arbitration" (`AccountComplianceDialog.kt:178–193`). Location is FINE + COARSE (`AndroidManifest.xml`), export contains only providers+jobs (`DataBackupHelper.generateBackupJson`), and no arbitration service exists. | Multiple false statements in a user-facing legal notice. |
| R09-F7 | High | No retention or purge logic anywhere (0 hits for retention/purge jobs); `allowBackup="true"` with empty backup rules. | Data kept forever and copied to cloud backup, contrary to minimisation duties. |
| R09-F8 | Medium | Guarantor consent is a single boolean (`Entities.kt:476`); no consent text, version, timestamp or withdrawal path. | Consent is not demonstrable. |
| R09-F9 | Medium | No disclosed third-party processors (Google, Africa's Talking, MTN, Gemini) and no cross-border transfer statement. | Uganda DPPA 2019 transfer/disclosure duties unaddressed. |
| R09-F10 | Medium | No DSAR workflow, SLA, or privacy contact; export is a share-sheet of local JSON that omits messages, ratings and ledger. | Subject-access requests cannot be fulfilled. |
| R09-F11 | Medium | NIN is copied to Firestore and shown in admin UI; `maskIdentifier` exists but is not applied to stored data. | Over-collection and unclear necessity. |
| R09-F12 | Info | `GuarantorValidators.scrubIdentifiers`/`maskIdentifier`, owner-or-admin KYC storage rules and phone-dedup validation are real. | Positive. |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Lawful, documented data flows | FAIL |
| Privacy notice accurate | FAIL |
| Consent demonstrable | FAIL |
| Erasure complete, atomic, authenticated | FAIL |
| Retention/minimisation | FAIL |
| Processor disclosure / transfer basis | FAIL |
| KYC data least-privilege | PARTIAL |

## 5. Passing conditions

1. Close public reads; field-level location policy implemented as described.
2. Deletion implemented server-side as one authenticated, re-authenticated job that enumerates owned
   data only, including Storage and disputes **preservation** (retain complaints per policy).
3. Accurate privacy notice with processor list, retention schedule and contact.
4. Consent records with version + timestamp; guarantor consent text.
5. Automated retention/purge with tests; backup excludes sensitive stores.
6. DSAR export covering all user-owned collections.

## 6. Residual risk

The combination of world-readable chats, a broken erasure cascade and an inaccurate privacy notice is
a regulatory exposure independent of engineering quality.

## Recommended remediation (not implemented)

1. Implement erasure server-side as one re-authenticated job over owned data only, including Storage.
2. Preserve complaint evidence on account deletion.
3. Correct the privacy notice and publish processor/transfer/retention terms.
4. Record consent with text version and timestamp; automate retention/purge; exclude backups.

## 7. Cross-references

F-003, F-007; R07-F1/F7, R02-F3, R08, R10-F3; G5.
