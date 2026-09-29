package io.github.warleysr.dechainer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.models.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FocusModeViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()

    var focusMinutes by mutableIntStateOf(FocusMode.getFocusMinutes(context))
        private set

    var shortBreakMinutes by mutableIntStateOf(FocusMode.getShortBreakMinutes(context))
        private set

    var longBreakMinutes by mutableIntStateOf(FocusMode.getLongBreakMinutes(context))
        private set

    var cyclesBeforeLongBreak by mutableIntStateOf(FocusMode.getCyclesBeforeLongBreak(context))
        private set

    var autoStartFocus by mutableStateOf(FocusMode.isAutoStartFocus(context))
        private set

    var soundEnabled by mutableStateOf(FocusMode.isSoundEnabled(context))
    var vibrationEnabled by mutableStateOf(FocusMode.isVibrationEnabled(context))
        private set

    var suspendedApps by mutableStateOf(FocusMode.getApps(context))
        private set

    var status by mutableStateOf(FocusMode.getStatus(context))
        private set

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoadingApps by mutableStateOf(false)
        private set

    init {
        loadApps()
        viewModelScope.launch {
            while (true) {
                FocusMode.syncIfDue(context)
                refreshStatus()
                delay(1000)
            }
        }
    }

    fun updateFocusMinutes(value: Int) {
        FocusMode.setFocusMinutes(context, value)
        focusMinutes = FocusMode.getFocusMinutes(context)
    }

    fun updateShortBreakMinutes(value: Int) {
        FocusMode.setShortBreakMinutes(context, value)
        shortBreakMinutes = FocusMode.getShortBreakMinutes(context)
    }

    fun updateLongBreakMinutes(value: Int) {
        FocusMode.setLongBreakMinutes(context, value)
        longBreakMinutes = FocusMode.getLongBreakMinutes(context)
    }

    fun updateCyclesBeforeLongBreak(value: Int) {
        FocusMode.setCyclesBeforeLongBreak(context, value)
        cyclesBeforeLongBreak = FocusMode.getCyclesBeforeLongBreak(context)
        refreshStatus()
    }

    fun updateAutoStartFocus(value: Boolean) {
        FocusMode.setAutoStartFocus(context, value)
        autoStartFocus = value
    }

    fun updateVibrationEnabled(value: Boolean) {
        FocusMode.setVibrationEnabled(context, value)
        vibrationEnabled = value
    }

    fun updateSoundEnabled(value: Boolean) {
        FocusMode.setSoundEnabled(context, value)
        soundEnabled = value
    }

    fun toggleAppSelection(packageName: String) {
        val newSet = suspendedApps.toMutableSet()
        if (!newSet.remove(packageName)) newSet.add(packageName)
        suspendedApps = newSet
        FocusMode.setApps(context, newSet)
        refreshStatus()
    }

    fun refreshStatus() {
        status = FocusMode.getStatus(context)
    }

    private fun loadApps() {
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) { AppRepository.getApps() }
            isLoadingApps = false
        }
    }
}
