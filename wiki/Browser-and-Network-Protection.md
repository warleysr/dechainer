# Browser & Network Protection

Web browsers are the primary entry point for unmoderated internet content. Déchaîner secures network traffic and web browsing using a dual-action system: **OS-level Family Private DNS** combined with **Enterprise Managed Browser Policies**.

---

## 1. Family Private DNS

Private DNS operates globally across your entire device (Wi-Fi, cellular data, and hotspot). It ensures that every domain resolution request is checked against a filtering blacklist before connection.

### How to Configure
1. Open the **Config** tab and tap **DNS Settings**.
2. Select one of the verified family-safe providers:
   - **Cloudflare Family** (`family.cloudflare-dns.com`): High performance, blocks known adult domains and malware.
   - **AdGuard DNS Family** (`family.adguard-dns.com`): Filters adult websites, enforces SafeSearch on search engines, and blocks advertisements.
   - **CleanBrowsing Adult Filter** (`adult-filter-dns.cleanbrowsing.org`): Strict filtering designed for families, forces SafeSearch on Google, Bing, and DuckDuckGo.
   - **Custom DNS**: Enter your own custom DNS over TLS (DoT) hostname (for example, your personal NextDNS profile: `xxxxxx.dns.nextdns.io`).
3. Tap **Apply**.

### Under the Hood
Déchaîner calls `DevicePolicyManager.setGlobalPrivateDnsModeSpecifiedHost()`. When combined with the system restriction `DISALLOW_CONFIG_PRIVATE_DNS`, the DNS setting is locked. It cannot be reverted in Android's network settings.

---

## 2. Browser Restrictions (`URLBlocklist`)

While Family DNS blocks millions of explicit domains, DNS filtering has limitations:
- It cannot block specific paths on otherwise benign domains (e.g., you cannot block `reddit.com/r/nsfw` via DNS without blocking all of `reddit.com`).
- It cannot stop new web proxies and mirrors created faster than DNS blocklists update.

To solve this, Déchaîner configures browser-level policies.

### Managing Blocked Sites
1. Go to **Config > Browser Restrictions**.
2. Under **Blocked Sites**, tap the **Add (+)** button to create a list (or edit an existing one).
3. Enter site patterns to block, one per line. Examples:
   ```text
   *://*.old.reddit.com/*
   *://*.proxysite.com/*
   *://*.kproxy.com/*
   *://*.archive.org/*
   ```
4. Save the list. Déchaîner pushes these URLs into the `URLBlocklist` managed configuration bundle across all installed browsers that support it.

---

## 3. Disabling Incognito Mode in Google Chrome

By default, Google Chrome allows users to open Incognito tabs. You can completely disable this feature:

1. Open the **Config** tab and select **Browser Restrictions** (or go to **Apps > Chrome**).
2. Tap **Chrome** to view its managed configurations.
3. Locate `IncognitoModeAvailability` and set its value to `1` (Disabled).
4. Chrome will instantly hide the *"New Incognito Tab"* option from its menu and refuse to open private browsing sessions.

> [!NOTE]
> **Browser History Deletion**: Google Chrome on Android currently does not expose an enterprise policy to disallow clearing browsing history (`AllowDeletingBrowserHistory` only exists on Chrome Desktop/ChromeOS). If history clearing is a concern, consider using Samsung Internet Browser, which supports deeper Knox/enterprise policies, or restricting browser usage time windows.

---

## 4. Handling Unsupported & Rogue Browsers

Not all mobile browsers respect Android enterprise policies (`RestrictionsManager`). For example, lightweight privacy browsers or obscure webview apps might ignore `URLBlocklist` and `ForceGoogleSafeSearch`.

To eliminate loopholes:
- Déchaîner scans installed browsers for `URLBlocklist` and `ForceGoogleSafeSearch` support.
- If a browser **does not support** these enterprise policies, Déchaîner marks it as unsupported.
- When `Browser Restrictions` or `Advanced Blocking` is active, newly installed unsupported browsers are **automatically suspended** by Déchaîner with the reason logged as `UNSUPPORTED_BROWSER`.
- You can review suspended browsers in **Config > Suspension Logs**.

---

## 5. DNS Leaks During Rapid Network Switching

A subtle Android behavior was identified in issue [#15](https://github.com/warleysr/dechainer/issues/15): when rapidly toggling between Wi-Fi and Mobile Data, Android OS temporarily falls back to the carrier's default DNS for a few milliseconds while the Private DNS TLS handshake establishes.

### How Déchaîner Prevents This Bypass
By enforcing `ForceGoogleSafeSearch` and `URLBlocklist` directly inside the browser application bundle, browser requests remain blocked even during transient network interface handovers. The browser itself drops the connection before any HTTP/HTTPS socket opens.
