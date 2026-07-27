package io.github.warleysr.dechainer.support

import io.github.warleysr.dechainer.viewmodels.ActivityBlockerViewModel
import io.github.warleysr.dechainer.viewmodels.AppsViewModel
import io.github.warleysr.dechainer.viewmodels.BlockedWordsViewModel
import io.github.warleysr.dechainer.viewmodels.BrowserRestrictionsViewModel
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import io.github.warleysr.dechainer.viewmodels.RestrictionsViewModel

/**
 * Single construction point for the app's ViewModels in tests.
 *
 * Routing every test through this factory means that changing these few lines
 * instead of every test that builds a ViewModel.
 */
object Fixtures {

    fun blockedWordsViewModel() = BlockedWordsViewModel()

    fun activityBlockerViewModel() = ActivityBlockerViewModel()

    fun appsViewModel() = AppsViewModel()

    fun browserRestrictionsViewModel() = BrowserRestrictionsViewModel()

    fun restrictionsViewModel() = RestrictionsViewModel()

    fun deviceOwnerViewModel() = DeviceOwnerViewModel()
}
