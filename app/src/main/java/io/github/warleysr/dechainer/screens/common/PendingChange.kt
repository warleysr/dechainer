package io.github.warleysr.dechainer.screens.common

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import io.github.warleysr.dechainer.models.TimeLimit
import io.github.warleysr.dechainer.models.TimeWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek

/** A new restriction only takes effect after a few seconds in which the user can still undo it. */
object PendingChange {
    const val UNDO_WINDOW_MS = 5000L

    /** Hosted by the main Scaffold. */
    val snackbarHost = SnackbarHostState()

    // App-wide scope: the screen that started the change may be gone before the delay ends.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun schedule(message: String, undoLabel: String, onUndo: (() -> Unit)? = null, action: () -> Unit) {
        // Dismissing a showing snackbar commits its change right away, so changes never overlap.
        snackbarHost.currentSnackbarData?.dismiss()
        scope.launch {
            val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                snackbarHost.showSnackbar(message, undoLabel, duration = SnackbarDuration.Indefinite)
            }
            if (result == SnackbarResult.ActionPerformed) onUndo?.invoke() else action()
        }
    }
}

// 0 means "no limit".
private fun cap(minutes: Int) = if (minutes == 0) Int.MAX_VALUE else minutes

fun isLooserLimit(old: TimeLimit, new: TimeLimit) =
    DayOfWeek.entries.any { cap(new.minutesFor(it)) > cap(old.minutesFor(it)) }

/** Windows are the *allowed* periods and an empty list means always allowed: loosening is allowing a minute that wasn't. */
fun isLooserWindows(old: List<TimeWindow>, new: List<TimeWindow>): Boolean {
    fun allowed(windows: List<TimeWindow>, minute: Int) = windows.isEmpty() || windows.any { it.contains(minute) }
    return (0 until 1440).any { allowed(new, it) && !allowed(old, it) }
}

fun isLooserRestrictions(current: android.os.Bundle, new: android.os.Bundle) =
    new.keySet().any { current.getBoolean(it) && !new.getBoolean(it) }

fun isLooserSet(old: Set<String>, new: Set<String>) = !new.containsAll(old)
