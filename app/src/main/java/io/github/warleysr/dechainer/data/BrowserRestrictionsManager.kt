package io.github.warleysr.dechainer.data

import android.content.RestrictionsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import org.json.JSONArray
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.net.toUri

class BrowserRestrictionsManager(private val context: Context) {

    fun getPossibleBrowsers(): List<ResolveInfo> {
        val pm = context.packageManager
        val resolvedPackages = mutableSetOf<String>()
        val results = mutableListOf<ResolveInfo>()

        // 1. Apps explicitly declared as browsers via CATEGORY_APP_BROWSER
        val browserCategoryIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_BROWSER)
        }
        pm.queryIntentActivities(browserCategoryIntent, PackageManager.MATCH_ALL).forEach { resolveInfo ->
            val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
            if (packageName != context.packageName && resolvedPackages.add(packageName)) {
                results.add(resolveInfo)
            }
        }

        // 2. Apps handling generic HTTP/HTTPS with CATEGORY_BROWSABLE
        // Must exclude apps that only handle specific domain hosts (App Links / deep links)
        val httpIntent = Intent(Intent.ACTION_VIEW, "http://www.google.com".toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val httpsIntent = Intent(Intent.ACTION_VIEW, "https://www.google.com".toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        listOf(httpIntent, httpsIntent).forEach { intent ->
            pm.queryIntentActivities(
                intent,
                PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
            ).forEach { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@forEach
                if (packageName == context.packageName || resolvedPackages.contains(packageName)) return@forEach

                val filter = resolveInfo.filter
                // Check if this intent filter is restricted to specific domains (like itau.com.br)
                // A genuine web browser does NOT restrict data authorities to specific domains.
                val isDeepLinkOnly = filter != null && filter.countDataAuthorities() > 0 &&
                        (0 until filter.countDataAuthorities()).none { i ->
                            val host = filter.getDataAuthority(i)?.host
                            host == null || host == "*" || host.isEmpty()
                        }

                if (!isDeepLinkOnly && resolvedPackages.add(packageName)) {
                    results.add(resolveInfo)
                }
            }
        }

        return results
    }

    fun isBrowser(packageName: String): Boolean {
        return getPossibleBrowsers().any { it.activityInfo?.packageName == packageName }
    }

    fun getPossibleTorrentApps(): Set<String> {
        val pm = context.packageManager

        // 1. Magnet links: magnet scheme is uniquely used by torrent clients
        val magnetIntent = Intent(Intent.ACTION_VIEW, "magnet:?xt=urn:btih:1234567890ABCDEF".toUri())
        val magnetHandlers = pm.queryIntentActivities(
            magnetIntent,
            PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
        ).mapNotNull { it.activityInfo?.packageName }

        // 2. Torrent files: must specifically handle "application/x-bittorrent" or ".torrent" extension
        // Excludes generic file handlers that merely declare wildcards (*/* or application/*)
        val torrentIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("content://dummy/file.torrent"), "application/x-bittorrent")
        }
        val torrentHandlers = pm.queryIntentActivities(
            torrentIntent,
            PackageManager.MATCH_ALL or PackageManager.GET_RESOLVED_FILTER
        ).filter { resolveInfo ->
            val filter = resolveInfo.filter ?: return@filter false

            // Explicit MIME type application/x-bittorrent (not */* or application/*)
            val hasExplicitMime = filter.typesIterator()?.asSequence()?.any { type ->
                type.equals("application/x-bittorrent", ignoreCase = true)
            } == true

            // Explicit .torrent file extension in data path
            val hasTorrentPath = (0 until filter.countDataPaths()).any { i ->
                val path = filter.getDataPath(i)
                path.path.endsWith(".torrent", ignoreCase = true)
            }

            val hasMagnetScheme = filter.hasDataScheme("magnet")

            hasExplicitMime || hasTorrentPath || hasMagnetScheme
        }.mapNotNull { it.activityInfo?.packageName }

        return (magnetHandlers + torrentHandlers).toSet()
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

        getPossibleBrowsers().forEach { info ->
            AppRepository.setApplicationRestrictions(info.activityInfo.packageName, urlRestrictions)
        }
    }
}
