/**
 * R07: executable Firestore security-rules tests.
 *
 * The review's verdict rested on rules behaviour that nobody had executed: "no public read",
 * "the owner cannot forge escrow", "deletion is possible". Those were read from source. This file
 * runs them against the local emulator for every actor the rules distinguish: anonymous, an
 * unrelated signed-in account, the job owner, the assigned provider, the account owner and an admin.
 *
 * Each negative test changes exactly one thing relative to a request the rules accept, so a failure
 * names the rule that denied it (escrow, money, ownership or PIN) rather than "some rule".
 *
 * Run with `npm test` inside `rules-test/`; CI wraps it in `firebase emulators:exec`.
 * Storage rules cannot run here (they need the Storage emulator) and are covered by
 * `storage.rules.check.mjs`.
 */
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, updateDoc, deleteDoc, collection, getDocs } from "firebase/firestore";

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, "..");

const OWNER = "owner_uid";
const WORKER = "worker_uid";
const STRANGER = "stranger_uid";
const ADMIN = "admin_uid";
const JOB = "job_completed";     // COMPLETED, proof submitted: the escrow-forgery scenario
const JOB_LIVE = "job_live";     // ACCEPTED_FUNDED: the provider's own progress scenario
const JOB_REQUEST = "job_request"; // REQUESTED: the owner's own pricing scenario

let env;

const nowSeconds = () => Math.floor(Date.now() / 1000);
const freshAuth = () => ({ auth_time: nowSeconds() });
const staleAuth = () => ({ auth_time: nowSeconds() - 3600 });
const adminAuth = () => ({ admin: true, auth_time: nowSeconds() });

const baseJob = (overrides = {}) => ({
  postedByUid: OWNER,
  assignedProviderUid: WORKER,
  assignedProviderId: WORKER,
  status: "COMPLETED",
  escrowStatus: "PROOF_SUBMITTED",
  postedByResident: "Resident",
  budget: 50000,
  grandTotal: 55000,
  platformFee: 5000,
  laborTotal: 50000,
  materialsSubtotal: 0,
  ...overrides,
});

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-skills-exchange",
    firestore: { rules: readFileSync(join(repoRoot, "firestore.rules"), "utf8") },
  });
});

test.after(async () => {
  await env?.cleanup();
});

test.beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "jobs", JOB), baseJob());
    await setDoc(doc(db, "jobs", JOB_LIVE), baseJob({ status: "ACCEPTED_FUNDED", escrowStatus: "FUNDS_LOCKED_IN_ESCROW" }));
    await setDoc(doc(db, "jobs", JOB_REQUEST), baseJob({
      status: "REQUESTED",
      escrowStatus: "QUOTE_PENDING",
      assignedProviderUid: null,
      assignedProviderId: null,
    }));
    await setDoc(doc(db, "users", OWNER), { fullName: "Owner", phoneNumber: "+256700000001" });
    await setDoc(doc(db, "worker_kyc", OWNER), { kycStatus: "APPROVED" });
    await setDoc(doc(db, "worker_kyc", WORKER), { kycStatus: "APPROVED" });
    await setDoc(doc(db, "worker_training", OWNER), { completed: true });
    await setDoc(doc(db, "providers", OWNER), { name: "Provider" });
    await setDoc(doc(db, "notifications", "n1"), { userId: OWNER, body: "hello" });
    await setDoc(doc(db, "momo_transactions", "t1"), { userId: OWNER, amountUgx: 1000 });
    await setDoc(doc(db, "job_secrets", JOB), { pinHash: "hash", salt: "salt", attempts: 0 });
    // R08: the money books.
    await setDoc(doc(db, "wallets", WORKER), { uid: WORKER, balanceUgx: 25000 });
    await setDoc(doc(db, "wallet_entries", "e1"), { uid: WORKER, kind: "EARNING", amountUgx: 25000, direction: "CREDIT" });
    await setDoc(doc(db, "wallet_withdrawals", "w1"), { uid: WORKER, amountUgx: 10000, status: "PROCESSING" });
    await setDoc(doc(db, "payouts", "PAYOUT-1"), { payoutId: "PAYOUT-1", jobId: JOB, workerId: WORKER, amount: 50000 });
    await setDoc(doc(db, "materialAdvances", JOB), { jobId: JOB, requestedBy: OWNER, workerId: WORKER, advanceAmount: 20000, status: "PENDING" });
    await setDoc(doc(db, "platform_fees", "FEE-1"), { jobId: JOB, platformFeeUgx: 5000 });
    await setDoc(doc(db, "reconciliation_reports", "r1"), { checked: 1 });
    await setDoc(doc(db, "jobs", JOB, "messages", "m1"), { senderId: OWNER, senderName: "Owner", body: "hi" });
  });
});

