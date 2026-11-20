package io.github.warleysr.dechainer.tiles

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.Tile
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.security.SecurityManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDevicePolicyManager

@RunWith(RobolectricTestRunner::class)
class QuickSettingsTilesTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var shadowDpm: ShadowDevicePolicyManager

    @Before
    fun setUp() {
        context.getSharedPreferences("recovery_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(FocusMode.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        shadowDpm = shadowOf(dpm)
    }

    private fun grantSetupAndDeviceOwner() {
        SecurityManager.saveRecoveryCode(context, "ABCDEFGHIJKLMNOP")
        shadowDpm.setDeviceOwner(ComponentName(context, DechainerDeviceAdminReceiver::class.java))
    }

    @Test
    fun `impulse lock tile is unavailable until recovery and device owner are ready`() {
        val service = Robolectric.buildService(ImpulseLockTileService::class.java).create().get()
        service.onStartListening()

        assertEquals(Tile.STATE_UNAVAILABLE, service.qsTile.state)
    }

    @Test
    fun `impulse lock tile activates impulse block and cannot be cancelled early`() {
        grantSetupAndDeviceOwner()
        val service = Robolectric.buildService(ImpulseLockTileService::class.java).create().get()

        service.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)

        service.onClick()
        assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
        assertTrue(SecurityManager.getImpulseBlockRemainingTime(context) > 0L)

        // Second tap must not cancel the active impulse block.
        service.onClick()
        assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
        assertTrue(SecurityManager.getImpulseBlockRemainingTime(context) > 0L)
    }

    @Test
    fun `focus mode tile starts pauses without releasing and resumes`() {
        grantSetupAndDeviceOwner()
        val service = Robolectric.buildService(FocusModeTileService::class.java).create().get()

        service.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, service.qsTile.state)

        // Tap 1: start focus
        service.onClick()
        val started = FocusMode.getStatus(context)
        assertNotNull(started)
        assertEquals(FocusMode.Phase.FOCUS, started!!.phase)
        assertFalse(started.paused)
        assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)

        // Tap 2: plain pause (apps stay suspended)
        service.onClick()
        val paused = FocusMode.getStatus(context)!!
        assertTrue(paused.paused)
        assertFalse(paused.releasedApps)
        assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)

        // Tap 3: resume focus
        service.onClick()
        val resumed = FocusMode.getStatus(context)!!
        assertFalse(resumed.paused)
        assertEquals(Tile.STATE_ACTIVE, service.qsTile.state)
    }
}
