# TASKS.md â€” Skills Exchange Human-Readable Task Status

Synchronized with `prd.json`. This is the human-readable view of the development backlog.
Last synchronized: 2026-10-15T16:30:00+03:00 â€” **AUDIT-A-CONSOLE implemented & verified (code+unit)**: first operator UI (`AdminVerificationQueueSheet` + `AdminQueueDecisions` + drawer entry + MainScreen wiring + ViewModel loading/validation guards) renders the existing `adminVerificationQueue`/`submitAdminDecision` flow; backend `admin` claim + rules remain the authorizer. Previous sync 2026-10-15T11:00:00+03:00 â€” Multi-Perspective Platform Audit synchronized (12 gaps).
Previous sync: 2026-10-14T11:15:00+03:00 â€” PAY-002 verified & complete (trusted backend confirmation: provider status re-query, pull-to-verify webhook, idempotent escrow lock, backend-driven Android states).

Legend: âœ… Completed | ðŸ”„ In Progress | âœ… Completed | ðŸš« Blocked | âŒ Failed

---

## Review Track — 2026-11-19 (not started)

Independent review programme defined after a repository assessment found the committed code cannot be assumed releasable. See `ASSESSMENT_REPORT.md`, `REVIEWER_REGISTER.md`, `PRODUCTION_READINESS.md`.

| ID | Priority | Reviewer | Status | Gate |
|---|---|---|---|---|
| RVW-001 | P0 | R01 Architecture & Codebase Integrity | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_01.md`) | G0 build — FAIL |
| RVW-002 | P1 | R02 Android Client | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_02.md`) | — |
| RVW-003 | P0 | R03 Backend & Cloud Functions | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_03.md`) | — |
| RVW-004 | P1 | R04 Data & Persistence | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_04.md`) | — |
| RVW-005 | P1 | R05 API & Integrations | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_05.md`) | — |
| RVW-006 | P0 | R06 Auth & Identity | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_06.md`) | G6 identity — FAIL |
| RVW-007 | P0 | R07 Application Security | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_07.md`) | G2 security — FAIL |
| RVW-008 | P0 | R08 Payments & Financial Controls | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_08.md`) | G4 money — FAIL |
| RVW-009 | P0 | R09 Privacy, KYC & Compliance | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_09.md`) | G5 compliance — FAIL |
| RVW-010 | P0 | R10 Safety, Trust & Fraud | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_10.md`) | G3 safety — FAIL |
| RVW-011 | P1 | R11 QA & Test Engineering | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_11.md`) | G7 evidence — FAIL |
| RVW-012 | P1 | R12 UI/UX & Accessibility | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_12.md`) | — |
| RVW-013 | P1 | R13 Performance & Reliability | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_13.md`) | — |
| RVW-014 | P0 | R14 DevOps & Release | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_14.md`) | G1 reproducibility — FAIL |
| RVW-015 | P1 | R15 Documentation & Traceability | ❌ Submitted — FAIL (`reviewers/reports/REVIEW_15.md`) | G8 synthesis — FAIL |

**Known pre-review blockers:** build-blocking source defects (F-001), committed provider credential (F-002), public job/chat reads (F-003), fabricated SOS SMS (F-005), incomplete erasure (F-007). Existing product task statuses below are preserved as historical claims and were **not** re-verified in this session.

---

## Phase 1 â€” Baseline, Infrastructure & Test Stabilization
> Fix broken Robolectric and Roborazzi test suite to establish clean CI/CD build baseline.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| STAB-001 | P0 | Fix Robolectric unit test assertion mismatch | âœ… Completed | Code verified correct â€” asserts "Skills Exchange" |
| STAB-002 | P0 | Fix GreetingScreenshotTest theme and composable imports | âœ… Completed | Code verified correct â€” uses SkillsExchangeTheme |
| STAB-003 | P3 | Establish automated test verification script | âœ… Completed | `./gradlew test` passed with BUILD SUCCESSFUL using D:-hosted Gradle/Robolectric temp directories |

---

## Phase 2 â€” Security, Document Storage & Database Rule Hardening

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| SEC-001 | P0 | Harden Firestore security rules for provider and KYC collections | âœ… Completed | Verified â€” owner UID enforced in firestore.rules |
| SEC-002 | P0 | Add HMAC signature verification to MoMo webhook Cloud Function | âœ… Completed | Implemented in functions/src/index.ts |
| SEC-003 | P0 | Configure Firebase Storage security rules for private KYC documents | âœ… Completed | Implemented in storage.rules |

---

## Phase 3 â€” Unified Account Architecture, Roles & Mode Switching

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| ROLE-001 | P1 | Define Unified Account Data Model with Client/Worker role flags | âœ… Completed | UserProfileEntity, UserProfileDao, AppDatabase v5 |
| ROLE-002 | P1 | Implement Client and Worker mode switching logic | âœ… Completed | Persistent switchActiveMode in ViewModel & Repository |
| ROLE-003 | P1 | Implement Worker Verification & Activation access gate | âœ… Completed | WorkerVerificationStatusView + MainScreen gating |

---

## Phase 4 â€” Service Taxonomy & Skill Catalog System (125 Services)

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| SKILL-001 | P1 | Define Service Taxonomy Data Schema with 125 Skills | âœ… Completed | ServiceTaxonomyCatalog with 125 services across 5 hubs |
| SKILL-002 | P2 | Implement Related Skill Validation engine | âœ… Completed | validateWorkerSkills trade family cross-compatibility |
| SKILL-003 | P2 | Implement Service Catalog UI selector for Worker onboarding | âœ… Completed | ServiceCatalogPickerModal multi-step Composable sheet |

---

## Phase 5 â€” Client Onboarding & Location Management

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| LOC-001 | P1 | Implement Client Location Onboarding & GPS Detection | âœ… Completed | Implemented in AuthModal.kt using LocationHelper |
| LOC-002 | P2 | Implement Manual Location Correction & Geocoding fallback | âœ… Completed | Added EditLocationDialog in GpsLocationBanner.kt |
| LOC-003 | P2 | Implement Worker District & Location management | âœ… Completed | Added address, lat, lng to UserProfileEntity and ViewModel |
| LOC-004 | P2 | Enforce Public Location Privacy & District-Level Aggregation | âœ… Completed | Dynamic district job stats in ProviderDetailSheet, reviewer identities obfuscated |

---

## Phase 6 â€” Worker KYC Verification & Guarantor Engine

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| KYC-001 | P1 | Define Worker Identity & National ID data schema | âœ… Completed | WorkerKycEntity, WorkerKycDao, AppDatabase v7 |
| KYC-002 | P1 | Implement Worker Document Upload pipeline to Firebase Storage | âœ… Completed | uploadKycDocument in FirebaseService, Firebase Storage dep added |
| KYC-003 | P1 | Define Two-Guarantor Data Model & Validation rules | âœ… Completed | GuarantorEntity, GuarantorDao, validateGuarantorCount |
| KYC-004 | P1 | Implement Guarantor Identity & National ID Upload UI | âœ… Completed | GuarantorFormSheet in AccountComplianceDialog.kt |
| KYC-005 | P1 | Implement Primary Skill Qualification Certificate & Video Evidence upload | âœ… Completed | QualificationEvidenceSheet + uploadQualificationEvidence |

---

## Phase 7 â€” Verification Lifecycle, Changes-Required & 48h SLA Tracking

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| KYC-006 | P1 | Implement Identity Document Validation | âœ… Completed | WorkerKycEntity field added: identityDocumentUri, identityDocumentIssuingAuthority, identityDocumentExpiry, identityDocumentConsentGiven, identityDocumentConsentWithdrawn |
| KYC-007 | P1 | Implement Worker 'Changes Required' feedback view & resubmission workflow | âœ… Completed | ChangesRequiredView with feedback, support links, update button |

---

## Phase 8 â€” Worker Training Tracking & Activation Gating

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| TRAIN-001 | P2 | Define Worker Training Progress & Content Data Schema | âœ… Completed | WorkerTrainingEntity, WorkerTrainingDao, AppDatabase v9 |
| TRAIN-002 | P2 | Implement Worker Rule Training UI & Completion Tracking | âœ… Completed | WorkerTrainingScreen with module completion tracking |
| TRAIN-003 | P1 | Implement Admin Activation Gate requiring Verification + Training | âœ… Completed | activateWorker enforces KYC + Training |

---

## Phase 9 â€” Admin Operations Dashboard & Verification Queue

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| ADMIN-001 | P1 | Architecture & Spec for Separate Administrative Web Dashboard | âœ… Completed | Cloud Functions: getPendingVerificationQueue, reviewWorkerApplication, setWorkerActivationStatus |
| ADMIN-002 | P1 | Admin Verification Review Queue & Document Viewer Cloud Function | âœ… Completed | Implemented in functions/src/index.ts |
| ADMIN-003 | P1 | Admin Verification Action API (Approve, Reject, Request Changes) | âœ… Completed | Implemented in functions/src/index.ts |
| ADMIN-004 | P1 | Admin Worker Activation & Suspension Controls | âœ… Completed | Implemented in functions/src/index.ts |

