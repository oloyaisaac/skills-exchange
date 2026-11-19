# R09 — Privacy, KYC & Regulatory Compliance Reviewer

| Field | Value |
|---|---|
| Reviewer | R09 — Privacy, KYC & Compliance (P0, Gate G5) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Data inventory, KYC, consent, erasure, retention, notices, processors |
| Verdict | **FAIL** — G5 not met |
| Issues | 7 (1 CRITICAL, 3 HIGH, 3 MEDIUM) |

## Areas examined
Personal data collected and where it lives, KYC/guarantor storage, consent records, deletion cascade,
retention/purge, privacy notice accuracy, third-party processors and transfer basis, DSAR/export.

## Source files inspected
`Entities.kt` (guarantor consent 475, NIN fields), `FirebaseService.kt` (`deleteUserFirestoreData` 275–315),
`SkillsExchangeViewModel.kt` (`permanentlyDeleteUserAccountAndData` 1336), `AccountComplianceDialog.kt`
(notice 178–193), `DataBackupHelper.kt`, `storage.rules`, `firestore.rules`, `AndroidManifest.xml`.

## Features examined
Worker KYC, guarantor submission/consent, account deletion, data export/backup, privacy/compliance
dialogs, job photos and chat, admin access to identity documents.

## Tests performed
- Traced deletion from UI through Firestore/Auth/Room (no Storage deletion).
- Read consent model and privacy copy against implemented behaviour.
- Grepped for retention/purge jobs (0) and processor disclosures (0).
- No DSR, export or restore was executed.

## Verified working functionality
- KYC/guarantor Storage objects are owner-or-admin; signed URLs are time-limited.
- Identifier masking helpers (`scrubIdentifiers`, `maskIdentifier`) exist and are unit-tested.
- Guarantor phone normalisation/dedup within a submission is implemented.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R09-01 | CRITICAL | Account erasure over-deletes, aborts and ignores Storage. | Deletion | VM `1336–1367` derives job IDs from every cached job; `FirebaseService.kt:306–312` deletes jobs/messages denied to non-admins; first failure aborts; no Storage deletion | Atomic, scoped erasure across all stores | Partial deletion, other users' jobs targeted, KYC photos retained | Server-side deletion job over owned data only | Per-store deletion evidence + retry test |
| R09-02 | HIGH | Deletion removes complaints made against the user. | Disputes | `FirebaseService.kt:295–299` deletes where `reportedBy` or `reportedUserId` = user | Preserve reports/complaints per policy | A reported user can erase the complaint trail | Retain disputes; anonymise only where lawful | Deletion test asserts disputes preserved |
| R09-03 | HIGH | Privacy notice contains unimplemented claims. | In-app notice | `AccountComplianceDialog.kt:178–193`: encryption-at-rest, coarse district only, full JSON export, 24/7 arbitration | Notice matches code | Location is FINE, export is partial, no arbitration | Correct the notice; implement or remove claims | Legal review + copy-to-code matrix |
| R09-04 | HIGH | No retention or purge schedule exists. | Platform | 0 retention/purge jobs; data kept indefinitely | Defined schedule enforced automatically | Indefinite retention | Implement retention/purge with tests | Age-based purge verification |
| R09-05 | MEDIUM | Consent, processor disclosure and DSAR workflow are missing. | Compliance ops | Consent is one boolean (`Entities.kt:476`); no processor list/transfer statement; no DSAR SLA | Demonstrable consent, disclosed processors, DSAR process | Cannot demonstrate consent or handle access requests | Add consent records (version/time), processor list, DSAR runbook | Consent audit + DSAR dry run |
| R09-06 | MEDIUM | NIN is over-collected and unmasked at rest. | KYC | Full NIN in Firestore; masking helper not applied to storage | Minimal, masked where possible | Plaintext identifiers retained | Data-minimise, mask, restrict access | Field-level access audit |
| R09-07 | HIGH | Backups are enabled with empty rules. | Manifest/XML | `allowBackup=true`; sample backup XML with no excludes | Credentials/PII excluded | Session, DB and PII eligible for cloud backup | Disable or exclude sensitive stores | Backup inspection |

## Missing functionality
Server-side erasure, retention, DSAR/export coverage, consent lifecycle, processor disclosure, DPIA.

## Security concerns
World-readable chats/jobs (R07) compound privacy exposure. Deletion is both under- and over-inclusive.

## Performance concerns
Full-collection scans during deletion can time out on large accounts; not measured.

## Recommendations
1. Rewrite erasure server-side; never enumerate other users' jobs; include Storage.
2. Preserve complaints; anonymise rather than delete.
3. Correct the privacy notice and publish processor/transfer/retention terms.
4. Record consent and implement retention/DSAR.

## Limitations / unable to verify
No legal review, no device backup/restore, no DSR execution, no retention job to observe.
Compliance conclusions are engineering-level, not legal advice.

## Cross-references
R06, R07, R10, MR-041/MR-037/MR-042/MR-043/MR-044/MR-045/MR-016.
