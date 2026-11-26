package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.PermanentRestrictionsManager
import io.github.warleysr.dechainer.models.ScreenBlockMode
import io.github.warleysr.dechainer.models.ScreenBlockRule
import io.github.warleysr.dechainer.models.WindowHierarchyNode
import io.github.warleysr.dechainer.screens.common.PermanentRestrictionBanner
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.utils.ScreenRuleMatcher
import io.github.warleysr.dechainer.viewmodels.ActivityBlockerViewModel
import io.github.warleysr.dechainer.viewmodels.AppHierarchyUi

private data class RuleEditTarget(
    val packageName: String,
    val appName: String,
    val hierarchyPath: List<String>,
    val existingRule: ScreenBlockRule?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityBlockerScreen(
    viewModel: ActivityBlockerViewModel = viewModel(),
) {
    var editTarget by remember { mutableStateOf<RuleEditTarget?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val context = LocalContext.current
    val isPermanent = remember {
        PermanentRestrictionsManager.isPermanent(context, PermanentRestrictionsManager.KEY_BLOCKED_ACTIVITIES)
    }
    val recoveryGate = rememberRecoveryGate(PermanentRestrictionsManager.KEY_BLOCKED_ACTIVITIES)

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val filteredHierarchies = remember(viewModel.appHierarchies, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            viewModel.appHierarchies
        } else {
            viewModel.appHierarchies.filter { app ->
                app.appName.contains(q, ignoreCase = true) ||
                    app.packageName.contains(q, ignoreCase = true) ||
                    containsMatchingNode(app.nodes, q)
            }
        }
    }

    Scaffold { _ ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (isPermanent) {
                item {
                    PermanentRestrictionBanner()
                }
            }

            // Active rules header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.screen_block_rules),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = {
                            viewModel.loadInstalledApps()
                            showAppPicker = true
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.add_app))
                    }
                }
            }

            if (viewModel.rules.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.screen_block_rules_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(viewModel.rules, key = { it.id }) { rule ->
                    ScreenBlockRuleCard(
                        rule = rule,
                        viewModel = viewModel,
                        onEdit = {
                            val appName = if (rule.isLegacyClassOnly) {
                                context.getString(R.string.legacy_screen_rule)
                            } else {
                                viewModel.resolveAppName(rule.packageName)
                            }
                            editTarget = RuleEditTarget(
                                packageName = rule.packageName,
                                appName = appName,
                                hierarchyPath = rule.hierarchyPath,
                                existingRule = rule
                            )
                        },
                        onDelete = {
                            recoveryGate.run { viewModel.removeRule(rule.id) }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Discovered hierarchies header
            item {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.screen_hierarchies),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.screen_hierarchies_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                        }
                        if (viewModel.appHierarchies.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirm = true }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                            }
                        }
                    }
                }
            }

            if (viewModel.appHierarchies.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        placeholder = { Text(stringResource(R.string.search_screens_hint)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                        } else null,
                        singleLine = true
                    )
                }
            }

            if (filteredHierarchies.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.screen_hierarchies_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredHierarchies, key = { it.packageName }) { appHierarchy ->
                    AppHierarchyAccordion(
                        appHierarchy = appHierarchy,
                        viewModel = viewModel,
                        searchQuery = searchQuery.trim(),
                        onSelectTarget = { path ->
                            editTarget = RuleEditTarget(
                                packageName = appHierarchy.packageName,
                                appName = appHierarchy.appName,
                                hierarchyPath = path,
                                existingRule = viewModel.findRule(appHierarchy.packageName, path)
                            )
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    editTarget?.let { target ->
        WindowRuleEditorDialog(
            target = target,
            onDismiss = { editTarget = null },
            onSave = { mode, words ->
                val updated = ScreenBlockRule(
                    id = target.existingRule?.id ?: java.util.UUID.randomUUID().toString(),
                    packageName = target.packageName,
                    hierarchyPath = target.hierarchyPath,
                    mode = mode,
                    words = if (mode == ScreenBlockMode.PASSIVE_WORDS) words else emptySet()
                )
                val loosening = ScreenRuleMatcher.isLoosening(target.existingRule, updated)
                recoveryGate.change(loosening = loosening) {
                    viewModel.upsertRule(updated)
                }
                editTarget = null
            },
            onDelete = target.existingRule?.let { existing ->
                {
                    recoveryGate.run {
                        viewModel.removeRule(existing.id)
                    }
                    editTarget = null
                }
            }
        )
    }

    if (showAppPicker) {
        ScreenBlockerAppPickerDialog(
            viewModel = viewModel,
            onDismiss = { showAppPicker = false },
            onSelectApp = { app ->
                showAppPicker = false
                editTarget = RuleEditTarget(
                    packageName = app.packageName,
                    appName = app.name,
                    hierarchyPath = emptyList(),
                    existingRule = viewModel.findRule(app.packageName, emptyList())
                )
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.clear_screen_history_title)) },
            text = { Text(stringResource(R.string.clear_screen_history_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearUnusedHierarchies()
                    showClearConfirm = false
                }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    RecoveryGateDialog(recoveryGate)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScreenBlockRuleCard(
    rule: ScreenBlockRule,
    viewModel: ActivityBlockerViewModel,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val appName = if (rule.isLegacyClassOnly) {
        stringResource(R.string.legacy_screen_rule)
    } else {
        viewModel.resolveAppName(rule.packageName)
    }
    val icon = remember(rule.packageName) {
        viewModel.resolveAppIcon(rule.packageName).toBitmap().asImageBitmap()
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!rule.isLegacyClassOnly) {
                        Text(
                            text = rule.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            HierarchyBreadcrumb(
                appName = appName,
                hierarchyPath = rule.hierarchyPath,
                isLegacyClassOnly = rule.isLegacyClassOnly
            )

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RuleModeBadge(mode = rule.mode, wordCount = rule.words.size)
                if (rule.mode == ScreenBlockMode.PASSIVE_WORDS && rule.words.isNotEmpty()) {
                    val preview = rule.words.sorted().take(4).joinToString(", ") +
                        if (rule.words.size > 4) "…" else ""
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HierarchyBreadcrumb(
    appName: String,
    hierarchyPath: List<String>,
    isLegacyClassOnly: Boolean = false
) {
    val segments = buildList {
        if (!isLegacyClassOnly) add(appName)
        hierarchyPath.forEach { add(ScreenBlockRule.simpleClassName(it)) }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        segments.forEachIndexed { index, segment ->
            val isTarget = index == segments.lastIndex
            Surface(
                color = if (isTarget) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = segment,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                    color = if (isTarget) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            if (!isTarget) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun RuleModeBadge(
    mode: ScreenBlockMode,
    wordCount: Int,
    compact: Boolean = false
) {
    val isTotal = mode == ScreenBlockMode.TOTAL
    val bgColor = if (isTotal) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isTotal) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val text = when {
        isTotal && compact -> stringResource(R.string.block_mode_total_short)
        isTotal -> stringResource(R.string.block_mode_total)
        compact -> stringResource(R.string.block_mode_passive_short, wordCount)
        else -> stringResource(R.string.block_mode_passive_count, wordCount)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Icon(
                imageVector = if (isTotal) Icons.Default.Block else Icons.Outlined.TextFields,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun AppHierarchyAccordion(
    appHierarchy: AppHierarchyUi,
    viewModel: ActivityBlockerViewModel,
    searchQuery: String,
    onSelectTarget: (List<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val autoExpanded = searchQuery.isNotEmpty()
    val isExpanded = expanded || autoExpanded
    val iconBitmap = remember(appHierarchy.packageName) {
        appHierarchy.icon.toBitmap().asImageBitmap()
    }
    val appRootRule = viewModel.findRule(appHierarchy.packageName, emptyList())

    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appHierarchy.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = appHierarchy.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (appHierarchy.activeRuleCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(appHierarchy.activeRuleCount.toString())
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    // Root node: entire app
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTarget(emptyList()) }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.entire_app_node, appHierarchy.appName),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.entire_app_node_desc),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (appRootRule != null) {
                            RuleModeBadge(
                                mode = appRootRule.mode,
                                wordCount = appRootRule.words.size,
                                compact = true
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Hierarchical window/screen tree
                    appHierarchy.nodes.forEachIndexed { index, node ->
                        HierarchyTreeNode(
                            node = node,
                            packageName = appHierarchy.packageName,
                            parentPath = emptyList(),
                            ancestorContinuations = emptyList(),
                            isLastSibling = index == appHierarchy.nodes.lastIndex,
                            viewModel = viewModel,
                            onSelectTarget = onSelectTarget
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HierarchyTreeNode(
    node: WindowHierarchyNode,
    packageName: String,
    parentPath: List<String>,
    ancestorContinuations: List<Boolean>,
    isLastSibling: Boolean,
    viewModel: ActivityBlockerViewModel,
    onSelectTarget: (List<String>) -> Unit
) {
    val nodePath = remember(parentPath, node.className) { parentPath + node.className }
    val rule = viewModel.findRule(packageName, nodePath)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val simpleName = remember(node.className) { ScreenBlockRule.simpleClassName(node.className) }
    val nodeIcon = remember(node.className) { iconForWindowClass(node.className) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable { onSelectTarget(nodePath) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ancestor vertical guide lines
            ancestorContinuations.forEach { hasMoreSiblings ->
                Canvas(
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight()
                ) {
                    if (hasMoreSiblings) {
                        val x = size.width / 2f
                        drawLine(
                            color = lineColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }

            // Connector branch (├ or └) for this node
            Canvas(
                modifier = Modifier
                    .width(20.dp)
                    .fillMaxHeight()
            ) {
                val x = size.width / 2f
                val midY = size.height / 2f
                val stroke = 1.5.dp.toPx()
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, if (isLastSibling) midY else size.height),
                    strokeWidth = stroke
                )
                drawLine(
                    color = lineColor,
                    start = Offset(x, midY),
                    end = Offset(size.width, midY),
                    strokeWidth = stroke
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = nodeIcon,
                    contentDescription = null,
                    tint = if (rule != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = simpleName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = node.className,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                if (rule != null) {
                    RuleModeBadge(
                        mode = rule.mode,
                        wordCount = rule.words.size,
                        compact = true
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        val childContinuations = remember(ancestorContinuations, isLastSibling) {
            ancestorContinuations + !isLastSibling
        }
        node.children.forEachIndexed { idx, child ->
            HierarchyTreeNode(
                node = child,
                packageName = packageName,
                parentPath = nodePath,
                ancestorContinuations = childContinuations,
                isLastSibling = idx == node.children.lastIndex,
                viewModel = viewModel,
                onSelectTarget = onSelectTarget
            )
        }
    }
}

@Composable
private fun WindowRuleEditorDialog(
    target: RuleEditTarget,
    onDismiss: () -> Unit,
    onSave: (ScreenBlockMode, Set<String>) -> Unit,
    onDelete: (() -> Unit)?
) {
    var selectedMode by remember(target) {
        mutableStateOf(
            target.existingRule?.mode
                ?: if (target.hierarchyPath.isEmpty()) ScreenBlockMode.PASSIVE_WORDS else ScreenBlockMode.TOTAL
        )
    }
    var wordsText by remember(target) {
        mutableStateOf(target.existingRule?.words?.sorted()?.joinToString("\n") ?: "")
    }
    val parsedWords = remember(wordsText) {
        wordsText.lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }
    val canSave = selectedMode == ScreenBlockMode.TOTAL || parsedWords.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.configure_screen_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HierarchyBreadcrumb(
                    appName = target.appName,
                    hierarchyPath = target.hierarchyPath,
                    isLegacyClassOnly = target.packageName.isEmpty()
                )

                ModeOptionCard(
                    selected = selectedMode == ScreenBlockMode.TOTAL,
                    title = stringResource(R.string.block_mode_total),
                    description = stringResource(R.string.block_mode_total_desc),
                    icon = Icons.Default.Block,
                    onClick = { selectedMode = ScreenBlockMode.TOTAL }
                )

                ModeOptionCard(
                    selected = selectedMode == ScreenBlockMode.PASSIVE_WORDS,
                    title = stringResource(R.string.block_mode_passive),
                    description = stringResource(R.string.block_mode_passive_desc),
                    icon = Icons.Outlined.TextFields,
                    onClick = { selectedMode = ScreenBlockMode.PASSIVE_WORDS }
                )

                AnimatedVisibility(visible = selectedMode == ScreenBlockMode.PASSIVE_WORDS) {
                    OutlinedTextField(
                        value = wordsText,
                        onValueChange = { wordsText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 200.dp),
                        placeholder = { Text(stringResource(R.string.word_hint)) },
                        supportingText = { Text(stringResource(R.string.one_word_per_line)) }
                    )
                }

                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.remove_rule))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                onClick = { onSave(selectedMode, parsedWords) }
            ) {
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
private fun ModeOptionCard(
    selected: Boolean,
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ScreenBlockerAppPickerDialog(
    viewModel: ActivityBlockerViewModel,
    onDismiss: () -> Unit,
    onSelectApp: (io.github.warleysr.dechainer.models.AppItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val filteredApps = remember(searchQuery, viewModel.installedApps, showSystemApps) {
        viewModel.installedApps
            .filter {
                (showSystemApps || !it.isSystem) &&
                    (it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true))
            }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_apps)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_apps)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.show_system_apps)) },
                                onClick = {
                                    showSystemApps = !showSystemApps
                                    showMenu = false
                                },
                                trailingIcon = {
                                    Checkbox(checked = showSystemApps, onCheckedChange = null)
                                }
                            )
                        }
                    }
                }

                if (viewModel.isLoadingApps) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectApp(app) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    bitmap = app.icon.toBitmap().asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Text(app.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun iconForWindowClass(className: String): ImageVector {
    return when {
        className.contains("Dialog", ignoreCase = true) ||
            className.contains("Popup", ignoreCase = true) ||
            className.contains("Sheet", ignoreCase = true) -> Icons.Outlined.WebAsset
        className.contains("Activity", ignoreCase = true) -> Icons.Outlined.Smartphone
        else -> Icons.Outlined.Layers
    }
}

private fun containsMatchingNode(nodes: List<WindowHierarchyNode>, query: String): Boolean {
    return nodes.any { node ->
        node.className.contains(query, ignoreCase = true) ||
            containsMatchingNode(node.children, query)
    }
}
