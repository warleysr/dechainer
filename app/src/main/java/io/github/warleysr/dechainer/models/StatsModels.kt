package io.github.warleysr.dechainer.models

enum class BlockEventType {
    VISUAL,
    WORD,
    ACTIVITY
}

data class BlockEvent(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: BlockEventType,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val date: String,
    val details: String? = null
)

data class DailyStats(
    val date: String,
    val totalUsageMillis: Long = 0L,
    val totalLimitMillis: Long = 0L,
    val appUsageMillis: Map<String, Long> = emptyMap(),
    val visualBlocks: Int = 0,
    val wordBlocks: Int = 0,
    val activityBlocks: Int = 0,
    val recoverySessionOpened: Boolean = false,
    val manualRelapseCount: Int = 0
) {
    val totalCriticalBlocks: Int
        get() = visualBlocks + wordBlocks + activityBlocks

    val isClean: Boolean
        get() = totalCriticalBlocks == 0 && !recoverySessionOpened && manualRelapseCount == 0
}

data class AppUsageSummary(
    val packageName: String,
    val appName: String,
    val usedMillis: Long,
    val weeklyLimitMillis: Long
)

data class WeeklyUsageSummary(
    val startDate: String,
    val endDate: String,
    val days: List<DailyStats>,
    val totalUsageMillis: Long,
    val totalLimitMillis: Long,
    val averageDailyMillis: Long,
    val appSummaries: List<AppUsageSummary>,
    val hasPreviousData: Boolean
)

data class RelapseEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val date: String,
    val note: String? = null
)

enum class StreakBreakReason {
    CRITICAL_BLOCK,
    RECOVERY_SESSION,
    MANUAL_RELAPSE
}

data class StreakSummary(
    val currentStreakDays: Int,
    val bestStreakDays: Int,
    val isTodayClean: Boolean,
    val lastBreakDate: String?,
    val lastBreakReason: StreakBreakReason?
)

data class AppBlockSummary(
    val packageName: String,
    val appName: String,
    val visualCount: Int,
    val wordCount: Int,
    val activityCount: Int
) {
    val totalCount: Int
        get() = visualCount + wordCount + activityCount
}
