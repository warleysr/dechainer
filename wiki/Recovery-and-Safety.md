# Recovery Code & Safety Architecture

Déchaîner is built with a singular premise: **your current self must be protected from your impulsive future self.**

Most blockers fail because they rely on willpower or a simple PIN that is easily memorized, changed, or bypassed during a moment of weakness. Déchaîner replaces easily accessible passwords with an offline physical security model.

---

## The 16-Character Recovery Code

When configuring Déchaîner for the first time, it generates a cryptographically random **16-character code** (letters `A` through `Z`):

```
Example: K R X M - W B P L - Q T V C - F Z H J
```

### Storage Rules
1. **Write it on paper by hand.** Never copy-paste it into a digital password manager, note app, or email draft. If you store it digitally, you might block the app or device needed to access it, or easily retrieve it during an urge.
2. **Store it out of reach.** Keep the physical paper in a place requiring deliberate effort to access:
   - A sealed envelope placed on a high closet shelf.
   - Inside a locked toolbox.
   - At your office, a relative's house, or given to an accountability partner.
3. **Verify during setup**: The in-app wizard requires you to re-type the code directly from your physical paper. If a single character is incorrect, setup will not proceed.

---

## Asymmetric Security: Tightening vs. Loosening

Déchaîner enforces strict **one-way ratchet mechanics** (via the `RecoveryGate` system):

| Action Type | Examples | Recovery Code Required? |
|-------------|----------|-------------------------|
| **Tightening** | Adding a forbidden keyword, reducing app time limit from 60m to 30m, adding a site to URLBlocklist, enabling color filters, starting an Impulse Lock, adding an activity block | **NO** (Instant & Free) |
| **Loosening** | Deleting a keyword, increasing app time limit, disabling visual blocker, turning off Device Owner, whitelisting a browser, removing restrictions | **YES** (Requires Recovery Code) |

This asymmetry means you never have to fetch your paper code to make your phone stricter or protect yourself right now. You only need the code when relaxing rules.

---

## The 10-Minute Security Session

Entering a 16-character code with a shuffled keyboard for every single toggle would be tedious and frustrating. 

To solve this:
1. Entering your valid recovery code starts an active **10-minute administrative session**.
2. A live timer (`MM:SS`) is displayed in the top bar alongside a lock icon.
3. While the session is active, you can adjust multiple settings, configure app limits, and manage restrictions freely without re-entering the code.
4. **Session Controls**:
   - **Renew Session (`Refresh` icon)**: Resets the timer back to 10 minutes (useful during extended device maintenance).
   - **End Session (`Logout` icon)**: Instantly locks the session and requires the recovery code again.
5. If the app is closed or 10 minutes expire, the session automatically terminates.

---

## Shuffle Keyboard

To stop muscle memory and prevent someone from memorizing the key through finger positioning:
- In **Config > Keyboard Type**, you can switch between **Normal Keyboard** and **Shuffle Keyboard**.
- The Shuffle Keyboard randomizes letter positions on every prompt.
- Even if you enter the code multiple times, finger patterns cannot be committed to muscle memory.

---

## The 48-Hour Forced Removal Fallback

A common fear with Device Owner apps is becoming permanently locked out if a paper key is lost or destroyed. If `DISALLOW_FACTORY_RESET` is enabled, even a hardware factory reset is impossible.

To prevent bricking your device while remaining relapse-proof, Déchaîner includes a **48-Hour Forced Removal** delay:

1. In the **Config** tab, tap **Forced Removal**.
2. Tap **Confirm** to start the 48-hour countdown.
3. The countdown is hardware-backed (`SystemClock.elapsedRealtime()`) and resists system clock manipulation or device restarts.
4. **During the 48 hours**:
   - All active restrictions remain strictly in effect.
   - You can cancel the timer at any time if you started it during an impulsive moment.
5. **After 48 hours expire**:
   - The status in Config displays **"Active"**.
   - Tap **Forced Removal**, check the confirmation box, and confirm.
   - Déchaîner cleanly strips Device Owner privileges and allows standard uninstallation.

> [!NOTE]
> Why 48 hours? Psychological studies and addiction research show that intense neurochemical urges and compulsive impulses subside well within 24 to 48 hours. A 48-hour delay is long enough to stop relapses, but short enough to rescue a user who genuinely lost their recovery code.
