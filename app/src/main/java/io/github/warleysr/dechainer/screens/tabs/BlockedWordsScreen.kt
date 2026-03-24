package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedWordsScreen(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel(),
    blockedWordsViewModel: BlockedWordsViewModel = viewModel()
) {
    var showAppSelectionDialog by remember { mutableStateOf(false) }
    var showRecoveryDialog by remember { mutableStateOf(false) }
    var isSessionActive by remember { mutableStateOf(SecurityManager.isSessionActive()) }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.apply_to_apps)) },
            supportingContent = {
                val count = blockedWordsViewModel.targetPackages.size
                Text(if (count == 0) stringResource(R.string.no_apps) else "$count apps")
            },
            trailingContent = {
                Button(onClick = { showAppSelectionDialog = true }) {
                    Text(stringResource(R.string.select_apps))
                }
            }
        )
        HorizontalDivider()

        Row {
            Text(
                stringResource(R.string.blocked_words_list),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )
            IconButton(onClick = { showRecoveryDialog = true }, enabled = !isSessionActive) {
                Icon(Icons.Outlined.Edit, null)
            }
        }

        OutlinedTextField(
            value = blockedWordsViewModel.blockedWordsText,
            onValueChange = {
                blockedWordsViewModel.updateWords(it)
                isSessionActive = SecurityManager.isSessionActive()
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.word_hint)) },
            supportingText = { Text(stringResource(R.string.one_word_per_line)) },
            enabled = isSessionActive
        )
    }

    if (showAppSelectionDialog) {
        AppSelectionDialog(
            viewModel = blockedWordsViewModel,
            onDismiss = { showAppSelectionDialog = false }
        )
    }

    if (showRecoveryDialog) {
        val storedHash = SecurityManager.getRecoveryHash(LocalContext.current)
        RecoveryConfirmDialog(
            onConfirm = { code ->
                if (SecurityManager.validatePassphrase(code, storedHash!!)) {
                    isSessionActive = true
                    true
                }
                else false
            },
            onDismiss = { showRecoveryDialog = false }
        )
    }
}

@Composable
fun AppSelectionDialog(
    viewModel: BlockedWordsViewModel,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, viewModel.apps) {
        viewModel.apps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
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
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
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
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(app.name, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                                }
                                Checkbox(
                                    checked = app.isSelected,
                                    onCheckedChange = { viewModel.toggleAppSelection(app.packageName) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}
