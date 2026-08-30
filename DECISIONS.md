# DECISIONS.md — Skills Exchange Architectural & Design Decisions

All significant architectural, design, and implementation decisions are recorded here. This allows future agents and developers to understand why specific choices were made.

Format:
```
## [DATE] [DECISION-ID] Title
Context, options considered, decision, rationale, consequences
```

---

## 2026-08-25

### [2026-08-25] DEC-001 Agent Memory Infrastructure Approach

**Context**: The project requires AI agent continuity across session/account boundaries. This is needed because AI agents have quota limits and conversations can end. Development must survive session changes without losing state.

**Options Considered**:
1. External database or service for state persistence
2. Filesystem-based agent memory (markdown files in project root)
3. Relying on conversation history only

**Decision**: Filesystem-based agent memory using markdown files in the project root:
- `AGENTS.md` — operating rules
- `AGENT_STATE.md` — current state
- `CHANGELOG.md` — history
- `DECISIONS.md` — this file
- `TASKS.md` — task tracking
- `README_AI.md` — recovery guide

**Rationale**: 
- The project filesystem is already version-controlled (git) and is the most durable, portable persistence layer available.
- Markdown files are human-readable and AI-readable without any external tooling.
- No external services, credentials, or network access required for state recovery.
- Any authorized agent with repository access can immediately resume.

**Consequences**:
- These files must be kept up-to-date by every agent session.
- Files must never contain credentials or secrets.
- The files add minimal overhead to the project root.

---

### [2026-08-25] DEC-002 Test Infrastructure — Robolectric SDK Level 36

**Context**: `ExampleRobolectricTest.kt` and `GreetingScreenshotTest.kt` use `@Config(sdk = [36])`. SDK 36 is Android 16 (early 2026). The project targets SDK 36 (`targetSdk = 36` in `app/build.gradle.kts`).

**Decision**: Maintain `sdk = [36]` in all Robolectric test configurations.

**Rationale**: Matches the project's `targetSdk` and `compileSdk` configuration. Using a lower SDK would cause mismatches. Robolectric supports SDK 36 as of the version specified in the project's dependency catalog.

**Consequences**: New test files must use `@Config(sdk = [36])` or omit the annotation to inherit defaults.

---

### [2026-08-25] DEC-003 Screenshot Test Graphics Mode — NATIVE

**Context**: `GreetingScreenshotTest.kt` uses `@GraphicsMode(GraphicsMode.Mode.NATIVE)` required by Roborazzi for accurate screenshot rendering.

