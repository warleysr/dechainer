package io.github.warleysr.dechainer.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.activities.MainActivity
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.utils.LocaleUtils
import timber.log.Timber
import java.util.Locale

object FocusNotifier {
    private const val CHANNEL_TIMER = "focus_timer"
    private const val CHANNEL_ALERT = "focus_alert"

    const val STATUS_ID = 0x0F0C05

    private const val SOUND_MAX_MS = 5_000L

    private val vibrationPattern = longArrayOf(0, 300, 200, 300)

    private fun localized(context: Context): Context {
        val locale = Locale.forLanguageTag(LocaleUtils.getLocale(context))
        val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(config)
    }

    private fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val localizedContext = localized(context)
        if (manager.getNotificationChannel(CHANNEL_TIMER) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_TIMER, localizedContext.getString(R.string.focus_channel_timer), NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
        // The phase-end alert notification is gone; drop its channel from earlier versions.
        manager.deleteNotificationChannel(CHANNEL_ALERT)
    }

    private fun contentIntent(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun actionIntent(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, FocusReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun phaseTitle(context: Context, status: FocusMode.Status): String = when (status.phase) {
        FocusMode.Phase.FOCUS -> context.getString(
            R.string.focus_phase_focus_count,
            (status.completedFocus + 1).coerceAtMost(status.cyclesBeforeLongBreak),
            status.cyclesBeforeLongBreak
        )
        FocusMode.Phase.SHORT_BREAK -> context.getString(R.string.focus_phase_short_break)
        FocusMode.Phase.LONG_BREAK -> context.getString(R.string.focus_phase_long_break)
        FocusMode.Phase.WAITING -> context.getString(R.string.focus_phase_waiting)
    }

    /** Also makes sure [FocusService] is running to keep the progress bar moving. */
    fun showStatus(context: Context) {
        if (!FocusMode.isActive(context)) return
        updateStatus(context)
        FocusService.ensureRunning(context)
    }

    /** For a service start that finds no session: it still has to go foreground before stopping. */
    fun buildPlaceholder(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setContentTitle(localized(context).getString(R.string.focus_mode))
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateStatus(context: Context) {
        val notification = buildStatus(context) ?: return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            Timber.d("Focus mode: notifications not enabled, status not shown")
            return
        }
        try {
            manager.notify(STATUS_ID, notification)
        } catch (e: SecurityException) {
            Timber.w(e, "Focus mode: could not post status notification")
        }
    }

    fun buildStatus(context: Context): Notification? {
        val status = FocusMode.getStatus(context) ?: return null
        ensureChannels(context)
        val localizedContext = localized(context)

        val title = phaseTitle(localizedContext, status).let {
            if (status.paused) localizedContext.getString(R.string.focus_phase_paused, it) else it
        }
        val message = when {
            status.phase == FocusMode.Phase.WAITING -> localizedContext.getString(R.string.focus_waiting_text)
            status.paused -> localizedContext.getString(R.string.focus_paused_text)
            status.phase == FocusMode.Phase.FOCUS -> {
                val apps = FocusMode.getActiveSuspension(context).size
                if (apps > 0) localizedContext.resources.getQuantityString(R.plurals.focus_apps_suspended, apps, apps)
                else localizedContext.getString(R.string.focus_focus_text)
            }
            else -> localizedContext.getString(R.string.focus_break_text)
        }

        // A custom view so the bar is the same green as the usage warning's, whatever the system accent.
        val views = RemoteViews(context.packageName, R.layout.notification_focus).apply {
            setTextViewText(R.id.focus_title, title)
            setTextViewText(R.id.focus_message, message)
            if (status.phase == FocusMode.Phase.WAITING) {
                setViewVisibility(R.id.focus_timer, View.GONE)
                setViewVisibility(R.id.focus_progress, View.GONE)
            } else {
                setChronometer(
                    R.id.focus_timer, SystemClock.elapsedRealtime() + status.remainingMillis, null, !status.paused
                )
                setChronometerCountDown(R.id.focus_timer, true)
                val durationSeconds = (status.phaseDurationMillis / 1000).toInt().coerceAtLeast(1)
                val elapsedSeconds = ((status.phaseDurationMillis - status.remainingMillis) / 1000).toInt()
                setProgressBar(R.id.focus_progress, durationSeconds, elapsedSeconds.coerceIn(0, durationSeconds), false)
            }
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification_focus)
            .setColor(ContextCompat.getColor(context, R.color.usage_warning_green))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setContentTitle(title)
            .setContentText(message)
            .setShowWhen(false)
            .setContentIntent(contentIntent(context))
            // Swiping it away (possible from Android 14 unless we're the device owner) just brings it back.
            .setDeleteIntent(actionIntent(context, FocusReceiver.ACTION_REPOST))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Only actions that need no recovery code.
        val isBreak = status.phase == FocusMode.Phase.SHORT_BREAK || status.phase == FocusMode.Phase.LONG_BREAK
        if (status.phase == FocusMode.Phase.WAITING) {
            builder
                .addAction(0, localizedContext.getString(R.string.focus_start), actionIntent(context, FocusReceiver.ACTION_START_FOCUS))
                .addAction(0, localizedContext.getString(R.string.focus_end_session), actionIntent(context, FocusReceiver.ACTION_STOP))
        }
        if (status.paused) {
            builder.addAction(0, localizedContext.getString(R.string.focus_resume), actionIntent(context, FocusReceiver.ACTION_RESUME))
        }
        if (isBreak) {
            builder.addAction(0, localizedContext.getString(R.string.focus_skip_break), actionIntent(context, FocusReceiver.ACTION_SKIP_BREAK))
        }

        return builder.build()
    }

    /** Sound and vibration only: the status notification itself moves on to the next phase's countdown. */
    fun alertPhaseEnded(context: Context) {
        if (FocusMode.isSoundEnabled(context)) playSound(context)
        if (FocusMode.isVibrationEnabled(context)) vibrate(context)
    }

    fun cancel(context: Context) {
        FocusService.stop(context)
        NotificationManagerCompat.from(context).cancel(STATUS_ID)
    }

    private fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        try {
            vibrator.vibrate(VibrationEffect.createWaveform(vibrationPattern, -1))
        } catch (e: Exception) {
            Timber.w(e, "Focus mode: could not vibrate")
        }
    }

    private fun playSound(context: Context) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
        ringtone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        try {
            ringtone.play()
            // Guards against a user-chosen default sound that's a whole song.
            Handler(Looper.getMainLooper()).postDelayed({ ringtone.stop() }, SOUND_MAX_MS)
        } catch (e: Exception) {
            Timber.w(e, "Focus mode: could not play alert sound")
        }
    }

    const val SOUND_KEEP_ALIVE_MS = SOUND_MAX_MS
}
