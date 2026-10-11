# TASKS.md — Skills Exchange Human-Readable Task Status

Synchronized with `prd.json`. This is the human-readable view of the development backlog.
Last synchronized: 2026-10-11T15:24:11+03:00 — Phase 17 documentation stories **REG-001, LOC-005, MAP-002, MAP-003, LEGAL-001, LEGAL-002, AUTH-012** completed (7 pending → 0). All seven are documentation-only stories; each was verified against its own acceptance criteria. MAP-003 was added to `requirement_file.md` (§13.8) where it had been missing, and the following sections were renumbered 13.8→13.9 … 13.12→13.13.

Legend: ✅ Completed | 🔄 In Progress | ⏳ Pending | 🚫 Blocked | ❌ Failed

---

## Phase 1 — Baseline, Infrastructure & Test Stabilization
> Fix broken Robolectric and Roborazzi test suite to establish clean CI/CD build baseline.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| STAB-001 | P0 | Fix Robolectric unit test assertion mismatch | ✅ Completed | Code verified correct — asserts "Skills Exchange" |
| STAB-002 | P0 | Fix GreetingScreenshotTest theme and composable imports | ✅ Completed | Code verified correct — uses SkillsExchangeTheme |
| STAB-003 | P3 | Establish automated test verification script | ✅ Completed | `./gradlew test` passed with BUILD SUCCESSFUL using D:-hosted Gradle/Robolectric temp directories |

---

## Phase 2 — Security, Document Storage & Database Rule Hardening

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| SEC-001 | P0 | Harden Firestore security rules for provider and KYC collections | ✅ Completed | Verified — owner UID enforced in firestore.rules |
| SEC-002 | P0 | Add HMAC signature verification to MoMo webhook Cloud Function | ✅ Completed | Implemented in functions/src/index.ts |
| SEC-003 | P0 | Configure Firebase Storage security rules for private KYC documents | ✅ Completed | Implemented in storage.rules |

---

## Phase 3 — Unified Account Architecture, Roles & Mode Switching

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| ROLE-001 | P1 | Define Unified Account Data Model with Client/Worker role flags | ✅ Completed | UserProfileEntity, UserProfileDao, AppDatabase v5 |
| ROLE-002 | P1 | Implement Client and Worker mode switching logic | ✅ Completed | Persistent switchActiveMode in ViewModel & Repository |
| ROLE-003 | P1 | Implement Worker Verification & Activation access gate | ✅ Completed | WorkerVerificationStatusView + MainScreen gating |

---

## Phase 4 — Service Taxonomy & Skill Catalog System (125 Services)

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| SKILL-001 | P1 | Define Service Taxonomy Data Schema with 125 Skills | ✅ Completed | ServiceTaxonomyCatalog with 125 services across 5 hubs |
| SKILL-002 | P2 | Implement Related Skill Validation engine | ✅ Completed | validateWorkerSkills trade family cross-compatibility |
| SKILL-003 | P2 | Implement Service Catalog UI selector for Worker onboarding | ✅ Completed | ServiceCatalogPickerModal multi-step Composable sheet |

---

## Phase 5 — Client Onboarding & Location Management

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| LOC-001 | P1 | Implement Client Location Onboarding & GPS Detection | ✅ Completed | Implemented in AuthModal.kt using LocationHelper |
| LOC-002 | P2 | Implement Manual Location Correction & Geocoding fallback | ✅ Completed | Added EditLocationDialog in GpsLocationBanner.kt |
| LOC-003 | P2 | Implement Worker District & Location management | ✅ Completed | Added address, lat, lng to UserProfileEntity and ViewModel |
| LOC-004 | P2 | Enforce Public Location Privacy & District-Level Aggregation | ✅ Completed | Dynamic district job stats in ProviderDetailSheet, reviewer identities obfuscated |

---

## Phase 6 — Worker KYC Verification & Guarantor Engine

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| KYC-001 | P1 | Define Worker Identity & National ID data schema | ✅ Completed | WorkerKycEntity, WorkerKycDao, AppDatabase v7 |
| KYC-002 | P1 | Implement Worker Document Upload pipeline to Firebase Storage | ✅ Completed | uploadKycDocument in FirebaseService, Firebase Storage dep added |
| KYC-003 | P1 | Define Two-Guarantor Data Model & Validation rules | ✅ Completed | GuarantorEntity, GuarantorDao, validateGuarantorCount |
| KYC-004 | P1 | Implement Guarantor Identity & National ID Upload UI | ✅ Completed | GuarantorFormSheet in AccountComplianceDialog.kt |
| KYC-005 | P1 | Implement Primary Skill Qualification Certificate & Video Evidence upload | ✅ Completed | QualificationEvidenceSheet + uploadQualificationEvidence |

---

## Phase 7 — Verification Lifecycle, Changes-Required & 48h SLA Tracking

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| KYC-006 | P1 | Implement Verification State Machine & 48h SLA Timestamps | ✅ Completed | State transition methods + getKycSlaDeadline(48h) |
| KYC-007 | P1 | Implement Worker 'Changes Required' feedback view & resubmission workflow | ✅ Completed | ChangesRequiredView with feedback, support links, update button |

---

