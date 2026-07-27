package io.github.warleysr.dechainer.contract

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.prefs
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Each test drives the real writer of one persistence contract and then reads the value straight out
 * of the `SharedPreferences` file with the exact raw name, key and typed getter the reading side uses,
 * so a writer whose literal or type drifts away from its reader turns this test red. The usage-stats
 * row runs the reverse way — the service is the writer there — so a raw long is written and read back
 * through the real view model. The `security_prefs` / `shuffle_keyboard` row is written and read only
 * from Compose UI and is left to the Compose test. Nothing here reaches into class
 * internals.
 */
@RunWith(AndroidJUnit4::class)
class PreferencesContractTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"

    @Test
    fun `blocked words are persisted as a string set under the key the service reads`() {
        val viewModel = Fixtures.blockedWordsViewModel()

        viewModel.updateWords("kumar\nbahis")

        prefs("blocked_words_prefs").getStringSet("blocked_words", emptySet()) shouldBe
            setOf("kumar", "bahis")
    }

    @Test
    fun `target packages are persisted as a string set under the key the service reads`() {
        val viewModel = Fixtures.blockedWordsViewModel()

        viewModel.toggleAppSelection(targetPackage)

        prefs("blocked_words_prefs").getStringSet("target_packages", emptySet()) shouldBe
            setOf(targetPackage)
    }

    @Test
    fun `passive words are persisted under the per-package prefixed key as a string set`() {
        val viewModel = Fixtures.blockedWordsViewModel()

        viewModel.updatePassiveWords(targetPackage, "kumar\nbahis")

        prefs("blocked_words_prefs").getStringSet("passive_words_$targetPackage", emptySet()) shouldBe
            setOf("kumar", "bahis")
    }

    @Test
    fun `clearing passive words removes the per-package key`() {
        val viewModel = Fixtures.blockedWordsViewModel()
        viewModel.updatePassiveWords(targetPackage, "kumar")

        viewModel.updatePassiveWords(targetPackage, "")

        prefs("blocked_words_prefs").contains("passive_words_$targetPackage") shouldBe false
    }

    @Test
    fun `blocked activities are persisted as a string set under the key the service reads`() {
        val viewModel = Fixtures.activityBlockerViewModel()

        viewModel.addBlockedActivity("com.example.app.SettingsActivity")

        prefs("activity_blocker_prefs").getStringSet("blocked_activities", emptySet()) shouldBe
            setOf("com.example.app.SettingsActivity")
    }

    @Test
    fun `an app time limit is persisted as an int under the package-named key`() {
        val viewModel = Fixtures.appsViewModel()

        viewModel.setAppTimeLimit(targetPackage, 30)

        prefs("app_limits").getInt(targetPackage, 0) shouldBe 30
    }

    @Test
    fun `an app reopen time is persisted as an int under the package-named key`() {
        val viewModel = Fixtures.appsViewModel()

        viewModel.setAppReopenTime(targetPackage, 60)

        prefs("reopen_times").getInt(targetPackage, 0) shouldBe 60
    }

    @Test
    fun `usage stats written as a long are read back by the view model in millis and minutes`() {
        prefs("internal_usage_stats").edit().putLong(targetPackage, 300_000L).commit()

        val viewModel = Fixtures.appsViewModel()

        viewModel.getAppUsage(targetPackage) shouldBe 300_000L
        viewModel.getAppUsage(targetPackage, inMinutes = true) shouldBe 5L
    }

    @Test
    fun `a browser blocklist is persisted as json under the key the manager reads with id title and sites`() {
        val viewModel = Fixtures.browserRestrictionsViewModel()

        viewModel.saveOrUpdateList("list-1", "Adult", "a.com\nb.com")

        val json = prefs("browser_prefs").getString("blocked_lists_json", null).shouldNotBeNull()
        val array = JSONArray(json)
        array.length() shouldBe 1
        val obj = array.getJSONObject(0)
        obj.getString("id") shouldBe "list-1"
        obj.getString("title") shouldBe "Adult"
        val sites = obj.getJSONArray("sites")
        (0 until sites.length()).map { sites.getString(it) } shouldContainExactlyInAnyOrder
            listOf("a.com", "b.com")
    }

    @Test
    fun `the recovery code is persisted as a string under the key SecurityManager reads`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        SecurityManager.saveRecoveryCode(context, "ABCDEFGHIJKLMNOP")

        prefs("recovery_prefs").getString("recovery_code", null) shouldBe "ABCDEFGHIJKLMNOP"
        SecurityManager.getRecoveryCode(context) shouldBe "ABCDEFGHIJKLMNOP"
    }
}
