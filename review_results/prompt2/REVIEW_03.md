# R03 — Backend & Cloud Functions Engineer

| Field | Value |
|---|---|
| Reviewer | R03 — Backend & Cloud Functions (P0) |
| Assignment | `reviewers/BACKEND_REVIEWER.md` (read in full); file deleted after independent verification — text preserved in `verification/reports/VERIFICATION_03.md` §9 |
| Executed | 2026-11-20 |
| Source reviewed | `arena/0570a462-skills-exchange` @ `aa4a0bc` |
| Mode | Review only — no backend source changed |
| Executable evidence | `npm run build` exit 0; `npm test` **171/171** (Node 22.22.3) |
| Deployment / emulator | **UNVERIFIED** — no Firebase project, CLI, emulator or Blaze plan |
| Verdict | **FAIL** |

## 1. Function inventory (actual at this HEAD)

| Function | Trigger | Auth | Notes |
|---|---|---|---|
| `createMoMoCollection` | callable | owner uid | validates job, amount authoritative |
| `getMoMoCollectionStatus` | callable | authenticated | limit-bounded |
| `releaseEscrowToWorker` | callable | owner uid + funded escrow | deterministic `PAYOUT-{jobId}` |
| `refundEscrow` | callable | **admin claim** | provider request, `refunds` record |
| `processMoMoWebhook` | HTTPS POST | HMAC + timestamp + rate limit | re-queries provider before mutation |
| `onJobCreated` | Firestore trigger | n/a | SMS radius dispatch |
| `onSosIncidentCreated` | Firestore trigger | n/a | SMS + FCM topic `sos_dispatch` |
| `getPendingVerificationQueue` | callable | admin | capped at 100 |
| `reviewWorkerApplication` | callable | admin | validates uid |
| `setWorkerActivationStatus` | callable | admin | validates uid |
| `getGuarantorSubmission` | callable | admin | signed URLs |
| `reviewGuarantorSubmission` | callable | admin | validated input |
| `resolveDispute` | callable | admin | now requests a provider refund |
| `getDisputesQueue` / `getSosQueue` | callable | admin | bounded |
| `acknowledgeSos` / `resolveSosIncident` | callable | admin | — |
| `createMoMoPayout` | callable | owner uid | **second payout path** (see F3) |
| `createMoMoMaterialAdvance` | callable | owner uid | — |
| `getMoMoPayoutStatus` | callable | authenticated | ownership checked |
| `sendOtp` / `verifyOtp` | callable | **none by design** | per-phone rate limits; no IP/device limit |
| `transcribeVoiceJob` | callable | authenticated | key server-side |
| `recordConsent` / `exportMyData` | callable | authenticated | DSAR |
| `getAdminAccess` | callable | authenticated | returns claim |
| `revokeMyTokens` | callable | authenticated | Admin SDK revocation |

**26 exported functions** (23 callables), while `reviewers/BACKEND_REVIEWER.md` and the roadmap still describe 18 — a documentation drift, not a runtime defect.

## 2. Authorization and validation

- Every admin callable routes through `checkAdminCaller` (`index.ts:997–1012`), which fails closed on
  unauthenticated callers and on a missing `admin` claim.
- Money callables do not trust client amounts: `createMoMoCollection` uses `validateCollectionInput`
  and now also rejects a tampered stored quote (REM-029); `releaseEscrowToWorker` derives amount and
  wallet server-side (REM-026).
- `sendOtp`/`verifyOtp` are intentionally unauthenticated because they mint the first session; both
  enforce per-phone rate limits (`otp_challenges`) and bounded attempts before the provider call.
- Input validation is present but uneven: `refundEscrow` converts `amount` with `Number()` and relies
  on `decideRefund`; `getMoMoPayoutStatus` validates ids via existing helpers. No callable was found
  that writes before validating.

## 3. Webhook trust model

`processMoMoWebhook` (`index.ts:794–872`) checks method, HMAC secret + timestamp freshness + replay
throttle **before** any document lookup, rejects unknown references, and then calls
`verifyAndConfirmCollection` which re-queries the provider. A forged callback cannot mutate escrow.
This meets the R03 requirement. Unexecuted: a live forged-request test (no emulator).

