package io.github.warleysr.dechainer.data

import android.content.SharedPreferences
import io.github.warleysr.dechainer.models.ColorFilterMode
import io.github.warleysr.dechainer.models.TimeWindow
import java.time.LocalTime
import java.util.concurrent.TimeUnit

object ColorFilterSettings {
    const val PREFS_NAME = "color_filter_prefs"
    const val KEY_ENABLED = "enabled"
    const val KEY_WINDOWS = "windows"
    const val KEY_MODES = "modes"
    const val KEY_NIGHT_LIGHT_INTENSITY = "night_light_intensity"
    const val KEY_EXTRA_DIM_LEVEL = "extra_dim_level"

    val DEFAULT_MODES = setOf(ColorFilterMode.GRAYSCALE)
    const val DEFAULT_NIGHT_LIGHT_INTENSITY = 70
    const val DEFAULT_EXTRA_DIM_LEVEL = 50

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun loadWindows(prefs: SharedPreferences): List<TimeWindow> =
        AppTimeWindows.decode(prefs.getString(KEY_WINDOWS, null))

    fun loadModes(prefs: SharedPreferences): Set<ColorFilterMode> {
        val names = prefs.getStringSet(KEY_MODES, null) ?: return DEFAULT_MODES
        return names.mapNotNull { name -> ColorFilterMode.entries.firstOrNull { it.name == name } }
            .filter { it.isAvailable }
            .toSet()
    }

    fun encodeModes(modes: Set<ColorFilterMode>): Set<String> = modes.map { it.name }.toSet()

    fun nightLightIntensity(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_NIGHT_LIGHT_INTENSITY, DEFAULT_NIGHT_LIGHT_INTENSITY)

    fun extraDimLevel(prefs: SharedPreferences): Int =
        prefs.getInt(KEY_EXTRA_DIM_LEVEL, DEFAULT_EXTRA_DIM_LEVEL)

    fun currentMinuteOfDay(now: LocalTime = LocalTime.now()): Int = now.hour * 60 + now.minute

    fun activeWindows(windows: List<TimeWindow>, minuteOfDay: Int): List<TimeWindow> =
        windows.filter { it.contains(minuteOfDay) }

    fun shouldEnforce(prefs: SharedPreferences, minuteOfDay: Int = currentMinuteOfDay()): Boolean =
        isEnabled(prefs) && loadModes(prefs).isNotEmpty() &&
            activeWindows(loadWindows(prefs), minuteOfDay).isNotEmpty()

    fun millisUntilNextBoundary(windows: List<TimeWindow>, now: LocalTime = LocalTime.now()): Long? {
        if (windows.isEmpty()) return null
        val nowMinute = currentMinuteOfDay(now)
        val minutesAhead = windows
            .flatMap { listOf(it.startMinute, it.endMinute % 1440) }
            .minOf { boundary -> ((boundary - nowMinute + 1440) % 1440).takeIf { it > 0 } ?: 1440 }
        val intoCurrentMinute = TimeUnit.SECONDS.toMillis(now.second.toLong()) +
            TimeUnit.NANOSECONDS.toMillis(now.nano.toLong())
        return TimeUnit.MINUTES.toMillis(minutesAhead.toLong()) - intoCurrentMinute
    }
}
