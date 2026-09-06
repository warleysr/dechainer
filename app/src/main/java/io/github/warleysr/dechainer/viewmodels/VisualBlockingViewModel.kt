package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.utils.VisualBlockingSettings
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

    fun loadApps() {
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) { AppRepository.getApps() }
            isLoadingApps = false
        }
    }
}
