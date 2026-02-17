package io.github.warleysr.dechainer

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableStateListOf
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import kotlin.math.max

@SuppressLint("AccessibilityPolicy")
class DechainerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var blockedPackageReopening: String? = null
    private var sessionStartTime: Long = 0
    private var lastCheckDate: String = LocalDate.now().toString()
    private val lastClosedTimes = HashMap<String, Long>();

    private lateinit var limitPrefs: SharedPreferences
    private lateinit var usagePrefs: SharedPreferences
    private lateinit var reopenPrefs: SharedPreferences

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
            val packageName = intent.data?.encodedSchemeSpecificPart ?: return

            val manager = BrowserRestrictionsManager(applicationContext)
            val isBrowser = manager.isBrowser(packageName)

            if (!isBrowser) return

            val supportsRestrictions = manager.supportsRestrictions(packageName)
            if (supportsRestrictions) {
                manager.applyRestrictions()
                return
            }

            val dpm = applicationContext.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(applicationContext, DechainerDeviceAdminReceiver::class.java)
            if (!dpm.isAdminActive(admin)) return

            dpm.setPackagesSuspended(admin, arrayOf(packageName), true)
        }
    }

    private val limitListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == currentPackage) {
            handler.removeCallbacks(blockRunnable)
            currentPackage?.let { startTracking(it) }
        }
    }

    private val blockRunnable = Runnable {
        executeBlocking()
    }

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
            if (accessedActivities.size > 100) accessedActivities.removeAt(accessedActivities.lastIndex)
        }
    }

    override fun onCreate() {
        super.onCreate()
        limitPrefs = getSharedPreferences("app_limits", MODE_PRIVATE)
        usagePrefs = getSharedPreferences("internal_usage_stats", MODE_PRIVATE)
        reopenPrefs = getSharedPreferences("reopen_times", MODE_PRIVATE)
        limitPrefs.registerOnSharedPreferenceChangeListener(limitListener)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }
        registerReceiver(stateReceiver, filter)
    }

    override fun onDestroy() {
        unregisterReceiver(stateReceiver)
        limitPrefs.unregisterOnSharedPreferenceChangeListener(limitListener)
        stopTrackingAndSave()
        super.onDestroy()
    }

    override fun onInterrupt() {
        handler.removeCallbacks(blockRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName == packageName) return

        if (event.className == "com.android.settings.SubSettings") {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                preventDisableService(rootNode)
                rootNode.recycle()
            }
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val newPackage = event.packageName?.toString() ?: return
            val className = event.className?.toString() ?: return

            if (newPackage != currentPackage) {
                stopTrackingAndSave()
                currentPackage = newPackage
                sessionStartTime = SystemClock.elapsedRealtime()
                checkDateReset()
                startTracking(newPackage)
            }

            if (className.endsWith("Activity", ignoreCase = true)) {
                addLog(newPackage, className)
                val blockerPrefs =
                    getSharedPreferences("activity_blocker_prefs", MODE_PRIVATE)
                val blockedActivities =
                    blockerPrefs.getStringSet("blocked_activities", emptySet()) ?: emptySet()

                if (blockedActivities.contains(className)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }
        }
    }

    private fun startTracking(pkg: String) {
        val limitMinutes = limitPrefs.getInt(pkg, 0)
        if (limitMinutes <= 0) return

        val alreadyUsedMillis = usagePrefs.getLong(pkg, 0L)
        val limitMillis = TimeUnit.MINUTES.toMillis(limitMinutes.toLong())
        val remainingMillis = limitMillis - alreadyUsedMillis

        if (remainingMillis <= 0) {
            executeBlocking()
        } else {
            val remainingSeconds = getRemainingSecondsToReopen(pkg)
            if (remainingSeconds > 0)
                executeBlocking(reopening = true, remainingSeconds = remainingSeconds)
            else
                handler.postDelayed(blockRunnable, remainingMillis)
        }
    }

    private fun stopTrackingAndSave() {
        handler.removeCallbacks(blockRunnable)
        val pkg = currentPackage ?: return

        if (getRemainingSecondsToReopen(pkg) == 0)
            lastClosedTimes[pkg] = SystemClock.elapsedRealtime()

        if (sessionStartTime == 0L) return

        val currentSessionMillis = SystemClock.elapsedRealtime() - sessionStartTime
        val total = usagePrefs.getLong(pkg, 0L) + currentSessionMillis

        usagePrefs.edit { putLong(pkg, total) }
        sessionStartTime = 0
    }

    private fun executeBlocking(reopening: Boolean = false, remainingSeconds: Int = 0) {
        val pkg = currentPackage ?: return
        val appName = try {
            packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
        } catch (e: Exception) {
            pkg
        }

        var activityClass: Any?
        var limit: Int?

        if (reopening) {
            activityClass = ReopeningLimitActivity::class.java
            limit = remainingSeconds
        } else {
            activityClass = TimeUpActivity::class.java
            limit = limitPrefs.getInt(pkg, 0)
        }

        startActivity(Intent(this, activityClass).apply {
            flags = FLAG_ACTIVITY_NEW_TASK
            putExtra("appName", appName)
            putExtra("limit", limit)
        })
    }

    private fun checkDateReset() {
        val today = LocalDate.now().toString()
        if (today != lastCheckDate) {
            usagePrefs.edit { clear() }
            lastCheckDate = today
        }
    }

    private fun preventDisableService(node: AccessibilityNodeInfo?) {
        if (node == null) return
        if (node.text != null && node.text.toString() == getString(R.string.accessibility_description)) {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            preventDisableService(child)
            child?.recycle()
        }
    }

    private fun getRemainingSecondsToReopen(pkg: String): Int {
        val reopenSeconds = reopenPrefs.getInt(pkg, 0)
        val lastClosedTime = lastClosedTimes.getOrDefault(pkg, 0L)
        if (reopenSeconds > 0 && lastClosedTime > 0) {
            val elapsed = SystemClock.elapsedRealtime() - lastClosedTime
            val elapsedSeconds = TimeUnit.MILLISECONDS.toSeconds(elapsed)
            if (elapsedSeconds <= reopenSeconds)
                return max(reopenSeconds - elapsedSeconds.toInt(), 0)
        }
        return 0
    }

}