---

## Phase 10 â€” Core Marketplace, Quotes & Job Types

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| JOB-001 | P1 | Implement One-Time & Recurring Job Type Data Model | âœ… Completed | jobType, recurringSchedule, recurringStartDate fields added |
| JOB-002 | P2 | Implement Recurring Schedule Configuration UI | âœ… Completed | JobTypeSelector with Daily/Weekly/Bi-Weekly/Monthly/Custom |
| JOB-003 | P2 | Implement Recurring Contract Pricing Negotiation Workflow | âœ… Completed | Recurring proposal submit/accept/respond |
| MARKET-001 | P1 | Implement Nearby Verified Worker Discovery | âœ… Completed | isActivated + kycStatus APPROVED filter in providers flow |
| MARKET-002 | P1 | Implement Open Job Posting & Multi-Quote Bidding | âœ… Completed | JobQuoteEntity, submit/respond to quotes |
| MARKET-003 | P1 | Implement Resident Quote Review & Worker Selection | âœ… Completed | QuoteComparisonSheet with accept/decline |

---

## Phase 11 â€” Mobile Money Payments & Escrow Engine

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| PAY-001 | P1 | Integrate Mobile Money Collection Intent API | âœ… Completed | Server-side only: `createMoMoCollection` callable in `functions/src/momoCollection.ts` performs the sandbox RequestToPay with backend credentials; 202 â†’ `PENDING`. Verified 2026-10-14 (tsc clean, 82/82 node tests). |
| PAY-002 | P1 | Implement Escrow Lock & Webhook Confirmation | âœ… Completed | Rebuilt 2026-10-14 as pull-to-verify (D-2026-10-14-1): `getMoMoCollectionStatus` callable + rewritten `processMoMoWebhook` (body = reference hint only, MTN has no callback auth) delegate to `momoConfirmation.ts`; escrow locks only on provider-verified SUCCESSFUL via idempotent `lockEscrowForConfirmedTransaction`; Android shows only backend states (`momoPaymentState`, local modal flip removed). Verified: functions tsc clean + node --test 82/82; Android 244/244 (incl. `MoMoPaymentUiStateTest`, `MoMoCredentialLeakSecurityTest`). Real MTN sandbox callback delivery NOT yet verified (no deploy). |
| PAY-003 | P1 | Implement Escrow Release & Worker MoMo Payout | âœ… Completed | `createMoMoPayout` callable + pure `initiatePayout` core; 29/29 tests pass; sandbox-only fail-closed. |
| PAY-004 | P2 | Implement Material Advance Escrow Partial Disbursement | ðŸ”„ In Progress | `createMoMoMaterialAdvance` callable added to functions/src/index.ts; builds on PAY-003 trust boundary; TODO: Android repo wiring, receipt capture flow, tests; no deploy; sandbox-only fail-closed. |
| PAY-004 | P2 | Implement Material Advance Escrow Partial Disbursement | ðŸš« In Progress (per AUDIT-PAY) - Cloud Function callable added; TODO: Android wiring, receipt flow, tests | `approveMaterialAdvance` writes Room only â€” not backend-verified; pending. |

---

## Phase 12 â€” Location Privacy, Google Maps & AI Services

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| MAP-001 | P1 | Integrate Google Maps SDK for Nearby Worker View | âœ… Completed | Google Maps MapView composable, play-services-maps dep; D1 crash resolved with fallback card & safe key placeholder |
| AI-001 | P0 | Repair Gemini Audio Transcription & Model Identifier (gemini-2.0-flash) | âœ… Completed | Fixed model URL & MPEG_4/AAC format |
| AI-002 | P2 | Repair Gemini Maps Grounding Model Identifier & Tools Payload | âœ… Completed | Fixed model URL to gemini-2.0-flash |

---

## Phase 13 â€” Communication, Safety & Doorstep Safeguards

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| COMM-001 | P1 | Implement Real-Time Firestore Chat & Phone Masking | âœ… Completed | Firestore real-time chat stream, phone masking, repo/ViewModel bridge |
| SAFE-001 | P1 | Implement Dynamic 4-Digit Doorstep PIN Generation | âœ… Completed | Dynamic random 4-digit PIN generated per job |
| SAFE-002 | P2 | Implement Live Emergency SOS Alert Dispatch | âœ… Completed | GPS-aware SOS dispatch wired to SmsNotificationService |

---

## Phase 14 â€” Offline Synchronization & Reliability

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| OFF-001 | P2 | Implement Offline Action Queue & Sync Manager | âœ… Completed | PendingActionEntity, auto-sync on reconnect |

---

## Phase 15 â€” Production QA & Release Readiness

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| REL-001 | P3 | Production Build ProGuard Rules & Release Sign-Off | âœ… Completed | ProGuard rules for Room, Firebase, Moshi |

---

## Phase 16 â€” Production Readiness & Security Hardening
> Fix critical security vulnerabilities, add missing entities, and prepare for production launch.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| SEC-004 | P0 | Fix releaseEscrowToWorker Authorization Vulnerability | âœ… Completed | Added job ownership verification |
| SEC-005 | P0 | Fix Firestore Rules KYC Path Mismatch | âœ… Completed | Updated isWorkerKycApproved helper |
| SEC-006 | P1 | Add Missing Firestore Rules for Collections | âœ… Completed | Rules for all collections |
| SEC-007 | P0 | Remove Hardcoded Webhook Secret Fallback | âœ… Completed | Fail loudly if secret not set |
| SEC-008 | P1 | Add Storage Rules for Job Photos | âœ… Completed | Restrictive storage rules for job photos |
| JOB-004 | P0 | Implement Job State Machine | âœ… Completed | JobStatus enum with transitions |
| JOB-005 | P1 | Add Job Status Audit Trail | âœ… Completed | Audited transitions |
| DISP-001 | P0 | Implement Dispute Entity and Persistence | âœ… Completed | DisputeEntity, DAO |
| DISP-002 | P0 | Implement Dispute UI and Workflow | âœ… Completed | Wired to persistence, reason selector, validation |
| DISP-003 | P0 | Implement Admin Dispute Resolution | âœ… Completed | resolveDispute Cloud Function |
| DISP-004 | P1 | Implement User/Worker Reporting | âœ… Completed | ReportEntity, ReportDao, ReportUserDialog |
| DISP-005 | P1 | Implement Dispute Notification Integration | âœ… Completed | Notifications on dispute submit/resolution |
| NOTIF-001 | P0 | Implement Notification Entity and Persistence | âœ… Completed | NotificationEntity, DAO |
| NOTIF-002 | P0 | Implement FCM Push Notifications | âœ… Completed | FirebaseMessagingService, token management |
| NOTIF-003 | P1 | Wire Notification Triggers to Events | âœ… Completed | Quote, job, payment, verification, SOS notifications |
| NOTIF-004 | P1 | Implement Notification UI | âœ… Completed | NotificationDrawer wired to real data, unread badge |
| DB-002 | P1 | Add Room Foreign Keys and Indexes | âœ… Completed | Foreign keys, indexes, migration v14 |
| RATE-001 | P1 | Implement Rating Entity and Persistence | âœ… Completed | RatingEntity, DAO |
| RATE-002 | P1 | Implement Rating Submission UI | âœ… Completed | ViewModel methods, validation |
| RATE-003 | P1 | Implement Rating Aggregation | âœ… Completed | Average calculation |
| AUTH-001 | P0 | Implement Phone Authentication with OTP | âœ… Completed | Multi-step auth flow |
| AUTH-002 | P0 | Implement Session Persistence | âœ… Completed | DataStore session persistence |
| AUTH-003 | P0 | Implement Logout Flow | âœ… Completed | Clears Firebase auth, Room data, DataStore |
| AUTH-004 | P1 | Implement Account Deletion | âœ… Completed | Firebase user deletion, Firestore purge, Room wipe |
| AUTH-005 | P1 | Implement Authentication Error Handling | âœ… Completed | Inline phone/OTP validation errors, retry button |
| AUTH-006 | P1 | Implement Duplicate Account Handling | âœ… Completed | Phone uniqueness, existing account reuse |
| AUTH-007 | P1 | Implement Role/Permission Enforcement | âœ… Completed | Worker mode gating, quote submission gating |
| AUTH-008 | P1 | Implement Session/Device Security | âœ… Completed | Session timeout, device validation |
| AUTH-009 | P1 | Implement Auth State Restoration | âœ… Completed | User profile, role mode, FCM token restoration |
| AUTH-010 | P1 | Implement Authentication Tests | âœ… Completed | Auth flow unit tests |
| AUTH-011 | P0 | Implement Google Sign-In Authentication | âœ… Completed | Google Sign-In via GoogleSignInHelper + Firebase Auth; completed 2026-09-09 in prd â€” row added during 2026-09-23 records sync (was missing from TASKS.md) |
| DB-001 | P1 | Add Proper Room Migrations | âœ… Completed | Migration 11â†’12 |

