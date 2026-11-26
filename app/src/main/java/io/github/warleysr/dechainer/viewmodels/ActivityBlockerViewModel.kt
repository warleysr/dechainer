package io.github.warleysr.dechainer.viewmodels

import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.ScreenBlockerRepository
import io.github.warleysr.dechainer.models.AppItem
import io.github.warleysr.dechainer.models.ScreenBlockRule
import io.github.warleysr.dechainer.models.WindowHierarchyNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppHierarchyUi(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val lastSeenTimestamp: Long,
    val nodes: List<WindowHierarchyNode>,
    val totalNodeCount: Int,
    val activeRuleCount: Int
)

class ActivityBlockerViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val packageManager = context.packageManager

    private val appNameCache = HashMap<String, String>()
    private val appIconCache = HashMap<String, Drawable>()

    var rules by mutableStateOf<List<ScreenBlockRule>>(emptyList())
        private set

    var appHierarchies by mutableStateOf<List<AppHierarchyUi>>(emptyList())
        private set

    var installedApps by mutableStateOf<List<AppItem>>(emptyList())
        private set

    var isLoadingApps by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        val loadedRules = ScreenBlockerRepository.getRules(context, forceRefresh = true)
        val loadedHierarchies = ScreenBlockerRepository.getHierarchies(context, forceRefresh = true)
        rules = loadedRules

        val rulesByPkg = loadedRules
            .filter { it.packageName.isNotBlank() }
            .groupBy { it.packageName }

        val hierarchyMap = loadedHierarchies.associateBy { it.packageName }.toMutableMap()

        // Ensure apps that have rules (e.g. migrated passive-word rules) are also shown in the hierarchy list.
        rulesByPkg.keys.forEach { pkg ->
            if (pkg !in hierarchyMap) {
                hierarchyMap[pkg] = io.github.warleysr.dechainer.models.AppScreenHierarchy(
                    packageName = pkg,
                    lastSeenTimestamp = 0L,
                    nodes = emptyList()
                )
            }
        }

        appHierarchies = hierarchyMap.values
            .map { hierarchy ->
                val pkg = hierarchy.packageName
                AppHierarchyUi(
                    packageName = pkg,
                    appName = resolveAppName(pkg),
                    icon = resolveAppIcon(pkg),
                    lastSeenTimestamp = hierarchy.lastSeenTimestamp,
                    nodes = hierarchy.nodes,
                    totalNodeCount = countNodes(hierarchy.nodes),
                    activeRuleCount = rulesByPkg[pkg]?.size ?: 0
                )
            }
            .sortedWith(
                compareByDescending<AppHierarchyUi> { it.activeRuleCount > 0 }
                    .thenByDescending { it.lastSeenTimestamp }
                    .thenBy { it.appName.lowercase() }
            )
    }

    fun loadInstalledApps() {
        if (installedApps.isNotEmpty() || isLoadingApps) return
        viewModelScope.launch {
            isLoadingApps = true
            installedApps = withContext(Dispatchers.IO) {
                AppRepository.getApps()
            }
            isLoadingApps = false
        }
    }

    fun upsertRule(rule: ScreenBlockRule) {
        ScreenBlockerRepository.upsertRule(context, rule)
        refresh()
    }

    fun removeRule(ruleId: String) {
        ScreenBlockerRepository.removeRule(context, ruleId)
        refresh()
    }

    fun clearUnusedHierarchies() {
        ScreenBlockerRepository.clearUnusedHierarchies(context)
        refresh()
    }

    fun findRule(packageName: String, hierarchyPath: List<String>): ScreenBlockRule? {
        return rules.firstOrNull { it.packageName == packageName && it.hierarchyPath == hierarchyPath }
    }

    fun resolveAppName(packageName: String): String {
        if (packageName.isBlank()) return ""
        appNameCache[packageName]?.let { return it }
        val name = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(packageManager).toString()
        } catch (_: Exception) {
            packageName
        }
        appNameCache[packageName] = name
        return name
    }

    fun resolveAppIcon(packageName: String): Drawable {
        if (packageName.isBlank()) return packageManager.defaultActivityIcon
        appIconCache[packageName]?.let { return it }
        val icon = try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.loadIcon(packageManager)
        } catch (_: Exception) {
            packageManager.defaultActivityIcon
        }
        appIconCache[packageName] = icon
        return icon
    }

    private fun countNodes(nodes: List<WindowHierarchyNode>): Int {
        return nodes.sumOf { 1 + countNodes(it.children) }
    }
}