const as = (uid, token) => env.authenticatedContext(uid, token).firestore();
const anon = () => env.unauthenticatedContext().firestore();

// ---------------------------------------------------------------- public reads (REM-010)

test("job documents are not world-readable and not readable by unrelated accounts", async () => {
  await assertFails(getDoc(doc(anon(), "jobs", JOB)));
  await assertFails(getDoc(doc(as(STRANGER, freshAuth()), "jobs", JOB)));
  await assertSucceeds(getDoc(doc(as(OWNER, freshAuth()), "jobs", JOB)));
  await assertSucceeds(getDoc(doc(as(WORKER, freshAuth()), "jobs", JOB)));
  await assertSucceeds(getDoc(doc(as(ADMIN, adminAuth()), "jobs", JOB)));
});

test("an unfiltered job listing is denied, so jobs cannot be enumerated", async () => {
  await assertFails(getDocs(collection(as(OWNER, freshAuth()), "jobs")));
  await assertFails(getDocs(collection(anon(), "jobs")));
});

test("chat messages are readable only by a job participant or the author", async () => {
  await assertFails(getDoc(doc(anon(), "jobs", JOB, "messages", "m1")));
  await assertFails(getDoc(doc(as(STRANGER, freshAuth()), "jobs", JOB, "messages", "m1")));
  await assertSucceeds(getDoc(doc(as(OWNER, freshAuth()), "jobs", JOB, "messages", "m1")));
  await assertSucceeds(getDoc(doc(as(WORKER, freshAuth()), "jobs", JOB, "messages", "m1")));
});

test("account documents, notifications and the money ledger are owner-scoped", async () => {
  for (const [collectionName, id] of [
    ["users", OWNER],
    ["worker_kyc", OWNER],
    ["notifications", "n1"],
    ["momo_transactions", "t1"],
  ]) {
    await assertFails(getDoc(doc(anon(), collectionName, id)));
    await assertFails(getDoc(doc(as(STRANGER, freshAuth()), collectionName, id)));
    await assertSucceeds(getDoc(doc(as(OWNER, freshAuth()), collectionName, id)));
    await assertSucceeds(getDoc(doc(as(ADMIN, adminAuth()), collectionName, id)));
  }
});

// ---------------------------------------------------------------- R07-P2-03 escrow and money

test("the owner cannot forge a payout: RELEASED + RELEASED_TO_WORKER is denied", async () => {
  const db = as(OWNER, freshAuth());
  // The status transition alone is legal (Case 4). Only the escrow rule may deny this.
  await assertFails(
    updateDoc(doc(db, "jobs", JOB), { status: "RELEASED", escrowStatus: "RELEASED_TO_WORKER" })
  );
});

test("the owner cannot forge payment received: FUNDS_LOCKED_IN_ESCROW is denied", async () => {
  const db = as(OWNER, freshAuth());
  await assertFails(
    updateDoc(doc(db, "jobs", JOB_REQUEST), {
      status: "ACCEPTED_FUNDED",
      escrowStatus: "FUNDS_LOCKED_IN_ESCROW",
    })
  );
});

test("an unrelated account cannot move escrow or status", async () => {
  const db = as(STRANGER, freshAuth());
  await assertFails(updateDoc(doc(db, "jobs", JOB_LIVE), { status: "IN_PROGRESS", escrowStatus: "WORK_IN_PROGRESS" }));
});

test("the assigned provider may record its own non-terminal progress", async () => {
  const db = as(WORKER, freshAuth());
  await assertSucceeds(
    updateDoc(doc(db, "jobs", JOB_LIVE), { status: "IN_PROGRESS", escrowStatus: "WORK_IN_PROGRESS" })
  );
});

