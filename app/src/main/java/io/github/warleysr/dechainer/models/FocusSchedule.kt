package io.github.warleysr.dechainer.models

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID

data class FocusSchedule(
    val id: String = UUID.randomUUID().toString(),
    val daysOfWeek: Set<Int>, // 1 = Monday .. 7 = Sunday (matching DayOfWeek.value)
    val hour: Int,            // 0..23
    val minute: Int,          // 0..59
    val enabled: Boolean = true
) {
    /** Formats time as "08:00". */
    fun formattedTime(): String = "%02d:%02d".format(hour, minute)

    /**
     * Calculates the next epoch timestamp (in milliseconds) at which this schedule should trigger,
     * given the current [now] datetime. Returns null if no days are configured or schedule is disabled.
     */
    fun nextTriggerMillis(now: LocalDateTime = LocalDateTime.now()): Long? {
        if (!enabled || daysOfWeek.isEmpty()) return null

        val currentDay = now.dayOfWeek.value
        val currentTimeInMinutes = now.hour * 60 + now.minute
        val targetTimeInMinutes = hour * 60 + minute

        // If today is in the schedule and the target time is later today:
        if (currentDay in daysOfWeek && targetTimeInMinutes > currentTimeInMinutes) {
            val targetDateTime = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            return targetDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        // Otherwise find the earliest day of week from tomorrow up to 7 days ahead
        var minDateTime: LocalDateTime? = null
        for (dayVal in daysOfWeek) {
            val dow = DayOfWeek.of(dayVal)
            val candidate = now.with(TemporalAdjusters.next(dow))
                .withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            if (minDateTime == null || candidate.isBefore(minDateTime)) {
                minDateTime = candidate
            }
        }

        return minDateTime?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
    }
}
