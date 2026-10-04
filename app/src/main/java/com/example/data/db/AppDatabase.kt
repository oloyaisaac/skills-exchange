package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ServiceProviderEntity::class,
        SkillEndorsementEntity::class,
        JobPostEntity::class,
        ChatMessageEntity::class,
        UserProfileEntity::class,
        WorkerKycEntity::class,
        GuarantorEntity::class,
        WorkerTrainingEntity::class,
        JobQuoteEntity::class,
        PendingActionEntity::class,
        DisputeEntity::class,
        RatingEntity::class,
        NotificationEntity::class,
        JobStatusAuditEntity::class,
        ReportEntity::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceProviderDao(): ServiceProviderDao
    abstract fun skillEndorsementDao(): SkillEndorsementDao
    abstract fun jobPostDao(): JobPostDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun workerKycDao(): WorkerKycDao
    abstract fun guarantorDao(): GuarantorDao
    abstract fun workerTrainingDao(): WorkerTrainingDao
    abstract fun jobQuoteDao(): JobQuoteDao
    abstract fun pendingActionDao(): PendingActionDao
    abstract fun disputeDao(): DisputeDao
    abstract fun ratingDao(): RatingDao
    abstract fun notificationDao(): NotificationDao
    abstract fun jobStatusAuditDao(): JobStatusAuditDao
    abstract fun reportDao(): ReportDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // DB-001: Proper migrations instead of destructive fallback
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // DISP-001: Create disputes table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS disputes (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        reportedBy TEXT NOT NULL,
                        reportedUserId TEXT NOT NULL,
                        disputeReason TEXT NOT NULL,
                        description TEXT NOT NULL,
                        evidenceUrlsCsv TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'OPEN',
                        adminNotes TEXT NOT NULL DEFAULT '',
                        resolutionType TEXT NOT NULL DEFAULT '',
                        resolutionAmount REAL NOT NULL DEFAULT 0.0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedBy TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())

                // RATE-001: Create ratings table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS ratings (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        fromUserId TEXT NOT NULL,
                        toUserId TEXT NOT NULL,
                        rating INTEGER NOT NULL,
                        review TEXT NOT NULL DEFAULT '',
                        isFromClient INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // NOTIF-001: Create notifications table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id TEXT PRIMARY KEY NOT NULL,
                        userId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        jobId TEXT NOT NULL DEFAULT '',
                        isRead INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // JOB-005: Create job_status_audit table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS job_status_audit (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        fromStatus TEXT NOT NULL,
                        toStatus TEXT NOT NULL,
                        changedBy TEXT NOT NULL,
                        changedAt INTEGER NOT NULL DEFAULT 0,
                        reason TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // DISP-004: Create reports table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reports (
                        id TEXT PRIMARY KEY NOT NULL,
                        reportedBy TEXT NOT NULL,
                        reportedUserId TEXT NOT NULL,
                        reportedUserName TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        evidenceUrlsCsv TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        adminNotes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedBy TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // DB-002: Add foreign keys and indexes
                // Recreate skill_endorsements with foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS skill_endorsements_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        providerId TEXT NOT NULL,
                        skillName TEXT NOT NULL,
                        endorsedByNeighborName TEXT NOT NULL,
                        neighborDistrict TEXT NOT NULL,
                        FOREIGN KEY (providerId) REFERENCES service_providers(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO skill_endorsements_new SELECT * FROM skill_endorsements")
                database.execSQL("DROP TABLE skill_endorsements")
                database.execSQL("ALTER TABLE skill_endorsements_new RENAME TO skill_endorsements")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_skill_endorsements_providerId ON skill_endorsements(providerId)")

                // Recreate job_posts with foreign key and indexes
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS job_posts_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL,
                        postedByResident TEXT NOT NULL,
                        district TEXT NOT NULL,
                        budget REAL NOT NULL,
                        urgency TEXT NOT NULL,
                        status TEXT NOT NULL,
                        assignedProviderId TEXT,
                        assignedProviderName TEXT,
                        createdTimestamp INTEGER NOT NULL,
                        estimatedHours REAL NOT NULL,
                        materialsCost REAL NOT NULL,
                        safetyFee REAL NOT NULL,
                        materialsProvidedBy TEXT NOT NULL DEFAULT 'RESIDENT',
                        laborHours REAL NOT NULL DEFAULT 2.0,
                        laborRate REAL NOT NULL DEFAULT 60.0,
                        laborTotal REAL NOT NULL DEFAULT 120.0,
                        materialsSubtotal REAL NOT NULL DEFAULT 0.0,
                        platformFee REAL NOT NULL DEFAULT 12.0,
                        grandTotal REAL NOT NULL DEFAULT 132.0,
                        materialReceiptsCsv TEXT NOT NULL DEFAULT '',
                        beforePhotoUrl TEXT,
                        afterPhotoUrl TEXT,
                        emergencyAddonRequested INTEGER NOT NULL DEFAULT 0,
                        emergencyAddonAmount REAL NOT NULL DEFAULT 0.0,
                        emergencyAddonReason TEXT NOT NULL DEFAULT '',
                        emergencyAddonReceiptUrl TEXT NOT NULL DEFAULT '',
                        emergencyAddonStatus TEXT NOT NULL DEFAULT 'NONE',
                        paymentChannel TEXT NOT NULL DEFAULT 'MTN_MOMO',
                        momoPhoneNumber TEXT NOT NULL DEFAULT '+256 772 198 420',
                        escrowStatus TEXT NOT NULL DEFAULT 'QUOTE_PENDING',
                        doorstepPin TEXT NOT NULL DEFAULT '4829',
                        isDoorstepVerified INTEGER NOT NULL DEFAULT 0,
                        materialAdvanceRequested INTEGER NOT NULL DEFAULT 0,
                        materialAdvanceAmount REAL NOT NULL DEFAULT 0.0,
                        materialAdvanceReceiptUrl TEXT NOT NULL DEFAULT '',
                        materialAdvanceStatus TEXT NOT NULL DEFAULT 'NONE',
                        isSosTriggered INTEGER NOT NULL DEFAULT 0,
                        calloutFee REAL NOT NULL DEFAULT 5.0,
                        jobType TEXT NOT NULL DEFAULT 'ONE_TIME',
                        recurringSchedule TEXT NOT NULL DEFAULT '',
                        recurringStartDate TEXT NOT NULL DEFAULT '',
                        recurringContractProposal TEXT NOT NULL DEFAULT '',
                        recurringNegotiatedRate REAL NOT NULL DEFAULT 0.0,
                        recurringProposalStatus TEXT NOT NULL DEFAULT 'NONE',
                        FOREIGN KEY (assignedProviderId) REFERENCES service_providers(id) ON DELETE SET NULL
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO job_posts_new SELECT * FROM job_posts")
                database.execSQL("DROP TABLE job_posts")
                database.execSQL("ALTER TABLE job_posts_new RENAME TO job_posts")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_job_posts_assignedProviderId ON job_posts(assignedProviderId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_job_posts_status ON job_posts(status)")

                // Recreate chat_messages with foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        senderName TEXT NOT NULL,
                        isFromUser INTEGER NOT NULL,
                        message TEXT NOT NULL,
                        timestampText TEXT NOT NULL,
                        isSystemNotification INTEGER NOT NULL,
                        FOREIGN KEY (jobId) REFERENCES job_posts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO chat_messages_new SELECT * FROM chat_messages")
                database.execSQL("DROP TABLE chat_messages")
                database.execSQL("ALTER TABLE chat_messages_new RENAME TO chat_messages")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_chat_messages_jobId ON chat_messages(jobId)")

                // Recreate disputes with foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS disputes_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        reportedBy TEXT NOT NULL,
                        reportedUserId TEXT NOT NULL,
                        disputeReason TEXT NOT NULL,
                        description TEXT NOT NULL,
                        evidenceUrlsCsv TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'OPEN',
                        adminNotes TEXT NOT NULL DEFAULT '',
                        resolutionType TEXT NOT NULL DEFAULT '',
                        resolutionAmount REAL NOT NULL DEFAULT 0.0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedBy TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (jobId) REFERENCES job_posts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO disputes_new SELECT * FROM disputes")
                database.execSQL("DROP TABLE disputes")
                database.execSQL("ALTER TABLE disputes_new RENAME TO disputes")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_disputes_jobId ON disputes(jobId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_disputes_status ON disputes(status)")

                // Recreate ratings with foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS ratings_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        fromUserId TEXT NOT NULL,
                        toUserId TEXT NOT NULL,
                        rating INTEGER NOT NULL,
                        review TEXT NOT NULL DEFAULT '',
                        isFromClient INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (jobId) REFERENCES job_posts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO ratings_new SELECT * FROM ratings")
                database.execSQL("DROP TABLE ratings")
                database.execSQL("ALTER TABLE ratings_new RENAME TO ratings")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ratings_jobId ON ratings(jobId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ratings_toUserId ON ratings(toUserId)")

                // Recreate notifications with foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notifications_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        userId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        title TEXT NOT NULL,
                        body TEXT NOT NULL,
                        jobId TEXT NOT NULL DEFAULT '',
                        isRead INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (userId) REFERENCES user_profiles(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO notifications_new SELECT * FROM notifications")
                database.execSQL("DROP TABLE notifications")
                database.execSQL("ALTER TABLE notifications_new RENAME TO notifications")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_userId ON notifications(userId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_createdAt ON notifications(createdAt)")

                // Recreate job_status_audit with foreign key
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS job_status_audit_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        jobId TEXT NOT NULL,
                        fromStatus TEXT NOT NULL,
                        toStatus TEXT NOT NULL,
                        changedBy TEXT NOT NULL,
                        changedAt INTEGER NOT NULL DEFAULT 0,
                        reason TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (jobId) REFERENCES job_posts(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO job_status_audit_new SELECT * FROM job_status_audit")
                database.execSQL("DROP TABLE job_status_audit")
                database.execSQL("ALTER TABLE job_status_audit_new RENAME TO job_status_audit")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_job_status_audit_jobId ON job_status_audit(jobId)")

                // Recreate reports with foreign keys
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reports_new (
                        id TEXT PRIMARY KEY NOT NULL,
                        reportedBy TEXT NOT NULL,
                        reportedUserId TEXT NOT NULL,
                        reportedUserName TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        evidenceUrlsCsv TEXT NOT NULL DEFAULT '',
                        status TEXT NOT NULL DEFAULT 'PENDING',
                        adminNotes TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedAt INTEGER NOT NULL DEFAULT 0,
                        resolvedBy TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY (reportedBy) REFERENCES user_profiles(id) ON DELETE CASCADE,
                        FOREIGN KEY (reportedUserId) REFERENCES user_profiles(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("INSERT INTO reports_new SELECT * FROM reports")
                database.execSQL("DROP TABLE reports")
                database.execSQL("ALTER TABLE reports_new RENAME TO reports")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reports_reportedBy ON reports(reportedBy)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reports_reportedUserId ON reports(reportedUserId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_reports_status ON reports(status)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // AUTH-006: Add unique index on phoneNumber to prevent duplicate accounts
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_user_profiles_phoneNumber ON user_profiles(phoneNumber)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // TASK-002: Add isOnboardingCompleted column to user_profiles with default 1 for existing data preservation
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN isOnboardingCompleted INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // COMM-002: Community feature removal — drop the neighborhood_posts table
                database.execSQL("DROP TABLE IF EXISTS neighborhood_posts")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // REG-003: Worker backup contact fields — optional, default empty for existing users
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN backupContactName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE user_profiles ADD COLUMN backupContactPhone TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * REG-002: Guarantor verification requirements.
         *
         * Additive only — the KYC-003 columns are untouched. Every new column defaults to
         * an empty/neutral value so previously registered workers keep working and simply
         * see the guarantor step as NOT_STARTED until they complete it.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Guarantor NIN + the two required photographs + consent + admin NIN state.
                database.execSQL("ALTER TABLE guarantors ADD COLUMN nationalIdNumber TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE guarantors ADD COLUMN guarantorPhotoStoragePath TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE guarantors ADD COLUMN workerWithGuarantorPhotoStoragePath TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE guarantors ADD COLUMN consentProvided INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE guarantors ADD COLUMN ninVerificationStatus TEXT NOT NULL DEFAULT 'PENDING'")
                database.execSQL("ALTER TABLE guarantors ADD COLUMN submittedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE guarantors ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                // Guarantor contact-number lookups (duplicate-guarantor detection).
                database.execSQL("CREATE INDEX IF NOT EXISTS index_guarantors_phoneNumber ON guarantors(phoneNumber)")
                // Group photograph + guarantor step state on the worker's KYC record.
                database.execSQL("ALTER TABLE worker_kyc ADD COLUMN groupPhotoStoragePath TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE worker_kyc ADD COLUMN guarantorStatus TEXT NOT NULL DEFAULT 'NOT_STARTED'")
                database.execSQL("ALTER TABLE worker_kyc ADD COLUMN guarantorsSubmittedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE worker_kyc ADD COLUMN guarantorReviewFeedback TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * C2-2 Fix #4 (audit finding **F4**): job authorship / `postedByUid`.
         *
         * Additive only — no existing job data is deleted, moved or rewritten. The column is
         * NULLABLE and deliberately added without a default:
         *
         * Pre-existing jobs have NO trustworthy owner. Their `postedByResident` is display text
         * ("Robert K. (Sunnyside)"), not an identity, and the device's newest Room profile is not
         * evidence of who posted a job weeks ago. Back-filling either one would silently hand
         * historical jobs to a random account — an ownership/earnings/liability forgery. So the
         * column stays NULL for them, which means "unknown owner": such a job is nobody's job,
         * and ownership comparisons fail closed.
         *
         * Every NEW job created after this migration carries a validated
         * `FirebaseAuth.currentUser.uid` (addJobPost fails closed when there is none).
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE job_posts ADD COLUMN postedByUid TEXT")
                // Ownership lookups ("my jobs", can this user manage this job) are indexed.
                database.execSQL("CREATE INDEX IF NOT EXISTS index_job_posts_postedByUid ON job_posts(postedByUid)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "skills_exchange_database"
                )
                    .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
