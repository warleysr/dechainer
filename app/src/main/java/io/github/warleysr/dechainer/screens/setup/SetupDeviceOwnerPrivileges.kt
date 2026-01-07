package io.github.warleysr.dechainer.screens.setup

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupDeviceOwnerPrivileges(viewModel: DeviceOwnerViewModel = viewModel()) {

    var shizukuInstalled by remember { mutableStateOf(viewModel.isShizukuInstalled()) }
    var shizukuRunning by remember { mutableStateOf(Shizuku.pingBinder()) }

    LaunchedEffect(true) {
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
        if (!shizukuInstalled) {
            ElevatedCard(Modifier.padding(8.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.shizuku_not_installed),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    TextButton(onClick = {
                        viewModel.installShizuku()
                    }) {
                        Row {
                            Icon(Icons.Outlined.InstallMobile, null)
                            Text(stringResource(R.string.install_shizuku))
                        }
                    }
                }
            }
        }
        else if (!shizukuRunning) {
            ElevatedCard(Modifier.padding(8.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.shizuku_not_running),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    TextButton(onClick = {
                        viewModel.openShizukuSetupGuide()
                    }) {
                        Row {
                            Icon(Icons.Outlined.Link, null)
                            Text(stringResource(R.string.setup_shizuku))
                        }
                    }
                }
            }
        }
        else if (!viewModel.isShizukuPermissionGranted()) {
            ElevatedCard(Modifier.padding(8.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.no_permission_shizuku),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    TextButton(onClick = {
                        Shizuku.requestPermission(911)
                    }) {
                        Row {
                            Icon(Icons.Outlined.QuestionMark, null)
                            Text(stringResource(R.string.ask_shizuku_permission))
                        }
                    }
                }
            }
        }
        else {
            val accounts = remember { mutableStateListOf<Pair<String, String>>() }
            accounts.addAll(viewModel.getAllAccountsViaShizuku())

            val extraUsers = remember { viewModel.getExtraUsersInfo() }

            if (accounts.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { accounts.clear() },
                    confirmButton = {
                        TextButton(onClick = {
                            val intent = Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            DechainerApplication.getInstance().startActivity(intent)
                        }) { Text(stringResource(R.string.remove_accounts)) }
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.no_account_allowed))
                            Spacer(Modifier.height(8.dp))
                            accounts.forEach {
                                val appName = viewModel.getAppNameFromAccountType(
                                    DechainerApplication.getInstance(), it.first)
                                Text(appName , fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
            else if (extraUsers.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { },
                    confirmButton = {
                        TextButton(onClick = {
                            val intent = Intent("android.settings.USER_SETTINGS").apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            DechainerApplication.getInstance().startActivity(intent)
                        }) { Text(stringResource(R.string.remove_users)) }
                    },
                    text = {
                        Column {
                            Text(stringResource(R.string.extra_users_description))
                            Spacer(Modifier.height(8.dp))
                            extraUsers.forEach {
                                Text(it, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }
            else if (!viewModel.isDeviceOwner()) {
                val currentDeviceOwner = viewModel.getCurrentDeviceOwner()
                if (currentDeviceOwner != null) {
                    var appName by remember { mutableStateOf("") }
                    var confirmRemove by remember { mutableStateOf(false) }

                    appName = try {
                        val packageManager = DechainerApplication.getInstance().packageManager
                        val appInfo = packageManager.getApplicationInfo(currentDeviceOwner.first, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (_: PackageManager.NameNotFoundException) {
                        ""
                    }
                    if (appName.isNotEmpty()) {
                        AlertDialog(
                            onDismissRequest = { appName = "" },
                            confirmButton = {
                                TextButton(
                                    enabled = confirmRemove,
                                    onClick = {
                                        appName = ""
                                        viewModel.removeCurrentDeviceOwner(currentDeviceOwner.second)
                                    }
                                ) { Text(stringResource(R.string.proceed)) }
                            },
                            text = {
                                Column {
                                    Text(stringResource(R.string.already_owner))
                                    Spacer(Modifier.height(8.dp))
                                    Text(appName, fontWeight = FontWeight.Bold)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable(onClick = { confirmRemove = !confirmRemove })
                                    ) {
                                        Checkbox(
                                            checked = confirmRemove,
                                            onCheckedChange = { confirmRemove = it }
                                        )
                                        Text(stringResource(R.string.confirm_remove_owner))
                                    }
                                }
                            }
                        )
                    }
                }
                else {
                    ElevatedCard(Modifier.padding(8.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.privileges_description),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(16.dp)
                            )
                            TextButton(onClick = {
                                viewModel.processDeviceOwnerPrivileges()
                            }) {
                                Row {
                                    Icon(Icons.Outlined.Adb, null)
                                    Text(stringResource(R.string.get_owner_privileges))
                                }
                            }
                        }
                    }
                }
            } else {
                ElevatedCard(Modifier.padding(8.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.remove_privileges_description),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp)
                        )
                        TextButton(onClick = {
                            viewModel.processDeviceOwnerPrivileges(true)
                        }) {
                            Row {
                                Icon(Icons.Outlined.DeleteForever, null)
                                Text(stringResource(R.string.remove_privileges))
                            }
                        }
                    }
                }
            }
        }
    }
}

