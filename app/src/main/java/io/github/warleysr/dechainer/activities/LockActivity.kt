package io.github.warleysr.dechainer.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.AuthenticationRequest
import androidx.biometric.AuthenticationResult
import androidx.biometric.AuthenticationResultCallback
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.compose.rememberAuthenticationLauncher
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.screens.challenges.MathChallenge
import io.github.warleysr.dechainer.screens.challenges.WordChallenge
import io.github.warleysr.dechainer.security.SecurityManager
import io.github.warleysr.dechainer.ui.theme.DechainerTheme
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class LockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DechainerTheme {
                LockScreen(
                    onAuthenticated = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun LockScreen(onAuthenticated: () -> Unit) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var challengeMode by remember { mutableStateOf<SecurityManager.ImpulseLockMode?>(null) }
    var authError by remember { mutableStateOf<String?>(null) }
    var impulseRemaining by remember { mutableLongStateOf(-1L) }
    
    val context = LocalContext.current
    val launcher = rememberAuthenticationLauncher(
        resultCallback = object : AuthenticationResultCallback {
            override fun onAuthResult(result: AuthenticationResult) {
                when (result) {
                    is AuthenticationResult.Success -> {
                        isAuthenticated = true
                        authError = null
                    }
                    is AuthenticationResult.Error -> {
                        authError = result.errString.toString()
                    }
                    else -> {}
                }
            }
            override fun onAuthAttemptFailed() {
                authError = context.getString(R.string.biometric_error)
            }
        }
    )

    LaunchedEffect(Unit) {
        val request = AuthenticationRequest.biometricRequest(
            title = context.getString(R.string.biometric_title),
            AuthenticationRequest.Biometric.Fallback.DeviceCredential
        ) {
            setSubtitle(context.getString(R.string.biometric_subtitle))
        }
        launcher.launch(request)
    }

    LaunchedEffect(Unit) {
        while (true) {
            impulseRemaining = SecurityManager.getImpulseBlockRemainingTime(context)
            delay(1.seconds)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (challengeMode != null) {
            when (challengeMode) {
                SecurityManager.ImpulseLockMode.NORMAL -> MathChallenge(onSuccess = onAuthenticated)
                SecurityManager.ImpulseLockMode.HARD -> WordChallenge(onSuccess = onAuthenticated)
                else -> onAuthenticated()
            }
            return@Surface
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!isAuthenticated) {
                Text(stringResource(R.string.biometric_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                if (authError != null) {
                    Text(authError!!, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val request = AuthenticationRequest.biometricRequest(
                            title = context.getString(R.string.biometric_title),
                            AuthenticationRequest.Biometric.Fallback.DeviceCredential
                        ) {
                            setSubtitle(context.getString(R.string.biometric_subtitle))
                        }
                        launcher.launch(request)
                    }) {
                        Text(stringResource(R.string.confirm))
                    }
                }
            } else {
                if (impulseRemaining > 0) {
                    Text(stringResource(R.string.impulse_timer_active), style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    val minutes = (impulseRemaining / 1000) / 60
                    val seconds = (impulseRemaining / 1000) % 60
                    Text(
                        text = "%02d:%02d".format(minutes, seconds),
                        style = MaterialTheme.typography.displayMedium
                    )
                } else {
                    Button(
                        onClick = {
                            SecurityManager.startImpulseBlock(context)
                            impulseRemaining = SecurityManager.getImpulseBlockRemainingTime(context)
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.having_impulses))
                    }

                    Button(
                        onClick = { 
                            val mode = SecurityManager.getImpulseLockMode(context)
                            if (mode == SecurityManager.ImpulseLockMode.OFF) {
                                onAuthenticated()
                            } else {
                                challengeMode = mode
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Text(stringResource(R.string.access_app))
                    }
                }
            }
        }
    }
}
