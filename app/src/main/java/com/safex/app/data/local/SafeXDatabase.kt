package com.safex.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.safex.app.data.NewsArticleDao
import com.safex.app.data.NewsArticleEntity

@Database(
    entities = [AlertEntity::class, NewsArticleEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SafeXDatabase : RoomDatabase() {

    abstract fun alertDao(): AlertDao
    abstract fun newsArticleDao(): NewsArticleDao

    companion object {
        @Volatile
        private var INSTANCE: SafeXDatabase? = null

        fun getInstance(context: Context): SafeXDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SafeXDatabase::class.java,
                    "safex.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
