package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.NoDeviceOwnerPrivileges
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import io.github.warleysr.dechainer.viewmodels.NavigationViewModel
import io.github.warleysr.dechainer.viewmodels.RestrictionsViewModel

@Composable
fun RestrictionsTab(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel(),
    restrictionsViewModel: RestrictionsViewModel = viewModel(),
    navViewModel: NavigationViewModel = viewModel()
) {
    val recoveryGate = rememberRecoveryGate()

    if (!deviceOwnerViewModel.isDeviceOwner()) {
        NoDeviceOwnerPrivileges(navViewModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                RestrictionAccordion(
                    title = stringResource(R.string.recommended_configs),
                    keys = restrictionsViewModel.recommendedKeys,
                    viewModel = restrictionsViewModel,
                    defaultExpanded = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                RestrictionAccordion(
                    title = stringResource(R.string.other_restrictions),
                    keys = restrictionsViewModel.otherKeys,
                    viewModel = restrictionsViewModel,
                    defaultExpanded = false,
                    showSearch = true
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { recoveryGate.run { restrictionsViewModel.applyChanges() } },
                enabled = restrictionsViewModel.hasPendingChanges(),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .wrapContentWidth()
                    .height(52.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.apply_restrictions),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    RecoveryGateDialog(recoveryGate)
}


@Composable
private fun RestrictionAccordion(
    title: String,
    keys: List<String>,
    viewModel: RestrictionsViewModel,
    defaultExpanded: Boolean,
    showSearch: Boolean = false
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val labelMap = remember(keys) {
        keys.associateWith { key ->
            viewModel.resourceNameFor(key)
                ?.let { context.resources.getIdentifier(it, "string", context.packageName) }
                ?.takeIf { it != 0 }
        }
    }

    val filteredKeys = remember(keys, searchQuery, labelMap) {
        if (searchQuery.isEmpty()) {
            keys
        } else {
            keys.filter { key ->
                val label = labelMap[key]?.let { context.getString(it) } ?: key
                label.contains(searchQuery, ignoreCase = true) || key.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val isAllEnabled = viewModel.isAllDraftsEnabled(filteredKeys)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAllEnabled,
                    onCheckedChange = { viewModel.toggleAllDrafts(filteredKeys, it) }
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    
                    if (showSearch) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            placeholder = { Text(stringResource(R.string.search_restrictions)) },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            singleLine = true
                        )
                    }

                    filteredKeys.forEach { key ->
                        RestrictionItem(
                            label = labelMap[key]?.let { stringResource(it) } ?: key,
                            value = key,
                            checked = viewModel.draftRestrictions[key] == true,
                            isApplied = viewModel.appliedRestrictions[key] == true,
                            onCheckedChange = { viewModel.toggleDraft(key, it) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RestrictionItem(
    label: String,
    value: String,
    checked: Boolean,
    isApplied: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isApplied) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.active),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
