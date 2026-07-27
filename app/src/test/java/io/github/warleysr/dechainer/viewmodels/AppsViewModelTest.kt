package io.github.warleysr.dechainer.viewmodels

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.github.warleysr.dechainer.support.Fixtures
import io.github.warleysr.dechainer.support.awaitUntil
import io.github.warleysr.dechainer.support.installApp
import io.github.warleysr.dechainer.support.prefs
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises [AppsViewModel]'s persistence and resilience logic through [Fixtures]: a time limit of zero
 * deletes its key rather than storing a literal zero the service would read as a real limit, usage read
 * back from the stats file comes out raw in millis and floored when asked for whole minutes, a reopen
 * time round-trips through the view model's own setter and getter and clears on zero, and loadApps keeps
 * every app in the list when the unprivileged device policy manager raises a SecurityException for each
 * one instead of collapsing to an empty list.
 */
@RunWith(AndroidJUnit4::class)
class AppsViewModelTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val targetPackage = "com.example.socialapp"

    @Test
    fun `setting a time limit of zero removes the key instead of storing zero`() {
        val viewModel = Fixtures.appsViewModel()
        viewModel.setAppTimeLimit(targetPackage, 30)
        prefs("app_limits").getInt(targetPackage, -1) shouldBe 30

        viewModel.setAppTimeLimit(targetPackage, 0)

        prefs("app_limits").contains(targetPackage) shouldBe false
    }

    @Test
    fun `getAppUsage returns raw millis and floors the whole-minute conversion`() {
        prefs("internal_usage_stats").edit().putLong(targetPackage, 90_000L).commit()

        val viewModel = Fixtures.appsViewModel()

        viewModel.getAppUsage(targetPackage) shouldBe 90_000L
        viewModel.getAppUsage(targetPackage, inMinutes = true) shouldBe 1L
        viewModel.getAppUsage("com.example.unknown") shouldBe 0L
    }

    @Test
    fun `a reopen time round-trips through the view model and clears on zero`() {
        val viewModel = Fixtures.appsViewModel()

        viewModel.setAppReopenTime(targetPackage, 60)
        viewModel.getAppReopenTime(targetPackage) shouldBe 60

        viewModel.setAppReopenTime(targetPackage, 0)
        viewModel.getAppReopenTime(targetPackage) shouldBe 0
        prefs("reopen_times").contains(targetPackage) shouldBe false
    }

    @Test
    fun `loadApps keeps apps listed when the device policy manager denies access`() {
        val userApp = "com.example.userapp"
        installApp(userApp, "User App")

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DechainerDeviceAdminReceiver::class.java)
        shouldThrow<SecurityException> { dpm.isApplicationHidden(admin, userApp) }

        val viewModel = Fixtures.appsViewModel()
        awaitUntil { viewModel.apps.any { it.packageName == userApp } }

        val app = viewModel.apps.first { it.packageName == userApp }
        app.isHidden shouldBe false
        app.isUninstallBlocked shouldBe false
        viewModel.apps.map { it.packageName } shouldContain userApp
    }
}
