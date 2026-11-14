# Installation & Setup Guide

Déchaîner operates with deep system-level control. To ensure it cannot be easily removed or bypassed, it requires Android **Device Owner** status and an active **Accessibility Service**. 

Follow this guide carefully to provision your device correctly.

---

## Requirements

- **Android 11 (API Level 30) or newer**: Android 11 introduced native on-device Wireless Debugging, which allows tools like [Shizuku](https://shizuku.rikka.app/) to execute administrative ADB shell commands directly on your phone without connecting to a computer.
- **Developer Options enabled**: Needed to activate Wireless Debugging.
- **Wi-Fi connection**: Required during the initial pairing step of Shizuku.

> [!IMPORTANT]
> If your device runs Android 10 or older, you cannot pair Shizuku via Wireless Debugging on the phone alone; you would need a PC with ADB. Déchaîner targets modern Android versions (Android 11+) to guarantee stability and security.

---

## Step 1: Install and Pair Shizuku

[Shizuku](https://shizuku.rikka.app/) acts as an on-device bridge that executes privileged commands on behalf of authorized apps.

1. Download and install Shizuku from the official [GitHub Releases](https://github.com/RikkaApps/Shizuku/releases) or Google Play Store.
2. Enable **Developer Options** on your phone:
   - Go to **System Settings > About Phone**.
   - Tap **Build Number** 7 times until you see the message *"You are now a developer!"*.
3. Connect your phone to a **Wi-Fi network**.
4. Open **Settings > System / Additional Settings > Developer Options**.
5. Enable **Wireless Debugging** (toggle it ON and check *"Always allow on this network"*).
6. Tap on the text **"Wireless Debugging"** to enter its settings screen, then tap **"Pair device with pairing code"**.
7. Keep the pairing code dialog open, pull down your notification shade, and tap **"Enter pairing code"** from Shizuku's notification. Enter the 6-digit code shown on screen.
8. Switch back to the Shizuku app and tap **"Start"**. Shizuku will show *"Shizuku is running"*.

---

## Step 2: Install Déchaîner & Setup Recovery Code

1. Download the latest APK from the [Official Releases](https://github.com/warleysr/dechainer/releases/latest).
2. Install the APK and launch **Déchaîner**.
3. **Recovery Code Wizard**:
   - On first launch, Déchaîner generates a unique **16-character recovery code** (e.g., `ABCD-EFGH-IJKL-MNOP`).
   - **Write this code down by hand on a physical piece of paper.**
   - Do **NOT** store it in note-taking apps, cloud storage, or screenshots. You may end up blocking the very tools needed to retrieve it.
   - The wizard will require you to type the code back from paper to verify you recorded every character correctly.
   - Choose a storage place that creates physical friction (e.g., inside a sealed envelope on a high shelf, at work, or with an accountability partner).

---

## Step 3: Android 13+ "Allow Restricted Settings"

On Android 13 and newer, Android automatically restricts sensitive permissions (such as Accessibility Services and Device Admin) for sideloaded APKs installed outside an app store.

If you see an error saying *"Restricted setting: For your security, this setting is currently unavailable"*:
1. Open your phone's **System Settings > Apps > All Apps > Déchaîner**.
2. Tap the **three-dot menu (⋮)** in the top right corner.
3. Tap **"Allow restricted settings"**.
4. Authenticate using your fingerprint, pattern, or PIN.

---

## Step 4: Become Device Owner

To lock critical system configurations and stop uninstallation, Déchaîner must be registered as the device owner.

### Account Clearance Requirement
Standard Android security prevents any app from becoming Device Owner if active user accounts or secondary profiles exist on the device.
Before granting Device Owner status:
1. Temporarily remove accounts from **Settings > Accounts / Passwords & Accounts** (Google, WhatsApp, Telegram, etc.).
   *(You can log right back in as soon as Device Owner is granted.)*
2. **Disable Dual Apps / App Clones / Second Space**:
   - Feature implementations like Samsung Dual Messenger, Xiaomi Dual Apps, OnePlus Parallel Apps, or Oppo/Realme App Clone create hidden secondary Android users (e.g., `User 10` or `DOBULE_APP`).
   - You must disable all cloned apps and turn off the feature in settings before proceeding.
3. **Samsung Secure Folder / Private Space**:
   - Secure Folder creates an isolated Knox profile that is fundamentally incompatible with third-party Device Owner apps. You must remove or deactivate Secure Folder before setting Déchaîner as Device Owner.

### Provisioning through Déchaîner
1. In Déchaîner, open the **Config** tab.
2. Tap on **Device Owner State** (shows *"Not granted"* in red).
3. If Shizuku is running, Déchaîner will automatically register itself as Device Admin and promote itself to Device Owner via Shizuku.
4. Once successful, the badge in the Config tab turns green and displays **"Granted"**.
5. You may now re-add your Google and other accounts back to your phone.

### Alternative A: Transfer Ownership from Dhizuku (v1.10.0+)
If you already use [Dhizuku](https://github.com/iamr0s/Dhizuku) to manage Device Owner permissions without removing accounts, Déchaîner supports transferring ownership directly from Dhizuku without having to wipe accounts again.

### Alternative B: Manual ADB Provisioning via PC
If you prefer using a computer terminal or if Shizuku fails on your device:
1. Connect your phone to your PC with USB debugging enabled.
2. Open a terminal and run:
   ```bash
   adb shell dpm set-device-owner io.github.warleysr.dechainer/.DechainerDeviceAdminReceiver
   ```
3. If the command succeeds, you will see `Success: Device owner set to package...`.

---

## Step 5: Enable Advanced Blocking (Accessibility Service)

Advanced features—such as Keyword Blocking, Visual ML Blocking, Activity Blocking, App Time Limits, and Scheduled Color Modes—rely on the Accessibility Service.

1. In the **Config** tab, locate **Activity Blocker / Advanced Blocking**.
2. Toggle the switch ON. If prompted by Shizuku, grant permission.
3. You can also verify or enable it manually under **System Settings > Accessibility > Downloaded Apps / Installed Services > Déchaîner**.
4. Turn the switch **ON** and confirm the system prompt.

> [!NOTE]
> **Anti-Tampering Safeguard**: If the Accessibility Service is ever turned off, Déchaîner immediately suspends all protected apps (apps with time limits or keyword/visual restrictions) and blocks app installations globally until the service is re-enabled. This prevents relapsing by killing the accessibility service.

---

## Manufacturer-Specific Pitfalls

### Vivo / iQOO / OriginOS / Funtouch OS
Devices running recent Vivo firmware actively intercept and block `dpm set-device-owner` commands from shell or ADB, returning the error `onCommand block shell`. If your phone displays this, the OEM firmware restricts Device Owner privileges. In this case, Déchaîner can still run in Focus Mode / timer mode, but OS-level Device Owner restrictions will not function.

### Infinix / Tecno / XOS / HiOS
Transsion devices run aggressive background cleaners (`tranpm One-key clean`) that kill Accessibility Services when clearing apps from the Recents screen.
- **Solution**: Open Phone Master, enable **Auto-start** for Déchaîner, and lock Déchaîner in your Recent Apps overview with the padlock icon.
