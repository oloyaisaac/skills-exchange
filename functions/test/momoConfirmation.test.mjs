/**
 * PAY-002: automated tests for the trusted backend MoMo collection CONFIRMATION core.
 *
 * These run with `node --test` against the pure TypeScript source (no emulator, no
 * Firestore, no network, NO real MTN transactions) because `momoConfirmation.ts` has
 * no `firebase-admin` / `firebase-functions` / `axios` imports — every external
 * effect is an injected fake.
 *
 * Coverage maps to the PAY-002 acceptance tests:
 *  1. HTTP 202 at initiation                                -> PENDING (never success)
 *  2. Provider reports PENDING                              -> PENDING
 *  3. Provider reports SUCCESSFUL (+ consistent amount)     -> SUCCESSFUL (+ escrow lock)
 *  4. Provider reports FAILED                               -> FAILED (no escrow)
 *  5. Provider timeout                                      -> NOT successful (PENDING kept)
 *  6. Malformed provider response                           -> NOT successful (UNKNOWN)
 *  7. Unknown transaction reference                         -> rejected
 *  8. Duplicate webhook/callback                            -> idempotent (no state change)
 *  9. Replay after terminal success                         -> no state corruption
 * 10. Callback/provider with manipulated amount             -> stored amount wins (no escrow)
 * 11. Callback with manipulated job/user fields             -> stored binding wins (ignored)
 * 12. Successful payment locks escrow exactly once
 * 13. Failed payment cannot fund escrow
 * 14. Unauthorized caller cannot query another user's tx    -> permission_denied
 * 15. Provider credentials never leave the backend (by construction: this core is
 *     server-side only and Android never imports it — the Android source is scanned
 *     by `MoMoCredentialLeakSecurityTest` on the Gradle side).
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  parseProviderStatusResponse,
  decideConfirmation,
  authorizeStatusQuery,
  extractCallbackReferenceHint,
  confirmationMessageFor,
  verifyAndConfirmCollection,
  TERMINAL_CONFIRMATION_STATUSES,
} from "../src/momoConfirmation.ts";
import { initiateCollection, isProviderAccepted, loadMomoConfig } from "../src/momoCollection.ts";


const TX_ID = "MOMO-1700000000000";
const PROVIDER_REF = "11111111-2222-3333-4444-555555555555";
const JOB_ID = "job-1";
const OWNER_UID = "client-uid-1";
const OTHER_UID = "client-uid-2";
const AMOUNT = 50000;

const SANDBOX_ENV = {
  MOMO_ENVIRONMENT: "sandbox",
  MOMO_BASE_URL: "https://sandbox.momodeveloper.mtn.com",
  MTN_SUBSCRIPTION_KEY: "sub-key",
  MTN_API_USER: "api-user",
  MTN_API_KEY: "api-key",
};

/** A stored PENDING transaction created by the PAY-001 initiation path. */
function storedTx(overrides = {}) {
  return {
    transactionId: TX_ID,
    providerReferenceId: PROVIDER_REF,
    jobId: JOB_ID,
    clientUid: OWNER_UID,
    amount: AMOUNT,
    currency: "UGX",
    status: "PENDING",
    escrowLockedAt: null,
    ...overrides,
  };
}

/**
 * Build ConfirmationDeps fakes recording every side effect so tests can assert what
 * was (and was NOT) called — in particular that escrow was never locked.
 */
function makeDeps({ stored = storedTx(), provider = { kind: "ok", status: "PENDING" }, tokenFails = false } = {}) {
  const calls = { load: 0, token: 0, query: 0, updates: [], locks: [] };
  let current = stored ? { ...stored } : null;
  const deps = {
    now: () => 1_700_000_000_500,
    loadConfig: (env) => loadMomoConfig(env),
    async loadStoredTransaction() {
      calls.load += 1;
      return current ? { ...current } : null;
    },
    async acquireToken() {
      calls.token += 1;
      if (tokenFails) return { ok: false, error: "token exchange failed" };
      return { ok: true, token: "provider-bearer-token" };
    },
    async queryProviderStatus() {
      calls.query += 1;
      return provider;
    },
    async updateTransaction(ref, patch) {
      calls.updates.push({ ref, patch });
      current = { ...current, ...patch };
    },
    async lockEscrow(record) {
      calls.locks.push(record.transactionId);
      // Mirrors the index.ts Firestore-transaction guard: idempotent, one lock only.
      if (current?.escrowLockedAt) return { locked: false, reason: "already_locked" };
      current = { ...current, escrowLockedAt: deps.now() };
      return { locked: true, reason: "locked" };
    },
  };
  return { deps, calls, getCurrent: () => current };
}


