# VERIFICATION_03 — R03: Backend & Cloud Functions Engineer

**Independent verification** of the R03 assignment against `cb61a31` (`main`), executed on
`arena/c5549ee2-skills-exchange`. **Date:** 2026-11-20.
**Verdict: VERIFIED COMPLETED**, with one stale code comment handed back (§7) and two limits stated (§5).

Sources read in full first: `reviewers/BACKEND_REVIEWER.md` (79 lines),
`review_results/prompt2/REVIEW_03.md` (123 lines), the one-sentence completion declaration, and
`REVIEWER_IMPLEMENTATION_STATE.md` §R03.

## 1. Passing conditions (quoted)

> PASS requires: every callable authenticated/validated, webhook cannot mutate without re-query, money
> operations idempotent, `npm run build` clean, deploy config complete, FCM gap resolved or explicitly
> accepted, and tests cover handlers rather than only pure modules.
> FAIL if any endpoint is unauthenticated by design without compensating verification, or deployment
> cannot succeed from this repository.

This reviewer is the first whose evidence the verifier can **execute directly** — the backend runs
without a JDK. Everything in §4 was run in this environment.

## 2. Declaration

`reviewer_completions/REVIEWER_03_DONE.md`: "I finished the work for this reviewer." No run, no count.
Re-derived from the tree and from commands.

## 3. An environment trap worth recording

Running `npm test` **before** installing dependencies gives **141 tests run, 29 failing** — the failures
are import errors, not defects. After `npm ci`: **318 tests, 318 pass, 0 fail, 1,686 ms**. A verifier that
reported the first number would have filed 29 invented failures. The correct sequence is recorded in
`docs/qa/TEST_STRATEGY.md`; this report states it because it is the kind of mistake the programme exists
to prevent.

## 4. Condition-by-condition verification (executed here)

| # | Condition | Verdict | Evidence |
|---|---|---|---|
| 1 | Every callable authenticated/validated | **PASS** | 49 exports: **43 callables**, 1 HTTPS, 5 Firestore triggers. A structural scan for `context.auth`, `isAdmin(`, `authorizeAdmin(`, `checkAdminCaller` leaves exactly **two** unauthenticated callables — `sendOtp` and `verifyOtp` — which mint the first session by design and carry per-phone **and per-IP** throttles (`otp_ip_limits`, `otp_challenges`). `checkAdminCaller` fails closed; `authorizeAdmin` maps its code to an `HttpsError` and returns the uid for attribution. |
| 2 | Webhook cannot mutate without re-query | **PASS** | `processMoMoWebhook` treats the body as a hint only: it validates the reference against the stored transaction and every state decision comes from `verifyAndConfirmCollection`'s authenticated provider re-query. `node --test test/webhookAuth.test.mjs` → **8 tests, 0 failures**. Note the review's §3 describes an HMAC gate that no longer exists — the tree removed it deliberately (PAY-002, D-2026-10-14-1) because MTN's callback is unauthenticated and a gate it can never satisfy only rejects real callbacks. The *requirement* is met by pull-to-verify, which is what matters. |
| 3 | Money operations idempotent | **PASS** | Payout: deterministic `PAYOUT-{jobId}` plus an `alreadyReleased` reply; the provider is asked first and the job is marked released only on acceptance. Refund: `decideRefund` rejects duplicates and over-amounts. Collection: 202 means PENDING, never funded. |
| 4 | `npm run build` clean | **PASS** | `npm run build` → `tsc` exits 0, no diagnostics. `npx tsc --noEmit` also clean. |
| 5 | Deploy config complete | **PASS** | `firebase.json` declares `functions.predeploy` (`npm --prefix "$RESOURCE_DIR" run build`), `firestore.indexes` → `firestore.indexes.json` (**4 composite indexes**), storage rules and emulators. `engines.node` is **22**. |
| 6 | FCM gap resolved | **PASS** | `sendUserPush` is called for payout released (`:1011`), verification decision (`:2328`), dispute resolved (`:2755`) and both parties (`:2771`), plus the SOS topic send. The review's "only SOS pushes" is no longer true. |
| 7 | Tests cover handlers, not only pure modules | **PASS** | `test:handlers` runs `test/handlers.emulator.mjs` — **19 handler tests** against the Firestore emulator — and the CI job "Backend handler tests (Firestore emulator)" is green. `npm test` now globs `test/*.test.mjs` (318 tests) instead of the explicit file list the review flagged as silently skipping new files. |
| — | FAIL conditions | **not met** | No endpoint is unauthenticated without compensating verification (the two OTP ones have documented throttles); deployment configuration is complete enough to attempt a deploy. No Firebase project here, so an actual deploy remains UNVERIFIED. |

