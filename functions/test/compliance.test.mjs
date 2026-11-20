/** REM-044: consent and DSAR export-shape tests (pure, no Firestore). */
import test from "node:test";
import assert from "node:assert/strict";

import {
  buildConsentRecord,
  sanitiseGuarantorForExport,
  buildDsarEnvelope,
  CONSENT_VERSION,
  DSAR_SLA_DAYS,
  GUARANTOR_CONSENT_SCOPE,
  MAX_GUARANTORS_PER_SUBMISSION,
  buildGuarantorConsentRecord,
  buildConsentWithdrawal,
  buildErasureNotice,
} from "../lib/compliance.js";

const NOW = 1_800_000_000_000;
const grants = { terms: true, privacy: true, dataProcessing: true };

test("consent requires a dated version and every grant", () => {
  const record = buildConsentRecord({ version: CONSENT_VERSION, grants }, NOW);
  assert.deepEqual(record.grants, grants);
  assert.equal(record.acceptedAt, NOW);
  assert.equal(record.version, CONSENT_VERSION);

  assert.throws(() => buildConsentRecord({ version: "v1", grants }, NOW));
  assert.throws(() => buildConsentRecord({ version: CONSENT_VERSION, grants: { ...grants, privacy: false } }, NOW));
  assert.throws(() => buildConsentRecord({ version: CONSENT_VERSION, grants: {} }, NOW));
  assert.throws(() => buildConsentRecord(undefined, NOW));
});

test("guarantor export drops third-party identifiers and paths", () => {
  const exported = sanitiseGuarantorForExport({
    fullName: "Jane Doe",
    relationship: "Sister",
    district: "Kampala",
    consentProvided: true,
    nationalIdNumber: "UG-123456",
    phoneNumber: "+256772000001",
    guarantorPhotoStoragePath: "users/u1/guarantors/a.jpg",
    ninVerificationStatus: "APPROVED",
    status: "APPROVED",
  });
  assert.deepEqual(Object.keys(exported).sort(), [
    "consentProvided",
    "district",
    "fullName",
    "ninVerificationStatus",
    "relationship",
    "status",
  ]);
  assert.ok(!JSON.stringify(exported).includes("UG-123456"));
  assert.ok(!JSON.stringify(exported).includes("+256772000001"));
});

test("DSAR envelope states a 30-day due date and a format version", () => {
  const envelope = buildDsarEnvelope({ profile: { id: "u1" } }, NOW);
  assert.equal(envelope.generatedAt, NOW);
  assert.equal(envelope.slaDueAt, NOW + DSAR_SLA_DAYS * 24 * 60 * 60 * 1000);
  assert.equal(envelope.formatVersion, 1);
  assert.deepEqual(envelope.data, { profile: { id: "u1" } });
});

// ---------------------------------------------------------------------------------------------
// R09-P2-02 / R09-P2-06
// ---------------------------------------------------------------------------------------------

test("guarantor consent: recorded with a dated version, a count and an explicit confirmation", () => {
  const record = buildGuarantorConsentRecord(
    { version: CONSENT_VERSION, guarantorCount: 2, confirmed: true },
    NOW
  );
  assert.equal(record.scope, GUARANTOR_CONSENT_SCOPE);
  assert.equal(record.version, CONSENT_VERSION);
  assert.equal(record.recordedAt, NOW);
  assert.equal(record.guarantorCount, 2);
  assert.equal(record.confirmedByWorker, true);
  assert.equal(record.separateGrant, true, "third-party consent is its own grant, not the worker's");
  assert.equal(record.source, "android");
});

test("guarantor consent fails closed without confirmation, a version or a sane count", () => {
  assert.throws(
    () => buildGuarantorConsentRecord({ version: CONSENT_VERSION, guarantorCount: 2, confirmed: false }, NOW),
    /agreed/
  );
  assert.throws(
    () => buildGuarantorConsentRecord({ version: CONSENT_VERSION, guarantorCount: 2 }, NOW),
    /agreed/
  );
  assert.throws(
    () => buildGuarantorConsentRecord({ version: "latest", guarantorCount: 2, confirmed: true }, NOW),
    /version/
  );
  assert.throws(
    () => buildGuarantorConsentRecord({ version: CONSENT_VERSION, guarantorCount: 0, confirmed: true }, NOW),
    /guarantorCount/
  );
  assert.throws(
    () =>
      buildGuarantorConsentRecord(
        { version: CONSENT_VERSION, guarantorCount: MAX_GUARANTORS_PER_SUBMISSION + 1, confirmed: true },
        NOW
      ),
    /guarantorCount/
  );
  assert.throws(
    () => buildGuarantorConsentRecord({ version: CONSENT_VERSION, guarantorCount: "2", confirmed: true }, NOW),
    /guarantorCount/
  );
});

test("consent withdrawal is a dated record, not a deleted receipt", () => {
  const withdrawal = buildConsentWithdrawal({ version: CONSENT_VERSION, reason: "changed my mind" }, NOW);
  assert.equal(withdrawal.version, CONSENT_VERSION);
  assert.equal(withdrawal.revokedAt, NOW);
  assert.equal(withdrawal.reason, "changed my mind");
  assert.equal(buildConsentWithdrawal({ version: CONSENT_VERSION }, NOW).reason.length > 0, true);
  assert.throws(() => buildConsentWithdrawal({ version: "" }, NOW), /version/);
  assert.throws(() => buildConsentWithdrawal(undefined, NOW), /version/);
});

test("erasure notice states what was deleted and what the law keeps", () => {
  const retained = [
    { category: "payment and escrow records", reason: "AML rules", retentionDays: 2555 },
    { category: "consent receipts", reason: "demonstrability", retentionDays: 2190 },
  ];
  const notice = buildErasureNotice({
    deletedRecords: 42,
    deletedFiles: 7,
    retained,
    complete: true,
  });
  assert.match(notice, /Deleted 42 record\(s\) and 7 file\(s\)/);
  assert.match(notice, /payment and escrow records/);
  assert.match(notice, /consent receipts/);
  assert.match(notice, /year/, "the notice says how long, not just that something is kept");

  const blocked = buildErasureNotice({
    deletedRecords: 5,
    deletedFiles: 1,
    retained,
    complete: false,
    pendingRecords: 3,
    pendingFiles: 2,
  });
  assert.match(blocked, /not finished/);
  assert.match(blocked, /3 record\(s\)/);
  assert.match(blocked, /2 file\(s\)/);
  assert.match(blocked, /nothing was lost/);

  const clean = buildErasureNotice({ deletedRecords: 1, deletedFiles: 0, retained: [], complete: true });
  assert.doesNotMatch(clean, /Kept because/);
});
