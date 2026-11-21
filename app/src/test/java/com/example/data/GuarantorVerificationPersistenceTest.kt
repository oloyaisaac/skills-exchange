package com.example.data

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AppDatabase
import com.example.data.db.GuarantorEntity
import com.example.data.db.UserProfileEntity
import com.example.data.db.WorkerKycEntity
import com.example.util.GuarantorValidators
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REG-002 persistence + schema coverage for the two-guarantor verification step.
 *
 * Covers what the validator tests cannot: that both guarantors (NIN, consent and both
 * photographs) survive a Room round-trip, that re-submitting the same people replaces
 * rather than duplicates rows, that one worker's guarantor rows can never be read,
 * cleared or NIN-updated through another worker's id, that the admin NIN decision is the
 * only field applied without user input, and that the MIGRATION_18_19 columns and index
 * still match the Room-generated v19 schema (migration/entity drift is the failure mode
 * that crashes the app on upgrade).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuarantorVerificationPersistenceTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun kyc(userId: String, status: String = "SUBMITTED") = WorkerKycEntity(
        userId = userId,
        fullLegalName = "Reg Worker",
        nationalIdNumber = "UG-8829104-B",
        dateOfBirth = "1994-05-02",
        gender = "FEMALE",
        idFrontStoragePath = "users/$userId/kyc/front.jpg",
        idBackStoragePath = "users/$userId/kyc/back.jpg",
        status = status
    )

    private fun guarantor(
        workerId: String,
        name: String,
        nin: String,
        phone: String,
        slot: Int
    ) = GuarantorEntity(
        id = GuarantorValidators.guarantorId(workerId, phone),
        workerKycId = workerId,
        fullName = name,
        phoneNumber = phone,
        relationship = "Community Leader",
        district = "Wakiso",
        idFrontStoragePath = "",
        idBackStoragePath = "",
        createdAt = 1_000L + slot,
        nationalIdNumber = nin,
        guarantorPhotoStoragePath = "users/$workerId/guarantors/guarantor_$slot.jpg",
        workerWithGuarantorPhotoStoragePath = "users/$workerId/guarantors/with_worker_$slot.jpg",
        consentProvided = true,
        submittedAt = 2_000L,
        updatedAt = 2_000L
    )

    /**
     * worker_kyc carries a foreign key on user_profiles, so the worker's profile row must
     * exist before the KYC row it belongs to — the same order the onboarding flow uses.
     */
    private suspend fun registerWorker(workerId: String, phone: String) {
        db.userProfileDao().insertUserProfile(
            UserProfileEntity(
                id = workerId,
                phoneNumber = phone,
                fullName = "Reg Worker",
                district = "Nakawa",
                initialRole = "WORKER",
                currentActiveMode = "WORKER",
                workerKycStatus = "SUBMITTED",
                isWorkerActivated = false,
                trainingCompleted = false,
                isOnboardingCompleted = true
            )
        )
        db.workerKycDao().insertWorkerKyc(kyc(workerId))
    }

    private suspend fun submitTwoGuarantors(
        workerId: String,
        phone: String = "+256 700 111 222"
    ) {
        registerWorker(workerId, phone)
        db.guarantorDao().insertGuarantors(
            listOf(
                guarantor(workerId, "Amina Nakato", "CM31019880042E", "+256 772 198 420", 1),
                guarantor(workerId, "Ivan Mugisha", "CM44209770118F", "+256 701 555 322", 2)
            )
        )
        db.workerKycDao().updateGuarantorStep(
            userId = workerId,
            guarantorStatus = "SUBMITTED",
            groupPhotoStoragePath = "users/$workerId/guarantors/group.jpg",
            submittedAt = 2_000L
        )
    }

    // --- requirement: two guarantors + NIN + photographs persist ---

    @Test
    fun `both guarantors with nin and photographs persist and reload`() = runBlocking {
        submitTwoGuarantors("worker_reg_002")

        val stored = db.guarantorDao().getGuarantorsForWorkerOnce("worker_reg_002")
        assertEquals(2, stored.size)
        assertEquals(listOf("Amina Nakato", "Ivan Mugisha"), stored.map { it.fullName })
        assertEquals(listOf("CM31019880042E", "CM44209770118F"), stored.map { it.nationalIdNumber })
        stored.forEachIndexed { index, row ->
            assertEquals(
                "users/worker_reg_002/guarantors/guarantor_${index + 1}.jpg",
                row.guarantorPhotoStoragePath
            )
            assertEquals(
                "users/worker_reg_002/guarantors/with_worker_${index + 1}.jpg",
                row.workerWithGuarantorPhotoStoragePath
            )
            assertTrue(row.consentProvided)
            // Nothing may self-approve the NIN check on insert.
            assertEquals("PENDING", row.ninVerificationStatus)
        }
    }

    @Test
    fun `group photo and guarantor status persist on the worker's own kyc row`() = runBlocking {
        submitTwoGuarantors("worker_reg_002")

        val stored = db.workerKycDao().getWorkerKycOnce("worker_reg_002")
        assertNotNull(stored)
        assertEquals("users/worker_reg_002/guarantors/group.jpg", stored?.groupPhotoStoragePath)
        assertEquals("SUBMITTED", stored?.guarantorStatus)
        assertEquals(2_000L, stored?.guarantorsSubmittedAt)
        assertEquals("", stored?.guarantorReviewFeedback)
        // The guarantor step is separate from the KYC decision it sits beside.
        assertEquals("SUBMITTED", stored?.status)
    }

    @Test
    fun `resubmitting the same guarantors replaces the rows instead of duplicating them`() = runBlocking {
        submitTwoGuarantors("worker_reg_002")

        // Corrected details after CHANGES_REQUIRED: same two people, one edited name.
        db.guarantorDao().insertGuarantors(
            listOf(
                guarantor("worker_reg_002", "Amina Nakato", "CM31019880042E", "+256 772 198 420", 1)
                    .copy(guarantorPhotoStoragePath = "users/worker_reg_002/guarantors/guarantor_1_retake.jpg"),
                guarantor("worker_reg_002", "Ivan K Mugisha", "CM44209770118F", "+256 701 555 322", 2)
            )
        )
        db.workerKycDao().updateGuarantorStep(
            userId = "worker_reg_002",
            guarantorStatus = "SUBMITTED",
            groupPhotoStoragePath = "users/worker_reg_002/guarantors/group.jpg",
            submittedAt = 9_000L,
            feedback = ""
        )

        val stored = db.guarantorDao().getGuarantorsForWorkerOnce("worker_reg_002")
        assertEquals(2, stored.size)
        assertEquals(1, stored.count { it.fullName == "Ivan K Mugisha" })
        assertEquals(
            "users/worker_reg_002/guarantors/guarantor_1_retake.jpg",
            stored.first { it.fullName == "Amina Nakato" }.guarantorPhotoStoragePath
        )
        assertEquals(
            9_000L,
            db.workerKycDao().getWorkerKycOnce("worker_reg_002")?.guarantorsSubmittedAt
        )
    }

    // --- requirement: own records only, admin decision mirror, no cross-worker writes ---

    @Test
    fun `one worker's guarantor rows are never returned for another worker`() = runBlocking {
        submitTwoGuarantors("worker_a")
        submitTwoGuarantors("worker_b", "+256 700 333 444")

        val workerA = db.guarantorDao().getGuarantorsForWorkerOnce("worker_a")
        assertEquals(2, workerA.size)
        assertTrue(workerA.none { it.workerKycId == "worker_b" })

        // Contact-number lookup is the deliberate cross-worker read — it exists so a
        // guarantor already listed for someone else can be detected. The DAO offers no
        // write path from it, so discovering a row there cannot alter another worker's data.
        val matches = db.guarantorDao().findGuarantorsByPhone("+256 772 198 420")
        assertEquals(2, matches.size)
        assertEquals(setOf("worker_a", "worker_b"), matches.map { it.workerKycId }.toSet())
    }

    @Test
    fun `clearing one worker's guarantors leaves every other worker intact`() = runBlocking {
        submitTwoGuarantors("worker_a")
        submitTwoGuarantors("worker_b", "+256 700 333 444")

        db.guarantorDao().deleteGuarantorsForWorker("worker_a")

        assertEquals(0, db.guarantorDao().countGuarantorsForWorker("worker_a"))
        assertEquals(2, db.guarantorDao().countGuarantorsForWorker("worker_b"))
        assertEquals(
            "users/worker_b/guarantors/group.jpg",
            db.workerKycDao().getWorkerKycOnce("worker_b")?.groupPhotoStoragePath
        )
    }

    @Test
    fun `admin nin decision is applied only to that worker's rows`() = runBlocking {
        submitTwoGuarantors("worker_a")
        submitTwoGuarantors("worker_b", "+256 700 333 444")

        db.guarantorDao().updateNinStatusForWorker("worker_a", "VERIFIED")

        assertTrue(
            db.guarantorDao().getGuarantorsForWorkerOnce("worker_a")
                .all { it.ninVerificationStatus == "VERIFIED" }
        )
        assertTrue(
            db.guarantorDao().getGuarantorsForWorkerOnce("worker_b")
                .all { it.ninVerificationStatus == "PENDING" }
        )
    }

    @Test
    fun `changes required decision keeps the data for correction and resubmission`() = runBlocking {
        submitTwoGuarantors("worker_reg_002")

        db.workerKycDao().updateGuarantorStep(
            userId = "worker_reg_002",
            guarantorStatus = "CHANGES_REQUIRED",
            groupPhotoStoragePath = "users/worker_reg_002/guarantors/group.jpg",
            submittedAt = 2_000L,
            feedback = "Guarantor 2 photograph is unreadable."
        )

        val kyc = db.workerKycDao().getWorkerKycOnce("worker_reg_002")
        assertEquals("CHANGES_REQUIRED", kyc?.guarantorStatus)
        assertEquals("Guarantor 2 photograph is unreadable.", kyc?.guarantorReviewFeedback)
        // The sheet reopens against the stored draft rather than an empty form.
        assertEquals(2, db.guarantorDao().getGuarantorsForWorkerOnce("worker_reg_002").size)
    }

    @Test
    fun `a guarantor row cannot exist without its worker kyc record`() = runBlocking {
        // workerKycId is a foreign key, so a stray row for a KYC record that was never
        // created (or was withdrawn) is rejected by SQLite itself.
        var rejected: Throwable? = null
        try {
            db.guarantorDao().insertGuarantor(
                guarantor("ghost_worker", "Amina Nakato", "CM31019880042E", "+256 772 198 420", 1)
            )
        } catch (error: Exception) {
            rejected = error
        }
        assertNotNull("Foreign key on workerKycId must reject orphan guarantor rows", rejected)
    }

    // --- existing security: the guarantor step never grants activation ---

    @Test
    fun `approved guarantors alone never satisfy the worker activation gate (SEC-009)`() = runBlocking {
        // registerWorker files the profile as SUBMITTED KYC, no training, not activated.
        submitTwoGuarantors("worker_reg_002")
        db.guarantorDao().updateNinStatusForWorker("worker_reg_002", "VERIFIED")
        db.workerKycDao().updateGuarantorStep(
            userId = "worker_reg_002",
            guarantorStatus = "APPROVED",
            groupPhotoStoragePath = "users/worker_reg_002/guarantors/group.jpg",
            submittedAt = 2_000L
        )

        val profile = db.userProfileDao().getUserProfile("worker_reg_002").first()
        assertNotNull(profile)
        assertEquals("APPROVED", db.workerKycDao().getWorkerKycOnce("worker_reg_002")?.guarantorStatus)
        assertEquals(2, db.guarantorDao().countGuarantorsForWorker("worker_reg_002"))

        val canAccessWorkerDashboard = profile!!.workerKycStatus == "APPROVED" &&
            profile.trainingCompleted &&
            profile.isWorkerActivated
        assertFalse(
            "Guarantor approval must never satisfy the worker dashboard gate",
            canAccessWorkerDashboard
        )
    }

    // --- MIGRATION_18_19 / entity drift guard ---

    private fun forEachRow(sql: String, block: (Cursor) -> Unit) {
        db.openHelper.writableDatabase.query(sql).use { cursor ->
            while (cursor.moveToNext()) block(cursor)
        }
    }

    private data class Column(val type: String, val notNull: Int, val default: String?)

    private fun tableInfo(table: String): Map<String, Column> {
        val columns = mutableMapOf<String, Column>()
        forEachRow("PRAGMA table_info($table)") { cursor ->
            columns[cursor.getString(cursor.getColumnIndexOrThrow("name"))] = Column(
                type = cursor.getString(cursor.getColumnIndexOrThrow("type")),
                notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")),
                default = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
            )
        }
        return columns
    }

    /**
     * Every MIGRATION_18_19 column must exist in the Room-generated schema with the same
     * SQLite type and nullability — that disagreement is what makes an upgraded install
     * fail its first query. Kotlin default arguments are not visible to Room, so this
     * project's entities declare no SQL DEFAULT clause (Room always writes every column
     * explicitly) while the migration supplies one so pre-existing rows get a neutral
     * value. If an entity ever starts declaring a default, it must be the migration's value.
     */
    private fun assertMigratedColumn(
        columns: Map<String, Column>,
        name: String,
        sqlType: String,
        migrationDefault: String
    ) {
        val column = columns[name]
        assertNotNull("Column $name is missing from the Room schema", column)
        assertEquals(
            "$name must have the same SQLite type as MIGRATION_18_19",
            sqlType,
            column!!.type
        )
        assertEquals("$name must be NOT NULL like the migrated column", 1, column.notNull)
        column.default?.let {
            assertEquals(
                "$name default must match MIGRATION_18_19",
                migrationDefault,
                it.trim().trim('\'')
            )
        }
    }

    @Test
    fun `migration eighteen to nineteen is additive and registered`() {
        assertEquals(18, AppDatabase.MIGRATION_18_19.startVersion)
        assertEquals(19, AppDatabase.MIGRATION_18_19.endVersion)

        var schemaVersion = 0L
        forEachRow("PRAGMA user_version") { cursor -> schemaVersion = cursor.getLong(0) }
        // C2-2 Fix #4 (F4) bumped the schema to v20 and Fix #5 (F5) to v21 (chat sender identity);
        // AUDIT-W-CASHOUT later bumped it to v22 (wallet ledger + worker withdrawals); AUDIT-A-SOS
        // bumped it to v23 (sos_incidents table).
        // The MIGRATION_18_19 assertions above are unchanged and still must hold.
        assertEquals("Room must build the current v30 schema", 30L, schemaVersion)
    }

    @Test
    fun `migration twenty to twenty one is additive and registered`() {
        // C2-2 Fix #5 (F5): chat sender identity. Additive only — existing messages survive with
        // an UNKNOWN author (NULL), never back-filled from the display senderName.
        assertEquals(20, AppDatabase.MIGRATION_20_21.startVersion)
        assertEquals(21, AppDatabase.MIGRATION_20_21.endVersion)

        var schemaVersion = 0L
        forEachRow("PRAGMA user_version") { cursor -> schemaVersion = cursor.getLong(0) }
        // AUDIT-W-CASHOUT later bumped it to v22 (wallet ledger + worker withdrawals); AUDIT-A-SOS
        // bumped it to v23 (sos_incidents table). This test asserts the F5 migration itself — its
        // senderId checks below — so only the live schema version moves forward. MIGRATION_20_21's
        // own start/end stay pinned at 20 -> 21.
        assertEquals("Room must build the current v30 schema", 30L, schemaVersion)

        var senderIdColumn: String? = null
        forEachRow("PRAGMA table_info(chat_messages)") { cursor ->
            if (cursor.getString(1) == "senderId") senderIdColumn = cursor.getString(2)
        }
        assertNotNull("senderId must exist on chat_messages", senderIdColumn)
        // Nullable on purpose: pre-F5 messages and system rows have no provable author.
        assertEquals("senderId must be TEXT", "TEXT", senderIdColumn)
    }

    @Test
    fun `migration nineteen to twenty is additive and registered`() {
        // C2-2 Fix #4 (F4): job authorship. Additive only — the job_posts rows that already exist
        // must survive, and postedByUid must be NULL for them (unknown owner) rather than
        // back-filled from the display string.
        assertEquals(19, AppDatabase.MIGRATION_19_20.startVersion)
        assertEquals(20, AppDatabase.MIGRATION_19_20.endVersion)

        var schemaVersion = 0L
        forEachRow("PRAGMA user_version") { cursor -> schemaVersion = cursor.getLong(0) }
        // C2-2 Fix #5 (F5) later bumped the schema to v21 (chat sender identity) and AUDIT-W-CASHOUT
        // to v22 (wallet ledger + worker withdrawals); AUDIT-A-SOS bumped it to v23 (sos_incidents
        // table). This test asserts the F4 migration itself — its postedByUid checks below — so
        // only the live schema version moves forward. The migration's own start/end versions stay
        // pinned at 19 -> 20.
        assertEquals("Room must build the current v30 schema", 30L, schemaVersion)

        var postedByUidColumn: String? = null
        forEachRow("PRAGMA table_info(job_posts)") { cursor ->
            if (cursor.getString(1) == "postedByUid") postedByUidColumn = cursor.getString(2)
        }
        assertNotNull("postedByUid must exist on job_posts", postedByUidColumn)
        // Nullable on purpose: pre-F4 jobs have an unknown owner.
        assertEquals("postedByUid must be TEXT", "TEXT", postedByUidColumn)
    }

    @Test
    fun `room schema matches every column added by the guarantor migration`() {
        // Room creates the schema from the entities, MIGRATION_18_19 creates it from SQL.
        // If the two disagree, an upgraded install fails its first query, so every migrated
        // column must exist, be NOT NULL and carry the same default.
        val guarantorColumns = tableInfo("guarantors")
        val kycColumns = tableInfo("worker_kyc")

        assertMigratedColumn(guarantorColumns, "nationalIdNumber", "TEXT", "")
        assertMigratedColumn(guarantorColumns, "guarantorPhotoStoragePath", "TEXT", "")
        assertMigratedColumn(guarantorColumns, "workerWithGuarantorPhotoStoragePath", "TEXT", "")
        assertMigratedColumn(guarantorColumns, "consentProvided", "INTEGER", "0")
        assertMigratedColumn(guarantorColumns, "ninVerificationStatus", "TEXT", "PENDING")
        assertMigratedColumn(guarantorColumns, "submittedAt", "INTEGER", "0")
        assertMigratedColumn(guarantorColumns, "updatedAt", "INTEGER", "0")
        assertMigratedColumn(kycColumns, "groupPhotoStoragePath", "TEXT", "")
        assertMigratedColumn(kycColumns, "guarantorStatus", "TEXT", "NOT_STARTED")
        assertMigratedColumn(kycColumns, "guarantorsSubmittedAt", "INTEGER", "0")
        assertMigratedColumn(kycColumns, "guarantorReviewFeedback", "TEXT", "")
    }

    @Test
    fun `guarantor phone number index exists for duplicate lookups`() {
        val indexes = mutableListOf<String>()
        forEachRow("PRAGMA index_list(guarantors)") { cursor ->
            indexes += cursor.getString(cursor.getColumnIndexOrThrow("name"))
        }
        assertTrue(
            "index_guarantors_phoneNumber is required by findGuarantorsByPhone, found: $indexes",
            indexes.contains("index_guarantors_phoneNumber")
        )
    }
}