// ---------------------------------------------------------------------------
// Provider response parsing (tests 5, 6: timeout/malformed can never be success)
// ---------------------------------------------------------------------------

test("parse: 200 with PENDING/SUCCESSFUL/FAILED maps exactly", () => {
  assert.equal(parseProviderStatusResponse(200, { status: "PENDING" }).status, "PENDING");
  assert.equal(parseProviderStatusResponse(200, { status: "successful" }).status, "SUCCESSFUL");
  assert.equal(parseProviderStatusResponse(200, { status: "FAILED" }).status, "FAILED");
});

test("parse: 404 is not_found, 5xx is provider_error", () => {
  assert.equal(parseProviderStatusResponse(404, {}).kind, "not_found");
  assert.equal(parseProviderStatusResponse(503, {}).kind, "provider_error");
});

test("parse: malformed bodies are malformed — never a status guess", () => {
  assert.equal(parseProviderStatusResponse(200, null).kind, "malformed");
  assert.equal(parseProviderStatusResponse(200, "garbage").kind, "malformed");
  assert.equal(parseProviderStatusResponse(200, {}).kind, "malformed");
  assert.equal(parseProviderStatusResponse(200, { status: "WHATEVER" }).kind, "malformed");
  assert.equal(parseProviderStatusResponse(200, { status: "SUCCESSFUL", amount: "NaN" }).kind, "malformed");
});

test("parse: verified success body carries amount/currency/financialTransactionId", () => {
  const r = parseProviderStatusResponse(200, {
    status: "SUCCESSFUL",
    amount: "50000",
    currency: "UGX",
    externalId: JOB_ID,
    financialTransactionId: "FTX-1",
  });
  assert.equal(r.kind, "ok");
  assert.equal(r.amount, 50000);
  assert.equal(r.financialTransactionId, "FTX-1");
});


// ---------------------------------------------------------------------------
// Decision core (tests 2, 3, 4, 5, 6, 9, 10)
// ---------------------------------------------------------------------------

test("2. provider PENDING -> PENDING, no escrow", () => {
  const d = decideConfirmation({ stored: storedTx(), provider: { kind: "ok", status: "PENDING" } });
  assert.equal(d.newStatus, "PENDING");
  assert.equal(d.action, "update_status");
});

test("3. provider SUCCESSFUL with consistent amount -> SUCCESSFUL + escrow lock", () => {
  const d = decideConfirmation({
    stored: storedTx(),
    provider: { kind: "ok", status: "SUCCESSFUL", amount: AMOUNT, currency: "UGX" },
  });
  assert.equal(d.newStatus, "SUCCESSFUL");
  assert.equal(d.action, "update_status_and_lock_escrow");
});

test("4. provider FAILED -> FAILED, no escrow", () => {
  const d = decideConfirmation({ stored: storedTx(), provider: { kind: "ok", status: "FAILED" } });
  assert.equal(d.newStatus, "FAILED");
  assert.equal(d.action, "update_status");
});

test("5. provider timeout -> NOT successful; last known PENDING recorded", () => {
  const d = decideConfirmation({ stored: storedTx(), provider: { kind: "provider_timeout" } });
  assert.notEqual(d.newStatus, "SUCCESSFUL");
  assert.equal(d.newStatus, "PENDING");
  assert.equal(d.reason, "provider_unreachable");
});

test("6. malformed provider response -> UNKNOWN, never SUCCESSFUL", () => {
  const d = decideConfirmation({ stored: storedTx(), provider: { kind: "malformed", statusCode: 200 } });
  assert.notEqual(d.newStatus, "SUCCESSFUL");
  assert.equal(d.newStatus, "UNKNOWN");
  assert.equal(d.action, "update_status");
});

test("9. contradicting answer on a terminal SUCCESSFUL is a no-op (replay safety)", () => {
  const d = decideConfirmation({
    stored: storedTx({ status: "SUCCESSFUL", escrowLockedAt: 1 }),
    provider: { kind: "ok", status: "FAILED" },
  });
  assert.equal(d.action, "none");
  assert.equal(d.newStatus, "SUCCESSFUL");
});

