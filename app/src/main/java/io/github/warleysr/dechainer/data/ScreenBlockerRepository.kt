package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import io.github.warleysr.dechainer.models.AppScreenHierarchy
import io.github.warleysr.dechainer.models.ScreenBlockMode
import io.github.warleysr.dechainer.models.ScreenBlockRule
import io.github.warleysr.dechainer.models.WindowHierarchyNode
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Persists unified screen/window blocking rules ([ScreenBlockRule]) and discovered
 * per-app window hierarchies ([AppScreenHierarchy]).
 *
 * Also migrates legacy `blocked_activities` and `passive_words_<pkg>` entries once.
 */
object ScreenBlockerRepository {
    const val PREFS_NAME = "activity_blocker_prefs"
    private const val LEGACY_BLOCKED_WORDS_PREFS = "blocked_words_prefs"
    private const val LEGACY_BLOCKED_ACTIVITIES_KEY = "blocked_activities"
    private const val LEGACY_PASSIVE_PREFIX = "passive_words_"

    const val KEY_RULES = "screen_block_rules_v2"
    const val KEY_HIERARCHIES = "screen_hierarchies_v1"
    private const val KEY_MIGRATED = "screen_rules_migrated_v2"

    private const val MAX_STORED_APPS = 60
    private const val MAX_CHILDREN_PER_NODE = 30
    private const val TIMESTAMP_PERSIST_INTERVAL_MS = 60_000L

    private val lock = Any()

    @Volatile
    private var cachedRules: List<ScreenBlockRule>? = null

    @Volatile
    private var cachedHierarchies: List<AppScreenHierarchy>? = null

    private var lastTimestampWriteMs: Long = 0L

    fun resetCacheForTests() {
        synchronized(lock) {
            cachedRules = null
            cachedHierarchies = null
            lastTimestampWriteMs = 0L
        }
    }

