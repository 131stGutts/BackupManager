package com.ayer.backupmanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncedFileDao {
    @Query("SELECT EXISTS(SELECT 1 FROM synced_files WHERE ruleId = :ruleId AND relativeFilePath = :path)")
    suspend fun isFileSynced(ruleId: Long, path: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsSynced(file: SyncedFile)

    @Query("DELETE FROM synced_files WHERE ruleId = :ruleId")
    suspend fun clearHistoryForRule(ruleId: Long)
}
