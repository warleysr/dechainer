package io.github.warleysr.dechainer.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.UserManager
import androidx.core.content.edit
import io.github.warleysr.dechainer.notifications.ApkUpdateResultReceiver
import java.io.File

/**
 * Lets an APK picked from the lock screen replace an app that is already installed, while new apps
 * stay blocked. The unknown sources restrictions are lifted only for the duration of the install
 * and put back once its result arrives.
 */
object ApkUpdateInstaller {
    const val EXTRA_PACKAGE = "io.github.warleysr.dechainer.extra.APK_UPDATE_PACKAGE"

    private const val PREFS_NAME = "security_prefs"
    private const val KEY_RESTORE_RESTRICTIONS = "apk_update_restore_restrictions"

    private val unknownSourcesRestrictions = listOf(
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
    )

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

    /**
     * Whether installing APKs is blocked, which is the only case where this updater is useful.
     * Restrictions lifted by an update still in progress count as active.
     */
    fun isUnknownSourcesRestricted(context: Context): Boolean {
        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (!dpm.isAdminActive(admin)) return false

        val pending = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_RESTORE_RESTRICTIONS, null)
        if (!pending.isNullOrEmpty()) return true

        val active = dpm.getUserRestrictions(admin)
        return unknownSourcesRestrictions.any { active.getBoolean(it) }
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
                liftUnknownSourcesRestrictions(context)
                session.commit(resultSender(context, sessionId, update.packageName))
            }
            true
        } catch (_: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            restoreUnknownSourcesRestrictionsIfIdle(context, sessionId)
            false
        } finally {
            update.file.delete()
        }
    }

    /** Puts back the restrictions lifted by [install]; a no-op when none are pending. */
    private fun restoreUnknownSourcesRestrictions(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(KEY_RESTORE_RESTRICTIONS, null) ?: return

        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (dpm.isAdminActive(admin)) {
            keys.forEach { dpm.addUserRestriction(admin, it) }
        }
        prefs.edit { remove(KEY_RESTORE_RESTRICTIONS) }
    }

    /**
     * Restores the restrictions unless another update is still running. [finishedSessionId] is the
     * session whose result is being handled, which may not have been cleaned up yet.
     */
    fun restoreUnknownSourcesRestrictionsIfIdle(context: Context, finishedSessionId: Int? = null) {
        val sessions = context.packageManager.packageInstaller.mySessions
        if (sessions.all { it.sessionId == finishedSessionId }) {
            restoreUnknownSourcesRestrictions(context)
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

    private fun liftUnknownSourcesRestrictions(context: Context) {
        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (!dpm.isAdminActive(admin)) return

        val active = dpm.getUserRestrictions(admin)
        val lifted = unknownSourcesRestrictions.filter { active.getBoolean(it) }
        if (lifted.isEmpty()) return

        // Saved before clearing, so a crash in between still leaves a way to put them back.
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(KEY_RESTORE_RESTRICTIONS, null).orEmpty()
        prefs.edit(commit = true) { putStringSet(KEY_RESTORE_RESTRICTIONS, pending + lifted) }
        lifted.forEach { dpm.clearUserRestriction(admin, it) }
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
