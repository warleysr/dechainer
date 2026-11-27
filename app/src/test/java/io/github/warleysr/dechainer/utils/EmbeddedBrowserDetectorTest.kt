package io.github.warleysr.dechainer.utils

import android.graphics.Rect
import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedBrowserDetectorTest {

    private val screenBounds = Rect(0, 0, 1080, 2400)
    private val headerBounds = Rect(120, 100, 960, 180)
    private val webViewBounds = Rect(0, 220, 1080, 2300)
    private val bottomBarBounds = Rect(40, 2260, 1040, 2380)

    private fun webViewNode(
        className: String = "android.webkit.WebView",
        bounds: Rect = webViewBounds,
        visible: Boolean = true
    ) = EmbeddedBrowserDetector.NodeSnapshot(
        className = className,
        viewId = null,
        text = null,
        hintText = null,
        isEditable = false,
        inputType = InputType.TYPE_NULL,
        isVisible = visible,
        bounds = bounds,
        insideWebView = false
    )

    private fun textNode(
        text: String?,
        viewId: String? = null,
        bounds: Rect = headerBounds,
        insideWebView: Boolean = false
    ) = EmbeddedBrowserDetector.NodeSnapshot(
        className = "androidx.appcompat.widget.AppCompatTextView",
        viewId = viewId,
        text = text,
        hintText = null,
        isEditable = false,
        inputType = InputType.TYPE_NULL,
        isVisible = true,
        bounds = bounds,
        insideWebView = insideWebView
    )

    private fun editNode(
        text: String? = null,
        hintText: String? = null,
        viewId: String? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        bounds: Rect = headerBounds,
        insideWebView: Boolean = false
    ) = EmbeddedBrowserDetector.NodeSnapshot(
        className = "androidx.appcompat.widget.AppCompatEditText",
        viewId = viewId,
        text = text,
        hintText = hintText,
        isEditable = true,
        inputType = inputType,
        isVisible = true,
        bounds = bounds,
        insideWebView = insideWebView
    )

    // --- True positives (must block embedded browser) ---

    @Test
    fun `detects Keeper DAppScreen with TonConnectWebView and host TextView`() {
        val nodes = listOf(
            textNode(text = "Google", viewId = "com.ton_keeper:id/title", bounds = Rect(120, 80, 960, 130)),
            textNode(text = "google.com", viewId = "com.ton_keeper:id/host", bounds = Rect(120, 130, 960, 175)),
            webViewNode(className = "com.tonapps.tonkeeper.ui.component.TonConnectWebView")
        )

        val result = EmbeddedBrowserDetector.detect("com.ton_keeper", screenBounds, nodes)
        assertNotNull(result)
        assertEquals("google.com", result?.detectedHost)
        assertEquals(EmbeddedBrowserDetector.Signal.URL_BAR_WITH_HOST, result?.signal)
    }

    @Test
    fun `detects Keeper DAppScreen with Web3 ton domain`() {
        val nodes = listOf(
            textNode(text = "foundation.ton", viewId = "com.ton_keeper:id/host"),
            webViewNode(className = "com.tonapps.tonkeeper.ui.component.TonConnectWebView")
        )

        val result = EmbeddedBrowserDetector.detect("com.ton_keeper", screenBounds, nodes)
        assertNotNull(result)
        assertEquals("foundation.ton", result?.detectedHost)
    }

    @Test
    fun `detects in-app browser with lock icon and https url in TextView`() {
        val nodes = listOf(
            textNode(text = "🔒 https://m.youtube.com/watch?v=123", viewId = "org.telegram.messenger:id/url"),
            webViewNode()
        )

        val result = EmbeddedBrowserDetector.detect("org.telegram.messenger", screenBounds, nodes)
        assertNotNull(result)
        assertEquals("m.youtube.com", result?.detectedHost)
    }

    @Test
    fun `detects embedded browser with editable URI inputType alongside WebView`() {
        val nodes = listOf(
            editNode(
                text = "",
                hintText = "Search",
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
                bounds = bottomBarBounds
            ),
            webViewNode(bounds = Rect(0, 100, 1080, 2240))
        )

        val result = EmbeddedBrowserDetector.detect("io.metamask", screenBounds, nodes)
        assertNotNull(result)
        assertEquals(EmbeddedBrowserDetector.Signal.EDITABLE_URI_BAR, result?.signal)
    }

    @Test
    fun `detects embedded browser with address-bar hint alongside WebView`() {
        val nodes = listOf(
            editNode(text = "", hintText = "Search or enter address"),
            webViewNode()
        )

        val result = EmbeddedBrowserDetector.detect("com.wallet.crypto.trustapp", screenBounds, nodes)
        assertNotNull(result)
        assertEquals(EmbeddedBrowserDetector.Signal.EDITABLE_ADDRESS_HINT_OR_ID, result?.signal)
    }

    @Test
    fun `detects GeckoView embedded browser with URL bar`() {
        val nodes = listOf(
            editNode(text = "duckduckgo.com", viewId = "com.example.stealth:id/url_bar"),
            webViewNode(className = "org.mozilla.geckoview.GeckoView")
        )

        val result = EmbeddedBrowserDetector.detect("com.example.stealth", screenBounds, nodes)
        assertNotNull(result)
        assertEquals("duckduckgo.com", result?.detectedHost)
    }

    // --- True negatives (must NOT break legitimate apps) ---

    @Test
    fun `does not trigger on Keeper search screen without WebView`() {
        val nodes = listOf(
            editNode(text = "google.com", hintText = "Search or enter address", viewId = "com.ton_keeper:id/search_input")
        )

        assertNull(EmbeddedBrowserDetector.detect("com.ton_keeper", screenBounds, nodes))
    }

    @Test
    fun `does not trigger on banking WebView with plain title and no URL bar`() {
        val nodes = listOf(
            textNode(text = "Termos de uso"),
            webViewNode()
        )

        assertNull(EmbeddedBrowserDetector.detect("com.itau", screenBounds, nodes))
    }

    @Test
    fun `does not trigger on first-party WebView matching app brand`() {
        val itauNodes = listOf(
            textNode(text = "faq.itau.com.br"),
            webViewNode()
        )
        assertNull(EmbeddedBrowserDetector.detect("com.itau", screenBounds, itauNodes))

        val keeperOwnNodes = listOf(
            textNode(text = "tonkeeper.com", viewId = "com.ton_keeper:id/host"),
            webViewNode(className = "com.tonapps.tonkeeper.ui.component.TonConnectWebView")
        )
        assertNull(EmbeddedBrowserDetector.detect("com.ton_keeper", screenBounds, keeperOwnNodes))
    }

    @Test
    fun `does not trigger on email client showing sender email or subject above WebView`() {
        val nodes = listOf(
            textNode(text = "john@example.com", viewId = "com.google.android.gm:id/sender"),
            textNode(text = "example.com", viewId = "com.google.android.gm:id/subject"),
            webViewNode()
        )

        assertNull(EmbeddedBrowserDetector.detect("com.google.android.gm", screenBounds, nodes))
    }

    @Test
    fun `does not trigger on document viewers showing filenames above WebView`() {
        for (fileName in listOf("notes.md", "invoice.pdf", "photo.png", "backup.zip", "v26.11.4")) {
            val nodes = listOf(textNode(text = fileName), webViewNode())
            assertNull("Should ignore $fileName", EmbeddedBrowserDetector.detect("com.reader.app", screenBounds, nodes))
        }
    }

    @Test
    fun `does not trigger on prose sentence mentioning a domain or Android package name`() {
        val proseNodes = listOf(
            textNode(text = "Visit google.com for support"),
            webViewNode()
        )
        assertNull(EmbeddedBrowserDetector.detect("com.some.app", screenBounds, proseNodes))

        val pkgNameNodes = listOf(
            textNode(text = "com.example.app"),
            webViewNode()
        )
        assertNull(EmbeddedBrowserDetector.detect("com.some.app", screenBounds, pkgNameNodes))
    }

    @Test
    fun `does not trigger on small banner WebView`() {
        val nodes = listOf(
            textNode(text = "google.com"),
            webViewNode(bounds = Rect(0, 2200, 1080, 2320))
        )

        assertNull(EmbeddedBrowserDetector.detect("com.some.app", screenBounds, nodes))
    }

    @Test
    fun `does not trigger when URL text is inside WebView content`() {
        val nodes = listOf(
            webViewNode(),
            textNode(text = "google.com", insideWebView = true)
        )

        assertNull(EmbeddedBrowserDetector.detect("com.hybrid.app", screenBounds, nodes))
    }

    @Test
    fun `does not trigger on exempt system packages`() {
        val nodes = listOf(
            textNode(text = "example.com"),
            webViewNode()
        )

        assertNull(EmbeddedBrowserDetector.detect("com.google.android.gms", screenBounds, nodes))
        assertNull(EmbeddedBrowserDetector.detect("com.android.settings", screenBounds, nodes))
    }
}
