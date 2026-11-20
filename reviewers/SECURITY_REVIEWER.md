# R07 — Application Security Engineer

## 1. Reviewer Number
R07 (PRD story `RVW-007`, Priority P0). Depends on R01 for compilable source; rules analysis can start immediately.

## 2. Professional Title
Application Security Engineer / Mobile & Cloud Penetration Tester (OWASP MASVS, OWASP Top 10, Firebase rules review).

## 3. Required Expertise
- Firebase Security Rules (Firestore + Storage) semantics, `request.auth`, `diff()`, `get()` costs and bypass patterns.
- OWASP MASVS/MASTG: insecure storage, auth flaws, network, platform interaction, code quality.
- IDOR/BOLA, mass assignment, privilege escalation, race conditions, replay.
- Secret scanning, dependency/supply-chain risk, release hardening (minify, debuggable, backup, exported components).
- Threat modelling for money, identity documents and emergency features.

## 4. Responsibilities
1. Review every rule for unintended public read/write and field-level privilege escalation.
2. Verify collection coverage against all client-accessed collections.
3. Search the repository for secrets, keys, tokens and credentials, including history-sensitive artifacts.
4. Audit the manifest and build hardening (exported components, backup, cleartext, debuggable, minify).
5. Verify App Check, rate limiting and abuse controls on callables and the webhook.
6. Audit logging for PII/secret leakage.
7. Confirm client-side authorization is never the only gate.

## 5. Exact Features to Examine
- Firestore reads/writes for `jobs`, `messages`, `users`, `providers`, `worker_kyc`, `guarantors`, `guarantor_submissions`, `job_quotes`, `disputes`, `notifications`, `momo_transactions`, `momo_payouts`, `sosIncidents`.
- Storage paths: KYC, guarantors, certificates, verification, job photos, avatars, public assets.
- Authentication endpoints and identity flows (with R06).
- MoMo webhook and payment callables (with R03/R08).
- Android manifest, backup rules, build config, ProGuard/R8, secrets plugin.
- Third-party credentials and API keys.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `firestore.rules` (340 lines) | `jobs` and nested `messages` are `allow read: if true`; `momo_transactions` create open to any authenticated user |
| `storage.rules` (122 lines) | private KYC prefixes, job-photo metadata gates, public avatars |
| `app/src/main/AndroidManifest.xml` | permissions, `allowBackup="true"`, exported `MainActivity`, Maps meta-data |
| `app/src/main/java/com/example/data/remote/AfricaTalkingService.kt` | hardcoded API key line 15 |
| `app/src/main/java/com/example/data/remote/FirebaseService.kt` | Firestore/Storage write paths; deletion implementation |
| `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` | authorization helpers (`isQuoteSubmissionAuthorized`, `isRatingAuthorized`, `isSos*`) |
| `app/src/main/java/com/example/data/db/Entities.kt` | `doorstepPin = "4829"`, PII fields |
| `app/build.gradle.kts` | `isMinifyEnabled = false`, secrets plugin, App Check dependency |
| `gradle/libs.versions.toml` | dependency versions (firebase-appcheck-recaptcha declared) |
| `app/google-services.json` | committed Firebase config/API key |
| `app/src/test/java/com/example/data/remote/MoMoCredentialLeakSecurityTest.kt` | existing source scan (MTN only) |
| `.gitignore`, `.env.example` | secret hygiene |

## 7. Functional Requirements
- A signed-in user can read only their own account data and only job/message data they participate in.
- No unauthenticated or cross-tenant write can change job status, escrow, KYC outcome or wallet state.
- Rules must reject client writes to server-owned fields.
- Every deployed function must be deny-by-default for non-admin callers.

## 8. Security Requirements
- No hardcoded secrets in source, tests, Gradle files or committed artifacts; rotate anything found.
- No public read of location, phone numbers, PINs, chat content or identity documents.
- Doorstep PIN must be server-held (or stored hashed), rate-limited, and never trusted from a client-supplied expected value.
- App Check enforcement (or an accepted compensating control) on callables.
- Release build must enable R8/ProGuard, exclude debug keys and restrict backups.
- Dependency audit with no known critical/high CVEs unmitigated.

## 9. Testing Requirements
- Firebase Emulator rules unit tests for every collection and every actor (owner, participant, other user, admin, anonymous).
- Negative tests for each privilege-escalation hypothesis.
- Secret scan in CI (gitleaks/trufflehog) over full history.
- Static analysis (Android Lint, detekt) and dependency scan (`npm audit`, Gradle dependency check).
- Penetration test cases for IDOR on `jobs`, `messages`, `guarantors`, `momo_transactions`.

## 10. Expected Evidence
- Rules coverage matrix: collection × actor × operation × expected/actual.
- Secret scan report with rotated/revoked status.
- Manifest/build hardening checklist.
- Exploit reproductions (redacted) for each confirmed issue.
- Residual-risk register with owners and severities.

## 11. Passing Conditions
- PASS requires: zero public-PII reads, all secrets removed and rotated, rules tests green, App Check enforced, release hardened, and no open critical/high findings.
- FAIL now: `jobs` and `messages` are world-readable (address, phone, payment channel, chat), an Africa's Talking credential is committed, and the doorstep PIN is plaintext with a default and no lockout.
