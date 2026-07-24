package io.github.warleysr.dechainer.support

import io.github.warleysr.dechainer.viewmodels.BlockedWordsViewModel

/**
 * Single construction point for the app's ViewModels in tests.
 *
 * Routing every test through this factory means that changing these few lines
 * instead of every test that builds a ViewModel.
 */
object Fixtures {

    fun blockedWordsViewModel() = BlockedWordsViewModel()
}
