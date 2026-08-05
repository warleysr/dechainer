package io.github.warleysr.dechainer

import android.content.RestrictionsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import org.json.JSONArray
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

class BrowserRestrictionsManager(private val context: Context) {

    fun getPossibleBrowsers(): List<ResolveInfo> {
        val pm = context.packageManager
        val resolvedPackages = mutableSetOf<String>()
        val results = mutableListOf<ResolveInfo>()

        val browserCategoryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_BROWSER)
        }

        val httpIntent = Intent(Intent.ACTION_VIEW, "http://www.example.com".toUri())
        val httpsIntent = Intent(Intent.ACTION_VIEW, "https://www.example.com".toUri())

        val flags =
            PackageManager.MATCH_ALL

        listOf(browserCategoryIntent, httpIntent, httpsIntent).forEach { intent ->
            pm.queryIntentActivities(intent, flags).forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo.packageName

                if (packageName != context.packageName && resolvedPackages.add(packageName)) {
                    results.add(resolveInfo)
                }
            }
        }

        return results
    }

    fun isBrowser(packageName: String): Boolean {
        return getPossibleBrowsers().any { it.activityInfo.packageName == packageName }
    }

    fun getPossibleTorrentApps(): Set<String> {
        val pm = context.packageManager

        val magnetIntent = Intent(Intent.ACTION_VIEW, "magnet:?xt=urn:btih:1234567890ABCDEF".toUri())
        val magnetHandlers = pm.queryIntentActivities(magnetIntent, PackageManager.MATCH_DEFAULT_ONLY)

        val torrentIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.EMPTY, "application/x-bittorrent")
        }
        val torrentHandlers = pm.queryIntentActivities(torrentIntent, PackageManager.MATCH_DEFAULT_ONLY)

        val suspiciousPackages = (magnetHandlers + torrentHandlers).map { it.activityInfo.packageName }.toSet()

        return suspiciousPackages
    }

    fun isTorrentApp(packageName: String): Boolean {
        return getPossibleTorrentApps().any { it == packageName }
    }

    fun supportsRestrictions(packageName: String): Boolean {
        return try {
            val rm = context.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
            rm.getManifestRestrictions(packageName).any { it.key == "URLBlocklist" }
                    && rm.getManifestRestrictions(packageName).any { it.key == "ForceGoogleSafeSearch" }
        } catch (_: Exception) {
            false
        }
    }

    fun applyRestrictions(installed: Boolean = false) {
        val prefs = context.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("blocked_lists_json", null) ?: return

        val allSites = mutableSetOf<String>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val sitesArray = array.getJSONObject(i).getJSONArray("sites")
                for (j in 0 until sitesArray.length()) {
                    allSites.add(sitesArray.getString(j))
                }
            }
        } catch (e: Exception) { e.printStackTrace() }

        val urlRestrictions = Bundle().apply {
            putStringArray("URLBlocklist", allSites.toTypedArray())

            if (installed)
                putBoolean("ForceGoogleSafeSearch", true)
        }

        val viewModel = DeviceOwnerViewModel()
        getPossibleBrowsers().forEach { info ->
            viewModel.setApplicationRestrictions(info.activityInfo.packageName, urlRestrictions)
        }
    }
}