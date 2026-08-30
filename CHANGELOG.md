# CHANGELOG.md — Skills Exchange Development History

All meaningful development changes are recorded here in reverse chronological order.

Format: `[DATE] [STORY-ID] [TYPE] Description`

Types: `IMPL` (implementation), `FIX` (bug fix), `TEST` (test), `INFRA` (infrastructure), `DOCS` (documentation), `SECURITY` (security)

---

### [2026-08-30] [AUTH-002] [IMPL] Implement Session Persistence
- Created SessionManager class using DataStore Preferences
- Added persistence for: isAuthenticated, userPhone, userId, userName, userDistrict, fcmToken
- Updated ViewModel to restore session on app launch
- Updated loginUser, verifyOtp, logoutUser to sync with SessionManager
- Added DataStore dependency to build.gradle.kts
- Session survives app restarts and process death

### [2026-08-30] [RATE-002] [IMPL] Implement Rating Submission UI
- Added RatingSubmissionState sealed class: Idle, Submitting, Success, Error
- Added submitRating method to ViewModel with validation
- Added duplicate rating prevention
- Added rating validation (1-5 stars)
- Added getUserRatings flow for displaying user ratings
- Integrated with existing Repository → ViewModel → UI architecture

### [2026-08-30] [RATE-003] [IMPL] Implement Rating Aggregation
- Added getAverageRatingForUser method to ViewModel
- Average rating calculated from all ratings in database
- Flow-based API for reactive UI updates
- Rating aggregation happens server-side in repository
- Created SkillsExchangeMessagingService extending FirebaseMessagingService
- Added FCM token management: getToken, deleteToken, onNewToken
- Added notification channel creation for Android O+
- Added foreground notification handling with deep linking
- Added Firebase Messaging dependency to build.gradle.kts
- Updated AndroidManifest.xml to register FCM service
- Added FCM state management to ViewModel: fcmToken, hasNotificationPermission
- Added initFcm, onFcmTokenRefreshed, clearFcmToken methods
- Notifications support deep linking to relevant jobs via jobId extra
- Updated ViewModel.updateJobStatus to use audited transition path
- All status changes now create JobStatusAuditEntity records
- Audit records capture: jobId, fromStatus, toStatus, changedBy, timestamp, reason
- Invalid transitions are rejected by JobStatus.isValidTransition
- Added getJobAuditTrail method to ViewModel
- Audit trail is created as part of trusted status-transition path
- Clients cannot create false audit records directly
- Added multi-step auth flow: PHONE_ENTRY → OTP_VERIFICATION → PROFILE_SETUP
- Added AuthStep enum for tracking current step
- Added phone number input with validation (minimum 10 characters)
- Added 6-digit OTP input with verification
- Added resend cooldown timer (60 seconds)
- Added loading states for sending/verifying OTP
- Added error display with user-friendly messages
- Added step indicator showing progress through auth flow
- Added AuthState sealed class: SignedOut, SendingOtp, OtpSent, VerifyingOtp, SignedIn, Error
- Added ViewModel methods: sendOtp, verifyOtp, logoutUser, clearAuthError
- Added FirebaseService methods: sendPhoneVerificationCode, verifyPhoneCode
- Integrated with existing AuthModal UI
- Removed hardcoded demo login credentials from form
- Added "Change phone number" option during OTP verification

### [2026-08-30] [DISP-002] [IMPL] Implement Dispute UI and Workflow
- Wired dispute dialog in EscrowAndProofSheet to persistence layer.
- Added DisputeReason selector with radio buttons (Quality Issue, No Show, Incomplete Work, Overcharging, Misrepresentation, Safety Concern, Other).
- Added input validation: description required, minimum 10 characters.
- Added loading state with progress indicator during submission.
- Added success message display after successful submission.
- Added duplicate dispute prevention (checks for existing dispute by same user for same job).
- Added ViewModel methods: submitDispute, getUserDisputes, resetDisputeSubmissionState.
- Added DisputeSubmissionState sealed class for Idle/Submitting/Success/Error states.
- Updated MainScreen to pass dispute callback and state to EscrowAndProofSheet.
- Job status now correctly set to "DISPUTED" on dispute submission.

