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
        NeighborhoodPostEntity::class,
        UserProfileEntity::class,
        WorkerKycEntity::class,
        GuarantorEntity::class,
        WorkerTrainingEntity::class,
        JobQuoteEntity::class,
        PendingActionEntity::class,
        DisputeEntity::class,
        RatingEntity::class,
        NotificationEntity::class,
        JobStatusAuditEntity::class
    ],
    version = 12,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serviceProviderDao(): ServiceProviderDao
    abstract fun skillEndorsementDao(): SkillEndorsementDao
    abstract fun jobPostDao(): JobPostDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun neighborhoodPostDao(): NeighborhoodPostDao
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "skills_exchange_database"
                )
                    .addMigrations(MIGRATION_11_12)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
