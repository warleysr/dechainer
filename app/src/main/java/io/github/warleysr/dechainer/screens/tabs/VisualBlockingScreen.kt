package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.VisualBlockingSettings
import io.github.warleysr.dechainer.screens.common.AppPickerDialog
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.isLooserSet
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.viewmodels.VisualBlockingViewModel

@Composable
fun VisualBlockingScreen(viewModel: VisualBlockingViewModel = viewModel()) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    val recoveryGate = rememberRecoveryGate()

    val accessibilityActive = DechainerAccessibilityService.isRunning

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text(
                stringResource(R.string.visual_blocking_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.visual_blocking_enable)) },
                supportingContent = {
                    if (!accessibilityActive) {
                        Text(
                            stringResource(R.string.visual_blocking_requires_accessibility),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                },
                leadingContent = { Icon(Icons.Outlined.ImageSearch, null) },
                trailingContent = {
                    Switch(
                        checked = viewModel.enabled,
                        enabled = viewModel.enabled || accessibilityActive,
                        onCheckedChange = { checked ->
                            recoveryGate.change(loosening = !checked) {
                                viewModel.updateEnabled(checked)
                            }
                        }
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.visual_blocking_apps_section)) },
                supportingContent = {
                    val count = viewModel.targetPackages.size
                    Text(
                        if (count == 0) stringResource(R.string.no_apps)
                        else stringResource(R.string.visual_blocking_apps_selected, count)
                    )
                },
                trailingContent = {
                    Button(
                        onClick = { showAppSelectionDialog = true }
                    ) {
                        Text(stringResource(R.string.select_apps))
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        item {
            Text(
                stringResource(R.string.visual_blocking_categories_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                stringResource(R.string.visual_blocking_categories_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        items(VisualBlockingSettings.SELECTABLE_CATEGORIES) { category ->
            val isSelected = viewModel.selectedCategories.contains(category)
            val toggle = {
                val loosening = isSelected
                recoveryGate.change(loosening) {
                    viewModel.toggleCategory(category)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { toggle() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { toggle() }
                )
                Text(categoryLabel(category), modifier = Modifier.padding(start = 8.dp))
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        item {
            Text(
                stringResource(R.string.visual_blocking_threshold_section),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(
                stringResource(R.string.visual_blocking_threshold_desc),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            var draftThreshold by remember(viewModel.threshold) { mutableFloatStateOf(viewModel.threshold) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = draftThreshold,
                    onValueChange = { draftThreshold = it },
                    onValueChangeFinished = {
                        if (draftThreshold != viewModel.threshold) {
                            val loosening = draftThreshold > viewModel.threshold
                            recoveryGate.change(
                                loosening = loosening,
                                onCancel = { draftThreshold = viewModel.threshold }
                            ) {
                                viewModel.updateThreshold(draftThreshold)
                            }
                        }
                    },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(draftThreshold * 100).toInt()}%",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        item {
            val toggleSuspend = {
                val target = !viewModel.suspendEnabled
                recoveryGate.change(loosening = !target) {
                    viewModel.updateSuspendEnabled(target)
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.visual_blocking_suspend)) },
                supportingContent = { Text(stringResource(R.string.visual_blocking_suspend_desc)) },
                leadingContent = { Icon(Icons.Outlined.Timer, null) },
                trailingContent = {
                    Checkbox(
                        checked = viewModel.suspendEnabled,
                        onCheckedChange = { toggleSuspend() }
                    )
                },
                modifier = Modifier.clickable { toggleSuspend() }
            )

            if (viewModel.suspendEnabled) {
                NumberSettingField(
                    label = stringResource(R.string.visual_blocking_suspend_count),
                    supportingText = stringResource(R.string.visual_blocking_suspend_count_desc),
                    value = viewModel.suspendBlockCount,
                    onValueCommit = { newValue, onCancel ->
                        val loosening = newValue > viewModel.suspendBlockCount
                        recoveryGate.change(loosening, onCancel) {
                            viewModel.updateSuspendBlockCount(newValue)
                        }
                    }
                )
                NumberSettingField(
                    label = stringResource(R.string.visual_blocking_suspend_window),
                    supportingText = stringResource(R.string.visual_blocking_suspend_window_desc),
                    value = viewModel.suspendWindowMinutes,
                    onValueCommit = { newValue, onCancel ->
                        val loosening = newValue < viewModel.suspendWindowMinutes
                        recoveryGate.change(loosening, onCancel) {
                            viewModel.updateSuspendWindowMinutes(newValue)
                        }
                    }
                )
                NumberSettingField(
                    label = stringResource(R.string.visual_blocking_suspend_duration),
                    supportingText = stringResource(R.string.visual_blocking_suspend_duration_desc),
                    value = viewModel.suspendDurationMinutes,
                    onValueCommit = { newValue, onCancel ->
                        val loosening = newValue < viewModel.suspendDurationMinutes
                        recoveryGate.change(loosening, onCancel) {
                            viewModel.updateSuspendDurationMinutes(newValue)
                        }
                    }
                )
                Text(
                    stringResource(
                        R.string.visual_blocking_suspend_summary,
                        viewModel.suspendBlockCount,
                        viewModel.suspendWindowMinutes,
                        viewModel.suspendDurationMinutes
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAppSelectionDialog) {
        var draftApps by remember(showAppSelectionDialog) { mutableStateOf(viewModel.targetPackages) }
        AppPickerDialog(
            apps = viewModel.apps,
            isLoading = viewModel.isLoadingApps,
            isSelected = { it in draftApps },
            onToggle = { pkg ->
                draftApps = if (pkg in draftApps) draftApps - pkg else draftApps + pkg
            },
            onDismiss = { showAppSelectionDialog = false },
            onConfirm = {
                if (draftApps != viewModel.targetPackages) {
                    val loosening = isLooserSet(viewModel.targetPackages, draftApps)
                    recoveryGate.change(loosening) {
                        viewModel.updateTargetPackages(draftApps)
                    }
                }
                showAppSelectionDialog = false
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

/**
 * Label + numeric text field row used by the "suspend app" settings. Commits changes on focus
 * loss or keyboard 'Done'.
 */
@Composable
private fun NumberSettingField(
    label: String,
    supportingText: String,
    value: Int,
    onValueCommit: (newValue: Int, onCancel: () -> Unit) -> Unit
) {
    var textFieldValue by remember(value) { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    val commit = {
        val parsed = textFieldValue.toIntOrNull() ?: 0
        if (parsed >= 1 && parsed != value) {
            onValueCommit(parsed) {
                textFieldValue = value.toString()
            }
        } else {
            textFieldValue = value.toString()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(supportingText, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                    textFieldValue = newValue
                }
            },
            modifier = Modifier
                .width(96.dp)
                .padding(start = 8.dp)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused && textFieldValue != value.toString()) {
                        commit()
                    }
                },
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    commit()
                    focusManager.clearFocus()
                }
            ),
            singleLine = true
        )
    }
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    "FEMALE_GENITALIA_EXPOSED" -> stringResource(R.string.nudenet_category_female_genitalia_exposed)
    "MALE_GENITALIA_EXPOSED" -> stringResource(R.string.nudenet_category_male_genitalia_exposed)
    "ANUS_EXPOSED" -> stringResource(R.string.nudenet_category_anus_exposed)
    "FEMALE_BREAST_EXPOSED" -> stringResource(R.string.nudenet_category_female_breast_exposed)
    "BUTTOCKS_EXPOSED" -> stringResource(R.string.nudenet_category_buttocks_exposed)
    "FEMALE_BREAST_COVERED" -> stringResource(R.string.nudenet_category_female_breast_covered)
    "BUTTOCKS_COVERED" -> stringResource(R.string.nudenet_category_buttocks_covered)
    "FEMALE_GENITALIA_COVERED" -> stringResource(R.string.nudenet_category_female_genitalia_covered)
    "ANUS_COVERED" -> stringResource(R.string.nudenet_category_anus_covered)
    "drawings" -> stringResource(R.string.nsfw_category_drawings)
    "hentai" -> stringResource(R.string.nsfw_category_hentai)
    "porn" -> stringResource(R.string.nsfw_category_porn)
    "sexy" -> stringResource(R.string.nsfw_category_sexy)
    else -> category
}
