package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.VisualBlockingSettings
import io.github.warleysr.dechainer.models.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VisualBlockingViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val prefs = context.getSharedPreferences(VisualBlockingSettings.PREFS_NAME, Context.MODE_PRIVATE)

    var enabled by mutableStateOf(prefs.getBoolean(VisualBlockingSettings.KEY_ENABLED, false))
        private set

    var targetPackages by mutableStateOf(
        prefs.getStringSet(VisualBlockingSettings.KEY_TARGET_PACKAGES, VisualBlockingSettings.DEFAULT_TARGET_PACKAGES)
            ?: VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
    )
        private set

    var selectedCategories by mutableStateOf(
        prefs.getStringSet(VisualBlockingSettings.KEY_CATEGORIES, VisualBlockingSettings.DEFAULT_CATEGORIES)
            ?: VisualBlockingSettings.DEFAULT_CATEGORIES
    )
        private set

    var threshold by mutableFloatStateOf(
        prefs.getFloat(VisualBlockingSettings.KEY_THRESHOLD, VisualBlockingSettings.DEFAULT_THRESHOLD)
    )
        private set

    var suspendEnabled by mutableStateOf(
        prefs.getBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, false)
    )
        private set

    var suspendBlockCount by mutableIntStateOf(
        prefs.getInt(
            VisualBlockingSettings.KEY_SUSPEND_BLOCK_COUNT,
            VisualBlockingSettings.DEFAULT_SUSPEND_BLOCK_COUNT
        )
    )
        private set

    var suspendWindowMinutes by mutableIntStateOf(
        prefs.getInt(
            VisualBlockingSettings.KEY_SUSPEND_WINDOW_MINUTES,
            VisualBlockingSettings.DEFAULT_SUSPEND_WINDOW_MINUTES
        )
    )
        private set

    var suspendDurationMinutes by mutableIntStateOf(
        prefs.getInt(
            VisualBlockingSettings.KEY_SUSPEND_DURATION_MINUTES,
            VisualBlockingSettings.DEFAULT_SUSPEND_DURATION_MINUTES
        )
    )
        private set

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoadingApps by mutableStateOf(false)
        private set

    init {
        loadApps()
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs.edit { putBoolean(VisualBlockingSettings.KEY_ENABLED, value) }
    }

    fun toggleAppSelection(packageName: String) {
        val newSet = targetPackages.toMutableSet()
        if (!newSet.remove(packageName)) newSet.add(packageName)
        targetPackages = newSet
        prefs.edit { putStringSet(VisualBlockingSettings.KEY_TARGET_PACKAGES, targetPackages) }
    }

    fun toggleCategory(category: String) {
        val newSet = selectedCategories.toMutableSet()
        if (!newSet.remove(category)) newSet.add(category)
        selectedCategories = newSet
        prefs.edit { putStringSet(VisualBlockingSettings.KEY_CATEGORIES, selectedCategories) }
    }

    fun updateThreshold(value: Float) {
        threshold = value
        prefs.edit { putFloat(VisualBlockingSettings.KEY_THRESHOLD, value) }
    }

    fun updateSuspendEnabled(value: Boolean) {
        suspendEnabled = value
        prefs.edit { putBoolean(VisualBlockingSettings.KEY_SUSPEND_ENABLED, value) }
    }

    /**
     * The three settings below accept 0 while the user is clearing the field to type a new
     * number, so they're only persisted once they hold a usable value — the service falls back to
     * the stored one until then.
     */
    fun updateSuspendBlockCount(value: Int) {
        suspendBlockCount = value
        if (value >= 1) prefs.edit { putInt(VisualBlockingSettings.KEY_SUSPEND_BLOCK_COUNT, value) }
    }

    fun updateSuspendWindowMinutes(value: Int) {
        suspendWindowMinutes = value
        if (value >= 1) prefs.edit { putInt(VisualBlockingSettings.KEY_SUSPEND_WINDOW_MINUTES, value) }
    }

    fun updateSuspendDurationMinutes(value: Int) {
        suspendDurationMinutes = value
        if (value >= 1) prefs.edit { putInt(VisualBlockingSettings.KEY_SUSPEND_DURATION_MINUTES, value) }
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) { AppRepository.getApps() }
            isLoadingApps = false
        }
    }
}
