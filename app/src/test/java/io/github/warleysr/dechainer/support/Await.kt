package io.github.warleysr.dechainer.support

import android.os.Looper
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Waits until [condition] holds, draining the main looper between checks.
 *
 * [io.github.warleysr.dechainer.DechainerAccessibilityService] launches its block screen through a
 * `Dispatchers.Default` scope that then hops to `Dispatchers.Main`, so the `startActivity` call is
 * only observable after the background coroutine has run and the posted main-thread work has been
 * executed. Each iteration idles the real main looper (running whatever the coroutine posted) and
 * re-checks; a short sleep gives the background thread room to make progress. A timeout turns a
 * never-satisfied condition into a failed assertion instead of a hang.
 */
fun awaitUntil(
    timeout: Duration = 2.seconds,
    poll: Duration = 10.milliseconds,
    condition: () -> Boolean,
) {
    val deadlineNanos = System.nanoTime() + timeout.inWholeNanoseconds
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        if (System.nanoTime() >= deadlineNanos)
            throw AssertionError("Condition was not satisfied within $timeout")
        Thread.sleep(poll.inWholeMilliseconds)
    }
}
