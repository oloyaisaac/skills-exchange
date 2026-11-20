#!/usr/bin/env node
/**
 * High-confidence secret scan used by CI and locally.
 *
 * This is not a replacement for a provider-backed scanner (GitHub secret scanning); it catches the
 * credential shapes that have actually appeared in this repository: Africa's Talking keys, Google
 * API keys, GitHub tokens and private-key blocks.
 */
import fs from "node:fs";
import path from "node:path";

const ROOT = process.cwd();
const SKIP_DIRS = new Set(["node_modules", ".git", "build", ".gradle", "lib", "dist", "coverage"]);
const PATTERNS = [
  ["Africa's Talking key", /atsk_[A-Za-z0-9]{32,}/g],
  ["Google API key", /AIza[0-9A-Za-z_-]{35}/g],
  ["GitHub token", /gh[pousr]_[A-Za-z0-9]{30,}/g],
  ["Private key block", /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/g],
];
// `google-services.json` ships Firebase client configuration (its API key is a client identifier,
// not a server credential) and the two evidence dumps are Android UI captures of that same client
// key. They are allow-listed explicitly; binary artifacts are skipped by extension.
const ALLOW_FILE = /(?:^|\/)(package-lock\.json|\.env\.example|google-services\.json|ui-now-activity[^/]*\.txt)$/;
const SKIP_EXT = /\.(apk|aab|jar|png|jpe?g|webp|gif|zip|so|dex)$/i;

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!SKIP_DIRS.has(entry.name)) walk(path.join(dir, entry.name), out);
    } else {
      out.push(path.join(dir, entry.name));
    }
  }
  return out;
}

const findings = [];
for (const file of walk(ROOT)) {
  const relative = path.relative(ROOT, file).split(path.sep).join("/");
  if (ALLOW_FILE.test(relative) || SKIP_EXT.test(relative)) continue;
  let text;
  try {
    text = fs.readFileSync(file, "utf8");
  } catch {
    continue; // binary/unreadable
  }
  for (const [label, pattern] of PATTERNS) {
    pattern.lastIndex = 0;
    let match;
    while ((match = pattern.exec(text)) !== null) {
      const line = text.slice(0, match.index).split("\n").length;
      findings.push(`${relative}:${line}: ${label}`);
    }
  }
}

if (findings.length > 0) {
  console.error(`Secret scan failed (${findings.length} finding(s)):`);
  for (const finding of findings) console.error(`  - ${finding}`);
  process.exit(1);
}

console.log("Secret scan passed: no high-confidence credential patterns found.");
