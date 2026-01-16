package io.github.warleysr.dechainer.screens.setup

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.DechainerApplication
import io.github.warleysr.dechainer.R

@Composable
fun SetupStepCard(
    text: String,
    buttonText: String,
    buttonIcon: ImageVector,
    onClick: () -> Unit
) {
    ElevatedCard(Modifier.padding(8.dp)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            TextButton(onClick = onClick) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(buttonIcon, null)
                    Spacer(Modifier.width(8.dp))
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
fun AccountWarningDialog(
    accounts: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    getAppName: (String) -> String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
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
                    Text(getAppName(it.first), fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
fun UserWarningDialog(extraUsers: List<String>) {
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

@Composable
fun ExistingOwnerDialog(
    ownerPackage: String,
    ownerReceiver: String,
    onRemove: (String) -> Unit
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val context = DechainerApplication.getInstance()
    val appName = remember(ownerPackage) {
        try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(ownerPackage, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            ownerPackage
        }
    }

    AlertDialog(
        onDismissRequest = { },
        confirmButton = {
            TextButton(
                enabled = confirmRemove,
                onClick = { onRemove(ownerReceiver) }
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
