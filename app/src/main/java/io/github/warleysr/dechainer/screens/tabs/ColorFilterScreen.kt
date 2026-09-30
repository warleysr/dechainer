package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessLow
import androidx.compose.material.icons.outlined.FilterBAndW
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.ColorFilterController
import io.github.warleysr.dechainer.data.ColorFilterSettings
import io.github.warleysr.dechainer.models.ColorFilterMode
import io.github.warleysr.dechainer.models.ColorFilterScope
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.screens.common.AppPickerDialog
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
    var editingWindowsFor by remember { mutableStateOf<ColorFilterMode?>(null) }
    var pickingAppsFor by remember { mutableStateOf<ColorFilterMode?>(null) }
    val recoveryGate = rememberRecoveryGate()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val shizukuNotRunningMsg = stringResource(R.string.shizuku_not_running)
    val permissionFailedMsg = stringResource(R.string.color_filter_permission_failed)
    val lockedMsg = stringResource(R.string.color_filter_locked_error)
    val showLocked: () -> Unit = { scope.launch { snackbarHostState.showSnackbar(lockedMsg) } }

    val accessibilityActive = DechainerAccessibilityService.isRunning

    val saveCoverage: (ColorFilterMode, ColorFilterScope.Coverage) -> Unit = { mode, newCoverage ->
        val save = { if (!viewModel.updateCoverage(mode, newCoverage)) showLocked() }
        if (viewModel.isLoosening(mode, newCoverage)) recoveryGate.run { save() } else save()
    }

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
                            enabled = viewModel.enabled || (accessibilityActive && !viewModel.grantingPermission),
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
                    TemperaturePicker(
                        value = viewModel.nightLightTemperature,
                        onSelect = { kelvin ->
                            recoveryGate.run {
                                if (!viewModel.updateNightLightTemperature(kelvin)) showLocked()
                            }
                        }
                    )
                    // The platform filter has no opacity, only the overlay fallback does.
                    if (!ColorFilterController.platformNightLight) {
                        StrengthSlider(
                            label = stringResource(R.string.color_filter_night_light_intensity),
                            value = viewModel.nightLightIntensity,
                            max = ColorFilterSettings.MAX_NIGHT_LIGHT_INTENSITY,
                            recoveryGate = recoveryGate,
                            onChange = { viewModel.updateNightLightIntensity(it) },
                            onLocked = showLocked
                        )
                    }
                }
                if (selected && mode == ColorFilterMode.EXTRA_DIM) {
                    StrengthSlider(
                        label = stringResource(R.string.color_filter_extra_dim_level),
                        value = viewModel.extraDimLevel,
                        recoveryGate = recoveryGate,
                        onChange = { viewModel.updateExtraDimLevel(it) },
                        onLocked = showLocked
                    )
                }
                if (selected) {
                    ModeWindows(
                        windows = viewModel.windows(mode),
                        activeWindows = viewModel.activeWindows(mode),
                        onEdit = { editingWindowsFor = mode }
                    )
                    val coverage = viewModel.coverage(mode)
                    ModeScope(
                        coverage = coverage,
                        onScopeChange = { saveCoverage(mode, coverage.copy(scope = it)) },
                        onPickApps = {
                            viewModel.loadAppsIfNeeded()
                            pickingAppsFor = mode
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    editingWindowsFor?.let { mode ->
        TimeWindowsDialog(
            title = stringResource(R.string.color_filter_windows),
            initialWindows = viewModel.windows(mode),
            lockedWindows = viewModel.lockedWindows(mode),
            emptyMessage = stringResource(R.string.color_filter_no_windows),
            onDismiss = { editingWindowsFor = null },
            onConfirm = { newWindows ->
                editingWindowsFor = null
                val save = {
                    if (!viewModel.updateWindows(mode, newWindows)) showLocked()
                }
                if (viewModel.isOnlyAdding(mode, newWindows)) save() else recoveryGate.run { save() }
            }
        )
    }

    pickingAppsFor?.let { mode ->
        val coverage = viewModel.coverage(mode)
        val excluding = coverage.scope == ColorFilterScope.EXCEPT_APPS
        var draft by remember(mode) { mutableStateOf(if (excluding) coverage.excludedApps else coverage.onlyApps) }
        AppPickerDialog(
            apps = viewModel.apps,
            isLoading = viewModel.isLoadingApps,
            isSelected = { it in draft },
            onToggle = { pkg -> draft = if (pkg in draft) draft - pkg else draft + pkg },
            onDismiss = {
                pickingAppsFor = null
                val newCoverage = if (excluding) coverage.copy(excludedApps = draft) else coverage.copy(onlyApps = draft)
                if (newCoverage != coverage) saveCoverage(mode, newCoverage)
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
private fun ColorFilterModeRow(
    mode: ColorFilterMode,
    selected: Boolean,
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
            Checkbox(checked = selected, onCheckedChange = onSelectedChange)
        },
        modifier = Modifier.clickable { onSelectedChange(!selected) }
    )
}

@Composable
private fun ModeWindows(windows: List<TimeWindow>, activeWindows: List<TimeWindow>, onEdit: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.color_filter_windows),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            if (windows.isEmpty()) {
                Text(
                    stringResource(R.string.color_filter_no_windows),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            windows.forEach { window ->
                Text(
                    if (window in activeWindows) stringResource(R.string.color_filter_window_active, window.formatted())
                    else window.formatted(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.color_filter_edit_windows)) }
    }
}

@Composable
private fun ModeScope(
    coverage: ColorFilterScope.Coverage,
    onScopeChange: (ColorFilterScope) -> Unit,
    onPickApps: () -> Unit
) {
    Column(modifier = Modifier.padding(start = 72.dp, end = 16.dp, top = 8.dp)) {
        Text(
            stringResource(R.string.color_filter_scope),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        ColorFilterScope.entries.forEach { option ->
            val labelRes = when (option) {
                ColorFilterScope.DEVICE -> R.string.color_filter_scope_device
                ColorFilterScope.EXCEPT_APPS -> R.string.color_filter_scope_except_apps
                ColorFilterScope.ONLY_APPS -> R.string.color_filter_scope_only_apps
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == coverage.scope,
                        role = Role.RadioButton,
                        onClick = { if (option != coverage.scope) onScopeChange(option) }
                    )
            ) {
                RadioButton(selected = option == coverage.scope, onClick = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
            }
        }

        val apps = when (coverage.scope) {
            ColorFilterScope.DEVICE -> return@Column
            ColorFilterScope.EXCEPT_APPS -> coverage.excludedApps
            ColorFilterScope.ONLY_APPS -> coverage.onlyApps
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    apps.isNotEmpty() -> stringResource(R.string.visual_blocking_apps_selected, apps.size)
                    coverage.scope == ColorFilterScope.ONLY_APPS -> stringResource(R.string.color_filter_only_apps_empty)
                    else -> stringResource(R.string.no_apps)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (apps.isEmpty() && coverage.scope == ColorFilterScope.ONLY_APPS)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onPickApps) { Text(stringResource(R.string.select_apps)) }
        }
    }
}

@Composable
private fun StrengthSlider(
    label: String,
    value: Int,
    max: Int = 100,
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
            valueRange = 0f..max.toFloat()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TemperaturePicker(value: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.padding(start = 72.dp, end = 16.dp)) {
        Text(stringResource(R.string.color_filter_night_light_temperature), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorFilterSettings.NIGHT_LIGHT_TEMPERATURES.forEach { kelvin ->
                FilterChip(
                    selected = kelvin == value,
                    onClick = { if (kelvin != value) onSelect(kelvin) },
                    label = { Text("${kelvin}K") }
                )
            }
        }
    }
}