### [2026-08-30] [DB-001] [INFRA] Add Proper Room Migrations
- Replaced `fallbackToDestiveMigration()` with proper migration from version 11 to 12.
- Added migration to create new tables: disputes, ratings, notifications, job_status_audit.
- Data is now preserved across schema changes.

### [2026-08-30] [RATE-001] [IMPL] Implement Rating Entity and Persistence
- Created `RatingEntity` Room table for storing ratings and reviews.
- Created `RatingDao` with queries for user ratings, job ratings, and average calculation.
- Added repository methods: getAllRatings, getRatingsForUser, submitRating, getAverageRatingForUser.
- Added `hasUserRatedJob` to prevent duplicate reviews.

### [2026-08-30] [NOTIF-001] [IMPL] Implement Notification Entity and Persistence
- Created `NotificationEntity` Room table for storing user notifications.
- Created `NotificationDao` with queries for user notifications and unread count.
- Added repository methods: getNotificationsForUser, createNotification, markNotificationRead.
- Added NotificationType enum with all notification types.

### [2026-08-30] [DISP-003] [IMPL] Implement Admin Dispute Resolution
- Added `resolveDispute` Cloud Function for admin dispute resolution.
- Added `getDisputesQueue` Cloud Function for admin dispute queue.
- Supports resolution types: REFUND_FULL, REFUND_PARTIAL, NO_ACTION, WORKER_SUSPENDED.
- Notifies both parties on resolution.

### [2026-08-30] [DISP-001] [IMPL] Implement Dispute Entity and Persistence
- Created `DisputeEntity` Room table for storing dispute data.
- Created `DisputeDao` with queries for user disputes and status filtering.
- Added repository methods: getAllDisputes, getDisputesForUser, createDispute, updateDisputeStatus.
- Added DisputeStatus and DisputeReason enums.

### [2026-08-30] [JOB-004] [IMPL] Implement Job State Machine
- Created `JobStatus` enum with all valid job states.
- Added `isValidTransition` and `canTransition` methods for state validation.
- Valid states: DRAFT, POSTED, QUOTING, WORKER_SELECTED, ACCEPTED, WORKER_EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED, PAYMENT_RELEASED, CANCELLED, DISPUTED, EXPIRED.
- Added `transitionJobStatus` repository method with audit logging.
- Added `JobStatusAuditEntity` for tracking all status changes.

### [2026-08-30] [SEC-007] [SECURITY] Remove Hardcoded Webhook Secret Fallback
- Removed default fallback value for MOMO_WEBHOOK_SECRET.
- Webhook verification now fails loudly if secret is not configured.
- Prevents unauthorized webhook processing with default secret.

### [2026-08-30] [SEC-006] [SECURITY] Add Missing Firestore Rules for Collections
- Added security rules for worker_kyc, worker_training, guarantors, job_quotes, notifications.
- Added rules for disputes, momo_transactions, momo_payouts collections.
- Proper access control for each collection based on user role.

### [2026-08-30] [SEC-005] [SECURITY] Fix Firestore Rules KYC Path Mismatch
- Updated `isWorkerKycApproved` helper to read from `worker_kyc` collection instead of `users/{uid}/verification`.
- Fixed broken worker-write security enforcement.

### [2026-08-30] [SEC-004] [SECURITY] Fix releaseEscrowToWorker Authorization Vulnerability
- Added job ownership verification to releaseEscrowToWorker Cloud Function.
- Only job owner (postedByResident or postedBy) can release escrow.
- Added state validation - escrow can only be released for completed jobs.
- Added worker ID verification against assigned provider.
- Prevents fund theft by unauthorized users.

## 2026-08-29

