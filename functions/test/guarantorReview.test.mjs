/**
 * ADMIN-005 (REG-002 recovery, gap G2): security contract of the admin guarantor review
 * path, executed directly against the TypeScript source.
 *
 * The reviewed module is deliberately free of `firebase-admin` / `firebase-functions`
 * imports, so this suite needs no emulator, no Firestore instance and no build step —
 * `node --test test/` (Node 22.23 runs the `.ts` source directly) is the whole story.
 *
 * Every test below maps to one security requirement of the story:
 *   1.  anonymous caller cannot read a submission          -> admin gate (unauthenticated)
 *   2.  signed-in non-admin cannot read a submission       -> admin gate (permission-denied)
 *   3.  a client-supplied role/claim is never trusted      -> only `auth.token.admin === true`
 *   4.  NIN masked by default in the admin detail response
 *   5.  full NIN only on an explicit admin opt-in
 *   6.  the queue never carries a full NIN
 *   7.  a photo outside the worker's own prefix is never handed a URL
 *   8.  an absolute URL is never treated as a Storage path
 *   9.  path traversal is refused
 *   10. missing photo -> reported, not guessed
 *   11. valid photos -> short-lived signed URL, bounded TTL
 *   12. the review write payload is a fixed admin-owned allowlist
 *   13. the review payload is validated and cannot smuggle worker-owned fields
 *   14. the queue merges/de-dupes/sorts KYC + guarantor work (incl. prototype safety)
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  GUARANTOR_DECISIONS,
  GUARANTOR_NIN_STATUSES,
  GUARANTOR_REVIEWABLE_STATUSES,
  GUARANTOR_PHOTO_PREFIX_SEGMENT,
  DEFAULT_PHOTO_URL_TTL_SECONDS,
  MAX_PHOTO_URL_TTL_SECONDS,
  MIN_GUARANTOR_FEEDBACK_LENGTH,
  MAX_GUARANTOR_FEEDBACK_LENGTH,
  REVIEW_SLA_MILLISECONDS,
  buildGuarantorNinUpdate,
  buildGuarantorReviewFields,
  buildPhotoAccess,
  buildQueueRows,
  checkAdminCaller,
  collectPrivatePhotoPaths,
  isPrivateGuarantorPhotoPath,
  mapDecisionToStatus,
  maskIdentifier,
  normalizeNinStatus,
  projectGuarantorForAdmin,
  projectQueueRow,
  projectSubmissionForAdmin,
  readWorkerId,
  resolvePhotoUrlTtlSeconds,
  validateGuarantorReviewInput
} from "../src/guarantorReview.ts";

const WORKER_ID = "worker-1";
const ADMIN_UID = "admin-uid-1";
const NOW = 1_700_000_000_000;
const NIN = "CM90012345678AB";

/** A context shaped like `functions.https.CallableContext`, but with no dependencies. */
function contextWith(token, uid = "caller-uid") {
  return { auth: { uid, token } };
}

function privatePhotoPath(name, workerId = WORKER_ID) {
  return `users/${workerId}/guarantors/${name}`;
}

function mintedFor(paths, urlPrefix = "https://storage.example/signed") {
  const map = Object.create(null);
  for (const path of paths) map[path] = { url: `${urlPrefix}/${path.split("/").pop()}` };
  return map;
}

// ==================== 1-3. authorization gate ====================

test("gate: an anonymous caller is rejected as unauthenticated", () => {
  for (const context of [undefined, null, {}, { auth: null }, { auth: { token: { admin: true } } }]) {
    const result = checkAdminCaller(context);
    assert.equal(result.ok, false);
    assert.equal(result.code, "unauthenticated");
  }
});

test("gate: a signed-in caller without the admin claim is denied", () => {
  const signedInWorker = contextWith({ role: "worker", admin: false }, WORKER_ID);
  const result = checkAdminCaller(signedInWorker);
  assert.equal(result.ok, false);
  assert.equal(result.code, "permission-denied");
});

test("gate: a client-supplied role or admin flag is never read", () => {
  // The uid is trusted only because the token that carried it was verified. A caller who
  // writes `admin: true` anywhere other than the (verified) token gets nowhere.
  const spoofedPayload = { workerId: WORKER_ID, decision: "APPROVE", admin: true, role: "admin" };
  const validated = validateGuarantorReviewInput(spoofedPayload, ADMIN_UID);
  assert.equal(validated.ok, true);
  // ...and the room they get is the worker's own, with the admin taken from the token.
  assert.equal(validated.value.adminUid, ADMIN_UID);
  assert.equal(validateGuarantorReviewInput(spoofedPayload, "").value.adminUid, "");
});

