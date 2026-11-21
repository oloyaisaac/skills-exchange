/** REM-034: SMS provider request/response handling (no network, no credits). */
import test from "node:test";
import assert from "node:assert/strict";

import {
  loadSmsConfig,
  normalisePhoneList,
  buildSmsBody,
  parseSmsResponse,
  createSmsSender,
  SMS_ENDPOINT,
} from "../lib/sms.js";

const config = { apiKey: "0123456789abcdef0123456789abcdef", username: "SkillsExchange" };

test("configuration requires both credential parts", () => {
  assert.deepEqual(loadSmsConfig({ AFRICASTALKING_API_KEY: config.apiKey, AFRICASTALKING_USERNAME: "SkillsExchange" }), config);
  assert.equal(loadSmsConfig({ AFRICASTALKING_API_KEY: config.apiKey }), null);
  assert.equal(loadSmsConfig({ AFRICASTALKING_USERNAME: "x" }), null);
  assert.equal(loadSmsConfig({}), null);
});

test("recipient lists are validated, de-duplicated and capped", () => {
  assert.deepEqual(normalisePhoneList("+256772000001, 0772000001; bad, +256772000002"), [
    "+256772000001",
    "0772000001",
    "+256772000002",
  ]);
  assert.equal(normalisePhoneList(Array.from({ length: 30 }, (_, i) => `07720000${String(i).padStart(2, "0")}`)).length, 20);
});

test("provider response parsing accepts success and rejects everything else", () => {
  assert.deepEqual(
    parseSmsResponse(201, { SMSMessageData: { Recipients: [{ status: "Success", messageId: "ATX-1" }] } }),
    { ok: true, messageId: "ATX-1" }
  );
  assert.equal(parseSmsResponse(200, { SMSMessageData: { Recipients: [{ status: "InvalidPhoneNumber" }] } }).ok, false);
  assert.equal(parseSmsResponse(200, { SMSMessageData: { Recipients: [] } }).ok, false);
  assert.equal(parseSmsResponse(500, {}).ok, false);
  assert.equal(parseSmsResponse(200, {}).ok, false);
});

test("sender posts the documented payload and reports provider failure honestly", async () => {
  let seen;
  const transport = async (url, body, headers) => {
    seen = { url, body, headers };
    return { status: 201, data: { SMSMessageData: { Recipients: [{ status: "Success", messageId: "ATX-2" }] } } };
  };
  const send = createSmsSender(config, transport);
  const result = await send(["+256772000001"], "hello");
  assert.deepEqual(result, { ok: true, messageId: "ATX-2" });
  assert.equal(seen.url, SMS_ENDPOINT);
  assert.equal(seen.body.username, "SkillsExchange");
  assert.deepEqual(seen.body.to, ["+256772000001"]);
  assert.equal(seen.headers.apiKey, config.apiKey);

  const failing = createSmsSender(config, async () => ({ status: 200, data: { SMSMessageData: { Recipients: [{ status: "InsufficientBalance" }] } } }));
  assert.equal((await failing(["+256772000001"], "hello")).ok, false);

  const throwing = createSmsSender(config, async () => { throw new Error("network down"); });
  const thrown = await throwing(["+256772000001"], "hello");
  assert.equal(thrown.ok, false);
  assert.equal(thrown.error, "network down");
});

test("empty recipients or message never call the provider", async () => {
  let called = false;
  const send = createSmsSender(config, async () => { called = true; return { status: 200, data: {} }; });
  assert.equal((await send([], "hi")).ok, false);
  assert.equal((await send(["+256772000001"], "   ")).ok, false);
  assert.equal(called, false);
});
