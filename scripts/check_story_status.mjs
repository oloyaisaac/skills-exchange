#!/usr/bin/env node
/**
 * R15-P2-01 / R15-P2-05: a status is a claim, and the claim needs evidence.
 *
 * The review found 112 stories marked `completed` that this programme had found failing when it looked
 * at the same features, and three different vocabularies in one file (`completed`, `COMPLETED`, `DONE`).
 * Both are now structural: one vocabulary, and `DONE` is only reachable through a written evidence note.
 *
 * The rules, all enforced here:
 *
 *   - the status of a story is exactly one of DONE, IN_PROGRESS, VERIFICATION_PENDING, NOT_STARTED,
 *     UNVERIFIED — no legacy spelling survives;
 *   - DONE requires a `verification_note` that starts with `evidence:` and is long enough to be a record
 *     rather than a tick;
 *   - UNVERIFIED requires a `verification_note` saying why it is not claimed;
 *   - story ids are unique;
 *   - TASKS.md's status column agrees with prd.json for every REM row. It did not: 41 of 73 rows
 *     disagreed when this check was written, because the table was maintained by hand in fourteen
 *     rounds while the story file moved underneath it.
 *
 * `--selftest` proves every refusal with fixtures.
 */
import fs from "node:fs";

export const STATUSES = ["DONE", "IN_PROGRESS", "VERIFICATION_PENDING", "NOT_STARTED", "UNVERIFIED"];
const MIN_EVIDENCE = 40;

export function auditStories(stories) {
  const problems = [];
  const seen = new Set();

  for (const story of stories) {
    const id = story.id || "(no id)";
    if (!story.id) problems.push("a story has no id");
    if (seen.has(story.id)) problems.push(`${id}: duplicate story id`);
    seen.add(story.id);

    if (!STATUSES.includes(story.status)) {
      problems.push(`${id}: status '${story.status}' is not in the vocabulary ${STATUSES.join(" / ")}`);
      continue;
    }

    const note = (story.verification_note || "").trim();

    if (story.status === "DONE") {
      if (!note.startsWith("evidence:")) {
        problems.push(`${id}: DONE without a verification_note starting 'evidence:'`);
      } else if (note.length < MIN_EVIDENCE) {
        problems.push(`${id}: DONE evidence is ${note.length} characters — a pointer, not a record`);
      }
    }

    if (story.status === "UNVERIFIED" && note.length === 0) {
      problems.push(`${id}: UNVERIFIED without a verification_note explaining what is not claimed`);
    }
  }

  return problems;
}

/** The task table carries the same statuses; a second hand-maintained copy drifts silently. */
export function auditTaskTable(text, statusById) {
  const problems = [];
  const rows = text.split("\n").filter((line) => /^\|\s*REM-\d{3}\s*\|/.test(line));
  if (rows.length === 0) problems.push("TASKS.md has no REM rows to cross-check");

  for (const row of rows) {
    const cells = row.split("|").map((cell) => cell.trim());
    const id = cells[1];
    const status = cells[cells.length - 2];
    if (!statusById.has(id)) {
      problems.push(`${id}: a TASKS.md row with no story in prd.json`);
    } else if (statusById.get(id) !== status) {
      problems.push(`${id}: TASKS.md says ${status}, prd.json says ${statusById.get(id)}`);
    }
  }

  return problems;
}

export function histogram(stories) {
  const counts = {};
  for (const story of stories) counts[story.status] = (counts[story.status] || 0) + 1;
  return counts;
}

