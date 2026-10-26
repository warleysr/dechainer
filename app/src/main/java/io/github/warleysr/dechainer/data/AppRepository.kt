package io.github.warleysr.dechainer.data

import android.content.Context
import android.content.RestrictionEntry
import android.content.RestrictionsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.SuspensionReason
import io.github.warleysr.dechainer.models.TimeLimit
import io.github.warleysr.dechainer.models.TimeWindow
import java.util.concurrent.TimeUnit

object AppRepository {
    private const val HIDDEN_APPS_PREFS = "hidden_apps_prefs"
    private const val KEY_HIDDEN_PACKAGES = "hidden_packages"

    private val context = DechainerApplication.getInstance()
    private val packageManager = context.packageManager
    private val dpm get() = DeviceAdmin.policyManager
    private val adminName get() = DeviceAdmin.component

    private val hiddenAppsPrefs
        get() = context.getSharedPreferences(HIDDEN_APPS_PREFS, Context.MODE_PRIVATE)

    private val dailyLimitsPrefs
        get() = context.getSharedPreferences(AppTimeLimits.PREFS_NAME, Context.MODE_PRIVATE)

    private val weeklyLimitsPrefs
        get() = context.getSharedPreferences(AppTimeLimits.WEEKLY_PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedApps: List<AppItem>? = null
    private val cacheLock = Any()

    fun getApps(forceRefresh: Boolean = false): List<AppItem> {
        if (!forceRefresh) {
            cachedApps?.let { return it }
        }
        synchronized(cacheLock) {
            if (!forceRefresh) {
                cachedApps?.let { return it }
            }
            val fresh = loadAppsFromSystem()
            cachedApps = fresh
            return fresh
        }
    }

    private fun loadAppsFromSystem(): List<AppItem> {
        val limitsPrefs = dailyLimitsPrefs
        val weeklyPrefs = weeklyLimitsPrefs
        val reopenPrefs = context.getSharedPreferences("reopen_times", Context.MODE_PRIVATE)
        val ratingsPrefs = context.getSharedPreferences("app_ratings", Context.MODE_PRIVATE)
        val timeWindowsPrefs = context.getSharedPreferences(AppTimeWindows.PREFS_NAME, Context.MODE_PRIVATE)

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
                    timeLimit = AppTimeLimits.read(limitsPrefs, weeklyPrefs, packageName),
                    reopeningSeconds = reopenPrefs.getInt(packageName, 0),
                    timeWindows = AppTimeWindows.decode(timeWindowsPrefs.getString(packageName, null)),
                    isSuspended = isSuspended,
                    hasExplicitContent = ratingsPrefs.getBoolean(packageName, false)
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList()
    }

    fun invalidateCache() {
        synchronized(cacheLock) {
            cachedApps = null
        }
    }

    private fun updateCachedApp(packageName: String, transform: (AppItem) -> AppItem) {
        synchronized(cacheLock) {
            cachedApps = cachedApps?.map { if (it.packageName == packageName) transform(it) else it }
        }
    }

    fun setAppHidden(packageName: String, hidden: Boolean) {
        var success = dpm.setApplicationHidden(adminName, packageName, hidden)
        if (!success && hidden) {
            // In Android 14+, DevicePolicyEngine may retain an old 'true' entry if the app
            // was uninstalled while hidden. Toggling false -> true forces the engine
            // to re-apply the policy to PackageManagerService.
            dpm.setApplicationHidden(adminName, packageName, false)
            success = dpm.setApplicationHidden(adminName, packageName, true)
            Log.d("DechainerPolicyUpdate", "setApplicationHidden retry after reset: package=$packageName accepted=$success")
        }
        // `success` reflects the real PackageManager state at the time of the call
        // (DevicePolicyManagerService double-checks isApplicationHidden() before
        // returning). Only persist the new state locally when it's true - otherwise
        // the cache/prefs would drift from reality and invert the next toggle.
        Log.d("DechainerPolicyUpdate", "setApplicationHidden request: package=$packageName hidden=$hidden accepted=$success")
        if (!success) {
            invalidateCache()
            return
        }

        updateCachedApp(packageName) { it.copy(isHidden = hidden) }

        hiddenAppsPrefs.edit {
            if (hidden) {
                putStringSet(
                    KEY_HIDDEN_PACKAGES,
                    (hiddenAppsPrefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()) + packageName
                )
            } else {
                putStringSet(
                    KEY_HIDDEN_PACKAGES,
                    (hiddenAppsPrefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet()) ?: emptySet()) - packageName
                )
            }
        }
    }

