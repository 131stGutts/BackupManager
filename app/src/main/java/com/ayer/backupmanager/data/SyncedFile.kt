package com.ayer.backupmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "synced_files",
    indices = [Index(value = ["ruleId", "relativeFilePath"], unique = true)]
)
data class SyncedFile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val relativeFilePath: String,
    val timestamp: Long = System.currentTimeMillis()
)
