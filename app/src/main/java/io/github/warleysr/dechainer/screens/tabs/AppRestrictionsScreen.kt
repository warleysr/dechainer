package io.github.warleysr.dechainer.screens.tabs

import android.content.RestrictionEntry
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.BrowserRestrictionsManager
import io.github.warleysr.dechainer.data.PermanentRestrictionsManager
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.isLooserRestrictions
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

class ManagedRestrictionState(
    val entry: RestrictionEntry,
    currentRestrictions: Bundle
) {
    var isManaged by mutableStateOf(currentRestrictions.containsKey(entry.key))

    var booleanValue by mutableStateOf(
        if (currentRestrictions.containsKey(entry.key)) currentRestrictions.getBoolean(entry.key)
        else if (entry.type == RestrictionEntry.TYPE_BOOLEAN) true
        else entry.selectedState
    )

    var stringValue by mutableStateOf(
        if (currentRestrictions.containsKey(entry.key)) currentRestrictions.getString(entry.key).orEmpty()
        else entry.selectedString.orEmpty()
    )

    var intValue by mutableStateOf(
        if (currentRestrictions.containsKey(entry.key)) currentRestrictions.getInt(entry.key).toString()
        else if (entry.type == RestrictionEntry.TYPE_INTEGER) entry.intValue.toString()
        else ""
    )

    var multiSelectValues by mutableStateOf<Set<String>>(
        if (currentRestrictions.containsKey(entry.key)) {
            currentRestrictions.getStringArray(entry.key)?.toSet() ?: emptySet()
        } else {
            entry.allSelectedStrings?.toSet() ?: emptySet()
        }
    )

    val nestedBundleStates = mutableStateListOf<ManagedRestrictionState>()
    val bundleArrayList = mutableStateListOf<List<ManagedRestrictionState>>()

    init {
        if (entry.type == RestrictionEntry.TYPE_BUNDLE) {
            val childBundle = currentRestrictions.getBundle(entry.key) ?: Bundle()
            entry.restrictions?.forEach { sub ->
                nestedBundleStates.add(ManagedRestrictionState(sub, childBundle))
            }
        } else if (entry.type == RestrictionEntry.TYPE_BUNDLE_ARRAY) {
            val parcelableArray = currentRestrictions.getParcelableArray(entry.key)
            val subEntries = entry.restrictions ?: emptyArray()
            parcelableArray?.filterIsInstance<Bundle>()?.forEach { itemBundle ->
                val itemStates = subEntries.map { ManagedRestrictionState(it, itemBundle) }
                bundleArrayList.add(itemStates)
            }
        }
    }

    fun addBundleArrayItem() {
        val subEntries = entry.restrictions ?: emptyArray()
        val itemStates = subEntries.map { ManagedRestrictionState(it, Bundle()) }
        bundleArrayList.add(itemStates)
        isManaged = true
    }

    fun removeBundleArrayItem(index: Int) {
        if (index in bundleArrayList.indices) {
            bundleArrayList.removeAt(index)
        }
    }

    fun writeToBundle(bundle: Bundle) {
        if (!isManaged) return

        when (entry.type) {
            RestrictionEntry.TYPE_BOOLEAN -> {
                bundle.putBoolean(entry.key, booleanValue)
            }
            RestrictionEntry.TYPE_STRING, RestrictionEntry.TYPE_CHOICE -> {
                bundle.putString(entry.key, stringValue)
            }
            RestrictionEntry.TYPE_INTEGER -> {
                intValue.toIntOrNull()?.let {
                    bundle.putInt(entry.key, it)
                }
            }
            RestrictionEntry.TYPE_MULTI_SELECT -> {
                bundle.putStringArray(entry.key, multiSelectValues.toTypedArray())
            }
            RestrictionEntry.TYPE_BUNDLE -> {
                val subBundle = Bundle()
                nestedBundleStates.forEach { it.writeToBundle(subBundle) }
                bundle.putBundle(entry.key, subBundle)
            }
            RestrictionEntry.TYPE_BUNDLE_ARRAY -> {
                val array = bundleArrayList.map { states ->
                    val itemBundle = Bundle()
                    states.forEach { it.writeToBundle(itemBundle) }
                    itemBundle
                }.toTypedArray()
                bundle.putParcelableArray(entry.key, array)
            }
        }
    }
}