test("server-owned release and PIN fields cannot be written by a client", async () => {
  const db = as(OWNER, freshAuth());
  for (const patch of [
    { releasedAt: 123 },
    { releasedBy: STRANGER },
    { payoutAmountUgx: 999999 },
    { platformFeeUgx: 0 },
    { doorstepPin: "1234" },
    { doorstepPinHash: "forged" },
    { doorstepPinAttempts: 0 },
    { doorstepPinLockedUntil: 0 },
  ]) {
    await assertFails(updateDoc(doc(db, "jobs", JOB), { status: "DISPUTED", ...patch }));
  }
});

test("priced amounts are frozen once money is attached, editable while only requested", async () => {
  const db = as(OWNER, freshAuth());
  await assertFails(updateDoc(doc(db, "jobs", JOB), { status: "DISPUTED", grandTotal: 1 }));
  await assertSucceeds(
    updateDoc(doc(db, "jobs", JOB_REQUEST), { status: "QUOTED", budget: 60000 })
  );
});

test("ownership can never be transferred, and jobs are never deleted from a client", async () => {
  const db = as(OWNER, freshAuth());
  await assertFails(updateDoc(doc(db, "jobs", JOB), { status: "DISPUTED", postedByUid: STRANGER }));
  await assertFails(deleteDoc(doc(db, "jobs", JOB)));
});

// ---------------------------------------------------------------- R07-P2-01 PIN material

test("doorstep PIN material is unreachable from every client, admin included", async () => {
  for (const actor of [
    anon(),
    as(OWNER, freshAuth()),
    as(WORKER, freshAuth()),
    as(ADMIN, adminAuth()),
  ]) {
    await assertFails(getDoc(doc(actor, "job_secrets", JOB)));
    await assertFails(setDoc(doc(actor, "job_secrets", JOB), { pinHash: "forged" }));
  }
});

// ---------------------------------------------------------------- R07-P2-02 erasure

test("the account owner can erase their own data only with a fresh sign-in", async () => {
  await assertFails(deleteDoc(doc(as(OWNER, staleAuth()), "users", OWNER)));
  await assertFails(deleteDoc(doc(as(OWNER, staleAuth()), "worker_kyc", OWNER)));
  await assertFails(deleteDoc(doc(as(STRANGER, freshAuth()), "users", OWNER)));

  await assertSucceeds(deleteDoc(doc(as(OWNER, freshAuth()), "users", OWNER)));
  await assertSucceeds(deleteDoc(doc(as(OWNER, freshAuth()), "worker_kyc", OWNER)));
  await assertSucceeds(deleteDoc(doc(as(OWNER, freshAuth()), "worker_training", OWNER)));
  await assertSucceeds(deleteDoc(doc(as(OWNER, freshAuth()), "providers", OWNER)));
});

test("nobody can grant themselves a privileged flag", async () => {
  const db = as(OWNER, freshAuth());
  await assertFails(updateDoc(doc(db, "users", OWNER), { admin: true }));
  await assertFails(updateDoc(doc(db, "users", OWNER), { isWorkerActivated: true }));
  await assertFails(updateDoc(doc(db, "users", OWNER), { workerKycStatus: "APPROVED" }));
});

test("notifications are server-written; owners may only mark them read", async () => {
  const db = as(OWNER, freshAuth());
  await assertFails(setDoc(doc(db, "notifications", "forged"), { userId: OWNER, body: "x" }));
  await assertSucceeds(updateDoc(doc(db, "notifications", "n1"), { read: true }));
  await assertFails(updateDoc(doc(db, "notifications", "n1"), { body: "rewritten" }));
});

test("the MoMo ledger cannot be written by a client", async () => {
  const db = as(OWNER, freshAuth());
  await assertFails(setDoc(doc(db, "momo_transactions", "forged"), { userId: OWNER, amountUgx: 1 }));
  await assertFails(updateDoc(doc(db, "momo_transactions", "t1"), { amountUgx: 1 }));
});

test("a chat message cannot be posted as another account", async () => {
  const db = as(STRANGER, freshAuth());
  await assertFails(
    setDoc(doc(db, "jobs", JOB, "messages", "forged"), { senderId: OWNER, senderName: "Owner", body: "x" })
  );
});

// ---------------------------------------------------------------- R08 money books

