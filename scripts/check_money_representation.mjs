#!/usr/bin/env node
/**
 * VC-06: heuristic schema and source checks, not proof of money-flow correctness.
 * Checks money-shaped columns against an explicit legacy REAL allowlist and
 * reports direct Kotlin member accesses to legacy walletBalance in all source layers.
 * It cannot detect SQL reads, reflection, aliases, indirect flows or validate semantics.
 * A separate review and tests of cashout authorization remain necessary.
 */
import fs from "node:fs";
import path from "node:path";

const SCHEMA_DIR = "app/schemas/com.example.data.db.AppDatabase";

/** Legacy REAL fields: roles are descriptive, not evidence of calculation or settlement. */
export const QUOTED_MONEY = {
  "job_posts.budget": "job budget",
  "job_posts.materialsCost": "job materials cost",
  "job_posts.safetyFee": "job safety fee",
  "job_posts.laborRate": "job labor rate",
  "job_posts.laborTotal": "job labor total",
  "job_posts.materialsSubtotal": "job materials subtotal",
  "job_posts.platformFee": "job platform fee",
  "job_posts.grandTotal": "job total",
  "job_posts.emergencyAddonAmount": "emergency add-on",
  "job_posts.materialAdvanceAmount": "material advance",
  "job_posts.calloutFee": "callout fee",
  "job_posts.recurringNegotiatedRate": "recurring rate",
  "job_quotes.totalQuote": "quote total",
  "job_quotes.proposedRate": "proposed quote rate",
  "disputes.resolutionAmount": "recorded dispute amount",
  "service_providers.hourlyRate": "provider rate",
};

/** Legacy fields are not proven ledger mirrors and must not authorize cashouts. */
export const DISPLAY_MIRRORS = ["user_profiles.walletBalance", "service_providers.walletBalance"];

// Column *names* that denote an amount of money, anchored at the end so prose columns
// (`reviewFeedback`) and URL columns (`materialAdvanceReceiptUrl`) are not mistaken for money.
const MONEY = /(amount|amountminor|budget|fees?|totals?|subtotals?|costs?|rates?|balances?|prices?|advances?|quotes?)$/i;

export function newestSchema(dir) {
  const files = fs
    .readdirSync(dir)
    .filter((file) => /^\d+\.json$/.test(file))
    .sort((a, b) => Number.parseInt(a, 10) - Number.parseInt(b, 10));
  if (files.length === 0) throw new Error(`no schema JSON in ${dir}`);
  const file = files[files.length - 1];
  const doc = JSON.parse(fs.readFileSync(path.join(dir, file), "utf8"));
  return { file, version: doc.database.version, entities: doc.database.entities };
}

/** Money columns in the schema, with the affinity Room will create. */
export function moneyColumns(entities) {
  const found = [];
  for (const entity of entities) {
    for (const field of entity.fields) {
      if (!MONEY.test(field.columnName)) continue;
      found.push({ key: `${entity.tableName}.${field.columnName}`, affinity: field.affinity });
    }
  }
  return found;
}

export function auditSchema({ entities }, quoted = QUOTED_MONEY, mirrors = DISPLAY_MIRRORS) {
  const problems = [];
  const columns = moneyColumns(entities);
  const seen = new Set(columns.map((column) => column.key));

  for (const column of columns) {
    if (column.affinity === "INTEGER" && !Object.hasOwn(quoted, column.key) && !mirrors.includes(column.key)) continue;
    if (column.affinity === "REAL" && Object.prototype.hasOwnProperty.call(quoted, column.key)) continue;
    if (column.affinity === "REAL" && mirrors.includes(column.key)) continue;
    problems.push(
      `${column.key}: money-shaped column stored as ${column.affinity} and not classified as a valid INTEGER or allowlisted REAL`
    );
  }

  for (const key of Object.keys(quoted)) {
    if (!seen.has(key)) problems.push(`${key}: classified as quoted money but no such column exists`);
  }

  return problems;
}

/** Remove Kotlin comments without treating // inside a string as a comment.
 * This is a lightweight lexer, not a parser; member names inside strings can produce false positives.
 */
export function stripComments(source) {
  let out = "", state = "code", depth = 0;
  for (let i = 0; i < source.length;) {
    const pair = source.slice(i, i + 2), triple = source.slice(i, i + 3), ch = source[i];
    if (state === "line") {
      if (ch === "\n") { state = "code"; out += "\n"; } else out += " ";
      i++; continue;
    }
    if (state === "block") {
      if (pair === "/*") { depth++; out += "  "; i += 2; continue; }
      if (pair === "*/") { depth--; out += "  "; i += 2; if (!depth) state = "code"; continue; }
      out += ch === "\n" ? "\n" : " "; i++; continue;
    }
    if (state === "triple") {
      if (triple === '"""') { out += triple; i += 3; state = "code"; }
      else { out += ch; i++; }
      continue;
    }
    if (state === "string" || state === "char") {
      out += ch; i++;
      if (ch === "\\" && i < source.length) { out += source[i++]; continue; }
      if ((state === "string" && ch === '"') || (state === "char" && ch === "'")) state = "code";
      continue;
    }
    if (pair === "//") { state = "line"; out += "  "; i += 2; continue; }
    if (pair === "/*") { state = "block"; depth = 1; out += "  "; i += 2; continue; }
    if (triple === '"""') { state = "triple"; out += triple; i += 3; continue; }
    if (ch === '"') state = "string";
    else if (ch === "'") state = "char";
    out += ch; i++;
  }
  return out;
}