## Phase 8 — Worker Training Tracking & Activation Gating

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| TRAIN-001 | P2 | Define Worker Training Progress & Content Data Schema | ✅ Completed | WorkerTrainingEntity, WorkerTrainingDao, AppDatabase v9 |
| TRAIN-002 | P2 | Implement Worker Rule Training UI & Completion Tracking | ✅ Completed | WorkerTrainingScreen with module completion tracking |
| TRAIN-003 | P1 | Implement Admin Activation Gate requiring Verification + Training | ✅ Completed | activateWorker enforces KYC + Training |

---

## Phase 9 — Admin Operations Dashboard & Verification Queue

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| ADMIN-001 | P1 | Architecture & Spec for Separate Administrative Web Dashboard | ✅ Completed | Cloud Functions: getPendingVerificationQueue, reviewWorkerApplication, setWorkerActivationStatus |
| ADMIN-002 | P1 | Admin Verification Review Queue & Document Viewer Cloud Function | ✅ Completed | Implemented in functions/src/index.ts |
| ADMIN-003 | P1 | Admin Verification Action API (Approve, Reject, Request Changes) | ✅ Completed | Implemented in functions/src/index.ts |
| ADMIN-004 | P1 | Admin Worker Activation & Suspension Controls | ✅ Completed | Implemented in functions/src/index.ts |

---

## Phase 10 — Core Marketplace, Quotes & Job Types

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| JOB-001 | P1 | Implement One-Time & Recurring Job Type Data Model | ✅ Completed | jobType, recurringSchedule, recurringStartDate fields added |
| JOB-002 | P2 | Implement Recurring Schedule Configuration UI | ✅ Completed | JobTypeSelector with Daily/Weekly/Bi-Weekly/Monthly/Custom |
| JOB-003 | P2 | Implement Recurring Contract Pricing Negotiation Workflow | ✅ Completed | Recurring proposal submit/accept/respond |
| MARKET-001 | P1 | Implement Nearby Verified Worker Discovery | ✅ Completed | isActivated + kycStatus APPROVED filter in providers flow |
| MARKET-002 | P1 | Implement Open Job Posting & Multi-Quote Bidding | ✅ Completed | JobQuoteEntity, submit/respond to quotes |
| MARKET-003 | P1 | Implement Resident Quote Review & Worker Selection | ✅ Completed | QuoteComparisonSheet with accept/decline |

---

## Phase 11 — Mobile Money Payments & Escrow Engine

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| PAY-001 | P1 | Integrate Mobile Money Collection Intent API | ✅ Completed | Retrofit + MTN MoMo Collection API integration |
| PAY-002 | P1 | Implement Escrow Lock & Webhook Confirmation | ✅ Completed | processMoMoWebhook locks escrow + notifications |
| PAY-003 | P1 | Implement Escrow Release & Worker MoMo Payout | ✅ Completed | releaseEscrowToWorker Cloud Function, worker payout |
| PAY-004 | P2 | Implement Material Advance Escrow Partial Disbursement | ✅ Completed | approveMaterialAdvance method, 50% disbursement logic |

---

## Phase 12 — Location Privacy, Google Maps & AI Services

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| MAP-001 | P1 | Integrate Google Maps SDK for Nearby Worker View | ✅ Completed | Google Maps MapView composable, play-services-maps dep; D1 crash resolved with fallback card & safe key placeholder |
| AI-001 | P0 | Repair Gemini Audio Transcription & Model Identifier (gemini-2.0-flash) | ✅ Completed | Fixed model URL & MPEG_4/AAC format |
| AI-002 | P2 | Repair Gemini Maps Grounding Model Identifier & Tools Payload | ✅ Completed | Fixed model URL to gemini-2.0-flash |

---

## Phase 13 — Communication, Safety & Doorstep Safeguards

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| COMM-001 | P1 | Implement Real-Time Firestore Chat & Phone Masking | ✅ Completed | Firestore real-time chat stream, phone masking, repo/ViewModel bridge |
| SAFE-001 | P1 | Implement Dynamic 4-Digit Doorstep PIN Generation | ✅ Completed | Dynamic random 4-digit PIN generated per job |
| SAFE-002 | P2 | Implement Live Emergency SOS Alert Dispatch | ✅ Completed | GPS-aware SOS dispatch wired to SmsNotificationService |

---

## Phase 14 — Offline Synchronization & Reliability

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| OFF-001 | P2 | Implement Offline Action Queue & Sync Manager | ✅ Completed | PendingActionEntity, auto-sync on reconnect |

---

## Phase 15 — Production QA & Release Readiness

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| REL-001 | P3 | Production Build ProGuard Rules & Release Sign-Off | ✅ Completed | ProGuard rules for Room, Firebase, Moshi |

---

