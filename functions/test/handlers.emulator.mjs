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
const {
  releaseEscrowToWorker,
  createMoMoPayout,
  fileReport,
  getReportsQueue,
  resolveReport,
  submitRating,
  getSosDispatchStatus,
  getSafetyMetrics,
} = await import("../lib/index.js");

const ft = functionsTest({ projectId: PROJECT_ID });

const callable = {
  release: ft.wrap(releaseEscrowToWorker),
  legacyPayout: ft.wrap(createMoMoPayout),
  // R11-P2-06: the safety callables are exercised through their real handlers too, so the path that
  // runs in production (auth context, Firestore reads and writes, the pure core) is what is tested.
  fileReport: ft.wrap(fileReport),
  reportsQueue: ft.wrap(getReportsQueue),
  resolveReport: ft.wrap(resolveReport),
  submitRating: ft.wrap(submitRating),
  sosDispatch: ft.wrap(getSosDispatchStatus),
  safetyMetrics: ft.wrap(getSafetyMetrics),
};

/** An admin caller: the `admin` claim, exactly as `checkAdminCaller` reads it. */
const ADMIN = { auth: { uid: "admin_1", token: { admin: true } } };
const RESIDENT = { auth: { uid: "resident_1", token: {} } };
const WORKER = { auth: { uid: "worker_1", token: {} } };
const STRANGER = { auth: { uid: "stranger_1", token: {} } };

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
  // R11: the safety collections too, so one test cannot inherit another's rows.
  await db.recursiveDelete(db.collection("reports"));
  await db.recursiveDelete(db.collection("ratings"));
  await db.recursiveDelete(db.collection("worker_stats"));
  await db.recursiveDelete(db.collection("sosIncidents"));
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

// ============================================================================
// R11-P2-06: handler-level tests for the safety callables.
//
// The pure cores (reports.ts, ratingsCore.ts, sosStatus.ts) already have unit
// tests. What was missing was proof that the *handlers* wire them to identity,
// Firestore and the response — the part a client actually reaches.
// ============================================================================

const DETAILS = "Threatened me at the gate after the job was finished.";

async function seedCompletedJob(overrides = {}) {
  await db.collection("jobs").doc("job_r1").set({
    postedByUid: "resident_1",
    assignedProviderUid: "worker_1",
    status: "COMPLETED",
    escrowStatus: "RELEASED",
    ...overrides,
  });
}

test("an unauthenticated caller cannot file a report", async () => {
  await reset();
  await assert.rejects(
    callable.fileReport({ subjectUid: "worker_1", category: "SAFETY", details: DETAILS }, {}),
    (error) => {
      assert.equal(error.code, "unauthenticated");
      return true;
    }
  );
  assert.equal((await db.collection("reports").get()).size, 0, "nothing is stored without a caller");
});

test("a SAFETY report is written with the server's priority and pages the safety desk", async () => {
  await reset();
  const result = await callable.fileReport(
    { subjectUid: "worker_1", subjectName: "Worker One", category: "SAFETY", details: DETAILS, jobId: "job_x" },
    RESIDENT
  );

  assert.equal(result.priority, "P0", "SAFETY is always P0");
  assert.equal(result.status, "OPEN");
  assert.match(result.message, /operator will review/i, "the reporter is told what happens next");

  const stored = (await db.collection("reports").doc(result.id).get()).data();
  assert.equal(stored.reportedBy, "resident_1", "the reporter is the authenticated uid, not a claim");
  assert.equal(stored.subjectUid, "worker_1");
  assert.equal(stored.status, "OPEN");
  assert.ok(stored.slaDueAt - stored.createdAt === 60 * 60 * 1000, "SAFETY has a one-hour first-response target");

  const paged = await db.collection("notifications").where("userId", "==", "safety_desk").get();
  assert.equal(paged.size, 1, "a P0 report is paged to the desk on the same channel SOS uses");
});