### [2026-08-29] [REL-001] [INFRA] Production Build ProGuard Rules & Release Sign-Off
- Added comprehensive ProGuard rules to `app/proguard-rules.pro` for release builds.
- Keep rules for Room entities, DAOs, and `@Entity`/`@Database`/`@Dao` annotated classes.
- Keep rules for Firebase and Google Play Services classes.
- Keep rules for Moshi/JSON serialization with `@SerializedName` annotations.
- Preserved Kotlin metadata, annotations, signatures, and coroutine internals.
- Added SourceFile/LineNumberTable attributes for debugging release crashes.
- All 47 of 48 stories now complete. STAB-003 remains blocked on JDK/Gradle environment conflict.

### [2026-08-29] [OFF-001] [IMPL] Offline Action Queue & Sync Manager
- Created `PendingActionEntity` Room table for offline write operations.
- Created `PendingActionDao` with queries for pending actions, status updates, and retry tracking.
- Added `PendingActionEntity` to `AppDatabase` (version 11) and exposed `pendingActionDao()`.
- Added repository methods: `enqueueAction`, `markActionCompleted`, `incrementActionRetry`, `getPendingActions`, `getPendingActionsCount`.
- Added ViewModel: `pendingActions` state, `enqueueOfflineAction`, `flushPendingActions`.
- Wired `NetworkConnectivityObserver` to automatically flush pending actions when network reconnects.
- Actions support retry count with max retries before marking as FAILED.

### [2026-08-29] [MARKET-003] [IMPL] Resident Quote Review & Worker Selection
- Created `QuoteComparisonSheet` composable in `JobPipelineKanban.kt` with quote list, worker details, pricing comparison, and accept/decline actions.
- Added "Compare Quotes" button to `JobPipelineCard` for jobs in "Requested" status.
- Wired `QuoteComparisonSheet` into `MainScreen.kt` with state management and accept quote callback.
- Sheet shows worker name, rate, hours, total, notes, and status (PENDING/ACCEPTED/REJECTED).

### [2026-08-29] [MARKET-002] [IMPL] Open Job Posting & Multi-Quote Bidding
- Created `JobQuoteEntity` Room table for storing worker quote proposals.
- Created `JobQuoteDao` with queries for job quotes, worker quotes, and status updates.
- Added `JobQuoteEntity` to `AppDatabase` (version 10) and exposed `jobQuoteDao()`.
- Added repository methods: `submitQuote`, `respondToQuote`, `getQuotesForJob`, `getQuotesByWorker`, `countPendingQuotesForJob`.
- Added ViewModel methods: `submitQuote`, `respondToQuote`, `getQuotesForJob`, `getQuotesByWorker`.
- Workers can submit quotes with rate, hours, and notes; clients can accept/decline with chat notifications.

### [2026-08-29] [JOB-003] [IMPL] Recurring Contract Pricing Negotiation Workflow
- Added `recurringContractProposal`, `recurringNegotiatedRate`, `recurringProposalStatus` fields to `JobPostEntity`.
- Added DAO methods: `updateRecurringProposal`, `updateRecurringProposalStatus`.
- Added repository methods: `submitRecurringProposal`, `respondToRecurringProposal`.
- Added ViewModel methods: `submitRecurringProposal`, `respondToRecurringProposal`.
- Workers can propose custom recurring rates; clients can accept/decline with chat notifications.

### [2026-08-29] [JOB-002] [IMPL] Recurring Schedule Configuration UI
- Created `JobTypeSelector` composable with One-Time/Recurring toggle and schedule frequency picker (Daily, Weekly, Bi-Weekly, Monthly, Custom).
- Integrated `JobTypeSelector` into `QuickRequestDrawer` between Step 2 and Step 3.
- Updated `QuickRequestDrawer` onPostJob callback to include `jobType` and `recurringSchedule` parameters.
- Updated `SkillsExchangeViewModel.postQuickJob` and `SkillsExchangeRepository.addJobPost` to persist new fields.
- Recurring schedule stored in `JobPostEntity.recurringSchedule` (empty for one-time jobs).

### [2026-08-29] [TRAIN-003] [IMPL] Admin Activation Gate requiring Verification + Training
- Added `activateWorker(userId)` repository method enforcing both KYC approval and training completion.
- Added `ActivationResult` sealed class with Success/Failure outcomes.
- Updated `setWorkerActivationStatus` Cloud Function to validate KYC and training status server-side before activation.
- Added `activateWorkerWithGate` ViewModel method for gated activation flow.

