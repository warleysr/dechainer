package io.github.warleysr.dechainer.data

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import io.github.warleysr.dechainer.notifications.FocusNotifier
import io.github.warleysr.dechainer.notifications.FocusReceiver
import io.github.warleysr.dechainer.security.SecurityManager
import timber.log.Timber

/**
 * Pomodoro-style focus mode. Every entry point (alarm, reboot, notification action, process start)
 * calls [sync], which is idempotent. Deadlines use elapsed realtime while the device stays up and
 * the wall clock only across a reboot, so moving the clock can't shorten a phase.
 */
object FocusMode {
    const val PREFS_NAME = "focus_prefs"

    const val FOCUS_MIN_MINUTES = 5
    const val FOCUS_MAX_MINUTES = 120
    const val FOCUS_DEFAULT_MINUTES = 25

    const val SHORT_BREAK_MIN_MINUTES = 1
    const val SHORT_BREAK_MAX_MINUTES = 30
    const val SHORT_BREAK_DEFAULT_MINUTES = 5

    const val LONG_BREAK_MIN_MINUTES = 5
    const val LONG_BREAK_MAX_MINUTES = 60
    const val LONG_BREAK_DEFAULT_MINUTES = 15

    const val CYCLES_MIN = 2
    const val CYCLES_MAX = 8
    const val CYCLES_DEFAULT = 4

    enum class Phase {
        FOCUS,
        SHORT_BREAK,
        LONG_BREAK,

        /** A break ran out without auto-start on: nothing is suspended until the next focus is started. */
        WAITING
    }

    data class Status(
        val phase: Phase,
        val paused: Boolean,
        val releasedApps: Boolean,
        val remainingMillis: Long,
        val phaseDurationMillis: Long,
        val completedFocus: Int,
        val cyclesBeforeLongBreak: Int
    )

    private const val KEY_FOCUS_MINUTES = "focus_minutes"
    private const val KEY_SHORT_BREAK_MINUTES = "short_break_minutes"
    private const val KEY_LONG_BREAK_MINUTES = "long_break_minutes"
    private const val KEY_CYCLES = "cycles_before_long_break"
    private const val KEY_AUTO_START_FOCUS = "auto_start_focus"
    private const val KEY_SOUND_ENABLED = "sound_enabled"
    private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
    private const val KEY_APPS = "suspended_apps"

    private const val KEY_PHASE = "phase"
    private const val KEY_PHASE_START_RTC = "phase_start_rtc"
    private const val KEY_PHASE_START_ELAPSED = "phase_start_elapsed"
    private const val KEY_PHASE_DURATION = "phase_duration"
    private const val KEY_PHASE_BOOT_COUNT = "phase_boot_count"
    private const val KEY_PAUSED_REMAINING = "paused_remaining"
    private const val KEY_PAUSE_RELEASES = "pause_releases"
    private const val KEY_COMPLETED_FOCUS = "completed_focus"
    private const val KEY_ACTIVE_SUSPENSION = "active_suspension"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getFocusMinutes(context: Context) = prefs(context)
        .getInt(KEY_FOCUS_MINUTES, FOCUS_DEFAULT_MINUTES).coerceIn(FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES)

    fun setFocusMinutes(context: Context, minutes: Int) = prefs(context).edit {
        putInt(KEY_FOCUS_MINUTES, minutes.coerceIn(FOCUS_MIN_MINUTES, FOCUS_MAX_MINUTES))
    }

    fun getShortBreakMinutes(context: Context) = prefs(context)
        .getInt(KEY_SHORT_BREAK_MINUTES, SHORT_BREAK_DEFAULT_MINUTES)
        .coerceIn(SHORT_BREAK_MIN_MINUTES, SHORT_BREAK_MAX_MINUTES)

    fun setShortBreakMinutes(context: Context, minutes: Int) = prefs(context).edit {
        putInt(KEY_SHORT_BREAK_MINUTES, minutes.coerceIn(SHORT_BREAK_MIN_MINUTES, SHORT_BREAK_MAX_MINUTES))
    }

    fun getLongBreakMinutes(context: Context) = prefs(context)
        .getInt(KEY_LONG_BREAK_MINUTES, LONG_BREAK_DEFAULT_MINUTES)
        .coerceIn(LONG_BREAK_MIN_MINUTES, LONG_BREAK_MAX_MINUTES)

    fun setLongBreakMinutes(context: Context, minutes: Int) = prefs(context).edit {
        putInt(KEY_LONG_BREAK_MINUTES, minutes.coerceIn(LONG_BREAK_MIN_MINUTES, LONG_BREAK_MAX_MINUTES))
    }

