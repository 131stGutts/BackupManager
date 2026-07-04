package com.ayer.backupmanager.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs WHERE ruleId = :ruleId ORDER BY timestamp DESC LIMIT 50")
    fun getLogsForRule(ruleId: Long): Flow<List<SyncLog>>

    @Insert
    suspend fun insertLog(log: SyncLog)

    @Query("DELETE FROM sync_logs WHERE ruleId = :ruleId")
    suspend fun clearLogsForRule(ruleId: Long)
}
