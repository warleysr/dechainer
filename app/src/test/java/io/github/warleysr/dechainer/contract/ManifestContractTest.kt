package io.github.warleysr.dechainer.contract

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.warleysr.dechainer.DechainerAccessibilityService
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import io.github.warleysr.dechainer.activities.BlockedWordActivity
import io.github.warleysr.dechainer.activities.MainActivity
import io.github.warleysr.dechainer.activities.ReopeningLimitActivity
import io.github.warleysr.dechainer.activities.TimeUpActivity
import io.github.warleysr.dechainer.support.DechainerTestRule
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

/**
 * Each test reads the app's own merged manifest through the real [PackageManager] and pins it against
 * the class references the code depends on, so a component that gets moved, renamed or stripped of its
 * permission without the manifest following turns this test red. The accessibility service, the
 * device-admin receiver and the launcher activity each carry the permission or intent-filter the
 * platform needs to bind them, and the three block screens the service launches must stay declared or
 * blocking silently fails to show. Nothing here reaches into class internals.
 */
@RunWith(AndroidJUnit4::class)
class ManifestContractTest {

    @get:Rule
    val rule = DechainerTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val packageManager = context.packageManager
    private val packageName = context.packageName

    @Test
    fun `the accessibility service is declared with the bind-accessibility-service permission`() {
        val service = packageManager
            .getPackageInfo(packageName, PackageManager.GET_SERVICES)
            .services
            ?.firstOrNull { it.name == DechainerAccessibilityService::class.java.name }
            .shouldNotBeNull()

        service.permission shouldBe "android.permission.BIND_ACCESSIBILITY_SERVICE"
        service.exported.shouldBeFalse()
    }

    @Test
    fun `the accessibility service is discoverable by its intent and carries its config meta-data`() {
        val intent = Intent("android.accessibilityservice.AccessibilityService")
        packageManager.queryIntentServices(intent, 0)
            .any { it.serviceInfo.name == DechainerAccessibilityService::class.java.name }
            .shouldBeTrue()

        val service = packageManager
            .getPackageInfo(packageName, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA)
            .services
            ?.firstOrNull { it.name == DechainerAccessibilityService::class.java.name }
            .shouldNotBeNull()

        val meta = service.metaData.shouldNotBeNull()
        meta.containsKey("android.accessibilityservice").shouldBeTrue()
    }

    @Test
    fun `the device admin receiver is declared exported with the bind-device-admin permission`() {
        val receiver = packageManager
            .getPackageInfo(packageName, PackageManager.GET_RECEIVERS)
            .receivers
            ?.firstOrNull { it.name == DechainerDeviceAdminReceiver::class.java.name }
            .shouldNotBeNull()

        receiver.permission shouldBe "android.permission.BIND_DEVICE_ADMIN"
        receiver.exported.shouldBeTrue()
    }

    @Test
    fun `the device admin receiver responds to the device-admin-enabled action and carries its policy meta-data`() {
        val intent = Intent("android.app.action.DEVICE_ADMIN_ENABLED")
        packageManager.queryBroadcastReceivers(intent, 0)
            .any { it.activityInfo.name == DechainerDeviceAdminReceiver::class.java.name }
            .shouldBeTrue()

        val receiver = packageManager
            .getPackageInfo(packageName, PackageManager.GET_RECEIVERS or PackageManager.GET_META_DATA)
            .receivers
            ?.firstOrNull { it.name == DechainerDeviceAdminReceiver::class.java.name }
            .shouldNotBeNull()

        val meta = receiver.metaData.shouldNotBeNull()
        meta.containsKey("android.app.device_admin").shouldBeTrue()
    }

    @Test
    fun `MainActivity is the launcher activity`() {
        val launch = packageManager.getLaunchIntentForPackage(packageName).shouldNotBeNull()

        launch.component?.className shouldBe MainActivity::class.java.name
    }

    @Test
    fun `the launcher and all block activities are declared`() {
        val declared = packageManager
            .getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            .activities
            ?.map { it.name }
            .orEmpty()

        declared shouldContainAll listOf(
            MainActivity::class.java.name,
            TimeUpActivity::class.java.name,
            ReopeningLimitActivity::class.java.name,
            BlockedWordActivity::class.java.name,
        )
    }

    @Test
    fun `the accessibility settings activity points to a declared activity`() {
        val service = packageManager
            .getPackageInfo(packageName, PackageManager.GET_SERVICES or PackageManager.GET_META_DATA)
            .services
            ?.firstOrNull { it.name == DechainerAccessibilityService::class.java.name }
            .shouldNotBeNull()

        val configResId = service.metaData.shouldNotBeNull().getInt("android.accessibilityservice")
        val settingsActivity = readSettingsActivity(configResId).shouldNotBeNull()

        packageManager.getActivityInfo(ComponentName(packageName, settingsActivity), 0)
    }

    private fun readSettingsActivity(configResId: Int): String? {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        val parser = context.resources.getXml(configResId)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "accessibility-service") {
                return parser.getAttributeValue(androidNamespace, "settingsActivity")
            }
            event = parser.next()
        }
        return null
    }
}
