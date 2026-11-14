package io.github.warleysr.dechainer.data

import io.github.warleysr.dechainer.utils.NsfwContentDetector

/**
 * Shared source of truth for the "Visual blocking" (NSFW / Nudity image monitoring) preferences —
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

    // Default categories: strictly explicit, exposed anatomical regions (NudeNet v3 detector)
    val DEFAULT_CATEGORIES: Set<String> = setOf(
        "FEMALE_GENITALIA_EXPOSED",
        "MALE_GENITALIA_EXPOSED",
        "ANUS_EXPOSED",
        "FEMALE_BREAST_EXPOSED",
        "BUTTOCKS_EXPOSED"
    )

    const val DEFAULT_THRESHOLD = 0.45f
    const val DEFAULT_SUSPEND_BLOCK_COUNT = 5
    const val DEFAULT_SUSPEND_WINDOW_MINUTES = 15
    const val DEFAULT_SUSPEND_DURATION_MINUTES = 30

    // Sensitive categories that can be toggled in the UI:
    // 5 explicit exposed + 4 covered/suggestive (swimwear/lingerie).
    // Safe anatomical classes (faces, bellies, feet, armpits, male breasts) are excluded to prevent false positives.
    val SELECTABLE_CATEGORIES: List<String> = listOf(
        "FEMALE_GENITALIA_EXPOSED",
        "MALE_GENITALIA_EXPOSED",
        "ANUS_EXPOSED",
        "FEMALE_BREAST_EXPOSED",
        "BUTTOCKS_EXPOSED",
        "FEMALE_BREAST_COVERED",
        "BUTTOCKS_COVERED",
        "FEMALE_GENITALIA_COVERED",
        "ANUS_COVERED"
    )

    /**
     * Sanitizes and migrates categories: converts legacy GantMan categories ("porn", "hentai", "sexy", "drawings")
     * to the corresponding NudeNet v3 categories, and ensures valid selections.
     */
    fun sanitizeCategories(saved: Set<String>?): Set<String> {
        if (saved.isNullOrEmpty()) return DEFAULT_CATEGORIES

        val hasLegacyExplicit = "porn" in saved || "hentai" in saved
        val hasLegacySuggestive = "sexy" in saved

        if (hasLegacyExplicit || hasLegacySuggestive) {
            val migrated = mutableSetOf<String>()
            if (hasLegacyExplicit) {
                migrated.addAll(DEFAULT_CATEGORIES)
            }
            if (hasLegacySuggestive) {
                migrated.add("FEMALE_BREAST_COVERED")
                migrated.add("BUTTOCKS_COVERED")
                migrated.add("FEMALE_GENITALIA_COVERED")
            }
            return migrated
        }

        val valid = saved.filter { it in SELECTABLE_CATEGORIES }.toSet()
        return if (valid.isEmpty()) DEFAULT_CATEGORIES else valid
    }
}
