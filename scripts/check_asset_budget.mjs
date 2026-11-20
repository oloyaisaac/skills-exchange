#!/usr/bin/env node
/**
 * R13-P2-06: keep the app's assets small enough to install and start quickly.
 *
 * The review found two sample JPEGs at ~896 KB and ~680 KB committed into `res/drawable` — roughly
 * 80 % of the module's resource bytes, paid for by every install and by every cold start that decodes
 * them. They are now 68 KB, 41 KB and 16 KB, and this check keeps it that way:
 *
 *   - no single resource file above 150 KB (REM-062's acceptance criterion);
 *   - the whole `res/` tree under 1 MB.
 *
 * `--selftest` builds a fixture tree and proves both refusals can happen.
 */
import fs from "node:fs";
import path from "node:path";
import os from "node:os";

const RES = "app/src/main/res";
export const PER_FILE_LIMIT = 150 * 1024;
export const TREE_LIMIT = 1024 * 1024;

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, files);
    else if (entry.isFile()) files.push({ path: full, size: fs.statSync(full).size });
  }
  return files;
}

export function problems(root) {
  const resRoot = path.join(root, RES);
  if (!fs.existsSync(resRoot)) return [`${RES} is missing`];

  const found = [];
  const files = walk(resRoot);
  const total = files.reduce((sum, file) => sum + file.size, 0);

  for (const file of files) {
    if (file.size > PER_FILE_LIMIT) {
      found.push(
        `${path.relative(root, file.path).split(path.sep).join("/")} is ${Math.round(file.size / 1024)} KB ` +
          `(limit ${PER_FILE_LIMIT / 1024} KB): downscale or re-encode it`
      );
    }
  }

  if (total > TREE_LIMIT) {
    found.push(
      `res/ totals ${Math.round(total / 1024)} KB (limit ${TREE_LIMIT / 1024} KB): ` +
        `every install and cold start pays for this`
    );
  }

  return found;
}

function selftest() {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "asset-budget-"));
  const drawable = path.join(tmp, RES, "drawable");
  fs.mkdirSync(drawable, { recursive: true });

  fs.writeFileSync(path.join(drawable, "small.png"), Buffer.alloc(1024));
  if (problems(tmp).length > 0) {
    throw new Error("selftest: a small asset tree must pass");
  }

  fs.writeFileSync(path.join(drawable, "huge.jpg"), Buffer.alloc(PER_FILE_LIMIT + 1));
  const tooBig = problems(tmp);
  if (!tooBig.some((problem) => problem.includes("huge.jpg"))) {
    throw new Error("selftest: an oversized asset was not refused");
  }
  fs.rmSync(path.join(drawable, "huge.jpg"));

  // Ten files just under the per-file limit still breach the tree budget.
  for (let index = 0; index < 11; index += 1) {
    fs.writeFileSync(path.join(drawable, `bulk-${index}.jpg`), Buffer.alloc(PER_FILE_LIMIT - 1024));
  }
  const tooMuch = problems(tmp);
  if (!tooMuch.some((problem) => problem.includes("res/ totals"))) {
    throw new Error("selftest: an over-budget resource tree was not refused");
  }

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log("Asset budget selftest passed: a small tree passes; an oversized file and an oversized tree are each refused.");
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  const root = process.cwd();
  const found = problems(root);
  if (found.length > 0) {
    console.error(`Asset budget check failed (${found.length} problem(s)):`);
    for (const problem of found) console.error(`  - ${problem}`);
    process.exit(1);
  }
  const files = walk(path.join(root, RES));
  const total = files.reduce((sum, file) => sum + file.size, 0);
  const largest = files.reduce((max, file) => Math.max(max, file.size), 0);
  console.log(
    `Asset budget check passed: ${files.length} resources, ${Math.round(total / 1024)} KB total, ` +
      `largest ${Math.round(largest / 1024)} KB (limits: ${TREE_LIMIT / 1024} KB tree, ${PER_FILE_LIMIT / 1024} KB file).`
  );
}
