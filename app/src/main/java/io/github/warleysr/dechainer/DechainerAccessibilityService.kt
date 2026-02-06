package io.github.warleysr.dechainer

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf

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

            if (!className.endsWith("Activity", ignoreCase = true)) return

            addLog(packageName, className)

            val prefs = getSharedPreferences("activity_blocker_prefs", Context.MODE_PRIVATE)
            val blockedActivities = prefs.getStringSet("blocked_activities", emptySet()) ?: emptySet()

            if (blockedActivities.contains(className)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                Toast.makeText(this, getString(R.string.activity_blocked_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onInterrupt() {}
}

