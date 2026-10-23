package io.github.warleysr.dechainer.data

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
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
    const val EXTRA_IS_DOWNGRADE = "io.github.warleysr.dechainer.extra.APK_UPDATE_IS_DOWNGRADE"
    const val EXTRA_DOWNGRADE_SESSION_ID = "io.github.warleysr.dechainer.extra.APK_DOWNGRADE_SESSION_ID"

    private const val PREFS_NAME = "security_prefs"
    private const val KEY_RESTORE_RESTRICTIONS = "apk_update_restore_restrictions"
    private const val KEY_RESTORE_UNINSTALL_BLOCKED = "apk_update_restore_uninstall_blocked"
    private const val KEY_RESTORE_SUSPENDED = "apk_update_restore_suspended"
    private const val KEY_RESTORE_HIDDEN = "apk_update_restore_hidden"

    private const val INSTALL_REQUEST_DOWNGRADE = 0x00000080
    private const val INSTALL_ALLOW_DOWNGRADE = 0x00100000

    private val unknownSourcesRestrictions = listOf(
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
        UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
    )

    private val downgradeRestrictions = unknownSourcesRestrictions + UserManager.DISALLOW_UNINSTALL_APPS

    sealed interface Inspection {
        data class Update(
            val file: File,
            val packageName: String,
            val label: String,
            val icon: Drawable?,
            val installedVersion: String,
            val newVersion: String,
            val isDowngrade: Boolean = false
        ) : Inspection

        data object NewApp : Inspection
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

        val isDowngrade = archive.longVersionCode < installed.longVersionCode ||
            (archive.longVersionCode == installed.longVersionCode &&
                archive.baseRevisionCode < installed.baseRevisionCode)

        val appInfo = installed.applicationInfo
        return Inspection.Update(
            file = file,
            packageName = archive.packageName,
            label = appInfo?.loadLabel(pm)?.toString() ?: archive.packageName,
            icon = appInfo?.loadIcon(pm),
            installedVersion = installed.versionName ?: installed.longVersionCode.toString(),
            newVersion = archive.versionName ?: archive.longVersionCode.toString(),
            isDowngrade = isDowngrade
        )
    }

    fun install(context: Context, update: Inspection.Update): Boolean {
        val uninstallFirst = update.isDowngrade &&
            needsUninstallForDowngrade(context, update.file, update.packageName)

        if (uninstallFirst && !signaturesMatch(context, update.file, update.packageName)) {
            update.file.delete()
            context.sendBroadcast(
                Intent(context, ApkUpdateResultReceiver::class.java)
                    .setAction(ApkUpdateResultReceiver.ACTION_INSTALL_RESULT)
                    .putExtra(EXTRA_PACKAGE, update.packageName)
                    .putExtra(EXTRA_IS_DOWNGRADE, true)
                    .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_CONFLICT)
            )
            return true
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply {
                setAppPackageName(update.packageName)
                if (update.isDowngrade) requestDowngrade(this)
            }
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
                if (uninstallFirst) {
                    savePreDowngradeState(context, update.packageName)
                    liftUnknownSourcesRestrictions(context, includeUninstall = true)
                    installer.uninstall(
                        update.packageName,
                        downgradeUninstallSender(context, sessionId, update.packageName)
                    )
                } else {
                    liftUnknownSourcesRestrictions(context, includeUninstall = false)
                    session.commit(
                        resultSender(
                            context,
                            sessionId,
                            update.packageName,
                            isDowngrade = update.isDowngrade
                        )
                    )
                }
            }
            true
        } catch (_: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            restorePreDowngradeState(context, update.packageName)
            restoreUnknownSourcesRestrictionsIfIdle(context, sessionId)
            false
        } finally {
            update.file.delete()
        }
    }

    /** Commits a staged downgrade session once the older version's predecessor has been uninstalled. */
    fun commitDowngradeSession(context: Context, sessionId: Int, packageName: String): Boolean {
        val installer = context.packageManager.packageInstaller
        return try {
            installer.openSession(sessionId).use { session ->
                session.commit(resultSender(context, sessionId, packageName, isDowngrade = true))
            }
            true
        } catch (_: Exception) {
            abortDowngradeSession(context, sessionId, packageName)
            false
        }
    }

    /** Cleans up a staged downgrade session when the preliminary uninstall fails. */
    fun abortDowngradeSession(context: Context, sessionId: Int, packageName: String) {
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
        onInstallFinished(context, sessionId, packageName)
    }

    /** Restores per-package policies and user restrictions once an install session finishes. */
    fun onInstallFinished(context: Context, sessionId: Int, packageName: String?) {
        if (packageName != null) {
            restorePreDowngradeState(context, packageName)
        }
        AppRepository.invalidateCache()
        restoreUnknownSourcesRestrictionsIfIdle(context, sessionId)
    }

    /**
     * Lets the session install an older version when the OS allows in-place downgrades (debuggable
     * builds or debuggable packages). [PackageInstaller.SessionParams.installFlags] is on the
     * light greylist (`@UnsupportedAppUsage`), whereas `setRequestDowngrade` is a hidden `@SystemApi`.
     */
    internal fun requestDowngrade(params: PackageInstaller.SessionParams) {
        runCatching {
            val field = PackageInstaller.SessionParams::class.java.getField("installFlags")
            field.setInt(
                params,
                field.getInt(params) or INSTALL_REQUEST_DOWNGRADE or INSTALL_ALLOW_DOWNGRADE
            )
        }
        runCatching {
            PackageInstaller.SessionParams::class.java
                .getMethod("setRequestDowngrade", Boolean::class.javaPrimitiveType)
                .invoke(params, true)
        }
    }

    /**
     * Production Android (`!Build.IS_DEBUGGABLE`) blocks in-place downgrades of non-debuggable
     * packages even when `INSTALL_REQUEST_DOWNGRADE` is set, requiring the current version to be
     * uninstalled before the older APK session is committed.
     */
    private fun needsUninstallForDowngrade(
        context: Context,
        archiveFile: File,
        packageName: String
    ): Boolean {
        if (packageName == context.packageName) return false
        val installed = installedInfo(context, packageName) ?: return false
        val flags = installed.applicationInfo?.flags ?: 0
        val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (isSystem) {
            if (!isUpdatedSystem) return false
            val archiveVersion = context.packageManager
                .getPackageArchiveInfo(archiveFile.path, 0)?.longVersionCode ?: return false
            val systemVersion = runCatching {
                context.packageManager
                    .getPackageInfo(packageName, PackageManager.MATCH_SYSTEM_ONLY)
                    .longVersionCode
            }.getOrNull()
            if (systemVersion != null && archiveVersion < systemVersion) return false
        }
        val isAppDebuggable = (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return !Build.IS_DEBUGGABLE && !isAppDebuggable
    }

    @Suppress("DEPRECATION")
    private fun signaturesMatch(context: Context, archiveFile: File, packageName: String): Boolean {
        val pm = context.packageManager
        val flags = PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(archiveFile.path, flags) ?: return false
        val installed = try {
            pm.getPackageInfo(packageName, flags)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        return signaturesMatch(archive, installed)
    }

    @Suppress("DEPRECATION")
    internal fun signaturesMatch(archive: PackageInfo, installed: PackageInfo): Boolean {
        val archiveSigning = archive.signingInfo
        val installedSigning = installed.signingInfo
        if (archiveSigning != null && installedSigning != null) {
            if (archiveSigning.hasMultipleSigners() || installedSigning.hasMultipleSigners()) {
                val archiveSigners = archiveSigning.apkContentsSigners?.toSet().orEmpty()
                val installedSigners = installedSigning.apkContentsSigners?.toSet().orEmpty()
                return archiveSigners.isNotEmpty() && archiveSigners == installedSigners
            }
            val archiveCerts = (archiveSigning.signingCertificateHistory ?: archiveSigning.apkContentsSigners)
                ?.toSet().orEmpty()
            val installedCerts = (installedSigning.signingCertificateHistory ?: installedSigning.apkContentsSigners)
                ?.toSet().orEmpty()
            if (archiveCerts.isNotEmpty() && installedCerts.isNotEmpty()) {
                return archiveCerts.intersect(installedCerts).isNotEmpty()
            }
        }

        val archiveSigs = archive.signatures?.toSet().orEmpty()
        val installedSigs = installed.signatures?.toSet().orEmpty()
        return archiveSigs.isNotEmpty() && archiveSigs == installedSigs
    }

    private fun savePreDowngradeState(context: Context, packageName: String) {
        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (!dpm.isAdminActive(admin)) return

        val wasUninstallBlocked = runCatching { dpm.isUninstallBlocked(admin, packageName) }.getOrDefault(false)
        val wasSuspended = runCatching { dpm.isPackageSuspended(admin, packageName) }.getOrDefault(false)
        val wasHidden = runCatching { dpm.isApplicationHidden(admin, packageName) }.getOrDefault(false)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit(commit = true) {
            if (wasUninstallBlocked) {
                val set = prefs.getStringSet(KEY_RESTORE_UNINSTALL_BLOCKED, null).orEmpty()
                putStringSet(KEY_RESTORE_UNINSTALL_BLOCKED, set + packageName)
            }
            if (wasSuspended) {
                val set = prefs.getStringSet(KEY_RESTORE_SUSPENDED, null).orEmpty()
                putStringSet(KEY_RESTORE_SUSPENDED, set + packageName)
            }
            if (wasHidden) {
                val set = prefs.getStringSet(KEY_RESTORE_HIDDEN, null).orEmpty()
                putStringSet(KEY_RESTORE_HIDDEN, set + packageName)
            }
        }

        if (wasUninstallBlocked) {
            runCatching { dpm.setUninstallBlocked(admin, packageName, false) }
        }
    }

    private fun restorePreDowngradeState(context: Context, packageName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val unblockSet = prefs.getStringSet(KEY_RESTORE_UNINSTALL_BLOCKED, null).orEmpty()
        val suspendedSet = prefs.getStringSet(KEY_RESTORE_SUSPENDED, null).orEmpty()
        val hiddenSet = prefs.getStringSet(KEY_RESTORE_HIDDEN, null).orEmpty()

        val restoreUninstallBlocked = packageName in unblockSet
        val restoreSuspended = packageName in suspendedSet
        val restoreHidden = packageName in hiddenSet
        if (!restoreUninstallBlocked && !restoreSuspended && !restoreHidden) return

        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (dpm.isAdminActive(admin) && installedInfo(context, packageName) != null) {
            if (restoreUninstallBlocked) {
                runCatching { dpm.setUninstallBlocked(admin, packageName, true) }
            }
            if (restoreSuspended) {
                runCatching { dpm.setPackagesSuspended(admin, arrayOf(packageName), true) }
            }
            if (restoreHidden) {
                runCatching { dpm.setApplicationHidden(admin, packageName, true) }
            }
        }

        prefs.edit {
            val nextUnblock = unblockSet - packageName
            if (nextUnblock.isEmpty()) remove(KEY_RESTORE_UNINSTALL_BLOCKED)
            else putStringSet(KEY_RESTORE_UNINSTALL_BLOCKED, nextUnblock)

            val nextSuspended = suspendedSet - packageName
            if (nextSuspended.isEmpty()) remove(KEY_RESTORE_SUSPENDED)
            else putStringSet(KEY_RESTORE_SUSPENDED, nextSuspended)

            val nextHidden = hiddenSet - packageName
            if (nextHidden.isEmpty()) remove(KEY_RESTORE_HIDDEN)
            else putStringSet(KEY_RESTORE_HIDDEN, nextHidden)
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
            context,
            packageName.hashCode(),
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        context.packageManager.packageInstaller.uninstall(packageName, pendingIntent.intentSender)
    }

    private fun liftUnknownSourcesRestrictions(context: Context, includeUninstall: Boolean) {
        val dpm = DeviceAdmin.policyManager
        val admin = DeviceAdmin.component
        if (!dpm.isAdminActive(admin)) return

        val active = dpm.getUserRestrictions(admin)
        val candidates = if (includeUninstall) downgradeRestrictions else unknownSourcesRestrictions
        val lifted = candidates.filter { active.getBoolean(it) }
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

    private fun downgradeUninstallSender(context: Context, sessionId: Int, packageName: String) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, ApkUpdateResultReceiver::class.java)
                .setAction(ApkUpdateResultReceiver.ACTION_DOWNGRADE_UNINSTALL_RESULT)
                .putExtra(EXTRA_DOWNGRADE_SESSION_ID, sessionId)
                .putExtra(EXTRA_PACKAGE, packageName),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ).intentSender

    private fun resultSender(
        context: Context,
        sessionId: Int,
        packageName: String,
        isDowngrade: Boolean = false
    ) =
        PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, ApkUpdateResultReceiver::class.java)
                .setAction(ApkUpdateResultReceiver.ACTION_INSTALL_RESULT)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_IS_DOWNGRADE, isDowngrade),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        ).intentSender
}
