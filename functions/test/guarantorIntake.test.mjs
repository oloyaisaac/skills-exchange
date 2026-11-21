/**
 * R10-P2-04: guarantor identity duplication and collusion.
 *
 * The review's finding: two guarantors are required and shape-checked, but the same person could be
 * listed twice under different spellings, a worker could list themselves, and one NIN could back many
 * accounts. These tests pin the decision — including every fail-closed branch.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  REQUIRED_GUARANTORS,
  MAX_SUBMISSIONS_PER_WINDOW,
  phoneKey,
  normaliseName,
  reviewGuarantorIntake,
  describeRejections,
} from "../lib/guarantorIntake.js";

const WORKER = "worker_1";

function candidate(index, overrides = {}) {
  return {
    index,
    ninFingerprint: `fp_${index}`,
    phoneKey: `77219842${index}`,
    normalisedName: index === 0 ? "nakato a" : "okello b",
    district: "Nakawa",
    consentProvided: true,
    hasBothPhotos: true,
    ...overrides,
  };
}

function intake(overrides = {}) {
  return {
    workerUid: WORKER,
    workerNinFingerprint: "fp_worker",
    workerPhoneKey: "700000000",
    candidates: [candidate(0), candidate(1)],
    existing: [],
    submissionsInWindow: 0,
    ...overrides,
  };
}

test("intake: two distinct, consenting people are accepted", () => {
  const decision = reviewGuarantorIntake(intake());
  assert.equal(decision.ok, true);
  assert.deepEqual(decision.rejections, []);
});

test("intake: a phone number is compared as a person, not as a string", () => {
  assert.equal(phoneKey("+256 772 198 420"), "772198420");
  assert.equal(phoneKey("0772198420"), "772198420");
  assert.equal(phoneKey("256772198420"), "772198420");
  assert.equal(phoneKey("772198420"), "772198420");
  assert.equal(phoneKey("0772 198 420"), phoneKey("+256772198420"));
  assert.equal(phoneKey("12345"), "", "too short to be an identity");
  assert.equal(phoneKey(null), "");
  assert.equal(phoneKey(772198420), "");
});

test("intake: a name is compared case- and punctuation-insensitively", () => {
  assert.equal(normaliseName("Nakato  A."), "nakato a");
  assert.equal(normaliseName("  NAKATO   a "), "nakato a");
  assert.equal(normaliseName(undefined), "");
});

test("intake: the wrong number of guarantors is refused", () => {
  assert.equal(reviewGuarantorIntake(intake({ candidates: [] })).rejections[0].code, "WRONG_COUNT");
  assert.equal(
    reviewGuarantorIntake(intake({ candidates: [candidate(0)] })).ok,
    false
  );
  const three = reviewGuarantorIntake(
    intake({ candidates: [candidate(0), candidate(1), candidate(2)] })
  );
  assert.equal(three.ok, false);
  assert.equal(REQUIRED_GUARANTORS, 2);
});

test("intake: a worker cannot guarantee themselves by NIN or by phone", () => {
  const byNin = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { ninFingerprint: "fp_worker" }), candidate(1)] })
  );
  assert.equal(byNin.ok, false);
  assert.ok(byNin.rejections.some((rejection) => rejection.code === "SELF_GUARANTOR"));

  const byPhone = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { phoneKey: "700000000" }), candidate(1)] })
  );
  assert.equal(byPhone.ok, false);
  assert.ok(byPhone.rejections.some((rejection) => rejection.code === "SELF_GUARANTOR"));
});

test("intake: the same person twice is refused, however they are spelled", () => {
  const sameNin = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { ninFingerprint: "fp_same" }), candidate(1, { ninFingerprint: "fp_same" })] })
  );
  assert.equal(sameNin.ok, false);
  assert.ok(sameNin.rejections.some((rejection) => rejection.code === "DUPLICATE_GUARANTOR"));

  const samePhone = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { phoneKey: "772198420" }), candidate(1, { phoneKey: "772198420" })] })
  );
  assert.equal(samePhone.ok, false);
  assert.ok(samePhone.rejections.some((rejection) => rejection.code === "DUPLICATE_GUARANTOR"));

  // Same name and district is suspicious but not provable — it is a flag for the reviewer, not a denial.
  const sameName = reviewGuarantorIntake(
    intake({
      candidates: [
        candidate(0, { normalisedName: "nakato a", district: "Nakawa" }),
        candidate(1, { normalisedName: "nakato a", district: "Nakawa" }),
      ],
    })
  );
  assert.equal(sameName.ok, true);
  assert.ok(sameName.flags.includes("SAME_NAME_AND_DISTRICT"));
});

test("intake: a person who already guarantees another account is refused", () => {
  const decision = reviewGuarantorIntake(
    intake({
      existing: [{ workerUid: "worker_2", ninFingerprint: "fp_0", phoneKey: "772198420" }],
    })
  );
  assert.equal(decision.ok, false);
  const rejection = decision.rejections.find((r) => r.code === "GUARANTOR_ALREADY_USED");
  assert.ok(rejection);
  assert.equal(rejection.index, 0);
});

test("intake: re-filing for the same worker is allowed and flagged", () => {
  const decision = reviewGuarantorIntake(
    intake({ existing: [{ workerUid: WORKER, ninFingerprint: "fp_0", phoneKey: "772198420" }] })
  );
  assert.equal(decision.ok, true);
  assert.ok(decision.flags.includes("RESUBMISSION"));
});

test("intake: consent, photographs and completeness are required", () => {
  const noConsent = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { consentProvided: false }), candidate(1)] })
  );
  assert.ok(noConsent.rejections.some((r) => r.code === "NO_CONSENT"));

  const noPhotos = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { hasBothPhotos: false }), candidate(1)] })
  );
  assert.ok(noPhotos.rejections.some((r) => r.code === "INCOMPLETE"));

  const blank = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { phoneKey: "", normalisedName: "" }), candidate(1)] })
  );
  assert.ok(blank.rejections.some((r) => r.code === "INCOMPLETE"));
});

test("intake: an unavailable fingerprint fails closed rather than skipping the check", () => {
  const decision = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { ninFingerprint: "" }), candidate(1)] })
  );
  assert.equal(decision.ok, false);
  assert.ok(decision.rejections.some((r) => r.code === "MISSING_EVIDENCE"));
});

test("intake: the resubmission limit is enforced", () => {
  const decision = reviewGuarantorIntake(intake({ submissionsInWindow: MAX_SUBMISSIONS_PER_WINDOW }));
  assert.equal(decision.ok, false);
  assert.ok(decision.rejections.some((r) => r.code === "RESUBMISSION_LIMIT"));
  assert.equal(reviewGuarantorIntake(intake({ submissionsInWindow: MAX_SUBMISSIONS_PER_WINDOW - 1 })).ok, true);
});

test("intake: the audit description names codes, never people", () => {
  const decision = reviewGuarantorIntake(
    intake({ candidates: [candidate(0, { ninFingerprint: "fp_worker" }), candidate(1)] })
  );
  const description = describeRejections(decision.rejections);
  assert.equal(description, "SELF_GUARANTOR");
  assert.equal(describeRejections([]), "NONE");
});
