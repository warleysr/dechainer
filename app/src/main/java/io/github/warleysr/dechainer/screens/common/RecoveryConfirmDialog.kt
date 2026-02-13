package io.github.warleysr.dechainer.screens.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.warleysr.dechainer.R
import io.github.warleysr.dechainer.security.SecurityManager

@Composable
fun RecoveryConfirmDialog(
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    if (SecurityManager.isSessionActive()) {
        onConfirm(code)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_recovery_code)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        val clean = input.uppercase().filter { it in 'A'..'Z' }.take(16)
                        code = clean
                        isError = false
                    },
                    label = { Text(stringResource(R.string.recovery_code_hint)) },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                stringResource(R.string.invalid_recovery_code),
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text("${code.length}/16")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.length == 16,
                onClick = {
                    if (!onConfirm(code)) {
                        isError = true
                    }
                }
            ) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
