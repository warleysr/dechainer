package io.github.warleysr.dechainer.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.FocusMode
import timber.log.Timber

object FocusReminderNotifier {
    private const val CHANNEL_REMINDER = "focus_schedule_reminder"
    const val NOTIFICATION_ID = 0x0F0C20

    private val vibrationPattern = longArrayOf(0, 400, 200, 400)

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_REMINDER) == null) {
            val channel = NotificationChannel(
                CHANNEL_REMINDER,
                context.getString(R.string.focus_schedule_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.focus_reminder_desc)
                enableVibration(true)
                vibrationPattern = FocusReminderNotifier.vibrationPattern
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminder(context: Context, activityIntent: Intent, snoozeCount: Int) {
        ensureChannel(context)

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setColor(ContextCompat.getColor(context, R.color.usage_warning_green))
            .setContentTitle(context.getString(R.string.focus_reminder_title))
            .setContentText(context.getString(R.string.focus_reminder_desc))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Could not post focus reminder notification")
        }

        if (FocusMode.isVibrationEnabled(context)) vibrate(context)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
        } catch (e: Exception) {
            Timber.w(e, "Could not vibrate for focus reminder")
        }
    }
}
