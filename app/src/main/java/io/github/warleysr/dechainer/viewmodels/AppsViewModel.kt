package io.github.warleysr.dechainer.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.models.AppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsViewModel : ViewModel() {
    var apps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    init {
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            isLoading = true
            apps = withContext(Dispatchers.IO) {
                try {
                    AppRepository.getApps()
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

    fun setAppTimeLimit(packageName: String, minutes: Int) {
        AppRepository.setAppTimeLimit(packageName, minutes)
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
}