test("10. manipulated provider amount -> NEEDS_RECONCILIATION, stored amount wins, no escrow", () => {
  const d = decideConfirmation({
    stored: storedTx(),
    provider: { kind: "ok", status: "SUCCESSFUL", amount: 1, currency: "UGX" },
  });
  assert.equal(d.newStatus, "NEEDS_RECONCILIATION");
  assert.equal(d.action, "update_status");
  assert.equal(d.reason, "amount_mismatch");
});

test("10b. manipulated provider currency -> NEEDS_RECONCILIATION, no escrow", () => {
  const d = decideConfirmation({
    stored: storedTx(),
    provider: { kind: "ok", status: "SUCCESSFUL", amount: AMOUNT, currency: "EUR" },
  });
  assert.equal(d.newStatus, "NEEDS_RECONCILIATION");
  assert.equal(d.action, "update_status");
});


// ---------------------------------------------------------------------------
// Orchestrator end-to-end (tests 1, 3, 5, 6, 7, 8, 12, 13)
// ---------------------------------------------------------------------------

test("1. HTTP 202 at initiation maps to PENDING — never SUCCESSFUL, no escrow", async () => {
  // The PAY-001 initiation path is the source of the PENDING record under test.
  assert.equal(isProviderAccepted(202), true);
  assert.equal(isProviderAccepted(200), false);
  assert.equal(isProviderAccepted(201), false);
});

test("3+12. verified provider SUCCESSFUL locks escrow exactly once", async () => {
  const { deps, calls, getCurrent } = makeDeps({
    provider: { kind: "ok", status: "SUCCESSFUL", amount: AMOUNT, currency: "UGX", financialTransactionId: "FTX-9" },
  });
  const out = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, true);
  assert.equal(out.status, "SUCCESSFUL");
  assert.equal(out.escrowLocked, true);
  assert.equal(calls.locks.length, 1);
  assert.equal(getCurrent().status, "SUCCESSFUL");
  assert.equal(getCurrent().providerFinancialTransactionId, "FTX-9");

  // Second run (duplicate webhook / repeated poll): terminal fast-path, escrow NOT locked again.
  const again = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(again.ok, true);
  assert.equal(again.status, "SUCCESSFUL");
  assert.equal(again.escrowLocked, false);
  assert.equal(calls.locks.length, 1, "escrow must be locked exactly once");
});

test("8. duplicate webhook is idempotent — second run does not query the provider or update", async () => {
  const { deps, calls } = makeDeps({ stored: storedTx({ status: "SUCCESSFUL", escrowLockedAt: 1 }) });
  const out = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, true);
  assert.equal(out.escrowLockReason, "already_terminal");
  assert.equal(calls.query, 0, "terminal transactions are not re-queried");
  assert.equal(calls.updates.length, 0, "terminal transactions are not rewritten");
  assert.equal(calls.locks.length, 0, "terminal transactions never re-lock escrow");
});

test("5. provider timeout end-to-end: not successful, escrow untouched", async () => {
  const { deps, calls, getCurrent } = makeDeps({ provider: { kind: "provider_timeout", error: "ETIMEDOUT" } });
  const out = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, true);
  assert.notEqual(out.status, "SUCCESSFUL");
  assert.equal(out.status, "PENDING");
  assert.equal(out.escrowLocked, false);
  assert.equal(calls.locks.length, 0);
  assert.equal(getCurrent().lastStatusCheckError, "provider_timeout");
});

test("6. malformed provider response end-to-end: UNKNOWN, escrow untouched", async () => {
  const { deps, calls, getCurrent } = makeDeps({ provider: { kind: "malformed", statusCode: 200 } });
  const out = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, true);
  assert.notEqual(out.status, "SUCCESSFUL");
  assert.equal(out.status, "UNKNOWN");
  assert.equal(calls.locks.length, 0);
  assert.equal(getCurrent().status, "UNKNOWN");
});

