# Activity Blocker

The **Activity Blocker** provides surgical screen-level protection. Instead of blocking an entire app, it lets you block specific screens (Android Activities) inside an app while leaving the rest of the application fully accessible.

Navigate to **Config > Blocked Activities** to manage activity rules.

---

## What is an Android Activity?

In the Android framework, an **Activity** is a single, focused window that a user can interact with (e.g., a login screen, a preferences screen, or a document viewer).

### The Primary Use Case
Consider an application like **Reddit**:
- Reddit contains valuable communities for programming, news, hobbies, and learning.
- However, Reddit's settings menu contains a toggle: *"Show NSFW / adult content (I am over 18)"*.
- If you use Reddit's internal toggle to disable NSFW content, an impulsive urge might tempt you to open Settings and turn it back on.
- **The Solution**: Use the Activity Blocker to block Reddit's `PreferenceActivity` screen. You can browse your favorite hobby subreddits normally, but the moment you try to enter the Settings page, Déchaîner intercepts the screen and kicks you out!

---

## Step-by-Step Configuration

Déchaîner makes discovering and blocking activities easy:

1. Ensure **Advanced Blocking** is enabled in the **Config** tab.
2. Open the app containing the screen you want to restrict (e.g., Reddit) and navigate directly into the target screen (e.g., Settings).
3. Switch back to **Déchaîner > Config > Blocked Activities**.
4. Scroll down to the **Accessed Activities** section.
5. Déchaîner logs all recently opened activities, organized by app icon, package name, and exact class name:
   ```text
   com.reddit.frontpage / com.reddit.frontpage.ui.preferences.PreferencesActivity
   ```
6. Tap the class name to copy it to your clipboard.
7. Tap the **Add (+)** button at the top of the screen, paste the class name, and tap **Confirm**.
8. **Test**: Return to the target app and attempt to open that screen. Déchaîner will instantly intercept and close it.

---

## Technical Distinction: Activities vs. Fragments (Issue #44)

In issue [#44](https://github.com/warleysr/dechainer/issues/44), users asked if the Activity Blocker could block specific tabs like **YouTube Shorts** or **Instagram Reels**.

Understanding this limitation is essential:

```
[ App Process: YouTube ]
  └── MainActivity (The sole Android Activity)
        ├── Tab 1: Home (Compose / Fragment)
        ├── Tab 2: Shorts (Compose / Fragment)  <-- NOT an Activity!
        └── Tab 3: Subscriptions (Fragment)
```

- **Single-Activity Architecture**: Modern Android apps frequently use one parent `MainActivity` and render sub-screens (like Shorts, Reels, or WhatsApp Status) as internal **Fragments** or **Jetpack Compose Composables**.
- Because Shorts or Reels do not run in their own Android Activity, adding `com.google.android.youtube.MainActivity` to the Activity Blocker would block the entire YouTube app.
- **Workarounds for Single-Activity Tabs**:
  1. **Passive Word Blocker**: Block unique words that only appear on that tab (e.g., blocking the word `"Remix"` inside YouTube).
  2. **Dedicated App Patches**: Use open-source tools like [Morphe](https://github.com/morpheapp) to strip unwanted UI tabs directly from the APK.
