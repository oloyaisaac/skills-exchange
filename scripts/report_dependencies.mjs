#!/usr/bin/env node
/**
 * R14-P2-06: a supply chain you can name, not one you hope about.
 *
 * The review's finding was that nothing in this repository could answer three ordinary questions:
 * what am I shipping, under what licence, and does anything in it have a known vulnerability? The
 * answers now come from the installed tree, not from a hand-written list:
 *
 *   - **SBOM** — every production package with a version and a package URL, written as CycloneDX 1.5
 *     JSON (`dependency-report.json`), so a downstream scanner can consume it.
 *   - **Licence inventory** — grouped by licence, from the `license` field npm records.
 *   - **Gate** — a production dependency under GPL/AGPL/SSPL, or one whose licence is missing, fails
 *     the build. Unknown is treated as a failure on purpose: a licence nobody recorded is not a licence
 *     anybody reviewed. Permissive families (MIT, Apache-2.0, BSD, ISC, 0BSD, Unlicense, CC0) pass.
 *
 * Vulnerability scanning is `npm audit` and stays there: it needs the registry's advisory feed, so it
 * runs in CI (`.github/workflows/ci.yml`, `--audit-level=high`) rather than inside this offline script.
 *
 * Usage: `node scripts/report_dependencies.mjs [--out dependency-report.json] [--selftest]`
 */
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";

/** The packages that actually ship. `rules-test` is a test harness and is reported but not gated. */
const SHIPPED = [{ dir: "functions", gated: true, label: "cloud functions" }];
const DEV_ONLY = [{ dir: "rules-test", gated: false, label: "rules test harness" }];

export const DENIED_LICENSES = ["GPL-2.0", "GPL-3.0", "AGPL-3.0", "SSPL-1.0", "CC-BY-NC-4.0", "EUPL-1.2"];

/**
 * Flattens `npm ls --json --long` into one entry per (name, version), which is what an SBOM is.
 * A node with no version is declared but not installed (an unmet optional dependency); it is not part
 * of the shipped tree, so it is counted separately instead of being reported as "unknown@unknown".
 */
export function flatten(tree, found = new Map(), unresolved = []) {
  for (const [name, node] of Object.entries(tree.dependencies || {})) {
    if (!node.version) {
      unresolved.push(name);
      flatten(node, found, unresolved);
      continue;
    }
    const key = `${name}@${node.version}`;
    if (!found.has(key)) {
      found.set(key, {
        name,
        version: node.version,
        license: node.license || (Array.isArray(node.licenses) ? node.licenses.map((l) => l.type).join(" OR ") : ""),
        resolved: node.resolved || "",
        path: node.path || "",
      });
    }
    flatten(node, found, unresolved);
  }
  return { components: [...found.values()], unresolved };
}

/** A licence string can be an SPDX expression; only the outermost token decides the gate. */
export function licenseTokens(license) {
  if (!license) return [];
  return String(license)
    .replace(/[()]/g, " ")
    .split(/\s+(?:OR|AND|WITH)\s+|,\s*|\s+/i)
    .map((token) => token.trim().toUpperCase())
    // SPDX allows `-only` / `-or-later` / `+` suffixes on the same licence identity.
    .map((token) => token.replace(/-ONLY$|-OR-LATER$|\+$/, ""))
    .filter(Boolean);
}

/**
 * True when a single SPDX token names a licence we cannot ship.
 *
 * The comparison is on the licence identity, so `GPL-3.0-only` and `GPL-3.0` are the same refusal.
 */
export function isDeniedToken(token) {
  const identity = String(token).trim().toUpperCase().replace(/-ONLY$|-OR-LATER$|\+$/, "");
  return DENIED_LICENSES.some((bad) => bad.toUpperCase() === identity);
}

/**
 * A dual licence is a choice, not a prohibition: `(BSD-3-Clause OR GPL-2.0)` may be used under BSD.
 * So an `OR` expression is denied only when *every* alternative is denied, and an `AND` expression
 * when *any* part is. A missing licence is denied.
 */
