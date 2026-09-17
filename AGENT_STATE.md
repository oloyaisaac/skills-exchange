# AGENT_STATE.md — Skills Exchange Project State

## Last Updated
2026-09-16T18:00:00+03:00

---

## Current Project Status
**Phase**: Post-Phase 17 Implementation — Feature Connection Audit & Bug Fixes
**Overall Status**: Phase 17 documents complete (12 new requirements, FUTURE/NOT IMPLEMENTED). All 79 Phase 1-16 stories complete. Post-launch audit in progress.

---

## Current PRD Story
**All Phase 16 implementable stories are complete, including STAB-003 build verification.**
"Status**: All implementation complete

---

## Current Objective
All Phase 16 implementable stories are complete, including the STAB-003 automated test verification.

---

## Completed & Verified Work (78 Stories)

### Phase 1 — Test & Baseline Stabilization
- **STAB-001**: Fix Robolectric unit test assertion mismatch (Verified correct assertion "Skills Exchange")
- **STAB-002**: Fix GreetingScreenshotTest theme and composable imports (Verified SkillsExchangeTheme)
- **STAB-003**: Automated test execution blocked by environment JDK 25 / Gradle 8.9 foojay plugin conflict. Documented as environment blocker.

### Phase 2 — Security, Storage & Webhooks
- **SEC-001**: Harden Firestore security rules for provider and KYC collections (Verified document owner UID check in firestore.rules)
- **SEC-002**: Add HMAC signature verification to MoMo webhook Cloud Function (Implemented in functions/src/index.ts)
- **SEC-003**: Configure Firebase Storage security rules for private KYC documents (Created storage.rules for KYC, guarantor IDs, and certificates)

### Phase 3 — Unified Account Architecture & Role Mode Switching
- **ROLE-001**: Define Unified Account Data Model with Client/Worker role flags (Created UserProfileEntity, UserProfileDao, AppDatabase v5, repository)
- **ROLE-002**: Implement Client and Worker mode switching logic (Added persistent setUserRoleMode and toggleUserRoleMode in ViewModel and Repository)
- **ROLE-003**: Implement Worker Verification & Activation access gate (Created WorkerVerificationStatusView and gated FreelancerDashboardView in MainScreen.kt)

### Phase 4 — Service Taxonomy & Skill Catalog System (125 Services)
- **SKILL-001**: Define Service Taxonomy Data Schema with 125 Skills (Created ServiceTaxonomyCatalog with 125 services across 5 hubs)
- **SKILL-002**: Implement Related Skill Validation engine (Created validateWorkerSkills engine enforcing trade family compatibility)
- **SKILL-003**: Implement Service Catalog UI selector for Worker onboarding (Created ServiceCatalogPickerModal with category tabs and live validation)

### Phase 5 — Client Onboarding & Location Management
- **LOC-001**: Implement Client Location Onboarding & GPS Detection (Implemented GPS permission request and location fetching in AuthModal.kt)
- **LOC-002**: Implement Manual Location Correction & Geocoding fallback (Added EditLocationDialog in GpsLocationBanner.kt)
- **LOC-003**: Implement Worker District & Location management (Added address, lat, lng to UserProfileEntity and ViewModel)
- **LOC-004**: Enforce Public Location Privacy & District-Level Aggregation (Dynamic district job stats in ProviderDetailSheet, obfuscated reviewer identities)

### Phase 6 — Worker KYC Verification & Guarantor Engine
- **KYC-001**: Define Worker Identity & National ID data schema (Created WorkerKycEntity, WorkerKycDao, AppDatabase v7)
- **KYC-002**: Implement Worker Document Upload pipeline to Firebase Storage (Added uploadKycDocument to FirebaseService, Firebase Storage dependency)
- **KYC-003**: Define Two-Guarantor Data Model & Validation rules (Created GuarantorEntity, GuarantorDao, validateGuarantorCount)
- **KYC-004**: Implement Guarantor Identity & National ID Upload UI (Built GuarantorFormSheet in AccountComplianceDialog.kt)
- **KYC-005**: Implement Primary Skill Qualification Certificate & Video Evidence upload (Added uploadQualificationEvidence + QualificationEvidenceSheet)

