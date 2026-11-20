/** R13-P2-07: the retry rule for provider reads, and the bound on it. */
import test from "node:test";
import assert from "node:assert/strict";

import {
  backoffDelay,
  isRetryableProviderError,
  withProviderRetry,
} from "../lib/providerRetry.js";

test("transport failures, timeouts, 429 and 5xx are retryable", () => {
  for (const error of [
    { status: 500 },
    { status: 503 },
    { status: 429 },
    { code: "ECONNRESET" },
    { code: "ECONNABORTED" },
    { code: "ETIMEDOUT" },
    { code: "EAI_AGAIN" },
    { message: "socket hang up" },
    { message: "timeout of 20000ms exceeded" },
  ]) {
    assert.equal(isRetryableProviderError(error), true, JSON.stringify(error));
  }
});

test("a malformed request is never retried", () => {
  // 400/401/403/404 mean the request or credentials are wrong; repeating it changes nothing.
  for (const status of [400, 401, 403, 404, 409, 422]) {
    assert.equal(isRetryableProviderError({ status }), false, String(status));
  }
  assert.equal(isRetryableProviderError({ message: "bad request" }), false);
});

test("a read that fails once is retried and succeeds", async () => {
  let calls = 0;
  const waits = [];
  const result = await withProviderRetry(
    async () => {
      calls += 1;
      if (calls < 3) throw { status: 503 };
      return "ok";
    },
    { sleep: async (ms) => waits.push(ms) }
  );
  assert.equal(result, "ok");
  assert.equal(calls, 3);
  assert.deepEqual(waits.length, 2);
});

test("the attempt count is a hard cap and the last error is surfaced", async () => {
  let calls = 0;
  await assert.rejects(
    () =>
      withProviderRetry(
        async () => {
          calls += 1;
          throw { status: 500, message: "provider down" };
        },
        { attempts: 3, sleep: async () => {} }
      ),
    (error) => error.status === 500 && error.message === "provider down"
  );
  assert.equal(calls, 3, "no more than the configured attempts");
});

test("a non-retryable error fails on the first attempt without waiting", async () => {
  let calls = 0;
  const waits = [];
  await assert.rejects(() =>
    withProviderRetry(
      async () => {
        calls += 1;
        throw { status: 400 };
      },
      { sleep: async (ms) => waits.push(ms) }
    )
  );
  assert.equal(calls, 1);
  assert.deepEqual(waits, []);
});

test("backoff grows, stays under the ceiling, and never returns zero", () => {
  for (let attempt = 1; attempt <= 6; attempt += 1) {
    for (let sample = 0; sample < 25; sample += 1) {
      const delay = backoffDelay(attempt, 250, 2000);
      assert.ok(delay >= 1 && delay <= 2000, `attempt ${attempt} produced ${delay}`);
    }
  }
  // First step is bounded by the base delay; later steps are bounded by the ceiling.
  assert.ok(backoffDelay(1, 250, 2000) <= 250);
  assert.ok(backoffDelay(5, 250, 2000) <= 2000);
});

test("every retry is reported before it waits", async () => {
  const seen = [];
  await withProviderRetry(
    (() => {
      let calls = 0;
      return async () => {
        calls += 1;
        if (calls === 1) throw { status: 502 };
        return "ok";
      };
    })(),
    { sleep: async () => {}, onRetry: (error, attempt, delayMs) => seen.push({ attempt, delayMs, status: error.status }) }
  );
  assert.equal(seen.length, 1);
  assert.equal(seen[0].attempt, 1);
  assert.equal(seen[0].status, 502);
  assert.ok(seen[0].delayMs >= 1);
});
