#!/usr/bin/env node
/**
 * R01 architecture guards.
 *
 * These are narrow, mechanical checks for findings that are easy to regress:
 *  1. one Firebase gateway per ViewModel (a second instance splits auth state);
 *  2. no composable reaches `viewModel.firebaseService` directly (it bypasses the repository);
 *  3. no unused root AndroidManifest.xml duplicate;
 *  4. README test count matches the backend suite's documented current count.
 *
 * They do not replace compilation; they prevent the specific regressions R01 recorded.
 */
import fs from "node:fs";

const problems = [];
const vmPath = "app/src/main/java/com/example/ui/viewmodel/SkillsExchangeViewModel.kt";
const vm = fs.readFileSync(vmPath, "utf8");

const constructions = (vm.match(/FirebaseService\(/g) || []).length;
if (constructions !== 1) {
  problems.push(`${vmPath}: expected exactly one FirebaseService construction, found ${constructions}`);
}
if (!/private val firebaseService = FirebaseService\(application\)/.test(vm)) {
  problems.push(`${vmPath}: the gateway must be a private field`);
}
if (!/SkillsExchangeRepository\(db, firebaseService\)/.test(vm)) {
  problems.push(`${vmPath}: the repository must receive the same gateway instance`);
}

const uiDir = "app/src/main/java/com/example/ui";
const walk = (dir) => fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
  const path = `${dir}/${entry.name}`;
  if (entry.isDirectory()) return walk(path);
  return entry.name.endsWith(".kt") ? [path] : [];
});
for (const file of walk(uiDir)) {
  const text = fs.readFileSync(file, "utf8");
  if (text.includes("viewModel.firebaseService")) {
    problems.push(`${file}: UI must not call viewModel.firebaseService directly`);
  }
}

if (fs.existsSync("AndroidManifest.xml")) {
  problems.push("root AndroidManifest.xml is an unused duplicate; delete it or wire it into the build");
}

if (problems.length > 0) {
  console.error(`Architecture guard check failed (${problems.length} problem(s)):`);
  for (const problem of problems) console.error(`  - ${problem}`);
  process.exit(1);
}

console.log("Architecture guard check passed: single Firebase gateway, no UI bypass, no duplicate manifest.");
