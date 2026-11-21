# DECISIONS.md — Skills Exchange Architectural & Design Decisions

All significant architectural, design, and implementation decisions are recorded here. This allows future agents and developers to understand why specific choices were made.

### D-2026-11-20-1 - R01 Remediation: One Firebase Gateway; God-Class Decomposition Roadmap

**Context.** R01 (Prompt-2 cycle) found two `FirebaseService` instances inside
`SkillsExchangeViewModel` (one for the repository, one exposed to composables), direct UI reads of
`viewModel.firebaseService` in `OnboardingScreen`, an unused root `AndroidManifest.xml`, and two
god classes (2,898-line repository, 2,792-line ViewModel) with no agreed split.

**Decision.**
1. Construct **one** `FirebaseService` before the repository and inject it. The field is private; the
   UI reads identity through `currentAuthenticatedUid()`.
2. Delete the root `AndroidManifest.xml`; the only manifest is `app/src/main/AndroidManifest.xml`.
3. Add `scripts/check_architecture_guards.mjs` to CI so the single-gateway rule cannot regress.
4. Decompose in slices, each behaviour-preserving and separately testable:
   - **A. Auth/session** → `AuthSessionCoordinator` (restore, login, logout, deletion, fresh-auth)
   - **B. Jobs/escrow** → `JobLifecycleCoordinator` (transitions, release, pricing hand-off)
   - **C. KYC/admin** → `VerificationCoordinator` (queue, reviews, guarantors)
   - **D. Wallet/cashout** → already mostly in `WorkerCashoutSecurityCore`; move orchestration there
   - **E. Notifications/SOS** → `SafetyCoordinator`
   Each slice moves state ownership first, then delegates from the ViewModel; no behaviour change is
   accepted without the existing security tests still passing. The repository splits along the same
   seams (auth identity, jobs, KYC, wallet, safety) once the ViewModel slices are stable.

**Consequences.** Auth state now has a single owner. God-class size remains until the slices land;
this record is the agreed roadmap R01 requires, not evidence that decomposition is complete. Android
compilation is still unverified in this environment, so R01 cannot be declared complete.

---

### D-2026-11-19-1 - Review-Only Assessment Programme; 15 Reviewers; No Source Changes

**Context.** A repository-wide assessment found that the committed HEAD cannot be assumed buildable and contains security-, money- and safety-relevant defects, while master documents (including `prd.json` with 97/97 stories marked completed) assert verified completion. Fixing defects during assessment would destroy the evidence trail and mix remediation with review.

**Decision.**
1. Keep this phase strictly review-only: no edits to `app/src/**`, `functions/src/**`, rules, build files or tests.
2. Create the review programme as new artefacts (`ASSESSMENT_REPORT.md`, `REVIEWER_REGISTER.md`, `REVIEW_PLAN.md`, `PRODUCTION_READINESS.md`, `reviewers/*.md`) and extend `prd.json` additively with phase 18 and 15 `RVW-*` stories at `not_started`.
3. Select 15 reviewers from actual trust boundaries and failure modes (architecture, Android client, backend, data, integrations, auth, security, payments, privacy/KYC, safety/fraud, QA, UI/UX & a11y, performance/reliability, DevOps, documentation); no reviewer without repository evidence.
4. Treat prior `completed` statuses as unverified historical claims, not as evidence. Do not flip them without reviewer reports.
5. Record the readiness verdict as **not production-ready** until P0 gates pass with reproducible evidence.

**Consequences.** The programme adds substantial documentation and 15 pending stories; it deliberately does not reduce the existing story count or rewrite history. Reviewers cannot self-waive gates. Remediation requires separate authorisation and new PRD stories. Any commit that changes the baseline re-opens affected reviewers.

---

### D-2026-11-19-2 - Evidence Standard: Executed, Read, Inferred, Unverified

**Context.** The repository contains extensive historical build/test logs and agent narratives ("BUILD SUCCESSFUL", "111 tests pass", "rules deployed") that cannot be attributed to the current commit, and the assessment environment lacks a JDK/Android SDK.

**Decision.** Every review claim must be labelled `OBSERVED` (a command was run), `READ` (source/doc inspected), `INFERRED` (reasoned from evidence) or `UNVERIFIED` (not executed). Historical logs are not admissible as current evidence. A reviewer who could not execute required checks may not issue `PASS`; the maximum verdict is `UNVERIFIED`.

