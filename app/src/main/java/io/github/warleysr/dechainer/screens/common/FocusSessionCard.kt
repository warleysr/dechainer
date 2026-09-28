package io.github.warleysr.dechainer.screens.common

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.notifications.FocusNotifier

/** Asks for the notification permission after starting if the device owner couldn't grant it. */
@Composable
fun rememberFocusStarter(onStarted: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) FocusNotifier.showStatus(context) }

    return {
        FocusMode.startFocus(context)
        onStarted()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private class FocusAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Starting, resuming and skipping a break are free; pausing and ending (except while waiting) go
 * through [gate], so the caller must also show [RecoveryGateDialog].
 */
@Composable
fun FocusSessionCard(
    status: FocusMode.Status?,
    gate: RecoveryGate,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val context = LocalContext.current
    val start = rememberFocusStarter(onStarted = onChanged)

    val startAction = FocusAction(Icons.Outlined.PlayArrow, stringResource(R.string.focus_start), start)
    val actions = when {
        status == null -> listOf(startAction)
        else -> buildList {
            val isBreak = status.phase == FocusMode.Phase.SHORT_BREAK || status.phase == FocusMode.Phase.LONG_BREAK
            when {
                status.phase == FocusMode.Phase.WAITING -> add(startAction)
                status.paused -> add(FocusAction(Icons.Outlined.PlayArrow, stringResource(R.string.focus_resume)) {
                    FocusMode.resume(context)
                    onChanged()
                })
            }
            if (isBreak) add(FocusAction(Icons.Outlined.SkipNext, stringResource(R.string.focus_skip_break)) {
                FocusMode.skipBreak(context)
                onChanged()
            })
            if (status.phase != FocusMode.Phase.WAITING && !status.paused) {
                add(FocusAction(Icons.Outlined.Pause, stringResource(R.string.focus_pause)) {
                    gate.run {
                        FocusMode.pause(context)
                        onChanged()
                    }
                })
            }
            add(FocusAction(Icons.Outlined.Stop, stringResource(R.string.focus_end_session)) {
                val stop = {
                    FocusMode.stop(context)
                    onChanged()
                }
                if (status.phase == FocusMode.Phase.WAITING) stop() else gate.run(action = stop)
            })
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        if (compact) CompactContent(status, actions) else FullContent(status, actions)
    }
}

@Composable
private fun phaseTitle(status: FocusMode.Status): String {
    val title = when (status.phase) {
        FocusMode.Phase.FOCUS -> stringResource(
            R.string.focus_phase_focus_count,
            (status.completedFocus + 1).coerceAtMost(status.cyclesBeforeLongBreak),
            status.cyclesBeforeLongBreak
        )
        FocusMode.Phase.SHORT_BREAK -> stringResource(R.string.focus_phase_short_break)
        FocusMode.Phase.LONG_BREAK -> stringResource(R.string.focus_phase_long_break)
        FocusMode.Phase.WAITING -> stringResource(R.string.focus_phase_waiting)
    }
    return if (status.paused) stringResource(R.string.focus_phase_paused, title) else title
}

@Composable
private fun CountdownRing(status: FocusMode.Status, size: Dp, strokeWidth: Dp, textStyle: androidx.compose.ui.text.TextStyle) {
    val fraction = if (status.phaseDurationMillis > 0)
        status.remainingMillis.toFloat() / status.phaseDurationMillis else 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        CircularProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            strokeWidth = strokeWidth,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.fillMaxSize()
        )
        Text(formatCountdown(status.remainingMillis), style = textStyle, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FullContent(status: FocusMode.Status?, actions: List<FocusAction>) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            if (status == null) stringResource(R.string.focus_status_idle) else phaseTitle(status),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))

        if (status != null) {
            if (status.phase == FocusMode.Phase.WAITING) {
                Text(
                    stringResource(R.string.focus_waiting_text),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            } else {
                CountdownRing(status, 180.dp, 8.dp, MaterialTheme.typography.displaySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    R.string.focus_cycle_progress,
                    status.completedFocus.coerceAtMost(status.cyclesBeforeLongBreak),
                    status.cyclesBeforeLongBreak
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
        }

        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth()
        ) {
            actions.forEachIndexed { index, action ->
                val content: @Composable RowScope.() -> Unit = {
                    Icon(action.icon, null)
                    Spacer(Modifier.width(8.dp))
                    Text(action.label)
                }
                if (index == 0) Button(onClick = action.onClick, content = content)
                else OutlinedButton(onClick = action.onClick, content = content)
            }
        }

        if (status != null && status.phase != FocusMode.Phase.WAITING) {
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.focus_end_requires_code),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CompactContent(status: FocusMode.Status?, actions: List<FocusAction>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (status == null || status.phase == FocusMode.Phase.WAITING) {
            Icon(
                Icons.Outlined.Timer, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        } else {
            CountdownRing(status, 76.dp, 5.dp, MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (status == null) stringResource(R.string.focus_mode) else phaseTitle(status),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    status == null -> stringResource(
                        R.string.focus_start_subtitle, FocusMode.getFocusMinutes(LocalContext.current)
                    )
                    else -> stringResource(
                        R.string.focus_cycle_progress,
                        status.completedFocus.coerceAtMost(status.cyclesBeforeLongBreak),
                        status.cyclesBeforeLongBreak
                    )
                },
                style = MaterialTheme.typography.bodySmall
            )
        }

        actions.forEachIndexed { index, action ->
            if (index == 0) {
                FilledIconButton(onClick = action.onClick) { Icon(action.icon, action.label) }
            } else {
                FilledTonalIconButton(onClick = action.onClick) { Icon(action.icon, action.label) }
            }
        }
    }
}

private fun formatCountdown(millis: Long): String {
    // Rounded up, so the display reads 00:00 only once the phase is actually over.
    val totalSeconds = (millis + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