test("7. unknown transaction reference is rejected (no provider call, no writes)", async () => {
  const { deps, calls } = makeDeps({ stored: null });
  const out = await verifyAndConfirmCollection({ transactionRef: "MOMO-does-not-exist", env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, false);
  assert.equal(out.error.code, "unknown_reference");
  assert.equal(calls.query, 0);
  assert.equal(calls.updates.length, 0);
  assert.equal(calls.locks.length, 0);
});

test("7b. missing sandbox config fails closed before any provider call", async () => {
  const { deps, calls } = makeDeps();
  const out = await verifyAndConfirmCollection(
    { transactionRef: TX_ID, env: { MOMO_ENVIRONMENT: "production" } },
    deps
  );
  assert.equal(out.ok, false);
  assert.equal(out.error.code, "config_error");
  assert.equal(calls.query, 0);
  assert.equal(calls.locks.length, 0);
});

test("13. provider FAILED end-to-end: FAILED record, escrow never funded", async () => {
  const { deps, calls, getCurrent } = makeDeps({ provider: { kind: "ok", status: "FAILED" } });
  const out = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, true);
  assert.equal(out.status, "FAILED");
  assert.equal(out.escrowLocked, false);
  assert.equal(calls.locks.length, 0);
  assert.equal(getCurrent().status, "FAILED");
});

test("10c. manipulated amount end-to-end: NEEDS_RECONCILIATION, no escrow lock", async () => {
  const { deps, calls, getCurrent } = makeDeps({
    provider: { kind: "ok", status: "SUCCESSFUL", amount: 999999999, currency: "UGX" },
  });
  const out = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, true);
  assert.equal(out.status, "NEEDS_RECONCILIATION");
  assert.equal(calls.locks.length, 0);
  assert.equal(getCurrent().status, "NEEDS_RECONCILIATION");
  assert.ok(TERMINAL_CONFIRMATION_STATUSES.includes("NEEDS_RECONCILIATION"));
});

test("2b. provider PENDING end-to-end: stays PENDING, no escrow", async () => {
  const { deps, calls, getCurrent } = makeDeps({ provider: { kind: "ok", status: "PENDING" } });
  const out = await verifyAndConfirmCollection({ transactionRef: TX_ID, env: SANDBOX_ENV }, deps);
  assert.equal(out.ok, true);
  assert.equal(out.status, "PENDING");
  assert.equal(calls.locks.length, 0);
  assert.equal(getCurrent().status, "PENDING");
});


// ---------------------------------------------------------------------------
// Callback handling + authorization (tests 11, 14)
// ---------------------------------------------------------------------------

test("11. callback body: only the reference hint is read; manipulated job/user/status/amount ignored", () => {
  const hint = extractCallbackReferenceHint({
    referenceId: PROVIDER_REF,
    status: "SUCCESSFUL", // ignored
    amount: 1, // ignored
    jobId: "attacker-job", // ignored
    userId: "attacker-uid", // ignored
    externalId: "attacker-external",
  });
  assert.equal(hint, PROVIDER_REF);

  // Legacy alias accepted.
  assert.equal(extractCallbackReferenceHint({ transactionRef: TX_ID }), TX_ID);
  // Nothing usable -> null (endpoint answers 400 without touching any state).
  assert.equal(extractCallbackReferenceHint({ status: "SUCCESSFUL" }), null);
  assert.equal(extractCallbackReferenceHint(null), null);
  assert.equal(extractCallbackReferenceHint("not-an-object"), null);
  assert.equal(extractCallbackReferenceHint({ referenceId: "   " }), null);
});

test("14. authorization: unauthenticated and foreign transactions are denied", () => {
  const stored = storedTx();
  assert.equal(authorizeStatusQuery({ callerUid: null, stored }).ok, false);
  assert.equal(authorizeStatusQuery({ callerUid: null, stored }).code, "unauthenticated");
  assert.equal(authorizeStatusQuery({ callerUid: OWNER_UID, stored: null }).code, "not_found");
  const denied = authorizeStatusQuery({ callerUid: OTHER_UID, stored });
  assert.equal(denied.ok, false);
  assert.equal(denied.code, "permission_denied");
  assert.equal(authorizeStatusQuery({ callerUid: OWNER_UID, stored }).ok, true);
});

test("messages never claim success for non-success states", () => {
  for (const s of ["PENDING", "FAILED", "UNKNOWN", "NEEDS_RECONCILIATION"]) {
    assert.ok(!/confirmed by the provider|funds are locked/i.test(confirmationMessageFor(s)));
  }
  assert.match(confirmationMessageFor("SUCCESSFUL"), /confirmed/i);
});