    fun reapplyHiddenIfNeeded(packageName: String) {
        val wasHidden = hiddenAppsPrefs.getStringSet(KEY_HIDDEN_PACKAGES, emptySet())
            ?.contains(packageName) == true
        Log.d("DechainerPolicyUpdate", "reapplyHiddenIfNeeded: package=$packageName wasHidden=$wasHidden")
        if (!wasHidden) return

        var success = dpm.setApplicationHidden(adminName, packageName, true)
        if (!success) {
            Log.d("DechainerPolicyUpdate", "reapplyHiddenIfNeeded: retrying with DPC reset false -> true for $packageName")
            dpm.setApplicationHidden(adminName, packageName, false)
            success = dpm.setApplicationHidden(adminName, packageName, true)
        }
        Log.d("DechainerPolicyUpdate", "reapplyHiddenIfNeeded request: package=$packageName accepted=$success")
        if (success) {
            updateCachedApp(packageName) { it.copy(isHidden = true) }
        } else {
            invalidateCache()
        }
    }

    fun getApp(packageName: String): AppItem? {
        return getApps().firstOrNull { it.packageName == packageName }
    }

    fun setAppSuspended(packageName: String, suspended: Boolean) {
        dpm.setPackagesSuspended(adminName, arrayOf(packageName), suspended)
        updateCachedApp(packageName) { it.copy(isSuspended = suspended) }
        if (suspended) {
            SuspensionLogRepository.log(context, packageName, SuspensionReason.MANUAL)
        }
    }

    fun setUninstallBlocked(packageName: String, block: Boolean) {
        dpm.setUninstallBlocked(adminName, packageName, block)
        updateCachedApp(packageName) { it.copy(isUninstallBlocked = block) }
    }

    fun setAppTimeLimit(packageName: String, limit: TimeLimit) {
        AppTimeLimits.write(dailyLimitsPrefs, weeklyLimitsPrefs, packageName, limit)
        updateCachedApp(packageName) { it.copy(timeLimit = limit) }
    }

    fun getAppTimeLimits(): Map<String, TimeLimit> = AppTimeLimits.readAll(dailyLimitsPrefs, weeklyLimitsPrefs)

    fun getAppUsage(packageName: String, inMinutes: Boolean = false): Long {
        val used = context.getSharedPreferences("internal_usage_stats", Context.MODE_PRIVATE)
            .getLong(packageName, 0L)
        return if (inMinutes) TimeUnit.MILLISECONDS.toMinutes(used) else used
    }

    fun setAppReopenTime(packageName: String, seconds: Int) {
        context.getSharedPreferences("reopen_times", Context.MODE_PRIVATE).edit {
            if (seconds > 0) putInt(packageName, seconds) else remove(packageName)
        }
        updateCachedApp(packageName) { it.copy(reopeningSeconds = seconds) }
    }

    fun getAppReopenTime(packageName: String): Int {
        return context.getSharedPreferences("reopen_times", Context.MODE_PRIVATE).getInt(packageName, 0)
    }

    fun setAppTimeWindows(packageName: String, windows: List<TimeWindow>) {
        context.getSharedPreferences(AppTimeWindows.PREFS_NAME, Context.MODE_PRIVATE).edit {
            if (windows.isEmpty()) remove(packageName) else putString(packageName, AppTimeWindows.encode(windows))
        }
        updateCachedApp(packageName) { it.copy(timeWindows = windows) }
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
