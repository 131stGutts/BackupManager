package com.ayer.backupmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.ayer.backupmanager.data.AppDatabase
import com.ayer.backupmanager.data.SyncRule
import com.ayer.backupmanager.network.*
import com.ayer.backupmanager.utils.RuleJsonHandler
import com.ayer.backupmanager.utils.ScheduleUtils
import com.ayer.backupmanager.worker.SyncWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class RuleViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val ruleDao = db.syncRuleDao()
    private val logDao = db.syncLogDao()
    private val workManager = WorkManager.getInstance(application)

    val allRules: StateFlow<List<SyncRule>> = ruleDao.getAllRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _importResult = MutableSharedFlow<String>()
    val importResult: SharedFlow<String> = _importResult

    fun saveRule(rule: SyncRule) {
        viewModelScope.launch {
            val nextExec = ScheduleUtils.calculateNextExecution(rule)
            val ruleWithNext = rule.copy(nextExecutionTimestamp = nextExec)
            
            val savedRule = if (rule.id == 0L) {
                val newId = ruleDao.insertRule(ruleWithNext)
                ruleWithNext.copy(id = newId)
            } else {
                ruleDao.updateRule(ruleWithNext)
                ruleWithNext
            }
            scheduleRule(savedRule)
        }
    }

    fun toggleRuleActive(rule: SyncRule) {
        viewModelScope.launch {
            val newActive = !rule.isActive
            val nextExec = if (newActive) ScheduleUtils.calculateNextExecution(rule.copy(isActive = true)) else 0L
            val updatedRule = rule.copy(isActive = newActive, nextExecutionTimestamp = nextExec)
            ruleDao.updateRule(updatedRule)
            if (newActive) {
                scheduleRule(updatedRule)
            } else {
                workManager.cancelUniqueWork("SyncRule_${rule.id}")
            }
        }
    }
    
    private fun scheduleRule(rule: SyncRule) {
        val workName = "SyncRule_${rule.id}"
        workManager.cancelUniqueWork(workName)
        
        if (rule.scheduleType == "Manual" || !rule.isActive) return

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (rule.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val delay = ScheduleUtils.getInitialDelay(rule)

        val request = when (rule.scheduleType) {
            "Daily" -> PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.DAYS)
            "Weekly" -> PeriodicWorkRequestBuilder<SyncWorker>(7, TimeUnit.DAYS)
            "Monthly" -> PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.DAYS)
            else -> return
        }
        .setConstraints(constraints)
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf("ruleId" to rule.id))
        .addTag("SyncRule_${rule.id}")
        .build()

        workManager.enqueueUniquePeriodicWork(workName, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun deleteRule(rule: SyncRule) {
        viewModelScope.launch {
            workManager.cancelUniqueWork("SyncRule_${rule.id}")
            ruleDao.deleteRule(rule)
        }
    }

    fun triggerSync(ruleId: Long) {
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf("ruleId" to ruleId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag("SyncRule_$ruleId")
            .build()
        workManager.enqueue(syncRequest)
    }

    fun getLogsForRule(ruleId: Long) = logDao.getLogsForRule(ruleId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getWorkInfo(ruleId: Long) = workManager.getWorkInfosByTagLiveData("SyncRule_$ruleId")

    suspend fun testConnection(rule: SyncRule): ConnectionResult {
        val client = when (rule.protocolType) {
            "SMB" -> SmbClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            "FTP" -> FtpClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            "FTPS" -> FtpsClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            "SFTP" -> SftpClient(rule.remoteIp, rule.remotePort, rule.username, rule.password, rule.remotePath)
            else -> return ConnectionResult.Failure("Invalid Protocol")
        }
        return client.testConnection()
    }

    fun exportRules(outputStream: OutputStream) {
        viewModelScope.launch {
            val rules = allRules.value
            RuleJsonHandler.exportRules(rules, outputStream)
        }
    }

    fun importRules(inputStream: InputStream) {
        viewModelScope.launch {
            try {
                val rules = RuleJsonHandler.importRules(inputStream)
                if (rules.isEmpty()) {
                    _importResult.emit("Import failed: No valid rules found")
                    return@launch
                }
                rules.forEach {
                    saveRule(it)
                }
                _importResult.emit("Successfully imported ${rules.size} rules")
            } catch (e: Exception) {
                e.printStackTrace()
                _importResult.emit("Import failed: ${e.localizedMessage}")
            }
        }
    }
}