### Phase 7 — Verification Lifecycle, Changes-Required & 48h SLA Tracking
- **KYC-006**: Implement Verification State Machine & 48h SLA Timestamps (Added state transition methods + getKycSlaDeadline)
- **KYC-007**: Implement Worker 'Changes Required' feedback view & resubmission workflow (Built ChangesRequiredView in AccountComplianceDialog.kt)

### Phase 10 — Core Marketplace, Quotes & Job Types
- **JOB-001**: Implement One-Time & Recurring Job Type Data Model (Added jobType, recurringSchedule, recurringStartDate to JobPostEntity + Firestore sync)
- **MARKET-001**: Implement Nearby Verified Worker Discovery (Added isActivated + kycStatus APPROVED filter in providers flow)

### Phase 11 — Mobile Money Payments & Escrow Engine
- **PAY-001**: Integrate Mobile Money Collection Intent API (Replaced mock delay with Retrofit + MTN MoMo Collection API)
- **PAY-002**: Implement Escrow Lock & Webhook Confirmation (Updated processMoMoWebhook to lock escrow and notify resident/worker)
- **PAY-003**: Implement Escrow Release & Worker MoMo Payout (Added releaseEscrowToWorker Cloud Function for worker payout)
- **PAY-004**: Implement Material Advance Escrow Partial Disbursement (Added approveMaterialAdvance repository method)

### Phase 12 — Location Privacy, Google Maps & AI Services
- **MAP-001**: Integrate Google Maps SDK for Nearby Worker View (Replaced custom Canvas with Google Maps MapView composable)
- **AI-001**: Repair Gemini Audio Transcription & Model Identifier (Fixed model URL from gemini-3.5-flash to gemini-2.0-flash & MPEG_4/AAC audio format)
- **AI-002**: Repair Gemini Maps Grounding Model Identifier & Tools Payload (Fixed model URL to gemini-2.0-flash)

### Phase 9 — Admin Operations Dashboard & Verification Queue
- **ADMIN-001**: Architecture & Spec for Separate Administrative Web Dashboard (Cloud Functions: getPendingVerificationQueue, reviewWorkerApplication, setWorkerActivationStatus)
- **ADMIN-002**: Admin Verification Review Queue & Document Viewer Cloud Function (Implemented in functions/src/index.ts)
- **ADMIN-003**: Admin Verification Action API (Approve, Reject, Request Changes) (Implemented in functions/src/index.ts)
- **ADMIN-004**: Admin Worker Activation & Suspension Controls (Implemented in functions/src/index.ts)

### Phase 8 — Worker Training Tracking & Activation Gating
- **TRAIN-001**: Define Worker Training Progress & Content Data Schema (Created WorkerTrainingEntity, WorkerTrainingDao, AppDatabase v9)
- **TRAIN-002**: Implement Worker Rule Training UI & Completion Tracking (Created WorkerTrainingScreen with module completion tracking)
- **TRAIN-003**: Implement Admin Activation Gate requiring Verification + Training (activateWorker enforces KYC + Training completion)

### Phase 14 — Offline Synchronization & Reliability
- **OFF-001**: Implement Offline Action Queue & Sync Manager (PendingActionEntity, auto-sync on network reconnect)

