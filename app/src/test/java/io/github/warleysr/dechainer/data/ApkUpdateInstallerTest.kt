package io.github.warleysr.dechainer.data

import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.Signature
import android.content.pm.SigningInfo
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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

    @Test
    fun `signaturesMatch returns true when single signer certificates match`() {
        val sigA = Signature("01020304")
        val archive = packageInfoWithSigning(history = arrayOf(sigA))
        val installed = packageInfoWithSigning(history = arrayOf(sigA))

        ApkUpdateInstaller.signaturesMatch(archive, installed) shouldBe true
    }

    @Test
    fun `signaturesMatch returns true when older archive signer is in installed key rotation history`() {
        val oldSig = Signature("01020304")
        val newSig = Signature("05060708")
        val archive = packageInfoWithSigning(history = arrayOf(oldSig))
        val installed = packageInfoWithSigning(history = arrayOf(oldSig, newSig))

        ApkUpdateInstaller.signaturesMatch(archive, installed) shouldBe true
    }

    @Test
    fun `signaturesMatch returns false when signers differ`() {
        val sigA = Signature("01020304")
        val sigB = Signature("05060708")
        val archive = packageInfoWithSigning(history = arrayOf(sigA))
        val installed = packageInfoWithSigning(history = arrayOf(sigB))

        ApkUpdateInstaller.signaturesMatch(archive, installed) shouldBe false
    }

    @Test
    fun `signaturesMatch requires exact set match when multiple signers are used`() {
        val sigA = Signature("01020304")
        val sigB = Signature("05060708")
        val archive = packageInfoWithSigning(multiple = true, contentSigners = arrayOf(sigA, sigB))
        val installedSame = packageInfoWithSigning(multiple = true, contentSigners = arrayOf(sigB, sigA))
        val installedPartial = packageInfoWithSigning(multiple = true, contentSigners = arrayOf(sigA))

        ApkUpdateInstaller.signaturesMatch(archive, installedSame) shouldBe true
        ApkUpdateInstaller.signaturesMatch(archive, installedPartial) shouldBe false
    }

    private fun packageInfoWithSigning(
        multiple: Boolean = false,
        history: Array<Signature>? = null,
        contentSigners: Array<Signature>? = history
    ): PackageInfo {
        val signingInfo = mockk<SigningInfo> {
            every { hasMultipleSigners() } returns multiple
            every { signingCertificateHistory } returns history
            every { apkContentsSigners } returns contentSigners
        }
        return PackageInfo().apply {
            this.signingInfo = signingInfo
        }
    }
}
