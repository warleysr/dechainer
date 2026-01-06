package io.github.warleysr.dechainer.viewmodels

import android.accounts.AccountManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.Context.DEVICE_POLICY_SERVICE
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.utils.ShizukuRunner
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener
import androidx.core.net.toUri

class DeviceOwnerViewModel() : ViewModel() {

    private val dpm = DechainerApplication.getInstance().getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val packageName = DechainerApplication.getInstance().packageName

    private var selectedTabState = mutableStateOf("restrictions")
    private var shizukuPermission = mutableStateOf(Shizuku.pingBinder() && checkShizukuPermission())
    private var isDeviceOwner = mutableStateOf(dpm.isDeviceOwnerApp(packageName))

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    private val requestResultPermissionListener =
        OnRequestPermissionResultListener { requestCode: Int, permissions: Int ->
            this.onRequestPermissionsResult(
                requestCode,
                permissions
            )
        }

    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        println("requestCode: $requestCode requestResult: $grantResult")
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        shizukuPermission.value = granted
    }

    fun isShizukuPermissionGranted() = shizukuPermission.value

    fun addShizukuListener() {
        Shizuku.addRequestPermissionResultListener(requestResultPermissionListener)
    }

    fun removeShizukuListener() {
        Shizuku.removeRequestPermissionResultListener(requestResultPermissionListener)
    }

    fun selectedTab() = selectedTabState.value

    fun navigateTo(screen: String) { selectedTabState.value = screen }

    fun isDeviceOwner() : Boolean {
        return isDeviceOwner.value
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            DechainerApplication.getInstance().packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) != null
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun installShizuku() {
        val intent = try {
            Intent(Intent.ACTION_VIEW, "market://details?id=$SHIZUKU_PACKAGE".toUri())
                .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        } catch (e: ActivityNotFoundException) {
            Intent(
                Intent.ACTION_VIEW,
                "https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE".toUri(),
            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        }
        DechainerApplication.getInstance().applicationContext.startActivity(intent)
    }

    fun openShizukuSetupGuide() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://shizuku.rikka.app/guide/setup/".toUri(),
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        DechainerApplication.getInstance().applicationContext.startActivity(intent)
    }

    fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            return false
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            return false
        } else {
            // Request the permission
            return false
        }
    }

    fun processDeviceOwnerPrivileges(remove: Boolean = false) {
        val command = if (remove) "remove-active-admin" else "set-device-owner"
        val packageName = DechainerApplication.getInstance().packageName
        ShizukuRunner.command(
            command = "dpm $command $packageName/.DechainerDeviceAdminReceiver",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                }
                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            })
    }

    fun setPrivateDNS(host: String) {
        ShizukuRunner.command(
            command = "settings put global private_dns_mode host",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                }
                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            }
        )
        ShizukuRunner.command(
            command = "settings put global private_dns_specifier $host",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                }
                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            }
        )
    }

    fun getAllAccountsViaShizuku(): List<Pair<String, String>> {
        val accounts = mutableListOf<Pair<String, String>>()
        try {
            ShizukuRunner.command(
                command = "dumpsys account",
                listener = object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        val regex = "Account \\{name=(.*?), type=(.*?)\\}".toRegex()

                        output.lines().forEach { line ->
                            val match = regex.find(line)
                            if (match != null) {
                                val accountName = match.groupValues[1]
                                val accountType = match.groupValues[2]
                                accounts.add(Pair(accountType, accountName))
                            }
                        }
                    }
                    override fun onCommandError(error: String) {
                        Log.e("Shizuku", error)
                    }
                })

        } catch (e: Exception) {
            Log.e("ShizukuError", "Erro ao buscar contas", e)
        }
        return accounts
    }

    fun getAppNameFromAccountType(context: Context, accountType: String): String {
        val am = AccountManager.get(context)
        val packManager = context.packageManager

        val authenticators = am.authenticatorTypes

        val auth = authenticators.find { it.type == accountType }

        return if (auth != null) {
            try {
                val appInfo = packManager.getApplicationInfo(auth.packageName, 0)
                packManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                accountType
            }
        } else {
            accountType
        }
    }

    fun getCurrentDeviceOwner(): Pair<String, String>? {
        return try {
            var dpmOutput = ""
            ShizukuRunner.command(
                command = "dpm list-owners",
                listener = object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        println("Output: $output Done: $done")
                        dpmOutput = output
                    }

                    override fun onCommandError(error: String) {
                        Log.e("Shizuku", error)
                    }
                })
            val componentPath = dpmOutput
                .substringAfter("admin=", "")
                .substringBefore(",", "")

            if (componentPath.isEmpty() || !componentPath.contains("/")) return null

            val parts = componentPath.split("/")
            val packageName = parts[0].trim()
            var receiverName = parts[1].trim()

            if (receiverName.startsWith(".")) {
                receiverName = "$packageName$receiverName"
            }

            Pair(packageName, receiverName)
        } catch (e: Exception) {
            null
        }
    }
}