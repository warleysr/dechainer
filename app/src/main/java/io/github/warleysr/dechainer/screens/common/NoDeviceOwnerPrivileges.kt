package io.github.warleysr.dechainer.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

@Composable
fun NoDeviceOwnerPrivileges(viewModel: DeviceOwnerViewModel) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        ElevatedCard(Modifier.padding(8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.not_device_owner),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
                TextButton(onClick = {
                    viewModel.navigateTo("config")
                }) {
                    Row {
                        Icon(Icons.Outlined.Settings, null)
                        Text(stringResource(R.string.config_device_owner))
                    }
                }
            }
        }
    }
}