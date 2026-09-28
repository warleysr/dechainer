package io.github.warleysr.dechainer.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import io.github.warleysr.dechainer.data.FocusMode
import timber.log.Timber

/**
 * Re-posts the countdown notification so its progress bar moves, which, unlike the chronometer,
 * doesn't happen on its own. Phase changes are driven by the alarm in [FocusMode], not by this.
 */
class FocusService : Service() {
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            FocusMode.syncIfDue(this@FocusService)
            val status = FocusMode.getStatus(this@FocusService)
            if (status == null) {
                stopSelf()
                return
            }
            if (status.phase == FocusMode.Phase.WAITING || status.paused) {
                schedule(WAITING_TICK_MS)
                return
            }
            if (getSystemService(PowerManager::class.java)?.isInteractive != false) {
                FocusNotifier.updateStatus(this@FocusService)
            }
            schedule(tickInterval(status.phaseDurationMillis))
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() must be called for every startForegroundService(), even when the session
        // ended in between; skipping it crashes the app with ForegroundServiceDidNotStartInTimeException.
        val notification = FocusNotifier.buildStatus(this)
        try {
            ServiceCompat.startForeground(
                this, FocusNotifier.STATUS_ID, notification ?: FocusNotifier.buildPlaceholder(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            )
        } catch (e: Exception) {
            Timber.w(e, "Focus mode: could not go foreground")
            stopSelf()
            return START_NOT_STICKY
        }
        if (notification == null || heldForDebugInstall) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        restartTicking()
        return START_STICKY
    }

    private fun restartTicking() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    // Always dropping what's queued keeps a single tick pending, even when a tick's own sync restarts ticking.
    private fun schedule(delayMillis: Long) {
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, delayMillis)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val MIN_TICK_MS = 1_000L
        private const val WAITING_TICK_MS = 60_000L

        private const val BAR_STEPS = 300

        private var instance: FocusService? = null

        private fun tickInterval(phaseDurationMillis: Long) =
            (phaseDurationMillis / BAR_STEPS).coerceAtLeast(MIN_TICK_MS)

        private var heldForDebugInstall = false

        fun ensureRunning(context: Context) {
            if (heldForDebugInstall) return
            instance?.let {
                it.restartTicking()
                return
            }
            try {
                context.startForegroundService(Intent(context, FocusService::class.java))
            } catch (e: Exception) {
                Timber.w(e, "Focus mode: could not start the timer service")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FocusService::class.java))
        }

        /** A running foreground service blocks installing a debug build; it stays down until the install kills the process. */
        fun stopForDebugInstall(context: Context) {
            heldForDebugInstall = true
            stop(context)
        }
    }
}
