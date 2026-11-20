package io.github.warleysr.dechainer.data

import android.content.Context
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.github.warleysr.dechainer.R

object PermanentRestrictionsManager {
    private const val PREFS_NAME = "permanent_restrictions_prefs"
    private const val KEY_PERMANENT_SET = "permanent_keys"
    private const val KEY_EDIT_ACTIVE = "edit_timer_active"
    private const val KEY_EDIT_ACCUMULATED = "edit_timer_accumulated"
    private const val KEY_EDIT_LAST_ELAPSED = "edit_timer_last_elapsed"
    private const val TARGET_DURATION_MS = 48L * 60 * 60 * 1000

    const val KEY_REMOVE_DEVICE_OWNER = "remove_device_owner"
    const val KEY_ACTIVITY_BLOCKER = "activity_blocker"
    const val KEY_VISUAL_BLOCKING = "visual_blocking"
    const val KEY_BLOCKED_WORDS = "blocked_words"
    const val KEY_BLOCKED_ACTIVITIES = "blocked_activities"
    const val KEY_BLOCK_TORRENTS = "block_torrents"
    const val KEY_BROWSER_RESTRICTIONS = "browser_restrictions"
    const val KEY_DNS_SETTINGS = "dns_settings"
    const val KEY_IMPULSE_LOCK = "impulse_lock"
    const val KEY_FOCUS_MODE = "focus_mode"
    const val KEY_COLOR_FILTER_SETTINGS = "color_filter_settings"

    data class FeatureOption(
        val key: String,
        @StringRes val titleRes: Int,
        @StringRes val descRes: Int,
        val requiresDeviceOwner: Boolean = false
    )

    val appFeatures = listOf(
        FeatureOption(
            key = KEY_REMOVE_DEVICE_OWNER,
            titleRes = R.string.permanent_remove_device_owner,
            descRes = R.string.permanent_remove_device_owner_desc,
            requiresDeviceOwner = true
        ),
        FeatureOption(
            key = KEY_DNS_SETTINGS,
            titleRes = R.string.dns_settings,
            descRes = R.string.dns_description,
            requiresDeviceOwner = true
        ),
        FeatureOption(
            key = KEY_BROWSER_RESTRICTIONS,
            titleRes = R.string.browser_restrictions,
            descRes = R.string.browser_restrictions_desc,
            requiresDeviceOwner = true
        ),
        FeatureOption(
            key = KEY_ACTIVITY_BLOCKER,
            titleRes = R.string.activity_blocker,
            descRes = R.string.activity_blocker_description
        ),
        FeatureOption(
            key = KEY_BLOCKED_ACTIVITIES,
            titleRes = R.string.blocked_activities,
            descRes = R.string.blocked_activities_desc
        ),
        FeatureOption(
            key = KEY_VISUAL_BLOCKING,
            titleRes = R.string.visual_blocking,
            descRes = R.string.visual_blocking_desc
        ),
        FeatureOption(
            key = KEY_BLOCKED_WORDS,
            titleRes = R.string.blocked_words_feat,
            descRes = R.string.blocked_words_feat_description
        ),
        FeatureOption(
            key = KEY_BLOCK_TORRENTS,
            titleRes = R.string.block_torrents,
            descRes = R.string.block_torrents_desc
        ),
        FeatureOption(
            key = KEY_IMPULSE_LOCK,
            titleRes = R.string.impulse_lock,
            descRes = R.string.impulse_lock_desc
        ),
        FeatureOption(
            key = KEY_FOCUS_MODE,
            titleRes = R.string.focus_mode,
            descRes = R.string.focus_mode_desc
        ),
        FeatureOption(
            key = KEY_COLOR_FILTER_SETTINGS,
            titleRes = R.string.color_filter_settings,
            descRes = R.string.color_filter_settings_desc
        )
    )

    fun dpcKey(restrictionKey: String): String = "dpc:$restrictionKey"

    fun getPermanentKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PERMANENT_SET, emptySet())?.toSet() ?: emptySet()
    }

    fun isPermanent(context: Context, key: String): Boolean {
        return key in getPermanentKeys(context)
    }

    fun isDpcPermanent(context: Context, restrictionKey: String): Boolean {
        return isPermanent(context, dpcKey(restrictionKey))
    }

    fun setPermanent(context: Context, key: String, permanent: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = getPermanentKeys(context).toMutableSet()
        if (permanent) updated.add(key) else updated.remove(key)
        prefs.edit {
            putStringSet(KEY_PERMANENT_SET, updated)
        }
    }

    fun startEditTimer(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_EDIT_ACTIVE, true)
            putLong(KEY_EDIT_ACCUMULATED, 0L)
            putLong(KEY_EDIT_LAST_ELAPSED, SystemClock.elapsedRealtime())
        }
    }

    fun cancelOrLockEdit(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_EDIT_ACTIVE, false)
            putLong(KEY_EDIT_ACCUMULATED, 0L)
            putLong(KEY_EDIT_LAST_ELAPSED, 0L)
        }
    }

    fun getEditRemainingTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_EDIT_ACTIVE, false)) return -1L

        var accumulated = prefs.getLong(KEY_EDIT_ACCUMULATED, 0L)
        val lastElapsed = prefs.getLong(KEY_EDIT_LAST_ELAPSED, 0L)
        val now = SystemClock.elapsedRealtime()

        val diff = if (now >= lastElapsed) now - lastElapsed else now
        accumulated += diff

        prefs.edit {
            putLong(KEY_EDIT_ACCUMULATED, accumulated)
            putLong(KEY_EDIT_LAST_ELAPSED, now)
        }

        return (TARGET_DURATION_MS - accumulated).coerceAtLeast(0L)
    }

    fun isEditUnlocked(context: Context): Boolean = getEditRemainingTime(context) == 0L

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            clear()
        }
    }
}
