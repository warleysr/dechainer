package io.github.warleysr.dechainer.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.activities.MainActivity
import io.github.warleysr.dechainer.utils.LocaleUtils
import timber.log.Timber
import java.util.Locale

class AccessibilityKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification(this)
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            Timber.w(e, "Accessibility keep-alive: could not go foreground")
            stopSelf()
            return START_NOT_STICKY
        }

        if (heldForDebugInstall) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 0x0A11CE
        const val CHANNEL_ID = "accessibility_keep_alive"

        private var instance: AccessibilityKeepAliveService? = null
        private var heldForDebugInstall = false

        val isRunning: Boolean
            get() = instance != null

        private fun localized(context: Context): Context {
            val locale = Locale.forLanguageTag(LocaleUtils.getLocale(context))
            val config = Configuration(context.resources.configuration).apply { setLocale(locale) }
            return context.createConfigurationContext(config)
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return

            val localizedContext = localized(context)
            val channel = NotificationChannel(
                CHANNEL_ID,
                localizedContext.getString(R.string.keep_alive_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = localizedContext.getString(R.string.keep_alive_channel_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        fun buildNotification(context: Context): Notification {
            ensureChannel(context)
            val localizedContext = localized(context)

            val contentIntent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_keep_alive)
                .setContentTitle(localizedContext.getString(R.string.keep_alive_notification_title))
                .setContentText(localizedContext.getString(R.string.keep_alive_notification_text))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
        }

        fun start(context: Context) {
            if (heldForDebugInstall) return
            try {
                val intent = Intent(context, AccessibilityKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Timber.w(e, "Accessibility keep-alive: could not start service")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AccessibilityKeepAliveService::class.java))
            } catch (e: Exception) {
                Timber.w(e, "Accessibility keep-alive: could not stop service")
            }
        }

        fun stopForDebugInstall(context: Context) {
            heldForDebugInstall = true
            stop(context)
        }
    }
}
