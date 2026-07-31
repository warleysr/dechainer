package io.github.warleysr.dechainer.models

import android.graphics.drawable.Drawable

data class AppItem(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    val isSystem: Boolean,
    val isHidden: Boolean = false,
    val isUninstallBlocked: Boolean = false,
    val timeLimitMinutes: Int = 0,
    val reopeningSeconds: Int = 0,
    val isSuspended: Boolean = false
)
