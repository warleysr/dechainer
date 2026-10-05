package io.github.warleysr.dechainer.data

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import io.github.warleysr.dechainer.activities.ApkUpdateActivity
import io.github.warleysr.dechainer.notifications.ApkUpdateResultReceiver
import java.io.File

/**
 * Lets an APK opened with Dechainer replace an app that is already installed, while new apps stay
 * blocked by the unknown sources restriction. Device owner sessions skip that restriction.
 */
object ApkUpdateInstaller {
    const val EXTRA_PACKAGE = "io.github.warleysr.dechainer.extra.APK_UPDATE_PACKAGE"

    sealed interface Inspection {
        data class Update(
            val file: File,
            val packageName: String,
            val label: String,
            val icon: Drawable?,
            val installedVersion: String,
            val newVersion: String
        ) : Inspection

        data object NewApp : Inspection
        data object Downgrade : Inspection
        data object Invalid : Inspection
    }

    private fun component(context: Context) = ComponentName(context, ApkUpdateActivity::class.java)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        context.packageManager.setComponentEnabledSetting(
            component(context), state, PackageManager.DONT_KILL_APP
        )
    }

    fun inspect(context: Context, uri: Uri): Inspection {
        val dir = File(context.cacheDir, "apk_update").apply { mkdirs() }
        val file = File.createTempFile("update", ".apk", dir)
        val copied = runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }.isSuccess
        if (!copied) return Inspection.Invalid.also { file.delete() }

        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, 0)
            ?: return Inspection.Invalid.also { file.delete() }
        val installed = installedInfo(context, archive.packageName)
            ?: return Inspection.NewApp.also { file.delete() }
        if (archive.longVersionCode < installed.longVersionCode) {
            return Inspection.Downgrade.also { file.delete() }
        }

        val appInfo = installed.applicationInfo
        return Inspection.Update(
            file = file,
            packageName = archive.packageName,
            label = appInfo?.loadLabel(pm)?.toString() ?: archive.packageName,
            icon = appInfo?.loadIcon(pm),
            installedVersion = installed.versionName ?: installed.longVersionCode.toString(),
            newVersion = archive.versionName ?: archive.longVersionCode.toString()
        )
    }

    fun install(context: Context, update: Inspection.Update): Boolean {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(update.packageName) }
        val sessionId = installer.createSession(params)

        return try {
            installer.openSession(sessionId).use { session ->
                update.file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, update.file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                if (installedInfo(context, update.packageName) == null) {
                    session.abandon()
                    return false
                }
                session.commit(resultSender(context, sessionId, update.packageName))
            }
            true
        } catch (_: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            false
        } finally {
            update.file.delete()
        }
    }

    fun isFreshInstall(context: Context, packageName: String): Boolean {
        val info = installedInfo(context, packageName) ?: return false
        return info.firstInstallTime == info.lastUpdateTime
    }

    fun uninstall(context: Context, packageName: String) {
        val intent = Intent(context, ApkUpdateResultReceiver::class.java)
            .setAction(ApkUpdateResultReceiver.ACTION_UNINSTALL_RESULT)
        val pendingIntent = PendingIntent.getBroadcast(
            context, packageName.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE
        )
        context.packageManager.packageInstaller.uninstall(packageName, pendingIntent.intentSender)
    }

    private fun installedInfo(context: Context, packageName: String) = try {
        context.packageManager.getPackageInfo(packageName, 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun resultSender(context: Context, sessionId: Int, packageName: String) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, ApkUpdateResultReceiver::class.java)
                .setAction(ApkUpdateResultReceiver.ACTION_INSTALL_RESULT)
                .putExtra(EXTRA_PACKAGE, packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ).intentSender
}
