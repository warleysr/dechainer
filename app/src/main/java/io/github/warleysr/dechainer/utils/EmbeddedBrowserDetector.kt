package io.github.warleysr.dechainer.utils

import android.graphics.Rect
import android.text.InputType
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Detects an unrestricted embedded browser screen (e.g. a crypto wallet's dApp browser or an
 * app's in-app WebView browser) from the live accessibility hierarchy without breaking legitimate
 * WebView screens (OAuth/SSO logins, checkout, terms of service, help pages, hybrid apps).
 *
 * Detection requires BOTH:
 * 1. A large, visible WebView/GeckoView container on screen, AND
 * 2. A native address/URL bar (`TextView` or `EditText`) located **outside** the WebView subtree
 *    that either displays a third-party web host or explicitly declares browser address-bar
 *    semantics (`TYPE_TEXT_VARIATION_URI` or address-bar hint/view ID).
 */
object EmbeddedBrowserDetector {

    private const val MAX_TRAVERSAL_NODES = 300
    private const val MIN_WEBVIEW_WIDTH_RATIO = 0.50f
    private const val MIN_WEBVIEW_HEIGHT_RATIO = 0.30f
    private const val MAX_CHROME_HEIGHT_RATIO = 0.15f
    private const val MAX_CHROME_HEIGHT_PX_FLOOR = 200
    private const val CHROME_EDGE_OVERLAP_RATIO = 0.20f
    private const val MAX_URL_TEXT_LENGTH = 256

    enum class Signal {
        URL_BAR_WITH_HOST,
        EDITABLE_URI_BAR,
        EDITABLE_ADDRESS_HINT_OR_ID
    }

    data class DetectionResult(
        val detectedHost: String?,
        val signal: Signal
    )

    data class NodeSnapshot(
        val className: String,
        val viewId: String?,
        val text: String?,
        val hintText: String?,
        val isEditable: Boolean,
        val inputType: Int,
        val isVisible: Boolean,
        val bounds: Rect,
        val insideWebView: Boolean
    )

    /** System/account packages that should never be flagged by embedded-browser detection. */
    private val EXEMPT_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.vending",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.webview"
    )

    /** Non-editable view IDs typical of email/chat headers rather than browser URL bars. */
    private val IGNORED_METADATA_VIEW_IDS = setOf(
        "subject", "sender", "from", "author", "username", "handle", "message", "body", "email"
    )

    /** Resource ID fragments that strongly indicate an editable browser omnibox/URL bar. */
    private val ADDRESS_BAR_VIEW_ID_PATTERNS = listOf(
        "url_bar", "address_bar", "omnibox", "location_bar", "url_input", "url_edit",
        "browser_url", "web_url", "url_field", "address_input", "address_field"
    )

    /** Hint substrings (EN/PT) specific to browser URL/address bars. */
    private val ADDRESS_BAR_HINT_PATTERNS = listOf(
        "search or enter",
        "search or type",
        "enter url",
        "enter address",
        "enter web address",
        "type a url",
        "type url",
        "website address",
        "pesquisar ou digitar",
        "pesquise ou digite",
        "digite um endereço",
        "digite uma url",
        "digite o endereço",
        "endereço da web",
        "endereço web"
    )

    /** Package segments that are too generic to identify a first-party brand domain. */
    private val GENERIC_PACKAGE_SEGMENTS = setOf(
        "com", "org", "net", "io", "br", "gov", "edu", "app", "apps", "android", "androidx",
        "mobile", "dev", "client", "beta", "debug", "release", "free", "pro", "lite", "plus",
        "digital", "online", "wallet", "frontpage", "messenger", "launcher", "viewer", "manager",
        "helper", "tools", "toolbox", "labs", "editors", "sec", "samsung", "privacy", "smart",
        "main", "core", "home", "shop", "store", "bank", "pay", "official", "global", "world"
    )

    /** Reverse-DNS prefixes used to reject Android/Java package names shown in plain text. */
    private val PACKAGE_PREFIX_LABELS = setOf(
        "com", "org", "net", "io", "br", "app", "android", "androidx", "gov", "edu", "dev"
    )

    /** Common file extensions that must not be treated as bare domains (without `http(s)://`). */
    private val FILE_EXTENSIONS = setOf(
        "pdf", "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "ico",
        "mp4", "mp3", "wav", "ogg", "mkv", "avi", "mov", "flac",
        "zip", "rar", "7z", "tar", "gz", "apk", "aab", "exe", "dmg", "iso",
        "txt", "md", "html", "htm", "css", "js", "ts", "jsx", "tsx", "py", "rb", "go", "cs",
        "json", "xml", "yaml", "yml", "csv", "log", "sql", "db",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "rtf"
    )

    private val DOMAIN_LABEL_REGEX = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val IPV4_REGEX = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")

    fun isExemptPackage(packageName: String): Boolean = packageName in EXEMPT_PACKAGES

    fun detect(packageName: String, root: AccessibilityNodeInfo): DetectionResult? {
        if (isExemptPackage(packageName)) return null

        val rootBounds = Rect()
        root.getBoundsInScreen(rootBounds)

        val snapshots = ArrayList<NodeSnapshot>(64)
        collectSnapshots(root, insideWebView = false, out = snapshots)
        return detect(packageName, rootBounds, snapshots)
    }

    internal fun detect(
        packageName: String,
        rootBounds: Rect,
        nodes: List<NodeSnapshot>
    ): DetectionResult? {
        if (isExemptPackage(packageName)) return null

        val webView = nodes
            .asSequence()
            .filter { it.isVisible && isWebViewClass(it.className) && isLargeWebView(it.bounds, rootBounds) }
            .maxByOrNull { it.bounds.width().toLong() * it.bounds.height().toLong() }
            ?: return null

        val externalNodes = nodes.filter {
            it.isVisible && !it.insideWebView && !isWebViewClass(it.className)
        }

        // 1. Check editable address bars outside the WebView first (highest signal).
        for (node in externalNodes) {
            if (!node.isEditable) continue
            if (!isInBrowserChrome(node.bounds, webView.bounds, rootBounds)) continue

            val host = extractHost(node.text)
            if (host != null) {
                if (isAllowedHostForPackage(packageName, host)) continue
                return DetectionResult(host, Signal.URL_BAR_WITH_HOST)
            }

            if (isUriInputType(node.inputType)) {
                return DetectionResult(null, Signal.EDITABLE_URI_BAR)
            }

            if (hasAddressBarHintOrId(node.hintText, node.viewId)) {
                return DetectionResult(null, Signal.EDITABLE_ADDRESS_HINT_OR_ID)
            }
        }

        // 2. Check non-editable URL/host bars (e.g. TextView in top/bottom toolbar like Keeper's id/host).
        for (node in externalNodes) {
            if (node.isEditable) continue
            if (!isInBrowserChrome(node.bounds, webView.bounds, rootBounds)) continue
            if (isIgnoredMetadataViewId(node.viewId)) continue

            val host = extractHost(node.text) ?: continue
            if (isAllowedHostForPackage(packageName, host)) continue
            return DetectionResult(host, Signal.URL_BAR_WITH_HOST)
        }

        return null
    }

    private fun collectSnapshots(
        node: AccessibilityNodeInfo,
        insideWebView: Boolean,
        out: MutableList<NodeSnapshot>
    ) {
        if (out.size >= MAX_TRAVERSAL_NODES) return

        val className = node.className?.toString().orEmpty()
        val isWebView = isWebViewClass(className)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        out.add(
            NodeSnapshot(
                className = className,
                viewId = node.viewIdResourceName,
                text = node.text?.toString(),
                hintText = node.hintText?.toString(),
                isEditable = node.isEditable,
                inputType = node.inputType,
                isVisible = node.isVisibleToUser,
                bounds = bounds,
                insideWebView = insideWebView
            )
        )

        // Never need to descend into a WebView subtree: webpage text/inputs are content, not
        // native browser chrome.
        if (isWebView || insideWebView) return

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (out.size >= MAX_TRAVERSAL_NODES) break
            val child = node.getChild(i) ?: continue
            try {
                collectSnapshots(child, insideWebView = false, out = out)
            } finally {
                child.recycle()
            }
        }
    }

    internal fun isWebViewClass(className: String): Boolean {
        return className.contains("webview", ignoreCase = true) ||
            className.contains("geckoview", ignoreCase = true)
    }

    private fun isLargeWebView(webViewBounds: Rect, rootBounds: Rect): Boolean {
        if (webViewBounds.width() <= 0 || webViewBounds.height() <= 0) return false
        if (rootBounds.width() <= 0 || rootBounds.height() <= 0) return true
        return webViewBounds.width() >= rootBounds.width() * MIN_WEBVIEW_WIDTH_RATIO &&
            webViewBounds.height() >= rootBounds.height() * MIN_WEBVIEW_HEIGHT_RATIO
    }

    /**
     * Ensures the candidate text/edit node looks like a compact top or bottom browser bar rather
     * than a full-screen body view.
     */
    private fun isInBrowserChrome(nodeBounds: Rect, webViewBounds: Rect, rootBounds: Rect): Boolean {
        if (nodeBounds.isEmpty || webViewBounds.isEmpty) return true

        val maxChromeHeight = maxOf(
            (rootBounds.height() * MAX_CHROME_HEIGHT_RATIO).toInt(),
            MAX_CHROME_HEIGHT_PX_FLOOR
        )
        if (nodeBounds.height() > maxChromeHeight) return false

        val overlapAllowance = (webViewBounds.height() * CHROME_EDGE_OVERLAP_RATIO).toInt()
        val centerY = nodeBounds.centerY()
        val inTopChrome = centerY <= webViewBounds.top + overlapAllowance
        val inBottomChrome = centerY >= webViewBounds.bottom - overlapAllowance
        return inTopChrome || inBottomChrome
    }

    private fun isUriInputType(inputType: Int): Boolean {
        val typeClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return typeClass == InputType.TYPE_CLASS_TEXT &&
            variation == InputType.TYPE_TEXT_VARIATION_URI
    }

    private fun hasAddressBarHintOrId(hintText: String?, viewId: String?): Boolean {
        val entry = viewIdEntryName(viewId)
        if (entry != null && ADDRESS_BAR_VIEW_ID_PATTERNS.any { entry.contains(it) }) {
            return true
        }
        val hint = hintText?.trim()?.lowercase(Locale.ROOT)
        if (!hint.isNullOrEmpty() && ADDRESS_BAR_HINT_PATTERNS.any { hint.contains(it) }) {
            return true
        }
        return false
    }

    private fun isIgnoredMetadataViewId(viewId: String?): Boolean {
        val entry = viewIdEntryName(viewId) ?: return false
        return entry in IGNORED_METADATA_VIEW_IDS
    }

    private fun viewIdEntryName(viewId: String?): String? {
        if (viewId.isNullOrBlank()) return null
        val slash = viewId.lastIndexOf('/')
        val raw = if (slash >= 0) viewId.substring(slash + 1) else viewId
        return raw.lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }

    /**
     * Extracts a normalized web host from a standalone URL/domain string (e.g. `"google.com"`,
     * `"🔒 https://m.youtube.com/watch?v=1"`, `"app.ston.fi"`).
     *
     * Deliberately rejects prose sentences, emails, filenames (`report.pdf`, `notes.md`),
     * numeric versions/prices, and Android package names (`com.example.app`).
     */
    internal fun extractHost(raw: String?): String? {
        if (raw.isNullOrBlank() || raw.length > MAX_URL_TEXT_LENGTH) return null
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) return null

        // Strip leading non-alphanumeric badges/icons (e.g. "🔒 google.com", "• example.org")
        val cleaned = raw.trim().dropWhile { !it.isLetterOrDigit() }.trimEnd('/', '.', ' ')
        if (cleaned.isEmpty()) return null
        if (cleaned.any { it.isWhitespace() }) return null

        val lower = cleaned.lowercase(Locale.ROOT)
        val hasExplicitScheme = lower.startsWith("https://") || lower.startsWith("http://")
        val withoutScheme = when {
            lower.startsWith("https://") -> lower.removePrefix("https://")
            lower.startsWith("http://") -> lower.removePrefix("http://")
            lower.contains("://") -> return null // non-web scheme (ton://, mailto://, etc.)
            else -> lower
        }

        // Cut at path / query / fragment
        val authorityEnd = withoutScheme.indexOfAny(charArrayOf('/', '?', '#'))
        val authority = if (authorityEnd >= 0) withoutScheme.substring(0, authorityEnd) else withoutScheme
        if (authority.isEmpty()) return null

        // Reject emails (user@domain.com) and userinfo credentials
        if ('@' in authority) return null

        // Strip optional :port
        val colonIdx = authority.lastIndexOf(':')
        val host = if (colonIdx >= 0) {
            val port = authority.substring(colonIdx + 1)
            if (port.isEmpty() || !port.all { it.isDigit() }) return null
            authority.substring(0, colonIdx)
        } else {
            authority
        }

        if (host.isEmpty() || host.length > 253) return null

        if (IPV4_REGEX.matches(host)) {
            val validOctets = host.split('.').all { it.toIntOrNull() in 0..255 }
            return if (validOctets && hasExplicitScheme) host else null
        }

        val labels = host.split('.')
        if (labels.size < 2) return null
        if (labels.any { !DOMAIN_LABEL_REGEX.matches(it) }) return null

        val tld = labels.last()
        if (tld.length < 2 || !tld.all { it.isLetter() }) return null

        if (!hasExplicitScheme) {
            // Reject reverse-DNS Android/Java package names like com.example.app
            if (labels.size >= 3 && labels.first() in PACKAGE_PREFIX_LABELS) return null
            if (tld in FILE_EXTENSIONS) return null
        }

        return host
    }

    internal fun isAllowedHostForPackage(packageName: String, host: String): Boolean {
        return isFirstPartyHost(packageName, host)
    }

    internal fun isFirstPartyHost(packageName: String, host: String): Boolean {
        val labels = host.lowercase(Locale.ROOT).split('.')
        if (labels.size < 2) return false

        val tokens = extractPackageBrandTokens(packageName)
        return labels.dropLast(1).any { label ->
            label.length >= 4 && tokens.any { token ->
                token == label ||
                    (token.length >= 5 && label.length >= 5 && (token.contains(label) || label.contains(token)))
            }
        }
    }

    private fun extractPackageBrandTokens(packageName: String): Set<String> {
        val result = mutableSetOf<String>()
        val dotParts = packageName.lowercase(Locale.ROOT).split('.')
        for (part in dotParts) {
            val joined = part.replace("_", "").replace("-", "")
            if (joined.length >= 4 && joined !in GENERIC_PACKAGE_SEGMENTS) {
                result.add(joined)
            }
            for (sub in part.split('_', '-')) {
                if (sub.length >= 4 && sub !in GENERIC_PACKAGE_SEGMENTS) {
                    result.add(sub)
                }
            }
        }
        return result
    }
}
