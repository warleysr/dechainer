package io.github.warleysr.dechainer.screens.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.security.SecurityManager

@Composable
fun SetupRecovery(paddingValues: PaddingValues) {
    var showGenerateDialog by remember { mutableStateOf(false) }
    var confirmKeySaved by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        modifier = Modifier.padding(paddingValues).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showGenerateDialog) {
            val generatedKey = SecurityManager.generatePassphrase()
            AlertDialog(
                onDismissRequest = { showGenerateDialog = false },
                confirmButton = {
                    TextButton(
                        enabled = confirmKeySaved,
                        onClick = {
                            SecurityManager.saveRecoveryHash(context, generatedKey)
                        }
                    ) {
                        Text(stringResource(R.string.proceed))
                    }
                },
                text = {
                    Column( horizontalAlignment = Alignment.CenterHorizontally ) {
                        Text(stringResource(R.string.your_key), fontWeight = FontWeight.Bold)
                        Text(
                            text = "${generatedKey.take(4)}-${generatedKey.substring(4)}",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = { confirmKeySaved = !confirmKeySaved })
                        ) {
                            Checkbox(
                                checked = confirmKeySaved,
                                onCheckedChange = { confirmKeySaved = it }
                            )
                            Text(stringResource(R.string.confirm_key_saved))
                        }
                    }
                }
            )
        }
        Text(
            stringResource(R.string.attention),
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            color = Color.Red,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.self_lock_risk),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showGenerateDialog = true }) {
            Text(stringResource(R.string.generate_key))
        }
    }
}