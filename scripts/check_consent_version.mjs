#!/usr/bin/env node
/**
 * R09-P2-02: the client must quote the policy version the server records.
 *
 * A consent receipt naming a version the server has never heard of proves nothing, and a version
 * bump that only lands on one side would silently split the history. This guard reads
 * `CONSENT_VERSION` from `functions/src/compliance.ts` and `POLICY_VERSION` from
 * `app/.../ConsentVersions.kt` and fails when they disagree or when either stops being a dated value.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

const SERVER_FILE = "functions/src/compliance.ts";
const CLIENT_FILE = "app/src/main/java/com/example/data/remote/ConsentVersions.kt";

const errors = [];

function read(relative) {
  try {
    return readFileSync(join(root, relative), "utf8");
  } catch {
    errors.push(`${relative} is missing`);
    return "";
  }
}

const server = read(SERVER_FILE);
const client = read(CLIENT_FILE);

const serverMatch = server.match(/export const CONSENT_VERSION = "([^"]+)";/);
const clientMatch = client.match(/const val POLICY_VERSION = "([^"]+)"/);

if (!serverMatch) errors.push(`${SERVER_FILE} no longer exports a literal CONSENT_VERSION`);
if (!clientMatch) errors.push(`${CLIENT_FILE} no longer declares a literal POLICY_VERSION`);

const DATED = /^\d{4}-\d{2}-\d{2}$/;
if (serverMatch && !DATED.test(serverMatch[1])) {
  errors.push(`CONSENT_VERSION "${serverMatch[1]}" is not a dated version (YYYY-MM-DD)`);
}
if (clientMatch && !DATED.test(clientMatch[1])) {
  errors.push(`POLICY_VERSION "${clientMatch[1]}" is not a dated version (YYYY-MM-DD)`);
}
if (serverMatch && clientMatch && serverMatch[1] !== clientMatch[1]) {
  errors.push(
    `consent version drift: server records "${serverMatch[1]}", the app quotes "${clientMatch[1]}"`
  );
}

// The guarantor receipt is a second, separate grant; it must be dated the same way.
const guarantorMatch = client.match(/const val GUARANTOR_VERSION = ([A-Z_]+|"[^"]+")/);
if (!guarantorMatch) {
  errors.push(`${CLIENT_FILE} no longer declares GUARANTOR_VERSION`);
} else if (guarantorMatch[1] !== "POLICY_VERSION" && !DATED.test(guarantorMatch[1].replace(/"/g, ""))) {
  errors.push("GUARANTOR_VERSION must be POLICY_VERSION or a dated literal");
}

// The server must still validate the count and the confirmation it is handed.
for (const fragment of [
  "buildGuarantorConsentRecord",
  "GUARANTOR_CONSENT_SCOPE",
  "MAX_GUARANTORS_PER_SUBMISSION",
]) {
  if (!server.includes(fragment)) errors.push(`${SERVER_FILE} no longer defines ${fragment}`);
}

if (errors.length > 0) {
  console.error("Consent version guard failed:");
  for (const error of errors) console.error(`  - ${error}`);
  process.exit(1);
}

console.log(`Consent version guard passed (policy ${serverMatch[1]}).`);
