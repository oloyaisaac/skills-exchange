#!/usr/bin/env node
/**
 * R09-P2-05: no identity numbers or phone numbers in a log line.
 *
 * `scrubForLog` exists, but a helper nobody is forced to use is a suggestion. This guard fails when a
 * log statement interpolates a sensitive field without masking it first, so the next `console.error`
 * that dumps a provider body cannot quietly write a phone number into Cloud Logging.
 *
 * What it looks for, and deliberately does not:
 *   - a template interpolation (`${...}`) containing an identifier field, without a mask/scrub call
 *     in the same expression;
 *   - an object-literal key naming an identifier field whose value is not a mask/scrub call;
 *   - prose that merely mentions the words ("NIN reuse detected") is ignored — that is a count, not
 *     a value.
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

const SENSITIVE = [
  "nationalIdNumber",
  "idNumber",
  "momoNumber",
  "phoneNumber",
  "recipientPhone",
  "doorstepPin",
  "oneTimeCode",
  "otpCode",
];

const SENSITIVE_GROUP = `(?:${SENSITIVE.join("|")})`;
const SAFE_CALL = /\b(?:scrub(?:ForLog|Fields)|mask(?:Nin|Phone|PhoneNumbers|Identifier|PhoneNumber))\s*\(/;

/** `console.log(...)` / `Log.d("tag", ...)` lines only. */
const LOG_LINE = /\b(?:console\s*\.\s*(?:log|warn|error|info|debug)|Log\s*\.\s*(?:v|d|i|w|e|wtf))\s*\(/;

/** `${field}` (JS) and `$field` (Kotlin) interpolations. */
const INTERPOLATION = new RegExp(
  `\\$\\{[^}]*${SENSITIVE_GROUP}[^}]*\\}|\\$\\{?${SENSITIVE_GROUP}\\b`,
  "i"
);
/** A count is not a value: `${recipientPhones.length}` says how many, not who. */
const COUNT_ONLY = /\.(?:length|size)\b|\bcount\b/i;
const OBJECT_KEY = new RegExp(`(?:^|[{,(]\\s*)${SENSITIVE_GROUP}\\s*:\\s*`, "i");
/** `{ phoneNumber }` shorthand, or a bare identifier passed as an argument. */
const SHORTHAND = new RegExp(`(?:[{,]\\s*|,\\s*)${SENSITIVE_GROUP}\\s*(?=[,})])`, "i");

function walk(directory, extension, files = []) {
  for (const entry of readdirSync(directory)) {
    if (entry === "node_modules" || entry === "lib" || entry === "build") continue;
    const path = join(directory, entry);
    const stats = statSync(path);
    if (stats.isDirectory()) walk(path, extension, files);
    else if (path.endsWith(extension)) files.push(path);
  }
  return files;
}

/**
 * One line is a leak when a log call carries a sensitive identifier as a *value* rather than a count,
 * and nothing in that expression masks it first.
 *
 * Returns the offending fragment, or null.
 */
function detect(line) {
  if (!LOG_LINE.test(line)) return null;
  if (SAFE_CALL.test(line)) return null;

  const interpolation = INTERPOLATION.exec(line);
  if (interpolation && !COUNT_ONLY.test(interpolation[0])) return interpolation[0];

  const shorthand = SHORTHAND.exec(line);
  if (shorthand) return shorthand[0].trim();

  const objectKey = OBJECT_KEY.exec(line);
  if (objectKey) {
    const value = line.slice(objectKey.index + objectKey[0].length).trim();
    if (!SAFE_CALL.test(value)) return `${objectKey[1]}: …`;
  }
  return null;
}

/**
 * Self-test: a guard that cannot fail is decoration. `--selftest` runs the detector over samples that
 * must be caught and samples that must be allowed, and exits non-zero if either behaves wrongly.
 */
const SELFTEST = [
  { line: 'console.error("otp failed", { phoneNumber });', caught: true },
  { line: 'console.log(`otp for ${phoneNumber} failed`);', caught: true },
  { line: 'console.log(`payout for ${momoNumber}`);', caught: true },
  { line: 'console.error("guarantor", { nationalIdNumber: nin });', caught: true },
  { line: 'Log.e("Kyc", "failed for $phoneNumber")', caught: true },
  { line: 'Log.e("Guarantor", "n");', caught: false },
  { line: 'console.error("otp failed", scrubForLog(error));', caught: false },
  { line: 'console.error("sms", { phone: maskPhone(phoneNumber) });', caught: false },
  { line: 'console.log(`sent to ${recipientPhones.length} workers`);', caught: false },
  { line: 'console.warn("NIN reuse detected");', caught: false },
];

if (process.argv.includes("--selftest")) {
  const failures = [];
  for (const sample of SELFTEST) {
    const caught = detect(sample.line) !== null;
    if (caught !== sample.caught) {
      failures.push(`${sample.caught ? "missed" : "false positive"}: ${sample.line}`);
    }
  }
  if (failures.length > 0) {
    console.error("PII-in-logs guard self-test failed:");
    for (const failure of failures) console.error(`  - ${failure}`);
    process.exit(1);
  }
  console.log(`PII-in-logs guard self-test passed (${SELFTEST.length} samples).`);
  process.exit(0);
}

const targets = [
  ...walk(join(root, "functions/src"), ".ts"),
  ...walk(join(root, "app/src/main/java"), ".kt"),
];

const errors = [];

for (const file of targets) {
  const lines = readFileSync(file, "utf8").split("\n");
  lines.forEach((line, index) => {
    const hit = detect(line);
    if (hit) errors.push(`${relative(root, file)}:${index + 1} logs ${hit} without masking it`);
  });
}

if (errors.length > 0) {
  console.error("PII-in-logs guard failed:");
  for (const error of errors) console.error(`  - ${error}`);
  process.exit(1);
}

console.log(`PII-in-logs guard passed (${targets.length} files scanned).`);
