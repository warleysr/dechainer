package io.github.warleysr.dechainer.utils

/**
 * Shared source of truth for the "Visual blocking" (NSFW image/video monitoring) preferences —
 * read by both the settings screen (writer) and [io.github.warleysr.dechainer.DechainerAccessibilityService]
 * (reader), so the keys and defaults can't drift between the two.
 */
object VisualBlockingSettings {
    const val PREFS_NAME = "visual_blocking_prefs"
    const val KEY_ENABLED = "enabled"
    const val KEY_TARGET_PACKAGES = "target_packages"
    const val KEY_CATEGORIES = "categories"
    const val KEY_THRESHOLD = "threshold"

    // Escalation: instead of only kicking the user out of the app on every detection, suspend the
    // app entirely once it has been blocked KEY_SUSPEND_BLOCK_COUNT times within
    // KEY_SUSPEND_WINDOW_MINUTES, for KEY_SUSPEND_DURATION_MINUTES. Off by default, so the
    // behaviour without it stays exactly what it was.
    const val KEY_SUSPEND_ENABLED = "suspend_enabled"
    const val KEY_SUSPEND_BLOCK_COUNT = "suspend_block_count"
    const val KEY_SUSPEND_WINDOW_MINUTES = "suspend_window_minutes"
    const val KEY_SUSPEND_DURATION_MINUTES = "suspend_duration_minutes"

    val DEFAULT_TARGET_PACKAGES: Set<String> = setOf("com.reddit.frontpage")
    val DEFAULT_CATEGORIES: Set<String> = setOf("hentai", "porn", "sexy")
    const val DEFAULT_THRESHOLD = 0.5f
    const val DEFAULT_SUSPEND_BLOCK_COUNT = 5
    const val DEFAULT_SUSPEND_WINDOW_MINUTES = 15
    const val DEFAULT_SUSPEND_DURATION_MINUTES = 30

    // "neutral" means "nothing flagged" — it doesn't belong in a "block if present" category list.
    val SELECTABLE_CATEGORIES: List<String> = NsfwContentDetector.LABELS.filter { it != "neutral" }
}
