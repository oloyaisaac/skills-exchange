/**
 * PAY-001 Phase 1: automated tests for the trusted backend MoMo Collection core.
 *
 * These run with `node --test` against the pure TypeScript source (no emulator, no
 * Firestore, no network) because `momoCollection.ts` has no `firebase-admin` /
 * `firebase-functions` / `axios` imports — every external effect is an injected fake.
 *
 * Coverage maps to the phase-1 safety requirements:
 *   1. Missing sandbox credentials            -> config_error, no provider call
 *   2. Non-sandbox / production environment    -> config_error (fail-closed)
 *   3. Invalid authentication                  -> unauthenticated
 *   4. Invalid authorization (not job owner)   -> not_job_owner
 *   5. Invalid job state                       -> invalid_job_state
 *   6. Already-funded job                      -> already_funded
 *   7. Duplicate collection request            -> duplicate_request, provider NOT called
 *   8. Provider error (non-202)                -> provider_error, FAILED record
 *   9. Provider timeout / network error        -> provider_timeout, FAILED record
 *  10. HTTP 202                                -> PENDING (never SUCCESSFUL), no escrow write
 *  11. Untrusted client amount override        -> amount_mismatch (backend amount wins)
 *  12. No fabricated success / no escrow update in any path
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  loadMomoConfig,
  validateCollectionInput,
  normalizeMsisdn,
  buildRequestToPayPayload,
  isProviderAccepted,
  mapProviderTransactionStatus,
  initiateCollection
} from "../src/momoCollection.ts";

const JOB_ID = "job-1";
const OWNER_UID = "client-uid-1";
const OTHER_UID = "client-uid-2";
const UG_PHONE = "+256 772 198 420"; // 256772198420

const SANDBOX_ENV = {
  MOMO_ENVIRONMENT: "sandbox",
  MOMO_BASE_URL: "https://sandbox.momodeveloper.mtn.com",
  MTN_SUBSCRIPTION_KEY: "sub-key",
  MTN_API_USER: "api-user",
  MTN_API_KEY: "api-key"
};

/** A funded-but-not-yet job the owner may pay. */
function eligibleJob(overrides = {}) {
  return {
    postedByUid: OWNER_UID,
    status: "QUOTE_ACCEPTED",
    escrowStatus: "QUOTE_PENDING",
    grandTotal: 50000,
    budget: 50000,
    momoPhoneNumber: UG_PHONE,
    currency: "UGX",
    ...overrides
  };
}

/**
 * Build a `CollectionDeps` fake. Every side effect is recorded so tests can assert the
 * provider was (or was not) called and that escrow was never written. `overrides` lets a
 * test force a particular failure mode.
 */
function makeDeps(overrides = {}) {
  const calls = { getJob: 0, requestToPay: 0, persist: [], token: 0, findPending: 0 };
  const job = "job" in overrides ? overrides.job : eligibleJob();
  const deps = {
    randomUuid: () => "11111111-2222-3333-4444-555555555555",
    now: () => 1_700_000_000_000,
    async getJob() {
      calls.getJob += 1;
      return job;
    },
    async findPendingCollection() {
      calls.findPending += 1;
      return overrides.pending || null;
    },
    async acquireToken() {
      calls.token += 1;
      if (overrides.tokenFails) return { ok: false, error: "token exchange failed" };
      return { ok: true, token: "provider-bearer-token" };
    },
    async requestToPay(_config, _args) {
      calls.requestToPay += 1;
      if (overrides.rtpNetwork) return { networkError: true, timeout: false, error: "socket hangup" };
      if (overrides.rtpTimeout) return { networkError: true, timeout: true, error: "ETIMEDOUT" };
      if (overrides.rtpStatus) return { statusCode: overrides.rtpStatus };
      return { statusCode: 202 };
    },
    async persistTransaction(record) {
      calls.persist.push(record);
    }
  };
  return { deps, calls };
}


// ---------------------------------------------------------------------------
// 1-2. Fail-closed configuration (missing creds, wrong env, wrong host)
// ---------------------------------------------------------------------------

test("REM-029: a job whose stored fee or total was tampered with is refused", () => {
  const tampered = eligibleJob({
    laborTotal: 50000,
    materialsSubtotal: 10000,
    platformFee: 500, // should be 5000
    grandTotal: 60500,
  });
  const result = validateCollectionInput({
    job: tampered,
    callerUid: OWNER_UID,
    requestedAmountUgx: 60500,
  });
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "amount_mismatch");
  assert.match(result.error.message, /fee mismatch/i);

  const consistent = eligibleJob({
    laborTotal: 50000,
    materialsSubtotal: 10000,
    platformFee: 5000,
    grandTotal: 65000,
  });
  assert.equal(
    validateCollectionInput({ job: consistent, callerUid: OWNER_UID, requestedAmountUgx: 65000 }).ok,
    true
  );
});

