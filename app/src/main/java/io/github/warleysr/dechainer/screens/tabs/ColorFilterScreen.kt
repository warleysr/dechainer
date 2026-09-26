package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.models.ColorFilterMode
import io.github.warleysr.dechainer.screens.common.RecoveryGate
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.viewmodels.ColorFilterViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Composable
fun ColorFilterScreen(viewModel: ColorFilterViewModel = viewModel()) {
    var showWindowsDialog by remember { mutableStateOf(false) }
    val recoveryGate = rememberRecoveryGate()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val shizukuNotRunningMsg = stringResource(R.string.shizuku_not_running)
    val permissionFailedMsg = stringResource(R.string.color_filter_permission_failed)
    val lockedMsg = stringResource(R.string.color_filter_locked_error)
    val showLocked: () -> Unit = { scope.launch { snackbarHostState.showSnackbar(lockedMsg) } }

    val accessibilityActive = DechainerAccessibilityService.isRunning

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refresh()
            delay(15.seconds)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    stringResource(R.string.color_filter_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                val activeWindowsText = viewModel.activeWindows.joinToString { it.formatted() }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.color_filter_enable)) },
                    supportingContent = {
                        when {
                            !accessibilityActive -> Text(
                                stringResource(R.string.advanced_blocking_required),
                                color = MaterialTheme.colorScheme.error
                            )
                            viewModel.isLocked -> Text(
                                stringResource(R.string.color_filter_locked, activeWindowsText),
                                color = MaterialTheme.colorScheme.primary
                            )
                            else -> Text(
                                stringResource(
                                    if (viewModel.filtersApplied) R.string.color_filter_status_on
                                    else R.string.color_filter_status_off
                                )
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Outlined.Palette, null) },
                    trailingContent = {
                        Switch(
                            checked = viewModel.enabled,
                            enabled = if (viewModel.enabled) !viewModel.isLocked
                                else accessibilityActive && !viewModel.grantingPermission,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (viewModel.permissionGranted) {
                                        viewModel.updateEnabled(true)
                                    } else if (!Shizuku.pingBinder()) {
                                        scope.launch { snackbarHostState.showSnackbar(shizukuNotRunningMsg) }
                                    } else {
                                        viewModel.grantPermission { granted ->
                                            if (granted) viewModel.updateEnabled(true)
                                            else scope.launch { snackbarHostState.showSnackbar(permissionFailedMsg) }
                                        }
                                    }
                                    return@Switch
                                }
                                recoveryGate.run {
                                    if (!viewModel.updateEnabled(false)) showLocked()
                                }
                            }
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                Text(
                    stringResource(R.string.color_filter_modes_section),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Text(
                    stringResource(R.string.color_filter_modes_desc),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (viewModel.modes.isEmpty()) {
                    Text(
                        stringResource(R.string.color_filter_no_modes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            items(ColorFilterMode.available) { mode ->
                val selected = mode in viewModel.modes
                ColorFilterModeRow(
                    mode = mode,
                    selected = selected,
                    enabled = !(selected && viewModel.isLocked),
                    onSelectedChange = { checked ->
                        if (checked) {
                            viewModel.updateMode(mode, true)
                        } else {
                            recoveryGate.run {
                                if (!viewModel.updateMode(mode, false)) showLocked()
                            }
                        }
                    }
                )
                if (selected && mode == ColorFilterMode.NIGHT_LIGHT) {
                    StrengthSlider(
                        label = stringResource(R.string.color_filter_night_light_intensity),
                        value = viewModel.nightLightIntensity,
                        enabled = !viewModel.isLocked,
                        recoveryGate = recoveryGate,
                        onChange = { viewModel.updateNightLightIntensity(it) },
                        onLocked = showLocked
                    )
                }
                if (selected && mode == ColorFilterMode.EXTRA_DIM) {
                    StrengthSlider(
                        label = stringResource(R.string.color_filter_extra_dim_level),
                        value = viewModel.extraDimLevel,
                        enabled = !viewModel.isLocked,
                        recoveryGate = recoveryGate,
                        onChange = { viewModel.updateExtraDimLevel(it) },
                        onLocked = showLocked
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val activeWindows = viewModel.activeWindows
                ListItem(
                    headlineContent = { Text(stringResource(R.string.color_filter_windows)) },
                    supportingContent = {
                        Column {
                            if (viewModel.windows.isEmpty()) {
                                Text(stringResource(R.string.color_filter_no_windows))
                            }
                            viewModel.windows.forEach { window ->
                                Text(
                                    if (window in activeWindows)
                                        stringResource(R.string.color_filter_window_active, window.formatted())
                                    else window.formatted()
                                )
                            }
                        }
                    },
                    leadingContent = { Icon(Icons.Outlined.Schedule, null) },
                    trailingContent = {
                        Button(onClick = { showWindowsDialog = true }) {
                            Text(stringResource(R.string.color_filter_edit_windows))
                        }
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                Text(
                    stringResource(R.string.color_filter_windows_desc),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showWindowsDialog) {
        TimeWindowsDialog(
            title = stringResource(R.string.color_filter_windows),
            initialWindows = viewModel.windows,
            lockedWindows = viewModel.lockedWindows.toSet(),
            emptyMessage = stringResource(R.string.color_filter_no_windows),
            onDismiss = { showWindowsDialog = false },
            onConfirm = { newWindows ->
                showWindowsDialog = false
                val save = {
                    if (!viewModel.updateWindows(newWindows)) showLocked()
                }
                if (viewModel.isOnlyAdding(newWindows)) save() else recoveryGate.run { save() }
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
private fun ColorFilterModeRow(
    mode: ColorFilterMode,
    selected: Boolean,
    enabled: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    val (icon, titleRes, descRes) = when (mode) {
        ColorFilterMode.GRAYSCALE -> Triple(
            Icons.Outlined.FilterBAndW, R.string.color_filter_mode_grayscale, R.string.color_filter_mode_grayscale_desc
        )
        ColorFilterMode.NIGHT_LIGHT -> Triple(
            Icons.Outlined.NightsStay, R.string.color_filter_mode_night_light, R.string.color_filter_mode_night_light_desc
        )
        ColorFilterMode.EXTRA_DIM -> Triple(
            Icons.Outlined.BrightnessLow, R.string.color_filter_mode_extra_dim, R.string.color_filter_mode_extra_dim_desc
        )
        ColorFilterMode.INVERSION -> Triple(
            Icons.Outlined.InvertColors, R.string.color_filter_mode_inversion, R.string.color_filter_mode_inversion_desc
        )
    }
    ListItem(
        headlineContent = { Text(stringResource(titleRes)) },
        supportingContent = { Text(stringResource(descRes)) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Checkbox(checked = selected, enabled = enabled, onCheckedChange = onSelectedChange)
        },
        modifier = Modifier.clickable(enabled = enabled) { onSelectedChange(!selected) }
    )
}

@Composable
private fun StrengthSlider(
    label: String,
    value: Int,
    enabled: Boolean,
    recoveryGate: RecoveryGate,
    onChange: (Int) -> Boolean,
    onLocked: () -> Unit
) {
    var draft by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(modifier = Modifier.padding(start = 72.dp, end = 16.dp, bottom = 8.dp)) {
        Text("$label: ${draft.roundToInt()}%", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = {
                val newValue = draft.roundToInt()
                if (newValue == value) return@Slider
                recoveryGate.run(onCancel = { draft = value.toFloat() }) {
                    if (!onChange(newValue)) {
                        draft = value.toFloat()
                        onLocked()
                    }
                }
            },
            valueRange = 0f..100f,
            enabled = enabled
        )
    }
}
