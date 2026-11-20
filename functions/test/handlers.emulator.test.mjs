/**
 * R03: handler-level tests for the money callables, executed against the Firestore emulator.
 *
 * These call the real exported handlers through `firebase-functions-test`, so the whole path runs:
 * authentication context, Firestore reads, the shared release core and the documents it writes.
 * They are run with `npm run test:handlers` under `firebase emulators:exec --only firestore`.
 */
const PROJECT_ID = process.env.GCLOUD_PROJECT || "demo-skills-exchange";
process.env.GCLOUD_PROJECT = PROJECT_ID;
process.env.GOOGLE_CLOUD_PROJECT = PROJECT_ID;
process.env.FIREBASE_CONFIG = process.env.FIREBASE_CONFIG || JSON.stringify({ projectId: PROJECT_ID });
process.env.FIRESTORE_EMULATOR_HOST = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";

import test from "node:test";
import assert from "node:assert/strict";
import admin from "firebase-admin";
import functionsTest from "firebase-functions-test";

// `../lib/index.js` initialises the default admin app itself (as it does in Cloud Functions), so the
// test must not initialise it first: doing so would raise app/duplicate-app.
const { releaseEscrowToWorker, createMoMoPayout } = await import("../lib/index.js");

const ft = functionsTest({ projectId: PROJECT_ID });

const callable = {
  release: ft.wrap(releaseEscrowToWorker),
  legacyPayout: ft.wrap(createMoMoPayout),
};

const db = admin.firestore();

async function seedJob(overrides = {}) {
  await db.collection("jobs").doc("job_1").set({
    postedByUid: "owner_1",
    assignedProviderId: "worker_1",
    status: "COMPLETED",
    escrowStatus: "FUNDS_LOCKED_IN_ESCROW",
    escrowAmount: 50_000,
    ...overrides,
  });
  await db.collection("users").doc("worker_1").set({
    momoNumber: "+256700000000",
    phone: "+256700000000",
  });
}

async function reset() {
  await db.recursiveDelete(db.collection("jobs"));
  await db.recursiveDelete(db.collection("users"));
  await db.recursiveDelete(db.collection("momo_payouts"));
  await db.recursiveDelete(db.collection("notifications"));
}

test("owner releases a funded job: payout and job documents are written", async () => {
  await reset();
  await seedJob();

  const result = await callable.release(
    { jobId: "job_1", workerId: "worker_1" },
    { auth: { uid: "owner_1" } }
  );

  assert.equal(result.success, true);
  const payout = (await db.collection("momo_payouts").doc("PAYOUT-job_1").get()).data();
  assert.equal(payout.amount, 50_000);
  assert.equal(payout.workerMomoNumber, "+256700000000");

  const job = (await db.collection("jobs").doc("job_1").get()).data();
  assert.equal(job.escrowStatus, "RELEASED_TO_WORKER");
  assert.equal(job.releasePayoutRef, "PAYOUT-job_1");
});

test("a repeat release is idempotent", async () => {
  await reset();
  await seedJob();

  await callable.release({ jobId: "job_1", workerId: "worker_1" }, { auth: { uid: "owner_1" } });
  const again = await callable.release(
    { jobId: "job_1", workerId: "worker_1" },
    { auth: { uid: "owner_1" } }
  );

  assert.equal(again.alreadyReleased, true);
  const payouts = await db.collection("momo_payouts").get();
  assert.equal(payouts.size, 1);
});

test("a non-owner cannot release escrow", async () => {
  await reset();
  await seedJob();

  await assert.rejects(
    callable.release({ jobId: "job_1", workerId: "worker_1" }, { auth: { uid: "someone_else" } }),
    /Only the job owner can release escrow/
  );
  assert.equal((await db.collection("momo_payouts").get()).size, 0);
});

test("an unfunded job cannot be released", async () => {
  await reset();
  await seedJob({ escrowStatus: "QUOTE_PENDING" });

  await assert.rejects(
    callable.release({ jobId: "job_1", workerId: "worker_1" }, { auth: { uid: "owner_1" } }),
    /Escrow is not funded/
  );
});

test("createMoMoPayout ignores a client-supplied amount and wallet", async () => {
  await reset();
  await seedJob();

  const result = await callable.legacyPayout(
    {
      jobId: "job_1",
      workerId: "worker_1",
      amountUgx: 9_999_999,
      workerMomoNumber: "+256799999999",
    },
    { auth: { uid: "owner_1" } }
  );

  assert.equal(result.success, true);
  const payout = (await db.collection("momo_payouts").doc("PAYOUT-job_1").get()).data();
  assert.equal(payout.amount, 50_000, "the server amount wins");
  assert.equal(payout.workerMomoNumber, "+256700000000", "the stored wallet wins");
});
