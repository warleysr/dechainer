# Déchaîner
<img src="https://i.imgur.com/oEDcaTf.png" width="200px" alt="Déchaîner" align="right">

> **Caution:** This software implements deep system-level modifications. It is designed to be difficult to bypass. Proceed only if you fully understand the implications of Device Owner privileges.


## Overview
**Déchaîner** (pronounced [/de.ʃɛ.ne/](https://en.wiktionary.org/wiki/d%C3%A9cha%C3%AEner)) is a French verb that means to unleash, unchain, or let loose. It is an Android app designed to function as a hard-to-bypass barrier against pornography and digital addiction, combining Device Owner privileges, an Accessibility Service, and an on-device machine learning model.


## How It Works
Déchaîner runs with **Device Owner** privileges, which let it enforce restrictions at the OS level and prevent its own removal without a recovery code. Most of the active blocking (word detection, visual content scanning, activity interception, time limits, torrent detection) is driven by an **Accessibility Service**, which is enabled separately from inside the app and is itself protected against being turned off.


## Features

### System Restrictions (Device Owner)
- Every Android system restriction (`UserManager` policy) can be toggled on. Three are pre-selected as recommended: block VPN configuration, block private DNS configuration, and block factory reset. Dozens of others are available in a searchable list (blocking app installs/uninstalls, safe boot, USB/ADB debugging, adding accounts, and more, depending on Android version).
- Per-app restriction editor: any restriction an individual app declares (via `RestrictionsManager`) can be inspected and applied to that app specifically.

### Browser and Network
- Forces a family-safe private DNS provider (Cloudflare Family, AdGuard DNS Family, CleanBrowsing, or a custom host).
- Applies `URLBlocklist` and `ForceGoogleSafeSearch` policies to browsers that support them.
- Any newly installed browser that doesn't support these policies is suspended outright instead of being left unrestricted.

### App Management
- Hide/block or instantly suspend individual apps.
- Block uninstallation on a per-app basis.
- Set a daily usage time limit per app, with a lock screen once the limit is reached.
- Set a minimum cooldown before an app can be reopened.
- Automatically suspends apps rated 18+ or flagged with explicit content, checked against the Play Store's content rating page when an app is installed or first opened.
- Automatically blocks torrent apps (detected by their ability to handle magnet links or `.torrent` files).

### Word Blocking
- Active blocking: erases a forbidden word as it's typed into a selected app and shows a block screen.
- Passive blocking: scans on-screen text (not just typed input) per app, useful for search results and feeds, and closes the app when a configured word appears anywhere on screen.

### Visual Blocking (on-device machine learning)
- A local TensorFlow Lite model (MobileNetV2) classifies on-screen images and video into drawings, hentai, neutral, porn, and sexy — no data leaves the device.
- The Accessibility Service scans the screen of selected apps for likely media regions, captures them, and runs the classifier; the app is closed when the combined score for the selected categories crosses a configurable threshold.
- Optional escalation: after a set number of blocks within a time window, the app is suspended for a configurable duration instead of just being closed.

### Activity Blocker
- Blocks specific Android screens (activities) by class name, with a log of recently accessed activities to help identify which ones to block.

### Impulse Lock (panic button)
- A panic button on the lock screen, reachable even before authenticating: starts a 15-minute to 6-hour lock on Déchaîner itself, optionally also suspending a user-chosen list of apps for the same duration.
- The timer resists system clock changes and survives the app or its service being restarted.
- Opening Déchaîner normally requires biometric or device authentication, plus an optional extra challenge before entry: a 5-problem arithmetic quiz, or typing 32 words correctly in a row.

### Other Safeguards
- Optional shuffled keypad, so the recovery code can't be memorized by watching finger position.
- Any configuration change requires the recovery code, which unlocks a 10-minute session so it isn't asked for on every single action.


## Installation and Configuration
The elevation to Device Owner status requires a bridge between user-space and system-space. Follow these steps precisely:

1.  **Environment Setup**: Install the [Shizuku](https://shizuku.rikka.app/) application. This is required to execute the necessary ADB commands.
2.  **Developer Authorization**: Enable **Wireless Debugging** in your Android Developer Options and pair it with Shizuku.
3.  **Application Pairing**: Open Déchaîner and grant it permission to access the Shizuku service.
4.  **Privilege Elevation**: Navigate to the Settings tab in Déchaîner and follow the prompts to register the application as the **Device Owner**. This will execute the required `dpm set-device-owner` command via the Shizuku bridge.
5.  **Accessibility Service**: Still in the Settings tab, enable the Accessibility Service (also applied through Shizuku). This is required for word blocking, visual blocking, activity blocking, torrent blocking, and usage time limits — app installation itself is blocked while this service is off, to prevent installing an unrestricted browser in the meantime.


## Recovery and Safety Protocol
Upon configuration, Déchaîner generates a unique **16-character recovery code**. This key is the only ordinary way to disable restrictions or uninstall the application without a complete device wipe (if a wipe is even permitted by your active settings).

### Mandatory Safety Steps:
*   **Physical Record**: You must manually write this key on a physical piece of paper.
*   **Secure Storage**: Store the paper in a physical location that is difficult to access (e.g. a high shelf or a separate building).
*   **Digital Prohibition**: Do **not** save this key in digital notes, emails, or cloud storage. You may inadvertently block access to the very tools needed to retrieve it.

### If the Code Is Lost
The Settings tab offers a **forced removal** option as a deliberate last resort: it starts a 48-hour timer, and once it expires, Device Owner privileges can be removed without the recovery code. The delay exists so this can't be used as a quick bypass — it can also be cancelled at any time before it completes.


## Critical Security Advisory
**Déchaîner is designed to be uncompromising.**

The activation of full system restrictions combined with the loss of your Recovery Code may result in a **permanent inability** to modify system parameters or restore the device to its original state before the 48-hour forced removal timer completes.

*   **Self-Lockout Risk**: This is an intentional feature designed to stop "your future self" from relapsing.
*   **No Other Backdoors**: Besides the recovery code and the 48-hour forced removal timer, there is no alternative recovery method.

---

**Disclaimer**: This software is provided "as is" without warranty of any kind. The developers are not liable for any data loss, system instability, or permanent device lockouts resulting from the use of Device Owner privileges.
