package io.github.warleysr.dechainer.support

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.os.Bundle
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowDevicePolicyManager

/**
 * A [DevicePolicyManager] shadow that makes user restrictions round-trip.
 *
 * The stock [ShadowDevicePolicyManager] never implements `getUserRestrictions(ComponentName)` — the real
 * framework method returns an empty `Bundle` — so nothing written by `addUserRestriction` is ever read
 * back, and its `add`/`clearUserRestriction` route through `enforceActiveAdmin`, which throws when no
 * active admin is registered. Both make it impossible to observe
 * [io.github.warleysr.dechainer.viewmodels.RestrictionsViewModel]'s apply/load cycle.
 *
 * This shadow keeps the enabled restriction keys in its own [Bundle]: `addUserRestriction` sets a key,
 * `clearUserRestriction` drops it, and `getUserRestrictions` returns a copy — so a test can grant a
 * device owner, apply a draft, and read the applied state back exactly as the view model does. Register
 * it with `@Config(shadows = [ShadowDechainerDpm::class])`; device-owner setup still uses the inherited
 * `setDeviceOwner`.
 */
@Implements(DevicePolicyManager::class)
class ShadowDechainerDpm : ShadowDevicePolicyManager() {

    private val userRestrictions = Bundle()

    @Implementation
    fun getUserRestrictions(admin: ComponentName?): Bundle = Bundle(userRestrictions)

    @Implementation
    override fun addUserRestriction(admin: ComponentName?, key: String?) {
        userRestrictions.putBoolean(key, true)
    }

    @Implementation
    override fun clearUserRestriction(admin: ComponentName?, key: String?) {
        userRestrictions.remove(key)
    }
}