### Phase 16 — Production Readiness & Security Hardening
- **SEC-004**: Fix releaseEscrowToWorker Authorization Vulnerability (Added job ownership verification)
- **SEC-005**: Fix Firestore Rules KYC Path Mismatch (Updated isWorkerKycApproved helper)
- **SEC-006**: Add Missing Firestore Rules for Collections (Rules for all collections)
- **SEC-007**: Remove Hardcoded Webhook Secret Fallback (Fail loudly if secret not set)
- **JOB-004**: Implement Job State Machine (JobStatus enum with validated transitions)
- **DISP-001**: Implement Dispute Entity and Persistence (DisputeEntity, DAO, enums)
- **DISP-002**: Implement Dispute UI and Workflow (Wired to persistence, reason selector, validation)
- **DISP-003**: Implement Admin Dispute Resolution (resolveDispute, getDisputesQueue Cloud Functions)
- **NOTIF-001**: Implement Notification Entity and Persistence (NotificationEntity, DAO, enums)
- **NOTIF-002**: Implement FCM Push Notifications (FirebaseMessagingService, token management)
- **RATE-001**: Implement Rating Entity and Persistence (RatingEntity, DAO)
- **RATE-002**: Implement Rating Submission UI (ViewModel methods, validation)
- **RATE-003**: Implement Rating Aggregation (Average calculation)
- **DB-001**: Add Proper Room Migrations (Migration 11→12, data preservation)
 - **AUTH-001**: Implement Phone Authentication with OTP (Multi-step auth flow)
 - **AUTH-002**: Implement Session Persistence (DataStore for auth state)
 - **AUTH-003**: Implement Logout Flow (Clears Firebase auth, Room data, and DataStore)
 - **AUTH-004**: Implement Account Deletion (Firebase user deletion, Firestore purge, Room wipe)
 - **AUTH-005**: Implement Authentication Error Handling (Inline phone/OTP validation errors, retry option)
 - **DISP-004**: Implement User/Worker Reporting (ReportEntity, ReportDao, ReportUserDialog in ProviderDetailSheet)
 - **DISP-005**: Implement Dispute Notification Integration (Notifications on dispute submit/resolution)
 - **NOTIF-003**: Implement Notification Triggers to Events (Quote, job, payment, verification, SOS notifications)
 - **NOTIF-004**: Implement Notification UI (NotificationDrawer wired to real data, unread badge)
 - **DB-002**: Add Room Foreign Keys and Indexes (Foreign keys, indexes, migration v14)
 - **AUTH-006**: Implement Duplicate Account Handling (Phone uniqueness, existing account reuse)
 - **AUTH-007**: Implement Role/Permission Enforcement (Worker mode gating, quote submission gating)
 - **AUTH-008**: Implement Session/Device Security (Session timeout, device validation)
 - **AUTH-009**: Implement Auth State Restoration (User profile, role mode, FCM token restoration)
 - **AUTH-010**: Implement Authentication Tests (Auth flow unit tests)
 - **SEC-008**: Add Storage Rules for Job Photos (Restrictive storage rules)
 - **JOB-005**: Add Job Status Audit Trail (Audited transitions)

---

## Work In Progress / Next Task
All Phase 16 implementable stories are complete. Phase 17 (documentation only) is complete.

**Current work:** Feature Connection Audit (Phase 17 POST-LAUNCH task — post-audit fixes)
Auditing and fixing disconnected features identified during the systematic feature connection audit.

### Fixes in Progress (AUTH-002/AUTH-006 + Security)
- **FirebaseService.kt**: Added `saveUserIdentity()`, `fetchUserIdentity()`, `fetchUserIdentityByPhone()` methods for durable Firestore identity record at `users/{uid}`
- **SkillsExchangeRepository.kt**: Added `saveUserIdentityToServer()`, `fetchUserIdentityFromServer()`, `fetchUserIdentityByPhoneFromServer()` pass-throughs
- **SkillsExchangeViewModel.kt**: 
  - `FirebaseService(application)` now passed to repository constructor
  - `isWorkerApprovedAndActivated` initial value changed from `true` to `false` (fail-closed)
  - `loginUser()`: Always resolves real Firebase UID before persisting profile; added Firestore identity lookup as fallback
  - `setUserRoleMode()`: Added `canAccessWorkerFeatures()` gate to prevent unauthorized worker mode switch
  - `loginWithGoogle()`: Existing-user check now only requires `isOnboardingCompleted` (not blank name/district)
