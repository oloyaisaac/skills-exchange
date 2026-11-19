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

console.log(`Query-limit check passed: ${checked} list queries all carry LIMIT.`);