### [2026-08-29] [TRAIN-002] [IMPL] Worker Rule Training UI & Completion Tracking
- Created `WorkerTrainingScreen` composable in `AccountComplianceDialog.kt` with training module list, progress tracking, and completion buttons.
- Added `TrainingModule` data class for module metadata (Platform Rules, Escrow Guidelines, Customer Etiquette).
- Added ViewModel methods: `startTraining`, `completeTrainingModule`, `completeTraining`, `currentTraining` state flow.
- Wired training screen into `MainScreen.kt` — shown when KYC is approved but training not completed.
- Training completion updates both `worker_training` table and `user_profiles.trainingCompleted` flag.

### [2026-08-29] [TRAIN-001] [IMPL] Worker Training Progress & Content Data Schema
- Created `WorkerTrainingEntity` Room table with status, completedModulesCsv, module completion booleans, and timestamps.
- Created `WorkerTrainingDao` with insert, update, and complete operations.
- Added `WorkerTrainingEntity` to `AppDatabase` (version 9) and exposed `workerTrainingDao()`.
- Added repository flows and methods: `currentTraining`, `getTrainingForUser`, `startTraining`, `updateTrainingModule`, `completeTraining`.

## 2026-08-28

### [2026-08-28] [ADMIN-001..004] [IMPL] Admin Operations Dashboard Cloud Functions
- Added `getPendingVerificationQueue` Cloud Function returning worker KYC applications sorted by 48h SLA deadline.
- Added `reviewWorkerApplication` Cloud Function accepting APPROVE/REJECT/CHANGES_REQUIRED decisions with feedback.
- Added `setWorkerActivationStatus` Cloud Function for activating/suspending worker bidding privileges.
- Added `isAdmin()` helper verifying admin custom claim on all admin endpoints.
- Added corresponding repository methods (`getAdminVerificationQueue`, `submitAdminVerificationDecision`, `setWorkerActivation`).
- Added ViewModel state and methods (`adminVerificationQueue`, `loadAdminVerificationQueue`, `submitAdminDecision`, `setWorkerActivation`).
- Wired FirebaseFunctions calls in `FirebaseService.kt` for all three admin endpoints.

### [2026-08-28] [SAFE-002] [IMPL] Live Emergency SOS Alert Dispatch
- Updated `SkillsExchangeViewModel.triggerSosAlert()` to wire SOS panic button to `repository.triggerEmergencySos()` with live GPS telemetry.
- Added `SmsNotificationService` constructor parameter to `SkillsExchangeRepository` for SOS dispatch dependency injection.
- SOS button now dispatches high-priority SMS with worker GPS coordinates to emergency contacts via `SmsNotificationService.dispatchEmergencySosAlert()`.

## 2026-08-27

### [2026-08-27] [COMM-001] [IMPL] Real-Time Firestore Chat & Phone Masking
- Added `streamChatMessages` and `sendChatMessage` with `maskPhoneNumbers` to `FirebaseService.kt`.
- Added `getRealtimeChatMessages` bridge in `SkillsExchangeRepository.kt` converting Firestore maps to `ChatMessageEntity`.
- Updated `SkillsExchangeViewModel.kt` `getChatMessagesForJob` to use real-time Firestore flow.

### [2026-08-27] [MAP-001] [IMPL] Integrate Google Maps SDK for Nearby Worker View
- Replaced custom Canvas drawing in `InteractiveMapView.kt` with Google Maps Android SDK MapView.
- Added `play-services-maps` dependency to `build.gradle.kts` and `libs.versions.toml`.

### [2026-08-27] [PAY-004] [IMPL] Material Advance Escrow Partial Disbursement
- Added `approveMaterialAdvance` to `SkillsExchangeRepository.kt` calculating 50% materials subtotal and updating job status.

### [2026-08-27] [PAY-003] [IMPL] Escrow Release & Worker MoMo Payout
- Added `releaseEscrowToWorker` callable Cloud Function in `functions/src/index.ts` to credit worker wallet upon escrow release.

