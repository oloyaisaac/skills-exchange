/**
 * R10-P2-06: ratings a modified client cannot invent.
 *
 * Ratings were Room-only, so nothing aggregated across devices, nothing could be moderated, and a
 * tampered client could write its own stars. These tests pin the server-side decision order and the
 * aggregate maths — including that a client's claim about which side it is on is ignored.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  MAX_REVIEW_LENGTH,
  ratingDocumentId,
  reviewRating,
  applyRating,
  displayRating,
} from "../lib/ratingsCore.js";

const JOB = {
  jobId: "job_1",
  status: "COMPLETED",
  postedByUid: "resident_1",
  assignedProviderUid: "worker_1",
};

const residentRating = (overrides = {}) => ({
  raterUid: "resident_1",
  subjectUid: "worker_1",
  stars: 5,
  review: "On time, tidy, fair price.",
  ...overrides,
});

test("ratings: the resident may rate the assigned worker once the job is complete", () => {
  const decision = reviewRating(residentRating(), JOB, null);
  assert.equal(decision.ok, true);
  assert.equal(decision.stars, 5);
  assert.equal(decision.review, "On time, tidy, fair price.");
});

test("ratings: the worker may rate the resident back", () => {
  const decision = reviewRating(
    { raterUid: "worker_1", subjectUid: "resident_1", stars: 4 },
    JOB,
    null
  );
  assert.equal(decision.ok, true);
});

test("ratings: the side is derived from the job, not from the client", () => {
  // A worker claiming to be the resident, rating the worker's own account, is refused.
  const spoof = reviewRating(
    { raterUid: "worker_1", subjectUid: "worker_1", stars: 5 },
    JOB,
    null
  );
  assert.equal(spoof.ok, false);
  assert.equal(spoof.code, "SELF_RATING");

  const wrongSubject = reviewRating(
    { raterUid: "resident_1", subjectUid: "someone_else", stars: 5 },
    JOB,
    null
  );
  assert.equal(wrongSubject.ok, false);
  assert.equal(wrongSubject.code, "SUBJECT_MISMATCH");
});

test("ratings: non-participants, unfinished jobs and missing jobs are refused", () => {
  assert.equal(reviewRating(residentRating({ raterUid: "stranger" }), JOB, null).code, "NOT_A_PARTICIPANT");
  assert.equal(
    reviewRating(residentRating(), { ...JOB, status: "ACCEPTED_FUNDED" }, null).code,
    "NOT_COMPLETED"
  );
  assert.equal(reviewRating(residentRating(), null, null).code, "JOB_NOT_FOUND");
  assert.equal(reviewRating(residentRating(), { ...JOB, assignedProviderUid: undefined }, null).code, "SUBJECT_MISMATCH");
});

test("ratings: stars and the review are validated", () => {
  for (const stars of [0, 6, -1, 2.5, "5", null, undefined]) {
    assert.equal(reviewRating(residentRating({ stars }), JOB, null).code, "INVALID_STARS", `${stars}`);
  }
  const long = reviewRating(residentRating({ review: "x".repeat(MAX_REVIEW_LENGTH + 100) }), JOB, null);
  assert.equal(long.review.length, MAX_REVIEW_LENGTH);
});

test("ratings: a second rating on the same job is refused", () => {
  const decision = reviewRating(residentRating(), JOB, "job_1_resident_1");
  assert.equal(decision.ok, false);
  assert.equal(decision.code, "ALREADY_RATED");
});

test("ratings: the document id is per job per rater, so stacking is impossible", () => {
  assert.equal(ratingDocumentId("job_1", "resident_1"), "job_1_resident_1");
  assert.notEqual(ratingDocumentId("job_1", "resident_1"), ratingDocumentId("job_1", "worker_1"));
  assert.throws(() => ratingDocumentId("", "resident_1"), /needs a job and a rater/);
});

test("ratings: the aggregate is derived, never supplied", () => {
  const first = applyRating(undefined, 5, 1_800_000_000_000);
  assert.deepEqual({ count: first.count, sum: first.sum, average: first.average }, { count: 1, sum: 5, average: 5 });

  const second = applyRating(first, 4, 1_800_000_001_000);
  assert.equal(second.count, 2);
  assert.equal(second.sum, 9);
  assert.equal(second.average, 4.5);

  const third = applyRating({ count: "2", sum: "9" }, 5, 1);
  assert.equal(third.average, 4.67, "two decimals, not a float artefact");

  assert.throws(() => applyRating(first, 9, 1), /integer from 1 to 5/);
  assert.throws(() => applyRating(first, 0, 1), /integer from 1 to 5/);
});

test("ratings: what the UI may display", () => {
  assert.equal(displayRating({ count: 2, average: 4.5 }), 4.5);
  assert.equal(displayRating({ count: 3, average: 4.333 }), 4.3);
  assert.equal(displayRating({ count: 0, average: 0 }), null);
  assert.equal(displayRating(undefined), null);
  assert.equal(displayRating({ count: 2, average: 9 }), null, "an out-of-range average is not a rating");
});