/** Detect direct member accesses in all Kotlin source layers; this is not a Kotlin parser. */
export function auditMirrorReaders(files, mirrors = DISPLAY_MIRRORS) {
  const problems = [];
  for (const mirror of mirrors) {
    const field = mirror.split(".")[1];
    for (const [file, text] of Object.entries(files)) {
      const reader = new RegExp(`\\.${field}\\b`);
      if (reader.test(stripComments(text))) problems.push(`${mirror}: direct member access in ${file}; this legacy field must not authorize cashouts`);
    }
  }
  return problems;
}

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(p, out);
    else if (p.endsWith(".kt")) out.push(p);
  }
  return out;
}

function selftest() {
  const schema = (fields) => ({
    entities: [{ tableName: "t", fields }],
  });

  const clean = schema([
    { columnName: "amountMinor", affinity: "INTEGER" },
    { columnName: "budget", affinity: "REAL" },
  ]);
  if (auditSchema(clean, { "t.budget": "quoted" }).length !== 0) {
    throw new Error("selftest: a classified schema was refused");
  }

  const unclassified = auditSchema(schema([{ columnName: "grandTotal", affinity: "REAL" }]), {});
  if (!unclassified.some((p) => p.includes("not classified"))) {
    throw new Error(`selftest: an unclassified REAL money column was not reported: ${unclassified}`);
  }

  const unclassifiedText = auditSchema(schema([{ columnName: "grandTotal", affinity: "TEXT" }]), {});
  if (!unclassifiedText.some((p) => p.includes("not classified"))) {
    throw new Error("selftest: unclassified TEXT money-shaped field was not reported");
  }

  const settled = auditSchema(schema([{ columnName: "walletBalance", affinity: "REAL" }]), {});
  if (!settled.some((p) => p.includes("walletBalance"))) {
    throw new Error("selftest: a REAL balance was not reported");
  }

  const stale = auditSchema(schema([{ columnName: "amountMinor", affinity: "INTEGER" }]), { "t.ghostFee": "gone" });
  if (!stale.some((p) => p.includes("no such column"))) {
    throw new Error("selftest: a stale allowlist entry was not reported");
  }

  if (auditSchema(schema([{ columnName: "amountMinor", affinity: "INTEGER" }]), {}).length !== 0) {
    throw new Error("selftest: a minor-unit column was refused");
  }

  const readers = auditMirrorReaders({ "app/src/main/java/com/example/ui/Foo.kt": "job.walletBalance * 2" }, [
    "t.walletBalance",
  ]);
  if (!readers.some((p) => p.includes("direct member access"))) {
    throw new Error("selftest: a mirror reader outside the data layer was not reported");
  }
  if (auditMirrorReaders({ "app/src/main/java/com/example/ui/Bar.kt": "// row.walletBalance is only display" }, ["t.walletBalance"]).length !== 0) {
    throw new Error("selftest: a comment was mistaken for a reader");
  }
  if (auditMirrorReaders({ "app/src/main/java/com/example/data/repository/X.kt": 'val url = "https://example.com"; val n = row.walletBalance' }, ["t.walletBalance"]).length === 0) {
    throw new Error("selftest: URL string hid member access");
  }
  if (auditMirrorReaders({ "app/src/main/java/com/example/data/repository/X.kt": "/* outer /* nested */ row.walletBalance */" }, ["t.walletBalance"]).length !== 0) {
    throw new Error("selftest: nested comment was mistaken for a read");
  }
  if (auditMirrorReaders({ "app/src/main/java/com/example/data/repository/X.kt": "row.walletBalance" }, ["t.walletBalance"]).length === 0) {
    throw new Error("selftest: data-layer member access was not reported");
  }
  if (auditSchema(schema([{ columnName: "budget", affinity: "NUMERIC" }]), { "t.budget": "legacy" }).length === 0) {
    throw new Error("selftest: a changed allowlisted affinity was not reported");
  }

  if (auditSchema(schema([{ columnName: "budget", affinity: "INTEGER" }]), { "t.budget": "legacy" }).length === 0) {
    throw new Error("selftest: converted field with stale REAL allowlist was not reported");
  }

  console.log(
    "Money-representation selftest passed: unclassified REAL/TEXT fields, affinity drift, stale allowlist and direct member access in UI/data fixtures detected."
  );
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  if (!fs.existsSync(SCHEMA_DIR)) {
    console.error(`${SCHEMA_DIR} not found — the schema must be exported`);
    process.exit(1);
  }
  const schema = newestSchema(SCHEMA_DIR);
  const files = {};
  for (const file of walk("app/src/main")) files[file] = fs.readFileSync(file, "utf8");

  const problems = [...auditSchema(schema), ...auditMirrorReaders(files)];
  if (problems.length > 0) {
    console.error(`Money-representation check failed (${problems.length} problem(s)):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }

  console.log(
    `Money-representation check passed: schema v${schema.version}; money-shaped columns are ` +
      `INTEGER or allowlisted REAL. No direct walletBalance member access found in scanned Kotlin files. ` +
      `This heuristic does not prove money flow or detect SQL reads.`
  );
}
