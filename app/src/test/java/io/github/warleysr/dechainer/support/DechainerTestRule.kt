package io.github.warleysr.dechainer.support

import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.security.SecurityManager
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import timber.log.Timber

/**
 * Resets the process-wide state that leaks between Robolectric tests running in the same JVM.
 *
 * Several pieces of app state outlive a single test method:
 * - [SecurityManager] keeps `sessionEndTime` in a `companion object`; a session left active by a
 *   previous test would make [SecurityManager.validateRecoveryCode] short-circuit to `true`.
 * - [DechainerAccessibilityService.accessedActivities] is a static list that accumulates entries.
 * - [Timber] holds a static forest; [io.github.warleysr.dechainer.DechainerApplication] plants a
 *   `DebugTree` on every `onCreate`, so trees pile up across tests.
 * - The app's `SharedPreferences` files persist across test methods, so words, limits or a saved
 *   recovery code written by one test would still be visible to the next; [clearAllPrefs] wipes all
 *   of them.
 *
 * The rule runs the reset both before and after each test so a test starts clean and never leaves
 * residue for the next one. It replaces the hand-written `@Before`/`@After` blocks in the test
 * classes that use it.
 *
 */
class DechainerTestRule : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                reset()
                try {
                    base.evaluate()
                } finally {
                    reset()
                }
            }
        }

    private fun reset() {
        SecurityManager.endSession()
        DechainerAccessibilityService.accessedActivities.clear()
        Timber.uprootAll()
        clearAllPrefs()
    }
}
