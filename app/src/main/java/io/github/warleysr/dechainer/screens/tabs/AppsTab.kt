package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.screens.common.NoDeviceOwnerPrivileges
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

@Composable
fun AppsTab(viewModel: DeviceOwnerViewModel = viewModel()) {
    if (!viewModel.isDeviceOwner())
        NoDeviceOwnerPrivileges(viewModel)
    else {

    }
}