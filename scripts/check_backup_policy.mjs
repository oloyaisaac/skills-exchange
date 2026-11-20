#!/usr/bin/env node
/**
 * R09 (backup evidence): no personal data may leave the device through cloud backup or device transfer.
 *
 * The reviewer's finding was that the backup rules were empty while backups were enabled. The app now
 * sets `allowBackup="false"` and excludes every domain in both rule files. Those are *files*, and files
 * drift; this guard reads the manifest and both XML policies and fails if any of them stops excluding
 * a domain that can hold personal data.
 *
 * It is a static check, not a restore test: an on-device backup/restore cycle is not run here, and
 * this script does not claim one has been.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const MANIFEST = "app/src/main/AndroidManifest.xml";
const CLOUD_RULES = "app/src/main/res/xml/backup_rules.xml";
const EXTRACTION_RULES = "app/src/main/res/xml/data_extraction_rules.xml";
/** Domains that can carry the Room database, DataStore, Firebase Auth tokens or cache. */
const REQUIRED_DOMAINS = ["root", "file", "database", "sharedpref", "external"];

const errors = [];
const read = (relative) => {
  try {
    return readFileSync(join(root, relative), "utf8");
  } catch {
    errors.push(`${relative} is missing`);
    return "";
  }
};

const manifest = read(MANIFEST);
const cloud = read(CLOUD_RULES);
const extraction = read(EXTRACTION_RULES);

if (!/android:allowBackup="false"/.test(manifest)) {
  errors.push(
    `${MANIFEST} no longer disables cloud backup (android:allowBackup="false"); the exclusion rules ` +
      "below are defence in depth, not the primary control"
  );
}

for (const domain of REQUIRED_DOMAINS) {
  const tag = new RegExp(`<exclude\\s+domain="${domain}"\\s+path="\\."\\s*/>`);
  if (!tag.test(cloud)) {
    errors.push(`${CLOUD_RULES} does not exclude domain="${domain}" path="."`);
  }
}

for (const section of ["cloud-backup", "device-transfer"]) {
  const block = extraction.match(new RegExp(`<${section}>([\\s\\S]*?)</${section}>`));
  if (!block) {
    errors.push(`${EXTRACTION_RULES} has no <${section}> section`);
    continue;
  }
  for (const domain of REQUIRED_DOMAINS) {
    const tag = new RegExp(`<exclude\\s+domain="${domain}"\\s+path="\\."\\s*/>`);
    if (!tag.test(block[1])) {
      errors.push(`${EXTRACTION_RULES} does not exclude domain="${domain}" from ${section}`);
    }
  }
}

if (errors.length > 0) {
  console.error("Backup policy guard failed:");
  for (const error of errors) console.error(`  - ${error}`);
  process.exit(1);
}

console.log(
  `Backup policy guard passed (allowBackup=false; ${REQUIRED_DOMAINS.length} domains excluded from ` +
    "backup and device transfer). Static check only: no on-device restore test is claimed."
);
