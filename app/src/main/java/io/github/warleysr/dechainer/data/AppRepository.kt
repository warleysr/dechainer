package io.github.warleysr.dechainer.data

import android.content.Context
import android.content.RestrictionEntry
import android.content.RestrictionsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.content.edit
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.models.AppItem
import java.util.concurrent.TimeUnit

object AppRepository {
    private val context = DechainerApplication.getInstance()
    private val packageManager = context.packageManager
    private val dpm get() = DeviceAdmin.policyManager
    private val adminName get() = DeviceAdmin.component

    fun getApps(): List<AppItem> {
        val limitsPrefs = context.getSharedPreferences("app_limits", Context.MODE_PRIVATE)
        val reopenPrefs = context.getSharedPreferences("reopen_times", Context.MODE_PRIVATE)
        val ratingsPrefs = context.getSharedPreferences("app_ratings", Context.MODE_PRIVATE)

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
                    isSuspended = isSuspended,
                    hasExplicitContent = ratingsPrefs.getBoolean(packageName, false)
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun setAppHidden(packageName: String, hidden: Boolean) {
        dpm.setApplicationHidden(adminName, packageName, hidden)
    }

    fun setAppSuspended(packageName: String, suspended: Boolean) {
        dpm.setPackagesSuspended(adminName, arrayOf(packageName), suspended)
    }

    fun setUninstallBlocked(packageName: String, block: Boolean) {
        dpm.setUninstallBlocked(adminName, packageName, block)
    }

    fun setAppTimeLimit(packageName: String, minutes: Int) {
        context.getSharedPreferences("app_limits", Context.MODE_PRIVATE).edit {
            if (minutes > 0) putInt(packageName, minutes) else remove(packageName)
        }
    }

    fun getAppUsage(packageName: String, inMinutes: Boolean = false): Long {
        val used = context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE)
            .getLong(packageName, 0L)
        return if (inMinutes) TimeUnit.MILLISECONDS.toMinutes(used) else used
    }

    fun setAppReopenTime(packageName: String, seconds: Int) {
        context.getSharedPreferences("reopen_times", Context.MODE_PRIVATE).edit {
            if (seconds > 0) putInt(packageName, seconds) else remove(packageName)
        }
    }

    fun getAppReopenTime(packageName: String): Int {
        return context.getSharedPreferences("reopen_times", Context.MODE_PRIVATE).getInt(packageName, 0)
    }

    fun getApplicationRestrictions(packageName: String): Bundle {
        return dpm.getApplicationRestrictions(adminName, packageName)
    }

    fun setApplicationRestrictions(packageName: String, restrictions: Bundle) {
        val current = dpm.getApplicationRestrictions(adminName, packageName)
        current.putAll(restrictions)
        dpm.setApplicationRestrictions(adminName, packageName, current)
    }

    fun getAvailableRestrictions(packageName: String): List<RestrictionEntry> {
        val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager

        try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            if (appInfo.metaData == null) return emptyList()
        } catch (_: PackageManager.NameNotFoundException) {
            return emptyList()
        }

        val restrictions = rm.getManifestRestrictions(packageName)
        return restrictions?.toList() ?: emptyList()
    }
}