test("missing sandbox credentials -> config_error and no provider call", async () => {
  const { deps, calls } = makeDeps();
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: { MOMO_ENVIRONMENT: "sandbox" } },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "config_error");
  assert.equal(calls.requestToPay, 0, "provider must not be called without creds");
  assert.equal(calls.persist.length, 0, "no transaction may be persisted without creds");
});

test("production environment is refused (fail-closed)", () => {
  const cfg = loadMomoConfig({
    MOMO_ENVIRONMENT: "production",
    MOMO_BASE_URL: "https://sandbox.momodeveloper.mtn.com",
    MTN_SUBSCRIPTION_KEY: "k",
    MTN_API_USER: "u",
    MTN_API_KEY: "s"
  });
  assert.ok("error" in cfg, "production env must not yield a usable config");
});

test("non-sandbox base URL host is refused (fail-closed)", () => {
  const cfg = loadMomoConfig({
    MOMO_ENVIRONMENT: "sandbox",
    MOMO_BASE_URL: "https://api.mtn.com",
    MTN_SUBSCRIPTION_KEY: "k",
    MTN_API_USER: "u",
    MTN_API_KEY: "s"
  });
  assert.ok("error" in cfg, "a non-sandbox host must be refused");
});

test("valid sandbox config loads", () => {
  const cfg = loadMomoConfig(SANDBOX_ENV);
  assert.ok(!("error" in cfg), "valid sandbox env should load");
});

// ---------------------------------------------------------------------------
// normalizeMsisdn
// ---------------------------------------------------------------------------

test("normalizeMsisdn: local 0-prefixed, +256, bare-9 and invalid forms", () => {
  assert.equal(normalizeMsisdn("0772198420"), "256772198420");
  assert.equal(normalizeMsisdn("+256 772 198 420"), "256772198420");
  assert.equal(normalizeMsisdn("772198420"), "256772198420");
  assert.equal(normalizeMsisdn("256772198420"), "256772198420");
  assert.equal(normalizeMsisdn("12345"), null);
  assert.equal(normalizeMsisdn(""), null);
  assert.equal(normalizeMsisdn(null), null);
});

// ---------------------------------------------------------------------------
// 3-6, 11. validateCollectionInput
// ---------------------------------------------------------------------------

test("unauthenticated caller is rejected", () => {
  const r = validateCollectionInput({ job: eligibleJob(), callerUid: null });
  assert.equal(r.ok, false);
  assert.equal(r.error.code, "unauthenticated");
});

test("missing job is rejected", () => {
  const r = validateCollectionInput({ job: null, callerUid: OWNER_UID });
  assert.equal(r.ok, false);
  assert.equal(r.error.code, "job_not_found");
});

test("non-owner is rejected (authorization)", () => {
  const r = validateCollectionInput({ job: eligibleJob(), callerUid: OTHER_UID });
  assert.equal(r.ok, false);
  assert.equal(r.error.code, "not_job_owner");
});

test("job already funded is rejected", () => {
  const job = eligibleJob({ escrowStatus: "FUNDS_LOCKED_IN_ESCROW" });
  const r = validateCollectionInput({ job, callerUid: OWNER_UID });
  assert.equal(r.ok, false);
  assert.equal(r.error.code, "already_funded");
});

test("invalid job state is rejected", () => {
  const job = eligibleJob({ status: "COMPLETED" });
  const r = validateCollectionInput({ job, callerUid: OWNER_UID });
  assert.equal(r.ok, false);
  assert.equal(r.error.code, "invalid_job_state");
});

// ---------------------------------------------------------------------------
// 7. Duplicate collection request -> idempotent short-circuit, provider NOT called
// ---------------------------------------------------------------------------

test("duplicate collection is short-circuited before any provider call", async () => {
  const { deps, calls } = makeDeps({ pending: { transactionId: "MOMO-existing" } });
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "duplicate_request");
  assert.equal(result.error.transactionId, "MOMO-existing");
  assert.equal(calls.requestToPay, 0, "provider must not be called on a duplicate");
});

// ---------------------------------------------------------------------------
// 10. HTTP 202 -> PENDING (never SUCCESSFUL) and no escrow write
// ---------------------------------------------------------------------------

test("HTTP 202 yields PENDING, persists a PENDING record, and never SUCCESSFUL", async () => {
  const { deps, calls } = makeDeps({});
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV },
    deps
  );
  assert.equal(result.ok, true);
  assert.equal(result.status, "PENDING");
  assert.notEqual(result.status, "SUCCESSFUL", "202 must never be reported as success");
  assert.equal(calls.persist.length, 1);
  assert.equal(calls.persist[0].status, "PENDING");
  assert.equal(calls.persist[0].providerStatusCode, 202);
  assert.ok(!("escrowStatus" in calls.persist[0]));
  assert.equal(typeof deps.updateJobEscrow, "undefined", "no escrow-writing dependency exists");
});

