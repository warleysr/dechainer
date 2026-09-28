package io.github.warleysr.dechainer.activities

import androidx.activity.compose.BackHandler
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationResult
import androidx.biometric.AuthenticationResultCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.compose.rememberAuthenticationLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.data.DeviceOwnerRepository
import io.github.warleysr.dechainer.data.FocusMode
import io.github.warleysr.dechainer.screens.challenges.ChallengeScaffold
import io.github.warleysr.dechainer.screens.challenges.MathChallenge
import io.github.warleysr.dechainer.screens.challenges.ReadingChallenge
import io.github.warleysr.dechainer.screens.challenges.TetrisChallenge
import io.github.warleysr.dechainer.screens.challenges.WordChallenge
import io.github.warleysr.dechainer.screens.common.UsageLimitsOverview
import io.github.warleysr.dechainer.screens.common.FocusSessionCard
import io.github.warleysr.dechainer.screens.common.RecoveryGateDialog
import io.github.warleysr.dechainer.screens.common.rememberRecoveryGate
import io.github.warleysr.dechainer.security.SecurityManager
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Entry gate of the app. Authentication is deliberately *not* triggered on open: the panic button
 * has to stay one tap away at all times, so the biometric prompt only runs when the user actually
 * asks to get in. Order is therefore: open -> "access app" -> biometrics -> challenge (if any).
 */
@Composable
fun LockScreen(onAuthenticated: () -> Unit) {
    var challenges by remember { mutableStateOf<List<SecurityManager.ChallengeType>?>(null) }
    var challengeIndex by remember { mutableIntStateOf(0) }
    var gaveUp by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var impulseRemaining by remember { mutableLongStateOf(-1L) }
    var showingLimits by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var focusStatus by remember { mutableStateOf(FocusMode.getStatus(context)) }
    val recoveryGate = rememberRecoveryGate()

    fun proceedAfterAuthentication() {
        authError = null
        gaveUp = false
        val required = SecurityManager.getAccessChallenges(context)
        if (required.isEmpty()) {
            onAuthenticated()
        } else {
            challengeIndex = 0
            challenges = required
        }
    }

    val launcher = rememberAuthenticationLauncher(
        resultCallback = object : AuthenticationResultCallback {
            override fun onAuthResult(result: AuthenticationResult) {
                when (result) {
                    is AuthenticationResult.Success -> proceedAfterAuthentication()
                    is AuthenticationResult.Error -> authError = result.errString.toString()
                    else -> {}
                }
            }

            override fun onAuthAttemptFailed() {
                authError = context.getString(R.string.biometric_error)
            }
        }
    )

    fun launchAuthentication() {
        val biometricManager = BiometricManager.from(context)
        val canUseBiometric = biometricManager.canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
        val canUseDeviceCredential = biometricManager.canAuthenticate(DEVICE_CREDENTIAL) ==
            BiometricManager.BIOMETRIC_SUCCESS

        when {
            canUseBiometric -> {
                val request = AuthenticationRequest.biometricRequest(
                    title = context.getString(R.string.biometric_title)
                ) {
                    setSubtitle(context.getString(R.string.biometric_subtitle))
                }
                launcher.launch(request)
            }

            canUseDeviceCredential -> {
                val request = AuthenticationRequest.credentialRequest(
                    title = context.getString(R.string.biometric_title)
                ) {
                    setSubtitle(context.getString(R.string.biometric_subtitle))
                }
                launcher.launch(request)
            }

            // No biometrics and no device credential configured: nothing to verify against.
            else -> proceedAfterAuthentication()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            impulseRemaining = SecurityManager.getImpulseBlockRemainingTime(context)
            FocusMode.syncIfDue(context)
            focusStatus = FocusMode.getStatus(context)
            delay(1.seconds)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        val pending = challenges
        if (pending != null) {
            fun giveUp() {
                challenges = null
                gaveUp = true
            }

            fun completeCurrent() {
                if (challengeIndex + 1 >= pending.size) onAuthenticated() else challengeIndex++
            }

            BackHandler(onBack = ::giveUp)
            ChallengeScaffold(step = challengeIndex + 1, total = pending.size, onGiveUp = ::giveUp) {
                key(challengeIndex) {
                    when (pending[challengeIndex]) {
                        SecurityManager.ChallengeType.MATH -> MathChallenge(onSuccess = ::completeCurrent)
                        SecurityManager.ChallengeType.WORDS -> WordChallenge(onSuccess = ::completeCurrent)
                        SecurityManager.ChallengeType.READING -> ReadingChallenge(onSuccess = ::completeCurrent)
                        SecurityManager.ChallengeType.TETRIS -> TetrisChallenge(
                            minutes = SecurityManager.getTetrisMinutes(context),
                            onSuccess = ::completeCurrent
                        )
                    }
                }
            }
            return@Surface
        }

        if (showingLimits) {
            BackHandler { showingLimits = false }
            UsageLimitsOverview(onClose = { showingLimits = false })
            return@Surface
        }

        val secondaryButtons: @Composable (showAccess: Boolean) -> Unit = { showAccess ->
            val showLimits = DeviceOwnerRepository.isDeviceOwner()
            if (showAccess || showLimits) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    if (showAccess) {
                        SmallActionButton(
                            icon = Icons.Outlined.LockOpen,
                            title = stringResource(R.string.access_app),
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { launchAuthentication() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (showLimits) {
                        SmallActionButton(
                            icon = Icons.Outlined.HourglassBottom,
                            title = stringResource(R.string.view_usage_limits),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = { showingLimits = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Works before authentication, like the panic button.
        val focusSection: @Composable () -> Unit = {
            FocusSessionCard(
                status = focusStatus,
                gate = recoveryGate,
                onChanged = { focusStatus = FocusMode.getStatus(context) },
                compact = true
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (impulseRemaining > 0) {
                ImpulseCountdown(impulseRemaining)
                Spacer(modifier = Modifier.height(16.dp))
                focusSection()
                Spacer(modifier = Modifier.height(16.dp))
                secondaryButtons(false)
            } else {
                if (gaveUp) {
                    Text(
                        stringResource(R.string.challenge_gave_up_message),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (DeviceOwnerRepository.isDeviceOwner()) {
                    BigActionButton(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.having_impulses),
                        subtitle = stringResource(R.string.having_impulses_subtitle),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        height = 104.dp,
                        onClick = {
                            SecurityManager.startImpulseBlock(context)
                            impulseRemaining = SecurityManager.getImpulseBlockRemainingTime(context)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

                focusSection()
                Spacer(modifier = Modifier.height(16.dp))
                secondaryButtons(true)

                if (authError != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        authError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        }

        RecoveryGateDialog(recoveryGate)
    }
}

@Composable
private fun SmallActionButton(
    icon: ImageVector,
    title: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier.height(64.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BigActionButton(
    icon: ImageVector,
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    height: Dp,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ImpulseCountdown(remainingMillis: Long) {
    val totalSeconds = remainingMillis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    // The block can now be configured up to 6 hours, so the hour part is only shown when there is one.
    val countdown = if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.Timer, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.impulse_timer_active),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = countdown,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