    fun getCyclesBeforeLongBreak(context: Context) = prefs(context)
        .getInt(KEY_CYCLES, CYCLES_DEFAULT).coerceIn(CYCLES_MIN, CYCLES_MAX)

    fun setCyclesBeforeLongBreak(context: Context, cycles: Int) {
        prefs(context).edit { putInt(KEY_CYCLES, cycles.coerceIn(CYCLES_MIN, CYCLES_MAX)) }
        FocusNotifier.showStatus(context)
    }

    fun isAutoStartFocus(context: Context) = prefs(context).getBoolean(KEY_AUTO_START_FOCUS, false)

    fun setAutoStartFocus(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_AUTO_START_FOCUS, enabled) }

    fun isSoundEnabled(context: Context) = prefs(context).getBoolean(KEY_SOUND_ENABLED, true)

    fun isVibrationEnabled(context: Context) = prefs(context).getBoolean(KEY_VIBRATION_ENABLED, true)

    fun setVibrationEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_VIBRATION_ENABLED, enabled) }

    fun setSoundEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit { putBoolean(KEY_SOUND_ENABLED, enabled) }

    fun getApps(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_APPS, emptySet()) ?: emptySet()

    fun setApps(context: Context, packages: Set<String>) {
        prefs(context).edit { putStringSet(KEY_APPS, packages) }
        if (isEnforcing(context)) {
            applySuspension(context, enforce = true)
            FocusNotifier.showStatus(context)
        }
    }

    fun isActive(context: Context) = currentPhase(context) != null

    private fun currentPhase(context: Context): Phase? = prefs(context).getString(KEY_PHASE, null)
        ?.let { runCatching { Phase.valueOf(it) }.getOrNull() }

    private fun isPaused(context: Context) = prefs(context).contains(KEY_PAUSED_REMAINING)

    private fun isTicking(context: Context): Boolean {
        val phase = currentPhase(context) ?: return false
        return phase != Phase.WAITING && !isPaused(context)
    }

    private fun isReleasedPause(context: Context) = prefs(context).getBoolean(KEY_PAUSE_RELEASES, false)

    private fun isEnforcing(context: Context) =
        currentPhase(context) == Phase.FOCUS && !(isPaused(context) && isReleasedPause(context))

    fun getStatus(context: Context): Status? {
        val phase = currentPhase(context) ?: return null
        val p = prefs(context)
        val paused = isPaused(context)
        return Status(
            phase = phase,
            paused = paused,
            releasedApps = paused && isReleasedPause(context),
            remainingMillis = when {
                phase == Phase.WAITING -> 0L
                paused -> p.getLong(KEY_PAUSED_REMAINING, 0L)
                else -> remainingMillis(context).coerceAtLeast(0L)
            },
            phaseDurationMillis = p.getLong(KEY_PHASE_DURATION, 0L),
            completedFocus = p.getInt(KEY_COMPLETED_FOCUS, 0),
            cyclesBeforeLongBreak = getCyclesBeforeLongBreak(context)
        )
    }

    /** What was actually suspended — releasing acts on this, since the configured list may have changed. */
    fun getActiveSuspension(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_ACTIVE_SUSPENSION, emptySet()) ?: emptySet()

    fun isHolding(context: Context, pkg: String) = pkg in getActiveSuspension(context)

    fun startFocus(context: Context) {
        if (!isActive(context)) prefs(context).edit { putInt(KEY_COMPLETED_FOCUS, 0) }
        grantNotificationPermission(context)
        enterPhase(context, Phase.FOCUS)
    }

    fun skipBreak(context: Context) {
        val phase = currentPhase(context)
        if (phase != Phase.SHORT_BREAK && phase != Phase.LONG_BREAK) return
        if (phase == Phase.LONG_BREAK) prefs(context).edit { putInt(KEY_COMPLETED_FOCUS, 0) }
        enterPhase(context, Phase.FOCUS)
    }

    /** With [releaseApps] the apps come back too, so callers gate that one behind the recovery code. */
    fun pause(context: Context, releaseApps: Boolean) {
        if (!isTicking(context)) return
        prefs(context).edit {
            putLong(KEY_PAUSED_REMAINING, remainingMillis(context).coerceAtLeast(0L))
            putBoolean(KEY_PAUSE_RELEASES, releaseApps)
        }
        sync(context)
    }

    fun resume(context: Context) {
        val p = prefs(context)
        if (!p.contains(KEY_PAUSED_REMAINING)) return
        val remaining = p.getLong(KEY_PAUSED_REMAINING, 0L)
        val elapsedSoFar = p.getLong(KEY_PHASE_DURATION, 0L) - remaining
        // Re-anchor the start so the phase ends `remaining` from now and the progress picks up where it was.
        p.edit {
            remove(KEY_PAUSED_REMAINING)
            remove(KEY_PAUSE_RELEASES)
            putLong(KEY_PHASE_START_RTC, System.currentTimeMillis() - elapsedSoFar)
            putLong(KEY_PHASE_START_ELAPSED, SystemClock.elapsedRealtime() - elapsedSoFar)
            putInt(KEY_PHASE_BOOT_COUNT, bootCount(context))
        }
        sync(context)
    }

    /** Outside [Phase.WAITING] this loosens the session, so callers gate it behind the recovery code. */
    fun stop(context: Context) {
        prefs(context).edit {
            remove(KEY_PHASE)
            remove(KEY_PHASE_START_RTC)
            remove(KEY_PHASE_START_ELAPSED)
            remove(KEY_PHASE_DURATION)
            remove(KEY_PHASE_BOOT_COUNT)
            remove(KEY_PAUSED_REMAINING)
            remove(KEY_PAUSE_RELEASES)
            remove(KEY_COMPLETED_FOCUS)
        }
        releaseSuspension(context)
        cancelAlarm(context)
        FocusNotifier.cancel(context)
    }

    /** Advances a phase that ran out (with the alert), then re-applies suspension, alarm and notification. */
    fun sync(context: Context) {
        val ctx = context.applicationContext
        var phase = currentPhase(ctx) ?: run {
            if (getActiveSuspension(ctx).isNotEmpty()) releaseSuspension(ctx)
            return
        }

        if (isTicking(ctx) && remainingMillis(ctx) <= 0L) {
            val finished = phase
            phase = nextPhase(ctx, finished)
            Timber.d("Focus mode: $finished ran out, moving to $phase")
            startPhase(ctx, phase)
            FocusNotifier.alertPhaseEnded(ctx)
        }

        applyState(ctx)
    }

    fun syncIfDue(context: Context) {
        if (isTicking(context) && remainingMillis(context) <= 0L) sync(context)
    }

    private fun enterPhase(context: Context, phase: Phase) {
        val ctx = context.applicationContext
        startPhase(ctx, phase)
        applyState(ctx)
    }

    private fun applyState(context: Context) {
        applySuspension(context, enforce = isEnforcing(context))
        scheduleAlarm(context)
        FocusNotifier.showStatus(context)
    }

    private fun nextPhase(context: Context, finished: Phase): Phase {
        val completed = prefs(context).getInt(KEY_COMPLETED_FOCUS, 0)
        val (next, newCompleted) = FocusCycle.next(
            finished = finished,
            completedFocus = completed,
            cyclesBeforeLongBreak = getCyclesBeforeLongBreak(context),
            autoStartFocus = isAutoStartFocus(context)
        )
        prefs(context).edit { putInt(KEY_COMPLETED_FOCUS, newCompleted) }
        return next
    }

    private fun startPhase(context: Context, phase: Phase) {
        val minutes = when (phase) {
            Phase.FOCUS -> getFocusMinutes(context)
            Phase.SHORT_BREAK -> getShortBreakMinutes(context)
            Phase.LONG_BREAK -> getLongBreakMinutes(context)
            Phase.WAITING -> 0
        }
        prefs(context).edit {
            remove(KEY_PAUSED_REMAINING)
            remove(KEY_PAUSE_RELEASES)
            putString(KEY_PHASE, phase.name)
            putLong(KEY_PHASE_START_RTC, System.currentTimeMillis())
            putLong(KEY_PHASE_START_ELAPSED, SystemClock.elapsedRealtime())
            putLong(KEY_PHASE_DURATION, minutes * 60_000L)
            putInt(KEY_PHASE_BOOT_COUNT, bootCount(context))
        }
    }

    // After a reboot only the wall clock is left; re-anchoring on the new boot hands back to elapsed realtime.
    private fun remainingMillis(context: Context): Long {
        val p = prefs(context)
        val duration = p.getLong(KEY_PHASE_DURATION, 0L)
        val startElapsed = p.getLong(KEY_PHASE_START_ELAPSED, 0L)
        val nowElapsed = SystemClock.elapsedRealtime()

        if (p.getInt(KEY_PHASE_BOOT_COUNT, -1) == bootCount(context)) {
            return startElapsed + duration - nowElapsed
        }

        val remaining = p.getLong(KEY_PHASE_START_RTC, 0L) + duration - System.currentTimeMillis()
        p.edit {
            putLong(KEY_PHASE_START_ELAPSED, nowElapsed + remaining - duration)
            putInt(KEY_PHASE_BOOT_COUNT, bootCount(context))
        }
        return remaining
    }

    private fun bootCount(context: Context): Int =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    // An app already suspended by something untracked (e.g. the user) is left unclaimed, so ending focus won't release it.
    private fun applySuspension(context: Context, enforce: Boolean) {
        if (!enforce) {
            releaseSuspension(context)
            return
        }

        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (!dpm.isAdminActive(admin)) return

        val targets = getApps(context)
        val claimed = getActiveSuspension(context)

        val dropped = claimed - targets
        if (dropped.isNotEmpty()) release(context, dropped)

        val toClaim = targets.filter { pkg ->
            pkg in claimed || !isSuspended(dpm, pkg) || isTrackedElsewhere(context, pkg)
        }
        val failed = if (toClaim.isEmpty()) emptyArray()
        else dpm.setPackagesSuspended(admin, toClaim.toTypedArray(), true)

        val nowClaimed = toClaim.toSet() - failed.toSet()
        prefs(context).edit { putStringSet(KEY_ACTIVE_SUSPENSION, nowClaimed) }
        if (failed.isNotEmpty()) Timber.w("Focus mode: could not suspend ${failed.joinToString()}")
    }

    private fun releaseSuspension(context: Context) {
        val claimed = getActiveSuspension(context)
        prefs(context).edit { remove(KEY_ACTIVE_SUSPENSION) }
        if (claimed.isNotEmpty()) release(context, claimed)
    }

    // Leaves out what an impulse block or visual blocking still holds; their own timers lift those.
    private fun release(context: Context, packages: Set<String>) {
        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (!dpm.isAdminActive(admin)) return
        val releasable = packages.filterNot { isTrackedElsewhere(context, it) }
        if (releasable.isNotEmpty()) dpm.setPackagesSuspended(admin, releasable.toTypedArray(), false)
    }

    private fun isTrackedElsewhere(context: Context, pkg: String) =
        pkg in SecurityManager.getActiveImpulseSuspension(context) ||
            VisualBlockingSuspensionTracker(context).isSuspended(pkg)

    private fun isSuspended(dpm: DevicePolicyManager, pkg: String) = try {
        dpm.isPackageSuspended(DeviceAdmin.component, pkg)
    } catch (_: Exception) {
        false
    }

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, FocusReceiver::class.java).setAction(FocusReceiver.ACTION_PHASE_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleAlarm(context: Context) {
        if (!isTicking(context)) {
            cancelAlarm(context)
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = SystemClock.elapsedRealtime() + remainingMillis(context).coerceAtLeast(0L)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent(context))
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, alarmIntent(context))
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }

    // Resetting to the default policy after granting keeps the permission granted but still revocable.
    private fun grantNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dpm = DeviceAdmin.policyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        runCatching {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                return
            }
            listOf(
                DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            ).forEach { state ->
                dpm.setPermissionGrantState(
                    DeviceAdmin.component, context.packageName, Manifest.permission.POST_NOTIFICATIONS, state
                )
            }
        }.onFailure { Timber.w(it, "Focus mode: could not grant notification permission") }
    }
}

object FocusCycle {
    fun next(
        finished: FocusMode.Phase,
        completedFocus: Int,
        cyclesBeforeLongBreak: Int,
        autoStartFocus: Boolean
    ): Pair<FocusMode.Phase, Int> = when (finished) {
        FocusMode.Phase.FOCUS -> {
            val completed = completedFocus + 1
            if (completed >= cyclesBeforeLongBreak) FocusMode.Phase.LONG_BREAK to completed
            else FocusMode.Phase.SHORT_BREAK to completed
        }
        FocusMode.Phase.SHORT_BREAK ->
            (if (autoStartFocus) FocusMode.Phase.FOCUS else FocusMode.Phase.WAITING) to completedFocus
        FocusMode.Phase.LONG_BREAK ->
            (if (autoStartFocus) FocusMode.Phase.FOCUS else FocusMode.Phase.WAITING) to 0
        FocusMode.Phase.WAITING -> FocusMode.Phase.WAITING to completedFocus
    }
}
