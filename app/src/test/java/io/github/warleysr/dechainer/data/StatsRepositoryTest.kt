package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.models.BlockEventType
import io.github.warleysr.dechainer.models.StreakBreakReason
import io.github.warleysr.dechainer.models.TimeLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class StatsRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        StatsRepository.clearAll(context)
        context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE)
            .edit { clear() }
        context.getSharedPreferences(AppTimeLimits.PREFS_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
        context.getSharedPreferences(AppTimeLimits.WEEKLY_PREFS_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
        context.getSharedPreferences(AppGroupRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .edit { clear() }
    }

    @Test
    fun `recordBlockEvent deduplicates same type and package within 5 seconds`() {
        val day = LocalDate.of(2026, 11, 20)
        StatsRepository.recordBlockEvent(
            context, BlockEventType.VISUAL, "com.example.app",
            nowMillis = 10_000L, date = day
        )
        StatsRepository.recordBlockEvent(
            context, BlockEventType.VISUAL, "com.example.app",
            nowMillis = 12_000L, date = day
        )
        StatsRepository.recordBlockEvent(
            context, BlockEventType.WORD, "com.example.app",
            details = "badword", nowMillis = 12_000L, date = day
        )
        StatsRepository.recordBlockEvent(
            context, BlockEventType.VISUAL, "com.example.app",
            nowMillis = 16_000L, date = day
        )

        val events = StatsRepository.getBlockEvents(context, days = 7, today = day)
        assertEquals(3, events.size)

        val summaries = StatsRepository.getAppBlockSummaries(context, days = 7, today = day)
        assertEquals(1, summaries.size)
        assertEquals(2, summaries[0].visualCount)
        assertEquals(1, summaries[0].wordCount)
        assertEquals(0, summaries[0].activityCount)
        assertEquals(3, summaries[0].totalCount)
    }

    @Test
    fun `streak counts consecutive clean days and resets on critical block or recovery session or relapse`() {
        val day1 = LocalDate.of(2026, 11, 18)
        val day3 = LocalDate.of(2026, 11, 20)
        val day5 = LocalDate.of(2026, 11, 22)

        StatsRepository.updateDailyUsage(context, day1.toString(), 60_000L)
        assertEquals(3, StatsRepository.getStreakSummary(context, today = day3).currentStreakDays)

        StatsRepository.recordRecoverySessionOpened(context, date = day3)
        val summaryOnDay3 = StatsRepository.getStreakSummary(context, today = day3)
        assertEquals(0, summaryOnDay3.currentStreakDays)
        assertEquals(3, summaryOnDay3.bestStreakDays)
        assertFalse(summaryOnDay3.isTodayClean)
        assertEquals(StreakBreakReason.RECOVERY_SESSION, summaryOnDay3.lastBreakReason)

        val summaryOnDay5 = StatsRepository.getStreakSummary(context, today = day5)
        assertEquals(2, summaryOnDay5.currentStreakDays)
        assertEquals(3, summaryOnDay5.bestStreakDays)
        assertTrue(summaryOnDay5.isTodayClean)

        StatsRepository.recordManualRelapse(context, note = "Opened browser", date = day5)
        val summaryAfterRelapse = StatsRepository.getStreakSummary(context, today = day5)
        assertEquals(0, summaryAfterRelapse.currentStreakDays)
        assertEquals(StreakBreakReason.MANUAL_RELAPSE, summaryAfterRelapse.lastBreakReason)
        assertEquals(1, StatsRepository.getRelapses(context).size)
        assertEquals("Opened browser", StatsRepository.getRelapses(context)[0].note)
    }

    @Test
    fun `getWeeklyUsageSummary computes usage vs limits and supports historical weeks`() {
        val daily = context.getSharedPreferences(AppTimeLimits.PREFS_NAME, Context.MODE_PRIVATE)
        val weekly = context.getSharedPreferences(AppTimeLimits.WEEKLY_PREFS_NAME, Context.MODE_PRIVATE)
        AppTimeLimits.write(daily, weekly, "com.example.social", TimeLimit.daily(60))

        // Monday 2026-11-23 is current week start; previous week is 2026-11-16..2026-11-22
        val prevMon = LocalDate.of(2026, 11, 16)
        val prevWed = LocalDate.of(2026, 11, 18)
        val today = LocalDate.of(2026, 11, 23)

        StatsRepository.updateDailyUsage(
            context,
            prevMon.toString(),
            40 * 60_000L,
            mapOf("com.example.social" to 40 * 60_000L)
        )
        StatsRepository.updateDailyUsage(
            context,
            prevWed.toString(),
            20 * 60_000L,
            mapOf("com.example.social" to 20 * 60_000L)
        )

        context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE).edit {
            putLong("com.example.social", 30 * 60_000L)
        }

        val currentWeek = StatsRepository.getWeeklyUsageSummary(context, weekOffset = 0, today = today)
        assertEquals("2026-11-23", currentWeek.startDate)
        assertEquals("2026-11-29", currentWeek.endDate)
        assertEquals(30 * 60_000L, currentWeek.totalUsageMillis)
        assertEquals(7 * 60 * 60_000L, currentWeek.totalLimitMillis)
        assertEquals(30 * 60_000L, currentWeek.averageDailyMillis)
        assertTrue(currentWeek.hasPreviousData)
        assertEquals(1, currentWeek.appSummaries.size)
        assertEquals(30 * 60_000L, currentWeek.appSummaries[0].usedMillis)

        val previousWeek = StatsRepository.getWeeklyUsageSummary(context, weekOffset = -1, today = today)
        assertEquals("2026-11-16", previousWeek.startDate)
        assertEquals("2026-11-22", previousWeek.endDate)
        assertEquals(60 * 60_000L, previousWeek.totalUsageMillis)
        assertEquals(7 * 60 * 60_000L, previousWeek.totalLimitMillis)
        assertFalse(previousWeek.hasPreviousData)
        assertEquals(40 * 60_000L, previousWeek.days[0].totalUsageMillis)
        assertEquals(60 * 60_000L, previousWeek.days[0].totalLimitMillis)
        assertEquals(60 * 60_000L, previousWeek.appSummaries[0].usedMillis)
    }

    @Test
    fun `getWeeklyUsageSummary respects per-weekday limits, group limits, and live usage date`() {
        val daily = context.getSharedPreferences(AppTimeLimits.PREFS_NAME, Context.MODE_PRIVATE)
        val weekly = context.getSharedPreferences(AppTimeLimits.WEEKLY_PREFS_NAME, Context.MODE_PRIVATE)
        // Mon=10, Tue=20, Wed=30, Thu=40, Fri=50, Sat=60, Sun=70
        AppTimeLimits.write(
            daily,
            weekly,
            "com.example.video",
            TimeLimit.perDay(listOf(10, 20, 30, 40, 50, 60, 70))
        )

        // Group with daily limit of 45m for apps without individual limits
        val groupJson = """[{"id":"g1","name":"Social","packages":["com.example.ig","com.example.tt"],"limit":45}]"""
        context.getSharedPreferences(AppGroupRepository.PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString("groups", groupJson)
        }

        // Unarchived live usage from Tuesday 2026-11-24 viewed on Wednesday 2026-11-25
        val tuesday = LocalDate.of(2026, 11, 24)
        val wednesday = LocalDate.of(2026, 11, 25)
        StatsRepository.setLiveUsageDate(context, tuesday.toString())
        context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE).edit {
            putLong("com.example.ig", 25 * 60_000L)
        }

        val summary = StatsRepository.getWeeklyUsageSummary(context, weekOffset = 0, today = wednesday)
        // Tuesday (index 1) should receive the 25m unarchived live usage, NOT Wednesday (index 2)
        assertEquals(25 * 60_000L, summary.days[1].totalUsageMillis)
        assertEquals(0L, summary.days[2].totalUsageMillis)

        // Daily limits should include both individual per-weekday limit and group cap (45m)
        assertEquals((10 + 45) * 60_000L, summary.days[0].totalLimitMillis)
        assertEquals((20 + 45) * 60_000L, summary.days[1].totalLimitMillis)
        assertEquals((30 + 45) * 60_000L, summary.days[2].totalLimitMillis)
        assertEquals((70 + 45) * 60_000L, summary.days[6].totalLimitMillis)
    }
}
