/**
 * R08-P2-02 / P2-06: the server wallet ledger and its controls.
 *
 * The ledger is pure, so these tests exercise the money semantics that Firestore cannot: an entry is
 * either withdrawable or a statement, a cashout can only ever debit once, and the destination rules
 * are decided from the profile rather than from anything the caller sends.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  summariseWallet,
  signedMinor,
  isOverdrawn,
  earningEntry,
  cashoutEntry,
  reversalEntry,
  cashoutKey,
  decideCashout,
  decideCashoutDestination,
  normaliseMsisdn,
  maskMsisdn,
  CASHOUT_MIN_UGX,
  CASHOUT_DAILY_LIMIT_UGX,
  CASHOUT_MAX_PER_DAY,
  DESTINATION_COOLOFF_MS,
} from "../lib/wallet.js";

const UID = "worker-uid-1";

function entry(overrides = {}) {
  return {
    id: "entry-1",
    uid: UID,
    kind: "EARNING",
    amountUgx: 10000,
    direction: "CREDIT",
    createdAt: 1,
    ...overrides,
  };
}

// ---------------------------------------------------------------- ledger shape

test("wallet: ids are derived, so a retry cannot double count", () => {
  assert.equal(earningEntry({ uid: UID, payoutId: "PAYOUT-job-1", amountUgx: 5000, now: 1 }).id, "EARN-PAYOUT-job-1");
  assert.equal(
    cashoutEntry({ uid: UID, withdrawalId: "WD-1", amountUgx: 5000, requestId: "req-1", now: 1 }).id,
    "WD-req-1"
  );
  assert.equal(
    reversalEntry({ uid: UID, withdrawalId: "WD-1", amountUgx: 5000, requestId: "req-1", now: 1, reason: "x" }).id,
    "REVERSAL-req-1"
  );
  assert.equal(cashoutKey(UID, "req-1"), `WD-${UID}-req-1`);
});

test("wallet: an escrow release earnings entry is a statement unless marked withdrawable", () => {
  const statement = earningEntry({ uid: UID, payoutId: "PAYOUT-1", amountUgx: 40000, now: 1 });
  assert.equal(statement.withdrawable, false);
  assert.equal(statement.direction, "CREDIT");
  assert.equal(statement.kind, "EARNING");
  // Money the provider paid straight to Mobile Money must not increase the spendable balance.
  assert.equal(summariseWallet([statement]).availableUgx, 0);
  assert.equal(summariseWallet([statement]).creditsUgx, 40000);

  const held = earningEntry({ uid: UID, payoutId: "PAYOUT-2", amountUgx: 40000, now: 1, withdrawable: true });
  assert.equal(summariseWallet([held]).availableUgx, 40000);
});

test("wallet: a cashout debits once and its reversal restores exactly the same amount", () => {
  const credit = earningEntry({ uid: UID, payoutId: "PAYOUT-1", amountUgx: 30000, now: 1, withdrawable: true });
  const reserve = cashoutEntry({ uid: UID, withdrawalId: "WD-1", amountUgx: 12000, requestId: "req-1", now: 2 });
  const afterCashout = summariseWallet([credit, reserve]);
  assert.equal(afterCashout.availableUgx, 18000);
  assert.equal(afterCashout.debitsUgx, 12000);

  const reversal = reversalEntry({ uid: UID, withdrawalId: "WD-1", amountUgx: 12000, requestId: "req-1", now: 3, reason: "failed" });
  assert.equal(reversal.direction, "CREDIT");
  assert.equal(summariseWallet([credit, reserve, reversal]).availableUgx, 30000);
  assert.equal(isOverdrawn([credit, reserve, reversal]), false);
});

test("wallet: a debit larger than the credits is detectable as an overdraft", () => {
  const credit = earningEntry({ uid: UID, payoutId: "PAYOUT-1", amountUgx: 9000, now: 1, withdrawable: true });
  const reserve = cashoutEntry({ uid: UID, withdrawalId: "WD-1", amountUgx: 10000, requestId: "req-1", now: 2 });
  const summary = summariseWallet([credit, reserve]);
  assert.equal(summary.availableUgx, 0, "a negative balance is reported as zero, never as spendable");
  assert.equal(isOverdrawn([credit, reserve]), true, "...and is flagged instead of hidden");
  assert.equal(signedMinor({ kind: "RESERVE", amountUgx: 10, direction: "DEBIT" }), -10);
  assert.equal(signedMinor({ kind: "EARNING", amountUgx: 10, direction: "CREDIT", withdrawable: false }), 0);
});

// ------------------------------------------------------------ cashout decisions

const healthy = { availableUgx: 100000, todayCount: 0, todayTotalUgx: 0, duplicateKey: false };

test("cashout: the minimum, the daily limit and the per-day count are enforced", () => {
  assert.equal(decideCashout(CASHOUT_MIN_UGX - 1, healthy, "k").code, "below_minimum");

  const overDaily = decideCashout(150000, { ...healthy, availableUgx: 500000, todayTotalUgx: CASHOUT_DAILY_LIMIT_UGX - 1000 }, "k");
  assert.equal(overDaily.code, "daily_limit");

  const tooMany = decideCashout(20000, { ...healthy, todayCount: CASHOUT_MAX_PER_DAY }, "k");
  assert.equal(tooMany.code, "too_many_requests");

  const tooMuch = decideCashout(200000, healthy, "k");
  assert.equal(tooMuch.code, "exceeds_available");
  assert.match(tooMuch.message, /100000/);
});

test("cashout: invalid amounts, duplicates and a valid request", () => {
  assert.equal(decideCashout(1000.5, healthy, "k").code, "invalid_amount");
  assert.equal(decideCashout(-5000, healthy, "k").code, "invalid_amount");
  assert.equal(decideCashout("5000", healthy, "k").code, "invalid_amount");
  assert.equal(decideCashout(10000, { ...healthy, duplicateKey: true }, "k").code, "duplicate_request");

  const ok = decideCashout(10000, healthy, "WD-key");
  assert.equal(ok.ok, true);
  assert.equal(ok.amountUgx, 10000);
  assert.equal(ok.idempotencyKey, "WD-key");
});

test("cashout: the balance is the server's, never the request's", () => {
  // A caller cannot pass a balance: only the context the server built is consulted.
  const decision = decideCashout(50000, { ...healthy, availableUgx: 49999 }, "k");
  assert.equal(decision.ok, false);
  assert.equal(decision.code, "exceeds_available");
});

// ---------------------------------------------------------- destination controls

test("destination: numbers are normalised and masked", () => {
  assert.equal(normaliseMsisdn("0771234567"), "+256771234567");
  assert.equal(normaliseMsisdn("+256 771 234 567"), "+256771234567");
  assert.equal(normaliseMsisdn("256771234567"), "+256771234567");
  assert.equal(normaliseMsisdn("(0771) 234-567"), "+256771234567");
  assert.equal(normaliseMsisdn("12345"), null);
  assert.equal(normaliseMsisdn(null), null);
  assert.equal(maskMsisdn("0771234567"), "+25677***567");
  assert.equal(maskMsisdn("nonsense"), "unknown");
});

test("destination: missing, unverified, recently changed and off-list numbers are refused", () => {
  const now = 1_800_000_000_000;

  const missing = decideCashoutDestination({ storedMsisdn: "", verified: true, now });
  assert.equal(missing.code, "no_destination");

  const unverified = decideCashoutDestination({ storedMsisdn: "0771234567", verified: false, now });
  assert.equal(unverified.code, "unverified_destination");

  const changed = decideCashoutDestination({
    storedMsisdn: "0771234567",
    verified: true,
    lastChangedAt: now - DESTINATION_COOLOFF_MS + 1000,
    now,
  });
  assert.equal(changed.code, "destination_recently_changed", "a new number must be re-verified first");

  const old = decideCashoutDestination({
    storedMsisdn: "0771234567",
    verified: true,
    lastChangedAt: now - DESTINATION_COOLOFF_MS - 1000,
    now,
  });
  assert.equal(old.ok, true);

  const offList = decideCashoutDestination({
    storedMsisdn: "0771234567",
    verified: true,
    now,
    allowList: ["+256700000001"],
  });
  assert.equal(offList.code, "destination_not_allowed");

  const onList = decideCashoutDestination({
    storedMsisdn: "0771234567",
    verified: true,
    now,
    allowList: ["0771 234 567"],
  });
  assert.equal(onList.ok, true);
  assert.equal(onList.msisdn, "+256771234567");
});
