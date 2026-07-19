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
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.BlockedWordsViewModel

@Composable
fun BlockedWordsScreen(
    viewModel: BlockedWordsViewModel = viewModel()
) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var showPassiveAppSelectionDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var onRecoverySuccess by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    var passiveAppToEdit by remember { mutableStateOf<String?>(null) }
    
    var activeExpanded by remember { mutableStateOf(true) }
    var passiveExpanded by remember { mutableStateOf(true) }

    val context = LocalContext.current

    fun runWithRecovery(action: () -> Unit) {
        if (SecurityManager.isSessionActive()) {
            action()
        } else {
            onRecoverySuccess = action
            showRecoveryDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
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
                                runWithRecovery { showAppSelectionDialog = true }
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
                            onClick = { runWithRecovery {} },
                            modifier = Modifier.padding(horizontal = 16.dp),
                            enabled = !SecurityManager.isSessionActive()
                        ) {
                            Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp))
                        }
                    }

                    OutlinedTextField(
                        value = viewModel.blockedWordsText,
                        onValueChange = {
                            viewModel.updateWords(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 256.dp, max = 256.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.word_hint)) },
                        supportingText = { Text(stringResource(R.string.one_word_per_line)) },
                        enabled = SecurityManager.isSessionActive()
                    )
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item {
            SectionHeader(
                title = stringResource(R.string.passive_blocking),
                description = stringResource(R.string.passive_blocking_tooltip),
                isExpanded = passiveExpanded,
                onToggle = { passiveExpanded = !passiveExpanded }
            )
        }

        item {
            AnimatedVisibility(visible = passiveExpanded) {
                Column {
                    viewModel.passiveWordsMap.keys.forEach { pkg ->
                        val app = viewModel.apps.find { it.packageName == pkg }
                        ListItem(
                            headlineContent = { Text(app?.name ?: pkg) },
                            supportingContent = {
                                val wordCount = viewModel.passiveWordsMap[pkg]?.split("\n")?.count { it.isNotBlank() } ?: 0
                                Text("$wordCount " + stringResource(R.string.blocked_words_feat))
                            },
                            leadingContent = {
                                app?.icon?.let {
                                    Image(
                                        bitmap = it.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { 
                                        runWithRecovery { passiveAppToEdit = pkg }
                                    }) {
                                        Icon(Icons.Outlined.Edit, null)
                                    }
                                    IconButton(onClick = { 
                                        runWithRecovery { viewModel.updatePassiveWords(pkg, "") }
                                    }) {
                                        Icon(Icons.Default.Delete, null)
                                    }
                                }
                            }
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { 
                            runWithRecovery { showPassiveAppSelectionDialog = true }
                        }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_app))
                        }
                    }
                }
            }
        }
    }

    if (showAppSelectionDialog) {
        AppSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showAppSelectionDialog = false },
            onAppClick = { pkg -> viewModel.toggleAppSelection(pkg) },
            multiSelect = true
        )
    }

    if (showPassiveAppSelectionDialog) {
        AppSelectionDialog(
            viewModel = viewModel,
            onDismiss = { showPassiveAppSelectionDialog = false },
            onAppClick = { pkg ->
                passiveAppToEdit = pkg
                showPassiveAppSelectionDialog = false
            },
            multiSelect = false
        )
    }

    if (passiveAppToEdit != null) {
        val pkg = passiveAppToEdit!!
        val app = viewModel.apps.find { it.packageName == pkg }
        var words by remember { mutableStateOf(viewModel.passiveWordsMap[pkg] ?: "") }

        AlertDialog(
            onDismissRequest = { passiveAppToEdit = null },
            title = { Text(app?.name ?: pkg) },
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
                    viewModel.updatePassiveWords(pkg, words)
                    passiveAppToEdit = null
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { passiveAppToEdit = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
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
    onDismiss: () -> Unit,
    onAppClick: (String) -> Unit,
    multiSelect: Boolean
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, viewModel.apps) {
        viewModel.apps
            .filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_apps)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )

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
                                        checked = viewModel.targetPackages.contains(app.packageName),
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
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.confirm))
                }
            }
        }
    )
}
