package io.github.warleysr.dechainer.screens.tabs

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.NoAdultContent
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
import io.github.warleysr.dechainer.utils.LocaleUtils
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

@Composable
fun ConfigTab(viewModel: DeviceOwnerViewModel = viewModel()) {
    var showDnsDialog by remember { mutableStateOf(false) }
    var dnsErrorRes by remember { mutableStateOf<Int?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showKeyboardDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val blockerPrefs = remember { context.getSharedPreferences("activity_blocker_prefs", Context.MODE_PRIVATE) }
    val switchInitialState = if (Shizuku.pingBinder())
        viewModel.isAccessibilityGranted()
    else
        blockerPrefs.getBoolean("switch_blocker", false)

    val securityPrefs = remember { context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE) }
    var shuffleKeyboard by remember { mutableStateOf(securityPrefs.getBoolean("shuffle_keyboard", false)) }

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
                modifier = Modifier.clickable { 
                    dnsErrorRes = null
                    showDnsDialog = true 
                }
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
                headlineContent = { Text(stringResource(R.string.blocked_words_feat)) },
                supportingContent = { Text(stringResource(R.string.blocked_words_feat_description)) },
                leadingContent = { Icon(Icons.Outlined.NoAdultContent, "") },
                modifier = Modifier.clickable { viewModel.navigateTo("blocked_words") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.language_settings)) },
                supportingContent = { Text(stringResource(R.string.language_description)) },
                leadingContent = { Icon(Icons.Outlined.Language, "") },
                modifier = Modifier.clickable { showLanguageDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            val keyboardLabel = if (shuffleKeyboard)
                stringResource(R.string.keyboard_shuffle)
            else
                stringResource(R.string.keyboard_normal)
            ListItem(
                headlineContent = { Text(stringResource(R.string.keyboard_type)) },
                supportingContent = { Text(keyboardLabel) },
                leadingContent = { Icon(Icons.Outlined.Keyboard, "") },
                modifier = Modifier.clickable { showKeyboardDialog = true }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    if (showDnsDialog) {
        val currentDns = viewModel.getPrivateDNS()
        DnsSelectionDialog(
            currentDns = currentDns,
            errorRes = dnsErrorRes,
            onDismiss = { showDnsDialog = false },
            onApply = { host ->
                val action = {
                    dnsErrorRes = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            viewModel.setPrivateDNS(host)
                        }
                        if (result == DevicePolicyManager.PRIVATE_DNS_SET_NO_ERROR) {
                            showDnsDialog = false
                        } else {
                            dnsErrorRes = when (result) {
                                DevicePolicyManager.PRIVATE_DNS_SET_ERROR_HOST_NOT_SERVING -> R.string.dns_error_not_serving
                                else -> R.string.dns_error_failure
                            }
                        }
                    }
                    Unit
                }
                pendingAction = action
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            onDismiss = { showLanguageDialog = false },
            onApply = { languageCode ->
                LocaleUtils.setLocale(context, languageCode)
                showLanguageDialog = false
            }
        )
    }

    if (showKeyboardDialog) {
        KeyboardTypeDialog(
            currentShuffle = shuffleKeyboard,
            onDismiss = { showKeyboardDialog = false },
            onApply = { isShuffle ->
                val action = {
                    shuffleKeyboard = isShuffle
                    securityPrefs.edit { putBoolean("shuffle_keyboard", isShuffle) }
                    showKeyboardDialog = false
                }
                pendingAction = action
            }
        )
    }

    if (pendingAction != null) {
        val storedCode = SecurityManager.getRecoveryCode(context)
        if (storedCode == null || SecurityManager.isSessionActive()) {
            pendingAction?.invoke()
            pendingAction = null
        } else {
            RecoveryConfirmDialog(
                onConfirm = { code ->
                    if (SecurityManager.validateRecoveryCode(code, storedCode)) {
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
fun DnsSelectionDialog(
    currentDns: String?,
    errorRes: Int? = null,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val options = listOf(
        "Cloudflare" to "family.cloudflare-dns.com",
        "AdGuard DNS" to "family.adguard-dns.com",
        "CleanBrowsing" to "adult-filter-dns.cleanbrowsing.org"
    )
    
    var selectedOption by remember { 
        mutableStateOf(
            when {
                options.any { it.second == currentDns } -> currentDns
                currentDns != null -> "custom"
                else -> null
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
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
                if (errorRes != null) {
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            val canApply = selectedOption != null && (selectedOption != "custom" || isCustomValid)
            TextButton(
                enabled = canApply,
                onClick = { 
                    val finalHost = when(selectedOption) {
                        "custom" -> customHost
                        else -> selectedOption
                    }
                    onApply(finalHost!!)
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

@Composable
fun LanguageSelectionDialog(
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val context = LocalContext.current
    val currentLocale = LocaleUtils.getLocale(context)
    val options = listOf(
        "en" to "English",
        "pt" to "Português"
    )
    
    var selectedOption by remember { 
        mutableStateOf(if (currentLocale.startsWith("pt")) "pt" else "en")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_settings)) },
        text = {
            Column {
                options.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = code }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedOption == code, onClick = { selectedOption = code })
                        Text(name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(selectedOption) }
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

@Composable
fun KeyboardTypeDialog(
    currentShuffle: Boolean,
    onDismiss: () -> Unit,
    onApply: (Boolean) -> Unit
) {
    var selectedShuffle by remember { mutableStateOf(currentShuffle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keyboard_type)) },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedShuffle = false }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = !selectedShuffle, onClick = { selectedShuffle = false })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.keyboard_normal), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.keyboard_normal_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedShuffle = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedShuffle, onClick = { selectedShuffle = true })
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.keyboard_shuffle), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.keyboard_shuffle_desc),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(selectedShuffle) }) {
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

