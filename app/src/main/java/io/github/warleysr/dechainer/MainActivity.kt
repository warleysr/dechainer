package io.github.warleysr.dechainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.screens.setup.SetupDeviceOwnerPrivileges
import io.github.warleysr.dechainer.screens.setup.SetupRecovery
import io.github.warleysr.dechainer.screens.tabs.ActivityBlockerScreen
import io.github.warleysr.dechainer.screens.tabs.AppsTab
import io.github.warleysr.dechainer.screens.tabs.BrowserRestrictionsScreen
import io.github.warleysr.dechainer.screens.tabs.ConfigTab
import io.github.warleysr.dechainer.screens.tabs.RestrictionsTab
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.ui.theme.DechainerTheme
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DechainerTheme {
                val viewModel: DeviceOwnerViewModel = viewModel()
                viewModel.addShizukuListener()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name), color = MaterialTheme.colorScheme.primary) }) },
                    bottomBar = {
                        val tabs = listOf(
                            Pair("restrictions", stringResource(R.string.restrictions)),
                            Pair("apps", stringResource(R.string.apps)),
                            Pair("config", stringResource(R.string.config))
                        )

                        NavigationBar {
                            tabs.forEach { pair ->
                                NavigationBarItem(
                                    selected = viewModel.selectedTab() == pair.first,
                                    onClick = { viewModel.navigateTo(pair.first) },
                                    label = { Text(pair.second) },
                                    icon = {
                                        Icon(
                                            when (pair.first) {
                                                "restrictions" -> Icons.Outlined.Block
                                                "apps" -> Icons.Default.AppBlocking
                                                else -> Icons.Outlined.Settings
                                            }, contentDescription = null
                                        )
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->

                    if (!(SecurityManager.isRecoveryPhraseSet(this)))
                        SetupRecovery(innerPadding)
                    else {
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (viewModel.selectedTab()) {
                                "restrictions" -> RestrictionsTab()
                                "apps" -> AppsTab()
                                "config" -> ConfigTab()
                                "browser_restrictions" -> BrowserRestrictionsScreen()
                                "activity_blocker" -> ActivityBlockerScreen()
                                "setup_device_owner" -> SetupDeviceOwnerPrivileges()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        setContent {
            val viewModel: DeviceOwnerViewModel = viewModel()
            viewModel.removeShizukuListener()
        }
    }
}