package io.github.warleysr.dechainer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.concurrent.TimeUnit

/**
 * Bookkeeping for the "suspend app" escalation of visual blocking (see [VisualBlockingSettings]):
 * counts how many times each package has been blocked inside the configured sliding window, and
 * remembers until when a package is currently suspended.
 *
 * Both are persisted because the accessibility service can be killed and recreated at any moment,
 * and a suspension that outlived the process with nothing left to lift it would leave the app
 * suspended forever — [activeSuspensions] is what
 * [io.github.warleysr.dechainer.DechainerAccessibilityService] uses on reconnect to lift or
 * re-schedule them.
 *
 * Deadlines are wall-clock ([System.currentTimeMillis]) rather than uptime-based, so they survive
 * a reboot; the trade-off is that moving the system clock backwards extends a suspension, which is
 * the safe direction to fail for this feature.
 */
class VisualBlockingSuspensionTracker(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Records one block for [pkg] and reports whether that was the [requiredHits]-th one inside
     * the last [windowMinutes] minutes — i.e. whether the app should now be suspended. The
     * counter is reset as soon as it fires, so the next suspension needs a fresh full window of
     * blocks instead of re-triggering on every subsequent detection.
     */
    fun registerBlock(
        pkg: String,
        requiredHits: Int,
        windowMinutes: Int,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        val hitsNeeded = requiredHits.coerceAtLeast(1)
        val windowMillis = TimeUnit.MINUTES.toMillis(windowMinutes.coerceAtLeast(1).toLong())

        val hits = readHits(pkg)
            .filter { now - it in 0..windowMillis }
            .plus(now)
            // Only the most recent hitsNeeded timestamps can ever matter; anything older can no
            // longer complete a window before it expires, so it's dropped instead of accumulating.
            .takeLast(hitsNeeded)

        if (hits.size >= hitsNeeded) {
            clearBlocks(pkg)
            return true
        }
        prefs.edit { putString(KEY_HITS_PREFIX + pkg, hits.joinToString(",")) }
        return false
    }

    fun clearBlocks(pkg: String) = prefs.edit { remove(KEY_HITS_PREFIX + pkg) }

    /**
     * Marks [pkg] as suspended for [durationMinutes] and returns the wall-clock deadline. The
     * block counter is dropped as part of starting the suspension, so a suspension can never
     * begin with hits left over from the window that triggered it.
     */
    fun startSuspension(
        pkg: String,
        durationMinutes: Int,
        now: Long = System.currentTimeMillis()
    ): Long {
        val until = now + TimeUnit.MINUTES.toMillis(durationMinutes.coerceAtLeast(1).toLong())
        prefs.edit {
            putLong(KEY_UNTIL_PREFIX + pkg, until)
            remove(KEY_HITS_PREFIX + pkg)
        }
        return until
    }

    /**
     * Ends [pkg]'s suspension *and* wipes its block counter, so the app comes back needing a full
     * new window of blocks. Clearing here rather than only in [registerBlock] is what keeps
     * anything recorded between the suspension starting and it being lifted from carrying over
     * into the next window.
     */
    fun endSuspension(pkg: String) = prefs.edit {
        remove(KEY_UNTIL_PREFIX + pkg)
        remove(KEY_HITS_PREFIX + pkg)
    }

    fun isSuspended(pkg: String, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getLong(KEY_UNTIL_PREFIX + pkg, 0L) > now

    /** Every package with a recorded suspension, mapped to its deadline (expired ones included). */
    fun activeSuspensions(): Map<String, Long> {
        val suspensions = mutableMapOf<String, Long>()
        prefs.all.forEach { (key, value) ->
            if (!key.startsWith(KEY_UNTIL_PREFIX)) return@forEach
            val until = value as? Long ?: return@forEach
            suspensions[key.removePrefix(KEY_UNTIL_PREFIX)] = until
        }
        return suspensions
    }

    private fun readHits(pkg: String): List<Long> =
        prefs.getString(KEY_HITS_PREFIX + pkg, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    companion object {
        const val PREFS_NAME = "visual_blocking_suspensions"
        private const val KEY_HITS_PREFIX = "hits_"
        private const val KEY_UNTIL_PREFIX = "until_"
    }
}
