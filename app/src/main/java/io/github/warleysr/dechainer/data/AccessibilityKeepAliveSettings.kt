package io.github.warleysr.dechainer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object AccessibilityKeepAliveSettings {
    const val PREFS_NAME = "keep_alive_prefs"
    const val KEY_ENABLED = "persistent_notification_enabled"
    const val DEFAULT_ENABLED = false

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }
}
