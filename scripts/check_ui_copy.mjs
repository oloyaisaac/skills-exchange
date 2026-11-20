#!/usr/bin/env node
/**
 * R12-P2-01 / R12-P2-06: keep the copy translatable and the palette readable.
 *
 * Three things this refuses:
 *
 * 1. **A locale that falls behind.** Every string in `values/strings.xml` must exist in each
 *    `values-<lang>/strings.xml`. A half-translated locale silently drops to English per-key, which is
 *    how "mostly translated" ships.
 * 2. **Copy that grows back.** The UI still builds most of its sentences inline. That is a known,
 *    counted debt (the review's finding), so it is baselined: the count may fall, never rise. Any new
 *    sentence must be a resource.
 * 3. **Graphics tint used as text colour.** `Slate400` is a 3.5:1 tone: fine for a bullet or an arrow,
 *    below the 4.5:1 body floor for a sentence. The three decorative glyphs are allow-listed by name.
 *
 * `--selftest` builds fixtures in a temporary directory and proves each refusal can happen; a check
 * that cannot fail is a claim, not a check.
 */
import fs from "node:fs";
import path from "node:path";
import os from "node:os";

const RES = "app/src/main/res";
const UI = "app/src/main/java/com/example/ui";

/** Brand and product names that are the same in every language. */
const LOCALE_EXEMPT = new Set(["app_name"]);

/** The only places Slate400 may colour a Text: punctuation, not prose. */
const DECORATIVE_GLYPHS = ['"•"', '"➔"', '"→"', '"·"'];

/**
 * Sentences still built inline in the UI. R12 measured 472; nothing may raise it. Lowering it is the
 * remaining half of R12-P2-01 and the number should fall with every subsequent round.
 */
const HARDCODED_COPY_BASELINE = 472;

function readStringNames(file) {
  const text = fs.readFileSync(file, "utf8");
  const names = new Map();
  for (const match of text.matchAll(/<string\s+name="([^"]+)"\s*>([\s\S]*?)<\/string>/g)) {
    names.set(match[1], match[2].trim());
  }
  return names;
}

function localeDirs(resRoot) {
  if (!fs.existsSync(resRoot)) return [];
  return fs
    .readdirSync(resRoot, { withFileTypes: true })
    .filter((entry) => entry.isDirectory() && /^values(-|$)/.test(entry.name))
    .map((entry) => entry.name)
    .filter((name) => fs.existsSync(path.join(resRoot, name, "strings.xml")))
    .sort();
}

function countHardcodedCopy(uiRoot) {
  let total = 0;
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (entry.name.endsWith(".kt")) {
        const text = fs.readFileSync(full, "utf8");
        total += (text.match(/Text\(\s*"/g) ?? []).length;
        total += (text.match(/text = "/g) ?? []).length;
      }
    }
  };
  if (fs.existsSync(uiRoot)) walk(uiRoot);
  return total;
}

function slate400AsText(uiRoot) {
  const offenders = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith(".kt")) {
        const lines = fs.readFileSync(full, "utf8").split("\n");
        lines.forEach((line, index) => {
          if (!line.includes("color = Slate400")) return;
          if (DECORATIVE_GLYPHS.some((glyph) => line.includes(glyph))) return;
          offenders.push(`${full}:${index + 1}: ${line.trim()}`);
        });
      }
    }
  };
  if (fs.existsSync(uiRoot)) walk(uiRoot);
  return offenders;
}

/**
 * Icon-only buttons with no accessible name — the one accessibility defect that is unambiguously a
 * defect and unambiguously detectable: `IconButton { Icon(contentDescription = null) }` reads as
 * "button" to a screen reader. An icon beside a `Text` label is fine (the label is the name), which is
 * why the scan looks at the whole button block rather than at the icon's own description.
 */
function unlabelledIconButtons(uiRoot) {
  const offenders = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith(".kt")) {
        const lines = fs.readFileSync(full, "utf8").split("\n");
        lines.forEach((line, index) => {
          if (!line.includes("IconButton(")) return;
          // Wide enough for a button whose onClick is a lambda spanning a dozen lines.
          const block = lines.slice(index, index + 30).join("\n");
          const labelled = block.includes('contentDescription = "') || block.includes("Text(");
          if (!labelled) offenders.push(`${full}:${index + 1}: an IconButton has no accessible name`);
        });
      }
    }
  };
  if (fs.existsSync(uiRoot)) walk(uiRoot);
  return offenders;
}

