#!/usr/bin/env node
/**
 * R14-P2-01: the repository is not a scratch directory.
 *
 * The review found ~64 MB of APKs, a device SQLite database and JVM crash logs tracked in git. R11
 * deleted the bulk of it; this round found the residue — a 244 KB device database, two `hs_err_pid`
 * logs and two JFR `replay_pid` logs — and no ignore rule that would have stopped any of them.
 *
 * Two things are checked, because either one alone is survivable but both together are not:
 *
 *   1. no tracked path is an artefact, a device capture, a signing key or a service secret;
 *   2. `.gitignore` actually carries the rules, so the next build does not re-add them.
 *
 * `--selftest` proves both refusals with fixtures.
 */
import fs from "node:fs";
import { execFileSync } from "node:child_process";

const FORBIDDEN = [
  { pattern: /\.apk$/i, why: "a built APK — release output, not source" },
  { pattern: /\.aab$/i, why: "a built app bundle — release output, not source" },
  { pattern: /\.sqlite3?$/i, why: "a device database — user rows can never be in git" },
  { pattern: /(^|\/)hs_err_pid\d+\.log$/i, why: "a JVM crash log — machine state, no review value" },
  { pattern: /(^|\/)replay_pid\d+\.log$/i, why: "a JFR recording — machine state, tens of MB" },
  { pattern: /\.hprof$/i, why: "a heap dump — can contain user data" },
  { pattern: /(^|\/)my-upload-key\.jks$/i, why: "the release signing key" },
  { pattern: /\.(jks|keystore)$/i, why: "a signing key" },
  { pattern: /(^|\/)\.env$/i, why: "secrets" },
];

/** The ignore lines this repository must keep, checked literally so a rewrite cannot drop them. */
const REQUIRED_IGNORES = [
  "*.apk",
  "*.sqlite",
  "hs_err_pid*.log",
  "replay_pid*.log",
  "*.jks",
];

export function forbiddenReasons(path) {
  return FORBIDDEN.filter((rule) => rule.pattern.test(path)).map((rule) => rule.why);
}

export function missingIgnores(text) {
  const lines = new Set(
    text
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line.length > 0 && !line.startsWith("#"))
  );
  return REQUIRED_IGNORES.filter((rule) => !lines.has(rule));
}

function trackedFiles() {
  const out = execFileSync("git", ["ls-files", "-z"], { encoding: "utf8" });
  return out.split("\0").filter(Boolean);
}

function selftest() {
  const checks = [
    ["app/build/outputs/apk/release/app-release.apk", true],
    ["_device_test/ondevice_db.sqlite", true],
    ["app/hs_err_pid12884.log", true],
    ["app/replay_pid12884.log", true],
    ["app/my-upload-key.jks", true],
    // `google-services.json` is deliberately tracked: it is the client config the Google Services
    // plugin reads at build time, it ships inside every APK anyway, and it holds no secret.
    ["app/google-services.json", false],
    ["app/src/main/java/com/example/MainActivity.kt", false],
    ["docs/qa/TEST_STRATEGY.md", false],
    [".env.example", false],
    ["functions/test/providerRetry.test.mjs", false],
  ];
  for (const [path, expected] of checks) {
    const got = forbiddenReasons(path).length > 0;
    if (got !== expected) {
      throw new Error(`selftest: ${path} classified as ${got}, expected ${expected}`);
    }
  }
  if (missingIgnores(REQUIRED_IGNORES.join("\n")).length !== 0) {
    throw new Error("selftest: a complete ignore file was reported as incomplete");
  }
  if (missingIgnores(REQUIRED_IGNORES.slice(1).join("\n")).length !== 1) {
    throw new Error("selftest: a single missing ignore rule was not detected");
  }
  console.log(
    `Repo hygiene selftest passed: ${checks.length} path classifications, both ignore-file refusals proven.`
  );
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  const problems = [];
  const files = trackedFiles();
  for (const file of files) {
    const reasons = forbiddenReasons(file);
    if (reasons.length > 0) problems.push(`${file}: ${reasons.join("; ")}`);
  }

  const ignorePath = ".gitignore";
  if (!fs.existsSync(ignorePath)) {
    problems.push(".gitignore is missing");
  } else {
    for (const missing of missingIgnores(fs.readFileSync(ignorePath, "utf8"))) {
      problems.push(`.gitignore does not carry the rule '${missing}'`);
    }
  }

  if (problems.length > 0) {
    console.error(`Repo hygiene check failed (${problems.length} problem(s)):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }
  console.log(
    `Repo hygiene check passed: ${files.length} tracked files, none of them an artefact, device capture or key; ignore rules present.`
  );
}
