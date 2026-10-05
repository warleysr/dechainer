package io.github.warleysr.dechainer.activities

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.ApkUpdateInstaller
import io.github.warleysr.dechainer.data.ApkUpdateInstaller.Inspection
import io.github.warleysr.dechainer.ui.theme.DechainerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApkUpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri == null) {
            finish()
            return
        }

        setContent {
            DechainerTheme {
                ApkUpdateDialog(uri = uri, onFinish = { finish() })
            }
        }
    }
}

@Composable
private fun ApkUpdateDialog(uri: Uri, onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inspection by remember { mutableStateOf<Inspection?>(null) }
    var installing by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        inspection = withContext(Dispatchers.IO) {
            ApkUpdateInstaller.inspect(context.applicationContext, uri)
        }
    }

    val current = inspection
    if (current == null || installing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.apk_update_title)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(if (installing) R.string.apk_update_installing else R.string.apk_update_checking))
                }
            },
            confirmButton = {}
        )
        return
    }

    if (current !is Inspection.Update) {
        val message = when (current) {
            Inspection.NewApp -> R.string.apk_update_new_app
            Inspection.Downgrade -> R.string.apk_update_downgrade
            else -> R.string.apk_update_invalid
        }
        AlertDialog(
            onDismissRequest = onFinish,
            title = { Text(stringResource(R.string.apk_update_title)) },
            text = { Text(stringResource(message)) },
            confirmButton = {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.close)) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = {
            current.file.delete()
            onFinish()
        },
        icon = if (current.icon != null) {
            {
                Image(
                    bitmap = remember(current.icon) { current.icon.toBitmap().asImageBitmap() },
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        } else null,
        title = { Text(stringResource(R.string.apk_update_confirm_title, current.label)) },
        text = {
            Column {
                Text(stringResource(R.string.apk_update_installed_version, current.installedVersion))
                Text(stringResource(R.string.apk_update_new_version, current.newVersion))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                installing = true
                scope.launch {
                    val started = withContext(Dispatchers.IO) {
                        ApkUpdateInstaller.install(context.applicationContext, current)
                    }
                    if (!started) {
                        Toast.makeText(context, R.string.apk_update_failed, Toast.LENGTH_LONG).show()
                    }
                    onFinish()
                }
            }) {
                Text(stringResource(R.string.apk_update_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                current.file.delete()
                onFinish()
            }) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