## Phase 16 — Production Readiness & Security Hardening
> Fix critical security vulnerabilities, add missing entities, and prepare for production launch.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| SEC-004 | P0 | Fix releaseEscrowToWorker Authorization Vulnerability | ✅ Completed | Added job ownership verification |
| SEC-005 | P0 | Fix Firestore Rules KYC Path Mismatch | ✅ Completed | Updated isWorkerKycApproved helper |
| SEC-006 | P1 | Add Missing Firestore Rules for Collections | ✅ Completed | Rules for all collections |
| SEC-007 | P0 | Remove Hardcoded Webhook Secret Fallback | ✅ Completed | Fail loudly if secret not set |
| SEC-008 | P1 | Add Storage Rules for Job Photos | ✅ Completed | Restrictive storage rules for job photos |
| JOB-004 | P0 | Implement Job State Machine | ✅ Completed | JobStatus enum with transitions |
| JOB-005 | P1 | Add Job Status Audit Trail | ✅ Completed | Audited transitions |
| DISP-001 | P0 | Implement Dispute Entity and Persistence | ✅ Completed | DisputeEntity, DAO |
| DISP-002 | P0 | Implement Dispute UI and Workflow | ✅ Completed | Wired to persistence, reason selector, validation |
| DISP-003 | P0 | Implement Admin Dispute Resolution | ✅ Completed | resolveDispute Cloud Function |
| DISP-004 | P1 | Implement User/Worker Reporting | ✅ Completed | ReportEntity, ReportDao, ReportUserDialog |
| DISP-005 | P1 | Implement Dispute Notification Integration | ✅ Completed | Notifications on dispute submit/resolution |
| NOTIF-001 | P0 | Implement Notification Entity and Persistence | ✅ Completed | NotificationEntity, DAO |
| NOTIF-002 | P0 | Implement FCM Push Notifications | ✅ Completed | FirebaseMessagingService, token management |
| NOTIF-003 | P1 | Wire Notification Triggers to Events | ✅ Completed | Quote, job, payment, verification, SOS notifications |
| NOTIF-004 | P1 | Implement Notification UI | ✅ Completed | NotificationDrawer wired to real data, unread badge |
| DB-002 | P1 | Add Room Foreign Keys and Indexes | ✅ Completed | Foreign keys, indexes, migration v14 |
| RATE-001 | P1 | Implement Rating Entity and Persistence | ✅ Completed | RatingEntity, DAO |
| RATE-002 | P1 | Implement Rating Submission UI | ✅ Completed | ViewModel methods, validation |
| RATE-003 | P1 | Implement Rating Aggregation | ✅ Completed | Average calculation |
| AUTH-001 | P0 | Implement Phone Authentication with OTP | ✅ Completed | Multi-step auth flow |
| AUTH-002 | P0 | Implement Session Persistence | ✅ Completed | DataStore session persistence |
| AUTH-003 | P0 | Implement Logout Flow | ✅ Completed | Clears Firebase auth, Room data, DataStore |
| AUTH-004 | P1 | Implement Account Deletion | ✅ Completed | Firebase user deletion, Firestore purge, Room wipe |
| AUTH-005 | P1 | Implement Authentication Error Handling | ✅ Completed | Inline phone/OTP validation errors, retry button |
| AUTH-006 | P1 | Implement Duplicate Account Handling | ✅ Completed | Phone uniqueness, existing account reuse |
| AUTH-007 | P1 | Implement Role/Permission Enforcement | ✅ Completed | Worker mode gating, quote submission gating |
| AUTH-008 | P1 | Implement Session/Device Security | ✅ Completed | Session timeout, device validation |
| AUTH-009 | P1 | Implement Auth State Restoration | ✅ Completed | User profile, role mode, FCM token restoration |
| AUTH-010 | P1 | Implement Authentication Tests | ✅ Completed | Auth flow unit tests |
| AUTH-011 | P0 | Implement Google Sign-In Authentication | ✅ Completed | Google Sign-In via GoogleSignInHelper + Firebase Auth; completed 2026-09-09 in prd — row added during 2026-09-23 records sync (was missing from TASKS.md) |
| DB-001 | P1 | Add Proper Room Migrations | ✅ Completed | Migration 11→12 |

---

## Phase 17 — Future Requirements & Post-Launch Enhancements

