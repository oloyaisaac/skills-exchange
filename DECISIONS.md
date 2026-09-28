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

---

## 2026-09-17

### [2026-09-17] DEC-018 Feature Connection Audit and Deferred Fix Register

**Context:** The application contains UI elements and components that are not always connected to their intended functionality. A READ-ONLY Feature Connection Audit (48 controls) found 8 disconnected (A1-A8), 9 partially connected (B1-B9), plus security/authorization (G1-G7), placeholder/demo (D1-D11), navigation/UX (N1-N4), permission (P1-P2), map (M1-M4), authentication (9 items), and Community removal references.

**Decision:** Perform a full connection audit before implementing individual fixes, and maintain the resulting findings as a persistent pending-fix register (AGENT_STATE.md 2026-09-17; TASKS.md Pending Fixes). DOCUMENTATION ONLY — no source code changed, no fixes implemented, no commit, prd.json untouched.

**Rationale:** Prevents fixing isolated symptoms while missing other disconnected features.

**Consequences:** The findings will be addressed systematically after the audit documentation is preserved. Nothing is marked completed from this entry.

---

## 2026-09-17

### [2026-09-17] DEC-019 Audit C1/C2/C3 Implementation (UNVERIFIED — build pending)

**Context:** The 2026-09-17 audit register lists C1 (profile persistence), C2 (UID consistency), C3 (Google completion) as the critical fix batch. Prior code already had the Firestore `users/{uid}` mirror, but synthetic-ID fallbacks (`user_<ts>`, `google_user`, `user_default_me`, `worker_default`, `anonymous`) and blank-phone completion paths remained.

**Decision:** Implement C1/C2/C3 minimally and fail-closed: recover Firebase-persisted sessions only for `isOnboardingCompleted` profiles; resolve the Firebase UID everywhere or abort instead of forking placeholder identities; require typed legal name + non-blank phone for completion; preserve role selection and worker KYC/activation/training gates; touch only `SkillsExchangeViewModel.kt`, `FirebaseService.kt`, `SkillsExchangeRepository.kt`, `AuthModal.kt`, `OnboardingScreen.kt`, `AccountComplianceDialog.kt`. No Phase-17 work, no Community removal, no new PRD IDs, no prd.json change.

**Rationale:** Fail-closed identity handling prevents duplicate/forked profiles while keeping the smallest diff that satisfies the audit requirements.

**Consequences:** C1/C2/C3 are implemented in source but NOT verified (compile still running — foojay JDK-25 toolchain download contention). No test/APK/device run yet. TASKS.md C1–C3 stay ⏳ Pending until verification lands.

**Verification follow-up (2026-09-17):** Compile passed after restoring the original one-argument `triggerSosAlert` signature, fixing only a duplicate-overload regression. The targeted persistence test could not execute because Robolectric runtime dependencies were unreachable. Keep C1/C2/C3 UNVERIFIED/PENDING; do not substitute compile or source inspection for Google/Firestore/device behavior verification. APK/install and manual A–F remain untested even though the Samsung SM-A025F is connected. Preserve dependencies, Gradle/AGP/toolchain configuration and PRD; resume the same targeted test when networking is available. JDK 17 launched Gradle, but existing daemon criteria selected JDK 25, so JDK-17-only execution is not established. This records a blocker, not successful completion or a new architecture decision.

**Verification outcome (2026-09-17, later session):** Automated verification completed without any dependency or configuration change: targeted `UserProfilePersistenceTest` passed 6/6 running `--offline` against the pre-existing complete Gradle cache at `D:\gradle-home` (earlier failures were environmental — the project `GRADLE_USER_HOME` cache lacked nine Robolectric runtime JARs and downloads stalled; copying JARs alone was insufficient, pointing `GRADLE_USER_HOME` at the complete cache resolved it). `:app:assembleDebug` passed and `adb install -r` succeeded on the Samsung SM-A025F. Manual cases A–F still require interactive Google/phone input on the device, so C1/C2/C3 remain PENDING in TASKS.md and must not be marked completed until they are recorded. prd.json unchanged (79 completed stories, 12 Phase-17 pending); no commit made.

