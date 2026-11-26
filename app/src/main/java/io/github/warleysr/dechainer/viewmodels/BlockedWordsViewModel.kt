package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.models.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockedWordsViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val prefs = context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE)

    var blockedWordsText by mutableStateOf(prefs.getStringSet("blocked_words", emptySet())?.joinToString("\n") ?: "")
        private set

    var targetPackages by mutableStateOf(prefs.getStringSet("target_packages", emptySet()) ?: emptySet())
        private set

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoadingApps by mutableStateOf(false)
        private set

    init {
        loadApps()
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

    fun updateTargetPackages(packages: Set<String>) {
        targetPackages = packages
        prefs.edit {
            putStringSet("target_packages", targetPackages)
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) {
                AppRepository.getApps()
            }
            isLoadingApps = false
        }
    }
}
