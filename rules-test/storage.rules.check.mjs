/**
 * R07: structural check of `storage.rules`.
 *
 * Storage rules cannot be executed by the JavaScript rules-unit-testing library, and the Storage
 * emulator is not part of this job, so this is deliberately described as a *structural* check rather
 * than a test: it fails when the shape of the policy changes in a dangerous way (a new world-readable
 * prefix, a private prefix losing its owner check, an unlimited upload).
 *
 * The Firestore rules — where the money, escrow and erasure decisions live — are executed for real in
 * `firestore.rules.test.mjs`.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, "..", "storage.rules"), "utf8");

const failures = [];
const check = (condition, message) => {
  if (!condition) failures.push(message);
};

// 1. Split the file into match blocks by their header lines.
const headerLines = source
  .split("\n")
  .filter((line) => line.includes("match /") && !line.includes("match /b/{bucket}/o"));

const blocks = headerLines.map((line) => {
  const header = line.trim();
  const start = source.indexOf(line);
  const open = source.lastIndexOf("{", source.indexOf("\n", start));
  let depth = 0;
  let index = open;
  let body = "";
  while (index < source.length) {
    const char = source[index];
    if (char === "{") depth += 1;
    if (char === "}") {
      depth -= 1;
      if (depth === 0) {
        body += char;
        break;
      }
    }
    body += char;
    index += 1;
  }
  return { header, body };
});

check(blocks.length >= 6, `expected several storage blocks, found ${blocks.length}`);

// 2. Only `public/` may be readable anonymously.
for (const { header, body } of blocks) {
  if (body.includes("allow read: if true")) {
    check(header.includes("match /public/"), `world-readable storage block: ${header}`);
  }
}

// 3. Identity material is owner-or-admin only, and never world-readable.
for (const prefix of ["kyc", "guarantors", "certificates", "verification"]) {
  const block = blocks.find((entry) => entry.header.includes(`/users/{userId}/${prefix}/`));
  check(Boolean(block), `missing private prefix rule: ${prefix}`);
  if (block) {
    check(/allow read: if isOwner\(userId\) \|\| isAdmin\(\)/.test(block.body), `${prefix} read must be owner-or-admin`);
    check(!block.body.includes("allow read: if true"), `${prefix} must not be public`);
    check(!/allow write: if isAuthenticated\(\)/.test(block.body), `${prefix} write must not be any session`);
  }
}

// 4. Job evidence is participant-scoped, immutable and size/content-type bounded.
const jobs = blocks.find((entry) => entry.header.includes("match /jobs/{jobId}/"));
check(Boolean(jobs), "missing job evidence rule");
if (jobs) {
  check(jobs.body.includes("isJobParticipant(jobId)"), "job evidence must be participant-scoped");
  check(jobs.body.includes("allow update: if false"), "job evidence must be immutable");
  check(/request\.resource\.size < 10 \* 1024 \* 1024/.test(jobs.body), "job uploads must be size bounded");
  check(/image\//.test(jobs.body), "job uploads must be image-only");
}

// 5. Avatars: R07-P2-07 closed the anonymous read; uploads stay owner-bound and image-only.
for (const header of ["match /avatars/{userId}/{allPaths=**}", "match /users/{userId}/avatars/{allPaths=**}"] ) {
  const block = blocks.find((entry) => entry.header.includes(header));
  check(Boolean(block), `missing avatar rule: ${header}`);
  if (block) {
    check(!block.body.includes("allow read: if true"), `${header} must not be world-readable`);
    check(block.body.includes("allow read: if isAuthenticated()"), `${header} read must require a session`);
    check(/request\.resource\.contentType\.matches\('image\/\.\*'\)/.test(block.body), `${header} uploads must be images`);
  }
}

// 6. No block may grant an authenticated stranger blanket write.
check(!/allow write: if isAuthenticated\(\);/.test(source), "no block may allow any session to write");

if (failures.length > 0) {
  console.error("Storage rules check failed:");
  for (const failure of failures) console.error(` - ${failure}`);
  process.exit(1);
}
console.log(`Storage rules structural check passed: ${blocks.length} blocks, ${headerLines.length} headers.`);
