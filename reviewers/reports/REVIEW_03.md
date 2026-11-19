# REVIEW-03 — Backend / Cloud Functions Review

| | |
|---|---|
| **Reviewer** | R03 — Backend / Cloud Functions Reviewer (P0) |
| **Wave** | 2 |
| **Date** | 2026-11-19 |
| **Commit reviewed** | `d448372` (`functions/` unchanged since `6b39a10`) |
| **Verdict** | **FAIL** |

## 1. Scope and evidence

All 4,027 lines of TypeScript in `functions/src/` were enumerated; every export was inspected for
authentication, authorization, input trust and idempotency. `npm test` was executed (111 pass) and
`npm run build` was executed (**exit code 2**). `firebase.json` was read for deploy controls.

## 2. Function inventory and authorization matrix

17 exports total: 13 HTTPS callables, 2 scheduled, 1 Firestore trigger (`onJobCreated`), 1 HTTP
endpoint (`processMoMoWebhook`).

| Function class | Auth enforced | Ownership enforced | Notes |
|---|---|---|---|
| `getMoMoCollectionStatus`, `releaseEscrowToWorker`, `createMoMoPayout`, `createMoMoMaterialAdvance`, `getMoMoPayoutStatus` | Yes (`context.auth`) | Yes (`postedByUid`) | Verified sound statically; do not re-flag. |
| `getGuarantorSubmission`, `reviewGuarantorSubmission` | Yes | `authorizeAdmin(context)` (`index.ts:757`) | Gate is correct **but the admin custom claim can never be provisioned** (R06-F4). |
| `submitGuarantorConsent` (public/callable) | Partial | Consent token | Not penetration-tested. |
| `processMoMoWebhook` | **No** (public HTTP) | Re-queries provider by stored reference; unknown refs 404 without creating rows | Correctly fails closed, but has no signature check and no replay rate limit. |
| Scheduled payout/reminder jobs | N/A (scheduler) | N/A | No dead-letter or alerting on failure. |

## 3. Confirmed findings

| ID | Severity | Evidence | Impact |
|---|---|---|---|
| R03-F1 | Critical | `npm run build` exits **2**; `tsc` type error on the `Request` type used by `processMoMoWebhook` (`index.ts:645`). | The function bundle cannot be built reproducibly from a clean checkout; whatever is deployed came from a tree that did not type-check. |
| R03-F2 | High | `firebase.json` has **no `predeploy` hook** and `node` engine is 18 while tooling/@types are newer. | `firebase deploy` can publish stale `lib/` output or unbuilt code; runtime is out of support. |
| R03-F3 | High | No App Check (`enforceAppCheck`) on any callable; no rate limiting; no allow-list of origins. | Callables are reachable by any client holding a Firebase token, including scripted abuse of payout/status endpoints. |
| R03-F4 | High | No `admin.messaging()` call exists anywhere in `functions/` (grep 0). `onJobCreated` only requests a Maps/Emerald radius and writes logs. | Push notifications described in the README are never sent by the backend (F-005). |
| R03-F5 | Medium | `processMoMoWebhook` trusts `extractCallbackReferenceHint(req.body)` but then re-queries; no HMAC/timestamp/allow-list. | Acceptable integrity posture by luck of design, not by authentication; no abuse throttle. |
| R03-F6 | Medium | Payout/refund scheduled paths do not implement a refund executor; `releaseEscrowToWorker` exists but the client never calls it (R08-F2). | Escrow remains locked after dispute resolution. |
| R03-F7 | Medium | Errors return `error.message` to callers (`index.ts` webhook catch; several callables). | Internal details leak; provider responses can surface to clients. |
| R03-F8 | Info | Amounts are read from the stored job document, not the client payload (`momoCollection.ts`), and currency is a constant `UGX`. | Positive: the classic amount-tampering bug is absent. |

## 4. Requirement checks

| Requirement | Result |
|---|---|
| Every callable authenticates | FAIL (webhook unauthenticated by design, unthrottled) |
| Admin-only operations gated | PARTIAL (code gate present, claim unprovisionable) |
| Money amounts server-authoritative | PASS (static) |
| Idempotent webhook/confirmation | PASS (static, tests pass) |
| Build/deploy reproducible | FAIL (R03-F1/F2) |
| Push delivery | FAIL (R03-F4) |

## 5. Passing conditions

1. `npm ci && npm run build` exits 0 in a clean environment and CI enforces it.
2. `predeploy` runs lint/build/test; Node runtime is supported and pinned.
3. App Check enforced, per-uid rate limits on payout/status, webhook signature + timestamp window.
4. FCM send path implemented for job/quote/payment/SOS events, with failure logging.
5. Refund executor implemented and wired to dispute resolution.

## 6. Residual risk

The webhook's re-query design limits forgery damage, but an unauthenticated endpoint with no throttle
on a payment-adjacent path is still an availability and cost risk. Deployment cannot be trusted until
the build gate is green.

## Recommended remediation (not implemented)

1. Make `npm ci && npm run build` a required, green CI gate; add a `predeploy` hook.
2. Enforce App Check and per-uid rate limits; sign and throttle the webhook.
3. Implement FCM send for job/quote/payment/SOS events with failure logging.
4. Implement the refund executor and scheduled-job dead-letter handling.

## 7. Cross-references

F-005, F-006, F-021; R08 (payments), R06-F4 (claims), R14 (deploy), R05-F3 (FCM).