test("gate: only the boolean true admin claim is accepted on a verified token", () => {
  assert.equal(checkAdminCaller(contextWith({ admin: true })).ok, true);
  assert.equal(checkAdminCaller(contextWith({ admin: true })).value, "caller-uid");
  for (const token of [
    { admin: "true" },
    { admin: 1 },
    { admin: {} },
    { admin: false },
    { role: "admin" },
    {},
    null,
    "admin"
  ]) {
    const result = checkAdminCaller(contextWith(token));
    assert.equal(result.ok, false, `token ${JSON.stringify(token)} must not pass`);
    assert.equal(result.code, "permission-denied");
  }
});

// ==================== NIN masking ====================

test("mask: only the last two characters of an identifier survive", () => {
  assert.equal(maskIdentifier(NIN), "*".repeat(NIN.length - 2) + "AB");
  assert.equal(maskIdentifier(NIN).length, NIN.length);
  assert.equal(maskIdentifier("12345678"), "******78");
  assert.equal(maskIdentifier("12"), "**");
  assert.equal(maskIdentifier("7"), "*");
  assert.equal(maskIdentifier(""), "");
  assert.equal(maskIdentifier(undefined), "");
  assert.equal(maskIdentifier(12345678), "");
  assert.ok(!maskIdentifier(NIN).includes(NIN));
});

test("mask: an absent NIN masks to the empty string rather than the word null", () => {
  assert.equal(maskIdentifier(null), "");
  assert.equal(maskIdentifier({}), "");
});


// ==================== 4-5. admin detail: NIN release is opt-in ====================

/** The five private photographs of a complete submission, in filing order. */
function submissionFixture() {
  return {
    status: "PENDING_REVIEW",
    submittedAt: NOW - 60_000,
    guarantorCount: 2,
    guarantorIds: ["g-2", "g-1"],
    groupPhotoStoragePath: privatePhotoPath("group_1.jpg"),
    consentConfirmed: true
  };
}

function guarantorFixture(id, workerId = WORKER_ID) {
  return {
    id,
    body: {
      workerKycId: workerId,
      fullName: `Guarantor ${id}`,
      nationalIdNumber: NIN,
      relationship: "Colleague",
      district: "Kampala",
      phoneNumber: "+256700000000",
      consentProvided: true,
      ninVerificationStatus: "PENDING",
      submittedAt: NOW - 90_000,
      guarantorPhotoStoragePath: privatePhotoPath(`${id}_guarantor.jpg`, workerId),
      workerWithGuarantorPhotoStoragePath: privatePhotoPath(`${id}_with_worker.jpg`, workerId)
    }
  };
}

function detailView(overrides = {}) {
  const submission = submissionFixture();
  const guarantorDocs = [guarantorFixture("g-1"), guarantorFixture("g-2")];
  const paths = collectPrivatePhotoPaths({ workerId: WORKER_ID, submission, guarantorDocs });
  return projectSubmissionForAdmin({
    workerId: WORKER_ID,
    submission,
    guarantorDocs,
    workerKycStatus: "APPROVED",
    includeFullNin: false,
    urlsByPath: mintedFor(paths),
    now: NOW,
    ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS,
    ...overrides
  });
}

test("detail: without an explicit opt-in the administrator sees only the masked NIN", () => {
  const view = detailView();
  assert.equal(view.exists, true);
  assert.equal(view.guarantors.length, 2);
  for (const guarantor of view.guarantors) {
    assert.equal(guarantor.ninMasked, "*".repeat(NIN.length - 2) + "AB");
    assert.equal(
      Object.prototype.hasOwnProperty.call(guarantor, "nationalIdNumber"),
      false,
      "the unmasked NIN must not even be a property"
    );
  }
  // Nothing in the whole payload leaks the identifier or a raw NIN string.
  assert.ok(!JSON.stringify(view).includes(NIN));
  assert.equal(view.reviewSlaDeadlineAt, NOW - 60_000 + REVIEW_SLA_MILLISECONDS);
  assert.equal(view.guarantorsAwaitingReview, true);
  assert.equal(view.guarantorCount, 2);
  assert.equal(view.workerKycStatus, "APPROVED");
});

test("detail: the unmasked NIN is released only when the caller asks for it", () => {
  const view = detailView({ includeFullNin: true });
  for (const guarantor of view.guarantors) {
    assert.equal(guarantor.nationalIdNumber, NIN);
  }
});

test("detail: guarantors keep the filing order recorded on the submission", () => {
  const view = detailView();
  assert.deepEqual(
    view.guarantors.map((guarantor) => guarantor.id),
    ["g-2", "g-1"]
  );
  // Slot numbers drive the photo slot names shown in the admin UI.
  assert.equal(view.guarantors[0].photos.portrait.slot, "guarantor1_portrait");
  assert.equal(view.guarantors[0].photos.withWorker.slot, "guarantor1_with_worker");
  assert.equal(view.guarantors[1].photos.portrait.slot, "guarantor2_portrait");
});

