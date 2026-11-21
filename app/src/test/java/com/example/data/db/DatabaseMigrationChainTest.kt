package com.example.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R04-P2-02: the whole Room migration chain is executed, not just one hop.
 *
 * The database starts as a real version-11 file built from `src/test/resources/migration/
 * v11_baseline.sql`, is seeded with a row, and is then opened through `AppDatabase` with the
 * production migration array. Room runs 11 → 30 and, because `exportSchema` is on and the entity
 * classes are the contract, validates the result: a column, index or foreign key that a migration
 * forgets makes `Room.openHelper.writableDatabase` throw `IllegalStateException("Migration didn't
 * properly handle ...")`, which fails this test.
 *
 * This is the test that caught the defect fixed alongside it: `MIGRATION_13_14` recreated
 * `job_posts` without the `photos` and `scheduledAt` columns the entity declares, so every upgrade
 * from v11–v13 produced a table Room could not validate (and `SELECT *` silently dropped the two
 * columns for any row that survived).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DatabaseMigrationChainTest {

    private val dbName = "migration_chain_test.db"
    private lateinit var context: Context
    private var db: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    private fun installV11Baseline() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        val sql = readResource("migration/v11_baseline.sql")
            // The baseline is a documented file, so its `--` comments are stripped before the
            // statements are split on the statement terminator.
            .lines()
            .filterNot { it.trimStart().startsWith("--") }
            .joinToString("\n")
        sql.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { raw.execSQL(it) }
        raw.version = 11
        raw.execSQL(
            "INSERT INTO job_posts (" +
                "id, title, category, description, postedByResident, district, budget, urgency, " +
                "status, assignedProviderId, assignedProviderName, createdTimestamp, estimatedHours, " +
                "materialsCost, safetyFee, escrowStatus, doorstepPin) VALUES (" +
                "'job_v11', 'Fix the tap', 'PLUMBING', 'Kitchen tap drips', 'Robert K.', 'Kampala', " +
                "50000.0, 'NORMAL', 'IN_PROGRESS', NULL, NULL, 1700000000000, 2.0, 0.0, 0.0, " +
                "'FUNDS_LOCKED_IN_ESCROW', '1234')"
        )
        raw.execSQL(
            "INSERT INTO service_providers (" +
                "id, name, tagline, category, neighborhood, residenceYears, hourlyRate, " +
                "distanceMiles, rating, completedJobsCount, avgResponseTime, isVerifiedResident, " +
                "isBackgroundChecked, bio, skillsCsv, latitude, longitude, endorsementsCount, " +
                "nationalIdNumber, idDocumentUrl, selfieVerificationUrl, kycStatus, isActivated, " +
                "momoNumber, momoProvider, walletBalance) VALUES (" +
                "'prov_v11', 'Alice', 'Plumber', 'PLUMBING', 'Kampala', 4, 50.0, 1.0, 4.5, 12, " +
                "'1h', 1, 1, 'bio', 'plumbing', 0.3, 32.6, 3, 'UG-1', 'u1', 'u2', 'APPROVED', 1, " +
                "'+256700000000', 'MTN_MOMO', 100.0)"
        )
        raw.close()
    }

    private fun readResource(path: String): String =
        javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader().use { it.readText() }

    /**
     * Reads the committed Room schema (`app/schemas/...`) from the module directory. Unit tests run
     * with the module as the working directory; no copy of the schema is kept, so the test always
     * validates against the file that is actually committed.
     */
    private fun readSchemaAsset(path: String): String {
        val candidates = listOf(
            java.io.File("schemas/$path"),
            java.io.File("app/schemas/$path"),
            java.io.File("../app/schemas/$path")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("schema not found: $path (cwd=${java.io.File(".").absolutePath})")
        return file.readText()
    }

    @Test
    fun `every consecutive version from 11 to 30 has exactly one migration`() {
        val pairs = AppDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(
            (11 until 30).map { it to it + 1 },
            pairs
        )
    }

    @Test
    fun `a v11 database migrates to the current version and keeps its data`() {
        installV11Baseline()
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // Opening the database is the validation: Room compares the migrated schema with the
        // entity definitions and throws if they disagree.
        assertEquals(30, db!!.openHelper.writableDatabase.version)

        val job = runBlocking { db!!.jobPostDao().getJobById("job_v11") }
        assertNotNull("the pre-migration job row must survive", job)
        assertEquals("Fix the tap", job!!.title)
        assertEquals(50000.0, job.budget, 0.0)
        assertEquals("FUNDS_LOCKED_IN_ESCROW", job.escrowStatus)
        assertEquals("1234", job.doorstepPin)

        // Columns that did not exist at v11 arrive with the neutral defaults their migrations set.
        assertEquals(emptyList<String>(), job.photos)
        assertEquals(0L, job.scheduledAt)
        assertEquals(null, job.postedByUid)
        assertEquals(null, job.assignedProviderUid)
        assertEquals("", job.streetAddress)

        val provider = runBlocking { db!!.serviceProviderDao().getProviderById("prov_v11") }
        assertNotNull(provider)
        assertEquals("Alice", provider!!.name)
    }

    @Test
    fun `the migrated database accepts new rows and viewer-scoped queries`() = runBlocking {
        installV11Baseline()
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        db!!.jobPostDao().insertJob(
            JobPostEntity(
                id = "job_new",
                title = "New job",
                category = "PLUMBING",
                description = "d",
                postedByResident = "Me",
                district = "Kampala",
                budget = 10.0,
                urgency = "NORMAL",
                estimatedHours = 2.0,
                materialsCost = 0.0,
                safetyFee = 0.0,
                status = "POSTED",
                assignedProviderId = null,
                assignedProviderName = null,
                createdTimestamp = System.currentTimeMillis(),
                postedByUid = "user_1",
                doorstepPin = "4321"
            )
        )

        val visible = db!!.jobPostDao().getJobsVisibleTo("user_1").first().map { it.id }
        assertTrue("the new job is visible to its owner", visible.contains("job_new"))
        assertTrue("the migrated job is still listed", visible.contains("job_v11"))
    }

    @Test
    fun `migration 27 to 28 converts wallet money to exact minor units`() {
        // Build a real version-27 database from the exported schema, put money in it as REAL, then
        // let the app migrate it. 100.50 and 250.25 must become 10050 and 25025 exactly.
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        installSchema(raw, version = 27)
        raw.version = 27
        raw.execSQL(
            "INSERT INTO user_profiles (id, phoneNumber, fullName, district, initialRole, " +
                "currentActiveMode, workerKycStatus, isWorkerActivated, trainingCompleted, " +
                "nationalIdNumber, additionalSkillsCsv, avatarUrl, momoNumber, momoProvider, " +
                "walletBalance, createdAtTimestamp, updatedAtTimestamp, isOnboardingCompleted, " +
                "backupContactName, backupContactPhone) VALUES (" +
                "'worker_1', '+256700000001', 'W', 'Kampala', 'WORKER', 'WORKER', 'APPROVED', 1, 1, " +
                "'UG-1', '', '', '+256700000001', 'MTN_MOMO', 0.0, 1, 1, 1, '', '')"
        )
        raw.execSQL(
            "INSERT INTO wallet_ledger (id, workerId, entryType, amount, withdrawalId, description, createdAt) " +
                "VALUES ('led_1', 'worker_1', 'CREDIT', 100.5, NULL, 'credit', 1)"
        )
        raw.execSQL(
            "INSERT INTO wallet_withdrawals (id, workerId, amount, destinationMomoNumber, status, " +
                "idempotencyKey, createdAt, updatedAt) VALUES " +
                "('wd_1', 'worker_1', 250.25, '700000001', 'PENDING', 'k1', 1, 1)"
        )
        raw.close()

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        assertEquals(30, db!!.openHelper.writableDatabase.version)
        val entries = runBlocking { db!!.walletLedgerDao().getEntriesForWorker("worker_1") }
        assertEquals(1, entries.size)
        assertEquals(10_050L, entries.single().amountMinor)
        val withdrawals = runBlocking { db!!.walletWithdrawalDao().getWithdrawalsForWorker("worker_1") }
        assertEquals(1, withdrawals.size)
        assertEquals(25_025L, withdrawals.single().amountMinor)
    }

    @Test
    fun `migration 28 to 29 clears the shared default door PIN and leaves real ones alone`() {
        // R10-P2-01: the migration that created `job_posts` gave `doorstepPin` the literal default
        // '4829'. A row written through it therefore holds a PIN anyone reading this repository knows.
        // The step must clear exactly those rows and touch nothing else.
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        installSchema(raw, version = 28)
        raw.version = 28
        insertJobRow(raw, id = "job_default_pin", pin = "4829")
        insertJobRow(raw, id = "job_real_pin", pin = "7319")
        raw.close()

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        assertEquals(30, db!!.openHelper.writableDatabase.version)
        val cleared = runBlocking { db!!.jobPostDao().getJobById("job_default_pin") }
        assertNotNull("the row itself must survive", cleared)
        assertEquals("the shared default PIN must be gone", "", cleared!!.doorstepPin)
        val kept = runBlocking { db!!.jobPostDao().getJobById("job_real_pin") }
        assertEquals("a job's own PIN is not the migration's business", "7319", kept!!.doorstepPin)
    }

    @Test
    fun `migration 29 to 30 preserves parents and all dependent rows`() {
        val file = context.getDatabasePath(dbName)
        file.parentFile?.mkdirs()
        val raw = SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.setForeignKeyConstraintsEnabled(true)
        installSchema(raw, version = 29)
        raw.version = 29
        val fixtures = listOf(
            "service_providers" to mapOf("id" to "'provider'", "walletBalance" to "123.5"),
            "user_profiles" to mapOf("id" to "'user'", "phoneNumber" to "'unique'", "walletBalance" to "456.25"),
            "job_posts" to mapOf("id" to "'job'", "assignedProviderId" to "'provider'"),
            "skill_endorsements" to mapOf("id" to "'endorsement'", "providerId" to "'provider'"),
            "worker_kyc" to mapOf("id" to "'kyc'", "userId" to "'user'"),
            "worker_training" to mapOf("id" to "'training'", "userId" to "'user'"),
            "notifications" to mapOf("id" to "'notification'", "userId" to "'user'"),
            "reports" to mapOf("id" to "'report'", "reportedBy" to "'user'", "reportedUserId" to "'user'"),
            "wallet_ledger" to mapOf("id" to "'ledger'", "workerId" to "'user'", "amountMinor" to "12345"),
            "wallet_withdrawals" to mapOf("id" to "'withdrawal'", "workerId" to "'user'"),
            "chat_messages" to mapOf("id" to "'chat'", "jobId" to "'job'"),
            "guarantors" to mapOf("id" to "'guarantor'", "workerKycId" to "'user'"),
            "job_quotes" to mapOf("id" to "'quote'", "jobId" to "'job'", "workerId" to "'provider'"),
            "disputes" to mapOf("id" to "'dispute'", "jobId" to "'job'"),
            "ratings" to mapOf("id" to "'rating'", "jobId" to "'job'"),
            "job_status_audit" to mapOf("id" to "'audit'", "jobId" to "'job'"),
            "sos_incidents" to mapOf("id" to "'sos'", "jobId" to "'job'")
        )
        for ((table, overrides) in fixtures) insertV29Fixture(raw, table, overrides)
        raw.close()

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()
        val migrated = db!!.openHelper.writableDatabase
        assertEquals(30, migrated.version)
        for ((table, _) in fixtures) {
            migrated.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("row lost from $table", 1, cursor.getInt(0))
            }
        }
        migrated.query("SELECT walletBalanceDisplay FROM service_providers WHERE id='provider'").use {
            assertTrue(it.moveToFirst()); assertEquals(123.5, it.getDouble(0), 0.0)
        }
        migrated.query("SELECT walletBalanceDisplay FROM user_profiles WHERE id='user'").use {
            assertTrue(it.moveToFirst()); assertEquals(456.25, it.getDouble(0), 0.0)
        }
        migrated.query("SELECT assignedProviderId FROM job_posts WHERE id='job'").use {
            assertTrue(it.moveToFirst()); assertEquals("provider", it.getString(0))
        }
        migrated.query("SELECT amountMinor FROM wallet_ledger WHERE id='ledger'").use {
            assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0))
        }
        migrated.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }

    private fun insertV29Fixture(raw: SQLiteDatabase, table: String, overrides: Map<String, String>) {
        val entities = org.json.JSONObject(readSchemaAsset("com.example.data.db.AppDatabase/29.json"))
            .getJSONObject("database").getJSONArray("entities")
        val entity = (0 until entities.length()).map { entities.getJSONObject(it) }
            .first { it.getString("tableName") == table }
        val fields = entity.getJSONArray("fields")
        val columns = mutableListOf<String>()
        val values = mutableListOf<String>()
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            val column = field.getString("columnName")
            columns += "`$column`"
            values += overrides[column] ?: when {
                !field.optBoolean("notNull", false) -> "NULL"
                field.getString("affinity") == "TEXT" -> "''"
                field.getString("affinity") == "REAL" -> "0.0"
                else -> "0"
            }
        }
        raw.execSQL("INSERT INTO `$table` (${columns.joinToString()}) VALUES (${values.joinToString()})")
    }

    /**
     * Inserts a `job_posts` row with every column the v28 schema declares.
     *
     * Written from the exported schema rather than by hand: `job_posts` gained NOT NULL columns without
     * defaults over seven versions, and a hand-written partial INSERT fails on whichever one the
     * migration added last (this is exactly how the first version of this test failed in CI).
     */
    private fun insertJobRow(raw: SQLiteDatabase, id: String, pin: String) {
        val json = org.json.JSONObject(readSchemaAsset("com.example.data.db.AppDatabase/28.json"))
        val entities = json.getJSONObject("database").getJSONArray("entities")
        var jobPosts: org.json.JSONObject? = null
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            if (entity.getString("tableName") == "job_posts") jobPosts = entity
        }
        val entity = jobPosts ?: error("job_posts is not in the v28 schema")
        val fields = entity.getJSONArray("fields")
        val columns = mutableListOf<String>()
        val values = mutableListOf<String>()
        for (index in 0 until fields.length()) {
            val field = fields.getJSONObject(index)
            val column = field.getString("columnName")
            val affinity = field.getString("affinity")
            columns += column
            values += when {
                column == "id" -> "'$id'"
                column == "doorstepPin" -> "'$pin'"
                affinity == "TEXT" -> "''"
                affinity == "INTEGER" -> "0"
                affinity == "REAL" -> "0.0"
                else -> "NULL"
            }
        }
        raw.execSQL(
            "INSERT INTO job_posts (${columns.joinToString(", ")}) VALUES (${values.joinToString(", ")})"
        )
    }

    /** Creates every table/index of an exported schema version, straight from `app/schemas`. */
    private fun installSchema(raw: SQLiteDatabase, version: Int) {
        val json = org.json.JSONObject(readSchemaAsset("com.example.data.db.AppDatabase/$version.json"))
        val entities = json.getJSONObject("database").getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            // Room writes the table name as a `${TABLE_NAME}` placeholder in the exported JSON.
            val placeholder = "$" + "{TABLE_NAME}"
            raw.execSQL(entity.getString("createSql").replace(placeholder, table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (i in 0 until indices.length()) {
                val idx = indices.getJSONObject(i)
                val columns = idx.getJSONArray("columnNames")
                val columnList = (0 until columns.length()).joinToString(", ") { columns.getString(it) }
                val unique = if (idx.optBoolean("unique", false)) "UNIQUE " else ""
                raw.execSQL(
                    "CREATE ${unique}INDEX IF NOT EXISTS `${idx.getString("name")}` " +
                        "ON `$table` ($columnList)"
                )
            }
        }
    }
}
