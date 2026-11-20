/** R07-P2-04: every callable enforces App Check; triggers and the webhook do not pretend to. */
import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import { APP_CHECK, withAppCheck } from "../lib/appCheck.js";

const here = dirname(fileURLToPath(import.meta.url));
const compiled = readFileSync(join(here, "..", "lib", "index.js"), "utf8");

/** Every `.https.onCall(` and the 240 characters that decide its runtime options. */
function callSites() {
  const sites = [];
  let index = compiled.indexOf(".https.onCall(");
  while (index !== -1) {
    sites.push({
      at: index,
      line: compiled.slice(compiled.lastIndexOf("\n", index) + 1, compiled.indexOf("\n", index)),
      before: compiled.slice(Math.max(0, index - 240), index),
    });
    index = compiled.indexOf(".https.onCall(", index + 1);
  }
  return sites;
}

test("both helpers enable enforcement", () => {
  assert.deepEqual(APP_CHECK, { enforceAppCheck: true });
  assert.deepEqual(withAppCheck({ secrets: ["KEY"] }), { secrets: ["KEY"], enforceAppCheck: true });
  // Extra options are preserved, and enforcement cannot be switched off through the helper.
  assert.deepEqual(withAppCheck({ timeoutSeconds: 30 }), { timeoutSeconds: 30, enforceAppCheck: true });
});

test("every callable is wrapped in App Check enforcement", () => {
  const sites = callSites();
  assert.ok(sites.length >= 25, `expected the callable set, found ${sites.length}`);
  const unwrapped = sites
    .filter((site) => !site.before.includes("APP_CHECK") && !site.before.includes("withAppCheck"))
    .map((site) => site.line.trim());
  assert.deepEqual(unwrapped, [], `callables without App Check: ${unwrapped.join(" | ")}`);
});

test("App Check is not applied to triggers or the HTTPS webhook", () => {
  // `enforceAppCheck` is a callable-only runtime option; applying it to a trigger or an
  // onRequest handler would be wrong at deploy time.
  for (const marker of [".firestore.document(", ".https.onRequest("]) {
    let index = compiled.indexOf(marker);
    while (index !== -1) {
      const before = compiled.slice(Math.max(0, index - 200), index);
      assert.equal(
        before.includes("enforceAppCheck"),
        false,
        `${marker} must not carry enforceAppCheck`
      );
      index = compiled.indexOf(marker, index + 1);
    }
  }
});

test("the client actually installs an App Check provider", () => {
  const service = readFileSync(
    join(here, "..", "..", "app", "src", "main", "java", "com", "example", "data", "remote", "FirebaseService.kt"),
    "utf8"
  );
  assert.ok(service.includes("installAppCheckProviderFactory"), "an App Check provider must be installed");
  assert.ok(service.includes("PlayIntegrityAppCheckProviderFactory"), "release builds use Play Integrity");
  assert.ok(service.includes("DebugAppCheckProviderFactory"), "debug builds use the debug provider");

  const versions = readFileSync(join(here, "..", "..", "gradle", "libs.versions.toml"), "utf8");
  assert.ok(versions.includes("firebase-appcheck-playintegrity"));
  assert.ok(versions.includes("firebase-appcheck-debug"));
});
