#!/usr/bin/env node
/**
 * R11-P2-04: a test file that nothing runs is not a test.
 *
 * `functions/package.json` used to enumerate every suite by hand. Adding a `*.test.mjs` and forgetting
 * to edit that list produced a green build and a suite that never ran — false confidence with a
 * passing check in front of it. The list is now a glob, and this guard proves the glob is the only
 * thing deciding what runs:
 *
 *   1. every `functions/test/*.mjs` is matched by exactly one npm script (`test` or `test:handlers`);
 *   2. `test:coverage` runs the same files as `test` — coverage cannot silently measure a subset;
 *   3. CI actually invokes `test`, `test:coverage` and `test:handlers` — a gate the pipeline does not
 *      run is a suggestion, not a gate.
 *
 * `--selftest` builds a temporary copy of the layout with an unregistered suite and asserts this
 * script fails on it. A guard that cannot fail is decoration.
 *
 * Run: node scripts/check_test_registration.mjs [--selftest]
 */
import fs from "node:fs";
import path from "node:path";
import os from "node:os";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, "..");

/** Expands one `test/…` glob against the files that actually exist under `treeRoot`. */
function expand(treeRoot, glob) {
  const dir = path.join(treeRoot, "functions", path.dirname(glob));
  if (!fs.existsSync(dir)) return [];
  const source = path
    .basename(glob)
    .split("*")
    .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
    .join(".*");
  const regex = new RegExp(`^${source}$`);
  return fs
    .readdirSync(dir)
    .filter((name) => regex.test(name))
    .map((name) => path.posix.join(path.dirname(glob), name))
    .sort();
}

/** The `test/…` files a `node --test …` script names, expanded. `null` when the script is absent. */
function filesFor(treeRoot, script) {
  if (typeof script !== "string") return null;
  const marker = "node --test ";
  const at = script.indexOf(marker);
  if (at === -1) return null;
  return script
    .slice(at + marker.length)
    .split(/\s+/)
    .filter((token) => token.startsWith("test/"))
    .flatMap((token) => (token.includes("*") ? expand(treeRoot, token) : [token]))
    .sort();
}

/** Returns the list of problems found in `treeRoot`. An empty list means the layout is honest. */
function problems(treeRoot) {
  const found = [];
  const functionsDir = path.join(treeRoot, "functions");
  const pkgPath = path.join(functionsDir, "package.json");
  if (!fs.existsSync(pkgPath)) return [`no ${pkgPath}`];
  const scripts = JSON.parse(fs.readFileSync(pkgPath, "utf8")).scripts ?? {};

  const unit = filesFor(treeRoot, scripts.test);
  const handlers = filesFor(treeRoot, scripts["test:handlers"]);
  const coverage = filesFor(treeRoot, scripts["test:coverage"]);
  if (unit === null) return ["functions/package.json has no `node --test` test script"];
  if (handlers === null) return ["functions/package.json has no `node --test` test:handlers script"];
  if (coverage === null) return ["functions/package.json has no `node --test` test:coverage script"];

  const testDir = path.join(functionsDir, "test");
  const onDisk = fs.existsSync(testDir)
    ? fs
        .readdirSync(testDir)
        .filter((name) => name.endsWith(".mjs"))
        .map((name) => path.posix.join("test", name))
        .sort()
    : [];

  // 1. Nothing silently unrun, nothing run twice.
  for (const file of onDisk) {
    const inUnit = unit.includes(file);
    const inHandlers = handlers.includes(file);
    if (!inUnit && !inHandlers) {
      found.push(`${file} exists but no npm script runs it — add it to the glob or to test:handlers`);
    }
    if (inUnit && inHandlers) {
      found.push(`${file} is run by both test and test:handlers — it would run twice`);
    }
  }
  // A glob that matches nothing is also a lie: it stopped matching after a rename.
  for (const [name, list] of [
    ["test", unit],
    ["test:handlers", handlers],
  ]) {
    if (list.length === 0) found.push(`npm run ${name} matches no file — the glob is stale`);
    for (const file of list) {
      if (!fs.existsSync(path.join(functionsDir, file))) {
        found.push(`npm run ${name} names ${file}, which does not exist`);
      }
    }
  }

  // 2. Coverage measures the same suite the tests run.
  if (coverage.join("|") !== unit.join("|")) {
    found.push(
      `test:coverage runs a different set from test (test: ${unit.join(", ")} | coverage: ${coverage.join(", ")})`,
    );
  }

  // 3. The pipeline runs the gates.
  const ciPath = path.join(treeRoot, ".github", "workflows", "ci.yml");
  if (!fs.existsSync(ciPath)) {
    found.push("no .github/workflows/ci.yml");
  } else {
    const ci = fs.readFileSync(ciPath, "utf8");
    for (const command of ["npm test", "npm run test:coverage", "npm run test:handlers"]) {
      if (!ci.includes(command)) found.push(`CI never runs \`${command}\``);
    }
  }
  return found;
}

function selftest() {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "test-registration-"));
  try {
    const copy = (relative) => {
      const target = path.join(tmp, relative);
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.copyFileSync(path.join(root, relative), target);
    };
    copy("functions/package.json");
    copy("functions/test/handlers.emulator.mjs");
    copy(".github/workflows/ci.yml");
    fs.writeFileSync(path.join(tmp, "functions/test/a.test.mjs"), "// fixture\n");
    fs.writeFileSync(path.join(tmp, "functions/test/b.test.mjs"), "// fixture\n");

    const clean = problems(tmp);
    if (clean.length > 0) {
      console.error("selftest: the faithful fixture did not pass:");
      for (const message of clean) console.error(`  - ${message}`);
      process.exit(1);
    }

    // Auto-discovery covers every `*.test.mjs`, so the file that can still be forgotten is one that
    // does *not* carry that suffix — exactly the shape `handlers.emulator.mjs` has. A `.mjs` in the
    // test directory that no script names is the real orphan.
    fs.writeFileSync(path.join(tmp, "functions/test/orphan.mjs"), "// fixture\n");
    const dirty = problems(tmp);
    if (!dirty.some((message) => message.includes("orphan.mjs"))) {
      console.error("selftest: an unregistered suite was NOT detected — the guard cannot fail");
      process.exit(1);
    }
    console.log(
      "Test-registration guard selftest passed: a clean tree passes, an unregistered suite is named and refused.",
    );
  } finally {
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}

if (process.argv.includes("--selftest")) {
  selftest();
  process.exit(0);
}

const found = problems(root);
if (found.length > 0) {
  console.error("Test-registration guard FAILED:");
  for (const message of found) console.error(`  - ${message}`);
  process.exit(1);
}
console.log(
  "Test-registration guard passed: every suite is run by a script, coverage measures the same set, and CI runs all three.",
);
