/** REM-051: pure validation tests for the server-side transcription contract. */
import test from "node:test";
import assert from "node:assert/strict";

import {
  extractJsonObject,
  extractGeminiText,
  validateTranscribedJob,
  buildGeminiTranscriptionRequest,
  buildTranscriptionPrompt,
  TRANSCRIPTION_CATEGORIES,
} from "../lib/transcription.js";

const valid = {
  fullTranscript: "My kitchen sink is leaking badly, please come today.",
  suggestedTitle: "Leaking kitchen sink",
  suggestedCategory: "plumbing",
  suggestedBudget: 85000,
  suggestedDescription: "Water under the sink since morning.",
};

test("a complete model response is normalised", () => {
  const job = validateTranscribedJob(valid);
  assert.equal(job.suggestedCategory, "Plumbing");
  assert.equal(job.suggestedBudget, 85000);
  assert.equal(job.suggestedTitle, "Leaking kitchen sink");
});

test("missing transcript or unknown category is rejected, never invented", () => {
  assert.throws(() => validateTranscribedJob({ ...valid, fullTranscript: "   " }));
  assert.throws(() => validateTranscribedJob({ ...valid, suggestedCategory: "Plumbing-ish" }));
  assert.throws(() => validateTranscribedJob({ ...valid, suggestedBudget: -1 }));
  assert.throws(() => validateTranscribedJob({ ...valid, suggestedBudget: 10_000_001 }));
  assert.throws(() => validateTranscribedJob({ ...valid, suggestedBudget: "85" }));
});

test("every advertised category is accepted case-insensitively", () => {
  for (const category of TRANSCRIPTION_CATEGORIES) {
    assert.equal(validateTranscribedJob({ ...valid, suggestedCategory: category.toLowerCase() }).suggestedCategory, category);
  }
});

test("title and description fall back to the transcript, not canned text", () => {
  const job = validateTranscribedJob({ ...valid, suggestedTitle: "", suggestedDescription: "" });
  assert.equal(job.suggestedTitle, valid.fullTranscript.slice(0, 45));
  assert.equal(job.suggestedDescription, valid.fullTranscript);
});

test("JSON extraction tolerates fences and surrounding prose", () => {
  assert.deepEqual(extractJsonObject('```json\n{"a":1}\n```'), { a: 1 });
  assert.deepEqual(extractJsonObject('Here you go: {"a":2} thanks'), { a: 2 });
  assert.throws(() => extractJsonObject("no json here"));
  assert.throws(() => extractJsonObject("[1,2,3]"));
});

test("Gemini text extraction reads all text parts and rejects empty responses", () => {
  assert.equal(
    extractGeminiText({ candidates: [{ content: { parts: [{ text: "a" }, { text: "b" }] } }] }),
    "a\nb"
  );
  assert.throws(() => extractGeminiText({ candidates: [] }));
  assert.throws(() => extractGeminiText({}));
});

test("request payload carries audio inline and asks for JSON", () => {
  const payload = buildGeminiTranscriptionRequest("AAA", "audio/mp4", "prompt");
  const parts = payload.contents[0].parts;
  assert.equal(parts[0].inlineData.data, "AAA");
  assert.equal(parts[0].inlineData.mimeType, "audio/mp4");
  assert.equal(payload.generationConfig.responseMimeType, "application/json");
});

test("R05-P2-03: a suggested budget is whole Uganda shillings, never a fraction", () => {
  const job = validateTranscribedJob({ ...valid, suggestedBudget: 85000.49 });
  assert.equal(job.suggestedBudget, 85000);
  assert.equal(Number.isInteger(job.suggestedBudget), true);
  assert.match(buildTranscriptionPrompt(), /Uganda shillings/);
  assert.doesNotMatch(buildTranscriptionPrompt(), /USD/);
});
