package com.ayer.backupmanager.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ayer.backupmanager.data.AppDatabase
import com.ayer.backupmanager.data.SyncLog
import com.ayer.backupmanager.data.SyncRule
import com.ayer.backupmanager.data.SyncedFile
import com.ayer.backupmanager.network.*
import com.ayer.backupmanager.utils.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = AppDatabase.getDatabase(context)
    private val ruleDao = db.syncRuleDao()
    private val logDao = db.syncLogDao()
    private val syncedFileDao = db.syncedFileDao()

    private var totalFilesToProcess = 0
    private var processedFiles = 0
    private var skippedFiles = 0
    private var errorCount = 0

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ruleId = inputData.getLong("ruleId", -1)
        if (ruleId == -1L) return@withContext Result.failure()

        val rule = ruleDao.getRuleById(ruleId) ?: return@withContext Result.failure()

        if (!checkNetwork(rule)) {
            return@withContext Result.retry()
        }

        updateStatus(rule, "In Progress")
        setProgress(workDataOf("progress" to 0, "current" to 0, "total" to 0))

        val client: RemoteClient = when (rule.protocolType) {
            "SMB" -> SmbClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            "FTP" -> FtpClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            "FTPS" -> FtpsClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            "SFTP" -> SftpClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            else -> return@withContext Result.failure()
        }

        try {
            if (client.connect()) {
                val sourceUri = Uri.parse(rule.sourceUri)
                val sourceDir = DocumentFile.fromTreeUri(applicationContext, sourceUri)

                if (sourceDir != null && sourceDir.isDirectory) {
                    val exclusions = rule.excludedFolders?.split(";")?.filter { it.isNotEmpty() } ?: emptyList()
                    
                    // PERFORMANCE OPTIMIZATION: Filter by last modification date
                    // Margin of 5s to account for filesystem differences
                    val lastSuccessTime = if (rule.lastSyncStatus == "Success") rule.lastSyncTimestamp - 5000 else 0L
                    
                    // 1. Scan for total files that actually need a check
                    totalFilesToProcess = countFilesToProcessRecursive(sourceDir, "", exclusions, lastSuccessTime)
                    setProgress(workDataOf("progress" to 0, "current" to 0, "total" to totalFilesToProcess))
                    
                    // 2. Perform backup
                    val success = backupRecursive(sourceDir, "", client, rule, exclusions, lastSuccessTime)
                    
                    if (success) {
                        updateStatus(rule, "Success")
                        addLog(rule.id, "Success", "Sync completed: $processedFiles uploaded, $skippedFiles skipped")
                        NotificationHelper.showSyncNotification(applicationContext, "Backup Success", "Rule: ${rule.name} finished.")
                        Result.success()
                    } else {
                        updateStatus(rule, "Error")
                        addLog(rule.id, "Error", "Sync finished with $errorCount errors. $processedFiles files transferred.")
                        NotificationHelper.showSyncNotification(applicationContext, "Backup Warning", "Rule: ${rule.name} had errors.", true)
                        Result.failure()
                    }
                } else {
                    updateStatus(rule, "Error: Source Dir")
                    addLog(rule.id, "Error", "Local source directory not found or inaccessible")
                    Result.failure()
                }
            } else {
                updateStatus(rule, "Error: Connection")
                addLog(rule.id, "Error", "Failed to connect to remote server: ${rule.protocolType}://${rule.remoteIp}")
                Result.failure()
            }
        } catch (e: Exception) {
            Log.e("BackupManager", "SyncWorker Exception", e)
            updateStatus(rule, "Error: Exception")
            addLog(rule.id, "Error", "Exception: ${e.localizedMessage}")
            Result.failure()
        } finally {
            client.disconnect()
        }
    }

    private fun countFilesToProcessRecursive(directory: DocumentFile, currentPath: String, exclusions: List<String>, lastSuccessTime: Long): Int {
        var count = 0
        directory.listFiles().forEach { file ->
            val fileName = file.name ?: ""
            val relativePath = if (currentPath.isEmpty()) fileName else "$currentPath/$fileName"
            
            if (exclusions.contains(relativePath)) return@forEach

            if (file.isDirectory) {
                count += countFilesToProcessRecursive(file, relativePath, exclusions, lastSuccessTime)
            } else if (file.isFile) {
                // Only count files modified after the last successful sync
                if (file.lastModified() > lastSuccessTime) {
                    count++
                }
            }
        }
        return count
    }

    private suspend fun backupRecursive(
        directory: DocumentFile,
        currentRelativePath: String,
        client: RemoteClient,
        rule: SyncRule,
        exclusions: List<String>,
        lastSuccessTime: Long
    ): Boolean {
        var allSuccessful = true
        val files = directory.listFiles()
        
        for (file in files) {
            val fileName = file.name ?: "unnamed"
            val remotePathForThisItem = if (currentRelativePath.isEmpty()) fileName else "$currentRelativePath/$fileName"
            
            if (exclusions.contains(remotePathForThisItem)) continue

            if (file.isDirectory) {
                client.makeDirectory(remotePathForThisItem)
                val success = backupRecursive(file, remotePathForThisItem, client, rule, exclusions, lastSuccessTime)
                if (!success) allSuccessful = false
            } else if (file.isFile) {
                // OPTIMIZATION 1: Quick Filter by timestamp
                if (file.lastModified() <= lastSuccessTime) {
                    // This file hasn't changed since last success, we don't even count it in progress
                    continue
                }

                // OPTIMIZATION 2: Check Local Database Cache
                val alreadySyncedLocally = syncedFileDao.isFileSynced(rule.id, remotePathForThisItem)
                if (alreadySyncedLocally) {
                    skippedFiles++
                    updateProgress()
                    continue
                }

                // OPTIMIZATION 3: Check Remote Server
                if (client.fileExists(remotePathForThisItem)) {
                    syncedFileDao.markAsSynced(SyncedFile(ruleId = rule.id, relativeFilePath = remotePathForThisItem))
                    skippedFiles++
                    updateProgress()
                    continue
                }

                // PERFORM UPLOAD
                val success = uploadAndCleanup(file, remotePathForThisItem, client, rule.deleteAfterSync)
                if (success) {
                    syncedFileDao.markAsSynced(SyncedFile(ruleId = rule.id, relativeFilePath = remotePathForThisItem))
                    processedFiles++
                    updateProgress()
                } else {
                    errorCount++
                    allSuccessful = false
                    updateProgress()
                    Log.e("BackupManager", "Failed to upload: $remotePathForThisItem")
                }
            }
        }
        return allSuccessful
    }

    private suspend fun updateProgress() {
        val totalProcessed = processedFiles + skippedFiles
        val progressPercent = if (totalFilesToProcess > 0) (totalProcessed * 100) / totalFilesToProcess else 0
        setProgress(workDataOf("progress" to progressPercent, "current" to totalProcessed, "total" to totalFilesToProcess))
    }

    private suspend fun uploadAndCleanup(
        file: DocumentFile,
        remoteFilePath: String,
        client: RemoteClient,
        deleteAfterSync: Boolean
    ): Boolean {
        return try {
            applicationContext.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                val uploadSuccess = client.uploadFile(inputStream, remoteFilePath)
                if (uploadSuccess && deleteAfterSync) {
                    file.delete()
                }
                uploadSuccess
            } ?: false
        } catch (e: Exception) {
            Log.e("BackupManager", "Upload error for $remoteFilePath", e)
            false
        }
    }

    private fun checkNetwork(rule: SyncRule): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isMobile = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val isRoaming = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        if (rule.wifiOnly && !isWifi) return false
        if (isMobile && isRoaming && !rule.allowMobileRoaming) return false
        return true
    }

    private suspend fun updateStatus(rule: SyncRule, status: String) {
        val updatedRule = rule.copy(
            lastSyncStatus = status,
            lastSyncTimestamp = System.currentTimeMillis()
        )
        ruleDao.updateRule(updatedRule)
    }

    private suspend fun addLog(ruleId: Long, status: String, message: String) {
        logDao.insertLog(SyncLog(ruleId = ruleId, status = status, message = message))
    }
}
