package io.github.warleysr.dechainer.screens.tabs

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.core.content.edit
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.ColorFilterSettings
import io.github.warleysr.dechainer.data.DeviceAdmin
import io.github.warleysr.dechainer.data.PermanentRestrictionsManager
import io.github.warleysr.dechainer.data.SystemRestrictions
import io.github.warleysr.dechainer.data.VisualBlockingSettings
import io.github.warleysr.dechainer.screens.common.PermanentBadge
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import kotlin.time.Duration.Companion.milliseconds

private data class PendingPermanentAdd(
    val key: String,
    val label: String,
    val isDpc: Boolean,
    val dpcKey: String? = null
)

@Composable
fun PermanentRestrictionsScreen(
    deviceOwnerViewModel: DeviceOwnerViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val recoveryGate = rememberRecoveryGate()

    val isDeviceOwner = remember { deviceOwnerViewModel.isDeviceOwner() }
    var permanentKeys by remember { mutableStateOf(PermanentRestrictionsManager.getPermanentKeys(context)) }
    var editRemaining by remember { mutableLongStateOf(PermanentRestrictionsManager.getEditRemainingTime(context)) }

    var showStartEditDialog by remember { mutableStateOf(false) }
    var showCancelEditDialog by remember { mutableStateOf(false) }
    var pendingAdd by remember { mutableStateOf<PendingPermanentAdd?>(null) }
    var confirmAddChecked by remember { mutableStateOf(false) }

    var dpcExpanded by remember { mutableStateOf(true) }
    var otherDpcExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val editUnlocked = editRemaining == 0L
    val blockedMsg = stringResource(R.string.permanent_blocked_snackbar)

    LaunchedEffect(Unit) {
        while (true) {
            editRemaining = PermanentRestrictionsManager.getEditRemainingTime(context)
            delay(60000.milliseconds)
        }
    }

    val dpcLabelMap = remember {
        SystemRestrictions.allKeys.associateWith { key ->
            SystemRestrictions.resolveStringRes(context, key)
        }
    }

    val filteredOtherKeys = remember(searchQuery, dpcLabelMap) {
        val keys = SystemRestrictions.otherKeys
        if (searchQuery.isEmpty()) keys
        else {
            val queryNorm = SystemRestrictions.normalizeForSearch(searchQuery)
            keys.filter { key ->
                val label = dpcLabelMap[key]?.let { context.getString(it) } ?: key
                SystemRestrictions.normalizeForSearch(label).contains(queryNorm) ||
                    SystemRestrictions.normalizeForSearch(key).contains(queryNorm)
            }
        }
    }

    fun requestToggle(key: String, label: String, currentlyPermanent: Boolean, isDpc: Boolean, dpcKey: String? = null) {
        if (currentlyPermanent) {
            if (!editUnlocked) {
                scope.launch { snackbarHostState.showSnackbar(blockedMsg) }
                return
            }
            PermanentRestrictionsManager.setPermanent(context, key, false)
            permanentKeys = PermanentRestrictionsManager.getPermanentKeys(context)
        } else {
            recoveryGate.run {
                confirmAddChecked = false
                pendingAdd = PendingPermanentAdd(key = key, label = label, isDpc = isDpc, dpcKey = dpcKey)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                EditModeCard(
                    editRemaining = editRemaining,
                    onStartEdit = {
                        recoveryGate.run { showStartEditDialog = true }
                    },
                    onCancelTimer = { showCancelEditDialog = true },
                    onLockAgain = {
                        PermanentRestrictionsManager.cancelOrLockEdit(context)
                        editRemaining = -1L
                    }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.permanent_section_app_features),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            items(PermanentRestrictionsManager.appFeatures, key = { it.key }) { feature ->
                val isPermanent = feature.key in permanentKeys
                val title = stringResource(feature.titleRes)
                val enabled = (!feature.requiresDeviceOwner || isDeviceOwner) && (!isPermanent || editUnlocked)

                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, modifier = Modifier.weight(1f, fill = false))
                            if (isPermanent) {
                                Spacer(modifier = Modifier.width(8.dp))
                                PermanentBadge()
                            }
                        }
                    },
                    supportingContent = {
                        if (feature.requiresDeviceOwner && !isDeviceOwner) {
                            Text(
                                stringResource(R.string.permanent_requires_device_owner),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(stringResource(feature.descRes))
                        }
                    },
                    trailingContent = {
                        Checkbox(
                            checked = isPermanent,
                            enabled = !feature.requiresDeviceOwner || isDeviceOwner,
                            onCheckedChange = {
                                if (!feature.requiresDeviceOwner || isDeviceOwner) {
                                    requestToggle(feature.key, title, isPermanent, isDpc = false)
                                }
                            }
                        )
                    },
                    modifier = Modifier.clickable(enabled = !feature.requiresDeviceOwner || isDeviceOwner) {
                        requestToggle(feature.key, title, isPermanent, isDpc = false)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.permanent_section_dpc_restrictions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                if (!isDeviceOwner) {
                    Text(
                        text = stringResource(R.string.permanent_requires_device_owner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            if (isDeviceOwner) {
                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { dpcExpanded = !dpcExpanded }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.recommended_configs),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (dpcExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                            AnimatedVisibility(visible = dpcExpanded) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                                    SystemRestrictions.recommendedKeys.forEach { key ->
                                        val label = dpcLabelMap[key]?.let { stringResource(it) } ?: key
                                        val permKey = PermanentRestrictionsManager.dpcKey(key)
                                        val isPermanent = permKey in permanentKeys
                                        DpcPermanentRow(
                                            label = label,
                                            restrictionKey = key,
                                            isPermanent = isPermanent,
                                            onClick = {
                                                requestToggle(permKey, label, isPermanent, isDpc = true, dpcKey = key)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { otherDpcExpanded = !otherDpcExpanded }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.other_restrictions),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (otherDpcExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }
                            AnimatedVisibility(visible = otherDpcExpanded) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
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
                                    filteredOtherKeys.forEach { key ->
                                        val label = dpcLabelMap[key]?.let { stringResource(it) } ?: key
                                        val permKey = PermanentRestrictionsManager.dpcKey(key)
                                        val isPermanent = permKey in permanentKeys
                                        DpcPermanentRow(
                                            label = label,
                                            restrictionKey = key,
                                            isPermanent = isPermanent,
                                            onClick = {
                                                requestToggle(permKey, label, isPermanent, isDpc = true, dpcKey = key)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showStartEditDialog) {
        AlertDialog(
            onDismissRequest = { showStartEditDialog = false },
            title = { Text(stringResource(R.string.permanent_start_edit_dialog_title)) },
            text = { Text(stringResource(R.string.permanent_start_edit_dialog_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        PermanentRestrictionsManager.startEditTimer(context)
                        editRemaining = PermanentRestrictionsManager.getEditRemainingTime(context)
                        showStartEditDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartEditDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCancelEditDialog) {
        AlertDialog(
            onDismissRequest = { showCancelEditDialog = false },
            title = { Text(stringResource(R.string.permanent_cancel_edit)) },
            text = { Text(stringResource(R.string.permanent_cancel_edit_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        PermanentRestrictionsManager.cancelOrLockEdit(context)
                        editRemaining = -1L
                        showCancelEditDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelEditDialog = false }) {
                    Text(stringResource(R.string.close))
                }
            }
        )
    }

    pendingAdd?.let { target ->
        AlertDialog(
            onDismissRequest = {
                pendingAdd = null
                confirmAddChecked = false
            },
            icon = {
                Icon(
                    Icons.Outlined.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.permanent_add_warning_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.permanent_add_warning_text, target.label))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmAddChecked = !confirmAddChecked }
                            .padding(top = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = confirmAddChecked,
                            onCheckedChange = { confirmAddChecked = it }
                        )
                        Text(
                            text = stringResource(R.string.permanent_add_confirm_checkbox),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = confirmAddChecked,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        if (target.isDpc && target.dpcKey != null) {
                            DeviceAdmin.policyManager.addUserRestriction(DeviceAdmin.component, target.dpcKey)
                        } else {
                            activateFeatureIfDisabled(context, target.key, deviceOwnerViewModel)
                        }
                        PermanentRestrictionsManager.setPermanent(context, target.key, true)
                        permanentKeys = PermanentRestrictionsManager.getPermanentKeys(context)
                        pendingAdd = null
                        confirmAddChecked = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingAdd = null
                        confirmAddChecked = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@Composable
private fun EditModeCard(
    editRemaining: Long,
    onStartEdit: () -> Unit,
    onCancelTimer: () -> Unit,
    onLockAgain: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (editRemaining == 0L)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        editRemaining == 0L -> Icons.Outlined.LockOpen
                        editRemaining > 0L -> Icons.Outlined.LockClock
                        else -> Icons.Outlined.Lock
                    },
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    when {
                        editRemaining == 0L -> {
                            Text(
                                text = stringResource(R.string.permanent_edit_unlocked),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.permanent_edit_unlocked_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        editRemaining > 0L -> {
                            val hours = (editRemaining / (1000 * 60 * 60)).toInt()
                            val minutes = ((editRemaining / (1000 * 60)) % 60).toInt()
                            val timeStr = if (hours > 0)
                                stringResource(R.string.time_hours_minutes, hours, minutes)
                            else
                                stringResource(R.string.time_minutes, minutes)
                            Text(
                                text = stringResource(R.string.permanent_edit_waiting, timeStr),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.permanent_start_edit_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.permanent_start_edit),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.permanent_start_edit_desc),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                editRemaining == 0L -> {
                    Button(
                        onClick = onLockAgain,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.permanent_lock_again))
                    }
                }
                editRemaining > 0L -> {
                    OutlinedButton(
                        onClick = onCancelTimer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.permanent_cancel_edit))
                    }
                }
                else -> {
                    Button(
                        onClick = onStartEdit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.permanent_start_edit))
                    }
                }
            }
        }
    }
}

@Composable
private fun DpcPermanentRow(
    label: String,
    restrictionKey: String,
    isPermanent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isPermanent,
            onCheckedChange = { onClick() }
        )
        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = restrictionKey,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isPermanent) {
            PermanentBadge(modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun activateFeatureIfDisabled(
    context: Context,
    key: String,
    deviceOwnerViewModel: DeviceOwnerViewModel
) {
    when (key) {
        PermanentRestrictionsManager.KEY_ACTIVITY_BLOCKER -> {
            if (!DechainerAccessibilityService.isRunning && Shizuku.pingBinder()) {
                deviceOwnerViewModel.changeAccessibilityPermission(true)
            }
        }
        PermanentRestrictionsManager.KEY_VISUAL_BLOCKING -> {
            val prefs = context.getSharedPreferences(VisualBlockingSettings.PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(VisualBlockingSettings.KEY_ENABLED, false)) {
                prefs.edit { putBoolean(VisualBlockingSettings.KEY_ENABLED, true) }
            }
        }
        PermanentRestrictionsManager.KEY_BLOCK_TORRENTS -> {
            if (!SecurityManager.isBlockTorrentsEnabled(context)) {
                SecurityManager.setBlockTorrentsEnabled(context, true)
            }
        }
        PermanentRestrictionsManager.KEY_COLOR_FILTER_SETTINGS -> {
            val prefs = context.getSharedPreferences(ColorFilterSettings.PREFS_NAME, Context.MODE_PRIVATE)
            if (!ColorFilterSettings.isEnabled(prefs)) {
                prefs.edit { putBoolean(ColorFilterSettings.KEY_ENABLED, true) }
            }
        }
    }
}
