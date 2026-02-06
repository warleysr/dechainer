package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.ActivityBlockerViewModel
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import io.github.warleysr.dechainer.viewmodels.GroupedActivityLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityBlockerScreen(
    viewModel: ActivityBlockerViewModel = viewModel(),
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.blocked_activities),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }

            items(viewModel.blockedActivities) { className ->
                ListItem(
                    headlineContent = { Text(className) },
                    trailingContent = {
                        IconButton(onClick = {
                            pendingAction = { viewModel.removeBlockedActivity(className) }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                        }
                    }
                )
                HorizontalDivider()
            }

            item {
                Text(
                    stringResource(R.string.accessed_activities),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            val groupedLogs = viewModel.getGroupedAccessedActivities()
            items(groupedLogs) { groupedLog ->
                ActivityLogAccordion(groupedLog)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showAddDialog) {
        var activityName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(stringResource(R.string.add_activity)) },
            text = {
                OutlinedTextField(
                    value = activityName,
                    onValueChange = { activityName = it },
                    label = { Text(stringResource(R.string.activity_name_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (activityName.isNotBlank()) {
                        pendingAction = { viewModel.addBlockedActivity(activityName) }
                    }
                    showAddDialog = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingAction != null) {
        val storedHash = SecurityManager.getRecoveryHash(context)
        if (storedHash == null) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            RecoveryConfirmDialog(
                onConfirm = { code ->
                    if (SecurityManager.validatePassphrase(code, storedHash)) {
                        pendingAction?.invoke()
                        pendingAction = null
                        true
                    } else false
                },
                onDismiss = { pendingAction = null }
            )
        }
    }
}

@Composable
fun ActivityLogAccordion(groupedLog: GroupedActivityLog) {
    var expanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = groupedLog.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(groupedLog.appName, style = MaterialTheme.typography.titleMedium)
                    Text(groupedLog.packageName, style = MaterialTheme.typography.labelSmall)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider()
                    groupedLog.activities.forEach { log ->
                        ListItem(
                            headlineContent = { 
                                Text(
                                    log.className, 
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) 
                            },
                            trailingContent = {
                                TextButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(log.className))
                                }) {
                                    Text(stringResource(R.string.copy))
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
