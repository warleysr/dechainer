package io.github.warleysr.dechainer.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.utils.ShizukuRunner
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceOwnerSetupBypassTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `ShizukuRunner ProcessResult holds correct output and exit code`() {
        val result = ShizukuRunner.ProcessResult(exitCode = 0, output = "1", error = "")
        result.exitCode shouldBe 0
        result.output shouldBe "1"
        result.error shouldBe ""
    }

    @Test
    fun `getUserId returns 0 for primary user`() {
        val userId = DeviceOwnerRepository.getUserId()
        userId shouldBe 0
    }

    @Test
    fun `accounts detected string formats account count correctly`() {
        val formatted = context.getString(R.string.privileges_description_accounts_detected, 3)
        formatted shouldContain "3"
        formatted shouldContain "account"
    }

    @Test
    fun `restoreStuckUserSetupComplete does not crash when Shizuku binder is unavailable`() {
        // Shizuku is not running in pure unit test environment, so this should return safely without throwing
        DeviceOwnerRepository.restoreStuckUserSetupComplete()
    }
}
