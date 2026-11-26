package io.github.warleysr.dechainer.screens.common

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.AppGroupRepository
import io.github.warleysr.dechainer.data.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LimitEntry(
    val key: String,
    val name: String,
    val detail: String?,
    val icon: ImageBitmap?,
    val limitMinutes: Int,
    val usedMinutes: Long
) {
    val fraction get() = (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f)
}

private data class LimitsOverview(val groups: List<LimitEntry>, val apps: List<LimitEntry>)

@Composable
fun UsageLimitsOverview(onClose: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val overview by produceState<LimitsOverview?>(null, refreshTrigger) {
        value = withContext(Dispatchers.IO) { loadOverview(context) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.close))
            }
            Text(
                stringResource(R.string.usage_limits_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        val data = overview
        when {
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            data.groups.isEmpty() && data.apps.isEmpty() -> Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.usage_limits_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (data.groups.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.usage_limits_groups)) }
                    items(data.groups, key = { "group:${it.key}" }) { LimitCard(it) }
                }
                if (data.apps.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.usage_limits_apps)) }
                    items(data.apps, key = { "app:${it.key}" }) { LimitCard(it) }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun LimitCard(entry: LimitEntry) {
    val reached = entry.usedMinutes >= entry.limitMinutes
    val accent = if (reached) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.icon != null) {
                    Image(bitmap = entry.icon, contentDescription = null, modifier = Modifier.size(40.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.detail != null) {
                        Text(
                            entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { entry.fraction },
                color = accent,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
            )
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(
                        R.string.usage_limits_used_of,
                        formatMinutes(entry.usedMinutes),
                        formatMinutes(entry.limitMinutes.toLong())
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (reached) stringResource(R.string.usage_limits_reached)
                    else stringResource(
                        R.string.usage_limits_remaining,
                        formatMinutes(entry.limitMinutes - entry.usedMinutes)
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Long): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}min"
        h > 0 -> "${h}h"
        else -> "${m}min"
    }
}

private fun loadOverview(context: Context): LimitsOverview {
    val pm = context.packageManager
    fun labelOf(pkg: String): String? = try {
        pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    // Limits that vary by weekday are shown with today's value; days without a limit are skipped.
    val groups = AppGroupRepository.getGroups()
        .filter { it.timeLimit.todayMinutes() > 0 }
        .map { group ->
            LimitEntry(
                key = group.id,
                name = group.name,
                detail = group.packageNames.mapNotNull(::labelOf).sortedBy { it.lowercase() }
                    .joinToString(", ").ifEmpty { null },
                icon = null,
                limitMinutes = group.timeLimit.todayMinutes(),
                usedMinutes = AppGroupRepository.getGroupUsage(group.id, inMinutes = true)
            )
        }
        .sortedByDescending { it.fraction }

    val apps = AppRepository.getAppTimeLimits().mapNotNull { (pkg, timeLimit) ->
        val limit = timeLimit.todayMinutes().takeIf { it > 0 } ?: return@mapNotNull null
        val info = try {
            pm.getApplicationInfo(pkg, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return@mapNotNull null
        }
        LimitEntry(
            key = pkg,
            name = info.loadLabel(pm).toString(),
            detail = AppGroupRepository.getGroupForPackage(pkg)?.let { context.getString(R.string.group_label, it.name) },
            icon = info.loadIcon(pm).toBitmap().asImageBitmap(),
            limitMinutes = limit,
            usedMinutes = AppRepository.getAppUsage(pkg, inMinutes = true)
        )
    }.sortedByDescending { it.fraction }

    return LimitsOverview(groups, apps)
}
