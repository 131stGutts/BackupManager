package com.ayer.backupmanager.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ayer.backupmanager.R
import com.ayer.backupmanager.data.SyncRule
import com.ayer.backupmanager.network.ConnectionResult
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditScreen(
    viewModel: RuleViewModel,
    rule: SyncRule?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var protocolType by remember { mutableStateOf(rule?.protocolType ?: "SMB") }
    var sourceUri by remember { mutableStateOf(rule?.sourceUri ?: "") }
    var ip by remember { mutableStateOf(rule?.remoteIp ?: "") }
    var port by remember { mutableStateOf(rule?.remotePort?.toString() ?: when(protocolType) {
        "SMB" -> "445"
        "SFTP" -> "22"
        else -> "21"
    }) }
    var path by remember { mutableStateOf(rule?.remotePath ?: "") }
    var user by remember { mutableStateOf(rule?.username ?: "") }
    var pass by remember { mutableStateOf(rule?.password ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    
    var deleteAfterSync by remember { mutableStateOf(rule?.deleteAfterSync ?: false) }
    var excludedFolders by remember { mutableStateOf(rule?.excludedFolders ?: "") }
    var showExclusionDialog by remember { mutableStateOf(false) }
    
    var wifiOnly by remember { mutableStateOf(rule?.wifiOnly ?: true) }
    var allowRoaming by remember { mutableStateOf(rule?.allowMobileRoaming ?: false) }
    var scheduleType by remember { mutableStateOf(rule?.scheduleType ?: "Manual") }
    
    var scheduleHour by remember { mutableStateOf(rule?.scheduleHour ?: 0) }
    var scheduleMinute by remember { mutableStateOf(rule?.scheduleMinute ?: 0) }
    var scheduleDayOfWeek by remember { mutableStateOf(rule?.scheduleDayOfWeek ?: 1) }
    var scheduleDayOfMonth by remember { mutableStateOf(rule?.scheduleDayOfMonth ?: 1) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            sourceUri = it.toString()
        }
    }

    if (showExclusionDialog && sourceUri.isNotEmpty()) {
        FolderExclusionDialog(
            sourceUri = sourceUri,
            initiallyExcluded = excludedFolders.split(";").filter { it.isNotEmpty() },
            onDismiss = { showExclusionDialog = false },
            onConfirm = { list ->
                excludedFolders = list.joinToString(";")
                showExclusionDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (rule == null) stringResource(R.string.new_rule) else stringResource(R.string.edit_rule), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = name, 
                onValueChange = { name = it }, 
                label = { Text(stringResource(R.string.rule_name)) }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Text(stringResource(R.string.protocol), style = MaterialTheme.typography.titleSmall)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProtocolOption("SMB", protocolType == "SMB") { 
                    protocolType = "SMB"; if (port == "21" || port == "22") port = "445" 
                }
                ProtocolOption("FTP", protocolType == "FTP") { 
                    protocolType = "FTP"; if (port == "445" || port == "22") port = "21" 
                }
                ProtocolOption("FTPS", protocolType == "FTPS") { 
                    protocolType = "FTPS"; if (port == "445" || port == "22") port = "21" 
                }
                ProtocolOption("SFTP", protocolType == "SFTP") { 
                    protocolType = "SFTP"; if (port == "445" || port == "21") port = "22" 
                }
            }

            Button(onClick = { safLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (sourceUri.isEmpty()) stringResource(R.string.select_local_folder) else stringResource(R.string.folder_selected))
            }
            if (sourceUri.isNotEmpty()) {
                Text(text = sourceUri, style = MaterialTheme.typography.bodySmall)
                
                OutlinedButton(
                    onClick = { showExclusionDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.excluded_folders))
                }
                if (excludedFolders.isNotEmpty()) {
                    Text(
                        text = excludedFolders.replace(";", ", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            TextField(
                value = ip, 
                onValueChange = { ip = it }, 
                label = { Text(stringResource(R.string.remote_ip)) }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            TextField(
                value = port, 
                onValueChange = { port = it }, 
                label = { Text(stringResource(R.string.port)) }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            TextField(
                value = path, 
                onValueChange = { path = it }, 
                label = { Text(stringResource(R.string.remote_path)) }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            TextField(
                value = user, 
                onValueChange = { user = it }, 
                label = { Text(stringResource(R.string.username)) }, 
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            
            TextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(stringResource(R.string.password)) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                    }
                }
            )

            Button(
                onClick = {
                    val tempRule = SyncRule(
                        name = name, sourceUri = sourceUri, protocolType = protocolType,
                        remoteIp = ip, remotePort = port.toIntOrNull() ?: 0, remotePath = path,
                        username = user, password = pass, deleteAfterSync = deleteAfterSync,
                        excludedFolders = excludedFolders,
                        wifiOnly = wifiOnly, allowMobileRoaming = allowRoaming, scheduleType = scheduleType
                    )
                    scope.launch {
                        val result = viewModel.testConnection(tempRule)
                        val msg = when (result) {
                            is ConnectionResult.Success -> context.getString(R.string.connection_success)
                            is ConnectionResult.Failure -> context.getString(R.string.connection_failed, result.message)
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(stringResource(R.string.test_connection))
            }

            HorizontalDivider()

            Text(stringResource(R.string.network_scheduling), style = MaterialTheme.typography.titleSmall)
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = wifiOnly, onCheckedChange = { wifiOnly = it })
                Text(stringResource(R.string.wifi_only))
            }

            val roamingColor = if (wifiOnly) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = allowRoaming, onCheckedChange = { allowRoaming = it }, enabled = !wifiOnly)
                Text(stringResource(R.string.allow_mobile_roaming), color = roamingColor)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = deleteAfterSync, onCheckedChange = { deleteAfterSync = it })
                Text(stringResource(R.string.delete_after_success))
            }

            Text(stringResource(R.string.schedule), style = MaterialTheme.typography.bodyMedium)
            val schedules = listOf("Manual", "Daily", "Weekly", "Monthly")
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(scheduleType)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    schedules.forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { scheduleType = s; expanded = false })
                    }
                }
            }

            if (scheduleType != "Manual") {
                Button(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        scheduleHour = h
                        scheduleMinute = m
                    }, scheduleHour, scheduleMinute, true).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("${stringResource(R.string.time)}: ${String.format(Locale.getDefault(), "%02d:%02d", scheduleHour, scheduleMinute)}")
                }

                if (scheduleType == "Weekly") {
                    var weekExpanded by remember { mutableStateOf(false) }
                    val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { weekExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("${stringResource(R.string.day)}: ${days[scheduleDayOfWeek - 1]}")
                        }
                        DropdownMenu(expanded = weekExpanded, onDismissRequest = { weekExpanded = false }) {
                            days.forEachIndexed { index, d ->
                                DropdownMenuItem(text = { Text(d) }, onClick = { scheduleDayOfWeek = index + 1; weekExpanded = false })
                            }
                        }
                    }
                }

                if (scheduleType == "Monthly") {
                    TextField(
                        value = scheduleDayOfMonth.toString(),
                        onValueChange = { scheduleDayOfMonth = it.toIntOrNull() ?: 1 },
                        label = { Text(stringResource(R.string.day)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val newRule = SyncRule(
                        id = rule?.id ?: 0L,
                        name = name,
                        sourceUri = sourceUri,
                        protocolType = protocolType,
                        remoteIp = ip,
                        remotePort = port.toIntOrNull() ?: 0,
                        remotePath = path,
                        username = user,
                        password = pass,
                        deleteAfterSync = deleteAfterSync,
                        excludedFolders = excludedFolders,
                        wifiOnly = wifiOnly,
                        allowMobileRoaming = allowRoaming,
                        scheduleType = scheduleType,
                        scheduleHour = scheduleHour,
                        scheduleMinute = scheduleMinute,
                        scheduleDayOfWeek = scheduleDayOfWeek,
                        scheduleDayOfMonth = scheduleDayOfMonth,
                        isActive = rule?.isActive ?: true
                    )
                    viewModel.saveRule(newRule)
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = name.isNotEmpty() && sourceUri.isNotEmpty() && ip.isNotEmpty()
            ) {
                Text(stringResource(R.string.save_rule))
            }
        }
    }
}

@Composable
fun ProtocolOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 4.dp))
    }
}
