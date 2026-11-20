# AGENTS.md — Skills Exchange AI Agent Operating Manual

## Purpose
This is the permanent operating manual for every AI agent, session, or account that works on the **Skills Exchange** Android project. Read this file first on every new session. Do not skip it.

---

## 1. Source of Truth Hierarchy

| File | Purpose |
|---|---|
| `prd.json` | Master structured development plan — **do not delete, replace, or recreate** |
| `requirement_file.md` | Full detailed product requirements source |
| `AGENT_STATE.md` | Current project/agent state — read on startup, update on handoff |
| `TASKS.md` | Human-readable synchronized task status |
| `CHANGELOG.md` | Development history |
| `DECISIONS.md` | Architectural and design decisions |
| `README_AI.md` | Recovery guide for a new agent resuming the project |
| `README.md` | User-facing README — **do not modify unless explicitly instructed** |

---

## 2. PRD Integrity Rules

- `prd.json` is the **master plan**. It must always exist at the project root.
- **Never** delete, replace, rename, move, or recreate `prd.json`.
- **Never** remove existing stories to make the project look cleaner.
- **Never** mark a story as `completed` unless the implementation was verified.
- Preserve all existing story IDs (`STAB-001`, `SEC-001`, etc.).
- Preserve all existing story fields and dependencies.
- When a story is genuinely completed and verified, update its `status` field to `"completed"` and add the ID to `completed_stories[]`.
- When genuine progress occurs, update `last_updated` timestamp.

---

## 3. Project Operating Rules

### One Story at a Time
- Read the PRD.
- Identify the highest-priority story whose `depends_on` stories are all `completed`.
- Implement **only that story**.
- Verify implementation.
- Update PRD and agent state.
- Then move to the next story.

### Never Implement Without Reading
Before touching any file, read:
1. `AGENTS.md` (this file)
2. `prd.json` — identify current story
3. `requirement_file.md` — relevant sections
4. `AGENT_STATE.md` — understand where the previous session stopped
5. `TASKS.md` — cross-check task status
6. Relevant source code files named in the story

### Never Fabricate Progress
- Do not mark a story `completed` if tests failed or were skipped.
- Do not hide failures in documentation.
- If a blocker exists, document it clearly in `AGENT_STATE.md`.

---

## 4. Startup Procedure (New Session / Account)

Every new agent session MUST:

1. Read `AGENTS.md` (this file) completely.
2. Read `prd.json` completely.
3. Read `requirement_file.md` — at minimum the relevant section for the current story.
4. Read `AGENT_STATE.md` — identify last completed action, current story, known blockers.
5. Read `TASKS.md` — verify synchronized task status.
6. Read recent entries in `CHANGELOG.md`.
7. Read relevant entries in `DECISIONS.md`.
8. **Verify** the actual source code matches what previous agent sessions documented.
9. Identify the next unfinished eligible PRD story.
10. Continue from there — do NOT restart the project.

---

## 5. PRD-Driven Development Loop

```
READ AGENT_STATE
        ↓
READ prd.json
        ↓
IDENTIFY NEXT UNFINISHED ELIGIBLE STORY
        ↓
READ requirement_file.md (relevant section)
        ↓
INSPECT EXISTING SOURCE CODE
        ↓
DEFINE SMALLEST NEXT CHANGE
        ↓
IMPLEMENT
        ↓
TEST / VERIFY
        ↓
FIX FAILURES
        ↓
UPDATE prd.json (story status)
        ↓
UPDATE AGENT_STATE.md
        ↓
UPDATE TASKS.md
        ↓
UPDATE CHANGELOG.md
        ↓
RECORD DECISIONS (if applicable)
        ↓
CHECK FOR REGRESSIONS
        ↓
SELECT NEXT STORY → REPEAT
```

Stop only when:
- All relevant PRD stories are complete, OR
- A genuine blocker prevents further progress (document it).

---

## 6. Account / Session Handoff Procedure

### When the Current Session Needs to Stop (Quota or Other Limit)

1. Stop at a safe, documented point — never mid-implementation.
2. Do not leave undocumented partial work.
3. Run available verification.
4. Record all completed and unfinished work.
5. Update `AGENT_STATE.md` — especially the `## Account / Session Handoff` section.
6. Update `prd.json` — mark stories that are genuinely complete.
7. Update `TASKS.md`.
8. Update `CHANGELOG.md`.
9. Update `DECISIONS.md` if architectural decisions were made.
10. Clearly state the next action so the next agent can resume immediately.

### What the Next Session Must Do

1. Read all agent memory files (this file, `AGENT_STATE.md`, `TASKS.md`, `CHANGELOG.md`).
2. Verify the actual source code matches documented state.
3. Identify the next eligible PRD story.
4. Continue from the documented next action.
5. Do NOT restart the project from scratch.

---

## 7. Account Switching / Quota Continuity

The project supports legitimate handoff between authorized AI accounts or sessions when quota is exhausted or a session must stop.

### Purpose
Allow development continuity across AI session boundaries without losing progress.

