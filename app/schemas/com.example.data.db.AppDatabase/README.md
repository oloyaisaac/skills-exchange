# Exported Room schemas

These files are written by Room during compilation because `AppDatabase` sets `exportSchema = true`.
They are the authority `scripts/check_money_representation.mjs` reads, and the files
`DatabaseMigrationChainTest` validates against.

**Why the directory starts at 27.** `exportSchema` was off until R04 (2026-11-20), so Room never wrote a
JSON for versions 11–26 and cannot reconstruct one retroactively: the exported file is a snapshot of the
entity contract at that version, not something derivable from migration code. Those versions are
covered by execution instead — `DatabaseMigrationChainTest` builds a real version-11 database from
`src/test/resources/migration/v11_baseline.sql` and opens it through the production migration array, so
Room validates 11 → 30 in one run.

**Adding a migration.** Bump `version`, add the migration, run `./gradlew :app:compileDebugKotlin` (or
let CI do it) and commit the new `N.json` in the same change. The chain test loads specific exported
versions for its fixtures and validates the final database against Room's generated schema; it does not
assert that every version has a committed JSON. Review the schema export separately.
