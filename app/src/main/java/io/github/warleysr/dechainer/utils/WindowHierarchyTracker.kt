package io.github.warleysr.dechainer.utils

import io.github.warleysr.dechainer.models.ScreenBlockMode
import io.github.warleysr.dechainer.models.ScreenBlockRule

/**
 * Tracks the currently active window/screen hierarchy path inside the foreground app
 * (e.g. `MainActivity > FrameLayout > Dialog`) across accessibility events.
 */
class WindowHierarchyTracker {

    data class StackEntry(
        val windowId: Int,
        val className: String,
        val isActivity: Boolean
    )

    private var activePackage: String? = null
    private val activeStack = mutableListOf<StackEntry>()

    fun currentPath(): List<String> = activeStack.map { it.className }

    fun reset() {
        activePackage = null
        activeStack.clear()
    }

    /**
     * Updates the stack when `TYPE_WINDOW_STATE_CHANGED` fires and returns the resulting path.
     *
     * - Switching packages clears the previous app's stack.
     * - An Activity starts a new root under the app.
     * - A non-Activity window/container (Dialog, FrameLayout, BottomSheet, PopupWindow, etc.)
     *   nests beneath the currently live parent window(s). If it already exists in the stack
     *   (e.g. the user dismissed a child dialog and returned to its parent), the stack pops
     *   back to that entry.
     */
    fun onWindowStateChanged(
        packageName: String,
        className: String,
        windowId: Int,
        isActivity: Boolean,
        liveWindowIds: Set<Int>
    ): List<String> {
        if (className.isBlank()) return currentPath()

        if (packageName != activePackage) {
            activePackage = packageName
            activeStack.clear()
        }

        if (isActivity) {
            activeStack.clear()
            activeStack.add(StackEntry(windowId, className, isActivity = true))
            return currentPath()
        }

        pruneClosedWindows(liveWindowIds, keepWindowId = windowId)

        val existingIndex = activeStack.indexOfFirst { entry ->
            entry.className == className && (windowId == -1 || entry.windowId == -1 || entry.windowId == windowId)
        }
        if (existingIndex >= 0) {
            while (activeStack.size > existingIndex + 1) {
                activeStack.removeAt(activeStack.lastIndex)
            }
            if (windowId != -1 && activeStack[existingIndex].windowId == -1) {
                activeStack[existingIndex] = activeStack[existingIndex].copy(windowId = windowId)
            }
        } else if (activeStack.size < MAX_DEPTH) {
            activeStack.add(StackEntry(windowId, className, isActivity = false))
        } else {
            activeStack[activeStack.lastIndex] = StackEntry(windowId, className, isActivity = false)
        }

        return currentPath()
    }

    /**
     * Reconciles the stack on `TYPE_WINDOW_CONTENT_CHANGED` so that dialogs/overlays whose
     * dismissal didn't emit a fresh `TYPE_WINDOW_STATE_CHANGED` are pruned promptly.
     */
    fun onWindowContentChanged(
        packageName: String,
        windowId: Int,
        liveWindowIds: Set<Int>
    ): List<String> {
        if (packageName != activePackage) return emptyList()
        pruneClosedWindows(liveWindowIds, keepWindowId = -1)
        return currentPath()
    }

    private fun pruneClosedWindows(liveWindowIds: Set<Int>, keepWindowId: Int) {
        if (liveWindowIds.isEmpty()) return
        // Prune from top down any non-activity window whose windowId is no longer alive.
        while (activeStack.isNotEmpty()) {
            val top = activeStack.last()
            if (top.isActivity) break
            if (top.windowId != -1 && top.windowId != keepWindowId && top.windowId !in liveWindowIds) {
                activeStack.removeAt(activeStack.lastIndex)
            } else {
                break
            }
        }
    }

    companion object {
        const val MAX_DEPTH = 8
    }
}

/**
 * Pure matching logic between a [ScreenBlockRule] and the currently active `(packageName, activePath)`.
 */
object ScreenRuleMatcher {

    fun matches(rule: ScreenBlockRule, packageName: String, activePath: List<String>): Boolean {
        if (rule.isLegacyClassOnly) {
            val targetClass = rule.hierarchyPath.firstOrNull() ?: return false
            return when (rule.mode) {
                ScreenBlockMode.TOTAL -> activePath.lastOrNull() == targetClass
                ScreenBlockMode.PASSIVE_WORDS -> targetClass in activePath
            }
        }

        if (rule.packageName != packageName) return false

        // Empty hierarchyPath means the rule targets the entire app.
        if (rule.hierarchyPath.isEmpty()) return true

        if (activePath.size < rule.hierarchyPath.size) return false
        for (i in rule.hierarchyPath.indices) {
            if (activePath[i] != rule.hierarchyPath[i]) return false
        }
        return true
    }

    /**
     * Determines whether replacing [existing] with [updated] loosens the restriction
     * (and therefore should require the recovery gate when applicable).
     */
    fun isLoosening(existing: ScreenBlockRule?, updated: ScreenBlockRule?): Boolean {
        if (existing == null) return false
        if (updated == null) return true
        return when {
            existing.mode == ScreenBlockMode.TOTAL && updated.mode == ScreenBlockMode.PASSIVE_WORDS -> true
            existing.mode == ScreenBlockMode.PASSIVE_WORDS && updated.mode == ScreenBlockMode.TOTAL -> false
            existing.mode == ScreenBlockMode.PASSIVE_WORDS && updated.mode == ScreenBlockMode.PASSIVE_WORDS ->
                !updated.words.containsAll(existing.words)
            else -> false
        }
    }
}
