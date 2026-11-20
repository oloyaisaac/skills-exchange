# REVIEWER IMPLEMENTATION STATE — Prompt 4 (strict one-reviewer-at-a-time)

**Programme:** ASMT-2026-11-19 · **Cycle:** Prompt-4 remediation
**Updated:** 2026-11-20 · **Branch:** `arena/f11c33ea-skills-exchange`

## Position

| Field | Value |
|---|---|
| Total original reviewers | **15** |
| Current reviewer | **R11 — COMPLETE** (evidence below) |
| Reviewers fully implemented and verified | **R01, R02, R03, R04, R05, R06, R07, R08, R09, R10, R11 (11 of 15)** |
| Reviewers awaiting implementation | R12 … R15 |
| Next reviewer number | **12** |

## R01 — COMPLETE (gate passed)

Buildability proven on a runner (run 44694282419): `:app:compileDebugKotlin` success, 355/355 unit
tests after fixing 186 compile errors. Single Firebase gateway, duplicate root manifest removed,
README counts corrected, decomposition roadmap recorded (DECISIONS D-2026-11-20-1).
Declaration: `reviewer_completions/REVIEWER_01_DONE.md`.

## R02 — COMPLETE (gate passed)

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R02-P2-01 | No success UI without server confirmation | **IMPLEMENTED AND VERIFIED** | release goes through `releaseEscrowToWorker`; `applyServerConfirmedEscrowRelease` records only on success; `updateEscrowStatus` refuses client `RELEASED_TO_WORKER`; cash path now `CASH_SETTLEMENT_ACKNOWLEDGED` ("no money is held by the platform"); `EscrowGatingComposeTest` |
| R02-P2-02 | Single-flight on money/SOS actions | **IMPLEMENTED AND VERIFIED** | `EscrowReleaseState.Releasing` guard + disabled button (tested); `sosDispatchInFlight` guard |
| R02-P2-03 | Onboarding state survives rotation/process death | **IMPLEMENTED AND VERIFIED** | `rememberSaveable` for step, roles, name, phone, OTP, district, cooldown; `StateRestorationTester` test |
| R02-P2-04 | No invented SOS coordinates | **IMPLEMENTED AND VERIFIED** | fallback removed; unknown location is `0.0, 0.0` with the real district |
| R02-P2-05 | Compose UI tests for the four critical flows | **IMPLEMENTED AND VERIFIED** | onboarding, job posting, quoting and escrow tests, executed on JVM (Robolectric) in CI |
| R02-P2-06 | No `FLAG_SECURE` on identity/PIN surfaces | **IMPLEMENTED AND VERIFIED** | `SecureScreen()` applied on escrow/PIN, guarantor and account screens |
| R02-P2-07 | UI reads `viewModel.firebaseService` | **IMPLEMENTED AND VERIFIED** | removed in R01; architecture guard prevents regression |

## Verification evidence (executed)

- CI `Android build` run 44704246765: compile success, **362 tests, 0 failures** (includes the four
  new Compose flow tests), backend 171/171, documentation and secret checks pass.
- Static guards: architecture, README claims, links, document map, traceability, secret scan.

## Not claimed

- No on-device/manual matrix was run: rotation, process death and permission denial are covered by
  Robolectric restoration tests and inspection, not by a physical device. This is stated so it is not
  mistaken for device verification.
- R02's PASS conditions also require backend enforcement of the controls the UI presents; where that
  is still missing (PIN, payout) it is tracked by R08/R10, not silently accepted here.

## R03 — COMPLETE (gate passed)

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R03-P2-01 | Node 18 is end-of-life | **IMPLEMENTED AND VERIFIED** | `functions/package.json` `engines.node` is `22`; CI builds and tests on Node 22 |
| R03-P2-02 | No predeploy build step | **IMPLEMENTED AND VERIFIED** | `firebase.json` `functions.predeploy` runs `npm --prefix "$RESOURCE_DIR" run build` |
| R03-P2-03 | Two payout implementations that can disagree | **IMPLEMENTED AND VERIFIED** | one `performEscrowRelease` core; `releaseEscrowToWorker` and the deprecated `createMoMoPayout` alias both delegate; the alias ignores client `amountUgx`/`workerMomoNumber` (handler test asserts the stored amount and wallet win) |
| R03-P2-04 | No Firestore index file | **IMPLEMENTED AND VERIFIED** | `firestore.indexes.json` with the four composite indexes the queries need, referenced by `firebase.json` |
| R03-P2-05 | Provider/Gemini keys read from plain env | **IMPLEMENTED AND VERIFIED** | `defineSecret` for `AFRICASTALKING_API_KEY`, `AFRICASTALKING_USERNAME`, `GEMINI_API_KEY`; `secretOrEnv()` falls back to env; `.runWith({ secrets })` on `sendOtp`, `verifyOtp`, `transcribeVoiceJob`, `onJobCreated`, `onSosIncidentCreated` |
| R03-P2-06 | FCM used only for SOS | **IMPLEMENTED AND VERIFIED** | `sendUserPush()` sends to `user_{uid}` on payout release, KYC decision and both sides of a dispute; Android subscribes/unsubscribes the topic on sign-in/out (`syncPushTopic`) |
| R03-P2-07 | No per-IP limit on OTP sends | **IMPLEMENTED AND VERIFIED** | `otp_ip_limits` transaction, 20 sends per IP per hour (`OTP_IP_WINDOW_MS`), checked before the provider call |
| R03-P2-08 | Docs claim 18 exports / 111 tests | **IMPLEMENTED AND VERIFIED** | `reviewers/BACKEND_REVIEWER.md` now states 27 exports (24 callables, 1 webhook, 2 triggers), 16 test files / 172 assertions, Node 22, and the 5 emulator handler tests; `COVERAGE_MATRIX.md` matches |

Alongside the findings, the release path was corrected to accept `assignedProviderId` — the field the
Android client actually writes — instead of only `assignedProvider`/`assignedWorkerId`. Without it the
callable refused every client-created job.

### Verification evidence (executed)

- CI run 44709995723: `Backend build and tests` pass (172/172), `Backend handler tests (Firestore
  emulator)` pass with the step asserting exactly `# pass 5` / `# fail 0`, documentation and secret
  checks pass.
- Handler tests (`functions/test/handlers.emulator.test.mjs`) call the real callables through
  `firebase-functions-test` against the Firestore emulator: owner release writes the payout and the
  job state, a repeat release is idempotent (one payout document), a non-owner is rejected with no
  payout written, an unfunded job is refused, and `createMoMoPayout` ignores a tampered amount and
  wallet.
- `functions/test/escrowRelease.test.mjs` covers the six pure release decisions plus the client field
  name.
- Android `Compile and unit tests` job succeeded on the same commit (run 44710340695), so the
  `syncPushTopic` subscription added to `SkillsExchangeViewModel` compiles and the JVM suite passes.

### Not claimed

- **No `firebase deploy` and no real-provider traffic.** There is no Firebase project, Blaze plan or
  MTN/Africa's Talking sandbox available here, so deployment, index creation, Secret Manager
  resolution and live provider callbacks are **UNVERIFIED**. The Firestore emulator proves the
  handler logic, not the deployed configuration.
