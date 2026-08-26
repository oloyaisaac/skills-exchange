# CHANGELOG.md — Skills Exchange Development History

All meaningful development changes are recorded here in reverse chronological order.

Format: `[DATE] [STORY-ID] [TYPE] Description`

Types: `IMPL` (implementation), `FIX` (bug fix), `TEST` (test), `INFRA` (infrastructure), `DOCS` (documentation), `SECURITY` (security)

---

## 2026-08-26

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
