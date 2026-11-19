/** REM-044: consent and DSAR export-shape tests (pure, no Firestore). */
import test from "node:test";
import assert from "node:assert/strict";

import {
  buildConsentRecord,
  sanitiseGuarantorForExport,
  buildDsarEnvelope,
  CONSENT_VERSION,
  DSAR_SLA_DAYS,
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