## 4. Idempotency and terminal states

- Collection: `momo_transactions` status gates; webhook confirmation is pull-to-verify.
- Payout: `releaseEscrowToWorker` returns `alreadyReleased` on repeat and uses `PAYOUT-{jobId}`.
- Refund: `decideRefund` rejects duplicates and over-amounts, writes `refunds/{uuid}`.
- Remaining risk: two payout implementations coexist (F3), and `createMoMoPayout` accepts
  client-supplied `workerMomoNumber`/`amountUgx` (validated against the job, but the contract differs).

## 5. Queries and indexes

All Firestore list reads in `index.ts` are bounded by `.limit()` (script check: 0 unbounded list
reads); `check_query_limits.mjs` passes with 50 Room queries. There is **no
`firestore.indexes.json`** in the repository and `firebase.json` does not declare one, so composite
queries (`momo_transactions` by jobId+clientUid+status, `worker_kyc` status-in, payout queries) depend
on console-created indexes. Deployment evidence is UNVERIFIED.

## 6. Secrets, runtime and deployment

| Item | Finding |
|---|---|
| Secrets | Read via `process.env` (`AFRICASTALKING_*`, `GEMINI_API_KEY`, `SOS_DISPATCH_NUMBERS`, `GS_STORAGE_BUCKET`); no `defineSecret`/Secret Manager binding. Values must be set per environment, and a missing value fails closed (SMS/transcription) |
| Runtime | `engines.node = "18"` — Node 18 is end-of-life; current Firebase runtimes are 20/22 |
| Deploy hook | `firebase.json` has **no `predeploy` build**, so `firebase deploy --only functions` can ship a stale `lib/` or none |
| Build | `tsconfig` includes `test/`, but tests are `.mjs`; `tsc` emits `lib/src` + `lib/test`? build exit 0 and `lib/index.js` exist — deploy entry is resolved by Firebase from `main`; verify on a real project |
| Indexes | no `firestore.indexes.json` |
| Emulator | not run |

## 7. Notifications (FCM)

Only the SOS trigger sends FCM (`index.ts:979`, topic `sos_dispatch`). Payout, KYC, dispute and job
status "notifications" are Firestore documents; nothing pushes them to a device. The product copy
("payment released", "job status updated") is therefore only visible if the client observes Firestore;
the FCM gap remains.

## 8. Tests

`npm test` = 26 files, **171 pass / 0 fail**. Tests cover pure modules (collection, confirmation,
disbursement, OTP, pricing, refund, escrow release, admin, session, SMS/SOS). No test exercises a
callable handler, Firestore transaction, duplicate webhook or emulator path; the test script is an
explicit file list, so a new test file silently does not run unless appended.

## 9. Findings

| ID | Severity | Finding | Evidence |
|---|---|---|---|
| R03-P2-01 | HIGH | Node 18 runtime is EOL; deployment target is not current | `functions/package.json` engines |
| R03-P2-02 | HIGH | No `predeploy` build hook; deploy can ship stale/absent `lib/` | `firebase.json` |
| R03-P2-03 | MEDIUM | Two payout implementations with different contracts | `releaseEscrowToWorker:560`, `createMoMoPayout:1824` |
| R03-P2-04 | MEDIUM | No `firestore.indexes.json`; composite-query provisioning is undocumented | repo root / `firebase.json` |
| R03-P2-05 | MEDIUM | Secrets via `process.env`, no Secret Manager binding | greps for `defineSecret` = 0 |
| R03-P2-06 | MEDIUM | Only SOS has FCM; other notifications are Firestore-only | `index.ts:979` only `messaging()` call |
| R03-P2-07 | LOW | OTP endpoints have per-phone but no IP/device throttle (SMS-cost abuse) | `sendOtp:2211` |
| R03-P2-08 | LOW | Documentation still says 18 exports / 111 tests | reviewer instruction; `package.json` test list |

## 10. Verdict

**FAIL.** The money-critical server paths are now validated and idempotent, and the webhook cannot
mutate without re-query, but R03's passing conditions also require a deployable configuration
(predeploy, supported runtime, indexes) and handler-level tests — neither exists. Build and unit
suite are green; deployment and emulator behaviour are **UNVERIFIED**.
