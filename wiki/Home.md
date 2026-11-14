# Welcome to the Déchaîner Wiki

**Déchaîner** (pronounced */de.ʃɛ.ne/*, a French verb meaning *to unleash, unchain, let loose*) is an open-source, hard-to-bypass Android barrier engineered to fight pornography and digital addiction.

Unlike ordinary digital wellbeing or app blocker utilities that can be bypassed in seconds through system settings, task killers, or casual uninstallation, Déchaîner combines **Android Device Owner (DPM) privileges**, an active **Accessibility Service**, and a local **on-device Machine Learning model**. It is explicitly designed so that your impulsive, future self cannot simply turn it off.

---

## Core Philosophy

1. **Self-Lockout Against Relapses**: When an urge or impulse strikes, ordinary toggles fail. Déchaîner locks critical system controls and requires a physical 16-character recovery code stored offline on paper to loosen restrictions.
2. **Zero Cloud Telemetry**: Everything runs 100% on-device. Images analyzed by the visual blocker, typed keystrokes, and app usage logs never leave your phone. The only external network call Déchaîner ever makes is checking Google Play Store content ratings for newly installed apps.
3. **Defense-in-Depth (Layered Architecture)**: No single blocker tool can solve digital addiction alone. Déchaîner uses a synchronized, multi-layer approach where each layer covers the blind spots of the others.

---

## The Layered Protection Model

As detailed by the project maintainer, Déchaîner uses a tiered defense strategy:

```
+-------------------------------------------------------------------+
| 1. Network Level: Enforced Family Private DNS                      |
|    (Blocks known adult domains and forces SafeSearch globally)     |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| 2. Browser Level: Managed Policies (URLBlocklist + Incognito Off)  |
|    (Blocks web proxies, archive sites, unblockable specific URLs)  |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| 3. Text Level: Keyword & Content Blocker (Active & Passive)        |
|    (Erases typed trigger words; detects trigger words on screen)   |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| 4. Visual Level: On-Device ML Model (MobileNetV2 NSFW Classifier) |
|    (Detects explicit images & videos inside apps like Reddit)      |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| 5. App & Screen Level: Limits, Cooldowns & Activity Blocker       |
|    (App time windows, daily quotas, reopening delays, sub-screens) |
+-------------------------------------------------------------------+
                                  |
                                  v
+-------------------------------------------------------------------+
| 6. OS Level: Device Owner Restrictions                            |
|    (Prevents VPN config, DNS tampering, factory reset, uninstall) |
+-------------------------------------------------------------------+
```

- **Why DNS alone is not enough**: Users can switch browsers or try web proxies.
- **Why Browser Policies alone are not enough**: Native social media apps (Reddit, Telegram) contain mixed content that browsers do not govern.
- **Why Machine Learning is not used everywhere**: Running an ML classifier across all apps 24/7 wastes battery and produces occasional false positives. Machine learning is targeted specifically at "unfiltered" media-rich apps, while DNS and browser policies safeguard web navigation cleanly.

---

## Wiki Table of Contents

- [**Installation & Setup Guide**](Installation-and-Setup.md): Prerequisites, step-by-step setup with Shizuku, Device Owner provisioning, and granting permissions.
- [**Recovery Code & Safety Architecture**](Recovery-and-Safety.md): The 16-character physical recovery code, 10-minute security sessions, shuffle keyboard, and the 48-hour forced removal fallback.
- [**System Restrictions (Device Owner)**](System-Restrictions.md): System-wide `UserManager` policies (VPN, DNS, factory reset, app installs) and per-app managed restrictions.
- [**Browser & Network Protection**](Browser-and-Network-Protection.md): Enforcing family DNS, applying `URLBlocklist`, disabling Chrome Incognito mode, and handling unsupported browsers.
- [**App Management & Time Limits**](App-Management-and-Time-Limits.md): Hiding, suspending, blocking uninstallation, daily time quotas, time windows, and reopening cooldowns.
- [**Keyword & Content Blocking**](Keyword-and-Content-Blocking.md): Active typing erasure vs. passive screen scanning, use cases, and tuning.
- [**On-Device Visual Blocking**](Visual-Blocking.md): Local TensorFlow Lite classification, category selection, threshold tuning, and suspension escalation.
- [**Activity Blocker**](Activity-Blocker.md): Inspecting and locking specific Android activities (sub-screens) by class name.
- [**Focus Mode & Impulse Lock**](Focus-Mode-and-Impulse-Lock.md): Pomodoro study cycles with app suspension, scheduled focus, lock-screen panic button, and mental challenges (Tetris, Scripture, Math, Words).
- [**Display & Color Modes**](Display-and-Color-Filters.md): Grayscale, night light, extra dim, inversion schedules, and per-app exclusions.
- [**Device Quirks & FAQ**](Device-Quirks-and-FAQ.md): Manufacturer-specific solutions (Samsung, Vivo, Xiaomi/MIUI, Infinix/XOS), dual apps, Secure Folder, and banking app safety.
- [**Review & Known Limitations**](Review-and-Known-Limitations.md): Technical areas flagged for developer review, architectural constraints, and open issues.
