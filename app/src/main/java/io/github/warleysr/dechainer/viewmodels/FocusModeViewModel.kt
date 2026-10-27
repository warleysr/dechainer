package io.github.warleysr.dechainer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppGroupRepository
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.data.FocusScheduleStorage
import io.github.warleysr.dechainer.models.AppGroup
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.FocusSchedule
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

    var selectedGroupIds by mutableStateOf(FocusMode.getGroupIds(context))
        private set

    var focusRestrictions by mutableStateOf(FocusMode.getFocusRestrictions(context))
        private set

    var schedules by mutableStateOf(FocusScheduleStorage.getSchedules(context))
        private set

    var status by mutableStateOf(FocusMode.getStatus(context))
        private set

    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var groups by mutableStateOf<List<AppGroup>>(emptyList())
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

    fun updateSuspendedApps(newApps: Set<String>) {
        suspendedApps = newApps
        FocusMode.setApps(context, newApps)
        refreshStatus()
    }

    fun updateSelectedGroupIds(newGroupIds: Set<String>) {
        selectedGroupIds = newGroupIds
        FocusMode.setGroupIds(context, newGroupIds)
        refreshStatus()
    }

    fun toggleAppSelection(packageName: String) {
        val newSet = suspendedApps.toMutableSet()
        if (!newSet.remove(packageName)) newSet.add(packageName)
        updateSuspendedApps(newSet)
    }

    fun updateFocusRestrictions(newRestrictions: Set<String>) {
        focusRestrictions = newRestrictions
        FocusMode.setFocusRestrictions(context, newRestrictions)
    }

    fun toggleRestrictionSelection(key: String) {
        val newSet = focusRestrictions.toMutableSet()
        if (!newSet.remove(key)) newSet.add(key)
        updateFocusRestrictions(newSet)
    }

    fun addOrUpdateSchedule(schedule: FocusSchedule) {
        FocusScheduleStorage.addOrUpdateSchedule(context, schedule)
        refreshSchedules()
    }

    fun deleteSchedule(id: String) {
        FocusScheduleStorage.deleteSchedule(context, id)
        refreshSchedules()
    }

    fun toggleSchedule(id: String, enabled: Boolean) {
        FocusScheduleStorage.toggleSchedule(context, id, enabled)
        refreshSchedules()
    }

    fun refreshSchedules() {
        schedules = FocusScheduleStorage.getSchedules(context)
    }

    fun refreshStatus() {
        status = FocusMode.getStatus(context)
    }

    fun refreshGroups() {
        groups = AppGroupRepository.getGroups()
        selectedGroupIds = FocusMode.getGroupIds(context)
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoadingApps = true
            apps = withContext(Dispatchers.IO) { AppRepository.getApps() }
            groups = withContext(Dispatchers.IO) { AppGroupRepository.getGroups() }
            isLoadingApps = false
        }
    }
}