## 5. What could not be executed (stated, not implied)

- **No Firebase project, no Blaze plan, no `firebase deploy`.** Deployment *configuration* is complete and
  internally consistent; whether a real deploy succeeds — and whether the 4 declared indexes cover every
  query shape — is UNVERIFIED. That is an environment gap, not a code defect, and R03's conditions ask for
  a complete configuration.
- The emulator suites (handlers 19, rules) run in CI, not here; this environment has no `firebase-tools`
  emulator. Their green CI runs are cited, not reproduced.

## 6. Finding-by-finding re-test

| Finding | Status | Evidence |
|---|---|---|
| R03-P2-01 **HIGH** Node 18 EOL | **CLOSED** | `functions/package.json` → `"node": "22"`; CI runs Node 22. |
| R03-P2-02 **HIGH** no `predeploy` | **CLOSED** | `firebase.json` builds before deploy. |
| R03-P2-03 **MEDIUM** two payout implementations | **CLOSED** | `createMoMoPayout` now delegates to `performEscrowRelease`, ignores client `amountUgx`/`workerMomoNumber`, and returns `deprecated` in its envelope. |
| R03-P2-04 **MEDIUM** no `firestore.indexes.json` | **CLOSED** | File exists with 4 composite indexes, declared in `firebase.json`. Coverage of every query shape is UNVERIFIED without a project. |
| R03-P2-05 **MEDIUM** secrets via `process.env` | **CLOSED** | `defineSecret` for `AFRICASTALKING_API_KEY`, `AFRICASTALKING_USERNAME`, `GEMINI_API_KEY`, bound through `runWith({ secrets: … })`; `secretOrEnv` falls back only when the parameter is unbound, and a missing value still fails closed. |
| R03-P2-06 **MEDIUM** only SOS sends FCM | **CLOSED** | Four `sendUserPush` call sites (payout, verification, dispute ×2) plus the SOS topic. |
| R03-P2-07 **LOW** OTP has no IP throttle | **CLOSED** | Per-IP windowed limiter in `sendOtp`, checked before the provider call. |
| R03-P2-08 **LOW** docs claim 18 exports / 111 tests | **CLOSED for the instruction** | The instruction now states 27 exports (it is deleted after this verification — §9). Remaining "111 tests" mentions are in `ASSESSMENT_REPORT.md`, which the document map keeps as a dated snapshot, and in historical logs. Current truth: **49 exports / 318 unit tests / 19 handler tests**. |

## 7. Correction handed back

- **VC-04 (LOW, stale comment).** `functions/src/index.ts:2215–2216` still says "a composite index that
  this repository does not declare (`firestore.indexes.json` is not part of the project)". The repository
  now declares four indexes and `firebase.json` points at the file, so the comment tells a future reader
  the opposite of the truth — the same class of defect the documentation round closed, in code.

## 8. Approval

**VERIFIED COMPLETED.** All seven passing conditions verified, six of them by executing commands in this
environment; both FAIL conditions are absent. Approved on 2026-11-20.

One-sentence completion declaration preserved verbatim from
`reviewer_completions/REVIEWER_03_DONE.md`:

> I finished the work for this reviewer.

Instruction file `reviewers/BACKEND_REVIEWER.md`: **deleted after verification** on 2026-11-20; the
filename is recorded in `VERIFICATION_MASTER_REGISTER.md`, every inbound reference now points here, and
the assignment's full text is preserved verbatim in §9.

## 9. Preserved assignment text (verbatim)

Deleted file: `reviewers/BACKEND_REVIEWER.md`. Nothing below was edited.

