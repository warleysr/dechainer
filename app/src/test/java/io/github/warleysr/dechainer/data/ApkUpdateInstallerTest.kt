package io.github.warleysr.dechainer.data

import android.content.pm.PackageInstaller
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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
}
