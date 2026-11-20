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
