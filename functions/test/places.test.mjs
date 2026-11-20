/** R05-P2-04 / R05-P2-05: Maps grounding contract. */
import test from "node:test";
import assert from "node:assert/strict";

import {
  buildMapsGroundingRequest,
  normaliseSearchInput,
  parseGroundedPlaces,
  ungroundedReply,
  MAX_QUERY_LENGTH,
} from "../lib/places.js";

const groundedResponse = {
  candidates: [
    {
      content: { parts: [{ text: "Here are two suppliers near you." }] },
      groundingMetadata: {
        groundingChunks: [
          {
            maps: {
              uri: "https://maps.google.com/?cid=111",
              title: "Kampala Hardware Ltd",
              text: "Plot 12 Luwum Street, Kampala",
            },
          },
          {
            maps: {
              uri: "https://maps.google.com/?cid=222",
              title: "Nakawa Plumbing Supplies",
              text: "Nakawa, Kampala",
            },
          },
          // duplicate uri must collapse
          {
            maps: {
              uri: "https://maps.google.com/?cid=222",
              title: "Nakawa Plumbing Supplies",
              text: "Nakawa, Kampala",
            },
          },
          // a web chunk is not a verified business
          { web: { uri: "https://example.com/blog", title: "Top 10 hardware shops" } },
        ],
      },
    },
  ],
};

test("only Google Maps grounding chunks become places", () => {
  const result = parseGroundedPlaces(groundedResponse);
  assert.equal(result.grounded, true);
  assert.equal(result.places.length, 2);
  assert.deepEqual(
    result.places.map((place) => place.name),
    ["Kampala Hardware Ltd", "Nakawa Plumbing Supplies"]
  );
  assert.equal(result.places[0].address, "Plot 12 Luwum Street, Kampala");
  assert.equal(result.places[0].uri, "https://maps.google.com/?cid=111");
  assert.equal(result.places[0].grounded, true);
});

test("no invented rating or phone number is ever attached to a place", () => {
  const place = parseGroundedPlaces(groundedResponse).places[0];
  assert.deepEqual(Object.keys(place).sort(), ["address", "grounded", "name", "uri"]);
});

test("a model answer with no maps chunks is reported as ungrounded, with no places", () => {
  const result = parseGroundedPlaces({
    candidates: [{ content: { parts: [{ text: "Try the market on Saturday." }] } }],
  });
  assert.equal(result.grounded, false);
  assert.deepEqual(result.places, []);
});

test("an empty or malformed response never throws and never yields places", () => {
  for (const payload of [null, {}, { candidates: [] }, { candidates: [{}] }]) {
    const result = parseGroundedPlaces(payload);
    assert.equal(result.grounded, false);
    assert.deepEqual(result.places, []);
  }
});

test("an ungrounded reply is labelled so the UI can say it is not verified", () => {
  const reply = ungroundedReply("Some shops may exist near the taxi park.");
  assert.equal(reply.grounded, false);
  assert.deepEqual(reply.places, []);
  assert.match(reply.summary, /Not verified on Google Maps/);
  assert.equal(ungroundedReply("").summary, "No verified places were found nearby.");
});

test("search input is bounded and a missing district falls back to Kampala", () => {
  assert.deepEqual(normaliseSearchInput("  cement  ", ""), { query: "cement", district: "Kampala" });
  assert.throws(() => normaliseSearchInput("", "Kampala"));
  assert.throws(() => normaliseSearchInput("cement", "x".repeat(81)));
  assert.throws(() => normaliseSearchInput("c".repeat(MAX_QUERY_LENGTH + 1), "Kampala"));
});

test("the request asks for Maps grounding and forbids invented details", () => {
  const request = buildMapsGroundingRequest("cement", "Kampala");
  assert.deepEqual(request.tools, [{ googleMaps: {} }]);
  const parts = request.contents[0].parts;
  assert.match(parts[0].text, /Do not invent names, phone numbers/);
  assert.match(parts[0].text, /Kampala/);
});
