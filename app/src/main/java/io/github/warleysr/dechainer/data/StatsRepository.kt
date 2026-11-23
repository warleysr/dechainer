package io.github.warleysr.dechainer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.warleysr.dechainer.models.AppBlockSummary
import io.github.warleysr.dechainer.models.BlockEvent
import io.github.warleysr.dechainer.models.BlockEventType
import io.github.warleysr.dechainer.models.DailyStats
import io.github.warleysr.dechainer.models.RelapseEntry
import io.github.warleysr.dechainer.models.StreakBreakReason
import io.github.warleysr.dechainer.models.StreakSummary
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

object StatsRepository {
    private const val PREFS_NAME = "stats_dashboard_prefs"
    private const val KEY_EVENTS = "block_events"
    private const val KEY_DAILY = "daily_stats"
    private const val KEY_RELAPSES = "relapses"
    private const val KEY_TRACKING_START = "tracking_start_date"
    private const val KEY_BEST_STREAK = "best_streak_days"
    private const val KEY_LAST_BREAK_DATE = "last_break_date"
    private const val KEY_LAST_BREAK_REASON = "last_break_reason"
    private const val KEY_TOTAL_SAVED_MILLIS = "total_saved_millis"

    private const val MAX_EVENTS = 1000
    private const val MAX_DAYS = 90
    private const val MAX_RELAPSES = 200
    private const val DEDUP_WINDOW_MS = 5_000L
    private const val TIME_LIMIT_SAVED_MILLIS = 15 * 60_000L
    private const val REOPEN_LIMIT_SAVED_MILLIS = 5 * 60_000L

    private val lock = Any()

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun ensureTrackingStartDate(p: SharedPreferences, today: LocalDate): LocalDate {
        val stored = p.getString(KEY_TRACKING_START, null)
        if (stored != null) {
            try {
                return LocalDate.parse(stored)
            } catch (_: Exception) {
            }
        }
        p.edit { putString(KEY_TRACKING_START, today.toString()) }
        return today
    }

    fun recordBlockEvent(
        context: Context,
        type: BlockEventType,
        packageName: String,
        details: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        date: LocalDate = LocalDate.now()
    ) {
        synchronized(lock) {
            val p = prefs(context)
            ensureTrackingStartDate(p, date)

            val events = decodeEvents(p.getString(KEY_EVENTS, null)).toMutableList()
            val recent = events.firstOrNull { it.type == type && it.packageName == packageName }
            if (recent != null && nowMillis - recent.timestamp < DEDUP_WINDOW_MS) {
                return
            }

            val appName = resolveAppName(context, packageName)
            val dateStr = date.toString()
            val entry = BlockEvent(
                packageName = packageName,
                appName = appName,
                timestamp = nowMillis,
                date = dateStr,
                type = type,
                details = details
            )
            events.add(0, entry)
            while (events.size > MAX_EVENTS) {
                events.removeAt(events.size - 1)
            }

            val dailyMap = decodeDailyMap(p.getString(KEY_DAILY, null)).toMutableMap()
            val current = dailyMap[dateStr] ?: DailyStats(date = dateStr)
            val addedSaved = type.estimatedSavedMinutes * 60_000L
            val updated = when (type) {
                BlockEventType.VISUAL -> current.copy(
                    visualBlocks = current.visualBlocks + 1,
                    savedMillis = current.savedMillis + addedSaved
                )
                BlockEventType.WORD -> current.copy(
                    wordBlocks = current.wordBlocks + 1,
                    savedMillis = current.savedMillis + addedSaved
                )
                BlockEventType.ACTIVITY -> current.copy(
                    activityBlocks = current.activityBlocks + 1,
                    savedMillis = current.savedMillis + addedSaved
                )
            }
            dailyMap[dateStr] = updated
            pruneOldDays(dailyMap)

            val totalSaved = p.getLong(KEY_TOTAL_SAVED_MILLIS, 0L) + addedSaved
            p.edit {
                putString(KEY_EVENTS, encodeEvents(events))
                putString(KEY_DAILY, encodeDailyMap(dailyMap))
                putLong(KEY_TOTAL_SAVED_MILLIS, totalSaved)
                putString(KEY_LAST_BREAK_DATE, dateStr)
                putString(KEY_LAST_BREAK_REASON, StreakBreakReason.CRITICAL_BLOCK.name)
            }
        }
    }

