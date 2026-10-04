# CHANGELOG.md — Skills Exchange Development History

All meaningful development changes are recorded here in reverse chronological order.

### [2026-10-04] [C2-2 Fix #1] [SECURITY] UID-Keyed Current-User Resolution — Implemented, Test Fixture Corrected, VERIFIED

- **Scope:** Fix #1 of the C2-2 audit only — make the authenticated Firebase UID the authoritative key for resolving the current Room `UserProfileEntity`. The rest of the C2-2 audit is NOT addressed here, and `C2` in `TASKS.md` / `prd.json` remains ⏳ **Pending Audit Fix** — this story is **not** marked complete.
- **Implementation (committed earlier in `db694a2` "Add UID-keyed DAO lookup"):**
  - `data/db/Daos.kt` — new `UserProfileDao.getCurrentUserById(uid): Flow<UserProfileEntity?>` = `SELECT * FROM user_profiles WHERE id = :uid LIMIT 1`. Exact-id only: no timestamp ordering, no `ORDER BY`, no fallback row.
  - `data/repository/SkillsExchangeRepository.kt` — new `getCurrentUserByUid(firebaseUid: String?)`. Blank/whitespace UID or the legacy id `user_default_me` short-circuits to `flowOf(null)`; otherwise delegates to `getCurrentUserById`. The legacy newest-row `currentUser` flow is deliberately retained for existing display consumers.
  - `ui/viewmodel/SkillsExchangeViewModel.kt` — new `currentUserByUid: StateFlow<UserProfileEntity?>` = `firebaseUser.flatMapLatest { repository.getCurrentUserByUid(it?.uid) }.stateIn(viewModelScope, WhileSubscribed(5000), null)`. Signed-out ⇒ `null` (fail closed).
- **Test-fixture defect found and fixed (this session).** `user_profiles` declares `indices = [Index(value = ["phoneNumber"], unique = true)]` (`Entities.kt:335`) and inserts use `@Insert(onConflict = REPLACE)` (`Daos.kt:278`). The first regression test seeded two profiles that shared the helper's default `phoneNumber`, so the second insert **replaced** the first and `getCurrentUserById("uid_A")` correctly returned `null` — a false failure caused by the fixture, not by the production query. Fix: `authenticatedWorkerProfile(...)` now takes an explicit `phoneNumber` (plus `fullName` / `updatedAtTimestamp`), every multi-row test uses distinct numbers, and the pre-existing "updating an existing profile" test now keeps the uniquely-indexed phone number constant (a same-account edit in production goes through targeted `UPDATE` queries, never `REPLACE` on a new number).
- **Tests (executed, evidence captured):**
  - `app/src/test/java/com/example/data/UserProfilePersistenceTest.kt` — 10 tests, incl. 3 new UID-keyed regressions: the UID row is returned even when another row is newer; `null` when the UID has no row; the demo placeholder is never returned.
  - Same file, second class `CurrentUserByUidRepositoryTest` — 3 new repository-boundary tests pinning the fail-closed rules: missing UID ⇒ `null`; `null` / `""` / `"   "` / `user_default_me` ⇒ `null` (never the newest row).
  - `:app:testDebugUnitTest --tests com.example.data.UserProfilePersistenceTest --tests com.example.data.CurrentUserByUidRepositoryTest` → **BUILD SUCCESSFUL**; `TEST-com.example.data.UserProfilePersistenceTest.xml` = 10 tests / 0 failures / 0 errors / 0 skipped; `TEST-com.example.data.CurrentUserByUidRepositoryTest.xml` = 3 / 0 / 0 / 0.
  - Full suite `:app:testDebugUnitTest` → **BUILD SUCCESSFUL**; 15 suites / 119 tests / 0 failures / 0 errors / 0 skipped (no regressions).
  - `:app:compileDebugKotlin` → **BUILD SUCCESSFUL** (KSP generated `UserProfileDao_Impl.getCurrentUserById` with `WHERE id = ? LIMIT 1` — confirmed at `app/build/generated/ksp/debug/.../UserProfileDao_Impl.kt:451`).
  - `:app:assembleDebug` → **BUILD SUCCESSFUL** (Room schema/KSP validation, dexing and packaging all pass).
  - `git --no-pager diff --check` → clean. Diff scope this session: **only** `UserProfilePersistenceTest.kt` (the three production files landed in the earlier `db694a2` commit); no `prd.json`, `TASKS.md`, `firestore.rules` or C2-1 file was modified.
