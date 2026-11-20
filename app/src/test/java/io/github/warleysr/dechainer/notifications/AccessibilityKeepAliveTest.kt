package io.github.warleysr.dechainer.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.data.AccessibilityKeepAliveSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AccessibilityKeepAliveTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.getSharedPreferences(AccessibilityKeepAliveSettings.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `keep alive setting is disabled by default`() {
        assertFalse(AccessibilityKeepAliveSettings.isEnabled(context))
    }

    @Test
    fun `keep alive setting can be enabled and disabled`() {
        AccessibilityKeepAliveSettings.setEnabled(context, true)
        assertTrue(AccessibilityKeepAliveSettings.isEnabled(context))

        AccessibilityKeepAliveSettings.setEnabled(context, false)
        assertFalse(AccessibilityKeepAliveSettings.isEnabled(context))
    }

    @Test
    fun `buildNotification creates valid ongoing notification and channel`() {
        val notification = AccessibilityKeepAliveService.buildNotification(context)

        assertNotNull(notification)
        // Notification should be ongoing
        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(AccessibilityKeepAliveService.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun `service enabled helper checks secure settings properly`() {
        val serviceName = ComponentName(context, DechainerAccessibilityService::class.java).flattenToString()

        // Initially disabled
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        assertFalse(DechainerAccessibilityService.isServiceEnabled(context))

        // When enabled with our service
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other.service/Service:$serviceName"
        )
        assertTrue(DechainerAccessibilityService.isServiceEnabled(context))
    }

    @Test
    fun `start and stop issue appropriate service intents`() {
        AccessibilityKeepAliveService.start(context)
        val shadowApp = shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
        val nextStartedService = shadowApp.nextStartedService
        assertNotNull(nextStartedService)
        assertEquals(
            AccessibilityKeepAliveService::class.java.name,
            nextStartedService.component?.className
        )

        AccessibilityKeepAliveService.stop(context)
        val nextStoppedService = shadowApp.nextStoppedService
        assertNotNull(nextStoppedService)
        assertEquals(
            AccessibilityKeepAliveService::class.java.name,
            nextStoppedService.component?.className
        )
    }
}
