# Focus Mode & Impulse Lock

When fighting compulsive habits, motivation fluctuates. Déchaîner combines **emergency intervention (Impulse Lock)** with **structured productivity (Focus Mode)** to keep you grounded.

---

# Part 1: Impulse Lock (Panic Button)

The **Impulse Lock** is an emergency circuit breaker designed for high-risk moments when an urge strikes.

Navigate to **Config > Impulse Lock** to configure its parameters.

---

## 1. Panic Button on the Lock Screen

- Whenever you open Déchaîner, the lock screen features a prominent **"I'm having impulses"** panic button.
- **No authentication required**: You can tap the panic button immediately without entering a PIN, biometric scan, or recovery code.
- Tapping the button activates the lock timer instantly.

---

## 2. Configurable Lock Action

Under **Impulse Action**, select what happens when the panic button is triggered:

1. **Timer Only**: Locks Déchaîner itself for the configured duration. You cannot open Déchaîner to loosen settings or inspect passwords.
2. **Timer and Suspend Apps**: Locks Déchaîner **and** automatically suspends a user-chosen list of apps (e.g. social media, browsers, messaging) for the full duration. Those apps immediately freeze and become unopenable.

---

## 3. Lock Duration & Anti-Tampering

- **Duration**: Adjustable from **15 minutes to 6 hours** (default: 60 minutes).
- **Tamper Resistance**: The countdown uses the Android monotonic hardware clock (`SystemClock.elapsedRealtime()`). 
- **Survives Clock Changes & Reboots**: Changing the phone's system time, date, or time zone has zero effect. Rebooting the device will not cancel or reset the timer.

---

## 4. Opening Challenges

To prevent impulsively entering Déchaîner to relax rules, opening the app requires passing through biometric/device authentication **plus optional cognitive challenges**. 

You can select any combination; they run sequentially:

```
[ Biometric / PIN ] ──> [ Math Quiz ] ──> [ Words Typing ] ──> [ Tetris ] ──> [ Reflective Reading ] ──> [ Unlocked ]
```

### Challenge 1: Math Quiz
- Solves 5 rapid mental arithmetic questions (addition, subtraction, multiplication).
- Engages the prefrontal cortex to interrupt emotional impulse loops.

### Challenge 2: Words Typing
- Requires accurately typing 32 random words in a row.
- Making a mistake requires starting over.

### Challenge 3: Tetris Challenge
- Play Tetris for a configurable duration (1 to 15 minutes; default: 3 minutes).
- Time only counts down while actively dropping pieces.
- Gravity speeds up as levels increase, with lock delay, line clear animations, and optional sound effects.
- Once the timer expires, the board stays active until you tap *"Continue"*, allowing you to finish your current piece.

### Challenge 4: Reflective Reading (Scripture & Philosophy)
- Forces reading a series of texts (1 to 10 passages; default: 5) centered on lust, self-discipline, and temperance.
- **Text Sources**:
  - **Bible**: King James Version (English) / João Ferreira de Almeida (Portuguese).
  - **Quran**: Saheeh International (English) / Samir El-Hayek (Portuguese).
  - **Philosophers**: Stoic, Classical, and Renaissance wisdom on temperance.
  - **Custom**: Your own personal reminders, commitments, or family photos.
- **Word-by-Word Highlighting**: Passages cannot be skimmed or rapidly scrolled through. Words highlight one by one at a slow, measured reading cadence based on character length. The *"Next"* button only unlocks when the reading timer completes.

> [!TIP]
> Every challenge screen includes a **"Give Up"** button that immediately returns to the lock screen. Giving up is always an option if you realize you were opening the app out of an urge.

---

# Part 2: Focus Mode (Pomodoro Sessions)

Focus Mode brings structured productivity sessions to Déchaîner, helping you dedicate blocks of time to study or deep work.

Navigate to **Config > Focus Mode** to configure session rules.

---

## 1. The Pomodoro Cycle

Focus Mode alternates between intense focus sessions and restorative breaks:
- **Focus Duration**: 15 to 120 minutes (default: 25 minutes).
- **Short Break**: 1 to 30 minutes (default: 5 minutes).
- **Long Break**: 5 to 60 minutes (default: 15 minutes).
- **Cycles Before Long Break**: 1 to 8 focus periods (default: 4).

---

## 2. App & Group Suspension During Focus

- **App & Group Selection**: Choose individual apps or entire App Groups (e.g. *"Games"*, *"Streaming"*) to suspend during focus periods.
- **Automatic Restoration**: When the focus timer ends and a break begins, the apps are automatically unsuspended so you can take a breather. As soon as the break timer ends, the apps are suspended again.

---

## 3. Focus System Restrictions (Scheduled Restrictions)

Many users asked (Issues [#41](https://github.com/warleysr/dechainer/issues/41), [#62](https://github.com/warleysr/dechainer/issues/62), [#70](https://github.com/warleysr/dechainer/issues/70)) if system restrictions like disabling Bluetooth, Wi-Fi, or app installs could run on a schedule instead of being permanent 24/7.

- In **Focus Mode > Focus Restrictions**, you can select specific system restrictions that apply **only while a Focus session is active**.
- Example: Disable app installations and restrict notifications during work sessions, and have them automatically restore when your session is finished.

---

## 4. Focus Timetable & Schedules

- Create weekly schedules (e.g. Monday–Friday from `09:00` to `12:00`).
- **Safety Prompt**: To prevent abruptly cutting off an urgent call or work in progress, scheduled focus sessions trigger an alert prompting you to start the session, or auto-start if configured.

---

## 5. Persistent Status Notification

While Focus Mode is active:
- An ongoing system notification displays your current phase (Focus / Short Break / Long Break), a live countdown, and a progress bar.
- **One-Way Ratchet**: Starting a session, skipping a break, or resuming is always free. Pausing or ending a session prematurely requires your **Recovery Code**.
