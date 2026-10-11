package io.github.warleysr.dechainer.viewmodels

import android.os.UserManager
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import io.github.warleysr.dechainer.data.DeviceAdmin
import io.github.warleysr.dechainer.data.SystemRestrictions

class RestrictionsViewModel : ViewModel() {
    private val dpm = DeviceAdmin.policyManager
    private val adminName = DeviceAdmin.component

    // Current state in the UI (draft)
    val draftRestrictions = mutableStateMapOf<String, Boolean>()
    
    // Actual state applied in the system
    val appliedRestrictions = mutableStateMapOf<String, Boolean>()

    val recommendedKeys get() = SystemRestrictions.recommendedKeys
    val otherKeys get() = SystemRestrictions.otherKeys
    private val allKeys get() = SystemRestrictions.allKeys

    fun resourceNameFor(key: String): String? = SystemRestrictions.resourceNameFor(key)

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

    fun hasPendingChanges(): Boolean = allKeys.any { draftRestrictions[it] != appliedRestrictions[it] }
}
