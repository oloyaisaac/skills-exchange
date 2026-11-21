#!/usr/bin/env node
/**
 * R15-P2-03: the onboarding documents must describe the machine the reader is on.
 *
 * The review found `AGENTS.md` asserting "No environment setup is required", pinning a Windows JDK path
 * that no longer exists in `gradle.properties`, and instructing agents **not** to report a missing JDK —
 * advice that produced a false "no blocker" on a machine with no JDK at all. `README_AI.md` still told
 * readers to work from a Windows directory.
 *
 * Three rules, checked on `AGENTS.md`, `README_AI.md` and `README.md`:
 *
 *   1. no Windows absolute path (`C:\`, `D:/`) — it does not exist on a runner or in the sandbox;
 *   2. no claim that setup is unnecessary, because nothing here supplies a JDK;
 *   3. `AGENTS.md` must say where compilation actually happens (CI) and that a missing JDK is a
 *      reportable blocker, so the next agent does not re-invent the false blocker;
 *   4. the guard count quoted in `docs/qa/TEST_STRATEGY.md` must match `scripts/` — it read "16 guards
 *      (7 with --selftest)" while 19/9 existed, because every round added a guard and nobody counted.
 *
 * `--selftest` proves every refusal with fixtures.
 */
import fs from "node:fs";

export const DOCS = ["AGENTS.md", "README_AI.md", "README.md"];

const WINDOWS_PATH = /\b[A-Za-z]:[\\/][^\s`"')]*/;
const SETUP_CLAIM = /no environment setup (?:is )?required/i;

export function auditDocs(files) {
  const problems = [];
  for (const [name, text] of Object.entries(files)) {
    const windows = text.match(WINDOWS_PATH);
    if (windows) problems.push(`${name}: Windows absolute path '${windows[0]}' — it does not exist on a runner`);
    if (SETUP_CLAIM.test(text)) {
      problems.push(`${name}: claims no environment setup is required; nothing here supplies a JDK`);
    }
  }

  const agents = files["AGENTS.md"];
  if (agents) {
    // Prose wraps; a quoted block breaks "GitHub Actions" across two lines, which is not a lie.
    const flat = agents.replace(/^\s*>\s?/gm, " ").replace(/\s+/g, " ");
    if (!/GitHub Actions/.test(flat)) {
      problems.push("AGENTS.md: does not say that GitHub Actions compiles this project");
    }
    if (!/no local JDK|no JDK/i.test(flat)) {
      problems.push("AGENTS.md: does not warn that no local JDK exists");
    }
    if (!/reportable blocker/i.test(flat)) {
      problems.push("AGENTS.md: does not say that a missing JDK is a reportable blocker");
    }
  }
  return problems;
}

/** A quoted count of the guards is a claim like any other; derive it from the directory instead. */
export function auditGuardCount(markdown, actual) {
  const match = /(\d+)\s+guards\s+\((\d+)\s+with\s+`?--selftest`?\)/.exec(markdown);
  if (!match) return ["TEST_STRATEGY.md no longer states how many guards exist"];
  const problems = [];
  if (Number(match[1]) !== actual.total) {
    problems.push(`TEST_STRATEGY.md says ${match[1]} guards, scripts/ has ${actual.total}`);
  }
  if (Number(match[2]) !== actual.withSelftest) {
    problems.push(`TEST_STRATEGY.md says ${match[2]} self-tests, scripts/ has ${actual.withSelftest}`);
  }
  return problems;
}

function selftest() {
  const good = {
    "AGENTS.md":
      "There is no local JDK here. GitHub Actions compiles the project; a missing JDK is a reportable blocker. Run ./gradlew testDebugUnitTest.",
    "README_AI.md": "Run ./gradlew assembleDebug from the repository root.",
    "README.md": "Build with ./gradlew.",
  };
  if (auditDocs(good).length !== 0) throw new Error(`selftest: a valid set was rejected: ${auditDocs(good)}`);

  const windowsPath = auditDocs({ ...good, "README_AI.md": "cd D:\\skills exchange\\ and run gradlew" });
  if (!windowsPath.some((p) => p.includes("Windows absolute path"))) {
    throw new Error("selftest: a Windows path was not reported");
  }

  const posixWindows = auditDocs({ ...good, "README.md": "cd D:/skills-exchange" });
  if (!posixWindows.some((p) => p.includes("Windows absolute path"))) {
    throw new Error("selftest: a drive-letter path with forward slashes was not reported");
  }

  const setup = auditDocs({ ...good, "AGENTS.md": good["AGENTS.md"] + " **No environment setup is required.**" });
  if (!setup.some((p) => p.includes("no environment setup"))) {
    throw new Error("selftest: the setup claim was not reported");
  }

  const silent = auditDocs({
    "AGENTS.md": "Run ./gradlew here. A missing JDK is a reportable blocker.",
    "README_AI.md": "x",
    "README.md": "y",
  });
  if (!silent.some((p) => p.includes("does not say that GitHub Actions"))) {
    throw new Error("selftest: a missing 'GitHub Actions' statement was not reported");
  }

  const counts = { total: 19, withSelftest: 9 };
  const line = "| x | 19 guards (9 with `--selftest`) |";
  if (auditGuardCount(line, counts).length !== 0) {
    throw new Error(`selftest: a correct guard count was refused: ${auditGuardCount(line, counts)}`);
  }
  if (!auditGuardCount("16 guards (7 with `--selftest`)", counts).some((p) => p.includes("says 16 guards"))) {
    throw new Error("selftest: a drifted guard count was not reported");
  }
  if (!auditGuardCount("| x | no counts |", counts).some((p) => p.includes("no longer states"))) {
    throw new Error("selftest: a missing guard count was not reported");
  }

  console.log("Onboarding-doc selftest passed: Windows paths, the setup claim, the missing CI/JDK statements and the guard count all checked.");
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  const files = {};
  const problems = [];
  for (const name of DOCS) {
    if (!fs.existsSync(name)) {
      problems.push(`${name}: missing — it is part of onboarding`);
      continue;
    }
    files[name] = fs.readFileSync(name, "utf8");
  }
  problems.push(...auditDocs(files));

  const strategy = "docs/qa/TEST_STRATEGY.md";
  if (fs.existsSync(strategy)) {
    const scripts = fs.readdirSync("scripts").filter((file) => file.endsWith(".mjs"));
    const actual = {
      total: scripts.length,
      withSelftest: scripts.filter((file) => fs.readFileSync(`scripts/${file}`, "utf8").includes("--selftest")).length,
    };
    problems.push(...auditGuardCount(fs.readFileSync(strategy, "utf8"), actual));
  }

  if (problems.length > 0) {
    console.error(`Onboarding-doc check failed (${problems.length} problem(s)):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }
  console.log(
    `Onboarding-doc check passed: ${DOCS.length} documents describe the real environment — no Windows paths, ` +
      `no setup claim, CI named as the compiler, and the quoted guard count true.`
  );
}
