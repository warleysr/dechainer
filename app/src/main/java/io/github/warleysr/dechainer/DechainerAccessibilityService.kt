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
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import androidx.core.content.edit
import io.github.warleysr.dechainer.activities.AccessibilityRequestActivity
import io.github.warleysr.dechainer.activities.BlockedWordActivity
import io.github.warleysr.dechainer.activities.NsfwContentBlockedActivity
import io.github.warleysr.dechainer.activities.OutsideTimeWindowActivity
import io.github.warleysr.dechainer.activities.ReopeningLimitActivity
import io.github.warleysr.dechainer.activities.TimeUpActivity
import io.github.warleysr.dechainer.data.AppGroupRepository
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.AppTimeLimits
import io.github.warleysr.dechainer.data.AppTimeWindows
import io.github.warleysr.dechainer.data.BrowserRestrictionsManager
import io.github.warleysr.dechainer.data.ColorFilterController
import io.github.warleysr.dechainer.data.ColorFilterSettings
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.data.PlayStoreRatingFetcher
import io.github.warleysr.dechainer.data.UsageWarningSettings
import io.github.warleysr.dechainer.data.VisualBlockingSettings
import io.github.warleysr.dechainer.data.VisualBlockingSuspensionTracker
import io.github.warleysr.dechainer.models.AppGroup
import io.github.warleysr.dechainer.models.ColorFilterMode
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.models.UsageAlertStage
import io.github.warleysr.dechainer.notifications.FocusService
import io.github.warleysr.dechainer.notifications.UsageWarningNotifier
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.utils.NightLightOverlay
import io.github.warleysr.dechainer.utils.NsfwContentDetector
import org.jsoup.HttpStatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors
import kotlin.math.max