test("detail: a guarantor id that is not on file is not projected", () => {
  const submission = submissionFixture();
  const guarantorDocs = [guarantorFixture("g-1"), guarantorFixture("g-2")];
  const view = projectSubmissionForAdmin({
    workerId: WORKER_ID,
    submission: { ...submission, guarantorIds: ["g-1", "someone-elses-doc"] },
    guarantorDocs,
    workerKycStatus: "NOT_STARTED",
    includeFullNin: false,
    urlsByPath: {},
    now: NOW,
    ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS
  });
  assert.deepEqual(
    view.guarantors.map((guarantor) => guarantor.id),
    ["g-1"]
  );
  assert.equal(view.groupPhoto.available, false);
  assert.equal(view.groupPhoto.error, "NOT_MINTED");
});

// ==================== 7-11. private photo access ====================

test("photos: only a path inside the worker's own private prefix qualifies", () => {
  assert.equal(isPrivateGuarantorPhotoPath(privatePhotoPath("a.jpg"), WORKER_ID), true);
  assert.equal(
    isPrivateGuarantorPhotoPath(privatePhotoPath("a.jpg").replace("/guarantors/", "/"), WORKER_ID),
    false
  );
  assert.equal(GUARANTOR_PHOTO_PREFIX_SEGMENT, "guarantors");
});

test("photos: another worker's folder is refused even if a URL was minted for it", () => {
  const foreign = "users/worker-2/guarantors/stolen.jpg";
  assert.equal(isPrivateGuarantorPhotoPath(foreign, WORKER_ID), false);
  const access = buildPhotoAccess({
    slot: "group_photo",
    storagePath: foreign,
    workerId: WORKER_ID,
    // Deliberately poisoned: the guard, not the map, is what refuses the download.
    urlsByPath: { [foreign]: { url: "https://storage.example/signed/stolen.jpg" } },
    now: NOW,
    ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS
  });
  assert.equal(access.available, false);
  assert.equal(access.error, "OUTSIDE_PRIVATE_PREFIX");
  assert.equal(access.downloadUrl, undefined);
  assert.ok(!JSON.stringify(access).includes("signed"));
});

test("photos: absolute URLs, traversal, leading slashes and empty segments are refused", () => {
  const rejected = [
    "",
    "   ",
    `https://storage.googleapis.com/bucket/users/${WORKER_ID}/guarantors/a.jpg`,
    `gs://bucket/users/${WORKER_ID}/guarantors/a.jpg`,
    `users/${WORKER_ID}/guarantors/../../worker-2/guarantors/a.jpg`,
    `users/${WORKER_ID}/guarantors/..`,
    `/users/${WORKER_ID}/guarantors/a.jpg`,
    `users/${WORKER_ID}/guarantors/`,
    `users/${WORKER_ID}/guarantors`,
    "users//guarantors/a.jpg",
    `users/${WORKER_ID}//a.jpg`,
    `users/${WORKER_ID}/kyc/a.jpg`,
    `guarantors/users/${WORKER_ID}/a.jpg`,
    `../users/${WORKER_ID}/guarantors/a.jpg`,
    undefined,
    null,
    42,
    {}
  ];
  for (const path of rejected) {
    assert.equal(
      isPrivateGuarantorPhotoPath(path, WORKER_ID),
      false,
      `${JSON.stringify(path)} must not qualify as a private photo path`
    );
  }
  // ...and without a worker id nothing qualifies, which is what stops a wildcard read.
  assert.equal(isPrivateGuarantorPhotoPath(privatePhotoPath("a.jpg"), ""), false);
});

test("photos: an empty stored path is reported as missing, never guessed at", () => {
  for (const storagePath of ["", undefined, null, 7]) {
    const access = buildPhotoAccess({
      slot: "guarantor1_portrait",
      storagePath,
      workerId: WORKER_ID,
      urlsByPath: {},
      now: NOW,
      ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS
    });
    assert.equal(access.available, false);
    assert.equal(access.error, "MISSING");
    assert.equal(access.storagePath, "");
  }
});

test("photos: a valid path yields a short-lived URL and nothing more", () => {
  const path = privatePhotoPath("group_1.jpg");
  const access = buildPhotoAccess({
    slot: "group_photo",
    storagePath: path,
    workerId: WORKER_ID,
    urlsByPath: mintedFor([path]),
    now: NOW,
    ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS
  });
  assert.equal(access.available, true);
  assert.equal(access.storagePath, path);
  assert.equal(access.downloadUrl, "https://storage.example/signed/group_1.jpg");
  assert.equal(access.expiresAt, NOW + DEFAULT_PHOTO_URL_TTL_SECONDS * 1000);
  assert.equal(access.error, undefined);
});

