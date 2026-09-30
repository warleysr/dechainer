package io.github.warleysr.dechainer.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Quickstep launchers (Pixel and other Launcher3 forks) show recents inside the home activity, so each
 * check below catches recents on some launchers; a launcher event none of them flags counts as home.
 */
class HomeScreenDetector(private val context: Context) {
    private var recentsLabelCache: Pair<String, String?>? = null

    fun isHomeScreen(event: AccessibilityEvent, root: AccessibilityNodeInfo?): Boolean {
        val pkg = event.packageName?.toString() ?: return false
        if (pkg !in homePackages()) return false

        if (event.className?.toString()?.contains("Recents", ignoreCase = true) == true) return false

        // Launcher3 fills its window events with the current state's description.
        val recentsLabel = recentsLabel(pkg)
        if (recentsLabel != null && event.text.any { it?.toString() == recentsLabel }) return false

        val overview = root?.takeIf { it.packageName == pkg }
            ?.findAccessibilityNodeInfosByViewId("$pkg:id/overview_panel")
        return overview.orEmpty().none { it.isVisibleToUser }
    }

    // With no default launcher chosen the resolver comes back, so every launcher counts then.
    // Settings is left out: its FallbackHome only covers the moments before the user unlocks.
    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }
            ?.let { return setOf(it) }
        return pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet() - SETTINGS_PACKAGE
    }

    @SuppressLint("DiscouragedApi")
    private fun recentsLabel(pkg: String): String? {
        val key = "$pkg|${Locale.getDefault()}"
        recentsLabelCache?.let { (cachedKey, label) -> if (cachedKey == key) return label }
        val label = runCatching {
            val resources = context.packageManager.getResourcesForApplication(pkg)
            val id = resources.getIdentifier("accessibility_recent_apps", "string", pkg)
            if (id != 0) resources.getString(id) else null
        }.getOrNull()
        recentsLabelCache = key to label
        return label
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
    }
}