test("a report about yourself is refused and nothing is stored", async () => {
  await reset();
  await assert.rejects(
    callable.fileReport({ subjectUid: "resident_1", category: "FRAUD", details: DETAILS }, RESIDENT),
    /cannot report itself/
  );
  assert.equal((await db.collection("reports").get()).size, 0);
});

test("a report with no explanation is refused", async () => {
  await reset();
  await assert.rejects(
    callable.fileReport({ subjectUid: "worker_1", category: "SAFETY", details: "bad" }, RESIDENT),
    /at least a sentence|describe/i
  );
  assert.equal((await db.collection("reports").get()).size, 0);
});

test("the report queue is admin-only", async () => {
  await reset();
  await callable.fileReport({ subjectUid: "worker_1", category: "SAFETY", details: DETAILS }, RESIDENT);

  await assert.rejects(callable.reportsQueue({}, RESIDENT), (error) => {
    assert.equal(error.code, "permission-denied", "a signed-in non-admin is not an operator");
    return true;
  });
  await assert.rejects(callable.reportsQueue({}, { auth: { uid: "resident_1", token: { admin: false } } }));

  const queue = await callable.reportsQueue({}, ADMIN);
  assert.equal(queue.reports.length, 1);
  assert.equal(queue.reports[0].priority, "P0");
  assert.equal(queue.breachingSla, false, "a fresh report is inside its window");
  assert.equal(queue.operatorUid, "admin_1");
});

test("an operator moves a report forward and the sentence names the SLA verdict", async () => {
  await reset();
  const filed = await callable.fileReport(
    { subjectUid: "worker_1", category: "SAFETY", details: DETAILS },
    RESIDENT
  );

  const decided = await callable.resolveReport(
    { reportId: filed.id, status: "TRIAGING", outcome: "called the reporter" },
    ADMIN
  );
  assert.equal(decided.status, "TRIAGING");
  assert.match(decided.message, /moved to triage/);
  assert.match(decided.message, /1-hour target/);

  const stored = (await db.collection("reports").doc(filed.id).get()).data();
  assert.equal(stored.triagedBy, "admin_1", "the decision is attributed to the operator");
  assert.ok(stored.triagedAt > 0);
});

test("a closed report cannot be reopened by another call", async () => {
  await reset();
  const filed = await callable.fileReport(
    { subjectUid: "worker_1", category: "FRAUD", details: DETAILS },
    RESIDENT
  );
  await callable.resolveReport(
    { reportId: filed.id, status: "ACTIONED", outcome: "reporter supported; account restricted" },
    ADMIN
  );

  await assert.rejects(
    callable.resolveReport({ reportId: filed.id, status: "TRIAGING", outcome: "looking again" }, ADMIN),
    /already closed/
  );
  const stored = (await db.collection("reports").doc(filed.id).get()).data();
  assert.equal(stored.status, "ACTIONED", "the closed state survives the refused call");
});

test("a stranger cannot rate a job they are not part of", async () => {
  await reset();
  await seedCompletedJob();

  await assert.rejects(
    callable.submitRating({ jobId: "job_r1", subjectUid: "worker_1", stars: 5 }, STRANGER),
    /resident and the worker/
  );
  assert.equal((await db.collection("ratings").get()).size, 0);
  assert.equal((await db.collection("worker_stats").get()).size, 0);
});

test("a completed job is rated once and the aggregate is derived on the server", async () => {
  await reset();
  await seedCompletedJob();

  const first = await callable.submitRating(
    { jobId: "job_r1", subjectUid: "worker_1", stars: 5, review: "Careful and on time." },
    RESIDENT
  );
  assert.equal(first.recorded, true);
  assert.equal(first.average, 5);
  assert.equal(first.ratingId, "job_r1_resident_1");

  const stats = (await db.collection("worker_stats").doc("worker_1").get()).data();
  assert.equal(stats.count, 1);
  assert.equal(stats.sum, 5);
  assert.equal(stats.average, 5);

  const rating = (await db.collection("ratings").doc("job_r1_resident_1").get()).data();
  assert.equal(rating.fromUid, "resident_1");
  assert.equal(rating.toUid, "worker_1");
  assert.equal(rating.stars, 5);
});