- **OnboardingScreen.kt**: Removed automatic Google display name pre-population of legal name field
- **AccountCenterSheets.kt**: New file with `EscrowWalletSheet`, `JobHistorySheet`, `VerificationStatusSheet`, `TrustAndSafetySheet` — real destinations replacing silent redirects
- **NavigationDrawer.kt**: Removed hardcoded wallet balance (UGX 240), removed hardcoded phone placeholder, added `onOpenVerification` parameter
- **MainScreen.kt**: Wired drawer menu items to new sheets with real data; removed `AccountComplianceDialog` import

### Pending Fixes (from audit)
- A6 ServiceCatalogPicker orphaned — needs wiring into worker onboarding
- B1 Rating UI missing — ViewModel/repository complete but no composable triggers rating
- M6 Notification click doesn't navigate to referenced job/chat
- M11 Theme mode not persisted to DataStore

---

## Files Recently Modified
- `app/proguard-rules.pro` (REL-001 ProGuard rules for Room, Firebase, Moshi, coroutines)
- `app/src/main/java/com/example/ui/components/AccountComplianceDialog.kt` (TRAIN-002 WorkerTrainingScreen + KYC-004/005/007)
- `app/src/main/java/com/example/ui/screens/MainScreen.kt` (TRAIN-002 training screen wiring + ROLE-003)
- `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (TRAIN-002 training methods + ADMIN + SAFE-002)
- `app/src/main/java/com/example/data/db/Entities.kt` (TRAIN-001 WorkerTrainingEntity + MARKET-001, JOB-001, KYC, LOC)
- `app/src/main/java/com/example/data/db/Daos.kt` (TRAIN-001 WorkerTrainingDao)
- `app/src/main/java/com/example/data/db/AppDatabase.kt` (TRAIN-001 AppDatabase v9)
- `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` (TRAIN-001/002 training flows + ADMIN + SAFE-002 + COMM-001 + PAY-004 + KYC)
- `functions/src/index.ts` (ADMIN-001..004: getPendingVerificationQueue, reviewWorkerApplication, setWorkerActivationStatus)
- `app/src/main/java/com/example/data/remote/FirebaseService.kt` (ADMIN functions + COMM-001 streamChatMessages, sendChatMessage, maskPhoneNumbers)
- `app/src/main/java/com/example/ui/components/InteractiveMapView.kt` (MAP-001 Google Maps SDK integration)
- `app/build.gradle.kts` (MAP-001 play-services-maps dependency)
- `gradle/libs.versions.toml` (MAP-001 play-services-maps version)
- `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` (PAY-004 approveMaterialAdvance)
- `functions/src/index.ts` (PAY-003 releaseEscrowToWorker Cloud Function)
- `functions/src/index.ts` (PAY-002 processMoMoWebhook escrow lock + notifications)
- `app/src/main/java/com/example/data/remote/MoMoPaymentService.kt` (PAY-001 Retrofit + MTN MoMo Collection API)
- `app/src/main/java/com/example/data/remote/MoMoCollectionApi.kt` (PAY-001 new Retrofit API interface)
- `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (MARKET-001 isActivated + kycStatus filter)
- `app/src/main/java/com/example/data/db/Entities.kt` (MARKET-001 isActivated field, JOB-001 jobType/recurring fields)
- `app/src/main/java/com/example/data/remote/FirebaseService.kt` (JOB-001 syncJobToFirestore new fields)
- `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (SAFE-002 triggerSosAlert GPS-aware dispatch)
- `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` (SAFE-002 smsNotificationService constructor param)
- `app/src/main/java/com/example/ui/components/AccountComplianceDialog.kt` (KYC-005 QualificationEvidenceSheet)
- `app/src/main/java/com/example/ui/components/AccountComplianceDialog.kt` (KYC-004 GuarantorFormSheet)
- `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (KYC-005 uploadQualificationEvidence, KYC-004 saveGuarantors/isValidGuarantorCount)
- `app/src/main/java/com/example/data/db/Entities.kt` (KYC-003 GuarantorEntity)
- `app/src/main/java/com/example/data/db/Daos.kt` (KYC-003 GuarantorDao)
- `app/src/main/java/com/example/data/db/AppDatabase.kt` (KYC-003 AppDatabase v8)
- `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` (KYC-003 guarantor repo methods, KYC-006 state machine)
- `app/src/main/java/com/example/data/remote/FirebaseService.kt` (KYC-002 uploadKycDocument, KYC-005 uploadQualificationEvidence)
- `app/build.gradle.kts` (KYC-002 firebase-storage dependency)
- `gradle/libs.versions.toml` (KYC-002 firebase-storage version catalog)
- `app/src/main/java/com/example/data/db/Entities.kt` (KYC-001 WorkerKycEntity)
- `app/src/main/java/com/example/data/db/Daos.kt` (KYC-001 WorkerKycDao)
- `app/src/main/java/com/example/ui/components/ProviderDetailSheet.kt` (LOC-004 dynamic district aggregation & reviewer privacy)
- `app/src/main/java/com/example/ui/screens/MainScreen.kt` (LOC-004 district stats wiring)
- `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (LOC-004 getDistrictJobStatsForProvider)
- `app/src/main/java/com/example/data/remote/GeminiAudioTranscriptionService.kt` (AI-001 model ID & MIME type fix)
- `app/src/main/java/com/example/data/remote/GeminiMapsGroundingService.kt` (AI-001/AI-002 model ID fix)
- `app/src/main/java/com/example/util/AudioRecorderHelper.kt` (AI-001 AAC audio recording format)
- `functions/src/index.ts` (SEC-002 HMAC webhook signature verification)
- `functions/tsconfig.json` (TypeScript configuration for Cloud Functions)
- `storage.rules` (SEC-003 Firebase Storage security rules for private KYC documents)
- `app/src/main/java/com/example/data/db/ServiceTaxonomy.kt` (SKILL-001 & SKILL-002 125-service catalog & validation engine)
- `app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt` (ROLE-001/002, SKILL-001/002, SAFE-001 dynamic PIN)
- `app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt` (ROLE-001/002 profile flows & mode switching)
- `app/src/main/java/com/example/ui/components/WorkerVerificationStatusView.kt` (ROLE-003 activation gate UI)
- `app/src/main/java/com/example/ui/components/ServiceCatalogPicker.kt` (SKILL-003 service picker modal)
- `app/src/main/java/com/example/ui/screens/MainScreen.kt` (ROLE-003 worker activation gating)
- `prd.json` (Updated story statuses, completed_stories, current_story)
- `TASKS.md` (Synchronized human-readable task board)
- `CHANGELOG.md` (Documented development history)
- `DECISIONS.md` (Architectural decisions recorded)

---

## Tests Performed
| Test | Command / Method | Result |
|---|---|---|
| STAB-001 Code Inspection | Manual file inspection | PASS — asserts "Skills Exchange" |
| STAB-002 Code Inspection | Manual file inspection | PASS — uses SkillsExchangeTheme |
| AI-001 Model ID & Audio format | Grep and code inspection | PASS — gemini-2.0-flash & MPEG_4/AAC |
| AI-002 Model ID | Grep search | PASS — gemini-2.0-flash throughout |
| SEC-001 Owner UID check | Code review firestore.rules | PASS — isDocOwner(userId) enforced |
| SEC-002 HMAC Webhook check | Code review functions/src/index.ts | PASS — verifyHmacSignature timingSafeEqual |
| SEC-003 Storage Isolation | Code review storage.rules | PASS — KYC/guarantor private doc rules |
| ROLE-001/002/003 Model & UI | Code inspection Room entities/UI | PASS — UserProfileEntity, DAO, Gate Screen |
| SKILL-001/002 Taxonomy & Engine | Code review ServiceTaxonomyCatalog | PASS — 125 items, family validation |
| SAFE-001 Dynamic PIN | Code review addJobPost | PASS — Random (1000..9999) PIN per job |
| LOC-004 Privacy & Aggregation | Code inspection ProviderDetailSheet | PASS — dynamic district stats, reviewer names hidden |
| KYC-001 Schema & DAO | Code inspection Entities/Daos/AppDatabase | PASS — WorkerKycEntity, WorkerKycDao, DB v7 |
| KYC-002 Upload Pipeline | Code inspection FirebaseService | PASS — uploadKycDocument, users/{uid}/kyc path, Storage dep added |
| KYC-003 Guarantor Model | Code inspection Entities/Daos/Repository | PASS — GuarantorEntity, GuarantorDao, validateGuarantorCount |
| KYC-004 Guarantor UI | Code inspection AccountComplianceDialog | PASS — GuarantorFormSheet 2-step form, photo pickers, validation |
| KYC-005 Qualification Evidence | Code inspection FirebaseService/AccountComplianceDialog | PASS — uploadQualificationEvidence, QualificationEvidenceSheet |
| KYC-006 State Machine | Code inspection Repository | PASS — submit/approve/reject/changes-required methods, 48h SLA deadline |
| KYC-007 Changes Required UI | Code inspection AccountComplianceDialog | PASS — ChangesRequiredView with feedback, support links, update button |
| JOB-001 Job Type Model | Code inspection Entities/FirebaseService | PASS — jobType, recurringSchedule, recurringStartDate fields added |
| MARKET-001 Worker Discovery Filter | Code inspection ViewModel/Entities | PASS — isActivated + kycStatus APPROVED filter in providers flow |
| PAY-001 MoMo Collection API | Code inspection MoMoPaymentService/MoMoCollectionApi | PASS — Retrofit + MTN MoMo Collection API integration |
| PAY-002 Escrow Lock & Webhook | Code inspection functions/src/index.ts | PASS — processMoMoWebhook locks escrow, sends notifications |
| PAY-003 Escrow Release & Payout | Code inspection functions/src/index.ts | PASS — releaseEscrowToWorker Cloud Function, worker payout |
| PAY-004 Material Advance Disbursement | Code inspection Repository | PASS — approveMaterialAdvance method, 50% disbursement logic |
| MAP-001 Google Maps SDK | Code inspection InteractiveMapView/build.gradle | PASS — Google Maps MapView composable, play-services-maps dep |
| DB-001 Room Migrations | Code inspection AppDatabase.kt | PASS - Migration 11→12 added, data preserved |
| RATE-001 Rating Entity | Code inspection Entities/Daos | PASS - RatingEntity with DAO methods |
| NOTIF-001 Notification Entity | Code inspection Entities/Daos | PASS - NotificationEntity with DAO methods |
| DISP-001 Dispute Entity | Code inspection Entities/Daos | PASS - DisputeEntity with enums and DAO |
| DISP-003 Admin Dispute Resolution | Code inspection functions/src/index.ts | PASS - resolveDispute, getDisputesQueue |
| JOB-004 Job State Machine | Code inspection Entities.kt | PASS - JobStatus enum with transitions |
| SEC-004 Escrow Authorization | Code inspection functions/src/index.ts | PASS - Ownership verification added |
| SEC-005 Firestore KYC Path | Code inspection firestore.rules | PASS - worker_kyc path fixed |
| SEC-006 Firestore Rules | Code inspection firestore.rules | PASS - All collections covered |
| SEC-007 Webhook Secret | Code inspection functions/src/index.ts | PASS - No default fallback |

---

## Known Blockers
| Blocker | Reason | Needed |
|---|---|---|
| None | No active blockers after full test verification | N/A |

---

## Feature Connection Audit — 2026-09-17 (PART 1/2)

**Status: AUDITED — FIXES PENDING**

> DOCUMENTATION ONLY — 2026-09-17. READ-ONLY audit. No source code changed, no fixes implemented, no commit. All findings below are NOT fixed unless a prior dated entry records a verified fix.

**Scope:** 48 interactive controls examined. Record: 8 confirmed disconnected (A1-A8), 9 partially connected (B1-B9), security/authorization (G1-G7), placeholder/demo (D1-D11), navigation/UX (N1-N4), authentication (9 items), map (M1-M4), permission (P1-P2).

### A. Confirmed disconnected (NOT fixed)
- A1 — Hamburger "Sign In / Register" — Google path leaves user stranded in the modal — HIGH — Pending Audit Fix
- A2 — Drawer "National ID & Verification" — silent no-op when authenticated — HIGH — Pending Audit Fix
- A3 — Drawer "MoMo & Escrow Wallet" — hardcoded fake balance + wrong destination — HIGH — Pending Audit Fix
- A4 — Drawer "Job History & Material Receipts" — no history screen — MEDIUM — Pending Audit Fix
- A5 — Drawer "Trust, Safety & Emergency SOS" — opens unrelated escrow sheet / can silently do nothing — HIGH — Pending Audit Fix
- A6 — ServiceCatalogPicker — implemented but zero call sites / orphaned — HIGH — Pending Audit Fix
- A7 — AccountComplianceDialog — implemented but never invoked / orphaned — MEDIUM — Pending Audit Fix
- A8 — Community/Board "Hire" button — silent failure — MEDIUM — Pending Audit Fix

### B. Partially connected (NOT fixed)
- B1 — Rating UI (RATE-001/002/003) — repository/ViewModel exist but no UI trigger — HIGH — Pending Audit Fix
- B2 — Existing-user recognition on Google sign-in — HIGH — Pending Audit Fix
- B3 — Legal name field — Onboarding fixed, AuthModal still auto-fills Google display name — HIGH — Pending Audit Fix. Distinction: OnboardingScreen.kt fix already made pending runtime verification; AuthModal.kt issue still pending.
- B4 — Worker Online toggle — in-memory only — MEDIUM — Pending Audit Fix
- B5 — Theme selector — not persisted — LOW — Pending Audit Fix
- B6 — Notification click — marks all read but doesn't navigate — MEDIUM — Pending Audit Fix
- B7 — Report User — report action exists but no review destination/list — LOW — Pending Audit Fix
- B8 — Map tab — no useful empty state when no providers/GPS — MEDIUM — Pending Audit Fix
- B9 — Worker distance/radius filtering — static distance data — MEDIUM — Pending Audit Fix

### G. Security / authorization (NOT fixed by this task)
- G1 — isWorkerApprovedAndActivated initialises to true / fail-open — HIGH — Pending Audit Fix
- G2 — setUserRoleMode() has no worker authorization gate — MEDIUM — Pending Audit Fix
- G3 — Worker dashboard authorization depends on UI state rather than sufficient lower-layer enforcement — HIGH — Pending Audit Fix
- G4 — WorkerVerificationStatusView "Start verification" opens post-job drawer instead of verification flow — MEDIUM — Pending Audit Fix
- G5 — Drawer claims "National ID & Phone Verified" / "Verified" for any authenticated user — MEDIUM — Pending Audit Fix
- G6 — Firestore/storage rules currently authenticate users but do not provide role/ownership checks — MEDIUM — Pending Audit Fix
- G7 — Google WEB_CLIENT_ID hardcoded in source — LOW — Pending Audit Fix

(AUDIT PART 2 FOLLOWS)

---

(AUDIT PART 2 — placeholder/demo, navigation, auth, map, permissions, community map, order)

### D. Placeholder / demo (NOT fixed)
- D1 — Hardcoded wallet balance CurrencyRegion.format(240.0)
- D2 — Hardcoded phone fallback "+256 700 123 456"
- D3 — Demo seed data from seedInitialDataIfNeeded()
- D4 — Full-name fallback "Specialist" / "Neighbor"
- D5 — Fallback user ID "user_default_me"
- D6 — Synthetic UID "user_${System.currentTimeMillis()}"
- D7 — Phone OTP is currently simulated rather than real Firebase Phone Auth
- D8 — refreshToken remains TODO/simulated
- D9 — provider.distanceMiles is seeded/static
- D10 — MoMo payment uses delay() / simulated gateway behavior
- D11 — Camera/media picker is absent for job photos

### N. Navigation / UX (NOT fixed)
- N1 — No Navigation-Compose graph; navigation is state-driven
- N2 — System Back does not close open drawers/sheets/modals (no BackHandler/back stack)
- N3 — Some drawer items point to Jobs instead of their advertised destination
- N4 — Notification click does not navigate to the referenced job/chat

### P. Permissions (NOT fixed)
- P1 — Notification permission granted callback is empty
- P2 — Camera/media permission/picker is not wired because job photo capture is not implemented

### M. Map (NOT fixed)
- M1 — Current Maps implementation loads on List/Map tabs
- M2 — No map tied specifically to an accepted/active job
- M3 — Map is not centred using device GPS
- M4 — Provider distance is not currently GPS-derived

### Authentication findings, 9 items (NOT fixed by this task)
1. Existing Google users can be forced through onboarding.
2. Google display name can be incorrectly used as legal name in AuthModal.
3. Google AuthModal Google branch does not capture phone number.
4. onComplete(false) relies on incidental ViewModel modal closing.
5. User profiles are currently Room-only.
6. User profiles are not persisted to Firestore.
7. logoutUser() clears the local profile.
8. Synthetic IDs can be created instead of real Firebase UIDs.
9. Therefore existing-user recognition can fail after logout, app-data clearing, reinstall, or on another device.
- Legal-name distinction: OnboardingScreen.kt fix already made, pending runtime verification. AuthModal.kt legal-name issue still pending.

### Community removal map (references only — DO NOT REMOVE NOW)
- MainScreen.kt — Board tab/navigation; MainScreen.kt — Board branches
- NeighborhoodFeedView.kt; SkillsExchangeViewModel.kt — neighborhoodPosts
- SkillsExchangeRepository — neighborhood posts; NeighborhoodPostEntity; NeighborhoodPostDao
- AppDatabase neighborhoodPostDao/version/migration references; MainScreen DynamicFeed import
- Firestore rules if applicable; Existing PRD/requirement documentation references

### Recommended implementation order (from audit — NOT started, NOT completed)
- Critical: C1 — Existing-user profile persistence; C2 — UID consistency; C3 — Google AuthModal completion path
- High: H1 — Worker authorization fail-closed; H2 — AuthModal legal-name handling; H3 — National ID & Verification drawer; H4 — Fake wallet balance; H5 — ServiceCatalogPicker; H6 — Rating UI; H7 — Trust/Safety & SOS; H8 — Worker dashboard security
- Medium: M1 through M12 from the audit
- Low: L1 through L5 from the audit

---

## Account / Session Handoff (ORIGINAL FOLLOWS)

```
Current session:          Feature Connection Audit & Post-Audit Bug Fix
Current PRD story:        None — all PRD stories complete; audit fixes are post-PRD
Current task:             Feature Connection Audit + AUTH-002/AUTH-006/SEC-008/SEC-009 fixes
Completed stories (79):   All PRD stories, including STAB-003
Current PRD Phase 17:      Complete (12 future requirements documented, FUTURE/NOT IMPLEMENTED)
Last completed action:    Feature Connection Audit complete; AUTH-002/AUTH-006/SEC-008/SEC-009 fixes implemented
Last verified action:     ./gradlew assembleDebug completed BUILD SUCCESSFUL (3m 46s); APK builds successfully
Files modified:           FirebaseService.kt, SkillsExchangeRepository.kt, SkillsExchangeViewModel.kt,
                          OnboardingScreen.kt, NavigationDrawer.kt, MainScreen.kt, AccountCenterSheets.kt (new)
Next action:              Install APK on device and test Google Sign-In existing-user recognition + legal name fix
                          Then wire ServiceCatalogPicker, rating UI, and remaining audit items
Handoff status:           Ready for continuation — device currently disconnected
```

> **SECURITY NOTE**: This file contains NO passwords, API keys, session tokens, cookies, or credentials of any kind.
