# Requirement Traceability Matrix

REM-070: requirement IDs are assigned here so every implemented feature can be traced from the
product requirement to the code that implements it and the test that exercises it. Gaps are shown
as `NONE` rather than implied: a requirement without an automated test is still unimplemented from
a verification standpoint.

- **ID scheme:** `REQ-<AREA>-<NN>`; areas match `requirement_file.md` sections.
- **Source of requirements:** `requirement_file.md` (authority 1 — see `docs/DOCUMENT_MAP.md`).
- **Keeping this honest:** `scripts/check_traceability.mjs` validates the table shape, unique IDs,
  section coverage 3–10 and that every referenced path exists. It also prints the test-gap count.

| ID | Requirement (section) | Implementation | Automated test | Status |
|---|---|---|---|---|
| REQ-ACC-01 | Unified client/worker account and mode switching (§3) | `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt`, `app/src/main/java/com/example/ui/screens/MainScreen.kt` | `app/src/test/java/com/example/ui/viewmodel/WorkerAuthorizationSecurityTest.kt` | Implemented; test suite not executable here |
| REQ-ACC-02 | Role permission matrix enforced server-side (§3) | `firestore.rules`, `app/src/main/java/com/example/ui/screens/MainScreen.kt` | NONE | Partial: rules untested, admin claim unprovisioned |
| REQ-LOC-01 | Location minimisation and district-level discovery (§4) | `app/src/main/java/com/example/util/LocationHelper.kt`, `firestore.rules` | NONE | Partial |
| REQ-KYC-01 | Exactly two distinct guarantors with consent and photographs (§5.2) | `app/src/main/java/com/example/util/GuarantorValidators.kt`, `firestore.rules` | `app/src/test/java/com/example/util/GuarantorValidatorsTest.kt` | Implemented; suite not executable here |
| REQ-KYC-02 | NIN structural validation (§5.1) | `app/src/main/java/com/example/util/GuarantorValidators.kt` | `app/src/test/java/com/example/util/GuarantorValidatorsTest.kt` | Implemented |
| REQ-KYC-03 | Admin review queue with collusion flags (§5.4) | `functions/src/index.ts`, `functions/src/guarantorRisk.ts`, `functions/src/guarantorReview.ts` | `functions/test/guarantorRisk.test.mjs`, `functions/test/guarantorReview.test.mjs` | Server implemented; admin UI does not render flags yet |
| REQ-KYC-04 | Guarantor submission persistence (§5.3) | `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` | `app/src/test/java/com/example/data/GuarantorVerificationPersistenceTest.kt` | Implemented; suite not executable here |
| REQ-TRAIN-01 | Training completion gates worker activation (§6) | `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` | NONE | Partial |
| REQ-TAX-01 | 125-service taxonomy validation (§7) | `app/src/main/java/com/example/data/db/ServiceTaxonomy.kt` | `app/src/test/java/com/example/data/db/ServiceTaxonomyValidationTest.kt` | Implemented; suite not executable here |
| REQ-JOB-01 | Job lifecycle state machine and authorship (§9) | `firestore.rules`, `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` | `app/src/test/java/com/example/data/JobStatusAuthorizationSecurityTest.kt` | Implemented; suite not executable here |
| REQ-NOTIF-01 | SMS and FCM delivery for alerts (§10) | `functions/src/sms.ts`, `functions/src/sosDispatch.ts` | `functions/test/sms.test.mjs`, `functions/test/sosDispatch.test.mjs` | Implemented; provider credits/devices unavailable |
| REQ-PAY-01 | MoMo collection initiation (§9) | `functions/src/momoCollection.ts` | `functions/test/momoCollection.test.mjs` | Implemented; sandbox unavailable |
| REQ-PAY-02 | Callback-confirmed escrow funding (§9) | `functions/src/momoConfirmation.ts`, `functions/src/index.ts` | `functions/test/momoConfirmation.test.mjs`, `functions/test/webhookAuth.test.mjs` | Implemented; sandbox unavailable |
| REQ-PAY-03 | Worker payout and settlement status (§9) | `functions/src/momoDisbursement.ts` | `functions/test/momoDisbursement.test.mjs` | Implemented; provider endpoint unavailable |
| REQ-AUTH-01 | Phone OTP yields a Firebase identity (§3) | `functions/src/otp.ts`, `app/src/main/java/com/example/data/remote/AfricaTalkingService.kt` | `functions/test/otp.test.mjs`, `app/src/test/java/com/example/data/remote/PhoneAuthServiceFailClosedTest.kt` | Implemented; real-project signup test outstanding |
| REQ-AI-01 | Server-side voice transcription (§9) | `functions/src/transcription.ts`, `app/src/main/java/com/example/data/remote/GeminiAudioTranscriptionService.kt` | `functions/test/transcription.test.mjs` | Implemented; deployment unavailable |
| REQ-SAFE-01 | SOS raise/acknowledge lifecycle (§10) | `firestore.rules`, `functions/src/index.ts` | `app/src/test/java/com/example/data/SosRaiseAuthorizationSecurityTest.kt`, `functions/test/sosDispatch.test.mjs` | Dispatch implemented server-side; FCM/SMS not exercised |
| REQ-PRIV-01 | Consent receipts and DSAR export (§8) | `functions/src/compliance.ts`, `docs/privacy/DSAR.md`, `app/src/main/java/com/example/data/remote/FirebaseService.kt` | `functions/test/compliance.test.mjs` | Server and docs implemented; UI not wired |
| REQ-PAY-04 | Escrow release and refunds are server-authoritative (§7) | `functions/src/escrowRelease.ts`, `functions/src/refund.ts`, `functions/src/index.ts` | `functions/test/escrowRelease.test.mjs`, `functions/test/refund.test.mjs` | Policies tested; provider calls and client release path unverified |
| REQ-SESS-01 | Fresh authentication for destructive actions and token revocation (§3) | `functions/src/session.ts`, `firestore.rules`, `app/src/main/java/com/example/data/remote/FirebaseService.kt` | `functions/test/session.test.mjs` | Server/rules/client implemented; no project to verify token rejection |
| REQ-ADMIN-01 | Admin claim provisioning and server-verified operator gate (§10) | `scripts/grant-admin.js`, `functions/src/admin.ts`, `app/src/main/java/com/example/ui/screens/MainScreen.kt` | `functions/test/admin.test.mjs` | Server gate and audit trail implemented; live claim provisioning untested |
| REQ-SEC-01 | Private documents and job evidence (§8) | `storage.rules`, `firestore.rules` | NONE | Rules changed; emulator suite missing (REM-019) |
| REQ-DOC-01 | Documentation precedence and link integrity (§17) | `docs/DOCUMENT_MAP.md`, `docs/traceability.md` | `scripts/check_document_map.mjs`, `scripts/check_doc_links.mjs`, `scripts/check_traceability.mjs`, `scripts/check_readme_claims.mjs` | Implemented and checked |

## Known gaps

1. Four requirements have no automated test at all (`REQ-ACC-02`, `REQ-LOC-01`, `REQ-TRAIN-01`,
   `REQ-SEC-01`), and rule branches inside `REQ-KYC-01` are untested. REM-019 (rules suite) and
   REM-064 (executable Android suite) are the gating tasks.
2. Android tests below `app/src/test` exist but cannot run in this environment; "implemented" here
   means the code and test source exist, not that the test passed.
3. This matrix claims no requirement is *verified*; it records where each requirement lives.
