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
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableStateListOf
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import io.github.warleysr.dechainer.activities.BlockedWordActivity
import io.github.warleysr.dechainer.activities.ReopeningLimitActivity
import io.github.warleysr.dechainer.activities.TimeUpActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

@SuppressLint("AccessibilityPolicy")
class DechainerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var sessionStartTime: Long = 0
    private var lastCheckDate: String = LocalDate.now().toString()
    private val lastClosedTimes = HashMap<String, Long>();

    private lateinit var limitPrefs: SharedPreferences
    private lateinit var usagePrefs: SharedPreferences
    private lateinit var reopenPrefs: SharedPreferences
    private lateinit var blockedWordsPrefs: SharedPreferences

    private var forbiddenPatterns: Map<String, Regex> = emptyMap()
    private var targetPackages: Set<String> = emptySet()

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile private var isBlocking = false

    private val packageReceiver = object : BroadcastReceiver() {
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

            val dpm =
                applicationContext.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(applicationContext, DechainerDeviceAdminReceiver::class.java)
            if (!dpm.isAdminActive(admin)) return

            dpm.setPackagesSuspended(admin, arrayOf(packageName), true)
        }
    }

    private var lastForegroundPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    lastForegroundPackage = currentPackage
                    stopTrackingAndSave(screenOff = true)
                    currentPackage = null
                }

                Intent.ACTION_SCREEN_ON -> {
                    lastForegroundPackage?.let {
                        currentPackage = it
                        sessionStartTime = SystemClock.elapsedRealtime()
                        checkDateReset()
                        startTracking(it)
                    }
                }
            }
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (prefs) {
            limitPrefs -> {
                if (key == currentPackage) {
                    handler.removeCallbacks(blockRunnable)
                    currentPackage?.let { startTracking(it) }
                }
            }

            blockedWordsPrefs -> {
                updateForbiddenPatterns()
            }
        }
    }

    private fun updateForbiddenPatterns() {
        val words = blockedWordsPrefs.getStringSet("blocked_words", emptySet()) ?: emptySet()
        forbiddenPatterns = words.associateWith { word ->
            Regex(
                "(?<![\\p{L}\\p{N}_])${Regex.escape(word)}(?![\\p{L}\\p{N}_])",
                RegexOption.IGNORE_CASE
            )
        }
        targetPackages = blockedWordsPrefs.getStringSet("target_packages", emptySet()) ?: emptySet()
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

        private const val DEBOUNCE_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        limitPrefs = getSharedPreferences("app_limits", MODE_PRIVATE)
        usagePrefs = getSharedPreferences("internal_usage_stats", MODE_PRIVATE)
        reopenPrefs = getSharedPreferences("reopen_times", MODE_PRIVATE)
        blockedWordsPrefs = getSharedPreferences("blocked_words_prefs", MODE_PRIVATE)

        limitPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        blockedWordsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)

        updateForbiddenPatterns()

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, packageFilter)

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, screenFilter)
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        unregisterReceiver(screenReceiver)
        limitPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        blockedWordsPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
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
            if (newPackage == "com.android.systemui") return
            if (className.contains("InputMethodService", ignoreCase = true)
                || className.contains("SoftInputWindow", ignoreCase = true)
            ) return

            if (newPackage != currentPackage) {
                stopTrackingAndSave()
                currentPackage = newPackage
                sessionStartTime = SystemClock.elapsedRealtime()
                checkDateReset()
                startTracking(newPackage)
            }

            if (className.contains("Activity", ignoreCase = true)) {
                addLog(newPackage, className)
                val blockerPrefs =
                    getSharedPreferences("activity_blocker_prefs", MODE_PRIVATE)
                val blockedActivities =
                    blockerPrefs.getStringSet("blocked_activities", emptySet()) ?: emptySet()

                if (blockedActivities.contains(className))
                    performGlobalAction(GLOBAL_ACTION_BACK)
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val pkg = currentPackage ?: return
            if (!targetPackages.contains(pkg)) return

            val text = buildScreenText(event) ?: return
            val forbiddenWord = checkForbiddenWord(text) ?: return

            if (isBlocking) return
            isBlocking = true

            Log.d("Dechainer", "FORBIDDEN WORD: $forbiddenWord")
            performGlobalAction(GLOBAL_ACTION_BACK)

            serviceScope.launch {
                delay(DEBOUNCE_MS)
                withContext(Dispatchers.Main) {
                    val intent = Intent(
                        this@DechainerAccessibilityService, BlockedWordActivity::class.java
                    ).apply {
                        flags = FLAG_ACTIVITY_NEW_TASK
                        putExtra("word", forbiddenWord)
                    }
                    startActivity(intent)
                }
                delay(DEBOUNCE_MS)
                isBlocking = false
            }
        }
    }

    private fun buildScreenText(event: AccessibilityEvent): String? {
        val sb = StringBuilder()

        event.text.forEach { sb.append(it).append(' ') }

        event.source?.also { root ->
            fun traverse(node: AccessibilityNodeInfo?) {
                node ?: return
                node.text?.let { sb.append(it).append(' ') }
                node.contentDescription?.let { sb.append(it).append(' ') }
                for (i in 0 until node.childCount) traverse(node.getChild(i))
            }
            traverse(root)
            root.recycle()
        }

        return sb.toString().trim().takeIf { it.isNotBlank() }
    }

    private fun checkForbiddenWord(text: String): String? {
        forbiddenPatterns.forEach { (word: String, regex: Regex) ->
            if (regex.containsMatchIn(text))
                return word
        }
        return null
    }

    private fun startTracking(pkg: String) {
        val limitMinutes = limitPrefs.getInt(pkg, 0)
        val remainingSecondsReopening = getRemainingSecondsToReopen(pkg)
        if (limitMinutes <= 0 && remainingSecondsReopening <= 0) return

        val alreadyUsedMillis = usagePrefs.getLong(pkg, 0L)
        val limitMillis = TimeUnit.MINUTES.toMillis(limitMinutes.toLong())
        val remainingMillis = limitMillis - alreadyUsedMillis

        if (remainingSecondsReopening > 0)
            executeBlocking(reopening = true, remainingSeconds = remainingSecondsReopening)
        else {
            if (remainingMillis <= 0)
                executeBlocking()
            else
                handler.postDelayed(blockRunnable, remainingMillis)
        }
    }

    private fun stopTrackingAndSave(screenOff: Boolean = false) {
        handler.removeCallbacks(blockRunnable)
        val pkg = currentPackage ?: return

        if (getRemainingSecondsToReopen(pkg) == 0 && !screenOff)
            lastClosedTimes[pkg] = SystemClock.elapsedRealtime()

        if (sessionStartTime == 0L) return
        if (limitPrefs.getInt(pkg, 0) == 0) return

        val currentSessionMillis = SystemClock.elapsedRealtime() - sessionStartTime
        val total = usagePrefs.getLong(pkg, 0L) + currentSessionMillis

        usagePrefs.edit { putLong(pkg, total) }
        sessionStartTime = 0
    }

    private fun executeBlocking(reopening: Boolean = false, remainingSeconds: Int = 0) {
        val pkg = currentPackage ?: return

        stopTrackingAndSave()
        currentPackage = null

        val appName = try {
            packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
        } catch (e: Exception) { pkg }

        val activityClass = if (reopening) ReopeningLimitActivity::class.java else TimeUpActivity::class.java
        val limit = if (reopening) remainingSeconds else limitPrefs.getInt(pkg, 0)

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