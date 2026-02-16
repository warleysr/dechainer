package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.NoDeviceOwnerPrivileges
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.AppItem
import io.github.warleysr.dechainer.viewmodels.AppsViewModel
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun AppsTab(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel(),
    appsViewModel: AppsViewModel = viewModel()
) {
    if (!deviceOwnerViewModel.isDeviceOwner()) {
        NoDeviceOwnerPrivileges(deviceOwnerViewModel)
    } else {
        AppsScreen(appsViewModel)
    }
}

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }
    var showTimeLimitDialog by remember { mutableStateOf<AppItem?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current

    val filteredApps = remember(viewModel.apps, searchQuery) {
        viewModel.apps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
        }

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppRow(app) { selectedApp = app }
                }
            }
        }
    }

    selectedApp?.let { app ->
        AppActionDialog(
            app = app,
            onDismiss = { selectedApp = null },
            onBlock = {
                pendingAction = { viewModel.blockApp(app.packageName, !app.isHidden) }
                selectedApp = null
            },
            onToggleUninstall = {
                pendingAction = { viewModel.setUninstallBlocked(app.packageName, !app.isUninstallBlocked) }
                selectedApp = null
            },
            onSetTimeLimit = {
                showTimeLimitDialog = app
                selectedApp = null
            }
        )
    }

    showTimeLimitDialog?.let { app ->
        TimeLimitDialog(
            app = app,
            onDismiss = { showTimeLimitDialog = null },
            onConfirm = { minutes ->
                pendingAction = { viewModel.setAppTimeLimit(app.packageName, minutes) }
                showTimeLimitDialog = null
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
                    } else {
                        false
                    }
                },
                onDismiss = { pendingAction = null }
            )
        }
    }
}

@Composable
fun AppRow(app: AppItem, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(app.name) },
        supportingContent = { 
            Column {
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                if (app.timeLimitMinutes > 0) {
                    val h = app.timeLimitMinutes / 60
                    val m = app.timeLimitMinutes % 60
                    Text(
                        "Limite: ${if (h > 0) "${h}h " else ""}${m}min",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        leadingContent = {
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (app.isHidden) {
                    StatusBadge(
                        text = stringResource(R.string.blocked),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (app.isUninstallBlocked) {
                    StatusBadge(
                        text = stringResource(R.string.protected_label),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    )
}

@Composable
private fun StatusBadge(text: String, containerColor: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color) {
    SuggestionChip(
        onClick = { },
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        )
    )
}

@Composable
fun AppActionDialog(
    app: AppItem,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onToggleUninstall: () -> Unit,
    onSetTimeLimit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.name) },
        text = {
            Column {
                Text(stringResource(R.string.manage_restrictions, app.packageName))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onSetTimeLimit, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Timer, null)
                    Text(stringResource(R.string.set_time_limit))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBlock) {
                Text(if (app.isHidden) stringResource(R.string.unblock) else stringResource(R.string.block))
            }
        },
        dismissButton = {
            TextButton(onClick = onToggleUninstall) {
                Text(if (app.isUninstallBlocked) stringResource(R.string.allow_uninstall) else stringResource(R.string.prevent_uninstall))
            }
        }
    )
}

@Composable
fun TimeLimitDialog(
    app: AppItem,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(app.timeLimitMinutes / 60) }
    var minutes by remember { mutableIntStateOf(app.timeLimitMinutes % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_time_limit_dialog_title, app.name)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NumberPickerWheel(
                        value = hours,
                        range = 0..23,
                        onValueChange = { hours = it },
                        label = stringResource(R.string.hours)
                    )
                    Text(
                        ":",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    NumberPickerWheel(
                        value = minutes,
                        range = 0..59,
                        onValueChange = { minutes = it },
                        label = stringResource(R.string.minutes)
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (hours == 0 && minutes == 0) {
                    Text(stringResource(R.string.none), style = MaterialTheme.typography.labelSmall)
                } else {
                    Text(
                        "Total: ${hours}h ${minutes}min",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hours * 60 + minutes) }) {
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

@Composable
fun NumberPickerWheel(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    label: String
) {
    val items = range.toList()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = items.indexOf(value))
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val centerIndex = listState.firstVisibleItemIndex
            if (centerIndex in items.indices) {
                onValueChange(items[centerIndex])
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(70.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp))
        Box(
            modifier = Modifier
                .height(120.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            // Selection Highlight
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
            )
            
            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                contentPadding = PaddingValues(vertical = 40.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(items) { item ->
                    val isSelected = item == value
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontSize = if (isSelected) 22.sp else 18.sp
                        )
                    }
                }
            }
        }
    }
}
