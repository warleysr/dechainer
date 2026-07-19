package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit

data class AppSelectionItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSelected: Boolean
)

class BlockedWordsViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val prefs = context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE)

    var blockedWordsText by mutableStateOf(prefs.getStringSet("blocked_words", emptySet())?.joinToString("\n") ?: "")
        private set

    var targetPackages by mutableStateOf(prefs.getStringSet("target_packages", emptySet()) ?: emptySet())
        private set

    var apps by mutableStateOf<List<AppSelectionItem>>(emptyList())
        private set

    var passiveWordsMap by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    var isLoadingApps by mutableStateOf(false)
        private set

    init {
        loadApps()
        loadPassiveWords()
    }

    private fun loadPassiveWords() {
        val all = prefs.all
        val map = all.filterKeys { it.startsWith("passive_words_") }
            .map { (key, value) ->
                val pkg = key.substringAfter("passive_words_")
                val wordsSet = (value as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                pkg to wordsSet.joinToString("\n")
            }.toMap()
        passiveWordsMap = map
    }

    fun updateWords(text: String) {
        blockedWordsText = text
        val wordsSet = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        prefs.edit {
            putStringSet("blocked_words", wordsSet)
        }
    }

    fun updatePassiveWords(packageName: String, text: String) {
        val wordsSet = text.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        prefs.edit {
            if (wordsSet.isEmpty()) {
                remove("passive_words_$packageName")
            } else {
                putStringSet("passive_words_$packageName", wordsSet)
            }
        }

        val newMap = passiveWordsMap.toMutableMap()
        if (wordsSet.isEmpty()) newMap.remove(packageName) else newMap[packageName] = text
        passiveWordsMap = newMap
    }

    fun toggleAppSelection(packageName: String) {
        val currentSet = targetPackages.toMutableSet()
        if (currentSet.contains(packageName)) {
            currentSet.remove(packageName)
        } else {
            currentSet.add(packageName)
        }
        targetPackages = currentSet
        prefs.edit {
            putStringSet("target_packages", targetPackages)
        }
        updateAppsSelection()
    }

    private fun updateAppsSelection() {
        apps = apps.map { it.copy(isSelected = targetPackages.contains(it.packageName)) }
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) {
                val packageManager = context.packageManager
                val installedApps = packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                installedApps.asSequence()
                    .filter { it.packageName != context.packageName }
                    .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { appInfo ->
                        AppSelectionItem(
                            name = appInfo.loadLabel(packageManager).toString(),
                            packageName = appInfo.packageName,
                            icon = appInfo.loadIcon(packageManager),
                            isSelected = targetPackages.contains(appInfo.packageName)
                        )
                    }
                    .sortedBy { it.name.lowercase() }
                    .toList()
            }
            isLoadingApps = false
        }
    }
}
