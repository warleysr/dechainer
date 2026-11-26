package io.github.warleysr.dechainer.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.models.ScreenBlockMode
import io.github.warleysr.dechainer.models.ScreenBlockRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScreenBlockerRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ScreenBlockerRepository.PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
        context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE).edit { clear() }
        ScreenBlockerRepository.resetCacheForTests()
    }

    @Test
    fun `migrates legacy blocked_activities and passive_words into unified rules`() {
        context.getSharedPreferences(ScreenBlockerRepository.PREFS_NAME, Context.MODE_PRIVATE).edit {
            putStringSet("blocked_activities", setOf("com.android.settings.Settings"))
        }
        context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE).edit {
            putStringSet("passive_words_com.reddit.frontpage", setOf("nsfw", "18+"))
        }

        val rules = ScreenBlockerRepository.getRules(context, forceRefresh = true)
        assertEquals(2, rules.size)

        val legacyActivityRule = rules.first { it.isLegacyClassOnly }
        assertEquals(listOf("com.android.settings.Settings"), legacyActivityRule.hierarchyPath)
        assertEquals(ScreenBlockMode.TOTAL, legacyActivityRule.mode)

        val passiveAppRule = rules.first { it.packageName == "com.reddit.frontpage" }
        assertEquals(emptyList<String>(), passiveAppRule.hierarchyPath)
        assertEquals(ScreenBlockMode.PASSIVE_WORDS, passiveAppRule.mode)
        assertEquals(setOf("nsfw", "18+"), passiveAppRule.words)

        // Legacy passive key should be cleaned up after migration
        val remainingWordsPrefs = context.getSharedPreferences("blocked_words_prefs", Context.MODE_PRIVATE).all
        assertTrue(remainingWordsPrefs.keys.none { it.startsWith("passive_words_") })
    }

    @Test
    fun `persists and merges multi-level screen hierarchies`() {
        val pkg = "com.example.app"
        ScreenBlockerRepository.recordPath(
            context,
            pkg,
            listOf("com.example.app.MainActivity", "android.widget.FrameLayout", "android.app.Dialog"),
            now = 1_000L
        )
        ScreenBlockerRepository.recordPath(
            context,
            pkg,
            listOf("com.example.app.MainActivity", "android.widget.FrameLayout", "android.app.AlertDialog"),
            now = 2_000L
        )

        ScreenBlockerRepository.resetCacheForTests()
        val hierarchies = ScreenBlockerRepository.getHierarchies(context, forceRefresh = true)
        assertEquals(1, hierarchies.size)

        val mainNode = hierarchies[0].nodes.single()
        assertEquals("com.example.app.MainActivity", mainNode.className)

        val frameNode = mainNode.children.single()
        assertEquals("android.widget.FrameLayout", frameNode.className)
        assertEquals(
            setOf("android.app.Dialog", "android.app.AlertDialog"),
            frameNode.children.map { it.className }.toSet()
        )
    }

    @Test
    fun `upsertRule replaces rule on same hierarchy target and clearUnusedHierarchies keeps ruled paths`() {
        val pkg = "com.example.app"
        val dialogPath = listOf("com.example.app.MainActivity", "android.widget.FrameLayout", "android.app.Dialog")
        ScreenBlockerRepository.recordPath(context, pkg, dialogPath, now = 1_000L)
        ScreenBlockerRepository.recordPath(context, "com.unused.app", listOf("com.unused.app.Home"), now = 1_500L)

        ScreenBlockerRepository.upsertRule(
            context,
            ScreenBlockRule(
                packageName = pkg,
                hierarchyPath = dialogPath,
                mode = ScreenBlockMode.TOTAL
            )
        )
        // Updating the same path to PASSIVE_WORDS replaces instead of duplicating
        ScreenBlockerRepository.upsertRule(
            context,
            ScreenBlockRule(
                packageName = pkg,
                hierarchyPath = dialogPath,
                mode = ScreenBlockMode.PASSIVE_WORDS,
                words = setOf("confirmar")
            )
        )

        val rules = ScreenBlockerRepository.getRules(context, forceRefresh = true)
        assertEquals(1, rules.size)
        assertEquals(ScreenBlockMode.PASSIVE_WORDS, rules[0].mode)
        assertEquals(setOf("confirmar"), rules[0].words)

        val kept = ScreenBlockerRepository.clearUnusedHierarchies(context)
        assertEquals(1, kept.size)
        assertEquals(pkg, kept[0].packageName)
        assertEquals("android.app.Dialog", kept[0].nodes.single().children.single().children.single().className)
    }
}
