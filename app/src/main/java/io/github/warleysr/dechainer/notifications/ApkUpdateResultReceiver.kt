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
        const val ACTION_DOWNGRADE_UNINSTALL_RESULT =
            "io.github.warleysr.dechainer.action.APK_DOWNGRADE_UNINSTALL_RESULT"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DOWNGRADE_UNINSTALL_RESULT -> onDowngradeUninstallResult(context, intent)
            ACTION_INSTALL_RESULT -> onInstallResult(context, intent)
        }
    }

    private fun onDowngradeUninstallResult(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(ApkUpdateInstaller.EXTRA_DOWNGRADE_SESSION_ID, -1)
        val packageName = intent.getStringExtra(ApkUpdateInstaller.EXTRA_PACKAGE) ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)

        if (status == PackageInstaller.STATUS_SUCCESS && sessionId != -1) {
            if (ApkUpdateInstaller.commitDowngradeSession(context, sessionId, packageName)) {
                return
            }
        } else {
            ApkUpdateInstaller.abortDowngradeSession(context, sessionId, packageName)
        }
        Toast.makeText(context, R.string.apk_update_failed, Toast.LENGTH_LONG).show()
    }

    private fun onInstallResult(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(ApkUpdateInstaller.EXTRA_PACKAGE)
        val isDowngrade = intent.getBooleanExtra(ApkUpdateInstaller.EXTRA_IS_DOWNGRADE, false)

        // Nobody is going to confirm it, so the session would otherwise stay open forever.
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION && sessionId != -1) {
            runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        }
        ApkUpdateInstaller.onInstallFinished(context, sessionId, packageName)
        if (packageName == null) return

        val message = when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                if (!isDowngrade && ApkUpdateInstaller.isFreshInstall(context, packageName)) {
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
