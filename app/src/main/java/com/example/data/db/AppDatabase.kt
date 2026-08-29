package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
        JobQuoteEntity::class
    ],
    version = 10,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "skills_exchange_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