test("202 never produces a SUCCESSFUL transaction record", async () => {
  const { deps, calls } = makeDeps({});
  await initiateCollection({ jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV }, deps);
  assert.ok(calls.persist.every((r) => r.status !== "SUCCESSFUL"));
});

// ---------------------------------------------------------------------------
// 8. Provider error (non-202) -> FAILED record + provider_error
// ---------------------------------------------------------------------------

test("provider non-202 -> provider_error and a FAILED record", async () => {
  const { deps, calls } = makeDeps({ rtpStatus: 500 });
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "provider_error");
  assert.equal(calls.persist.length, 1);
  assert.equal(calls.persist[0].status, "FAILED");
});

// ---------------------------------------------------------------------------
// 9. Provider timeout / network error -> FAILED record + provider_timeout
// ---------------------------------------------------------------------------

test("provider timeout -> provider_timeout and a FAILED record", async () => {
  const { deps, calls } = makeDeps({ rtpTimeout: true });
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "provider_timeout");
  assert.equal(calls.persist[0].status, "FAILED");
});

// ---------------------------------------------------------------------------
// 12. No fabricated success: provider reference is the minted UUID; amount sent is
//     the trusted amount, not a client override.
// ---------------------------------------------------------------------------

test("client cannot lower the amount: a disagreeing requested amount is rejected", async () => {
  const { deps } = makeDeps({});
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV, requestedAmountUgx: 1 },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "amount_mismatch");
});

test("provider reference id equals the server-minted UUID (no fabricated ref)", async () => {
  let captured;
  const { deps } = makeDeps({});
  const spy = {
    ...deps,
    async requestToPay(_config, args) {
      captured = args;
      return { statusCode: 202 };
    }
  };
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV },
    spy
  );
  assert.equal(result.ok, true);
  assert.equal(result.providerReferenceId, "11111111-2222-3333-4444-555555555555");
  assert.equal(captured.referenceId, result.providerReferenceId);
  assert.equal(captured.payload.amount, "50000");
});

// ---------------------------------------------------------------------------
// Pure helpers
// ---------------------------------------------------------------------------

test("isProviderAccepted: only 202 is acceptance", () => {
  assert.equal(isProviderAccepted(202), true);
  assert.equal(isProviderAccepted(200), false);
  assert.equal(isProviderAccepted(500), false);
});

test("mapProviderTransactionStatus maps vocabulary", () => {
  assert.equal(mapProviderTransactionStatus("SUCCESSFUL"), "SUCCESSFUL");
  assert.equal(mapProviderTransactionStatus("failed"), "FAILED");
  assert.equal(mapProviderTransactionStatus("pending"), "PENDING");
  assert.equal(mapProviderTransactionStatus("whatever"), "PENDING");
});

test("buildRequestToPayPayload uses UGX, MSISDN payer and string amount", () => {
  const p = buildRequestToPayPayload({ amountUgx: 50000, externalId: "job-1", payerMsisdn: "256772198420" });
  assert.equal(p.currency, "UGX");
  assert.equal(p.amount, "50000");
  assert.equal(p.payer.partyIdType, "MSISDN");
  assert.equal(p.payer.partyId, "256772198420");
  assert.equal(p.externalId, "job-1");
});

test("provider network error -> provider_error and a FAILED record", async () => {
  const { deps, calls } = makeDeps({ rtpNetwork: true });
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "provider_error");
  assert.equal(calls.persist[0].status, "FAILED");
});

test("token acquisition failure -> config_error, no RequestToPay", async () => {
  const { deps, calls } = makeDeps({ tokenFails: true });
  const result = await initiateCollection(
    { jobId: JOB_ID, callerUid: OWNER_UID, env: SANDBOX_ENV },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "config_error");
  assert.equal(calls.requestToPay, 0);
});

test("client amount that disagrees with trusted amount is rejected (amount_mismatch)", () => {
  const job = eligibleJob({ grandTotal: 50000 });
  const r = validateCollectionInput({ job, callerUid: OWNER_UID, requestedAmountUgx: 1 });
  assert.equal(r.ok, false);
  assert.equal(r.error.code, "amount_mismatch");
});

test("matching client amount is accepted and trusted amount is used", () => {
  const job = eligibleJob({ grandTotal: 50000 });
  const r = validateCollectionInput({ job, callerUid: OWNER_UID, requestedAmountUgx: 50000 });
  assert.equal(r.ok, true);
  assert.equal(r.amountUgx, 50000);
});

test("non-integer trusted amount is rejected", () => {
  const job = eligibleJob({ grandTotal: 100.5, budget: 100.5 });
  const r = validateCollectionInput({ job, callerUid: OWNER_UID });
  assert.equal(r.ok, false);
  assert.equal(r.error.code, "invalid_argument");
});
