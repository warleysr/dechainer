package io.github.warleysr.dechainer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.ReadingLibrary
import io.github.warleysr.dechainer.data.ReadingPassage
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.security.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings behind the "I'm having impulses" panic button. Persistence goes through
 * [SecurityManager], which owns `security_prefs` and is also what
 * [io.github.warleysr.dechainer.DechainerAccessibilityService] reads to apply the suspensions.
 */
class ImpulseLockViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()

    var challenges by mutableStateOf(SecurityManager.getAccessChallenges(context).toSet())
        private set

    var tetrisMinutes by mutableIntStateOf(SecurityManager.getTetrisMinutes(context))
        private set

    var readingSource by mutableStateOf(SecurityManager.getReadingSource(context))
        private set

    var readingLanguage by mutableStateOf(SecurityManager.getReadingLanguage(context))
        private set

    /** The count as stored; [readingCount] is what will actually be shown with the current source. */
    private var storedReadingCount by mutableIntStateOf(SecurityManager.getReadingCount(context))

    var customPassages by mutableStateOf(SecurityManager.getCustomReadingPassages(context))
        private set

    val availableReadings: Int
        get() = if (readingSource == SecurityManager.ReadingSource.CUSTOM) customPassages.size
        else ReadingLibrary.availableCount(context, readingSource)

    val readingCount: Int
        get() = storedReadingCount.coerceAtMost(availableReadings)

    var action by mutableStateOf(SecurityManager.getImpulseAction(context))
        private set

    var durationMinutes by mutableIntStateOf(SecurityManager.getImpulseDurationMinutes(context))
        private set

    var suspendedApps by mutableStateOf(SecurityManager.getImpulseSuspendedApps(context))
        private set

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoadingApps by mutableStateOf(false)
        private set

    init {
        loadApps()
    }

    fun toggleChallenge(type: SecurityManager.ChallengeType) {
        val newSet = challenges.toMutableSet()
        if (!newSet.remove(type)) newSet.add(type)
        challenges = newSet
        SecurityManager.setAccessChallenges(context, newSet)
    }

    fun updateTetrisMinutes(value: Int) {
        val clamped = value.coerceIn(SecurityManager.TETRIS_MIN_MINUTES, SecurityManager.TETRIS_MAX_MINUTES)
        tetrisMinutes = clamped
        SecurityManager.setTetrisMinutes(context, clamped)
    }

    fun updateReadingSource(value: SecurityManager.ReadingSource) {
        readingSource = value
        SecurityManager.setReadingSource(context, value)
    }

    fun updateReadingLanguage(value: SecurityManager.ReadingLanguage) {
        readingLanguage = value
        SecurityManager.setReadingLanguage(context, value)
    }

    fun updateReadingCount(value: Int) {
        val clamped = value.coerceIn(SecurityManager.READING_MIN_COUNT, availableReadings.coerceAtLeast(1))
        storedReadingCount = clamped
        SecurityManager.setReadingCount(context, clamped)
    }

    fun addCustomPassage(text: String, source: String) {
        if (text.isBlank()) return
        val updated = customPassages + ReadingPassage(text.trim(), source.trim())
        customPassages = updated
        SecurityManager.setCustomReadingPassages(context, updated)
    }

    fun removeCustomPassage(index: Int) {
        val updated = customPassages.filterIndexed { i, _ -> i != index }
        customPassages = updated
        SecurityManager.setCustomReadingPassages(context, updated)
    }

    fun updateAction(value: SecurityManager.ImpulseAction) {
        action = value
        SecurityManager.setImpulseAction(context, value)
    }

    fun updateDurationMinutes(value: Int) {
        val clamped = value.coerceIn(
            SecurityManager.IMPULSE_MIN_DURATION_MINUTES,
            SecurityManager.IMPULSE_MAX_DURATION_MINUTES
        )
        durationMinutes = clamped
        SecurityManager.setImpulseDurationMinutes(context, clamped)
    }

    fun toggleAppSelection(packageName: String) {
        val newSet = suspendedApps.toMutableSet()
        if (!newSet.remove(packageName)) newSet.add(packageName)
        suspendedApps = newSet
        SecurityManager.setImpulseSuspendedApps(context, newSet)
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) { AppRepository.getApps() }
            isLoadingApps = false
        }
    }
}
