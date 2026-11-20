#!/usr/bin/env node
/**
 * REM-071: keep README claims aligned with the code.
 *
 * The README may describe intended behaviour, but it must not advertise a provider or surface that
 * does not exist, and it must keep the pre-production caveat plus a pointer to the traceability
 * matrix. This is a narrow regression guard, not a general truth check.
 */
import fs from "node:fs";

const text = fs.readFileSync("README.md", "utf8");
const problems = [];

if (!/Pre-production\. Not release-ready\./.test(text)) {
  problems.push("missing the pre-production / not release-ready caveat");
}
if (!text.includes("docs/traceability.md")) {
  problems.push("missing the link to the traceability matrix");
}
if (/Airtel Money Collection/i.test(text) || /Airtel Money \/ Collection/i.test(text)) {
  problems.push("advertises an Airtel Money integration");
}
if (/Admin Operations Dashboard/i.test(text)) {
  problems.push("advertises an admin web dashboard");
}
if (/production-ready/i.test(text) && !/not production-ready|Not release-ready/i.test(text)) {
  problems.push("claims production readiness");
}

if (problems.length > 0) {
  console.error(`README claim check failed (${problems.length} problem(s)):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}

console.log("README claim check passed: caveats present, no non-existent providers or dashboards advertised.");