export function deniedReason(license) {
  if (!license) return "no licence recorded";
  const text = String(license).trim();
  if (/^SEE LICEN[CS]E IN /i.test(text)) {
    return "licence is a file reference (SEE LICENSE IN ...), not a licence";
  }

  const alternatives = text.replace(/[()]/g, " ").split(/\s+OR\s+/i).map((part) => part.trim()).filter(Boolean);
  if (alternatives.length > 1) {
    const allDenied = alternatives.every((alternative) =>
      alternative
        .split(/\s+AND\s+/i)
        .map((part) => part.trim())
        .some((part) => isDeniedToken(part))
    );
    return allDenied ? `every alternative is copyleft (${text})` : null;
  }

  const tokens = text
    .replace(/[()]/g, " ")
    .split(/\s+AND\s+|,\s*|\s+/i)
    .map((token) => token.trim())
    .filter(Boolean);
  if (tokens.length === 0) return "no licence recorded";
  const denied = tokens.find((token) => isDeniedToken(token));
  return denied ? `copyleft licence ${denied}` : null;
}

/**
 * Last resort when npm metadata has no licence but the package shipped a licence file: read it and
 * classify. Denied markers are checked first, so a file mentioning GPL anywhere is refused; the
 * permalink in the report records that the licence came from the file, not from metadata.
 */
export function licenseFromFile(packageDir) {
  if (!packageDir) return null;
  let entries;
  try {
    entries = fs.readdirSync(packageDir);
  } catch {
    return null;
  }
  const file = entries.find((entry) => /^(licen[cs]e|copying)(\.(md|txt|markdown))?$/i.test(entry));
  if (!file) return null;
  let text;
  try {
    text = fs.readFileSync(path.join(packageDir, file), "utf8").slice(0, 20000);
  } catch {
    return null;
  }
  if (/GNU (AFFERO )?GENERAL PUBLIC LICENSE|Server Side Public License|Mozilla Public License/i.test(text)) return null;
  if (/Permission is hereby granted, free of charge/i.test(text)) return "MIT (LICENSE file)";
  if (/Apache License,?\s*Version 2\.0/i.test(text)) return "Apache-2.0 (LICENSE file)";
  if (/Redistribution and use in source and binary forms/i.test(text)) return "BSD (LICENSE file)";
  return null;
}

/** Decides one component, falling back to its licence file, and reports where the answer came from. */
export function assessComponent(component) {
  const reason = deniedReason(component.license);
  if (!reason) return { ok: true, license: component.license, source: "npm metadata" };
  if (/no licence recorded/i.test(reason) || /file reference/i.test(reason)) {
    const fromFile = licenseFromFile(component.path);
    if (fromFile) {
      const stillDenied = deniedReason(fromFile.split(" ")[0]);
      if (!stillDenied) return { ok: true, license: fromFile, source: "LICENSE file" };
    }
  }
  return { ok: false, license: component.license || "(none)", source: "npm metadata", reason };
}

export function cycloneDx(components, toolVersion) {
  return {
    bomFormat: "CycloneDX",
    specVersion: "1.5",
    version: 1,
    metadata: {
      timestamp: new Date().toISOString(),
      tools: [{ vendor: "skills-exchange", name: "report_dependencies.mjs", version: toolVersion }],
      component: { type: "application", name: "skills-exchange-backend" },
    },
    components: components.map((component) => ({
      type: "library",
      name: component.name,
      version: component.version,
      purl: `pkg:npm/${component.name.replace("@", "%40")}@${component.version}`,
      licenses: component.license ? [{ license: { id: component.license } }] : [],
    })),
  };
}

function npmTree(dir, productionOnly) {
  const out = execFileSync("npm", ["ls", "--all", "--json", "--long", ...(productionOnly ? ["--omit=dev"] : [])], {
    cwd: dir,
    encoding: "utf8",
    stdio: ["ignore", "pipe", "ignore"],
    maxBuffer: 64 * 1024 * 1024,
  });
  return JSON.parse(out);
}

