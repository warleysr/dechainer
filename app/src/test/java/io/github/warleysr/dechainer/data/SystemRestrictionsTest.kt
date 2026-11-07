package io.github.warleysr.dechainer.data

import android.content.Context
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemRestrictionsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `allKeys contains recommendedKeys and otherKeys`() {
        val all = SystemRestrictions.allKeys
        assertTrue(all.containsAll(SystemRestrictions.recommendedKeys))
        assertTrue(all.containsAll(SystemRestrictions.otherKeys))
        assertTrue(all.containsAll(SystemRestrictions.additionalKeys))
    }

    @Test
    fun `restrictionFieldNames contains all boolean restrictions not only DISALLOW and ALLOW`() {
        val names = SystemRestrictions.restrictionFieldNames
        assertTrue(names.containsKey(UserManager.DISALLOW_CONFIG_VPN))
        assertTrue(names.containsKey(UserManager.DISALLOW_FACTORY_RESET))
        assertTrue(names.containsKey(UserManager.ALLOW_PARENT_PROFILE_APP_LINKING))
        assertTrue(names.containsKey(UserManager.ENSURE_VERIFY_APPS))
        assertFalse(names.containsKey("KEY_RESTRICTIONS_PENDING"))

        // Known additional / Knox / OEM restrictions
        assertTrue(names.containsKey("disallow_biometric"))
        assertTrue(names.containsKey("no_camera"))
        assertTrue(names.containsKey("no_wallpaper"))
        assertTrue(names.containsKey("no_add_clone_profile"))
        assertTrue(names.containsKey("no_config_wifi_private"))
        assertTrue(names.containsKey("no_config_wifi_shared"))
        assertTrue(names.containsKey("no_non_market_app_by_knox"))
    }

    @Test
    fun `resourceNameFor resolves all boolean restriction keys and additional keys`() {
        assertNotNull(SystemRestrictions.resourceNameFor(UserManager.DISALLOW_CONFIG_VPN))
        assertNotNull(SystemRestrictions.resourceNameFor(UserManager.ENSURE_VERIFY_APPS))
        assertNotNull(SystemRestrictions.resourceNameFor(UserManager.ALLOW_PARENT_PROFILE_APP_LINKING))
        assertNotNull(SystemRestrictions.resourceNameFor("disallow_biometric"))
        assertNotNull(SystemRestrictions.resourceNameFor("no_non_market_app_by_knox"))
        assertNotNull(SystemRestrictions.resourceNameFor("no_camera"))
    }

    @Test
    fun `resolveStringRes finds string resource for no_camera and all additional keys`() {
        val keysToTest = listOf(
            "no_camera",
            "disallow_camera",
            "no_wallpaper",
            "disallow_wallpaper",
            "disallow_biometric",
            "no_add_clone_profile",
            "no_config_wifi_private",
            "no_config_wifi_shared",
            "no_non_market_app_by_knox"
        )
        for (key in keysToTest) {
            val resId = SystemRestrictions.resolveStringRes(context, key)
            assertNotNull("Failed to resolve string resource for $key", resId)
            assertTrue("Resource ID must be > 0 for $key", resId!! > 0)
        }
    }

    @Test
    fun `normalizeForSearch removes diacritics and matches unaccented queries`() {
        assertEquals("desativar camera", SystemRestrictions.normalizeForSearch("Desativar câmera"))
        assertEquals("camera", SystemRestrictions.normalizeForSearch("Câmera"))
        assertEquals("camera", SystemRestrictions.normalizeForSearch("camera"))

        val label = "Desativar câmera"
        val queryUnaccented = "camera"
        val queryAccented = "câmera"

        assertTrue(SystemRestrictions.normalizeForSearch(label).contains(SystemRestrictions.normalizeForSearch(queryUnaccented)))
        assertTrue(SystemRestrictions.normalizeForSearch(label).contains(SystemRestrictions.normalizeForSearch(queryAccented)))
    }
}
