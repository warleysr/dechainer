package io.github.warleysr.dechainer.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.ApkUpdateInstaller

class ApkUpdateResultReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_INSTALL_RESULT = "io.github.warleysr.dechainer.action.APK_UPDATE_INSTALL_RESULT"
        const val ACTION_UNINSTALL_RESULT = "io.github.warleysr.dechainer.action.APK_UPDATE_UNINSTALL_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        // Nobody is going to confirm it, so the session would otherwise stay open forever.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        }
        ApkUpdateInstaller.restoreUnknownSourcesRestrictionsIfIdle(context, sessionId)

        val packageName = intent.getStringExtra(ApkUpdateInstaller.EXTRA_PACKAGE) ?: return
        val message = when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                if (ApkUpdateInstaller.isFreshInstall(context, packageName)) {
                    ApkUpdateInstaller.uninstall(context, packageName)
                    R.string.apk_update_new_app
                } else {
                    R.string.apk_update_success
                }
            }
            PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.apk_update_signature_mismatch
            else -> R.string.apk_update_failed
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
}
