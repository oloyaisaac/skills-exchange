#!/usr/bin/env node
/**
 * R04-P2-04 / VC-06: money has one representation per role, and the schema has to agree.
 *
 * The round documented `Money` as "the one money representation" while the exported schema still held
 * eighteen REAL money columns and two REAL balances. A comment cannot enforce that; this can.
 *
 * The rule, checked against the newest committed Room schema (not against prose):
 *
 *   - money that is **settled** — anything that is summed into a balance, reserved, withdrawn or paid
 *     out — is stored as `INTEGER` minor units and summed as `Long`;
 *   - money that is **quoted** — a price the user is still editing — may be stored as `REAL`, but only
 *     if it is named in `QUOTED_MONEY` below with the reason, and only if it is produced by a single
 *     rounding step (`JobMoney.split`) before it becomes money;
 *   - a column whose name looks like money but appears in neither list fails the build, so the next
 *     contributor has to make the same decision deliberately;
 *   - an allowlist entry that no longer matches a column fails too, so the list cannot rot;
 *   - both `walletBalance` columns are display mirrors of the ledger. They may stay `REAL` only while
 *     nothing outside the data layer reads them — checked here, because "display only" is a claim like
 *     any other.
 *
 * `--selftest` proves each refusal with fixtures.
 */
import fs from "node:fs";
import path from "node:path";

const SCHEMA_DIR = "app/schemas/com.example.data.db.AppDatabase";

/** Money columns that are quoted, not settled: REAL, with the reason they are allowed to be. */
export const QUOTED_MONEY = {
  "job_posts.budget": "the resident's asking price while the job is REQUESTED/QUOTED; whole shillings from JobMoney.split",
  "job_posts.materialsCost": "a cost estimate entered by the resident, rounded once by JobMoney.split",
  "job_posts.safetyFee": "a quoted add-on, summed by JobMoney.split in whole shillings",
  "job_posts.laborRate": "an hourly rate the worker may still renegotiate (REQ-003 recurring contracts)",
  "job_posts.laborTotal": "the rounded labour component of a quote, recomputed by JobMoney.split on change",
  "job_posts.materialsSubtotal": "the rounded materials component of a quote",
  "job_posts.platformFee": "the rounded 10% fee of a quote, recomputed by JobMoney.split",
  "job_posts.grandTotal": "the rounded sum of the components, never accumulated with Double arithmetic",
  "job_posts.emergencyAddonAmount": "a quoted add-on, folded into the total by JobMoney.split",
  "job_posts.materialAdvanceAmount": "a quoted advance, settled only through the wallet ledger",
  "job_posts.calloutFee": "a quoted visit fee, folded into the total by JobMoney.split",
  "job_posts.recurringNegotiatedRate": "a worker's proposed rate for a recurring contract, not yet money",
  "job_quotes.totalQuote": "the quote total as offered; the ledger records what is actually paid",
  "job_quotes.proposedRate": "a worker's price for one job; accepted quotes are re-derived server-side",
  "disputes.resolutionAmount": "the administrator's figure for the record; the refund itself moves through the ledger",
  "service_providers.hourlyRate": "a public profile rate, display and search only",
};

/** Columns that are balances in name only: display mirrors of `wallet_ledger`, never read for money. */
export const DISPLAY_MIRRORS = ["user_profiles.walletBalance", "service_providers.walletBalance"];

// Column *names* that denote an amount of money, anchored at the end so prose columns
// (`reviewFeedback`) and URL columns (`materialAdvanceReceiptUrl`) are not mistaken for money.
const MONEY = /(amount|amountminor|budget|fees?|totals?|subtotals?|costs?|rates?|balances?|prices?|advances?|quotes?)$/i;
const NUMERIC = new Set(["REAL", "INTEGER", "NUMERIC", "FLOAT", "DOUBLE"]);

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
      if (!NUMERIC.has(field.affinity)) continue; // TEXT/BLOB cannot store an amount
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
    if (column.affinity === "INTEGER") continue; // settled money, minor units — the default
    if (Object.prototype.hasOwnProperty.call(quoted, column.key)) continue;
    // A display mirror is allowed to be REAL only because it is unread outside the data layer;
    // `auditMirrorReaders` is what proves that, so the two rules are always applied together.
    if (mirrors.includes(column.key)) continue;
    problems.push(
      `${column.key}: money-shaped column stored as ${column.affinity} and not classified as quoted money`
    );
  }

  for (const key of Object.keys(quoted)) {
    if (!seen.has(key)) problems.push(`${key}: classified as quoted money but no such column exists`);
  }

  return problems;
}

/** Kotlin comments discuss these columns by name; only code that reads them matters. */
export function stripComments(source) {
  return source.replace(/\/\*[\s\S]*?\*\//g, " ").replace(/\/\/[^\n]*/g, " ");
}

/** "Display only" is a claim: nothing outside the data layer may read a display mirror. */
export function auditMirrorReaders(files, mirrors = DISPLAY_MIRRORS) {
  const problems = [];
  for (const mirror of mirrors) {
    const field = mirror.split(".")[1];
    for (const [file, text] of Object.entries(files)) {
      if (file.startsWith("app/src/main/java/com/example/data/")) continue; // the layer that owns them
      const reader = new RegExp(`\\.${field}\\b`);
      if (reader.test(stripComments(text))) problems.push(`${mirror}: read by ${file}, but it is a display mirror of the ledger`);
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
  if (!readers.some((p) => p.includes("display mirror"))) {
    throw new Error("selftest: a mirror reader outside the data layer was not reported");
  }
  if (auditMirrorReaders({ "app/src/main/java/com/example/ui/Bar.kt": "// row.walletBalance is only display" }, ["t.walletBalance"]).length !== 0) {
    throw new Error("selftest: a comment was mistaken for a reader");
  }
  if (auditMirrorReaders({ "app/src/main/java/com/example/data/db/X.kt": "row.walletBalance" }, ["t.walletBalance"]).length !== 0) {
    throw new Error("selftest: the owning data layer was refused");
  }

  console.log(
    "Money-representation selftest passed: unclassified REAL money, REAL balances, stale allowlist and stray mirror readers all refused."
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
    `Money-representation check passed: schema v${schema.version} — every money column is minor-unit ` +
      `INTEGER or classified quoted money (${Object.keys(QUOTED_MONEY).length}), both balances are ` +
      `unread display mirrors.`
  );
}
