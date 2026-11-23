package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.models.BlockEventType
import io.github.warleysr.dechainer.models.StreakBreakReason
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
        val day2 = LocalDate.of(2026, 11, 19)
        val day3 = LocalDate.of(2026, 11, 20)
        val day4 = LocalDate.of(2026, 11, 21)
        val day5 = LocalDate.of(2026, 11, 22)

        // Initialize tracking on day1
        StatsRepository.updateDailyUsage(context, day1.toString(), 60_000L)
        assertEquals(3, StatsRepository.getStreakSummary(context, today = day3).currentStreakDays)

        // Recovery session on day3 breaks streak
        StatsRepository.recordRecoverySessionOpened(context, date = day3)
        val summaryOnDay3 = StatsRepository.getStreakSummary(context, today = day3)
        assertEquals(0, summaryOnDay3.currentStreakDays)
        assertEquals(3, summaryOnDay3.bestStreakDays)
        assertFalse(summaryOnDay3.isTodayClean)
        assertEquals(StreakBreakReason.RECOVERY_SESSION, summaryOnDay3.lastBreakReason)

        // Day4 and Day5 are clean -> streak = 2
        val summaryOnDay5 = StatsRepository.getStreakSummary(context, today = day5)
        assertEquals(2, summaryOnDay5.currentStreakDays)
        assertEquals(3, summaryOnDay5.bestStreakDays)
        assertTrue(summaryOnDay5.isTodayClean)

        // Manual relapse on day5 breaks streak again
        StatsRepository.recordManualRelapse(context, note = "Opened browser", date = day5)
        val summaryAfterRelapse = StatsRepository.getStreakSummary(context, today = day5)
        assertEquals(0, summaryAfterRelapse.currentStreakDays)
        assertEquals(StreakBreakReason.MANUAL_RELAPSE, summaryAfterRelapse.lastBreakReason)
        assertEquals(1, StatsRepository.getRelapses(context).size)
        assertEquals("Opened browser", StatsRepository.getRelapses(context)[0].note)

        // Day4 had 0 blocks; verify time limit block does NOT break streak on day4
        StatsRepository.recordTimeLimitBlock(context, isReopening = false, date = day4)
        assertTrue(StatsRepository.getDailyStatsRange(context, days = 2, today = day4).last().isClean)
    }

    @Test
    fun `getDailyStatsRange merges live today usage and calculates saved time`() {
        val yesterday = LocalDate.of(2026, 11, 21)
        val today = LocalDate.of(2026, 11, 22)

        StatsRepository.updateDailyUsage(context, yesterday.toString(), 30 * 60_000L)
        StatsRepository.recordBlockEvent(
            context, BlockEventType.ACTIVITY, "com.example.social",
            details = "ShortsActivity", nowMillis = 1000L, date = yesterday
        )
        StatsRepository.recordTimeLimitBlock(context, isReopening = false, date = today)

        context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE).edit {
            putLong("com.example.social", 45 * 60_000L)
        }

        val range = StatsRepository.getDailyStatsRange(context, days = 2, today = today)
        assertEquals(2, range.size)
        assertEquals(30 * 60_000L, range[0].totalUsageMillis)
        assertEquals(1, range[0].activityBlocks)
        assertEquals(45 * 60_000L, range[1].totalUsageMillis)

        // 10 min from activity block + 15 min from time limit block = 25 min
        assertEquals(25 * 60_000L, StatsRepository.getTotalSavedMillis(context))
    }
}
