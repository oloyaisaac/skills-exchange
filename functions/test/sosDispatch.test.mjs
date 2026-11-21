/** REM-034 / REM-035: SOS dispatch policy. */
import test from "node:test";
import assert from "node:assert/strict";

import {
  planSosDispatch,
  selectSosRecipients,
  buildSosMessage,
} from "../lib/sosDispatch.js";

const active = {
  jobId: "job-1",
  postedByUid: "u1",
  district: "Kampala",
  latitude: 0.3476,
  longitude: 32.5825,
  status: "ACTIVE",
};

test("only an un-attempted, owned, active incident is dispatched", () => {
  assert.deepEqual(planSosDispatch(active), { shouldDispatch: true });
  assert.equal(planSosDispatch({ ...active, status: "CLEARED" }).reason, "not_active");
  assert.equal(planSosDispatch({ ...active, postedByUid: "" }).reason, "no_owner");
  assert.equal(planSosDispatch({ ...active, dispatch: { attemptedAt: 1 } }).reason, "already_attempted");
});

test("recipients combine the emergency contact and platform numbers without duplicates", () => {
  assert.deepEqual(
    selectSosRecipients("+256700000003,+256700000004", "+256700000001"),
    ["+256700000001", "+256700000003", "+256700000004"]
  );
  assert.deepEqual(selectSosRecipients("", undefined), []);
});

test("message names the job, district and coordinates", () => {
  const message = buildSosMessage(active);
  assert.match(message, /SOS/);
  assert.match(message, /job-1/);
  assert.match(message, /Kampala/);
  assert.match(message, /0\.3476, 32\.5825/);
});

test("missing location is described, not invented", () => {
  const message = buildSosMessage({ ...active, latitude: undefined, longitude: undefined });
  assert.match(message, /unknown, unknown/);
});
