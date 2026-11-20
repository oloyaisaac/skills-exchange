# Deploy and rollback

**Owner:** Engineering lead · **Reviewer round:** R14 · **Finding:** `REVIEW_14.md` P2-03, P2-07

The review's point was that deployment was not reproducible from the repository: a stale build could
ship, indexes lived only in a console, and there was no written path from "merged" to "live" or back
again. What exists now, and what still does not, is below. **Nothing in this document has been
executed** — there is no Firebase project reachable from the remediation environment and no credentials
for one, so every command here is a procedure to be run by an operator, not a record of a deploy.

## 1. What the repository owns

| Piece | Where | State |
|---|---|---|
| Firestore rules | `firestore.rules` | deployed by `firebase deploy --only firestore:rules` |
| Firestore indexes | `firestore.indexes.json` (4 composite indexes) | deployed by `firebase deploy --only firestore:indexes`; no longer console-only |
| Storage rules | `storage.rules` | static-checked in CI (`rules-test/storage.rules.check.mjs`), never executed against the emulator |
| Cloud Functions | `functions/` | `predeploy` runs `npm --prefix "$RESOURCE_DIR" run build`, so a stale `lib/` cannot ship |
| Emulators | `emulators` block in `firebase.json` | Firestore 8080, Storage 9199, UI disabled, single-project mode |
| Project selection | `.firebaserc` | one `default` project. **No staging alias exists** |

## 2. One-command deploy

```bash
# from the repository root, authenticated as a project owner
firebase deploy --only functions,firestore:rules,firestore:indexes,storage
```

Order matters and is handled by `firebase deploy` itself: rules and indexes go out before functions are
promoted, and `predeploy` compiles `functions/` first, so the deployed code matches the commit.

Before running it:

1. `git status` is clean and `main` is at the commit you intend to ship.
2. CI is green on that commit — 6 jobs in `ci.yml` plus both `android.yml` jobs.
3. `functions/package-lock.json` is unchanged since the last successful deploy, or the deploy is
   deliberate (a lockfile change is a dependency change).
4. Secrets (`AFRICA_TALKING_*`, `GEMINI_*`, MTN MoMo credentials) are present in Secret Manager under
   the bindings declared in `functions/src/index.ts`; a missing secret fails closed at the call site.

## 3. Promotion and staging — not implemented

There is **no staging project**. `.firebaserc` has a single `default` entry, and adding an alias that
points at a project nobody created would make `firebase deploy --project staging` fail in a confusing
way rather than a correct one.

To add one, in this order:

```bash
# once, by an owner with billing access
firebase projects:create skills-exchange-staging       # or the console
firebase apps:create ANDROID "Skills Exchange staging" --package-name com.aistudio.skillsexchange.nxptvq --project skills-exchange-staging
firebase firestore:databases:create '(default)' --project skills-exchange-staging --location nam5
# then, in the repository
firebase use --add                                     # writes the staging alias into .firebaserc
firebase deploy --only functions,firestore:rules,firestore:indexes,storage --project staging
```

Until that exists, a change reaches production by merging to `main` and deploying from it. That is a
single environment with no rehearsal, and it is recorded here as a risk rather than dressed up as a
pipeline.

## 4. Rollback

| Layer | How to roll back | Recovery time | Caveat |
|---|---|---|---|
| Cloud Functions | redeploy the previous commit: `git revert` → PR → merge → `firebase deploy --only functions` | one CI cycle plus deploy | a gen-1 deploy replaces the function atomically; in-flight invocations finish on the old code |
| Firestore rules | `firebase deploy --only firestore:rules` from the reverted commit | minutes | rules are versioned by Cloud Storage; the console lists prior versions |
| Firestore indexes | redeploy the previous `firestore.indexes.json` | an index build is minutes to hours on a large collection | **a removed index cannot be un-built for free**: existing queries relying on it start failing as soon as the rules deploy lands |
| Data | Firestore export from the backup schedule (`docs/`, `check_backup_policy.mjs`) | hours | restoring a whole database overwrites later writes; a targeted restore is the only safe path and has no runbook yet |
| Client | Play staged rollout halt; the previous APK can be re-promoted | minutes | versionCode must still increase for any new upload |

Rollback of a **financial** change is not a deploy operation: a payout that already left the provider
is reversed by the refund path (`refundEscrow`), not by redeploying an older function.

## 5. Rollback drill — not run

A rollback that has never been exercised is a plan, not a control. The drill to run before the first
production release:

1. Deploy commit A to staging; record `firebase functions:list` and a rules read as evidence.
2. Deploy commit B (a deliberate, harmless behaviour change) to staging.
3. Revert to A and deploy again; confirm the change is gone and the rules checks still pass.
4. Restore one deleted document from the most recent export into a scratch collection; confirm the
   export is usable and note how long it took.
5. Write the timings into this table.

None of the five steps has been performed. The remediation environment has no project, no credentials
and no emulator licence to run a Storage emulator, so the drill is the operator's first task, not a
claim in this document.

## 6. Not claimed

- No deploy of any kind has been executed by this programme; every "deployed" state in this repository
  describes source, not a live environment.
- Staging does not exist; promotion is a single environment.
- The rollback drill has not been run.
- Storage rules are statically checked, never executed.
- Data restore has no tested runbook; only the export policy is machine-checked.