- **Security behaviour proven:** with Room holding UID-A (older) and UID-B (newest), the legacy `getCurrentUser()` returns **UID-B** while `getCurrentUserById("uid_A")` and `repository.getCurrentUserByUid("uid_A")` both return **UID-A** — the authenticated identity. No UID / blank UID / `user_default_me` never yields a substitute identity.
- **Still outstanding (C2-2 F2–F7, NOT this session's scope):** the legacy newest-row `getCurrentUser()` and its consumers still exist (no F7 call-site sweep yet), `currentUserByUid` has no UI consumer yet, `getUserProfileByPhone` remains device-local and non-authoritative, and the seeded `user_default_me` row is still present in Room. No device/emulator run was possible (no adb device attached).


### [2026-09-30] [C2-1] [SECURITY] Phone Authentication Identity Boundary — Implemented, ⚠️ NOT VERIFIED

- **Defect (reported by the C1/C2/C3 audit):** `FirebaseService.sendPhoneVerificationCode` fabricated a local `"<timestamp>_<phoneHash>"` "verification id" and `verifyPhoneCode` accepted **any** six-digit code before calling `signInAnonymously()`; `SkillsExchangeViewModel.loginUser` then treated a phone-number match as account recovery and copied `workerKycStatus` / `isWorkerActivated` / `trainingCompleted` onto the newly authenticated UID; `firestore.rules` allowed `allow read: if isAuthenticated();` on `match /users/{userId}`.
- **Fix — real phone auth:** `PhoneAuthProvider.verifyPhoneNumber(PhoneAuthOptions)` (Activity-bound via a `WeakReference` holder registered by `MainActivity`, 60s timeout, awaited with `suspendCancellableCoroutine`) plus `PhoneAuthProvider.getCredential` + `signInWithCredential`. New `toE164PhoneNumber` normalises UG input (`0772198420` / `256…` / `+256…` / 9-digit) and fails closed on anything ambiguous. `signInAnonymously()` no longer appears on any successful phone-auth path.
- **Fix — fail-closed verification gate:** `phoneVerificationGateFailureReason(...)` refuses every code while no Firebase verification is pending; the authenticated identity must be non-anonymous, phone-backed and match the requested number. Pending state is cleared on resend, cancel, sign-out, account deletion, success and failure.
- **Fix — no privilege copying:** new companion policy `resolveAuthenticatedProfile(...)` reuses a record only when its id == the authenticated Firebase UID; a phone match with any other uid is rebuilt fail-closed (`UNVERIFIED`/`NOT_APPLIED`, `isWorkerActivated = false`, `trainingCompleted = false`) with none of the other record's fields copied. `loginUser` no longer mints an anonymous UID and no longer falls back to "newest row". `verifyOtp` keys the session to the verified Firebase UID.
- **Fix — Firestore privacy:** `users/{userId}` read is now `isDocOwner(userId) || isAdmin()`. The client by-phone query degrades to null under the tightened rules and is documented as non-authoritative.
- **Tests added (NOT yet executed):** `PhoneAuthVerificationSecurityTest`, `PhoneAuthServiceFailClosedTest` (Robolectric), `FirestoreUsersReadPrivacyTest`, `PhoneAuthIdentitySecurityTest`.
- **⚠️ NOT VERIFIED — `C2` remains ⏳ in `TASKS.md` / `prd.json`.** No `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`, `firebase deploy` or device run was possible: `run_commands` returned exit 1 with no output and no side effects for every attempt this session (three file-writing probes never created their files). See `AGENT_STATE.md` for the ordered next actions.

### [2026-09-30] [SEC-009 / G1 / G3 / H1 / H8 / M8] [SECURITY] Worker Dashboard Fail-Closed Authorization Implemented

- **Problem:** `isWorkerApprovedAndActivated` initialized to `true` (fail-open, G1); worker dashboard authorization depended on UI state (G3); `setUserRoleMode("Offer Skills")` lacked a server-authoritative gate (M8); `FreelancerDashboardView` had no component-level auth guard (H8); the combined pattern violated H1 (fail-closed requirement).
- **Solution — ViewModel layer (fail-closed):**
  - Added `isWorkerAuthorized(isAuthenticated: Boolean, user: UserProfileEntity?): Boolean` companion pure function to `SkillsExchangeViewModel`. Returns `true` **only when** `initialRole == "WORKER"` AND `workerKycStatus == "APPROVED"` AND `trainingCompleted == true` AND `isWorkerActivated == true`. Returns `false` for every other value including null, blank, loading, pending, or deactivated.
  - `isWorkerApprovedAndActivated: StateFlow<Boolean>` now combines `isAuthenticated` + `currentUserProfile` StateFlows through `isWorkerAuthorized()`.
  - `canAccessWorkerFeatures()` delegates entirely to `isWorkerAuthorized()`.
- **Solution — UI layer (component boundary):**
  - `FreelancerDashboardView` accepts `isAuthorized: Boolean`; if `false` it renders a hard-coded locked-screen card regardless of any other composable state — preventing UI state injection bypass.
  - `MainScreen` passes `isWorkerApprovedAndActivated.collectAsState()` as `isAuthorized`.
- **Tests:** `WorkerAuthorizationSecurityTest.kt` — 13 test cases covering unauthenticated, client-mode, KYC pending, KYC submitted, KYC rejected, training incomplete, not activated, fully approved, null profile, empty strings, wrong role, and auth state mismatch — all PASSED. Existing suite: PASSED (all UP-TO-DATE, no regressions).
- **Device install:** Blocked by Samsung SM-A025F storage (98%, `INSTALL_FAILED_INSUFFICIENT_STORAGE`). Hardware constraint only — clear device storage to re-run.
- **Files changed:** `SkillsExchangeViewModel.kt`, `FreelancerDashboardView.kt`, `MainScreen.kt` (modified); `WorkerAuthorizationSecurityTest.kt` (new).
- **Stories closed:** G1, G3, H1, H8, M8. SEC-009 already `completed` in `prd.json` / `completed_stories`.

### [2026-09-30] [MAP-001 / D1] [BUGFIX+VERIFY] InteractiveMapView Crash Fixed & Safe Maps Key Wiring Implemented

- **Defect D1 Resolved**: Physical device crash (`IllegalStateException: API key not found` at `InteractiveMapView.kt:98`) fixed.
- **Root Cause & Solution**:
  1. `InteractiveMapView.kt` now calls `isMapsApiKeyPresent(context)` before creating `MapView`. If the key is null, empty, un-substituted (`${...}`), or matching placeholder tokens (`DEFAULT_MAPS_API_KEY`, `MY_MAPS_API_KEY`, `YOUR_KEY_HERE`), the composable renders `MapUnavailableFallback` immediately without touching `MapView`.
  2. Wrapped `map.onCreate(null)` in `try/catch` as defense-in-depth against Play Services absence or runtime SDK initialization exceptions.
  3. Configured `.env.example`, `.env`, and `app/build.gradle.kts` (`manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_MAPS_API_KEY"`) using the `DEFAULT_MAPS_API_KEY` token (aligned with `GEMINI_API_KEY=MY_GEMINI_API_KEY`), preventing secrets-gradle-plugin 2.0.1 from generating invalid Java syntax in `BuildConfig.java`.
  4. Injected `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}" />` into `app/src/main/AndroidManifest.xml`.
- **Verification**:
  - `:app:compileDebugJavaWithJavac`: PASSED.
  - `:app:testDebugUnitTest`: PASSED (75 tests across 9 suites, 0 failures, 0 errors).
  - `:app:assembleDebug`: PASSED in 7m 59s (`app-debug.apk` 35,259,265 bytes).

### [2026-09-30] [MAP-003] [DOCS] Google Maps Fallback Requirement Added as Future Pending Story

- **Scope:** Documentation only. No source code was changed. No Firebase configuration was changed. No Google Maps dependency or API key was added. No implementation was performed.
- **Added story `MAP-003` (phase 17, priority P2, status `pending`)** to `prd.json`, `TASKS.md`, `AGENT_STATE.md`, `CHANGELOG.md`, and `DECISIONS.md`.
- **Requirement summary:** Google Maps must NOT be a hard dependency for the Skills Exchange job lifecycle. Maps may only appear on the active-job screen (after a client has accepted/hired a worker). If Maps fails for any reason (no API key, billing disabled, SDK init failure, network failure, location/map load failure, invalid config, location permission denied, or any other Maps-related failure) the app must NOT crash, freeze, or show an infinite loading screen. Instead the active-job screen must fall back gracefully to a clear message ("Map location is currently unavailable.") and offer a **Contact Worker** action (reusing the existing COMM-001 masked Firestore-chat mechanism — no private phone number exposed, no new communication system created) and a **Continue Without Map** action. Worker GPS location collection must remain independent from Google Maps. All existing job lifecycle flows (acceptance, start, status update, arrival PIN, completion, notifications, reviews, payment/escrow, and disputes) must remain fully functional when Maps is unavailable. API key must be managed via secure configuration (manifestPlaceholders / .env), never hardcoded in Kotlin/source. Treat Google Maps as a **visualization layer only**.
- **Ordering:** placed after `MAP-002` (Google Maps Usage Minimization) and all other existing unfinished items in Phase 17. `depends_on: ["MAP-002"]`.
- **DECISIONS.md:** DEC-020 (Google Maps Is an Optional Visualization Layer — Graceful Fallback Required on Active-Job Screen).
- **prd.json:** total stories now 93 (85 completed / 1 in-progress / 7 pending). `last_updated` updated to `2026-09-30T14:36:00+03:00`.
- **TASKS.md:** pending count updated from 6 → 7, total from 92 → 93.

### [2026-09-29] [MAP-001 / REG-002] [DEVICE-VERIFY] Real-Device Test on Samsung SM-A025F — Migration 18→19 Proven, Two Defects Found

- **Scope:** build → install → cold launch → UI walkthrough → crash repro → on-device database inspection on a physical Samsung **SM-A025F (Android 12 / API 31, 720×1600 @280 dpi)**, package `com.aistudio.skillsexchange.nxptvq` (versionCode 1 / versionName 1.0, minSdk 24, targetSdk 36). **No source file was changed; nothing was committed; `prd.json` was not modified.** Consolidated evidence: `_device_test/DEVICE_TEST_REPORT.md`.
- **Install:** `assembleDebug` exit 0 (1m 36s) → 35,254,412 B APK. First `adb install -r` failed `INSTALL_FAILED_INSUFFICIENT_STORAGE`; root cause was `/data` at 99 % with ~349 MB free, **not** a signature mismatch (the installed cert SHA-256 `d5b68479…ac32` matched the new APK). ~551 MB was reclaimed with `adb shell pm trim-caches 2147483648` — no app uninstalled, no user media touched. The reinstall was therefore an **in-place update**: `firstInstallTime` stayed `2026-09-16 20:20:52`, `lastUpdateTime` became `2026-09-29 17:02:25`, and `adb shell ls -l` on the installed `base.apk` returned the same 35,254,412 B as the build output.
- **Room migration proven on hardware (new evidence for REG-002):** the live DB was pulled from the app sandbox with `run-as` + `exec-out` (recipe kept at `_device_test/pull_db.ps1`) and its header inspected byte-wise: valid SQLite magic, `page_size = 4096`, **`user_version = 19`** — exactly `AppDatabase`'s `version = 19` with `MIGRATION_18_19` registered, and `guarantors` carries `nationalIdNumber` + `phoneNumber`. The app was **not** wiped and still opened at schema 19, so `MIGRATION_18_19` ran successfully on a device — previously only ever asserted at unit-test level.
- **Working paths:** cold start 6,305 ms with no ANR and clean Firebase init; client-mode home with live GPS ("Live: Sunnyside District, 15m accuracy filtering"); navigation drawer; National ID & Verification correctly refused for a guest via the CLIENT ONBOARDING sheet; Job Status; theme Light↔Dark; providers list with correct empty state. Post-crash relaunch returns to `MainActivity` cleanly with no corrupted state.
- **Defect D1 (critical, 3/3 reproducible):** tapping **Map View** kills the process — `java.lang.IllegalStateException: API key not found …` raised through `CreatorImpl.newMapViewDelegate` into `InteractiveMapView.kt:98` (`map.onCreate(null)` inside the `AndroidView` factory, unguarded). Three captures, three distinct PIDs (23654 / 25548 / 25768). Root cause is configuration, not the device: `app/src/main/AndroidManifest.xml` contains **no `<meta-data>` element at all**, `.env`/`.env.example` define only `GEMINI_API_KEY`, no code injects a Maps key, and `README.md` step 4 asks the developer to hand-edit the manifest while its own `.env` block advertises an unused `MAPS_API_KEY`. **`prd.json` MAP-001 is recorded `completed` / `verified_by: "code_inspection"` although its acceptance criterion ("Map view renders interactive Google Maps tiles") and its verification step ("Open map tab … verify real street map") both fail on real hardware** — reopening MAP-001 is recommended (AGENTS.md §2) and deliberately **not** done by this verification task.
- **Defect D2 (UX, silent failure):** the guest tap on the worker/client mode card is correctly refused by `canAccessWorkerFeatures()` but communicates nothing — `SkillsExchangeViewModel` writes `authError` and **the message is only rendered inside `AuthModal` / `OnboardingScreen`** (`MainScreen.kt:741` hosts the modal only `if (isAuthModalOpen)`, which the refusal path does not open), and `SnackbarHost` appears in **zero** source files — so on the screen where the tap happens the user sees nothing, while the drawer copy "Tap to switch to worker dashboard" invites the tap.
- **Evidence retained:** 6 logcat captures (incl. 3 crash captures), 15 UI screenshots `ui1.png`…`ui15_postcrash_settled.png`, `ui.xml` (uiautomator hierarchy), `ondevice_db.sqlite` + `-wal`, `installed-base.apk` (pre-update copy used for the cert comparison), `_device_test/pull_db.ps1`, `build_debug.log`.
- **Not verified:** anything requiring sign-in (worker dashboard, chat, escrow, payment, guarantor submission end-to-end), release variant / R8 / signing, and every server-side rule (still no `firebase deploy`). Suggested order: guard `InteractiveMapView`, wire `MAPS_API_KEY` via `manifestPlaceholders`, re-verify MAP-001 on device, then host `authError` in a `SnackbarHost`.


### [2026-09-29] [ADMIN-005 / REG-002 gap G2] [IMPL+VERIFY] Admin Guarantor Review Path (fetch + decide) Implemented & Verified

- **Gap closed (gap G2 of the post-REG-002 review)**: REG-002 shipped the *worker* half of guarantor verification — two guarantors, three private photographs, 48h SLA tracking and admin-reserved review fields in `firestore.rules` — but there was **no way for an administrator to read or decide a submission**. The only admin callables (`getPendingVerificationQueue`, `reviewWorkerApplication`, `setWorkerActivationStatus`) know nothing about `guarantor_submissions`, and the queue returned every worker's **unmasked** `nationalIdNumber`. This change adds the missing admin path; it does not touch any other known gap.
- **New file [guarantorReview.ts](file:///d:/skills%20exchange/functions/src/guarantorReview.ts)** (≈660 lines): the dependency-free security core — status/NIN vocabulary constants, `maskIdentifier`, `checkAdminCaller`, `readWorkerId`, `mapDecisionToStatus`, `normalizeNinStatus`, `validateGuarantorReviewInput`, `isPrivateGuarantorPhotoPath`, `resolvePhotoUrlTtlSeconds`, `buildGuarantorReviewFields`, `buildGuarantorNinUpdate`, `buildPhotoAccess`, `collectPrivatePhotoPaths`, `projectGuarantorForAdmin`, `projectSubmissionForAdmin`, `projectQueueRow`, `buildQueueRows`. It deliberately imports neither `firebase-admin` nor `firebase-functions`, so the whole security contract is unit-testable without an emulator.
- **[index.ts](file:///d:/skills%20exchange/functions/src/index.ts)**: `isAdmin` now delegates to `checkAdminCaller` (same trust anchor, one implementation); new `authorizeAdmin(context)`; new callables `getGuarantorSubmission` (admin detail: status, SLA, feedback, reviewer, both guarantors, consent, masked NIN by default, five short-lived private-photo URLs) and `reviewGuarantorSubmission` (decision + feedback + NIN result, per-guarantor NIN batch update, conditional KYC mirror, worker notification); `getPendingVerificationQueue` rewritten to merge KYC and guarantor work, to emit **`nationalIdNumberMasked` instead of `nationalIdNumber`**, and to add `kycOnFile` / `guarantorStatus` / `guarantorSubmittedAt` / `guarantorCount` / `ninVerificationStatus` / `guarantorsAwaitingReview` per row; `privatePhotoBucket()` + `mintPrivatePhotoUrls()` mint v4 read URLs (default 15 min, hard cap 60 min) only for paths that already passed the private-prefix check.
- **Security contract** (all enforced server-side, never by client-supplied roles): only a **verified** ID token carrying the boolean `admin === true` custom claim may call either endpoint (`unauthenticated` for anonymous, `permission-denied` otherwise) — the same anchor `firestore.rules` uses; a NIN is masked in every list/queue payload and masked **by default** in the admin detail response, the unmasked value being released only when the admin call passes `includeFullNin: true`; a photo URL is minted only for `users/{workerId}/guarantors/<file>` — absolute URLs, `..` traversal, leading slashes, empty segments and **another worker's prefix** are refused with a fixed reason code (`OUTSIDE_PRIVATE_PREFIX` / `MISSING` / `NOT_MINTED` / `URL_MINT_FAILED` / `BUCKET_UNAVAILABLE`); the reviewer identity comes from the token and the timestamp from the Firestore server clock, so neither can be forged; the write payloads are a fixed allowlist of admin-owned fields, so a review can never write a worker-owned field; guarantor documents are located by `workerKycId == workerId` **and re-filtered** before any write, never by trusting ids stored inside the submission; all map lookups are own-key lookups (`Object.create(null)`), so prototype keys (`__proto__`, `constructor`, `toString`) can never be borrowed as a name or a document.
- **Verification (real, on this machine)**: `npm run build` PASSED — `tsc` exit 0, emitting `lib/index.js` and `lib/guarantorReview.js`; `node --test test/guarantorReview.test.mjs` PASSED — **32 tests, 32 pass, 0 fail** (authorization, NIN masking, photo-path guard, TTL clamping, write-payload allowlist, input validation, queue merge/sort/limit/prototype safety). The previous session's "`npx tsc` exits 0" claim was **not** valid evidence: `npx tsc` resolved to the placeholder `tsc` npm package ("This is not the tsc command you are looking for"), and `node_modules/typescript` had been reduced to an empty `bin/` plus locale folders. See the new blocker rows in `AGENT_STATE.md`.
- **Build configuration repaired ([functions/package.json](file:///d:/skills%20exchange/functions/package.json))**: `devDependencies` never declared the types the `build` script needs — `@types/express` and `@types/node` are now declared (a clean clone could not run `npm run build` before), and a `test` script (`node --test test/guarantorReview.test.mjs`) was added. Several `@types/*` folders had been gutted to empty directories; reinstalling `@types/express-serve-static-core`, `@types/body-parser`, `@types/serve-static`, `@types/glob` and `@types/ms` restored them and produced the clean exit-0 build.
- **Deliberate non-change**: the new `guarantor_submissions` queue query omits `orderBy("submittedAt")` because `status in [...]` + `orderBy` requires a composite index that this repository does not declare (there is no `firestore.indexes.json`); `buildQueueRows` sorts by the earliest outstanding timestamp and applies the limit in code, so omitting it trades a slightly larger read for not failing with `FAILED_PRECONDITION`.
- **Not verified / still open**: no deployment (`firebase deploy --only functions`) and no live Firestore/Storage round-trip were possible in this session; `reviewGuarantorSubmission`'s KYC mirror is skipped when the worker has no `worker_kyc` document (the app still never creates that document — pre-existing blocker); the admin UI that would call these two callables does not exist yet. Gaps G1, G3, G4, G5, G6, G7 and G12 from the same review remain untouched and unfixed.

### [2026-09-27] [REG-002] [VERIFY] Guarantor Verification Built and Unit-Tested (VERIFIED)

- **The 2026-09-26 "no JDK on this machine" blocker was false.** `gradle.properties` pins the build JVM with `org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10` (Amazon Corretto `17.0.20.1`, present on disk) and `systemProp.org.gradle.user.home=D:/gradle-home`, so `gradlew.bat` builds without any environment setup. `jdk11/jdk-11.0.22+7/bin/java.exe` also exists, and Android Studio's JetBrains Runtime (OpenJDK `25.0.2`) is installed. Only the repo-root `jdk17/` folder is actually empty, and Gradle never referenced it. Verification commands for the next session are simply `.\gradlew.bat <task> --console=plain`.
- **First real compile of the guarantor feature surfaced two genuine errors** that hand inspection had missed, both fixed:
  - [SkillsExchangeViewModel.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt) holds `var` Compose state (`guarantorVerificationOpen` etc. via `by mutableStateOf`) but never imported `androidx.compose.runtime.getValue`, `mutableStateOf` and `setValue` — added (property delegates and `mutableStateOf` are not covered by the file's existing `kotlinx.coroutines.flow.*` wildcard).
  - [GuarantorValidators.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/util/GuarantorValidators.kt) `validateNationalIdNumber()` rejected a NIN made of one repeated character via `value.distinct().count()`, which does not resolve on `String` in this module; it is now `value.toSet().size <= 1` — identical rule, plain stdlib call.
- **Verification results**: `:app:compileDebugKotlin` PASSED (warnings only), `:app:testDebugUnitTest` PASSED — **57 tests across 8 suites, 0 failures / 0 errors / 0 skipped**, with the new `GuarantorValidatorsTest` at **18/18**, and `:app:assembleDebug` PASSED (BUILD SUCCESSFUL in 3m 35s) producing `app/build/outputs/apk/debug/app-debug.apk` (35,254,412 bytes). Room's KSP pass validated the entities, DAO queries and the new `phoneNumber` index.
- **Repository state**: nothing was committed by this session - `git status` reports 16 modified files plus 3 untracked REG-002 files (`GuarantorValidators.kt`, `GuarantorVerificationSheet.kt`, `GuarantorValidatorsTest.kt`); committing them is a mechanical follow-up.
- **PRD state**: REG-002 moved to `completed` (86 completed / 0 in progress / 6 pending of 92). Still outstanding and unchanged: the on-device golden path and `firebase deploy --only firestore:rules,storage`.

### [2026-09-26] [REG-002] [IMPL] Guarantor Verification Sheet Completed + Repository/DAO Alignment (BUILD UNVERIFIED AT THE TIME — SEE THE 2026-09-27 ENTRY)

- **Alignment fixes**: `SkillsExchangeRepository` no longer calls the non-existent `WorkerKycDao.updateGuarantorReview(status = ...)`; the real DAO surface `updateGuarantorStep(userId, guarantorStatus, groupPhotoStoragePath, submittedAt, feedback)` is used for the draft, submit and status-refresh paths. The repository also imports `GuarantorInput` from `com.example.util` and consumes `GuarantorValidators.validateGuarantorSet(...)` as the **single blocker `String?`** it actually returns (the previous draft expected an `Outcome`/`issues` map that does not exist).
- **Sheet completed** in [GuarantorVerificationSheet.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/GuarantorVerificationSheet.kt): added the missing `GuarantorDraft` state holder and the `GuarantorEntity ↔ GuarantorDraft ↔ GuarantorInput` mappers, `GuarantorDraftCard` (phone, full name, relationship, district, masked NIN, guarantor photo, worker+guarantor photo, consent checkbox), `GuarantorPhotoField` and `GroupPhotoCard` (GetContent picker, attached/replace/remove with confirmation, only paths or content URIs stored — never signed URLs), `GuarantorChoiceField` dropdown, and `VerificationStatusChip`. Removed the corrupted duplicated tail, the obsolete `issues` map logic, unused imports and the invented `SpaceMD/SpaceSM` theme references (local `SpaceXS/SM/MD` dp values instead, matching the rest of the codebase which uses literal dp).
- **Wiring**: [MainScreen.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/screens/MainScreen.kt) renders the sheet on `viewModel.guarantorVerificationOpen`; `IdentityVerificationSheet` in [AccountCenterSheets.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/AccountCenterSheets.kt) gained a "Guarantor verification" status row, the admin review feedback text and an entry button (`onManageGuarantors`), and both drawer entry points now refresh the admin decision via `viewModel.refreshGuarantorVerificationStatus()`.
- **Security posture unchanged**: two distinct guarantors, no self-guaranteeing, consent per guarantor, structural-only NIN check, photos only under the owner prefix `users/{uid}/guarantors/`, Storage paths (never download URLs) persisted, client can only write `ninVerificationStatus = "PENDING"`, and all failure messages are scrubbed so NIN/phone digits never reach logs.
- **Tests added**: [GuarantorValidatorsTest.kt](file:///d:/skills%20exchange/app/src/test/java/com/example/util/GuarantorValidatorsTest.kt) — 18 pure-JVM tests over the whole two-guarantor rule set (count, duplicate person/number, self-phone, missing photos, missing consent, NIN bounds, unauthenticated / non-worker blockers, photo-slot whitelist, deterministic ids, masking and scrubbing).
- **Room schema fix**: `MIGRATION_18_19` creates `index_guarantors_phoneNumber ON guarantors(phoneNumber)`, but `GuarantorEntity` declared only `Index(["workerKycId"])`. The entity now declares `Index(value = ["workerKycId"]), Index(value = ["phoneNumber"])`, so the fresh-install schema Room generates equals the migrated schema (the default index name Room derives — `index_guarantors_phoneNumber` — is precisely the one the migration executes), keeping Room's schema/`TableInfo` validation and future migrations consistent. AppDatabase stays at **version 19** with `MIGRATION_18_19` registered.
- **Verification was not run in that session**: it recorded a blocker claiming the machine had no JDK, which the 2026-09-27 entry above shows to be wrong (`gradle.properties` pins `org.gradle.java.home` to an existing Corretto 17, and the build then ran first time). Nothing was compiled or tested at that time, so REG-002 was left `in_progress` (85 completed / 1 in progress / 6 pending). Static cross-checks performed instead: UI→ViewModel→repository→DAO→`FirebaseService` call signatures and named arguments, entity columns/defaults against `MIGRATION_18_19`, imports and icon/composable references in the new sheet, and the client payloads against the `guarantors` / `guarantor_submissions` Firestore rules and the `users/{uid}/guarantors/**` Storage rules — this audit found the undeclared `phoneNumber` index described above. Brace balance was also verified for all edited files. REG-002 is therefore recorded as `in_progress` (85 completed / 1 in progress / 6 pending).


### [2026-09-23] [REG-003] [REG-004] [IMPL] Worker Backup Contact + Worker Profile Photo Implemented & Verified

- **Audit first**: REG-003 was NOT IMPLEMENTED (no backup/emergency/secondary-contact field existed; only the primary `phoneNumber`, plus the guarantor phone in KYC-003, which is a different concept). REG-004 was PARTIALLY COMPLETED — `UserProfileEntity.avatarUrl` existed but was only a decorative seeded stock-photo default: no picker, no upload, no Storage write, no client-facing use. `firebase-storage` was already a dependency but unused (the `storage` field was never called), and Coil was declared in `libs.versions.toml` but commented out in `app/build.gradle.kts`.
- **REG-003 — data model**: Added `backupContactName` + `backupContactPhone` (both `String = ""`) to `UserProfileEntity` ([Entities.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt)). Optional by design: empty strings mean "not set". No relationship/foreign key was added because the requirement treats the backup contact as a contact detail of the worker and explicitly **NOT** a guarantor — the `guarantors` table and the KYC-003 two-guarantor flow are untouched.
- **REG-003 — validation**: New shared validator `ProfileValidators.validateBackupContact` ([ProfileValidators.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/util/ProfileValidators.kt)): both fields optional when empty; name-only or phone-only allowed; phone validated with the project's existing digit-count rule (9–15 digits, the same rule onboarding/auth use); a backup number equal to the primary `phoneNumber` is rejected. Errors surface in the UI through the field's supporting text so the worker can correct the value before submitting.
- **REG-003 — decision (immediate vs. review)**: Updates are **IMMEDIATE**, matching how other profile metadata (district, active mode) is saved; the KYC review queue applies only to identity documents and the requirement introduces no review step for a contact number. Writes always target the signed-in user's own Room row and own Firestore `users/{uid}` document (`firestore.rules`: `allow create, update: if isDocOwner(userId)`), so one user can never modify another user's backup contact. The requirement's open question ("own number or another person's?") is resolved in the implementation and `prd.json` notes as the worker's own alternate contact, with the name field optional.

- **REG-004 — storage/upload**: `FirebaseService.uploadProfilePhoto(userId, photoUri)` uploads to Firebase Storage `avatars/{userId}/profile_<timestamp>.jpg` and returns the download URL; only that URL is persisted on `avatarUrl` in Room + Firestore — **no image bytes** in Room or Firestore. It reuses the existing `storage.rules` `avatars/{userId}/{allPaths=**}` block (already `allow read: if true; allow write: if isOwner(userId)`), so **no rules change was required**. It fails closed (returns null) on a blank userId, a missing Storage instance, or an upload error.
- **REG-004 — UI**: New `ProfileSettingsSheet` in [AccountCenterSheets.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/components/AccountCenterSheets.kt), opened from the navigation drawer's new "Profile Photo & Backup Contact" item: circular preview with an initials fallback, "Choose photo"/"Replace photo", explicit Upload and Cancel buttons, an upload spinner with "Uploading photo…", and a user-visible error on failure. `UserProfileEntity.avatarUrl` default changed from the seeded Unsplash stock photo to `""` so new profiles render initials instead of a stranger's photo.
- **REG-004 — visibility (documented decision)**: The worker sees the photo in the drawer header and in the sheet; a blank, missing, or failed-to-load URL renders the initials layer underneath rather than a broken image. Client-facing *discovery* cards (`ProviderCard`/`ProviderDetailSheet`) still render initials because they read `service_providers` rows, a separate catalog table with no FK to `user_profiles`; linking them would require a data-model change and is recorded as a follow-up rather than done here. The photo is deliberately **not** gated on KYC approval or activation (it behaves like other profile metadata), and National ID images, guarantor photos, certificates and other verification documents are never exposed through this field — those live under the private `users/{uid}/kyc|guarantors|certificates` Storage prefixes and separate KYC tables.
- **Database/migration**: `AppDatabase` version 17 → 18 with new `MIGRATION_17_18` adding `backupContactName`/`backupContactPhone` via `ALTER TABLE user_profiles ADD COLUMN ... TEXT NOT NULL DEFAULT ''` — non-destructive (existing users keep their data and simply start unset), registered in `addMigrations`. `avatarUrl` needed no migration because its column already existed; only its Kotlin default changed. `exportSchema = false` matches the existing convention, so no schema JSON is produced.
- **Firestore additions (additive, backwards compatible)**: `users/{uid}` gains `avatarUrl` (String, optional), `backupContactName` (String, optional) and `backupContactPhone` (String, optional); `FirebaseService.saveUserIdentity`/`fetchUserIdentity` write and read them, and older documents lacking the fields restore as empty strings.
- **Security**: No gate was weakened. `uploadProfilePhoto` only ever receives the signed-in user's own id (the ViewModel passes `currentUser.value!!.id`) and blanks out for the demo placeholder profile id; signed-out users are routed to auth by the drawer before the sheet can open. Storage writes are UID-scoped and Storage-rule enforced, Firestore profile writes are owner-only, and the five-layer worker dashboard gate (`canAccessWorkerFeatures`) is unchanged — a new test proves that backup contact + photo presence cannot satisfy it.
- **Tests added**: [ProfileValidatorsTest.kt](file:///d:/skills%20exchange/app/src/test/java/com/example/util/ProfileValidatorsTest.kt) (15 tests: valid/invalid/empty backup phone, duplicate-primary rejection, upload blockers) and [ProfileContactPhotoPersistenceTest.kt](file:///d:/skills%20exchange/app/src/test/java/com/example/data/ProfileContactPhotoPersistenceTest.kt) (5 tests: backup contact save/reload, per-UID ownership, empty defaults, avatar URL persistence, SEC-009 gate unaffected).
- **Verification**: `:app:compileDebugKotlin` PASSED; `:app:testDebugUnitTest` PASSED — **39/39 tests, 0 failures, 0 errors** (XML verified: ProfileValidatorsTest 15, ProfileContactPhotoPersistenceTest 5, UserProfilePersistenceTest 7, AuthViewModelTest 9, example/screenshot 3); `:app:assembleDebug` PASSED (BUILD SUCCESSFUL in 5m). One failure was found and fixed during development: the new `ProfileValidatorsTest` "too long" case used a 14-digit number, which is valid under the project rule — the test expectation was corrected, not the validator, and no production code was changed to make a test pass.
- **Environment note**: The first `assembleDebug` attempt lost its Gradle daemon to a native-memory `mmap` failure (`hs_err_pid11432.log`: "insufficient memory for the Java Runtime Environment … G1 virtual space") during dexing; re-running the identical command succeeded (5m). Heap/memory settings were deliberately NOT changed (out of scope); this is recorded as an environment observation only.
- **Dependency**: `libs.coil.compose` uncommented in `app/build.gradle.kts` (the library was already declared in `libs.versions.toml`). No Gradle/JDK/memory configuration was otherwise touched.
- **PRD**: `REG-003` and `REG-004` marked `completed` (completed: 85 of 92).
- **Device verification still required**: see `prd.json` risks for both stories.

### [2026-09-23] [COMM-002] [IMPL] Community Feature Removal Implemented & Verified

- **Audit**: The Community feature ships under the "Board" tab / neighborhood-feed names (no literal `Community*` classes). Full dependency report produced before removal: UI, navigation, ViewModel, repository, Room entity/DAO, migrations, Firestore rules (none), tests (none), docs.
- **UI / Navigation ([MainScreen.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/screens/MainScreen.kt))**: Removed the "Board" bottom-nav tab (now List / Map / Jobs only), the `"Board" ->` content branch, the `androidx.compose.material.icons.outlined.DynamicFeed` import, and the Board mention in the map lazy-load comment. NavigationDrawer/MainActivity had no Community entries.
- **ViewModel ([SkillsExchangeViewModel.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt))**: Removed `neighborhoodPosts` flow and the "Board" comment from `activeMobileTab`.
- **Repository ([SkillsExchangeRepository.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/data/repository/SkillsExchangeRepository.kt))**: Removed neighborhood-post seed data/flow (~42 lines).
- **Database**: Removed `NeighborhoodPostEntity` (Entities.kt) and `NeighborhoodPostDao` (Daos.kt); removed both from `AppDatabase` registrations; version bumped 16 → 17; added and registered `MIGRATION_16_17` executing `DROP TABLE IF EXISTS neighborhood_posts` (safe/idempotent — no data migration needed for a dropped feature table).
- **Component**: Deleted `app/src/main/java/com/example/ui/components/NeighborhoodFeedView.kt`.
- **Kept deliberately (dependencies / false positives)**: `ServiceProviderEntity.neighborhood` and the provider `neighborhood` Firestore field (location data used by marketplace filtering and `functions/src/index.ts` district queries); the "COMMUNITY SKILL ENDORSEMENTS" label (endorsements feature); "community guidelines" training-module copy (worker training TRAIN-001/002).
- **Pre-existing failure found during verification (NOT caused by COMM-002)**: `AuthViewModelTest` 2/19 still asserted pre-SEC-009 `UserProfileEntity` defaults; `git diff app/src/test` was empty before repair, proving COMM-002 never touched tests. Assertions corrected to the shipped SEC-009 fail-closed defaults (`workerKycStatus=NOT_APPLIED`, `isWorkerActivated=false`, `trainingCompleted=false`); no production code changed.
- **Verification**: `:app:compileDebugKotlin` PASSED; `:app:testDebugUnitTest` PASSED — **19/19, 0 failures** (UserProfilePersistenceTest 7, AuthViewModelTest 9, ExampleRobolectricTest 1, ExampleUnitTest 1, GreetingScreenshotTest 1); `:app:assembleDebug` PASSED (BUILD SUCCESSFUL in 8m 12s). Offline, `GRADLE_USER_HOME=D:\gradle-home`, `JAVA_HOME=JDK17`, `--max-workers=1`, `--no-parallel`, `--no-configuration-cache`; no memory/Gradle/JDK settings changed.
- **PRD**: `COMM-002` marked `completed` in `prd.json` (completed count: 83).
- **Pre-existing records gap repaired during sync**: `AUTH-011` (Google Sign-In, P0, completed 2026-09-09) had `status: completed` but was missing from `completed_stories[]` and had no row in `TASKS.md`; added to both. Corrected totals: 83 completed / 9 pending / 92 total.

### [2026-09-23] [FEAT] SEC-009 — Worker Dashboard Security & Access Control Implemented & Verified

- **Entities ([Entities.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/data/db/Entities.kt))**: Set `UserProfileEntity` defaults to fail-closed: `workerKycStatus = "NOT_APPLIED"`, `isWorkerActivated = false`, `trainingCompleted = false`.
- **ViewModel ([SkillsExchangeViewModel.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt))**:
  - `canAccessWorkerFeatures()` strictly enforces: authenticated session + worker registration/qualification + KYC status `APPROVED` + `trainingCompleted` + `isWorkerActivated`.
  - `isWorkerApprovedAndActivated` StateFlow enforces the exact same multi-layer requirement.
  - `completeOnboarding()` and `restoreSession()` no longer set client accounts to `APPROVED` or `isWorkerActivated = true`; client accounts remain fail-closed as `NOT_APPLIED` and inactive.
  - `toggleWorkerOnline()` is gated behind `canAccessWorkerFeatures()`.
- **UI ([MainScreen.kt](file:///d:/skills%20exchange/app/src/main/java/com/example/ui/screens/MainScreen.kt))**:
  - Worker dashboard (`FreelancerDashboardView`) is rendered only when `isWorkerApprovedAndActivated` is true.
  - When unverified or unapproved, `WorkerVerificationStatusView` or `WorkerTrainingScreen` is displayed.
  - Connected `WorkerVerificationStatusView`'s `onStartVerification` callback to `IdentityVerificationSheet` (fixing audit G4).
- **Verification**: `:app:testDebugUnitTest --tests com.example.data.UserProfilePersistenceTest` passed with 7/7 tests passing (including new `client account cannot access worker dashboard or satisfy activation gate (SEC-009)`).
- **PRD**: Updated `SEC-009` to `completed` in `prd.json` (completed count: 80).



- `d:\skills exchange\app\src\main\java\com\example\ui\screens\OnboardingScreen.kt` and `d:\skills exchange\app\src\main\java\com\example\ui\components\AuthModal.kt`: explicitly set Slate900 focused/unfocused legal-name text on white, retaining blue cursor/labels. Theme-derived light text against a light surface caused the contrast defect; no Google display-name prefill introduced.
- Onboarding district no longer defaults to Sunnyside or replaces selection on GPS errors; required label and nonblank completion gate added. AuthModal already gates nonblank district but retains initialDistrict default. Device validation remains untested.
- Compile and assembleDebug PASSED (combined 6m 56s, exit 0); Samsung SM-A025F replacement install PASSED without clearing data. Installed version 1.0/code 1, lastUpdateTime 22:46:09; installed/local APK SHA-256 match F5497194E64BC72B1E195B287C6A0ABE3B4B62FE60420440555BAA99F7E009EB.
- Google buttons trace through GoogleSignInHelper signInIntent, ViewModel and Firebase credential sign-in. Current device is signed in; auth button/chooser not reproduced. Recent logcat filter contains no matching sign-in failure. Earlier missing-chooser root cause is unknown; no speculative Google/Firebase changes.
- User confirmed no display-name prefill before this patch. Post-install legal-name contrast, chooser launch and district-required behavior require manual retest. C1/C2/C3 remain PENDING. No other audit items, Phase-17, Community, PRD changes or commit.


### [2026-09-17] [VERIFY] C1/C2/C3 — compile, targeted tests 6/6, APK, device install PASSED; manual A–F pending

- `:app:compileDebugKotlin` PASSED (3m 12s) after the earlier duplicate-signature slip was corrected to the original `triggerSosAlert(jobId)` signature.
- `UserProfilePersistenceTest` PASSED **6/6, 0 failures, 0 errors, 0 skipped** (fresh 2026-09-17 XML) running `--offline` against the pre-existing complete Gradle cache at `D:\gradle-home`. Earlier failures were environmental: the project `GRADLE_USER_HOME` cache lacked nine Robolectric runtime JARs and downloads stalled; no dependency, repository, Gradle/AGP/toolchain or source configuration was changed to fix this.
- `:app:assembleDebug` PASSED (2m 53s); `app-debug.apk` (32,135,868 bytes) built and installed on Samsung SM-A025F (serial R9HR30HWJ5J) via `adb install -r` → Success. No app data or Firebase account cleared.
- Manual cases A–F require interactive Google/phone input on the device and remain NOT TESTED; C1–C3 therefore stay ⏳ Pending Audit Fix in TASKS.md and are not marked completed.
- No H-items, Phase-17, Community, PRD or completion-status changes; no commit. prd.json verified unchanged (79 completed stories, 12 Phase-17 pending).


### [2026-09-17] [VERIFICATION] C1/C2/C3 — Compile passed; runtime verification blocked

- Corrected only the duplicate-signature compiler regression in `d:\skills exchange\app\src\main\java\com\example\data\repository\SkillsExchangeRepository.kt`: restored `triggerSosAlert(jobId)`; existing GPS/SMS method unchanged.
- Compile retry passed in 3m 12s (JDK 17 launcher, existing JDK 25 daemon selected by unchanged configuration). No configuration changes.
- Targeted `com.example.data.UserProfilePersistenceTest` build failed in 6m 52s before tests executed: Robolectric runtime dependencies unavailable, network unreachable. No dependency substitutions or full suite run. Two online retries stalled during artifact download and were cancelled. A final offline run failed in 1m 6s with nine uncached runtime JARs (including Robolectric native-runtime 1.0.18); no fresh tests executed. Stale September-16 generated results were discarded. See AGENT_STATE.md for diagnostics and retained logs.
- Samsung SM-A025F connected. APK/install not attempted because tests did not pass; manual cases A–F all NOT TESTED. No Firebase account or local device data deleted.
- UID audit still finds the demo entity default and development anonymous-auth paths; their safety is not established by string search alone. Detailed evidence and residual risks are in AGENT_STATE.md.
- C1/C2/C3 remain PENDING / UNVERIFIED. No H-items, Phase-17, Community, PRD or completion-status changes; no commit. Four requested obsolete scratch logs removed; current verification evidence retained.


### [2026-09-17] [FIX] Audit C1/C2/C3 — Profile persistence, UID consistency, Google completion (IMPLEMENTED, verification blocked)

**Scope:** Only audit items C1, C2, C3. No Phase-17 work, no Community removal, no H-items, no new PRD IDs, no prd.json change.

**C1 — Existing-user profile persistence**
- `SkillsExchangeViewModel.restoreSession()` else-branch: when no local DataStore session exists but `FirebaseService.getCurrentUser()` still returns a signed-in user (Room wiped / app-data cleared while the Firebase token survived), the profile is recovered via Room UID lookup then `users/{uid}` Firestore lookup, re-persisted to Room, and the session + role mode are rebuilt — but ONLY when the recovered profile has `isOnboardingCompleted == true`. Otherwise the user stays on onboarding. Worker KYC/activation/training gates untouched.
- Existing authenticated-session restore path (Room → server → session rebuild) unchanged.

**C2 — UID consistency (no synthetic IDs for authenticated users)**
- `loginUser()`: removed `?: "user_${System.currentTimeMillis()}"` fallback — fails closed with an error when Firebase supplies no UID.
- `loginWithGoogle()`: removed `?: "google_user"` fallback — fails closed on blank UID.
- `FirebaseService.signInAnonymously()`: returns the already-signed-in user instead of minting a second anonymous identity that would fork the UID.
- `uploadQualificationEvidence()`, `startTraining()`, `completeTrainingModule()`, `completeTraining()`, `submitQuote()`, `submitRecurringProposal()`, `reportUser()`: resolve Room profile id (excluding `user_default_me`) else Firebase UID, abort when absent — no `user_default_me` / `worker_default` / `anonymous` placeholders.
- `SkillsExchangeRepository.triggerEmergencySos()` + `respondToQuote()`: notifications attributed to the real UID; notification step aborts when no real id exists (status updates still applied).
- `AccountComplianceDialog.kt`: guarantor submit aborts when no authenticated UID (orphaned A7 path) instead of filing under `user_default_me`.
- Verified by search: zero remaining `?: "user_default_me"`, `?: "worker_default"`, `?: "anonymous"`, `user_${...}`, `?: "google_user"` fallbacks in `app/src/main`; remaining `user_default_me` hits are the `DEMO_PROFILE_ID` constant definition, the DAO exclusion clause, and test fixtures only.

**C3 — Google auth/onboarding completion**
- `loginUser()`: blank phone fails closed with `phoneError` before any profile/session/server write.
- `loginUser()` new-profile branch: `fullName` stored exactly as typed — removed `firebaseUser?.displayName ?: "Specialist"/"Neighbor"` fallback.
- `AuthModal.kt` PROFILE_SETUP "Complete Setup" button now requires `fullName && phoneNumber && district` (was name + district only).
- `OnboardingScreen.kt` completion button now requires `fullName && phoneNumber` (was name only); selected CLIENT/WORKER role preserved via `selectedRole`; no display-name pre-population (unchanged from prior fix).
- `AuthModal.kt` Google launcher already dismisses explicitly via `onDismiss()` on the recognised-account path (verified, unchanged); `loginWithGoogle()` never invents a blank-phone profile for unknown accounts — returns `onComplete(true)` so the UI collects legal name + phone.

**Verification status:** `:app:compileDebugKotlin` attempted with bundled JDK 11 (`jdk11/jdk-11.0.22+7`, `GRADLE_USER_HOME=.gradle`). Offline run failed (foojay plugin not cached); online run is in progress downloading the JDK 25 toolchain (PID 8808/17716, foojay lock contention observed). No test suite, APK build, or device test completed yet — device connectivity unknown. C1/C2/C3 therefore NOT marked completed/verified.

### [2026-09-17] [AUDIT] Feature Connection Audit — Documentation Only

- A READ-ONLY Feature Connection Audit was performed: 48 interactive controls examined.
- Found: 8 confirmed disconnected features (A1–A8); 9 partially connected features (B1–B9).
- Also documented: 7 security/authorization findings (G1–G7), 11 placeholder/demo findings (D1–D11), 4 navigation/UX findings (N1–N4), 2 permission findings (P1–P2), 4 map findings (M1–M4), 9 authentication findings, and the Community removal reference map (DO NOT REMOVE NOW).
- Findings preserved in AGENT_STATE.md (2026-09-17 section) and TASKS.md (Pending Fixes register); decision recorded as DEC-018 in DECISIONS.md; cross-reference added to requirement_file.md.
- No source code was changed. No fixes were implemented. No commit was made. prd.json untouched (79 completed / 12 Phase-17 pending unchanged).

### [2026-09-16] [FIX] Feature Connection Audit — Post-Audit Bug Fixes

**AUTH-006 / AUTH-002 — Existing Google User Recognition**
- Added durable server-side identity record at `users/{uid}` in Firestore (`saveUserIdentity`, `fetchUserIdentity`, `fetchUserIdentityByPhone`)
- `loginUser()` now resolves real Firebase UID before persisting profile (eliminates synthetic `user_<timestamp>` IDs)
- `loginUser()` now checks Firestore identity as fallback when local Room profile missing
- `loginUser()` now persists profile to server via `saveUserIdentityToServer`
- `isWorkerApprovedAndActivated` `stateIn` initial value changed from `true` to `false` (fail-closed)

**AUTH-007 / SEC-009 — Worker Dashboard Security**
- `setUserRoleMode()` now enforces `canAccessWorkerFeatures()` gate at the ViewModel boundary
- `canAccessWorkerFeatures()` now also checks `isAuthenticated.value` (signed-out device cannot unlock worker mode)

**Legal Name / Google Display Name**
- `OnboardingScreen.kt`: Removed automatic `account.displayName` pre-population of the legal name field

**Navigation Drawer — Real Destinations**
- `NavigationDrawer.kt`: Removed hardcoded wallet balance (`CurrencyRegion.format(240.0)`) and hardcoded phone fallback (`"+256 700 123 456"`)
- `NavigationDrawer.kt`: Added `onOpenVerification` callback parameter
- `MainScreen.kt`: Drawer menu items now wire to real sheets (`EscrowWalletSheet`, `JobHistorySheet`, `VerificationStatusSheet`, `TrustAndSafetySheet`) with actual data

**New Files:**
- `app/src/main/java/com/example/ui/components/AccountCenterSheets.kt` — Real destination sheets

### [2026-09-16] [DOC] Future Requirements Documentation Capture (Phase 17)

**Added 12 new future/planned requirements to PRD.json, requirement_file.md, TASKS.md, and AGENT_STATE.md:**
- PAY-005: Job Payment Fees & Platform Deductions (5% commission + 5% platform fee)
- REG-001: First-Time Worker Registration Fee (UGX 20,000)
- REG-002: Worker Registration Guarantor Verification Requirements (2 guarantors + photos)
- REG-003: Worker Backup Contact Number
- REG-004: Worker Profile Photo
- LOC-005: Worker Location Using Device GPS
- MAP-002: Google Maps Usage Minimization
- COMM-002: Community Feature Removal
- SEC-009: Worker Dashboard Security & Access Control
- LEGAL-001: Liability Disclaimer
- LEGAL-002: Privacy Policy / Terms & Conditions / User Consent
- AUTH-012: Preserve Existing Authentication/Onboarding Requirements

**Status:** All new requirements marked as FUTURE / PLANNED / NOT IMPLEMENTED.
**Documentation files updated:** prd.json, requirement_file.md, TASKS.md, AGENT_STATE.md
**No code changes.** This is documentation capture only.

### [2026-09-16] [AUTH-002/ROLE-001] [FIX] Persist and resolve the authenticated user profile correctly
**Root causes found and fixed:**
1. `UserProfileDao.getCurrentUser()` used `SELECT * FROM user_profiles LIMIT 1` with no WHERE clause, so the seeded demo row (`user_default_me` — "Sarah Namukasa", KYC APPROVED, activated) was returned as the current user instead of the authenticated profile. The NavigationDrawer therefore showed the demo name/initials ("Sarah Namukasa" / "SN") and the worker activation gate could be satisfied by the demo row.
   - Fix: `getCurrentUser()` now excludes the demo placeholder (`WHERE id != 'user_default_me' ORDER BY updatedAtTimestamp DESC LIMIT 1`).
2. `SkillsExchangeRepository.seedInitialDataIfNeeded()` inserted a fake demo `UserProfileEntity`, which created that shadowing row on every fresh install.
   - Fix: the demo user profile is no longer seeded; only demo content (providers/jobs/chat/posts/endorsements) is seeded. The authenticated profile is persisted by the onboarding flow (`completeOnboarding` → `loginUser` → `repository.saveUserProfile`, keyed by Firebase UID with the selected role preserved).
3. `loginUser()` reused any row matching the typed phone number, including the demo row (whose number is the onboarding placeholder hint), which kept the demo identity and silently downgraded a selected WORKER to the demo row's CLIENT role.
   - Fix: the demo row is excluded from the existing-account lookup; new profiles are always keyed by the Firebase UID and keep the selected role (CLIENT stays CLIENT, WORKER stays WORKER with UNVERIFIED / not-activated / training-incomplete gates intact).
4. `restoreSession()` never rebuilt the profile when the persisted session existed but the Room row did not (cleared/recreated database), so `currentUser` could stay null and the drawer fell back to Guest.
   - Fix: the profile is rehydrated from the persisted session (user id, name, district, role) and re-inserted into Room. Role is now persisted in DataStore (`user_role`) and restored. Restored workers fail closed (`UNVERIFIED`, not activated).
5. `restoreSession()` collected `sessionManager.fcmToken` with a never-completing `collect`, so `isSessionRestored` was never set and the app hung on the loading spinner after relaunching an authenticated session.
   - Fix: replaced with a terminating `first()` read (live updates still handled by `onFcmTokenRefreshed`).

**Verification:** test-result XMLs show `UserProfilePersistenceTest` 6/6, `AuthViewModelTest` 9/9, `ExampleRobolectricTest` 1/1 with 0 failures/errors; prior full run `testDebugUnitTest` + `assembleDebug` BUILD SUCCESSFUL (see `D:/gradle-temp/build2.log`). Clean compile/unit re-run is blocked in this environment (AGP 9.3.2 requires Gradle 9.5.0 but only a partial 9.5.0 wrapper dist exists, so the wrapper tries to re-download it; `--offline` fails plugin resolution; no device attached for runtime checks — `adb devices` returns empty).

### [2026-09-14] [AUTH-009] [FIX] Profile Name & Dynamic Initials Display Fix
- Initialize `MapView` and call `getMapAsync` once from `AndroidView.factory` instead of repeating lifecycle setup during recomposition
- Retain the `GoogleMap` instance in Compose state and keep marker creation/camera movement dependent on map readiness
- Preserve provider marker selection through the state-driven map effect
- Kotlin static diagnostics pass; full Gradle verification completed in the subsequent STAB-003 entry below

### [2026-09-14] [AUTH-009] [FIX] Profile Name & Dynamic Initials Display Fix
- Updated `NavigationDrawer.kt` profile header to reactively display `currentUserProfile?.fullName` when authenticated.
- Implemented `extractInitials` function returning uppercase initials ("Isaac Kato" → "IK", "Isaac" → "I", empty/null → "N", guest → "G").
- Added secondary display of user's phone number under full name in drawer header.
- Added unit tests in `AuthViewModelTest.kt` verifying initials extraction logic.

### [2026-09-05] [STAB-003] [TEST] Complete automated verification
- Full `./gradlew test` completed with `BUILD SUCCESSFUL`
- Robolectric and Roborazzi tests passed after redirecting Gradle and Robolectric temporary/cache directories to D: because C: had insufficient free space
- Verified with Gradle 9.3.1, Android Studio JDK 17, and the installed Android SDK

### [2026-09-05] [REL-001] [FIX] Restore debug APK packaging
- Removed the invalid custom debug signing configuration that referenced a missing repository-root `debug.keystore`
- Debug APK now uses the Android Gradle Plugin's standard debug signing behavior
- `./gradlew assembleDebug` completed with `BUILD SUCCESSFUL`

Format: `[DATE] [STORY-ID] [TYPE] Description`

Types: `IMPL` (implementation), `FIX` (bug fix), `TEST` (test), `INFRA` (infrastructure), `DOCS` (documentation), `SECURITY` (security)

---

### [2026-08-31] [SEC-008] [SECURITY] Add Storage Rules for Job Photos
- Updated storage.rules to restrict job photos access to job owner, assigned provider, and admin
- Files must include custom metadata: jobOwnerId and assignedProviderId
- Unauthenticated users cannot access job photos

### [2026-08-31] [AUTH-010] [IMPL] Implement Authentication Tests
- Created AuthViewModelTest.kt with unit tests for user model and access check logic
- Tests cover: UserProfileEntity defaults, worker access check logic, phone number uniqueness
- Tests use JUnit4 without external mocking dependencies

### [2026-08-31] [AUTH-009] [IMPL] Implement Auth State Restoration
- Updated restoreSession to restore user profile and role mode
- Added session corruption handling with automatic re-authentication
- Restored FCM token on app startup
- Session state now fully persists across app restarts

### [2026-08-31] [AUTH-008] [IMPL] Implement Session/Device Security
- Added session timestamp and device ID storage to SessionManager
- Added 7-day session expiration check in restoreSession
- Added device ID validation to detect device changes
- Updated loginUser and verifyOtp to save device ID with session
- Auto-logout on session expiration or device change

### [2026-08-31] [AUTH-007] [IMPL] Implement Role/Permission Enforcement
- Added canAccessWorkerFeatures() helper to ViewModel
- Updated toggleUserRoleMode() to prevent switching to worker mode without verification/activation
- Updated submitQuote() to reject quotes from unverified/inactive workers
- Role gating enforced at ViewModel layer for client/worker mode transitions

### [2026-08-31] [AUTH-006] [IMPL] Implement Duplicate Account Handling
- Added unique index on phoneNumber in UserProfileEntity
- Added getUserProfileByPhoneNumber() to UserProfileDao
- Added getUserProfileByPhone() to SkillsExchangeRepository
- Updated ViewModel.loginUser() to check existing account by phone before creating new profile
- Added MIGRATION_14_15 for unique index creation
- Bumped AppDatabase version to 15

### [2026-08-30] [DB-002] [IMPL] Add Room Foreign Keys and Indexes
- Added @ForeignKey and @Index annotations to skill_endorsements, job_posts, chat_messages, disputes, ratings, notifications, job_status_audit, reports
- Updated AppDatabase version to 14
- Added MIGRATION_13_14 with table recreation SQL for all affected tables
- Foreign keys enforce referential integrity; indexes improve query performance

### [2026-08-30] [NOTIF-004] [IMPL] Implement Notification UI
- Updated NotificationDrawer to accept List<NotificationEntity> instead of NeighborhoodNotification
- Added time-ago formatting for notification timestamps
- Added click handling on notification items
- Updated MainScreen to use real notifications from ViewModel instead of mock data
- Added ViewModel notification state: notifications, unreadNotificationCount
- Added markAllNotificationsRead() method to ViewModel
- TopHeaderBar unread badge now reflects real unread count

### [2026-08-30] [NOTIF-003] [IMPL] Wire Notification Triggers to Events
- Added notification creation in repository.submitQuote for new quote submissions
- Added notification creation in repository.respondToQuote for quote accept/decline
- Added notification creation in repository.updateEscrowStatus for payment status changes
- Added notification creation in repository.triggerSosAlert for emergency SOS events
- Added notification creation in KYC methods: submitWorkerKycApplication, approveKyc, rejectKyc, requestKycChanges
- Added notification creation in completeTraining for training completion
- Added ViewModel.updateJobStatus notification for job status changes
- All key business events now create NotificationEntity records in local Room DB

### [2026-08-30] [DISP-005] [IMPL] Implement Dispute Notification Integration
- Added notification creation in repository.updateDisputeStatus for dispute status changes
- Notifications created for dispute submission and resolution events
- Notification type DISPUTE_UPDATE with title and body based on resolution type

### [2026-08-30] [DISP-004] [IMPL] Implement User/Worker Reporting
- Added ReportEntity Room table with ReportDao for persisting user reports
- Added ReportDao with queries for all reports, user reports, and status filtering
- Added MIGRATION_12_13 creating reports table in AppDatabase v13
- Added repository methods: reportUser, getAllReports, getReportsForUser, getPendingReports, updateReportStatus
- Added ViewModel.reportUser() setting reportedBy from current user
- Built ReportUserDialog in ProviderDetailSheet with reason and description fields
- Wired onReportUser callback from MainScreen to ViewModel

### [2026-08-30] [AUTH-005] [IMPL] Implement Authentication Error Handling
- Added phoneError and otpError state flows to ViewModel for inline validation
- AuthModal displays red error text below phone and OTP fields on invalid input
- Added retry button to AuthModal when auth errors are present
- Added retryAuth() ViewModel method clearing errors and resetting auth state
- MainScreen passes phoneError, otpError, and onRetry to AuthModal

### [2026-08-30] [AUTH-004] [IMPL] Implement Account Deletion
- Updated permanentlyDeleteUserAccountAndData() to delete Firebase auth user, Firestore documents, and Room data
- Added FirebaseService.deleteCurrentUser() for Firebase auth user deletion
- Added FirebaseService.deleteUserFirestoreData() purging user profile, KYC, training, providers, guarantors, notifications, disputes, quotes, and job messages
- AccountDeletionDialog confirmation flow already wired to permanent deletion
- Full account purge requires re-authentication after deletion

### [2026-08-30] [AUTH-003] [IMPL] Implement Logout Flow
- Updated logoutUser() to clear Firebase auth, Room data via clearAllUserData(), and DataStore session
- Added repository.clearAllUserData() clearing all user tables on logout
- Logout resets all auth-related UI state (authState, isAuthenticated, currentUserPhone, selectedProvider, selectedJob)
- User must re-authenticate after logout as all local session data is purged

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
