package com.ayer.backupmanager.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncRuleDao {
    @Query("SELECT * FROM sync_rules")
    fun getAllRules(): Flow<List<SyncRule>>

    @Query("SELECT * FROM sync_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): SyncRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: SyncRule): Long

    @Update
    suspend fun updateRule(rule: SyncRule)

    @Delete
    suspend fun deleteRule(rule: SyncRule)
}
