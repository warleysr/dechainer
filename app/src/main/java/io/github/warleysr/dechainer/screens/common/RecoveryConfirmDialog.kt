package io.github.warleysr.dechainer.screens.common

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val context = LocalContext.current
    val shuffleKeyboard = remember {
        context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE)
            .getBoolean("shuffle_keyboard", false)
    }

    if (shuffleKeyboard) {
        ShuffleKeyboardRecoveryDialog(
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    } else {
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
}

/** All uppercase letters that can appear in the recovery passphrase. */
private val ALPHABET = ('A'..'Z').toList()

/**
 * Builds a 4x4 grid (16 cells) where [correctChar] occupies a random position and the remaining
 * 15 cells are filled with distinct random letters (different from [correctChar]).
 */
private fun buildGrid(correctChar: Char): List<Char> {
    val pool = (ALPHABET - correctChar).shuffled().take(15).toMutableList()
    pool.add(correctChar)
    pool.shuffle()
    return pool
}

/**
 * Recovery dialog that presents a 4x4 shuffle keyboard.
 * Only one cell per row contains the character at the current code position.
 * The entire grid reshuffles after every tap.
 */
@Composable
private fun ShuffleKeyboardRecoveryDialog(
    onConfirm: (String) -> Boolean,
    onDismiss: () -> Unit
) {
    var code by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var recoveryCode by remember { mutableStateOf(SecurityManager.getRecoveryCode(context)) }
    var grid by remember { mutableStateOf(buildGrid(recoveryCode?.get(code.length.coerceAtMost(15)) ?: 'X')) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enter_recovery_code)) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Progress indicator
                Text(
                    text = "${code.length}/16",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isError) {
                    Text(
                        text = stringResource(R.string.invalid_recovery_code),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 4 x 4 grid
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (row in 0 until 4) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (col in 0 until 4) {
                                val index = row * 4 + col
                                val letter = grid[index]
                                OutlinedButton(
                                    onClick = {
                                        if (code.length < 16) {
                                            code += letter
                                            isError = false
                                            grid = buildGrid(recoveryCode?.get(code.length.coerceAtMost(15)) ?: 'X')
                                        }
                                    },
                                    modifier = Modifier.weight(1f).aspectRatio(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Backspace button
                if (code.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            code = code.dropLast(1)
                            isError = false
                            grid = buildGrid(recoveryCode?.get(code.length.coerceAtMost(15)) ?: 'X')
                        }
                    ) {
                        Text(stringResource(R.string.shuffle_keyboard_backspace))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.length == 16,
                onClick = {
                    if (!onConfirm(code))
                        isError = true
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