@SuppressLint("AccessibilityPolicy")
class DechainerAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null
    private var sessionStartTime: Long = 0
    private var lastCheckDate: String = LocalDate.now().toString()
    private val lastClosedTimes = HashMap<String, Long>();

    private lateinit var limitPrefs: SharedPreferences
    private lateinit var weeklyLimitPrefs: SharedPreferences
    private lateinit var usagePrefs: SharedPreferences
    private lateinit var reopenPrefs: SharedPreferences
    private lateinit var timeWindowPrefs: SharedPreferences
    private lateinit var groupsPrefs: SharedPreferences
    private lateinit var groupUsagePrefs: SharedPreferences
    private lateinit var blockedWordsPrefs: SharedPreferences
    private lateinit var securityPrefs: SharedPreferences
    private lateinit var ratingPrefs: SharedPreferences
    private lateinit var visualBlockingPrefs: SharedPreferences
    private lateinit var usageWarningPrefs: SharedPreferences
    private lateinit var colorFilterPrefs: SharedPreferences

    private var forbiddenPatterns: Map<String, Regex> = emptyMap()
    private var passiveForbiddenPatterns: Map<String, Map<String, Regex>> = emptyMap()
    private var targetPackages: Set<String> = emptySet()

    private var wasTypingActively = false
    private var lastActiveTypedText = ""

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // NSFW image/video monitoring: lazily created so the model is only loaded into memory
    // once the user actually opens the monitored app, and reused afterwards to avoid the
    // cost of reloading/re-initializing the interpreter on every screen.
    private var nsfwDetector: NsfwContentDetector? = null
    private var lastNsfwScanElapsedMs = 0L

    // Cached copies of visualBlockingPrefs (VisualBlockingSettings), refreshed in
    // updateVisualBlockingSettings() so onAccessibilityEvent doesn't hit SharedPreferences on
    // every event — kept in sync live via prefsListener while the settings screen is open.
    private var nsfwEnabled: Boolean = false
    private var nsfwTargetPackages: Set<String> = VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
    private var nsfwCategories: Set<String> = VisualBlockingSettings.DEFAULT_CATEGORIES
    private var nsfwThreshold: Float = VisualBlockingSettings.DEFAULT_THRESHOLD
    private var nsfwSuspendEnabled: Boolean = false
    private var nsfwSuspendBlockCount: Int = VisualBlockingSettings.DEFAULT_SUSPEND_BLOCK_COUNT
    private var nsfwSuspendWindowMinutes: Int = VisualBlockingSettings.DEFAULT_SUSPEND_WINDOW_MINUTES
    private var nsfwSuspendDurationMinutes: Int = VisualBlockingSettings.DEFAULT_SUSPEND_DURATION_MINUTES

    // Sliding-window block counter + persisted deadlines of the timed suspensions above.
    private lateinit var nsfwSuspensionTracker: VisualBlockingSuspensionTracker

    private var usageWarningEnabled: Boolean = UsageWarningSettings.DEFAULT_ENABLED
    private var usageWarningStages: List<UsageAlertStage> = emptyList()

    private var currentWarnKey: String? = null

    private var activeStageId: String? = null

    // Pending "lift the suspension" callbacks, keyed by package so a new suspension of the same
    // app replaces the previous timer instead of racing it.
    private val nsfwSuspensionReleases = HashMap<String, Runnable>()

    // Same idea for the impulse block, which suspends its whole app list as one unit.
    private var impulseReleaseRunnable: Runnable? = null

    // NsfwContentDetector may use a GPU delegate, which LiteRT requires to be created and
    // driven from the same thread — this dedicated single-thread dispatcher is what gives it
    // that guarantee across scans (Dispatchers.Default alone doesn't pin coroutines to a thread).
    private val nsfwDispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "NsfwDetector") }
        .asCoroutineDispatcher()

    // Set on the main thread before a scan starts and cleared from whichever thread finishes
    // it (background inference or the screenshot callback), so it must stay volatile.
    @Volatile
    private var nsfwScanInFlight = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
            val packageName = intent.data?.encodedSchemeSpecificPart ?: return

            val isUpdate = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (!isUpdate) {
                AppRepository.reapplyHiddenIfNeeded(packageName)
            }

            val manager = BrowserRestrictionsManager(applicationContext)
            val isBrowser = manager.isBrowser(packageName)

            if (isBrowser) {
                val supportsRestrictions = manager.supportsRestrictions(packageName)
                if (supportsRestrictions) {
                    manager.applyRestrictions(installed = true)
                    return
                }

                suspendPackage(packageName)
            }
            else {
                val isTorrentApp = manager.isTorrentApp(packageName)
                if (isTorrentApp && SecurityManager.isBlockTorrentsEnabled(applicationContext))
                    suspendPackage(packageName)
            }

            if (!isUpdate) {
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val info = PlayStoreRatingFetcher.fetch(packageName)


                        if (info.hasExplicitContent && info.contentRating == "Rated 18+")
                            withContext(Dispatchers.Main) {
                                suspendPackage(packageName)
                            }

                        ratingPrefs.edit { putBoolean(packageName, info.hasExplicitContent) }
                    } catch (e: HttpStatusException) {
                        e.printStackTrace()
                        if (e.statusCode == 404) {
                            ratingPrefs.edit { putBoolean(packageName, false) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
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
                    syncColorFilters()
                    lastForegroundPackage?.let {
                        currentPackage = it
                        sessionStartTime = SystemClock.elapsedRealtime()
                        checkDateReset()
                        startTracking(it)
                    }
                }

                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> syncColorFilters()
            }
        }
    }

    private val colorFilterObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            syncColorFilters()
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (prefs) {
            limitPrefs, weeklyLimitPrefs -> {
                if (key == currentPackage) {
                    handler.removeCallbacks(blockRunnable)
                    currentPackage?.let { startTracking(it) }
                }
            }

            timeWindowPrefs -> {
                if (key == currentPackage) {
                    handler.removeCallbacks(blockRunnable)
                    handler.removeCallbacks(windowEndRunnable)
                    currentPackage?.let { startTracking(it) }
                }
            }

            groupsPrefs -> {
                handler.removeCallbacks(blockRunnable)
                handler.removeCallbacks(windowEndRunnable)
                currentPackage?.let { startTracking(it) }
            }

            blockedWordsPrefs -> {
                updateForbiddenPatterns()
            }

            visualBlockingPrefs -> {
                updateVisualBlockingSettings()
            }

            colorFilterPrefs -> {
                syncColorFilters()
            }

            usageWarningPrefs -> {
                updateUsageWarningSettings()
                if (!usageWarningEnabled) {
                    cancelActiveUsageWarning()
                }
                currentPackage?.let { startTracking(it) }
            }

            securityPrefs -> {
                // Written by SecurityManager both when the panic button starts a block and when
                // the block is found to have run out.
                if (key == "impulse_block_active") syncImpulseSuspension()
            }
        }
    }

    private fun updateVisualBlockingSettings() {
        nsfwEnabled = visualBlockingPrefs.getBoolean(VisualBlockingSettings.KEY_ENABLED, false)
        nsfwTargetPackages = visualBlockingPrefs.getStringSet(
            VisualBlockingSettings.KEY_TARGET_PACKAGES, VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
        ) ?: VisualBlockingSettings.DEFAULT_TARGET_PACKAGES
        nsfwCategories = visualBlockingPrefs.getStringSet(
            VisualBlockingSettings.KEY_CATEGORIES, VisualBlockingSettings.DEFAULT_CATEGORIES
        ) ?: VisualBlockingSettings.DEFAULT_CATEGORIES
        nsfwThreshold = visualBlockingPrefs.getFloat(
            VisualBlockingSettings.KEY_THRESHOLD, VisualBlockingSettings.DEFAULT_THRESHOLD
        )
        nsfwSuspendEnabled = visualBlockingPrefs.getBoolean(
            VisualBlockingSettings.KEY_SUSPEND_ENABLED, false
        )
        nsfwSuspendBlockCount = visualBlockingPrefs.getInt(
            VisualBlockingSettings.KEY_SUSPEND_BLOCK_COUNT,
            VisualBlockingSettings.DEFAULT_SUSPEND_BLOCK_COUNT
        )
        nsfwSuspendWindowMinutes = visualBlockingPrefs.getInt(
            VisualBlockingSettings.KEY_SUSPEND_WINDOW_MINUTES,
            VisualBlockingSettings.DEFAULT_SUSPEND_WINDOW_MINUTES
        )
        nsfwSuspendDurationMinutes = visualBlockingPrefs.getInt(
            VisualBlockingSettings.KEY_SUSPEND_DURATION_MINUTES,
            VisualBlockingSettings.DEFAULT_SUSPEND_DURATION_MINUTES
        )
    }

    private fun updateUsageWarningSettings() {
        usageWarningEnabled = usageWarningPrefs.getBoolean(
            UsageWarningSettings.KEY_ENABLED, UsageWarningSettings.DEFAULT_ENABLED
        )
        usageWarningStages = UsageWarningSettings.loadOrSeedStages(usageWarningPrefs)
            .sortedByDescending { it.minutesBefore }
    }

    private fun cancelActiveUsageWarning() {
        val key = currentWarnKey ?: return
        val stageId = activeStageId ?: return
        UsageWarningNotifier.cancel(applicationContext, key, stageId)
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

        val all = blockedWordsPrefs.all
        val passiveMap = mutableMapOf<String, Map<String, Regex>>()
        all.forEach { (key, value) ->
            if (key.startsWith("passive_words_")) {
                val pkg = key.substringAfter("passive_words_")
                val wordsSet = (value as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                if (wordsSet.isNotEmpty()) {
                    passiveMap[pkg] = wordsSet.associateWith { word ->
                        Regex(
                            "(?<![\\p{L}\\p{N}_])${Regex.escape(word)}(?![\\p{L}\\p{N}_])",
                            RegexOption.IGNORE_CASE
                        )
                    }
                }
            }
        }
        passiveForbiddenPatterns = passiveMap
    }

    private val blockRunnable = Runnable {
        executeBlocking()
    }

    private val windowEndRunnable = Runnable {
        executeBlocking(outsideWindow = true)
    }

    private val warnTickRunnable = Runnable {
        tickUsageWarning()
    }

    private val colorFilterSyncRunnable = Runnable {
        syncColorFilters()
    }

    private val nightLightOverlay by lazy { NightLightOverlay(this) }

    private var colorFilterForegroundPackage: String? = null
    private var colorFilterAppliedModes: Set<ColorFilterMode> = emptySet()

    companion object {
        private const val NSFW_SCAN_INTERVAL_MS = 2000L

        private const val USAGE_WARNING_TICK_INTERVAL_MS = 2000L

        // --- Generic, app-agnostic media-detection signals, tried in this order for every app ---

        // 1) Node class-name substrings that indicate a view is likely rendering media. Covers
        // both the stock Android widgets and the common naming convention custom view classes
        // use across many apps' own codebases (e.g. "MediaFrameLayout", "PhotoView", "GifImageView").
        // Doesn't help with Compose apps, which collapse everything to plain "android.view.View".
        private val NSFW_MEDIA_CLASS_KEYWORDS = listOf(
            "Image", "Photo", "Video", "Media", "Player", "Gif", "TextureView", "SurfaceView", "WebView"
        )

        // 2) Shared PT/EN vocabulary for the media-type label apps set for screen readers — the
        // same list is checked against both fields, just with different strictness: contentDescription
        // is a curated a11y string an app author wrote on purpose, so a loose "contains" match is
        // safe; a node's plain text can be arbitrary user content (e.g. a chat message), so it's
        // only matched as a *prefix* to avoid tripping on a message that merely mentions "foto".
        private val NSFW_MEDIA_KEYWORDS = listOf(
            "imagem", "image", "picture", "foto", "photo", "vídeo", "video", "gif", "media", "mídia"
        )

        // 3) Narrow, app-specific exception: Reddit's feed thumbnails carry no media-specific text
        // at all — the only label on the card is a combined one for the whole post (rating tag +
        // title + attribution), e.g. "18+, Anal, Postado no r/short_porn 2 anos atrás, 5". This
        // isn't really a "media" signal, it's a "this is a post" signal used as a fallback proxy
        // until/unless the geometric fallback below is confirmed to find the same thumbnails on
        // its own (it targets exactly this kind of unlabeled-image gap, but more generically).
        private val NSFW_MEDIA_DESCRIPTION_PATTERNS = listOf(
            Regex("""^\d{1,2}\+,"""), // e.g. "18+, ..."
            Regex("postado (no|em)", RegexOption.IGNORE_CASE),
            Regex("posted (in|to)", RegexOption.IGNORE_CASE)
        )

        // 4) Geometric fallback for apps that expose neither a distinctive class name nor any
        // accessibility text on their media views (only tried when 1-3 find nothing on a given
        // screen, see findNsfwMediaRegions): a leaf node with no text/description at all, sized
        // and shaped like a plausible photo/video, is very likely to be an unlabeled image view —
        // this is the generic, app-agnostic case the keyword-based signals above can't cover.
        private const val NSFW_FALLBACK_MIN_ASPECT = 0.4f
        private const val NSFW_FALLBACK_MAX_ASPECT = 3.0f
        // Caps worst-case cost when a screen has many large, empty, unlabeled containers (which
        // aren't actually media) — the largest candidates are kept as the most plausible ones.
        private const val NSFW_FALLBACK_MAX_CANDIDATES = 5

        private const val NSFW_MIN_MEDIA_SIZE_DP = 96
        private const val NSFW_DIAG_MIN_SIZE_DP = 48
        private const val NSFW_DIAG_MAX_NODES = 40

        var isRunning by mutableStateOf(false)
            private set

        var disablingService = false
            private set

        var checkingRating = false
            private set

        fun prepareServiceDisable() { disablingService = true }

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        disablingService = false

        limitPrefs = getSharedPreferences(AppTimeLimits.PREFS_NAME, MODE_PRIVATE)
        weeklyLimitPrefs = getSharedPreferences(AppTimeLimits.WEEKLY_PREFS_NAME, MODE_PRIVATE)
        usagePrefs = getSharedPreferences("internal_usage_stats", MODE_PRIVATE)
        reopenPrefs = getSharedPreferences("reopen_times", MODE_PRIVATE)
        timeWindowPrefs = getSharedPreferences(AppTimeWindows.PREFS_NAME, MODE_PRIVATE)
        groupsPrefs = getSharedPreferences(AppGroupRepository.PREFS_NAME, MODE_PRIVATE)
        groupUsagePrefs = getSharedPreferences("group_usage_stats", MODE_PRIVATE)
        blockedWordsPrefs = getSharedPreferences("blocked_words_prefs", MODE_PRIVATE)
        securityPrefs = getSharedPreferences("security_prefs", MODE_PRIVATE)
        ratingPrefs = getSharedPreferences("app_ratings", MODE_PRIVATE)
        visualBlockingPrefs = getSharedPreferences(VisualBlockingSettings.PREFS_NAME, MODE_PRIVATE)
        usageWarningPrefs = getSharedPreferences(UsageWarningSettings.PREFS_NAME, MODE_PRIVATE)
        colorFilterPrefs = getSharedPreferences(ColorFilterSettings.PREFS_NAME, MODE_PRIVATE)
        ColorFilterSettings.migrate(colorFilterPrefs)
        nsfwSuspensionTracker = VisualBlockingSuspensionTracker(applicationContext)

        limitPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        weeklyLimitPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        timeWindowPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        groupsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        blockedWordsPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        visualBlockingPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        usageWarningPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        colorFilterPrefs.registerOnSharedPreferenceChangeListener(prefsListener)
        securityPrefs.registerOnSharedPreferenceChangeListener(prefsListener)

        updateForbiddenPatterns()
        updateVisualBlockingSettings()
        updateUsageWarningSettings()

        ColorFilterController.observedUris.forEach {
            contentResolver.registerContentObserver(it, false, colorFilterObserver)
        }
        syncColorFilters()

        val blockedPackages = getControlledPackages()
        suspendPackages(blockedPackages, false)

        // Must run after the un-suspend above, which would otherwise release a package that is
        // still serving a visual-blocking suspension, an impulse block or a focus period.
        restoreNsfwSuspensions()
        syncImpulseSuspension()
        FocusMode.sync(applicationContext)

        val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DechainerDeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.clearUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
        }

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, packageFilter)

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(screenReceiver, screenFilter)

        if (BuildConfig.DEBUG) {
            val filter = IntentFilter("io.github.warleysr.dechainer.DEBUG_INSTALL")
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    disablingService = true
                    context.getSharedPreferences("security_prefs", MODE_PRIVATE).edit {
                        putBoolean(SecurityManager.DEBUG_AUTO_START_SESSION_KEY, true)
                    }
                    SecurityManager.suspendUnknownSourcesRestrictionForDebugInstall(context)
                    FocusService.stopForDebugInstall(context)
                    disableSelf()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(receiver, filter)
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterReceiver(packageReceiver)
        unregisterReceiver(screenReceiver)
        limitPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        weeklyLimitPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        timeWindowPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        groupsPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        blockedWordsPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        visualBlockingPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        usageWarningPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        colorFilterPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        securityPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        contentResolver.unregisterContentObserver(colorFilterObserver)
        handler.removeCallbacks(colorFilterSyncRunnable)
        // Must run after unregistering the observer, or restoring would re-enforce the modes.
        ColorFilterController.release(applicationContext)
        nightLightOverlay.hide()
        impulseReleaseRunnable?.let { handler.removeCallbacks(it) }
        impulseReleaseRunnable = null
        // Drop the pending release timers: with the service gone the block below re-suspends the
        // controlled packages on purpose, and a stale timer firing afterwards would undo that.
        // The deadlines stay persisted, so restoreNsfwSuspensions() picks them up on reconnect.
        nsfwSuspensionReleases.values.forEach { handler.removeCallbacks(it) }
        nsfwSuspensionReleases.clear()
        stopTrackingAndSave()
        // Close on the same thread that created/drove it (GPU delegate requirement), then let
        // that queued close finish before shutting the dispatcher's executor down.
        val detectorToClose = nsfwDetector
        nsfwDetector = null
        if (detectorToClose != null) {
            serviceScope.launch(nsfwDispatcher) { detectorToClose.close() }
        }
        nsfwDispatcher.close()
        isRunning = false

        if (!disablingService) {
            val blockedPackages = getControlledPackages()
            suspendPackages(blockedPackages)

            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val admin = ComponentName(this, DechainerDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(admin)) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_INSTALL_APPS)
            }

            val intent = Intent(
                this@DechainerAccessibilityService, AccessibilityRequestActivity::class.java
            ).apply { flags = FLAG_ACTIVITY_NEW_TASK }
            startActivity(intent)
        }

        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        handler.removeCallbacks(blockRunnable)
        handler.removeCallbacks(windowEndRunnable)
        handler.removeCallbacks(warnTickRunnable)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) onColorFilterForegroundChanged(event)
        if (event.packageName == packageName) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            checkActiveBlocking()
        }

        // App tracking to control time limits and time between re-openings
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
                wasTypingActively = false
                lastActiveTypedText = ""

                if (nsfwEnabled && newPackage in nsfwTargetPackages) {
                    warmUpNsfwDetector()
                }
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
        }

        // Passive blocking: when the forbidden word appears on the screen
        else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val pkg = currentPackage ?: return

            if (nsfwEnabled && pkg in nsfwTargetPackages) {
                maybeScanForNsfwContent(pkg)
            }

            if (!ratingPrefs.contains(pkg) && !checkingRating) {
                checkingRating = true
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val info = PlayStoreRatingFetcher.fetch(pkg)

                        if (info.hasExplicitContent && info.contentRating == "Rated 18+")
                            withContext(Dispatchers.Main) {
                                suspendPackage(pkg)
                            }

                        ratingPrefs.edit { putBoolean(pkg, info.hasExplicitContent) }
                    } catch (e: HttpStatusException) {
                        e.printStackTrace()
                        if (e.statusCode == 404) {
                            ratingPrefs.edit { putBoolean(pkg, false) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        checkingRating = false
                    }
                }
            }

            val passivePatterns = passiveForbiddenPatterns[pkg] ?: return

            val screenText = buildScreenText(event) ?: return

            passivePatterns.forEach { (word, regex) ->
                if (regex.containsMatchIn(screenText)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    showBlockedActivity(word)
                    return@forEach
                }
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

    private fun showBlockedActivity(forbiddenWord: String) {
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                val intent = Intent(
                    this@DechainerAccessibilityService, BlockedWordActivity::class.java
                ).apply {
                    flags = FLAG_ACTIVITY_NEW_TASK
                    putExtra("word", forbiddenWord)
                }
                startActivity(intent)
            }
        }
    }

    /**
     * Kicks off [NsfwContentDetector]'s one-time setup (mmap the model + build the interpreter,
     * including XNNPACK delegate compilation) as soon as the user opens a monitored app, instead
     * of paying that ~1s cost — and the frame drops that come with it — during the first real
     * scan. By the time the user scrolls to an actual image the detector is usually already warm.
     * A no-op if it's already been created.
     */
    private fun warmUpNsfwDetector() {
        if (nsfwDetector != null) return
        serviceScope.launch(nsfwDispatcher) {
            if (nsfwDetector == null) {
                nsfwDetector = NsfwContentDetector(applicationContext)
            }
        }
    }

    /**
     * Classifies the media currently on screen with [NsfwContentDetector], throttled to at
     * most once every [NSFW_SCAN_INTERVAL_MS] and never overlapping a scan already in progress
     * — Reddit fires content-changed events continuously while scrolling, so without this the
     * screen would be captured and run through the model far more often than needed. A
     * screenshot is only taken when [findNsfwMediaRegions] finds at least one candidate region,
     * since most content-changed events (text, votes, comments) have nothing to classify.
     */
    private fun maybeScanForNsfwContent(pkg: String) {
        if (nsfwScanInFlight) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastNsfwScanElapsedMs < NSFW_SCAN_INTERVAL_MS) return
        lastNsfwScanElapsedMs = now
        nsfwScanInFlight = true

        // The tree walk below makes many cross-process AccessibilityNodeInfo calls, which is
        // too slow to do synchronously on the main thread (was dropping frames) — so the whole
        // scan, from tree walk to screenshot request, is kicked off from a background thread.
        serviceScope.launch {
            val mediaRegions = findNsfwMediaRegions()
            if (mediaRegions.isEmpty()) {
                nsfwScanInFlight = false
                return@launch
            }

            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    result.hardwareBuffer.close()

                    if (hardwareBitmap == null) {
                        Timber.w("NSFW scan: failed to wrap screenshot hardware buffer")
                        nsfwScanInFlight = false
                        return
                    }

                    serviceScope.launch(nsfwDispatcher) {
                        try {
                            val softwareBitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBitmap.recycle()

                            val detector = nsfwDetector
                                ?: NsfwContentDetector(applicationContext).also { nsfwDetector = it }

                            var maxUnsafeScore = 0f
                            for (region in mediaRegions) {
                                val crop = cropToBitmap(softwareBitmap, region) ?: continue

                                val startMs = SystemClock.elapsedRealtime()
                                val scores = detector.predict(crop)
                                val elapsedMs = SystemClock.elapsedRealtime() - startMs
                                crop.recycle()

                                val categories = nsfwCategories
                                val threshold = nsfwThreshold
                                val unsafeScore = detector.unsafeScore(scores, categories)
                                val scoresText = NsfwContentDetector.LABELS.zip(scores.toList())
                                    .joinToString { (label, score) -> "$label=${"%.3f".format(score)}" }
                                Timber.d(
                                    "NSFW scan region=$region (${elapsedMs}ms): $scoresText, " +
                                        "unsafe(${categories.joinToString("+")})=${"%.3f".format(unsafeScore)}"
                                )

                                if (unsafeScore > maxUnsafeScore) maxUnsafeScore = unsafeScore
                                if (unsafeScore > threshold) break
                            }
                            softwareBitmap.recycle()

                            if (maxUnsafeScore > nsfwThreshold) {
                                Timber.d("NSFW scan: blocking (max unsafe score=${"%.3f".format(maxUnsafeScore)})")
                                withContext(Dispatchers.Main) {
                                    if (currentPackage in nsfwTargetPackages) {
                                        executeNsfwBlocking(pkg)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "NSFW scan failed")
                        } finally {
                            nsfwScanInFlight = false
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Timber.w("NSFW scan: takeScreenshot failed, errorCode=$errorCode")
                    nsfwScanInFlight = false
                }
            })
        }
    }

    /**
     * Walks the current window's accessibility tree looking for views that are likely
     * rendering an image/gif/video, so the classifier only ever looks at the actual media
     * instead of the whole screen — the model expects its subject to fill the frame, and a
     * full-screen screenshot dilutes it with surrounding UI (text, buttons, other posts) once
     * downscaled to the model's 224x224 input.
     *
     * Tries the same signals for every app, roughly from most to least specific: class name
     * ([NSFW_MEDIA_CLASS_KEYWORDS]), accessibility text/description vocabulary ([NSFW_MEDIA_KEYWORDS]),
     * Reddit's post-card pattern ([NSFW_MEDIA_DESCRIPTION_PATTERNS]) — then, only if none of those
     * found anything on this screen, the geometric fallback (leaf node, no text at all, plausible
     * photo/video size and aspect ratio). None of these are Compose-aware, so an app like Reddit
     * that renders everything as plain `android.view.View` only gets caught via the text-based or
     * geometric signals, never the class-based one.
     *
     * Small nodes (avatars, vote icons) are filtered out via [NSFW_MIN_MEDIA_SIZE_DP].
     */
    private fun findNsfwMediaRegions(): List<Rect> {
        val root = rootInActiveWindow ?: return emptyList()
        val minSizePx = (NSFW_MIN_MEDIA_SIZE_DP * resources.displayMetrics.density).toInt()
        val diagMinSizePx = (NSFW_DIAG_MIN_SIZE_DP * resources.displayMetrics.density).toInt()
        val regions = mutableListOf<Rect>()
        val fallbackCandidates = mutableListOf<Rect>()
        // Debug-only breadcrumb: every sufficiently large node, matched or not, so the
        // heuristics above can be tuned against what the target app actually exposes.
        val diagnostics = mutableListOf<String>()

        fun traverse(node: AccessibilityNodeInfo?) {
            node ?: return
            val className = node.className?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val bigEnough = rect.width() >= minSizePx && rect.height() >= minSizePx

            val matchesClass = NSFW_MEDIA_CLASS_KEYWORDS.any { className.contains(it, ignoreCase = true) }
            val matchesDescription = description.isNotEmpty() && (
                NSFW_MEDIA_KEYWORDS.any { description.contains(it, ignoreCase = true) } ||
                    NSFW_MEDIA_DESCRIPTION_PATTERNS.any { it.containsMatchIn(description) }
                )
            val matchesText = NSFW_MEDIA_KEYWORDS.any { text.startsWith(it, ignoreCase = true) }

            if ((matchesClass || matchesDescription || matchesText) && bigEnough && regions.none { it == rect }) {
                regions.add(rect)
            } else if (node.childCount == 0 && description.isEmpty() && text.isEmpty() && bigEnough) {
                val aspect = rect.width().toFloat() / rect.height().toFloat()
                if (aspect in NSFW_FALLBACK_MIN_ASPECT..NSFW_FALLBACK_MAX_ASPECT && fallbackCandidates.none { it == rect }) {
                    fallbackCandidates.add(rect)
                }
            }

            if (diagnostics.size < NSFW_DIAG_MAX_NODES &&
                rect.width() >= diagMinSizePx && rect.height() >= diagMinSizePx
            ) {
                diagnostics.add("class=$className bounds=$rect desc=\"${description.take(50)}\" text=\"${text.take(30)}\"")
            }

            for (i in 0 until node.childCount) traverse(node.getChild(i))
        }
        traverse(root)
        root.recycle()

        if (regions.isEmpty() && fallbackCandidates.isNotEmpty()) {
            val capped = fallbackCandidates
                .sortedByDescending { it.width().toLong() * it.height() }
                .take(NSFW_FALLBACK_MAX_CANDIDATES)
            Timber.d(
                "NSFW scan: no labeled media found, using ${capped.size}/${fallbackCandidates.size} " +
                    "geometric fallback candidate(s): $capped"
            )
            regions.addAll(capped)
        }

        if (regions.isEmpty()) {
            Timber.d(
                "NSFW scan: no media regions found. Large nodes on screen (>=${NSFW_DIAG_MIN_SIZE_DP}dp):\n" +
                    diagnostics.joinToString("\n")
            )
        } else {
            Timber.d("NSFW scan: found ${regions.size} candidate region(s): $regions")
        }
        return regions
    }

    private fun cropToBitmap(source: Bitmap, rect: Rect): Bitmap? {
        val left = rect.left.coerceIn(0, source.width)
        val top = rect.top.coerceIn(0, source.height)
        val right = rect.right.coerceIn(left, source.width)
        val bottom = rect.bottom.coerceIn(top, source.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return null

        return try {
            Bitmap.createBitmap(source, left, top, width, height)
        } catch (e: Exception) {
            Timber.w(e, "NSFW scan: failed to crop region $rect")
            null
        }
    }

    /**
     * Reaction to the classifier flagging the screen of [pkg].
     *
     * Default behaviour — and what still happens for every block that doesn't complete a window —
     * is to suspend and immediately un-suspend the package: suspending is what force-closes the
     * app, un-suspending right after leaves the user free to reopen it.
     *
     * With the "suspend app" option on, repeated blocks escalate: once [pkg] has been blocked
     * [nsfwSuspendBlockCount] times within [nsfwSuspendWindowMinutes], the suspension is left in
     * place for [nsfwSuspendDurationMinutes] instead of being lifted straight away.
     */
    private fun executeNsfwBlocking(pkg: String) {
        // Suspending a package doesn't tear its window down instantly, and the blocked-content
        // screen belongs to us (so it never updates currentPackage) — the app therefore keeps
        // emitting content-changed events for a while after a suspension starts, and each one
        // would otherwise be counted as a fresh block *and* run the un-suspend of the default
        // path below, cutting the suspension short. Ignore everything until it's over.
        if (nsfwSuspensionTracker.isSuspended(pkg)) {
            Timber.d("NSFW scan: $pkg is already suspended, block ignored")
            return
        }

        if (nsfwSuspendEnabled) {
            val shouldSuspend = nsfwSuspensionTracker.registerBlock(
                pkg, nsfwSuspendBlockCount, nsfwSuspendWindowMinutes
            )
            if (shouldSuspend) {
                val until = nsfwSuspensionTracker.startSuspension(pkg, nsfwSuspendDurationMinutes)
                suspendPackage(pkg)
                scheduleNsfwSuspensionRelease(pkg, until)
                Timber.d("NSFW scan: $pkg suspended for $nsfwSuspendDurationMinutes min (until $until)")
                showNsfwBlockedActivity(nsfwSuspendDurationMinutes)
                return
            }
        }

        suspendPackage(pkg)
        suspendPackage(pkg, suspend = false)
        showNsfwBlockedActivity()
    }

    /**
     * Queues the automatic release of [pkg]'s suspension for the wall-clock instant [until].
     *
     * The timer only lives as long as this service instance; [restoreNsfwSuspensions] is what
     * covers the case where the service is recreated before it fires.
     */
    private fun scheduleNsfwSuspensionRelease(pkg: String, until: Long) {
        nsfwSuspensionReleases.remove(pkg)?.let { handler.removeCallbacks(it) }

        val release = Runnable {
            nsfwSuspensionReleases.remove(pkg)
            nsfwSuspensionTracker.endSuspension(pkg)
            if (!isHeldByOtherBlock(pkg)) suspendPackage(pkg, suspend = false)
            Timber.d("NSFW scan: suspension of $pkg lifted")
        }
        nsfwSuspensionReleases[pkg] = release
        handler.postDelayed(release, (until - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    /**
     * Re-applies (or lifts) the suspensions recorded by [VisualBlockingSuspensionTracker] after
     * the service is recreated — without this, a suspension whose release timer died with the
     * previous process would never be lifted.
     */
    private fun restoreNsfwSuspensions() {
        val now = System.currentTimeMillis()
        nsfwSuspensionTracker.activeSuspensions().forEach { (pkg, until) ->
            if (until <= now) {
                nsfwSuspensionTracker.endSuspension(pkg)
                if (!isHeldByOtherBlock(pkg)) suspendPackage(pkg, suspend = false)
            } else {
                suspendPackage(pkg)
                scheduleNsfwSuspensionRelease(pkg, until)
            }
        }
    }

    private fun showNsfwBlockedActivity(suspendedMinutes: Int = 0) {
        startActivity(Intent(this, NsfwContentBlockedActivity::class.java).apply {
            flags = FLAG_ACTIVITY_NEW_TASK
            putExtra(NsfwContentBlockedActivity.EXTRA_SUSPENDED_MINUTES, suspendedMinutes)
        })
    }

    /**
     * Brings the app suspensions in line with the current state of the impulse block: suspends the
     * configured list while a block is running with [SecurityManager.ImpulseAction.TIMER_AND_SUSPEND]
     * selected, and releases whatever was suspended once it isn't.
     *
     * Called both from the prefs listener (the panic button, and the block expiring) and from
     * [onServiceConnected], which is what re-applies a block that outlived this service.
     */
    private fun syncImpulseSuspension() {
        val ctx = applicationContext
        val remaining = SecurityManager.getImpulseBlockRemainingTime(ctx)
        val targets = SecurityManager.getImpulseSuspendedApps(ctx)
        val shouldSuspend = remaining > 0 &&
            SecurityManager.getImpulseAction(ctx) == SecurityManager.ImpulseAction.TIMER_AND_SUSPEND &&
            targets.isNotEmpty()

        if (!shouldSuspend) {
            releaseImpulseSuspension()
            return
        }

        // The configured list may have changed since the block started; let go of anything that
        // is no longer part of it before applying the current one.
        val alreadySuspended = SecurityManager.getActiveImpulseSuspension(ctx)
        val dropped = (alreadySuspended - targets).filterNot { FocusMode.isHolding(ctx, it) }
        if (dropped.isNotEmpty()) suspendPackages(dropped.toTypedArray(), false)

        suspendPackages(targets.toTypedArray(), true)
        SecurityManager.setActiveImpulseSuspension(ctx, targets)

        impulseReleaseRunnable?.let { handler.removeCallbacks(it) }
        val release = Runnable {
            impulseReleaseRunnable = null
            releaseImpulseSuspension()
        }
        impulseReleaseRunnable = release
        handler.postDelayed(release, remaining)
        Timber.d("Impulse block: suspended ${targets.size} app(s) for ${remaining}ms")
    }

    private fun releaseImpulseSuspension() {
        impulseReleaseRunnable?.let { handler.removeCallbacks(it) }
        impulseReleaseRunnable = null

        val ctx = applicationContext
        val suspended = SecurityManager.getActiveImpulseSuspension(ctx)
        if (suspended.isEmpty()) return

        // A package can be serving a visual-blocking suspension or a focus period at the same time;
        // releasing it here would cut that one short, so those are left alone for their own timer to lift.
        val releasable = suspended.filterNot { nsfwSuspensionTracker.isSuspended(it) || FocusMode.isHolding(ctx, it) }
        if (releasable.isNotEmpty()) suspendPackages(releasable.toTypedArray(), false)

        SecurityManager.clearActiveImpulseSuspension(ctx)
        Timber.d("Impulse block: released ${releasable.size} app(s)")
    }

    private fun isHeldByOtherBlock(pkg: String): Boolean {
        val ctx = applicationContext
        return pkg in SecurityManager.getActiveImpulseSuspension(ctx) || FocusMode.isHolding(ctx, pkg)
    }

    private fun syncColorFilters() {
        handler.removeCallbacks(colorFilterSyncRunnable)

        val modes = ColorFilterSettings.modesToApply(colorFilterPrefs, colorFilterForegroundPackage)
        colorFilterAppliedModes = modes
        if (modes.isNotEmpty()) {
            ColorFilterController.enforce(applicationContext, ColorFilterController.targetsFor(colorFilterPrefs, modes))
        } else {
            ColorFilterController.release(applicationContext)
        }

        val overlayNightLight = !ColorFilterController.platformNightLight && ColorFilterMode.NIGHT_LIGHT in modes
        if (overlayNightLight) {
            nightLightOverlay.show(
                ColorFilterSettings.nightLightTemperature(colorFilterPrefs),
                ColorFilterSettings.nightLightIntensity(colorFilterPrefs)
            )
        } else {
            nightLightOverlay.hide()
        }

        if (!ColorFilterSettings.isEnabled(colorFilterPrefs)) return
        val delay = ColorFilterSettings.millisUntilNextBoundary(ColorFilterSettings.scheduledWindows(colorFilterPrefs))
            ?: return
        handler.postDelayed(colorFilterSyncRunnable, delay)
    }

    // Kept apart from currentPackage, which skips Déchaîner's own screens and is cleared when an app gets blocked.
    private fun onColorFilterForegroundChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        if (pkg == "com.android.systemui") return
        if (className.contains("InputMethodService", ignoreCase = true) ||
            className.contains("SoftInputWindow", ignoreCase = true)
        ) return
        // Our own overlays (e.g. night light) aren't a screen the user switched to.
        if (pkg == packageName && !className.startsWith(packageName)) return
        if (pkg == colorFilterForegroundPackage) return

        colorFilterForegroundPackage = pkg
        if (!ColorFilterSettings.dependsOnForegroundApp(colorFilterPrefs)) return
        val modes = ColorFilterSettings.modesToApply(colorFilterPrefs, pkg)
        if (modes != colorFilterAppliedModes) syncColorFilters()
    }

    private fun suspendPackages(packages: Array<String>, suspend: Boolean = true) {
        val dpm =
            applicationContext.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(applicationContext, DechainerDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(admin)) return
        dpm.setPackagesSuspended(admin, packages, suspend)
    }

    private fun suspendPackage(packageName: String, suspend: Boolean = true) = suspendPackages(
        arrayOf(packageName), suspend
    )

    private fun getControlledPackages(): Array<String> {
        val restrictedGroupPackages = AppGroupRepository.getGroups()
            .filter { it.timeLimit.isSet || it.timeWindows.isNotEmpty() }
            .flatMap { it.packageNames }

        return targetPackages
            .union(passiveForbiddenPatterns.keys)
            .union(limitPrefs.all.keys)
            .union(weeklyLimitPrefs.all.keys)
            .union(reopenPrefs.all.keys)
            .union(timeWindowPrefs.all.keys)
            .union(restrictedGroupPackages)
            .toTypedArray()
    }

    private fun checkForbiddenWord(text: String): String? {
        forbiddenPatterns.forEach { (word: String, regex: Regex) ->
            if (regex.containsMatchIn(text))
                return word
        }
        return null
    }

    private data class ScreenExtraction(val text: String, val isTyping: Boolean)

    private fun extractEditableFieldsTextAndFocus(): ScreenExtraction {
        val root = rootInActiveWindow ?: return ScreenExtraction("", false)
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        root.recycle()

        val isTyping = focused?.isEditable == true
        val text = if (isTyping) focused?.text?.toString().orEmpty() else ""
        focused?.recycle()

        return ScreenExtraction(text, isTyping)
    }

    private fun checkActiveBlocking() {
        val pkg = currentPackage ?: return
        if (!targetPackages.contains(pkg)) return

        val extraction = extractEditableFieldsTextAndFocus()
        if (extraction.isTyping) {
            wasTypingActively = true
            lastActiveTypedText = extraction.text
            return
        }
        if (!wasTypingActively) return
        wasTypingActively = false

        val text = extraction.text.ifBlank { lastActiveTypedText }
        lastActiveTypedText = ""

        val forbiddenWord = checkForbiddenWord(text) ?: return
        performGlobalAction(GLOBAL_ACTION_BACK)
        showBlockedActivity(forbiddenWord)
    }

    /**
     * The app's own settings plus its group's, when it belongs to one. A group does not replace
     * the app's individual limit/windows — it adds another cap alongside them, and whichever of
     * the two is more restrictive is the one that actually applies (see [startTracking]).
     * Limits that vary by weekday are resolved to today's value here.
     */
    private data class TrackingConfig(
        val group: AppGroup?,
        val appLimitMinutes: Int,
        val groupLimitMinutes: Int,
        val windows: List<TimeWindow>
    )

    private fun trackingConfigFor(pkg: String): TrackingConfig {
        val group = AppGroupRepository.getGroupForPackage(pkg)
        val appWindows = AppTimeWindows.decode(timeWindowPrefs.getString(pkg, null))
        val appLimitMinutes = AppTimeLimits.read(limitPrefs, weeklyLimitPrefs, pkg).todayMinutes()
        return if (group != null) {
            TrackingConfig(
                group = group,
                appLimitMinutes = appLimitMinutes,
                groupLimitMinutes = group.timeLimit.todayMinutes(),
                windows = TimeWindow.intersect(group.timeWindows, appWindows)
            )
        } else {
            TrackingConfig(null, appLimitMinutes, 0, appWindows)
        }
    }

    private fun startTracking(pkg: String) {
        handler.removeCallbacks(warnTickRunnable)

        val config = trackingConfigFor(pkg)

        if (config.windows.isNotEmpty()) {
            val nowMinute = currentMinuteOfDay()
            val activeWindow = config.windows.firstOrNull { it.contains(nowMinute) }
            if (activeWindow == null) {
                executeBlocking(outsideWindow = true)
                return
            }
            handler.postDelayed(
                windowEndRunnable,
                TimeUnit.MINUTES.toMillis(activeWindow.minutesUntilEnd(nowMinute).toLong())
            )
        }

        val remainingSecondsReopening = getRemainingSecondsToReopen(pkg)
        if (config.appLimitMinutes <= 0 && config.groupLimitMinutes <= 0 && remainingSecondsReopening <= 0) return

        var appRemaining: Long? = null
        var groupRemaining: Long? = null
        var limitReached = false

        if (config.appLimitMinutes > 0) {
            val remaining = TimeUnit.MINUTES.toMillis(config.appLimitMinutes.toLong()) - usagePrefs.getLong(pkg, 0L)
            if (remaining <= 0) limitReached = true else appRemaining = remaining
        }

        if (config.group != null && config.groupLimitMinutes > 0) {
            val remaining = TimeUnit.MINUTES.toMillis(config.groupLimitMinutes.toLong()) -
                groupUsagePrefs.getLong(config.group.id, 0L)
            if (remaining <= 0) limitReached = true else groupRemaining = remaining
        }

        if (limitReached) {
            executeBlocking()
            return
        }
        val remainingMillis = listOfNotNull(appRemaining, groupRemaining).minOrNull()
        if (remainingMillis != null) {
            handler.postDelayed(blockRunnable, remainingMillis)
        }

        scheduleUsageWarning(pkg, config, appRemaining, groupRemaining)

        if (remainingSecondsReopening > 0)
            executeBlocking(reopening = true, remainingSeconds = remainingSecondsReopening)
    }

    private fun scheduleUsageWarning(pkg: String, config: TrackingConfig, appRemaining: Long?, groupRemaining: Long?) {
        currentWarnKey = null
        activeStageId = null
        if (!usageWarningEnabled || usageWarningStages.isEmpty()) return

        val useGroup = groupRemaining != null && config.group != null &&
            (appRemaining == null || groupRemaining < appRemaining)
        val key = when {
            useGroup -> config.group!!.id
            appRemaining != null -> pkg
            else -> return
        }
        val remaining = if (useGroup) groupRemaining!! else appRemaining!!

        currentWarnKey = key

        val farthestMillis = stageMillis(usageWarningStages.first())
        if (remaining > farthestMillis) {
            Timber.d("UsageWarning: schedule($key) - outside every stage, entering farthest in ${remaining - farthestMillis}ms")
            handler.postDelayed(warnTickRunnable, remaining - farthestMillis)
        } else {
            tickUsageWarning()
        }
    }

    private fun stageMillis(stage: UsageAlertStage) = (stage.minutesBefore * 60_000).toLong()

    private fun tickUsageWarning() {
        handler.removeCallbacks(warnTickRunnable)
        val pkg = currentPackage ?: return
        val key = currentWarnKey ?: return
        if (usageWarningStages.isEmpty()) return

        val config = trackingConfigFor(pkg)
        val elapsed = SystemClock.elapsedRealtime() - sessionStartTime
        val isGroupKey = config.group?.id == key

        val remaining = when {
            isGroupKey && config.groupLimitMinutes > 0 ->
                TimeUnit.MINUTES.toMillis(config.groupLimitMinutes.toLong()) - groupUsagePrefs.getLong(key, 0L) - elapsed
            !isGroupKey && config.appLimitMinutes > 0 ->
                TimeUnit.MINUTES.toMillis(config.appLimitMinutes.toLong()) - usagePrefs.getLong(pkg, 0L) - elapsed
            else -> return
        }
        Timber.d("UsageWarning: tick($key) pkg=$pkg elapsed=${elapsed}ms remaining=${remaining}ms")

        if (remaining <= 0) {
            activeStageId?.let { UsageWarningNotifier.cancel(applicationContext, key, it) }
            activeStageId = null
            return
        }

        val enteredStage = usageWarningStages.lastOrNull { remaining <= stageMillis(it) }

        if (enteredStage == null) {
            activeStageId?.let { staleId ->
                UsageWarningNotifier.cancel(applicationContext, key, staleId)
                UsageWarningNotifier.reset(applicationContext, key, staleId)
            }
            activeStageId = null
            val farthestMillis = stageMillis(usageWarningStages.first())
            handler.postDelayed(warnTickRunnable, (remaining - farthestMillis).coerceAtLeast(500L))
            return
        }

        if (enteredStage.id != activeStageId) {
            activeStageId?.let { UsageWarningNotifier.cancel(applicationContext, key, it) }
            activeStageId = enteredStage.id
        }

        if (!UsageWarningNotifier.isDismissed(applicationContext, key, enteredStage.id)) {
            if (!UsageWarningNotifier.hasBeenShown(applicationContext, key, enteredStage.id)) {
                UsageWarningNotifier.markShown(applicationContext, key, enteredStage.id)
            }
            val displayName = if (isGroupKey) config.group?.name ?: key else appLabel(pkg)
            val barMillis = stageMillis(usageWarningStages.first())
            UsageWarningNotifier.showOrUpdate(
                applicationContext, key, enteredStage, displayName, remaining, barMillis
            )
        }

        handler.postDelayed(warnTickRunnable, USAGE_WARNING_TICK_INTERVAL_MS)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationInfo(pkg, 0).loadLabel(packageManager).toString()
    } catch (e: Exception) {
        pkg
    }

    private fun stopTrackingAndSave(screenOff: Boolean = false) {
        handler.removeCallbacks(blockRunnable)
        handler.removeCallbacks(windowEndRunnable)
        handler.removeCallbacks(warnTickRunnable)
        val pkg = currentPackage ?: return

        if (getRemainingSecondsToReopen(pkg) == 0 && !screenOff)
            lastClosedTimes[pkg] = SystemClock.elapsedRealtime()

        if (sessionStartTime == 0L) return

        val config = trackingConfigFor(pkg)
        val currentSessionMillis = SystemClock.elapsedRealtime() - sessionStartTime

        if (config.appLimitMinutes > 0) {
            usagePrefs.edit { putLong(pkg, usagePrefs.getLong(pkg, 0L) + currentSessionMillis) }
        }
        if (config.group != null && config.groupLimitMinutes > 0) {
            val groupId = config.group.id
            groupUsagePrefs.edit { putLong(groupId, groupUsagePrefs.getLong(groupId, 0L) + currentSessionMillis) }
        }

        sessionStartTime = 0
    }

    private fun executeBlocking(reopening: Boolean = false, remainingSeconds: Int = 0, outsideWindow: Boolean = false) {
        val pkg = currentPackage ?: return
        val config = trackingConfigFor(pkg)

        stopTrackingAndSave()
        currentPackage = null

        if (!reopening && !outsideWindow) {
            usageWarningStages.forEach { stage ->
                UsageWarningNotifier.cancel(applicationContext, pkg, stage.id)
                config.group?.let { UsageWarningNotifier.cancel(applicationContext, it.id, stage.id) }
            }
        }

        val individualAppName = appLabel(pkg)

        // For a plain limit block, name and blame whichever cap actually ran out: the app's own,
        // or (when both are exhausted) the shared group cap, since that's the one still binding.
        var displayName = individualAppName
        var displayLimit = config.appLimitMinutes
        if (!reopening && !outsideWindow && config.group != null && config.groupLimitMinutes > 0) {
            val groupReached = groupUsagePrefs.getLong(config.group.id, 0L) >=
                TimeUnit.MINUTES.toMillis(config.groupLimitMinutes.toLong())
            val appReached = config.appLimitMinutes > 0 &&
                usagePrefs.getLong(pkg, 0L) >= TimeUnit.MINUTES.toMillis(config.appLimitMinutes.toLong())
            if (groupReached && !appReached) {
                displayName = config.group.name
                displayLimit = config.groupLimitMinutes
            }
        }

        val activityClass = when {
            outsideWindow -> OutsideTimeWindowActivity::class.java
            reopening -> ReopeningLimitActivity::class.java
            else -> TimeUpActivity::class.java
        }

        val intent = Intent(this, activityClass).apply {
            flags = FLAG_ACTIVITY_NEW_TASK
            putExtra("appName", displayName)
        }

        if (outsideWindow) {
            intent.putStringArrayListExtra("windows", ArrayList(config.windows.map { it.formatted() }))
        } else {
            intent.putExtra("limit", if (reopening) remainingSeconds else displayLimit)
        }

        startActivity(intent)
    }

    private fun currentMinuteOfDay(): Int {
        val now = LocalTime.now()
        return now.hour * 60 + now.minute
    }
    
    
    private fun checkDateReset() {
        val today = LocalDate.now().toString()
        if (today != lastCheckDate) {
            usagePrefs.edit { clear() }
            groupUsagePrefs.edit { clear() }
            cancelActiveUsageWarning()
            activeStageId = null
            UsageWarningNotifier.resetAll(applicationContext)
            lastCheckDate = today
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