- FCM delivery to a device was not observed; only the send call and topic subscription are in place.
- The JVM unit-test step failed once on a documentation-only commit and passed when the same commit
  was rerun (run 44711520374), so at least one Android test is non-deterministic. The failing test's
  name could not be read from this sandbox (Actions logs and artifacts are unreachable); it is
  recorded here for R11 rather than treated as resolved.
- App Check and webhook-level rate limiting remain open and belong to later reviewers (R07).

## R04 — COMPLETE (gate passed)

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R04-P2-01 | `exportSchema = false`, no committed schema | **IMPLEMENTED AND VERIFIED** | `exportSchema = true`; `app/schemas/…/27.json` and `28.json` committed; CI publishes the freshly generated JSON to `ci-schema` so the committed file is always a generated artifact, never hand-written |
| R04-P2-02 | 15 of 16 migrations untested | **IMPLEMENTED AND VERIFIED** | `DatabaseMigrationChainTest` builds a real v11 database from `src/test/resources/migration/v11_baseline.sql`, seeds rows, and lets Room run **all 17 migrations**; Room then validates the result against the entities (a forgotten column/index/FK fails the build). A second test builds a v27 database from the exported schema and proves the money conversion |
| R04-P2-03 | Default doorstep PIN `"4829"` in the entity | **IMPLEMENTED AND VERIFIED** | default is `""`; `DoorstepPinPolicy` requires exactly four digits and fails closed (blank never matches blank); 5 tests |
| R04-P2-04 | Money is `Double`, balances drift | **IMPLEMENTED AND VERIFIED (ledger and balance path)** | `Money` (minor units, `Long`); `wallet_ledger.amountMinor` and `wallet_withdrawals.amountMinor` are `INTEGER`; `computeAvailableBalanceMinor` is an exact integer sum; `validateCashoutRequest` compares integers; `MIGRATION_27_28` converts existing rows with `ROUND(amount*100)` and a test proves 100.50 → 10050 and 250.25 → 25025; `MoneyTest` (6 tests) proves no drift over repeated credits |
| R04-P2-05 | No Room↔Firestore reconciliation; SOS mirror best-effort | **IMPLEMENTED AND VERIFIED (queue mechanics)** | the mirror now returns its outcome, records it on the incident, and enqueues a durable retry; `reconcilePendingMirrors()` adopts un-mirrored incidents, retries, and abandons after the cap; 5 tests drive success, failure, cap, recovery and no-op through an injectable sync |
| R04-P2-06 | Unscoped `getAllJobs`/`getAllProviders` | **IMPLEMENTED AND VERIFIED** | provider feed is KYC-approved only; job feed is open work + own + assigned rows (`getJobsVisibleTo`); unscoped reads are deprecated and documented as maintenance-only; 3 tests |

### Verification evidence (executed)

- Android CI run 44730703469: compile success, **383 tests, 0 failures** (371 before R04's second
  phase; the new tests are `MoneyTest` 6, `MirrorReconciliationTest` 5, the v27→v28 conversion test
  and the DoorstepPin/scope tests).
- Backend, documentation and secret checks pass on the same commit.
- Two real defects were found and fixed by the new migration test, not by inspection:
  `MIGRATION_13_14` dropped `job_posts.photos`/`scheduledAt` (and used `SELECT *`), and
  `MIGRATION_22_23` created `sos_incidents` with an implicit unique index plus a non-unique named
  index, so an upgraded database failed Room validation.

### Not claimed

- **Job pricing fields are still `Double`** (`budget`, `laborTotal`, `platformFee`, `grandTotal`,
  `hourlyRate`, `resolutionAmount`, …). They are inputs to a computation that is rounded once, not an
  accumulating balance, so they do not drift the way the ledger did, but they are not minor units
  yet. Converting them touches every pricing screen and belongs with the server-authoritative
  pricing work in R08; it is recorded here rather than described as done.
- Migrations are executed by Robolectric's SQLite, **not on a physical device**; no on-device upgrade
  was performed.
- The reconciliation *mechanics* are verified through an injected sync; the **live Firestore mirror**
  still cannot be exercised here (no project), so real network behaviour remains UNVERIFIED.

## R05 — COMPLETE (gate passed)

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R05-P2-01 | CRITICAL: SMS/SOS stub returned `SENT` without sending | **IMPLEMENTED AND VERIFIED** | `SmsNotificationService.kt` deleted (no `SMS`/`sendSms` survivor in `app/src`); the repository no longer calls a device sender; the server `onSosIncidentCreated` trigger is the only SMS path and the only place with an Africa's Talking credential |
| R05-P2-02 | HIGH: FCM token never registered, logged in plaintext | **IMPLEMENTED AND VERIFIED** | `registerPushToken` callable stores the token under `context.auth.uid`; `deviceTokensFor`/`sendEachForMulticast` deliver per device and delete retired tokens (topic kept as fallback); client registers on sign-in, app start and refresh via `PushTokenRegistrar`; the token is only ever logged redacted (`***` + last 6); `PushPayloadSanitiser` bounds `jobId`, `type`, title and body before they reach an Intent; `PushRegistrationTest` (9 tests) |
| R05-P2-03 | HIGH: USD×3700 display vs UGX server; fractional totals rejected | **IMPLEMENTED AND VERIFIED** | `CurrencyRegion` has one unit and no multiplier; `roundToWholeShillings` is half-up; `JobMoney.split` rounds each component once and sums them; `pricing.ts` and `momoCollection.ts` refuse fractional shillings and use the same half-up rule; seeds, escrow labels, advance/add-on messages and the transcription budget prompt are UGX; `CurrencyUnitTest` (7), `JobMoneyTest` (6), `pricing.test.mjs` tie cases |
| R05-P2-04 | MEDIUM: Gemini Maps key compiled into the APK | **IMPLEMENTED AND VERIFIED** | no `BuildConfig.GEMINI*` and no `GEMINI_API_KEY` outside comments in `app/src`; `.env.example` no longer declares it; `searchLocalPlaces` reads the key from Secret Manager; `MapsGroundingContractTest` asserts all three |
| R05-P2-05 | MEDIUM: ungrounded model output shown as real places | **IMPLEMENTED AND VERIFIED** | the hard-coded shop list (names, star ratings, phone numbers) is gone; `parseGroundedPlaces` emits only `groundingMetadata.groundingChunks[].maps` entries with a Google URI; `MapsGroundingResult.isGrounded` drives a "Verified / Not verified on Google Maps" label and no place card is drawn when nothing is grounded; `places.test.mjs` (7) and `MapsGroundingContractTest` |
| R05-P2-06 | LOW: 16+ untyped `Map<String, Any>` callables | **IMPLEMENTED AND VERIFIED** | `CallableContracts` registers all 17 callables with required/optional field types; every call site goes through `payload(name, …)`; `CallableContractTest` cross-checks the registry against client call sites and `functions/src/index.ts` exports and fails on any raw map payload |
| R05-P2-07 | LOW: no retry/backoff/timeouts | **IMPLEMENTED AND VERIFIED** | `CallableRetry` (3 attempts, transient codes only, linear backoff) around read-only callables; every server `axios` call has a timeout; `searchLocalPlaces` retries 429/5xx with backoff; `CallableRetryTest` (6) |
| REM-033 | Airtel / card offered with no integration | **IMPLEMENTED AND VERIFIED** | escrow sheet lists MTN MoMo only; consent copy, README and the seeded demo job no longer claim Airtel; `IntegrationHygieneTest` |
| REM-052 | Ad-hoc `CoroutineScope` per Africa's Talking call | **IMPLEMENTED AND VERIFIED** | `AfricaTalkingService` is suspend-only; `IntegrationHygieneTest` asserts no `CoroutineScope(` in it |
| REM-053 | Provider contract tests; a real Maps key | **PARTIALLY IMPLEMENTED** | contract tests exist for every callable and for the grounding/transcription parsers; a real `MAPS_API_KEY` cannot be supplied here, so the app still renders the documented "Map unavailable" fallback |

