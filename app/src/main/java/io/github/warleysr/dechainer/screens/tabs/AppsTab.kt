package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.NoDeviceOwnerPrivileges
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.models.AppGroup
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.viewmodels.AppsViewModel
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import io.github.warleysr.dechainer.viewmodels.NavigationViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

import android.content.RestrictionEntry
import android.os.Bundle
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.window.Dialog

@Composable
fun AppsTab(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel(),
    appsViewModel: AppsViewModel = viewModel(),
    navViewModel: NavigationViewModel = viewModel()
) {
    if (!deviceOwnerViewModel.isDeviceOwner()) {
        NoDeviceOwnerPrivileges(navViewModel)
    } else {
        AppsScreen(appsViewModel, deviceOwnerViewModel)
    }
}

@Composable
fun AppsScreen(viewModel: AppsViewModel, deviceOwnerViewModel: DeviceOwnerViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }
    var showTimeLimitDialog by remember { mutableStateOf<AppItem?>(null) }
    var showTimeWindowsDialog by remember { mutableStateOf<AppItem?>(null) }
    var showRestrictionsDialog by remember { mutableStateOf<AppItem?>(null) }
    var showGroupPickerDialog by remember { mutableStateOf<AppItem?>(null) }
    var showGroupsManagementDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf<AppGroup?>(null) }
    var selectedGroupFilter by remember { mutableStateOf<String?>(null) }
    val recoveryGate = rememberRecoveryGate()
    var showSystemApps by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val restrictedGroupPackages = remember(viewModel.groups) {
        viewModel.groups.filter { it.timeLimitMinutes > 0 || it.timeWindows.isNotEmpty() }
            .flatMap { it.packageNames }
            .toSet()
    }

    val filteredApps = remember(viewModel.apps, viewModel.groups, searchQuery, showSystemApps, selectedGroupFilter) {
        viewModel.apps.filter {
            (showSystemApps || !it.isSystem || it.isHidden || it.isUninstallBlocked ||
                it.timeLimitMinutes > 0 || it.timeWindows.isNotEmpty() ||
                it.packageName in restrictedGroupPackages) &&
            (it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)) &&
            (selectedGroupFilter == null ||
                viewModel.groups.firstOrNull { g -> g.id == selectedGroupFilter }?.packageNames?.contains(it.packageName) == true)
        }
        .sortedBy {
            it.timeLimitMinutes == 0 && it.timeWindows.isEmpty() && it.packageName !in restrictedGroupPackages
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.show_system_apps)) },
                                onClick = {
                                    showSystemApps = !showSystemApps
                                    showMenu = false
                                },
                                trailingIcon = {
                                    Checkbox(checked = showSystemApps, onCheckedChange = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.manage_groups)) },
                                leadingIcon = { Icon(Icons.Default.Group, null) },
                                onClick = {
                                    showGroupsManagementDialog = true
                                    showMenu = false
                                }
                            )
                        }
                    }
                }

                if (viewModel.groups.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedGroupFilter == null,
                                onClick = { selectedGroupFilter = null },
                                label = { Text(stringResource(R.string.all_apps_filter)) }
                            )
                        }
                        items(viewModel.groups, key = { it.id }) { group ->
                            FilterChip(
                                selected = selectedGroupFilter == group.id,
                                onClick = {
                                    selectedGroupFilter = if (selectedGroupFilter == group.id) null else group.id
                                },
                                label = { Text(group.name) }
                            )
                        }
                    }
                }
            }
        }

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppRow(app, viewModel) { selectedApp = app }
                }
            }
        }
    }

    selectedApp?.let { app ->
        AppActionDialog(
            app = app,
            onDismiss = { selectedApp = null },
            onBlock = {
                recoveryGate.run { viewModel.blockApp(app.packageName, !app.isHidden) }
                selectedApp = null
            },
            onToggleUninstall = {
                recoveryGate.run { viewModel.setUninstallBlocked(app.packageName, !app.isUninstallBlocked) }
                selectedApp = null
            },
            onSuspend = {
                recoveryGate.run { viewModel.suspendApp(app.packageName, !app.isSuspended) }
                selectedApp = null
            },
            onSetTimeLimit = {
                showTimeLimitDialog = app
            },
            onSetTimeWindows = {
                showTimeWindowsDialog = app
            },
            onSetGroup = {
                showGroupPickerDialog = app
            },
            currentGroupName = viewModel.groupFor(app.packageName)?.name,
            onManageRestrictions = {
                showRestrictionsDialog = app
            }
        )
    }

    showTimeLimitDialog?.let { app ->
        TimeLimitDialog(
            app = app,
            onDismiss = { showTimeLimitDialog = null },
            onConfirm = { minutes, reopeningSeconds ->
                recoveryGate.run {
                    viewModel.setAppTimeLimit(app.packageName, minutes)
                    viewModel.setAppReopenTime(app.packageName, reopeningSeconds)
                }
                showTimeLimitDialog = null
            }
        )
    }

    showTimeWindowsDialog?.let { app ->
        TimeWindowsDialog(
            title = stringResource(R.string.time_windows_dialog_title, app.name),
            initialWindows = app.timeWindows,
            onDismiss = { showTimeWindowsDialog = null },
            onConfirm = { windows ->
                recoveryGate.run {
                    viewModel.setAppTimeWindows(app.packageName, windows)
                }
                showTimeWindowsDialog = null
            }
        )
    }

    showRestrictionsDialog?.let { app ->
        AppRestrictionsDialog(
            app = app,
            viewModel = deviceOwnerViewModel,
            onDismiss = { showRestrictionsDialog = null },
            onSave = { restrictions ->
                recoveryGate.run {
                    deviceOwnerViewModel.setApplicationRestrictions(app.packageName, restrictions)
                }
                showRestrictionsDialog = null
            }
        )
    }

    showGroupPickerDialog?.let { app ->
        AppGroupPickerDialog(
            app = app,
            groups = viewModel.groups,
            currentGroupId = viewModel.groupFor(app.packageName)?.id,
            onDismiss = { showGroupPickerDialog = null },
            onSelect = { groupId ->
                recoveryGate.run { viewModel.setPackageGroup(app.packageName, groupId) }
                showGroupPickerDialog = null
            },
            onCreateNew = {
                showGroupPickerDialog = null
                showCreateGroupDialog = true
            }
        )
    }

    if (showGroupsManagementDialog) {
        GroupsManagementDialog(
            groups = viewModel.groups,
            onDismiss = { showGroupsManagementDialog = false },
            onCreateGroup = {
                showGroupsManagementDialog = false
                showCreateGroupDialog = true
            },
            onSelectGroup = { group ->
                showGroupsManagementDialog = false
                showEditGroupDialog = group
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name ->
                recoveryGate.run { viewModel.createGroup(name) }
                showCreateGroupDialog = false
            }
        )
    }

    showEditGroupDialog?.let { group ->
        EditGroupDialog(
            group = group,
            allApps = viewModel.apps,
            onDismiss = { showEditGroupDialog = null },
            onSave = { name, limitMinutes, windows, packageNames ->
                recoveryGate.run {
                    if (name != group.name) viewModel.renameGroup(group.id, name)
                    viewModel.setGroupTimeLimit(group.id, limitMinutes)
                    viewModel.setGroupTimeWindows(group.id, windows)
                    viewModel.setGroupPackages(group.id, packageNames)
                }
                showEditGroupDialog = null
            },
            onDelete = {
                recoveryGate.run { viewModel.deleteGroup(group.id) }
                showEditGroupDialog = null
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
fun AppRestrictionsDialog(
    app: AppItem,
    viewModel: DeviceOwnerViewModel,
    onDismiss: () -> Unit,
    onSave: (Bundle) -> Unit
) {
    val availableRestrictions = remember { viewModel.getAvailableRestrictions(app.packageName) }
    val currentRestrictions = remember { viewModel.getApplicationRestrictions(app.packageName) }
    
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredRestrictions = remember(availableRestrictions, searchQuery) {
        availableRestrictions.filter { entry ->
            entry.title?.contains(searchQuery, ignoreCase = true) == true ||
            entry.key.contains(searchQuery, ignoreCase = true)
        }
    }
    
    val selectedRestrictions = remember { 
        val map = mutableStateMapOf<String, Boolean>()
        availableRestrictions.forEach { entry ->
            if (entry.type == RestrictionEntry.TYPE_BOOLEAN) {
                map[entry.key] = currentRestrictions.getBoolean(entry.key, entry.selectedState)
            }
        }
        map
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_restrictions, app.name)) },
        text = {
            if (availableRestrictions.isEmpty()) {
                Text(stringResource(R.string.no_restrictions_available, app.name))
            } else {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_restrictions)) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        items(filteredRestrictions, key = { it.key }) { entry ->
                            if (entry.type == RestrictionEntry.TYPE_BOOLEAN) {
                                var expanded by remember { mutableStateOf(false) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { expanded = !expanded }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    entry.title ?: entry.key,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                Icon(
                                                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp).padding(start = 4.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                entry.key,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            )
                                        }
                                        Checkbox(
                                            checked = selectedRestrictions[entry.key] ?: false,
                                            onCheckedChange = { selectedRestrictions[entry.key] = it }
                                        )
                                    }
                                    if (expanded && entry.description != null) {
                                        Text(
                                            entry.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(
                                                start = 8.dp,
                                                end = 32.dp,
                                                bottom = 8.dp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val bundle = Bundle()
                selectedRestrictions.forEach { (key, value) ->
                    bundle.putBoolean(key, value)
                }
                onSave(bundle)
            }) {
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
fun LimitUsageRow(
    limitMinutes: Int,
    usedMinutes: Long,
    @androidx.annotation.StringRes limitLabelRes: Int,
    @androidx.annotation.StringRes usedLabelRes: Int
) {
    val h = limitMinutes / 60
    val m = limitMinutes % 60
    val fmtLimit = "${if (h > 0) "${h}h " else ""}${if (m > 0) "${m}min" else ""}"

    val usedH = usedMinutes / 60
    val usedM = usedMinutes % 60
    val fmtUsed = "${if (usedH > 0) "${usedH}h " else ""}${if (usedM > 0) "${usedM}min" else ""}"

    Row {
        Text(
            stringResource(limitLabelRes, fmtLimit),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (usedMinutes > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(usedLabelRes, fmtUsed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
fun AppRow(app: AppItem, viewModel: AppsViewModel, onClick: () -> Unit) {
    val group = remember(app.packageName, viewModel.groups) { viewModel.groupFor(app.packageName) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(app.name) },
        supportingContent = {
            Column {
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)

                if (group != null) {
                    Text(
                        stringResource(R.string.group_label, group.name),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (app.timeLimitMinutes > 0) {
                    LimitUsageRow(
                        limitMinutes = app.timeLimitMinutes,
                        usedMinutes = viewModel.getAppUsage(app.packageName, inMinutes = true),
                        limitLabelRes = R.string.limit,
                        usedLabelRes = R.string.used
                    )
                }

                if (group != null && group.timeLimitMinutes > 0) {
                    LimitUsageRow(
                        limitMinutes = group.timeLimitMinutes,
                        usedMinutes = viewModel.getGroupUsage(group.id, inMinutes = true),
                        limitLabelRes = R.string.group_limit,
                        usedLabelRes = R.string.group_used
                    )
                }

                val effectiveWindows = if (group != null) TimeWindow.intersect(group.timeWindows, app.timeWindows)
                                       else app.timeWindows
                if (effectiveWindows.isNotEmpty()) {
                    Text(
                        stringResource(
                            R.string.time_windows_summary,
                            effectiveWindows.joinToString(", ") { it.formatted() }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                if (app.hasExplicitContent) {
                    Text(
                        stringResource(R.string.explicit_content),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        leadingContent = {
            val iconBitmap = remember(app.packageName) { app.icon.toBitmap().asImageBitmap() }
            Image(
                bitmap = iconBitmap,
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
                if (app.isSuspended) {
                    StatusBadge(
                        text = stringResource(R.string.suspended),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
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
    onSuspend: () -> Unit,
    onSetTimeLimit: () -> Unit,
    onSetTimeWindows: () -> Unit,
    onSetGroup: () -> Unit,
    currentGroupName: String?,
    onManageRestrictions: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val iconBitmap = remember(app.packageName) { app.icon.toBitmap().asImageBitmap() }
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                AppActionItem(
                    icon = Icons.Default.Group,
                    label = currentGroupName?.let { stringResource(R.string.group_label, it) } ?: stringResource(R.string.app_group),
                    onClick = onSetGroup
                )
                AppActionItem(
                    icon = Icons.Default.Timer,
                    label = stringResource(R.string.set_time_limit),
                    onClick = onSetTimeLimit
                )
                AppActionItem(
                    icon = Icons.Default.Schedule,
                    label = stringResource(R.string.time_windows),
                    onClick = onSetTimeWindows
                )
                AppActionItem(
                    icon = Icons.Default.Block,
                    label = stringResource(R.string.manage_app_restrictions),
                    onClick = onManageRestrictions
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                AppActionItem(
                    icon = if (app.isSuspended) Icons.Default.PlayArrow else Icons.Default.Pause,
                    label = if (app.isSuspended) stringResource(R.string.unsuspend) else stringResource(R.string.suspend),
                    onClick = onSuspend
                )
                AppActionItem(
                    icon = if (app.isUninstallBlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                    label = if (app.isUninstallBlocked) stringResource(R.string.allow_uninstall) else stringResource(R.string.prevent_uninstall),
                    onClick = onToggleUninstall
                )
                AppActionItem(
                    icon = if (app.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    label = if (app.isHidden) stringResource(R.string.unblock) else stringResource(R.string.block),
                    onClick = onBlock
                )
            }
        }
    }
}

@Composable
private fun AppActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TimeLimitDialog(
    app: AppItem,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(app.timeLimitMinutes / 60) }
    var minutes by remember { mutableIntStateOf(app.timeLimitMinutes % 60) }
    var reopeningSeconds by remember { mutableIntStateOf(app.reopeningSeconds) }

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
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.reopen_time))
                SecondInputField(
                    initialSeconds = reopeningSeconds,
                    onValueChange = { reopeningSeconds = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(hours * 60 + minutes, reopeningSeconds) }) {
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
fun TimeWindowsDialog(
    title: String,
    initialWindows: List<TimeWindow>,
    onDismiss: () -> Unit,
    lockedWindows: Set<TimeWindow> = emptySet(),
    emptyMessage: String = stringResource(R.string.no_time_windows),
    onConfirm: (List<TimeWindow>) -> Unit
) {
    val windows = remember { mutableStateListOf(*initialWindows.toTypedArray()) }
    var showAddForm by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var startHour by remember { mutableIntStateOf(18) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(22) }
    var endMinute by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (windows.isEmpty() && !showAddForm) {
                    Text(emptyMessage, style = MaterialTheme.typography.bodySmall)
                }
                windows.forEachIndexed { index, window ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(window.formatted(), modifier = Modifier.weight(1f))
                        IconButton(onClick = { windows.removeAt(index) }, enabled = window !in lockedWindows) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (showAddForm) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.start_time), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.Center) {
                            NumberPickerWheel(
                                value = startHour,
                                range = 0..23,
                                onValueChange = { startHour = it },
                                label = stringResource(R.string.hours)
                            )
                            NumberPickerWheel(
                                value = startMinute,
                                range = 0..59,
                                onValueChange = { startMinute = it },
                                label = stringResource(R.string.minutes)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.end_time), style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.Center) {
                            NumberPickerWheel(
                                value = endHour,
                                range = 0..23,
                                onValueChange = { endHour = it },
                                label = stringResource(R.string.hours)
                            )
                            NumberPickerWheel(
                                value = endMinute,
                                range = 0..59,
                                onValueChange = { endMinute = it },
                                label = stringResource(R.string.minutes)
                            )
                        }
                        if (showError) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.invalid_time_window),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = { showAddForm = false; showError = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            TextButton(onClick = {
                                val start = startHour * 60 + startMinute
                                val end = endHour * 60 + endMinute
                                if (start == end) {
                                    showError = true
                                } else {
                                    windows.add(TimeWindow(start, end))
                                    showAddForm = false
                                    showError = false
                                }
                            }) {
                                Text(stringResource(R.string.confirm))
                            }
                        }
                    }
                } else {
                    TextButton(onClick = { showAddForm = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.add_time_window))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(windows.toList()) }) {
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

@Composable
fun SecondInputField(
    initialSeconds: Int = 0,
    onValueChange: (Int) -> Unit
) {
    var seconds by remember { mutableIntStateOf(initialSeconds) }
    var textFieldValue by remember { mutableStateOf(seconds.toString()) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        SmallIncrementButton(label = "-15") {
            seconds = (seconds - 15).coerceAtLeast(0)
            textFieldValue = seconds.toString()
            onValueChange(seconds)
        }

        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                    textFieldValue = newValue
                    val parsed = newValue.toIntOrNull() ?: 0
                    seconds = parsed
                    onValueChange(parsed)
                }
            },
            modifier = Modifier
                .width(120.dp)
                .padding(horizontal = 8.dp),
            label = { Text(stringResource(R.string.seconds), fontSize = 12.sp) },
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        SmallIncrementButton(label = "+15") {
            seconds += 15
            textFieldValue = seconds.toString()
            onValueChange(seconds)
        }
    }
}

@Composable
fun SmallIncrementButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        modifier = Modifier.height(40.dp)
    ) {
        Text(text = label, fontSize = 12.sp)
    }
}
