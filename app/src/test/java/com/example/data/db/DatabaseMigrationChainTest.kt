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
 * production migration array. Room runs 11 → 27 and, because `exportSchema` is on and the entity
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

    @Test
    fun `every consecutive version from 11 to 27 has exactly one migration`() {
        val pairs = AppDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(
            (11 until 27).map { it to it + 1 },
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
        assertEquals(27, db!!.openHelper.writableDatabase.version)

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
}
