package io.github.warleysr.dechainer.utils

import io.github.warleysr.dechainer.models.ScreenBlockMode
import io.github.warleysr.dechainer.models.ScreenBlockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowHierarchyTrackerTest {

    @Test
    fun `tracks nested hierarchy App to MainActivity to FrameLayout to Dialog`() {
        val tracker = WindowHierarchyTracker()
        val pkg = "com.example.app"

        val p1 = tracker.onWindowStateChanged(
            packageName = pkg,
            className = "com.example.app.MainActivity",
            windowId = 10,
            isActivity = true,
            liveWindowIds = setOf(10)
        )
        assertEquals(listOf("com.example.app.MainActivity"), p1)

        val p2 = tracker.onWindowStateChanged(
            packageName = pkg,
            className = "android.widget.FrameLayout",
            windowId = 11,
            isActivity = false,
            liveWindowIds = setOf(10, 11)
        )
        assertEquals(
            listOf("com.example.app.MainActivity", "android.widget.FrameLayout"),
            p2
        )

        val p3 = tracker.onWindowStateChanged(
            packageName = pkg,
            className = "android.app.Dialog",
            windowId = 12,
            isActivity = false,
            liveWindowIds = setOf(10, 11, 12)
        )
        assertEquals(
            listOf("com.example.app.MainActivity", "android.widget.FrameLayout", "android.app.Dialog"),
            p3
        )

        // Closing Dialog and receiving content change on FrameLayout prunes dead windowId 12
        val p4 = tracker.onWindowContentChanged(
            packageName = pkg,
            windowId = 11,
            liveWindowIds = setOf(10, 11)
        )
        assertEquals(
            listOf("com.example.app.MainActivity", "android.widget.FrameLayout"),
            p4
        )

        // Returning to MainActivity resets to MainActivity
        val p5 = tracker.onWindowStateChanged(
            packageName = pkg,
            className = "com.example.app.MainActivity",
            windowId = 10,
            isActivity = true,
            liveWindowIds = setOf(10)
        )
        assertEquals(listOf("com.example.app.MainActivity"), p5)
    }

    @Test
    fun `switching packages resets previous stack`() {
        val tracker = WindowHierarchyTracker()
        tracker.onWindowStateChanged("com.app.one", "com.app.one.MainActivity", 1, true, setOf(1))
        tracker.onWindowStateChanged("com.app.one", "android.app.Dialog", 2, false, setOf(1, 2))

        val path = tracker.onWindowStateChanged("com.app.two", "com.app.two.HomeActivity", 3, true, setOf(3))
        assertEquals(listOf("com.app.two.HomeActivity"), path)
    }

    @Test
    fun `rule targeting Dialog does not match MainActivity or FrameLayout`() {
        val pkg = "com.example.app"
        val dialogPath = listOf(
            "com.example.app.MainActivity",
            "android.widget.FrameLayout",
            "android.app.Dialog"
        )
        val totalRule = ScreenBlockRule(
            packageName = pkg,
            hierarchyPath = dialogPath,
            mode = ScreenBlockMode.TOTAL
        )
        val passiveRule = ScreenBlockRule(
            packageName = pkg,
            hierarchyPath = dialogPath,
            mode = ScreenBlockMode.PASSIVE_WORDS,
            words = setOf("forbidden")
        )

        assertFalse(ScreenRuleMatcher.matches(totalRule, pkg, listOf("com.example.app.MainActivity")))
        assertFalse(ScreenRuleMatcher.matches(totalRule, pkg, listOf("com.example.app.MainActivity", "android.widget.FrameLayout")))
        assertTrue(ScreenRuleMatcher.matches(totalRule, pkg, dialogPath))

        assertFalse(ScreenRuleMatcher.matches(passiveRule, pkg, listOf("com.example.app.MainActivity")))
        assertTrue(ScreenRuleMatcher.matches(passiveRule, pkg, dialogPath))
        assertFalse(ScreenRuleMatcher.matches(passiveRule, "com.other.app", dialogPath))
    }

    @Test
    fun `entire-app rule matches any hierarchy path inside the package`() {
        val pkg = "com.example.app"
        val appRule = ScreenBlockRule(
            packageName = pkg,
            hierarchyPath = emptyList(),
            mode = ScreenBlockMode.PASSIVE_WORDS,
            words = setOf("nsfw")
        )

        assertTrue(ScreenRuleMatcher.matches(appRule, pkg, emptyList()))
        assertTrue(ScreenRuleMatcher.matches(appRule, pkg, listOf("com.example.app.MainActivity")))
        assertTrue(ScreenRuleMatcher.matches(appRule, pkg, listOf("com.example.app.MainActivity", "android.app.Dialog")))
        assertFalse(ScreenRuleMatcher.matches(appRule, "com.other.app", listOf("com.other.app.MainActivity")))
    }

    @Test
    fun `legacy class-only rule matches regardless of package`() {
        val legacyRule = ScreenBlockRule(
            packageName = "",
            hierarchyPath = listOf("com.android.settings.Settings"),
            mode = ScreenBlockMode.TOTAL
        )

        assertTrue(ScreenRuleMatcher.matches(legacyRule, "com.android.settings", listOf("com.android.settings.Settings")))
        assertFalse(ScreenRuleMatcher.matches(legacyRule, "com.android.settings", listOf("com.android.settings.Other")))
    }

    @Test
    fun `isLoosening detects mode downgrades and removed words`() {
        val total = ScreenBlockRule(packageName = "pkg", hierarchyPath = listOf("A"), mode = ScreenBlockMode.TOTAL)
        val passive2 = ScreenBlockRule(packageName = "pkg", hierarchyPath = listOf("A"), mode = ScreenBlockMode.PASSIVE_WORDS, words = setOf("a", "b"))
        val passive3 = ScreenBlockRule(packageName = "pkg", hierarchyPath = listOf("A"), mode = ScreenBlockMode.PASSIVE_WORDS, words = setOf("a", "b", "c"))
        val passive1 = ScreenBlockRule(packageName = "pkg", hierarchyPath = listOf("A"), mode = ScreenBlockMode.PASSIVE_WORDS, words = setOf("a"))

        assertFalse(ScreenRuleMatcher.isLoosening(null, total))
        assertTrue(ScreenRuleMatcher.isLoosening(total, null))
        assertTrue(ScreenRuleMatcher.isLoosening(total, passive2))
        assertFalse(ScreenRuleMatcher.isLoosening(passive2, total))
        assertFalse(ScreenRuleMatcher.isLoosening(passive2, passive3))
        assertTrue(ScreenRuleMatcher.isLoosening(passive2, passive1))
    }
}
