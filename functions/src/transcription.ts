// REM-051: server-side voice transcription.
//
// The Gemini credential used for transcription stays in the Functions environment. The client
// uploads audio bytes and receives either a structured transcription or a hard error — never a
// canned "example" job, which is what the old client-side fallback produced when the key was
// missing or the API failed.

export const TRANSCRIPTION_CATEGORIES = [
  "Plumbing",
  "Electrical",
  "Mechanic",
  "Carpentry",
  "Tutoring",
  "Cleaning",
  "Gardening",
] as const;

export type TranscriptionCategory = typeof TRANSCRIPTION_CATEGORIES[number];

export interface TranscribedVoiceJob {
  fullTranscript: string;
  suggestedTitle: string;
  suggestedCategory: string;
  suggestedBudget: number;
  suggestedDescription: string;
}

export const MAX_AUDIO_BASE64_LENGTH = 8_000_000;

export function buildTranscriptionPrompt(): string {
  return [
    "Listen to this voice recording carefully and transcribe the spoken audio.",
    "Then return ONLY a JSON object with exactly these keys:",
    '  "fullTranscript" (string, verbatim transcription),',
    '  "suggestedTitle" (string, max 60 characters),',
    '  "suggestedCategory" (one of: ' + TRANSCRIPTION_CATEGORIES.join(", ") + "),",
    // R05-P2-03: the app stores money in whole Uganda shillings everywhere; asking the model for
    // "USD" is what produced budgets that were later read as shillings.
    '  "suggestedBudget" (number, whole Uganda shillings (UGX), between 0 and 10000000),',
    '  "suggestedDescription" (string).',
    "Do not wrap the JSON in prose or markdown.",
  ].join("\n");
}

/** Extract the first JSON object from a model response that may contain fences or prose. */
export function extractJsonObject(raw: string): Record<string, unknown> {
  const trimmed = raw.trim();
  const withoutFence = trimmed
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```$/, "")
    .trim();
  const start = withoutFence.indexOf("{");
  const end = withoutFence.lastIndexOf("}");
  if (start === -1 || end <= start) {
    throw new Error("transcription response did not contain JSON");
  }
  const parsed = JSON.parse(withoutFence.slice(start, end + 1)) as unknown;
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    throw new Error("transcription response was not an object");
  }
  return parsed as Record<string, unknown>;
}

/**
 * Validate and normalise a parsed model response. Throws instead of inventing fields: a partial
 * or unrecognised response must surface as an error to the caller.
 */
export function validateTranscribedJob(value: Record<string, unknown>): TranscribedVoiceJob {
  const transcript = value.fullTranscript;
  if (typeof transcript !== "string" || transcript.trim().length === 0) {
    throw new Error("transcription is empty");
  }

  const categoryRaw = value.suggestedCategory;
  if (typeof categoryRaw !== "string") {
    throw new Error("suggestedCategory missing");
  }
  const match = TRANSCRIPTION_CATEGORIES.find(
    (category) => category.toLowerCase() === categoryRaw.trim().toLowerCase()
  );
  if (!match) {
    throw new Error("suggestedCategory is not recognised");
  }

  const budget = typeof value.suggestedBudget === "number" ? value.suggestedBudget : Number.NaN;
  if (!Number.isFinite(budget) || budget < 0 || budget > 10_000_000) {
    throw new Error("suggestedBudget is out of range");
  }

  const title = typeof value.suggestedTitle === "string" && value.suggestedTitle.trim().length > 0
    ? value.suggestedTitle.trim().slice(0, 60)
    : transcript.trim().slice(0, 45);
  const description = typeof value.suggestedDescription === "string" && value.suggestedDescription.trim().length > 0
    ? value.suggestedDescription.trim()
    : transcript.trim();

  return {
    fullTranscript: transcript.trim(),
    suggestedTitle: title,
    suggestedCategory: match,
    // Whole shillings: a fractional shilling cannot be collected by the payment provider.
    suggestedBudget: Math.round(budget),
    suggestedDescription: description,
  };
}

/** Pull the concatenated text out of a Gemini generateContent response. */
export function extractGeminiText(body: unknown): string {
  const root = (body ?? {}) as Record<string, unknown>;
  const candidates = root.candidates;
  if (!Array.isArray(candidates) || candidates.length === 0) {
    throw new Error("Gemini returned no candidates");
  }
  const candidate = candidates[0] as Record<string, unknown>;
  const content = candidate.content as Record<string, unknown> | undefined;
  const parts = content?.parts;
  if (!Array.isArray(parts)) {
    throw new Error("Gemini returned no content parts");
  }
  const text = parts
    .map((part) => (part as Record<string, unknown>).text)
    .filter((value): value is string => typeof value === "string")
    .join("\n")
    .trim();
  if (!text) {
    throw new Error("Gemini returned empty text");
  }
  return text;
}

export function buildGeminiTranscriptionRequest(
  audioBase64: string,
  mimeType: string,
  prompt: string
): Record<string, unknown> {
  return {
    contents: [
      {
        parts: [
          { inlineData: { mimeType, data: audioBase64 } },
          { text: prompt },
        ],
      },
    ],
    generationConfig: { temperature: 0.2, responseMimeType: "application/json" },
  };
}
