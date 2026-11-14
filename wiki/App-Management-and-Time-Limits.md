# App Management & Time Limits

The **Apps** tab gives you comprehensive control over every application installed on your device. Whether managing screen time, stopping compulsive doom-scrolling, or locking down risky apps, Déchaîner combines Device Owner APIs with Accessibility monitoring.

---

## App Controls: Hiding, Suspending & Uninstall Protection

Tap on any app in the list to reveal its management dialog:

| Action | API Used | Behavior & Use Case |
|--------|----------|---------------------|
| **Suspend App** | `DevicePolicyManager.setPackagesSuspended` | Greys out the app icon on the home screen and prevents the app from running in the background. If tapped, Android displays *"This action is not allowed by your organization IT"*. Perfect for freezing tempting apps. |
| **Hide App** | `DevicePolicyManager.setApplicationHidden` | Completely hides the app from your launcher and app drawer, making it invisible without uninstalling its data. |
| **Block Uninstallation** | `DevicePolicyManager.setUninstallBlocked` | Prevents the app from being uninstalled from the launcher, settings, or Play Store. Essential for protecting accountability tools, habit trackers, and Déchaîner itself. |

---

## Daily Usage Time Limits

You can set strict daily screen time quotas for individual apps or groups of apps:

1. In the **Apps** tab, tap the **Timer icon** next to an app (or tap the app and select **Time limits**).
2. Set the maximum allowed usage time (e.g., 30 minutes).
3. **Per-Weekday Configuration**: You can customize limits per day (e.g., 20 minutes Monday through Friday, 45 minutes on Saturday and Sunday).
4. **Behavior when time expires**: As soon as the daily limit is exhausted, Déchaîner's Accessibility Service intercepts the app and presents a full-screen block card (`TimeUpActivity`) stating that your daily time has expired.

---

## Allowed Time Windows

Some apps are necessary for study or work, but become addictive late at night:

1. Tap an app and select **Time windows**.
2. Define allowed start and end times (e.g., `09:00 - 18:00`).
3. You can set multiple disjoint windows (e.g., `08:00 - 09:00` and `19:00 - 21:00`).
4. Outside of these configured windows, attempting to launch the app triggers the `OutsideTimeWindowActivity` screen, preventing access.

---

## Minimum Reopening Cooldown

A major cause of digital addiction is **compulsive re-opening**—closing an app like Instagram, only to open it again 30 seconds later on autopilot.

- Configure a **Minimum reopening delay** (e.g., 15 minutes).
- Once you close the app, a hardware countdown begins.
- If you try to reopen the app before the cooldown expires, Déchaîner blocks the app with `ReopeningLimitActivity`, breaking autopilot habits.

---

## App Groups

Instead of configuring each app individually, organize related apps into **App Groups**:

1. Tap the **Group icon** at the top of the Apps tab.
2. Create a new group (e.g., *"Social Media"*, *"Video Feeds"*, *"Gaming"*).
3. Add member apps (e.g., Instagram, TikTok, Reddit, X).
4. Apply a **Shared Daily Time Limit** (e.g., 45 minutes total across all social media apps combined).
5. When the shared quota is spent, all apps in that group are blocked together until midnight.

---

## Automatic Content Suspensions

Déchaîner actively shields your device from adult or risky apps without requiring manual intervention:

### 1. Automatic 18+ App Suspension
- When a new app is installed or opened for the first time, Déchaîner queries the Google Play Store content rating heuristic.
- If an app is rated **18+ with explicit/sexual content** (e.g., Telegram, adult apps), Déchaîner immediately suspends the package.
- If you need a suspended app for legitimate work (such as Telegram or Discord), you can manually unsuspend it in the Apps tab (requires recovery code if protected).

### 2. Automatic Torrent App Blocker
- BitTorrent networks are widely used to download unmonitored explicit videos.
- When **Block Torrents** is toggled ON in the Config tab, Déchaîner inspects package intent filters.
- Any app that registers handlers for `magnet:` links or `.torrent` MIME types (`application/x-bittorrent`) is automatically suspended.

---

## Understanding Suspension Logs

If an app suddenly displays *"This action is not allowed by your organization IT"*, do not panic. Déchaîner maintains an audit log explaining exactly why the app was suspended.

Navigate to **Config > Suspension Logs**:

| Log Reason | What Caused the Suspension | How to Resolve |
|------------|----------------------------|----------------|
| `UNSUPPORTED_BROWSER` | The browser does not support enterprise URL filtering. | Use Chrome, Brave, or Samsung Internet instead. |
| `TORRENT_APP` | The app can download torrent files. | Disable torrent blocking in Config, or remove the torrent app. |
| `RATED_18_PLUS` | Google Play Store flagged the app as 18+ explicit. | Unsuspend manually in the Apps tab if needed for work. |
| `VISUAL_BLOCKING` | The on-device ML model detected repeated NSFW content. | Wait for the configured suspension duration to expire. |
| `IMPULSE_LOCK` | You activated the lock-screen panic button. | Wait for the impulse lock timer to finish. |
| `FOCUS_MODE` | An active Pomodoro study session is running. | Apps will be automatically restored during break periods. |
| `ACCESSIBILITY_DISABLED`| The Accessibility Service was turned off. | Re-enable the Accessibility Service in Android settings. |
| `MANUAL` | Suspended manually by the user in the Apps tab. | Unsuspend manually in the Apps tab. |