> Document planned future requirements for payment fees, worker registration, guarantor verification, GPS location, maps usage, community removal, dashboard security, legal disclaimers, and privacy/terms without implementation.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| PAY-005 | P2 | Job Payment Fees & Platform Deductions | ✅ Completed | 5% worker commission + 5% platform fee itemized in EscrowAndProofSheet.kt with net payout display |
| REG-001 | P2 | First-Time Worker Registration Fee (Future) | ✅ Completed | COMPLETED 2026-10-11 (documentation-only; no code changed, no payment processing implemented). Requirement documented in requirement_file.md 13.2: first-time worker registration requires a **UGX 20,000 registration fee**, and the worker registration UI must clearly communicate that the applicant should prepare/pay UGX 20,000. Verified against the acceptance criterion "Registration UI requirement notes UGX 20,000 fee communication". No internal breakdown invented. Open questions retained: what the UGX 20,000 covers, and which payment mechanism will be used. |
| REG-002 | P2 | Worker Registration Guarantor Verification Requirements | ✅ Completed | Implemented 2026-09-26, BUILD-VERIFIED 2026-09-27: `GuarantorVerificationSheet` completes the two-guarantor form (name, phone, relationship, district, masked NIN, guarantor photo, worker+guarantor photo, consent) plus the group photograph card; drafts save/restore locally, submit uploads photos to `users/{uid}/guarantors/` and files `guarantor_submissions` for admin review. Opened from drawer → National ID & Verification (new guarantor status row + admin feedback + entry button). Repository now uses the real DAO call `updateGuarantorStep` and the validators' single blocker string. Added `GuarantorValidatorsTest` (**18** tests). VERIFIED 2026-09-27: `:app:compileDebugKotlin` PASSED after two real compile errors were fixed — `SkillsExchangeViewModel.kt` lacked the `androidx.compose.runtime` `getValue`/`mutableStateOf`/`setValue` imports that its `by mutableStateOf` properties require, and `GuarantorValidators.validateNationalIdNumber` called `value.distinct()` on a `String` (now `value.toSet().size`, identical rule); `:app:testDebugUnitTest` PASSED — **57 tests / 8 suites / 0 failures / 0 errors / 0 skipped** (GuarantorValidatorsTest 18/18); `:app:assembleDebug` PASSED in 3m 35s → `app/build/outputs/apk/debug/app-debug.apk` (35,254,412 bytes). Room's KSP pass validated the entities, DAO queries, `MIGRATION_18_19` and the new `phoneNumber` index (Room still v19). The 2026-09-26 "no JDK" blocker was **false**: `gradle.properties` pins `org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10` (Amazon Corretto 17.0.20.1), so `gradlew.bat` builds with no environment setup. That session's static cross-check (UI→ViewModel→repository→DAO→FirebaseService signatures, entity columns/defaults vs `MIGRATION_18_19`, client payloads vs the `guarantors` / `guarantor_submissions` Firestore rules + `users/{uid}/guarantors/**` Storage rules) did find and fix one real bug — `GuarantorEntity` now declares `Index(value = ["phoneNumber"])` to match the index `MIGRATION_18_19` creates — but it missed the two compile errors above, so signatures are not a substitute for the compiler. REMAINING (operational, not code): the on-device golden path (two guarantors + three photographs, save draft, force-kill, resume, submit → `guarantor_submissions/{uid}` reaches `PENDING_REVIEW`, drawer reflects the admin decision) and `firebase deploy --only firestore:rules,storage` |
| REG-003 | P2 | Worker Backup Contact Number | ✅ Completed | Implemented 2026-09-23: `backupContactName` + `backupContactPhone` on `UserProfileEntity` (Room + Firestore `users/{uid}`), editable in `ProfileSettingsSheet` (drawer → Profile Photo & Backup Contact) with review-before-save, clear validation errors and save/reload persistence. `ProfileValidators.validateBackupContact` allows empty optional fields, reuses the project's 9–15 digit phone rule and rejects a backup number equal to the primary. DECISION: immediate update (same path as other profile metadata), owner-only write, NOT a guarantor (KYC-003 untouched). Unit tests: ProfileValidatorsTest 15, ProfileContactPhotoPersistenceTest 5 |
| REG-004 | P2 | Worker Profile Photo | ✅ Completed | Implemented 2026-09-23: gallery picker (GetContent) → preview → cancel → upload with loading state and error handling → replace photo, in `ProfileSettingsSheet`. `FirebaseService.uploadProfilePhoto` writes to Firebase Storage `avatars/{uid}/profile_<ts>.jpg` (existing storage.rules: public read, owner-only write) and only the download URL is stored on `UserProfileEntity.avatarUrl` (no image bytes in Room/Firestore). Photo shows in the navigation drawer header + sheet; a blank/invalid URL falls back to initials. Camera capture NOT implemented (no camera architecture existed). `avatarUrl` default changed from a seeded stock photo to empty. Coil (`libs.coil.compose`) enabled for image loading |
| LOC-005 | P2 | Worker Location Using Device GPS (Future) | ✅ Completed | COMPLETED 2026-10-11 (documentation-only; no code changed). Requirement documented in requirement_file.md 13.6 and verified against all three acceptance criteria: requirements specify GPS permission enablement for workers; requirements acknowledge Android location permissions and device accuracy limitations (GPS not claimed to be always exact); the old manual district/location concept ("Sunside/Sun-side district") is noted for removal. LOC-001..004 still use GPS for clients and manual district/location fields for workers, intentionally unchanged. |
| MAP-002 | P2 | Google Maps Usage Minimization (Future) | ✅ Completed | COMPLETED 2026-10-11 (documentation-only; no code changed). Requirement documented in requirement_file.md 13.7 and verified against both acceptance criteria: maps are shown only during an active client-worker interaction (client accepts/hires a worker, an active job, location actually needed for that job); the goal of minimizing unnecessary Google Maps usage/cost is stated. |
| MAP-003 | P2 | Google Maps Fallback for Active-Job Screen (Future) | ✅ Completed | COMPLETED 2026-10-11 (documentation-only; no code changed, per the story instruction "Do not implement this now"). The requirement existed in prd.json/TASKS.md/CHANGELOG.md/DECISIONS.md but was MISSING from requirement_file.md; that gap is now closed as requirement_file.md 13.8, recording all 13 acceptance criteria as FUTURE REQUIREMENTS (maps only on the active-job screen; on ANY Maps failure the app must not crash/freeze/infinite-load and must show "Map location is currently unavailable." with a Contact Worker action reusing COMM-001 masked chat and a Continue Without Map action; worker GPS stays independent; all job lifecycle flows unaffected; API key via secure config; try/catch around SDK init; permission-denial handling; feature flag). Those implementation-side criteria remain UNIMPLEMENTED by design. Open questions: reconciling the MAP-001 unguarded `map.onCreate()` crash (device defect D1) with the active-job screen, and whether COMM-001 chat is reachable from the active-job context. |
| COMM-002 | P3 | Community Feature Removal (Future) | ✅ Completed | Community/Board feed removed: NeighborhoodFeedView deleted, Board tab/branch, neighborhoodPosts, NeighborhoodPostEntity/Dao removed; AppDatabase v17 + MIGRATION_16_17 drops neighborhood_posts; compile + full unit suite 19/19 PASSED |
| SEC-009 | P1 | Worker Dashboard Security & Access Control | ✅ Completed | Multi-layer enforcement in Entities, ViewModel (canAccessWorkerFeatures & isWorkerApprovedAndActivated), MainScreen, and 7/7 passing unit tests |
| LEGAL-001 | P2 | Liability Disclaimer (Future) | ✅ Completed | COMPLETED 2026-10-11 (documentation-only; no legal wording finalized or invented). Requirement documented in requirement_file.md 13.11 and verified against all three acceptance criteria: a short liability disclaimer stating Skills Exchange is not responsible for work performed by workers or damages caused by workers/clients, subject to applicable law; placement noted in T&C, registration/onboarding areas and potentially job acceptance/transaction flows; final wording must be reviewed against applicable Ugandan legal requirements before public launch. No legal guarantees claimed. |
| LEGAL-002 | P2 | Privacy Policy / Terms & Conditions / User Consent (Future) | ✅ Completed | COMPLETED 2026-10-11 (documentation-only; no pages or consent tracking implemented, no legal requirements invented). Requirement documented in requirement_file.md 13.12 and verified against both acceptance criteria: coverage areas listed (data collection and use, location data, worker KYC/NIN, mobile money/payment/escrow, user rights, account deletion, marketplace rules, worker verification, jobs, messaging, ratings, disputes, prohibited activities, account termination, liability); user consent/acceptance tracking noted as a future need. |
| AUTH-012 | P0 | Preserve Existing Authentication/Onboarding Requirements (Future Note) | ✅ Completed | COMPLETED 2026-10-11 (preservation note only; no code changed). Requirement documented in requirement_file.md 13.13 and verified against all three acceptance criteria. Confirmed by source inspection that the documented flow is the real one: `OnboardingScreen.kt` declares `OnboardingStep.ROLE_SELECTION`, `NEW_USER_ROLE_SELECTION`, `AUTHENTICATION`, `PROFILE_SETUP` (role selection -> authentication -> role-specific onboarding -> Main App); `SkillsExchangeViewModel.restoreSession()` restores an existing session straight to the main app (7-day expiry, AUTH-008); `setUserRoleMode()` still refuses "Offer Skills" unless `canAccessWorkerFeatures()` passes, so the worker activation/KYC/approval gate is intact. Phase 17 requirements are additions, not replacements. |