test("the wallet balance and its ledger are never readable from a client, not even by the owner", async () => {
  for (const actor of [anon(), as(WORKER, freshAuth()), as(OWNER, freshAuth())]) {
    await assertFails(getDoc(doc(actor, "wallets", WORKER)));
    await assertFails(getDoc(doc(actor, "wallet_entries", "e1")));
    await assertFails(getDoc(doc(actor, "wallet_withdrawals", "w1")));
  }
  // An administrator may read the books for support, and nobody may write them.
  await assertSucceeds(getDoc(doc(as(ADMIN, adminAuth()), "wallets", WORKER)));
  for (const actor of [as(WORKER, freshAuth()), as(ADMIN, adminAuth())]) {
    await assertFails(setDoc(doc(actor, "wallets", WORKER), { balanceUgx: 999999 }));
    await assertFails(setDoc(doc(actor, "wallet_entries", "forged"), { uid: WORKER, amountUgx: 999999, direction: "CREDIT" }));
    await assertFails(setDoc(doc(actor, "wallet_withdrawals", "forged"), { uid: WORKER, amountUgx: 1 }));
    await assertFails(updateDoc(doc(actor, "wallets", WORKER), { balanceUgx: 999999 }));
  }
});

test("payout, fee and reconciliation records are admin-readable and never client-written", async () => {
  for (const path of [["payouts", "PAYOUT-1"], ["platform_fees", "FEE-1"], ["reconciliation_reports", "r1"]]) {
    await assertFails(getDoc(doc(as(WORKER, freshAuth()), path[0], path[1])));
    await assertFails(getDoc(doc(as(OWNER, freshAuth()), path[0], path[1])));
    await assertSucceeds(getDoc(doc(as(ADMIN, adminAuth()), path[0], path[1])));
    await assertFails(setDoc(doc(as(ADMIN, adminAuth()), path[0], "forged"), { forged: true }));
  }
});

test("a material advance is visible to the resident and the worker, and writable by neither", async () => {
  await assertSucceeds(getDoc(doc(as(OWNER, freshAuth()), "materialAdvances", JOB)));
  await assertSucceeds(getDoc(doc(as(WORKER, freshAuth()), "materialAdvances", JOB)));
  await assertFails(getDoc(doc(as(STRANGER, freshAuth()), "materialAdvances", JOB)));

  const owner = as(OWNER, freshAuth());
  await assertFails(setDoc(doc(owner, "materialAdvances", "forged"), { jobId: "forged", advanceAmount: 1 }));
  await assertFails(updateDoc(doc(owner, "materialAdvances", JOB), { advanceAmount: 999999 }));
});

// ------------------------------------------------------------------ R09 privacy & consent

const guarantorPayload = (overrides = {}) => ({
  workerKycId: WORKER,
  fullName: "Third Party",
  nationalIdNumber: "CM90012345678ABC",
  phoneNumber: "+256772198420",
  relationship: "Sister",
  district: "Nakawa",
  guarantorPhotoStoragePath: `users/${WORKER}/guarantors/guarantor_1.jpg`,
  workerWithGuarantorPhotoStoragePath: `users/${WORKER}/guarantors/with_worker_1.jpg`,
  consentProvided: true,
  consentVersion: "2026-11-19",
  consentRecordedAt: 1_760_000_000_000,
  ninVerificationStatus: "PENDING",
  ...overrides,
});

const submissionPayload = (overrides = {}) => ({
  workerId: WORKER,
  guarantorCount: 2,
  consentConfirmed: true,
  consentVersion: "2026-11-19",
  consentRecordedAt: 1_760_000_000_000,
  groupPhotoStoragePath: `users/${WORKER}/guarantors/group_1.jpg`,
  status: "PENDING_REVIEW",
  ...overrides,
});

test("a guarantor row is filed only with a dated, recorded third-party consent", async () => {
  const worker = as(WORKER, freshAuth());

  // One field removed at a time: the consent proof is what the rule is there for. Each denial uses a
  // fresh document id, so each is a create rather than an update.
  const missingVersion = guarantorPayload();
  delete missingVersion.consentVersion;
  await assertFails(setDoc(doc(worker, "guarantors", "g_no_version"), missingVersion));

  const missingTimestamp = guarantorPayload();
  delete missingTimestamp.consentRecordedAt;
  await assertFails(setDoc(doc(worker, "guarantors", "g_no_time"), missingTimestamp));

  await assertFails(
    setDoc(doc(worker, "guarantors", "g_bad_version"), guarantorPayload({ consentVersion: "latest" }))
  );
  await assertFails(
    setDoc(doc(worker, "guarantors", "g_false_consent"), guarantorPayload({ consentProvided: false }))
  );
  // The same row with the dated receipt is accepted.
  await assertSucceeds(setDoc(doc(worker, "guarantors", "g_ok"), guarantorPayload()));

  // The consent proof is immutable after filing, and the reviewer's own fields stay admin-only.
  await assertFails(
    updateDoc(doc(worker, "guarantors", "g_ok"), { consentRecordedAt: 1_760_000_009_999 })
  );
  await assertFails(updateDoc(doc(worker, "guarantors", "g_ok"), { ninMasked: "CM*********ABC" }));
});

