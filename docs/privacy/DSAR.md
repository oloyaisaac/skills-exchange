# Data-subject access and deletion requests

REM-044: this is the operating procedure for access, correction and deletion requests.

## Service level

- **Acknowledgement:** within 2 working days.
- **Fulfilment:** within **30 calendar days** of a verified request (`DSAR_SLA_DAYS` in
  `functions/src/compliance.ts`).
- **Extension:** one 30-day extension is permitted for complex requests; the requester is told
  before the first deadline expires.

## Access / portability

1. Verify the requester controls the account (signed-in session; re-authentication for deletion).
2. Call the `exportMyData` callable; it returns a JSON envelope with `generatedAt`, `slaDueAt`,
   `formatVersion` and the data: profile, jobs posted/assigned, disputes, reports, notifications,
   payouts, the guarantor submission, consent history, and **sanitised** guarantor rows.
3. Third-party guarantor NINs, phone numbers, photographs and storage paths are **never** included;
   the worker sees only that a guarantor exists and its review state.
4. Deliver the JSON over an authenticated channel; do not email it unencrypted.

## Deletion

1. Re-authenticate the requester.
2. Run the in-app deletion flow (account, jobs, messages, Storage objects, disputes, notifications).
3. Retain only what law or fraud prevention requires (for example, completed payment records) and
   tell the requester what was retained and why.
4. Confirm completion in writing within the 30-day window.

## Evidence

Record, for each request: request date, verification method, export timestamp (or deletion
completion), and the operator who handled it. Do not store the export itself after delivery.
