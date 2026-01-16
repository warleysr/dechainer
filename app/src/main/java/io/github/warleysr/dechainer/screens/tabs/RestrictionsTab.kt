package io.github.warleysr.dechainer.screens.tabs

import android.os.UserManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.NoDeviceOwnerPrivileges
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import io.github.warleysr.dechainer.viewmodels.RestrictionsViewModel

@Composable
fun RestrictionsTab(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel(),
    restrictionsViewModel: RestrictionsViewModel = viewModel()
) {
    if (!deviceOwnerViewModel.isDeviceOwner()) {
        NoDeviceOwnerPrivileges(deviceOwnerViewModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            RecommendedRestrictionsAccordion(restrictionsViewModel)
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = { restrictionsViewModel.applyChanges() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply_restrictions))
            }
        }
    }
}

@Composable
fun RecommendedRestrictionsAccordion(viewModel: RestrictionsViewModel) {
    var expanded by remember { mutableStateOf(true) }
    val isAllEnabled = viewModel.isAllDraftsEnabled()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isAllEnabled,
                    onCheckedChange = { viewModel.toggleAllDrafts(it) }
                )
                Text(
                    text = stringResource(R.string.recommended_configs),
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

            // Expandable Content
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    
                    val items = listOf(
                        stringResource(R.string.block_vpn) to UserManager.DISALLOW_CONFIG_VPN,
                        stringResource(R.string.block_private_dns) to UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
                        stringResource(R.string.block_factory_reset) to UserManager.DISALLOW_FACTORY_RESET
                    )
                    
                    items.forEach { (label, key) ->
                        RestrictionItem(
                            label = label,
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
        Text(
            text = label,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
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
