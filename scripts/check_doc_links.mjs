#!/usr/bin/env node
/**
 * REM-069: documentation link checker.
 *
 * Fails when a Markdown file in this repository:
 *   - links to a host-absolute path (`file:///...`), which no other clone can resolve, or
 *   - links to this repository through a web host instead of a repo-relative path, or
 *   - links to a relative path that does not exist.
 *
 * External http(s) links are not fetched (no network in CI); only their shape is checked.
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const SKIP_DIRS = new Set(["node_modules", ".git", "build", ".gradle", "lib"]);

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) walk(path.join(dir, entry.name), out);
    } else if (entry.name.toLowerCase().endsWith(".md")) {
      out.push(path.join(dir, entry.name));
    }
  }
  return out;
}

const LINK = /\]\(([^)\s]+)(?:\s+"[^"]*")?\)/g;
const problems = [];
let checked = 0;

for (const file of walk(ROOT)) {
  const relativeFile = path.relative(ROOT, file).split(path.sep).join("/");
  const text = fs.readFileSync(file, "utf8");
  for (const match of text.matchAll(LINK)) {
    const raw = match[1];
    if (/^(https?:|mailto:|tel:)/i.test(raw)) {
      if (/^https?:\/\/(www\.)?github\.com\/[^/]+\/[^/]+\/(blob|tree)\//i.test(raw)) {
        problems.push(`${relativeFile}: host-absolute repository link ${raw}`);
      }
      continue;
    }
    if (/^file:/i.test(raw)) {
      problems.push(`${relativeFile}: host-absolute file link ${raw}`);
      continue;
    }
    if (raw.startsWith("#")) continue;

    checked += 1;
    const target = decodeURIComponent(raw.split("#")[0]);
    if (!target) continue;
    const resolved = target.startsWith("/") ? path.join(ROOT, target) : path.resolve(path.dirname(file), target);
    if (!fs.existsSync(resolved)) {
      problems.push(`${relativeFile}: broken relative link ${raw}`);
    }
  }
}

if (problems.length > 0) {
  console.error(`Documentation link check failed (${problems.length} problem(s)):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}

console.log(`Documentation link check passed: ${checked} relative links resolve; no host-absolute links.`);
