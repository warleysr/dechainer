package io.github.warleysr.dechainer.utils

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams

class NightLightOverlay(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show(intensity: Int) {
        val color = tint(intensity)
        view?.let {
            it.setBackgroundColor(color)
            return
        }

        val overlay = View(service).apply { setBackgroundColor(color) }
        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT,
            LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            LayoutParams.FLAG_NOT_TOUCHABLE or LayoutParams.FLAG_NOT_FOCUSABLE or
                LayoutParams.FLAG_LAYOUT_IN_SCREEN or LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            layoutInDisplayCutoutMode = LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
        }
        windowManager.addView(overlay, params)
        view = overlay
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        view = null
    }

    private fun tint(intensity: Int): Int {
        val alpha = intensity.coerceIn(0, 100) * MAX_ALPHA / 100
        return Color.argb(alpha, 255, 130, 0)
    }

    private companion object {
        const val MAX_ALPHA = 120
    }
}