---

## Feature Connection Audit — Pending Fixes (2026-09-17, AUDITED — FIXES PENDING)

> DOCUMENTATION ONLY. READ-ONLY audit of 48 interactive controls. No source code changed, no fixes implemented, no commit. Every item below is ⏳ Pending Audit Fix. Do NOT mark completed.
>
> 2026-09-17 AUTOMATED VERIFICATION: compile PASSED (3m 12s). Targeted `UserProfilePersistenceTest` PASSED 6/6 (offline via the existing complete `D:\gradle-home` Gradle cache; earlier failures were uncached Robolectric runtime JARs in the project `GRADLE_USER_HOME`, not a code problem). `:app:assembleDebug` PASSED (2m 53s) and the APK installed on Samsung SM-A025F via adb → Success. Manual matrix A–F (Google account selection, legal-name entry, phone handling, logout/relogin, missing-Room recovery, roles/gates) still requires interactive on-device testing. C1–C3 therefore remain ⏳ Pending Audit Fix until that manual verification is recorded. No other rows changed.
> 2026-09-17 DEVICE UI FOLLOW-UP: user confirmed legal name is not Google-prefilled. Explicit dark-on-white legal-name colors added in both auth screens; Onboarding district starts empty, is labelled required and blocks completion when blank. Compile/APK passed (combined 6m 56s); replacement installation succeeded at 22:46:09, with matching APK hash. Post-install text contrast, Google button/chooser and district behavior remain NOT TESTED: device was signed in. Missing chooser root cause is unconfirmed; no speculative Google configuration fix. C1–C3 remain PENDING; user manual retest is next.



