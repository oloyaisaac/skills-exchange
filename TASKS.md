# TASKS.md — Skills Exchange Human-Readable Task Status

Synchronized with `prd.json`. This is the human-readable view of the development backlog.
Last synchronized: 2026-08-29T19:02:00+03:00

Legend: ✅ Completed | 🔄 In Progress | ⏳ Pending | 🚫 Blocked | ❌ Failed

---

## Phase 1 — Baseline, Infrastructure & Test Stabilization
> Fix broken Robolectric and Roborazzi test suite to establish clean CI/CD build baseline.

| ID | Priority | Title | Status | Notes |
|---|---|---|---|---|
| STAB-001 | P0 | Fix Robolectric unit test assertion mismatch | ✅ Completed | Code verified correct — asserts "Skills Exchange" |
| STAB-002 | P0 | Fix GreetingScreenshotTest theme and composable imports | ✅ Completed | Code verified correct — uses SkillsExchangeTheme |
| STAB-003 | P3 | Establish automated test verification script | 🚫 Blocked | JDK 25 / Gradle 8.9 foojay plugin environment conflict |

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
| MAP-001 | P1 | Integrate Google Maps SDK for Nearby Worker View | ✅ Completed | Google Maps MapView composable, play-services-maps dep |
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
| DB-001 | P1 | Add Proper Room Migrations | ✅ Completed | Migration 11→12 |

---

## Summary

| Status | Count |
|---|---|
| ✅ Completed | 78 |
| 🔄 In Progress | 0 |
| ⏳ Pending | 0 |
| 🚫 Blocked | 1 (STAB-003) |
| **Total** | **79** |

---

_This file is synchronized with `prd.json`. Always update both when story status changes._
_Last synchronized: 2026-08-31T12:42:00+03:00_
