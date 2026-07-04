package com.ayer.backupmanager.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.ayer.backupmanager.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FolderExclusionDialog(
    sourceUri: String,
    initiallyExcluded: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val rootUri = Uri.parse(sourceUri)
    
    var currentExclusions by remember { mutableStateOf(initiallyExcluded.toSet()) }
    var folderList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceUri) {
        isLoading = true
        errorMessage = null
        
        withContext(Dispatchers.IO) {
            try {
                Log.d("BackupManager", "Listing immediate subfolders for URI: $sourceUri")
                
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                if (rootDoc != null && rootDoc.isDirectory && rootDoc.canRead()) {
                    val list = mutableListOf<Pair<String, String>>()
                    
                    // Simple, non-recursive list of immediate children
                    rootDoc.listFiles().forEach { file ->
                        if (file.isDirectory) {
                            val fileName = file.name ?: ""
                            if (fileName.isNotEmpty() && fileName != "." && fileName != "..") {
                                // For immediate subfolders, the relative path IS the folder name
                                list.add(fileName to fileName)
                            }
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        folderList = list.sortedBy { it.first }
                        Log.d("BackupManager", "Found ${folderList.size} immediate folders")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Cannot access source folder. Please re-select it."
                    }
                }
            } catch (e: Exception) {
                Log.e("BackupManager", "Error listing folders", e)
                withContext(Dispatchers.Main) {
                    errorMessage = e.localizedMessage
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_exclusions)) },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 400.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (folderList.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_subfolders),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn {
                        items(folderList) { (name, path) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = currentExclusions.contains(path),
                                    onCheckedChange = { checked ->
                                        currentExclusions = if (checked) {
                                            currentExclusions + path
                                        } else {
                                            currentExclusions - path
                                        }
                                    }
                                )
                                Icon(
                                    Icons.Default.Folder, 
                                    contentDescription = null, 
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = name, 
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentExclusions.toList()) }) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
