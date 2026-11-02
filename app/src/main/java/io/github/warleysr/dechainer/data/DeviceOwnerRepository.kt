package io.github.warleysr.dechainer.data

import android.accounts.AccountManager
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.IInterface
import android.os.Process
import android.util.Log
import androidx.core.net.toUri
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.utils.ShizukuRunner
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import timber.log.Timber

/** Device-owner setup/state: Shizuku shell commands plus the [DevicePolicyManager] calls they unlock. */
object DeviceOwnerRepository {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    private val context = DechainerApplication.getInstance()
    private val dpm get() = DeviceAdmin.policyManager
    private val adminName get() = DeviceAdmin.component
    private val packageName = context.packageName
    private val serviceComponent = "$packageName/.DechainerAccessibilityService"

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(packageName)

    fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0) != null
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
        context.startActivity(intent)
    }

    fun openShizukuSetupGuide() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "https://shizuku.rikka.app/guide/setup/".toUri(),
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        context.startActivity(intent)
    }

    fun checkShizukuPermission(): Boolean {
        if (Shizuku.isPreV11()) return false
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun getUserId(): Int = Process.myUid() / 100000

    @SuppressLint("PrivateApi")
    fun <T> withShizukuDevicePolicyManager(action: (DevicePolicyManager) -> T): T {
        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val field = manager.javaClass.getDeclaredField("mService").apply { isAccessible = true }
        val originalService = field.get(manager) as IInterface
        val wrappedBinder = ShizukuBinderWrapper(originalService.asBinder())
        val stubClass = Class.forName("android.app.admin.IDevicePolicyManager\$Stub")
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        val proxiedService = asInterface.invoke(null, wrappedBinder)

        field.set(manager, proxiedService)
        return try {
            action(manager)
        } finally {
            try {
                field.set(manager, originalService)
            } catch (_: Throwable) {
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun DevicePolicyManager.forceUpdateUserSetupComplete(userId: Int) {
        val clazz = this::class.java
        runCatching {
            clazz.getDeclaredMethod("forceUpdateUserSetupComplete", Int::class.javaPrimitiveType!!)
                .also { it.isAccessible = true }.invoke(this, userId)
        }.onFailure {
            runCatching {
                clazz.getDeclaredMethod("forceUpdateUserSetupComplete")
                    .also { it.isAccessible = true }.invoke(this)
            }
        }
    }

    @SuppressLint("PrivateApi")
    private fun DevicePolicyManager.setActiveAdminCompat(receiver: ComponentName, refreshing: Boolean, userId: Int) {
        val clazz = this::class.java
        val method3 = clazz.methods.firstOrNull {
            it.name == "setActiveAdmin" && it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == ComponentName::class.java &&
                    it.parameterTypes[1] == java.lang.Boolean.TYPE &&
                    it.parameterTypes[2] == java.lang.Integer.TYPE
        } ?: clazz.declaredMethods.firstOrNull {
            it.name == "setActiveAdmin" && it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == ComponentName::class.java &&
                    it.parameterTypes[1] == java.lang.Boolean.TYPE &&
                    it.parameterTypes[2] == java.lang.Integer.TYPE
        }

        if (method3 != null) {
            method3.isAccessible = true
            method3.invoke(this, receiver, refreshing, userId)
        } else {
            val method2 = clazz.methods.firstOrNull {
                it.name == "setActiveAdmin" && it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == ComponentName::class.java &&
                        it.parameterTypes[1] == java.lang.Boolean.TYPE
            } ?: clazz.declaredMethods.firstOrNull {
                it.name == "setActiveAdmin" && it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == ComponentName::class.java &&
                        it.parameterTypes[1] == java.lang.Boolean.TYPE
            }
            method2?.isAccessible = true
            method2?.invoke(this, receiver, refreshing)
        }
    }

    @SuppressLint("PrivateApi")
    private fun DevicePolicyManager.setDeviceOwnerCompat(who: ComponentName, ownerName: String?, userId: Int): Boolean {
        val clazz = this::class.java

        val method2 = clazz.methods.firstOrNull {
            it.name == "setDeviceOwner" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == ComponentName::class.java &&
                    it.parameterTypes[1] == java.lang.Integer.TYPE
        } ?: clazz.declaredMethods.firstOrNull {
            it.name == "setDeviceOwner" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == ComponentName::class.java &&
                    it.parameterTypes[1] == java.lang.Integer.TYPE
        }
        if (method2 != null) {
            method2.isAccessible = true
            val res = method2.invoke(this, who, userId)
            return (res as? Boolean) ?: true
        }

        val method3 = clazz.methods.firstOrNull {
            it.name == "setDeviceOwner" && it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == ComponentName::class.java &&
                    it.parameterTypes[1] == String::class.java &&
                    it.parameterTypes[2] == java.lang.Integer.TYPE
        } ?: clazz.declaredMethods.firstOrNull {
            it.name == "setDeviceOwner" && it.parameterTypes.size == 3 &&
                    it.parameterTypes[0] == ComponentName::class.java &&
                    it.parameterTypes[1] == String::class.java &&
                    it.parameterTypes[2] == java.lang.Integer.TYPE
        }
        if (method3 != null) {
            method3.isAccessible = true
            val res = method3.invoke(this, who, ownerName, userId)
            return (res as? Boolean) ?: true
        }

        val methodLegacy = clazz.methods.firstOrNull {
            it.name == "setDeviceOwner" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == String::class.java &&
                    it.parameterTypes[1] == String::class.java
        } ?: clazz.declaredMethods.firstOrNull {
            it.name == "setDeviceOwner" && it.parameterTypes.size == 2 &&
                    it.parameterTypes[0] == String::class.java &&
                    it.parameterTypes[1] == String::class.java
        }
        if (methodLegacy != null) {
            methodLegacy.isAccessible = true
            val res = methodLegacy.invoke(this, who.packageName, ownerName)
            return (res as? Boolean) ?: true
        }

        return false
    }

    fun bypassUserSetupComplete(userId: Int = getUserId()): Boolean {
        val current = ShizukuRunner.exec("settings", "--user", "$userId", "get", "secure", "user_setup_complete")
        if (current?.output?.trim() != "1") return false
        val put = ShizukuRunner.exec("settings", "--user", "$userId", "put", "secure", "user_setup_complete", "0")
        if (put == null || put.exitCode != 0) return false
        refreshUserSetupComplete(userId)
        return true
    }

    fun restoreUserSetupComplete(userId: Int = getUserId()) {
        ShizukuRunner.exec("settings", "--user", "$userId", "put", "secure", "user_setup_complete", "1")
        refreshUserSetupComplete(userId)
    }

    fun refreshUserSetupComplete(userId: Int = getUserId()) {
        runCatching {
            withShizukuDevicePolicyManager { manager ->
                manager.forceUpdateUserSetupComplete(userId)
            }
        }.onFailure {
            Timber.w(it, "forceUpdateUserSetupComplete failed (may not be supported on this Android version)")
        }
    }

    fun restoreStuckUserSetupComplete() {
        if (!Shizuku.pingBinder() || !checkShizukuPermission()) return
        val userId = getUserId()
        val setup = ShizukuRunner.exec("settings", "--user", "$userId", "get", "secure", "user_setup_complete")?.output?.trim()
        if (setup != "0") return
        val provisioned = ShizukuRunner.exec("settings", "--user", "$userId", "get", "global", "device_provisioned")?.output?.trim()
        if (provisioned != "1") return
        restoreUserSetupComplete(userId)
    }

    /** Removes device-owner status, or requests it via Shizuku with setup bypass — returns the command output. */
    fun processDeviceOwnerPrivileges(remove: Boolean = false): String {
        if (remove && dpm.isAdminActive(adminName)) {
            dpm.clearDeviceOwnerApp(packageName)
            return ""
        }

        val userId = getUserId()
        val bypassed = bypassUserSetupComplete(userId)
        var commandOutput = ""

        try {
            var success = false
            runCatching {
                withShizukuDevicePolicyManager { wrappedDpm ->
                    wrappedDpm.setActiveAdminCompat(adminName, true, userId)
                    success = wrappedDpm.setDeviceOwnerCompat(adminName, null, userId)
                }
            }.onFailure {
                Timber.w(it, "Direct binder setDeviceOwner failed, trying shell command")
            }

            if (!success && !dpm.isDeviceOwnerApp(packageName)) {
                ShizukuRunner.command(
                    command = "dpm set-device-owner $packageName/.DechainerDeviceAdminReceiver",
                    listener = object : ShizukuRunner.CommandResultListener {
                        override fun onCommandResult(output: String, done: Boolean) {
                            println("Output: $output Done: $done")
                            if (output.isNotBlank()) {
                                commandOutput = output.trim()
                            }
                        }
                        override fun onCommandError(error: String) {
                            Log.e("Shizuku", error)
                            if (error.isNotBlank()) {
                                commandOutput = if (commandOutput.isNotBlank()) {
                                    "$commandOutput\n${error.trim()}"
                                } else {
                                    error.trim()
                                }
                            }
                        }
                    })
            }
        } finally {
            if (bypassed) {
                restoreUserSetupComplete(userId)
            }
        }

        val granted = dpm.isDeviceOwnerApp(packageName)
        if (granted) ColorFilterController.grantPermissionViaShizuku(context)
        return commandOutput
    }

    fun setPrivateDNS(host: String): Int {
        return dpm.setGlobalPrivateDnsModeSpecifiedHost(adminName, host)
    }

    fun getPrivateDNS(): String? {
        if (dpm.getGlobalPrivateDnsMode(adminName) != DevicePolicyManager.PRIVATE_DNS_MODE_PROVIDER_HOSTNAME)
            return null
        return dpm.getGlobalPrivateDnsHost(adminName)
    }

    fun getAllAccountsViaShizuku(): List<Pair<String, String>> {
        val accounts = mutableListOf<Pair<String, String>>()
        try {
            ShizukuRunner.command(
                command = "dumpsys account",
                listener = object : ShizukuRunner.CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        val regex = " {4}Account \\{name=(.*?), type=(.*?)\\}".toRegex()

                        output.lines().forEach { line ->
                            val match = regex.find(line)
                            if (match != null) {
                                val accountName = match.groupValues[1]
                                val accountType = match.groupValues[2]
                                accounts.add(Pair(accountType, accountName))
                            }
                        }

                        println("Output: \n$output")
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
                receiverName = "$packageName/$receiverName"
            }

            Pair(packageName, receiverName)
        } catch (e: Exception) {
            null
        }
    }

    fun getExtraUsersInfo(): List<String> {
        val users = mutableListOf<String>()

        ShizukuRunner.command(
            command = "pm list users",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")

                    val regex = Regex("""UserInfo\{(\d+):([^:]+):""")

                    val matches = regex.findAll(output)
                    for (match in matches) {
                        val id = match.groupValues[1].toInt()
                        val name = match.groupValues[2]

                        if (id != 0)
                            users.add(name)
                    }
                }

                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            })

        return users
    }

    fun getAccessibilityServices(): String {
        var services = ""
        ShizukuRunner.command(
            "settings get secure enabled_accessibility_services",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                    services = output
                }

                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            })
        return services.replace("\n", "")
    }

    fun isAccessibilityGranted(): Boolean {
        return getAccessibilityServices().contains(".DechainerAccessibilityService")
    }

    fun changeAccessibilityPermission(grant: Boolean) {
        var services = getAccessibilityServices()
        val isGranted = isAccessibilityGranted()

        if (grant && !isGranted) {
            if (services.isNotEmpty())
                services += ":"
            services += serviceComponent
        }
        else if (!grant && isGranted) {
            services = "'null'"
        }

        ShizukuRunner.command(
            "settings put secure enabled_accessibility_services $services",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                }

                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            })

        ShizukuRunner.command(
            "settings put secure accessibility_enabled 1",
            listener = object : ShizukuRunner.CommandResultListener {
                override fun onCommandResult(output: String, done: Boolean) {
                    println("Output: $output Done: $done")
                }

                override fun onCommandError(error: String) {
                    Log.e("Shizuku", error)
                }
            })

        if (grant) ColorFilterController.grantPermissionViaShizuku(context)
    }
}