test("a second rating in the same direction is refused and changes no aggregate", async () => {
  await reset();
  await seedCompletedJob();
  await callable.submitRating({ jobId: "job_r1", subjectUid: "worker_1", stars: 5 }, RESIDENT);

  await assert.rejects(
    callable.submitRating({ jobId: "job_r1", subjectUid: "worker_1", stars: 1 }, RESIDENT),
    /already/i
  );
  assert.equal((await db.collection("ratings").get()).size, 1, "one record per job per direction");
  const stats = (await db.collection("worker_stats").doc("worker_1").get()).data();
  assert.equal(stats.count, 1, "the refused rating never reached the aggregate");
  assert.equal(stats.sum, 5);
});

test("an unfinished job cannot be rated", async () => {
  await reset();
  await seedCompletedJob({ status: "IN_PROGRESS" });

  await assert.rejects(
    callable.submitRating({ jobId: "job_r1", subjectUid: "worker_1", stars: 5 }, RESIDENT),
    /once the job is completed/
  );
  assert.equal((await db.collection("ratings").get()).size, 0);
});

test("a stranger cannot read an alert's dispatch outcome", async () => {
  await reset();
  await db.collection("sosIncidents").doc("sos_1").set({
    postedByUid: "resident_1",
    assignedProviderUid: "worker_1",
    status: "ACTIVE",
    raisedAt: Date.now(),
    dispatch: { status: "FAILED", recipients: 2, delivered: 0, attemptedAt: Date.now() },
  });

  await assert.rejects(callable.sosDispatch({ incidentId: "sos_1" }, STRANGER), (error) => {
    assert.equal(error.code, "permission-denied");
    return true;
  });
});

test("the owner is told plainly when no emergency message reached anyone", async () => {
  await reset();
  await db.collection("sosIncidents").doc("sos_1").set({
    postedByUid: "resident_1",
    assignedProviderUid: "worker_1",
    status: "ACTIVE",
    raisedAt: Date.now(),
    dispatch: { status: "FAILED", recipients: 2, delivered: 0, attemptedAt: Date.now() },
  });

  const described = await callable.sosDispatch({ incidentId: "sos_1" }, RESIDENT);
  assert.equal(described.state, "FAILED");
  assert.equal(described.adviseDirectCall, true);
  assert.match(described.message, /could not be sent/);
  assert.match(described.message, /call the police or an ambulance yourself now/);
  assert.match(described.chatMessage, /emergency/i);
});

test("the safety metrics are admin-only and count what the desk must answer", async () => {
  await reset();
  await callable.fileReport({ subjectUid: "worker_1", category: "SAFETY", details: DETAILS }, RESIDENT);
  await db.collection("sosIncidents").doc("sos_1").set({
    postedByUid: "resident_1",
    status: "ACTIVE",
    raisedAt: Date.now(),
    dispatch: { status: "FAILED", recipients: 1, delivered: 0, attemptedAt: Date.now() },
  });

  await assert.rejects(callable.safetyMetrics({}, RESIDENT), (error) => {
    assert.equal(error.code, "permission-denied");
    return true;
  });

  const metrics = await callable.safetyMetrics({}, ADMIN);
  assert.equal(metrics.sosRaised, 1);
  assert.equal(metrics.sosUnacknowledged, 1, "an unanswered emergency is the number that matters");
  assert.equal(metrics.sosDispatchFailed, 1);
  assert.equal(metrics.reportsOpen, 1);
  assert.match(metrics.summary, /sos=1/);
  assert.match(metrics.summary, /reportsOpen=1/);
});
