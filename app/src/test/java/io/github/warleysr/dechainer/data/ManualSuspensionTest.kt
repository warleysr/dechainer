package io.github.warleysr.dechainer.data

import android.app.admin.DevicePolicyManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDevicePolicyManager

@RunWith(RobolectricTestRunner::class)
class ManualSuspensionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var shadowDpm: ShadowDevicePolicyManager

    @Before
    fun setUp() {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        shadowDpm = shadowOf(dpm)
        // Clean up manual suspensions prefs between tests
        context.getSharedPreferences("manual_suspensions_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `untouched app is not manually controlled`() {
        val pkg = "com.example.untouched"
        assertFalse(AppRepository.isManuallyControlled(pkg))
        assertFalse(AppRepository.isManuallyUnsuspended(pkg))
        assertFalse(AppRepository.isManuallySuspended(pkg))
    }

    @Test
    fun `manually unsuspending an app marks it as manually unsuspended`() {
        val pkg = "com.cronometer.android.gold"
        AppRepository.setManualSuspension(pkg, false)

        assertTrue(AppRepository.isManuallyControlled(pkg))
        assertTrue(AppRepository.isManuallyUnsuspended(pkg))
        assertFalse(AppRepository.isManuallySuspended(pkg))
    }

    @Test
    fun `manually suspending an app marks it as manually suspended`() {
        val pkg = "com.example.distracting"
        AppRepository.setManualSuspension(pkg, true)

        assertTrue(AppRepository.isManuallyControlled(pkg))
        assertTrue(AppRepository.isManuallySuspended(pkg))
        assertFalse(AppRepository.isManuallyUnsuspended(pkg))
    }

    @Test
    fun `clearing manual suspension resets control state`() {
        val pkg = "com.example.temp"
        AppRepository.setManualSuspension(pkg, false)
        assertTrue(AppRepository.isManuallyControlled(pkg))

        AppRepository.clearManualSuspension(pkg)
        assertFalse(AppRepository.isManuallyControlled(pkg))
        assertFalse(AppRepository.isManuallyUnsuspended(pkg))
    }
}
