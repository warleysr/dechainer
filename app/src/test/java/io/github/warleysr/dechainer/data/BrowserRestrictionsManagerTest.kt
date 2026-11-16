package io.github.warleysr.dechainer.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BrowserRestrictionsManagerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var manager: BrowserRestrictionsManager

    @Before
    fun setUp() {
        manager = BrowserRestrictionsManager(context)
    }

    @Test
    fun `browser with CATEGORY_APP_BROWSER is recognized as browser`() {
        val shadowPm = shadowOf(context.packageManager)
        val pkg = "com.example.browser.explicit"

        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_APP_BROWSER)
        }
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.MainActivity"
            }
            this.filter = filter
        }

        shadowPm.addResolveInfoForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            resolveInfo
        )

        assertTrue(manager.isBrowser(pkg))
    }

    @Test
    fun `genuine browser handling both generic HTTP and HTTPS is recognized as browser`() {
        val shadowPm = shadowOf(context.packageManager)
        val pkg = "com.example.browser.web"

        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.BrowserActivity"
            }
            filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
            }
        }

        val httpIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://www.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val httpsIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        shadowPm.addResolveInfoForIntent(httpIntent, resolveInfo)
        shadowPm.addResolveInfoForIntent(httpsIntent, resolveInfo)

        assertTrue(manager.isBrowser(pkg))
    }

    @Test
    fun `app handling only HTTPS without authority like Cronometer rationale is NOT recognized as browser`() {
        val shadowPm = shadowOf(context.packageManager)
        val pkg = "com.cronometer.android.gold"

        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.PermissionsRationaleActivity"
            }
            filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
                // Notice: no authority/host specified, reproducing Cronometer's manifest bug
            }
        }

        val httpsIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        shadowPm.addResolveInfoForIntent(httpsIntent, resolveInfo)

        // It only handles https, NOT http
        assertFalse(manager.isBrowser(pkg))
    }

    @Test
    fun `app handling deep links with specific domains is NOT recognized as browser`() {
        val shadowPm = shadowOf(context.packageManager)
        val pkg = "com.itau.app"

        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.DeepLinkActivity"
            }
            filter = IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("http")
                addDataScheme("https")
                addDataAuthority("itau.com.br", null)
            }
        }

        val httpIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://www.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        val httpsIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        shadowPm.addResolveInfoForIntent(httpIntent, resolveInfo)
        shadowPm.addResolveInfoForIntent(httpsIntent, resolveInfo)

        assertFalse(manager.isBrowser(pkg))
    }
}
