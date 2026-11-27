package io.github.warleysr.dechainer.screens.common

import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.ApkUpdateInstaller
import io.github.warleysr.dechainer.data.ApkUpdateInstaller.Inspection
import io.github.warleysr.dechainer.data.DeviceOwnerRepository
import io.github.warleysr.dechainer.data.GitHubUpdater
import io.github.warleysr.dechainer.data.GitHubUpdater.CheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Checks the APK at [uri] and, when it updates an installed app, asks before installing it. */
@Composable
fun ApkUpdateDialog(uri: Uri, onFinish: () -> Unit) {
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

/** Explains what updating through an APK does before the file picker opens. */
@Composable
fun ApkUpdateIntroDialog(
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
    onCheckGitHub: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text(stringResource(R.string.apk_update_intro_title)) },
        text = {
            Column {
                Text(stringResource(R.string.apk_update_intro_text))
                if (onCheckGitHub != null) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onCheckGitHub) {
                        Text(stringResource(R.string.app_update_check_github))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSelect) { Text(stringResource(R.string.apk_update_select)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private sealed interface GitHubUpdateUiState {
    data object Checking : GitHubUpdateUiState
    data class UpToDate(val currentVersion: String) : GitHubUpdateUiState
    data class Available(val release: GitHubUpdater.GitHubRelease) : GitHubUpdateUiState
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : GitHubUpdateUiState
    data object Installing : GitHubUpdateUiState
    data class Failed(val message: String) : GitHubUpdateUiState
}

/** Checks the latest GitHub release for Déchaîner and lets the user download and install it. */
@Composable
fun GitHubUpdateDialog(
    onDismiss: () -> Unit,
    preloadedRelease: GitHubUpdater.GitHubRelease? = null,
    isAutoPrompt: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var retryTick by remember { mutableIntStateOf(0) }
    var state by remember(preloadedRelease) {
        mutableStateOf<GitHubUpdateUiState>(
            if (preloadedRelease != null) GitHubUpdateUiState.Available(preloadedRelease)
            else GitHubUpdateUiState.Checking
        )
    }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(retryTick, preloadedRelease) {
        if (preloadedRelease != null && retryTick == 0) return@LaunchedEffect
        state = GitHubUpdateUiState.Checking
        val result = withContext(Dispatchers.IO) { GitHubUpdater.checkForUpdate() }
        state = when (result) {
            is CheckResult.UpToDate -> GitHubUpdateUiState.UpToDate(result.currentVersion)
            is CheckResult.UpdateAvailable -> GitHubUpdateUiState.Available(result.release)
            CheckResult.NoApkAsset -> GitHubUpdateUiState.Failed(context.getString(R.string.app_update_no_apk))
            is CheckResult.Error -> GitHubUpdateUiState.Failed(
                context.getString(R.string.app_update_error, result.message ?: "unknown")
            )
        }
    }

    when (val current = state) {
        GitHubUpdateUiState.Checking, GitHubUpdateUiState.Installing -> {
            AlertDialog(
                onDismissRequest = {},
                icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                title = { Text(stringResource(R.string.app_update_title)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                        Text(
                            stringResource(
                                if (current is GitHubUpdateUiState.Installing) R.string.apk_update_installing
                                else R.string.app_update_checking
                            )
                        )
                    }
                },
                confirmButton = {}
            )
        }

        is GitHubUpdateUiState.Downloading -> {
            AlertDialog(
                onDismissRequest = {
                    downloadJob?.cancel()
                    onDismiss()
                },
                icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                title = { Text(stringResource(R.string.app_update_title)) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.app_update_downloading))
                        Spacer(Modifier.height(12.dp))
                        if (current.totalBytes > 0L) {
                            val progress = (current.downloadedBytes.toFloat() / current.totalBytes.toFloat())
                                .coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${Formatter.formatShortFileSize(context, current.downloadedBytes)} / " +
                                    Formatter.formatShortFileSize(context, current.totalBytes),
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = {
                        downloadJob?.cancel()
                        onDismiss()
                    }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }

        is GitHubUpdateUiState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                title = { Text(stringResource(R.string.app_update_title)) },
                text = { Text(stringResource(R.string.app_update_up_to_date, current.currentVersion)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            )
        }

        is GitHubUpdateUiState.Failed -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                title = { Text(stringResource(R.string.app_update_title)) },
                text = { Text(current.message) },
                confirmButton = {
                    TextButton(onClick = { retryTick++ }) {
                        Text(stringResource(R.string.app_update_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            )
        }

        is GitHubUpdateUiState.Available -> {
            val release = current.release
            val isDeviceOwner = remember { DeviceOwnerRepository.isDeviceOwner() }
            var disableAutoUpdates by remember { mutableStateOf(false) }

            fun persistAutoCheckChoice() {
                if (isAutoPrompt && disableAutoUpdates) {
                    GitHubUpdater.setAutoCheckEnabled(context, false)
                }
            }

            AlertDialog(
                onDismissRequest = {
                    persistAutoCheckChoice()
                    onDismiss()
                },
                icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
                title = { Text(stringResource(R.string.app_update_available, release.version)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (isAutoPrompt) {
                            Text(
                                text = stringResource(R.string.app_update_prompt_question),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            stringResource(
                                R.string.app_update_version_transition,
                                io.github.warleysr.dechainer.BuildConfig.VERSION_NAME,
                                release.version
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                        if (release.sizeBytes > 0L) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(
                                    R.string.app_update_size,
                                    Formatter.formatShortFileSize(context, release.sizeBytes)
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (release.releaseNotes.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            MarkdownText(
                                markdown = release.releaseNotes,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (isAutoPrompt) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { disableAutoUpdates = !disableAutoUpdates }
                                    .padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = disableAutoUpdates,
                                    onCheckedChange = { disableAutoUpdates = it }
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.app_update_dont_ask_again),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (isDeviceOwner) {
                        TextButton(onClick = {
                            persistAutoCheckChoice()
                            downloadJob = scope.launch {
                                val appCtx = context.applicationContext
                                var lastUiUpdateMs = 0L
                                state = GitHubUpdateUiState.Downloading(0L, release.sizeBytes)
                                val file = GitHubUpdater.downloadApk(appCtx, release.apkUrl) { done, total ->
                                    val now = android.os.SystemClock.elapsedRealtime()
                                    if (now - lastUiUpdateMs >= 100L || (total > 0L && done >= total)) {
                                        lastUiUpdateMs = now
                                        scope.launch(Dispatchers.Main) {
                                            if (state is GitHubUpdateUiState.Downloading) {
                                                state = GitHubUpdateUiState.Downloading(done, total)
                                            }
                                        }
                                    }
                                }
                                if (file == null) {
                                    state = GitHubUpdateUiState.Failed(context.getString(R.string.app_update_download_error))
                                    return@launch
                                }

                                state = GitHubUpdateUiState.Installing
                                val outcome = withContext(Dispatchers.IO) {
                                    if (!ApkUpdateInstaller.hasMatchingSignature(appCtx, file)) {
                                        file.delete()
                                        return@withContext R.string.apk_update_signature_mismatch
                                    }
                                    val inspection = ApkUpdateInstaller.inspectFile(appCtx, file)
                                    if (inspection !is Inspection.Update ||
                                        inspection.packageName != appCtx.packageName ||
                                        inspection.isDowngrade
                                    ) {
                                        if (inspection is Inspection.Update) inspection.file.delete()
                                        else file.delete()
                                        return@withContext R.string.apk_update_failed
                                    }
                                    if (!ApkUpdateInstaller.install(appCtx, inspection)) {
                                        return@withContext R.string.apk_update_failed
                                    }
                                    null
                                }
                                if (outcome != null) {
                                    state = GitHubUpdateUiState.Failed(context.getString(outcome))
                                } else {
                                    onDismiss()
                                }
                            }
                        }) {
                            Text(
                                stringResource(
                                    if (isAutoPrompt) R.string.app_update_now
                                    else R.string.app_update_download_and_install
                                )
                            )
                        }
                    } else if (release.htmlUrl.startsWith("https://")) {
                        TextButton(onClick = {
                            persistAutoCheckChoice()
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, release.htmlUrl.toUri()))
                            }
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.app_update_open_release))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        persistAutoCheckChoice()
                        if (isAutoPrompt) {
                            GitHubUpdater.ignoreVersion(context, release.version)
                        }
                        onDismiss()
                    }) {
                        Text(
                            stringResource(
                                if (isAutoPrompt) R.string.app_update_ignore_version
                                else R.string.app_update_later
                            )
                        )
                    }
                }
            )
        }
    }
}
