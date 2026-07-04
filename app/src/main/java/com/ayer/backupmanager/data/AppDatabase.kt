package com.ayer.backupmanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SyncRule::class, SyncLog::class, SyncedFile::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncRuleDao(): SyncRuleDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun syncedFileDao(): SyncedFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "backup_manager_database"
                )
                .fallbackToDestructiveMigration() // Version 6 for SyncedFile tracking
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
