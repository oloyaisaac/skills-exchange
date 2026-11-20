# Retention schedule and the deletion sweep

R09-P2-04: before this round nothing purged or minimised anything. There was no TTL, no purge job
and no stated window — KYC evidence, chats, SOS incident coordinates and notifications were kept for
ever because no one had decided otherwise. A retention policy that exists only in a policy document
is not a control; this one is code.

The single source of truth is `RETENTION_POLICY` in `functions/src/retention.ts`. The sweep, the
tests and this table all read it. Change the table when you change the constant, in the same commit.

## Categories swept

| Category | Collection (scope) | Timestamp field | Window | Action |
|---|---|---|---|---|
| `notifications` | `notifications` | `createdAt` | 90 days | PURGE |
| `chat_messages` | `messages` (collection group) | `timestamp` | 365 days | ANONYMISE (`body`, `text`, `senderName`, `attachmentUrl`) |
| `sos_incidents` | `sosIncidents` | `createdAt` | 365 days | ANONYMISE (`latitude`, `longitude`, `address`, `landmark`, `notes`) |
| `otp_rate_limits` | `otp_rate_limits` | `updatedAt` | 2 days | PURGE |
| `webhook_rate_limits` | `webhook_rate_limits` | `updatedAt` | 2 days | PURGE |
| `reconciliation_reports` | `reconciliation_reports` | `createdAt` | 400 days | PURGE |
| `retention_reports` | `retention_reports` | `createdAt` | 400 days | PURGE |
| `account_deletions` | `account_deletions` | `at` | 3 years | PURGE |

**Why chat and SOS are anonymised rather than deleted:** both may be evidence in a dispute or a
safety investigation inside the window, so the record's shape stays and the personal content goes.
Purging them outright would destroy the evidence the arbitration protocol depends on.

**Why the rate-limit buckets are swept:** they hold a phone number or an IP per attempt. Once the
window has passed the bucket has no purpose, and keeping it turns an anti-abuse measure into a
store of contact data.

## Legal holds — never swept

| Hold | Window | Collection |
|---|---|---|
| Payments / escrow / wallet / payouts / fees / refunds | 7 years | `payments`, `payouts`, `platform_fees`, `refunds`, `wallet_entries` |
| Identity verification | 5 years | `worker_kyc` |
| Consent proof | 6 years | `users/*/consents`, `users/*/guarantor_consents` |

`LEGAL_HOLDS` names them, the tests assert that no hold is also a swept category, and the deletion
notice reads the same list. A regime that deleted the payment books to satisfy an erasure request
would be worse than one that kept them; a regime that kept them while telling the person "everything
is deleted" would be dishonest. Neither happens: `buildErasureNotice` states the hold, the reason and
the duration.

## How the sweep behaves

`applyRetentionPolicy` is a scheduled function (`every 24 hours`) that, per category:

1. reads at most `RETENTION_BATCH_SIZE` (200) documents older than the cutoff, ordered oldest first;
2. decides per document with `decideRetention` — **a document that cannot be dated is retained and
   counted as `undated`**, never purged (a missing timestamp is not an age, and a purge that treats it
   as one deletes live records);
3. applies either `delete()` (PURGE) or `anonymisePatch()` (ANONYMISE), and stops at `maxActions` so
   one run cannot exhaust the function's time budget;
4. reports counts and errors per category into `retention_reports`, with a log line that carries
   counts only — no document ids, no sender names, no identity numbers.

One category failing does not stop the others, and a failure is recorded rather than swallowed.

## What is not covered

- **Sub-collections of `users/{uid}`** other than the consent receipts are removed by account
  deletion, not by the sweep; they are part of an active account.
- **Storage objects** are not aged by this sweep. They are covered by account deletion
  (`storagePurgePrefixes`: `users/{uid}/`, `avatars/{uid}/`, `private_docs/{uid}/`,
  `jobs/{jobId}/`) and by `storage.rules` ownership; a Storage lifecycle rule for abandoned KYC
  uploads is **UNVERIFIED/not claimed** — it needs a bucket policy, which is an infrastructure change,
  not application code.
- **Backups.** Google-managed Firestore backups are outside the sweep's reach; a deleted document can
  persist in a backup until the backup expires. That is stated here rather than claimed as handled.

## Related records

`docs/privacy/ROPA.md` is the processing inventory this schedule belongs to: every category here has a
row there with its purpose, basis and recipients. `docs/privacy/DSAR.md` is the procedure that applies
both.

## Evidence

`retention_reports` (`admin`-readable, client-unwritable) records each run: timestamp, per-category
counts, and the summary line. `functions/test/retention.test.mjs` proves the windows, the actions,
the undated-document fail-safe and the batch cap; `rules-test/firestore.rules.test.mjs` proves the
reports are not client data.
