package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.models.AppGroup
import io.github.warleysr.dechainer.models.TimeLimit
import io.github.warleysr.dechainer.models.TimeWindow
import io.github.warleysr.dechainer.models.TimeWindows
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.util.UUID
import java.util.concurrent.TimeUnit

object AppGroupRepository {
    private val context = DechainerApplication.getInstance()

    const val PREFS_NAME = "app_groups"
    private const val KEY_GROUPS = "groups"
    private const val USAGE_PREFS_NAME = "group_usage_stats"

    @Volatile
    private var cachedGroups: List<AppGroup>? = null
    private val lock = Any()

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getGroups(forceRefresh: Boolean = false): List<AppGroup> {
        if (!forceRefresh) cachedGroups?.let { return it }
        synchronized(lock) {
            if (!forceRefresh) cachedGroups?.let { return it }
            val fresh = decode(prefs().getString(KEY_GROUPS, null))
            cachedGroups = fresh
            return fresh
        }
    }

    fun getGroupForPackage(packageName: String): AppGroup? =
        getGroups().firstOrNull { packageName in it.packageNames }

    fun createGroup(name: String): AppGroup {
        val group = AppGroup(id = UUID.randomUUID().toString(), name = name)
        synchronized(lock) { persist(getGroups() + group) }
        return group
    }

    fun deleteGroup(groupId: String) {
        synchronized(lock) { persist(getGroups().filterNot { it.id == groupId }) }
        context.getSharedPreferences(USAGE_PREFS_NAME, Context.MODE_PRIVATE).edit { remove(groupId) }
    }

    fun renameGroup(groupId: String, name: String) = updateGroup(groupId) { it.copy(name = name) }

    fun setGroupTimeLimit(groupId: String, limit: TimeLimit) =
        updateGroup(groupId) { it.copy(timeLimit = limit) }

    fun setGroupTimeWindows(groupId: String, windows: TimeWindows) =
        updateGroup(groupId) { it.copy(timeWindows = windows) }

    /** Replaces the group's members. Any package added here is removed from whichever other group held it, keeping each app in at most one group. */
    fun setGroupPackages(groupId: String, packageNames: Set<String>) {
        synchronized(lock) {
            val updated = getGroups().map { group ->
                if (group.id == groupId) group.copy(packageNames = packageNames)
                else group.copy(packageNames = group.packageNames - packageNames)
            }
            persist(updated)
        }
    }

    /** Assigns [packageName] to [groupId] (or removes it from any group when null), keeping the one-group-per-app invariant. */
    fun setPackageGroup(packageName: String, groupId: String?) {
        synchronized(lock) {
            val updated = getGroups().map { group ->
                when {
                    group.id == groupId -> group.copy(packageNames = group.packageNames + packageName)
                    packageName in group.packageNames -> group.copy(packageNames = group.packageNames - packageName)
                    else -> group
                }
            }
            persist(updated)
        }
    }

    fun getGroupUsage(groupId: String, inMinutes: Boolean = false): Long {
        val packageNames = getGroups().firstOrNull { it.id == groupId }?.packageNames ?: emptySet()
        val used = DechainerAccessibilityService.getGroupUsageMillis(context, groupId, packageNames)
        return if (inMinutes) TimeUnit.MILLISECONDS.toMinutes(used) else used
    }

    private fun updateGroup(groupId: String, transform: (AppGroup) -> AppGroup) {
        synchronized(lock) {
            persist(getGroups().map { if (it.id == groupId) transform(it) else it })
        }
    }

    private fun persist(groups: List<AppGroup>) {
        prefs().edit { putString(KEY_GROUPS, encode(groups)) }
        cachedGroups = groups
    }

    private fun encode(groups: List<AppGroup>): String {
        val array = JSONArray()
        groups.forEach { group ->
            array.put(JSONObject().apply {
                put("id", group.id)
                put("name", group.name)
                put("packages", JSONArray(group.packageNames.toList()))
                put("limit", group.timeLimit.dailyMinutes)
                group.timeLimit.weeklyMinutes?.let { put("weeklyLimit", JSONArray(it)) }
                put("windows", AppTimeWindows.encodeList(group.timeWindows.dailyWindows))
                group.timeWindows.weeklyWindows?.let { put("weeklyWindows", AppTimeWindows.encodeWeekly(it)) }
            })
        }
        return array.toString()
    }

    private fun decode(json: String?): List<AppGroup> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                val packagesArray = obj.getJSONArray("packages")
                val packages = (0 until packagesArray.length()).map { packagesArray.getString(it) }.toSet()
                val windowsArray = obj.optJSONArray("windows")
                val windows = if (windowsArray != null) {
                    (0 until windowsArray.length()).map { j ->
                        val w = windowsArray.getJSONObject(j)
                        TimeWindow(w.getInt("s"), w.getInt("e"))
                    }
                } else emptyList()
                val weeklyWindows = obj.optJSONArray("weeklyWindows")
                    ?.let { arr -> AppTimeWindows.decodeWeekly(arr) }
                // Groups saved before per-weekday limits existed only have the daily "limit".
                val weekly = obj.optJSONArray("weeklyLimit")
                    ?.let { arr -> (0 until arr.length()).map { arr.getInt(it) } }
                    ?.takeIf { it.size == DayOfWeek.entries.size }
                AppGroup(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    packageNames = packages,
                    timeLimit = weekly?.let { TimeLimit.perDay(it) } ?: TimeLimit.daily(obj.getInt("limit")),
                    timeWindows = weeklyWindows?.let { TimeWindows.perDay(it) } ?: TimeWindows.daily(windows)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
