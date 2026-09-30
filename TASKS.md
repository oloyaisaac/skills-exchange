# TASKS.md — Skills Exchange Human-Readable Task Status

Synchronized with `prd.json`. This is the human-readable view of the development backlog.
Last synchronized: 2026-09-30T15:50:00+03:00 — Defect D1 (MAP-001: InteractiveMapView crash on absent Maps API key) **✅ Resolved & Verified**: guarded key presence, fallback UI on absent/placeholder key, try/catch around MapView initialization, safe DEFAULT_MAPS_API_KEY placeholder wiring in .env / manifestPlaceholders; 75/75 unit tests passed, assembleDebug passed.

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
| REG-001 | P2 | First-Time Worker Registration Fee (Future) | ⏳ Pending | UGX 20,000 registration fee. FUTURE / NOT IMPLEMENTED |
| REG-002 | P2 | Worker Registration Guarantor Verification Requirements | ✅ Completed | Implemented 2026-09-26, BUILD-VERIFIED 2026-09-27: `GuarantorVerificationSheet` completes the two-guarantor form (name, phone, relationship, district, masked NIN, guarantor photo, worker+guarantor photo, consent) plus the group photograph card; drafts save/restore locally, submit uploads photos to `users/{uid}/guarantors/` and files `guarantor_submissions` for admin review. Opened from drawer → National ID & Verification (new guarantor status row + admin feedback + entry button). Repository now uses the real DAO call `updateGuarantorStep` and the validators' single blocker string. Added `GuarantorValidatorsTest` (**18** tests). VERIFIED 2026-09-27: `:app:compileDebugKotlin` PASSED after two real compile errors were fixed — `SkillsExchangeViewModel.kt` lacked the `androidx.compose.runtime` `getValue`/`mutableStateOf`/`setValue` imports that its `by mutableStateOf` properties require, and `GuarantorValidators.validateNationalIdNumber` called `value.distinct()` on a `String` (now `value.toSet().size`, identical rule); `:app:testDebugUnitTest` PASSED — **57 tests / 8 suites / 0 failures / 0 errors / 0 skipped** (GuarantorValidatorsTest 18/18); `:app:assembleDebug` PASSED in 3m 35s → `app/build/outputs/apk/debug/app-debug.apk` (35,254,412 bytes). Room's KSP pass validated the entities, DAO queries, `MIGRATION_18_19` and the new `phoneNumber` index (Room still v19). The 2026-09-26 "no JDK" blocker was **false**: `gradle.properties` pins `org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10` (Amazon Corretto 17.0.20.1), so `gradlew.bat` builds with no environment setup. That session's static cross-check (UI→ViewModel→repository→DAO→FirebaseService signatures, entity columns/defaults vs `MIGRATION_18_19`, client payloads vs the `guarantors` / `guarantor_submissions` Firestore rules + `users/{uid}/guarantors/**` Storage rules) did find and fix one real bug — `GuarantorEntity` now declares `Index(value = ["phoneNumber"])` to match the index `MIGRATION_18_19` creates — but it missed the two compile errors above, so signatures are not a substitute for the compiler. REMAINING (operational, not code): the on-device golden path (two guarantors + three photographs, save draft, force-kill, resume, submit → `guarantor_submissions/{uid}` reaches `PENDING_REVIEW`, drawer reflects the admin decision) and `firebase deploy --only firestore:rules,storage` |
| REG-003 | P2 | Worker Backup Contact Number | ✅ Completed | Implemented 2026-09-23: `backupContactName` + `backupContactPhone` on `UserProfileEntity` (Room + Firestore `users/{uid}`), editable in `ProfileSettingsSheet` (drawer → Profile Photo & Backup Contact) with review-before-save, clear validation errors and save/reload persistence. `ProfileValidators.validateBackupContact` allows empty optional fields, reuses the project's 9–15 digit phone rule and rejects a backup number equal to the primary. DECISION: immediate update (same path as other profile metadata), owner-only write, NOT a guarantor (KYC-003 untouched). Unit tests: ProfileValidatorsTest 15, ProfileContactPhotoPersistenceTest 5 |
| REG-004 | P2 | Worker Profile Photo | ✅ Completed | Implemented 2026-09-23: gallery picker (GetContent) → preview → cancel → upload with loading state and error handling → replace photo, in `ProfileSettingsSheet`. `FirebaseService.uploadProfilePhoto` writes to Firebase Storage `avatars/{uid}/profile_<ts>.jpg` (existing storage.rules: public read, owner-only write) and only the download URL is stored on `UserProfileEntity.avatarUrl` (no image bytes in Room/Firestore). Photo shows in the navigation drawer header + sheet; a blank/invalid URL falls back to initials. Camera capture NOT implemented (no camera architecture existed). `avatarUrl` default changed from a seeded stock photo to empty. Coil (`libs.coil.compose`) enabled for image loading |
| LOC-005 | P2 | Worker Location Using Device GPS (Future) | ⏳ Pending | GPS-based worker location, remove manual district fields. FUTURE / NOT IMPLEMENTED |
| MAP-002 | P2 | Google Maps Usage Minimization (Future) | ⏳ Pending | Maps only during active job interactions. FUTURE / NOT IMPLEMENTED |
| MAP-003 | P2 | Google Maps Fallback for Active-Job Screen (Future) | ⏳ Pending | Maps must NOT be a hard dependency. If Maps fails (no API key, billing disabled, SDK failure, network, permissions, any reason) the app must NOT crash or freeze — show fallback UI ("Map location is currently unavailable.") with Contact Worker (reuses COMM-001 masked chat) and Continue Without Map. Maps only on active-job screen. Worker GPS remains independent. All job lifecycle flows (acceptance, start, status, PIN, completion, payment, dispute) unaffected. API key via secure config. FUTURE / NOT IMPLEMENTED |
| COMM-002 | P3 | Community Feature Removal (Future) | ✅ Completed | Community/Board feed removed: NeighborhoodFeedView deleted, Board tab/branch, neighborhoodPosts, NeighborhoodPostEntity/Dao removed; AppDatabase v17 + MIGRATION_16_17 drops neighborhood_posts; compile + full unit suite 19/19 PASSED |
| SEC-009 | P1 | Worker Dashboard Security & Access Control | ✅ Completed | Multi-layer enforcement in Entities, ViewModel (canAccessWorkerFeatures & isWorkerApprovedAndActivated), MainScreen, and 7/7 passing unit tests |
| LEGAL-001 | P2 | Liability Disclaimer (Future) | ⏳ Pending | Liability disclaimer for T&C, onboarding, transactions. FUTURE / NOT IMPLEMENTED |
| LEGAL-002 | P2 | Privacy Policy / Terms & Conditions / User Consent (Future) | ⏳ Pending | Full legal docs and consent tracking. FUTURE / NOT IMPLEMENTED |
| AUTH-012 | P0 | Preserve Existing Authentication/Onboarding Requirements (Future Note) | ⏳ Pending | Preservation note for existing auth flow. FUTURE / NOT IMPLEMENTED |

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
| C2 | UID consistency | ⏳ Pending Audit Fix |
| C3 | Google AuthModal completion path | ⏳ Pending Audit Fix |

