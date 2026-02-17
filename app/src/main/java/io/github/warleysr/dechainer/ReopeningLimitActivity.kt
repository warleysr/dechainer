package io.github.warleysr.dechainer

import android.app.Activity
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
import androidx.compose.material.icons.filled.FrontHand
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class ReopeningLimitActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            val context = LocalContext.current
            val activity = context as? Activity

            ReopeningLimitScreen(
                appName = intent.getStringExtra("appName") ?: "",
                limit = intent.getIntExtra("limit", 0),
                onClose = {
                    activity?.finishAffinity()
                }
            )
        }
    }
}

@Composable
fun ReopeningLimitScreen(appName: String, limit: Int, onClose: () -> Unit) {
    var reopeningDescription by remember { mutableStateOf("") }
    val originalDescription = stringResource(R.string.app_reopening_description)

    LaunchedEffect(Unit) {
        repeat(limit) { i ->
            reopeningDescription = originalDescription
                .replace("{appName}", appName)
                .replace("{seconds}", (limit - i).toString())
            delay(1000)

            if (limit - i == 1)
                onClose()
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.FrontHand, contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(stringResource(R.string.app_reopening), style = MaterialTheme.typography.headlineMedium)
            Text(
                reopeningDescription,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onClose) {
                Text(stringResource(R.string.close))
            }
        }
    }
}