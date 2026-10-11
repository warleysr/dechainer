package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.DeviceOwnerRepository
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.models.FocusSchedule
import io.github.warleysr.dechainer.screens.common.AppPickerDialog
import io.github.warleysr.dechainer.screens.common.FocusRestrictionsDialog
import io.github.warleysr.dechainer.screens.common.FocusScheduleDialog
import io.github.warleysr.dechainer.screens.common.FocusSessionCard
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.viewmodels.FocusModeViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun FocusModeScreen(viewModel: FocusModeViewModel = viewModel()) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var showRestrictionsDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var scheduleToEdit by remember { mutableStateOf<FocusSchedule?>(null) }
    val recoveryGate = rememberRecoveryGate()

    val sessionActive = recoveryGate.isSessionActive
    val isDeviceOwner = remember { DeviceOwnerRepository.isDeviceOwner() }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.focus_mode_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        item {
            FocusSessionCard(
                status = viewModel.status,
                gate = recoveryGate,
                onChanged = { viewModel.refreshStatus() },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        if (!sessionActive) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.visual_blocking_locked)) },
                    leadingContent = { Icon(Icons.Outlined.Lock, null) },
                    modifier = Modifier.clickable { recoveryGate.run {} }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }

        item {
            MinutesSlider(
                title = stringResource(R.string.focus_duration),
                value = viewModel.focusMinutes,
                range = FocusMode.FOCUS_MIN_MINUTES..FocusMode.FOCUS_MAX_MINUTES,
                step = 5,
                enabled = sessionActive,
                onChange = { viewModel.updateFocusMinutes(it) }
            )
            MinutesSlider(
                title = stringResource(R.string.focus_short_break_duration),
                value = viewModel.shortBreakMinutes,
                range = FocusMode.SHORT_BREAK_MIN_MINUTES..FocusMode.SHORT_BREAK_MAX_MINUTES,
                step = 1,
                enabled = sessionActive,
                onChange = { viewModel.updateShortBreakMinutes(it) }
            )
            MinutesSlider(
                title = stringResource(R.string.focus_long_break_duration),
                value = viewModel.longBreakMinutes,
                range = FocusMode.LONG_BREAK_MIN_MINUTES..FocusMode.LONG_BREAK_MAX_MINUTES,
                step = 5,
                enabled = sessionActive,
                onChange = { viewModel.updateLongBreakMinutes(it) }
            )
            SettingSlider(
                title = stringResource(R.string.focus_cycles),
                valueText = viewModel.cyclesBeforeLongBreak.toString(),
                value = viewModel.cyclesBeforeLongBreak,
                range = FocusMode.CYCLES_MIN..FocusMode.CYCLES_MAX,
                step = 1,
                enabled = sessionActive,
                minLabel = FocusMode.CYCLES_MIN.toString(),
                maxLabel = FocusMode.CYCLES_MAX.toString(),
                onChange = { viewModel.updateCyclesBeforeLongBreak(it) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_auto_start)) },
                supportingContent = { Text(stringResource(R.string.focus_auto_start_desc)) },
                trailingContent = {
                    Switch(
                        checked = viewModel.autoStartFocus,
                        enabled = sessionActive,
                        onCheckedChange = { viewModel.updateAutoStartFocus(it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_sound)) },
                supportingContent = { Text(stringResource(R.string.focus_sound_desc)) },
                trailingContent = {
                    Switch(
                        checked = viewModel.soundEnabled,
                        enabled = sessionActive,
                        onCheckedChange = { viewModel.updateSoundEnabled(it) }
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_vibration)) },
                supportingContent = { Text(stringResource(R.string.focus_vibration_desc)) },
                trailingContent = {
                    Switch(
                        checked = viewModel.vibrationEnabled,
                        enabled = sessionActive,
                        onCheckedChange = { viewModel.updateVibrationEnabled(it) }
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_apps_section)) },
                supportingContent = {
                    val count = viewModel.suspendedApps.size
                    Text(
                        if (count == 0) stringResource(R.string.no_apps)
                        else stringResource(R.string.visual_blocking_apps_selected, count)
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Apps, null) },
                trailingContent = {
                    Button(enabled = sessionActive && isDeviceOwner, onClick = { showAppSelectionDialog = true }) {
                        Text(stringResource(R.string.select_apps))
                    }
                }
            )
            if (!isDeviceOwner) {
                Text(
                    stringResource(R.string.focus_requires_device_owner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_restrictions_section)) },
                supportingContent = {
                    val count = viewModel.focusRestrictions.size
                    Text(
                        if (count == 0) stringResource(R.string.focus_no_restrictions)
                        else stringResource(R.string.focus_restrictions_selected, count)
                    )
                },
                leadingContent = { Icon(Icons.Outlined.Security, null) },
                trailingContent = {
                    Button(enabled = sessionActive && isDeviceOwner, onClick = { showRestrictionsDialog = true }) {
                        Text(stringResource(R.string.focus_select_restrictions))
                    }
                }
            )
            if (!isDeviceOwner) {
                Text(
                    stringResource(R.string.focus_requires_device_owner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.focus_schedules_section)) },
                leadingContent = { Icon(Icons.Outlined.Schedule, null) },
                trailingContent = {
                    Button(
                        enabled = sessionActive,
                        onClick = {
                            scheduleToEdit = null
                            showScheduleDialog = true
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.focus_add_schedule))
                        }
                    }
                }
            )
        }

        if (viewModel.schedules.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.focus_no_schedules),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        } else {
            items(viewModel.schedules.size, key = { viewModel.schedules[it].id }) { index ->
                val schedule = viewModel.schedules[index]
                val context = LocalContext.current
                ListItem(
                    headlineContent = {
                        Text(
                            text = schedule.formattedTime(),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    supportingContent = {
                        Text(
                            text = formatDaysSummary(context, schedule.daysOfWeek),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = schedule.enabled,
                                enabled = sessionActive,
                                onCheckedChange = { viewModel.toggleSchedule(schedule.id, it) }
                            )
                            IconButton(
                                enabled = sessionActive,
                                onClick = { viewModel.deleteSchedule(schedule.id) }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable(enabled = sessionActive) {
                        scheduleToEdit = schedule
                        showScheduleDialog = true
                    }
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showAppSelectionDialog && isDeviceOwner) {
        AppPickerDialog(
            apps = viewModel.apps,
            isLoading = viewModel.isLoadingApps,
            isSelected = { viewModel.suspendedApps.contains(it) },
            onToggle = { viewModel.toggleAppSelection(it) },
            onDismiss = { showAppSelectionDialog = false }
        )
    }

    if (showRestrictionsDialog && isDeviceOwner) {
        FocusRestrictionsDialog(
            selectedRestrictions = viewModel.focusRestrictions,
            onToggle = { viewModel.toggleRestrictionSelection(it) },
            onDismiss = { showRestrictionsDialog = false }
        )
    }

    if (showScheduleDialog) {
        FocusScheduleDialog(
            initialSchedule = scheduleToEdit,
            onSave = {
                viewModel.addOrUpdateSchedule(it)
                showScheduleDialog = false
            },
            onDismiss = { showScheduleDialog = false }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

private fun formatDaysSummary(context: android.content.Context, days: Set<Int>): String {
    if (days.size == 7) return context.getString(R.string.focus_schedule_days_all)
    if (days == setOf(1, 2, 3, 4, 5)) return context.getString(R.string.focus_schedule_days_weekdays)
    if (days == setOf(6, 7)) return context.getString(R.string.focus_schedule_days_weekend)
    val locale = Locale.getDefault()
    return (1..7)
        .filter { it in days }
        .map { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale).trimEnd('.') }
        .joinToString(", ")
}

@Composable
private fun MinutesSlider(
    title: String,
    value: Int,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit
) {
    SettingSlider(
        title = title,
        valueText = formatMinutes(value),
        value = value,
        range = range,
        step = step,
        enabled = enabled,
        minLabel = formatMinutes(range.first),
        maxLabel = formatMinutes(range.last),
        onChange = onChange
    )
}

@Composable
private fun SettingSlider(
    title: String,
    valueText: String,
    value: Int,
    range: IntRange,
    step: Int,
    enabled: Boolean,
    minLabel: String,
    maxLabel: String,
    onChange: (Int) -> Unit
) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
    )
    Text(
        valueText,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    Slider(
        value = value.toFloat(),
        enabled = enabled,
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first) / step - 1,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(minLabel, style = MaterialTheme.typography.bodySmall)
        Text(maxLabel, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val remaining = minutes % 60
    return when {
        hours == 0 -> stringResource(R.string.time_minutes, remaining)
        remaining == 0 -> stringResource(R.string.time_hours, hours)
        else -> stringResource(R.string.time_hours_minutes, hours, remaining)
    }
}