private enum class RestrictionFilter {
    ALL, ACTIVE, DEFAULT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRestrictionsScreen(
    app: AppItem,
    viewModel: DeviceOwnerViewModel = viewModel(),
    onBack: () -> Unit
) {
    val availableRestrictions = remember(app.packageName) {
        viewModel.getAvailableRestrictions(app.packageName)
            .filter { !it.key.equals("URLBlocklist", ignoreCase = true) }
    }
    val currentRestrictions = remember(app.packageName) {
        viewModel.getApplicationRestrictions(app.packageName)
    }

    val states = remember(availableRestrictions, currentRestrictions) {
        availableRestrictions.map { ManagedRestrictionState(it, currentRestrictions) }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(RestrictionFilter.ALL) }
    var showResetDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val permanentKey = remember(app.packageName) {
        if (BrowserRestrictionsManager(context).supportsRestrictions(app.packageName))
            PermanentRestrictionsManager.KEY_BROWSER_RESTRICTIONS
        else null
    }
    val recoveryGate = rememberRecoveryGate(permanentKey)

    val activeCount = states.count { it.isManaged }

    val filteredStates = remember(states, searchQuery, selectedFilter) {
        states.filter { state ->
            val matchesFilter = when (selectedFilter) {
                RestrictionFilter.ALL -> true
                RestrictionFilter.ACTIVE -> state.isManaged
                RestrictionFilter.DEFAULT -> !state.isManaged
            }
            if (!matchesFilter) return@filter false

            if (searchQuery.isBlank()) true
            else {
                val title = state.entry.title.orEmpty()
                val key = state.entry.key
                val desc = state.entry.description.orEmpty()
                title.contains(searchQuery, ignoreCase = true) ||
                        key.contains(searchQuery, ignoreCase = true) ||
                        desc.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (availableRestrictions.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // --- 1. Top Section (FIXED & COMPACT): App Header Card ---
        Spacer(Modifier.height(4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = app.icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = app.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "• ${activeCount}/${availableRestrictions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.clear_all_restrictions),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // --- 2. Controls Section (FIXED & COMPACT): Custom Surface Search & Filter Chips ---
        Spacer(Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_restrictions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == RestrictionFilter.ALL,
                onClick = { selectedFilter = RestrictionFilter.ALL },
                label = { Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp)
            )
            FilterChip(
                selected = selectedFilter == RestrictionFilter.ACTIVE,
                onClick = { selectedFilter = RestrictionFilter.ACTIVE },
                label = { Text("${stringResource(R.string.filter_active)} ($activeCount)", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp)
            )
            FilterChip(
                selected = selectedFilter == RestrictionFilter.DEFAULT,
                onClick = { selectedFilter = RestrictionFilter.DEFAULT },
                label = { Text("${stringResource(R.string.filter_default)} (${states.size - activeCount})", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(30.dp)
            )
        }
        Spacer(Modifier.height(6.dp))

        // --- 3. Scrollable Restriction List ---
        if (filteredStates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty()) {
                        stringResource(R.string.no_matching_restrictions, searchQuery)
                    } else stringResource(R.string.no_restrictions_available, app.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredStates, key = { it.entry.key }) { state ->
                    ManagedRestrictionCard(state = state)
                }
            }
        }

        // --- 4. Bottom Save Button (No separate background surface) ---
        Button(
            onClick = {
                val bundle = Bundle()
                if (currentRestrictions.containsKey("URLBlocklist")) {
                    val urls = currentRestrictions.getStringArray("URLBlocklist")
                    if (urls != null) bundle.putStringArray("URLBlocklist", urls)
                }
                states.filter { it.isManaged }.forEach { it.writeToBundle(bundle) }

                recoveryGate.change(isLooserRestrictions(currentRestrictions, bundle)) {
                    viewModel.setApplicationRestrictions(app.packageName, bundle, merge = false)
                    onBack()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.save_restrictions))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.clear_all_restrictions)) },
            text = { Text(stringResource(R.string.clear_all_restrictions_confirm, app.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        states.forEach { it.isManaged = false }
                    }
                ) {
                    Text(
                        stringResource(R.string.confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagedRestrictionCard(state: ManagedRestrictionState) {
    val entry = state.entry
    var expandedDesc by remember { mutableStateOf(false) }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (state.isManaged) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Title, Key & Manage Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title ?: entry.key,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = entry.key,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                }

                Spacer(Modifier.width(8.dp))

                Switch(
                    checked = state.isManaged,
                    onCheckedChange = { state.isManaged = it }
                )
            }

            // Description
            if (!entry.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                val isLongDesc = (entry.description?.length ?: 0) > 120
                Text(
                    text = if (isLongDesc && !expandedDesc) {
                        (entry.description?.take(120) ?: "") + "…"
                    } else entry.description.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLongDesc) {
                    Row(
                        modifier = Modifier
                            .clickable { expandedDesc = !expandedDesc }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (expandedDesc) stringResource(R.string.see_less)
                            else stringResource(R.string.see_more),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (expandedDesc) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // If managed, display specific value editor (without duplicate switches)
            if (state.isManaged) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(8.dp))

                when (entry.type) {
                    RestrictionEntry.TYPE_BOOLEAN -> {
                        // Segmented buttons for boolean value: NO DUPLICATE SWITCH!
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = state.booleanValue,
                                onClick = { state.booleanValue = true },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text(stringResource(R.string.status_enabled))
                            }
                            SegmentedButton(
                                selected = !state.booleanValue,
                                onClick = { state.booleanValue = false },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text(stringResource(R.string.status_disabled))
                            }
                        }
                    }
                    RestrictionEntry.TYPE_CHOICE -> {
                        ChoiceSelectorView(
                            entry = entry,
                            selectedValue = state.stringValue,
                            onSelected = { state.stringValue = it }
                        )
                    }
                    RestrictionEntry.TYPE_STRING -> {
                        OutlinedTextField(
                            value = state.stringValue,
                            onValueChange = { state.stringValue = it },
                            label = { Text(entry.title ?: entry.key) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    RestrictionEntry.TYPE_INTEGER -> {
                        OutlinedTextField(
                            value = state.intValue,
                            onValueChange = { state.intValue = it.filter { ch -> ch.isDigit() } },
                            label = { Text(entry.title ?: entry.key) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                    RestrictionEntry.TYPE_MULTI_SELECT -> {
                        MultiSelectSelectorView(
                            entry = entry,
                            selectedValues = state.multiSelectValues,
                            onSelectionChanged = { state.multiSelectValues = it }
                        )
                    }
                    RestrictionEntry.TYPE_BUNDLE -> {
                        BundleEditorView(state = state)
                    }
                    RestrictionEntry.TYPE_BUNDLE_ARRAY -> {
                        BundleArrayEditorView(state = state)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceSelectorView(
    entry: RestrictionEntry,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    val values = remember(entry) { entry.choiceValues ?: emptyArray() }
    val entries = remember(entry) { entry.choiceEntries ?: entry.choiceValues ?: emptyArray() }

    Column(modifier = Modifier.fillMaxWidth()) {
        values.indices.forEach { i ->
            val valStr = values[i]
            val labelStr = entries.getOrNull(i) ?: valStr
            val isSelected = selectedValue == valStr

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(valStr) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onSelected(valStr) }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = labelStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (labelStr != valStr) {
                        Text(
                            text = valStr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiSelectSelectorView(
    entry: RestrictionEntry,
    selectedValues: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit
) {
    val values = remember(entry) { entry.choiceValues ?: emptyArray() }
    val entries = remember(entry) { entry.choiceEntries ?: entry.choiceValues ?: emptyArray() }

    Column(modifier = Modifier.fillMaxWidth()) {
        values.indices.forEach { i ->
            val valStr = values[i]
            val labelStr = entries.getOrNull(i) ?: valStr
            val checked = valStr in selectedValues

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectionChanged(
                            if (checked) selectedValues - valStr else selectedValues + valStr
                        )
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { isChecked ->
                        onSelectionChanged(
                            if (isChecked) selectedValues + valStr else selectedValues - valStr
                        )
                    }
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = labelStr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal
                    )
                    if (labelStr != valStr) {
                        Text(
                            text = valStr,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleEditorView(state: ManagedRestrictionState) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            state.nestedBundleStates.forEach { subState ->
                ManagedRestrictionCard(state = subState)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BundleArrayEditorView(state: ManagedRestrictionState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        state.bundleArrayList.forEachIndexed { index, itemStates ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.item_number, index + 1),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { state.removeBundleArrayItem(index) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.remove_item),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    itemStates.forEach { subState ->
                        ManagedRestrictionCard(state = subState)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = { state.addBundleArrayItem() },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.add_item))
        }
    }
}
