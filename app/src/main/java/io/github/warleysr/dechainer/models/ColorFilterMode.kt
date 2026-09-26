package io.github.warleysr.dechainer.models

import android.os.Build

enum class ColorFilterMode {
    GRAYSCALE,
    NIGHT_LIGHT,
    EXTRA_DIM,
    INVERSION;

    val isAvailable: Boolean
        get() = this != EXTRA_DIM || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    companion object {
        val available: List<ColorFilterMode> get() = entries.filter { it.isAvailable }
    }
}