    fun migrateIfNeeded(context: Context) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_MIGRATED, false)) return

            val migratedRules = decodeRules(prefs.getString(KEY_RULES, null)).toMutableList()

            // 1. Migrate legacy flat blocked_activities
            val legacyActivities = prefs.getStringSet(LEGACY_BLOCKED_ACTIVITIES_KEY, emptySet()) ?: emptySet()
            legacyActivities.sorted().forEach { className ->
                if (className.isNotBlank()) {
                    val alreadyExists = migratedRules.any {
                        it.packageName.isEmpty() && it.hierarchyPath == listOf(className)
                    }
                    if (!alreadyExists) {
                        migratedRules.add(
                            ScreenBlockRule(
                                packageName = "",
                                hierarchyPath = listOf(className),
                                mode = ScreenBlockMode.TOTAL
                            )
                        )
                    }
                }
            }

            // 2. Migrate legacy per-package passive_words_<pkg> from blocked_words_prefs
            val wordsPrefs = context.getSharedPreferences(LEGACY_BLOCKED_WORDS_PREFS, Context.MODE_PRIVATE)
            val legacyPassiveKeys = wordsPrefs.all.keys.filter { it.startsWith(LEGACY_PASSIVE_PREFIX) }
            if (legacyPassiveKeys.isNotEmpty()) {
                wordsPrefs.edit {
                    legacyPassiveKeys.forEach { key ->
                        val pkg = key.substringAfter(LEGACY_PASSIVE_PREFIX)
                        val wordsSet = (wordsPrefs.all[key] as? Set<*>)
                            ?.filterIsInstance<String>()
                            ?.map { it.trim() }
                            ?.filter { it.isNotBlank() }
                            ?.toSet()
                            ?: emptySet()
                        if (pkg.isNotBlank() && wordsSet.isNotEmpty()) {
                            val existingIdx = migratedRules.indexOfFirst {
                                it.packageName == pkg && it.hierarchyPath.isEmpty()
                            }
                            if (existingIdx >= 0) {
                                val existing = migratedRules[existingIdx]
                                if (existing.mode == ScreenBlockMode.PASSIVE_WORDS) {
                                    migratedRules[existingIdx] = existing.copy(words = existing.words + wordsSet)
                                }
                            } else {
                                migratedRules.add(
                                    ScreenBlockRule(
                                        packageName = pkg,
                                        hierarchyPath = emptyList(),
                                        mode = ScreenBlockMode.PASSIVE_WORDS,
                                        words = wordsSet
                                    )
                                )
                            }
                        }
                        remove(key)
                    }
                }
            }

            prefs.edit {
                putString(KEY_RULES, encodeRules(migratedRules))
                putBoolean(KEY_MIGRATED, true)
                remove(LEGACY_BLOCKED_ACTIVITIES_KEY)
            }
            cachedRules = migratedRules
        }
    }

    fun getRules(context: Context, forceRefresh: Boolean = false): List<ScreenBlockRule> {
        migrateIfNeeded(context)
        if (!forceRefresh) cachedRules?.let { return it }
        synchronized(lock) {
            if (!forceRefresh) cachedRules?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fresh = decodeRules(prefs.getString(KEY_RULES, null))
            cachedRules = fresh
            return fresh
        }
    }

    fun upsertRule(context: Context, rule: ScreenBlockRule): List<ScreenBlockRule> {
        migrateIfNeeded(context)
        synchronized(lock) {
            val current = getRules(context).toMutableList()
            val cleanedRule = if (rule.mode == ScreenBlockMode.PASSIVE_WORDS) {
                rule.copy(words = rule.words.map { it.trim() }.filter { it.isNotBlank() }.toSet())
            } else {
                rule.copy(words = emptySet())
            }

            val existingIdx = current.indexOfFirst {
                it.id == cleanedRule.id || it.targetKey() == cleanedRule.targetKey()
            }

            if (cleanedRule.mode == ScreenBlockMode.PASSIVE_WORDS && cleanedRule.words.isEmpty()) {
                if (existingIdx >= 0) current.removeAt(existingIdx)
            } else if (existingIdx >= 0) {
                current[existingIdx] = cleanedRule.copy(id = current[existingIdx].id)
            } else {
                current.add(cleanedRule)
            }

            persistRules(context, current)
            return current
        }
    }

    fun removeRule(context: Context, ruleId: String): List<ScreenBlockRule> {
        migrateIfNeeded(context)
        synchronized(lock) {
            val updated = getRules(context).filterNot { it.id == ruleId }
            persistRules(context, updated)
            return updated
        }
    }

    fun getHierarchies(context: Context, forceRefresh: Boolean = false): List<AppScreenHierarchy> {
        if (!forceRefresh) cachedHierarchies?.let { return it }
        synchronized(lock) {
            if (!forceRefresh) cachedHierarchies?.let { return it }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val fresh = decodeHierarchies(prefs.getString(KEY_HIERARCHIES, null))
            cachedHierarchies = fresh
            return fresh
        }
    }

    /**
     * Records a visited screen/window [path] under [packageName] into the saved hierarchy tree.
     * Writes to disk immediately when a new app or node is discovered, and throttles
     * timestamp-only updates to keep accessibility event handling lightweight.
     */
    fun recordPath(
        context: Context,
        packageName: String,
        path: List<String>,
        now: Long = System.currentTimeMillis()
    ) {
        if (packageName.isBlank() || path.isEmpty()) return
        synchronized(lock) {
            val current = getHierarchies(context).toMutableList()
            val idx = current.indexOfFirst { it.packageName == packageName }
            val existingApp = if (idx >= 0) current[idx] else null

            val (updatedNodes, structureChanged) = mergePath(existingApp?.nodes ?: emptyList(), path, 0, now)
            val updatedApp = AppScreenHierarchy(
                packageName = packageName,
                lastSeenTimestamp = now,
                nodes = updatedNodes
            )

            if (idx >= 0) {
                current[idx] = updatedApp
            } else {
                current.add(0, updatedApp)
            }

            val isNewApp = existingApp == null
            if (current.size > MAX_STORED_APPS) {
                val protectedPackages = getRules(context).map { it.packageName }.toSet()
                val sorted = current.sortedWith(
                    compareByDescending<AppScreenHierarchy> { it.packageName in protectedPackages }
                        .thenByDescending { it.lastSeenTimestamp }
                )
                current.clear()
                current.addAll(sorted.take(MAX_STORED_APPS))
            }

            cachedHierarchies = current

            val shouldPersist = isNewApp || structureChanged ||
                (now - lastTimestampWriteMs >= TIMESTAMP_PERSIST_INTERVAL_MS)
            if (shouldPersist) {
                lastTimestampWriteMs = now
                persistHierarchies(context, current)
            }
        }
    }

    /**
     * Removes discovered hierarchies for apps/nodes that don't have active rules attached.
     */
    fun clearUnusedHierarchies(context: Context): List<AppScreenHierarchy> {
        synchronized(lock) {
            val rulesByPkg = getRules(context)
                .filter { it.packageName.isNotBlank() }
                .groupBy { it.packageName }

            val kept = mutableListOf<AppScreenHierarchy>()
            rulesByPkg.forEach { (pkg, pkgRules) ->
                var nodes = emptyList<WindowHierarchyNode>()
                val now = System.currentTimeMillis()
                pkgRules.forEach { rule ->
                    if (rule.hierarchyPath.isNotEmpty()) {
                        nodes = mergePath(nodes, rule.hierarchyPath, 0, now).first
                    }
                }
                kept.add(AppScreenHierarchy(packageName = pkg, lastSeenTimestamp = now, nodes = nodes))
            }
            persistHierarchies(context, kept)
            return kept
        }
    }

    private fun mergePath(
        nodes: List<WindowHierarchyNode>,
        path: List<String>,
        depth: Int,
        now: Long
    ): Pair<List<WindowHierarchyNode>, Boolean> {
        if (depth >= path.size) return nodes to false
        val segment = path[depth]
        val existingIdx = nodes.indexOfFirst { it.className == segment }
        return if (existingIdx >= 0) {
            val existing = nodes[existingIdx]
            val (newChildren, childChanged) = mergePath(existing.children, path, depth + 1, now)
            val updatedNode = existing.copy(lastSeenTimestamp = now, children = newChildren)
            val mutable = nodes.toMutableList()
            mutable[existingIdx] = updatedNode
            mutable to childChanged
        } else {
            val (children, _) = mergePath(emptyList(), path, depth + 1, now)
            val newNode = WindowHierarchyNode(
                className = segment,
                lastSeenTimestamp = now,
                children = children
            )
            val mutable = nodes.toMutableList()
            mutable.add(0, newNode)
            if (mutable.size > MAX_CHILDREN_PER_NODE) {
                val trimmed = mutable.sortedByDescending { it.lastSeenTimestamp }.take(MAX_CHILDREN_PER_NODE)
                trimmed to true
            } else {
                mutable to true
            }
        }
    }

    private fun persistRules(context: Context, rules: List<ScreenBlockRule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_RULES, encodeRules(rules)) }
        cachedRules = rules
    }

    private fun persistHierarchies(context: Context, hierarchies: List<AppScreenHierarchy>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString(KEY_HIERARCHIES, encodeHierarchies(hierarchies)) }
        cachedHierarchies = hierarchies
    }

    internal fun encodeRules(rules: List<ScreenBlockRule>): String {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.id)
                put("packageName", rule.packageName)
                put("hierarchyPath", JSONArray(rule.hierarchyPath))
                put("mode", rule.mode.name)
                if (rule.words.isNotEmpty()) {
                    put("words", JSONArray(rule.words.sorted()))
                }
            })
        }
        return array.toString()
    }

    internal fun decodeRules(json: String?): List<ScreenBlockRule> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ScreenBlockRule>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id").ifBlank { UUID.randomUUID().toString() }
                val packageName = obj.optString("packageName", "")
                val pathArr = obj.optJSONArray("hierarchyPath")
                val path = mutableListOf<String>()
                if (pathArr != null) {
                    for (j in 0 until pathArr.length()) {
                        val seg = pathArr.optString(j)
                        if (seg.isNotBlank()) path.add(seg)
                    }
                }
                val mode = try {
                    ScreenBlockMode.valueOf(obj.optString("mode", ScreenBlockMode.TOTAL.name))
                } catch (_: Exception) {
                    ScreenBlockMode.TOTAL
                }
                val words = mutableSetOf<String>()
                val wordsArr = obj.optJSONArray("words")
                if (wordsArr != null) {
                    for (j in 0 until wordsArr.length()) {
                        val w = wordsArr.optString(j).trim()
                        if (w.isNotBlank()) words.add(w)
                    }
                }
                list.add(ScreenBlockRule(id, packageName, path, mode, words))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }

    internal fun encodeHierarchies(hierarchies: List<AppScreenHierarchy>): String {
        val array = JSONArray()
        hierarchies.forEach { app ->
            array.put(JSONObject().apply {
                put("packageName", app.packageName)
                put("lastSeenTimestamp", app.lastSeenTimestamp)
                put("nodes", encodeNodes(app.nodes))
            })
        }
        return array.toString()
    }

    private fun encodeNodes(nodes: List<WindowHierarchyNode>): JSONArray {
        val array = JSONArray()
        nodes.forEach { node ->
            array.put(JSONObject().apply {
                put("className", node.className)
                put("lastSeenTimestamp", node.lastSeenTimestamp)
                if (node.children.isNotEmpty()) {
                    put("children", encodeNodes(node.children))
                }
            })
        }
        return array
    }

    internal fun decodeHierarchies(json: String?): List<AppScreenHierarchy> {
        if (json.isNullOrBlank()) return emptyList()
        val list = mutableListOf<AppScreenHierarchy>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pkg = obj.optString("packageName")
                if (pkg.isBlank()) continue
                val ts = obj.optLong("lastSeenTimestamp", 0L)
                val nodes = decodeNodes(obj.optJSONArray("nodes"))
                list.add(AppScreenHierarchy(pkg, ts, nodes))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }

    private fun decodeNodes(array: JSONArray?): List<WindowHierarchyNode> {
        if (array == null) return emptyList()
        val list = mutableListOf<WindowHierarchyNode>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val className = obj.optString("className")
            if (className.isBlank()) continue
            val ts = obj.optLong("lastSeenTimestamp", 0L)
            val children = decodeNodes(obj.optJSONArray("children"))
            list.add(WindowHierarchyNode(className, ts, children))
        }
        return list
    }
}
