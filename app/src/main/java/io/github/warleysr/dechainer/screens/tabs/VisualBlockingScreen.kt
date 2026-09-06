package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
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
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showAppSelectionDialog) {
        VisualBlockingAppSelectionDialog(
            viewModel = viewModel,
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
private fun categoryLabel(category: String): String = when (category) {
    "drawings" -> stringResource(R.string.nsfw_category_drawings)
    "hentai" -> stringResource(R.string.nsfw_category_hentai)
    "porn" -> stringResource(R.string.nsfw_category_porn)
    "sexy" -> stringResource(R.string.nsfw_category_sexy)
    else -> category
}

@Composable
private fun VisualBlockingAppSelectionDialog(
    viewModel: VisualBlockingViewModel,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val filteredApps = remember(searchQuery, viewModel.apps, showSystemApps) {
        viewModel.apps
            .filter {
                (showSystemApps || !it.isSystem) &&
                    (it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true))
            }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_apps)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, null)
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
                        }
                    }
                }

                if (viewModel.isLoadingApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(filteredApps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleAppSelection(app.packageName) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Column(modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 12.dp)) {
                                    Text(app.name, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = viewModel.targetPackages.contains(app.packageName),
                                    onCheckedChange = { viewModel.toggleAppSelection(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}