**Decision**: All Roborazzi screenshot tests must use `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.

**Rationale**: Roborazzi requires native graphics mode to render Compose UI faithfully on Robolectric. Without this annotation, screenshots may fail or produce blank images.

**Consequences**: Any new screenshot test must include this annotation.

---

### [2026-08-25] DEC-004 No Credentials in Agent Files

**Decision**: Agent memory files (`AGENTS.md`, `AGENT_STATE.md`, `CHANGELOG.md`, `DECISIONS.md`, `TASKS.md`, `README_AI.md`) must NEVER contain passwords, API keys, session tokens, cookies, signing credentials, or any form of authentication secret.

**Rationale**: These files are likely committed to version control. Credentials in committed files create severe security vulnerabilities and may violate platform terms of service.

**Implementation**: 
- Credentials go in `.env` (gitignored) or environment variables only
- `.env.example` contains only placeholder values
- Firebase configs use environment variables via Gradle secrets plugin
- Signing configs reference `System.getenv()` in `app/build.gradle.kts`

**Consequences**: Any agent session that needs API access must use pre-configured environment credentials. Agent state files reference account roles/profiles by label only — never by credential value.

---

### [2026-08-25] DEC-005 PRD-Driven Development — One Story at a Time

**Context**: The project has 30+ stories across 15 phases with complex dependencies. Previous unconstrained development can lead to partial implementations, broken dependencies, and unclear project state.

**Decision**: Strict one-story-at-a-time execution following the dependency graph in `prd.json`.

**Rationale**:
- Ensures complete, verified implementation of each feature before moving on
- Makes it easy to determine true project state at any time
- Prevents partial implementations that block later stories
- Allows any agent to pick up from a clean, known state

**Execution Order**: Phase 1 (STAB) → Phase 2 (SEC) → Phase 3 (ROLE) → Phase 4 (SKILL) → etc.

**Priority within phase**: P0 > P1 > P2 > P3, with dependency graph respected.

### [2026-08-25] DEC-006 Unified Account Data Model with Single Profile & Dual Roles
**Context**: The application required supporting users who can act as both Clients (hiring) and Workers (offering services) without forcing them to register two separate accounts with different phone numbers.
**Decision**: Create a single `UserProfileEntity` in Room and Firestore with `initialRole`, `currentActiveMode`, `workerKycStatus`, and `isWorkerActivated` flags.
**Rationale**: Simplifies authentication, keeps a single Mobile Money wallet balance, and allows frictionless mode toggling while maintaining strict gating for worker capabilities.

---

### [2026-08-25] DEC-007 Structured 125-Service Catalog with Trade-Family Skill Validation
**Context**: Preventing unverified workers from advertising incompatible random skills (e.g. Gardening combined with Electrical High-Voltage wiring).
**Decision**: Standardized on a 125-service catalog mapped into 5 trade categories and explicit skill families, requiring 1 Primary Skill + up to 2 related Additional Skills validated via `ServiceTaxonomyCatalog.validateWorkerSkills()`.
**Rationale**: Protects clients from unqualified labor and maintains marketplace trust.

---

### [2026-08-25] DEC-008 Private Storage Bucket Document Isolation
**Context**: National IDs, selfie liveness captures, and guarantor IDs contain sensitive PII.
**Decision**: Enforce strict Firebase Storage rules (`storage.rules`) restricting KYC documents to `request.auth.uid == userId || request.auth.token.admin == true`.
**Rationale**: GDPR / Uganda Data Protection and Privacy Act compliance.

---

### [2026-08-29] DEC-009 Training Progress Tracking in Separate Room Table

**Context**: Worker training progress (2-3 day rule training) needs to be tracked separately from identity verification status (KYC).

**Decision**: Create a dedicated `worker_training` Room table with module-level completion booleans (`platformRulesCompleted`, `escrowGuidelinesCompleted`, `customerEtiquetteCompleted`, `safetyProtocolsCompleted`) and a status field (NOT_STARTED, IN_PROGRESS, COMPLETED).

**Rationale**: Separating training from KYC allows independent lifecycle management. Training begins after KYC approval and must be completed before admin activation.

**Consequences**: AppDatabase migrated to version 9. Admin activation gate (TRAIN-003) will require both `kycStatus == APPROVED` and `trainingStatus == COMPLETED`.

---

### [2026-08-29] DEC-010 Dual-Gate Admin Activation (KYC + Training)

**Context**: Workers should not be activated until both identity verification (KYC) and mandatory training are complete.

**Decision**: Implement a dual-gate activation check in both the local repository (`activateWorker`) and the Cloud Function (`setWorkerActivationStatus`). Both paths verify `kycStatus == APPROVED` AND `trainingStatus == COMPLETED` before setting `isActivated = true`.

**Rationale**: Enforcing the gate at both client and server levels prevents activation bypass. The Cloud Function provides the authoritative server-side check, while the repository method provides immediate UI feedback.

**Consequences**: Admin activation will fail with descriptive error messages if either gate is not satisfied. Workers must complete both KYC and training before appearing in nearby searches.

---

## 2026-08-29

### [2026-08-29] DEC-011 ProGuard Keep Rules for Release Build

**Context**: REL-001 requires ProGuard rules to prevent minification reflection crashes in release builds. The app uses Room (reflection-based entity mapping), Firebase serialization, and Moshi JSON adapters.

**Options Considered**:
1. Use `@Keep` annotations on every entity class individually
2. Use broad ProGuard keep rules covering entire packages
3. Hybrid: package-level keep rules plus annotation-based keeps for external consumers

**Decision**: Adopted package-level keep rules in `app/proguard-rules.pro` covering `com.example.data.db.**`, Firebase/Gson annotated fields, Kotlin metadata, and coroutines internals. Also preserved SourceFile/LineNumberTable for debugging release crashes.

**Rationale**: Package-level rules are less error-prone than annotating 20+ entities individually and ensure future entities added to the package are automatically covered. Keeping Kotlin metadata and coroutines internals is required for structured concurrency to work under minification.

**Consequences**: Release APK will retain entity class names and prevent reflection crashes. APK size will be slightly larger due to kept classes, but correctness is prioritized over size optimization.

---

## 2026-08-30

### [2026-08-30] DEC-012 Job State Machine Design

**Context**: JOB-004 required implementing a proper job state machine. The previous implementation used raw strings with no validation, leading to three incompatible vocabularies across UI, backend, and Firestore rules.

**Options Considered**:
1. Use string constants with validation in ViewModel
2. Use enum with transition map
3. Use sealed class with state-specific data

**Decision**: Adopted `JobStatus` enum with a `validTransitions` map defining legal state transitions. States: DRAFT, POSTED, QUOTING, WORKER_SELECTED, ACCEPTED, WORKER_EN_ROUTE, ARRIVED, IN_PROGRESS, COMPLETED, PAYMENT_RELEASED, CANCELLED, DISPUTED, EXPIRED.

**Rationale**: Enum with transition map provides compile-time safety and clear documentation of valid flows. The transition map makes it impossible to define invalid state changes.

**Consequences**: All job status changes must go through `transitionJobStatus` which validates the transition. Invalid transitions are rejected. Audit trail tracks all changes.

---

### [2026-08-30] DEC-013 Escrow Release Security Model

**Context**: SEC-004 fixed a critical vulnerability where any authenticated user could release escrow for any job.

**Options Considered**:
1. Only check authentication (insecure)
2. Check job ownership via Firestore document
3. Check ownership + validate job state + verify worker ID

**Decision**: Implemented triple verification: job ownership (postedByResident or postedBy must match caller), job state (must be COMPLETED or WORK_COMPLETED), and worker ID verification (must match assigned provider).

**Rationale**: Defense in depth - even if one check is bypassed, others prevent unauthorized access. Server-side validation is authoritative.

**Consequences**: Unauthorized escrow release attempts are rejected with 403. Legitimate releases require proper job completion flow.

---

### [2026-08-30] DEC-014 Firestore Rules Alignment

**Context**: SEC-005/SEC-006 fixed Firestore rules that were reading from wrong paths and missing collections.

**Decision**: Updated `isWorkerKycApproved` to read from `worker_kyc` collection. Added rules for all collections: worker_kyc, worker_training, guarantors, job_quotes, notifications, disputes, momo_transactions, momo_payouts.

**Rationale**: Rules must match actual data model. Each collection needs appropriate access control based on user role and ownership.

**Consequences**: Security enforcement now works correctly. Unauthorized access to sensitive collections is denied.

---

### [2026-08-30] DEC-015 Room Migration Strategy

**Context**: DB-001 replaced `fallbackToDestructiveMigration()` with proper migrations.

**Decision**: Added `MIGRATION_11_12` that creates new tables (disputes, ratings, notifications, job_status_audit) without data loss.

**Rationale**: Destructive migration wipes all user data on schema change, unacceptable for production. Proper migrations preserve data.

**Consequences**: Future schema changes must include appropriate migrations. Data is preserved across app updates.

---

_This document will be updated whenever significant architectural or design decisions are made._

