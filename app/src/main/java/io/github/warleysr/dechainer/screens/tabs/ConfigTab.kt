package io.github.warleysr.dechainer.screens.tabs

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Web
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.common.RecoveryConfirmDialog
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import androidx.core.content.edit
import rikka.shizuku.Shizuku

@Composable
fun ConfigTab(viewModel: DeviceOwnerViewModel = viewModel()) {
    var showDnsDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    val context = LocalContext.current
    val blockerPrefs = remember { context.getSharedPreferences("activity_blocker_prefs", Context.MODE_PRIVATE) }
    val switchInitialState = if (Shizuku.pingBinder())
        viewModel.isAccessibilityGranted()
    else
        blockerPrefs.getBoolean("switch_blocker", false)

    var blockSpecificActivity by remember { mutableStateOf(switchInitialState) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.device_owner_state)) },
                supportingContent = { Text(stringResource(R.string.device_owner_description)) },
                leadingContent = { Icon(Icons.Outlined.Adb, "") },
                trailingContent = {
                    val owner = viewModel.isDeviceOwner()
                    val badgeColor = if (owner) Color(26, 163, 63) else Color.Red
                    val badgeText = if (owner) stringResource(R.string.granted) else stringResource(R.string.not_granted)
                    Badge(containerColor = badgeColor, contentColor = Color.White) {
                        Text(badgeText, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                modifier = Modifier.clickable(onClick = { viewModel.navigateTo("setup_device_owner") })
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dns_settings)) },
                supportingContent = { 
                    Text( stringResource(R.string.dns_description))
                },
                leadingContent = { Icon(Icons.Outlined.Dns, "") },
                modifier = Modifier.clickable { showDnsDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.browser_restrictions)) },
                supportingContent = { Text(stringResource(R.string.browser_restrictions_desc)) },
                leadingContent = { Icon(Icons.Outlined.Web, "") },
                modifier = Modifier.clickable { viewModel.navigateTo("browser_restrictions") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.activity_blocker)) },
                supportingContent = { Text(stringResource(R.string.activity_blocker_description)) },
                leadingContent = { Icon(Icons.Outlined.Accessibility, "") },
                trailingContent = {
                    Switch(blockSpecificActivity, onCheckedChange = { checked ->
                        val action = {
                            blockSpecificActivity = checked
                            blockerPrefs.edit { putBoolean("switch_blocker", checked) }
                            viewModel.changeAccessibilityPermission(checked)
                        }
                        pendingAction = action
                    })
                },
                modifier = Modifier.clickable { viewModel.navigateTo("activity_blocker") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.vpn_settings)) },
                supportingContent = { Text(stringResource(R.string.vpn_description)) },
                leadingContent = { Icon(Icons.Outlined.VpnKey, "") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.language_settings)) },
                supportingContent = { Text(stringResource(R.string.language_description)) },
                leadingContent = { Icon(Icons.Outlined.Language, "") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    if (showDnsDialog) {
        val currentDns = context.getSharedPreferences("dns_prefs", Context.MODE_PRIVATE)
            .getString("private_dns_host", null)
        DnsSelectionDialog(
            currentDns = currentDns,
            onDismiss = { showDnsDialog = false },
            onApply = { host ->
                val action = { applyDns(context, viewModel, host) }
                pendingAction = action
                showDnsDialog = false
            }
        )
    }

    if (pendingAction != null) {
        val storedHash = SecurityManager.getRecoveryHash(context)
        if (storedHash == null || SecurityManager.isSessionActive()) {
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

private fun applyDns(context: Context, viewModel: DeviceOwnerViewModel, host: String?) {
    viewModel.setPrivateDNS(host)
    val prefs = context.getSharedPreferences("dns_prefs", Context.MODE_PRIVATE)
    if (host == null) {
        prefs.edit { remove("private_dns_host") }
    } else {
        prefs.edit { putString("private_dns_host", host) }
    }
}

@Composable
fun DnsSelectionDialog(
    currentDns: String?,
    onDismiss: () -> Unit,
    onApply: (String?) -> Unit
) {
    val options = listOf(
        "Cloudflare" to "family.cloudflare-dns.com",
        "AdGuard DNS" to "family.adguard-dns.com",
        "CleanBrowsing" to "adult-filter-dns.cleanbrowsing.org"
    )
    
    var selectedOption by remember { 
        mutableStateOf(
            when {
                currentDns == null -> "none"
                options.any { it.second == currentDns } -> currentDns
                else -> "custom"
            }
        )
    }
    var customHost by remember { mutableStateOf(if (selectedOption == "custom") currentDns ?: "" else "") }

    val isCustomValid = remember(customHost) {
        customHost.matches(Regex("^([a-z0-9]+(-[a-z0-9]+)*\\.)+[a-z]{2,}\$"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns_settings)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = "none" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedOption == "none", onClick = { selectedOption = "none" })
                    Text(stringResource(R.string.none), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }

                options.forEach { (name, host) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = host }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == host, onClick = { selectedOption = host })
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(name, fontWeight = FontWeight.Bold)
                            Text(host, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = "custom" }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedOption == "custom", onClick = { selectedOption = "custom" })
                    Text(stringResource(R.string.custom), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                if (selectedOption == "custom") {
                    OutlinedTextField(
                        value = customHost,
                        onValueChange = { customHost = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        placeholder = { Text(stringResource(R.string.dns_host_hint)) },
                        singleLine = true,
                        isError = !isCustomValid && customHost.isNotEmpty(),
                        supportingText = {
                            if (!isCustomValid && customHost.isNotEmpty()) {
                                Text(stringResource(R.string.invalid_dns_host))
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            val canApply = selectedOption != "custom" || isCustomValid
            TextButton(
                enabled = canApply,
                onClick = { 
                    val finalHost = when(selectedOption) {
                        "none" -> null
                        "custom" -> customHost
                        else -> selectedOption
                    }
                    onApply(finalHost) 
                }
            ) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