test("photos: a minting failure is reported by code instead of leaking the error", () => {
  const path = privatePhotoPath("group_1.jpg");
  const access = buildPhotoAccess({
    slot: "group_photo",
    storagePath: path,
    workerId: WORKER_ID,
    urlsByPath: { [path]: { url: null, error: "URL_MINT_FAILED" } },
    now: NOW,
    ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS
  });
  assert.equal(access.available, false);
  assert.equal(access.error, "URL_MINT_FAILED");
  assert.equal(access.downloadUrl, undefined);
});

test("photos: an inherited map entry is never treated as a minted URL", () => {
  const path = privatePhotoPath("group_1.jpg");
  const inherited = Object.create({
    [path]: { url: "https://storage.example/signed/inherited.jpg" }
  });
  const access = buildPhotoAccess({
    slot: "group_photo",
    storagePath: path,
    workerId: WORKER_ID,
    urlsByPath: inherited,
    now: NOW,
    ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS
  });
  assert.equal(access.available, false);
  assert.equal(access.error, "NOT_MINTED");
});

test("photos: exactly the five photographs of a submission are collected, once each", () => {
  const submission = submissionFixture();
  const guarantorDocs = [guarantorFixture("g-1"), guarantorFixture("g-2")];
  const paths = collectPrivatePhotoPaths({ workerId: WORKER_ID, submission, guarantorDocs });
  assert.equal(paths.length, 5);
  assert.equal(new Set(paths).size, 5);
  assert.ok(paths.includes(privatePhotoPath("group_1.jpg")));
  assert.ok(paths.includes(privatePhotoPath("g-1_guarantor.jpg")));
  assert.ok(paths.includes(privatePhotoPath("g-1_with_worker.jpg")));
  assert.ok(paths.includes(privatePhotoPath("g-2_guarantor.jpg")));
  assert.ok(paths.includes(privatePhotoPath("g-2_with_worker.jpg")));

  // A path filed twice for two different slots is only minted once...
  const duplicated = collectPrivatePhotoPaths({
    workerId: WORKER_ID,
    submission,
    guarantorDocs: [
      {
        id: "g-1",
        body: {
          ...guarantorFixture("g-1").body,
          guarantorPhotoStoragePath: privatePhotoPath("group_1.jpg")
        }
      }
    ]
  });
  assert.equal(duplicated.length, 2);

  // ...and a foreign or tampered path never becomes one of them.
  const tampered = collectPrivatePhotoPaths({
    workerId: WORKER_ID,
    submission: { groupPhotoStoragePath: "https://evil.example/x.jpg", guarantorIds: [] },
    guarantorDocs: [
      {
        id: "g-1",
        body: {
          workerKycId: WORKER_ID,
          guarantorPhotoStoragePath: "users/other-worker/guarantors/x.jpg",
          workerWithGuarantorPhotoStoragePath: `users/${WORKER_ID}/guarantors/../../other/a.jpg`
        }
      }
    ]
  });
  assert.deepEqual(tampered, []);
});

test("photos: the signed-URL lifetime defaults to 15 minutes and is capped at an hour", () => {
  assert.equal(DEFAULT_PHOTO_URL_TTL_SECONDS, 900);
  assert.equal(MAX_PHOTO_URL_TTL_SECONDS, 3600);
  for (const configured of [undefined, null, 0, -1, "900", "abc", {}, []]) {
    assert.equal(resolvePhotoUrlTtlSeconds(configured), DEFAULT_PHOTO_URL_TTL_SECONDS);
  }
  assert.equal(resolvePhotoUrlTtlSeconds(120), 120);
  assert.equal(resolvePhotoUrlTtlSeconds(900.9), 900);
  assert.equal(resolvePhotoUrlTtlSeconds(3600), 3600);
  assert.equal(resolvePhotoUrlTtlSeconds(999999), MAX_PHOTO_URL_TTL_SECONDS);
  assert.equal(resolvePhotoUrlTtlSeconds(Number.POSITIVE_INFINITY), DEFAULT_PHOTO_URL_TTL_SECONDS);
});

// ==================== 12. the review write payload is an allowlist ====================

