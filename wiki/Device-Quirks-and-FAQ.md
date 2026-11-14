# Device Quirks & FAQ

Because Déchaîner utilizes advanced Android enterprise APIs (`DevicePolicyManager`) and deep system hooks, behavior can vary across custom Android manufacturer skins (One UI, MIUI/HyperOS, OriginOS, ColorOS, XOS).

Here is a troubleshooting reference based on real issues encountered and solved by the community.

---

## Frequently Asked Questions (FAQ)

### Does Déchaîner send my photos, keystrokes, or browsing data to a server?
**No. Absolutely not.**
Déchaîner is 100% open-source under the Apache 2.0 license. All features—including the Machine Learning visual classifier, keyword filtering, and usage tracking—execute completely offline on your device. The only external network request Déchaîner ever makes is checking Google Play Store content ratings when a new app is installed.

### Will my banking or financial apps still work?
**Yes.** 
Déchaîner does not modify app certificates or inject proxies into encrypted HTTPS banking traffic. Trusted family DNS providers (such as Cloudflare `family.cloudflare-dns.com` or AdGuard `family.adguard-dns.com`) simply resolve domain names over TLS (DoT). Banking apps communicate with their legitimate servers normally.

### Why does an antivirus scanner (VirusTotal / Play Protect) flag Déchaîner?
Some antivirus engines flag Déchaîner as a heuristic false positive (see Issue [#34](https://github.com/warleysr/dechainer/issues/34)). 
This happens because Déchaîner combines **Device Owner status** and an **Accessibility Service**—a powerful pairing that allows locking system settings. Antivirus heuristics flag this combination because malware has historically abused these permissions to lock phones. Since Déchaîner is open-source, you can inspect the code or build the APK yourself in Android Studio.

### Can I uninstall Déchaîner without my recovery code?
Not immediately. If you lost your paper recovery code, use the **48-Hour Forced Removal** option in the **Config** tab. Once the 48-hour hardware delay expires, you can confirm and remove Device Owner privileges. Outside of the recovery code and the 48-hour delay, there are no backdoors.

---

## Manufacturer-Specific Quirks & Solutions

### 1. Samsung (One UI)

- **Secure Folder & Work Profiles**:
  - *Symptom*: Setup fails with *"Not allowed to set the device owner because there are already some accounts/users"*.
  - *Cause*: Samsung Secure Folder creates an isolated, encrypted secondary profile (`User 150` on Knox). Android enterprise architecture prohibits third-party Device Owner apps from coexisting with Secure Folder.
  - *Solution*: You must back up your Secure Folder files and deactivate Secure Folder before provisioning Déchaîner as Device Owner.
- **Samsung Smart Switch**:
  - *Symptom*: Smart Switch reports *"Security policy restricts use of Smart Switch"* when trying to restore backups (Issue [#10](https://github.com/warleysr/dechainer/issues/10)).
  - *Solution*: In Déchaîner, open the **Apps** tab, enable **Show system apps**, locate **Smart Switch**, tap **Managed configurations**, and toggle **"Allow SmartSwitch Run"**.

### 2. Vivo / iQOO (Funtouch OS / OriginOS)

- **The `onCommand block shell` Error**:
  - *Symptom*: When tapping *"Grant Privileges"* or running ADB, the terminal returns `onCommand block shell` (Issues [#43](https://github.com/warleysr/dechainer/issues/43), [#68](https://github.com/warleysr/dechainer/issues/68), [#74](https://github.com/warleysr/dechainer/issues/74)).
  - *Cause*: Vivo’s proprietary security firmware actively blocks the shell command `dpm set-device-owner` from running over ADB or Shizuku.
  - *Solution*: On recent Vivo devices with this firmware restriction, Device Owner cannot be provisioned via shell. You can still use Déchaîner for Focus Mode, timers, and accessibility blocking, but deep OS restrictions cannot be enabled.
- **Private DNS Not Visually Greyed Out**:
  - On some Vivo builds, even when the DNS restriction is active, the system settings toggle may not appear greyed out visually. However, attempting to modify or save a different DNS server is blocked by the OS.

### 3. Xiaomi / Redmi / POCO (MIUI / HyperOS)

- **Dual Apps & Second Space**:
  - *Symptom*: Setup insists secondary users exist even after removing all Google accounts.
  - *Cause*: MIUI creates a hidden `User 10` whenever Dual Apps is turned on.
  - *Solution*: Go to **Settings > Apps > Dual Apps**, tap the gear icon, and select **"Delete Dual Apps account"**. Then reboot.
- **Shizuku Pairing on MIUI**:
  - Ensure **"USB debugging (Security settings)"** is enabled in Developer Options (requires signing into a Mi Account with a SIM card inserted).

### 4. Infinix / Tecno (XOS / HiOS)

- **Accessibility Service Shows "Not working. Tap for info"**:
  - *Symptom*: The Accessibility Service stops running after clearing recent apps (Issue [#55](https://github.com/warleysr/dechainer/issues/55)).
  - *Cause*: Transsion’s power manager (`tranpm One-key clean`) aggressively terminates accessibility services when the app is removed from Recents.
  - *Solution*:
    1. Open **Phone Master > App launch / Auto-start management**, and enable **Auto-start** for Déchaîner.
    2. Open your Recent Apps screen and tap the **Padlock icon** on Déchaîner's card to lock it into memory.

### 5. OnePlus / Oppo / Realme (OxygenOS / ColorOS)

- **App Clone / Private Safe**:
  - Disable any cloned apps in **Settings > Special Features / Apps > App Cloner**.
  - In Developer Options, enable **"Disable permission monitoring"** if prompted during Shizuku authorization.
