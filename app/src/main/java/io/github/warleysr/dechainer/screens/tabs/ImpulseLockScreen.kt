package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.PermanentRestrictionsManager
import io.github.warleysr.dechainer.screens.common.AppPickerDialog
import io.github.warleysr.dechainer.screens.common.PermanentRestrictionBanner
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.ImpulseLockViewModel

@Composable
fun ImpulseLockScreen(viewModel: ImpulseLockViewModel = viewModel()) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var showAddPassageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val isPermanent = remember {
        PermanentRestrictionsManager.isPermanent(context, PermanentRestrictionsManager.KEY_IMPULSE_LOCK)
    }
    val recoveryGate = rememberRecoveryGate(PermanentRestrictionsManager.KEY_IMPULSE_LOCK)

    val sessionActive = recoveryGate.isSessionActive && !isPermanent
    val accessibilityActive = DechainerAccessibilityService.isRunning

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (isPermanent) {
            item {
                PermanentRestrictionBanner()
            }
        }
        item {
            Text(
                stringResource(R.string.impulse_lock_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (!isPermanent && !sessionActive) {
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.visual_blocking_locked)) },
                    leadingContent = { Icon(Icons.Outlined.Lock, null) },
                    modifier = Modifier.clickable { recoveryGate.run {} }
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
            SecurityManager.ChallengeType.entries.forEach { type ->
                OptionRow(
                    selected = type in viewModel.challenges,
                    enabled = sessionActive,
                    multiple = true,
                    title = when (type) {
                        SecurityManager.ChallengeType.MATH -> stringResource(R.string.challenge_option_math)
                        SecurityManager.ChallengeType.WORDS -> stringResource(R.string.challenge_option_words)
                        SecurityManager.ChallengeType.TETRIS -> stringResource(R.string.challenge_option_tetris)
                        SecurityManager.ChallengeType.READING -> stringResource(R.string.challenge_option_reading)
                    },
                    supporting = when (type) {
                        SecurityManager.ChallengeType.MATH -> stringResource(R.string.challenge_option_math_desc)
                        SecurityManager.ChallengeType.WORDS -> stringResource(R.string.challenge_option_words_desc)
                        SecurityManager.ChallengeType.TETRIS -> stringResource(R.string.challenge_option_tetris_desc)
                        SecurityManager.ChallengeType.READING -> stringResource(R.string.challenge_option_reading_desc)
                    },
                    onClick = { viewModel.toggleChallenge(type) }
                )
                // Each challenge's own settings sit right under its option, not after the whole list.
                if (type in viewModel.challenges) {
                    when (type) {
                        SecurityManager.ChallengeType.TETRIS ->
                            TetrisSettings(viewModel = viewModel, enabled = sessionActive)
                        SecurityManager.ChallengeType.READING -> ReadingSettings(
                            viewModel = viewModel,
                            enabled = sessionActive,
                            onAddPassage = { showAddPassageDialog = true }
                        )
                        else -> {}
                    }
                }
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

    if (showAddPassageDialog) {
        AddPassageDialog(
            onConfirm = { text, source ->
                viewModel.addCustomPassage(text, source)
                showAddPassageDialog = false
            },
            onDismiss = { showAddPassageDialog = false }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
private fun TetrisSettings(viewModel: ImpulseLockViewModel, enabled: Boolean) {
    Text(
        stringResource(R.string.tetris_duration),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    Text(
        formatDuration(viewModel.tetrisMinutes),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Slider(
        value = viewModel.tetrisMinutes.toFloat(),
        enabled = enabled,
        onValueChange = { viewModel.updateTetrisMinutes(it.toInt()) },
        valueRange = SecurityManager.TETRIS_MIN_MINUTES.toFloat()..
            SecurityManager.TETRIS_MAX_MINUTES.toFloat(),
        steps = SecurityManager.TETRIS_MAX_MINUTES - SecurityManager.TETRIS_MIN_MINUTES - 1,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun ReadingSettings(viewModel: ImpulseLockViewModel, enabled: Boolean, onAddPassage: () -> Unit) {
    Text(
        stringResource(R.string.reading_source_section),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    SecurityManager.ReadingSource.entries.forEach { source ->
        OptionRow(
            selected = viewModel.readingSource == source,
            enabled = enabled,
            title = when (source) {
                SecurityManager.ReadingSource.BIBLE -> stringResource(R.string.reading_source_bible)
                SecurityManager.ReadingSource.QURAN -> stringResource(R.string.reading_source_quran)
                SecurityManager.ReadingSource.QUOTES -> stringResource(R.string.reading_source_quotes)
                SecurityManager.ReadingSource.CUSTOM -> stringResource(R.string.reading_source_custom)
            },
            supporting = when (source) {
                SecurityManager.ReadingSource.BIBLE -> stringResource(R.string.reading_source_bible_desc)
                SecurityManager.ReadingSource.QURAN -> stringResource(R.string.reading_source_quran_desc)
                SecurityManager.ReadingSource.QUOTES -> stringResource(R.string.reading_source_quotes_desc)
                SecurityManager.ReadingSource.CUSTOM -> stringResource(R.string.reading_source_custom_desc)
            },
            onClick = { viewModel.updateReadingSource(source) }
        )
    }

    if (viewModel.readingSource == SecurityManager.ReadingSource.CUSTOM) {
        viewModel.customPassages.forEachIndexed { index, passage ->
            ListItem(
                headlineContent = { Text(passage.text, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                supportingContent = if (passage.source.isNotBlank()) {
                    { Text(passage.source) }
                } else null,
                trailingContent = {
                    IconButton(enabled = enabled, onClick = { viewModel.removeCustomPassage(index) }) {
                        Icon(Icons.Outlined.Delete, stringResource(R.string.reading_custom_delete))
                    }
                },
                modifier = Modifier.padding(start = 40.dp)
            )
        }
        if (viewModel.customPassages.isEmpty()) {
            Text(
                stringResource(R.string.reading_custom_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        OutlinedButton(
            enabled = enabled,
            onClick = onAddPassage,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Icon(Icons.Outlined.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.reading_custom_add))
        }
    }

    val available = viewModel.availableReadings
    if (available > 0) {
        Text(
            stringResource(R.string.reading_count),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        Text(
            stringResource(R.string.reading_count_value, viewModel.readingCount, available),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (available > SecurityManager.READING_MIN_COUNT) {
            Slider(
                value = viewModel.readingCount.toFloat(),
                enabled = enabled,
                onValueChange = { viewModel.updateReadingCount(it.toInt()) },
                valueRange = SecurityManager.READING_MIN_COUNT.toFloat()..available.toFloat(),
                steps = available - SecurityManager.READING_MIN_COUNT - 1,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@Composable
private fun AddPassageDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reading_custom_add)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.reading_custom_text)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(stringResource(R.string.reading_custom_source)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text, source) }) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
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
    supporting: String? = null,
    multiple: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (multiple) {
            Checkbox(checked = selected, enabled = enabled, onCheckedChange = { onClick() })
        } else {
            RadioButton(selected = selected, enabled = enabled, onClick = onClick)
        }
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