test("write: a decision maps onto the status vocabulary the worker's app already knows", () => {
  assert.deepEqual(GUARANTOR_DECISIONS, ["APPROVE", "REJECT", "CHANGES_REQUIRED"]);
  assert.deepEqual(GUARANTOR_NIN_STATUSES, ["PENDING", "VERIFIED", "FAILED"]);
  assert.deepEqual(GUARANTOR_REVIEWABLE_STATUSES, ["PENDING_REVIEW", "UNDER_REVIEW"]);

  assert.equal(mapDecisionToStatus("APPROVE"), "APPROVED");
  assert.equal(mapDecisionToStatus("approve"), "APPROVED");
  assert.equal(mapDecisionToStatus("Reject"), "REJECTED");
  assert.equal(mapDecisionToStatus("CHANGES_REQUIRED"), "CHANGES_REQUIRED");
  assert.equal(mapDecisionToStatus(" changes_required "), "CHANGES_REQUIRED");
  for (const bogus of ["", undefined, null, "APPROVED", "GRANT", 1, {}, ["APPROVE"]]) {
    assert.equal(mapDecisionToStatus(bogus), null);
  }

  assert.equal(normalizeNinStatus(undefined), "PENDING");
  assert.equal(normalizeNinStatus(""), "PENDING");
  // A non-string carries no instruction, so it can never verify anything: it degrades to
  // "not checked yet" instead of bubbling an unexpected value into Firestore.
  assert.equal(normalizeNinStatus(null), "PENDING");
  assert.equal(normalizeNinStatus(1), "PENDING");
  assert.equal(normalizeNinStatus({}), "PENDING");
  assert.equal(normalizeNinStatus("verified"), "VERIFIED");
  assert.equal(normalizeNinStatus("Failed"), "FAILED");
  for (const bogus of ["VERIFY", "true", "0", "PASSED", "pending_review"]) {
    assert.equal(normalizeNinStatus(bogus), null, `${bogus} is not part of the NIN vocabulary`);
  }
});

test("write: only admin-owned fields are ever written, and never a worker-owned one", () => {
  const command = validateGuarantorReviewInput(
    { workerId: WORKER_ID, decision: "REJECT", feedback: "The NIN does not match the photograph supplied." },
    ADMIN_UID
  ).value;

  const reviewedAt = "SERVER_TIMESTAMP";
  const submissionFields = buildGuarantorReviewFields(command, reviewedAt);
  assert.deepEqual(Object.keys(submissionFields).sort(), [
    "ninVerificationStatus",
    "reviewFeedback",
    "reviewedAt",
    "reviewedBy",
    "status",
    "updatedAt"
  ]);
  assert.equal(submissionFields.status, "REJECTED");
  assert.equal(submissionFields.reviewFeedback, "The NIN does not match the photograph supplied.");
  assert.equal(submissionFields.ninVerificationStatus, "PENDING");
  assert.equal(submissionFields.reviewedBy, ADMIN_UID);
  assert.equal(submissionFields.reviewedAt, reviewedAt);
  assert.equal(submissionFields.updatedAt, reviewedAt);

  const guarantorFields = buildGuarantorNinUpdate(command, reviewedAt);
  assert.deepEqual(Object.keys(guarantorFields).sort(), [
    "ninVerificationStatus",
    "updatedAt",
    "verifiedAt",
    "verifiedBy"
  ]);
  assert.equal(guarantorFields.ninVerificationStatus, "PENDING");
  assert.equal(guarantorFields.verifiedBy, ADMIN_UID);
  assert.equal(guarantorFields.verifiedAt, reviewedAt);

  for (const fields of [submissionFields, guarantorFields]) {
    for (const workerOwned of [
      "fullName",
      "nationalIdNumber",
      "guarantorIds",
      "guarantorCount",
      "groupPhotoStoragePath",
      "guarantorPhotoStoragePath",
      "consentProvided",
      "submittedAt"
    ]) {
      assert.equal(
        Object.prototype.hasOwnProperty.call(fields, workerOwned),
        false,
        `a review must never write ${workerOwned}`
      );
    }
  }
});

// ==================== 13. the review payload is validated ====================

test("review: an approval needs no feedback, a rejection does", () => {
  const approved = validateGuarantorReviewInput(
    { workerId: WORKER_ID, decision: "APPROVE", ninStatus: "VERIFIED" },
    ADMIN_UID
  );
  assert.equal(approved.ok, true);
  assert.deepEqual(Object.keys(approved.value).sort(), [
    "adminUid",
    "decision",
    "feedback",
    "ninStatus",
    "status",
    "workerId"
  ]);
  assert.equal(approved.value.status, "APPROVED");
  assert.equal(approved.value.ninStatus, "VERIFIED");
  assert.equal(approved.value.feedback, "");
  // An omitted ninStatus means "still pending", never an accidental verification.
  assert.equal(
    validateGuarantorReviewInput({ workerId: WORKER_ID, decision: "APPROVE" }, ADMIN_UID).value.ninStatus,
    "PENDING"
  );

  for (const decision of ["REJECT", "CHANGES_REQUIRED"]) {
    const withoutFeedback = validateGuarantorReviewInput({ workerId: WORKER_ID, decision }, ADMIN_UID);
    assert.equal(withoutFeedback.ok, false);
    assert.equal(withoutFeedback.code, "invalid-argument");
    const shortFeedback = validateGuarantorReviewInput(
      { workerId: WORKER_ID, decision, feedback: "nope" },
      ADMIN_UID
    );
    assert.equal(shortFeedback.ok, false);
    assert.equal(shortFeedback.code, "invalid-argument");
    const ok = validateGuarantorReviewInput(
      {
        workerId: WORKER_ID,
        decision,
        feedback: "Please re-take the photograph with the guarantor present."
      },
      ADMIN_UID
    );
    assert.equal(ok.ok, true);
    assert.equal(ok.value.status, decision === "REJECT" ? "REJECTED" : "CHANGES_REQUIRED");
  }
  assert.equal(MIN_GUARANTOR_FEEDBACK_LENGTH, 10);
});

