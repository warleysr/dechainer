package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.BrowserRestrictionsManager
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.AppsViewModel
import io.github.warleysr.dechainer.viewmodels.BlockedList
import io.github.warleysr.dechainer.viewmodels.BrowserRestrictionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserRestrictionsScreen(
    viewModel: BrowserRestrictionsViewModel = viewModel(),
    appsViewModel: AppsViewModel = viewModel()
) {
    var showEditDialog by remember { mutableStateOf<BlockedList?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.allowed_browsers),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        viewModel.browsers.forEach { browser ->
                            val manager = BrowserRestrictionsManager(context)
                            val notSupported = !manager.supportsRestrictions(browser.packageName)
                            ListItem(
                                headlineContent = { Text(browser.name) },
                                supportingContent = {
                                    Column {
                                        Text(browser.packageName)
                                        if (notSupported) {
                                            Text(
                                                stringResource(R.string.browser_support_warning),
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                },
                                leadingContent = {
                                    Image(
                                        bitmap = browser.icon.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp)
                                    )
                                },
                                trailingContent = {
                                    Switch(
                                        checked = browser.isEnabled,
                                        onCheckedChange = { checked ->
                                            pendingAction = {
                                                appsViewModel.suspendApp(browser.packageName, !checked)
                                                browser.isEnabled = checked
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Blocked Sites Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.blocked_sites),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = { 
                        isCreatingNew = true
                        showEditDialog = BlockedList("", "", emptyList())
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                }
            }

            items(viewModel.blockedLists, key = { "url_${it.id}" }) { list ->
                BlockedListAccordion(
                    list = list,
                    onEdit = { 
                        isCreatingNew = false
                        showEditDialog = list 
                    },
                    onDelete = {
                        pendingAction = { viewModel.removeList(list.id) }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

        }
    }

    if (showEditDialog != null) {
        val currentList = showEditDialog!!
        var title by remember { mutableStateOf(currentList.title) }
        var sites by remember { mutableStateOf(currentList.sites.joinToString("\n")) }
        
        AlertDialog(
            onDismissRequest = { showEditDialog = null },
            title = { 
                Text(
                    if (isCreatingNew) {
                        stringResource(R.string.add_site)
                    } else currentList.title
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título da Lista") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = sites,
                        onValueChange = { sites = it },
                        label = { Text("Sites (um por linha)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        pendingAction = { 
                            viewModel.saveOrUpdateList(
                                if (isCreatingNew) null else currentList.id,
                                title,
                                sites
                            )
                        }
                    }
                    showEditDialog = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (pendingAction != null) {
        val storedHash = SecurityManager.getRecoveryHash(context)
        if (storedHash == null) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            RecoveryConfirmDialog(
                onConfirm = { code ->
                    if (SecurityManager.validatePassphrase(code, storedHash)) {
                        pendingAction?.invoke()
                        pendingAction = null
                        true
                    } else false
                },
                onDismiss = { pendingAction = null }
            )
        }
    }
}

@Composable
fun BlockedListAccordion(
    list: BlockedList,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(list.title, style = MaterialTheme.typography.titleMedium)
                    Text("${list.sites.size} sites", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "")
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    HorizontalDivider()
                    list.sites.forEach { site ->
                        Text(
                            site,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
