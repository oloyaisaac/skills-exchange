/**
 * R09-P2-05 / REM-041: national-ID minimisation.
 *
 * A NIN is the key to a Ugandan identity. It was held in plaintext, copied into exports and the UI
 * claimed it was "encrypted". These tests pin what the code actually does — masking, a salted
 * fingerprint for duplicate detection, and log scrubbing — so the privacy copy can be honest.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  maskNin,
  ninFingerprint,
  looksLikeNin,
  scrubForLog,
  scrubFields,
  sanitiseGuarantorSubmissionForExport,
} from "../lib/pii.js";

const NIN = "CM90012345678ABC";
const SALT = "a-server-held-salt-value-32-chars";

test("nin: masking keeps a recognisable head and tail and never echoes the middle", () => {
  const masked = maskNin(NIN);
  assert.equal(masked.length, NIN.length);
  assert.ok(masked.startsWith("CM"));
  assert.ok(masked.endsWith("ABC"));
  assert.ok(!masked.includes("90012345678"));
  assert.equal(maskNin("  cm90012345678abc  "), maskNin("cm90012345678abc"), "whitespace does not change the mask");
  for (const value of [null, undefined, "", "123", 42, {}]) {
    assert.equal(maskNin(value), "***");
  }
});

test("nin: the fingerprint is salted, stable and not reversible", () => {
  const first = ninFingerprint(NIN, SALT);
  assert.equal(first.length, 32);
  assert.equal(first, ninFingerprint(` ${NIN.toLowerCase()} `, SALT), "case and spacing are normalised");
  assert.notEqual(first, ninFingerprint(NIN, `${SALT}-other`), "a different salt gives a different fingerprint");
  assert.notEqual(first, ninFingerprint("CM90012345678ABD", SALT));
  assert.ok(!first.includes(NIN.toLowerCase()));
});

test("nin: without a real salt no fingerprint is produced", () => {
  for (const salt of [undefined, null, "", "short", "   "]) {
    assert.equal(ninFingerprint(NIN, salt), "", "an unsalted hash of a NIN is brute-forceable");
  }
  assert.equal(ninFingerprint("", SALT), "");
  assert.equal(ninFingerprint("AB1", SALT), "");
});

test("nin: NIN-shaped strings are recognised", () => {
  assert.equal(looksLikeNin(NIN), true);
  assert.equal(looksLikeNin("my id is CM90012345678ABC ok"), true);
  assert.equal(looksLikeNin("no identity here"), false);
  assert.equal(looksLikeNin(null), false);
});

test("logs: a NIN or a phone number never reaches a log line unmasked", () => {
  const line = `Failed for ${NIN} on +256772198420 (0771234567)`;
  const scrubbed = scrubForLog(line);
  assert.ok(!scrubbed.includes(NIN));
  assert.ok(!scrubbed.includes("+256772198420"));
  assert.ok(!scrubbed.includes("0771234567"));
  assert.match(scrubbed, /\+256\*\*\*20/);
  assert.equal(scrubForLog(null), "");
  assert.equal(scrubForLog(42), "42");
});

test("logs: a context object is scrubbed field by field and nested values are summarised", () => {
  const scrubbed = scrubFields({
    uid: "worker_1",
    nin: NIN,
    phone: "+256772198420",
    nested: { secret: NIN },
    missing: undefined,
  });
  assert.equal(scrubbed.uid, "worker_1");
  assert.ok(!scrubbed.nin.includes(NIN));
  assert.ok(!scrubbed.phone.includes("198420"));
  assert.equal(scrubbed.nested, "[object]");
  assert.equal(scrubbed.missing, "");
});

test("export: a guarantor submission is redacted before it leaves the server", () => {
  const submission = {
    id: "worker_1",
    status: "APPROVED",
    submittedAt: 1_700_000_000_000,
    reviewerFeedback: "verified",
    groupPhotoStoragePath: "users/worker_1/guarantors/group_1.jpg",
    guarantors: [
      {
        fullName: "Third Party",
        phoneNumber: "+256772198420",
        nationalIdNumber: NIN,
        guarantorPhotoStoragePath: "users/worker_1/guarantors/g_1.jpg",
        relationship: "Sister",
        district: "Nakawa",
        consentProvided: true,
        consentVersion: "2026-11-19",
        consentRecordedAt: 1_700_000_000_000,
        ninVerificationStatus: "VERIFIED",
      },
    ],
  };

  const exported = sanitiseGuarantorSubmissionForExport(submission);
  assert.equal(exported.status, "APPROVED");
  const row = exported.guarantors[0];
  assert.equal(row.nationalIdNumber, maskNin(NIN), "the third party's NIN is masked");
  assert.equal(row.relationship, "Sister");
  assert.equal(row.consentVersion, "2026-11-19");
  assert.equal(row.consentRecordedAt, 1_700_000_000_000);
  assert.equal(row.fullName, undefined, "the third party's name is not exported");
  assert.equal(row.phoneNumber, undefined);
  assert.equal(row.guarantorPhotoStoragePath, undefined);
  assert.equal(exported.groupPhotoStoragePath, undefined, "a storage path is a capability, not data");
  assert.match(exported.redactionNote, /third-party/i);

  assert.equal(sanitiseGuarantorSubmissionForExport(null), null);
  assert.equal(sanitiseGuarantorSubmissionForExport("nope"), null);
  const empty = sanitiseGuarantorSubmissionForExport({});
  assert.deepEqual(empty.guarantors, []);
});