### This is NOT for:
- Bypassing platform authentication
- Defeating access controls
- Evading account restrictions
- Circumventing platform security
- Manipulating quota counters
- Falsifying account identity
- Transferring session cookies or tokens
- Creating unauthorized accounts

### This IS for:
- Persisting project state on the filesystem so any authorized agent can resume
- Documenting where work stopped
- Enabling a new authorized session to continue without conversation memory

### How It Works
The filesystem IS the persistent memory:
- `prd.json` — what is planned and what is done
- `AGENT_STATE.md` — where the last agent stopped
- `TASKS.md` — current task status
- `CHANGELOG.md` — history of what changed
- Source code — the actual implementation

A new authorized agent reads these files and continues. No session token, cookie, or credential transfer is involved or permitted.

---

## 8. Security Rules

- **Never** store passwords, API keys, session tokens, cookies, or authentication secrets in `AGENT_STATE.md`, `AGENTS.md`, `CHANGELOG.md`, `DECISIONS.md`, `TASKS.md`, or `README_AI.md`.
- Credentials referenced in the project (e.g., `GEMINI_API_KEY`) are managed via `.env` (gitignored) and must never be committed.
- The `.env.example` file contains only placeholder values — this is safe.
- Firebase credentials, MoMo API keys, and signing configs use environment variables only.
- Do not hardcode credentials in source code.

---

## 9. Verification Requirements

After implementing any story:
- Run the story's `verification` commands as listed in `prd.json`.
- Run the existing test suite where applicable.
- Do not mark complete if tests fail.
- Document test results in `AGENT_STATE.md`.

### Standard Verification Commands
```bash
# Unit tests (Robolectric)
./gradlew testDebugUnitTest

# Specific test
./gradlew testDebugUnitTest --tests com.example.ExampleRobolectricTest

# Screenshot tests
./gradlew testDebugUnitTest --tests com.example.GreetingScreenshotTest

# Build check
./gradlew assembleDebug
```

> **Note (verified 2026-09-27)**: **No environment setup is required.** `gradle.properties` already pins the build JVM — `org.gradle.java.home=D:/kilo_downloads/jdk17/jdk17.0.20_10` (Amazon Corretto `17.0.20.1`) — and the Gradle home (`systemProp.org.gradle.user.home=D:/gradle-home`). `org.gradle.java.home` outranks `JAVA_HOME`, so `java` does not need to be on `PATH`; on Windows just run `.\gradlew.bat <task> --console=plain`. The repo-root `jdk17/` folder is empty and referenced by nothing; `jdk11/jdk-11.0.22+7/bin/java.exe` exists and is supplied as the JDK 11 toolchain through `org.gradle.java.installations.paths`; Android Studio's JetBrains Runtime (OpenJDK `25.0.2`) is a working fallback. **Never record "no JDK on this machine" as a blocker without checking `gradle.properties` first** — that false blocker once left a finished story unverified (DECISIONS.md D-2026-09-27-1). Expect the daemon to print nothing for minutes at a time during Kotlin compile and APK packaging; poll `app\build\` timestamps rather than assuming a hang.

---

## 10. Failure Recovery

If an implementation fails:
1. Record the failure in `AGENT_STATE.md`.
2. Record the error message and cause.
3. Preserve any useful partial work.
4. Attempt corrective action.
5. Re-verify.
6. If unresolvable, document as BLOCKER with: what was attempted, why it failed, what is needed.

A failed task must NEVER silently appear as completed.

---

## 11. Documentation Synchronization Rules

Every time meaningful progress is made:

| File | Action |
|---|---|
| `prd.json` | Update story `status` and `completed_stories` |
| `AGENT_STATE.md` | Update current state, last action, next action |
| `TASKS.md` | Synchronize human-readable task list |
| `CHANGELOG.md` | Add entry for meaningful changes |
| `DECISIONS.md` | Record architectural decisions |

Documentation must describe the actual implementation — never use it to pretend unfinished work is finished.

---

## 12. Do Not Overengineer

- Do not introduce unnecessary dependencies.
- Do not rewrite working architecture.
- Do not migrate frameworks.
- Do not refactor unrelated modules.
- Implement the smallest reliable solution that fits the existing architecture.
- Every change must trace to a specific PRD story.

---

## Project Architecture Quick Reference

| Layer | Path |
|---|---|
| Android App | `app/src/main/java/com/example/` |
| UI Screens | `app/src/main/java/com/example/ui/screens/` |
| UI Components | `app/src/main/java/com/example/ui/components/` |
| ViewModel | `app/src/main/java/com/example/ui/viewmodel/` |
| Data (DB) | `app/src/main/java/com/example/data/db/` |
| Remote Services | `app/src/main/java/com/example/data/remote/` |
| Repository | `app/src/main/java/com/example/data/repository/` |
| Utilities | `app/src/main/java/com/example/util/` |
| Cloud Functions | `functions/src/index.ts` |
| Firestore Rules | `firestore.rules` |
| Unit Tests | `app/src/test/java/com/example/` |
| Android Tests | `app/src/androidTest/java/com/example/` |

---

_Last updated: 2026-08-25_
