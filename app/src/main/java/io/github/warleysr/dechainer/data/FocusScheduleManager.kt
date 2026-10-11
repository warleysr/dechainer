package io.github.warleysr.dechainer.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.warleysr.dechainer.activities.FocusReminderActivity
import io.github.warleysr.dechainer.notifications.FocusReminderNotifier
import io.github.warleysr.dechainer.notifications.FocusScheduleReceiver
import timber.log.Timber

object FocusScheduleManager {
    private const val SCHEDULE_ALARM_REQUEST_CODE = 0x0F0C10
    private const val SNOOZE_ALARM_REQUEST_CODE = 0x0F0C11
    const val SNOOZE_DELAY_MILLIS = 5 * 60 * 1000L // 5 minutes

    fun reschedule(context: Context) {
        val schedules = FocusScheduleStorage.getSchedules(context)
        val enabled = schedules.filter { it.enabled }
        val nextTrigger = enabled.mapNotNull { it.nextTriggerMillis() }.minOrNull()

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = schedulePendingIntent(context)

        if (nextTrigger != null) {
            Timber.d("Next focus schedule trigger at: $nextTrigger")
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pendingIntent)
            }
        } else {
            Timber.d("No enabled focus schedules, cancelling alarm")
            alarmManager.cancel(pendingIntent)
        }
    }

    fun scheduleSnooze(context: Context, snoozeCount: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = System.currentTimeMillis() + SNOOZE_DELAY_MILLIS
        val pendingIntent = snoozePendingIntent(context, snoozeCount)

        Timber.d("Scheduling focus snooze #$snoozeCount for 5 min from now")
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancelSnooze(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(snoozePendingIntent(context, 0))
    }

    fun launchReminder(context: Context, snoozeCount: Int) {
        val activityIntent = Intent(context, FocusReminderActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(FocusReminderActivity.EXTRA_SNOOZE_COUNT, snoozeCount)
        }

        // Post high-priority notification with full-screen intent so it works reliably on lock screen
        FocusReminderNotifier.showReminder(context, activityIntent, snoozeCount)

        try {
            context.startActivity(activityIntent)
        } catch (e: Exception) {
            Timber.w(e, "Could not start FocusReminderActivity directly from background")
        }
    }

    private fun schedulePendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            SCHEDULE_ALARM_REQUEST_CODE,
            Intent(context, FocusScheduleReceiver::class.java).setAction(FocusScheduleReceiver.ACTION_SCHEDULE_TRIGGER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun snoozePendingIntent(context: Context, snoozeCount: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            SNOOZE_ALARM_REQUEST_CODE,
            Intent(context, FocusScheduleReceiver::class.java).apply {
                action = FocusScheduleReceiver.ACTION_SNOOZE_TRIGGER
                putExtra(FocusScheduleReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}