```markdown
# R03 — Backend & Cloud Functions Engineer

## 1. Reviewer Number
R03 (PRD story `RVW-003`, Priority P0). Depends on R01 for the repository-wide build gate; function tests can run independently.

## 2. Professional Title
Senior Backend / Serverless Engineer, TypeScript + Firebase Cloud Functions, payment-provider integrations and idempotent event processing.

## 3. Required Expertise
- Firebase Functions v4 callable and HTTPS semantics, `context.auth`, custom claims, error-code mapping.
- Idempotency, replay safety, terminal-state immutability, pull-to-verify webhooks.
- Firestore server-side transactions, query/index requirements, Admin SDK privilege boundaries.
- Secret management (`defineSecret`/Secret Manager vs `process.env`), runtime lifecycle and deployment hooks.
- Node runtime support and TypeScript build/deploy pipelines.

## 4. Responsibilities
1. Inventory every exported function and its caller, authorization gate and failure modes.
2. Verify each callable validates input types, bounds and ownership before any write.
3. Review the MoMo webhook trust model: unauthenticated POST must never itself mutate money state.
4. Verify idempotency keys, duplicate handling and terminal-state immutability for collection and payout.
5. Audit Firestore queries for required composite indexes and unbounded reads.
6. Audit secret loading, runtime version, deployment configuration and build reproducibility.
7. Confirm the backend sends the notifications the product claims (FCM), or record the gap.

## 5. Exact Features to Examine
- `createMoMoCollection`, `getMoMoCollectionStatus`, `processMoMoWebhook`.
- `releaseEscrowToWorker`, `createMoMoPayout`, `createMoMoMaterialAdvance`, `getMoMoPayoutStatus`.
- `onJobCreated` Firestore trigger.
- `getPendingVerificationQueue`, `reviewWorkerApplication`, `setWorkerActivationStatus`.
- `getGuarantorSubmission`, `reviewGuarantorSubmission` (signed photo URLs, TTL).
- `resolveDispute`, `getDisputesQueue`, `getSosQueue`, `acknowledgeSos`, `resolveSosIncident`.

## 6. Relevant Source Files and Components
| Path | Why |
|---|---|
| `functions/src/index.ts` (2,547 lines) | 27 exports: 24 callables, 1 HTTPS webhook, 2 Firestore triggers; admin gating on every callable |
| `functions/src/momoCollection.ts` (483) | config loading, initiation, collection rules |
| `functions/src/momoConfirmation.ts` (420) | pull-to-verify, status parsing, escrow lock |
| `functions/src/momoDisbursement.ts` (529) | payout eligibility, payloads, terminal states |
| `functions/src/guarantorReview.ts` (706) | `checkAdminCaller`, queue projection, signed URLs |
| `functions/test/*.test.mjs` (16 files, 172 assertions) | pure-module tests (executed: 172 pass); `test/handlers.emulator.test.mjs` (5) runs the payout callables against the Firestore emulator |
| `functions/package.json` | engine node 22, `firebase-functions ^4.3.1`, `firebase-admin ^11.8.0`, explicit `node --test` file list |
| `functions/tsconfig.json` | `strict: true`, includes `test/` in build |
| `firebase.json` | no `predeploy` build hook, no `firestore.indexes.json`, no hosting |
| `app/src/main/java/com/example/data/remote/FirebaseService.kt` | client callable contract (files, payload shapes) |

Coordinate with R08 for money semantics and R07 for rules; R03 owns server implementation, runtime and contracts.

## 7. Functional Requirements
- Every callable returns a typed, stable result envelope; no raw provider error reaches the client.
- A 202/`PENDING` initiation must never be reported as funded escrow.
- Webhook body is a hint only; authoritative status comes from an authenticated provider re-query.
- Payout creation must be idempotent per job and reject ineligible job states.
- Admin callables must fail closed without the `admin` claim and validate `workerId` as a document id.
- Notification claims in the product must map to real server dispatch.

## 8. Security Requirements
- No callable trusts a client-supplied amount, user id, job owner or payment status.
- Secrets are not read from a source-controlled `.env` and not exposed in errors/logs.
- Signed URL TTL is bounded (15 min default, 1 h maximum) and paths are validated against the caller's scope.
- Webhook endpoint must tolerate hostile payloads without state mutation (test with forged reference/amount).
- Admin claim provisioning must exist and be documented; otherwise every admin callable is unreachable in practice.

## 9. Testing Requirements
- Run `npm test` from `functions/` and record the exact output.
- Add tests for each callable's input validation and error mapping (current tests cover pure modules only).
- Emulator-based tests for Firestore transactions, duplicate callback and payout idempotency.
- Verify `npm run build` produces `functions/lib/index.js` and that deployment would use it.

## 10. Expected Evidence
- Function inventory table: name, trigger, auth, inputs, writes, idempotency, caller.
- Exact `npm test` output (172 pass / 0 fail, plus 5 handler tests under the Firestore emulator in CI).
- Query/index analysis with the failing composite query named.
- Deployment dry-run or written blockers (engine mismatch, missing predeploy, missing Blaze plan).
- Webhook threat-model note with forged-request test results.

## 11. Passing Conditions
- PASS requires: every callable authenticated/validated, webhook cannot mutate without re-query, money operations idempotent, `npm run build` clean, deploy config complete, FCM gap resolved or explicitly accepted, and tests cover handlers rather than only pure modules.
- FAIL if any endpoint is unauthenticated by design without compensating verification, or deployment cannot succeed from this repository.
```