test("review: feedback boundaries and unknown vocabulary are refused", () => {
  const atLimit = validateGuarantorReviewInput(
    {
      workerId: WORKER_ID,
      decision: "REJECT",
      feedback: "x".repeat(MAX_GUARANTOR_FEEDBACK_LENGTH)
    },
    ADMIN_UID
  );
  assert.equal(atLimit.ok, true);
  assert.equal(atLimit.value.feedback.length, MAX_GUARANTOR_FEEDBACK_LENGTH);

  const overLimit = validateGuarantorReviewInput(
    {
      workerId: WORKER_ID,
      decision: "REJECT",
      feedback: "x".repeat(MAX_GUARANTOR_FEEDBACK_LENGTH + 1)
    },
    ADMIN_UID
  );
  assert.equal(overLimit.ok, false);
  assert.equal(overLimit.code, "invalid-argument");

  const badDecision = validateGuarantorReviewInput(
    { workerId: WORKER_ID, decision: "APPROVED" },
    ADMIN_UID
  );
  assert.equal(badDecision.ok, false);
  assert.equal(badDecision.code, "invalid-argument");
  assert.ok(badDecision.message.includes("APPROVE"));

  const badNin = validateGuarantorReviewInput(
    { workerId: WORKER_ID, decision: "APPROVE", ninStatus: "MAYBE" },
    ADMIN_UID
  );
  assert.equal(badNin.ok, false);
  assert.equal(badNin.code, "invalid-argument");

  for (const payload of [null, undefined, "APPROVE", 42, []]) {
    const result = validateGuarantorReviewInput(payload, ADMIN_UID);
    assert.equal(result.ok, false);
    assert.equal(result.code, "invalid-argument");
  }
});

test("review: the worker id is validated before it is ever used as a document path", () => {
  assert.equal(readWorkerId({ workerId: "  worker-1 " }).value, "worker-1");
  const rejected = [
    undefined,
    null,
    "",
    "   ",
    "../../workers",
    "workers/../admin",
    "users/admin",
    "worker 1",
    "worker#1",
    {},
    42,
    "a".repeat(129)
  ];
  for (const workerId of rejected) {
    const result = readWorkerId({ workerId });
    assert.equal(result.ok, false, `workerId ${JSON.stringify(workerId)} must be refused`);
    assert.equal(result.code, "invalid-argument");
  }
  assert.equal(readWorkerId(undefined).ok, false);
  assert.equal(readWorkerId({}).ok, false);

  const injected = validateGuarantorReviewInput(
    { workerId: "worker-1/../../admin", decision: "APPROVE" },
    ADMIN_UID
  );
  assert.equal(injected.ok, false);
  assert.equal(injected.code, "invalid-argument");
});

test("review: worker-owned fields cannot be smuggled in through the review payload", () => {
  const validated = validateGuarantorReviewInput(
    {
      workerId: WORKER_ID,
      decision: "APPROVE",
      ninStatus: "VERIFIED",
      // Everything below is worker-owned or admin-identity data and must be ignored.
      fullName: "Injected Name",
      nationalIdNumber: "CM00000000000000",
      guarantorCount: 99,
      guarantorIds: ["attacker"],
      groupPhotoStoragePath: "users/worker-2/guarantors/x.jpg",
      consentProvided: false,
      submittedAt: 1,
      status: "APPROVED",
      reviewedBy: "somebody-else",
      adminUid: "somebody-else",
      role: "admin",
      admin: true
    },
    ADMIN_UID
  );
  assert.equal(validated.ok, true);
  const command = validated.value;
  assert.deepEqual(Object.keys(command).sort(), [
    "adminUid",
    "decision",
    "feedback",
    "ninStatus",
    "status",
    "workerId"
  ]);
  assert.equal(command.adminUid, ADMIN_UID);
  assert.equal(JSON.stringify(command).includes("Injected Name"), false);
  assert.equal(JSON.stringify(command).includes("CM00000000000000"), false);
  assert.equal(JSON.stringify(command).includes("worker-2"), false);
});

