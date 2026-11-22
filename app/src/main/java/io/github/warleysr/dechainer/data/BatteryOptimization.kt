package io.github.warleysr.dechainer.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import io.github.warleysr.dechainer.utils.ShizukuRunner
import timber.log.Timber

object BatteryOptimization {

    fun isExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(PowerManager::class.java)
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    fun grantViaShizuku(context: Context): Boolean {
        if (isExempt(context)) return true
        runCatching {
            ShizukuRunner.exec("dumpsys", "deviceidle", "whitelist", "+${context.packageName}")
        }.onFailure {
            Timber.w(it, "Failed to grant battery optimization exemption via Shizuku")
        }
        return isExempt(context)
    }

    fun request(context: Context) {
        runCatching {
            val hasActivity = context.findActivity() != null
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                "package:${context.packageName}".toUri()
            ).apply {
                if (!hasActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    if (!hasActivity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }.onFailure {
            Timber.w(it, "Failed to open battery optimization settings")
        }
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
