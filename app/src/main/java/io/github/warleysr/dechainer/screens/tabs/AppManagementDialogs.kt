package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.models.AppGroup
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.isLooserLimit
import io.github.warleysr.dechainer.screens.common.isLooserRestrictions
import io.github.warleysr.dechainer.screens.common.isLooserWindows
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.viewmodels.AppsViewModel
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

@Composable
fun AppManagementDialogs(
    selectedApp: AppItem?,
    onDismissSelectedApp: () -> Unit,
    viewModel: AppsViewModel = viewModel(),
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel()
) {
    var showTimeLimitDialog by remember { mutableStateOf<AppItem?>(null) }
    var showTimeWindowsDialog by remember { mutableStateOf<AppItem?>(null) }
    var showRestrictionsDialog by remember { mutableStateOf<AppItem?>(null) }
    var showGroupPickerDialog by remember { mutableStateOf<AppItem?>(null) }
    var showGroupsManagementDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showEditGroupDialog by remember { mutableStateOf<AppGroup?>(null) }
    val recoveryGate = rememberRecoveryGate()

    selectedApp?.let { app ->
        AppActionDialog(
            app = app,
            onDismiss = onDismissSelectedApp,
            onBlock = {
                recoveryGate.change(loosening = app.isHidden) { viewModel.blockApp(app.packageName, !app.isHidden) }
                onDismissSelectedApp()
            },
            onToggleUninstall = {
                recoveryGate.change(loosening = app.isUninstallBlocked) { viewModel.setUninstallBlocked(app.packageName, !app.isUninstallBlocked) }
                onDismissSelectedApp()
            },
            onSuspend = {
                recoveryGate.change(loosening = app.isSuspended) { viewModel.suspendApp(app.packageName, !app.isSuspended) }
                onDismissSelectedApp()
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
            onConfirm = { limit, reopeningSeconds ->
                recoveryGate.change(isLooserLimit(app.timeLimit, limit) || reopeningSeconds < app.reopeningSeconds) {
                    viewModel.setAppTimeLimit(app.packageName, limit)
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
                recoveryGate.change(isLooserWindows(app.timeWindows, windows)) {
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
                val current = deviceOwnerViewModel.getApplicationRestrictions(app.packageName)
                recoveryGate.change(isLooserRestrictions(current, restrictions)) {
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
                val leaving = viewModel.groupFor(app.packageName)?.id.let { it != null && it != groupId }
                recoveryGate.change(leaving) { viewModel.setPackageGroup(app.packageName, groupId) }
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
                viewModel.createGroup(name)
                showCreateGroupDialog = false
            }
        )
    }

    showEditGroupDialog?.let { group ->
        EditGroupDialog(
            group = group,
            allApps = viewModel.apps,
            onDismiss = { showEditGroupDialog = null },
            onSave = { name, limit, windows, packageNames ->
                val loosening = isLooserLimit(group.timeLimit, limit) ||
                    isLooserWindows(group.timeWindows, windows) ||
                    !packageNames.containsAll(group.packageNames)
                recoveryGate.change(loosening) {
                    if (name != group.name) viewModel.renameGroup(group.id, name)
                    viewModel.setGroupTimeLimit(group.id, limit)
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
