package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.screens.common.NoDeviceOwnerPrivileges
import io.github.warleysr.dechainer.viewmodels.AppItem
import io.github.warleysr.dechainer.viewmodels.AppsViewModel
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import io.github.warleysr.dechainer.R

@Composable
fun AppsTab(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel(),
    appsViewModel: AppsViewModel = viewModel()
) {
    if (!deviceOwnerViewModel.isDeviceOwner()) {
        NoDeviceOwnerPrivileges(deviceOwnerViewModel)
    } else {
        AppsScreen(appsViewModel)
    }
}

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedApp by remember { mutableStateOf<AppItem?>(null) }

    val filteredApps = remember(viewModel.apps, searchQuery) {
        viewModel.apps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(tonalElevation = 3.dp) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text(stringResource(R.string.search_apps)) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
        }

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filteredApps, key = { it.packageName }) { app ->
                    AppRow(app) { selectedApp = app }
                }
            }
        }
    }

    selectedApp?.let { app ->
        AppActionDialog(
            app = app,
            onDismiss = { selectedApp = null },
            onBlock = {
                viewModel.blockApp(app.packageName, !app.isHidden)
                selectedApp = null
            },
            onToggleUninstall = {
                viewModel.setUninstallBlocked(app.packageName, !app.isUninstallBlocked)
                selectedApp = null
            }
        )
    }
}

@Composable
fun AppRow(app: AppItem, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(app.name) },
        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
        leadingContent = {
            Image(
                bitmap = app.icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (app.isHidden) {
                    StatusBadge(
                        text = stringResource(R.string.blocked),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (app.isUninstallBlocked) {
                    StatusBadge(
                        text = stringResource(R.string.protected_label),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    )
}

@Composable
private fun StatusBadge(text: String, containerColor: androidx.compose.ui.graphics.Color, contentColor: androidx.compose.ui.graphics.Color) {
    SuggestionChip(
        onClick = { },
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = containerColor,
            labelColor = contentColor
        )
    )
}

@Composable
fun AppActionDialog(
    app: AppItem,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onToggleUninstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(app.name) },
        text = { Text("Gerenciar restrições para ${app.packageName}") },
        confirmButton = {
            TextButton(onClick = onBlock) {
                Text(if (app.isHidden) "Desbloquear" else "Bloquear")
            }
        },
        dismissButton = {
            TextButton(onClick = onToggleUninstall) {
                Text(if (app.isUninstallBlocked) "Permitir Desinstalar" else "Impedir Desinstalação")
            }
        }
    )
}
