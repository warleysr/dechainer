package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.AppPickerDialog
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.utils.VisualBlockingSettings
import io.github.warleysr.dechainer.viewmodels.VisualBlockingViewModel

@Composable
fun VisualBlockingScreen(viewModel: VisualBlockingViewModel = viewModel()) {
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
                stringResource(R.string.visual_blocking_explanation),
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
                        enabled = sessionActive && accessibilityActive,
                        onCheckedChange = { viewModel.updateEnabled(it) }
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
                        enabled = sessionActive,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = sessionActive) { viewModel.toggleCategory(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.selectedCategories.contains(category),
                    enabled = sessionActive,
                    onCheckedChange = { viewModel.toggleCategory(category) }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Slider(
                    value = viewModel.threshold,
                    enabled = sessionActive,
                    onValueChange = { viewModel.updateThreshold(it) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${(viewModel.threshold * 100).toInt()}%",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }

        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.visual_blocking_suspend)) },
                supportingContent = { Text(stringResource(R.string.visual_blocking_suspend_desc)) },
                leadingContent = { Icon(Icons.Outlined.Timer, null) },
                trailingContent = {
                    Checkbox(
                        checked = viewModel.suspendEnabled,
                        enabled = sessionActive,
                        onCheckedChange = { viewModel.updateSuspendEnabled(it) }
                    )
                },
                modifier = Modifier.clickable(enabled = sessionActive) {
                    viewModel.updateSuspendEnabled(!viewModel.suspendEnabled)
                }
            )

            if (viewModel.suspendEnabled) {
                NumberSettingField(
                    label = stringResource(R.string.visual_blocking_suspend_count),
                    supportingText = stringResource(R.string.visual_blocking_suspend_count_desc),
                    value = viewModel.suspendBlockCount,
                    enabled = sessionActive,
                    onValueChange = { viewModel.updateSuspendBlockCount(it) }
                )
                NumberSettingField(
                    label = stringResource(R.string.visual_blocking_suspend_window),
                    supportingText = stringResource(R.string.visual_blocking_suspend_window_desc),
                    value = viewModel.suspendWindowMinutes,
                    enabled = sessionActive,
                    onValueChange = { viewModel.updateSuspendWindowMinutes(it) }
                )
                NumberSettingField(
                    label = stringResource(R.string.visual_blocking_suspend_duration),
                    supportingText = stringResource(R.string.visual_blocking_suspend_duration_desc),
                    value = viewModel.suspendDurationMinutes,
                    enabled = sessionActive,
                    onValueChange = { viewModel.updateSuspendDurationMinutes(it) }
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
        AppPickerDialog(
            apps = viewModel.apps,
            isLoading = viewModel.isLoadingApps,
            isSelected = { viewModel.targetPackages.contains(it) },
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

/**
 * Label + numeric text field row used by the "suspend app" settings. The field is allowed to go
 * empty while the user retypes a number — [onValueChange] then reports 0, which the view model
 * treats as "not a usable value yet" and doesn't persist.
 */
@Composable
private fun NumberSettingField(
    label: String,
    supportingText: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    var textFieldValue by remember { mutableStateOf(value.toString()) }

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
            enabled = enabled,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() } && newValue.length <= 4) {
                    textFieldValue = newValue
                    onValueChange(newValue.toIntOrNull() ?: 0)
                }
            },
            modifier = Modifier
                .width(96.dp)
                .padding(start = 8.dp),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
private fun categoryLabel(category: String): String = when (category) {
    "drawings" -> stringResource(R.string.nsfw_category_drawings)
    "hentai" -> stringResource(R.string.nsfw_category_hentai)
    "porn" -> stringResource(R.string.nsfw_category_porn)
    "sexy" -> stringResource(R.string.nsfw_category_sexy)
    else -> category
}