### [2026-08-27] [PAY-002] [IMPL] Escrow Lock & Webhook Confirmation
- Updated `processMoMoWebhook` in `functions/src/index.ts` to lock escrow and send notifications to resident and worker upon successful payment.

### [2026-08-27] [PAY-001] [IMPL] Mobile Money Collection Intent API
- Replaced mock `delay(600)` in `MoMoPaymentService.kt` with Retrofit HTTP POST to MTN MoMo Collection API.
- Created `MoMoCollectionApi.kt` Retrofit interface for `/collection/v1_0/requesttopay`.

### [2026-08-27] [MARKET-001] [IMPL] Nearby Verified Worker Discovery
- Added `isActivated` field to `ServiceProviderEntity` in `Entities.kt`.
- Updated `providers` flow in `SkillsExchangeViewModel.kt` to filter only activated workers with `kycStatus == APPROVED`.

### [2026-08-27] [JOB-001] [IMPL] One-Time & Recurring Job Type Data Model
- Added `jobType`, `recurringSchedule`, and `recurringStartDate` fields to `JobPostEntity` in `Entities.kt`.
- Updated `syncJobToFirestore` in `FirebaseService.kt` to persist new job type fields.

### [2026-08-27] [KYC-007] [IMPL] Worker 'Changes Required' feedback view & resubmission workflow
- Built `ChangesRequiredView` in `AccountComplianceDialog.kt` displaying admin feedback, support phone/WhatsApp links, and document update button.

### [2026-08-27] [KYC-006] [IMPL] Verification State Machine & 48h SLA Timestamps
- Added state transition methods to `SkillsExchangeRepository.kt`: submitWorkerKycApplication, startKycReview, requestKycChanges, approveKyc, rejectKyc.
- Added `getKycSlaDeadline` calculating 48-hour review target from submittedAt timestamp.

### [2026-08-27] [KYC-005] [IMPL] Primary Skill Qualification Certificate & Video Evidence upload
- Added `uploadQualificationEvidence` to `FirebaseService.kt` for certificate photos and video links.
- Built `QualificationEvidenceSheet` in `AccountComplianceDialog.kt` with certificate/video toggle and upload flow.

### [2026-08-27] [KYC-004] [IMPL] Guarantor Identity & National ID Upload UI
- Built `GuarantorFormSheet` in `AccountComplianceDialog.kt` with 2-step form, photo pickers, and district selection.
- Added `saveGuarantors` and `isValidGuarantorCount` methods to `SkillsExchangeViewModel.kt`.

### [2026-08-27] [KYC-003] [IMPL] Two-Guarantor Data Model & Validation rules
- Created `GuarantorEntity` in `Entities.kt` with name, phone, relationship, district, and ID storage paths.
- Created `GuarantorDao` in `Daos.kt` with insert, delete, count, and query operations.
- Added `validateGuarantorCount` to `SkillsExchangeRepository.kt` enforcing exactly 2 guarantors.
- Upgraded `AppDatabase` to version 8.

### [2026-08-27] [KYC-002] [IMPL] Worker Document Upload pipeline to Firebase Storage
- Added `uploadKycDocument` method to `FirebaseService.kt` uploading image bytes to `users/{userId}/kyc/` path.
- Added `firebase-storage` dependency to `build.gradle.kts` and `libs.versions.toml`.

### [2026-08-27] [KYC-001] [IMPL] Worker Identity & National ID data schema
- Created `WorkerKycEntity` isolated Room table in `Entities.kt` storing full legal name, National ID number, DOB, gender, and ID document storage paths.
- Created `WorkerKycDao` in `Daos.kt` with insert, status update, and review query methods.
- Upgraded `AppDatabase` to version 7 with `fallbackToDestructiveMigration`.

### [2026-08-27] [LOC-004] [IMPL] Public Location Privacy & District-Level Aggregation
- Added `getDistrictJobStatsForProvider` to `SkillsExchangeViewModel.kt` to compute dynamic district job counts from actual job history.
- Updated `ProviderDetailSheet.kt` to display dynamic district-level job breakdown instead of hardcoded values.
- Obfuscated reviewer identities in public endorsements by removing `endorsedByNeighborName` display.

