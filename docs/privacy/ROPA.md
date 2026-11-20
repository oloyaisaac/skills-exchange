# Record of processing (RoPA)

R09: the reviewer's evidence list asks for a data inventory — category, subject, purpose, basis,
location, recipient, retention — as the artefact that shows lawful basis and minimisation rather than
asserting them. This is that artefact, written from the code. Every row names where the data lives;
where a claim cannot be verified from this repository it says so instead of implying otherwise.

Uganda's Data Protection and Privacy Act 2019 is the primary regime (Kenya DPA 2019, Tanzania PDPA
2022 and Rwanda Law 058/2021 apply to users in those jurisdictions). **This document is an engineering
record, not legal advice, and no filing, notification or DPA signature is claimed anywhere.**

## 1. Inventory

| # | Category | Subjects | Purpose (why) | Basis | Where it lives | Recipients | Retention |
|---|---|---|---|---|---|---|---|
| 1 | Phone number, UID, display name, district | Account holders | Authentication, matching, safety contact | Contract; legitimate interest (fraud prevention) | Firebase Auth, `users/{uid}` | Google Firebase; Africa's Talking (OTP/SMS) | Life of account; SMS/OTP delivery logs at the provider are **not controlled by this repo** |
| 2 | Job postings, quotes, escrow state | Residents, workers | Performing the service | Contract | `jobs`, `job_quotes`, `materialAdvances` | Google Firebase | Life of account; job photos deleted with the account (`jobs/{jobId}/`) |
| 3 | Chat messages | Residents, workers | Coordinating the job; dispute evidence | Contract | `jobs/{jobId}/messages` | Google Firebase | 365 days, then **anonymised** (`RETENTION.md`) |
| 4 | National ID front/back, legal name, DOB, gender, NIN | Workers (KYC) | Identity verification (regulated KYC) | Legal obligation; explicit consent | Cloud Storage `users/{uid}/kyc/…`, `users/{uid}/verification/…`, `worker_kyc` | Google Firebase; verification reviewers (staff) | 5 years (legal hold, never swept) |
| 5 | Guarantor NIN, phone, photographs | Third parties (guarantors) | Verifying the worker; collusion detection | **Separate third-party consent**, recorded (`users/{uid}/guarantor_consents/{version}`) | `guarantors`, `guarantor_submissions`, Cloud Storage `users/{uid}/guarantors/…` | Google Firebase; verification reviewers | 5 years with the KYC record; deleted with the worker's account otherwise |
| 6 | Photographs (profile avatar) | Account holders | Identifying a person to their counterpart | Contract | `avatars/{uid}/`, `users/{uid}/avatars/` | Google Firebase (session-only reads) | Until replaced or deleted with the account |
| 7 | Precise GPS, landmark | Residents, workers | Doorstep arrival, SOS dispatch | Consent (foreground permission) | `jobs`, `sosIncidents`, `users/{uid}` | Google Firebase | 365 days for SOS coordinates, then anonymised |
| 8 | SOS incidents | Account holders | Emergency response | Vital interests | `sosIncidents` | Google Firebase; FCM; Africa's Talking (SMS) | 365 days, then coordinates and notes anonymised |
| 9 | Mobile-money numbers, amounts, references | Payers, payees | Collection, escrow, payout, refunds | Contract; legal obligation (AML) | `payments`, `payouts`, `platform_fees`, `refunds`, `wallet_entries`, `wallets/{uid}` | MTN MoMo; Google Firebase | 7 years (legal hold) |
| 10 | Doorstep PIN | Residents | Proving presence at handover | Contract | `jobs` (server-only field, unreachable from every client, admin included) | none | Deleted with the job |
| 11 | Notifications | Account holders | Service and safety messages | Contract | `notifications` | Google Firebase (FCM) | 90 days, then purged |
| 12 | Consent receipts (account + guarantor) | Account holders, guarantors | Proving agreement; answering a complaint | Legal obligation (accountability) | `users/{uid}/consents`, `users/{uid}/guarantor_consents` | Google Firebase | 6 years (legal hold); withdrawal is recorded on the same receipt |
| 13 | Voice notes, transcribed text | Account holders | Describing a job by voice | Consent (microphone) | Cloud Storage `jobs/{jobId}/…`, `jobs/{jobId}/messages` | **Google Gemini API** (transcription); Google Firebase | With the message (365 days, then anonymised) |
| 14 | Disputes and arbitration decisions | Both parties | Resolving a dispute | Contract; legal obligation | `disputes` | Google Firebase; staff | With the account (see gap 3) |
| 15 | Rate-limit buckets (phone/IP per attempt) | Any caller | Abuse prevention | Legitimate interest | `otp_rate_limits`, `webhook_rate_limits` | Google Firebase | 2 days, then purged |
| 16 | Operational reports (reconciliation, retention, deletion audit) | None (counts only) | Proving the platform's own controls | Accountability | `reconciliation_reports`, `retention_reports`, `account_deletions` | Google Firebase; staff | 400 days (3 years for deletion audits) |

## 2. Minimisation, as code

- **NIN**: stored in one access-controlled document, never client-readable for anyone else, masked
  wherever displayed (`maskNin`), and the reviewer's verification leaves only a masked value and a
  salted fingerprint (`ninFingerprint`) rather than a second plaintext copy.
- **Exports**: the subject-access envelope carries the worker's own data plus *masked* third-party
  identifiers; guarantor names, phones and Storage paths are dropped before the file exists
  (`sanitiseGuarantorSubmissionForExport`).
- **Logs**: identity and phone values are masked before a log line or a persisted report
  (`scrubForLog`), and `scripts/check_no_pii_logs.mjs` fails the build when a log statement carries
  one unmasked.
- **Location**: clients see district-level location until a quote is accepted; precise coordinates are
  used for dispatch and are anonymised after a year.
- **Device backup**: `allowBackup="false"` with every domain excluded in `backup_rules.xml` and
  `data_extraction_rules.xml`; `scripts/check_backup_policy.mjs` fails if that changes.

## 3. Gaps, stated plainly

1. **Off-device copies at providers.** Google-managed backups and provider-side logs (Africa's
   Talking, MTN, Gemini) are outside the retention sweep. The sweep controls Firestore and Cloud
   Storage only.
2. **Storage lifecycle for abandoned uploads.** No bucket lifecycle rule exists; a KYC upload from an
   account that is never deleted stays until the account is deleted. This is an infrastructure change
   and is not claimed.
3. **Dispute records are erased with the account.** Preserving a complaint while erasing the
   complainant needs a decision about who may read it afterwards (task REM-037, still open).
4. **Cross-border transfer instruments.** `PROCESSORS.md` names each processor and its contract
   status; no signed DPA is claimed, and the project region is a deployment choice that this
   repository does not pin.
5. **Consent for analytics or marketing.** None is collected, so none is listed.

## 4. Keeping this honest

`docs/DOCUMENT_MAP.md` lists this file; `docs/traceability.md` maps the privacy requirements to code
and tests; `docs/privacy/RETENTION.md` holds the windows and the legal holds that this inventory
cites. If a category is added to `RETENTION_POLICY` without a row here, the inventory is incomplete —
both are reviewed together.
