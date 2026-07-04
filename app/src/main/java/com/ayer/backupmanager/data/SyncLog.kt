package com.ayer.backupmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "sync_logs",
    foreignKeys = [
        ForeignKey(
            entity = SyncRule::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SyncLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // "Success", "Error"
    val message: String,
    val filesTransferred: Int = 0,
    val totalSize: Long = 0
)
