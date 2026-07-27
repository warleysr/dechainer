package io.github.warleysr.dechainer.viewmodels

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With no device owner granted and Shizuku absent — the state a fresh install is in — each test
 * constructs one of the six ViewModels through [Fixtures] and exercises its primary mutator, asserting
 * that it degrades to doing nothing rather than throwing. This is the layer that proves the product
 * stays crash-free when its privileges are missing. Nothing here grants a device owner or touches Shizuku, 
 * so the environment is genuinely unprivileged.
 */
@RunWith(AndroidJUnit4::class)
class DegradedModeTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"

    @Test
    fun `BlockedWordsViewModel constructs and toggles selection without device owner`() {
        shouldNotThrowAny {
            val viewModel = Fixtures.blockedWordsViewModel()
            viewModel.toggleAppSelection(targetPackage)
        }
    }

    @Test
    fun `AppsViewModel constructs and applies a limit and blocking without device owner`() {
        shouldNotThrowAny {
            val viewModel = Fixtures.appsViewModel()
            viewModel.setAppTimeLimit(targetPackage, 30)
            viewModel.blockApp(targetPackage, true)
        }
    }

    @Test
    fun `ActivityBlockerViewModel constructs and adds a blocked activity without device owner`() {
        shouldNotThrowAny {
            val viewModel = Fixtures.activityBlockerViewModel()
            viewModel.addBlockedActivity("com.example.app.SettingsActivity")
        }
    }

    @Test
    fun `BrowserRestrictionsViewModel constructs and saves a list without device owner`() {
        shouldNotThrowAny {
            val viewModel = Fixtures.browserRestrictionsViewModel()
            viewModel.saveOrUpdateList("list-1", "Adult", "a.com")
        }
    }

    @Test
    fun `RestrictionsViewModel constructs and applies changes without device owner`() {
        shouldNotThrowAny {
            val viewModel = Fixtures.restrictionsViewModel()
            viewModel.applyChanges()
        }
    }

    @Test
    fun `DeviceOwnerViewModel constructs and reports no privileges without device owner`() {
        val viewModel = Fixtures.deviceOwnerViewModel()

        viewModel.isDeviceOwner() shouldBe false
        viewModel.isShizukuInstalled() shouldBe false
    }
}
