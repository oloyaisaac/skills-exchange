# R03 — Backend & Cloud Functions Engineer

| Field | Value |
|---|---|
| Reviewer | R03 — Backend / Cloud Functions (P0) |
| Assignment | `reviewers/BACKEND_REVIEWER.md` (read in full) |
| Executed | 2026-11-19 |
| Source reviewed | HEAD `06ff649` |
| Areas | 18 exported functions, authorization, webhook, deployability, FCM, idempotency |
| Verdict | **FAIL** |
| Issues | 6 (2 CRITICAL, 1 HIGH, 3 MEDIUM) |

## Areas examined
All 4,027 lines of TypeScript: callables, scheduled jobs, Firestore trigger, HTTP webhook, shared money
helpers, environment configuration and deploy settings.

## Source files inspected
`functions/src/index.ts`, `momoCollection.ts`, `momoDisbursement.ts`, `momoConfirmation.ts`,
`momoConfig.ts`, `types.ts`; `functions/package.json`, `functions/tsconfig.json`, `firebase.json`.

## Features examined
Collection initiation/status, escrow release, payout/material advance, guarantor consent/review, admin
queues, dispute/SOS resolution, job-created trigger, MoMo webhook.

## Tests performed
- `npm ci` (dependencies present).
- `npm test` → **111/111 pass** (Node test runner, 582 ms).
- `npm run build` → **exit 2**, 8 TypeScript errors (`index.ts:645–683`).
- Enumerated exports, grep for `admin.messaging`, App Check and `setCustomUserClaims`.
- No Firebase emulator, provider sandbox or deployment.

## Verified working functionality
- Amounts are read from the stored job document, not client payload; currency is a constant `UGX`.
- The webhook rejects unknown references and re-queries the provider, failing closed on uncertainty.
- 111 unit tests pass, including money helper and confirmation tests.

## Problems

| ID | Severity | Description | Component | Evidence | Expected | Actual | Recommended correction | Required verification |
|---|---|---|---|---|---|---|---|---|
| R03-01 | CRITICAL | The function bundle cannot be built from source. | `functions/src/index.ts` | `npm run build` exit 2; 8 × TS2339 on `Request`/`Response` at `:645–683`; `lib/` still emitted | `tsc` exit 0 | Type-check fails while emitting partial output | Use `firebase-functions` request/response types; fail build on error | `npm ci && npm run build` exit 0 in CI |
| R03-02 | MEDIUM | Deploy has no build/verify step and targets an unsupported runtime. | `firebase.json`, `package.json` | No `predeploy`; engine `node 18` (`package.json:14`) while tests import `.ts` | Build+test before deploy on a supported runtime | Stale/partial `lib/` can deploy; runtime EOL | Add `predeploy` (build/test) and pin a supported Node version | Deploy dry-run with predeploy log |
| R03-03 | HIGH | No App Check enforcement or per-user throttling on callables. | All callables | No `enforceAppCheck`; no rate-limit middleware | Only genuine app instances; bounded abuse | Any Firebase token can invoke endpoints | Enforce App Check and add per-uid limits | Negative test with unverified client |
| R03-04 | CRITICAL | No push notification is ever sent by the backend. | Functions | 0 `admin.messaging` calls; `onJobCreated` only logs/radius logic | FCM on job/quote/payment/SOS events | No push delivery exists | Implement FCM send with token cleanup and failure logging | Device receives event-driven push |
| R03-05 | MEDIUM | MoMo webhook is unauthenticated and unthrottled. | `processMoMoWebhook` | Public HTTP handler, no signature/timestamp/allow-list (`index.ts:643`) | Signed, time-bounded, rate-limited callback | Any caller can force provider re-checks | Add signature/IP allow-list and throttle | Forged-callback test rejected |
| R03-06 | MEDIUM | Scheduled payout/refund jobs have no executor, dead-letter or alerting. | Scheduled functions | No refund executor; no dead-letter/alert path | Failures retried and surfaced | Stuck escrow possible with no signal | Implement executor + DLQ + alert | Failure-injection test |

## Outcome classification

- **Verified working:** see "Verified working functionality" above.
- **Verified failing:** R03-01, R03-05.
- **Not implemented:** R03-02, R03-03, R03-04, R03-06.
- **Partially implemented:** none.
- **Unable to verify:** runtime-only checks listed under "Limitations / unable to verify".

## Missing functionality
- Refund executor, FCM send, webhook authentication, rate limiting, dead-letter handling, staging deploy.

## Security concerns
Admin callables are correctly gated by `authorizeAdmin` (`index.ts:757`), but the claim cannot be
provisioned (R06). The webhook's re-query design limits forgery damage but does not authenticate callers.

## Performance concerns
No timeouts beyond provider defaults, no concurrency caps on scheduled fan-out, no cold-start budget.

## Recommendations
1. Make `tsc` green and gate deploys on it.
2. Add App Check, rate limits and webhook signing.
3. Implement FCM and the refund executor.
4. Add `predeploy`, supported runtime and DLQ/alerting.

## Limitations / unable to verify
No emulator, Firebase project, MoMo sandbox or deployment. Handler behaviour is **unable to verify**;
only pure modules were executed. The review's earlier export count of 17 was corrected to **18** on
recount (see MASTER_REVIEW_FINDINGS §4).

## Cross-references
R05 (integrations), R08 (money), R14 (deploy), R06 (claims), MR-002/MR-009/MR-017/MR-035/MR-021/MR-032.
