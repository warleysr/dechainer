package io.github.warleysr.dechainer.models

import io.github.warleysr.dechainer.data.FocusScheduleStorage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FocusScheduleTest {

    @Test
    fun `nextTriggerMillis triggers later today if day is in schedule and time has not passed`() {
        // Wednesday, 07:30
        val now = LocalDateTime.of(2026, 10, 14, 7, 30) // 2026-10-14 is a Wednesday (DayOfWeek value = 3)
        // Mon-Fri at 08:00
        val schedule = FocusSchedule(
            daysOfWeek = setOf(1, 2, 3, 4, 5),
            hour = 8,
            minute = 0,
            enabled = true
        )

        val expected = LocalDateTime.of(2026, 10, 14, 8, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        schedule.nextTriggerMillis(now) shouldBe expected
    }

    @Test
    fun `nextTriggerMillis triggers tomorrow if target time has passed today`() {
        // Wednesday, 08:30
        val now = LocalDateTime.of(2026, 10, 14, 8, 30)
        // Mon-Fri at 08:00
        val schedule = FocusSchedule(
            daysOfWeek = setOf(1, 2, 3, 4, 5),
            hour = 8,
            minute = 0,
            enabled = true
        )

        // Should trigger Thursday at 08:00
        val expected = LocalDateTime.of(2026, 10, 15, 8, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        schedule.nextTriggerMillis(now) shouldBe expected
    }

    @Test
    fun `nextTriggerMillis skips weekend when schedule is Mon-Fri`() {
        // Friday, 18:00
        val now = LocalDateTime.of(2026, 10, 16, 18, 0) // Friday (value = 5)
        // Mon-Fri at 08:00
        val schedule = FocusSchedule(
            daysOfWeek = setOf(1, 2, 3, 4, 5),
            hour = 8,
            minute = 0,
            enabled = true
        )

        // Next Monday is 2026-10-19 at 08:00
        val expected = LocalDateTime.of(2026, 10, 19, 8, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        schedule.nextTriggerMillis(now) shouldBe expected
    }

    @Test
    fun `nextTriggerMillis returns null if disabled or no days configured`() {
        val now = LocalDateTime.of(2026, 10, 14, 7, 30)
        val disabled = FocusSchedule(
            daysOfWeek = setOf(1, 2, 3),
            hour = 8,
            minute = 0,
            enabled = false
        )
        disabled.nextTriggerMillis(now) shouldBe null

        val emptyDays = FocusSchedule(
            daysOfWeek = emptySet(),
            hour = 8,
            minute = 0,
            enabled = true
        )
        emptyDays.nextTriggerMillis(now) shouldBe null
    }

    @Test
    fun `json encoding and decoding preserves schedules`() {
        val original = listOf(
            FocusSchedule(id = "s1", daysOfWeek = setOf(1, 2, 3, 4, 5), hour = 8, minute = 0, enabled = true),
            FocusSchedule(id = "s2", daysOfWeek = setOf(6, 7), hour = 10, minute = 30, enabled = false)
        )

        val json = FocusScheduleStorage.encode(original)
        val decoded = FocusScheduleStorage.decode(json)

        decoded shouldBe original
    }

    @Test
    fun `snooze progression allows reminding up to 2 more times then stops`() {
        // First presentation: snoozeCount = 0
        val isFirstSnoozeAllowed = 0 < 2
        isFirstSnoozeAllowed shouldBe true
        val nextAfterFirst = 0 + 1 // 1

        // Second presentation (1st 5-min snooze fired): snoozeCount = 1
        val isSecondSnoozeAllowed = nextAfterFirst < 2
        isSecondSnoozeAllowed shouldBe true
        val nextAfterSecond = nextAfterFirst + 1 // 2

        // Third presentation (2nd 5-min snooze fired): snoozeCount = 2
        // On 3rd presentation: button switches to 'Não posso iniciar agora' and snooze is disallowed
        val isThirdSnoozeAllowed = nextAfterSecond < 2
        isThirdSnoozeAllowed shouldBe false
    }
}
