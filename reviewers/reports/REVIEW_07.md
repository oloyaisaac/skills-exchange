# REVIEW-07 — Application Security Review

| | |
|---|---|
| **Reviewer** | R07 — Application Security Reviewer (P0, Gate G2) |
| **Wave** | 1 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` |
| **Verdict** | **FAIL** — Gate G2 not met |

## 1. Scope and evidence

Full read of `firestore.rules` (353 lines) and `storage.rules`; manifest and backup configuration;
release build config; secret scan across the repo; dependency audit output; grep for App Check,
certificate pinning, root detection and rules tests.

## 2. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R07-F1 | Critical | `jobs`: **`allow read: if true`** (`firestore.rules:228`). The adjacent comment claims GPS is "gated for unverified workers" — no such condition exists. `messages`: **`allow read: if true`** (`:295`). `providers`: public read (`:216`). | Every job post (address, coordinates, budget, contact data) and every chat message is readable by **unauthenticated** internet clients. Job IDs are listed publicly, so chat subcollections are enumerable. |
| R07-F2 | Critical | `AFRICASTALKING_API_KEY` hardcoded (`AfricaTalkingService.kt:15`), shipped in the APK, `isMinifyEnabled = false`. | SMS/OTP account takeover and toll fraud; strings trivially recoverable from the un-obfuscated APK. |
| R07-F3 | High | Duplicate `match /sosIncidents` blocks at `firestore.rules:156` and `:318`. Firestore **ORs** allow rules for the same path, so the weaker first block wins. | The second block's `exists(/jobs/$(jobId))` guard and `assignedProviderUid` field are nullified: an SOS can be raised against a non-existent job and the legacy `assignedProviderId` read path remains live. |
| R07-F4 | High | `storage.rules` job writes authorize on **incoming custom metadata** (`jobOwnerId`) that the uploader controls, and `create, update` share the same condition. | Any authenticated user can write — and overwrite a known filename — inside another job's photo folder, then read it back. Evidence photos are not tamper-evident. |
| R07-F5 | High | `notifications`: `allow create: if isAuthenticated()` with no field binding (`:132`). | Any user can inject a notification carrying any `userId`, e.g. a fake "payment released" or "SOS resolved" message, into another user's feed. |
| R07-F6 | High | `momo_transactions`: create open to any authenticated user (`:177`). | Forged transaction rows pollute reconciliation and can be used to spoof "paid" states to any UI that reads the collection. |
| R07-F7 | High | `allowBackup="true"` with **empty sample** `backup_rules.xml` and `data_extraction_rules.xml`. | Firebase refresh tokens, the Room DB (PII + ledger) and DataStore session are eligible for cloud backup and device transfer. |
| R07-F8 | High | `job_quotes`: `allow read: if isAuthenticated()` (`:119`). | Every worker's bid, price and job history is readable by every other authenticated user. |
| R07-F9 | Medium | No `reports` collection rules at all; `reportUser()` writes Room only. | Safety reports never reach the backend (default-deny), so abuse reports are silently local. |
| R07-F10 | Medium | Zero App Check usage; no `enforceAppCheck` on callables; no rate limits. | Scripted abuse of OTP, SOS and payout-status endpoints. |
| R07-F11 | Medium | No rules unit tests (`0` files), no `firebase emulators` config for rules. | Nothing prevents the next rules regression. |
| R07-F12 | Medium | `isMinifyEnabled = false`, no `network_security_config`, no certificate pinning. | Easier reverse engineering and MITM on hostile networks. |
| R07-F13 | Info | `users` privileged fields are protected from self-write; `providers` sync excludes `momoNumber`. | Positive — do not re-flag. |

## 3. Threat review (selected)

| Threat | Result |
|---|---|
| Unauthenticated scraping of jobs/chats | **Exploitable** (F1) |
| Forged notification / transaction | **Exploitable** (F5, F6) |
| Overwrite another job's evidence photo | **Exploitable** (F4) |
| Admin impersonation via UI | Cosmetic only; server gate holds but is unprovisionable (R06-F3/F4) |
| Secret extraction from APK | **Exploitable** (F2) |
| Cloud-backup exfiltration of tokens | **Exploitable** (F7) |
| Client-forged escrow release | Not exploitable — server gates verified |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| No public PII collections | FAIL |
| Least-privilege rules per collection | FAIL |
| Every collection has explicit rules | FAIL (`reports`) |
| Secrets never in client | FAIL |
| Backup excludes credentials | FAIL |
| Rules tested | FAIL |
| App Check / abuse throttling | FAIL |

## 5. Passing conditions

1. `jobs`, `messages`, `providers` reads constrained to participants/admin; GPS field-level gating
   implemented and tested.
2. Duplicate `sosIncidents` block removed; single canonical rule with `exists(job)` proven by a rules
   test.
3. Notification/transaction creates bound to server-only paths (or removed from client SDK usage).
4. Storage writes bind to stored job ownership, not self-asserted metadata; uploads versioned.
5. Key rotated, removed from source, OTP behind a callable; App Check enforced.
6. Backup excludes `google_firebase` prefs, DataStore and the DB, or is disabled.
7. Rules test suite in CI.

## 6. Residual risk

Rules are the only server-side protection for direct SDK access, and three of the largest collections
are world-readable. This is the single most severe cluster in the assessment.

## Recommended remediation (not implemented)

1. Close `jobs`/`messages`/`providers` public reads and implement the GPS field policy the comments claim.
2. Delete the duplicate `sosIncidents` block and bind notification/transaction creates to server-only paths.
3. Authorize storage writes on stored job ownership; version evidence uploads.
4. Remove secrets, disable backups, enforce App Check, and add a rules test suite to CI.

## 7. Cross-references

F-002, F-003, F-005; R06, R05-F1, R09, R10-F3, R14 (no CI security gate), G2.
