package io.github.warleysr.dechainer.screens.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Adb
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

@Composable
fun ConfigTab(viewModel: DeviceOwnerViewModel = viewModel()) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.device_owner_state)) },
                supportingContent = { Text(stringResource(R.string.device_owner_description)) },
                leadingContent = { Icon(Icons.Outlined.Adb, "") },
                trailingContent = {
                    val owner = viewModel.isDeviceOwner()
                    val badgeColor = if (owner) Color(26, 163, 63) else Color.Red
                    val badgeText = if (owner) stringResource(R.string.granted) else stringResource(R.string.not_granted)
                    Badge(containerColor = badgeColor, contentColor = Color.White) {
                        Text(badgeText, style = MaterialTheme.typography.bodyMedium)
                    }
                },
                modifier = Modifier.clickable(onClick = { viewModel.navigateTo("setup_device_owner") })
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.dns_settings)) },
                supportingContent = { Text(stringResource(R.string.dns_description)) },
                leadingContent = { Icon(Icons.Outlined.Dns, "") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.vpn_settings)) },
                supportingContent = { Text(stringResource(R.string.vpn_description)) },
                leadingContent = { Icon(Icons.Outlined.VpnKey, "") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.language_settings)) },
                supportingContent = { Text(stringResource(R.string.language_description)) },
                leadingContent = { Icon(Icons.Outlined.Language, "") }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}