    fun recordTimeLimitBlock(
        context: Context,
        isReopening: Boolean,
        date: LocalDate = LocalDate.now()
    ) {
        synchronized(lock) {
            val p = prefs(context)
            ensureTrackingStartDate(p, date)
            val dateStr = date.toString()
            val addedSaved = if (isReopening) REOPEN_LIMIT_SAVED_MILLIS else TIME_LIMIT_SAVED_MILLIS
            val dailyMap = decodeDailyMap(p.getString(KEY_DAILY, null)).toMutableMap()
            val current = dailyMap[dateStr] ?: DailyStats(date = dateStr)
            dailyMap[dateStr] = current.copy(savedMillis = current.savedMillis + addedSaved)
            pruneOldDays(dailyMap)

            val totalSaved = p.getLong(KEY_TOTAL_SAVED_MILLIS, 0L) + addedSaved
            p.edit {
                putString(KEY_DAILY, encodeDailyMap(dailyMap))
                putLong(KEY_TOTAL_SAVED_MILLIS, totalSaved)
            }
        }
    }

    fun updateDailyUsage(
        context: Context,
        date: String,
        totalUsageMillis: Long
    ) {
        synchronized(lock) {
            val p = prefs(context)
            val parsedDate = try {
                LocalDate.parse(date)
            } catch (_: Exception) {
                LocalDate.now()
            }
            ensureTrackingStartDate(p, parsedDate)
            val dailyMap = decodeDailyMap(p.getString(KEY_DAILY, null)).toMutableMap()
            val current = dailyMap[date] ?: DailyStats(date = date)
            dailyMap[date] = current.copy(totalUsageMillis = totalUsageMillis.coerceAtLeast(0L))
            pruneOldDays(dailyMap)
            p.edit { putString(KEY_DAILY, encodeDailyMap(dailyMap)) }
        }
    }

    fun recordRecoverySessionOpened(
        context: Context,
        date: LocalDate = LocalDate.now()
    ) {
        synchronized(lock) {
            val p = prefs(context)
            ensureTrackingStartDate(p, date)
            val dateStr = date.toString()
            val dailyMap = decodeDailyMap(p.getString(KEY_DAILY, null)).toMutableMap()
            val current = dailyMap[dateStr] ?: DailyStats(date = dateStr)
            dailyMap[dateStr] = current.copy(recoverySessionOpened = true)
            pruneOldDays(dailyMap)

            p.edit {
                putString(KEY_DAILY, encodeDailyMap(dailyMap))
                putString(KEY_LAST_BREAK_DATE, dateStr)
                putString(KEY_LAST_BREAK_REASON, StreakBreakReason.RECOVERY_SESSION.name)
            }
        }
    }

    fun recordManualRelapse(
        context: Context,
        note: String? = null,
        nowMillis: Long = System.currentTimeMillis(),
        date: LocalDate = LocalDate.now()
    ) {
        synchronized(lock) {
            val p = prefs(context)
            ensureTrackingStartDate(p, date)
            val dateStr = date.toString()
            val relapses = decodeRelapses(p.getString(KEY_RELAPSES, null)).toMutableList()
            val cleanNote = note?.trim()?.takeIf { it.isNotEmpty() }
            relapses.add(
                0,
                RelapseEntry(
                    timestamp = nowMillis,
                    date = dateStr,
                    note = cleanNote
                )
            )
            while (relapses.size > MAX_RELAPSES) {
                relapses.removeAt(relapses.size - 1)
            }

            val dailyMap = decodeDailyMap(p.getString(KEY_DAILY, null)).toMutableMap()
            val current = dailyMap[dateStr] ?: DailyStats(date = dateStr)
            dailyMap[dateStr] = current.copy(manualRelapseCount = current.manualRelapseCount + 1)
            pruneOldDays(dailyMap)

            p.edit {
                putString(KEY_RELAPSES, encodeRelapses(relapses))
                putString(KEY_DAILY, encodeDailyMap(dailyMap))
                putString(KEY_LAST_BREAK_DATE, dateStr)
                putString(KEY_LAST_BREAK_REASON, StreakBreakReason.MANUAL_RELAPSE.name)
            }
        }
    }

