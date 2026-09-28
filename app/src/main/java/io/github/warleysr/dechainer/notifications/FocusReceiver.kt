package io.github.warleysr.dechainer.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import io.github.warleysr.dechainer.data.FocusMode

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PHASE_END -> {
                // Keeps the process alive long enough for the alert sound to finish playing.
                val pending = goAsync()
                FocusMode.sync(context)
                Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, FocusNotifier.SOUND_KEEP_ALIVE_MS)
            }
            ACTION_START_FOCUS -> if (FocusMode.getStatus(context)?.phase == FocusMode.Phase.WAITING) {
                FocusMode.startFocus(context)
            }
            ACTION_SKIP_BREAK -> FocusMode.skipBreak(context)
            ACTION_RESUME -> FocusMode.resume(context)
            // Anywhere but WAITING, ending needs the recovery code.
            ACTION_STOP -> if (FocusMode.getStatus(context)?.phase == FocusMode.Phase.WAITING) {
                FocusMode.stop(context)
            }
            ACTION_REPOST -> FocusMode.sync(context)
        }
    }

    companion object {
        const val ACTION_PHASE_END = "io.github.warleysr.dechainer.focus.PHASE_END"
        const val ACTION_START_FOCUS = "io.github.warleysr.dechainer.focus.START_FOCUS"
        const val ACTION_SKIP_BREAK = "io.github.warleysr.dechainer.focus.SKIP_BREAK"
        const val ACTION_STOP = "io.github.warleysr.dechainer.focus.STOP"
        const val ACTION_RESUME = "io.github.warleysr.dechainer.focus.RESUME"
        const val ACTION_REPOST = "io.github.warleysr.dechainer.focus.REPOST"
    }
}

/** Re-arms focus mode after a reboot, an app update or a clock change. */
class FocusSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED ->
                FocusMode.sync(context)
        }
    }
}
