package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppTimeWindows
import io.github.warleysr.dechainer.data.ColorFilterController
import io.github.warleysr.dechainer.data.ColorFilterSettings
import io.github.warleysr.dechainer.models.ColorFilterMode
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ColorFilterViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val prefs = context.getSharedPreferences(ColorFilterSettings.PREFS_NAME, Context.MODE_PRIVATE)

    var enabled by mutableStateOf(ColorFilterSettings.isEnabled(prefs))
        private set

    var windows by mutableStateOf(ColorFilterSettings.loadWindows(prefs))
        private set

    var modes by mutableStateOf(ColorFilterSettings.loadModes(prefs))
        private set

    var nightLightIntensity by mutableIntStateOf(ColorFilterSettings.nightLightIntensity(prefs))
        private set

    var extraDimLevel by mutableIntStateOf(ColorFilterSettings.extraDimLevel(prefs))
        private set

    var permissionGranted by mutableStateOf(ColorFilterController.hasPermission(context))
        private set

    var grantingPermission by mutableStateOf(false)
        private set

    var filtersApplied by mutableStateOf(ColorFilterController.isApplied(context))
        private set

    private var minuteOfDay by mutableIntStateOf(ColorFilterSettings.currentMinuteOfDay())

    val activeWindows: List<TimeWindow>
        get() = if (enabled && modes.isNotEmpty()) ColorFilterSettings.activeWindows(windows, minuteOfDay)
            else emptyList()

    val isLocked: Boolean get() = activeWindows.isNotEmpty() && !SecurityManager.isSessionActive()

    val lockedWindows: List<TimeWindow> get() = if (isLocked) activeWindows else emptyList()

    fun refresh() {
        minuteOfDay = ColorFilterSettings.currentMinuteOfDay()
        permissionGranted = ColorFilterController.hasPermission(context)
        filtersApplied = ColorFilterController.isApplied(context)
    }

    fun updateEnabled(value: Boolean): Boolean {
        refresh()
        if (!value && isLocked) return false
        enabled = value
        prefs.edit { putBoolean(ColorFilterSettings.KEY_ENABLED, value) }
        return true
    }

    fun updateMode(mode: ColorFilterMode, selected: Boolean): Boolean {
        refresh()
        if (!selected && isLocked) return false
        val newModes = if (selected) modes + mode else modes - mode
        modes = newModes
        prefs.edit { putStringSet(ColorFilterSettings.KEY_MODES, ColorFilterSettings.encodeModes(newModes)) }
        return true
    }

    fun updateNightLightIntensity(value: Int): Boolean {
        refresh()
        if (isLocked) return false
        nightLightIntensity = value
        prefs.edit { putInt(ColorFilterSettings.KEY_NIGHT_LIGHT_INTENSITY, value) }
        return true
    }

    fun updateExtraDimLevel(value: Int): Boolean {
        refresh()
        if (isLocked) return false
        extraDimLevel = value
        prefs.edit { putInt(ColorFilterSettings.KEY_EXTRA_DIM_LEVEL, value) }
        return true
    }

    fun isOnlyAdding(newWindows: List<TimeWindow>): Boolean = newWindows.containsAll(windows)

    fun updateWindows(newWindows: List<TimeWindow>): Boolean {
        refresh()
        if (!newWindows.containsAll(lockedWindows)) return false
        windows = newWindows
        prefs.edit { putString(ColorFilterSettings.KEY_WINDOWS, AppTimeWindows.encode(newWindows)) }
        return true
    }

    fun grantPermission(onResult: (Boolean) -> Unit) {
        grantingPermission = true
        viewModelScope.launch {
            val granted = withContext(Dispatchers.IO) {
                ColorFilterController.grantPermissionViaShizuku(context)
            }
            grantingPermission = false
            permissionGranted = granted
            onResult(granted)
        }
    }
}
