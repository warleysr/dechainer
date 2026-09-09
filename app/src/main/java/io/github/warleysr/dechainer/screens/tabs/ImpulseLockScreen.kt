package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.AppPickerDialog
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.ImpulseLockViewModel

@Composable
fun ImpulseLockScreen(viewModel: ImpulseLockViewModel = viewModel()) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var onRecoverySuccess by remember { mutableStateOf<(() -> Unit)?>(null) }

    val context = LocalContext.current
    val sessionActive = SecurityManager.isSessionActive()
    val accessibilityActive = DechainerAccessibilityService.isRunning

    fun runWithRecovery(action: () -> Unit) {
        if (SecurityManager.isSessionActive()) {
            action()
        } else {
            onRecoverySuccess = action
            showRecoveryDialog = true
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.impulse_lock_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (!sessionActive) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.visual_blocking_locked)) },
                    leadingContent = { Icon(Icons.Outlined.Lock, null) },
                    modifier = Modifier.clickable { runWithRecovery {} }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        // --- Challenge required to get into Dechainer itself ---
        item {
            SectionHeader(
                title = stringResource(R.string.impulse_lock_challenge_section),
                description = stringResource(R.string.impulse_lock_challenge_desc)
            )
            SecurityManager.ImpulseLockMode.entries.forEach { mode ->
                OptionRow(
                    selected = viewModel.lockMode == mode,
                    enabled = sessionActive,
                    title = when (mode) {
                        SecurityManager.ImpulseLockMode.OFF -> stringResource(R.string.impulse_lock_off)
                        SecurityManager.ImpulseLockMode.NORMAL -> stringResource(R.string.impulse_lock_normal)
                        SecurityManager.ImpulseLockMode.HARD -> stringResource(R.string.impulse_lock_hard)
                    },
                    onClick = { viewModel.updateLockMode(mode) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // --- What the panic button does ---
        item {
            SectionHeader(
                title = stringResource(R.string.impulse_action_section),
                description = stringResource(R.string.impulse_action_desc)
            )
            SecurityManager.ImpulseAction.entries.forEach { action ->
                OptionRow(
                    selected = viewModel.action == action,
                    enabled = sessionActive,
                    title = when (action) {
                        SecurityManager.ImpulseAction.TIMER_ONLY ->
                            stringResource(R.string.impulse_action_timer_only)
                        SecurityManager.ImpulseAction.TIMER_AND_SUSPEND ->
                            stringResource(R.string.impulse_action_suspend_apps)
                    },
                    supporting = when (action) {
                        SecurityManager.ImpulseAction.TIMER_ONLY ->
                            stringResource(R.string.impulse_action_timer_only_desc)
                        SecurityManager.ImpulseAction.TIMER_AND_SUSPEND ->
                            stringResource(R.string.impulse_action_suspend_apps_desc)
                    },
                    onClick = { viewModel.updateAction(action) }
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // --- How long the block lasts ---
        item {
            SectionHeader(
                title = stringResource(R.string.impulse_duration_section),
                description = stringResource(R.string.impulse_duration_desc)
            )
            Text(
                formatDuration(viewModel.durationMinutes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Slider(
                value = viewModel.durationMinutes.toFloat(),
                enabled = sessionActive,
                onValueChange = { viewModel.updateDurationMinutes(it.toInt()) },
                valueRange = SecurityManager.IMPULSE_MIN_DURATION_MINUTES.toFloat()..
                    SecurityManager.IMPULSE_MAX_DURATION_MINUTES.toFloat(),
                // 15-minute increments between the two bounds.
                steps = ((SecurityManager.IMPULSE_MAX_DURATION_MINUTES -
                    SecurityManager.IMPULSE_MIN_DURATION_MINUTES) / 15) - 1,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDuration(SecurityManager.IMPULSE_MIN_DURATION_MINUTES),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    formatDuration(SecurityManager.IMPULSE_MAX_DURATION_MINUTES),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // --- Apps to suspend, only relevant for the second action ---
        if (viewModel.action == SecurityManager.ImpulseAction.TIMER_AND_SUSPEND) {
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.impulse_apps_section)) },
                    supportingContent = {
                        val count = viewModel.suspendedApps.size
                        Text(
                            if (count == 0) stringResource(R.string.no_apps)
                            else stringResource(R.string.visual_blocking_apps_selected, count)
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Apps, null) },
                    trailingContent = {
                        Button(
                            enabled = sessionActive,
                            onClick = { showAppSelectionDialog = true }
                        ) {
                            Text(stringResource(R.string.select_apps))
                        }
                    }
                )
                if (!accessibilityActive) {
                    Text(
                        stringResource(R.string.impulse_apps_requires_accessibility),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAppSelectionDialog) {
        AppPickerDialog(
            apps = viewModel.apps,
            isLoading = viewModel.isLoadingApps,
            isSelected = { viewModel.suspendedApps.contains(it) },
            onToggle = { viewModel.toggleAppSelection(it) },
            onDismiss = { showAppSelectionDialog = false }
        )
    }

    if (showRecoveryDialog) {
        val storedCode = SecurityManager.getRecoveryCode(context)
        RecoveryConfirmDialog(
            onConfirm = { code ->
                if (SecurityManager.validateRecoveryCode(code, storedCode!!)) {
                    showRecoveryDialog = false
                    onRecoverySuccess?.invoke()
                    onRecoverySuccess = null
                    true
                } else false
            },
            onDismiss = {
                showRecoveryDialog = false
                onRecoverySuccess = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, description: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
    Text(
        description,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun OptionRow(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    onClick: () -> Unit,
    supporting: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.time_minutes, remaining)
        remaining == 0 -> stringResource(R.string.time_hours, hours)
        else -> stringResource(R.string.time_hours_minutes, hours, remaining)
    }
}
