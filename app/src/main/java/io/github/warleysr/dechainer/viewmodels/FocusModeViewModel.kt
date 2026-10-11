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
import io.github.warleysr.dechainer.data.FocusScheduleStorage
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.FocusSchedule
import io.github.warleysr.dechainer.security.SecurityManager
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

    var focusRestrictions by mutableStateOf(FocusMode.getFocusRestrictions(context))
        private set

    var schedules by mutableStateOf(FocusScheduleStorage.getSchedules(context))
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

    private fun isPermitted(): Boolean =
        SecurityManager.getRecoveryCode(context) == null || SecurityManager.isSessionActive()

    fun updateFocusMinutes(value: Int) {
        if (!isPermitted()) return
        FocusMode.setFocusMinutes(context, value)
        focusMinutes = FocusMode.getFocusMinutes(context)
    }

    fun updateShortBreakMinutes(value: Int) {
        if (!isPermitted()) return
        FocusMode.setShortBreakMinutes(context, value)
        shortBreakMinutes = FocusMode.getShortBreakMinutes(context)
    }

    fun updateLongBreakMinutes(value: Int) {
        if (!isPermitted()) return
        FocusMode.setLongBreakMinutes(context, value)
        longBreakMinutes = FocusMode.getLongBreakMinutes(context)
    }

    fun updateCyclesBeforeLongBreak(value: Int) {
        if (!isPermitted()) return
        FocusMode.setCyclesBeforeLongBreak(context, value)
        cyclesBeforeLongBreak = FocusMode.getCyclesBeforeLongBreak(context)
        refreshStatus()
    }

    fun updateAutoStartFocus(value: Boolean) {
        if (!isPermitted()) return
        FocusMode.setAutoStartFocus(context, value)
        autoStartFocus = value
    }

    fun updateVibrationEnabled(value: Boolean) {
        if (!isPermitted()) return
        FocusMode.setVibrationEnabled(context, value)
        vibrationEnabled = value
    }

    fun updateSoundEnabled(value: Boolean) {
        if (!isPermitted()) return
        FocusMode.setSoundEnabled(context, value)
        soundEnabled = value
    }

    fun toggleAppSelection(packageName: String) {
        if (!isPermitted()) return
        val newSet = suspendedApps.toMutableSet()
        if (!newSet.remove(packageName)) newSet.add(packageName)
        suspendedApps = newSet
        FocusMode.setApps(context, newSet)
        refreshStatus()
    }

    fun toggleRestrictionSelection(key: String) {
        if (!isPermitted()) return
        val newSet = focusRestrictions.toMutableSet()
        if (!newSet.remove(key)) newSet.add(key)
        focusRestrictions = newSet
        FocusMode.setFocusRestrictions(context, newSet)
    }

    fun addOrUpdateSchedule(schedule: FocusSchedule) {
        if (!isPermitted()) return
        FocusScheduleStorage.addOrUpdateSchedule(context, schedule)
        refreshSchedules()
    }

    fun deleteSchedule(id: String) {
        if (!isPermitted()) return
        FocusScheduleStorage.deleteSchedule(context, id)
        refreshSchedules()
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        if (!isPermitted()) return
        FocusScheduleStorage.toggleSchedule(context, id, enabled)
        refreshSchedules()
    }

    fun refreshSchedules() {
        schedules = FocusScheduleStorage.getSchedules(context)
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
