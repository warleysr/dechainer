package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.DechainerApplication
import androidx.core.content.edit

data class GroupedActivityLog(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val activities: List<DechainerAccessibilityService.Companion.ActivityLog>
)

class ActivityBlockerViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val packageManager = context.packageManager
    
    val blockedActivities = mutableStateListOf<String>()
    
    init {
        loadBlockedActivities()
    }

    private fun loadBlockedActivities() {
        val prefs = context.getSharedPreferences("activity_blocker_prefs", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet("blocked_activities", emptySet()) ?: emptySet()
        blockedActivities.clear()
        blockedActivities.addAll(blocked.sorted())
    }

    fun addBlockedActivity(className: String) {
        if (className.isNotBlank() && !blockedActivities.contains(className)) {
            blockedActivities.add(className)
            saveBlockedActivities()
        }
    }

    fun removeBlockedActivity(className: String) {
        if (blockedActivities.remove(className)) {
            saveBlockedActivities()
        }
    }

    private fun saveBlockedActivities() {
        val prefs = context.getSharedPreferences("activity_blocker_prefs", Context.MODE_PRIVATE)
        prefs.edit { putStringSet("blocked_activities", blockedActivities.toSet()) }
    }

    fun getGroupedAccessedActivities(): List<GroupedActivityLog> {
        return DechainerAccessibilityService.accessedActivities
            .groupBy { it.packageName }
            .map { (packageName, logs) ->
                val appInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (_: Exception) { null }
                
                GroupedActivityLog(
                    packageName = packageName,
                    appName = appInfo?.loadLabel(packageManager)?.toString() ?: packageName,
                    icon = appInfo?.loadIcon(packageManager) ?: context.packageManager.defaultActivityIcon,
                    activities = logs
                )
            }
            .sortedBy { it.appName.lowercase() }
    }
}
