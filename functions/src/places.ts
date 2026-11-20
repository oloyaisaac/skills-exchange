// R05-P2-04 / R05-P2-05: Google Maps grounding for the "nearby suppliers" search.
//
// Two defects are fixed here.
//
// P2-04 — the Gemini credential used to be compiled into the Android APK (`BuildConfig.GEMINI_API_KEY`)
// and appended to a request URL, so anyone with the APK could extract and spend it. The key now lives
// only in the Functions environment, and the client calls an authenticated callable.
//
// P2-05 — the client's `parseGroundedPlacesFromText` ignored the model output entirely and returned a
// hard-coded list of invented shops with invented star ratings and phone numbers, presented as real
// Google Maps results. Here, a place only exists if Google actually grounded it
// (`groundingMetadata.groundingChunks[].maps`). Anything the model merely wrote is returned as prose
// flagged `grounded: false`, never as a shop with a phone number.

export interface GroundedPlace {
  name: string;
  address: string;
  uri: string;
  /** Always true: a place is only emitted from a Google Maps grounding chunk. */
  grounded: true;
}

export interface GroundedPlacesResponse {
  summary: string;
  places: GroundedPlace[];
  /** True only when at least one place came from a Maps grounding chunk. */
  grounded: boolean;
}

export const MAX_QUERY_LENGTH = 200;
export const MAX_DISTRICT_LENGTH = 80;
const MAX_SUMMARY_LENGTH = 1200;
const MAX_FIELD_LENGTH = 300;
const MAX_PLACES = 8;

/** Trims and bounds a free-text field; returns "" for anything that is not a string. */
function clip(value: unknown, max: number): string {
  if (typeof value !== "string") return "";
  const trimmed = value.trim().replace(/\s+/g, " ");
  if (trimmed.length <= max) return trimmed;
  return `${trimmed.slice(0, max - 1)}\u2026`;
}

/**
 * Validates the two user-controlled inputs. Throws with a client-safe message so the callable can
 * answer `invalid-argument` instead of guessing.
 */
export function normaliseSearchInput(rawQuery: unknown, rawDistrict: unknown): {
  query: string;
  district: string;
} {
  const query = typeof rawQuery === "string" ? rawQuery.trim() : "";
  const district = typeof rawDistrict === "string" ? rawDistrict.trim() : "";
  if (query.length < 2) throw new Error("Search text is too short");
  if (query.length > MAX_QUERY_LENGTH) throw new Error("Search text is too long");
  if (district.length > MAX_DISTRICT_LENGTH) throw new Error("District is too long");
  return { query, district: district || "Kampala" };
}

export function buildMapsGroundingPrompt(query: string, district: string): string {
  return [
    `A resident in ${district}, Uganda is looking for: "${query}".`,
    "Using Google Maps, name real businesses near them and say what each one stocks.",
    "Only mention businesses that exist on Google Maps. Do not invent names, phone numbers,",
    "addresses, ratings or opening hours. If you cannot find any, say so plainly.",
  ].join(" ");
}

export function buildMapsGroundingRequest(query: string, district: string): Record<string, unknown> {
  return {
    contents: [{ role: "user", parts: [{ text: buildMapsGroundingPrompt(query, district) }] }],
    tools: [{ googleMaps: {} }],
    // Grounding metadata is what makes a result verifiable; without it nothing is shown as a place.
    generationConfig: { temperature: 0.2 },
  };
}

/** Concatenated model text from a Gemini `generateContent` response. */
export function extractCandidateText(data: any): string {
  const parts = data?.candidates?.[0]?.content?.parts;
  if (!Array.isArray(parts)) return "";
  return parts
    .map((part: any) => (typeof part?.text === "string" ? part.text : ""))
    .join("")
    .trim();
}

/**
 * Pulls places out of `groundingMetadata.groundingChunks`.
 *
 * Only `maps` chunks become places. `web` chunks are deliberately ignored: a web link is not a
 * verified business address and would put the app back to presenting unverified data as a shop.
 */
export function parseGroundedPlaces(data: any): GroundedPlacesResponse {
  const summaryText = clip(extractCandidateText(data), MAX_SUMMARY_LENGTH);
  const chunks = data?.candidates?.[0]?.groundingMetadata?.groundingChunks;
  const places: GroundedPlace[] = [];
  const seen = new Set<string>();

  if (Array.isArray(chunks)) {
    for (const chunk of chunks) {
      const maps = chunk?.maps;
      if (!maps) continue;
      const uri = clip(maps.uri, MAX_FIELD_LENGTH);
      const name = clip(maps.title, MAX_FIELD_LENGTH);
      if (!name || !uri) continue;
      const key = uri.toLowerCase();
      if (seen.has(key)) continue;
      seen.add(key);
      places.push({
        name,
        // Google's grounding snippet for a place — never a fabricated street line.
        address: clip(maps.text, MAX_FIELD_LENGTH),
        uri,
        grounded: true,
      });
      if (places.length >= MAX_PLACES) break;
    }
  }

  const summary = summaryText || (places.length > 0 ? "Nearby places found on Google Maps." : "");
  return { summary, places, grounded: places.length > 0 };
}

/** Response for "the model answered, but nothing was grounded in Google Maps". */
export function ungroundedReply(summaryText: string): GroundedPlacesResponse {
  const summary = clip(summaryText, MAX_SUMMARY_LENGTH);
  return {
    summary: summary
      ? `${summary}\n\nNot verified on Google Maps: these are model suggestions, not confirmed businesses.`
      : "No verified places were found nearby.",
    places: [],
    grounded: false,
  };
}
