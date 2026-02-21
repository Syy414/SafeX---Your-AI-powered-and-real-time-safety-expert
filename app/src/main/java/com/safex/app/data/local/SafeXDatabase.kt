package com.safex.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.safex.app.data.NewsArticleDao
import com.safex.app.data.NewsArticleEntity
import com.safex.app.data.NewsReadHistoryEntity

@Database(
    entities = [AlertEntity::class, NewsArticleEntity::class, NewsReadHistoryEntity::class],
    version = 6,
    exportSchema = false
)
abstract class SafeXDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao
    abstract fun newsArticleDao(): NewsArticleDao

    companion object {
        @Volatile
        private var INSTANCE: SafeXDatabase? = null

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add new columns to alerts table
                database.execSQL("ALTER TABLE alerts ADD COLUMN sender TEXT")
                database.execSQL("ALTER TABLE alerts ADD COLUMN fullMessage TEXT")
                database.execSQL("ALTER TABLE alerts ADD COLUMN geminiAnalysis TEXT")
                database.execSQL("ALTER TABLE alerts ADD COLUMN analysisLanguage TEXT")
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alerts ADD COLUMN heuristicScore REAL")
                database.execSQL("ALTER TABLE alerts ADD COLUMN tfliteScore REAL")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE news_articles ADD COLUMN warningsAndTips TEXT")
            }
        }

        fun getInstance(context: Context): SafeXDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SafeXDatabase::class.java,
                    "safex.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
