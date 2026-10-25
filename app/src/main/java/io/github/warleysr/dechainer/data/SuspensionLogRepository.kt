package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import io.github.warleysr.dechainer.models.SuspensionLogEntry
import io.github.warleysr.dechainer.models.SuspensionReason
import org.json.JSONArray
import org.json.JSONObject

object SuspensionLogRepository {
    private const val PREFS_NAME = "suspension_logs_prefs"
    private const val KEY_LOGS = "logs"
    private const val MAX_LOGS = 100

    private var cachedLogs: List<SuspensionLogEntry>? = null
    private val lock = Any()

    fun getLogs(context: Context): List<SuspensionLogEntry> {
        synchronized(lock) {
            cachedLogs?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_LOGS, null) ?: return emptyList()
            val logs = decode(json)
            cachedLogs = logs
            return logs
        }
    }

    fun log(
        context: Context,
        packageName: String,
        reason: SuspensionReason,
        details: String? = null
    ) {
        synchronized(lock) {
            val current = getLogs(context).toMutableList()

            val now = System.currentTimeMillis()
            // Deduplicate: avoid logging the same package with the same reason within 5 seconds
            val recent = current.firstOrNull { it.packageName == packageName && it.reason == reason }
            if (recent != null && now - recent.timestamp < 5000) {
                return
            }

            val appName = try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                packageName
            }

            val entry = SuspensionLogEntry(
                packageName = packageName,
                appName = appName,
                timestamp = now,
                reason = reason,
                details = details
            )

            current.add(0, entry)
            while (current.size > MAX_LOGS) {
                current.removeAt(current.size - 1)
            }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { putString(KEY_LOGS, encode(current)) }
            cachedLogs = current
        }
    }

    fun clearLogs(context: Context) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit { remove(KEY_LOGS) }
            cachedLogs = emptyList()
        }
    }

    private fun encode(logs: List<SuspensionLogEntry>): String {
        val array = JSONArray()
        logs.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id)
                put("packageName", entry.packageName)
                put("appName", entry.appName)
                put("timestamp", entry.timestamp)
                put("reason", entry.reason.name)
                entry.details?.let { put("details", it) }
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decode(json: String): List<SuspensionLogEntry> {
        val list = mutableListOf<SuspensionLogEntry>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", java.util.UUID.randomUUID().toString())
                val packageName = obj.getString("packageName")
                val appName = obj.optString("appName", packageName)
                val timestamp = obj.getLong("timestamp")
                val reasonStr = obj.getString("reason")
                val reason = try {
                    SuspensionReason.valueOf(reasonStr)
                } catch (_: Exception) {
                    SuspensionReason.MANUAL
                }
                val details = if (obj.has("details")) obj.getString("details") else null
                list.add(SuspensionLogEntry(id, packageName, appName, timestamp, reason, details))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
