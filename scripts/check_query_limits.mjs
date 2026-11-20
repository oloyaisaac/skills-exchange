#!/usr/bin/env node
/**
 * REM-049: no unbounded list query in the Room DAOs.
 *
 * Every `SELECT *` must carry `LIMIT`; counts and single-row lookups are allowed without one only
 * when they use `COUNT(...)` or `LIMIT 1` already. This is a static guard, not a load test.
 */
import fs from "node:fs";
import path from "node:path";

const FILE = path.join(process.cwd(), "app/src/main/java/com/example/data/db/Daos.kt");
if (!fs.existsSync(FILE)) {
  console.error("Daos.kt not found");
  process.exit(1);
}

const text = fs.readFileSync(FILE, "utf8");
const problems = [];
let checked = 0;

for (const match of text.matchAll(/@Query\("([^"]+)"\)/g)) {
  const query = match[1];
  if (!/^\s*SELECT\s+\*/i.test(query)) continue;
  checked += 1;
  if (!/\bLIMIT\b/i.test(query)) {
    const line = text.slice(0, match.index).split("\n").length;
    problems.push(`Daos.kt:${line}: unbounded query: ${query}`);
  }
}

if (problems.length > 0) {
  console.error(`Query-limit check failed (${problems.length} problem(s)):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}



// R13-P2-03: the same rule for the client's Firestore listeners. A snapshot listener without a bound
// streams the whole collection into memory on attach and keeps it there; the review found one on
// `jobs` (dead, and denied by the rules) and one on a job's messages (live, unbounded).
const CLIENT_ROOT = path.join(process.cwd(), "app/src/main/java");
const listenerProblems = [];
let listeners = 0;

function walkClient(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walkClient(full);
    else if (entry.isFile() && entry.name.endsWith(".kt")) checkListeners(full);
  }
}

function checkListeners(file) {
  const source = fs.readFileSync(file, "utf8");
  let index = source.indexOf(".addSnapshotListener");
  while (index !== -1) {
    listeners += 1;
    const before = source.slice(Math.max(0, index - 600), index);
    if (!/\.(limit|limitToLast)\(/.test(before)) {
      const line = source.slice(0, index).split("\n").length;
      listenerProblems.push(
        `${path.relative(process.cwd(), file)}:${line}: listener has no limit( / limitToLast( within 600 chars`
      );
    }
    index = source.indexOf(".addSnapshotListener", index + 1);
  }
}

if (fs.existsSync(CLIENT_ROOT)) walkClient(CLIENT_ROOT);

if (listenerProblems.length > 0) {
  console.error(`Listener-bound check failed (${listenerProblems.length} problem(s)):`);
  for (const problem of listenerProblems) console.error(`  - ${problem}`);
  process.exit(1);
}

console.log(`Query-limit check passed: ${checked} list queries all carry LIMIT; ${listeners} client listener(s) all bounded.`);
