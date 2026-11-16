# Review & Known Limitations

This page documents technical bottlenecks, known architectural constraints, UX friction spots, and open bug investigations identified from community testing and GitHub issues. 

Contributors and developers should use this document as a backlog of areas requiring code review, refactoring, or upstream feature attention.

---

## 1. UX & Configuration Friction Points

### 1.1 Inverted Intuition on the Visual Blocker Sensitivity Slider (Issues #82, #83)
- **Problem**: In **Config > Visual Blocking**, the slider controls the confidence threshold percentage (0% to 100%).
- **Current Behavior**: A *higher* percentage (e.g., 90%) means the machine learning model requires a higher confidence score before triggering, which **decreases** false positives. Conversely, a *lower* percentage (e.g., 20%) triggers aggressively on almost anything.
- **User Confusion**: Users frequently interpret "Sensitivity" as "detection power" (thinking 90% is hyper-sensitive and 10% is relaxed). As a result, users experiencing false alarms dial the slider down to 10% or 1%, which makes the false triggers worse.
- **Recommendation for Review**: Clarify the label in `VisualBlockingScreen.kt` from *"Sensitivity"* to *"Strictness / Confidence Threshold"*, and add explicit helper text: *"Higher values (80%–95%) reduce false alarms; lower values trigger more aggressively."*

### 1.2 Browser Search Suggestions Triggering Passive Keyword Blocks (Issue #81)
- **Problem**: When a user adds a word like `proxy` to Passive Blocking on a browser and types `pro` in the URL bar, the search engine's dropdown suggests `proxy list`.
- **Current Behavior**: The Accessibility Service detects the text `proxy` on screen and immediately terminates the browser.
- **Recommendation for Review**: Consider ignoring text nodes located inside `AutoCompleteTextView` or search engine suggestion list containers, or display an in-app tip advising users to use **Active Blocking** on browsers instead of Passive Scanning.

---

## 2. Bug Investigations & Edge Cases

### 2.1 Manually Unsuspended Apps Being Re-Suspended (Issue #84 - RESOLVED)
- **Symptom**: A user manually unsuspends an essential work app (such as Telegram or Discord) in the Apps tab, but the app gets automatically re-suspended later, forcing the user to retrieve the recovery key again.
- **Resolution**: Implemented persistent manual suspension tracking in `AppRepository` (`manual_suspensions_prefs`). Any app whose suspension is manually toggled (suspended or unsuspended) is governed exclusively by user action: automated evaluators (Play Store rating checks, browser restrictions, torrent checks, and accessibility status reconciliations) respect manual exemptions and will never automatically suspend a manually unsuspended app. Historical unsuspensions are also automatically migrated.

### 2.2 Edge Panel / Floating Window Bypasses (Issue #64)
- **Symptom**: On custom skins with floating windows (such as Samsung Edge Panel, Oppo Smart Sidebar, or Xiaomi Floating Windows), an app blocked by a time window or daily limit can still be launched inside a mini-floating window over the block card.
- **Cause**: Déchaîner displays an overlay activity (`OutsideTimeWindowActivity` / `TimeUpActivity`) when an app comes to the foreground. In multi-window or floating mode, the target app remains resumed underneath or alongside the blocker activity without losing focus.
- **Recommendation for Review**: When an app's time window or daily limit is active, consider calling `DevicePolicyManager.setPackagesSuspended` directly instead of merely relying on foreground activity interception.

### 2.3 OEM Zombie Accessibility Service (Issue #55)
- **Symptom**: On Infinix/Tecno (XOS/HiOS) and some Xiaomi builds, clearing recent apps invokes proprietary task killers (`tranpm manual_oneKeyClean`), which kills the `DechainerAccessibilityService`. The Android settings toggle remains ON, but the system displays *"Not working. Tap for info"*.
- **Recommendation for Review**: 
  - Investigate adding a lightweight sticky foreground service (`START_STICKY`) with a low-priority notification when aggressive OEM power management is detected.
  - Implement a periodic worker or broadcast receiver that tests accessibility responsiveness and auto-rebinds via Shizuku where possible.

---

## 3. Platform & Architectural Limitations

### 3.1 Single-Activity Architecture vs. Screen Blocking (Issue #44)
- **Constraint**: The Activity Blocker can only intercept Android Activities registered in `AndroidManifest.xml`.
- **Limitation**: Modern applications (YouTube, Instagram, WhatsApp) render tabs (Shorts, Reels, Status) as Jetpack Compose views or Android Fragments inside one unified `MainActivity`. They cannot be individually targeted by class name without parsing internal view hierarchies.
- **Alternative**: Advise users to combine Active/Passive keyword blocking with external open-source patches (like Morphe).

### 3.2 Lack of `AllowDeletingBrowserHistory` on Android Chrome (Issue #75)
- **Constraint**: The Chromium team only implements the `AllowDeletingBrowserHistory` enterprise policy on ChromeOS and Desktop Chrome. It is not currently parsed by Chrome on Android.
- **Mitigation**: Users who need history retention must rely on browser time limits, strict DNS logging (e.g. NextDNS query logs), or enterprise browsers like Samsung Internet.

### 3.3 Hardware & OEM Blocks (`onCommand block shell` on Vivo / OriginOS)
- **Constraint**: Devices running recent Vivo/OriginOS/Funtouch firmware intercept the `dpm set-device-owner` command at the proprietary framework level.
- **Mitigation**: Documented clearly in the Installation Guide so users understand this is an OEM firmware barrier, not a Déchaîner software defect.
