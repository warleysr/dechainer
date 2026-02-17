package io.github.warleysr.dechainer.viewmodels

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
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
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.edit
import java.util.concurrent.TimeUnit

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val isHidden: Boolean,
    val isUninstallBlocked: Boolean,
    val timeLimitMinutes: Int = 0
)

class AppsViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val packageManager = context.packageManager
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminName = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

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
                    val prefs = context.getSharedPreferences("app_limits", Context.MODE_PRIVATE)
                    val installedApps = packageManager.getInstalledApplications(
                        PackageManager.MATCH_UNINSTALLED_PACKAGES
                    )
                    
                    installedApps.asSequence()
                        .filter { it.packageName != DechainerApplication.getInstance().packageName }
                        .mapNotNull { appInfo ->
                            val packageName = appInfo.packageName
                            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            
                            val isHidden = try { 
                                dpm.isApplicationHidden(adminName, packageName) 
                            } catch (_: Exception) { false }
                            
                            val isUninstallBlocked = try {
                                dpm.isUninstallBlocked(adminName, packageName)
                            } catch (_: Exception) { false }
                            
                            val timeLimit = prefs.getInt(packageName, 0)
                            
                            // Filter: Only show non-system apps OR system apps interacted with (hidden/protected/limited)
                            if (isSystem && !isHidden && !isUninstallBlocked && timeLimit == 0) return@mapNotNull null
                            
                            AppItem(
                                name = appInfo.loadLabel(packageManager).toString(),
                                packageName = packageName,
                                icon = appInfo.loadIcon(packageManager),
                                isSystem = isSystem,
                                isHidden = isHidden,
                                isUninstallBlocked = isUninstallBlocked,
                                timeLimitMinutes = timeLimit
                            )
                        }
                        .sortedBy { it.name.lowercase() }
                        .toList()
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
                dpm.setApplicationHidden(adminName, packageName, hidden)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun suspendApp(packageName: String, suspended: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dpm.setPackagesSuspended(adminName, arrayOf(packageName), suspended)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setUninstallBlocked(packageName: String, block: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dpm.setUninstallBlocked(adminName, packageName, block)
                loadApps()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setAppTimeLimit(packageName: String, minutes: Int) {
        context.getSharedPreferences("app_limits", Context.MODE_PRIVATE).edit {
            if (minutes > 0) putInt(packageName, minutes) else remove(packageName)
        }
        loadApps()
    }

    fun getAppUsage(packageName: String, inMinutes: Boolean = false): Long {
        val prefs = context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE)
        val used = prefs.getLong(packageName, 0L)

        if (inMinutes)
            return TimeUnit.MILLISECONDS.toMinutes(used)

        return used
    }
}
