package io.github.warleysr.dechainer.data

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver

/** Shared admin component + [DevicePolicyManager] handle, used by every class that talks to device policy. */
object DeviceAdmin {
    val component: ComponentName = ComponentName(
        DechainerApplication.getInstance(), DechainerDeviceAdminReceiver::class.java
    )

    val policyManager: DevicePolicyManager
        get() = DechainerApplication.getInstance()
            .getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
}