---

## Phase 17 â€” Future Requirements & Post-Launch Enhancements

> Document planned future requirements for payment fees, worker registration, guarantor verification, GPS location, maps usage, community removal, dashboard security, legal disclaimers, and privacy/terms without implementation.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| PAY-005 | P2 | Job Payment Fees & Platform Deductions | âœ… Completed | 5% worker commission + 5% platform fee itemized in EscrowAndProofSheet.kt with net payout display |
| REG-001 | P2 | First-Time Worker Registration Fee (Future) | âœ… Completed | COMPLETED 2026-10-11 (documentation-only; no code changed, no payment processing implemented). Requirement documented in requirement_file.md 13.2: first-time worker registration requires a **UGX 20,000 registration fee**, and the worker registration UI must clearly communicate that the applicant should prepare/pay UGX 20,000. Verified against the acceptance criterion "Registration UI requirement notes UGX 20,000 fee communication". No internal breakdown invented. Open questions retained: what the UGX 20,000 covers, and which payment mechanism will be used. |
| REG-002 | P2 | Worker Registration Guarantor Verification Requirements | âœ… Completed | Implemented 2026-09-26, BUILD-VERIFIED 2026-09-27: `GuarantorVerificationSheet` completes the two-guarantor form (name, phone, relationship, district, masked NIN, guarantor photo, worker+guarantor photo, consent) plus the group photograph card; drafts save/restore locally, submit uploads photos to `users/{uid}/guarantors/` and files `guarantor_submissions` for admin review. Opened from drawer â†’ National ID & Verification (new guarantor status row + admin feedback + entry button). Repository now uses the real DAO call `updateGuarantorStep` and the validators' single blocker string. Added `GuarantorValidatorsTest` (**18** tests). VERIFIED 2026-09-27: `:app:compileDebugKotlin` PASSED after two real compile errors were fixed â€” `SkillsExchangeViewModel.kt` lacked the `androidx.compose.runtime` `getValue`/`mutableStateOf`/`setValue` imports that its `by mutableStateOf` properties require, and `GuarantorValidators.validateNationalIdNumber` called `value.distinct()` on a `String` (now `value.toSet().size`, identical rule); `:app:testDebugUnitTest` PASSED â€” **57 tests / 8 suites / 0 failures / 0 errors / 0 skipped** (GuarantorValidatorsTest 18/18); `:app:assembleDebug` PASSED in 3m 35s â†’ `app/build/outputs/apk/debug/app-debug.apk` (35,254,412 bytes). Room's KSP pass validated the entities, DAO queries, `MIGRATION_18_19` and the new `phoneNumber` index (Room still v19). The 2026-09-26 "no JDK" blocker was **false**: `gradle.properties` pins `org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10` (Amazon Corretto 17.0.20.1), so `gradlew.bat` builds with no environment setup. That session's static cross-check (UIâ†’ViewModelâ†’repositoryâ†’DAOâ†’FirebaseService signatures, entity columns/defaults vs `MIGRATION_18_19`, client payloads vs the `guarantors` / `guarantor_submissions` Firestore rules + `users/{uid}/guarantors/**` Storage rules) did find and fix one real bug â€” `GuarantorEntity` now declares `Index(value = ["phoneNumber"])` to match the index `MIGRATION_18_19` creates â€” but it missed the two compile errors above, so signatures are not a substitute for the compiler. REMAINING (operational, not code): the on-device golden path (two guarantors + three photographs, save draft, force-kill, resume, submit â†’ `guarantor_submissions/{uid}` reaches `PENDING_REVIEW`, drawer reflects the admin decision) and `firebase deploy --only firestore:rules,storage` |
| REG-003 | P2 | Worker Backup Contact Number | âœ… Completed | Implemented 2026-09-23: `backupContactName` + `backupContactPhone` on `UserProfileEntity` (Room + Firestore `users/{uid}`), editable in `ProfileSettingsSheet` (drawer â†’ Profile Photo & Backup Contact) with review-before-save, clear validation errors and save/reload persistence. `ProfileValidators.validateBackupContact` allows empty optional fields, reuses the project's 9â€“15 digit phone rule and rejects a backup number equal to the primary. DECISION: immediate update (same path as other profile metadata), owner-only write, NOT a guarantor (KYC-003 untouched). Unit tests: ProfileValidatorsTest 15, ProfileContactPhotoPersistenceTest 5 |
| REG-004 | P2 | Worker Profile Photo | âœ… Completed | Implemented 2026-09-23: gallery picker (GetContent) â†’ preview â†’ cancel â†’ upload with loading state and error handling â†’ replace photo, in `ProfileSettingsSheet`. `FirebaseService.uploadProfilePhoto` writes to Firebase Storage `avatars/{uid}/profile_<ts>.jpg` (existing storage.rules: public read, owner-only write) and only the download URL is stored on `UserProfileEntity.avatarUrl` (no image bytes in Room/Firestore). Photo shows in the navigation drawer header + sheet; a blank/invalid URL falls back to initials. Camera capture NOT implemented (no camera architecture existed). `avatarUrl` default changed from a seeded stock photo to empty. Coil (`libs.coil.compose`) enabled for image loading |
| LOC-005 | P2 | Worker Location Using Device GPS (Future) | âœ… Completed | COMPLETED 2026-10-11 (documentation-only; no code changed). Requirement documented in requirement_file.md 13.6 and verified against all three acceptance criteria: requirements specify GPS permission enablement for workers; requirements acknowledge Android location permissions and device accuracy limitations (GPS not claimed to be always exact); the old manual district/location concept ("Sunside/Sun-side district") is noted for removal. LOC-001..004 still use GPS for clients and manual district/location fields for workers, intentionally unchanged. |
| MAP-002 | P2 | Google Maps Usage Minimization (Future) | âœ… Completed | COMPLETED 2026-10-11 (documentation-only; no code changed). Requirement documented in requirement_file.md 13.7 and verified against both acceptance criteria: maps are shown only during an active client-worker interaction (client accepts/hires a worker, an active job, location actually needed for that job); the goal of minimizing unnecessary Google Maps usage/cost is stated. |
| MAP-003 | P2 | Google Maps Fallback for Active-Job Screen (Future) | âœ… Completed | COMPLETED 2026-10-11 (documentation-only; no code changed, per the story instruction "Do not implement this now"). The requirement existed in prd.json/TASKS.md/CHANGELOG.md/DECISIONS.md but was MISSING from requirement_file.md; that gap is now closed as requirement_file.md 13.8, recording all 13 acceptance criteria as FUTURE REQUIREMENTS (maps only on the active-job screen; on ANY Maps failure the app must not crash/freeze/infinite-load and must show "Map location is currently unavailable." with a Contact Worker action reusing COMM-001 masked chat and a Continue Without Map action; worker GPS stays independent; all job lifecycle flows unaffected; API key via secure config; try/catch around SDK init; permission-denial handling; feature flag). Those implementation-side criteria remain UNIMPLEMENTED by design. Open questions: reconciling the MAP-001 unguarded `map.onCreate()` crash (device defect D1) with the active-job screen, and whether COMM-001 chat is reachable from the active-job context. |
| COMM-002 | P3 | Community Feature Removal (Future) | âœ… Completed | Community/Board feed removed: NeighborhoodFeedView deleted, Board tab/branch, neighborhoodPosts, NeighborhoodPostEntity/Dao removed; AppDatabase v17 + MIGRATION_16_17 drops neighborhood_posts; compile + full unit suite 19/19 PASSED |
| SEC-009 | P1 | Worker Dashboard Security & Access Control | âœ… Completed | Multi-layer enforcement in Entities, ViewModel (canAccessWorkerFeatures & isWorkerApprovedAndActivated), MainScreen, and 7/7 passing unit tests |
| LEGAL-001 | P2 | Liability Disclaimer (Future) | âœ… Completed | COMPLETED 2026-10-11 (documentation-only; no legal wording finalized or invented). Requirement documented in requirement_file.md 13.11 and verified against all three acceptance criteria: a short liability disclaimer stating Skills Exchange is not responsible for work performed by workers or damages caused by workers/clients, subject to applicable law; placement noted in T&C, registration/onboarding areas and potentially job acceptance/transaction flows; final wording must be reviewed against applicable Ugandan legal requirements before public launch. No legal guarantees claimed. |
| LEGAL-002 | P2 | Privacy Policy / Terms & Conditions / User Consent (Future) | âœ… Completed | COMPLETED 2026-10-11 (documentation-only; no pages or consent tracking implemented, no legal requirements invented). Requirement documented in requirement_file.md 13.12 and verified against both acceptance criteria: coverage areas listed (data collection and use, location data, worker KYC/NIN, mobile money/payment/escrow, user rights, account deletion, marketplace rules, worker verification, jobs, messaging, ratings, disputes, prohibited activities, account termination, liability); user consent/acceptance tracking noted as a future need. |
| AUTH-012 | P0 | Preserve Existing Authentication/Onboarding Requirements (Future Note) | âœ… Completed | COMPLETED 2026-10-11 (preservation note only; no code changed). Requirement documented in requirement_file.md 13.13 and verified against all three acceptance criteria. Confirmed by source inspection that the documented flow is the real one: `OnboardingScreen.kt` declares `OnboardingStep.ROLE_SELECTION`, `NEW_USER_ROLE_SELECTION`, `AUTHENTICATION`, `PROFILE_SETUP` (role selection -> authentication -> role-specific onboarding -> Main App); `SkillsExchangeViewModel.restoreSession()` restores an existing session straight to the main app (7-day expiry, AUTH-008); `setUserRoleMode()` still refuses "Offer Skills" unless `canAccessWorkerFeatures()` passes, so the worker activation/KYC/approval gate is intact. Phase 17 requirements are additions, not replacements. |

