package io.github.warleysr.dechainer.viewmodels

import android.content.Context
import android.content.RestrictionEntry
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.data.AppRepository
import io.github.warleysr.dechainer.data.DeviceOwnerRepository
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

class DeviceOwnerViewModel : ViewModel() {

    private var shizukuPermission = mutableStateOf(
        Shizuku.pingBinder() && DeviceOwnerRepository.checkShizukuPermission()
    )
    private var isDeviceOwner = mutableStateOf(DeviceOwnerRepository.isDeviceOwner())

    private val requestResultPermissionListener =
        OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            shizukuPermission.value = grantResult == PackageManager.PERMISSION_GRANTED
        }

    fun addShizukuListener() {
        Shizuku.addRequestPermissionResultListener(requestResultPermissionListener)
    }

    fun removeShizukuListener() {
        Shizuku.removeRequestPermissionResultListener(requestResultPermissionListener)
    }

    fun isShizukuPermissionGranted() = shizukuPermission.value

    fun isDeviceOwner() = isDeviceOwner.value

    fun isShizukuInstalled() = DeviceOwnerRepository.isShizukuInstalled()

    fun installShizuku() = DeviceOwnerRepository.installShizuku()

    fun openShizukuSetupGuide() = DeviceOwnerRepository.openShizukuSetupGuide()

    fun removeDeviceOwner() = processDeviceOwnerPrivileges(remove = true)

    fun processDeviceOwnerPrivileges(remove: Boolean = false) {
        isDeviceOwner.value = DeviceOwnerRepository.processDeviceOwnerPrivileges(remove)
    }

    fun setPrivateDNS(host: String): Int = DeviceOwnerRepository.setPrivateDNS(host)

    fun getPrivateDNS(): String? = DeviceOwnerRepository.getPrivateDNS()

    fun getAllAccountsViaShizuku(): List<Pair<String, String>> = DeviceOwnerRepository.getAllAccountsViaShizuku()

    fun getAppNameFromAccountType(context: Context, accountType: String): String =
        DeviceOwnerRepository.getAppNameFromAccountType(context, accountType)

    fun getCurrentDeviceOwner(): Pair<String, String>? = DeviceOwnerRepository.getCurrentDeviceOwner()

    fun getExtraUsersInfo(): List<String> = DeviceOwnerRepository.getExtraUsersInfo()

    fun changeAccessibilityPermission(grant: Boolean) = DeviceOwnerRepository.changeAccessibilityPermission(grant)

    fun getApplicationRestrictions(packageName: String): Bundle =
        AppRepository.getApplicationRestrictions(packageName)

    fun setApplicationRestrictions(packageName: String, restrictions: Bundle) =
        AppRepository.setApplicationRestrictions(packageName, restrictions)

    fun getAvailableRestrictions(packageName: String): List<RestrictionEntry> =
        AppRepository.getAvailableRestrictions(packageName)
}