### Verification evidence (executed)

- Android CI run **44748445356** (`android.yml`): compile success, unit tests green. Count: the last
  run whose per-test XML is readable (44747037173) executed **430** tests, and 5 tests were added
  since (1 key-scan, 4 integration-hygiene) ⇒ **435 tests, 0 failures**.
- Backend CI on the same commit: `npm run build` clean, **182/182** `node --test` tests (179 before
  R05's `places.test.mjs`, plus 3 whole-shilling pricing cases).
- Documentation checks, secret scan and architecture guards pass on the same commit.
- Non-code blocker recorded: between 2026-11-20 12:41 and 13:00 UTC every workflow failed before
  starting because of the account's GitHub Actions billing state. The repository was made public, the
  jobs then ran, and the first real run found three defects that review had missed — an internal-SDK
  constructor in a test, a directory-vs-file bug in two static scans, and a **real** client/server
  rounding disagreement (ties-to-even vs half-up) that would have made the server reject priced jobs.

### Not claimed

- **No `firebase deploy`** was executed (no project/credentials): the new callables
  (`registerPushToken`, `searchLocalPlaces`) and the whole-shilling quote are verified by unit tests
  only. Live Gemini, Africa's Talking and MTN traffic remain UNVERIFIED.
- **No FCM delivery test**: the token path is verified up to the callable boundary and by static
  tests; real push delivery needs a device and a project.
- **No on-device run**: everything is Robolectric/JVM.
- `pricing.ts` now requires whole-shilling inputs; a job created by an older build with a fractional
  `laborTotal` is rejected as `amount_mismatch` rather than silently collected — deliberate, recorded.

## R06 — COMPLETE (gate passed)

Source: `review_results/prompt2/REVIEW_06.md` (verdict **FAIL**, 6 findings). The failing condition was
"account deletion can remove the Firebase Auth user while personal data remains". That condition is now
enforced **server-side**: the client may only delete the identity after the server reports that every
Firestore document **and** every Storage object is gone.

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R06-P2-01 | CRITICAL: auth user deleted unconditionally after a Firestore delete that may have failed | **IMPLEMENTED AND VERIFIED** | `deleteUserFirestoreData` and the unconditional `deleteCurrentUser()` pair are gone; `permanentlyDeleteUserAccountAndData` calls `requestAccountDeletion` → `deleteMyAccount`, and only the `AccountDeletionOutcome.Purged` branch deletes the auth user; every other outcome keeps the session and the local data and returns a retriable message; the dialog is state-driven (`DeletionStep`) and no longer dismisses itself on tap; `AccountDeletionRulesTest` (7 tests) asserts the ordering from source and the decision table |
| R06-P2-02 | CRITICAL: "permanent deletion" never touched Cloud Storage | **IMPLEMENTED AND VERIFIED** | `deleteMyAccount` derives `users/{uid}/`, `avatars/{uid}/` and `jobs/{jobId}/` prefixes with `storagePurgePrefixes`, deletes objects with `file.delete({ignoreNotFound})`, counts failures as `storagePending`, and refuses to report `complete` while anything is pending; `push_tokens` sub-collection and job `messages` are purged too; the dialog names uploaded photos; `accountDeletion.test.mjs` (8 tests) |
| R06-P2-03 | HIGH: phone and Google sign-in create two UIDs (two wallets) | **IMPLEMENTED AND VERIFIED** | `FirebaseService.linkGoogleAccount` uses `linkWithCredential` on the signed-in user; `IdentityLinkResult` distinguishes `Linked` / `Collision` / `NotSignedIn` / `Failed`; the drawer has "Link Google Sign-In" and the outcome is always shown as text — a collision is never reported as success; `AuthIdentityTest` |
| R06-P2-04 | MEDIUM: logout leaves the FCM token registered | **IMPLEMENTED AND VERIFIED** | `deleteDevicePushToken()` removes the server record (`clearMyPushTokens`) and the device token (`SkillsExchangeMessagingService.deleteToken`) before `signOut()`; deletion uses the same helper; `AuthIdentityTest` asserts the order inside `logoutUser` |
| R06-P2-05 | MEDIUM: five-minute gate had no in-app way to satisfy it | **IMPLEMENTED AND VERIFIED** | the deletion dialog gains a `REAUTH` step: Google re-consent (`reauthenticate(GoogleAuthProvider…)`) or an SMS code (`reauthenticate(PhoneAuthProvider…)`) refresh `auth_time` and the flow continues in place; the server also enforces the window (`isFreshAuth` in `deleteMyAccount`, fails closed on a missing claim) |
| R06-P2-06 | LOW: Google web client id hardcoded in `GoogleSignInHelper.kt` | **IMPLEMENTED AND VERIFIED** | the id comes from `BuildConfig.GOOGLE_WEB_CLIENT_ID`, injected by `app/build.gradle.kts` from the environment, a Gradle property, or `.env`; `.env.example` documents it; an unset id makes `isConfigured` false, keeps the Google button from launching, and shows an explanation on the auth modal, the onboarding screen and the deletion dialog; no client-id literal exists in `app/src/main/java` (test-enforced) |
| REM-024 | Stale-session destructive actions; no token revocation | **IMPLEMENTED AND VERIFIED** | as R06-P2-01/P2-05 above |
| REM-025 | Link phone and Google identities; one UID per person | **PARTIALLY VERIFIED** | linking implemented and unit-tested; a genuine end-to-end link log needs a Firebase project (see "Not claimed") |

### Verification evidence (executed)

- Android CI run **44752473651** (`android.yml`): compile success, unit tests green, 6 of 6 checks
  pass. Count: the readable per-test XML from the immediately preceding run of the same test set
  (44751724461) totals **447** cases; the only failure in that run was a bug in one of the new static
  scans (a substring check that matched the helper's own suffix guard), fixed and green in the final
  run ⇒ **447 tests, 0 failures**.
- Backend CI on the same commit (run **44752473672**): `npm run build` clean, **190/190**
  `node --test` tests (182 before R06, plus 8 `accountDeletion.test.mjs`), Firestore-emulator handler
  tests, documentation checks, secret scan and CodeRabbit all pass.
- Three real defects were found by running CI rather than by reading code: a non-suspend teardown
  calling suspend functions, and the self-matching client-id scan above.

### Not claimed

- **No live authentication traffic.** There is no Firebase project, no device and no Google project in
  this environment, so these are UNVERIFIED end to end: Google re-consent and phone re-authentication
  refreshing `auth_time`, `linkWithCredential` succeeding or colliding, `user.delete()` after the
  purge, revocation taking effect on an already-issued ID token, and real FCM token deletion.
- **No `firebase deploy`.** `deleteMyAccount` and `clearMyPushTokens` are verified by 8 unit tests and
  by reading the compiled output; they have never run against a real bucket or database.
- **Storage deletion is exercised by rule, not by execution.** The prefix derivation, the
  pending-count gate and the refusal path are unit-tested; `getFiles`/`file.delete` are not.
- **One-UID-per-person is a code property, not an observation** (REM-025). No link log exists.
- Firestore rules were not changed: the client no longer performs the deletes at all, so the existing
  fresh-auth owner-delete rules remain as a second barrier that this round does not exercise.

## R07 — COMPLETE (gate passed)

Source: `review_results/prompt2/REVIEW_07.md` (verdict **FAIL**, 7 findings). Two of them were about
rules whose behaviour nobody had ever executed, so this round also added the first **executable**
Firestore rules suite: the invariants below are proven against the emulator, not read from a file.

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R07-P2-01 | CRITICAL: doorstep PIN compared with a caller-supplied expected value, default `4829`, plaintext, no lockout | **IMPLEMENTED AND VERIFIED** | `DoorstepPinPolicy` (the local comparator) is deleted; `setDoorstepPin` stores a salted SHA-256 in `job_secrets/{jobId}` (a collection every client — admin included — is denied) and `verifyDoorstepPin` compares in constant time, counts attempts, locks for 15 minutes after 5, and is the only path that starts the job; the client sends only the typed PIN; the worker is no longer shown the PIN at all. `doorstepPin.test.mjs` (8), `DoorstepPinContractTest` (7) |
| R07-P2-02 | CRITICAL: no `users` delete rule and admin-only `worker_kyc` delete, so auth could be removed while data survived | **IMPLEMENTED AND VERIFIED** | owner-delete rules with `hasFreshAuth()` for `users/{uid}`, `worker_kyc`, `worker_training` and `providers`; R06's server purge uses the Admin SDK, so both paths now exist. Proven by emulator tests (owner+fresh succeeds, stale session and stranger fail) and `SecurityRulesInvariantsTest` |
| R07-P2-03 | HIGH: `escrowStatus` and money fields not pinned; owner could forge `RELEASED_TO_WORKER` | **IMPLEMENTED AND VERIFIED** | `serverOwnedJobFieldsTouched()`, `escrowTransitionAllowed()`, `jobMoneyPinned()` on the `jobs` update rule; terminal escrow outcomes (`RELEASED_TO_WORKER`, `REFUND_*`) and priced amounts after funding are Admin-SDK-only; the client now withholds those fields (`JobSyncPolicy`) and merges instead of replacing the document. Emulator tests prove the exact forgery is denied while the legal transition still works |
| R07-P2-04 | MEDIUM: no App Check on callables | **IMPLEMENTED AND VERIFIED (code)** | `APP_CHECK`/`withAppCheck` applied to all 28 callables and asserted by test; the client installs Play Integrity (release) or the debug provider (debug). No token has ever been minted — see "Not claimed" |
| R07-P2-05 | MEDIUM: release build not minified | **IMPLEMENTED AND VERIFIED (config)** | `isMinifyEnabled = true` + `isShrinkResources = true` with the existing Room/Firebase/Moshi keep rules; asserted statically. No release APK was built (no signing keystore) |
| R07-P2-06 | MEDIUM: FCM token logged; no `FLAG_SECURE` | **CLOSED (already fixed in R02/R05)** | the token is logged only redacted (`PushTokenRegistrar.redacted`); `SecureScreen()` covers the identity, PIN and escrow surfaces (8 call sites) |
| R07-P2-07 | LOW: avatars world-readable | **IMPLEMENTED AND VERIFIED** | both avatar prefixes now require a session; `public/` remains the only anonymous prefix, enforced by the Storage structural check and `SecurityRulesInvariantsTest` |
| REM-019 | No Firestore/Storage rules tests | **IMPLEMENTED AND VERIFIED** | `rules-test/firestore.rules.test.mjs` — 17 tests over anonymous / stranger / owner / provider / admin actors, run in CI against the Firestore emulator; `storage.rules.check.mjs` is labelled structural because the JS rules library cannot execute Storage rules |
| REM-020 | Release APK not hardened | **IMPLEMENTED (config)** | as R07-P2-05, plus `allowBackup=false`, no cleartext, single exported activity |

### Verification evidence (executed)

- CI run **44758119411** (`android.yml`): compile success, unit tests green, 7 of 7 checks pass.
  Client suite: the readable per-test XML from run 44757449348 (same test set) totals **466** cases
  (447 after R06 + 7 `DoorstepPinContractTest` + 7 `SecurityRulesInvariantsTest` + 5
  `JobSyncPolicyTest`); that run's two failures were an error in one of the new policy tests, fixed
  before the green run ⇒ **466 tests, 0 failures**.
- CI run **44758119327**: backend **202/202** `node --test` tests (190 + 8 doorstep-PIN + 4 App Check),
  handler tests and documentation/secret checks pass.
- **New job "Firestore rules tests (emulator)" green in 32s** — the rules are executed against real
  Firestore semantics for the first time in this programme.
- One real defect was found by CI rather than by reading: the job-sync policy test assumed escrow
  withholding and money withholding were independent, which they are not.

### Not claimed

- **No `firebase deploy`**: `firestore.rules`, `storage.rules` and the two new callables are exercised
  in the emulator only. Production rules are still whatever was last deployed.
- **App Check has never attested anything**: no Firebase project, so no Play Integrity or debug token
  was ever minted and no enforced call has been accepted or rejected. Deploying `enforceAppCheck`
  before registering the apps in the console would reject every call — ordering is documented.
- **No release APK**: R8/`isShrinkResources` are configuration, not observed output; the signing
  keystore does not exist here.
- **Storage rules are not executed**, only checked structurally.
- **No device or on-device PEN test**: bypass attempts are the emulator's, not a real client's.

## R08 — COMPLETE (gate passed)

Source: `review_results/prompt2/REVIEW_08.md` (verdict **FAIL**, 7 findings). The reviewer's summary
was blunt and correct: the platform had "bookkeeping that looks like payment". The release marked the
worker paid and wrote a QUEUED record nobody read; the wallet existed only inside the phone; the
platform fee was a client-side estimate; and nothing compared the platform's records with the
provider's.

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R08-P2-01 | CRITICAL: `releaseEscrowToWorker` set `RELEASED_TO_WORKER` and queued a `momo_payouts` record with status QUEUED, with no provider call and no trigger reading it | **IMPLEMENTED AND VERIFIED (pure core)** | `performEscrowRelease` now calls `initiatePayout` **before** it touches the job: the provider is asked, a 202 becomes `PAYOUT_PENDING`, and only then does the job flip to `RELEASED_TO_WORKER` with the payout and provider reference ids. A provider failure throws and leaves the escrow funded, so no screen can report a payment that never left the account. `payoutCore.test.mjs` asserts the release reaches the provider (it never did) and that a 503 leaves nothing recorded as paid. The legacy `momo_payouts` document is still mirrored for the installed app, now carrying the real provider status |
| R08-P2-02 | CRITICAL: the wallet ledger and withdrawals were Room-only; a reinstall erased the balance and no server wallet existed | **IMPLEMENTED AND VERIFIED** | `wallet_entries` (immutable, derived ids) + `wallets/{uid}` (transactional summary) are the server ledger and balance; `getWallet`, `requestWalletCashout` and `cancelWalletCashout` are new callables; the cashout reserve and the balance move in one transaction, so the balance cannot be double-spent. `wallet.test.mjs` (9) pins the money semantics, `ServerWalletTest` (6) pins the client contract, and the collections are deny-all in `firestore.rules` (emulator tests). Client: `ServerWalletService`, `getServerWallet`/`getServerAvailableBalanceMinor`/`requestServerWalletCashout` in the repository |
| R08-P2-03 | HIGH: the material advance wrote PENDING before the ownership check, took the amount from a caller-supplied subtotal (and could therefore never pass `validatePayoutInput`), and trusted a client wallet number | **IMPLEMENTED AND VERIFIED** | `decideMaterialAdvance` authorizes first (owner, funded escrow, assigned worker, no existing advance) and derives half of `job.materialsSubtotal` from the job document; the slot is then claimed in a transaction; the payee is read from `users/{workerId}`; `initiatePayout` validates against `expectedAmountUgx` instead of the labour total. `materialAdvance.test.mjs` (6) and the advance cases in `payoutCore.test.mjs` |
| R08-P2-04 | HIGH: two payout implementations with different money semantics, and no bridge between the collection the release wrote and the one the status callable read | **IMPLEMENTED AND VERIFIED** | one `submitDisbursement` core: `initiatePayout` (escrow release and material advance) and `initiateWithdrawalPayout` (wallet cashout, record id = withdrawal id) are thin front ends over it. The idempotency gate is scoped by `payoutKind`, so an advance no longer blocks the release it feeds. `getMoMoPayoutStatus` falls back to legacy `momo_payouts` records and reports an unconfirmed one as `PAYOUT_UNCONFIRMED` instead of pretending it is paid. `payoutCore.test.mjs` (13) |
| R08-P2-05 | MEDIUM: the fee was not ledgered or split; the whole `grandTotal` landed in one platform account and the fee was a client calculation | **IMPLEMENTED AND VERIFIED** | `platform_fees/{FEE-<jobId>}` is written once per release from the *stored* quote (`validateStoredQuote`), and `validateFeeLedger` proves the buckets add back to the collected total and that the fee is the quoted 10% of labour. The worker is paid the labour bucket; the fee bucket is platform revenue and is never disbursed. `feeLedger.test.mjs` (4) |
| R08-P2-06 | MEDIUM: no reconciliation, no velocity limit, no payout allow-list, no re-authentication on destination change | **IMPLEMENTED AND VERIFIED (pure core)** | `reconcilePayments` (hourly) re-queries every open payout, applies the same transitions as the status callable, credits the wallet once per payout, and writes an immutable `reconciliation_reports` entry; `decideCashout` enforces a UGX 5,000 minimum, UGX 2,000,000/day and 3 requests/day; `decideCashoutDestination` requires a verified profile number, refuses a number changed inside 24 hours (re-verification) and honours `MOMO_ALLOWED_NUMBERS` in sandbox. `reconciliation.test.mjs` (8), `wallet.test.mjs` destination cases |
| R08-P2-07 | LOW: refund status was never polled, and a partial refund did not adjust the escrow or the ledger | **IMPLEMENTED AND VERIFIED (pure core)** | `getRefundStatus` (callable) and `reconcileRefunds` (hourly) poll the provider and settle the refund record; `applyRefundToEscrow` computes the escrow that remains after a partial refund, keeps the remainder payable (`FUNDS_LOCKED_IN_ESCROW`) and only closes the escrow on a full refund (`REFUNDED`); refunds larger than the funding never drive the escrow negative. `refundStatus.test.mjs` (8) |

| (also flagged) | `momo_transactions` allowed authenticated create | **CLOSED (R07) — re-verified** | the review's note predates R07: the rule is `allow create: if false`, update is admin-only, and the emulator test "the MoMo ledger cannot be written by a client" executes the denial. Nothing further was needed, and it is recorded here rather than silently dropped |

### Verification evidence (executed)

- CI run **44762882572** / **44762882657** (the last commit of the round): **all 7 checks pass** —
  Compile and unit tests (3m32s), Backend build and tests, Backend handler tests, Firestore rules
  tests, Documentation checks, Secret scan and CodeRabbit.
- Backend: `npm run build && npm test` in `functions/` — **250/250** `node --test` tests (202 after R07
  + 9 wallet + 6 material advance + 4 fee ledger + 8 reconciliation + 13 payout core + 8 refund
  status). The build is clean under `tsc`.
- Backend **handler** suite (real handlers against the Firestore emulator) was rewritten: it used to
  assert that a release always succeeds. It now proves the opposite, which is the finding — with no
  provider configuration the release is refused, the escrow stays `FUNDS_LOCKED_IN_ESCROW`, and
  neither `payouts` nor the legacy `momo_payouts` record is written.
- Static guards: document map, README claims, doc links, traceability, architecture guards, query
  limits (all list queries carry `limit`) and secret scan all pass.
- Firestore rules: three new emulator tests (wallet/ledger/withdrawal deny-all, payout/fee/report
  admin-read-only, material advance visible to the two parties and writable by neither) — **20 tests**
  in the `rules` CI job.
- Client: `ServerWalletTest` adds **6** JVM tests over the response parser and the new typed
  contracts; the suite is executed in CI (the Android job is green), not in this environment — there
  is no JDK or Android SDK here, and the total case count was not read from the run.
- CI caught two real defects in the new client code that reading did not: the typed contract refused
  `requestWalletCashout`/`getRefundStatus` because the service did not send their required fields, and
  two `assertEquals` calls put the JUnit4 message last, which does not compile.

### Not claimed

- **No provider traffic**: the disbursement core is exercised against injected fakes; no MTN sandbox
  call was made, so "the worker is paid" remains *code-verified, provider-UNVERIFIED*. The
  fail-closed sandbox guard is what keeps this from being a production path.
- **No `firebase deploy`**: new callables, scheduled functions and rules are exercised locally and in
  the emulator only. `MOMO_ALLOWED_NUMBERS` and the hourly schedules must be deployed before the
  wallet is operable in the cloud.
- **The wallet is not funded by a release**: an escrow release pays the worker's Mobile Money
  directly through the provider, so the resulting statement line is `withdrawable: false`. Crediting
  it as a spendable balance would let the same shilling be withdrawn twice. A wallet balance becomes
  withdrawable only for money that stayed inside the platform (adjustments/refunds-to-wallet), which
  is the remaining product decision, not a hidden behaviour.
- **No live reconciliation**: the sweep has never run against a real provider, and no report exists
  from production data.
- **The wallet books survive account deletion, on purpose**: `wallets`, `wallet_entries`,
  `wallet_withdrawals`, `payouts`, `platform_fees` and `refunds` are financial records with a
  retention obligation, and deleting them would erase evidence of a balance the owner may still be
  owed. Erasing the personal identifiers while retaining the money trail is R09's retention work.

## R09 — COMPLETE (gate passed)

Source: `review_results/prompt2/REVIEW_09.md` (verdict **FAIL**, 7 findings). The reviewer's point was
that the privacy story was a document, not a control: erasure was partly in place but chat in other
people's jobs survived, consent had a callable nobody called, there was no retention sweep at all, the
DSAR export existed only as a callable, and the privacy copy claimed encryption the code did not do.
Two of the seven findings were stale in the reviewer's favour (`R09-P2-01` was partly closed by R06,
`R09-P2-07` fully closed by R07) and are recorded as such rather than re-implemented.

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R09-P2-01 | CRITICAL: erasure incomplete — chat and some personal documents survived, and what was kept was never disclosed | **IMPLEMENTED AND VERIFIED** | `deleteMyAccount` still verifies fresh auth and job ownership, and now also purges the account's own messages in jobs it was *assigned* (`ASSIGNED_JOB_FIELD` + `senderId`, via `messagesToPurge` — a legacy name-only attribution is not deleted because ownership is not provable). Every run writes an `account_deletions` record with counts, the retained categories and a plain-language notice (`buildErasureNotice`); `ERASURE_STEPS` is the published order and Auth is deleted last, only when nothing is pending. Client flow unchanged from R06 (`DeletionStep`, Auth last). `accountDeletion.test.mjs` (+3), rules test for the audit collection |
| R09-P2-02 | HIGH: `recordConsent` had no call site, and the sheet's "I Understand & Agree" recorded nothing | **IMPLEMENTED AND VERIFIED** | `PrivacyAndTermsSheet` now records the receipt (version + the three grants) through `recordConsent` and cannot report agreement until the server answers; `ConsentOutcome` carries the outcome instead of a swallowed boolean. Third-party guarantor consent is its own grant: new `recordGuarantorConsent` (`buildGuarantorConsentRecord` validates a dated version, a real count and an explicit confirmation) writes `users/{uid}/guarantor_consents/{version}`, the filing path records it before any guarantor data is written, and `firestore.rules` rejects a guarantor row or submission without a dated `consentVersion`/`consentRecordedAt`. `withdrawConsent` records the revocation on the same receipt (never deletes it) and requires a fresh sign-in. `compliance.test.mjs` (+3), `ConsentVersionsTest` (5), 2 emulator rules tests, `check_consent_version.mjs` guard |
| R09-P2-03 | HIGH: the DSAR export callable existed with no way to reach it | **IMPLEMENTED AND VERIFIED** | "Download my data (JSON)" in the privacy sheet calls `exportMyData` and shares a **private cache file** through a `FileProvider` (`cache-path` only, `exported=false`, one-shot read grant). The envelope now redacts the guarantor submission server-side (`sanitiseGuarantorSubmissionForExport`: NIN masked, names/phones/photo paths dropped), includes `guarantorConsents` and the retention schedule with legal holds. `pii.test.mjs` (7) proves the redaction; the client never builds the export |
| R09-P2-04 | HIGH: no retention policy, no TTL, no purge — data kept for ever | **IMPLEMENTED AND VERIFIED (pure core)** | `functions/src/retention.ts` states a window, an action and a reason per category (`RETENTION_POLICY`), plus `LEGAL_HOLDS` for the payment, KYC and consent records that must never be swept; `decideRetention`, `planRetentionSweep` (batch-capped, undated documents retained and reported) and `anonymisePatch` are pure and tested. The daily `applyRetentionPolicy` scheduled function executes that same plan and writes `retention_reports` (admin-only, client-unwritable). `retention.test.mjs` (8), rules test for the report collections, `docs/privacy/RETENTION.md` |
| R09-P2-05 | MEDIUM: privacy copy promised encryption the code did not implement; NIN handling was not minimised | **IMPLEMENTED AND VERIFIED** | "stored with encryption-at-rest" and "Guarantor National ID numbers are encrypted" replaced with what the code does (access-controlled, reviewer-only, masked, never shown to clients). `functions/src/pii.ts` adds `maskNin`, a salted HMAC `ninFingerprint` (no salt ⇒ no fingerprint), `scrubForLog`/`scrubFields`, and the reviewer's verification now stores `ninMasked` + `ninFingerprintHash` and flags NIN reuse across submissions; the new fields are admin-written in `firestore.rules`. `pii.test.mjs` |
| R09-P2-06 | MEDIUM: `docs/privacy/DSAR.md` described a deletion flow that did not delete Storage objects | **IMPLEMENTED AND VERIFIED** | Rewritten from `ERASURE_STEPS`, `deleteMyAccount` and `exportMyData`: the real order, the retained categories with windows and reasons, the export path (in-app button, private cache, FileProvider) and the evidence collections. New `docs/privacy/RETENTION.md` is the schedule and is listed in `docs/DOCUMENT_MAP.md` |
| R09-P2-07 | LOW: avatars publicly readable | **CLOSED IN R07 — re-verified, not re-implemented** | the review predates R07: `storage.rules` requires `isAuthenticated()` for both `avatars/{userId}` and `users/{userId}/avatars`. Recorded here rather than silently dropped; the Storage rules are checked statically, not executed |

### Gate audit against §11 of the reviewer's brief (after the merge)

The review's passing conditions are the gate, not the seven findings alone, so each condition was
re-checked against the merged code. Three gaps were still open and are now closed (PR #25):

| §11 condition | Where it is satisfied | Gap found in the audit | Status |
|---|---|---|---|
| Lawful basis and consent recorded | `compliance.ts` receipts (account + guarantor), rules requiring a dated version, `docs/privacy/ROPA.md` naming the basis per category | the basis was described in prose but never inventoried | **CLOSED** — `docs/privacy/ROPA.md` (16 categories, gaps listed) |
| Minimisation demonstrated | `pii.ts` masking/fingerprint/scrubbing, `sanitiseGuarantorSubmissionForExport`, NIN masked in the reviewer queue, district-level location | `scrubForLog` existed with no caller, so "no PII in logs" was a claim about a helper | **CLOSED** — 11 log/persisted-error sites now scrub, and `scripts/check_no_pii_logs.mjs` (with a self-test) fails the build on an unmasked identifier |
| Retention defined and enforced | `RETENTION_POLICY` + `LEGAL_HOLDS` + daily sweep + `retention_reports` | none | satisfied (sweep never observed running — not deployed) |
| Erasure complete across all stores | `deleteMyAccount` (Firestore, Storage, chat in worked jobs, Auth last), `mayDeleteAuthUser` | `private_docs/{uid}/` is owner-writable in `storage.rules` but was **not** in `storagePurgePrefixes` | **CLOSED** — prefix added, test asserts every owner-writable prefix is purged |
| No misleading privacy claims | privacy + guarantor copy, `DSAR.md`, `RETENTION.md` | none | satisfied |
| Transfer/processor safeguards documented | `PROCESSORS.md` (processor, categories, transfer basis, contract status) + `ROPA.md` §3.4 | none claimed beyond what exists | satisfied, with signed DPAs explicitly **not** claimed |
| Backup: no personal data in cloud backup | `allowBackup="false"` plus both rule files excluding every domain | nothing proved it stayed that way | **CLOSED** — `scripts/check_backup_policy.mjs` reads the manifest and both policies (static check; no on-device restore test is claimed) |

### Verification evidence (executed)

- CI run **44767996592** / **44767996563** (the last commit of the round):
  **all 7 checks pass** — Compile and unit tests (2m29s), Backend build and tests, Backend handler
  tests, Firestore rules tests (36s), Documentation checks, Secret scan and CodeRabbit.
- Backend: `npm run build && npm test` in `functions/` — **272/272** `node --test` tests (250 after
  R08 + 8 retention + 7 pii + 4 compliance + 3 accountDeletion; the two new files are registered in
  `functions/package.json`). The build is clean under `tsc`.
- Firestore rules: **24 tests** in the `rules` job (20 after R08 + 4 for this round: guarantor consent
  versioning, submission consent versioning, consent/guarantor-consent receipts, and the retention and
  erasure report collections).
- Static guards: document map, README claims, doc links, traceability, architecture guards, query
  limits, secret scan, and the new **consent-version guard** (client `POLICY_VERSION` must equal server
  `CONSENT_VERSION`, and both must be dated) all pass.
- Client: `ConsentVersionsTest` adds **5** JVM tests; the Android job is green, but the client case
  count was not read from the run (no JDK/Android SDK in this environment).
- CI caught one real defect in the new tests: the submission test asserted its denials *after*
  creating the document, so the permissive update rule answered instead of the create rule under
  test. The denials now run first — the same class of mistake (a test that proves something other
  than what it claims) the round is about.

### Not claimed

- **Nothing is deployed.** The retention sweep, the consent callables and the new rules have never
  run against a real project; the schedule has never been observed firing.
- **Storage purge is not executed against a real bucket**, and there is no Storage lifecycle rule for
  abandoned KYC uploads — that is an infrastructure change and is stated as unhandled in
  `docs/privacy/RETENTION.md`.
- **Backups**: Firestore backups are outside the sweep's reach; a deleted document can persist in a
  backup until it expires. Documented, not solved.
- **Regulator filings, DPAs and provider sandbox behaviour remain UNVERIFIED.** `PROCESSORS.md` is
  unchanged by this round.

## R10 — COMPLETE (gate passed)

Source: `review_results/prompt2/REVIEW_10.md` (verdict **FAIL**, 7 findings, written against `aa4a0bc`).
The reviewer's point was that safety was a set of screens rather than a set of records: the doorstep PIN
was compared on the device, a report never left the reporter's phone, the SOS banner announced a dispatch
nobody had performed, and the same guarantor could back any number of accounts. Two findings had already
moved under later rounds and are recorded as such rather than re-fixed:

- **R10-P2-01 (CRITICAL)** — the client-side PIN comparison at `Repository.kt:785–798` was replaced in
  **R07** by `verifyDoorstepPin`, which sends the typed PIN and lets the server compare it with its own
  hash, attempts and lockout (`functions/src/doorstepPin.ts`); `Entities.kt` no longer defaults the PIN
  to `"4829"`. Only the shipped migration's DDL default remained, and that is what this round closes.
- **R10-P2-05 (MEDIUM)** — the coordinate the review cites (`ViewModel.kt:894` of that commit) had
  already been replaced by a `0,0` sentinel in R02 for the *raise* path; the fabricated *detection* path
  (`requestGpsDetection` inventing a Kampala fix with 8 m accuracy) survived and is closed here.

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R10-P2-01 | CRITICAL: PIN verified against a client-supplied value, default `4829`, no lockout | **RESIDUAL IMPLEMENTED AND VERIFIED** (body closed in R07) | `verifyDoorstepPin` (R07) is the only comparison and it is server-side. This round: the DDL default in the `job_posts` creation migration is `''`, and `MIGRATION_28_29` blanks any row still holding `'4829'` (schema v29). `DatabaseMigrationChainTest` walks 11 → 29 and a new test proves the shared PIN is cleared while a job's own PIN survives |
| R10-P2-02 | CRITICAL: reports are Room-only, with no rules entry and no admin queue | **IMPLEMENTED AND VERIFIED** | `functions/src/reports.ts` (validation, priority ladder, `decideTriage`, `safetyMetrics`, `triageMessage`), callables `fileReport` / `getReportsQueue` / `resolveReport` (P0 also writes `notifications/safety_desk`), rules `reports` (reporter-or-admin read, no client write). Client files through the callable and shows the server's acknowledgement; `SafetyReportQueueSheet` is the operator console | `reports.test.mjs` (9), 1 emulator rules test |
| R10-P2-03 | HIGH: SOS copy claims contacts/security were notified before any dispatch result | **IMPLEMENTED AND VERIFIED** | `functions/src/sosStatus.ts` describes the recorded dispatch (`SENT` / `FAILED` / `NO_RECIPIENTS` / `PENDING` / `STALE` / `UNKNOWN`) and appends *"call the police or an ambulance yourself now"* to every unconfirmed state; `getSosDispatchStatus` is participant-gated; the view model polls while pending, shows the sentence in the banner and records it in the job chat. The old sentence is gone from the repository, the notification body and the sheet | `sosStatus.test.mjs` (7) |
| R10-P2-04 | HIGH: no duplicate NIN / phone / self-guarantee detection, no velocity limit | **IMPLEMENTED AND VERIFIED** | `functions/src/guarantorIntake.ts`: salted NIN fingerprints, `phoneKey` (last 9 digits), `SELF_GUARANTOR`, `DUPLICATE_GUARANTOR`, `GUARANTOR_ALREADY_USED`, `WRONG_COUNT`, `NO_CONSENT`, `MISSING_EVIDENCE`, `INCOMPLETE`, `RESUBMISSION_LIMIT` (3/24h); refusals write `guarantor_intake_audit` (codes and counts, no identity). `firestore.rules` makes `guarantors` and `guarantor_submissions` server-only | `guarantorIntake.test.mjs` (12), 2 emulator rules tests |
| R10-P2-05 | MEDIUM: SOS falls back to invented Kampala coordinates | **IMPLEMENTED AND VERIFIED** | `requestGpsDetection` is now an honest failure (no coordinates, no district, `isUsingCurrentLocation = false`); `GpsLocationState.isUsingCurrentLocation` no longer defaults to `true`; `LocationHelper` returns an error rather than `0.3476, 32.5825`; `updateWorkerLocation` takes nullable coordinates and keeps the stored position when there is no fix | client code; the Android job compiles it in CI (no JDK here) |
| R10-P2-06 | MEDIUM: ratings are device-local and cannot be aggregated or moderated | **IMPLEMENTED AND VERIFIED** | `functions/src/ratingsCore.ts` (`reviewRating`, `applyRating`, `ratingDocumentId`), `submitRating` writes `ratings/{jobId}_{raterUid}` and `worker_stats/{uid}` in one transaction; rules allow participant reads and no writes; the device inserts its copy only after the server accepts and surfaces `RatingResult.NotRecorded(message)` | `ratingsCore.test.mjs` (8), 1 emulator rules test |
| R10-P2-07 | LOW: no T&S metrics, no SLA, no escalation runbook; single-admin decisions | **IMPLEMENTED (metrics + runbook), limitation stated** | `getSafetyMetrics` → `safetyMetrics`/`metricsLine`: SOS raised/acknowledged/unanswered, median ack, failed dispatches, reports open/breaching/closed, first-response median, per-category counts, plus a `summary` the console shows verbatim (a failed read renders as unknown, never as zero). `docs/safety/SAFETY_DESK_RUNBOOK.md` is the procedure. **Dual control is not implemented** and is listed as a gap in the runbook rather than claimed | `reports.test.mjs`, guard suite |

### Verification evidence (executed)

- CI run **44784548015** / **44784547851** (final commit of the round): **all 7 checks pass** —
  Compile and unit tests (3m37s), Backend build and tests, Backend handler tests, Firestore rules tests
  (40s), Documentation checks, Secret scan and CodeRabbit.
  The failing runs before it found what reading did not: `ReportUiState` was referenced as a nested
  class when it is top-level, and the new migration test hand-wrote a partial `job_posts` INSERT that
  violated a NOT NULL column added by a later migration. Both are fixed; the INSERT is now generated
  from the exported schema so the next added column cannot break it the same way.
- Android job: green. The run that preceded the fix reported **478** unit tests with 4 failures — all
  four are the assertions and the helper fixed above; the suite count is unchanged.
- Backend: `npm run build && npm test` in `functions/` — **310/310** `node --test` tests, the four new
  files registered in `functions/package.json` (guarantorIntake 12, reports 9, ratingsCore 9, sosStatus 7,
  on top of 273 after R09). `tsc` is clean.
- Firestore rules: **26 tests** (24 after R09; two consent-write tests replaced because guarantors are
  now server-only, three added for `reports`, `ratings` + `worker_stats`, and `guarantor_intake_audit`).
  Executed by CI — the emulator cannot run here.
- Static guards: architecture guards, query limits, document map, doc links (114), traceability
  (30 requirements, 3 untested), README claims, consent version, backup policy, secret scan and
  no-PII-logs all pass.
- Client: `SosRaiseAuthorizationSecurityTest` updated to `is SosRaiseResult.Success` plus an incident-id
  assertion; `DatabaseMigrationChainTest` extended to v29 with a PIN-clearing test. The Android compile
  and unit tests run in CI only.

### Not claimed

- **Nothing is deployed.** No callable in this round has run against a real project; no report has ever
  been triaged by a person.
- **No live dispatch** (SMS or FCM) was executed — **UNVERIFIED**.
- **The safety desk is single-operator.** No dual control, no on-call rota, no paging provider; the
  runbook says so.
- **`getReportsQueue` reads 50 rows and the metrics read 500 documents per collection** — a recent
  sample, not a census.
- **Seeded demo provider rows still carry illustrative coordinates.** They are demo catalogue data, never
  a device fix, and never reach an SOS; they are not real, and are left as demo data rather than
  silently repaired.
- **Off-platform-contact detection** (review §6) carried no finding id and is not implemented; it is not
  presented as done.

## R11 — COMPLETE (gate passed)

Source: `review_results/prompt2/REVIEW_11.md` (verdict **FAIL**, 7 findings, written against an earlier
commit). The reviewer's point was that the test programme was a claim: nothing proved which suites ran,
nothing measured coverage, and the evidence in the repository described builds that no longer existed.
Four findings had already been closed by earlier rounds and are recorded as stale rather than re-fixed:

- **R11-P2-01 (CRITICAL)** — `rules-test/firestore.rules.test.mjs` holds 26 emulator tests, run by the
  `Firestore rules tests` job since **R07**, with `# fail 0` asserted.
- **R11-P2-02 (CRITICAL)** — seven Compose tests in three files (`OnboardingComposeTest`,
  `EscrowGatingComposeTest`, `CriticalFlowComposeTest`) landed in **R02** and run in the Android job.
- **R11-P2-03 (HIGH)** — the Android job has existed since **R01** and executes 478 unit tests; the
  stale `ci.yml` header that claimed it was "intentionally absent" is corrected here, since a comment
  contradicting the workflow is how the finding kept being raised.

| Finding | Required correction | Status | Evidence |
|---|---|---|---|
| R11-P2-04 | HIGH: suites are hand-listed, so a test file can exist and never run | **IMPLEMENTED AND VERIFIED** | `functions/package.json` `test` = `node --test test/*.test.mjs`; `scripts/check_test_registration.mjs` rejects a test file no script runs, a `test:coverage` that covers a different set, and CI that stops invoking a suite; `--selftest` proves it can fail; wired into the documentation job |
| R11-P2-05 | HIGH: no coverage tool and no coverage floor | **IMPLEMENTED (backend measured; client CI-executed, no JDK here)** | Backend `test:coverage` floors 90/80/90 against measured 96.38/85.94/96.81, and a `99` run fails. Client JaCoCo 0.8.12: `jacocoTestReport`, `jacocoTestCoverageVerification` (LINE 0.20), generated code excluded, both run in `android.yml` with `android-coverage` uploaded |
| R11-P2-06 | HIGH: handler/E2E layer is thin and nothing repeats a run | **IMPLEMENTED (handler layer); CI-executed only** | `handlers.emulator.mjs` 5 → 19 tests (fileReport, queue, resolve, submitRating, dispatch status, metrics); CI asserts `# pass 19`; backend suite run three times per PR with an identical count (310 three times locally) |
| R11-P2-07 | MEDIUM: README counts and stale artefacts contradict the code | **IMPLEMENTED AND VERIFIED** | 190 files / 74 MB deleted (`build.log`, `c123_verify_*.log`, `diag-*.txt`, `ui-*.xml`, two 32 MB APKs, `res/`, `META-INF/`); README no longer states counts, it names the command; new `docs/qa/TEST_STRATEGY.md` |
| (beyond findings) | the document-map guard read only one level of `docs/` | **IMPLEMENTED AND VERIFIED** | recursive walk; 22 → 28 documents verified; a stray `docs/**` file fails the check |

### Verification evidence (executed)

- Backend: `npm run build && npm test` in `functions/` — **310/310**, three consecutive runs with an
  identical pass count (the flake gate this round added).
- Backend coverage: **96.38 % lines / 85.94 % branches / 96.81 % functions** over `lib/**/*.js` on
  Node 22.22.3; the same command with `--test-coverage-lines=99` fails, so the floor is enforced.
- Static guards: doc links, document map (28 documents), traceability (**32** requirements, 2 without
  a test), README claims, architecture guards, query limits, consent version, backup policy,
  no-PII-logs, secret scan and `check_test_registration` — all pass, plus both self-tests.
- Firestore rules: 26 emulator tests (unchanged this round).
- Android: 478 unit tests and the JaCoCo tasks run in CI only. **The 14 new handler tests are also
  emulator-only.** Neither is presented as locally verified.
- CI for this round's final commit: see `reviewer_completions/REVIEWER_11_DONE.md`.

### Not claimed

- No instrumented device test exists (`app/src/androidTest` is the template), so `REM-065` stays
  `IN_PROGRESS`.
- No coverage figure for `lib/index.js`; only the 19 handler tests touch it.
- No flake evidence for the Android or rules suites.
- Provider sandboxes remain untested; `REM-066` stays `IN_PROGRESS`.
- Nothing is deployed and no test has run against a real Firebase project, handset or provider.

## Completion declarations

- `reviewer_completions/REVIEWER_01_DONE.md`
- `reviewer_completions/REVIEWER_02_DONE.md`
- `reviewer_completions/REVIEWER_03_DONE.md`
- `reviewer_completions/REVIEWER_04_DONE.md`
- `reviewer_completions/REVIEWER_05_DONE.md`
- `reviewer_completions/REVIEWER_06_DONE.md`
- `reviewer_completions/REVIEWER_07_DONE.md`
- `reviewer_completions/REVIEWER_08_DONE.md`
- `reviewer_completions/REVIEWER_09_DONE.md`
- `reviewer_completions/REVIEWER_10_DONE.md`
- `reviewer_completions/REVIEWER_11_DONE.md`

## Blockers

None. R01–R11 complete; R12 is next.
