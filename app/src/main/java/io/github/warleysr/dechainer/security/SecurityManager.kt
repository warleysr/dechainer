package io.github.warleysr.dechainer.security

import android.content.Context
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.SecureRandom
import androidx.core.content.edit

class SecurityManager {

    enum class ImpulseLockMode {
        OFF, NORMAL, HARD
    }

    /** What the "I'm having impulses" panic button does on top of locking Dechainer itself. */
    enum class ImpulseAction {
        /** Only the timer that already blocks access to Dechainer. */
        TIMER_ONLY,

        /** The timer, plus suspending a user-picked list of apps until it runs out. */
        TIMER_AND_SUSPEND
    }

    companion object {
        private const val CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

        const val IMPULSE_MIN_DURATION_MINUTES = 15
        const val IMPULSE_MAX_DURATION_MINUTES = 360
        const val IMPULSE_DEFAULT_DURATION_MINUTES = 60
        private val isRecoveryKeySet = mutableStateOf(false)
        
        var sessionEndTime by mutableLongStateOf(0L)
            private set

        fun isSessionActive(): Boolean = System.currentTimeMillis() < sessionEndTime

        private fun startSession() {
            sessionEndTime = System.currentTimeMillis() + (10 * 60 * 1000) // 10 minutes
        }

        fun endSession() {
            sessionEndTime = 0L
        }

        fun getImpulseLockMode(context: Context): ImpulseLockMode {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return ImpulseLockMode.valueOf(prefs.getString("impulse_lock_mode", ImpulseLockMode.OFF.name)!!)
        }

        fun setImpulseLockMode(context: Context, mode: ImpulseLockMode) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("impulse_lock_mode", mode.name) }
        }

        fun getImpulseAction(context: Context): ImpulseAction {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            val stored = prefs.getString("impulse_action", ImpulseAction.TIMER_ONLY.name)!!
            return runCatching { ImpulseAction.valueOf(stored) }.getOrDefault(ImpulseAction.TIMER_ONLY)
        }

        fun setImpulseAction(context: Context, action: ImpulseAction) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("impulse_action", action.name) }
        }

        /** Always inside [IMPULSE_MIN_DURATION_MINUTES]..[IMPULSE_MAX_DURATION_MINUTES]. */
        fun getImpulseDurationMinutes(context: Context): Int {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getInt("impulse_duration_minutes", IMPULSE_DEFAULT_DURATION_MINUTES)
                .coerceIn(IMPULSE_MIN_DURATION_MINUTES, IMPULSE_MAX_DURATION_MINUTES)
        }

        fun setImpulseDurationMinutes(context: Context, minutes: Int) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putInt(
                    "impulse_duration_minutes",
                    minutes.coerceIn(IMPULSE_MIN_DURATION_MINUTES, IMPULSE_MAX_DURATION_MINUTES)
                )
            }
        }

        /** Apps the user picked to be suspended while an impulse block is running. */
        fun getImpulseSuspendedApps(context: Context): Set<String> {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getStringSet("impulse_suspended_apps", emptySet()) ?: emptySet()
        }

        fun setImpulseSuspendedApps(context: Context, packages: Set<String>) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putStringSet("impulse_suspended_apps", packages) }
        }

        /**
         * Apps that are suspended *right now* because of an impulse block, as opposed to the list
         * the user configured — the two can differ if the configuration changes mid-block, and
         * releasing has to act on what was actually suspended.
         */
        fun getActiveImpulseSuspension(context: Context): Set<String> {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            return prefs.getStringSet("impulse_active_suspension", emptySet()) ?: emptySet()
        }

        fun setActiveImpulseSuspension(context: Context, packages: Set<String>) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { putStringSet("impulse_active_suspension", packages) }
        }

        fun clearActiveImpulseSuspension(context: Context) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            prefs.edit { remove("impulse_active_suspension") }
        }

        fun startImpulseBlock(context: Context) {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            val duration = getImpulseDurationMinutes(context) * 60 * 1000L
            prefs.edit {
                putLong("impulse_block_start_rtc", System.currentTimeMillis())
                putLong("impulse_block_start_elapsed", SystemClock.elapsedRealtime())
                putLong("impulse_block_duration", duration)
                putBoolean("impulse_block_active", true)
            }
        }

        fun getImpulseBlockRemainingTime(context: Context): Long {
            val prefs = context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("impulse_block_active", false)) return -1L

            val startRtc = prefs.getLong("impulse_block_start_rtc", 0L)
            val startElapsed = prefs.getLong("impulse_block_start_elapsed", 0L)
            val duration = prefs.getLong("impulse_block_duration", 0L)

            val nowRtc = System.currentTimeMillis()
            val nowElapsed = SystemClock.elapsedRealtime()

            // Resistance logic:
            // 1. If elapsed time says it's over, it's over.
            // 2. If RTC says it's over, but elapsed time says it's NOT, 
            //    it means the user moved the clock forward. Trust elapsed time.
            // 3. If elapsed time is LESS than startElapsed, a reboot happened.
            //    In this case, we have to trust RTC but cross-reference if possible.
            
            val remainingElapsed = (startElapsed + duration) - nowElapsed
            val remainingRtc = (startRtc + duration) - nowRtc

            val remaining = if (nowElapsed < startElapsed) {
                // Reboot occurred, fallback to RTC but ensure it didn't jump forward illegally
                // Actually, without a secure remote clock, we can only do so much.
                // But we can at least detect if they moved it backwards.
                remainingRtc
            } else {
                // No reboot, trust elapsed time as it's resistant to clock changes
                remainingElapsed
            }

            if (remaining <= 0) {
                prefs.edit { putBoolean("impulse_block_active", false) }
                return -1L
            }
            return remaining
        }

        fun generateRecoveryCode(length: Int = 16): String {
            val random = SecureRandom()
            return (1..length)
                .map { CHAR_POOL[random.nextInt(CHAR_POOL.length)] }
                .joinToString("")
        }

        fun getRecoveryCode(context: Context) : String? {
            return context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE).getString("recovery_code", null)
        }

        fun isRecoveryCodeSet(context: Context) : Boolean  {
            isRecoveryKeySet.value = getRecoveryCode(context) != null
            return isRecoveryKeySet.value
        }

        fun saveRecoveryCode(context: Context, code: String) {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("recovery_code", code) }
            isRecoveryKeySet.value = true
        }

        fun validateRecoveryCode(userInput: String, storedKey: String): Boolean {
            if (isSessionActive()) return true

            val success =  userInput == storedKey

            if (success)
                startSession()

            return success
        }

        fun startForcedRemoval(context: Context) {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean("forced_removal_active", true)
                putLong("forced_removal_accumulated", 0L)
                putLong("forced_removal_last_elapsed", SystemClock.elapsedRealtime())
            }
        }

        fun cancelForcedRemoval(context: Context) {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            prefs.edit {
                putBoolean("forced_removal_active", false)
            }
        }

        fun getForcedRemovalRemainingTime(context: Context): Long {
            val prefs = context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("forced_removal_active", false)) return -1L

            var accumulated = prefs.getLong("forced_removal_accumulated", 0L)
            val lastElapsed = prefs.getLong("forced_removal_last_elapsed", 0L)
            val now = SystemClock.elapsedRealtime()

            val diff = if (now >= lastElapsed) now - lastElapsed else now
            accumulated += diff

            prefs.edit {
                putLong("forced_removal_accumulated", accumulated)
                putLong("forced_removal_last_elapsed", now)
            }

            val target = 48L * 60 * 60 * 1000
            return (target - accumulated).coerceAtLeast(0L)
        }

    }
}
