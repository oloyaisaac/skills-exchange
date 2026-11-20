/**
 * R08-P2-04: one disbursement core, two money movements.
 *
 * The reviewer found two payout implementations with different money semantics: the escrow release
 * wrote `momo_payouts` with status QUEUED and never called the provider, while `initiatePayout` had a
 * real (but different) implementation. Both now go through `submitDisbursement`, and a wallet cashout
 * reaches the same code through `initiateWithdrawalPayout`.
 *
 * These tests pin the shared semantics: fail-closed config, server-minted reference, 202 => PENDING,
 * anything else => FAILED, and no fabricated success.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  initiatePayout,
  initiateWithdrawalPayout,
  submitDisbursement,
  validatePayoutInput,
  buildPayoutRequestPayload,
} from "../lib/momoDisbursement.js";

const OWNER_UID = "client-uid-1";
const WORKER_ID = "worker-uid-1";
const WORKER_MSISDN = "+256770000001";
const AMOUNT = 120000;

const SANDBOX_ENV = {
  MOMO_ENVIRONMENT: "sandbox",
  MOMO_BASE_URL: "https://sandbox.momodeveloper.mtn.com",
  MTN_SUBSCRIPTION_KEY: "sub-key",
  MTN_API_USER: "api-user",
  MTN_API_KEY: "api-key",
};

function fundedJob(overrides = {}) {
  return {
    postedByUid: OWNER_UID,
    status: "COMPLETED",
    escrowStatus: "FUNDS_LOCKED_IN_ESCROW",
    laborTotal: AMOUNT,
    materialsSubtotal: 40000,
    assignedProviderId: WORKER_ID,
    currency: "UGX",
    ...overrides,
  };
}

function makeDeps({ job = fundedJob(), pending = null, providerStatus = 202, tokenFails = false } = {}) {
  const calls = { token: 0, request: 0, persists: [], findPending: [] };
  return {
    calls,
    deps: {
      now: () => 1_700_000_000_000,
      randomUuid: () => "ref-uuid-0001",
      async findPendingPayout(jobId, clientUid, kind) {
        calls.findPending.push({ jobId, clientUid, kind });
        return pending ? { ...pending } : null;
      },
      async getJob() {
        return job ? { ...job } : null;
      },
      async acquireToken() {
        calls.token += 1;
        if (tokenFails) return { ok: false, error: "token exchange failed" };
        return { ok: true, token: "provider-bearer-token" };
      },
      async requestPayout() {
        calls.request += 1;
        if (providerStatus === "network") return { networkError: true, timeout: false, error: "connection refused" };
        if (providerStatus === "timeout") return { networkError: true, timeout: true, error: "timed out" };
        return { statusCode: providerStatus };
      },
      async queryProviderPayoutStatus() {
        return { kind: "ok", status: "PENDING" };
      },
      async persistPayout(record) {
        calls.persists.push(record);
      },
      async updatePayout() {},
    },
  };
}

// ------------------------------------------------------------------ validation

test("payee: a server-resolved worker satisfies the assignment check", () => {
  const ok = validatePayoutInput({
    job: fundedJob(),
    callerUid: OWNER_UID,
    requestedAmountUgx: AMOUNT,
    workerMomoNumber: WORKER_MSISDN,
    payeeWorkerId: WORKER_ID,
    payeeResolvedServerSide: true,
  });
  assert.equal(ok.ok, true);
  assert.equal(ok.workerId, WORKER_ID);

  const stranger = validatePayoutInput({
    job: fundedJob(),
    callerUid: OWNER_UID,
    requestedAmountUgx: AMOUNT,
    workerMomoNumber: WORKER_MSISDN,
    payeeWorkerId: "someone-else",
    payeeResolvedServerSide: true,
  });
  assert.equal(stranger.ok, false);
  assert.equal(stranger.error.code, "worker_mismatch");
});

test("payee: an arbitrary number is rejected unless the server resolved it", () => {
  const fromClient = validatePayoutInput({
    job: fundedJob({ momoPhoneNumber: "+256700000009" }),
    callerUid: OWNER_UID,
    requestedAmountUgx: AMOUNT,
    workerMomoNumber: WORKER_MSISDN,
    payeeWorkerId: WORKER_ID,
  });
  assert.equal(fromClient.ok, false);
  assert.equal(fromClient.error.code, "worker_mismatch");

  const serverResolved = validatePayoutInput({
    job: fundedJob({ momoPhoneNumber: "+256700000009" }),
    callerUid: OWNER_UID,
    requestedAmountUgx: AMOUNT,
    workerMomoNumber: WORKER_MSISDN,
    payeeWorkerId: WORKER_ID,
    payeeResolvedServerSide: true,
  });
  assert.equal(serverResolved.ok, true);
});

test("amount: an advance is validated against its own amount, not the labour total", () => {
  const advance = validatePayoutInput({
    job: fundedJob(),
    callerUid: OWNER_UID,
    requestedAmountUgx: 20000,
    workerMomoNumber: WORKER_MSISDN,
    payeeWorkerId: WORKER_ID,
    payeeResolvedServerSide: true,
    expectedAmountUgx: 20000,
  });
  assert.equal(advance.ok, true, "the advance used to fail with amount_mismatch every time");

  const inflated = validatePayoutInput({
    job: fundedJob(),
    callerUid: OWNER_UID,
    requestedAmountUgx: 40000,
    workerMomoNumber: WORKER_MSISDN,
    payeeWorkerId: WORKER_ID,
    payeeResolvedServerSide: true,
    expectedAmountUgx: 20000,
  });
  assert.equal(inflated.ok, false);
  assert.equal(inflated.error.code, "amount_mismatch");

  const releaseDefault = validatePayoutInput({
    job: fundedJob(),
    callerUid: OWNER_UID,
    requestedAmountUgx: 99999,
    workerMomoNumber: WORKER_MSISDN,
    payeeWorkerId: WORKER_ID,
    payeeResolvedServerSide: true,
  });
  assert.equal(releaseDefault.error.code, "amount_mismatch", "the labour total stays the default");
});

// ------------------------------------------------------------- shared core rules

test("core: malformed amounts and payees never reach the provider", async () => {
  const { deps, calls } = makeDeps();
  for (const amount of [0, -100, 10.5, Number.NaN]) {
    const result = await submitDisbursement(
      { payoutId: "P-1", jobId: "job-1", externalId: "job-1", workerId: WORKER_ID, workerMomoNumber: WORKER_MSISDN, clientUid: OWNER_UID, amountUgx: amount, env: SANDBOX_ENV },
      deps
    );
    assert.equal(result.ok, false);
    assert.equal(result.error.code, "invalid_argument");
  }
  const badPayee = await submitDisbursement(
    { payoutId: "P-1", jobId: "job-1", externalId: "job-1", workerId: WORKER_ID, workerMomoNumber: "12345", clientUid: OWNER_UID, amountUgx: 1000, env: SANDBOX_ENV },
    deps
  );
  assert.equal(badPayee.error.code, "worker_mismatch");
  assert.equal(calls.request, 0);
  assert.equal(calls.token, 0, "no token is fetched for an invalid order");
});

test("core: an unconfigured environment fails closed before any provider call", async () => {
  const { deps, calls } = makeDeps();
  const result = await submitDisbursement(
    { payoutId: "P-1", jobId: "job-1", externalId: "job-1", workerId: WORKER_ID, workerMomoNumber: WORKER_MSISDN, clientUid: OWNER_UID, amountUgx: 5000, env: { MOMO_ENVIRONMENT: "production" } },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "config_error");
  assert.equal(calls.request, 0);
});

test("core: 202 becomes PAYOUT_PENDING with a real reference, never success", async () => {
  const { deps, calls } = makeDeps({ providerStatus: 202 });
  const result = await submitDisbursement(
    { payoutId: "WD-1", jobId: "WD-1", externalId: "WD-1", workerId: WORKER_ID, workerMomoNumber: WORKER_MSISDN, clientUid: OWNER_UID, amountUgx: 5000, env: SANDBOX_ENV, payoutKind: "CASHOUT" },
    deps
  );
  assert.equal(result.ok, true);
  assert.equal(result.status, "PAYOUT_PENDING");
  assert.equal(result.providerReferenceId, "ref-uuid-0001");
  assert.equal(calls.persists.length, 1);
  assert.equal(calls.persists[0].status, "PAYOUT_PENDING");
  assert.equal(calls.persists[0].payoutKind, "CASHOUT");
});

test("core: a non-202 or a network failure records FAILED and reports no money moved", async () => {
  for (const providerStatus of [500, 400, "network", "timeout"]) {
    const { deps, calls } = makeDeps({ providerStatus });
    const result = await submitDisbursement(
      { payoutId: "P-1", jobId: "job-1", externalId: "job-1", workerId: WORKER_ID, workerMomoNumber: WORKER_MSISDN, clientUid: OWNER_UID, amountUgx: 5000, env: SANDBOX_ENV },
      deps
    );
    assert.equal(result.ok, false);
    assert.equal(calls.persists.length, 1);
    assert.equal(calls.persists[0].status, "PAYOUT_FAILED");
  }
});

// ------------------------------------------------------------- cashouts

test("cashout: the payout record id is the withdrawal id, so a retry cannot pay twice", async () => {
  const { deps, calls } = makeDeps();
  const result = await initiateWithdrawalPayout(
    { withdrawalId: "WD-worker-req1", uid: WORKER_ID, amountUgx: 15000, msisdn: WORKER_MSISDN, env: SANDBOX_ENV },
    deps
  );
  assert.equal(result.ok, true);
  assert.equal(result.payoutId, "WD-worker-req1");
  assert.equal(calls.persists[0].withdrawalId, "WD-worker-req1");
  assert.equal(calls.persists[0].payoutKind, "CASHOUT");
  assert.equal(calls.findPending[0].kind, "CASHOUT", "the gate is scoped to cashouts");
});

test("cashout: a pending or terminal payout for the same withdrawal blocks a second transfer", async () => {
  const pending = await initiateWithdrawalPayout(
    { withdrawalId: "WD-1", uid: WORKER_ID, amountUgx: 15000, msisdn: WORKER_MSISDN, env: SANDBOX_ENV },
    makeDeps({ pending: { payoutId: "WD-1", status: "PAYOUT_PENDING" } }).deps
  );
  assert.equal(pending.ok, false);
  assert.equal(pending.error.code, "already_pending");

  const terminal = await initiateWithdrawalPayout(
    { withdrawalId: "WD-1", uid: WORKER_ID, amountUgx: 15000, msisdn: WORKER_MSISDN, env: SANDBOX_ENV },
    makeDeps({ pending: { payoutId: "WD-1", status: "PAYOUT_SUCCESSFUL" } }).deps
  );
  assert.equal(terminal.ok, false);
  assert.equal(terminal.error.code, "duplicate_request");
});

test("cashout: malformed requests are refused before the provider is contacted", async () => {
  const { deps, calls } = makeDeps();
  assert.equal((await initiateWithdrawalPayout({ withdrawalId: "", uid: WORKER_ID, amountUgx: 15000, msisdn: WORKER_MSISDN, env: SANDBOX_ENV }, deps)).error.code, "invalid_argument");
  assert.equal((await initiateWithdrawalPayout({ withdrawalId: "WD-1", uid: "", amountUgx: 15000, msisdn: WORKER_MSISDN, env: SANDBOX_ENV }, deps)).error.code, "unauthenticated");
  assert.equal((await initiateWithdrawalPayout({ withdrawalId: "WD-1", uid: WORKER_ID, amountUgx: 0.5, msisdn: WORKER_MSISDN, env: SANDBOX_ENV }, deps)).error.code, "invalid_argument");
  assert.equal(calls.token, 0);
});

// ------------------------------------------------------------- release front end

test("release: the escrow release now reaches the provider and persists the accepted payout", async () => {
  const { deps, calls } = makeDeps();
  const result = await initiatePayout(
    {
      jobId: "job-1",
      clientUid: OWNER_UID,
      amountUgx: AMOUNT,
      workerMomoNumber: WORKER_MSISDN,
      payeeResolvedServerSide: true,
      payeeWorkerId: WORKER_ID,
      expectedAmountUgx: AMOUNT,
      payoutId: "PAYOUT-job-1",
      env: SANDBOX_ENV,
    },
    deps
  );
  assert.equal(result.ok, true, "the old path never called the provider at all");
  assert.equal(calls.request, 1);
  assert.equal(calls.persists[0].payoutId, "PAYOUT-job-1");
  assert.equal(calls.persists[0].payoutKind, "RELEASE");
  assert.equal(calls.persists[0].jobId, "job-1");
  assert.equal(calls.persists[0].amount, AMOUNT);
  assert.equal(calls.findPending[0].kind, "RELEASE", "an advance for the same job must not block this");
});

test("release: a provider failure leaves the escrow untouched (nothing is reported as paid)", async () => {
  const { deps } = makeDeps({ providerStatus: 503 });
  const result = await initiatePayout(
    {
      jobId: "job-1",
      clientUid: OWNER_UID,
      amountUgx: AMOUNT,
      workerMomoNumber: WORKER_MSISDN,
      payeeResolvedServerSide: true,
      payeeWorkerId: WORKER_ID,
      env: SANDBOX_ENV,
    },
    deps
  );
  assert.equal(result.ok, false);
  assert.equal(result.error.code, "provider_error");
});

test("payload: the provider request carries the payee and a traceable external id", () => {
  const payload = buildPayoutRequestPayload({ amount: 5000, workerMomoNumber: WORKER_MSISDN, externalId: "job-1" });
  assert.equal(payload.payeeMsisdn, WORKER_MSISDN);
  assert.equal(payload.externalId, "job-1");
  assert.equal(payload.currency, "UGX");
  assert.equal(payload.amount, 5000);
});