test("a guarantor submission must name the consent version it was filed under", async () => {
  const worker = as(WORKER, freshAuth());

  // The denials run first, so each one is a *create* (the document does not exist yet) and the create
  // rule is what rejects it. Re-ordering these after the success would silently test the update rule.
  const noVersion = submissionPayload();
  delete noVersion.consentVersion;
  await assertFails(setDoc(doc(worker, "guarantor_submissions", WORKER), noVersion));
  await assertFails(
    setDoc(doc(worker, "guarantor_submissions", WORKER), submissionPayload({ consentVersion: "v2" }))
  );
  await assertFails(
    setDoc(doc(worker, "guarantor_submissions", WORKER), submissionPayload({ consentRecordedAt: 1 }))
  );
  // A submission is never filed for someone else's account.
  await assertFails(
    setDoc(doc(as(STRANGER, freshAuth()), "guarantor_submissions", WORKER), submissionPayload())
  );
  // The same payload with a dated version and a real receipt timestamp is accepted.
  await assertSucceeds(setDoc(doc(worker, "guarantor_submissions", WORKER), submissionPayload()));
});

test("consent receipts are readable by their owner and forgeable by nobody", async () => {
  await env.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await setDoc(doc(db, "users", WORKER, "consents", "2026-11-19"), { version: "2026-11-19", acceptedAt: 1 });
    await setDoc(doc(db, "users", WORKER, "guarantor_consents", "2026-11-19"), { version: "2026-11-19", recordedAt: 1 });
  });

  await assertSucceeds(getDoc(doc(as(WORKER, freshAuth()), "users", WORKER, "consents", "2026-11-19")));
  await assertSucceeds(
    getDoc(doc(as(WORKER, freshAuth()), "users", WORKER, "guarantor_consents", "2026-11-19"))
  );
  await assertFails(getDoc(doc(as(STRANGER, freshAuth()), "users", WORKER, "consents", "2026-11-19")));
  await assertFails(
    getDoc(doc(as(STRANGER, freshAuth()), "users", WORKER, "guarantor_consents", "2026-11-19"))
  );

  const worker = as(WORKER, freshAuth());
  await assertFails(setDoc(doc(worker, "users", WORKER, "consents", "2026-11-20"), { acceptedAt: 2 }));
  await assertFails(
    setDoc(doc(worker, "users", WORKER, "guarantor_consents", "2026-11-20"), { recordedAt: 2 })
  );
  await assertFails(deleteDoc(doc(worker, "users", WORKER, "consents", "2026-11-19")));
  await assertFails(deleteDoc(doc(worker, "users", WORKER, "guarantor_consents", "2026-11-19")));
  // Withdrawal is a server-written revocation on the same receipt, not a client edit.
  await assertFails(
    updateDoc(doc(worker, "users", WORKER, "consents", "2026-11-19"), { revokedAt: 3 })
  );
});

test("the retention and erasure reports are admin evidence, never client data", async () => {
  for (const [collectionName, id] of [["retention_reports", "r1"], ["account_deletions", "d1"]]) {
    await assertFails(getDoc(doc(as(WORKER, freshAuth()), collectionName, id)));
    await assertFails(getDoc(doc(as(OWNER, freshAuth()), collectionName, id)));
    await assertFails(getDoc(doc(anon(), collectionName, id)));
    await assertSucceeds(getDoc(doc(as(ADMIN, adminAuth()), collectionName, id)));
    await assertFails(setDoc(doc(as(WORKER, freshAuth()), collectionName, "forged"), { forged: true }));
    await assertFails(setDoc(doc(as(ADMIN, adminAuth()), collectionName, "forged"), { forged: true }));
  }
});
