package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
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
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.PermanentRestrictionsManager
import io.github.warleysr.dechainer.screens.common.PermanentRestrictionBanner
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.viewmodels.BlockedWordsViewModel

@Composable
fun BlockedWordsScreen(
    viewModel: BlockedWordsViewModel = viewModel()
) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var showActiveEdit by remember { mutableStateOf(false) }
    var activeExpanded by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val isPermanent = remember {
        PermanentRestrictionsManager.isPermanent(context, PermanentRestrictionsManager.KEY_BLOCKED_WORDS)
    }
    val recoveryGate = rememberRecoveryGate(PermanentRestrictionsManager.KEY_BLOCKED_WORDS)

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isPermanent) {
            item {
                PermanentRestrictionBanner()
            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.active_blocking),
                description = stringResource(R.string.active_blocking_tooltip),
                isExpanded = activeExpanded,
                onToggle = { activeExpanded = !activeExpanded }
            )
        }

        item {
            AnimatedVisibility(visible = activeExpanded) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.apply_to_apps)) },
                        supportingContent = {
                            val count = viewModel.targetPackages.size
                            Text(if (count == 0) stringResource(R.string.no_apps) else "$count apps")
                        },
                        trailingContent = {
                            Button(onClick = {
                                showAppSelectionDialog = true
                            }) {
                                Text(stringResource(R.string.select_apps))
                            }
                        }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.blocked_words_list),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        TextButton(
                            onClick = { showActiveEdit = true },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        }
                    }

                    OutlinedTextField(
                        value = viewModel.blockedWordsText,
                        onValueChange = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 256.dp, max = 256.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.word_hint)) },
                        supportingText = { Text(stringResource(R.string.one_word_per_line)) },
                        readOnly = true
                    )
                }
            }
        }
    }

    if (showAppSelectionDialog) {
        var selectedPackages by remember { mutableStateOf(viewModel.targetPackages) }
        AppSelectionDialog(
            viewModel = viewModel,
            selectedPackages = selectedPackages,
            onDismiss = { showAppSelectionDialog = false },
            onConfirm = {
                val loosening = !selectedPackages.containsAll(viewModel.targetPackages)
                recoveryGate.change(loosening) {
                    viewModel.updateTargetPackages(selectedPackages)
                }
                showAppSelectionDialog = false
            },
            onAppClick = { pkg ->
                selectedPackages = if (selectedPackages.contains(pkg)) {
                    selectedPackages - pkg
                } else {
                    selectedPackages + pkg
                }
            },
            multiSelect = true
        )
    }

    if (showActiveEdit) {
        var words by remember { mutableStateOf(viewModel.blockedWordsText) }
        AlertDialog(
            onDismissRequest = { showActiveEdit = false },
            title = { Text(stringResource(R.string.blocked_words_list)) },
            text = {
                OutlinedTextField(
                    value = words,
                    onValueChange = { words = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    placeholder = { Text(stringResource(R.string.word_hint)) },
                    supportingText = { Text(stringResource(R.string.one_word_per_line)) }
                )
            },
            confirmButton = {
                Button(onClick = {
                    val loosening = !wordSet(words).containsAll(wordSet(viewModel.blockedWordsText))
                    recoveryGate.change(loosening) { viewModel.updateWords(words) }
                    showActiveEdit = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showActiveEdit = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
fun SectionHeader(
    title: String,
    description: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { showInfo = true }) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text(title) },
            text = { Text(description) },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }
}

@Composable
fun AppSelectionDialog(
    viewModel: BlockedWordsViewModel,
    selectedPackages: Set<String> = viewModel.targetPackages,
    onDismiss: () -> Unit,
    onConfirm: (() -> Unit)? = null,
    onAppClick: (String) -> Unit,
    multiSelect: Boolean
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
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
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
                        }
                    }
                }

                if (viewModel.isLoadingApps) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
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
                                    .clickable { onAppClick(app.packageName) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp)
                                )
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(app.name, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                                if (multiSelect) {
                                    Checkbox(
                                        checked = selectedPackages.contains(app.packageName),
                                        onCheckedChange = { onAppClick(app.packageName) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multiSelect) {
                Button(onClick = {
                    onConfirm?.invoke() ?: onDismiss()
                }) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    )
}

private fun wordSet(text: String) = text.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