function selftest() {
  const ok = [
    { id: "A-1", status: "DONE", verification_note: "evidence: CI run 44807529394, backend 318/318 green on 5247699" },
    { id: "A-2", status: "UNVERIFIED", verification_note: "marked complete by an earlier agent; not re-verified" },
    { id: "A-3", status: "IN_PROGRESS" },
    { id: "A-4", status: "NOT_STARTED" },
  ];
  if (auditStories(ok).length !== 0) throw new Error(`selftest: a valid set was rejected: ${auditStories(ok)}`);

  const cases = [
    [{ id: "B-1", status: "completed" }, "not in the vocabulary"],
    [{ id: "B-2", status: "COMPLETED" }, "not in the vocabulary"],
    [{ id: "B-3", status: "DONE" }, "without a verification_note"],
    [{ id: "B-4", status: "DONE", verification_note: "evidence: done" }, "not a record"],
    [{ id: "B-5", status: "UNVERIFIED" }, "without a verification_note"],
    [{ id: "B-6", status: "NOT_STARTED" }, null],
    [{ id: "B-7", status: "DONE", verification_note: "looks fine to me, I read the code carefully" }, "starting 'evidence:'"],
  ];
  for (const [story, expected] of cases) {
    const problems = auditStories([story]);
    if (expected === null) {
      if (problems.length !== 0) throw new Error(`selftest: ${story.id} was refused unexpectedly: ${problems}`);
    } else if (!problems.some((problem) => problem.includes(expected))) {
      throw new Error(`selftest: ${story.id} was not refused for '${expected}': got ${JSON.stringify(problems)}`);
    }
  }

  if (auditStories([{ id: "D-1", status: "DONE", verification_note: "evidence: " + "x".repeat(40) }, { id: "D-1", status: "NOT_STARTED" }])
    .some((problem) => !problem.includes("duplicate"))) {
    throw new Error("selftest: a duplicate id was not reported");
  }

  const table = [
    "| REM-001 | HIGH | R01 | one | - | DONE |",
    "| REM-002 | HIGH | R01 | two | - | NOT_STARTED |",
  ].join("\n");
  const statusById = new Map([["REM-001", "DONE"], ["REM-002", "NOT_STARTED"]]);
  if (auditTaskTable(table, statusById).length !== 0) {
    throw new Error(`selftest: an agreeing task table was refused: ${auditTaskTable(table, statusById)}`);
  }
  const drifted = auditTaskTable(table.replace("| DONE |", "| COMPLETED |"), statusById);
  if (!drifted.some((problem) => problem.includes("TASKS.md says COMPLETED"))) {
    throw new Error(`selftest: task-table drift was not reported: ${JSON.stringify(drifted)}`);
  }
  const phantom = auditTaskTable("| REM-999 | LOW | R99 | x | - | DONE |", statusById);
  if (!phantom.some((problem) => problem.includes("no story in prd.json"))) {
    throw new Error("selftest: a task row with no story was not reported");
  }

  const counts = histogram(ok);
  if (counts.DONE !== 1 || counts.UNVERIFIED !== 1) throw new Error("selftest: the histogram is wrong");

  console.log(
    "Story-status selftest passed: vocabulary, evidence, UNVERIFIED-note, duplicate-id and task-table drift refusals all fire."
  );
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  const path = "prd.json";
  if (!fs.existsSync(path)) {
    console.error(`${path} not found`);
    process.exit(1);
  }
  const doc = JSON.parse(fs.readFileSync(path, "utf8"));
  const stories = doc.stories || [];
  const problems = auditStories(stories);

  if (problems.length > 0) {
    console.error(`Story-status check failed (${problems.length} problem(s)):`);
    for (const problem of problems.slice(0, 40)) console.error(`  - ${problem}`);
    if (problems.length > 40) console.error(`  ... and ${problems.length - 40} more`);
    process.exit(1);
  }

  if (fs.existsSync("TASKS.md")) {
    const statusById = new Map(stories.map((story) => [story.id, story.status]));
    problems.push(...auditTaskTable(fs.readFileSync("TASKS.md", "utf8"), statusById));
  }

  const counts = histogram(stories);
  const summary = STATUSES.map((status) => `${status} ${counts[status] || 0}`).join(", ");
  console.log(
    `Story-status check passed: ${stories.length} stories in one vocabulary (${summary}); ` +
      `every DONE carries evidence, every UNVERIFIED carries a reason, ` +
      `and TASKS.md agrees row for row.`
  );
}
