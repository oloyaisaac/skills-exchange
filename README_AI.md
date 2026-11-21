# README_AI.md — Skills Exchange: AI Agent Recovery & Continuation Guide

## Purpose
This guide enables any authorized AI agent to recover and continue development on the **Skills Exchange** project after a session change, account change, or conversation loss. It is written for the AI, not for humans.

---

## Critical First Rule
**Do NOT restart this project.** Development is already in progress. Read this guide fully before taking any action.

---

## Step 1 — Read These Files First (In Order)

```
1. AGENTS.md              ← Operating rules (required reading)
2. prd.json               ← Master plan and story backlog
3. requirement_file.md    ← Full product requirements
4. AGENT_STATE.md         ← Where the last agent stopped
5. TASKS.md               ← Human-readable task status
6. CHANGELOG.md           ← What has changed and when
7. DECISIONS.md           ← Architectural decisions already made
```

Do NOT take any action until you have read all seven files.

---

## Step 2 — Understand the Project

**Skills Exchange** is a hyperlocal Android marketplace connecting clients with verified local service providers in East African urban centers (Uganda, Kenya, Tanzania, Rwanda).

### Tech Stack
| Layer | Technology |
|---|---|
| Mobile app | Kotlin + Jetpack Compose (Android) |
| Local database | Room (SQLite) |
| Backend | Firebase (Firestore, Auth, Storage, Cloud Functions) |
| Payments | MTN MoMo + Airtel Money APIs |
| AI services | Gemini 2.0 Flash |
| Maps | Google Maps Android SDK (planned) |
| Tests | Robolectric + Roborazzi |
| Build | Gradle (Kotlin DSL) + JDK 11 |

### Project Root Structure
```
skills-exchange/
├── prd.json                    ← Master plan (READ ONLY — preserve structure)
├── requirement_file.md         ← Full requirements (READ ONLY)
├── AGENTS.md                   ← This operating manual
├── AGENT_STATE.md              ← Current state
├── CHANGELOG.md                ← History
├── DECISIONS.md                ← Design decisions
├── TASKS.md                    ← Task status
├── README_AI.md                ← This file
├── README.md                   ← User README (do not modify)
├── firestore.rules             ← Firebase security rules
├── app/                        ← Android application
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/
│       │   ├── data/db/        ← Room entities, DAOs
│       │   ├── data/remote/    ← Firebase, MoMo, Gemini services
│       │   ├── data/repository/← SkillsExchangeRepository
│       │   ├── ui/components/  ← Composable UI components
│       │   ├── ui/screens/     ← Screen composables
│       │   ├── ui/theme/       ← SkillsExchangeTheme
│       │   ├── ui/viewmodel/   ← SkillsExchangeViewModel
│       │   └── util/           ← LocationHelper, AudioRecorderHelper, etc.
│       └── test/java/com/example/
│           ├── ExampleRobolectricTest.kt
│           ├── GreetingScreenshotTest.kt
│           └── ExampleUnitTest.kt
├── functions/src/index.ts      ← Firebase Cloud Functions
├── .env                        ← Credentials (gitignored — do NOT commit)
└── .env.example                ← Placeholder values (safe to commit)
```

---

## Step 3 — Verify Actual State Matches Documentation

After reading the documentation, **inspect the actual source code** to verify.

### Check These Key Files

**STAB-001 (should be complete):**
```
app/src/test/java/com/example/ExampleRobolectricTest.kt
→ Should assert assertEquals("Skills Exchange", appName)
```

**STAB-002 (should be complete):**
```
app/src/test/java/com/example/GreetingScreenshotTest.kt
→ Should import com.example.ui.theme.SkillsExchangeTheme
→ Should NOT reference MyApplicationTheme or Greeting()
```

**PRD status (verify):**
```
prd.json → stories[] → STAB-001.status should be "completed"
prd.json → stories[] → STAB-002.status should be "completed"
prd.json → completed_stories[] should contain ["STAB-001", "STAB-002"]
```

If documentation and code do not match — trust the code, update documentation.

---

