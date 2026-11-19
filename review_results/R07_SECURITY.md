# R07 — Application Security Engineer

| Field | Value |
|---|---|
| Reviewer | R07 — Application Security (P0, Gate G2) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | Firestore/Storage rules, secrets, manifest, backups, abuse controls |
| Verdict | **FAIL** — G2 not met |
| Issues | 11 (1 CRITICAL, 7 HIGH, 3 MEDIUM) |

## Areas examined
All rules files, manifest and backup configuration, release build settings, secret handling, storage
authorization, client-forgeable collections, abuse throttling, rules test coverage.

## Source files inspected
`firestore.rules` (353 lines), `storage.rules`, `AndroidManifest.xml`, `res/xml/backup_rules.xml`,
`res/xml/data_extraction_rules.xml`, `app/build.gradle.kts`, `AfricaTalkingService.kt`,
`MainScreen.kt`, `FirebaseService.kt`.

## Features examined
Job/chat reads, provider catalogue, notifications, MoMo transactions, quotes, SOS incidents, KYC and
job-photo storage, app backup, admin gates.

## Tests performed
- Full rule-by-rule reading with path/line mapping.
- grep for App Check enforcement, `isAdmin`, secrets, backup settings.
- Checked for rules test sources (0).
- No Firebase emulator; no exploit was executed against a live project.

## Verified working functionality
- `users` privileged fields are protected from self-write; `providers` mirror excludes `momoNumber`.
- KYC/guarantor/certificate Storage paths are owner-or-admin.
- Client-forged escrow release is blocked by server-side callable authorization.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R07-01 | CRITICAL | `jobs`, `messages` and `providers` are world-readable. | Firestore rules | `:228`, `:295`, `:216` are `allow read: if true` | Participant/admin only, field-level GPS | Unauthenticated clients can read addresses, phones, budgets, chat | Scope reads to participants and gate fields | Emulator rules tests per actor |
| R07-02 | HIGH | Duplicate `sosIncidents` blocks make the weaker rule win. | Firestore rules | Blocks at `:156` and `:318`; OR semantics | One canonical rule with job-existence check | `exists(job)` guard bypassed; legacy field accepted | Remove duplicate; keep the stricter block | Rules test: SOS for missing job denied |
| R07-03 | HIGH | Notifications and MoMo transactions are client-forgeable. | Firestore rules | `notifications` create any auth (`:132`); `momo_transactions` create any auth (`:177`) | Server-only writes with field binding | Any user can inject rows for any user | Bind creates or move writes to Admin SDK | Rules tests for forged payloads |
| R07-04 | HIGH | Storage job-photo writes authorize self-asserted metadata. | Storage rules | `match /jobs/{jobId}/{allPaths=**}` create/update checks incoming `jobOwnerId` | Authorize against stored job ownership | Any auth user can write/overwrite another job's folder | Validate against Firestore job owner; version uploads | Storage emulator test for cross-job write |
| R07-05 | HIGH | All quotes are readable by every authenticated user. | Firestore rules | `job_quotes` `allow read: if isAuthenticated()` (`:119`) | Owner and quoting worker only | Bid/price data leaks across users | Scope reads to job owner + quote owner | Rules tests |
| R07-06 | HIGH | Backups are enabled with empty backup rules. | Manifest/XML | `allowBackup="true"` (`:18`); sample backup/extraction XML with no include/exclude | Credentials/session excluded | Tokens, DataStore and DB eligible for backup | Disable backup or exclude sensitive stores | `adb backup`/restore test |
| R07-07 | HIGH | No App Check enforcement or rate limits. | Client/backend | App Check dependency declared (`build.gradle.kts:119`) but never initialized/enforced | Verified app instances only | Scripted abuse of OTP/SOS/status endpoints | Initialize and enforce App Check; add limits | Unverified-client request rejected |
| R07-08 | MEDIUM | Abuse reports have no collection rules. | Firestore rules | 0 `match /reports`; `reportUser` writes Room only | Admin-readable reports collection | Reports never reach moderators | Add rules and server write path | Rules test + moderator queue E2E |
| R07-09 | MEDIUM | There are zero rules tests. | Test suite | 0 rules test files/emulator config | Regression tests per rule | Rules defects ship silently | Add emulator rules suite to CI | Tests fail on intentional rule regression |
| R07-10 | MEDIUM | Release APK is unminified with no network hardening. | Build config | `isMinifyEnabled=false` (`:44`); no network security config or pinning | R8 + TLS policy | Easier reverse engineering/MITM | Enable R8, add network security config | Release build inspection |
| R07-11 | HIGH | A live-looking Africa's Talking key ships in the APK. | Client | `AfricaTalkingService.kt:15` (value redacted) | Server-held secret | SMS/OTP account abuse | Rotate and move server-side | Secret scan + revocation evidence |

## Missing functionality
Field-level GPS policy, server-only collections, storage ownership binding, rules tests, App Check,
backup exclusions, network hardening.

## Security concerns
The public-read cluster is the most severe issue in the repository: job addresses, phone numbers and
full chat transcripts are exposed to unauthenticated clients, and job IDs are enumerable from the same
public list.

## Performance concerns
None specific; open reads also create unbounded cost exposure (R13).

## Recommendations
1. Close public reads and add field-level location controls.
2. Remove the duplicate SOS block and bind/relocate forgeable writes.
3. Authorize Storage against stored ownership.
4. Disable backups, enforce App Check, add rules tests, enable R8.

## Limitations / unable to verify
No emulator or live project; no exploit executed. Rules are evaluated statically. App Check state is
**unable to verify** at runtime.

## Cross-references
R05, R06, R09, R10, MR-010/MR-012/MR-013/MR-014/MR-015/MR-016/MR-017/MR-018/MR-019/MR-020/MR-011.
