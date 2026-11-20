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

/**
 * R08-P2-01: the release is only a release when the provider accepted it.
 *
 * This environment has no MTN sandbox credentials, so the provider path fails closed. That is the
 * property worth testing here: the handler must refuse, leave the escrow funded and write no payout,
 * rather than the old behaviour of marking the worker paid and queueing a document nobody read.
 */
test("an unconfigured provider refuses the release and nothing is marked paid", async () => {
  await reset();
  await seedJob();

  await assert.rejects(
    callable.release({ jobId: "job_1", workerId: "worker_1" }, { auth: { uid: "owner_1" } }),
    (error) => {
      assert.equal(error.code, "failed-precondition", "a missing provider configuration is a precondition");
      assert.match(error.message, /sandbox|MOMO_/i);
      return true;
    }
  );

  const job = (await db.collection("jobs").doc("job_1").get()).data();
  assert.equal(job.escrowStatus, "FUNDS_LOCKED_IN_ESCROW", "the escrow stays funded so the release can be retried");
  assert.equal(job.releasePayoutRef, undefined, "no payout reference is recorded");
  assert.equal(job.releasedAt, undefined);

  assert.equal((await db.collection("payouts").get()).size, 0, "no provider payout record exists");
  assert.equal((await db.collection("momo_payouts").get()).size, 0, "no legacy payout record is queued either");
});

test("a retried release still refuses and never creates a second record", async () => {
  await reset();
  await seedJob();

  await assert.rejects(callable.release({ jobId: "job_1", workerId: "worker_1" }, { auth: { uid: "owner_1" } }));
  await assert.rejects(callable.release({ jobId: "job_1", workerId: "worker_1" }, { auth: { uid: "owner_1" } }));

  assert.equal((await db.collection("payouts").get()).size, 0);
  assert.equal((await db.collection("momo_payouts").get()).size, 0);
  const job = (await db.collection("jobs").doc("job_1").get()).data();
  assert.equal(job.escrowStatus, "FUNDS_LOCKED_IN_ESCROW");
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

test("createMoMoPayout is the same core: a client amount and wallet cannot buy a payout", async () => {
  await reset();
  await seedJob();

  await assert.rejects(
    callable.legacyPayout(
      {
        jobId: "job_1",
        workerId: "worker_1",
        amountUgx: 9_999_999,
        workerMomoNumber: "+256799999999",
      },
      { auth: { uid: "owner_1" } }
    ),
    (error) => {
      // The alias reaches the same fail-closed provider path as the release: it cannot pay out at
      // all here, and nothing it was told by the client appears in any document.
      assert.equal(error.code, "failed-precondition");
      return true;
    }
  );

  assert.equal((await db.collection("payouts").get()).size, 0);
  assert.equal((await db.collection("momo_payouts").get()).size, 0, "no payout carries the client's amount");
  const job = (await db.collection("jobs").doc("job_1").get()).data();
  assert.equal(job.escrowStatus, "FUNDS_LOCKED_IN_ESCROW");
});