    fun getBlockEvents(
        context: Context,
        days: Int? = null,
        today: LocalDate = LocalDate.now()
    ): List<BlockEvent> {
        synchronized(lock) {
            val events = decodeEvents(prefs(context).getString(KEY_EVENTS, null))
            if (days == null) return events
            val cutoff = today.minusDays((days - 1).coerceAtLeast(0).toLong()).toString()
            return events.filter { it.date >= cutoff && it.date <= today.toString() }
        }
    }

    fun getAppBlockSummaries(
        context: Context,
        days: Int? = null,
        today: LocalDate = LocalDate.now()
    ): List<AppBlockSummary> {
        val events = getBlockEvents(context, days, today)
        return events.groupBy { it.packageName }
            .map { (pkg, list) ->
                AppBlockSummary(
                    packageName = pkg,
                    appName = list.firstOrNull()?.appName ?: pkg,
                    visualCount = list.count { it.type == BlockEventType.VISUAL },
                    wordCount = list.count { it.type == BlockEventType.WORD },
                    activityCount = list.count { it.type == BlockEventType.ACTIVITY }
                )
            }
            .sortedByDescending { it.totalCount }
    }

    fun getDailyStatsRange(
        context: Context,
        days: Int = 7,
        today: LocalDate = LocalDate.now()
    ): List<DailyStats> {
        synchronized(lock) {
            val p = prefs(context)
            val dailyMap = decodeDailyMap(p.getString(KEY_DAILY, null))
            val liveTodayUsage = readLiveUsageMillis(context)
            val todayStr = today.toString()

            val result = mutableListOf<DailyStats>()
            for (offset in (days - 1) downTo 0) {
                val dateStr = today.minusDays(offset.toLong()).toString()
                val base = dailyMap[dateStr] ?: DailyStats(date = dateStr)
                val adjusted = if (dateStr == todayStr && liveTodayUsage > base.totalUsageMillis) {
                    base.copy(totalUsageMillis = liveTodayUsage)
                } else {
                    base
                }
                result.add(adjusted)
            }
            return result
        }
    }

    fun getTotalSavedMillis(context: Context): Long {
        synchronized(lock) {
            val p = prefs(context)
            val stored = p.getLong(KEY_TOTAL_SAVED_MILLIS, 0L)
            if (stored > 0L) return stored
            return decodeDailyMap(p.getString(KEY_DAILY, null)).values.sumOf { it.savedMillis }
        }
    }

    fun getStreakSummary(
        context: Context,
        today: LocalDate = LocalDate.now()
    ): StreakSummary {
        synchronized(lock) {
            val p = prefs(context)
            val startDate = ensureTrackingStartDate(p, today)
            val dailyMap = decodeDailyMap(p.getString(KEY_DAILY, null))

            var currentStreak = 0
            var cursor = today
            while (!cursor.isBefore(startDate)) {
                val stats = dailyMap[cursor.toString()]
                if (stats != null && !stats.isClean) {
                    break
                }
                currentStreak++
                cursor = cursor.minusDays(1)
            }

            val isTodayClean = dailyMap[today.toString()]?.isClean ?: true
            val storedBest = p.getInt(KEY_BEST_STREAK, 0)
            val bestStreak = maxOf(storedBest, currentStreak)
            if (bestStreak != storedBest) {
                p.edit { putInt(KEY_BEST_STREAK, bestStreak) }
            }

            val lastBreakDate = p.getString(KEY_LAST_BREAK_DATE, null)
            val lastBreakReason = p.getString(KEY_LAST_BREAK_REASON, null)?.let {
                try {
                    StreakBreakReason.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }

            return StreakSummary(
                currentStreakDays = currentStreak,
                bestStreakDays = bestStreak,
                isTodayClean = isTodayClean,
                lastBreakDate = lastBreakDate,
                lastBreakReason = lastBreakReason
            )
        }
    }

    fun getRelapses(context: Context): List<RelapseEntry> {
        synchronized(lock) {
            return decodeRelapses(prefs(context).getString(KEY_RELAPSES, null))
        }
    }

    fun clearAll(context: Context) {
        synchronized(lock) {
            prefs(context).edit { clear() }
        }
    }

    private fun readLiveUsageMillis(context: Context): Long {
        val usagePrefs = context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE)
        return usagePrefs.all.values.filterIsInstance<Long>().sum()
    }

