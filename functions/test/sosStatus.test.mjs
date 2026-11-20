/**
 * R10-P2-03: the app must not claim help was contacted before the platform says so.
 *
 * The old copy ("Live GPS location & job telemetry broadcasted to local emergency contacts and
 * platform security") was written the instant the button was pressed. The server writes the real
 * outcome; this module turns it into a sentence, and the app displays it verbatim.
 */
import test from "node:test";
import assert from "node:assert/strict";

import {
  DISPATCH_STALE_MS,
  describeSosDispatch,
  shouldKeepChecking,
  dispatchChatMessage,
} from "../lib/sosStatus.js";

const NOW = 1_800_000_000_000;

test("sos: a successful dispatch says how many contacts were reached", () => {
  const description = describeSosDispatch(
    { status: "SENT", recipients: 2, delivered: 2, attemptedAt: NOW - 1000 },
    NOW
  );
  assert.equal(description.state, "SENT");
  assert.match(description.message, /2 contacts/);
  assert.equal(description.adviseDirectCall, false);
  assert.equal(shouldKeepChecking(description), false);
});

test("sos: a partial dispatch says so and advises calling", () => {
  const description = describeSosDispatch(
    { status: "PARTIAL", recipients: 3, delivered: 1, attemptedAt: NOW - 1000 },
    NOW
  );
  assert.equal(description.state, "PARTIAL");
  assert.match(description.message, /1 of 3/);
  assert.equal(description.adviseDirectCall, true);
});

test("sos: no recipients is stated plainly, with the fix", () => {
  const description = describeSosDispatch({ status: "NO_RECIPIENTS", recipients: 0, delivered: 0, attemptedAt: NOW }, NOW);
  assert.equal(description.state, "NO_RECIPIENTS");
  assert.match(description.message, /No emergency contact/);
  assert.match(description.message, /call the police/i);
  assert.equal(description.adviseDirectCall, true);
});

test("sos: a failed dispatch never reads as success", () => {
  const description = describeSosDispatch({ status: "FAILED", recipients: 2, delivered: 0, attemptedAt: NOW }, NOW);
  assert.equal(description.state, "FAILED");
  assert.match(description.message, /could not be sent/);
  assert.doesNotMatch(description.message, /delivered|on the way|help is/i);
  assert.equal(description.adviseDirectCall, true);
});

test("sos: absent dispatch is pending while fresh, unconfirmed when stale", () => {
  const pending = describeSosDispatch(undefined, NOW);
  assert.equal(pending.state, "NOT_DISPATCHED");
  assert.equal(pending.adviseDirectCall, true, "an unconfirmed alert always advises a direct call");
  assert.match(pending.message, /has not confirmed/i);

  const fresh = describeSosDispatch({ attemptedAt: NOW - 1000 }, NOW);
  assert.equal(fresh.state, "PENDING");
  assert.equal(shouldKeepChecking(fresh), true);

  const stale = describeSosDispatch({ attemptedAt: NOW - DISPATCH_STALE_MS - 1 }, NOW);
  assert.equal(stale.state, "STALE");
  assert.match(stale.message, /has not confirmed that anyone was contacted/);
  assert.equal(shouldKeepChecking(stale), false);
});

test("sos: an unreadable record is unknown, not optimistic", () => {
  const unknown = describeSosDispatch({ status: "SOMETHING_NEW", attemptedAt: NOW - DISPATCH_STALE_MS - 1 }, NOW);
  assert.equal(unknown.state, "UNKNOWN", "a status we do not recognise is reported as unreadable");
  assert.equal(unknown.adviseDirectCall, true);

  const stalePending = describeSosDispatch({ status: "PENDING", attemptedAt: NOW - DISPATCH_STALE_MS - 1 }, NOW);
  assert.equal(stalePending.state, "STALE", "a pending dispatch that never reported is unconfirmed");

  const noStatusFresh = describeSosDispatch({ status: "SOMETHING_NEW", attemptedAt: NOW }, NOW);
  assert.equal(noStatusFresh.state, "PENDING");
});

test("sos: the chat line reports the outcome, never a promise", () => {
  const sent = describeSosDispatch({ status: "SENT", recipients: 1, delivered: 1, attemptedAt: NOW }, NOW);
  const line = dispatchChatMessage(sent, "14:05");
  assert.match(line, /delivered to 1 contact/);
  assert.doesNotMatch(line, /help is on the way/i);

  const failed = describeSosDispatch({ status: "FAILED", recipients: 1, delivered: 0, attemptedAt: NOW }, NOW);
  assert.match(dispatchChatMessage(failed, "14:05"), /could not be sent/);

  const pending = describeSosDispatch({ attemptedAt: NOW }, NOW);
  assert.match(dispatchChatMessage(pending, "14:05"), /not yet confirmed/);
});