**Device UI follow-up (2026-09-17):** Correct only the observed contrast defect using Material 3 OutlinedTextField explicit Slate900 text/white container colors and the existing blue cursor, and the inspected onboarding district omission using an empty initial district plus a required/nonblank completion gate. Keep legal-name Google prefill disabled. No auth redesign or Firebase configuration change: source wiring is present but the missing chooser is not reproduced and no precise failure is captured. Compile/APK and data-preserving Samsung installation passed (22:46:09, installed/local hash match); rendered contrast, Google button/chooser and district validation await user retest. Maintain C1/C2/C3 PENDING rather than infer behavior from compile/install. No new PRD IDs or unrelated fixes.




---

## 2026-09-16

### [2026-09-16] DEC-016 Future Requirements Documentation Capture

**Context**: 12 new future requirements were identified that need to be permanently recorded in the project documentation so they are not forgotten. These requirements span payment fees, worker registration, guarantor verification, GPS location, maps usage, community removal, dashboard security, and legal/disclaimer items.

**Decision**: Create Phase 17 "Future Requirements & Post-Launch Enhancements" in prd.json and document all 12 requirements with clear "FUTURE / PLANNED / NOT IMPLEMENTED" markers. Update requirement_file.md, TASKS.md, AGENT_STATE.md, and CHANGELOG.md to reflect the new requirements.

**Rationale**: 
- Centralized documentation ensures future Anti-Gravity sessions can discover these requirements
- Clear "NOT IMPLEMENTED" status prevents premature implementation
- Open questions are explicitly recorded for future resolution
- Existing PRD structure and story IDs are preserved

**Consequences**:
- 12 new story IDs added: PAY-005, REG-001, REG-002, REG-003, REG-004, LOC-005, MAP-002, COMM-002, SEC-009, LEGAL-001, LEGAL-002, AUTH-012
- All new stories have status "pending" (NOT completed)
- Existing 79 completed stories unchanged
- No code changes made

---

### [2026-09-16] DEC-017 Feature Connection Audit & Post-Audit Fixes

**Context**: A systematic feature connection audit was performed (Phases 1-12) to identify UI features that appear functional but are not properly connected to their intended functionality. The audit discovered: 8 confirmed disconnected features (A1-A8), 9 partially connected features (B1-B9), 23 correctly connected features, 11 placeholder/demo items, plus auth/security/map/navigation issues.

**Decision**: Prioritize and fix the critical/high-severity issues from the audit:

1. **AUTH-002/AUTH-006 (C1/C2/C3)**: Existing Google user not recognized → Root cause: profiles live only in Room and are deleted on logout; UID lookup failed due to synthetic IDs; Google AuthModal didn't dismiss on existing-user detection
   - Fix: Added Firestore `users/{uid}` identity record; resolve real Firebase UID; fix `onComplete(false)` handling

2. **SEC-009/SEC-008 (G1/G2)**: Worker dashboard security
   - Fix: `isWorkerApprovedAndActivated` fail-closed (`true` → `false`); `setUserRoleMode()` enforces `canAccessWorkerFeatures()` gate at ViewModel boundary; `canAccessWorkerFeatures()` requires `isAuthenticated.value`

3. **Legal name (B3)**: Google display name used as legal name
   - Fix: Removed `account.displayName` pre-population in `AuthModal.kt` (was already fixed in `OnboardingScreen.kt`)

4. **Drawer features (A3, A4, A5, G4, G5)**: Hardcoded fake wallet balance, silent no-op National ID item, wrong destination for History/Wallet/SOS
   - Fix: New `AccountCenterSheets.kt` with real destination sheets; removed hardcoded `UGX 240` and `"+256 700 123 456"` fallback phone

5. **Orphaned components (A6, A7)**: `ServiceCatalogPicker` and `AccountComplianceDialog` have zero call sites
   - Fix: `AccountComplianceDialog` import removed from MainScreen (it was never used); `ServiceCatalogPicker` remains orphaned, needs future wiring

