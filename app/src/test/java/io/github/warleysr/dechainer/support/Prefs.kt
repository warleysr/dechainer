package io.github.warleysr.dechainer.support

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider

/**
 * Raw access to the app's `SharedPreferences` files from tests.
 *
 * The persistence contract between the UI layer and [io.github.warleysr.dechainer.DechainerAccessibilityService]
 * lives entirely in eight `SharedPreferences` files whose names and keys are duplicated string
 * literals on both ends. Tests reach those files by their raw names on purpose: pinning the name
 * itself is the point, so nothing here funnels through a shared constants object.
 *
 * [clearAllPrefs] wipes every one of those files so state written by one test never leaks into the
 * next; it is the single reset used by [DechainerTestRule].
 */

private val ALL_PREF_FILES = listOf(
    "blocked_words_prefs",
    "activity_blocker_prefs",
    "app_limits",
    "reopen_times",
    "internal_usage_stats",
    "browser_prefs",
    "recovery_prefs",
    "security_prefs",
)

fun prefs(name: String): SharedPreferences =
    ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences(name, Context.MODE_PRIVATE)

fun clearAllPrefs() {
    ALL_PREF_FILES.forEach { prefs(it).edit().clear().commit() }
}
