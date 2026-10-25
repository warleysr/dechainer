package io.github.warleysr.dechainer.screens.tabs

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.SuspensionLogRepository
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.SuspensionLogEntry
import io.github.warleysr.dechainer.viewmodels.AppsViewModel
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SuspensionLogsScreen(
    appsViewModel: AppsViewModel = viewModel(),
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel()
) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf(SuspensionLogRepository.getLogs(context)) }
    var searchQuery by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }

    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    val filteredLogs = remember(logs, searchQuery) {
        if (searchQuery.isBlank()) {
            logs
        } else {
            logs.filter { entry ->
                entry.appName.contains(searchQuery, ignoreCase = true) ||
                entry.packageName.contains(searchQuery, ignoreCase = true) ||
                context.getString(entry.reason.titleResId).contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = logs.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.clear_suspension_logs)
                        )
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.HistoryToggleOff,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.no_suspension_logs),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredLogs, key = { it.id }) { log ->
                        SuspensionLogItem(
                            log = log,
                            dateFormat = dateFormat,
                            onClick = {
                                val found = appsViewModel.apps.firstOrNull { it.packageName == log.packageName }
                                    ?: AppRepository.getApp(log.packageName)
                                if (found != null) {
                                    selectedApp = found
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_suspension_logs)) },
            text = { Text(stringResource(R.string.clear_suspension_logs_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        SuspensionLogRepository.clearLogs(context)
                        logs = emptyList()
                        showClearDialog = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    AppManagementDialogs(
        selectedApp = selectedApp,
        onDismissSelectedApp = { selectedApp = null },
        viewModel = appsViewModel,
        deviceOwnerViewModel = deviceOwnerViewModel
    )
}

@Composable
private fun SuspensionLogItem(
    log: SuspensionLogEntry,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val appIcon: Drawable? = remember(log.packageName) {
        try {
            context.packageManager.getApplicationIcon(log.packageName)
        } catch (_: Exception) {
            null
        }
    }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            if (appIcon != null) {
                val bitmap = remember(log.packageName) {
                    try {
                        appIcon.toBitmap().asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Icon(
                        Icons.Outlined.Android,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                }
            } else {
                Icon(
                    Icons.Outlined.Android,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            }
        },
        headlineContent = {
            Text(
                text = log.appName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = log.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dateFormat.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(4.dp))
                SuggestionChip(
                        onClick = onClick,
                        label = {
                            Text(
                                stringResource(log.reason.titleResId),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        border = null
                    )
            }
        }
    )
}