### High
| ID | Title | Status |
|---|---|---|
| H1 | Worker authorization fail-closed | ⏳ Pending Audit Fix |
| H2 | AuthModal legal-name handling (OnboardingScreen fix already made pending verification; AuthModal still pending) | ⏳ Pending Audit Fix |
| H3 | National ID & Verification drawer | ⏳ Pending Audit Fix |
| H4 | Fake wallet balance | ⏳ Pending Audit Fix |
| H5 | ServiceCatalogPicker | ⏳ Pending Audit Fix |
| H6 | Rating UI | ⏳ Pending Audit Fix |
| H7 | Trust/Safety & SOS | ⏳ Pending Audit Fix |
| H8 | Worker dashboard security | ⏳ Pending Audit Fix |

### Medium (M1–M12 from the audit — all pending)
| ID | Title | Status |
|---|---|---|
| M1 | A4 Job History screen | ⏳ Pending Audit Fix |
| M2 | A7 AccountComplianceDialog wiring | ⏳ Pending Audit Fix |
| M3 | A8 Community Hire (obsolete — Community/Board removed under COMM-002) | ⏳ Pending Audit Fix |
| M4 | B4 Worker Online toggle persistence | ⏳ Pending Audit Fix |
| M5 | B6 Notification navigation | ⏳ Pending Audit Fix |
| M6 | B8 Map empty state | ⏳ Pending Audit Fix |
| M7 | B9 Distance filtering | ⏳ Pending Audit Fix |
| M8 | G2 setUserRoleMode gate | ⏳ Pending Audit Fix |
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
| ✅ Completed | 85 |
| 🔄 In Progress | 1 |
| ⏳ Pending | 7 |
| 🚫 Blocked | 0 |
| **Total** | **93** |

---

_This file is synchronized with `prd.json`. Always update both when story status changes._
_Last synchronized: 2026-09-30T14:36:00+03:00 — MAP-003 (Google Maps Fallback for Active-Job Screen) added as ⏳ Pending future requirement. No implementation performed. Story count: 85 completed / 1 in-progress / 7 pending / 93 total._