---

## 2026-08-26

### [2026-08-26] [LOC-003] [IMPL] Worker District & Location management
- Upgraded `UserProfileEntity` and `AppDatabase` (v6) to support `latitude`, `longitude`, and `address` fields.
- Upgraded `updateWorkerLocation` in `SkillsExchangeViewModel.kt` to persist all location metadata.

### [2026-08-26] [LOC-002] [IMPL] Manual Location Correction
- Added `EditLocationDialog` in `GpsLocationBanner.kt` to allow users to manually correct their active district.
- Wired manual district selection to `SkillsExchangeViewModel` through `MainScreen.kt`.

### [2026-08-26] [LOC-001] [IMPL] Client Location Onboarding & GPS Detection
- Implemented GPS location requesting logic using LocationHelper in AuthModal.kt, which sets the user's neighborhood automatically.

## 2026-08-25

### [2026-08-25] [SAFE-001] [SECURITY] Dynamic 4-Digit Doorstep PIN Generation
- Replaced static default `'4829'` PIN with secure random 4-digit PIN generated per job creation in `SkillsExchangeRepository.kt`.

### [2026-08-25] [SKILL-001..003] [IMPL] 125-Service Taxonomy, Validation Engine & Picker UI
- Created `ServiceTaxonomyCatalog` in `ServiceTaxonomy.kt` with 125 structured services across 5 hubs (Construction, Cleaning, Technology, Automotive, Personal).
- Implemented `validateWorkerSkills` engine enforcing 1 Primary + up to 2 Related Additional skills within compatible trade families.
- Created `ServiceCatalogPickerModal` multi-step Composable sheet with live validation and dynamic trade-family filtering.

### [2026-08-25] [ROLE-001..003] [IMPL] Unified Account Model, Mode Switching & Activation Gate
- Created `UserProfileEntity`, `UserProfileDao`, and migrated `AppDatabase` to version 5.
- Implemented persistent Client/Worker mode switching in `SkillsExchangeViewModel` and `SkillsExchangeRepository`.
- Created `WorkerVerificationStatusView` access gate component in `ui/components/` to restrict unverified/unactivated worker access to the Freelancer Dashboard in `MainScreen.kt`.

### [2026-08-25] [SEC-002..003] [SECURITY] HMAC Webhook Signature Verification & Firebase Storage Security Rules
- Implemented HMAC-SHA256 signature verification in `functions/src/index.ts` for payment callbacks with `crypto.timingSafeEqual`.
- Created `storage.rules` isolating National IDs, guarantor documents, and qualification certificates under owner and admin authorization.

### [2026-08-25] [AI-001..002] [FIX] Gemini 2.0 Flash Model Identifier & Audio Encoding Alignment
- Corrected invalid model identifier `gemini-3.5-flash` to `gemini-2.0-flash` in `GeminiAudioTranscriptionService.kt` and `GeminiMapsGroundingService.kt`.
- Updated `AudioRecorderHelper.kt` to record in MPEG_4/AAC audio format with aligned `audio/mp4` MIME type.

### [2026-08-25] [SEC-001] [SECURITY] Harden Firestore Security Rules — Verified
- Verified owner UID validation on provider updates and KYC sub-collections in `firestore.rules`.

### [2026-08-25] [INFRA] Agent Memory Infrastructure Established
- Created permanent operating manual (`AGENTS.md`), current state tracker (`AGENT_STATE.md`), decisions log (`DECISIONS.md`), synchronized task status (`TASKS.md`), and recovery guide (`README_AI.md`).

### [2026-08-25] [STAB-001..002] [TEST] Robolectric Assertion & Screenshot Test Fixes — Verified
- Verified `ExampleRobolectricTest.kt` string resource assertion (`"Skills Exchange"`).
- Verified `GreetingScreenshotTest.kt` theme and composable imports (`SkillsExchangeTheme`).