// ==================== 14. the queue merges KYC + guarantor work ====================

const KYC_SUBMITTED_AT = NOW - 600_000;
const GUARANTOR_SUBMITTED_AT = NOW - 300_000;

function queueFixture() {
  return {
    kycDocs: [
      {
        id: "worker-a",
        body: {
          fullLegalName: "Amina Worker",
          nationalIdNumber: NIN,
          status: "UNDER_REVIEW",
          submittedAt: KYC_SUBMITTED_AT,
          idFrontStoragePath: `users/worker-a/kyc/front.jpg`,
          idBackStoragePath: `users/worker-a/kyc/back.jpg`,
          reviewFeedback: ""
        }
      },
      { id: "worker-b", body: { fullLegalName: "Bob Worker", status: "SUBMITTED", submittedAt: NOW - 800_000 } }
    ],
    submissionDocs: [
      {
        id: "worker-a",
        body: {
          status: "PENDING_REVIEW",
          submittedAt: GUARANTOR_SUBMITTED_AT,
          guarantorCount: 2,
          ninVerificationStatus: "PENDING"
        }
      },
      { id: "worker-c", body: { status: "PENDING_REVIEW", submittedAt: NOW - 900_000, guarantorCount: 2 } },
      { id: "worker-d", body: { status: "APPROVED", submittedAt: NOW - 1_000_000, guarantorCount: 2 } }
    ],
    namesByWorkerId: { "worker-c": "Cathy Worker", "worker-d": "Dan Worker" },
    now: NOW,
    limit: 50
  };
}

test("queue: a worker in both feeds appears once, with both states attached", () => {
  const rows = buildQueueRows(queueFixture());
  assert.equal(rows.length, 4);
  assert.deepEqual(rows.map((row) => row.userId), ["worker-d", "worker-c", "worker-b", "worker-a"]);

  const workerA = rows.find((row) => row.userId === "worker-a");
  assert.equal(workerA.kycOnFile, true);
  assert.equal(workerA.status, "UNDER_REVIEW");
  assert.equal(workerA.guarantorStatus, "PENDING_REVIEW");
  assert.equal(workerA.guarantorSubmittedAt, GUARANTOR_SUBMITTED_AT);
  assert.equal(workerA.guarantorCount, 2);
  assert.equal(workerA.guarantorsAwaitingReview, true);
  // The earliest outstanding timestamp decides the queue position, i.e. the 48h SLA clock
  // of whichever review has been waiting longest.
  assert.equal(workerA.sortTimestamp, KYC_SUBMITTED_AT);
  assert.equal(workerA.slaDeadline, KYC_SUBMITTED_AT + REVIEW_SLA_MILLISECONDS);
});

test("queue: a guarantor-only worker is still visible to an administrator", () => {
  const rows = buildQueueRows(queueFixture());
  const workerC = rows.find((row) => row.userId === "worker-c");
  assert.equal(workerC.kycOnFile, false);
  assert.equal(workerC.status, "NOT_STARTED");
  assert.equal(workerC.fullLegalName, "Cathy Worker");
  assert.equal(workerC.submittedAt, 0);
  assert.equal(workerC.slaDeadline, 0);
  assert.equal(workerC.guarantorsAwaitingReview, true);

  // A submission that is already decided is not queued as outstanding work.
  const workerD = rows.find((row) => row.userId === "worker-d");
  assert.equal(workerD.guarantorStatus, "APPROVED");
  assert.equal(workerD.guarantorsAwaitingReview, false);

  // A KYC-only worker has no guarantor state at all.
  const workerB = rows.find((row) => row.userId === "worker-b");
  assert.equal(workerB.guarantorStatus, "");
  assert.equal(workerB.guarantorSubmittedAt, 0);
  assert.equal(workerB.guarantorsAwaitingReview, false);
});

test("queue: rows with the same timestamp are ordered deterministically and the limit applies after sorting", () => {
  const sameTimestamp = NOW - 10_000;
  const rows = buildQueueRows({
    kycDocs: [
      { id: "worker-z", body: { status: "SUBMITTED", submittedAt: sameTimestamp } },
      { id: "worker-y", body: { status: "SUBMITTED", submittedAt: sameTimestamp } }
    ],
    submissionDocs: [],
    namesByWorkerId: {},
    now: NOW,
    limit: 50
  });
  assert.deepEqual(rows.map((row) => row.userId), ["worker-y", "worker-z"]);

  const limited = buildQueueRows({ ...queueFixture(), limit: 2 });
  assert.deepEqual(limited.map((row) => row.userId), ["worker-d", "worker-c"]);
  assert.equal(buildQueueRows({ ...queueFixture(), limit: 0 }).length, 4);
});

