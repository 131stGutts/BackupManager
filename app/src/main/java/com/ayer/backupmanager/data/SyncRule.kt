package com.ayer.backupmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_rules")
data class SyncRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceUri: String,
    val protocolType: String, // "SMB", "FTP", "FTPS", "SFTP"
    val remoteIp: String,
    val remotePort: Int,
    val remotePath: String,
    val username: String,
    val password: String,
    val deleteAfterSync: Boolean,
    val wifiOnly: Boolean = true,
    val allowMobileRoaming: Boolean = false,
    val scheduleType: String = "Manual", // "Manual", "Daily", "Weekly", "Monthly"
    val scheduleHour: Int = 0,
    val scheduleMinute: Int = 0,
    val scheduleDayOfWeek: Int = 1,
    val scheduleDayOfMonth: Int = 1,
    val cronExpression: String? = null,
    val isActive: Boolean = true,
    val excludedFolders: String? = "", // Nullable to handle JSON import from older versions
    val lastSyncStatus: String = "Idle",
    val lastSyncTimestamp: Long = 0,
    val nextExecutionTimestamp: Long = 0
)
