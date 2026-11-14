# Keyword & Content Blocking

Digital triggers often start with text: a search query, a suggested topic, a video title, or a post caption. Déchaîner’s **Blocked Words** engine operates at the accessibility level to detect and neutralize textual triggers before they lead to explicit content.

Navigate to **Config > Blocked Words** to configure text protections.

---

## Active Blocking vs. Passive Blocking

Déchaîner provides two distinct text filtering mechanisms:

| Feature | How It Works | Trigger Action | Best Use Case |
|---------|--------------|----------------|---------------|
| **Active Blocking** | Monitors keystrokes as you type in text input fields. | Erases the forbidden word in real time and shows a blue block screen (`BlockedWordActivity`). | Search bars, messaging inputs, browser address bars. |
| **Passive Blocking** | Scans visible text elements across the whole screen (titles, feeds, comments). | Intercepts the screen and exits the app when a forbidden word is detected. | Feeds, search recommendation lists, unmoderated community boards. |

---

## 1. Configuring Active Blocking

Active blocking monitors what you type into selected apps:

1. Under **Active Blocking**, tap **Select Apps** to choose which apps to monitor (e.g., Chrome, Brave, Play Store).
2. Tap the **Edit (pencil)** button next to **Blocked Words List**.
3. Enter trigger keywords, **one word per line**:
   ```text
   proxy
   nsfw
   escort
   webcam
   ```
4. Save the list.
5. **Testing**: Open a monitored app and type one of the words into any text box. Déchaîner will erase the characters as they appear and present the blue block screen with the detected word.

---

## 2. Configuring Passive Blocking

Passive blocking reads on-screen text nodes rendered by the Android UI hierarchy:

1. Under **Passive Blocking**, tap **Add App** to specify which applications should have their screen scanned.
2. For each selected app, enter the specific keywords to watch for.
3. If an article, comment, post title, or feed item displays that word on screen, Déchaîner immediately closes or blocks the screen.

---

## Important Best Practices & Troubleshooting (Issue #81)

In issue [#81](https://github.com/warleysr/dechainer/issues/81), users reported unexpected blocks and confusion between active and passive blocking. Follow these guidelines to avoid false triggers:

### 1. Beware of Browser Search Suggestions
If you add `proxy` to **Passive Blocking** on a browser:
- You start typing `pro` intending to search for `professional tools`.
- The browser search engine immediately suggests `proxy list` in the dropdown below the address bar.
- Passive blocking sees the word `proxy` appear on screen and immediately closes the browser!
- **Recommendation**: Use **Active Blocking** for browsers so you can type freely; reserve Passive Blocking for feeds where words appear without you typing them.

### 2. Do Not Enable Passive Blocking on Safe Platforms
Do not enable passive scanning on apps like YouTube, Wikipedia, or news apps with generic words like `model`, `video`, or `anime`. These words naturally appear in educational or entertainment content and will cause annoying false positives.

### 3. Word Matching Details
- Keyword matching is **case-insensitive** (`Porn`, `PORN`, and `porn` match identically).
- Accent marks and diacritics are normalized.

### 4. Security Gate
- **Adding** new words or adding apps to the monitor list is instant and free.
- **Removing** words, clearing the list, or deselecting apps requires authenticating with your **Recovery Code**.