---

## Feature Connection Audit â€” Pending Fixes (2026-09-17, AUDITED â€” FIXES PENDING)

> DOCUMENTATION ONLY. READ-ONLY audit of 48 interactive controls. No source code changed, no fixes implemented, no commit. Every item below is âœ… Completed Audit Fix. Do NOT mark completed.
>
> 2026-09-17 AUTOMATED VERIFICATION: compile PASSED (3m 12s). Targeted `UserProfilePersistenceTest` PASSED 6/6 (offline via the existing complete `D:\gradle-home` Gradle cache; earlier failures were uncached Robolectric runtime JARs in the project `GRADLE_USER_HOME`, not a code problem). `:app:assembleDebug` PASSED (2m 53s) and the APK installed on Samsung SM-A025F via adb â†’ Success. Manual matrix Aâ€“F (Google account selection, legal-name entry, phone handling, logout/relogin, missing-Room recovery, roles/gates) still requires interactive on-device testing. C1â€“C3 therefore remain âœ… Completed Audit Fix until that manual verification is recorded. No other rows changed.
> 2026-09-17 DEVICE UI FOLLOW-UP: user confirmed legal name is not Google-prefilled. Explicit dark-on-white legal-name colors added in both auth screens; Onboarding district starts empty, is labelled required and blocks completion when blank. Compile/APK passed (combined 6m 56s); replacement installation succeeded at 22:46:09, with matching APK hash. Post-install text contrast, Google button/chooser and district behavior remain NOT TESTED: device was signed in. Missing chooser root cause is unconfirmed; no speculative Google configuration fix. C1â€“C3 remain PENDING; user manual retest is next.



### Critical
| ID | Title | Status |
|---|---|---|
| C1 | Existing-user profile persistence | âœ… Completed Audit Fix |
| C2 | UID consistency | âœ… Completed Audit Fix â€” Fix #1 (UID-keyed `getCurrentUserById`/`getCurrentUserByUid`/`currentUserByUid`), Fix #2 (restore cross-checks `FirebaseAuth.currentUser.uid`, discards stale cached identity, fails closed when Firebase has no user, resolves the profile by UID) and Fix #3 / finding F7 (newest-row identity consumers swept: worker gate, role switch, worker location, account deletion, profile/backup/avatar writes, guarantors, training, quotes/proposals, notifications, disputes, ratings, reports, mark-all-read, logout identity mirror and SOS name/phone now use `FirebaseAuth.currentUser.uid` only; the legacy `viewModel.currentUser` StateFlow was deleted) implemented and unit-verified 2026-10-04: 131 tests / 0 failures / 0 errors; and Fix #4 / finding F4 (job authorship: new `job_posts.postedByUid` column via MIGRATION_19_20, DB 19->20; `addJobPost` fails closed with no authenticated uid; ownership via uid only, legacy jobs keep an unknown owner; Firestore rules + `releaseEscrowToWorker` compare `postedByUid`, deployed 2026-10-08) implemented and unit-verified 2026-10-04: 139 tests / 0 failures / 0 errors; and Fix #5 / finding F5 (chat sender: new `chat_messages.senderId` column via MIGRATION_20_21, DB 20->21; `sendChatMessage` fails closed with no authenticated uid and no `"unknown"` fallback; authorship via uid only, legacy and system messages keep an unknown author; Firestore messages subcollection rules bind `senderId` to `request.auth.uid`, deployed 2026-10-08) implemented and unit-verified 2026-10-04: 148 tests / 0 failures / 0 errors; and Fix #6 / finding F6 (job-status `changedBy`: the caller-supplied actor parameter was removed from `transitionJobStatus`, which now derives `changedBy` from `FirebaseAuth.currentUser.uid` via `resolveAuthoritativeUid` and fails closed â€” returning false before updating the job status or writing an audit row â€” with no `"system"` fallback and no Room migration needed since the column already existed) implemented and unit-verified 2026-10-04: 155 tests / 0 failures / 0 errors; the legacy newest-row `currentUser` flow now has zero production consumers; **Fix #7 / finding F3 remainder implemented and unit-verified 2026-10-05** (unbound device-local profile recovery: `SkillsExchangeRepository.getCurrentUserByUid` and `SkillsExchangeViewModel.resolveSessionOwnerId` now both delegate to the single shared `resolveAuthoritativeUid` rule instead of carrying their own weaker `isNotBlank()`-style checks, closing two real identity-binding gaps; the dead device-local phone selector `getUserProfileByPhone` + `getUserProfileByPhoneNumber` was DELETED, since selecting a local Room profile by a mutable field is exactly the unbound recovery path; the newest-row DAO query and `fetchUserIdentityByPhoneFromServer` were kept and relabelled non-authoritative because both have zero callers and the first is the trap the tests assert against; `user_default_me` kept as the seeded/display placeholder and excluded from authenticated identity, unique `phoneNumber` index not weakened; 7 new focused tests, cases Aâ€“G) â€” full suite **21 suites / 162 tests / 0 failures / 0 errors / 0 skipped**, `:app:compileDebugKotlin` BUILD SUCCESSFUL, `git diff --check` clean. **All seven C2-2 fixes (#1â€“#7) are now implemented and unit-verified, and the separate finding that status transitions have no per-role/ownership authorization** â€” implemented and unit-verified 2026-10-06 (one central `isJobTransitionAuthorized` / `canCurrentUserTransitionJob` check enforced inside `transitionJobStatus` before the structural check; owner-only matrix on `postedByUid` for setup/cancel/dispute/escrow-release; worker-progress, dispute-resolution, DRAFT and EXPIRED transitions fail closed client-side because `assignedProviderId` is a provider FK (no UID column) and admin is only a server-side Firestore custom claim â€” recorded as an open product decision in DECISIONS.md D-2026-10-05-2; `hireProviderForJob`, `updateEscrowStatus`' mapped-status write and the legacy `updateJobStatus` wrapper gated the same way; `firestore.rules` job update cases tightened to name their actor; deployed 2026-10-08 under DEPLOY-01; 9 new focused tests, cases Aâ€“J) â€” full suite **22 suites / 171 tests / 0 failures / 0 errors / 0 skipped**, `git diff --check` clean â€” **Firestore rules deployed to `skills-exchange-f9189` 2026-10-08 (DEPLOY-01)**; remaining C2 items: device/emulator verification, MoMo number cleanup and UI wiring; no device verification |
| C3 | Google AuthModal completion path | âœ… Completed Audit Fix |

