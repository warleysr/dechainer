package io.github.warleysr.dechainer.data

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.models.SuspensionReason
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ApkUpdateInstallerTest {

    @Test
    fun `requestDowngrade sets INSTALL_REQUEST_DOWNGRADE and INSTALL_ALLOW_DOWNGRADE in installFlags`() {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        ApkUpdateInstaller.requestDowngrade(params)

        val flags = PackageInstaller.SessionParams::class.java.getField("installFlags").getInt(params)
        (flags and 0x00000080) shouldBe 0x00000080
        (flags and 0x00100000) shouldBe 0x00100000
    }

    @Test
    fun `restorePreDowngradeState logs suspension reason when restoring suspended package`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val shadowDpm = shadowOf(dpm)
        val admin = DeviceAdmin.component
        shadowDpm.setProfileOwner(admin)
        shadowDpm.setDeviceOwner(admin)

        val pkg = "com.example.updated"
        shadowOf(context.packageManager).installPackage(
            PackageInfo().apply {
                packageName = pkg
                versionCode = 1
            }
        )

        // Simulate app was suspended with RATED_18_PLUS
        SuspensionLogRepository.log(context, pkg, SuspensionReason.RATED_18_PLUS)
        dpm.setPackagesSuspended(admin, arrayOf(pkg), true)

        ApkUpdateInstaller.savePreDowngradeState(context, pkg)

        // Clear logs to verify restore re-logs it
        SuspensionLogRepository.clearLogs(context)

        ApkUpdateInstaller.restorePreDowngradeState(context, pkg)

        val logs = SuspensionLogRepository.getLogs(context)
        logs.size shouldBe 1
        logs.first().packageName shouldBe pkg
        logs.first().reason shouldBe SuspensionReason.RATED_18_PLUS
    }
}
