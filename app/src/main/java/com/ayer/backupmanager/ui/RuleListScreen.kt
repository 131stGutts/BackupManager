package com.ayer.backupmanager.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.WorkInfo
import com.ayer.backupmanager.R
import com.ayer.backupmanager.data.SyncRule
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    viewModel: RuleViewModel,
    onAddRule: () -> Unit,
    onEditRule: (SyncRule) -> Unit,
    onViewLogs: (SyncRule) -> Unit
) {
    val rules by viewModel.allRules.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.importResult.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { os ->
                viewModel.exportRules(os)
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { `is` ->
                viewModel.importRules(`is`)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.app_name).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.export_rules)) },
                            onClick = { 
                                menuExpanded = false
                                exportLauncher.launch("rules_backup.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.import_rules)) },
                            onClick = { 
                                menuExpanded = false
                                importLauncher.launch(arrayOf("application/json", "application/octet-stream"))
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { rules.forEach { if (it.isActive) viewModel.triggerSync(it.id) } },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = "Run All Now")
                }
                FloatingActionButton(onClick = onAddRule) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_rule))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = padding,
            modifier = Modifier.fillMaxSize()
        ) {
            items(rules) { rule ->
                RuleItem(
                    viewModel = viewModel,
                    rule = rule,
                    onSync = { viewModel.triggerSync(rule.id) },
                    onDelete = { viewModel.deleteRule(rule) },
                    onClick = { onEditRule(rule) },
                    onViewLogs = { onViewLogs(rule) },
                    onToggleActive = { viewModel.toggleRuleActive(rule) }
                )
            }
        }
    }
}

@Composable
fun RuleItem(
    viewModel: RuleViewModel,
    rule: SyncRule,
    onSync: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onViewLogs: () -> Unit,
    onToggleActive: () -> Unit
) {
    val workInfos by viewModel.getWorkInfo(rule.id).observeAsState(emptyList())
    val activeWork = workInfos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rule.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val statusText = when (rule.lastSyncStatus) {
                        "Idle" -> stringResource(R.string.status_idle)
                        "In Progress" -> stringResource(R.string.status_in_progress)
                        "Success" -> stringResource(R.string.status_success)
                        "Error" -> stringResource(R.string.status_error)
                        "Error: Network Policy" -> stringResource(R.string.status_error_network)
                        "Error: Source Dir" -> stringResource(R.string.status_error_source)
                        "Error: Connection" -> stringResource(R.string.status_error_connection)
                        else -> rule.lastSyncStatus
                    }
                    
                    Text(
                        text = "${rule.protocolType} - $statusText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleActive) {
                        Icon(
                            imageVector = if (rule.isActive) Icons.Default.StopCircle else Icons.Default.PlayCircle,
                            contentDescription = if (rule.isActive) "Stop" else "Start",
                            tint = if (rule.isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    IconButton(onClick = onSync) {
                        Icon(
                            Icons.Default.FastForward,
                            contentDescription = "Sync Now",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            activeWork?.progress?.let { progressData ->
                val progressPercent = progressData.getInt("progress", -1)
                val current = progressData.getInt("current", 0)
                val total = progressData.getInt("total", 0)

                if (progressPercent >= 0) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        LinearProgressIndicator(
                            progress = progressPercent / 100f,
                            modifier = Modifier.fillMaxWidth().clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                        Text(
                            text = stringResource(R.string.sync_progress, current, total, progressPercent),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
            
            if (rule.isActive && rule.nextExecutionTimestamp > 0 && (activeWork == null || activeWork.state != WorkInfo.State.RUNNING)) {
                val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                val nextDate = sdf.format(Date(rule.nextExecutionTimestamp))
                Text(
                    text = stringResource(R.string.next_sync, nextDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onViewLogs) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "History")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}