**Consequences**:
- 7 source files modified, 1 new file added
- Compilation: BUILD SUCCESSFUL (`gradlew assembleDebug`, 3m 46s)
- No test suite run (per project constraint — limited RAM)
- Device currently disconnected — physical testing pending
- Firestore rules already have ownership checks (verified `users/{userId}` owner-only create/update in `firestore.rules` MODULE 2)

---


## D-2026-09-26-1 — REG-002: guarantor verification reuses the KYC-003 tables, and the UI treats validation as a single blocker

**Context**: REG-002 (two guarantors with NIN + three photographs) arrived after KYC-003 had
already shipped a two-guarantor model (`GuarantorEntity` with `idFront/idBackStoragePath`) and an
unwired `GuarantorFormSheet`. A partially written `GuarantorVerificationSheet.kt` assumed APIs
that do not exist (`updateGuarantorReview`, a validator `Outcome` object), so nothing compiled.

**Decision**:
1. **Augment, do not migrate.** `GuarantorEntity` keeps the KYC-003 columns and gains the REG-002
   ones (`nationalIdNumber`, `guarantorPhotoStoragePath`, `workerWithGuarantorPhotoStoragePath`,
   `consentProvided`, `ninVerificationStatus`); `worker_kyc` gains `guarantorStatus`,
   `groupPhotoStoragePath`, `guarantorsSubmittedAt`, `guarantorReviewFeedback`. No table rewrite,
   no second guarantor model, and the dead KYC-003 `GuarantorFormSheet` is left untouched.
2. **One blocker string, not a result object.** `GuarantorValidators.validateGuarantorSet`
   returns the first blocking message (`String?`); the sheet shows exactly that message and gates
   submit on `blocker == null`. The repository re-runs the same validator with the real
   `workerPrimaryPhone`, so UI-side leniency can never file an invalid submission.
3. **UI never owns review state.** The client may write only `PENDING` for the NIN check;
   `SUBMITTED` is recorded locally only after the Firestore write succeeded, and admin outcomes
   (`PENDING_REVIEW`, `APPROVED`, `REJECTED`, `CHANGES_REQUIRED`) come solely from the worker's
   own `guarantor_submissions/{uid}` document through `refreshGuarantorVerificationStatus`.
4. **Photographs are Storage paths, never URLs**, under `users/{uid}/guarantors/` with the slot
   whitelist (`guarantor`, `with_worker`, `group`), and drafts may keep `content://` URIs locally
   until submission uploads them.
5. **Entry point**: the drawer's "National ID & Verification" sheet hosts the guarantor step
   (status row + admin feedback + button) instead of inventing a new registration wizard; the
   progressive step-by-step registration flow remains a future REG-002 UI item.
6. **No shared spacing tokens invented**: the sheet defines local `SpaceXS/SM/MD` dp values
   because the theme exposes none and every other component uses literal dp.
7. **Room declares every index the migration creates.** `MIGRATION_18_19` executes
   `CREATE INDEX index_guarantors_phoneNumber ON guarantors(phoneNumber)` (duplicate-guarantor
   lookups), so `GuarantorEntity` must declare `Index(value = ["phoneNumber"])` next to
   `Index(value = ["workerKycId"])`. An index that exists physically but not on the entity is
   invisible to Room's schema/`TableInfo` validation and to every later migration generated from
   the entity definitions — so the entity is the source of truth and the migration mirrors it
   (the default index name Room derives is exactly the name the migration uses).

**Consequences**:
- One Room/Firestore model serves KYC-003 and REG-002; the old sheet stays dead code until the
  registration wizard is built.
- Verification of this increment was recorded as **outstanding** on the strength of a "no JDK exists
  on this machine" blocker. That blocker was **false** — see D-2026-09-27-1; the build ran the next
  day and REG-002 is `completed`.
