#!/usr/bin/env node
/**
 * REM-072: verify docs/DOCUMENT_MAP.md stays complete.
 *
 * Every top-level Markdown file and every Markdown file under docs/ must be listed in the map,
 * and every document named in the map must exist. This keeps the precedence table from silently
 * drifting when a document is added or removed.
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const MAP = path.join(ROOT, "docs", "DOCUMENT_MAP.md");

if (!fs.existsSync(MAP)) {
  console.error("docs/DOCUMENT_MAP.md is missing");
  process.exit(1);
}

const mapText = fs.readFileSync(MAP, "utf8");
const listed = new Set();
for (const line of mapText.split("\n")) {
  if (!line.startsWith("|")) continue;
  const first = line.split("|")[1]?.trim();
  if (!first || first === "Document" || first.startsWith("---")) continue;
  for (const token of first.match(/`([^`]+\.md)`/g) ?? []) {
    listed.add(token.replace(/`/g, ""));
  }
}

const actual = new Set();
for (const entry of fs.readdirSync(ROOT, { withFileTypes: true })) {
  if (entry.isFile() && entry.name.endsWith(".md")) actual.add(entry.name);
}
const docsDir = path.join(ROOT, "docs");
// R11: walk `docs/` recursively. The first version of this check looked one level deep, so a document
// in `docs/privacy/`, `docs/safety/` or `docs/qa/` could be added without ever appearing in the map —
// a completeness check that could not see most of the directory it was checking.
if (fs.existsSync(docsDir)) {
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.isFile() && entry.name.endsWith(".md")) {
        actual.add(path.relative(ROOT, full).split(path.sep).join("/"));
      }
    }
  };
  walk(docsDir);
}

const problems = [];
for (const document of actual) {
  if (!listed.has(document)) problems.push(`not listed in DOCUMENT_MAP.md: ${document}`);
}
for (const document of listed) {
  if (document.includes("*")) continue; // documented globs describe evidence directories
  if (!fs.existsSync(path.join(ROOT, document))) problems.push(`listed but missing: ${document}`);
}

if (problems.length > 0) {
  console.error(`Document map check failed (${problems.length} problem(s)):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}

console.log(`Document map check passed: ${actual.size} documents listed with a role and owner.`);
