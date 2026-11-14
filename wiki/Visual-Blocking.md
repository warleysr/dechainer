# On-Device Visual Blocking (Machine Learning)

Text and DNS filtering are effective, but cannot filter visual media inside mixed-content applications (such as Reddit or Telegram) where explicit photos or video clips appear alongside normal discussions.

Déchaîner addresses this with an **on-device Machine Learning computer vision classifier**.

---

## How It Works

1. **Accessibility View Inspection**: When you browse an app configured for visual monitoring, Déchaîner’s Accessibility Service identifies visible image, video, and media canvas views.
2. **On-Device Inference**: View regions are captured and analyzed locally using a compressed **TensorFlow Lite (MobileNetV2)** neural network model (derived from the open-source `nsfwjs` classification architecture).
3. **100% Privacy Guarantee**: The model executes strictly inside your device's memory. No images, screenshots, or metadata are ever transmitted over the network or saved to disk.
4. **Trigger Action**: If the combined confidence score of the analyzed media exceeds your configured threshold, Déchaîner immediately intervenes, displaying a red block screen (`NsfwContentBlockedActivity`) and closing the app.

---

## Configuration Guide

Open **Config > Visual Blocking** to adjust visual protection settings:

### 1. Select Target Apps
- Tap **Select Apps** to pick which apps should be monitored visually.
- **Recommended**: Only enable visual blocking on apps with unmoderated visual feeds (e.g. **Reddit**, **Telegram**, **X/Twitter**).
- **Not Recommended**: Do **not** enable visual blocking on standard web browsers (use DNS + `URLBlocklist` instead) or dedicated safe video apps like YouTube. Running real-time computer vision on every browser webpage wastes battery and causes false positives.

### 2. Choose Detection Categories
Déchaîner classifies images into five standard categories:
- **Porn**: Explicit sexual acts and genital exposure.
- **Hentai**: Explicit illustrated or animated sexual content.
- **Sexy**: Provocative clothing, swimwear, lingerie, or partial nudity.
- **Drawings**: Harmless anime, art, or cartoon drawings.
- **Neutral**: Ordinary, non-sexual objects, faces, landscapes, and daily life.

> [!TIP]
> **Developer Recommendation (Issue #81 & #82)**:
> For the best balance between protection and stability, select **only `Porn` and `Hentai`**. 
> Selecting `Sexy` can cause false positives on fitness feeds, medical diagrams, beach photos, or skin-toned clothing.

### 3. Sensitivity / Confidence Threshold Slider
The threshold slider sets the required confidence score before Déchaîner takes action:
- **Threshold = 90% (Recommended)**: The model must be at least 90% confident that the image is explicit before triggering. This prevents false positives on loading animations, faces, or beige objects.
- **Threshold = 50% or lower**: Triggers on ambiguous shapes, moving fingers, or circular loading icons.

---

## Escalation: Temporary App Suspension

During an intense urge, closing an app might not be enough—a user might compulsively reopen the app repeatedly.

Déchaîner includes an automated **escalation protocol**:
- **Enable Suspension**: When toggled ON, Déchaîner counts how many visual blocks occur within a rolling time window.
- **Configurable Parameters**:
  - *Block Count Threshold*: E.g., 3 visual blocks.
  - *Time Window*: E.g., within 10 minutes.
  - *Suspension Duration*: E.g., suspend the app for 30 minutes.
- **Result**: If an app triggers the visual blocker 3 times in 10 minutes, Déchaîner automatically suspends the app via Device Owner. The app icon greys out, and attempting to open it displays the system IT restriction until the 30-minute timer expires.

---

## Understanding False Positives (Issues #82 & #83)

- **Loading Animations & Skin Tones**: Neural networks detect patterns of color and texture. Occasionally, circular orange/pink loading animations or close-ups of human hands and faces can resemble skin tones. Increasing the threshold to **90%+** resolves almost all false positives.
- **Sensitivity Percentage Direction**: Note that a *higher* percentage means *stricter certainty required*, resulting in fewer false alarms. A *lower* percentage is hyper-aggressive and triggers on everything.