- What *was* possible without a compiler — hand cross-checking call signatures across
  UI → ViewModel → repository → DAO → `FirebaseService`, entity columns/defaults against
  `MIGRATION_18_19`, and the client payloads against the `guarantors` / `guarantor_submissions`
  Firestore rules and the `users/{uid}/guarantors/**` Storage rules — was performed and is logged
  in `AGENT_STATE.md`. It found and fixed the undeclared phone-number index. It does **not**
  substitute for a build: Kotlin type inference, Compose recomposition correctness and the new
  unit tests remain unverified until the build runs. **Update 2026-09-27 (D-2026-09-27-1):** the
  build did run and found exactly the two categories this audit could not see — a missing
  `androidx.compose.runtime` import set behind `by mutableStateOf`, and `distinct()` called on a
  `String`. Both fixed; 57 unit tests green; debug APK produced.

---

## D-2026-09-27-1 — REG-002 build verification: the "no JDK" blocker was false, and the build is the only real gate

**Context**: On 2026-09-26 REG-002 was left `in_progress` with a recorded blocker stating that the
machine had no JDK — `jdk11/` and `jdk17/` in the project root were inspected, `java` was not on
`PATH`, and from that the session concluded that `gradlew` could not start, then substituted a
hand cross-check of every layer for a compile. A later session tested the premise instead of
accepting it.

**Findings**: `gradle.properties` pins the build JVM — `org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10`
(Amazon Corretto `17.0.20.1`, present on disk) — and the Gradle home
(`systemProp.org.gradle.user.home=D:/gradle-home`), plus `org.gradle.java.installations.paths=D:/skills exchange/jdk11/jdk-11.0.22+7`
whose `bin\java.exe` does exist. Only the repo-root `jdk17/` folder is genuinely empty, and no
build file ever referenced it. Because `org.gradle.java.home` outranks `JAVA_HOME`, `gradlew.bat`
starts its daemon on the pinned JDK regardless of `PATH`. `.\gradlew.bat <task> --console=plain`
therefore works with no environment setup at all.

**Options considered**:
1. Accept the recorded blocker and hand REG-002 on unverified (the 2026-09-26 choice).
2. Install a new JDK — unnecessary; two usable JDKs were already on disk.
3. Verify the premise in seconds (`Test-Path` on the properties in `gradle.properties`) and build. ✅

**Decision**: Build verification is mandatory before any story is marked `completed`, and a tooling
blocker must be *reproduced*, not inherited. `AGENTS.md` §9 now documents the real JVM resolution
so the same dead end is not rediscovered a third time.

**Rationale**: the static audit was thorough and still missed two compile errors, both in the
categories signature checking cannot observe — operators that property delegates require
implicitly (`getValue`/`setValue` for `by mutableStateOf`, which no wildcard `kotlinx.coroutines.flow.*`
import supplies) and a stdlib call that does not resolve on the receiver type (`String.distinct()`).
Room's schema validation, Compose's delegate resolution and the new unit tests can only be judged
by running them.

**Verification outcome (2026-09-27)**: `:app:compileDebugKotlin` PASSED after the two fixes
(`SkillsExchangeViewModel.kt` Compose runtime imports; `GuarantorValidators.validateNationalIdNumber` now uses
`value.toSet().size > 1` instead of `value.distinct().count() > 1` — identical rule, valid call);
`:app:testDebugUnitTest` PASSED with **57 tests / 8 suites / 0 failures** (`GuarantorValidatorsTest`
18/18); `:app:assembleDebug` PASSED in 3m 35s producing `app-debug.apk` (35,254,412 bytes). REG-002
is now `completed`.

**Consequences**:
- REG-002 moved to `completed` (86 completed / 0 in progress / 6 pending of 92); the remaining
  REG-002 work is operational, not code: the on-device golden path and
  `firebase deploy --only firestore:rules,storage`.
- `GuarantorValidatorsTest` has **18** tests, not the 19 recorded in the 2026-09-26 docs; counts in
  `prd.json` and `CHANGELOG.md` were corrected to match the XML results.
- Future sessions must check `gradle.properties` before claiming a tooling blocker, and must treat
  "the daemon printed nothing" as normal — watch `app\build\` timestamps rather than assuming a hang.

---

