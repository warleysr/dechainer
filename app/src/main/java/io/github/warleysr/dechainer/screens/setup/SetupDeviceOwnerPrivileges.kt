package io.github.warleysr.dechainer.screens.setup

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

@Composable
fun SetupDeviceOwnerPrivileges(viewModel: DeviceOwnerViewModel = viewModel()) {
    var shizukuInstalled by remember { mutableStateOf(viewModel.isShizukuInstalled()) }
    var shizukuRunning by remember { mutableStateOf(Shizuku.pingBinder()) }

    LaunchedEffect(Unit) {
        while (true) {
            if (!shizukuInstalled)
                shizukuInstalled = viewModel.isShizukuInstalled()

            if (shizukuInstalled && !shizukuRunning)
                shizukuRunning = Shizuku.pingBinder()

            delay(500)
        }
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            !shizukuInstalled -> ShizukuNotInstalledCard(viewModel)
            !shizukuRunning -> ShizukuNotRunningCard(viewModel)
            !viewModel.isShizukuPermissionGranted() -> ShizukuNoPermissionCard()
            else -> DeviceOwnerSetupContent(viewModel)
        }
    }
}

@Composable
private fun ShizukuNotInstalledCard(viewModel: DeviceOwnerViewModel) {
    SetupStepCard(
        text = stringResource(R.string.shizuku_not_installed),
        buttonText = stringResource(R.string.install_shizuku),
        buttonIcon = Icons.Outlined.InstallMobile,
        onClick = { viewModel.installShizuku() }
    )
}

@Composable
private fun ShizukuNotRunningCard(viewModel: DeviceOwnerViewModel) {
    SetupStepCard(
        text = stringResource(R.string.shizuku_not_running),
        buttonText = stringResource(R.string.setup_shizuku),
        buttonIcon = Icons.Outlined.Link,
        onClick = { viewModel.openShizukuSetupGuide() }
    )
}

@Composable
private fun ShizukuNoPermissionCard() {
    SetupStepCard(
        text = stringResource(R.string.no_permission_shizuku),
        buttonText = stringResource(R.string.ask_shizuku_permission),
        buttonIcon = Icons.Outlined.QuestionMark,
        onClick = { Shizuku.requestPermission(911) }
    )
}

@Composable
private fun DeviceOwnerSetupContent(viewModel: DeviceOwnerViewModel) {
    val accounts = remember { mutableStateListOf<Pair<String, String>>() }
    LaunchedEffect(Unit) {
        accounts.addAll(viewModel.getAllAccountsViaShizuku())
    }

    val extraUsers = remember { viewModel.getExtraUsersInfo() }

    when {
        accounts.isNotEmpty() -> {
            AccountWarningDialog(
                accounts = accounts,
                onDismiss = { accounts.clear() },
                getAppName = { type -> 
                    viewModel.getAppNameFromAccountType(DechainerApplication.getInstance(), type) 
                }
            )
        }
        extraUsers.isNotEmpty() -> {
            UserWarningDialog(extraUsers = extraUsers)
        }
        !viewModel.isDeviceOwner() -> {
            val currentDeviceOwner = viewModel.getCurrentDeviceOwner()
            if (currentDeviceOwner != null) {
                ExistingOwnerDialog(
                    ownerPackage = currentDeviceOwner.first,
                    ownerReceiver = currentDeviceOwner.second,
                    onRemove = { viewModel.removeCurrentDeviceOwner(it) }
                )
            } else {
                SetupStepCard(
                    text = stringResource(R.string.privileges_description),
                    buttonText = stringResource(R.string.get_owner_privileges),
                    buttonIcon = Icons.Outlined.Adb,
                    onClick = { viewModel.processDeviceOwnerPrivileges() }
                )
            }
        }
        else -> {
            SetupStepCard(
                text = stringResource(R.string.remove_privileges_description),
                buttonText = stringResource(R.string.remove_privileges),
                buttonIcon = Icons.Outlined.DeleteForever,
                onClick = { viewModel.processDeviceOwnerPrivileges(true) }
            )
        }
    }
}
