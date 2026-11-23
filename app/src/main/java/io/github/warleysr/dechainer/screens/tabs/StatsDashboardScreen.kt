package io.github.warleysr.dechainer.screens.tabs

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.StatsRepository
import io.github.warleysr.dechainer.models.AppBlockSummary
import io.github.warleysr.dechainer.models.BlockEventType
import io.github.warleysr.dechainer.models.DailyStats
import io.github.warleysr.dechainer.models.RelapseEntry
import io.github.warleysr.dechainer.models.StreakBreakReason
import io.github.warleysr.dechainer.models.StreakSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class StatsPeriod(val days: Int, val labelRes: Int) {
    TODAY(1, R.string.stats_period_today),
    WEEK(7, R.string.stats_period_7_days),
    MONTH(30, R.string.stats_period_30_days)
}

private data class DashboardState(
    val streak: StreakSummary,
    val weeklyDays: List<DailyStats>,
    val totalSavedMillis: Long,
    val visualCount: Int,
    val wordCount: Int,
    val activityCount: Int,
    val topApps: List<AppBlockSummary>,
    val relapses: List<RelapseEntry>
)

@Composable
fun StatsDashboardScreen() {
    val context = LocalContext.current
    var selectedPeriod by remember { mutableStateOf(StatsPeriod.WEEK) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showRelapseDialog by remember { mutableStateOf(false) }

    val state by produceState<DashboardState?>(null, selectedPeriod, refreshTrigger) {
        value = withContext(Dispatchers.IO) { loadDashboardState(context, selectedPeriod.days) }
    }

    val data = state
    if (data == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                StreakCard(
                    streak = data.streak,
                    onLogRelapse = { showRelapseDialog = true }
                )
            }

            item {
                WeeklyUsageAndSavedCard(
                    weeklyDays = data.weeklyDays,
                    totalSavedMillis = data.totalSavedMillis
                )
            }

            item {
                BlockTriggersCard(
                    selectedPeriod = selectedPeriod,
                    onSelectPeriod = { selectedPeriod = it },
                    visualCount = data.visualCount,
                    wordCount = data.wordCount,
                    activityCount = data.activityCount,
                    weeklyDays = data.weeklyDays
                )
            }

            item {
                Text(
                    text = stringResource(R.string.stats_top_apps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (data.topApps.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.stats_no_blocks_period),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(data.topApps, key = { it.packageName }) { app ->
                    AppBlockSummaryRow(app)
                }
            }

            if (data.relapses.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.stats_relapse_history_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(data.relapses.take(10), key = { it.id }) { relapse ->
                    RelapseHistoryItem(relapse)
                }
            }
        }
    }

    if (showRelapseDialog) {
        LogRelapseDialog(
            onDismiss = { showRelapseDialog = false },
            onConfirm = { note ->
                StatsRepository.recordManualRelapse(context, note)
                showRelapseDialog = false
                refreshTrigger++
            }
        )
    }
}

private fun loadDashboardState(context: Context, periodDays: Int): DashboardState {
    val streak = StatsRepository.getStreakSummary(context)
    val weeklyDays = StatsRepository.getDailyStatsRange(context, days = 7)
    val totalSavedMillis = StatsRepository.getTotalSavedMillis(context)
    val events = StatsRepository.getBlockEvents(context, days = periodDays)
    val topApps = StatsRepository.getAppBlockSummaries(context, days = periodDays)
    val relapses = StatsRepository.getRelapses(context)

    return DashboardState(
        streak = streak,
        weeklyDays = weeklyDays,
        totalSavedMillis = totalSavedMillis,
        visualCount = events.count { it.type == BlockEventType.VISUAL },
        wordCount = events.count { it.type == BlockEventType.WORD },
        activityCount = events.count { it.type == BlockEventType.ACTIVITY },
        topApps = topApps,
        relapses = relapses
    )
}

@Composable
private fun StreakCard(
    streak: StreakSummary,
    onLogRelapse: () -> Unit
) {
    val statusColor = if (streak.isTodayClean) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (streak.isTodayClean) {
                            Icons.Outlined.VerifiedUser
                        } else {
                            Icons.Outlined.WarningAmber
                        },
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.stats_clean_days_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (streak.currentStreakDays == 1) {
                            stringResource(R.string.stats_clean_day_single)
                        } else {
                            stringResource(R.string.stats_clean_days_count, streak.currentStreakDays)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (streak.isTodayClean) {
                            stringResource(R.string.stats_today_clean)
                        } else {
                            stringResource(R.string.stats_today_broken)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.stats_best_streak, streak.bestStreakDays),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = onLogRelapse,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.stats_log_relapse),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            if (streak.lastBreakDate != null && streak.lastBreakReason != null) {
                val reasonLabel = when (streak.lastBreakReason) {
                    StreakBreakReason.CRITICAL_BLOCK -> stringResource(R.string.stats_break_reason_critical)
                    StreakBreakReason.RECOVERY_SESSION -> stringResource(R.string.stats_break_reason_recovery)
                    StreakBreakReason.MANUAL_RELAPSE -> stringResource(R.string.stats_break_reason_relapse)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.stats_last_break, formatIsoDateShort(streak.lastBreakDate), reasonLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeeklyUsageAndSavedCard(
    weeklyDays: List<DailyStats>,
    totalSavedMillis: Long
) {
    val weekSavedMillis = remember(weeklyDays) { weeklyDays.sumOf { it.savedMillis } }
    val weekUsageMillis = remember(weeklyDays) { weeklyDays.sumOf { it.totalUsageMillis } }
    val maxUsageMillis = remember(weeklyDays) {
        (weeklyDays.maxOfOrNull { it.totalUsageMillis } ?: 0L).coerceAtLeast(1L)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_weekly_usage_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricBox(
                    label = stringResource(R.string.stats_time_saved_total),
                    value = formatDurationMillis(totalSavedMillis),
                    icon = Icons.Outlined.Savings,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = stringResource(R.string.stats_time_saved_week),
                    value = formatDurationMillis(weekSavedMillis),
                    icon = Icons.Outlined.Timer,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.stats_weekly_usage_total, formatDurationMillis(weekUsageMillis)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // 7-day bar chart
            val todayStr = remember { LocalDate.now().toString() }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyDays.forEach { day ->
                    val fraction = if (day.totalUsageMillis == 0L) {
                        0.04f
                    } else {
                        (day.totalUsageMillis.toFloat() / maxUsageMillis.toFloat()).coerceIn(0.08f, 1f)
                    }
                    val isToday = day.date == todayStr
                    val barColor = if (isToday) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = formatShortMinutes(day.totalUsageMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height((72 * fraction).dp)
                                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                .background(barColor)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = dayLabel(day.date),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.stats_time_saved_estimate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BlockTriggersCard(
    selectedPeriod: StatsPeriod,
    onSelectPeriod: (StatsPeriod) -> Unit,
    visualCount: Int,
    wordCount: Int,
    activityCount: Int,
    weeklyDays: List<DailyStats>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.stats_blocks_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(stringResource(period.labelRes)) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricBox(
                    label = stringResource(R.string.stats_block_visual),
                    value = visualCount.toString(),
                    icon = Icons.Outlined.ImageSearch,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = stringResource(R.string.stats_block_word),
                    value = wordCount.toString(),
                    icon = Icons.Outlined.Password,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                MetricBox(
                    label = stringResource(R.string.stats_block_activity),
                    value = activityCount.toString(),
                    icon = Icons.Outlined.Block,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.stats_daily_blocks_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            val maxDailyBlocks = remember(weeklyDays) {
                (weeklyDays.maxOfOrNull { it.totalCriticalBlocks } ?: 0).coerceAtLeast(1)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyDays.forEach { day ->
                    val total = day.totalCriticalBlocks
                    val fraction = if (total == 0) 0.05f else (total.toFloat() / maxDailyBlocks.toFloat()).coerceIn(0.12f, 1f)
                    val color = if (total == 0) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            text = total.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height((48 * fraction).dp)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(color)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = dayLabel(day.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    label: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AppBlockSummaryRow(summary: AppBlockSummary) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(summary.packageName) {
        try {
            context.packageManager.getApplicationIcon(summary.packageName).toBitmap().asImageBitmap()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.stats_app_block_breakdown,
                        summary.visualCount,
                        summary.wordCount,
                        summary.activityCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Badge(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    text = summary.totalCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RelapseHistoryItem(entry: RelapseEntry) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Flag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.note ?: stringResource(R.string.stats_relapse_no_note),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogRelapseDialog(
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stats_log_relapse_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.stats_log_relapse_desc),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    placeholder = { Text(stringResource(R.string.stats_log_relapse_note_hint)) },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note) }) {
                Text(
                    text = stringResource(R.string.stats_log_relapse_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun formatDurationMillis(millis: Long): String {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

private fun formatShortMinutes(millis: Long): String {
    val mins = TimeUnit.MILLISECONDS.toMinutes(millis)
    return if (mins >= 60) "${mins / 60}h" else "${mins}m"
}

private fun dayLabel(isoDate: String): String = try {
    LocalDate.parse(isoDate).dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .replace(".", "")
        .take(3)
} catch (_: Exception) {
    isoDate.takeLast(2)
}

private fun formatIsoDateShort(isoDate: String): String = try {
    val parsed = LocalDate.parse(isoDate)
    "%02d/%02d".format(parsed.dayOfMonth, parsed.monthValue)
} catch (_: Exception) {
    isoDate
}