### Critical
| ID | Title | Status |
|---|---|---|
| C1 | Existing-user profile persistence | ⏳ Pending Audit Fix |
| C2 | UID consistency | ⏳ Pending Audit Fix — Fix #1 (UID-keyed `getCurrentUserById`/`getCurrentUserByUid`/`currentUserByUid`), Fix #2 (restore cross-checks `FirebaseAuth.currentUser.uid`, discards stale cached identity, fails closed when Firebase has no user, resolves the profile by UID) and Fix #3 / finding F7 (newest-row identity consumers swept: worker gate, role switch, worker location, account deletion, profile/backup/avatar writes, guarantors, training, quotes/proposals, notifications, disputes, ratings, reports, mark-all-read, logout identity mirror and SOS name/phone now use `FirebaseAuth.currentUser.uid` only; the legacy `viewModel.currentUser` StateFlow was deleted) implemented and unit-verified 2026-10-04: 131 tests / 0 failures / 0 errors; and Fix #4 / finding F4 (job authorship: new `job_posts.postedByUid` column via MIGRATION_19_20, DB 19->20; `addJobPost` fails closed with no authenticated uid; ownership via uid only, legacy jobs keep an unknown owner; Firestore rules + `releaseEscrowToWorker` compare `postedByUid`, deployed 2026-10-08) implemented and unit-verified 2026-10-04: 139 tests / 0 failures / 0 errors; and Fix #5 / finding F5 (chat sender: new `chat_messages.senderId` column via MIGRATION_20_21, DB 20->21; `sendChatMessage` fails closed with no authenticated uid and no `"unknown"` fallback; authorship via uid only, legacy and system messages keep an unknown author; Firestore messages subcollection rules bind `senderId` to `request.auth.uid`, deployed 2026-10-08) implemented and unit-verified 2026-10-04: 148 tests / 0 failures / 0 errors; and Fix #6 / finding F6 (job-status `changedBy`: the caller-supplied actor parameter was removed from `transitionJobStatus`, which now derives `changedBy` from `FirebaseAuth.currentUser.uid` via `resolveAuthoritativeUid` and fails closed — returning false before updating the job status or writing an audit row — with no `"system"` fallback and no Room migration needed since the column already existed) implemented and unit-verified 2026-10-04: 155 tests / 0 failures / 0 errors; the legacy newest-row `currentUser` flow now has zero production consumers; **Fix #7 / finding F3 remainder implemented and unit-verified 2026-10-05** (unbound device-local profile recovery: `SkillsExchangeRepository.getCurrentUserByUid` and `SkillsExchangeViewModel.resolveSessionOwnerId` now both delegate to the single shared `resolveAuthoritativeUid` rule instead of carrying their own weaker `isNotBlank()`-style checks, closing two real identity-binding gaps; the dead device-local phone selector `getUserProfileByPhone` + `getUserProfileByPhoneNumber` was DELETED, since selecting a local Room profile by a mutable field is exactly the unbound recovery path; the newest-row DAO query and `fetchUserIdentityByPhoneFromServer` were kept and relabelled non-authoritative because both have zero callers and the first is the trap the tests assert against; `user_default_me` kept as the seeded/display placeholder and excluded from authenticated identity, unique `phoneNumber` index not weakened; 7 new focused tests, cases A–G) — full suite **21 suites / 162 tests / 0 failures / 0 errors / 0 skipped**, `:app:compileDebugKotlin` BUILD SUCCESSFUL, `git diff --check` clean. **All seven C2-2 fixes (#1–#7) are now implemented and unit-verified, and the separate finding that status transitions have no per-role/ownership authorization** — implemented and unit-verified 2026-10-06 (one central `isJobTransitionAuthorized` / `canCurrentUserTransitionJob` check enforced inside `transitionJobStatus` before the structural check; owner-only matrix on `postedByUid` for setup/cancel/dispute/escrow-release; worker-progress, dispute-resolution, DRAFT and EXPIRED transitions fail closed client-side because `assignedProviderId` is a provider FK (no UID column) and admin is only a server-side Firestore custom claim — recorded as an open product decision in DECISIONS.md D-2026-10-05-2; `hireProviderForJob`, `updateEscrowStatus`' mapped-status write and the legacy `updateJobStatus` wrapper gated the same way; `firestore.rules` job update cases tightened to name their actor; deployed 2026-10-08 under DEPLOY-01; 9 new focused tests, cases A–J) — full suite **22 suites / 171 tests / 0 failures / 0 errors / 0 skipped**, `git diff --check` clean — **Firestore rules deployed to `skills-exchange-f9189` 2026-10-08 (DEPLOY-01)**; remaining C2 items: device/emulator verification, MoMo number cleanup and UI wiring; no device verification |
| C3 | Google AuthModal completion path | ⏳ Pending Audit Fix |

### High
| ID | Title | Status |
|---|---|---|
| H1 | Worker authorization fail-closed | ✅ Completed (2026-09-30) — `isWorkerAuthorized()` companion pure function; `isWorkerApprovedAndActivated` combines `isAuthenticated` + DB user; `canAccessWorkerFeatures()` delegates to same function; `FreelancerDashboardView` enforces at render boundary; 13/13 new security tests PASSED |
| H2 | AuthModal legal-name handling | ✅ Verified at unit level (2026-10-10): Google display name never seeds legal-name field; PROFILE_SETUP gates name+phone+district; `GoogleAuthModalCompletionSecurityTest` 9/9 PASSED; OnboardingScreen fix already made — device verification still pending |
| H3 | National ID & Verification drawer | ✅ Verified at code+compile level (2026-10-10): drawer opens real `IdentityVerificationSheet` (UID-keyed profile + `myWorkerKyc`); per-field status rows, verified tick gated on real KYC state; `compileDebugKotlin` BUILD SUCCESSFUL — device walkthrough still pending |
| H4 | Fake wallet balance | ✅ Fixed & compiled (2026-10-10): `MainScreen` now feeds the drawer the real escrow total + real KYC state (was placeholder defaults); `EscrowWalletSheet` already used live job records; `compileDebugKotlin` BUILD SUCCESSFUL — device walkthrough still pending |
| H5 | ServiceCatalogPicker | ✅ Implemented, compiled & unit-tested (2026-10-10): picker had zero call sites — now wired full-stack (DAO targeted update → repository `saveWorkerSkills` fail-closed own-row via `validateWorkerSkills` → ViewModel state → ProfileSettingsSheet "TRADE SKILLS" section); `compileDebugKotlin` BUILD SUCCESSFUL; new `ServiceTaxonomyValidationTest` 11/11 PASSED — device walkthrough still pending |
| H6 | Rating UI | ✅ Implemented + unit-tested (2026-10-10): release-gated "Rate Worker" button + dialog wired to `submitJobRating` secured boundary (authoritative-UID, owner-only, released-only, assigned-provider-only, 1–5, duplicate-blocked); synthetic `unknown_worker` fallback removed; backend rejections surface in-dialog; `RatingAuthorizationSecurityTest` 7/7 PASSED; `compileDebugKotlin` BUILD SUCCESSFUL — device walkthrough still pending |
| H7 | Trust/Safety & SOS | ✅ Implemented, wired, compiled & unit-tested (2026-10-10): owner-gated fail-closed SOS stand-down (`repo.clearEmergencySos` → pure `isSosClearAuthorized` owner-only + raised-only + F7 identity; `SosClearState` flow → `EscrowAndProofSheet` STAND DOWN button with `isClearingSos`/`sosClearError`); dispute fail-closed guard (no synthetic `unknown_worker` reportedUserId; backend rejections surface in-dialog); `compileDebugKotlin` BUILD SUCCESSFUL; new `SosClearAuthorizationSecurityTest` 5/5 PASSED + `RatingAuthorizationSecurityTest` 7/7 re-confirmed — device walkthrough still pending |
| H8 | Worker dashboard security | ✅ Completed (2026-09-30) — dual-layer enforcement: ViewModel gate + component-level `isAuthorized` guard in `FreelancerDashboardView` |

