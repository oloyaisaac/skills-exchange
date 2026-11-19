#!/usr/bin/env node
/**
 * REM-070: structural checks for docs/traceability.md.
 *
 * Fails on duplicate/malformed IDs, missing implementation or test cells, references to files that
 * do not exist, or missing coverage of requirement sections 3–10. Prints the number of requirements
 * without an automated test instead of pretending the matrix is complete.
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const FILE = path.join(ROOT, "docs", "traceability.md");

if (!fs.existsSync(FILE)) {
  console.error("docs/traceability.md is missing");
  process.exit(1);
}

const rows = [];
for (const line of fs.readFileSync(FILE, "utf8").split("\n")) {
  if (!line.startsWith("| REQ-")) continue;
  const cells = line.split("|").map((cell) => cell.trim());
  // cells[0] and cells[last] are empty because the line starts and ends with a pipe.
  rows.push({
    id: cells[1],
    requirement: cells[2] ?? "",
    implementation: cells[3] ?? "",
    test: cells[4] ?? "",
  });
}

const problems = [];
const ids = new Set();
const sections = new Set();
let testGaps = 0;

for (const row of rows) {
  if (!/^REQ-[A-Z]+-\d{2}$/.test(row.id)) problems.push(`malformed id: ${row.id}`);
  if (ids.has(row.id)) problems.push(`duplicate id: ${row.id}`);
  ids.add(row.id);

  const section = row.requirement.match(/§(\d+)(?:\.\d+)?/);
  if (section) sections.add(Number(section[1]));
  else problems.push(`no requirement section for ${row.id}`);

  if (!row.implementation || row.implementation === "NONE") {
    problems.push(`no implementation for ${row.id}`);
  }
  if (!row.test) problems.push(`no test cell for ${row.id}`);
  if (/^NONE\b/.test(row.test)) testGaps += 1;

  for (const cell of [row.implementation, row.test]) {
    for (const match of cell.matchAll(/`([^`]+)`/g)) {
      const target = match[1];
      if (target.includes("*") || target.includes("§")) continue;
      if (!fs.existsSync(path.join(ROOT, target))) {
        problems.push(`${row.id}: missing referenced path ${target}`);
      }
    }
  }
}

for (let section = 3; section <= 10; section += 1) {
  if (!sections.has(section)) problems.push(`requirement section §${section} has no trace row`);
}

if (rows.length === 0) problems.push("no requirement rows found");

if (problems.length > 0) {
  console.error(`Traceability check failed (${problems.length} problem(s)):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}

console.log(
  `Traceability check passed: ${rows.length} requirements across sections ` +
    `${Array.from(sections).sort((a, b) => a - b).join(", ")}; ${testGaps} without an automated test.`
);
