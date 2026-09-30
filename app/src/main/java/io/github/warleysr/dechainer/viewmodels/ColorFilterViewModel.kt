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
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.AppTimeWindows
import io.github.warleysr.dechainer.data.ColorFilterController
import io.github.warleysr.dechainer.data.ColorFilterSettings
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.ColorFilterMode
import io.github.warleysr.dechainer.models.ColorFilterScope
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ColorFilterViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val prefs = context.getSharedPreferences(ColorFilterSettings.PREFS_NAME, Context.MODE_PRIVATE)
        .also { ColorFilterSettings.migrate(it) }

    var enabled by mutableStateOf(ColorFilterSettings.isEnabled(prefs))
        private set

    var modes by mutableStateOf(ColorFilterSettings.loadModes(prefs))
        private set

    private var windowsByMode by mutableStateOf(
        ColorFilterMode.entries.associateWith { ColorFilterSettings.loadWindows(prefs, it) }
    )

    private var coverageByMode by mutableStateOf(
        ColorFilterMode.entries.associateWith { ColorFilterSettings.loadCoverage(prefs, it) }
    )

    var nightLightIntensity by mutableIntStateOf(ColorFilterSettings.nightLightIntensity(prefs))
        private set

    var nightLightTemperature by mutableIntStateOf(ColorFilterSettings.nightLightTemperature(prefs))
        private set

    var extraDimLevel by mutableIntStateOf(ColorFilterSettings.extraDimLevel(prefs))
        private set

    var permissionGranted by mutableStateOf(ColorFilterController.hasPermission(context))
        private set

    var grantingPermission by mutableStateOf(false)
        private set

    var filtersApplied by mutableStateOf(ColorFilterController.isApplied(context))
        private set

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoadingApps by mutableStateOf(false)
        private set

    private var minuteOfDay by mutableIntStateOf(ColorFilterSettings.currentMinuteOfDay())

    fun windows(mode: ColorFilterMode): List<TimeWindow> = windowsByMode[mode].orEmpty()

    fun coverage(mode: ColorFilterMode): ColorFilterScope.Coverage = coverageByMode.getValue(mode)

    fun activeWindows(mode: ColorFilterMode): List<TimeWindow> =
        if (enabled && mode in modes) ColorFilterSettings.activeWindows(windows(mode), minuteOfDay) else emptyList()

    fun isLocked(mode: ColorFilterMode): Boolean =
        activeWindows(mode).isNotEmpty() && !SecurityManager.isSessionActive()

    fun lockedWindows(mode: ColorFilterMode): Set<TimeWindow> =
        if (isLocked(mode)) activeWindows(mode).toSet() else emptySet()

    val isLocked: Boolean get() = modes.any { isLocked(it) }

    val activeWindows: List<TimeWindow> get() = modes.flatMap { activeWindows(it) }.distinct()

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
        if (!selected && isLocked(mode)) return false
        val newModes = if (selected) modes + mode else modes - mode
        modes = newModes
        prefs.edit { putStringSet(ColorFilterSettings.KEY_MODES, ColorFilterSettings.encodeModes(newModes)) }
        return true
    }

    fun updateNightLightIntensity(value: Int): Boolean {
        refresh()
        if (isLocked(ColorFilterMode.NIGHT_LIGHT)) return false
        nightLightIntensity = value
        prefs.edit { putInt(ColorFilterSettings.KEY_NIGHT_LIGHT_INTENSITY, value) }
        return true
    }

    fun updateNightLightTemperature(value: Int): Boolean {
        refresh()
        if (isLocked(ColorFilterMode.NIGHT_LIGHT)) return false
        nightLightTemperature = value
        prefs.edit { putInt(ColorFilterSettings.KEY_NIGHT_LIGHT_TEMPERATURE, value) }
        return true
    }

    fun updateExtraDimLevel(value: Int): Boolean {
        refresh()
        if (isLocked(ColorFilterMode.EXTRA_DIM)) return false
        extraDimLevel = value
        prefs.edit { putInt(ColorFilterSettings.KEY_EXTRA_DIM_LEVEL, value) }
        return true
    }

    fun isOnlyAdding(mode: ColorFilterMode, newWindows: List<TimeWindow>): Boolean =
        newWindows.containsAll(windows(mode))

    fun updateWindows(mode: ColorFilterMode, newWindows: List<TimeWindow>): Boolean {
        refresh()
        if (!newWindows.containsAll(lockedWindows(mode))) return false
        windowsByMode = windowsByMode + (mode to newWindows)
        prefs.edit { putString(ColorFilterSettings.windowsKey(mode), AppTimeWindows.encode(newWindows)) }
        return true
    }

    fun isLoosening(mode: ColorFilterMode, newCoverage: ColorFilterScope.Coverage): Boolean =
        ColorFilterScope.isLoosening(coverage(mode), newCoverage)

    fun updateCoverage(mode: ColorFilterMode, newCoverage: ColorFilterScope.Coverage): Boolean {
        refresh()
        if (isLoosening(mode, newCoverage) && isLocked(mode)) return false
        coverageByMode = coverageByMode + (mode to newCoverage)
        prefs.edit {
            putString(ColorFilterSettings.scopeKey(mode), newCoverage.scope.name)
            putStringSet(ColorFilterSettings.excludedAppsKey(mode), newCoverage.excludedApps)
            putStringSet(ColorFilterSettings.onlyAppsKey(mode), newCoverage.onlyApps)
        }
        return true
    }

    fun loadAppsIfNeeded() {
        if (apps.isNotEmpty() || isLoadingApps) return
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) { AppRepository.getApps() }
            isLoadingApps = false
        }
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