### Medium (M1–M12 from the audit — all pending)
| ID | Title | Status |
|---|---|---|
| M1 | A4 Job History screen | ✅ Implemented & wired (verified 2026-10-10): drawer "Job History & Material Receipts" now opens real `JobHistorySheet` (`AccountCenterSheets.kt`) via `onOpenHistory` → `isJobHistorySheetOpen`; filters `jobs` to COMPLETED/RELEASED/`RELEASED_TO_WORKER` and shows material-receipt counts + escrow release totals from `viewModel.jobs` (real data, not placeholder); `compileDebugKotlin` BUILD SUCCESSFUL |
| M2 | A7 AccountComplianceDialog wiring | ⏸️ DEFERRED (2026-10-10, user decision) — the namesake `AccountComplianceDialog` composable no longer exists (removed as the original A7 fix); the misnamed `AccountComplianceDialog.kt` holds 3 live composables (`AccountDeletionDialog`, `PrivacyAndTermsSheet`, `WorkerTrainingScreen`) + 3 orphaned ones (`GuarantorFormSheet`, `QualificationEvidenceSheet`, `ChangesRequiredView`) superseded by live components (`WorkerVerificationStatusView` handles KYC-007; REG-002 sheet handles guarantor). Deleting `GuarantorFormSheet` would contradict D-2026-09-26-1. Awaiting a wire-vs-delete decision |
| M3 | A8 Community Hire (obsolete — Community/Board removed under COMM-002) | ⛔ OBSOLETE (verified 2026-10-10) — finding no longer applicable: Community/Board feature fully removed under COMM-002 (2026-09-23); repo-wide grep confirms no Community/Board screen or Hire button remains (only incidental "community" text in training/feedback/endorsement strings). No action possible |
| M4 | B4 Worker Online toggle persistence | ✅ Fixed (2026-10-10) — the worker online/availability toggle previously lived only in an in-memory `MutableStateFlow(true)` and silently reset to online on every launch. Now persisted via the existing SessionManager DataStore (`worker_online` key): restored in `restoreSession()` (default online) and written on every `toggleWorkerOnline()`. Regression test `WorkerOnlinePersistenceTest` (4/4 pass). Compile BUILD SUCCESSFUL |
| M5 | B6 Notification navigation | ✅ Fixed (2026-10-10) — notification tap previously only marked all read + closed the drawer, never navigating. Added pure type→target map `NotificationRouting` (`util/NotificationRouting.kt`) and a `MainScreen.handleNotificationClick` that opens the matching sheet: VERIFICATION_DECISION→`IdentityVerificationSheet`, PAYMENT_STATUS→`EscrowWalletSheet`, DISPUTE_UPDATE/SOS_EVENT→`TrustSafetySheet`, NEW_QUOTE→quote-comparison (only when the job resolves by `jobId`); unresolvable/unknown types (e.g. TRAINING_REQUIREMENT) fall back to mark-read-only. Regression test `NotificationRoutingTest` (7/7 pass). Compile + test BUILD SUCCESSFUL |
| M6 | B8 Map empty state | ✅ Fixed (2026-10-10) — the Map tab showed a blank grey map (only a "0 Nearby Specialists" chip) when no providers matched, with no explanation. Added `MapEmptyProvidersOverlay` (internal, in `InteractiveMapView.kt`, matching the `MapUnavailableFallback` style) shown when `providers.isEmpty()`: "No providers nearby" + guidance to widen radius / adjust filters / switch to List. GPS status is already covered by `GpsLocationBanner`. Regression test `MapEmptyProvidersOverlayTest` (Robolectric+Compose) 1/1 pass. Compile + test BUILD SUCCESSFUL |
| M7 | B9 Distance filtering | ✅ Completed | Real haversine distance (util/DistanceUtil.kt) replaces static seeded distanceMiles for both filter and display; effectiveDistanceMiles falls back to stored value when GPS unknown. DistanceUtilTest 5/5 PASS (0 failures); compile + targeted test BUILD SUCCESSFUL (EXIT=0). |
| M8 | G2 setUserRoleMode gate | ✅ Completed (2026-09-30) — `setUserRoleMode("Offer Skills")` calls `canAccessWorkerFeatures()` which calls `isWorkerAuthorized()`; combined with H1/H8 fix |
| M9 | G4 Verification entry flow | ⏳ Pending Audit Fix |
| M10 | G5 Verified-claim accuracy | ⏳ Pending Audit Fix |
| M11 | G6 Firestore/storage role checks | ⏳ Pending Audit Fix |
| M12 | N2/N3/N4 + P1/P2 + M1–M4 navigation/map/permission bundle | ⏳ Pending Audit Fix |

