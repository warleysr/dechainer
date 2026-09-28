package io.github.warleysr.dechainer

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.ColorFilterController
import io.github.warleysr.dechainer.data.FocusMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class DechainerApplication : Application() {

    companion object {
        private lateinit var instance: DechainerApplication

        fun getInstance() : DechainerApplication {
            return instance
        }
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidateCache()
            applicationScope.launch { AppRepository.getApps() }
        }
    }

    override fun onCreate() {
        super.onCreate()

        instance = this

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        registerReceiver(packageChangeReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        })

        applicationScope.launch {
            AppRepository.getApps()
        }

        releaseOrphanedColorFilters()

        // Catches up with a focus phase that ran out while the process was dead, and re-arms its alarm.
        FocusMode.sync(this)
    }

    // A killed process skips the service's onUnbind, leaving the modes applied.
    private fun releaseOrphanedColorFilters() {
        if (!ColorFilterController.isApplied(this)) return
        val service = ComponentName(this, DechainerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')
            ?.any { ComponentName.unflattenFromString(it) == service } == true
        if (!enabled) ColorFilterController.release(this)
    }
}