package io.github.warleysr.dechainer.models

import io.github.warleysr.dechainer.R

enum class SuspensionReason(val titleResId: Int) {
    UNSUPPORTED_BROWSER(R.string.suspension_reason_browser_unsupported),
    TORRENT_APP(R.string.suspension_reason_torrent),
    RATED_18_PLUS(R.string.suspension_reason_18_plus),
    VISUAL_BLOCKING(R.string.suspension_reason_visual_blocking),
    IMPULSE_LOCK(R.string.suspension_reason_impulse),
    FOCUS_MODE(R.string.suspension_reason_focus),
    ACCESSIBILITY_DISABLED(R.string.suspension_reason_accessibility_disabled),
    MANUAL(R.string.suspension_reason_manual)
}

data class SuspensionLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: SuspensionReason,
    val details: String? = null
)