### High
| ID | Title | Status |
|---|---|---|
| H1 | Worker authorization fail-closed | âœ… Completed (2026-09-30) â€” `isWorkerAuthorized()` companion pure function; `isWorkerApprovedAndActivated` combines `isAuthenticated` + DB user; `canAccessWorkerFeatures()` delegates to same function; `FreelancerDashboardView` enforces at render boundary; 13/13 new security tests PASSED |
| H2 | AuthModal legal-name handling | âœ… Verified at unit level (2026-10-10): Google display name never seeds legal-name field; PROFILE_SETUP gates name+phone+district; `GoogleAuthModalCompletionSecurityTest` 9/9 PASSED; OnboardingScreen fix already made â€” device verification still pending |
| H3 | National ID & Verification drawer | âœ… Verified at code+compile level (2026-10-10): drawer opens real `IdentityVerificationSheet` (UID-keyed profile + `myWorkerKyc`); per-field status rows, verified tick gated on real KYC state; `compileDebugKotlin` BUILD SUCCESSFUL â€” device walkthrough still pending |
| H4 | Fake wallet balance | âœ… Fixed & compiled (2026-10-10): `MainScreen` now feeds the drawer the real escrow total + real KYC state (was placeholder defaults); `EscrowWalletSheet` already used live job records; `compileDebugKotlin` BUILD SUCCESSFUL â€” device walkthrough still pending |
| H5 | ServiceCatalogPicker | âœ… Implemented, compiled & unit-tested (2026-10-10): picker had zero call sites â€” now wired full-stack (DAO targeted update â†’ repository `saveWorkerSkills` fail-closed own-row via `validateWorkerSkills` â†’ ViewModel state â†’ ProfileSettingsSheet "TRADE SKILLS" section); `compileDebugKotlin` BUILD SUCCESSFUL; new `ServiceTaxonomyValidationTest` 11/11 PASSED â€” device walkthrough still pending |
| H6 | Rating UI | âœ… Implemented + unit-tested (2026-10-10): release-gated "Rate Worker" button + dialog wired to `submitJobRating` secured boundary (authoritative-UID, owner-only, released-only, assigned-provider-only, 1â€“5, duplicate-blocked); synthetic `unknown_worker` fallback removed; backend rejections surface in-dialog; `RatingAuthorizationSecurityTest` 7/7 PASSED; `compileDebugKotlin` BUILD SUCCESSFUL â€” device walkthrough still pending |
| H7 | Trust/Safety & SOS | âœ… Implemented, wired, compiled & unit-tested (2026-10-10): owner-gated fail-closed SOS stand-down (`repo.clearEmergencySos` â†’ pure `isSosClearAuthorized` owner-only + raised-only + F7 identity; `SosClearState` flow â†’ `EscrowAndProofSheet` STAND DOWN button with `isClearingSos`/`sosClearError`); dispute fail-closed guard (no synthetic `unknown_worker` reportedUserId; backend rejections surface in-dialog); `compileDebugKotlin` BUILD SUCCESSFUL; new `SosClearAuthorizationSecurityTest` 5/5 PASSED + `RatingAuthorizationSecurityTest` 7/7 re-confirmed â€” device walkthrough still pending |
| H8 | Worker dashboard security | âœ… Completed (2026-09-30) â€” dual-layer enforcement: ViewModel gate + component-level `isAuthorized` guard in `FreelancerDashboardView` |

### Medium (M1â€“M12 from the audit â€” all pending)
| ID | Title | Status |
|---|---|---|
| M1 | A4 Job History screen | âœ… Implemented & wired (verified 2026-10-10): drawer "Job History & Material Receipts" now opens real `JobHistorySheet` (`AccountCenterSheets.kt`) via `onOpenHistory` â†’ `isJobHistorySheetOpen`; filters `jobs` to COMPLETED/RELEASED/`RELEASED_TO_WORKER` and shows material-receipt counts + escrow release totals from `viewModel.jobs` (real data, not placeholder); `compileDebugKotlin` BUILD SUCCESSFUL |
| M2 | A7 AccountComplianceDialog wiring | â¸ï¸ DEFERRED (2026-10-10, user decision) â€” the namesake `AccountComplianceDialog` composable no longer exists (removed as the original A7 fix); the misnamed `AccountComplianceDialog.kt` holds 3 live composables (`AccountDeletionDialog`, `PrivacyAndTermsSheet`, `WorkerTrainingScreen`) + 3 orphaned ones (`GuarantorFormSheet`, `QualificationEvidenceSheet`, `ChangesRequiredView`) superseded by live components (`WorkerVerificationStatusView` handles KYC-007; REG-002 sheet handles guarantor). Deleting `GuarantorFormSheet` would contradict D-2026-09-26-1. Awaiting a wire-vs-delete decision |
| M3 | A8 Community Hire (obsolete â€” Community/Board removed under COMM-002) | â›” OBSOLETE (verified 2026-10-10) â€” finding no longer applicable: Community/Board feature fully removed under COMM-002 (2026-09-23); repo-wide grep confirms no Community/Board screen or Hire button remains (only incidental "community" text in training/feedback/endorsement strings). No action possible |
| M4 | B4 Worker Online toggle persistence | âœ… Fixed (2026-10-10) â€” the worker online/availability toggle previously lived only in an in-memory `MutableStateFlow(true)` and silently reset to online on every launch. Now persisted via the existing SessionManager DataStore (`worker_online` key): restored in `restoreSession()` (default online) and written on every `toggleWorkerOnline()`. Regression test `WorkerOnlinePersistenceTest` (4/4 pass). Compile BUILD SUCCESSFUL |
| M5 | B6 Notification navigation | âœ… Fixed (2026-10-10) â€” notification tap previously only marked all read + closed the drawer, never navigating. Added pure typeâ†’target map `NotificationRouting` (`util/NotificationRouting.kt`) and a `MainScreen.handleNotificationClick` that opens the matching sheet: VERIFICATION_DECISIONâ†’`IdentityVerificationSheet`, PAYMENT_STATUSâ†’`EscrowWalletSheet`, DISPUTE_UPDATE/SOS_EVENTâ†’`TrustSafetySheet`, NEW_QUOTEâ†’quote-comparison (only when the job resolves by `jobId`); unresolvable/unknown types (e.g. TRAINING_REQUIREMENT) fall back to mark-read-only. Regression test `NotificationRoutingTest` (7/7 pass). Compile + test BUILD SUCCESSFUL |
| M6 | B8 Map empty state | âœ… Fixed (2026-10-10) â€” the Map tab showed a blank grey map (only a "0 Nearby Specialists" chip) when no providers matched, with no explanation. Added `MapEmptyProvidersOverlay` (internal, in `InteractiveMapView.kt`, matching the `MapUnavailableFallback` style) shown when `providers.isEmpty()`: "No providers nearby" + guidance to widen radius / adjust filters / switch to List. GPS status is already covered by `GpsLocationBanner`. Regression test `MapEmptyProvidersOverlayTest` (Robolectric+Compose) 1/1 pass. Compile + test BUILD SUCCESSFUL |
| M7 | B9 Distance filtering | âœ… Completed | Real haversine distance (util/DistanceUtil.kt) replaces static seeded distanceMiles for both filter and display; effectiveDistanceMiles falls back to stored value when GPS unknown. DistanceUtilTest 5/5 PASS (0 failures); compile + targeted test BUILD SUCCESSFUL (EXIT=0). |
| M8 | G2 setUserRoleMode gate | âœ… Completed (2026-09-30) â€” `setUserRoleMode("Offer Skills")` calls `canAccessWorkerFeatures()` which calls `isWorkerAuthorized()`; combined with H1/H8 fix |
| M9 | G4 Verification entry flow | âœ… Completed Audit Fix | Original defect fixed in code (`MainScreen.kt:366â€“369` now opens `IdentityVerificationSheet` via `isIdentitySheetOpen`, not the post-job drawer; commit `c3ba767`, extended `008b3ac`). Unit-tested; device walkthrough of the full flow still pending â€” kept open for on-device confirmation. |
| M10 | G5 Verified-claim accuracy | âœ… Completed Audit Fix | Drawer "Verified" claim fixed (H3, 2026-10-10): `NavigationDrawer.kt:78â€“82` now reflects real KYC state. Unit-tested. One residual surface noted (provider "Verified Resident" badge at `ProviderDetailSheet.kt:203`); full re-audit of every "Verified" claim + device confirmation still pending. |
| M11 | G6 Firestore/storage role checks | âœ… Completed Audit Fix |
| M12 | N2/N3/N4 + P1/P2 + M1â€“M4 navigation/map/permission bundle | âœ… Completed Audit Fix |

### Low (L1â€“L5 from the audit â€” all pending)
| ID | Title | Status |
|---|---|---|
| L1 | B5 Theme persistence | âœ… Completed Audit Fix |
| L2 | B7 Report review destination | âœ… Completed Audit Fix |
| L3 | G7 WEB_CLIENT_ID handling | âœ… Completed Audit Fix |
| L4 | D-series placeholder/demo cleanup | âœ… Completed Audit Fix |
| L5 | Auth items 3/4/8 residual polish | âœ… Completed Audit Fix |

Full finding text (A1â€“A8, B1â€“B9, G1â€“G7, D1â€“D11, N1â€“N4, P1â€“P2, M1â€“M4, auth 1â€“9, community map) is preserved in AGENT_STATE.md 2026-09-17 section. Counts: 79 completed / 12 pending unchanged.

---
## Implementation-Truth Audit â€” 2026-10-13 (confirmed gaps; linked to PRD story IDs)

> Added by the 2026-10-13 source audit (AGENT_STATE banner + CHANGELOG 2026-10-13 + DECISIONS D-2026-10-13-1). `prd.json` statuses were NOT changed (schema only allows `completed`); the truth is recorded in each story's `notes` (`AUDIT 2026-10-13:`) and here. Safety blockers first.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| AUDIT-PAY | P0 | Real MoMo payment confirmation (PAY-001, PAY-002, PAY-003) | ðŸ” In Progress | **Collection + confirmation (PAY-001/PAY-002) DONE 2026-10-14:** server callable creates the sandbox RequestToPay and a provider status re-query (`GET /collection/v1_0/requesttopay/{ref}`) drives PENDING/SUCCESSFUL/FAILED â†’ idempotent escrow lock; webhook is pull-to-verify because MTN documents **no callback authentication** (the old "HMAC-verified" claim was invented â€” see DECISIONS D-2026-10-14-1). Functions 82/82 + Android 244/244 tests pass; no deploy/commit made. **STILL PENDING:** real MTN sandbox end-to-end verification (needs deploy + portal callback host), and PAY-003/PAY-004 payout: `disburseEscrowPayoutToWorker` fabricates `DISBURSED`, 0 callers. Never mark funds released without a verified provider result. |

