package io.github.warleysr.dechainer.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import io.github.warleysr.dechainer.models.ColorFilterMode
import io.github.warleysr.dechainer.utils.ShizukuRunner

object ColorFilterController {
    private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER_MODE = "accessibility_display_daltonizer"
    private const val INVERSION_ENABLED = "accessibility_display_inversion_enabled"
    private const val NIGHT_DISPLAY_ACTIVATED = "night_display_activated"
    private const val NIGHT_DISPLAY_TEMPERATURE = "night_display_color_temperature"
    private const val EXTRA_DIM_ACTIVATED = "reduce_bright_colors_activated"
    private const val EXTRA_DIM_LEVEL = "reduce_bright_colors_level"

    private const val MODE_MONOCHROMACY = "0"

    private const val FALLBACK_NIGHT_TEMPERATURE_MIN = 2596
    private const val FALLBACK_NIGHT_TEMPERATURE_MAX = 4082

    private const val STATE_PREFS_NAME = "color_filter_state"
    private const val KEY_SAVED = "saved_keys"
    private const val PREVIOUS_PREFIX = "previous_"

    private val allKeys = listOf(
        DALTONIZER_ENABLED, DALTONIZER_MODE, INVERSION_ENABLED, NIGHT_DISPLAY_ACTIVATED,
        NIGHT_DISPLAY_TEMPERATURE, EXTRA_DIM_ACTIVATED, EXTRA_DIM_LEVEL
    )

    // Some OEMs (e.g. Samsung) disable it, and forcing its settings there can black out the screen.
    val platformNightLight: Boolean by lazy { systemBoolean("config_nightDisplayAvailable") }

    val observedUris: List<Uri>
        get() = allKeys.map { Settings.Secure.getUriFor(it) }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun grantPermissionViaShizuku(context: Context): Boolean {
        if (hasPermission(context)) return true
        ShizukuRunner.command(
            "pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            }
        )
        return hasPermission(context)
    }

    fun isApplied(context: Context): Boolean = savedKeys(state(context)).isNotEmpty()

    fun targetsFor(prefs: SharedPreferences): List<Pair<String, String>> {
        val modes = ColorFilterSettings.loadModes(prefs)
        val targets = mutableListOf<Pair<String, String>>()

        if (ColorFilterMode.GRAYSCALE in modes) {
            targets += DALTONIZER_MODE to MODE_MONOCHROMACY
            targets += DALTONIZER_ENABLED to "1"
        }
        if (ColorFilterMode.NIGHT_LIGHT in modes && platformNightLight) {
            targets += NIGHT_DISPLAY_TEMPERATURE to
                nightLightTemperature(ColorFilterSettings.nightLightIntensity(prefs)).toString()
            targets += NIGHT_DISPLAY_ACTIVATED to "1"
        }
        if (ColorFilterMode.EXTRA_DIM in modes) {
            targets += EXTRA_DIM_LEVEL to ColorFilterSettings.extraDimLevel(prefs).toString()
            targets += EXTRA_DIM_ACTIVATED to "1"
        }
        if (ColorFilterMode.INVERSION in modes) {
            targets += INVERSION_ENABLED to "1"
        }
        return targets
    }

    fun enforce(context: Context, targets: List<Pair<String, String>>) {
        if (!hasPermission(context)) return
        val resolver = context.contentResolver
        val state = state(context)

        try {
            val targetKeys = targets.map { it.first }.toSet()
            savedKeys(state).filterNot { it in targetKeys }.forEach { restore(context, state, it) }

            for ((key, value) in targets) {
                val current = Settings.Secure.getString(resolver, key)
                if (key !in savedKeys(state)) {
                    state.edit(commit = true) {
                        putString(PREVIOUS_PREFIX + key, current)
                        putStringSet(KEY_SAVED, savedKeys(state) + key)
                    }
                }
                if (current != value) Settings.Secure.putString(resolver, key, value)
            }
        } catch (e: Exception) {
            Log.e("ColorFilter", "Failed to apply color filters", e)
        }
    }

    fun release(context: Context) {
        val state = state(context)
        val saved = savedKeys(state)
        if (saved.isEmpty() || !hasPermission(context)) return

        try {
            allKeys.reversed().filter { it in saved }.forEach { restore(context, state, it) }
        } catch (e: Exception) {
            Log.e("ColorFilter", "Failed to restore display settings", e)
        }
    }

    private fun restore(context: Context, state: SharedPreferences, key: String) {
        val previous = state.getString(PREVIOUS_PREFIX + key, null)
        state.edit(commit = true) {
            remove(PREVIOUS_PREFIX + key)
            putStringSet(KEY_SAVED, savedKeys(state) - key)
        }
        Settings.Secure.putString(context.contentResolver, key, previous)
    }

    private fun nightLightTemperature(intensity: Int): Int {
        val min = systemInteger("config_nightDisplayColorTemperatureMin", FALLBACK_NIGHT_TEMPERATURE_MIN)
        val max = systemInteger("config_nightDisplayColorTemperatureMax", FALLBACK_NIGHT_TEMPERATURE_MAX)
        return max - (max - min) * intensity.coerceIn(0, 100) / 100
    }

    @SuppressLint("DiscouragedApi")
    private fun systemBoolean(name: String): Boolean {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(name, "bool", "android")
        return id != 0 && resources.getBoolean(id)
    }

    @SuppressLint("DiscouragedApi")
    private fun systemInteger(name: String, fallback: Int): Int {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(name, "integer", "android")
        return if (id != 0) resources.getInteger(id) else fallback
    }

    private fun state(context: Context) =
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)

    private fun savedKeys(state: SharedPreferences): Set<String> =
        state.getStringSet(KEY_SAVED, emptySet()) ?: emptySet()
}
