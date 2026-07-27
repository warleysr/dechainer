package io.github.warleysr.dechainer.viewmodels

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.ShadowDechainerDpm
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Exercises [RestrictionsViewModel] through [Fixtures]: the three recommended keys resolve to distinct
 * non-blank UserManager constants, the draft toggles are pure in-memory state, and applyChanges
 * reconciles the applied map with the draft by routing add/clear through the device policy manager —
 * backed by [ShadowDechainerDpm] — and reading it back. Run under API 30 and 36 so the apply/load cycle
 * over the framework Bundle is proven on both. The reflection-derived `otherKeys` list is deliberately
 * not asserted: Robolectric's instrumented android jar strips `final` off UserManager's DISALLOW_*
 * constants, so the view model's `Modifier.isFinal` filter drops every key and otherKeys is always empty
 * here — a real device keeps them final. Only the recommended keys, read directly, survive this
 * environment.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [30, 36], shadows = [ShadowDechainerDpm::class])
class RestrictionsViewModelTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

    @Test
    fun `recommendedKeys resolve to three distinct non-blank restriction keys`() {
        val viewModel = Fixtures.restrictionsViewModel()

        viewModel.recommendedKeys.size shouldBe 3
        viewModel.recommendedKeys.toSet().size shouldBe 3
        viewModel.recommendedKeys.forEach { it.shouldNotBeBlank() }
    }

    @Test
    fun `toggleDraft, toggleAllDrafts and isAllDraftsEnabled are pure draft-state operations`() {
        val viewModel = Fixtures.restrictionsViewModel()
        val keys = listOf(UserManager.DISALLOW_CONFIG_VPN, UserManager.DISALLOW_FACTORY_RESET)

        viewModel.toggleDraft(keys[0], true)
        viewModel.draftRestrictions[keys[0]] shouldBe true

        viewModel.toggleAllDrafts(keys, true)
        viewModel.isAllDraftsEnabled(keys) shouldBe true

        viewModel.toggleDraft(keys[1], false)
        viewModel.isAllDraftsEnabled(keys) shouldBe false
    }

    @Test
    fun `applyChanges reconciles applied state with the draft through the policy manager`() {
        shadowOf(dpm).setDeviceOwner(admin)
        val viewModel = Fixtures.restrictionsViewModel()
        val key = UserManager.DISALLOW_CONFIG_VPN

        viewModel.toggleDraft(key, true)
        viewModel.applyChanges()

        viewModel.appliedRestrictions[key] shouldBe true
        viewModel.draftRestrictions[key] shouldBe true

        viewModel.toggleDraft(key, false)
        viewModel.applyChanges()

        viewModel.appliedRestrictions[key] shouldBe false
    }
}
