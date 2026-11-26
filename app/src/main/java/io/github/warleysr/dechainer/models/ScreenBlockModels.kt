package io.github.warleysr.dechainer.models

import java.util.UUID

enum class ScreenBlockMode {
    TOTAL,
    PASSIVE_WORDS
}

data class ScreenBlockRule(
    val id: String = UUID.randomUUID().toString(),
    val packageName: String,
    /**
     * Sequence of window/class names from the app root down to the targeted window.
     * - Empty list: targets the entire app (all windows/screens inside [packageName]).
     * - Non-empty: e.g. ["com.app.MainActivity", "android.widget.FrameLayout", "android.app.Dialog"].
     *
     * Legacy rules migrated from the old flat `blocked_activities` set have an empty [packageName]
     * and a single-element [hierarchyPath] holding the raw class name.
     */
    val hierarchyPath: List<String>,
    val mode: ScreenBlockMode,
    val words: Set<String> = emptySet()
) {
    val isLegacyClassOnly: Boolean
        get() = packageName.isEmpty() && hierarchyPath.size == 1

    fun targetKey(): Pair<String, List<String>> = packageName to hierarchyPath

    companion object {
        fun simpleClassName(className: String): String {
            val afterDot = className.substringAfterLast('.')
            val afterDollar = afterDot.substringAfterLast('$')
            return afterDollar.ifBlank { afterDot.ifBlank { className } }
        }
    }
}

data class WindowHierarchyNode(
    val className: String,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val children: List<WindowHierarchyNode> = emptyList()
)

data class AppScreenHierarchy(
    val packageName: String,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val nodes: List<WindowHierarchyNode> = emptyList()
)
