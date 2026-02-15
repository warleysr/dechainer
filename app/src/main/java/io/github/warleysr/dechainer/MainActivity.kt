package io.github.warleysr.dechainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppBlocking
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.warleysr.dechainer.screens.setup.SetupDeviceOwnerPrivileges
import io.github.warleysr.dechainer.screens.setup.SetupRecovery
import io.github.warleysr.dechainer.screens.tabs.*
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.ui.theme.DechainerTheme
import io.github.warleysr.dechainer.viewmodels.DeviceOwnerViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DechainerTheme {
                val viewModel: DeviceOwnerViewModel = viewModel()
                viewModel.addShizukuListener()

                var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        currentTime = System.currentTimeMillis()
                        delay(1000)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            actions = {
                                if (SecurityManager.isSessionActive()) {
                                    val remaining = SecurityManager.sessionEndTime - currentTime
                                    val minutes = (remaining / 1000) / 60
                                    val seconds = (remaining / 1000) % 60
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.LockClock, null, modifier = Modifier.padding(end = 4.dp))
                                        Text(
                                            text = "%02d:%02d".format(minutes, seconds),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                        IconButton(onClick = { SecurityManager.endSession() }) {
                                            Icon(Icons.Outlined.Logout, null)
                                        }
                                    }
                                }
                            }
                        )
                    },
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
                                "setup_device_owner" -> SetupDeviceOwnerPrivileges()
                                "activity_blocker" -> ActivityBlockerScreen()
                                "browser_restrictions" -> BrowserRestrictionsScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        SecurityManager.endSession()
    }
}
