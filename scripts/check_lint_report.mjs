#!/usr/bin/env node
/**
 * R14-P2-02: Android Lint runs, and its errors decide a build.
 *
 * The review's point was that nothing static ever inspected the Android source in CI — no lint, no
 * detekt, no ktlint. Detekt and ktlint would add a toolchain for rules lint already implements well, so
 * this round wires Android Lint itself and makes *errors* fail the build while warnings are reported.
 *
 * Two deliberate choices:
 *
 *   - **Errors gate, warnings do not.** A warning-only gate gets switched off within a month; an
 *     error-only gate stays credible and still catches `NewApi` on the wrong minSdk, a missing
 *     permission and a broken manifest.
 *   - **An allowlist exists but is empty and must be typed.** Every entry needs a reason, and the check
 *     fails if an allowlisted rule no longer fires — an allowlist that rots silently is worse than none.
 *
 * Usage: `node scripts/check_lint_report.mjs [--report path] [--allowlist path] [--selftest]`
 */
import fs from "node:fs";
import path from "node:path";

const DEFAULT_REPORT = "app/build/reports/lint-results-debug.xml";
const DEFAULT_ALLOWLIST = "scripts/lint-allowlist.json";

/** Minimal parse of the AGP lint XML: `<issue id= severity= message=><location file line/></issue>`. */
export function parseLintXml(xml) {
  const issues = [];
  const issuePattern = /<issue\b([^>]*)>([\s\S]*?)<\/issue>/g;
  let match;
  while ((match = issuePattern.exec(xml)) !== null) {
    const attributes = match[1];
    const body = match[2];
    const attr = (name) => {
      const found = new RegExp(`${name}="([^"]*)"`).exec(attributes);
      return found ? found[1] : "";
    };
    const location = /<location\b([^>]*)\/?>/.exec(body);
    const locAttr = (name) => {
      if (!location) return "";
      const found = new RegExp(`${name}="([^"]*)"`).exec(location[1]);
      return found ? found[1] : "";
    };
    issues.push({
      id: attr("id"),
      severity: attr("severity"),
      message: attr("message").replace(/&#10;/g, " ").slice(0, 200),
      file: locAttr("file"),
      line: locAttr("line"),
    });
  }
  return issues;
}

export function blockingIssues(issues, allowlist) {
  const allowed = new Map(allowlist.map((entry) => [entry.id, entry]));
  const blocking = [];
  const usedAllowlist = new Set();

  for (const issue of issues) {
    if (issue.severity !== "Error" && issue.severity !== "Fatal") continue;
    const entry = allowed.get(issue.id);
    if (entry) {
      usedAllowlist.add(issue.id);
      continue;
    }
    blocking.push(issue);
  }
  const stale = allowlist.filter((entry) => !usedAllowlist.has(entry.id)).map((entry) => entry.id);
  return { blocking, stale };
}

function selftest() {
  const xml = `<?xml version="1.0"?>
<issues format="6" by="lint 9.0.0">
  <issue id="NewApi" severity="Error" message="Call requires API level 33 &amp; more" category="Correctness">
    <location file="app/src/main/java/com/example/A.kt" line="12" column="5"/>
  </issue>
  <issue id="UnusedResources" severity="Warning" message="Unused resource" category="Performance">
    <location file="app/src/main/res/values/strings.xml" line="3"/>
  </issue>
  <issue id="MissingPermission" severity="Fatal" message="Missing android.permission.INTERNET" category="Correctness">
    <location file="app/src/main/AndroidManifest.xml" line="7"/>
  </issue>
</issues>`;

  const issues = parseLintXml(xml);
  if (issues.length !== 3) throw new Error(`selftest: parsed ${issues.length} issues, expected 3`);
  if (issues[0].id !== "NewApi" || issues[0].line !== "12") {
    throw new Error(`selftest: issue attributes not parsed: ${JSON.stringify(issues[0])}`);
  }

  const plain = blockingIssues(issues, []);
  if (plain.blocking.length !== 2) {
    throw new Error(`selftest: ${plain.blocking.length} blocking issues, expected 2 (Error + Fatal)`);
  }

  const allowed = blockingIssues(issues, [{ id: "NewApi", reason: "selftest" }]);
  if (allowed.blocking.length !== 1 || allowed.blocking[0].id !== "MissingPermission") {
    throw new Error("selftest: the allowlist did not remove exactly the allowed rule");
  }

  const stale = blockingIssues(issues, [{ id: "UnusedResources", reason: "selftest" }]);
  if (stale.stale.length !== 1 || stale.stale[0] !== "UnusedResources") {
    throw new Error("selftest: a stale allowlist entry was not reported");
  }

  console.log("Lint-report selftest passed: parsing, severity gating, allowlisting and stale-entry detection.");
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  const read = (flag, fallback) => {
    const index = process.argv.indexOf(flag);
    return index === -1 ? fallback : process.argv[index + 1];
  };
  const reportPath = read("--report", DEFAULT_REPORT);
  const allowlistPath = read("--allowlist", DEFAULT_ALLOWLIST);

  if (!fs.existsSync(reportPath)) {
    console.error(
      `Lint report not found at ${reportPath}. Run \`./gradlew :app:lintDebug\` first — this check reads ` +
        `the report rather than trusting the build to have produced one.`
    );
    process.exit(1);
  }

  const issues = parseLintXml(fs.readFileSync(reportPath, "utf8"));
  const allowlist = fs.existsSync(allowlistPath) ? JSON.parse(fs.readFileSync(allowlistPath, "utf8")) : [];
  const { blocking, stale } = blockingIssues(issues, allowlist);

  const counts = issues.reduce((acc, issue) => {
    acc[issue.severity] = (acc[issue.severity] || 0) + 1;
    return acc;
  }, {});

  const summary = [
    `lint report: ${reportPath}`,
    `issues: ${issues.length} (${Object.entries(counts).map(([k, v]) => `${k} ${v}`).join(", ") || "none"})`,
    `allowlisted: ${allowlist.length}`,
    ...blocking.map((issue) => `BLOCKING ${issue.severity} ${issue.id}: ${issue.message} (${issue.file}:${issue.line})`),
    ...stale.map((id) => `STALE ALLOWLIST ENTRY ${id}: the rule no longer fires, remove it`),
  ];
  fs.writeFileSync("lint-summary.txt", `${summary.join("\n")}\n`);

  if (blocking.length > 0 || stale.length > 0) {
    console.error(`Lint gate failed (${blocking.length} blocking, ${stale.length} stale allowlist):`);
    for (const line of summary.slice(3)) console.error(`  - ${line}`);
    process.exit(1);
  }
  console.log(
    `Lint gate passed: ${issues.length} issues reported (${Object.entries(counts).map(([k, v]) => `${k} ${v}`).join(", ") || "none"}); ` +
      `no Error or Fatal outside the allowlist.`
  );
}
