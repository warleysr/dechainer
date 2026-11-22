package io.github.warleysr.dechainer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.data.AppGroupRepository
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.models.AppGroup
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.TimeLimit
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.models.TimeWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsViewModel : ViewModel() {
    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var groups by mutableStateOf<List<AppGroup>>(emptyList())
        private set

    init {
        loadApps()
        loadGroups()
    }

    fun loadApps(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (apps.isEmpty()) isLoading = true
            apps = withContext(Dispatchers.IO) {
                try {
                    AppRepository.getApps(forceRefresh)
                } catch (e: Exception) {
                    emptyList()
                }
            }
            isLoading = false
        }
    }

    fun blockApp(packageName: String, hidden: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppRepository.setAppHidden(packageName, hidden)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun suspendApp(packageName: String, suspended: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppRepository.setAppSuspended(packageName, suspended)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setUninstallBlocked(packageName: String, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppRepository.setUninstallBlocked(packageName, block)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setAppTimeLimit(packageName: String, limit: TimeLimit) {
        AppRepository.setAppTimeLimit(packageName, limit)
        loadApps()
    }

    fun getAppUsage(packageName: String, inMinutes: Boolean = false): Long {
        return AppRepository.getAppUsage(packageName, inMinutes)
    }

    fun setAppReopenTime(packageName: String, seconds: Int) {
        AppRepository.setAppReopenTime(packageName, seconds)
    }

    fun getAppReopenTime(packageName: String): Int {
        return AppRepository.getAppReopenTime(packageName)
    }

    fun setAppTimeWindows(packageName: String, windows: TimeWindows) {
        AppRepository.setAppTimeWindows(packageName, windows)
        loadApps()
    }

    fun setAppTimeWindows(packageName: String, windows: List<TimeWindow>) {
        setAppTimeWindows(packageName, TimeWindows.daily(windows))
    }

    fun loadGroups() {
        groups = AppGroupRepository.getGroups()
    }

    fun groupFor(packageName: String): AppGroup? = groups.firstOrNull { packageName in it.packageNames }

    fun createGroup(name: String): AppGroup {
        val group = AppGroupRepository.createGroup(name)
        loadGroups()
        return group
    }

    fun deleteGroup(groupId: String) {
        AppGroupRepository.deleteGroup(groupId)
        loadGroups()
    }

    fun renameGroup(groupId: String, name: String) {
        AppGroupRepository.renameGroup(groupId, name)
        loadGroups()
    }

    fun setGroupTimeLimit(groupId: String, limit: TimeLimit) {
        AppGroupRepository.setGroupTimeLimit(groupId, limit)
        loadGroups()
    }

    fun setGroupTimeWindows(groupId: String, windows: TimeWindows) {
        AppGroupRepository.setGroupTimeWindows(groupId, windows)
        loadGroups()
    }

    fun setGroupTimeWindows(groupId: String, windows: List<TimeWindow>) {
        setGroupTimeWindows(groupId, TimeWindows.daily(windows))
    }

    fun setGroupPackages(groupId: String, packageNames: Set<String>) {
        AppGroupRepository.setGroupPackages(groupId, packageNames)
        loadGroups()
    }

    fun setPackageGroup(packageName: String, groupId: String?) {
        AppGroupRepository.setPackageGroup(packageName, groupId)
        loadGroups()
    }

    fun getGroupUsage(groupId: String, inMinutes: Boolean = false): Long {
        return AppGroupRepository.getGroupUsage(groupId, inMinutes)
    }
}