test("queue: rows without a timestamp sort last and never claim an SLA", () => {
  const rows = buildQueueRows({
    kycDocs: [{ id: "worker-untimed", body: { status: "SUBMITTED" } }],
    submissionDocs: [{ id: "worker-timed", body: { status: "PENDING_REVIEW", submittedAt: NOW } }],
    namesByWorkerId: {},
    now: NOW,
    limit: 50
  });
  assert.deepEqual(rows.map((row) => row.userId), ["worker-timed", "worker-untimed"]);
  assert.equal(rows[1].sortTimestamp, 0);
  assert.equal(rows[1].slaDeadline, 0);
});

test("queue: the queue never carries a full NIN — only the masked form", () => {
  const rows = buildQueueRows(queueFixture());
  assert.ok(!JSON.stringify(rows).includes(NIN));
  for (const row of rows) {
    assert.equal(
      Object.prototype.hasOwnProperty.call(row, "nationalIdNumber"),
      false,
      "the queue must not emit an unmasked NIN field at all"
    );
  }
  assert.equal(
    rows.find((row) => row.userId === "worker-a").nationalIdNumberMasked,
    "*".repeat(NIN.length - 2) + "AB"
  );
  // A worker without a KYC document on file simply masks to nothing.
  assert.equal(rows.find((row) => row.userId === "worker-c").nationalIdNumberMasked, "");
});

test("detail: a single guarantor projection falls back safely on a sparse record", () => {
  const doc = guarantorFixture("g-1");
  const base = {
    id: doc.id,
    body: doc.body,
    slot: 1,
    workerId: WORKER_ID,
    urlsByPath: {},
    now: NOW,
    ttlSeconds: DEFAULT_PHOTO_URL_TTL_SECONDS
  };
  const masked = projectGuarantorForAdmin({ ...base, includeFullNin: false });
  assert.equal(masked.id, "g-1");
  assert.equal(masked.ninMasked, "*".repeat(NIN.length - 2) + "AB");
  assert.equal(Object.prototype.hasOwnProperty.call(masked, "nationalIdNumber"), false);
  assert.equal(masked.ninVerificationStatus, "PENDING");
  assert.equal(masked.consentProvided, true);
  assert.equal(masked.photos.portrait.error, "NOT_MINTED");

  const full = projectGuarantorForAdmin({ ...base, includeFullNin: true });
  assert.equal(full.nationalIdNumber, NIN);

  // A guarantor who never consented, and whose NIN state is unknown, must not look
  // verified: consent defaults to false and the state to PENDING.
  const sparse = projectGuarantorForAdmin({ ...base, body: {}, includeFullNin: true });
  assert.equal(sparse.ninMasked, "");
  assert.equal(sparse.nationalIdNumber, "");
  assert.equal(sparse.consentProvided, false);
  assert.equal(sparse.ninVerificationStatus, "PENDING");
  assert.equal(sparse.fullName, "");
  assert.equal(sparse.photos.portrait.error, "MISSING");
  assert.equal(sparse.photos.withWorker.error, "MISSING");
});

test("queue: prototype keys can never be borrowed as a name or a document", () => {
  const rows = buildQueueRows({
    kycDocs: [{ id: "constructor", body: { status: "SUBMITTED", submittedAt: NOW } }],
    submissionDocs: [],
    // Neither an own key nor an inherited one may supply the display name here.
    namesByWorkerId: Object.create({ constructor: "Inherited Name", "worker-9": "Inherited Name" }),
    now: NOW,
    limit: 50
  });
  assert.equal(rows.length, 1);
  assert.equal(rows[0].userId, "constructor");
  assert.equal(rows[0].kycOnFile, true);
  assert.equal(rows[0].fullLegalName, "");

  const inheritedOnly = buildQueueRows({
    kycDocs: [{ id: "worker-9", body: { status: "SUBMITTED", submittedAt: NOW } }],
    submissionDocs: [],
    namesByWorkerId: Object.create({ "worker-9": "Inherited Name" }),
    now: NOW,
    limit: 50
  });
  assert.equal(inheritedOnly[0].fullLegalName, "");

  // A hostile document id keys nothing but itself.
  const protoRow = projectQueueRow({
    userId: "__proto__",
    kyc: { fullLegalName: "Proto Worker", status: "SUBMITTED", submittedAt: NOW },
    submission: null,
    fullName: "",
    now: NOW
  });
  assert.equal(protoRow.userId, "__proto__");
  assert.equal(protoRow.fullLegalName, "Proto Worker");
  assert.equal(Object.prototype.fullLegalName, undefined);
  assert.equal(Object.prototype.kycOnFile, undefined);
});