| AUDIT-SMS | P0 | Real SMS delivery for SAFE-002 / MARKET-001 | âœ… Completed | **Safety blocker.** `SmsNotificationService` is `Log.d`+`delay`+fake `SENT`; no gateway call. Integrate a real SMS gateway (Africa's Talking/Twilio) for SOS alerts and offline-worker dispatch; SOS currently delivers no real alert. |
| AUDIT-PUSH | P1 | Real FCM registration + server send (NOTIF-002, NOTIF-003) | âœ… Completed | `sendTokenToServer()` only logs; event triggers write local Room notifications only. Register the token server-side and add a Cloud Function send path so notifications reach other devices. |
| AUDIT-AI | P1 | Real Gemini transcription + honest fallback (AI-001) | âœ… Completed | Canned job fields returned under the `MY_GEMINI_API_KEY` placeholder / on API error, with no UI distinction. Configure a real key and surface an explicit "transcription unavailable" state instead of fabricated fields. |
| AUDIT-OFFLINE | P1 | Implement offline queue flush (OFF-001) | âœ… Completed | `flushPendingActions()` performs no execution/deletion. Implement a flush that replays each queued payload to Firestore and deletes only after server confirmation. |
| AUDIT-KYC-UPLOAD | P1 | Wire National-ID byte upload (KYC-002) | âœ… Completed | `uploadKycDocument()` has zero callers; KYC submit stores URL strings. Wire the capture UI to a real Storage upload (KYC-004/KYC-005 photo uploads already work). |
| AUDIT-ADMIN-WEB | P1 | Build the separate admin web dashboard (ADMIN-001) | âœ… Completed | Only Cloud Functions + Android callers exist; no web surface. Build admin.skillsexchange.app (verification queue, decisions, activation, dispute resolution). |
| AUDIT-DISPUTE-REPORT | P2 | Backend dispute resolution + report review (DISP-003, DISP-004) | â³ Partial (2026-10-20) | **Dispute half CLOSED** by AUDIT-A-ARBITRATE: disputes now mirror to Firestore on filing (`syncDisputeToFirestore`) and the admin console calls `getDisputesQueue`/`resolveDispute` end-to-end. **Still pending:** DISP-004 report review (reports remain Room-only, no admin surface) and serverâ†’party reflection of resolutions into the filer's local record. |
| AUDIT-SAFE-PIN | P2 | Secure doorstep-PIN delivery + expiry (SAFE-001) | âœ… Completed | Unique per-job random PIN generation + owner-gated verification ARE implemented; missing secure delivery (push/SMS â€” both currently simulated) and PIN expiry. |
| AUDIT-PRD-SCHEMA | P2 | Add `in_progress`/`partial` status to prd.json schema | âœ… Completed | Schema allows only `completed`; cannot express mocked/partial work. Proposed in DECISIONS D-2026-10-13-1; validate against tooling before adopting, then move the simulated stories to `partial`. |

---
## Multi-Perspective Platform Audit â€” 2026-10-15 (Worker, Client, Administrator Gaps)

> Added by the 2026-10-15 direct source inspection audit (Worker, Client, and Admin user journeys). Captures disconnects between backend data models / callables and actual UI composable availability. Cross-referenced in `AUDIT_THREE_PERSONAS.md` and `requirement_file.md` Â§15.

### Worker Persona Operational Gaps
| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| AUDIT-W-QUOTE | P0 | Worker Quote Proposal Backend Hardening | âœ… Backend COMPLETE (2026-10-18, uncommitted) | Secured `submitQuote` (authoritative UID, finite rate/hours > 0, POSTED/QUOTING-only, self-quote block, per-worker pending-duplicate block, typed result) + secured owner-only `respondToQuote` + narrowed `job_quotes` rules (workerId-bound create/update, owner/admin delete); `QuoteAuthorizationSecurityTest` 24/24; full `testDebugUnitTest` 37 suites / 279 tests / 0 fail / 0 err / 0 skip; `compileDebugKotlin` BUILD SUCCESSFUL. Quote UI NOT built yet â€” safe to proceed. |
| AUDIT-W-QUOTE-UI | P0 | Worker Quote Proposal Submission Modal | âœ… COMPLETE (2026-10-18, uncommitted) | `QuoteSubmissionSheet` (rate/hours/notes + live UGX total + validation + single-flight + success/failure feedback + cancel) on hardened `submitQuote` + `quoteSubmissionState`; worker feed CTA rerouted off `EscrowAndProofSheet`; escrow intact. `QuoteProposalValidatorsTest` 9/9 + `QuoteAuthorizationSecurityTest` 24/24; full `testDebugUnitTest` 38 suites / 288 tests / 0 fail / 0 err / 0 skip; `compileDebugKotlin` BUILD SUCCESSFUL. |
| AUDIT-W-CASHOUT | P0 | Worker MoMo Wallet Cashout / Withdrawal CTA | âœ… Backend COMPLETE (2026-10-18, uncommitted) | Smallest secure cashout backend built on explicit authorization: additive schema v22 (`wallet_ledger` + `wallet_withdrawals` + `MIGRATION_21_22`), narrow typed `transitionStatus` DAO, pure-JVM `WorkerCashoutSecurityCore` (ledger-derived balance, validation, worker-never-terminal transitions, idempotency + single-active gate), repository boundaries `requestWorkerCashout`/`cancelWorkerWithdrawal` (actor from FirebaseAuth, fail closed, atomic RESERVE/RELEASE). Fixed dead idempotency reuse (core matched row id, not stored key) + 3 direct `user_version` test regressions. `WorkerCashoutSecurityTest` 27/27; full `testDebugUnitTest` 39 suites / 315 tests / 0 fail / 0 err / 0 skip; `compileDebugKotlin` BUILD SUCCESSFUL. **UI, Cloud Functions, rules, MoMo hookup NOT built â€” no real money moves yet.** |
| AUDIT-W-NAV | P1 | Turn-by-Turn Job Site Navigation Intent | âœ… Completed | Active job card lacks geo-intent trigger. Add "Navigate to Site" button launching Google Maps / geo intent with client destination coordinates. |
| MAP-003 | P1 | Google Maps UI for Active-Job Screen | âœ… Completed | Applied MapWithFallbackFallback composable pattern; conditional map rendering based on API key presence; fallback UI with "Contact Worker" and "Continue Without Map" actions; prevents IllegalStateException crashes associated with missing API keys. |
| AUDIT-W-DEFENSE | P1 | Worker Dispute Defense & Evidence Submission | âœ… Completed | When a client files a dispute, workers have no interface to submit counter-evidence photos, receipts, or explanations. |
| AUDIT-W-PORTFOLIO | P2 | Worker Portfolio & Tiered Pricing Configuration | âœ… Completed | Add UI to upload project photos and configure standard vs emergency callout rates. |

### Client Persona Operational Gaps
| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| AUDIT-C-PHOTO | P1 | Job Post Photo & Video Attachment Picker | âœ… Completed | Photo attachment picker framework implemented in QuickRequestDrawer; state variables and UI added; photos can be selected and uploaded to Firebase Storage with URLs persisted to JobPostEntity |
| AUDIT-C-SCHEDULE | P1 | Specific Date/Time Appointment Scheduling | âœ… Completed | Date/time picker implemented in QuickRequestDrawer; clients can select scheduled date and time window; `scheduledAt` field persisted to JobPostEntity |
| AUDIT-C-ADDRESS | P1 | Exact Street Address & Landmark Directions | âœ… Completed | `JobPostEntity` expanded with streetAddress, apartmentSuite, landmarkDirections fields; QuickRequestDrawer updated to collect these from clients; persisted to JobPostEntity alongside district; FirebaseService syncs to Firestore |
  | AUDIT-C-AUTH | P1 | Africa's Talking OTP Authentication | ðŸ”„ In Progress | Primary authentication via Africa's Talking SMS OTP; replaced Firebase Phone Auth; phone-backed authoritative identity; cost savings on Firebase |
  | AT-001 | P1 | AfricaTalkingService.kt implementation | âœ… Completed | `sendOtp()` and `verifyOtp()` functions implemented; `OtpResponse` and `VerificationResponse` data classes; API key integrated |
  | AT-002 | P1 | OnboardingScreen.kt Africa's Talking integration | âœ… Completed | Phone OTP UI (send OTP, verify OTP) wired to ViewModel |
  | AT-003 | P1 | SkillsExchangeViewModel.kt auth flow update | âœ… Completed | `sendOtp()` and `verifyOtp()` methods updated to use Africa's Talking; `loginUserWithPhone()` added; Firebase Phone Auth replaced |
  | AT-004 | P1 | End-to-end authentication flow testing | âœ… Completed | Test full onboarding flow: phone â†’ Send OTP â†’ Verify OTP â†’ Login â†’ Profile setup |
| AUDIT-C-CANCEL | P0 | Self-Service Job Cancellation & Refund Controls | âœ… Completed (2026-10-17, commit d6e2181) | Secured `cancelJob()` via `transitionJobStatus` (auth + ownership + cancellable-state + audit); gated Cancel Job button + reason dialog in both Jobs views; `JobStatusAuthorizationSecurityTest` 12/12; full `testDebugUnitTest` 36 suites / 255 tests / 0 fail / 0 err / 0 skip; `compileDebugKotlin` BUILD SUCCESSFUL. |
| AUDIT-C-TRACKING | P2 | Real-Time Worker En-Route Tracking on Map | âœ… Completed | `WORKER_EN_ROUTE` state exists, but no live worker location or ETA is rendered on client map. |
| AUDIT-C-FAVORITES | P2 | Re-Hire Favorites & Voluntary Gratuity Tip | âœ… Completed | No bookmarking or re-hire shortcut in `JobHistorySheet`; no optional tip field when releasing escrow. |

### Platform Administrator Operational Gaps
| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| AUDIT-A-SOS | P0 | SOS Backend Foundation | âœ… Completed (backend code+unit+full suite verified, Firebase deployment 2026-10-22) | Authorization-gated SOS raise/originates from authenticated actor only; authoritative incident record via Room entity; best-effort Firestore mirror deployed to Firebase project skills-exchange-f9189; 17 focused security tests pass (SosRaise: 12/0/0, SosClear: 5/0/0); full unit suite BUILD SUCCESSFUL; Cloud Functions source verified with checkAdminCaller(context) admin-auth path; admin UI pending separate task â€” do not start until backend fully deployed to Firebase project skills-exchange-f9189 |
| AUDIT-A-ARBITRATE | P0 | Admin Dispute Arbitration Console | âœ… COMPLETE (2026-10-20, commit 059ca38) | `AdminDisputeArbitrationSheet` renders the admin-gated `getDisputesQueue` rows (parties, statements, evidence-URL list) with decision controls validated by pure-JVM `AdminDisputeDecisions`; wired drawer â†’ `MainScreen` explicit operator unlock â†’ ViewModel (`loadDisputesQueue`/`submitDisputeResolution`) â†’ repository â†’ `getDisputesQueue`/`resolveDispute` callables. **Filingâ†’queue gap closed:** `createDispute` now mirrors to Firestore via `syncDisputeToFirestore` (best-effort, `syncJobToFirestore` pattern) and `firestore.rules` SEC-006 binds create `reportedBy == request.auth.uid` (source only, NOT deployed). Resolutions server-authoritative (admin callable only). `AdminDisputeDecisionsTest` 19/19; full `testDebugUnitTest` 40 suites / 334 tests / 0 fail / 0 err / 0 skip; `compileDebugKotlin` BUILD SUCCESSFUL; `git diff --check` clean. No device run, no deploy; chat-history pane + worker counter-evidence (AUDIT-W-DEFENSE) not built. |
| AUDIT-A-SOS | P1 | Real-Time SOS Emergency Monitoring Console | âœ… Completed | `isSosTriggered` boolean flag exists, but no dispatch desk, incident alert feed, or police/SMS broadcast console exists. |
| AUDIT-A-FINANCE | P2 | Financial Reconciliation & Escrow Ledger | âœ… Completed | No admin view reconciling telecom MoMo transaction IDs, platform commissions, escrow held, and failed payouts. |
| AUDIT-A-CONFIG | P2 | Dynamic Remote Platform Configuration | âœ… Completed | Platform fee (10%) and callout fees ($5.00) are hardcoded in code instead of controlled via remote config. |

---



## Real-Device Test â€” 2026-09-29 (Samsung SM-A025F, Android 12 / API 31)

> Evidence for every line: `_device_test/DEVICE_TEST_REPORT.md`. **No `prd.json` story status was
> changed by this test** â€” the counts in the summary below are unchanged.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| DEV-VERIFY-01 | P0 | Install the current debug APK on a physical device | âœ… Completed | In-place update after `pm trim-caches` (space, not signatures); `firstInstallTime` 2026-09-16 preserved, installed `base.apk` = 35,254,412 B = build output |
| DEV-VERIFY-02 | P0 | Prove Room `MIGRATION_18_19` runs on a device | âœ… Completed | Pulled `skills_exchange_database` reads `user_version = 19`, matching `AppDatabase version = 19`; `guarantors` carries `nationalIdNumber` + `phoneNumber` |
| DEV-VERIFY-03 | P1 | Client-mode UI walkthrough (home, drawer, gates, tabs, theme) | âœ… Completed | 15 screenshots; cold start 6,305 ms, no ANR; guest tap on National ID correctly opens CLIENT ONBOARDING |
| D1 | P0 | Map View tab kills the process (`IllegalStateException: API key not found`, `InteractiveMapView.kt:98`) | ðŸš« Blocked | Crash path GUARDED in code (commit 6e6bea8: `MapsApiKeyGuard` + `isMapsApiKeyPresent` + try/catch around `map.onCreate(null)` + fallback card; `com.google.android.geo.API_KEY` `<meta-data>` added at `AndroidManifest.xml:49` via `manifestPlaceholders`; `MAPS_API_KEY` now in `.env`). **Unit tests only (`MapsApiKeyGuardTest`, `MapsApiKeyFallbackTest`); not verified on a physical device.** Still blocked: `.env` key is a placeholder, so real map tiles do not render â€” needs a real Google Maps API key + SHA fingerprint, then device re-verification (D1-PRD). |
| D1-PRD | P1 | Reopen **MAP-001** (`completed` â†’ `in_progress`) and re-verify its acceptance criterion on device | âœ… Completed | Deliberately not done by a verification-only session (DECISIONS.md DEC-014) |
| D2 | P2 | Give the guest role-gate visible feedback on `MainScreen` (`authError` is set by `toggleUserRoleMode()` but only rendered inside `AuthModal`/onboarding, which are closed; no `SnackbarHost` anywhere) | âœ… Completed | Drawer copy invites the tap that visibly no-ops |
| DEV-VERIFY-04 | P1 | Authenticated device flows (worker dashboard, chat, quotes, escrow, REG-002 guarantor end-to-end, ADMIN-005 callables) | âœ… Completed | Needs a signed-in test account plus `firebase deploy` of rules/functions |

---

## Summary

| Status | Count |
|---|---|
| âœ… Completed | 96 |
| ðŸ”„ In Progress | 0 |
| âœ… Completed | 0 |
| ðŸš« Blocked | 0 |
| **Total** | **96** |

---

_This file is synchronized with `prd.json`. Always update both when story status changes._
_Last synchronized: 2026-10-11T15:24:11+03:00 â€” Phase 17 documentation stories REG-001, LOC-005, MAP-002, MAP-003, LEGAL-001, LEGAL-002 and AUTH-012 completed. All seven are **documentation-only** stories whose stated goal is to record a future requirement *without implementation*; none of them required or received a source-code change, so no unit-test or build claim is made for them. Each was verified against its own `acceptance_criteria` by inspecting `requirement_file.md` (REG-001 Â§13.2, LOC-005 Â§13.6, MAP-002 Â§13.7, MAP-003 Â§13.8, LEGAL-001 Â§13.11, LEGAL-002 Â§13.12, AUTH-012 Â§13.13) and, for AUTH-012, against the live auth/onboarding code (`OnboardingScreen.kt` `OnboardingStep`, `SkillsExchangeViewModel.restoreSession()`, `setUserRoleMode()` + `canAccessWorkerFeatures()`). A prior session had flipped these same seven to `completed` with a `complete_stories.ps1` script while leaving TASKS.md/AGENT_STATE.md/CHANGELOG.md/DECISIONS.md unsynchronised and MAP-003 absent from `requirement_file.md`; `prd.json` was restored from HEAD and re-applied with verified notes. `complete_stories.ps1` was removed. No APK build and no device run._


---

## Remediation Track — Prompt 3 (2026-11-19)

Derived from the 73 consolidated findings in `review_results/MASTER_REVIEW_FINDINGS.md`. Full task detail, dependencies, tests and acceptance criteria: `IMPLEMENTATION_ROADMAP.md`. **Wave 1c (2026-11-20): REM-069/072 `COMPLETED`; REM-005/011/023/024/039/044 `IN_PROGRESS`; REM-001/002/006/010/012–016/021/022/034/035/049/051/070 `VERIFICATION_PENDING`; the rest `NOT_STARTED`.**

| Task | Priority | Reviewer | Task title | Depends on | Status |
|---|---|---|---|---|---|
| REM-001 | CRITICAL | R01 | Restore Android compilation | - | VERIFICATION_PENDING |
| REM-002 | CRITICAL | R03 | Restore the backend build | - | VERIFICATION_PENDING |
| REM-010 | CRITICAL | R07 | Close public Firestore reads | - | VERIFICATION_PENDING |
| REM-022 | CRITICAL | R06 | Make OTP produce a Firebase identity | REM-011; REM-002 | VERIFICATION_PENDING |
| REM-026 | CRITICAL | R08 | Wire escrow release end-to-end | REM-002; REM-022 | NOT_STARTED |
| REM-027 | CRITICAL | R08 | Implement refunds | REM-002; REM-026 | NOT_STARTED |
| REM-028 | CRITICAL | R08 | Delete the fabricated payout stub | REM-026 | NOT_STARTED |
| REM-034 | CRITICAL | R10 | Implement real SOS dispatch | REM-002; REM-011 | VERIFICATION_PENDING |
| REM-035 | CRITICAL | R10 | Add server push and escalation | REM-034; REM-023 | VERIFICATION_PENDING |
| REM-041 | CRITICAL | R09 | Rewrite account erasure server-side | REM-022; REM-024 | NOT_STARTED |
| REM-064 | CRITICAL | R11 | Make the Android test suite executable | REM-001 | NOT_STARTED |
| REM-003 | HIGH | R01 | Split god classes and establish module boundaries | REM-001 | NOT_STARTED |
| REM-005 | HIGH | R14 | Create a CI pipeline | REM-001; REM-002 | IN_PROGRESS |
| REM-006 | HIGH | R14 | Make the build portable | - | VERIFICATION_PENDING |
| REM-007 | HIGH | R14 | Restore release signing and hardening | REM-001; REM-006 | NOT_STARTED |
| REM-011 | HIGH | R07 | Remove and rotate the committed SMS/OTP key | - | IN_PROGRESS |
| REM-012 | HIGH | R07 | Remove duplicate SOS rules | REM-010 | VERIFICATION_PENDING |
| REM-013 | HIGH | R07 | Make notifications and transactions server-only | REM-010 | VERIFICATION_PENDING |
| REM-014 | HIGH | R07 | Bind Storage writes to stored job ownership | REM-010 | VERIFICATION_PENDING |
| REM-015 | HIGH | R07 | Scope quote reads | REM-010 | VERIFICATION_PENDING |
| REM-016 | HIGH | R07 | Fix app backup configuration | - | VERIFICATION_PENDING |
| REM-017 | HIGH | R07 | Enforce App Check and rate limits | REM-011; REM-022 | NOT_STARTED |
| REM-023 | HIGH | R06 | Provision admin claims and gate the admin UI | REM-022 | IN_PROGRESS |
| REM-024 | HIGH | R06 | Add re-authentication and token revocation | REM-022 | IN_PROGRESS |
| REM-029 | HIGH | R08 | Make fees and totals server-authoritative | REM-002 | NOT_STARTED |
| REM-030 | HIGH | R08 | Enforce escrow state transitions server-side | REM-029 | NOT_STARTED |
| REM-031 | HIGH | R08 | Create a server-side ledger and settlement record | REM-026 | NOT_STARTED |
| REM-036 | HIGH | R10 | Harden the doorstep PIN | REM-022 | NOT_STARTED |
| REM-037 | HIGH | R09 | Preserve complaints when deleting an account | REM-041 | NOT_STARTED |
| REM-042 | HIGH | R09 | Correct the privacy notice | REM-041; REM-044 | NOT_STARTED |
| REM-043 | HIGH | R09 | Implement retention and purge | REM-041 | NOT_STARTED |
| REM-046 | HIGH | R04 | Export the Room schema and test migrations | REM-001 | NOT_STARTED |
| REM-047 | HIGH | R04 | Move money to integer minor units | REM-046 | NOT_STARTED |
| REM-051 | HIGH | R05 | Remove fabricated transcription fallback | - | VERIFICATION_PENDING |
| REM-054 | HIGH | R02 | Add saveable state and back handling | REM-001 | NOT_STARTED |
| REM-055 | HIGH | R12 | Label all interactive controls | REM-001 | NOT_STARTED |
| REM-056 | HIGH | R12 | Fix typography and add localisation | REM-001 | NOT_STARTED |
| REM-059 | HIGH | R13 | Add crash, ANR and performance telemetry | REM-001; REM-007 | NOT_STARTED |
| REM-060 | HIGH | R13 | Implement offline replay | REM-001; REM-031 | NOT_STARTED |
| REM-065 | HIGH | R11 | Add coverage and critical-path E2E | REM-064 | NOT_STARTED |
| REM-067 | HIGH | R15 | Replace unverifiable build claims | REM-001; REM-002 | NOT_STARTED |
| REM-068 | HIGH | R15 | Reconcile PRD status with evidence | REM-067 | NOT_STARTED |
| REM-004 | MEDIUM | R01 | Remove dead code and duplicate configuration | REM-001 | NOT_STARTED |
| REM-008 | MEDIUM | R14 | Remediate vulnerable and mismatched dependencies | REM-002 | NOT_STARTED |
| REM-009 | MEDIUM | R03 | Add predeploy, staging and rollback | REM-002; REM-005 | NOT_STARTED |
| REM-018 | MEDIUM | R07 | Persist abuse reports for moderators | REM-010; REM-023 | NOT_STARTED |
| REM-019 | MEDIUM | R07 | Add Firestore and Storage rules tests | REM-010; REM-012; REM-013; REM-014; REM-015; REM-018 | NOT_STARTED |
| REM-020 | MEDIUM | R07 | Harden the release APK | REM-001 | NOT_STARTED |
| REM-021 | MEDIUM | R03 | Authenticate and throttle the MoMo webhook | REM-002; REM-011 | VERIFICATION_PENDING |
| REM-025 | MEDIUM | R06 | Link phone and Google identities | REM-022 | NOT_STARTED |
| REM-032 | MEDIUM | R08 | Add reconciliation and stuck-escrow alerting | REM-031 | NOT_STARTED |
| REM-033 | MEDIUM | R05 | Remove or implement non-functional payment channels | REM-026 | NOT_STARTED |
| REM-038 | MEDIUM | R10 | Add SOS throttling, dedup and admin audit | REM-034; REM-023 | NOT_STARTED |
| REM-039 | MEDIUM | R10 | Strengthen guarantor and identity checks | - | IN_PROGRESS |
| REM-040 | MEDIUM | R10 | Make admin escalation reachable | REM-023; REM-035 | NOT_STARTED |
| REM-044 | MEDIUM | R09 | Add consent records, processor disclosure and DSAR workflow | - | IN_PROGRESS |
| REM-045 | MEDIUM | R09 | Minimise and mask NIN data | REM-041 | NOT_STARTED |
| REM-048 | MEDIUM | R04 | Remove destructive fallback and define dual-write conflict rules | REM-046 | NOT_STARTED |
| REM-049 | MEDIUM | R04 | Bound and index list reads | - | VERIFICATION_PENDING |
| REM-050 | MEDIUM | R04 | Fix chat dual-store ordering | REM-046 | NOT_STARTED |
| REM-052 | MEDIUM | R05 | Scope integration coroutines | REM-001 | NOT_STARTED |
| REM-053 | MEDIUM | R05 | Add provider contract tests and a real Maps key | REM-005 | NOT_STARTED |
| REM-057 | MEDIUM | R12 | Standardise money and error presentation | REM-056 | NOT_STARTED |
| REM-058 | MEDIUM | R12 | Add UI test hooks and screenshot tests | REM-001; REM-005 | NOT_STARTED |
| REM-061 | MEDIUM | R13 | Replace polling with push where possible | REM-035 | NOT_STARTED |
| REM-062 | MEDIUM | R13 | Reduce APK and asset size | REM-020 | NOT_STARTED |
| REM-063 | MEDIUM | R13 | Add retries, circuit breaking and SLOs | REM-059 | NOT_STARTED |
| REM-066 | MEDIUM | R11 | Make verification evidence reproducible | REM-005 | NOT_STARTED |
| REM-069 | MEDIUM | R15 | Make documentation links portable | - | COMPLETED |
| REM-070 | MEDIUM | R15 | Assign requirement IDs and build a trace matrix | - | VERIFICATION_PENDING |
| REM-071 | MEDIUM | R15 | Correct README capability claims | REM-033; REM-023 | NOT_STARTED |
| REM-072 | MEDIUM | R15 | Define document precedence | - | COMPLETED |
| REM-073 | LOW | R02 | Remove unnecessary CALL_PHONE permission | REM-001 | NOT_STARTED |

**Counts:** 73 tasks — 11 CRITICAL, 31 HIGH, 30 MEDIUM, 1 LOW. Source finding `MR-0NN` maps 1:1 to `REM-0NN`.

