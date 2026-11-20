# Data-subject access and deletion requests

REM-044 / R09: the operating procedure for access, correction and deletion requests.

This file describes what the code does. Where it previously promised more than the code did (the
deletion section claimed Storage objects were removed by a flow that did not remove them), it has
been corrected — the reviewer's finding R09-P2-06. If this document and the code disagree again, the
code is the defect **and** this document is wrong; fix both in the same change.

## Service level

- **Acknowledgement:** within 2 working days.
- **Fulfilment:** within **30 calendar days** of a verified request (`DSAR_SLA_DAYS` in
  `functions/src/compliance.ts`). The export envelope carries `slaDueAt` computed from the same
  constant.
- **Extension:** one 30-day extension is permitted for complex requests; the requester is told before
  the first deadline expires.

## Access / portability

1. Verify the requester controls the account (signed-in session; re-authentication for deletion).
2. **In the app:** Settings → *Privacy policy & terms* → **Download my data (JSON)**. The button calls
   `exportMyData` (`functions/src/index.ts`) and hands the resulting file to the Android share sheet
   from a private cache file (`FileProvider`, `res/xml/file_paths.xml`). Nothing is emailed.
3. **On the server:** `exportMyData` builds the envelope — `generatedAt`, `slaDueAt`,
   `formatVersion`, profile, jobs posted and assigned, disputes, reports, notifications, payouts, the
   guarantor submission (redacted), the account's own consent history, third-party guarantor consent
   receipts, the sanitised guarantor rows, and the retention schedule that applies to each category.
4. Third-party data is minimised **on the server** before the envelope exists:
   - guarantor submissions are passed through `sanitiseGuarantorSubmissionForExport`
     (`functions/src/pii.ts`), which masks the guarantor NIN, drops names, phone numbers and every
     Storage path, and keeps only the relationship, district, review state and consent receipt;
   - guarantor rows go through `sanitiseGuarantorForExport`;
   - a Storage path is a capability, not data, and is never included.
5. Deliver the JSON over an authenticated channel; do not paste it into email or chat.

## Deletion

The in-app flow (Settings → *Delete my account*) calls `deleteMyAccount`
(`functions/src/index.ts`), which executes `ERASURE_STEPS` (`functions/src/accountDeletion.ts`) in
this order:

1. **Fresh authentication.** `auth_time` must be inside the window
   (`FRESH_AUTH_WINDOW_SECONDS`); otherwise the call is refused with `REAUTH_REQUIRED`.
2. **Ownership of every job id** the client supplied is verified before anything under that job is
   touched.
3. **Cloud Storage** objects under `users/{uid}/`, `avatars/{uid}/` and `jobs/{jobId}/` (for the
   verified jobs) are deleted, and the count of deleted objects is reported.
4. **Firestore personal documents:** `users/{uid}` and its sub-collections (`consents`,
   `guarantor_consents`, `guarantor_consents`, `push_tokens`, …), `worker_kyc`, `worker_training`,
   `providers`, `guarantor_submissions`.
5. **Chat:** every message in the account's own jobs, **and** every message the account authored in
   the jobs it was assigned to (`assignedProviderUid`), matched on `senderId`. A legacy message
   attributed only by name is not deleted, because ownership cannot be proven for it.
6. **Query-scoped records:** `guarantors` (by `workerKycId`), notifications, disputes (both sides),
   job quotes.
7. **The job documents** themselves, last.
8. **An `account_deletions` audit record** is written with the counts, the retained categories and a
   plain-language notice (`buildErasureNotice`).
9. **The Firebase Auth user is deleted only if nothing is pending.** If any record or object could
   not be removed, the call fails with `PURGE_INCOMPLETE`, the account is left intact, and the app
   says deletion did not finish. A deleted identity cannot finish removing its own data, so this
   ordering is the point: the identity is the last thing to go.

### What is retained, and why

Retained records are **not** deleted; their direct identifiers are handled as described in
`docs/privacy/RETENTION.md`, and the person is told which categories were kept, with the reason and
the duration, in the deletion notice. Today's categories:

| Category | Window | Why |
|---|---|---|
| Payment, escrow, wallet, payout, fee and refund records | 7 years | AML and tax obligations; they may also show money still owed |
| Identity-verification evidence (`worker_kyc`, verified guarantor rows) | 5 years | KYC record-keeping |
| Consent receipts | 6 years | The agreement must stay demonstrable for as long as it is relied on |

The retention sweep and the legal holds are defined in `functions/src/retention.ts`; the deletion
notice quotes them, so the app cannot claim in one place what another place contradicts.

## Correction

Profile fields are editable in the app. Identity-verification fields are not client-writable
(`firestore.rules` pins `ninVerificationStatus`, `verifiedAt`, `verifiedBy`, `ninMasked`,
`ninFingerprintHash`, `ninDuplicateOfWorker` to admins), so a correction there is made by the
verification reviewer against evidence, and the change is recorded with `verifiedBy`.

## Evidence

Record, for each request: request date, verification method, export timestamp (or deletion
completion), and the operator who handled it. Do not store the export itself after delivery. Deletion
evidence lives in `account_deletions` (counts and categories only — no personal data); retention
sweep evidence lives in `retention_reports`. Both collections are admin-readable and client-unwritable.