### Low (L1–L5 from the audit — all pending)
| ID | Title | Status |
|---|---|---|
| L1 | B5 Theme persistence | ⏳ Pending Audit Fix |
| L2 | B7 Report review destination | ⏳ Pending Audit Fix |
| L3 | G7 WEB_CLIENT_ID handling | ⏳ Pending Audit Fix |
| L4 | D-series placeholder/demo cleanup | ⏳ Pending Audit Fix |
| L5 | Auth items 3/4/8 residual polish | ⏳ Pending Audit Fix |

Full finding text (A1–A8, B1–B9, G1–G7, D1–D11, N1–N4, P1–P2, M1–M4, auth 1–9, community map) is preserved in AGENT_STATE.md 2026-09-17 section. Counts: 79 completed / 12 pending unchanged.

---

## Real-Device Test — 2026-09-29 (Samsung SM-A025F, Android 12 / API 31)

> Evidence for every line: `_device_test/DEVICE_TEST_REPORT.md`. **No `prd.json` story status was
> changed by this test** — the counts in the summary below are unchanged.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| DEV-VERIFY-01 | P0 | Install the current debug APK on a physical device | ✅ Completed | In-place update after `pm trim-caches` (space, not signatures); `firstInstallTime` 2026-09-16 preserved, installed `base.apk` = 35,254,412 B = build output |
| DEV-VERIFY-02 | P0 | Prove Room `MIGRATION_18_19` runs on a device | ✅ Completed | Pulled `skills_exchange_database` reads `user_version = 19`, matching `AppDatabase version = 19`; `guarantors` carries `nationalIdNumber` + `phoneNumber` |
| DEV-VERIFY-03 | P1 | Client-mode UI walkthrough (home, drawer, gates, tabs, theme) | ✅ Completed | 15 screenshots; cold start 6,305 ms, no ANR; guest tap on National ID correctly opens CLIENT ONBOARDING |
| D1 | P0 | Map View tab kills the process (`IllegalStateException: API key not found`, `InteractiveMapView.kt:98`) | 🚫 Blocked | No `com.google.android.geo.API_KEY` `<meta-data>` in the manifest, no `MAPS_API_KEY` in `.env`; needs a real key **and** a guarded `onCreate` + fallback UI |
| D1-PRD | P1 | Reopen **MAP-001** (`completed` → `in_progress`) and re-verify its acceptance criterion on device | ⏳ Pending | Deliberately not done by a verification-only session (DECISIONS.md DEC-014) |
| D2 | P2 | Give the guest role-gate visible feedback on `MainScreen` (`authError` is set by `toggleUserRoleMode()` but only rendered inside `AuthModal`/onboarding, which are closed; no `SnackbarHost` anywhere) | ⏳ Pending | Drawer copy invites the tap that visibly no-ops |
| DEV-VERIFY-04 | P1 | Authenticated device flows (worker dashboard, chat, quotes, escrow, REG-002 guarantor end-to-end, ADMIN-005 callables) | ⏳ Pending | Needs a signed-in test account plus `firebase deploy` of rules/functions |

---

## Summary

| Status | Count |
|---|---|
| ✅ Completed | 96 |
| 🔄 In Progress | 0 |
| ⏳ Pending | 0 |
| 🚫 Blocked | 0 |
| **Total** | **96** |

---

_This file is synchronized with `prd.json`. Always update both when story status changes._
_Last synchronized: 2026-10-11T15:24:11+03:00 — Phase 17 documentation stories REG-001, LOC-005, MAP-002, MAP-003, LEGAL-001, LEGAL-002 and AUTH-012 completed. All seven are **documentation-only** stories whose stated goal is to record a future requirement *without implementation*; none of them required or received a source-code change, so no unit-test or build claim is made for them. Each was verified against its own `acceptance_criteria` by inspecting `requirement_file.md` (REG-001 §13.2, LOC-005 §13.6, MAP-002 §13.7, MAP-003 §13.8, LEGAL-001 §13.11, LEGAL-002 §13.12, AUTH-012 §13.13) and, for AUTH-012, against the live auth/onboarding code (`OnboardingScreen.kt` `OnboardingStep`, `SkillsExchangeViewModel.restoreSession()`, `setUserRoleMode()` + `canAccessWorkerFeatures()`). A prior session had flipped these same seven to `completed` with a `complete_stories.ps1` script while leaving TASKS.md/AGENT_STATE.md/CHANGELOG.md/DECISIONS.md unsynchronised and MAP-003 absent from `requirement_file.md`; `prd.json` was restored from HEAD and re-applied with verified notes. `complete_stories.ps1` was removed. No APK build and no device run._
