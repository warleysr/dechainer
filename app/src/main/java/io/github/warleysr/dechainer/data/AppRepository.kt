package io.github.warleysr.dechainer.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import io.github.warleysr.dechainer.models.AppItem

object AppRepository {
    private val context = DechainerApplication.getInstance()
    private val packageManager = context.packageManager
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminName = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

    fun getApps(): List<AppItem> {
        val limitsPrefs = context.getSharedPreferences("app_limits", Context.MODE_PRIVATE)
        val reopenPrefs = context.getSharedPreferences("reopen_times", Context.MODE_PRIVATE)
        
        val installedApps = packageManager.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)

        return installedApps.asSequence()
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                val packageName = appInfo.packageName
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                
                val isHidden = try { dpm.isApplicationHidden(adminName, packageName) } catch (_: Exception) { false }
                val isUninstallBlocked = try { dpm.isUninstallBlocked(adminName, packageName) } catch (_: Exception) { false }
                val isSuspended = try { dpm.isPackageSuspended(adminName, packageName) } catch (_: Exception) { false }

                AppItem(
                    name = appInfo.loadLabel(packageManager).toString(),
                    packageName = packageName,
                    icon = appInfo.loadIcon(packageManager),
                    isSystem = isSystem,
                    isHidden = isHidden,
                    isUninstallBlocked = isUninstallBlocked,
                    timeLimitMinutes = limitsPrefs.getInt(packageName, 0),
                    reopeningSeconds = reopenPrefs.getInt(packageName, 0),
                    isSuspended = isSuspended
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }
}
