# R07 — Application Security Engineer

| Field | Value |
|---|---|
| Reviewer | R07 — Application Security (P0) |
| Assignment | `reviewers/SECURITY_REVIEWER.md` (read in full) |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no rules, manifest or source changed |
| Rules execution | **UNVERIFIED** — no Firebase CLI/JDK/emulator; static rules analysis only |
| Verdict | **FAIL** |

## 1. Firestore rules coverage (static)

| Collection | Read | Write | Assessment |
|---|---|---|---|
| `jobs` | owner / assigned provider / admin | create binds `postedByUid`; update pinned to actor per transition | PII no longer public (REM-010 fixed); **escrow fields unconstrained** (F3) |
| `jobs/{id}/messages` | participant or author | create binds `senderId` | fixed from world-readable |
| `users/{uid}` | owner/admin | create/update owner (privileged fields locked); **no delete rule** | deletion impossible (F2) |
| `users/{uid}/consents` | owner/admin | `false` | correct |
| `worker_kyc` | owner/admin | create owner, update admin, **delete admin** | client cannot erase own KYC (F2) |
| `guarantors`, `guarantor_submissions` | owner/admin | constrained | no public read found |
| `momo_transactions` | owner/admin | create `false`; update admin | server-owned (REM-013) |
| `momo_payouts` | worker/admin | create/update admin | server-owned (but server writes use Admin SDK) |
| `refunds` | admin | `false` | server-owned |
| `disputes`, `notifications` | participant/owner | participant rules | reviewed spot-checks |
| `sosIncidents` | owner/provider/admin | owner create | not public |
| `admin_audit` | admin | `false` | immutable |
| `public`, `avatars`, `users/*/avatars` (Storage) | **public read** | admin/owner | avatars intentionally public (residual risk) |
| KYC/guarantor Storage | owner/admin | owner/admin | private (REM-014 fixed) |

No `allow read: if true` exists in `firestore.rules`; Storage has three public-read scopes
(`public/`, `avatars/`, `users/*/avatars/`).

## 2. Doorstep PIN (explicit FAIL condition)

`verifyDoorstepPin(jobId, enteredPin, expectedPin)` (`Repository.kt:785`) compares the entered PIN to a
**caller-supplied** expected value; the UI passes the stored job PIN (`MainScreen.kt:599`). A caller can
therefore supply `expectedPin == enteredPin` and pass without knowing the PIN. Additional problems:

- default PIN `"4829"` in the entity (`Entities.kt:259`);
- the PIN is stored in plaintext in Room and in the Firestore job document;
- no attempt counter or lockout anywhere (`pinAttempt|lockout` grep = 0);
- a successful check also mutates job status to IN_PROGRESS (`Repository.kt:798`), so a bypass moves
  the job, not just a flag.

R07's §8 requires the PIN to be server-held or hashed, rate-limited, and never compared against a
client-supplied value. All four requirements fail.

## 3. Escrow fields are client-writable (rules)

`jobs` update rules constrain `postedByUid` and the `status` transition, but **not `escrowStatus` or
any other field**. The owner can therefore write `escrowStatus = "RELEASED_TO_WORKER"` (or
`FUNDS_LOCKED_IN_ESCROW`) in the same update that moves `status` to `RELEASED` — forging payment state
at the rules layer. This is the server-side twin of R02's UI fabrication.

## 4. Secrets and history

- `secret_scan.mjs` passes; no Africa's Talking or MTN credential in `app/src` (the AT key was moved
  server-side, R05).
- `app/google-services.json` contains the Firebase client API key; that key is public by design and
  must be restricted by package/SHA in Google Cloud, which cannot be verified from the repository
  (**UNVERIFIED**).
- `.env.example` contains placeholders only. Full-history secret scanning (gitleaks/trufflehog) was
  not available; the repository's own scan is pattern-based.

## 5. Manifest and release hardening

| Control | State | Finding |
|---|---|---|
| `allowBackup` | `false` + backup rules | fixed |
| Exported components | `MainActivity` exported (launcher), service exported=false | acceptable |
| Cleartext | not enabled | pass |
| `isMinifyEnabled` | **false** | release unhardened (no R8/obfuscation) |
| `debuggable` | not set in release | pass |
| App Check | **absent** (no dependency, no `AppCheck` init) | callables/webhook rely on auth alone |

## 6. Abuse controls

- OTP: per-phone rate limit and attempt cap (R03), but no device/IP throttling; SMS pumping remains.
- Webhook: HMAC + timestamp + rate limit (R03) — good.
- Callables: no App Check, so any client with a valid token (or the public custom-token endpoint) can
  call them; admin callables still require the claim.

## 7. Logging and PII

- FirebaseService logs exception messages; sampled lines contain no phone/OTP/token values.
- FCM logs the full registration token (R05) — token leakage, medium.
- No `FLAG_SECURE` (R02) — identity/PIN screens screenshot-exposed.
- Analytics/crash reporting SDKs: none found in the inspected Gradle files.

## 8. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R07-P2-01 | **CRITICAL** | Doorstep PIN compared to a client-supplied `expectedPin`, default `4829`, plaintext, no lockout | `Repository.kt:785–798`; `Entities.kt:259`; `MainScreen.kt:599` |
| R07-P2-02 | **CRITICAL** | `users/{uid}` has no delete rule and `worker_kyc` delete is admin-only, so client-side account deletion cannot remove data while Auth is deleted anyway | `firestore.rules:207–232,64–69`; `ViewModel.kt:1372–1373` |
| R07-P2-03 | HIGH | `jobs` update rules do not pin `escrowStatus`; owner can forge payment state | `firestore.rules` jobs update block |
| R07-P2-04 | MEDIUM | No App Check on callables | grep = 0 |
| R07-P2-05 | MEDIUM | Release build not minified/obfuscated | `app/build.gradle.kts:45` |
| R07-P2-06 | MEDIUM | FCM token logged; no `FLAG_SECURE` | `SkillsExchangeMessagingService.kt:58`; R02 |
| R07-P2-07 | LOW | Public avatar reads expose user photos to anyone with a URL | `storage.rules:90–110` |

## 9. Verdict

**FAIL.** Major baseline issues (world-readable jobs/messages, committed AT key, public KYC storage)
are fixed, but the doorstep PIN is still client-trusted with a default and no lockout, deletion is
impossible under the rules while Auth is removed, and escrow state is not pinned by rules. Rules
behaviour and App Check enforcement are **UNVERIFIED** (no emulator/project).
