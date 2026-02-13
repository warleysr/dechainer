package io.github.warleysr.dechainer

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import java.util.concurrent.TimeUnit

@SuppressLint("AccessibilityPolicy")
class DechainerAccessibilityService : AccessibilityService() {

    companion object {
        val accessedActivities = mutableStateListOf<ActivityLog>()
        
        data class ActivityLog(
            val packageName: String,
            val className: String,
            val timestamp: Long = System.currentTimeMillis()
        )
        
        private fun addLog(packageName: String, className: String) {
            if (accessedActivities.any { it.packageName == packageName && it.className == className }) {
                accessedActivities.removeIf { it.packageName == packageName && it.className == className }
            }
            accessedActivities.add(0, ActivityLog(packageName, className))
            if (accessedActivities.size > 100) accessedActivities.removeLast()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: return

            // Check usage limits
            val prefs = getSharedPreferences("app_limits", Context.MODE_PRIVATE)
            val limitMinutes = prefs.getInt(packageName, 0)
            
            if (limitMinutes > 0) {
                val statsManager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager?
                val stats = statsManager?.queryAndAggregateUsageStats(
                    System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1), 
                    System.currentTimeMillis()
                )
                
                val appStats = stats?.get(packageName)
                if (appStats != null) {
                    val usedMinutes = TimeUnit.MILLISECONDS.toMinutes(appStats.totalTimeInForeground)
                    if (usedMinutes >= limitMinutes) {
                        startActivity(Intent(this, TimeUpActivity::class.java).apply { 
                            flags = FLAG_ACTIVITY_NEW_TASK 
                        })
                    }
                }
            }

            if (!className.endsWith("Activity", ignoreCase = true)) return

            addLog(packageName, className)

            val blockerPrefs = getSharedPreferences("activity_blocker_prefs", Context.MODE_PRIVATE)
            val blockedActivities = blockerPrefs.getStringSet("blocked_activities", emptySet()) ?: emptySet()

            if (blockedActivities.contains(className)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                Toast.makeText(this, getString(R.string.activity_blocked_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInterrupt() {}
}
