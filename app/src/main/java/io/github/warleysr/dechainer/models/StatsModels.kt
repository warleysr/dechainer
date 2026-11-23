package io.github.warleysr.dechainer.models

enum class BlockEventType(val estimatedSavedMinutes: Int) {
    VISUAL(10),
    WORD(10),
    ACTIVITY(10)
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
    val savedMillis: Long = 0L,
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
