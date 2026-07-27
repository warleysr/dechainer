package io.github.warleysr.dechainer.browser

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.RestrictionEntry
import android.content.RestrictionsManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.BrowserRestrictionsManager
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.installBrowser
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Exercises [BrowserRestrictionsManager] against a Robolectric package manager and a mockk
 * [RestrictionsManager]: `getPossibleBrowsers` collapses the same browser resolved by all three of its
 * intent queries into one entry and drops our own package, `isBrowser` mirrors that membership,
 * `supportsRestrictions` requires both `URLBlocklist` and `ForceGoogleSafeSearch` in the browser's
 * manifest restrictions, and `applyRestrictions` returns without touching any browser when no
 * `blocked_lists_json` is saved.
 */
@RunWith(AndroidJUnit4::class)
class BrowserRestrictionsManagerTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = BrowserRestrictionsManager(context)
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

    private fun managerReporting(entries: List<RestrictionEntry>): BrowserRestrictionsManager {
        val restrictionsManager = mockk<RestrictionsManager>()
        every { restrictionsManager.getManifestRestrictions(any()) } returns entries
        val mockContext = mockk<Context>()
        every { mockContext.getSystemService(Context.RESTRICTIONS_SERVICE) } returns restrictionsManager
        return BrowserRestrictionsManager(mockContext)
    }

    @Test
    fun `getPossibleBrowsers deduplicates the three intent queries and drops our own package`() {
        installBrowser("com.fake.browser")
        installBrowser("com.other.browser")
        installBrowser(context.packageName)

        val browsers = manager.getPossibleBrowsers().map { it.activityInfo.packageName }

        browsers shouldContainExactlyInAnyOrder listOf("com.fake.browser", "com.other.browser")
    }

    @Test
    fun `isBrowser is true for an installed browser and false otherwise`() {
        installBrowser("com.fake.browser")

        manager.isBrowser("com.fake.browser") shouldBe true
        manager.isBrowser("com.not.abrowser") shouldBe false
    }

    @Test
    fun `supportsRestrictions is true when the browser declares both required keys`() {
        val entries = listOf(
            RestrictionEntry(RestrictionEntry.TYPE_STRING, "URLBlocklist"),
            RestrictionEntry(RestrictionEntry.TYPE_STRING, "ForceGoogleSafeSearch"),
        )

        managerReporting(entries).supportsRestrictions("com.fake.browser") shouldBe true
    }

    @Test
    fun `supportsRestrictions is false when a required key is missing`() {
        val entries = listOf(RestrictionEntry(RestrictionEntry.TYPE_STRING, "URLBlocklist"))

        managerReporting(entries).supportsRestrictions("com.fake.browser") shouldBe false
    }

    @Test
    fun `applyRestrictions leaves installed browsers untouched when no blocklist json is saved`() {
        installBrowser("com.fake.browser")
        shadowOf(dpm).setDeviceOwner(admin)

        shouldNotThrowAny { manager.applyRestrictions() }

        dpm.getApplicationRestrictions(admin, "com.fake.browser").isEmpty shouldBe true
    }
}