/**
 * Icon buttons constrained below the 48dp floor. Material's `IconButton` is 48dp by default; a
 * `Modifier.size(24.dp)` on it silently shrinks the hit area to a quarter of that. The icon inside may
 * be any size — this rule looks only at the button's own size modifier.
 */
function smallIconButtons(uiRoot) {
  const offenders = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.name.endsWith(".kt")) {
        const lines = fs.readFileSync(full, "utf8").split("\n");
        lines.forEach((line, index) => {
          if (!line.includes("IconButton(")) return;
          // Only the button's own modifier counts: stop at the content lambda, so the icon's
          // size inside the button body is not mistaken for the button's size.
          const raw = lines.slice(index, index + 12).join("\n");
          const header = raw.split(") {")[0];
          const sized = header.match(/\.size\((\d+)\.dp\)/);
          if (sized && Number(sized[1]) < 48) {
            offenders.push(`${full}:${index + 1}: an IconButton is ${sized[1]}dp, below the 48dp floor`);
          }
        });
      }
    }
  };
  if (fs.existsSync(uiRoot)) walk(uiRoot);
  return offenders;
}

export function problems(root) {
  const found = [];
  const resRoot = path.join(root, RES);
  const uiRoot = path.join(root, UI);

  const baseFile = path.join(resRoot, "values", "strings.xml");
  if (!fs.existsSync(baseFile)) return ["app/src/main/res/values/strings.xml is missing"];

  const base = readStringNames(baseFile);
  const locales = localeDirs(resRoot);
  if (locales.length < 2) {
    found.push("no translated locale exists: the app targets four countries and ships English only");
  }

  for (const locale of locales) {
    if (locale === "values") continue;
    const translated = readStringNames(path.join(resRoot, locale, "strings.xml"));
    for (const name of base.keys()) {
      if (LOCALE_EXEMPT.has(name)) continue;
      if (!translated.has(name)) found.push(`${locale}: string "${name}" is not translated`);
    }
    for (const [name, value] of translated) {
      if (!base.has(name)) found.push(`${locale}: "${name}" does not exist in values/strings.xml`);
      if (!value) found.push(`${locale}: "${name}" is empty`);
    }
  }

  for (const [name, value] of base) {
    if (!value) found.push(`values: "${name}" is empty`);
  }

  const total = countHardcodedCopy(uiRoot);
  if (total > HARDCODED_COPY_BASELINE) {
    found.push(
      `hardcoded user-facing copy grew from ${HARDCODED_COPY_BASELINE} to ${total}: ` +
        `add the sentence to res/values/strings.xml instead (the remaining debt is R12-P2-01)`
    );
  }

  for (const offender of unlabelledIconButtons(uiRoot)) {
    found.push(offender);
  }

  for (const offender of smallIconButtons(uiRoot)) {
    found.push(offender);
  }

  for (const offender of slate400AsText(uiRoot)) {
    found.push(`Slate400 colours text below the 4.5:1 floor — use Slate500 (${offender})`);
  }

  return found;
}

