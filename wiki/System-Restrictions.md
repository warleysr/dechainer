# System Restrictions (Device Owner)

Android includes enterprise-grade management capabilities through the `DevicePolicyManager` and `UserManager` APIs. In a corporate environment, IT administrators use these APIs to secure corporate-owned devices. Déchaîner leverages these same APIs to let you become the administrator of your own device.

---

## How System Restrictions Work

In the **Restrictions** tab, you can inspect and toggle operating system restrictions. 
When applied, restrictions are enforced directly by the Android OS kernel and framework. The system UI greys out or blocks corresponding options with messages like *"This action is not allowed by your organization"* or *"Managed by your administrator"*.

---

## The 4 Recommended Restrictions

When first setting up Déchaîner, four essential restrictions are highlighted under **Recommended configurations**:

### 1. Disallow VPN Configuration (`DISALLOW_CONFIG_VPN`)
- **Use Case**: Prevents the user from installing, configuring, or enabling third-party VPNs.
- **Why It Matters**: VPN services route traffic through encrypted tunnels that bypass your phone's enforced Family Private DNS and local content filters. Blocking VPN configuration closes this bypass route.

### 2. Disallow Private DNS Configuration (`DISALLOW_CONFIG_PRIVATE_DNS`)
- **Use Case**: Locks the **Private DNS** setting in Android's network settings.
- **Why It Matters**: Prevents changing the DNS back to "Automatic" or entering a custom DNS server that does not filter adult domains.

### 3. Disallow Factory Reset (`DISALLOW_FACTORY_RESET`)
- **Use Case**: Removes the option to perform a factory reset from Android's settings menu and blocks system recovery wipes where supported.
- **Why It Matters**: Prevents factory resetting the phone in an impulsive moment to remove Déchaîner.

### 4. Disallow Unknown Sources Globally (`DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY`)
- **Use Case**: Blocks sideloading APK files from outside authorized app stores (e.g. from web browsers, file managers, or messaging apps).
- **Why It Matters**: Prevents downloading unrestricted third-party browsers or adult apps from unofficial websites.

---

## Other System Restrictions

Tapping the **Other restrictions** accordion reveals dozens of additional Android system restrictions. Use the search bar to filter by name:

| Restriction Key | Real-World Impact |
|-----------------|-------------------|
| `DISALLOW_SAFE_BOOT` | Blocks rebooting into Android Safe Mode. Safe Mode disables all user accessibility services, which could otherwise be used as a temporary bypass. |
| `DISALLOW_DEBUGGING_FEATURES` | Disables USB debugging (ADB) and wireless debugging. Prevents issuing shell commands to alter settings. |
| `DISALLOW_MODIFY_ACCOUNTS` | Prevents adding or removing accounts from device settings. |
| `DISALLOW_ADD_USER` / `NO_ADD_CLONE_PROFILE` | Disables Guest mode, secondary users, and app clone profiles. |
| `DISALLOW_UNINSTALL_APPS` | Completely locks the ability to uninstall any application on the device. |
| `DISALLOW_APPS_CONTROL` | Prevents force-stopping apps or clearing their cache and data from system settings. |
| `DISALLOW_CONFIG_BLUETOOTH` / `DISALLOW_CONFIG_WIFI` | Prevents altering Bluetooth or Wi-Fi configurations. |

> [!TIP]
> **Pending Changes & Confirmation**: When toggling restrictions, changes are queued as *pending*. Tap the floating **Apply Restrictions** button at the bottom of the screen to commit them. Tightening restrictions is applied immediately; unchecking or loosening any restriction will prompt for your Recovery Code.

---

## Per-App Restrictions Editor (Managed Configurations)

Many modern Android applications declare custom enterprise configurations using the Android `RestrictionsManager` framework. Déchaîner lets you inspect and edit these policies individually:

1. Open the **Apps** tab.
2. Tap on any app (e.g., **Chrome**, **Brave**, **Smart Switch**).
3. Tap **Managed configurations / App restrictions**.
4. Déchaîner dynamically reads the app's manifest XML and displays its configurable enterprise toggles and parameters.

### Practical Examples:
- **Google Chrome**:
  - `IncognitoModeAvailability`: Set to `1` to completely disable Incognito tabs.
  - `ForceGoogleSafeSearch`: Force SafeSearch across all Google search queries.
  - `URLBlocklist`: Array of blocked URLs and domains.
- **Samsung Smart Switch**:
  - `Allow SmartSwitch Run`: If Samsung displays a policy restricting Smart Switch while Device Owner is active, enabling this configuration allows Smart Switch backups and restores to proceed without stripping Device Owner.
