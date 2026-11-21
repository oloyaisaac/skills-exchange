#!/usr/bin/env node
/**
 * R15-P2-02: every story is either traced to a requirement or declared a gap.
 *
 * The review measured traceability at 24 requirement ids against 185 stories and called the coverage
 * ~13%. Mapping 185 stories to 185 rows would be a second backlog nobody maintains; what is maintainable
 * is the level the stories already have — their area prefix — with a count and an answer per area:
 * which requirement rows cover it, or why it is a gap.
 *
 * This check parses both sides and fails when:
 *   - a story area in `prd.json` has no row in the coverage table,
 *   - a row's count disagrees with `prd.json`,
 *   - a row names a requirement id that does not exist in the requirements table,
 *   - a row claims to trace an area but names no requirement and gives no reason (the reason column
 *     must be non-empty either way).
 *
 * `--selftest` proves each refusal with fixtures.
 */
import fs from "node:fs";

const PRD = "prd.json";
const DOC = "docs/traceability.md";

export function areaOf(id) {
  const match = /^([A-Z]+)-\d+$/.exec(id);
  return match ? match[1] : null;
}

/** Counts stories per area, and reports any id that does not follow the `AREA-NNN` shape. */
export function areaCounts(stories) {
  const counts = new Map();
  const malformed = [];
  for (const story of stories) {
    const area = areaOf(story.id || "");
    if (!area) {
      malformed.push(story.id || "(no id)");
      continue;
    }
    counts.set(area, (counts.get(area) || 0) + 1);
  }
  return { counts, malformed };
}

/** Requirement ids from the requirements table (rows that start with a REQ id). */
export function requirementIds(markdown) {
  const ids = new Set();
  for (const line of markdown.split("\n")) {
    const match = /^\|\s*(REQ-[A-Z]+-\d{2})\s*\|/.exec(line);
    if (match) ids.add(match[1]);
  }
  return ids;
}

/** The coverage table: `| AREA | N | requirements | reason |`. */
export function coverageRows(markdown) {
  const rows = new Map();
  const section = markdown.split("## Story coverage by area")[1] || "";
  for (const line of section.split("\n")) {
    const match = /^\|\s*`?([A-Z]{2,7})`?\s*\|\s*(\d+)\s*\|([^|]*)\|([^|]*)\|/.exec(line);
    if (!match) continue;
    const [, area, count, requirementsCell, reason] = match;
    rows.set(area, {
      count: Number(count),
      requirements: [...requirementsCell.matchAll(/REQ-[A-Z]+-\d{2}/g)].map((m) => m[0]),
      declaredGap: /none/i.test(requirementsCell.trim()),
      reason: reason.trim(),
    });
  }
  return rows;
}

export function audit(prd, markdown) {
  const problems = [];
  const { counts, malformed } = areaCounts(prd.stories || []);
  const requirements = requirementIds(markdown);
  const rows = coverageRows(markdown);

  for (const id of malformed) problems.push(`story id '${id}' does not follow AREA-NNN, so it has no area`);

  for (const [area, count] of counts) {
    const row = rows.get(area);
    if (!row) {
      problems.push(`${area}: ${count} stories and no coverage row in ${DOC}`);
      continue;
    }
    if (row.count !== count) {
      problems.push(`${area}: coverage row says ${row.count} stories, prd.json has ${count}`);
    }
    if (row.reason.length < 10) {
      problems.push(`${area}: the reason column is empty — say why the area is or is not traced`);
    }
    for (const requirement of row.requirements) {
      if (!requirements.has(requirement)) {
        problems.push(`${area}: names ${requirement}, which no requirement row defines`);
      }
    }
  }

  for (const area of rows.keys()) {
    if (!counts.has(area)) problems.push(`${area}: coverage row exists but prd.json has no such area`);
  }

  return { problems, counts, rows, requirements };
}

function selftest() {
  const prd = { stories: [{ id: "AAA-001" }, { id: "AAA-002" }, { id: "BBB-001" }] };
  const markdown = `# Traceability

| ID | Requirement (section) | Implementation | Automated test | Status |
|---|---|---|---|---|
| REQ-TEST-01 | Something (§3) | x | y | z |

## Story coverage by area

| Area | Stories | Requirement rows | Why it is, or is not, traced |
|---|---|---|---|
| \`AAA\` | 2 | \`REQ-TEST-01\` | covered by the test requirement row |
| \`BBB\` | 1 | none | a process area with no product requirement |
`;
  const clean = audit(prd, markdown);
  if (clean.problems.length !== 0) throw new Error(`selftest: a valid pair was rejected: ${clean.problems}`);

  const missing = audit(prd, markdown.replace(/^\| `BBB`.*$/m, ""));
  if (!missing.problems.some((p) => p.includes("BBB") && p.includes("no coverage row"))) {
    throw new Error(`selftest: a missing area was not reported: ${missing.problems}`);
  }

  const drifted = audit(prd, markdown.replace("| `AAA` | 2 |", "| `AAA` | 3 |"));
  if (!drifted.problems.some((p) => p.includes("coverage row says 3"))) {
    throw new Error("selftest: a drifted count was not reported");
  }

  const phantom = audit(prd, markdown.replace("`REQ-TEST-01`", "`REQ-GHOST-09`"));
  if (!phantom.problems.some((p) => p.includes("REQ-GHOST-09"))) {
    throw new Error("selftest: a requirement id that does not exist was not reported");
  }

  const emptyReason = audit(prd, markdown.replace("covered by the test requirement row", ""));
  if (!emptyReason.problems.some((p) => p.includes("reason column is empty"))) {
    throw new Error("selftest: an empty reason was not reported");
  }

  const badId = audit({ stories: [{ id: "nope" }] }, markdown);
  if (!badId.problems.some((p) => p.includes("does not follow AREA-NNN"))) {
    throw new Error("selftest: a malformed story id was not reported");
  }

  console.log("Story-coverage selftest passed: missing area, drifted count, phantom requirement, empty reason and malformed id all refused.");
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  for (const file of [PRD, DOC]) {
    if (!fs.existsSync(file)) {
      console.error(`${file} not found`);
      process.exit(1);
    }
  }
  const prd = JSON.parse(fs.readFileSync(PRD, "utf8"));
  const markdown = fs.readFileSync(DOC, "utf8");
  const { problems, counts } = audit(prd, markdown);

  if (problems.length > 0) {
    console.error(`Story-coverage check failed (${problems.length} problem(s)):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }

  const stories = [...counts.values()].reduce((sum, n) => sum + n, 0);
  console.log(
    `Story-coverage check passed: ${stories} stories across ${counts.size} areas, every area with a count ` +
      `and either requirement rows or a stated reason.`
  );
}
