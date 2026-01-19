package io.github.warleysr.dechainer.screens.tabs

import android.os.UserManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import io.github.warleysr.dechainer.viewmodels.RestrictionsViewModel

@Composable
fun RestrictionsTab(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel(),
    restrictionsViewModel: RestrictionsViewModel = viewModel()
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (!deviceOwnerViewModel.isDeviceOwner()) {
        NoDeviceOwnerPrivileges(deviceOwnerViewModel)
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
                    labelMap = getLabelMap(),
                    defaultExpanded = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                RestrictionAccordion(
                    title = stringResource(R.string.other_restrictions),
                    keys = restrictionsViewModel.otherKeys,
                    viewModel = restrictionsViewModel,
                    labelMap = getLabelMap(),
                    defaultExpanded = false
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.apply_restrictions))
            }
        }
    }

    if (showConfirmDialog) {
        val storedHash = SecurityManager.getRecoveryHash(context)
        if (storedHash == null) {
            restrictionsViewModel.applyChanges()
            showConfirmDialog = false
        } else {
            RecoveryConfirmDialog(
                onConfirm = { code ->
                    if (SecurityManager.validatePassphrase(code, storedHash)) {
                        restrictionsViewModel.applyChanges()
                        showConfirmDialog = false
                        true
                    } else {
                        false
                    }
                },
                onDismiss = { showConfirmDialog = false }
            )
        }
    }
}


@Composable
private fun RestrictionAccordion(
    title: String,
    keys: List<String>,
    viewModel: RestrictionsViewModel,
    labelMap: Map<String, Int>,
    defaultExpanded: Boolean
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val isAllEnabled = viewModel.isAllDraftsEnabled(keys)

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
                    onCheckedChange = { viewModel.toggleAllDrafts(keys, it) }
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
                    
                    keys.forEach { key ->
                        RestrictionItem(
                            label = if (labelMap.containsKey(key)) stringResource(labelMap[key]!!) else key,
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

private fun getLabelMap(): Map<String, Int> {
    return mapOf(
        UserManager.DISALLOW_CONFIG_VPN to R.string.block_vpn,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS to R.string.block_private_dns,
        UserManager.DISALLOW_FACTORY_RESET to R.string.block_factory_reset,
        UserManager.DISALLOW_SAFE_BOOT to R.string.disallow_safe_boot,
        UserManager.DISALLOW_USB_FILE_TRANSFER to R.string.disallow_usb_file_transfer,
        UserManager.DISALLOW_DEBUGGING_FEATURES to R.string.disallow_debugging_features,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES to R.string.disallow_install_unknown_sources,
        UserManager.DISALLOW_MODIFY_ACCOUNTS to R.string.disallow_modify_accounts,
        UserManager.DISALLOW_ADD_USER to R.string.disallow_add_user
    )
}
