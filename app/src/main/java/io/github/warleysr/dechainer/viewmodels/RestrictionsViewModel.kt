package io.github.warleysr.dechainer.viewmodels

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.DechainerDeviceAdminReceiver
import kotlin.collections.forEach

class RestrictionsViewModel : ViewModel() {
    private val context = DechainerApplication.getInstance()
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminName = ComponentName(context, DechainerDeviceAdminReceiver::class.java)

    // Current state in the UI (draft)
    val draftRestrictions = mutableStateMapOf<String, Boolean>()
    
    // Actual state applied in the system
    val appliedRestrictions = mutableStateMapOf<String, Boolean>()

    val recommendedKeys = listOf(
        UserManager.DISALLOW_CONFIG_VPN,
        UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
        UserManager.DISALLOW_FACTORY_RESET,
    )

    val otherKeys = UserManager::class.java.fields
        .filter { field ->

            java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                    java.lang.reflect.Modifier.isFinal(field.modifiers) &&
                    (field.name.startsWith("DISALLOW_") || field.name.startsWith("ALLOW_"))
                    && !recommendedKeys.contains(field.get(null))
        }
        .map { it.get(null) as String }
        .sorted()

    private val allKeys = (recommendedKeys + otherKeys).distinct()

    init {
        loadRestrictions()
    }

    fun loadRestrictions() {
        if (!dpm.isDeviceOwnerApp(adminName.packageName)) return
        val currentRestrictions = dpm.getUserRestrictions(adminName)
        allKeys.forEach { key ->
            val isEnabled = currentRestrictions.getBoolean(key as String?)
            draftRestrictions[key] = isEnabled
            appliedRestrictions[key] = isEnabled
        }
    }

    fun toggleDraft(key: String, enabled: Boolean) {
        draftRestrictions[key] = enabled
    }

    fun toggleAllDrafts(keys: List<String>, enabled: Boolean) {
        keys.forEach { key ->
            draftRestrictions[key] = enabled
        }
    }

    fun applyChanges() {
        allKeys.forEach { key ->
            val shouldEnable = draftRestrictions[key] ?: false
            val currentlyAppliedRestrictions = dpm.getUserRestrictions(adminName)
            if (shouldEnable) {
                dpm.addUserRestriction(adminName, key as String?)
            } else if (currentlyAppliedRestrictions.containsKey(key)) {
                dpm.clearUserRestriction(adminName, key as String?)
            }
        }
        loadRestrictions()
    }

    fun isAllDraftsEnabled(keys: List<String>): Boolean = keys.all { draftRestrictions[it] == true }
}
