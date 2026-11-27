package io.github.warleysr.dechainer.activities

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PublicOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R

class EmbeddedBrowserBlockedActivity : ComponentActivity() {

    companion object {
        const val EXTRA_HOST = "detectedHost"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val detectedHost = intent.getStringExtra(EXTRA_HOST)

        setContent {
            EmbeddedBrowserBlockedScreen(
                detectedHost = detectedHost,
                onClose = { finishAfterTransition() }
            )
        }
    }
}

@Composable
fun EmbeddedBrowserBlockedScreen(detectedHost: String? = null, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.PublicOff, contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.embedded_browser_blocked),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.embedded_browser_blocked_description),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            if (!detectedHost.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    detectedHost,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onClose) {
                Text(stringResource(R.string.close))
            }
        }
    }
}
