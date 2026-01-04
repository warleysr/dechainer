package io.github.warleysr.dechainer.viewmodels

import android.accounts.AccountManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Context.DEVICE_POLICY_SERVICE
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.utils.ShizukuRunner
import rikka.shizuku.Shizuku

class DeviceOwnerViewModel() : ViewModel() {

    private val dpm = DechainerApplication.getInstance().getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val packageName = DechainerApplication.getInstance().packageName

    private var selectedTabState = mutableStateOf("restrictions")

    fun selectedTab() = selectedTabState.value

    fun navigateTo(screen: String) { selectedTabState.value = screen }

    fun isDeviceOwner() : Boolean {
        return dpm.isDeviceOwnerApp(packageName)
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

    fun processDeviceOwnerPrivileges(context: Context, remove: Boolean) {
        val command = if (remove) "remove-active-admin" else "set-device-owner"
        ShizukuRunner.command(
            command = "dpm $command ${context.packageName}/.DechainerDeviceAdminReceiver",
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
}