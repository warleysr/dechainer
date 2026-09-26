package io.github.warleysr.dechainer.models

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Build

enum class ColorFilterMode {
    GRAYSCALE,
    NIGHT_LIGHT,
    EXTRA_DIM,
    INVERSION;

    val isAvailable: Boolean
        get() = when (this) {
            EXTRA_DIM -> extraDimAvailable
            GRAYSCALE, NIGHT_LIGHT, INVERSION -> true
        }

    companion object {
        val available: List<ColorFilterMode> get() = entries.filter { it.isAvailable }

        private val extraDimAvailable by lazy {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                systemBoolean("config_reduceBrightColorsAvailable")
        }

        @SuppressLint("DiscouragedApi")
        private fun systemBoolean(name: String): Boolean {
            val resources = Resources.getSystem()
            val id = resources.getIdentifier(name, "bool", "android")
            return id != 0 && resources.getBoolean(id)
        }
    }
}
