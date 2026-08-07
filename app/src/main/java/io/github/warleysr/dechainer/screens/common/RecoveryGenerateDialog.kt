package io.github.warleysr.dechainer.screens.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.security.SecurityManager

@Composable
fun RecoveryGenerateDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var confirmKeySaved by remember { mutableStateOf(false) }
    var typedKey by remember { mutableStateOf("") }
    val generatedKey by remember { mutableStateOf(SecurityManager.generateRecoveryCode()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = confirmKeySaved && typedKey == generatedKey,
                onClick = { onConfirm(generatedKey) }
            ) {
                Text(stringResource(R.string.proceed))
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.your_key), fontWeight = FontWeight.Bold)
                Text(
                    text = generatedKey,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.blur(if (confirmKeySaved) 8.dp else 0.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
                if (confirmKeySaved) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = typedKey,
                        onValueChange = {
                            val clean = it.uppercase().filter { it in 'A'..'Z' }.take(16)
                            typedKey = clean
                        },
                        label = { Text(stringResource(R.string.enter_recovery_code)) },
                        placeholder = { Text(stringResource(R.string.recovery_code_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters
                        )
                    )
                }
            }
        }
    )
}
