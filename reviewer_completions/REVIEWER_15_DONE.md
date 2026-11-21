# REVIEWER_15 — Documentation, Status Accuracy & Traceability: DONE

I finished the work for this reviewer. This was the last of the fifteen rounds.

- Review: `review_results/prompt2/REVIEW_15.md` (verdict FAIL, 6 findings, written against `aa4a0bc`).
  Standard: `reviewers/DOCUMENTATION_REVIEWER.md`.
- Branch: `arena/9287401d-skills-exchange`, based on `main` @ `39d9043`.
- Two claims had gone stale by the time it was implemented; four were live, and one was **worse** than
  reported: `prd.json` carried three status spellings, not two.

## Findings

| ID | Severity | Outcome against this code |
|---|---|---|
| R15-P2-01 | HIGH | **Fixed.** The finding was exact: 112 stories sat at `completed` on `verified_by: code_inspection`, and the cycle had just watched that same claim fail for escrow release (R02), account deletion (R06), the doorstep PIN (R07), the offline queue (R13) and reports (R10). All 112 are now **`UNVERIFIED`**, each carrying the reason in a `verification_note` — "marked completed by an earlier agent… never re-verified… so code inspection is not evidence here and the story is not claimed done". The 25 remediation stories that have a round record stay `DONE` and cite it. The completion count moved 137 → 25; that drop is the correction. |
| R15-P2-02 | HIGH | **Fixed, at the level stories actually have.** Mapping 185 stories to 185 requirement rows would create a second backlog nobody maintains, so `docs/traceability.md` gained a coverage table over the **27 story areas** — story count, the requirement rows that cover each area, or a stated reason it is a gap — and `scripts/check_story_coverage.mjs` fails on a missing area, a drifted count, a requirement id that no row defines, or an empty reason. Three new requirement rows were added where code and tests already existed without one (chat `REQ-COMM-01`, disputes `REQ-DISP-01`, map fallback `REQ-MAP-01`). |
| R15-P2-03 | HIGH | **Fixed.** `AGENTS.md` claimed "No environment setup is required", pinned a `D:/` JDK path that R01 had deleted, and told agents **never to record "no JDK on this machine" as a blocker** — advice that is right on a machine with a pinned JDK and false in an environment with none. `README_AI.md` sent readers to `d:\skills exchange\`. `README.md` described a `gradle.properties` that no longer exists. All three now state the truth: no local JDK or Android SDK, GitHub Actions is the compiler of record, and a missing JDK **is** a reportable blocker. `scripts/check_onboarding_docs.mjs` refuses a Windows absolute path, the setup claim, or an `AGENTS.md` that stops naming CI. |
| R15-P2-04 | MEDIUM | **Mostly stale, remaining part fixed.** `AGENT_STATE.md` now carries every Prompt-4 round (R01–R15), and the README's "111 tests" is gone. What the review also asked for — decision records for this cycle — did not exist, and now does: D-2026-11-20-13 (one vocabulary, `DONE` only through evidence) and D-2026-11-20-14 (unverified is a first-class outcome; CI is the compiler of record), with the stale D-2026-09-27-1 marked superseded. |
| R15-P2-05 | MEDIUM | **Fixed, and it was worse than reported.** Three spellings were counted; the file had `completed` (112), `COMPLETED` (16), `DONE` (9), plus `IN_PROGRESS`, `VERIFICATION_PENDING` and `NOT_STARTED` — six in total. One vocabulary now, machine-enforced by `scripts/check_story_status.mjs`: a legacy spelling fails, `DONE` without an `evidence:` note fails, an `evidence:` note too short to be a record fails, and `UNVERIFIED` without a reason fails. |
| R15-P2-06 | LOW | **Fixed.** The root held 22 scratch files: `fix*.ps1`, `inspect*.ps1`, `update_*.py`, `repro.bat`, `run-gradle.bat`, a stray root `package-lock.json` with no `package.json`, two audit dumps, and `dependency-report.json` — which this repository's own supply-chain script had written and a later `git add -A` swept in. The scripts are deleted, the dumps moved to `review_results/archive/` (listed in the document map), the artefact is ignored, and `check_repo_hygiene.mjs` now refuses a root file that is neither a Markdown document nor a known build/config file. |

## Evidence

- CI on `60d47d5`: **8 checks green** — Backend build and tests (318), Backend handler tests, Firestore
  rules, Supply chain, Documentation checks (**now including the three new guards**), Secret scan,
  Compile and unit tests (494 + lint gate), Release build with R8.
- `check_story_status.mjs`: 185 stories, one vocabulary — DONE 28, IN_PROGRESS 27,
  VERIFICATION_PENDING 10, NOT_STARTED 8, **UNVERIFIED 112**; every DONE carries evidence, every
  UNVERIFIED carries a reason.
- `check_story_coverage.mjs`: 185 stories across 27 areas, each with a count and either requirement rows
  or a stated reason.
- `check_onboarding_docs.mjs`: three documents describe the real environment.
- Guards: **19 static checks, 10 with `--selftest`**; 32 documents; 49 requirements.
- Tracked files 452 → 439 across R14–R15, all of the reduction scratch or artefact.

## Not claimed

- **No runtime claim has become verifiable.** There is still no deployment, no device, no Firebase
  project and no provider sandbox; every SLO, provider behaviour and rollback step remains as R13/R14
  left it — specified or written, not executed.
- `UNVERIFIED` is not "probably fine". It means an earlier agent asserted completion and this programme
  did not check; re-promoting a story to `DONE` requires writing evidence, which is exactly the work
  R15 declined to fabricate.
- The coverage table proves every story area is *accounted for*, not that every story is *correct*: the
  requirement rows behind it inherit whatever their own status says.
- Two requirements still have no automated test (`REQ-LOC-01`, `REQ-TRAIN-01`) and the matrix says so.