**Consequences.** Some clearly correct-looking work (e.g. the server money core's 111 passing pure-module tests) is accepted only for what it proves; handler, emulator, device and deployment behaviour remain unverified until executed in a provisioned environment.

---

### D-2026-10-20-1 - AUDIT-A-ARBITRATE: Client Mirrors Dispute Filing to Firestore; `reportedBy` Bound on Create; Resolutions Stay Server-Only

**Context.** The arbitration console reads the admin-gated `getDisputesQueue` callable, which queries the Firestore `disputes` collection — but dispute filing (`submitDispute` → `createDispute`) only wrote the Room row, so the console would always have shown an empty queue at runtime. The established precedent for moving local entities to Firestore is the best-effort `syncJobToFirestore`/`syncProviderToFirestore` mirror. Until now no client wrote the `disputes` collection at all, which is why SEC-006's create rule was permissive.

**Decision.**
1. Add `FirebaseService.syncDisputeToFirestore` (best-effort `set()` with try/catch notice log, guarded `firestore ?: return`) invoked from `SkillsExchangeRepository.createDispute` after the Room insert — Room stays the filer's record; a failed network write never blocks reporting.
2. Map `DisputeEntity` 1:1 onto the document fields the callable/function contract already uses (`disputeReason`, `evidenceUrlsCsv`, `status`, `createdAt`/`updatedAt` epoch ms) so no adapter layer is needed on read.
3. Tighten `firestore.rules` SEC-006 create from `isAuthenticated()` to `isAuthenticated() && request.resource.data.reportedBy == request.auth.uid` — now that clients actually write this collection, a caller can only file disputes reported BY themselves (mirrors the F4 `postedByUid` binding for jobs). Read/update/delete unchanged (parties-or-admin read; admin-only write).
4. **No change to resolution authority**: outcomes are written only by the admin-gated `resolveDispute` callable (Admin SDK); the client never writes `status`/`resolutionType`/`resolutionAmount` after filing.

**Consequences.** The queue has real rows end-to-end on-device; a dispute cannot claim another user as its author. Residual risk: rules are source-only until deployed; JVM tests cannot execute the Firestore write (guarded early-return) so the sync leg is compile/inspection-verified; `reportedUserId` remains client-supplied — it determines who the server notifies on resolution, acceptable only while moderation is admin-driven and the filer's own statement is what's judged.


### D-2026-10-15-2 - AUDIT-A-CONSOLE: In-App Operator Sheet with Explicit Unlock; Backend Claim Stays the Authorizer

**Context.** `adminVerificationQueue` + `submitAdminDecision()` existed with zero Composable call sites, while the backend already enforced the `admin` custom claim server-side (callables + `firestore.rules`) and the project deliberately mirrors no client-side admin boolean (D-2026-10-05-2). Operators needed a first screen without inventing a client role.

**Decision.**
1. Ship the operator surface **inside the Android app** as `AdminVerificationQueueSheet` (drawer entry + bottom sheet), not a separate web portal — smallest traceable step that reuses the existing callable flow.
2. Gate **visibility** client-side with an explicit `isAdminUnlocked` operator toggle (default locked, held in `MainScreen` remember-state, not derived from any profile flag); gate **authorization** server-side only (callable + rules return/decide nothing without the `admin` claim).
3. Render only what the queue payload carries: `nationalIdNumberMasked` + Storage *paths* + timestamps + guarantor state; never fabricate NINs, URLs, or counts.
4. Mirror the server decision vocabulary + feedback floor client-side (`AdminQueueDecisions`: allow-list + 10-char feedback for REJECT/CHANGES_REQUIRED) so malformed taps never leave the device.
5. Leave `prd.json` untouched (all 93 stories `completed`); track this as TASKS.md `AUDIT-A-CONSOLE` + CHANGELOG entry until a PRD schema for audit follow-ups is agreed.

**Consequences.** Non-admin devices see a locked console and an empty queue (backend returns no rows); a field operator can unlock the viewer on a provisioned device, but every decision still passes the backend claim check. Future admin work (`AUDIT-A-ARBITRATE`, `AUDIT-A-SOS`) should reuse this sheet pattern and the explicit-unlock posture.

### D-2026-10-15-1 - Multi-Perspective Persona Gap Remediation Roadmap: Prioritizing Mobile Operational Loops (Phase 1) and Operations Console (Phase 2)

**Context.** A direct source-code audit across Worker, Client, and Administrator user journeys revealed severe gaps between backend readiness and frontend execution:
- **Workers** cannot submit bids or custom price quotes in the UI (the "Submit Quote" CTA in `FreelancerDashboardView` bypasses quoting and jumps to `EscrowAndProofSheet`), cannot withdraw wallet funds (`EscrowWalletSheet` is read-only), and have no turn-by-turn navigation or dispute defense interfaces.
- **Clients** cannot attach photos when requesting jobs (`QuickRequestDrawer` is text/numbers/enum only), cannot pick specific appointment dates/times, cannot specify street addresses or landmarks, and have no UI button to cancel a request or trigger an escrow refund.
- **Administrators** have zero UI surfaces (neither web portal nor mobile Composable) to review National IDs or guarantor submissions (`adminVerificationQueue` and `submitAdminDecision` in `SkillsExchangeViewModel` have zero call sites), no dispute arbitration console, and no live SOS emergency dispatch desk.

**Decision.**
1. **Prioritize Mobile Operational Loops (Phase 1):** The marketplace cannot function if workers cannot quote and clients cannot specify problems or cancel requests. Phase 1 will implement:
   - `QuoteSubmissionSheet` Composable in `FreelancerDashboardView.kt` connected to `SkillsExchangeViewModel.submitQuote(jobId, workerId, proposedRate, proposedHours, notes)`.
   - Media photo attachment picker (up to 3 photos) and date/time window picker in `QuickRequestDrawer.kt`.
   - Street address and landmark fields in `JobPostEntity` / `QuickRequestDrawer.kt`.
   - "Cancel Job" dialog in `JobPipelineKanban.kt` and `MainScreen.kt` using the existing secured `transitionJobStatus` boundary (`CANCELLED`).
   - "Withdraw to Mobile Money" action in `EscrowWalletSheet.kt`.
   - "Navigate to Site" geo-intent launcher on the active job card.
2. **Operations & Admin Console (Phase 2):**
   - Provide an operational interface connecting to the existing Cloud Functions (`getVerificationQueue`, `reviewWorkerApplication`, `reviewGuarantorSubmission`, `setWorkerActivationStatus`, `getDisputesQueue`, `resolveDispute`).
   - Deliver dispute arbitration and SOS emergency monitoring views.
3. **Documentation Alignment:** All six primary project memory files (`prd.json`, `requirement_file.md`, `AUDIT_THREE_PERSONAS.md`, `AGENT_STATE.md`, `TASKS.md`, `CHANGELOG.md`) are updated to reflect these findings and track the 12 resulting action items.

**Consequences.** Future implementation sessions should pick stories from the Phase 1 operational roadmap first to unlock end-to-end user journeys before building the administrative backoffice.

### D-2026-10-14-1 - PAY-002: MTN callbacks are unsigned, so confirmation is pull-to-verify; the invented HMAC webhook gate is removed

**Context.** PAY-001 (commit b740705) safely creates a sandbox collection and records HTTP 202 as `PENDING`. PAY-002 had to answer: how does the platform learn the provider ACTUALLY succeeded, and how may escrow then lock? The repo's legacy `processMoMoWebhook` (SEC-002, 2026-08-27) claimed "HMAC signature verification": it required a `x-momo-signature`-style header HMAC-signed with `MOMO_WEBHOOK_SECRET` and, on success, trusted the body's `status`/`jobId` and flipped escrow — while notifying `postedByResident` (a display string, not a uid).

**Provider contract (verified 2026-10-14 against MTN MoMo API documentation, not guessed):**
- Status of a RequestToPay: `GET /collection/v1_0/requesttopay/{referenceId}` with the platform's own Bearer token + `Ocp-Apim-Subscription-Key` + `X-Target-Environment`. HTTP 200 body carries `status` ∈ {`PENDING`,`SUCCESSFUL`,`FAILED`}, plus `amount`, `currency`, `externalId`, `financialTransactionId`; HTTP 404 = unknown reference. 202 on initiation means "accepted/delivered to the handset" and is explicitly not payment.
- Callbacks: supplied per-request via the `X-Callback-Url` header on the initiation POST; MTN then POSTs the transaction result to that URL. **MTN documents NO callback authentication** — no HMAC, no signature header, no shared secret, no mTLS; only that the callback host must be registered on the developer portal. The SEC-002 HMAC protocol was therefore something MTN could never satisfy: a real callback could only ever be rejected by it, while the (unauthenticated) body underneath it went straight to an escrow flip keyed on attacker-supplied `jobId`.

**Decision.**
1. **Pull-to-verify.** The only authenticity mechanism the provider documentation establishes is the authenticated status re-query with the platform's own credentials. Both user-facing paths (`getMoMoCollectionStatus` callable, `processMoMoWebhook` HTTP) delegate to one pure core (`functions/src/momoConfirmation.ts::verifyAndConfirmCollection`) that re-queries the provider. The callback body is **never trusted**: only a reference hint (`referenceId`, legacy alias `transactionRef`) is read, purely to locate an already-stored transaction; unknown references → 404 with no state created; `status`, `amount`, `jobId` and any user value in the body are ignored. A forged callback can do nothing except cause a legitimate re-check.
2. **The invented HMAC gate (`verifyHmacSignature`, `MOMO_WEBHOOK_SECRET`) was removed** and replaced with the pull-to-verify trigger. (The helper stayed unused-safe either way; `tsc` `noUnusedLocals` is off, but dead security theater is worse than none.) SEC-002's prd note is annotated accordingly rather than erased.
3. **Explicit states:** `PENDING | SUCCESSFUL | FAILED | UNKNOWN | NEEDS_RECONCILIATION`. Malformed provider bodies → `UNKNOWN`; provider unreachable/timeouts → last known `PENDING` recorded with the check error (never `FAILED`, never success); amount/currency disagreement between provider body and STORED transaction → `NEEDS_RECONCILIATION` (no escrow). Terminal states are immutable on this path: replays and contradicting later reports are recorded no-ops.
4. **Escrow locks only via an idempotent Firestore transaction** (`lockEscrowForConfirmedTransaction`) that re-reads the job, re-verifies tx↔job binding, amount, currency and non-funded escrow, and sets `escrowLockedAt` on the transaction so a duplicate confirmation can never double-lock. Worker payout (PAY-003) untouched.
5. **Android displays only backend-published phases** (`MoMoPaymentUiState`/`mapBackendStatusToPhase`, fail-closed to `NEEDS_ATTENTION`); the sheet's modal no longer flips escrow locally — it triggers the trusted callable and renders the polled `getMoMoCollectionStatus` state.

**Consequences / residual risk.** The endpoint is implemented but its end-to-end delivery (portal-registered callback host, real sandbox reference) is **not yet verified in the MTN sandbox** — no deploy was performed. Until then the callable's pull path is the primary confirmation mechanism; the callback is an optional accelerator. Cash-settlement mode in `EscrowAndProofSheet` still sets `FUNDS_LOCKED_IN_ESCROW` locally (a non-MoMo product path, deliberately untouched, flagged as a remaining disconnected path).

**Verification.** `functions`: `tsc --noEmit` clean; `node --test` → 82/82 pass (25 confirmation-core tests + 25 collection tests + 32 guarantor tests), mocked provider only, no real MTN transactions. Android: compile + unit suite results recorded in CHANGELOG.md / AGENT_STATE.md.

### D-2026-10-13-1 - Implementation-truth audit: payment/SMS/push/KYC paths are simulated; PRD status vocabulary is too narrow to express this

**Context.** A source audit of the completed stories found that several are mocked or disconnected:
MoMo collection/release never reach the MTN gateway (empty key server-side; fabricated `PAYOUT-` refs; `disburseEscrowPayoutToWorker` has zero callers), SMS (`SmsNotificationService`) is `Log.d`+`delay`+fake `SENT`, FCM tokens are never registered server-side (`sendTokenToServer` logs only) and event notifications are local-Room only, `GeminiAudioTranscriptionService` returns canned job fields under the placeholder key, the offline queue's `flushPendingActions()` explicitly does nothing, `uploadKycDocument()` has zero callers, and disputes/reports are Room-only with the `resolveDispute`/`getDisputesQueue` callables never invoked from the app. There is no separate admin web dashboard. Meanwhile AUTH-008, SAFE-001 (per-job random PIN), MARKET-001 discovery, and COMM-001 chat ARE functional.

**Problem with expressing this in `prd.json`.** The schema only allows the single status value `completed` (all 93 stories) and `completed_stories[]` lists all 93. There is no `in_progress`, `partial`, or `blocked` value to represent "implemented but mocked/disconnected." Inventing a new status without checking tooling compatibility risks breaking consumers, so per the audit brief it was NOT introduced.

**Options considered:**
1. Bulk-flip the affected stories to a new `partial`/`in_progress` status.
2. Leave `prd.json` statuses as `completed` and record the truth in each story's `notes` plus the tracking docs.
3. Delete or downgrade the stories.

**Decision.** Option 2 (chosen now) + a proposed schema change (deferred, needs owner sign-off):
- Annotated each affected story's `notes` with an `AUDIT 2026-10-13:` block stating the exact evidence and remaining work. Statuses left `completed` to preserve schema/tooling compatibility; this is an honest limitation recorded here rather than a claim that the features work.
- **Proposed (NOT applied):** add `in_progress` and `partial` to the permitted status set, move the simulated/disconnected stories to `partial`, and reconcile `completed_stories[]`. This must be validated against any tooling that reads `prd.json` (AGENTS.md §2) before adoption.

**Unresolved architectural choices (deliberately not decided here):**
- **Payments:** no trusted server-side payment confirmation exists. Need a real MTN MoMo Collection/Disbursement integration (server callable that calls the gateway, plus a genuine HMAC-verified webhook that drives escrow lock/release). Whether funds may be treated as collected before a verified webhook is an open product decision.
- **Sensitive KYC storage:** National-ID byte upload (`uploadKycDocument`) is disconnected from the app; guarantor photos and certificates are real. The authoritative private path and the review access path (Admin SDK, per DEC-011) exist, but the National-ID capture UI→upload wiring is missing.
- **Notification delivery:** no server-side FCM send path; only local Room notifications. Choosing FCM-vs-another channel and implementing token registration + server send is open.
- **Backend authorization for disputes/reports:** the admin callables exist but no admin surface calls them; building the web dashboard (ADMIN-001) is the open work.

**Consequences.** Future agents must read the `AUDIT 2026-10-13:` notes and the AGENT_STATE banner before trusting any "completed" payment/SMS/push/AI/offline/KYC-upload/dispute claim. Nothing was deployed, committed, or implemented in this audit.

**Verification.** `prd.json` re-parses (93 stories, statuses unchanged); `git --no-pager diff --check` clean. No tests run (documentation-only change).


### D-2026-10-11-1 - Phase 17 "Future" stories are completed as documentation; completion claims must be verified, never scripted

**Context.** Seven Phase 17 stories (REG-001, LOC-005, MAP-002, MAP-003, LEGAL-001, LEGAL-002,
AUTH-012) remained `pending`. Their own `goal` fields make them **documentation** stories — each one
explicitly says to record a future requirement *without* implementation, and several say "Do not
implement this now." MAP-003 in addition *depends on* MAP-002 and must be reconciled with device
defect D1.

A prior session nevertheless flipped all seven to `completed` with a `complete_stories.ps1` script
that wrote a single boilerplate note per story, and stopped there: `TASKS.md` still showed them
⏳ Pending, `AGENT_STATE.md` / `CHANGELOG.md` / `DECISIONS.md` were untouched, and MAP-003 was still
absent from `requirement_file.md` — so the acceptance criteria were not actually met anywhere.

**Options considered:**
1. Trust the existing `completed` status and only sync the surrounding docs.
2. Revert `prd.json` to HEAD, verify each story against its real acceptance criteria, then complete
   the genuine work and re-apply the status change.
3. Leave the stories `pending` because "nothing was implemented".

**Decision.** Option 2. Option 1 would have enshrined an unverified completion (AGENTS.md §2/§3/§11).
Option 3 misreads the PRD: for these stories the deliverable *is* the documentation, and the
documentation genuinely now exists — verified line by line against each `acceptance_criteria`, plus,
for AUTH-012, against the live auth/onboarding code (`OnboardingStep`, `restoreSession()`,
`setUserRoleMode()` + `canAccessWorkerFeatures()`).

**Also decided, and material to future sessions:**
- `prd.json` is edited by **exact string replacement with occurrence-count assertions**, never by a
  `ConvertFrom-Json` / `ConvertTo-Json` round-trip. PowerShell's `ConvertTo-Json` rewrites the whole
  file (it dropped the original formatting, changed non-ASCII byte counts and is depth-limited), and
  any note containing a `"` must be written as `\"` or the file stops being valid JSON. The result is
  re-parsed and `git diff --check`ed before the edit is accepted.
- **MAP-003 was genuinely missing from `requirement_file.md`** and is now §13.8. Inserting it shifted
  the later Phase 13 sections (Community → §13.9, SEC-009 → §13.10, LEGAL-001 → §13.11,
  LEGAL-002 → §13.12, AUTH-012 → §13.13). Section numbers in that file are therefore *not* stable
  across commits; refer to these requirements by story ID, not by §-number.
- `complete_stories.ps1` was deleted. Bulk status-flipping scripts are not a permissible way to
  complete a story under AGENTS.md.

**Consequences.** All 93 PRD stories are now `completed` (0 pending). The 4 remaining ⏳/🚫 rows in
`TASKS.md` (D1, D1-PRD, D2, DEV-VERIFY-04) are **not** PRD stories — they are device-test and audit
findings and are unaffected by this decision. The *implementation* work MAP-003 describes (graceful
fallback UI, Contact Worker via COMM-001, `try/catch` around `map.onCreate`, feature flag) is still
entirely outstanding and is explicitly recorded as such, including the unresolved D1 crash.

**Not decided here.** Whether to reopen MAP-001 for the D1 device defect (see DECISIONS DEC-014), and
any actual Maps fallback implementation.

**Verification.** `prd.json` parses as valid JSON (93 stories / 93 completed / 0 pending /
`last_updated` 2026-10-11T15:24:11+03:00), encoding preserved (no BOM, 44 non-ASCII bytes as before),
`git diff --check` clean on `prd.json`, `requirement_file.md`, `TASKS.md`. No Gradle build, no unit
tests and no device run — none is applicable because no source code changed.


### D-2026-10-05-1 - One shared identity resolver; delete the dead device-local phone lookup (F3 remainder)

**Decision.** `FirebaseAuth.currentUser.uid`, filtered by a single shared predicate, is the only
identity the app may use. Concretely, for the F3 remainder:

1. `SkillsExchangeRepository.getCurrentUserByUid()` and `SkillsExchangeViewModel.resolveSessionOwnerId()`
   now both delegate to the shared `resolveAuthoritativeUid()` instead of each carrying its own
   weaker `isNotBlank()`-style check. **Rationale:** two divergent definitions of "valid identity"
   is the root cause of this class of finding - the repository copy silently accepted
   presentation text and the synthetic sentinels, and it backs the app's only current-user source.
   There is now one rule, mirrored in the data and UI layers (mirroring rather than sharing is
   deliberate: the data layer must not depend on the UI layer).
2. The device-local phone lookup (`SkillsExchangeRepository.getUserProfileByPhone()` +
   `UserProfileDao.getUserProfileByPhoneNumber()`) was **deleted**, not just documented. Both had
   zero callers. **Rationale:** the capability to select a local Room profile by a mutable,
   user-editable field is exactly the unbound recovery path this finding targets; leaving a
   documented-but-callable footgun invites reuse. The remote twin
   `fetchUserIdentityByPhoneFromServer()` was **kept** and re-documented, because the tightened
   `users` read rule already denies it for non-owners (it returns null in practice) and because
   removing a migration-support path is out of scope for an identity fix.
3. The newest-row query `UserProfileDao.getCurrentUser()` was **kept**, relabelled
   LEGACY / NON-AUTHORITATIVE. **Rationale:** it has zero production consumers, and the security
   tests depend on it as the trap that proves the newest row answers with the *wrong* account and
   that no identity path consumes that answer. Deleting the trap would delete the evidence.
4. `user_default_me` was **kept** and remains excluded from authenticated identity (shared resolver
   + the `it.id != DEMO_PROFILE_ID` ownership test in `resolveAuthenticatedProfile`). It is still
   the seeded placeholder for demo/display data, and the unique `phoneNumber` index was not weakened.

**Consequence / tradeoff.** `getCurrentUserByUid()` is now stricter than before: a caller passing a
non-uid-shaped string (spaces, parentheses, or a literal sentinel) gets `null` instead of a row.
That is the intended fail-closed behaviour and is covered by tests, but it is a behaviour change at
the data boundary and must be re-checked if a future caller supplies a non-Firebase identifier.

**Not decided here (separate findings, intentionally out of scope).** Per-role/ownership checks on
job status transitions; worker authorization; UI redesign; MoMo phone cleanup. Firestore rules and
Cloud Functions were not modified and nothing was deployed.

**Verification.** `DeviceLocalProfileRecoveryF3SecurityTest` 7/0/0/0; full `:app:testDebugUnitTest`
**21 suites / 162 tests / 0 failures / 0 errors / 0 skipped** (baseline 20/155, +7 tests, no
regressions); `:app:compileDebugKotlin` BUILD SUCCESSFUL; `git --no-pager diff --check` clean. No
device run (no adb device attached).

---

### D-2026-10-05-2 - Job-status authorization is owner-only on the client; worker/admin transitions fail closed

**Context.** C2-2 Fix #6 (F6) made `transitionJobStatus()` attribute `changedBy` to the
authenticated Firebase UID, but the method still relied solely on `JobStatus.isValidTransition()`
for permission: identity (who) was correct, authorization (may they) did not exist. Any signed-in
user could move any other user's job along a structurally legal edge, because the transition graph
knows nothing about the actor.

**Options considered.**
1. *Any authenticated user* — rejected; this is the defect itself.
2. *Any worker* — rejected; there is no trustworthy worker identity on the client (see below).
3. *Job owner for every transition* — rejected; `PAYMENT_RELEASED`-style and worker-progress
   semantics would be wrong, and it would let an owner drive worker states.
4. *Owner for owner-meaningful transitions; fail closed for the rest* — **chosen.**

**Decision.** One central check, `SkillsExchangeRepository.isJobTransitionAuthorized(actorUid, job, target)`
(the pure, actor-explicit form reachable in production via `canCurrentUserTransitionJob` and
enforced inside `transitionJobStatus`), with this matrix:

- **Owner only** (requires `job.postedByUid == actorUid`, F4 authoritative owner identity):
  - setup lifecycle `POSTED / QUOTING / WORKER_SELECTED / ACCEPTED` — from `CLIENT_SETUP_STATES`
  - `CANCELLED` — from `CANCELLABLE_STATES` (never resurrect a finished job)
  - `DISPUTED` — from `DISPUTABLE_STATES`
  - `PAYMENT_RELEASED` — only from `COMPLETED` (client approves escrow release)
- **Fail closed for everyone on the client:** `DRAFT`, `WORKER_EN_ROUTE`, `ARRIVED`,
  `IN_PROGRESS`, `COMPLETED`, `EXPIRED`, and dispute *resolutions* out of `DISPUTED`.

**Rationale for fail-closed on worker/admin states.** The client cannot identify a worker or an
admin, so guessing would be inventing a role model:
- `JobPostEntity.assignedProviderId` is a `ServiceProviderEntity` primary key (`"prov_1"`), **not**
  a Firebase UID; `ServiceProviderEntity` has no UID column. Comparing it to `request.auth.uid`
  would be an identity check against a non-identity column.
- Admin is only the Firestore custom claim `request.auth.token.admin`, evaluated server-side in
  `firestore.rules`; mirroring it into a client boolean would be unverifiable client state.

Worker/admin transitions are therefore authorized at the server boundary (`firestore.rules`
`/jobs/{jobId}` update branch, whose Cases 1/3/4/5 were tightened to name their actor in the same
change), and by `isWorkerKycApproved()` for worker progress. Enabling client-side worker/admin
transitions requires a **product decision**: a worker-UID column on the job (or on the provider
table) plus a client-readable admin claim — that is schema/product work, not a security fix.

**Consequence / tradeoff.** Until a worker UID exists client-side, a worker using the client
cannot mark their own job `IN_PROGRESS`/`COMPLETED` via `updateJobStatus()` — the call returns
false and nothing is written. That is intentional fail-closed behaviour, reported as an open
product decision rather than papered over. `hireProviderForJob()` and the escrow-mapped status
write in `updateEscrowStatus()` were gated the same way (owner-only) because both mutate
`job_posts.status` as a side effect.

**Known vocabulary mismatch (reported, not fixed here):** `updateEscrowStatus()` maps escrow
states to display strings (`"In Progress"`, `"Review"`, `"Completed"`) that are not `JobStatus`
enum members, so that write deliberately bypasses the state machine and is protected by the
direct ownership gate instead. Changing the status vocabulary is out of scope for an
authorization fix.

**Verification.** `JobStatusAuthorizationSecurityTest` 9/0/0/0; full `:app:testDebugUnitTest`
**22 suites / 171 tests / 0 failures / 0 errors / 0 skipped** (baseline 21/162, +9 tests, no
regressions); `git --no-pager diff --check` clean. `firestore.rules` change is source-only —
**not deployed** (consistent with the F4/F5 rules changes, which are also undeployed).

---



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

> **Superseded 2026-11-20 (R15).** The pinned `D:/` JDK paths this decision rests on were removed from
> `gradle.properties` in R01, because they made a clean clone fail on anything but one Windows machine.
> The decision's *reasoning* stands — a "no JDK" blocker must be tested, not assumed — but its
> *premise* does not: there is now no pinned JVM, and the remediation environment has no JDK at all.
> See D-2026-11-20-14 for the rule that replaced it.

### D-2026-11-20-15 - Document existing money and time representations

**Decision and current limitations.** Room's wallet ledger and withdrawals use INTEGER `amountMinor`
(hundredths of a UGX). The local cashout calculation reads ledger entries, not legacy profile balances.
This is not a claim that all payment, refund, server, or provider paths use this representation.
Sixteen other money fields and two `walletBalanceDisplay` fields remain REAL in Room v30; `Money.kt` lists
them. Their meanings and writers differ: job creation uses `JobMoney.split`, quote submission directly
multiplies rate by hours, and demo jobs have literal values. The two legacy balance fields default to
185.0; a profile setter exists. They are not established to mirror the ledger and must not authorize
cashouts. This exclusion is established for the local cashout path, not for all money workflows:
`FirebaseService` sends `job.grandTotal` to Firestore, and `functions/src/momoCollection.ts`
validates and uses Firestore job pricing. These REAL values cannot honestly be called merely display
values or said never to participate in payment decisions.

**Time.** Database event timestamps such as `createdAt`, `updatedAt`, and `createdTimestamp` are
`Long` epoch milliseconds when populated by `System.currentTimeMillis()`. This is not a universal
time representation: presentation fields such as `timestampText` and scheduling strings also exist.
New persisted event instants should use epoch milliseconds; conversion to display/time-zone-specific
values belongs at the edge.

**Guard.** `scripts/check_money_representation.mjs` flags unclassified money-shaped schema
columns and direct Kotlin `walletBalance` and `walletBalanceDisplay` member access in any layer. It is a name-based heuristic,
not a proof of authorization, arithmetic, SQL access, time semantics, or end-to-end payment behavior.
It allows the listed legacy REAL columns. Tests of actual cashout behavior
and review of SQL and remote flows remain required.

**Migration 29→30.** Rename the legacy balance columns to `walletBalanceDisplay`, preserving their
values and descendants. The migration snapshots and restores dependent rows inside the Room migration
transaction because dropping either parent can cascade. It needs temporary storage proportional to those
rows. Compiler-generated schema and the migration chain tests are required before release. A subsequent
conversion of the remaining REAL amounts is proposed as `MIGRATION_30_31` (not implemented or
reserved). It requires explicit rounding, preservation of legacy values, and provider/Firestore
compatibility. Until that work is implemented and verified, the single-money-representation criterion
remains unmet.

### D-2026-11-20-13 - One story vocabulary, and DONE is only reachable through evidence

**Context.** R15 found `prd.json` carrying three ways to say a story was finished (`completed` 112,
`COMPLETED` 16, `DONE` 9) and no evidence rule at all. The 112 `completed` stories had been closed by an
earlier agent, mostly with `verified_by: code_inspection`; this programme then watched that exact kind of
claim fail under review for escrow release (R02), account deletion (R06), the doorstep PIN (R07), the
offline queue (R13) and reports (R10).

**Decision.** One vocabulary — `DONE`, `IN_PROGRESS`, `VERIFICATION_PENDING`, `NOT_STARTED`, `UNVERIFIED`
— enforced by `scripts/check_story_status.mjs`. `DONE` requires a `verification_note` beginning
`evidence:` and long enough to be a record. `UNVERIFIED` is a real state with a required explanation: it
means "an earlier agent marked this complete and this programme did not re-verify it", so nobody is
credited with it. All 112 `completed` stories were moved to `UNVERIFIED`; the 25 remediation stories that
have a round record were kept at `DONE` with that record as their evidence.

**Consequences.** The completion count dropped from 137 to 25 in one commit — that is the point, not a
regression: the previous number was a claim, and this one is a record. A story can be re-promoted to
`DONE` by writing evidence, which is cheap when the evidence exists and impossible when it does not.

### D-2026-11-20-14 - Unverified is a first-class outcome, and CI is the compiler of record

**Context.** Half of this programme's findings could not be verified in the environment that fixed them:
no JDK or Android SDK, no Firebase project, no device, no provider sandbox. An earlier decision
(D-2026-09-27-1) had also taught agents to distrust "no JDK" reports, which is correct advice on a
machine with a pinned JDK and false on one without.

**Decision.**
1. A change that cannot be compiled, run or deployed here is recorded as implemented-and-`UNVERIFIED`,
   with the reason, and the evidence comes from the CI run rather than from the author.
2. `AGENTS.md` states the environment truth: no local JDK, GitHub Actions is the compiler of record, and
   a missing JDK **is** a reportable blocker. `scripts/check_onboarding_docs.mjs` refuses a doc that
   claims setup is unnecessary or points at a Windows path.
3. No document may state a runtime, latency, coverage or provider result that a command in this
   repository cannot produce.

**Consequences.** The programme's status is deliberately pessimistic: 0 domains pass, 15/15 reviews
FAIL at review time, and every round ends with a "Not claimed" list. That list is the product of this
decision, not an apology for it.

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

## 2026-09-29

### [2026-09-29] DEC-009 Admin Guarantor Review — Authorization Anchor and Single Gate

**Context**: REG-002 (guarantor verification) had no admin read/decide path at all (post-PRD gap G2). The existing admin callables each re-implemented their own check (`context.auth?.token?.admin === true` inside `isAdmin`), and `firestore.rules` uses `request.auth.token.admin == true`. An admin endpoint that mints signed URLs for private identity photographs is the most sensitive surface in the project, so the gate had to be unambiguous and identical everywhere.

**Options considered**:
1. Trust a role/claim supplied in the callable payload (e.g. `data.role === "admin"`).
2. Read an `admins/{uid}` Firestore document on every call.
3. Require the boolean `admin === true` **custom claim of the verified ID token**, checked in one shared helper.

**Decision**: Option 3. `functions/src/guarantorReview.ts` owns `checkAdminCaller(context)`, which returns `unauthenticated` when there is no verified uid and `permission-denied` unless `auth.token.admin === true` (strictly boolean — `"true"`, `1`, `{}` and `role: "admin"` all fail). The legacy `isAdmin()` in `index.ts` now delegates to it, and the new `authorizeAdmin()` throws the matching `HttpsError`. Client-supplied roles are never read.

**Rationale**: It mirrors the anchor already deployed in `firestore.rules`, so server code and rules cannot drift; it costs no extra Firestore read; and a caller cannot choose their own authorization level because the claim only exists on a token Firebase Auth signed.

**Consequences**: Promoting an admin stays an operational task (set the custom claim, refresh the token), not a code change. Every future admin endpoint must use `authorizeAdmin`/`checkAdminCaller` instead of re-implementing the check. Anonymous and non-admin callers stay distinguishable, which keeps client error handling honest.

---

### [2026-09-29] DEC-010 NIN Handling in Admin Payloads — Masked by Default, Explicit Opt-In for the Full Value

**Context**: `getPendingVerificationQueue` returned every worker's full `nationalIdNumber` inside a list payload that is polled, cached and screenshotted, while the admin detail view genuinely needs the digits to compare them against a photograph. A NIN is a third party's identifier (data-protection exposure) as much as the worker's.

**Options considered**:
1. Keep returning full NINs everywhere (status quo).
2. Never return the full NIN (an admin would compare only the last two digits — impractical for a real review).
3. Mask by default, releasing the full value only on an explicit admin-only detail request.

**Decision**: Option 3. `maskIdentifier` (a TypeScript twin of `GuarantorValidators.maskIdentifier` in the app) keeps only the last two characters. The queue emits `nationalIdNumberMasked` and no `nationalIdNumber` key at all; `projectGuarantorForAdmin` omits the `nationalIdNumber` property unless the caller passes `includeFullNin: true` to `getGuarantorSubmission`.

**Rationale**: The common case (finding work to do) carries no identifier; the exceptional case (reviewing one submission) is one audited call away and is never cached in a list. Sharing the validator's masking format keeps client and server displays identical.

**Consequences**: An admin UI must opt in per submission rather than receiving NINs wholesale. Tests assert the property is **absent**, not merely empty, when it was not requested, so a future refactor cannot silently reintroduce it.

---

### [2026-09-29] DEC-011 Private Verification Photographs — Server-Minted Short-Lived Signed URLs

**Context**: The guarantor photographs live under `users/{uid}/guarantors/**`, which `storage.rules` restricts to the owner (`request.auth.uid == userId || request.auth.token.admin == true`). No admin client exists, and making the prefix readable "for admins" would risk exposing every worker's documents to anyone whose client enforces access incorrectly.

**Options considered**:
1. Loosen `storage.rules` so admin-claimed clients read the files directly.
2. Copy images into an admin-only prefix, or store download tokens in Firestore.
3. Mint short-lived signed URLs server-side, only for paths that pass an ownership check.

**Decision**: Option 3. `collectPrivatePhotoPaths` gathers at most the five photographs of the reviewed submission, each only after `isPrivateGuarantorPhotoPath(path, workerId)` proves it sits inside `users/{workerId}/guarantors/<file>` (absolute URLs, `..`, leading slashes, empty segments and foreign worker prefixes are all refused). `mintPrivatePhotoUrls` then creates v4 read URLs with a 15-minute default capped at 60 minutes, reporting failures as fixed reason codes (`OUTSIDE_PRIVATE_PREFIX`, `MISSING`, `NOT_MINTED`, `URL_MINT_FAILED`, `BUCKET_UNAVAILABLE`).

**Rationale**: Storage rules stay exactly as strict as before (the prefix remains owner-only); no long-lived token is ever written into a document; a tampered submission cannot steer an admin's URL into another worker's folder; and the fixed codes avoid echoing bucket paths or half-built signatures into a response.

**Consequences**: An admin can only view a photograph while holding a fresh URL from an authorized call, so the UI must re-request rather than bookmark. If review volume ever grows, the TTL default is the single knob to revisit — it is already bounded by tests.

---

### [2026-09-29] DEC-012 Guarantor Queue Without a Composite Firestore Index

**Context**: The queue merges two collections. Combining `status in [...]` with `orderBy("submittedAt")` requires a composite index, and this repository has **no** `firestore.indexes.json`, so such an index exists only if somebody created it by hand in the console. On a fresh project such a query fails with `FAILED_PRECONDITION`, breaking the whole queue.

**Options considered**:
1. Query with `orderBy` and depend on a hand-created console index.
2. Introduce `firestore.indexes.json` as part of this change.
3. Query with the `in` filter only, and sort/limit in code.

**Decision**: Option 3. The new `guarantor_submissions` query keeps `where("status", "in", GUARANTOR_REVIEWABLE_STATUSES)` and `limit`, but drops `orderBy`; `buildQueueRows` sorts by the earliest outstanding timestamp and applies the limit.

**Rationale**: The queue must not depend on unversioned console state, and correctness is unaffected because the authoritative ordering already happens in code. (The older `worker_kyc` query keeps its `orderBy` — it predates this decision and is left untouched.)

**Consequences**: The read is slightly larger when a backlog is very large. Adding `firestore.indexes.json` with a composite index on `guarantor_submissions` (`status`, `submittedAt`) is an optional future improvement, recorded as such in `AGENT_STATE.md`.

---

### [2026-09-29] DEC-013 Security Core Kept Free of Firebase Imports; Tests Run Against the TypeScript Source

**Context**: A previous session recorded a **false** verification ("`npx tsc` exits 0") because `npx` resolved to the placeholder `tsc` npm package while `node_modules/typescript` had been gutted. The project needs a verification path that is cheap, honest and hard to fake.

**Options considered**:
1. Emulator-based integration tests for the callables.
2. Jest/mocha with a build step and mocked `firebase-admin`.
3. Keep the decision logic in an import-free module and test it directly with `node --test`, which strips the types from the `.ts` file (native in Node 22).

**Decision**: Option 3. `guarantorReview.ts` imports neither `firebase-admin` nor `firebase-functions`; it is pure functions over loose data. `functions/test/guarantorReview.test.mjs` imports it straight from source and runs via `node --test test/guarantorReview.test.mjs` (wired as `npm test`). `functions/package.json` now declares `@types/express` and `@types/node`, which its `build` script always needed.

**Rationale**: The whole security contract (authorization, masking, path guard, write allowlist, validation, queue semantics) is assertable in about a second with no emulator, device or Firebase project — while `npm run build` (the real `tsc`) still proves the module compiles with the rest of the tree.

**Consequences**: Anything security-relevant must stay in the import-free module and orchestration in `index.ts` should stay thin. Never record a type-check result produced by `npx tsc` in this repository — run the compiler by path (`node node_modules\typescript\bin\tsc`) and require no output plus exit 0. Emulator/live-project verification is still required before this gap can be called closed.

---

### [2026-09-29] DEC-014 Real-Device Verification Is Mandatory Before a Map/UI-Visible Story Is Called Complete

**Context**: The 2026-09-29 device run on a Samsung SM-A025F (Android 12 / API 31) found that
**MAP-001** — marked `completed` with `verified_by: "code_inspection"` — does not work at all:
tapping Map View throws `IllegalStateException: API key not found` at
`InteractiveMapView.kt:98` and kills the process 3 times out of 3, because
`app/src/main/AndroidManifest.xml` has no `com.google.android.geo.API_KEY` `<meta-data>`. Code
inspection, `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` all pass with that defect
present. The same run *also* proved something no unit test had: `MIGRATION_18_19` really executes on
a device that was updated rather than reinstalled (`user_version = 19` read from the pulled DB).

**Options considered**:
1. Leave the workflow as it is — verification by reading code and compiling.
2. Reopen `MAP-001` in `prd.json` immediately in this verification session.
3. Keep the PRD as the master plan untouched, record the failed verification prominently, and let
   the story be reopened by the session that actually fixes it.

**Decision**: Option 1 is rejected: **a story whose acceptance criterion describes something a user
can see or tap may only be marked `completed` after it has been observed on a device or emulator.**
Options 2/3 were combined as **3** for this session: `prd.json` was **not** modified (AGENTS.md §2 —
the master plan is not edited by a verification task that implements nothing), while AGENT_STATE.md,
CHANGELOG.md and `_device_test/DEVICE_TEST_REPORT.md` all record that MAP-001's criterion fails and
that reopening it is the required next step.

**Rationale**: A status field that a compile check can satisfy is not evidence. The cheapest honest
gate for UI-visible behaviour is the device that is already plugged in: install, tap, screenshot,
`logcat`. Equally, a verification task must not silently rewrite planning state it did not
implement — the reopen should happen in the session that lands the fix, so status and evidence move
together.

**Consequences**: Every future map/navigation/UI story needs a device step in its verification list,
and `verified_by: "code_inspection"` is now treated as *not verified* for UI-visible behaviour.
Install steps must check `df /data` first and prefer `adb shell pm trim-caches` (non-destructive)
over deleting user data or uninstalling — the first install of this run failed for space, not for
signatures. DEX/APK copies made for diagnosis go in `_device_test/` and are safe to delete; they are
not build outputs.

---

### [2026-09-30] DEC-020 Google Maps Is an Optional Visualization Layer — Graceful Fallback Required on Active-Job Screen

**Context**: Device testing on 2026-09-29 (Samsung SM-A025F, Android 12) proved that the existing
`InteractiveMapView.kt` crashes the process (`IllegalStateException: API key not found` at line 98)
whenever the Maps tab is tapped, because no `com.google.android.geo.API_KEY` `<meta-data>` element
exists in `AndroidManifest.xml`. The crash is unguarded. Additionally, the current design shows a
Map View tab during ordinary worker browsing — not only during active jobs — which is inconsistent
with the intended usage pattern captured in MAP-002.

**Future requirement added as MAP-003** (phase 17, status `pending`): when eventually implemented,
Maps must only appear on the active-job screen (after a client has accepted/hired a worker) and must
degrade gracefully when unavailable.

**Decision**: Treat Google Maps as a **visualization layer only**, not as a requirement for any part
of the job lifecycle. Specifically:

1. **Isolation**: Maps SDK calls must be confined to the active-job screen. They must not appear
   during ordinary worker browsing, provider discovery, quoting, or any other normal app navigation.
2. **Graceful fallback**: Any Maps failure (missing API key, billing disabled, SDK init exception,
   network failure, map load failure, location permission denied, or any other Maps-related error)
   must produce a clear fallback UI — not a crash, freeze, or infinite loading screen.
3. **Contact Worker via COMM-001**: The fallback UI's Contact Worker action must reuse the existing
   Firestore real-time chat + phone-masking mechanism (COMM-001), not expose a raw phone number and
   not create a second communication system.
4. **Worker GPS independence**: Worker GPS location collection (used by existing LOC-001/LOC-003
   features) must remain independent from the Google Maps SDK. Maps failing must not stop GPS.
5. **Job lifecycle independence**: All job flows (acceptance, start, status updates, arrival PIN,
   completion, notifications, reviews, payment/escrow, disputes) must work even when Maps is
   unavailable.
6. **Secure key management**: The Maps API key must be delivered via `manifestPlaceholders` / `.env`,
   never hardcoded in Kotlin or committed to source control.
7. **Feature abstraction**: A feature flag or configuration abstraction should allow Maps to be
   enabled or disabled without redesigning the active-job system.

**Options considered**:
1. Make Maps a hard dependency (current broken state) — rejected: causes crash.
2. Remove Maps entirely — rejected: the intent is to show it when a job is active and the key is
   configured.
3. Optional visualization layer with fallback — chosen: lowest coupling, preserves all job
   functionality, unblocks release even without a Maps key.

**Rationale**: A crashed app cannot complete a job. The job lifecycle (acceptance → active → arrival
PIN → completion → payment) is the core product value. Maps is a UX convenience — helpful when
available, but its absence must never block a worker from being paid or a client from managing
their job. The COMM-001 reuse decision avoids duplicating the masked-communication security model.

**Consequences**: When MAP-003 is eventually implemented:
- `InteractiveMapView.kt` must wrap `map.onCreate()` in a try/catch and set a fallback state.
- A new `ActiveJobScreen` (or updated existing screen) must show the fallback UI when Maps is
  unavailable.
- The manifest must include a `manifestPlaceholders`-backed `MAPS_API_KEY` entry.
- No new Maps dependency is needed (MAP-001 already added it).
- No existing story status is changed by this decision record.

**Stories affected**: MAP-003 (new, pending). MAP-002 (pending — usage-minimization strategy that
MAP-003 depends on). MAP-001 (completed, but D1 device defect means its acceptance criterion has
not been satisfied on hardware — DECISIONS.md DEC-014).

---

### [2026-09-30] DEC-021 D1 Defect Resolution — InteractiveMapView Crash & Safe Key Placeholder Wiring

**Context**: Real-device testing on Samsung SM-A025F (Android 12) revealed critical defect D1:
tapping the Map View tab crashed the app with `IllegalStateException: API key not found` at
`InteractiveMapView.kt:98`. Root cause: `AndroidManifest.xml` lacked any `<meta-data>` entry for
`com.google.android.geo.API_KEY`, while `MapView(ctx).onCreate(null)` was called unconditionally.
Furthermore, secrets-gradle-plugin 2.0.1 generates invalid Java in `BuildConfig.java` (`String MAPS_API_KEY = ;`)
if the key is an empty string in `.env`.

**Decision**:
1. Follow the established `GEMINI_API_KEY=MY_GEMINI_API_KEY` convention: configure `MAPS_API_KEY=DEFAULT_MAPS_API_KEY`
   in `.env.example`, `.env`, and `manifestPlaceholders["MAPS_API_KEY"] = "DEFAULT_MAPS_API_KEY"` in `app/build.gradle.kts`.
2. Add `<meta-data android:name="com.google.android.geo.API_KEY" android:value="${MAPS_API_KEY}" />` to `AndroidManifest.xml`.
3. In `InteractiveMapView.kt`, gate `MapView` creation on `isMapsApiKeyPresent(context)`. The guard checks that the key
   is not null/blank, does not start with `${`, and does not equal placeholders (`DEFAULT_MAPS_API_KEY`, `MY_MAPS_API_KEY`,
   `YOUR_KEY_HERE`). If absent or default placeholder, render `MapUnavailableFallback` immediately without touching `MapView`.
4. Wrap `map.onCreate(null)` in `try/catch` defense-in-depth so any SDK initialization error (e.g. missing Google Play Services)
   sets `mapsInitError = true` and degrades cleanly to the fallback UI instead of crashing the process.

**Verification**:
- `compileDebugJavaWithJavac`: PASSED cleanly.
- `testDebugUnitTest`: PASSED 75/75 tests across 9 suites (0 failures, 0 errors).
- `assembleDebug`: PASSED, outputting debug APK (35,259,265 bytes).

---

### [2026-10-04] DEC-022 Session Restore Trusts FirebaseAuth Only — the Cached Session Is Never an Identity

**Context**: C2-2 finding F2. `restoreSession()` reconstructed an authenticated session from
DataStore: after the AUTH-008 expiry and device-id checks it read `SessionManager.getUserId()` and
then resolved the profile with the legacy newest-row query (`repository.currentUser.first()`).
The cached UID was therefore an *identity source* rather than a cache. A UID left behind by a
previous Firebase account could survive a sign-in change and attribute that account's profile,
phone number and worker approval to the newly signed-in user; with Firebase signed out, stale
device state still produced a "signed-in" app; and a placeholder (`user_default_me`) or synthetic
(`"unknown"`) id could be adopted as the session owner.

**Options considered**:
1. Keep the cached UID as the session identity and merely re-validate the Room row (status quo).
2. Trust FirebaseAuth when it has a user, but keep restoring the cached session when Firebase has
   no user (offline-tolerant recovery).
3. Treat `FirebaseAuth.currentUser.uid` as the only identity; discard the cached session whenever it
   does not match, and fail closed to signed-out/guest when Firebase has no authenticated user.

**Decision**: Option 3, encoded as two pure companion functions plus a sealed result type so the
policy is unit-testable without Android dependencies:
- `decideRestoreIdentity(cachedUid, firebaseUid) : RestoreIdentity` — `None` unless Firebase reports
  a usable, non-placeholder UID; `Authoritative(uid, cachedUidTrusted)` where `cachedUidTrusted` is
  true **only** when the cached UID is non-blank, not `user_default_me`, and equal to the Firebase
  UID.
- `resolveRestoredProfile(authenticatedUid, row)` — a row is eligible **only** when `row.id ==
  authenticatedUid`; otherwise `null` (no newest-row, no placeholder, no substitution).
- `restoreSession()` fails closed (`clearSession()` + signed-out) when no authoritative UID exists;
  clears the stale cache when the cached UID does not match; resolves the profile by UID
  (`getCurrentUserByUid` → durable record → fail-closed `resolveAuthenticatedProfile`, reusing
  cached descriptive fields only when the cached UID matched); and rewrites the DataStore session
  only when the cached identity was stale.
- The `init` block's role sync reads the UID-keyed row too, so it cannot undo the restore.

**Rationale**:
- FirebaseAuth is the only authority that can prove which account owns the device session; device
  storage is attacker/stale-state reachable and must never mint an identity.
- Matching cache ⇒ no session rewrite, so a mere relaunch cannot extend the AUTH-008 7-day expiry.
- Failing closed on "no Firebase user" means a cached session can no longer resurrect a signed-out
  account; the legitimate offline/room-wiped recovery path (`C1` branch + AUTH-006 durable record)
  already exists and stays uid-keyed.

**Consequences**:
- A user who is genuinely offline at launch is signed out (guest/onboarding) until Firebase has a
  user again — accepted deliberately over silently trusting stale device state.
- The legacy newest-row `getCurrentUser()` remains for display consumers and is now explicitly
  **not** a restore fallback; it must be swept from identity call sites under finding F7.
- C2-2 is *not* complete: F3–F7 (F7 call-site sweep, wiring `currentUserByUid` to the UI,
  `getUserProfileByPhone` device-local lookup, removing the seeded `user_default_me` row) remain
  open, and no on-device verification was possible (no adb device attached).

**Verification**: `RestoreSessionIdentitySecurityTest` (5 cases A–E) + 2 repository-boundary cases in
`CurrentUserByUidRepositoryTest`; full suite 126 tests / 0 failures / 0 errors; `assembleDebug` PASSED.
(Follow-up 2026-10-04: the F7 sweep in DEC-023 migrated the legacy newest-row consumers; the F3
remainder plus F4, F5 and F6 are still open.)

---

### [2026-10-04] DEC-023 The Newest Room Row Is Never an Identity — F7 Sweep Policy and the Deliberate SOS Fail-Closed Change

**Context**: Audit finding F7. `UserProfileDao.getCurrentUser()` answers "the most recently updated
real profile row on this device" (`ORDER BY updatedAtTimestamp DESC LIMIT 1`) and was still reachable
from security-sensitive code — the worker authorization gate, the mode switch, worker location,
account deletion, profile / backup-contact / avatar writes, guarantor drafts+submissions, training
rows, quotes/proposals, notifications, disputes, ratings, reports, mark-all-notifications-read, the
logout identity mirror, the SOS name/phone payload, and the drawer/worker/guarantor UI. A device that
had ever held another account (or a sign-in change) could therefore attribute a write, an approval or
a notification to the wrong account.

**Options considered**:
1. Delete the newest-row query and force every consumer to the UID-keyed path in one step.
2. Keep the query and replace each consumer individually with `FirebaseAuth.currentUser.uid` →
   `getCurrentUserByUid(uid)` (fail closed on null), leaving genuinely unrelated findings alone.
3. Leave the query as-is and rely on the F2 restore fix alone.

**Decision**: Option 2, applied strictly (C2-2 Fix #3).
- Identity rule: `FirebaseAuth.currentUser.uid`, trimmed; blank/whitespace/`user_default_me` ⇒ `null`
  and the operation is ABORTED (`resolveAuthoritativeUid` in the ViewModel, and the same rule in the
  repository because the data layer must stay decoupled from the UI).
- Every migrated consumer then resolves its own row with `getCurrentUserByUid(uid)` /
  `getCurrentUserById(uid)` — exact `id == uid`, no fallback.
- The legacy `viewModel.currentUser` StateFlow was **deleted**, so re-introducing a newest-row identity
  source is now a compile error rather than a silent regression. `SkillsExchangeRepository.currentUser`
  survives only as documented legacy for the un-migrated **F5** chat-sender attribution and the
   **F6** job-status `changedBy` attribution.
- `currentUserByUid` now uses `SharingStarted.Eagerly`: the authorization gates read `.value`
  synchronously, and a subscription-gated flow would have returned the `null` initial value before the
  UI subscribed — silently denying legitimate operations.
- SOS (`triggerEmergencySos`) now takes name/phone from the authenticated row only; when no
  authenticated profile exists the SMS is NOT dispatched (the in-app SOS flag is still raised). The
  fabricated `"Worker"` / `"+256 700 000 000"` fallback is removed.
- F4 (job authorship), F5 (chat sender) and F6 (job-status `changedBy`) are left untouched on purpose
  so this change stays scoped; F6 keeps its newest-row read *explicitly*, with an inline note.

**Rationale**: A timestamp is not an identity. The gate, the attribution fields and the ownership of a
write must all derive from the authenticated UID, otherwise a stale or secondary account row can grant
privileges (an older "APPROVED + activated" worker row could even outrank the current account's row if
it were newer) or shift blame/earnings to the wrong person. Moving all consumers in one audit pass
prevents the "half-migrated" state where some writes are UID-keyed and others are not.

**Consequences**:
- Any operation invoked with no Firebase user now aborts (fails closed) instead of quietly acting as
  "whoever is newest" — including account deletion, ratings, disputes, reports, guarantors, training,
  quotes, proposals, notifications, mark-all-read and both SOS paths.
- A legitimate user whose Room row is missing (Room wiped) sees no profile until the normal UID-keyed
  recovery re-creates it; worker features stay denied until then (correct: `trainingCompleted` /
  `workerKycStatus` live on that row).
- No SOS SMS is sent without an authenticated profile — accepted deliberately: sending a stranger's or
  a made-up number and name to emergency contacts is worse than sending none.
- Remaining legacy surface is now explicit and tracked: F4/F5/F6 plus
  `WorkerTrainingDao.getCurrentUserTraining()` (`SELECT * FROM worker_training LIMIT 1`, display-only)
  and the device-local `getUserProfileByPhone`.
- The seeded `user_default_me` row was NOT deleted: `CurrentUserByUidRepositoryTest` /
  `UserProfilePersistenceTest` still assert the DAO refuses it, and the seed is what those
  regressions are written against; removal is a separate, audited step.

**Verification**: new `app/src/test/java/com/example/data/LegacyCurrentUserSweepSecurityTest.kt`
(5 Robolectric cases: with Firebase UID-A + Room UID-A older + Room UID-B newer, resolution and the
worker gate use UID-A and never UID-B; missing/blank/placeholder Firebase UID fails closed while a real
approved row exists; trimming + placeholder rejection in both layers; SOS contact selection).
Full suite 17 suites / 131 tests / 0 failures / 0 errors / 0 skipped; `compileDebugKotlin` and
`compileDebugUnitTestKotlin` BUILD SUCCESSFUL. No on-device verification was possible (no adb device
attached), so C2-2 remains ⏳.

### [2026-10-04] DEC-024 A Job Has an Owner UID; A Display String Is Not an Owner — F4 `postedByUid`

**Context**: Audit finding F4. `JobPostEntity` carried **no owner identity at all**. `addJobPost()`
wrote `postedByResident = "You ($district)"` — presentation text — so no code path could answer
"is this my job?" against something stable. The escrow Cloud Function made this concrete:
`releaseEscrowToWorker` compared `postedByResident` / `postedBy` against `context.auth.uid`. A display
string can never equal a Firebase uid, and the client never wrote a field named `postedBy`, so the
escrow ownership check had no reliable owner to compare against at all.

**Decision**:
- `postedByUid: String?` is the authoritative, immutable owner — `FirebaseAuth.currentUser.uid`.
  `postedByResident` is retained unchanged as DISPLAY-ONLY. Neither replaces the other.
- `addJobPost()` resolves the uid through the existing `resolveAuthoritativeUid` (no competing
  identity source was introduced) and **returns `null` without persisting anything** when there is no
  authoritative uid. A job with no provable owner can never be cancelled, edited or attributed, so
  refusing to create it is safer than creating an unattributable one.
- Ownership reads (`getMyJobs`, `isJobOwnedByCurrentUser`) compare uid-to-uid only. There is no
  display-string and no newest-Row fallback in either.
- `MIGRATION_19_20` (DB 19 → 20) adds the column **nullable with no default**. Historical jobs are
  left with an unknown owner rather than back-filled from `postedByResident` or from the newest Room
  profile — guessing would hand real jobs (and their earnings and liability) to a random account.
  The column is nullable for exactly that reason; new jobs always have it set.
- `resolveAuthoritativeUid` was hardened in both layers to reject values containing whitespace or
  brackets (i.e. presentation text) and the literal sentinels `unknown`/`system`/`anonymous`/
  `guest`/`none`.
- Firestore: minimal rules only — `create` binds `postedByUid` to `request.auth.uid`, and `update`
  requires it to be unchanged, making ownership immutable in every state-machine branch. **Not
  deployed.**

**Rationale**: Authorship is a security fact, not a label. Attributing a job by a human-readable
string that the same UI writes as `"You ($district)"` means ownership cannot be enforced at all, and
the escrow check demonstrates exactly that. Making the uid immutable at creation and comparing it
everywhere turns "whose job is this" into a question with a single, verifiable answer.

**Consequences**:
- Legacy jobs have no owner, so nobody can manage them through the new predicates. They remain
  visible and readable; only ownership-gated actions are unavailable. Deliberate.
- Legacy Firestore job documents lacking `postedByUid` will be rejected by the new `create` rule and
  fail the Cloud Function's owner check — fail-closed by design. Re-homing historical cloud jobs is
  a separate, unaddressed decision.
- `momoPhoneNumber = "+256 772 198 420"` remains hardcoded in `addJobPost`; it is a payment-contact
  issue, not an authorship one, and was deliberately left untouched.

**Verification**: new `app/src/test/java/com/example/data/JobAuthorshipF4SecurityTest.kt` (Robolectric,
7 cases A–F) covering uid-stamped creation, fail-closed creation with no Firebase user, display text
never becoming ownership, the uid_A/uid_B-newer-row trap, uid-keyed ownership queries, and the
unknown-owner legacy job; plus a new `migration nineteen to twenty is additive and registered` case
in `GuarantorVerificationPersistenceTest`. Full suite 18 suites / 139 tests / 0 failures / 0 errors /
0 skipped; `compileDebugKotlin` BUILD SUCCESSFUL. **No on-device verification was possible** (no adb
device attached) and neither the rules nor the Cloud Function was deployed.

---
### [2026-10-04] DEC-025 A Chat Message Has an Author UID — F5 `senderId`

**Context**: Audit finding F5. `sendChatMessage()` set `senderId = currentUser?.id ?: "unknown"` from the
legacy newest-Room-row query. Two problems, one of them larger than the reported one:
1. On a device that had held another account, chat authorship was attributed to the wrong person, and
   `"unknown"` is not an identity.
2. **`ChatMessageEntity` had no `senderId` column at all.** Room persisted only `senderName` (display)
   and `isFromUser` (a client-supplied boolean). Authorship was not stored locally, so it could not be
   enforced, and the Firestore read path silently **discarded** the `senderId` it received.

**Decision**:
- `ChatMessageEntity.senderId: String?` is the authenticated author (`FirebaseAuth.currentUser.uid`);
  `senderName` and `isFromUser` remain presentation/bubble-alignment fields and are never identities.
- `sendChatMessage()` returns `Boolean` and resolves the author through the **existing**
  `resolveAuthoritativeUid` helper — no second, competing identity source — and returns false
  **without persisting or sending anything** when there is no authoritative uid.
- `MIGRATION_20_21` (DB 20 → 21) adds the column **nullable with no default**. Pre-F5 messages and
  system/notification rows keep an **unknown author** rather than being back-filled from
  `senderName`. `isMessageAuthoredByCurrentUser` therefore returns false for them.
- The Firestore read path now preserves `senderId` instead of dropping it.
- `firestore.rules`: the `jobs/{jobId}/messages` subcollection had **no rule at all** (Firestore
  denies by default). A minimal subcollection rule binds `create` to `request.auth.uid`, allows
  `update` only by the original author and only with an unchanged `senderId`, and denies `delete`.
  **Not deployed.**

**Rationale**: Message authorship is the basis for "did I write this?", for moderation and for blame.
Deriving it from a timestamp-ordered local row made it both wrong under account switching and
unenforceable; the missing Room column meant there was nothing to enforce even in principle. Writing
the uid at creation and comparing uid-to-uid makes authorship a verifiable fact, and binding it in
rules stops the client from asserting someone else's identity.

**Consequences**:
- Pre-F5 messages and system rows have no author, so ownership-gated actions on them are unavailable.
  Deliberate — they are not attributable.
- `repository.currentUser` now has exactly one remaining production consumer: the ViewModel's
  `updateJobStatus` `changedBy` (finding **F6**, still open).
- The new authorship helpers are not yet wired into any UI; there is no edit/delete/moderate affordance
  using them yet, so no live UI ownership bug exists today.

**Verification**: new `app/src/test/java/com/example/data/ChatSenderIdentityF5SecurityTest.kt`
(Robolectric, 8 cases A–F) plus a new `migration twenty to twenty one is additive and registered` case
in `GuarantorVerificationPersistenceTest`. Full suite 19 suites / 148 tests / 0 failures / 0 errors /
0 skipped; `compileDebugKotlin` BUILD SUCCESSFUL. **No on-device verification was possible** (no adb
device attached) and the rules were **not deployed**.

---

### [2026-10-10] DEC-026 Worker Online Toggle Is a Persisted Device Preference — SessionManager DataStore (B4)

**Context**: Audit finding B4 (Medium). The worker's online/availability toggle ("🟢 ONLINE & ACCEPTING JOBS" vs "⚪ OFFLINE (PAUSED)") was held only in an in-memory `MutableStateFlow(true)` (`SkillsExchangeViewModel.isWorkerOnline`). `toggleWorkerOnline()` flipped the flow in memory and nothing wrote it anywhere, so a worker who deliberately paused came back **online** on every restart — silently re-entering the job queue against their intent. The data model had **no** `isOnline`/`isAvailable` field on `UserProfileEntity`, and `FirebaseService` had no method to set one.

**Decision**:
- Persist the flag in the existing **SessionManager DataStore** (`skills_exchange_session`) as a `worker_online` boolean key that **defaults to online (`true`)** — a fresh install or a first launch still appears available.
- `SessionManager` gains `setWorkerOnline(Boolean)` / `getWorkerOnline()`; `SkillsExchangeViewModel.restoreSession()` seeds `isWorkerOnline` from the stored value after role-mode restore, and `toggleWorkerOnline()` writes the new value through `viewModelScope.launch { … }` (the toggle fn stays non-suspend for its UI callers).
- **DataStore, not a Room column** for this fix, and **not Firestore**.

**Rationale**: The finding is about the choice not surviving a restart — a device-local preference. The SessionManager DataStore is exactly the mechanism the app already uses for lightweight session flags (`is_authenticated`, `user_phone`, `user_role`, `onboarding_completed`, …), so it needs **no schema migration, no Room version bump, and no migration test** — the lowest-risk way to close a Medium finding. A Room column would force a v21→v22 migration and add an authority surface for what is, today, only read by one toggle. Server-shared presence (a Firestore `isOnline` that clients query to filter workers) is a much larger feature — cross-device sync, security rules, and a presence/heartbeat model — and is deliberately **out of scope** for this finding.

**Consequences**:
- The online state is **per-device and local only**; it is not synced across devices and is not visible to clients searching for workers. Correct for the reported bug (don't lose *my* paused choice), but it is not a presence system.
- The flag is stored unconditionally, not keyed per-uid. Because it is only ever *written* after `canAccessWorkerFeatures()` passes and only *read* into the single worker toggle, there is no cross-account leak in practice; a stricter per-uid key can be added if multi-account device sharing becomes real.
- `restoreSession()` seeds the value even for a signed-in non-worker; harmless because the toggle UI is gated behind worker features.

**Verification**: New `app/src/test/java/com/example/data/WorkerOnlinePersistenceTest.kt` (Robolectric) — default true; offline persists; toggling back online persists; and the value survives a **new `SessionManager` instance** (restart). **4/4 pass**, `failures=0`, `errors=0`; `:app:compileDebugKotlin` **BUILD SUCCESSFUL**.

---

### [2026-10-10] DEC-027 Notification Tap Navigates via a Pure Type→Target Map — MainScreen Side Effects (B6)

**Context**: Audit finding B6 (Medium). Tapping an in-app notification in `NotificationDrawer` only called `viewModel.markAllNotificationsRead()` and closed the drawer — it never navigated to the job/verification/payment the notification was about (`MainScreen.kt` `onNotificationClick`). Each `NotificationEntity` already carries a `type` (raw string) and a `jobId`.

**Decision**:
- Introduced a **pure, Android-free** mapping `util/NotificationRouting.kt`: `NotificationRouting.targetFor(type)` returns a `NotificationTarget` enum — `VERIFICATION_DECISION`→IDENTITY, `PAYMENT_STATUS`→WALLET, `DISPUTE_UPDATE`/`SOS_EVENT`→TRUST_SAFETY, `NEW_QUOTE`→QUOTE_COMPARISON, everything else (incl. `TRAINING_REQUIREMENT`)→NONE. These are exactly the `type` strings `SkillsExchangeRepository` writes.
- `MainScreen` gained a local `handleNotificationClick(notification)` that keeps the old mark-read + close-drawer, then opens the destination sheet for the mapped target. Destinations are the **local `remember` sheet states** already in `MainScreen`: `isIdentitySheetOpen`, `isWalletSheetOpen`, `isTrustSafetySheetOpen`, and (for NEW_QUOTE) the quote-comparison sheet — the last **only when `jobs.firstOrNull { it.id == notification.jobId }` still resolves**.

**Rationale**: Navigation in this app is **state-driven, not NavController-driven** — the destination sheets are composable-local `remember` state, so the side effects must live in `MainScreen`. Splitting the *decision* (pure map) from the *effect* (MainScreen) keeps the routing logic unit-testable without a Compose/Android harness, which is what makes a regression test possible on this resource-limited machine. Falling back to mark-read-only for `NONE`/unresolvable keeps the exact prior behaviour for types with no safe destination (e.g. `TRAINING_REQUIREMENT` is only reachable via an inline gated view, not a sheet), so the fix strictly adds navigation and never regresses the old path.

**Consequences**:
- New/deleted notification types must be added to `NotificationRouting.targetFor` or they silently fall back to mark-read-only (safe default, but worth remembering).
- `NEW_QUOTE` only navigates when the job still exists locally; a quote on a job pruned from the local DB opens nothing (mark-read), which is the correct degradation.
- The `type` strings are duplicated between the repository and the routing map (both raw literals), mirroring how the repo already hardcodes them; a shared constant would be a follow-up cleanup, deliberately not done here to keep the change minimal.

**Verification**: New `app/src/test/java/com/example/NotificationRoutingTest.kt` (plain JUnit) — 7 cases covering every repo-written type, `TRAINING_REQUIREMENT`→NONE, and unknown/empty→NONE. **7/7 pass**, `failures=0`, `errors=0`; `:app:compileDebugKotlin` and `:app:testDebugUnitTest` **BUILD SUCCESSFUL**.

---

### [2026-10-10] DEC-028 Map Empty State Is an Overlay in the Map Component, Not a New Screen (B8)

**Context**: Audit finding B8 (Medium). The Map tab rendered a blank grey map (plus a small "0 Nearby Specialists" chip) whenever the filtered provider list was empty, with no explanation or next step. The finding also cited "GPS", but GPS/permission status is already surfaced by the existing `GpsLocationBanner` at the top of the List view, and `InteractiveMapView` takes no location input (its camera is currently hardcoded to `(0.0, 0.0)`).

**Decision**:
- Added `MapEmptyProvidersOverlay` — an `internal` composable inside `InteractiveMapView.kt`, styled to match the existing `MapUnavailableFallback` (icon + title + guidance on the light map background) — rendered **over the map** when `providers.isEmpty()`.
- Kept GPS handling out of the map card; the existing `GpsLocationBanner` already communicates permission / "Use GPS" / live-neighborhood, so duplicating it on the map would be redundant.
- Made the composable `internal` (not `private`) so it can be rendered directly in a Robolectric + Compose test without a live Google Map (the full `InteractiveMapView` early-returns to the API-key fallback under Robolectric, so it can't be tested end-to-end there).

**Rationale**: The defect is "nothing to show here" with no messaging — the smallest correct fix is an informational overlay on the existing map, not a new destination or a rewrite of the map's location model. Matching `MapUnavailableFallback` keeps the two map fallback states visually consistent, and the overlay (rather than replacing the map) preserves spatial context.

**Consequences**:
- The empty state is informational only (no button). A future enhancement could add a "Browse List" action that calls `viewModel.setMobileTab("List")`, which would require threading a callback into `InteractiveMapView`.
- The map still centers on `(0.0, 0.0)` rather than the user's location — a separate, pre-existing gap (out of scope for B8).
- The overlay only keys off `providers.isEmpty()`; it does not distinguish "no providers in the world" from "none within the radius/filters", so the guidance text covers both.

**Verification**: New `app/src/test/java/com/example/MapEmptyProvidersOverlayTest.kt` (Robolectric + Compose, NATIVE graphics) — asserts the title "No providers nearby" and the guidance text render when the overlay is shown. **1/1 pass**, `failures=0`, `errors=0`; `:app:compileDebugKotlin` and `:app:testDebugUnitTest` **BUILD SUCCESSFUL**.

---



### [2026-10-10] DEC-029 Worker Distance Filtering Uses Real Haversine Distance (B9)

**Context**: Audit finding B9 (Medium). The Provider discovery radius filter and the displayed "X mi away" used a static, seeded `distanceMiles` that never reflected the user's actual GPS location, so a moving user saw the same providers/thresholds.

**Decision**:
- New pure-Kotlin `util/DistanceUtil.kt` with `haversineMiles(...)` (great-circle distance in miles) and `effectiveDistanceMiles(...)`.
- `SkillsExchangeViewModel` now decorates each projected provider with the real distance from the user's GPS to the provider before the existing category/distance/rate/verified/activated/query filtering and display, so filter and UI stay consistent with no UI change.
- `effectiveDistanceMiles` returns the stored `distanceMiles` when the user's GPS is unknown (GPS off/unavailable), so the filter degrades gracefully instead of breaking.

**Rationale**: The stored `distanceMiles` was valid only for the seeded/mock location, not the user's actual position, so it misled radius filtering and the "X mi away" display. Computing the real straight-line distance keeps filter and UI consistent with no UI change.

**Verification**: New `app/src/test/java/com/example/DistanceUtilTest.kt` (plain JUnit) — 5 tests / 0 failures / 0 errors (same-coords zero, SF-to-LA ≈347 mi, symmetric, real-location beats stored fallback, stored-value fallback when GPS null). `:app:testDebugUnitTest --tests com.example.DistanceUtilTest` BUILD SUCCESSFUL (EXIT=0); `:app:compileDebugKotlin` unchanged. Decision **DEC-029**.


## D-2026-10-04-2 — Job-status `changedBy` is derived in the repository, not supplied by the caller

**Decision** (C2-2 Fix #6, audit finding **F6**):

- `SkillsExchangeRepository.transitionJobStatus(jobId, fromStatus, toStatus, reason)` **no longer
  accepts a `changedBy` argument**. It resolves the actor itself via
  `resolveAuthoritativeUid(firebaseService.getCurrentUser()?.uid)` and writes that uid to
  `JobStatusAuditEntity.changedBy`.
- With no authoritative uid it **returns `false` before any write**: neither the job status update
  nor the audit insert happens. The identity check precedes the `JobStatus.isValidTransition` check.
- `SkillsExchangeViewModel.updateJobStatus()` no longer reads the legacy newest-Room-row `currentUser`
  flow, has no `"system"` fallback, and returns early on failure so no `JOB_STATUS_CHANGE`
  notification is emitted for a transition that did not occur.
- **No Room migration.** `changedBy` already existed as a non-null `String`; only its source and
  meaning changed. `AppDatabase` stays at v21.
- **No Firestore change.** The audit trail is Room-only (`job_status_audit`); a codebase-wide sweep
  confirms `changedBy` has no serialization path.

**Rationale**:
- The defect was an *identity* defect, not a display defect: the actor came from
  `repository.currentUser.first()?.id ?: "system"`, so on a device that had held another account the
  change was attributed to the wrong person, and with no profile it was attributed to a fabricated
  `"system"`.
- Making the actor a **caller-supplied parameter** was the deeper problem — any call site could
  assert an actor of its choosing. Deriving it inside the repository means the value is correct by
  construction and the compiler prevents re-introduction, matching how F3/F4/F5/F7 were fixed.
- **Failing closed beats recording a guess.** A missing audit entry is recoverable; a wrong or
  fabricated one silently corrupts an accountability trail.
- **Historical rows are not rewritten.** Legacy `"system"` / `"unknown"` values are data as recorded;
  inventing a replacement actor would itself be fabrication.

**Scope boundary — deliberately not fixed here**:
- `JobStatus.isValidTransition()` remains the **only** gate on a status transition. There is **no
  per-role or ownership authorization** determining who may move a job to which state, so any
  signed-in user can move any job along any legal edge. This is a **separate finding**, identified
  and reported, and intentionally left unfixed — F6 makes the recorded actor correct and weakens no
  existing check. It remains open and should be its own fix.
- This decision does not close `C2`; the F3 remainder also remains open, and `prd.json` was not
  modified.

**Known limitation recorded rather than papered over**: `resolveAuthoritativeUid` rejects literal
synthetic sentinels, `user_default_me`, and whitespace/bracket-bearing presentation text, but it
**cannot** reject a bare single alphanumeric token such as a district name — Firebase UIDs are opaque
alphanumeric strings, so shape alone cannot distinguish one from a real UID, and blocklisting
district names would be guesswork. A test asserting otherwise was corrected, not the production code.

**Verification**: `JobStatusAttributionF6SecurityTest` (7 cases) — including the uid_A / uid_B
newer-row trap, fail-closed with no profile row, fail-closed on invalid uid, and untouched legacy
rows; full suite **20 suites / 155 tests / 0 failures / 0 errors / 0 skipped**;
`:app:compileDebugKotlin` BUILD SUCCESSFUL; `git --no-pager diff --check` clean. No device run was
possible (no adb device attached).

**Environment caveat for future sessions**: `gradle.properties` pins `org.gradle.jvmargs=-Xmx4g`,
but this machine has only **3.88 GB total RAM**, so the daemon crashes with a native
`OutOfMemoryError` before running. Use `-Dorg.gradle.jvmargs=-Xmx1600m --max-workers=1
-Dorg.gradle.parallel=false`, keep `TMP`/`TEMP` on D:, and in PowerShell place `-D…` arguments after
the `--%` stop-parsing token or they are mangled into a bogus Gradle task name.

---
---
