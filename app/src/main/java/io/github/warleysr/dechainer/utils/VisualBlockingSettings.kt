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

    val DEFAULT_TARGET_PACKAGES: Set<String> = setOf("com.reddit.frontpage")
    val DEFAULT_CATEGORIES: Set<String> = setOf("hentai", "porn", "sexy")
    const val DEFAULT_THRESHOLD = 0.5f

    // "neutral" means "nothing flagged" — it doesn't belong in a "block if present" category list.
    val SELECTABLE_CATEGORIES: List<String> = NsfwContentDetector.LABELS.filter { it != "neutral" }
}
