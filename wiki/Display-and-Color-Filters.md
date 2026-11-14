# Display & Color Modes

Modern smartphone apps and video feeds are psychologically engineered with bright, high-saturation colors to trigger dopamine spikes. 

Déchaîner’s **Color Modes** remove this visual stimulation on a schedule, turning addictive feeds into dull, utilitarian tools.

Navigate to **Config > Color Filter Settings** to configure screen modes.

---

## Available Display Modes

You can activate any combination of the following four display filters:

| Mode | What It Does | Psychological / Functional Benefit |
|------|--------------|-----------------------------------|
| **Grayscale / Monochrome** | Removes all color, converting the display to black, white, and shades of grey. | Destroys dopamine cues in social feeds, video thumbnails, and notification badges. Makes doom-scrolling boring within minutes. |
| **Night Light** | Filters out harsh blue light, shifting the display to warm amber tones (with adjustable intensity). | Lowers evening stimulation, preserves melatonin production, and prepares your brain for sleep. |
| **Extra Dim** *(Android 12+)* | Reduces screen luminance below the device's hardware slider minimum. | Ideal for late-night reading without blinding glare. |
| **Color Inversion** | Inverts pixel color values. | High-contrast reading mode that disorients standard media consumption. |

---

## 1. Scheduled Time Windows & Midnight Spanning

- Set scheduled time blocks for color modes (e.g., enable Grayscale every evening between `21:00` and `07:00`).
- **Midnight-Spanning Support**: Time windows that cross midnight (such as `22:00` to `06:00`) are automatically recognized and enforced without needing two separate rules.
- **Multiple Windows**: You can set daytime focus blocks (e.g., `09:00 - 12:00`) and nighttime blocks independently.

---

## 2. Selective App Coverage & Whitelists (Issue #54)

In issue [#54](https://github.com/warleysr/dechainer/issues/54), users pointed out that system-wide grayscale makes essential utilities—like the **Camera**, **Photo Gallery**, or **Google Maps**—difficult to use.

Déchaîner solves this with **Selective Coverage**:

- **System-wide with Exclusions (Whitelist)**: Grayscale applies everywhere by default, but you can whitelist specific apps (e.g. Camera, Maps, Photos). When you open a whitelisted app, Déchaîner temporarily restores full color; the moment you leave the app, the screen immediately snaps back to grayscale.
- **Specific Apps Only**: You can invert the rule so that Grayscale *only* applies inside specific distracting apps (like Instagram, TikTok, or Reddit), leaving the rest of the OS in natural color.

---

## 3. Anti-Tampering & Quick Settings Lock

If you attempt to toggle Grayscale or Night Light off from your Android Quick Settings tile during an active scheduled window:
1. Déchaîner intercepts the change in real time via its Accessibility Service and `WRITE_SECURE_SETTINGS` permission.
2. The color filter is immediately reapplied.
3. Loosening a scheduled window, removing an active mode, or decreasing filter intensity requires authenticating with your **Recovery Code**.

---

## 4. Hardware Compatibility & Permissions

- **Permissions**: Requires the Accessibility Service and the `WRITE_SECURE_SETTINGS` permission. Both are granted automatically via Shizuku during Device Owner setup.
- **Samsung Night Light Fallback**: On certain Samsung devices (such as budget Galaxy A-series models), forcing Android's native platform night light setting can cause display glitches or blackouts. Déchaîner automatically detects this and renders a smooth amber overlay via the Accessibility Service instead, ensuring safe operation across all hardware.
