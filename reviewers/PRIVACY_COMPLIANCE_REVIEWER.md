# R09 — Privacy, KYC & Regulatory Compliance Reviewer

## 1. Reviewer Number
R09 (PRD story `RVW-009`, Priority P0). Depends on R01; can run in parallel with R07.

## 2. Professional Title
Privacy & Data Protection Counsel / KYC Compliance Reviewer for East African digital marketplaces (Uganda DPPA 2019, Kenya DPA 2019, Tanzania PDPA 2022, Rwanda Law 058/2021).

## 3. Required Expertise
- Data protection principles: lawful basis, purpose limitation, minimisation, accuracy, storage limitation, integrity, accountability.
- Data subject rights: access, correction, deletion, objection, portability; response timelines.
- Sensitive personal data and third-party data (guarantor NINs, photographs, phone numbers).
- Cross-border transfer and processor agreements (Google Firebase region, Africa's Talking, MTN).
- KYC/AML record-keeping versus deletion obligations; consent capture and withdrawal.
- Privacy-by-design review of mobile storage, backups, logs and analytics.

## 4. Responsibilities
1. Inventory all personal and sensitive data, collection points, purposes, storage locations and recipients.
2. Verify consent capture (worker KYC, guarantor consent, privacy policy/terms acceptance) is recorded and revocable where required.
3. Audit data minimisation and purpose limitation, including location precision and device backup.
4. Audit storage isolation and access control for National IDs and guarantor documents.
5. Audit retention, deletion and account-erasure completeness across Firebase Auth, Firestore, Storage and Room.
6. Assess privacy notices/UI claims against actual implementation (e.g. "encryption-at-rest", "never sold").
7. Identify registration/notification obligations with the relevant data protection authority.

## 5. Exact Features to Examine
- Client onboarding: name, phone, photo, district, GPS, landmark.
- Worker KYC: National ID front/back, legal name, DOB, gender, NIN, qualifications, demo video.
- Guarantor system: third-party NIN, photo, phone, consent.
- Verification review: admin access to documents via signed URLs.
- Chat masking and contact privacy.
- Location display and district-level stats.
- Account deletion, data backup export, localStorage/session.
- Ratings, reports/disputes and notification content.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `app/src/main/java/com/example/data/db/Entities.kt` | NIN, DOB, gender, guarantor PII, `doorstepPin`, location fields |
| `app/src/main/java/com/example/data/remote/FirebaseService.kt` | upload paths, `deleteUserFirestoreData`, profile sync |
| `storage.rules` | KYC/guarantor/certificate isolation, avatar public read |
| `firestore.rules` | `users` owner-or-admin, `guarantors`, `guarantor_submissions` |
| `app/src/main/java/com/example/ui/components/AccountComplianceDialog.kt` | privacy/terms copy and "encryption" claim |
| `app/src/main/java/com/example/ui/components/GuarantorVerificationSheet.kt` | third-party consent and "encrypted" claim |
| `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (`permanentlyDeleteUserAccountAndData`) | erasure scope |
| `app/src/main/java/com/example/util/DataBackupHelper.kt` | JSON export of providers/jobs (incl. MoMo number) |
| `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | empty backup policy with `allowBackup="true"` |
| `requirement_file.md` §5, §15 | stated privacy/retention requirements |
| `functions/src/guarantorReview.ts` | signed URL TTL and admin projection |

## 7. Functional Requirements
- Collection limited to fields required for the stated purpose.
- Consent is explicit, recorded with timestamp/version, and separate for third-party guarantors.
- Data subject can obtain, correct and delete their data through a documented process.
- Retention periods are defined per data category and enforced.
- Admin access to identity documents is logged and least-privilege.

## 8. Security Requirements
- Identity documents are never publicly readable and never delivered as permanent public URLs.
- Guarantor data is visible only to the submitting worker and authorised reviewers.
- Sensitive fields are encrypted at rest beyond platform defaults, or the UI claim is corrected.
- No PII in logs, crash reports, backups or exported JSON without user intent.
- Processor/transfer safeguards documented (Firebase project region, vendor DPAs).

## 9. Testing Requirements
- Access-control tests per actor for KYC/guarantor paths (with R07).
- Erasure test asserting Auth, Firestore, Storage and Room are all cleared, and that partial failure is surfaced.
- Backup/restore test proving no sensitive data leaks into cloud backup.
- Data-export review for unintended PII.
- Retention job test once implemented.

## 10. Expected Evidence
- Data inventory/RoPA: category, subject, purpose, basis, location, recipient, retention.
- Consent artifacts and UI copy versus code.
- Deletion runbook with evidence of each store cleared.
- DPIA or documented screening for KYC/location/financial data.
- Gap list mapped to specific statutory provisions and responsible owner.

## 11. Passing Conditions
- PASS requires: lawful basis and consent recorded, minimisation demonstrated, retention defined and enforced, erasure complete and verified across all stores, no misleading privacy claims, and transfer/processor safeguards documented.
- FAIL now: the first delete (`users/{uid}`) is denied by rules, so the whole Firestore cascade aborts and Storage KYC/guarantor images are never deleted, while Auth deletion proceeds; backup rules are empty while backups are enabled; UI claims encryption that is not implemented in code.