function selftest() {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "ui-copy-"));
  const res = path.join(tmp, RES);
  const ui = path.join(tmp, UI);
  fs.mkdirSync(path.join(res, "values"), { recursive: true });
  fs.mkdirSync(path.join(res, "values-sw"), { recursive: true });
  fs.mkdirSync(ui, { recursive: true });

  const write = (target, body) => {
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, body);
  };
  const baseStrings = (extra = "") =>
    `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n  <string name="app_name">Skills Exchange</string>\n  <string name="safety_sos_panic">SOS PANIC</string>\n${extra}</resources>\n`;

  write(path.join(res, "values", "strings.xml"), baseStrings());
  write(
    path.join(res, "values-sw", "strings.xml"),
    `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n  <string name="safety_sos_panic">MSAADA WA HARAKA</string>\n</resources>\n`
  );
  write(
    path.join(ui, "Clean.kt"),
    `class Clean {\n  fun a() { Text("•", color = Slate400) }\n  fun b() { Text(stringResource(R.string.safety_sos_panic)) }\n}\n`
  );

  const cleanProblems = problems(tmp);
  if (cleanProblems.length > 0) {
    throw new Error(`selftest: a clean tree must pass, got ${JSON.stringify(cleanProblems)}`);
  }

  // 1. a locale that is missing a key
  write(
    path.join(res, "values-sw", "strings.xml"),
    `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n`
  );
  const missingKey = problems(tmp);
  if (!missingKey.some((problem) => problem.includes('"safety_sos_panic" is not translated'))) {
    throw new Error("selftest: a missing translation was not refused");
  }
  write(
    path.join(res, "values-sw", "strings.xml"),
    `<?xml version="1.0" encoding="utf-8"?>\n<resources>\n  <string name="safety_sos_panic">MSAADA WA HARAKA</string>\n</resources>\n`
  );

  // 2. copy that grows back
  write(
    path.join(ui, "Loud.kt"),
    `class Loud {\n${Array.from({ length: HARDCODED_COPY_BASELINE + 1 }, () => '  fun f() { Text("hello") }').join("\n")}\n}\n`
  );
  const grown = problems(tmp);
  if (!grown.some((problem) => problem.includes("hardcoded user-facing copy grew"))) {
    throw new Error("selftest: a new hardcoded sentence was not refused");
  }
  fs.rmSync(path.join(ui, "Loud.kt"));

  // 3. graphics tint used as text colour
  write(path.join(ui, "Tinted.kt"), `class Tinted {\n  fun f() { Text("Cancel", color = Slate400) }\n}\n`);
  const tinted = problems(tmp);
  if (!tinted.some((problem) => problem.includes("Slate400 colours text"))) {
    throw new Error("selftest: Slate400 as a text colour was not refused");
  }

  // 4. an icon-only button with no accessible name
  write(
    path.join(ui, "Unlabelled.kt"),
    `class Unlabelled {\n  fun f() {\n    IconButton(onClick = {}) {\n      Icon(Icons.Default.Close, contentDescription = null)\n    }\n  }\n}\n`
  );
  const unlabelled = problems(tmp);
  if (!unlabelled.some((problem) => problem.includes("no accessible name"))) {
    throw new Error("selftest: an unlabelled IconButton was not refused");
  }
  fs.rmSync(path.join(ui, "Unlabelled.kt"));

  // 5. an icon button smaller than the touch floor
  write(
    path.join(ui, "Tiny.kt"),
    `class Tiny {\n  fun f() {\n    IconButton(onClick = {}, modifier = Modifier.size(24.dp)) {\n      Icon(Icons.Default.Close, contentDescription = "Close")\n    }\n  }\n}\n`
  );
  const tiny = problems(tmp);
  if (!tiny.some((problem) => problem.includes("below the 48dp floor"))) {
    throw new Error("selftest: a 24dp IconButton was not refused");
  }
  fs.rmSync(path.join(ui, "Tiny.kt"));

  fs.rmSync(tmp, { recursive: true, force: true });
  console.log("UI copy guard selftest passed: clean passes; a missing translation, new hardcoded copy, a tint-as-text, an unlabelled IconButton and a 24dp IconButton are each refused.");
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  const found = problems(process.cwd());
  if (found.length > 0) {
    console.error(`UI copy check failed (${found.length} problem(s)):`);
    for (const problem of found) console.error(`  - ${problem}`);
    process.exit(1);
  }
  const resRoot = path.join(process.cwd(), RES);
  const base = readStringNames(path.join(resRoot, "values", "strings.xml"));
  const locales = localeDirs(resRoot).filter((locale) => locale !== "values");
  const total = countHardcodedCopy(path.join(process.cwd(), UI));
  console.log(
    `UI copy check passed: ${base.size} strings in ${locales.length + 1} locale(s) ` +
      `(${locales.join(", ")}), ${total}/${HARDCODED_COPY_BASELINE} inline sentences left before the baseline moves, ` +
      `no Slate400 text.`
  );
}
