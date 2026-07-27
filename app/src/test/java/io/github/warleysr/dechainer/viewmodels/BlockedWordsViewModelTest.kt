package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.awaitUntil
import io.github.warleysr.dechainer.support.installApp
import io.github.warleysr.dechainer.support.prefs
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [BlockedWordsViewModel]'s own logic through [Fixtures]: word text is normalized (trimmed,
 * blank lines dropped, duplicates collapsed) before it is persisted, toggling a target package is a
 * reversible set operation that never clobbers other selections, the passive-words map is rebuilt from
 * the per-package prefixed keys on construction and drops a package once its words are cleared, and the
 * installable-apps list leaves out both the app's own package and system apps.
 */
@RunWith(AndroidJUnit4::class)
class BlockedWordsViewModelTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"
    private val otherPackage = "com.example.other"

    @Test
    fun `updateWords trims, drops blank lines and de-duplicates before persisting`() {
        val viewModel = Fixtures.blockedWordsViewModel()

        viewModel.updateWords("  kumar  \n\n  bahis  \nkumar\n   ")

        prefs("blocked_words_prefs").getStringSet("blocked_words", emptySet()) shouldBe
            setOf("kumar", "bahis")
    }

    @Test
    fun `toggling a package on then off returns the selection to empty`() {
        val viewModel = Fixtures.blockedWordsViewModel()

        viewModel.toggleAppSelection(targetPackage)
        viewModel.targetPackages shouldBe setOf(targetPackage)

        viewModel.toggleAppSelection(targetPackage)
        viewModel.targetPackages shouldBe emptySet<String>()
        prefs("blocked_words_prefs").getStringSet("target_packages", emptySet()) shouldBe
            emptySet<String>()
    }

    @Test
    fun `toggling a second package keeps the first one selected`() {
        val viewModel = Fixtures.blockedWordsViewModel()

        viewModel.toggleAppSelection(targetPackage)
        viewModel.toggleAppSelection(otherPackage)

        viewModel.targetPackages shouldBe setOf(targetPackage, otherPackage)
        prefs("blocked_words_prefs").getStringSet("target_packages", emptySet()) shouldBe
            setOf(targetPackage, otherPackage)
    }

    @Test
    fun `passive words are loaded into the map when the view model is constructed`() {
        prefs("blocked_words_prefs").edit()
            .putStringSet("passive_words_$targetPackage", setOf("kumar"))
            .commit()

        val viewModel = Fixtures.blockedWordsViewModel()

        viewModel.passiveWordsMap shouldBe mapOf(targetPackage to "kumar")
    }

    @Test
    fun `clearing passive words removes the package from the map`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updatePassiveWords(targetPackage, "kumar")
        viewModel.passiveWordsMap shouldBe mapOf(targetPackage to "kumar")

        viewModel.updatePassiveWords(targetPackage, "")

        viewModel.passiveWordsMap shouldBe emptyMap<String, String>()
    }

    @Test
    fun `the apps list excludes the app's own package and system apps`() {
        val userApp = "com.example.userapp"
        val systemApp = "com.android.systemapp"
        installApp(userApp, "User App")
        installApp(systemApp, "System App", system = true)

        val viewModel = Fixtures.blockedWordsViewModel()
        awaitUntil { viewModel.apps.any { it.packageName == userApp } }

        val ownPackage = ApplicationProvider.getApplicationContext<Context>().packageName
        val packages = viewModel.apps.map { it.packageName }
        packages shouldContain userApp
        packages shouldNotContain systemApp
        packages shouldNotContain ownPackage
    }
}