## Step 4 — Identify the Next Eligible Story

Using the dependency graph in `prd.json`:

1. Find all stories with `status: "pending"` or `status: "in_progress"`.
2. For each, check that all `depends_on` story IDs are in `completed_stories`.
3. Among eligible stories, prioritize by `priority` (P0 > P1 > P2 > P3).
4. That is your next story.

### Current Status & Next Priorities (as of 2026-10-15)

All 93 baseline PRD stories are tracked in `prd.json`.
The project is currently addressing findings from the **2026-10-15 Multi-Perspective Platform Audit** (see `AUDIT_THREE_PERSONAS.md`, `requirement_file.md` §15, and `TASKS.md`):

1. **Phase 1: Close Worker & Client Operational Loops (Mobile App)**
   - `AUDIT-W-QUOTE` (P0): Quote Proposal Modal Composable wired to `SkillsExchangeViewModel.submitQuote`.
   - `AUDIT-C-CANCEL` (P0): Self-service "Cancel Job" dialog in `JobPipelineKanban` and `MainScreen` with escrow refund.
   - `AUDIT-W-CASHOUT` (P0): "Withdraw to MoMo" button in `EscrowWalletSheet`.
   - `AUDIT-C-PHOTO` & `AUDIT-C-SCHEDULE` (P1): Photo attachment picker and appointment date/time picker in `QuickRequestDrawer`.
   - `AUDIT-W-NAV` (P1): Turn-by-turn navigation intent on active job cards.

2. **Phase 2: Operations & Admin Console (Internal / Admin Portal)**
   - `AUDIT-A-CONSOLE` (P0): Admin verification queue UI for KYC IDs and guarantor photos.
   - `AUDIT-A-ARBITRATE` (P0): Dispute arbitration console with evidence viewer.
   - `AUDIT-A-SOS` (P1): Live SOS emergency monitoring desk.


---

## Step 5 — Run the PRD Development Loop

```
READ AGENT_STATE → READ prd.json → IDENTIFY NEXT STORY
→ READ requirements → INSPECT CODE
→ IMPLEMENT → TEST → FIX → VERIFY
→ UPDATE prd.json → UPDATE AGENT_STATE → UPDATE TASKS → UPDATE CHANGELOG
→ REPEAT
```

Never skip verification before marking a story complete.

---

## Account / Session Continuity

If you are a new agent session taking over:
- Read `AGENT_STATE.md` → `## Account / Session Handoff` section
- That section describes exactly where the previous session stopped
- Continue from there — do not restart

If you are stopping due to quota or session limits:
1. Stop at a clean point
2. Update `AGENT_STATE.md` → `## Account / Session Handoff` section
3. Update `prd.json`, `TASKS.md`, `CHANGELOG.md`
4. Identify and document the next action

---

## Security Rules (Mandatory)

- Never store passwords, API keys, tokens, or credentials in any agent file
- Credentials are in `.env` (gitignored)
- Do not commit `.env`
- Do not read `.env` values and write them anywhere else
- Firebase credentials and signing keys come from environment variables only

---

## Common Verification Commands

```powershell
# Windows — from project root (skills-exchange/)

# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test
./gradlew testDebugUnitTest --tests com.example.ExampleRobolectricTest

# Run screenshot test
./gradlew testDebugUnitTest --tests com.example.GreetingScreenshotTest

# Build debug APK
./gradlew assembleDebug

# Note: JDK must be configured. Project bundles jdk11/ and jdk17/.
# Set JAVA_HOME=skills-exchange/jdk11 or jdk17 before running.
```

---

## What Never To Do

- ❌ Delete or replace `prd.json`
- ❌ Remove stories from `prd.json`
- ❌ Mark stories complete without verification
- ❌ Restart the project from scratch
- ❌ Implement multiple unrelated stories at once
- ❌ Store credentials in agent files
- ❌ Modify `README.md` without explicit instruction
- ❌ Rewrite working architecture without a PRD story justifying it

---

_This file was created on 2026-08-25. Update it when the project architecture changes significantly._
