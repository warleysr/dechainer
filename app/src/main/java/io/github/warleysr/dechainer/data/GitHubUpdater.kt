package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import io.github.warleysr.dechainer.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/warleysr/dechainer/releases/latest"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val PREFS_NAME = "github_updater_prefs"
    private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    fun isAutoCheckEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHECK_ENABLED, true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_AUTO_CHECK_ENABLED, enabled)
        }
    }

    fun getIgnoredVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_IGNORED_VERSION, null)

    fun ignoreVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_IGNORED_VERSION, normalizeVersion(version))
        }
    }

    fun shouldPromptForRelease(context: Context, release: GitHubRelease): Boolean {
        if (!isAutoCheckEnabled(context)) return false
        val ignored = getIgnoredVersion(context)?.let(::normalizeVersion)
        return normalizeVersion(release.version) != ignored
    }

    data class GitHubRelease(
        val version: String,
        val tagName: String,
        val releaseNotes: String,
        val apkUrl: String,
        val apkName: String,
        val sizeBytes: Long,
        val htmlUrl: String
    )

    sealed interface CheckResult {
        data class UpToDate(val currentVersion: String) : CheckResult
        data class UpdateAvailable(val release: GitHubRelease) : CheckResult
        data object NoApkAsset : CheckResult
        data class Error(val message: String?) : CheckResult
    }

    fun checkForUpdate(currentVersion: String = BuildConfig.VERSION_NAME): CheckResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Dechainer/$currentVersion")
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                return CheckResult.Error("HTTP $code")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseLatestReleaseJson(body, currentVersion)
        } catch (e: Exception) {
            CheckResult.Error(e.localizedMessage ?: e.javaClass.simpleName)
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseLatestReleaseJson(rawJson: String, currentVersion: String): CheckResult {
        return try {
            val json = JSONObject(rawJson)
            val tagName = json.optString("tag_name").trim()
            if (tagName.isEmpty()) return CheckResult.Error("Missing tag_name")

            val remoteVersion = normalizeVersion(tagName)
            if (!isNewerVersion(remoteVersion, normalizeVersion(currentVersion))) {
                return CheckResult.UpToDate(normalizeVersion(currentVersion))
            }

            val assets = json.optJSONArray("assets") ?: return CheckResult.NoApkAsset
            val apkAsset = pickApkAsset(assets) ?: return CheckResult.NoApkAsset

            val apkUrl = apkAsset.optString("browser_download_url")
            if (!apkUrl.startsWith("https://")) return CheckResult.NoApkAsset

            CheckResult.UpdateAvailable(
                GitHubRelease(
                    version = remoteVersion,
                    tagName = tagName,
                    releaseNotes = json.optString("body").trim(),
                    apkUrl = apkUrl,
                    apkName = apkAsset.optString("name"),
                    sizeBytes = apkAsset.optLong("size", -1L),
                    htmlUrl = json.optString("html_url")
                )
            )
        } catch (e: Exception) {
            CheckResult.Error(e.localizedMessage ?: e.javaClass.simpleName)
        }
    }

    private fun pickApkAsset(assets: org.json.JSONArray): JSONObject? {
        val candidates = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .filter { asset ->
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                name.endsWith(".apk", ignoreCase = true) && url.startsWith("https://")
            }
        return candidates.firstOrNull { asset ->
            val name = asset.optString("name").lowercase()
            !name.contains("debug") && !name.contains("unsigned")
        } ?: candidates.firstOrNull()
    }

    internal fun normalizeVersion(version: String): String =
        version.trim().removePrefix("v").removePrefix("V")

    internal fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteCore = remote.substringBefore('-').substringBefore('+')
        val currentCore = current.substringBefore('-').substringBefore('+')

        val remoteParts = remoteCore.split('.').mapNotNull { it.toIntOrNull() }
        val currentParts = currentCore.split('.').mapNotNull { it.toIntOrNull() }
        if (remoteParts.isEmpty() || currentParts.isEmpty()) return false

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }

        // Same numeric core: a stable release (no '-') is newer than a pre-release (has '-').
        val remotePre = remote.contains('-')
        val currentPre = current.contains('-')
        return !remotePre && currentPre
    }

    /**
     * Downloads the APK at [url] into the app's update cache directory. Returns null if the download
     * fails, or rethrows [CancellationException] if cancelled.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null

        val dir = File(context.cacheDir, "apk_update").apply { mkdirs() }
        val target = File(dir, "github-release.apk")
        target.delete()

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Dechainer/${BuildConfig.VERSION_NAME}")
            }
            if (connection.responseCode !in 200..299) {
                return@withContext null
            }

            val totalBytes = connection.contentLengthLong
            var downloaded = 0L
            val buffer = ByteArray(16 * 1024)

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    while (true) {
                        ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, totalBytes)
                    }
                }
            }
            target
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (_: Exception) {
            target.delete()
            null
        } finally {
            connection?.disconnect()
        }
    }
}
