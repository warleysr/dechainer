package io.github.warleysr.dechainer.viewmodels

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver

class RestrictionsViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminName = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

    // Current state in the UI (draft)
    val draftRestrictions = mutableStateMapOf<String, Boolean>()
    
    // Actual state applied in the system
    val appliedRestrictions = mutableStateMapOf<String, Boolean>()

    private val restrictionKeys = listOf(
        UserManager.DISALLOW_CONFIG_VPN,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
        UserManager.DISALLOW_FACTORY_RESET
    )

    init {
        loadRestrictions()
    }

    fun loadRestrictions() {
        val currentRestrictions = dpm.getUserRestrictions(adminName)
        restrictionKeys.forEach { key ->
            val isEnabled = currentRestrictions.getBoolean(key)
            draftRestrictions[key] = isEnabled
            appliedRestrictions[key] = isEnabled
        }
    }

    fun toggleDraft(key: String, enabled: Boolean) {
        draftRestrictions[key] = enabled
    }

    fun toggleAllDrafts(enabled: Boolean) {
        restrictionKeys.forEach { key ->
            draftRestrictions[key] = enabled
        }
    }

    fun applyChanges() {
        restrictionKeys.forEach { key ->
            val shouldEnable = draftRestrictions[key] ?: false
            if (shouldEnable) {
                dpm.addUserRestriction(adminName, key)
            } else {
                dpm.clearUserRestriction(adminName, key)
            }
        }
        loadRestrictions() // Refresh states
    }

    fun isAllDraftsEnabled(): Boolean = restrictionKeys.all { draftRestrictions[it] == true }
}
