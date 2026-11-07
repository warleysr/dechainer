package io.github.warleysr.dechainer.screens.tabs

import android.content.RestrictionEntry
import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

class RestrictionEditorState(
    val entry: RestrictionEntry,
    currentRestrictions: Bundle
) {
    var booleanValue by mutableStateOf(
        if (currentRestrictions.containsKey(entry.key)) currentRestrictions.getBoolean(entry.key)
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

    val nestedBundleStates = mutableStateListOf<RestrictionEditorState>()
    val bundleArrayList = mutableStateListOf<List<RestrictionEditorState>>()

    init {
        if (entry.type == RestrictionEntry.TYPE_BUNDLE) {
            val childBundle = currentRestrictions.getBundle(entry.key) ?: Bundle()
            entry.restrictions?.forEach { sub ->
                nestedBundleStates.add(RestrictionEditorState(sub, childBundle))
            }
        } else if (entry.type == RestrictionEntry.TYPE_BUNDLE_ARRAY) {
            val parcelableArray = currentRestrictions.getParcelableArray(entry.key)
            val subEntries = entry.restrictions ?: emptyArray()
            parcelableArray?.filterIsInstance<Bundle>()?.forEach { itemBundle ->
                val itemStates = subEntries.map { RestrictionEditorState(it, itemBundle) }
                bundleArrayList.add(itemStates)
            }
        }
    }

    fun addBundleArrayItem() {
        val subEntries = entry.restrictions ?: emptyArray()
        val itemStates = subEntries.map { RestrictionEditorState(it, Bundle()) }
        bundleArrayList.add(itemStates)
    }

    fun removeBundleArrayItem(index: Int) {
        if (index in bundleArrayList.indices) {
            bundleArrayList.removeAt(index)
        }
    }

    fun writeToBundle(bundle: Bundle) {
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

@Composable
fun AppRestrictionsDialog(
    app: AppItem,
    viewModel: DeviceOwnerViewModel,
    onDismiss: () -> Unit,
    onSave: (Bundle) -> Unit
) {
    val availableRestrictions = remember {
        viewModel.getAvailableRestrictions(app.packageName)
            .filter { !it.key.equals("URLBlocklist", ignoreCase = true) }
    }
    val currentRestrictions = remember { viewModel.getApplicationRestrictions(app.packageName) }
    
    var searchQuery by remember { mutableStateOf("") }
    
    val states = remember(availableRestrictions, currentRestrictions) {
        availableRestrictions.map { RestrictionEditorState(it, currentRestrictions) }
    }

    val filteredStates = remember(states, searchQuery) {
        if (searchQuery.isEmpty()) {
            states
        } else {
            states.filter { state ->
                val title = state.entry.title.orEmpty()
                val key = state.entry.key
                val desc = state.entry.description.orEmpty()
                title.contains(searchQuery, ignoreCase = true) ||
                        key.contains(searchQuery, ignoreCase = true) ||
                        desc.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_restrictions, app.name)) },
        text = {
            if (availableRestrictions.isEmpty()) {
                Text(stringResource(R.string.no_restrictions_available, app.name))
            } else {
                Column {
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(filteredStates, key = { it.entry.key }) { state ->
                            RestrictionEntryRow(state = state)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val bundle = Bundle()
                if (currentRestrictions.containsKey("URLBlocklist")) {
                    val urls = currentRestrictions.getStringArray("URLBlocklist")
                    if (urls != null) bundle.putStringArray("URLBlocklist", urls)
                }
                states.forEach { it.writeToBundle(bundle) }
                onSave(bundle)
            }) {
                Text(stringResource(R.string.confirm))
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
fun RestrictionEntryRow(state: RestrictionEditorState) {
    val entry = state.entry
    var expandedDesc by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Header with title and key
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { expandedDesc = !expandedDesc }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.title ?: entry.key,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.description != null) {
                        Icon(
                            imageVector = if (expandedDesc) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).padding(start = 4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = entry.key,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }

            // Boolean toggle if TYPE_BOOLEAN
            if (entry.type == RestrictionEntry.TYPE_BOOLEAN) {
                Checkbox(
                    checked = state.booleanValue,
                    onCheckedChange = { state.booleanValue = it }
                )
            }
        }

        if (expandedDesc && entry.description != null) {
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
        }

        // Specific editor for each type
        when (entry.type) {
            RestrictionEntry.TYPE_STRING -> {
                OutlinedTextField(
                    value = state.stringValue,
                    onValueChange = { state.stringValue = it },
                    label = { Text(entry.title ?: entry.key) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            RestrictionEntry.TYPE_INTEGER -> {
                OutlinedTextField(
                    value = state.intValue,
                    onValueChange = { state.intValue = it.filter { ch -> ch.isDigit() } },
                    label = { Text(entry.title ?: entry.key) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            RestrictionEntry.TYPE_CHOICE -> {
                ChoiceSelector(
                    entry = entry,
                    selectedValue = state.stringValue,
                    onSelected = { state.stringValue = it }
                )
            }
            RestrictionEntry.TYPE_MULTI_SELECT -> {
                MultiSelectSelector(
                    entry = entry,
                    selectedValues = state.multiSelectValues,
                    onSelectionChanged = { state.multiSelectValues = it }
                )
            }
            RestrictionEntry.TYPE_BUNDLE -> {
                BundleEditor(state = state)
            }
            RestrictionEntry.TYPE_BUNDLE_ARRAY -> {
                BundleArrayEditor(state = state)
            }
        }
    }
}

@Composable
private fun ChoiceSelector(
    entry: RestrictionEntry,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val values = remember(entry) { entry.choiceValues ?: emptyArray() }
    val entries = remember(entry) { entry.choiceEntries ?: entry.choiceValues ?: emptyArray() }

    val currentDisplay = remember(selectedValue, values, entries) {
        val idx = values.indexOf(selectedValue)
        if (idx >= 0 && idx < entries.size) entries[idx] else selectedValue
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (currentDisplay.isNotEmpty()) currentDisplay else stringResource(R.string.select_option),
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentDisplay.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(entry.title ?: entry.key) },
            text = {
                LazyColumn {
                    items(values.indices.toList()) { i ->
                        val valStr = values[i]
                        val labelStr = entries.getOrNull(i) ?: valStr
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(valStr)
                                    showDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedValue == valStr,
                                onClick = {
                                    onSelected(valStr)
                                    showDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(labelStr, style = MaterialTheme.typography.bodyMedium)
                                if (labelStr != valStr) {
                                    Text(
                                        valStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun MultiSelectSelector(
    entry: RestrictionEntry,
    selectedValues: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val values = remember(entry) { entry.choiceValues ?: emptyArray() }
    val entries = remember(entry) { entry.choiceEntries ?: entry.choiceValues ?: emptyArray() }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.selected_count, selectedValues.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 8.dp)) {
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
                            .padding(vertical = 4.dp),
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
                            Text(labelStr, style = MaterialTheme.typography.bodySmall)
                            if (labelStr != valStr) {
                                Text(
                                    valStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleEditor(state: RestrictionEditorState) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sub_fields_count, state.nestedBundleStates.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    state.nestedBundleStates.forEach { subState ->
                        RestrictionEntryRow(state = subState)
                    }
                }
            }
        }
    }
}

@Composable
private fun BundleArrayEditor(state: RestrictionEditorState) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.items_configured_count, state.bundleArrayList.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    state.bundleArrayList.forEachIndexed { index, itemStates ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
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
                                    IconButton(
                                        onClick = { state.removeBundleArrayItem(index) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.remove_item),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                itemStates.forEach { subState ->
                                    RestrictionEntryRow(state = subState)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { state.addBundleArrayItem() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_item))
                    }
                }
            }
        }
    }
}