function selftest() {
  const cases = [
    ["MIT", false],
    ["Apache-2.0", false],
    ["ISC", false],
    ["BSD-3-Clause", false],
    ["(MIT OR Apache-2.0)", false],
    ["GPL-3.0", true],
    ["AGPL-3.0-only", true],
    // A dual licence is a choice: MIT is available, so this ships.
    ["(MIT OR GPL-3.0)", false],
    ["SSPL-1.0", true],
    ["", true],
    [undefined, true],
    ["SEE LICENSE IN LICENSE.md", true],
    ["(BSD-3-Clause OR GPL-2.0)", false],
    ["(GPL-2.0 OR GPL-3.0)", true],
    ["(MIT AND GPL-3.0)", true],
  ];
  for (const [license, expected] of cases) {
    const got = deniedReason(license) !== null;
    if (got !== expected) {
      throw new Error(`selftest: ${JSON.stringify(license)} classified as denied=${got}, expected ${expected}`);
    }
  }
  const flat = flatten({
    dependencies: {
      a: { version: "1.0.0", license: "MIT", dependencies: { b: { version: "2.0.0", license: "Apache-2.0" } } },
      b: { version: "2.0.0", license: "Apache-2.0" },
      ghost: { missing: true, dependencies: {} },
    },
  });
  if (flat.components.length !== 2) {
    throw new Error(`selftest: flatten produced ${flat.components.length} components, expected 2`);
  }
  if (flat.unresolved.length !== 1) {
    throw new Error(`selftest: an uninstalled optional dependency was not counted separately`);
  }
  const bom = cycloneDx(flat.components, "selftest");
  if (bom.components[0].purl !== "pkg:npm/a@1.0.0") {
    throw new Error(`selftest: purl shape is ${bom.components[0].purl}`);
  }
  console.log(`Supply-chain selftest passed: ${cases.length} licence classifications, flattening and the SBOM shape.`);
}

if (process.argv.includes("--selftest")) {
  selftest();
} else {
  const outIndex = process.argv.indexOf("--out");
  const outFile = outIndex === -1 ? "dependency-report.json" : process.argv[outIndex + 1];

  const sections = [];
  const problems = [];
  for (const entry of [...SHIPPED, ...DEV_ONLY]) {
    const dir = path.join(process.cwd(), entry.dir);
    if (!fs.existsSync(path.join(dir, "node_modules"))) {
      if (entry.gated) problems.push(`${entry.dir}: node_modules is missing — run \`npm ci\` first`);
      continue;
    }
    const { components, unresolved } = flatten(npmTree(dir, entry.gated));
    const licenses = {};
    let fromFiles = 0;
    for (const component of components) {
      if (entry.gated) {
        const verdict = assessComponent(component);
        if (!verdict.ok) {
          problems.push(`${entry.dir}: ${component.name}@${component.version} — ${verdict.reason}`);
          continue;
        }
        if (verdict.source === "LICENSE file") fromFiles += 1;
        component.license = verdict.license;
      }
      const key = component.license || "(none recorded)";
      licenses[key] = (licenses[key] || 0) + 1;
    }
    sections.push({ ...entry, components, licenses, unresolved, fromFiles });
  }

  const shipped = sections.filter((section) => section.gated).flatMap((section) => section.components);
  const report = {
    generatedAt: new Date().toISOString(),
    packages: sections.map((section) => ({
      dir: section.dir,
      label: section.label,
      gated: section.gated,
      productionComponents: section.components.length,
      unresolvedOptional: section.unresolved || [],
      licensesFromFiles: section.fromFiles || 0,
      licenses: section.licenses,
    })),
    sbom: cycloneDx(shipped, "1"),
  };
  fs.writeFileSync(outFile, `${JSON.stringify(report, null, 2)}\n`);

  for (const section of sections) {
    const top = Object.entries(section.licenses)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 6)
      .map(([license, count]) => `${license} ${count}`)
      .join(", ");
    console.log(
      `${section.dir} (${section.label}${section.gated ? ", gated" : ", not gated"}): ` +
        `${section.components.length} components — ${top}` +
        (section.fromFiles ? ` (${section.fromFiles} licence(s) read from the package's LICENSE file)` : "")
    );
  }

  if (problems.length > 0) {
    console.error(`Supply-chain check failed (${problems.length} problem(s)):`);
    for (const problem of problems) console.error(`  - ${problem}`);
    process.exit(1);
  }
  console.log(`Supply-chain check passed: SBOM written to ${outFile} (${shipped.length} production components).`);
}