    private fun resolveAppName(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun pruneOldDays(map: MutableMap<String, DailyStats>) {
        if (map.size <= MAX_DAYS) return
        val keysToRemove = map.keys.sorted().take(map.size - MAX_DAYS)
        keysToRemove.forEach { map.remove(it) }
    }

    private fun encodeEvents(events: List<BlockEvent>): String {
        val array = JSONArray()
        events.forEach { e ->
            array.put(JSONObject().apply {
                put("id", e.id)
                put("type", e.type.name)
                put("packageName", e.packageName)
                put("appName", e.appName)
                put("timestamp", e.timestamp)
                put("date", e.date)
                e.details?.let { put("details", it) }
            })
        }
        return array.toString()
    }

    private fun decodeEvents(json: String?): List<BlockEvent> {
        if (json.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<BlockEvent>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val type = try {
                    BlockEventType.valueOf(obj.getString("type"))
                } catch (_: Exception) {
                    continue
                }
                list.add(
                    BlockEvent(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        type = type,
                        packageName = obj.getString("packageName"),
                        appName = obj.optString("appName", obj.getString("packageName")),
                        timestamp = obj.getLong("timestamp"),
                        date = obj.getString("date"),
                        details = if (obj.has("details")) obj.getString("details") else null
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun encodeDailyMap(map: Map<String, DailyStats>): String {
        val root = JSONObject()
        map.forEach { (date, s) ->
            root.put(date, JSONObject().apply {
                put("totalUsageMillis", s.totalUsageMillis)
                put("savedMillis", s.savedMillis)
                put("visualBlocks", s.visualBlocks)
                put("wordBlocks", s.wordBlocks)
                put("activityBlocks", s.activityBlocks)
                put("recoverySessionOpened", s.recoverySessionOpened)
                put("manualRelapseCount", s.manualRelapseCount)
            })
        }
        return root.toString()
    }

    private fun decodeDailyMap(json: String?): Map<String, DailyStats> {
        if (json.isNullOrEmpty()) return emptyMap()
        val map = mutableMapOf<String, DailyStats>()
        try {
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val date = keys.next()
                val obj = root.getJSONObject(date)
                map[date] = DailyStats(
                    date = date,
                    totalUsageMillis = obj.optLong("totalUsageMillis", 0L),
                    savedMillis = obj.optLong("savedMillis", 0L),
                    visualBlocks = obj.optInt("visualBlocks", 0),
                    wordBlocks = obj.optInt("wordBlocks", 0),
                    activityBlocks = obj.optInt("activityBlocks", 0),
                    recoverySessionOpened = obj.optBoolean("recoverySessionOpened", false),
                    manualRelapseCount = obj.optInt("manualRelapseCount", 0)
                )
            }
        } catch (_: Exception) {
        }
        return map
    }

    private fun encodeRelapses(relapses: List<RelapseEntry>): String {
        val array = JSONArray()
        relapses.forEach { r ->
            array.put(JSONObject().apply {
                put("id", r.id)
                put("timestamp", r.timestamp)
                put("date", r.date)
                r.note?.let { put("note", it) }
            })
        }
        return array.toString()
    }

    private fun decodeRelapses(json: String?): List<RelapseEntry> {
        if (json.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<RelapseEntry>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RelapseEntry(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        timestamp = obj.getLong("timestamp"),
                        date = obj.getString("date"),
                        note = if (obj.has("note")) obj.getString("note") else null
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }
}
